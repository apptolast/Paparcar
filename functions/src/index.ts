import { onDocumentWritten } from 'firebase-functions/v2/firestore';
import { onRequest, type Response } from 'firebase-functions/v2/https';
import { onSchedule } from 'firebase-functions/v2/scheduler';
import { gzipSync } from 'node:zlib';
import { setGlobalOptions } from 'firebase-functions/v2';
import * as logger from 'firebase-functions/logger';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { isDeadToken, targetsFor, verdictFor, type PublishedSpot, type ZoneAlertSub } from './fanout';
import { blockAround, cellId, zoneOf } from './parkingsGrid';
import {
  isWorthWriting,
  parseMadrid,
  toStored,
  type CityMeasurement,
  type StoredMeasurement,
} from './cityOccupancy';
import {
  cacheControlFor,
  hasPerishable,
  responseFor,
  type SnapshotCell,
  type SnapshotManifest,
} from './parkingsCell';
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
 *
 * ⛔ `onDocumentWritten` y no `onDocumentCreated`, por la misma razón que `onSharedCarParked`: la
 * noticia no es que el documento nazca. Desde [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] una plaza
 * puede NACER diciendo que el coche sigue dentro (`RELEASING`) y volverse plaza de verdad en un
 * UPDATE, que el disparador de creación no veía — las alertas de zona habrían dejado de salir en
 * silencio justo para las plazas mejor detectadas.
 * [SEC-A-RELEASING-SPOT-IS-DENIED-AT-THE-DOOR-001]
 */
