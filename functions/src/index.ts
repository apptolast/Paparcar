import { onDocumentCreated, onDocumentWritten } from 'firebase-functions/v2/firestore';
import { setGlobalOptions } from 'firebase-functions/v2';
import * as logger from 'firebase-functions/logger';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { isDeadToken, targetsFor, verdictFor, type PublishedSpot, type ZoneAlertSub } from './fanout';
import {
  targetsFor as sharedCarTargetsFor,
  verdictFor as sharedCarVerdictFor,
  type SharedCarMember,
  type SharedCarState,
} from './sharedCarFanout';

initializeApp();

/**
 * ⛔ `europe-west1`, because `pap-26` keeps Firestore in `eur3`. A function far from its database
 * pays latency and egress on EVERY trigger, and this one fires once per published spot.
 */
setGlobalOptions({ region: 'europe-west1', maxInstances: 10 });

/** ⛔ Mirrored in the app's `FirestoreZoneAlertSubscriptionRepository` and in `firestore.rules`. */
const SUBS_COLLECTION = 'zoneAlertSubs';

/**
 * Cuantas plazas VIVAS puede tener a la vez la misma cuenta sin que se deje de anunciar.
 * [SEC-A-SPOT-IS-NOT-A-MEGAPHONE-001]
 *
 * No es un limite de publicacion: las plazas se crean igual y el mapa las enseña. Lo que se corta
 * es el aviso, que es lo que llega al movil de otra persona. Cinco es holgado para un humano --
 * una plaza dura 2 h y nadie libera cinco a la vez -- y estrecho para un script.
 */
const MAX_LIVE_SPOTS_PER_AUTHOR = 5;

/**
 * Lo que hace falta para que un mensaje de SOLO DATOS llegue a un iPhone que no está delante.
 * [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * ⛔ APNs no entrega un mensaje sin `content-available` a una app en segundo plano, y FCM no lo
 * añade por su cuenta: sin este bloque, un iPhone con su token publicado seguiría sin recibir nada
 * y nadie se enteraría — ni error, ni rebote, ni fila.
 *
 * ⛔ **Prioridad 5, no 10, y no es una elección**: Apple exige `apns-priority: 5` para un push de
 * tipo `background`, y rechaza la combinación con 10. Es la contrapartida asumida de que el mensaje
 * sea de datos: la copia en nueve idiomas y el dedup de lo ya anunciado viven en el cliente, y para
 * que el cliente decida hay que despertarlo en silencio. Android sí va a prioridad alta arriba.
 */
const APNS_BACKGROUND = {
  headers: {
    'apns-push-type': 'background',
    'apns-priority': '5',
  },
  payload: {
    aps: { contentAvailable: true },
  },
};

/**
 * A community spot was published: tell everyone whose zone contains it.
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * This replaces a 15-minute poll per zone per user. The cost stops scaling with how many people are
 * watching and starts scaling with how much is actually happening, which is the whole point.
 *
 * ⛔ **Entitlement is NOT checked here, and that is deliberate.** Plus lives in RevenueCat and is
 * read on the device; a second source of truth on the server would drift from it. Instead, *being in
 * the index at all* is the permission: the client only keeps a subscription while Plus ∧ the
 * Settings switch ∧ the zone's own opt-in all hold, and deletes it the moment one stops. Losing Plus
 * therefore pauses the alert without touching any zone — the same downgrade rule as before, enforced
 * in a new place.
 *
 * ⛔ This function does I/O and nothing else. Every decision lives in `fanout.ts`, where it can be
 * tested without publishing a real spot and staring at a real phone.
 */
