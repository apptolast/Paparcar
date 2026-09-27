/**
 * Who should be told about a spot, decided without a database, a clock or a network.
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * ⛔ Split out of `index.ts` on purpose, and it is the same split the app uses everywhere: the
 * trigger does I/O and this file decides. A rule that only runs inside a Firestore trigger can only
 * be checked by publishing a real spot and watching a real phone — which is not a test, it is a
 * field trip.
 */

/** The geohash precision the subscription index is keyed by. ⛔ Mirrored by the app's `cellsOf`. */
export const CELL_PRECISION = 6;

export interface ZoneAlertSub {
  uid: string;
  zoneId: string;
  zoneName: string;
  latitude: number;
  longitude: number;
  radiusMeters: number;
  cells: string[];
  tokens: string[];
  /** Epoch millis. A lapsed user who never reopens the app must not be watched forever. */
  expiresAt: number;
}

/** Only what the decision needs — so a test does not have to fake a Firestore document. */
export interface PublishedSpot {
  id: string;
  latitude: number;
  longitude: number;
  geohash?: string;
  reportedBy?: string;
  status?: string;
  expiresAt?: number;
}

/** Why a spot was not announced at all. Named, so the log says something a human can act on. */
export type SkipReason =
  | 'no-geohash'
  | 'retracted'
  | 'releasing'
  | 'already-expired'
  | 'gone'
  | 'already-announced';

export type SpotVerdict =
  | { kind: 'skip'; reason: SkipReason }
  | { kind: 'announce'; cell: string; isProvisional: boolean; expiresAt: number };

/** Metres. Mirrors `haversineMeters` in the app — the only geometry on this side. */
export function distanceMeters(
  lat1: number, lon1: number, lat2: number, lon2: number,
): number {
  const earthRadius = 6_371_000;
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) ** 2;
  return 2 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

/** What a spot would be announced AS, or why it would not be announced at all. */
type Offer =
  | { ok: true; cell: string; isProvisional: boolean; expiresAt: number }
  | { ok: false; reason: SkipReason };

/**
 * Does this spot, as it stands, offer a stranger a plaza right now?
 *
 * Asked of one VERSION of the document, so `verdictFor` can ask it of the document before and
 * after the write and announce only the crossing between the two.
 */
function offer(spot: PublishedSpot | undefined, nowMs: number): Offer {
  if (!spot) return { ok: false, reason: 'gone' };
  if (!spot.geohash || spot.geohash.length < CELL_PRECISION) {
    return { ok: false, reason: 'no-geohash' };
  }
  // A spot written already-withdrawn would be a notification about a space the app itself does not
  // believe in.
  if (spot.status === 'RETRACTED') return { ok: false, reason: 'retracted' };

  // ⛔ [SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001] Un embarque NO se anuncia, por las dos mitades de
  // esa razón y cada una basta: la plaza vive CUATRO minutos, y sobre todo el coche **sigue
  // dentro** — un push diciendo «hay una plaza en tu zona» sería falso en el instante en que
  // suena. Mismo veredicto que `ZoneSpotAlertPolicy` toma en el cliente: son las dos mitades de
  // la misma promesa y no pueden discrepar. Lo que sí se anuncia es su PROMOCIÓN, más abajo.
  if (spot.status === 'RELEASING') return { ok: false, reason: 'releasing' };

  // Una plaza sin caducidad NO se anuncia. [SEC-A-SPOT-IS-NOT-A-MEGAPHONE-001]
  //
  // `expiresAt === 0` era «TTL todavia sin fijar» y se dejaba pasar por prudencia; escrito a mano
  // es una plaza que no caduca nunca, y anunciarla convierte la coleccion en un megafono. La regla
  // nueva impide crearlas, pero una regla no repara las filas que ya existen: esto si.
  const expiresAt = typeof spot.expiresAt === 'number' ? spot.expiresAt : 0;
  if (expiresAt === 0 || expiresAt <= nowMs) {
    return { ok: false, reason: 'already-expired' };
  }
  return {
    ok: true,
    cell: spot.geohash.slice(0, CELL_PRECISION),
    isProvisional: spot.status === 'PROVISIONAL',
    expiresAt,
  };
}

/**
 * Is this WRITE worth looking anybody up for, and in which cell.
 *
 * ⛔ El veredicto es sobre la TRANSICIÓN, no sobre el documento — se anuncia cuando el documento
 * pasa a ofrecer una plaza y antes no la ofrecía. [SEC-A-RELEASING-SPOT-IS-DENIED-AT-THE-DOOR-001]
 *
 * Nació preguntando sólo por el documento nuevo porque su disparador era `onDocumentCreated`, y
 * «creado» y «pasa a haber plaza» eran lo mismo. Dejaron de serlo con el embarque: la nominación
 * gasta el CREATE diciendo que el coche sigue dentro, así que la salida real —la única noticia de
 * verdad— llega como un UPDATE que aquel disparador no veía. Con el veredicto en la transición, la
 * plaza se anuncia una vez y en el momento en que existe, venga por donde venga.
 *
 * PROVISIONAL is announced — it is the freshest thing on the map and the one the old fifteen-minute
 * poll could never reach — but it comes back flagged, so the copy can say it is not confirmed. Su
 * promoción a CONFIRMED no vuelve a anunciar: ya avisó al nacer.
 */
export function verdictFor(
  before: PublishedSpot | undefined,
  after: PublishedSpot | undefined,
  nowMs: number,
): SpotVerdict {
  const now = offer(after, nowMs);
  if (!now.ok) return { kind: 'skip', reason: now.reason };
  // Ya había plaza antes de esta escritura: un contador comunitario, una promoción de
  // PROVISIONAL a CONFIRMED… noticia del documento, no de la plaza.
  if (offer(before, nowMs).ok) return { kind: 'skip', reason: 'already-announced' };
  return {
    kind: 'announce',
    cell: now.cell,
    isProvisional: now.isProvisional,
    expiresAt: now.expiresAt,
  };
}

/**
 * Of the subscriptions whose cell matched, the ones this spot is actually news for.
 *
 * ⛔ The cell overfetches — it is a rectangle and a zone is a circle — so the distance check is not
 * a refinement, it is the contract. Without it a spot a kilometre away would wake somebody up
 * because it shared a geohash prefix.
 */
export function targetsFor(
  spot: PublishedSpot,
  subs: ZoneAlertSub[],
  nowMs: number,
): ZoneAlertSub[] {
  return subs.filter((sub) => {
    // The place YOU just left is not news to you.
    if (sub.uid === spot.reportedBy) return false;
    // A subscription nobody has refreshed stopped being a statement about today.
    if (typeof sub.expiresAt === 'number' && sub.expiresAt <= nowMs) return false;
    // Nobody to tell: a row with no device is one the server reads for nothing.
    if (!sub.tokens || sub.tokens.filter((t) => !!t).length === 0) return false;
    return distanceMeters(sub.latitude, sub.longitude, spot.latitude, spot.longitude)
      <= sub.radiusMeters;
  });
}

/** Only the two codes that mean "this device is gone" — a network blip must not drop a token. */
export function isDeadToken(code: string | undefined): boolean {
  return (
    code === 'messaging/registration-token-not-registered' ||
    code === 'messaging/invalid-registration-token'
  );
}