export const onSpotPublished = onDocumentWritten('spots/{spotId}', async (event) => {
  const spotId = event.params.spotId;
  const beforeRaw = event.data?.before?.data();
  const afterRaw = event.data?.after?.data();
  // Un borrado no es noticia para nadie: la plaza deja de estar y el mapa lo ve solo.
  if (!afterRaw) return;

  const after: PublishedSpot = {
    id: spotId,
    latitude: afterRaw.latitude,
    longitude: afterRaw.longitude,
    geohash: afterRaw.geohash,
    reportedBy: afterRaw.reportedBy,
    status: afterRaw.status,
    expiresAt: afterRaw.expiresAt,
  };
  const before: PublishedSpot | undefined = beforeRaw && {
    id: spotId,
    latitude: beforeRaw.latitude,
    longitude: beforeRaw.longitude,
    geohash: beforeRaw.geohash,
    reportedBy: beforeRaw.reportedBy,
    status: beforeRaw.status,
    expiresAt: beforeRaw.expiresAt,
  };
  const spot = after;

  const now = Date.now();
  const verdict = verdictFor(before, after, now);
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
      // Un Error de verdad y no un objeto suelto: Error Reporting agrupa por stack, y un `err`
      // dentro del payload se queda en un log que nadie lee.
      // [OPS-AN-INDEX-IS-VERIFIED-AGAINST-PRODUCTION-001]
      logger.error(
        'live-spot count failed',
        err instanceof Error ? err : new Error(String(err)),
        { spotId },
      );
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

// ─── /api/parkings ───────────────────────────────────────────────────────────

/**
 * Donde vive la base pre-cocinada. [PARKINGS-ONE-CALL-MERGED-001]
 *
 * ⛔ Es la MISMA Hosting que hace el `rewrite` hasta aqui, y a proposito: un solo artefacto, el que
 * el ticket 1 publica y el que ODbL obliga a ofrecer. Overridable por entorno para poder apuntar al
 * emulador sin tocar codigo.
 */
const PARKINGS_BASE_URL = process.env.PARKINGS_BASE_URL ?? 'https://paparcar.com';

/** El esquema de la respuesta. Un `v` que no conocemos es un error, no algo que servir a medias. */
const SCHEMA_VERSION = '1';

/**
 * ⛔ Las celdas son INMUTABLES bajo su version, asi que una vez leidas se quedan en la instancia
 * para siempre: el `version/celda` es la llave y no hay nada que invalidar. El puntero NO, porque
 * es lo que puede cambiar de semana y lo que lleva el interruptor.
 */
const cellCache = new Map<string, SnapshotCell | null>();
let pointerCache: { manifest: SnapshotManifest; atMs: number } | null = null;

/** Lo que el puntero vive en memoria de la instancia. Corto: lleva el interruptor. */
const POINTER_TTL_MS = 60_000;

async function readPointer(now: number): Promise<SnapshotManifest | null> {
  if (pointerCache && now - pointerCache.atMs < POINTER_TTL_MS) return pointerCache.manifest;
  const response = await fetch(`${PARKINGS_BASE_URL}/parkings/v1/latest.json`);
  if (!response.ok) return null;
  const manifest = (await response.json()) as SnapshotManifest;
  pointerCache = { manifest, atMs: now };
  return manifest;
}

async function readCell(version: string, cell: string): Promise<SnapshotCell | null> {
  const key = `${version}/${cell}`;
  const cached = cellCache.get(key);
  if (cached !== undefined) return cached;
  const response = await fetch(`${PARKINGS_BASE_URL}/parkings/v1/${version}/${cell}.json`);
  // ⛔ Un 404 es «esta celda no tiene parkings», que es la respuesta correcta para la mayoria del
  // territorio — NO un error. Lo que distingue «vacia» de «sin publicar» es el puntero, que ya se
  // ha leido antes de llegar aqui.
  const cell404 = response.status === 404;
  if (!response.ok && !cell404) throw new Error(`cell ${key}: HTTP ${response.status}`);
  const value = cell404 ? null : ((await response.json()) as SnapshotCell);
  cellCache.set(key, value);
  return value;
}

/** Colección de la ocupación viva. ⛔ Espejada en `firestore.rules`. */
const OCCUPANCY_COLLECTION = 'publicParkings';

/** Cuánto vive la ocupación en memoria de la instancia. Corto: es el dato más perecedero que hay. */
const OCCUPANCY_TTL_MS = 30_000;

let occupancyCache: { measurements: CityMeasurement[]; atMs: number } | null = null;

/**
 * La ocupación viva, **entera**. [PARKINGS-LIVE-OCCUPANCY-001]
 *
 * ⛔ Se lee la colección COMPLETA y no por celda, a propósito: son **24 documentos** (Madrid, lo
 * único medido vivo el 03-11) y una consulta geográfica sobre eso costaría un índice y una caja que
 * mantener para ahorrar veinte lecturas. Quien filtra por proximidad es `fuseAvailability`, que ya
 * tiene que hacerlo igual.
 *
 * ⚠️ **Y deja de ser cierto cuando la colección crezca.** El umbral no es un número redondo: es
 * cuando leerla entera cueste más que su índice. Si entran el NAP nacional o varias ciudades, esto
 * pasa a consulta por caja — y entonces el índice hay que verificarlo CONTRA PRODUCCIÓN, que es la
 * lección de `OPS-A-COUNT-WITHOUT-ITS-INDEX-SILENCES-EVERY-ZONE-001`.
 */
async function readOccupancy(now: number): Promise<CityMeasurement[]> {
  if (occupancyCache && now - occupancyCache.atMs < OCCUPANCY_TTL_MS) {
    return occupancyCache.measurements;
  }
  const snapshot = await getFirestore().collection(OCCUPANCY_COLLECTION).get();
  const measurements = snapshot.docs.map((doc) => {
    const stored = doc.data() as StoredMeasurement;
    return { key: doc.id, ...stored } as CityMeasurement;
  });
  occupancyCache = { measurements, atMs: now };
  return measurements;
}

/**
 * **Trae la ocupación de las ciudades y la guarda — escribiendo sólo lo que ha cambiado.**
 * [PARKINGS-LIVE-OCCUPANCY-001]
 *
 * Es **lo único nuevo de infraestructura** en toda la ola: Cloud Scheduler. El resto —runtime,
 * deploy, secretos, Blaze— ya estaba estrenado, al contrario de lo que el roadmap daba por hecho.
 *
 * ⛔ **Cada 5 minutos y no cada 3**, que es lo que fijaba el roadmap: la capa gratuita de Firestore
 * es de 20.000 escrituras **al día**, y a 3 min reescribiendo los 24 de Madrid eran 11.520 — el
 * 58 % — en su mayoría para repetir lo ya guardado.
 *
 * ⛔ **Solo I/O**: el parseo, qué merece escritura y la fusión viven en `cityOccupancy.ts`.
 *
 * ⛔ **Y si Madrid falla, no se borra nada.** Una ingesta que vaciara la colección ante un 500
 * ajeno convertiría una caída de diez minutos del Ayuntamiento en «ningún parking de Madrid
 * publica plazas libres», que es la mentira que este expediente entero evita. El dato viejo se
 * queda, y es el `updatedAt` el que dice cuánto hace que se midió.
 */
export const ingestOccupancy = onSchedule(
  { schedule: 'every 5 minutes', region: 'europe-west1', timeZone: 'Europe/Madrid', maxInstances: 1 },
  async () => {
    const now = Date.now();
    let measurements: CityMeasurement[] = [];
    try {
      const response = await fetch(MADRID_ENDPOINT, {
        method: 'POST',
        headers: {
          'Content-Type': 'text/xml; charset=utf-8',
          SOAPAction: MADRID_SOAP_ACTION,
        },
        body: MADRID_ENVELOPE,
      });
      if (!response.ok) throw new Error(`madrid: HTTP ${response.status}`);
      measurements = parseMadrid(await response.text(), now);
    } catch (err) {
      // ⛔ Se registra y se SALE sin tocar la colección. Ver el KDoc de arriba.
      logger.error('madrid occupancy unreadable', err instanceof Error ? err : new Error(String(err)));
      return;
    }

    if (measurements.length === 0) {
      // Cero medidas no es un ciclo normal: es la forma de la respuesta cambiando. Nunca se
      // interpreta como «hoy no hay plazas libres en Madrid».
      logger.error('madrid occupancy parsed to nothing', new Error('shape changed?'));
      return;
    }

    const db = getFirestore();
    const collection = db.collection(OCCUPANCY_COLLECTION);
    const existing = new Map(
      (await collection.get()).docs.map((doc) => [doc.id, doc.data() as StoredMeasurement]),
    );

    let written = 0;
    const batch = db.batch();
    for (const measurement of measurements) {
      const next = toStored(measurement);
      if (!isWorthWriting(existing.get(measurement.key), next)) continue;
      batch.set(collection.doc(measurement.key), next, { merge: true });
      written++;
    }
    if (written > 0) await batch.commit();

    // La línea del coste: `measurements` es lo que la ciudad publicó, `written` lo que nos cobran.
    // La diferencia entre las dos es lo que ahorra escribir sólo cuando cambia.
    logger.info('occupancy ingested', { measurements: measurements.length, written });
  },
);

/** El servicio SOAP de Madrid. Público: ni clave, ni registro, ni token [MEDIDO 03-11-2026]. */
const MADRID_ENDPOINT = 'https://servayto.madrid.es/MTPAR_WSINFO/InfoParking';
const MADRID_SOAP_ACTION = '"http://tempuri.org/iInfoParking/GetListParking"';
const MADRID_ENVELOPE =
  '<?xml version="1.0" encoding="utf-8"?>' +
  '<soap:Envelope xmlns:soap="http://schemas.xmlsoap.org/soap/envelope/" xmlns:tem="http://tempuri.org/">' +
  '<soap:Body><tem:GetListParking><tem:language>es</tem:language></tem:GetListParking></soap:Body>' +
  '</soap:Envelope>';

/**
 * ⛔ **Comprime la respuesta NOSOTROS, porque Hosting no lo hace con la de una funcion.**
 *
 * Medido en la URL viva el 02-11: la celda de Sol son **95.188 B** y sin esto se mandan tal cual —
 * Hosting SI comprime los ficheros estaticos (el `.json` de una celda viaja a 1,6 KB) pero la salida
 * de la funcion sale entera. Son ~8× los datos del usuario, en la pantalla que el producto usa
 * conduciendo.
 *
 * `Vary: accept-encoding` no es decorativo: sin el, el CDN guardaria UNA copia y se la daria tambien
 * a quien no sepa descomprimirla.
 */
function sendJson(acceptEncoding: string | undefined, payload: unknown, response: Response): void {
  const body = JSON.stringify(payload);
  response.set('Content-Type', 'application/json; charset=utf-8');
  response.set('Vary', 'Accept-Encoding');
  if (!(acceptEncoding ?? '').includes('gzip')) {
    response.send(body);
    return;
  }
  response.set('Content-Encoding', 'gzip');
  response.send(gzipSync(Buffer.from(body, 'utf8')));
}

/**
 * **Los parkings de una celda, ya fusionados: UNA sola llamada para el movil.**
 * [PARKINGS-ONE-CALL-MERGED-001]
 *
 * Hoy la unica fuente es la base de OpenStreetMap del ticket 1. Lo que justifica que haya una
 * funcion y no una lectura directa del CDN es lo que viene: la ocupacion municipal (ticket 3) y
 * Parclick (ticket 5) se fusionan **aqui**, una vez, y no una por plataforma — que es la decision
 * del user en §E.3 del expediente y lo que decide un KMP que va a iOS.
 *
 * ⛔ **Solo I/O**, como las otras dos funciones de este fichero: el `blockAround`, la fusion y el
 * `Cache-Control` viven en `parkingsGrid.ts` y `parkingsCell.ts`, con sus tests.
 *
 * ⛔ **ANONIMA y sin cookies**: un CDN no cachea lo que depende de quien pregunta, y sin cache esta
 * funcion correria **una vez por usuario** en vez de una vez por celda y TTL. Encaja con la
 * doctrina del producto: nunca se gatea VER un parking, asi que no hay nada que personalizar.
 *
 * ⛔ **Y si algo falla, falla DE VERDAD** (5xx) en vez de devolver una lista vacia: el cliente tiene
 * Overpass de fallback y solo lo usa si esto se queja. Un 200 con `[]` seria «aqui no hay parkings»,
 * que es la mentira que todo este expediente existe para no contar.
 */
export const mergeCell = onRequest(
  { region: 'europe-west1', maxInstances: 10, cors: false, invoker: 'public' },
  async (request, response) => {
    const now = Date.now();
    const cell = String(request.query.cell ?? '');
    const schema = String(request.query.v ?? SCHEMA_VERSION);

    if (schema !== SCHEMA_VERSION) {
      response.status(400).json({ error: `unsupported schema v=${schema}` });
      return;
    }
    const zone = zoneOf(cell);
    if (!zone) {
      response.status(400).json({ error: 'cell must be <band>_<column>' });
      return;
    }

    try {
      const manifest = await readPointer(now);
      if (!manifest) {
        // El puntero es lo que distingue «celda vacia» de «snapshot sin publicar». Sin el no se
        // puede contestar sin mentir, asi que se admite el fallo y el cliente se va a Overpass.
        logger.error('parkings pointer unreadable', new Error('latest.json not available'));
        response.status(503).json({ error: 'pointer unavailable' });
        return;
      }

      const block = blockAround(zone);
      const files = await Promise.all(block.map((z) => readCell(manifest.version, cellId(z))));
      const cells = files.filter((f): f is SnapshotCell => f !== null);
      const payload = responseFor(cellId(zone), manifest, cells, now, await readOccupancy(now));

      logger.info('parkings cell served', {
        cell: payload.cell,
        withAvailability: payload.parkings.filter((p) => p.availability).length,
        version: manifest.version,
        blockCells: block.length,
        withData: cells.length,
        parkings: payload.parkings.length,
        serve: payload.serve,
      });

      response.set('Cache-Control', cacheControlFor(hasPerishable(payload)));
      sendJson(request.headers['accept-encoding'], payload, response);
    } catch (err) {
      logger.error(
        'parkings cell failed',
        err instanceof Error ? err : new Error(String(err)),
        { cell },
      );
      response.status(503).json({ error: 'upstream unavailable' });
    }
  },
);