export const onSpotPublished = onDocumentCreated('spots/{spotId}', async (event) => {
  const raw = event.data?.data();
  if (!raw) return;

  const spotId = event.params.spotId;
  const spot: PublishedSpot = {
    id: spotId,
    latitude: raw.latitude,
    longitude: raw.longitude,
    geohash: raw.geohash,
    reportedBy: raw.reportedBy,
    status: raw.status,
    expiresAt: raw.expiresAt,
  };

  const now = Date.now();
  const verdict = verdictFor(spot, now);
  if (verdict.kind === 'skip') {
    logger.info('spot not announced', { spotId, reason: verdict.reason });
    return;
  }

  // ⛔ Tercera capa de [SEC-A-SPOT-IS-NOT-A-MEGAPHONE-001], y la unica que ve el RITMO: una regla
  // valida plaza a plaza y no puede contar cuantas lleva alguien en diez minutos. Publicar sigue
  // permitido -- lo que se corta es GRITAR, que es lo caro para quien recibe.
  // ⛔ El conteo es una SEGUNDA capa, y por eso su fallo no puede costar el aviso: la plaza ya pasó
  // la regla -- caducidad valida, estado valido, firmada con su uid --, asi que ante la duda se
  // anuncia. Al reves, una consulta rota silenciaria el producto entero, que es exactamente lo que
  // paso al desplegar esto sin su indice compuesto (igualdad + rango en campos distintos).
  // [OPS-A-COUNT-WITHOUT-ITS-INDEX-SILENCES-EVERY-ZONE-001]
  const live = await getFirestore()
    .collection('spots')
    .where('reportedBy', '==', spot.reportedBy)
    .where('expiresAt', '>', now)
    .count()
    .get()
    .then((snap) => snap.data().count)
    .catch((err) => {
      logger.error('live-spot count failed', { spotId, err });
      return null;
    });
  if (live !== null && live > MAX_LIVE_SPOTS_PER_AUTHOR) {
    logger.warn('spot not announced: author is flooding', {
      spotId,
      reportedBy: spot.reportedBy,
      live,
    });
    return;
  }

  const snapshot = await getFirestore()
    .collection(SUBS_COLLECTION)
    .where('cells', 'array-contains', verdict.cell)
    .get();

  const withRefs = snapshot.docs.map((doc) => ({
    sub: doc.data() as ZoneAlertSub,
    ref: doc.ref,
  }));
  const targets = targetsFor(spot, withRefs.map((w) => w.sub), now);
  const byId = new Map(withRefs.map((w) => [`${w.sub.uid}_${w.sub.zoneId}`, w.ref]));

  // The cost line: `subsInCell` is what Firestore charged for on this trigger, `targets` is what it
  // bought. The gap between them is the geohash overfetch, and it is the number that says whether
  // the cell precision is right.
  logger.info('spot fan-out', {
    spotId,
    cell: verdict.cell,
    subsInCell: snapshot.size,
    targets: targets.length,
    provisional: verdict.isProvisional,
  });

  if (targets.length === 0) return;

  await Promise.all(
    targets.map((sub) => {
      const ref = byId.get(`${sub.uid}_${sub.zoneId}`);
      return ref
        ? deliver(sub, ref, spotId, spot.latitude, spot.longitude, verdict.isProvisional, verdict.expiresAt)
        : Promise.resolve();
    }),
  );
});

/**
 * El coche compartido se ha movido: contárselo al resto del grupo y mover su valla con él.
 * [SHARED-A-PARKING-TELLS-THE-GROUP-001] + [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
 *
 * Dos noticias por el mismo documento, porque son el mismo hecho contado por sus dos caras:
 * **aparcado** dice dónde está el coche, y **se lo han llevado** dice que ya no está ahí — y
 * esa segunda es, además, el momento en que la valla compartida tiene que salir de un sitio
 * que el coche ha dejado, antes de que dispare por un coche que nadie ha movido.
 *
 * Why a push and not the app noticing on its own: the mirror is already written and the others
 * already *can* read it — but only while they have the app open, or on the janitor's next 15-minute
 * pass. That is fine for a figure in a history screen and useless for the two things this feature
 * exists for: knowing the car moved, and having the geofence sitting where the car actually is
 * before you walk out of the door. The push is what turns «eventually finds out» into «finds out».
 *
 * ⛔ `onDocumentWritten` and not `onDocumentCreated`: the state document is written once per
 * parking, but it is CREATED once per car — every parking after the first one is an update.
 *
 * ⛔ Same rule as the zone fan-out: entitlement is not read here. Being in `members/{uid}` with a
 * token IS the permission, and leaving the group deletes the row.
 *
 * ⛔ I/O only. What counts as news and who hears it lives in `sharedCarFanout.ts`.
 */
