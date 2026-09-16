/**
 * Who hears that the shared car just got parked, decided without a database or a clock.
 * [SHARED-A-PARKING-TELLS-THE-GROUP-001]
 *
 * ⛔ Split from `index.ts` for the same reason the zone fan-out is: a rule that only runs inside a
 * Firestore trigger can only be checked by parking a real car and watching a real phone — which is
 * not a test, it is an afternoon.
 */

/** `sharedCars/{carId}/state/current` — the group's view of where the car is now. */
export interface SharedCarState {
  sessionId?: string;
  authorUid?: string;
  authorName?: string;
  latitude?: number;
  longitude?: number;
  /** Cuándo se paró el coche: es lo que el móvil que recibe compara con su propio ancla para saber
   *  si esta noticia es de su propio viaje. [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001] */
  parkedAtMs?: number;
  isParked?: boolean;
  address?: { street?: string | null; city?: string | null } | null;
}

/** `sharedCars/{carId}/members/{uid}` — one person and the devices they read on. */
export interface SharedCarMember {
  uid: string;
  displayName?: string | null;
  tokens?: string[];
}

export type FanoutVerdict =
  | { kind: 'skip'; reason: string }
  | { kind: 'announce'; sessionId: string; authorUid: string; authorName: string; addressLine: string }
  | { kind: 'announce-left'; sessionId: string; authorUid: string; authorName: string };

/**
 * Whether this write is news, and what it says.
 *
 * Two kinds of news, and they are not symmetrical. **Parked** answers *where is the car*;
 * **left** answers *the car is not there any more* — which for everyone else in the group is also
 * the moment their shared fence has to come off a spot the car has vacated, or it sits there as a
 * trigger for a car nobody moved. [DET-A-SHARED-FENCE-FOLLOWS-THE-CAR-001]
 *
 * Refusals, and each one is a real event that must not reach a phone:
 *  - **Nothing happened to the parking** — a write that leaves the car where it was is the
 *    geocoder answering late, not a departure.
 *  - **Same parking as before** — that same late answer rewrites the parking with its street.
 *    Announcing both is the same car parked twice.
 *  - **Nothing to point at** — a state with no session or no coordinates cannot open a map.
 */
export function verdictFor(before: SharedCarState | undefined, after: SharedCarState | undefined): FanoutVerdict {
  if (!after) return { kind: 'skip', reason: 'state-deleted' };
  if (after.isParked !== true) return departureVerdict(before, after);
  const sessionId = after.sessionId;
  if (!sessionId) return { kind: 'skip', reason: 'no-session' };
  if (typeof after.latitude !== 'number' || typeof after.longitude !== 'number') {
    return { kind: 'skip', reason: 'no-position' };
  }
  if (before?.isParked === true && before.sessionId === sessionId) {
    return { kind: 'skip', reason: 'same-parking' };
  }
  const authorUid = after.authorUid;
  if (!authorUid) return { kind: 'skip', reason: 'no-author' };

  return {
    kind: 'announce',
    sessionId,
    authorUid,
    // El nombre puede faltar (perfil sin displayName): el cliente sabe decir «alguien del grupo»,
    // pero la cadena vacía viaja tal cual — inventar un nombre aquí sería ponerle voz al servidor.
    authorName: after.authorName ?? '',
    addressLine: addressLineOf(after),
  };
}

/**
 * The car just left, and who took it.
 *
 * ⛔ The departure has to be a TRANSITION, not a state: `isParked: false` is the resting value of a
 * car nobody has moved since yesterday, and every later write of that document — the group's name
 * changing, a stale sync landing — would re-announce a departure that happened a day ago.
 *
 * ⛔ The author is whoever PARKED it, and that is not a shortcut: the mirror is only flipped to
 * "gone" by the phone whose own session the state is showing. Somebody else taking the car does not
 * write this document at all — they write a new parking when they stop, which is the other verdict.
 * That is also why nobody needs to be told "the car is being driven by a person we cannot name".
 */
function departureVerdict(before: SharedCarState | undefined, after: SharedCarState): FanoutVerdict {
  if (before?.isParked !== true) return { kind: 'skip', reason: 'not-parked' };
  const sessionId = after.sessionId;
  if (!sessionId || before.sessionId !== sessionId) return { kind: 'skip', reason: 'another-parking' };
  const authorUid = after.authorUid;
  if (!authorUid) return { kind: 'skip', reason: 'no-author' };
  return { kind: 'announce-left', sessionId, authorUid, authorName: after.authorName ?? '' };
}

/**
 * Quién recibe el aviso: el grupo MENOS quien aparcó.
 *
 * ⛔ La exclusión del autor se hace aquí y no en el móvil, porque un móvil no puede saber si el
 * mensaje era para otro — solo puede callarse DESPUÉS de haberlo recibido, y para entonces ya ha
 * gastado una entrega y una vibración.
 */
export function targetsFor(members: SharedCarMember[], authorUid: string): SharedCarMember[] {
  return members.filter((m) => m.uid !== authorUid && (m.tokens ?? []).length > 0);
}

/** La calle, o vacío: el cliente dice «toca para ver dónde está» cuando no hay ninguna. */
function addressLineOf(state: SharedCarState): string {
  const street = state.address?.street?.trim();
  if (street) return street;
  const city = state.address?.city?.trim();
  return city ?? '';
}
