import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { setGlobalOptions } from 'firebase-functions/v2';
import * as logger from 'firebase-functions/logger';
import { initializeApp } from 'firebase-admin/app';
import { getFirestore, FieldValue } from 'firebase-admin/firestore';
import { getMessaging } from 'firebase-admin/messaging';
import { isDeadToken, targetsFor, verdictFor, type PublishedSpot, type ZoneAlertSub } from './fanout';

initializeApp();

/**
 * ⛔ `europe-west1`, because `pap-26` keeps Firestore in `eur3`. A function far from its database
 * pays latency and egress on EVERY trigger, and this one fires once per published spot.
 */
setGlobalOptions({ region: 'europe-west1', maxInstances: 10 });

/** ⛔ Mirrored in the app's `FirestoreZoneAlertSubscriptionRepository` and in `firestore.rules`. */
const SUBS_COLLECTION = 'zoneAlertSubs';

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