export const onSharedCarParked = onDocumentWritten('sharedCars/{carId}/state/current', async (event) => {
  const carId = event.params.carId;
  const before = event.data?.before?.data() as SharedCarState | undefined;
  const after = event.data?.after?.data() as SharedCarState | undefined;

  const verdict = sharedCarVerdictFor(before, after);
  if (verdict.kind === 'skip') {
    logger.info('shared car parking not announced', { carId, reason: verdict.reason });
    return;
  }

  const membersRef = getFirestore().collection('sharedCars').doc(carId).collection('members');
  const snapshot = await membersRef.get();
  const members = snapshot.docs.map((doc) => ({ ...(doc.data() as SharedCarMember), uid: doc.id }));
  const targets = sharedCarTargetsFor(members, verdict.authorUid);

  logger.info('shared car fan-out', {
    carId,
    kind: verdict.kind,
    sessionId: verdict.sessionId,
    members: snapshot.size,
    targets: targets.length,
  });

  if (targets.length === 0) return;

  const payload: Record<string, string> =
    verdict.kind === 'announce'
      ? {
          type: 'shared_car_parked',
          carId,
          authorName: verdict.authorName,
          addressLine: verdict.addressLine,
          latitude: String(after!.latitude!),
          longitude: String(after!.longitude!),
          parkedAtMs: String(after!.parkedAtMs ?? 0),
          sessionId: verdict.sessionId,
        }
      : { type: 'shared_car_left', carId, authorName: verdict.authorName, sessionId: verdict.sessionId };

  await Promise.all(
    targets.map((member) => deliverToMember(membersRef.doc(member.uid), member, carId, payload)),
  );
});

/**
 * One member's devices, for either piece of news.
 *
 * A **data** message, like the zone one and for a cousin of the same reason: the wording lives in
 * the app, which is the only side that has the nine locales. A notification message would arrive in
 * whatever language the server guessed. It also lets the phone decide to stay quiet about a trip it
 * was on itself — a decision only it can make. [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
 */
async function deliverToMember(
  ref: FirebaseFirestore.DocumentReference,
  member: SharedCarMember,
  carId: string,
  data: Record<string, string>,
): Promise<void> {
  const tokens = (member.tokens ?? []).filter((t) => !!t);
  if (tokens.length === 0) return;

  const response = await getMessaging().sendEachForMulticast({
    tokens,
    data,
    android: {
      // High priority because the fence move rides on this message: a delivery held until the next
      // Doze window would put the geofence back exactly where this feature is trying not to leave it.
      priority: 'high',
    },
    apns: APNS_BACKGROUND,
  });

  const dead = response.responses
    .map((r, i) => (!r.success && isDeadToken(r.error?.code) ? tokens[i] : null))
    .filter((t): t is string => t !== null);

  if (dead.length > 0) {
    logger.info('dropping dead shared-car tokens', { carId, uid: member.uid, count: dead.length });
    await ref.update({ tokens: FieldValue.arrayRemove(...dead) });
  }
}

/**
 * One subscription's devices.
 *
 * ⛔ A **data** message, not a notification one: the client decides whether to show it, because the
 * client is the only side that knows what it has already announced (`zone_alerted_spots`). Letting
 * FCM display it directly would announce the same free space twice the day a spot arrives by two
 * routes — and the dedup rule would have two homes.
 */
async function deliver(
  sub: ZoneAlertSub,
  ref: FirebaseFirestore.DocumentReference,
  spotId: string,
  latitude: number,
  longitude: number,
  isProvisional: boolean,
  expiresAt: number,
): Promise<void> {
  const tokens = (sub.tokens ?? []).filter((t) => !!t);
  if (tokens.length === 0) return;

  const response = await getMessaging().sendEachForMulticast({
    tokens,
    data: {
      type: 'zone_spot',
      spotId,
      zoneId: sub.zoneId,
      zoneName: sub.zoneName,
      latitude: String(latitude),
      longitude: String(longitude),
      provisional: String(isProvisional),
      // Travels so the client can write the dedup row with the spot's own death as its expiry —
      // what stops a repeat is the space still existing, not the age of the notification.
      expiresAt: String(expiresAt),
    },
    android: {
      // A free space is worth minutes: this has to wake an app in Doze, not wait for a window.
      priority: 'high',
    },
    apns: APNS_BACKGROUND,
  });

  // Tokens Firebase tells us are dead. Left alone they would be retried on every spot for ever —
  // the slow leak that turns a cheap fan-out into an expensive one.
  const dead = response.responses
    .map((r, i) => (!r.success && isDeadToken(r.error?.code) ? tokens[i] : null))
    .filter((t): t is string => t !== null);

  if (dead.length > 0) {
    logger.info('dropping dead tokens', { uid: sub.uid, count: dead.length });
    await ref.update({ tokens: FieldValue.arrayRemove(...dead) });
  }
}
