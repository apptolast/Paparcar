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
  | { kind: 'announce'; sessionId: string; authorUid: string; authorName: string; addressLine: string };

/**
 * Whether this write is news, and what it says.
 *
 * Three refusals, and each one is a real event that must not reach a phone:
 *  - **Not parked** — the state also gets written when the car LEAVES. «Marta ha aparcado» would be
 *    exactly backwards.
 *  - **Same parking as before** — the geocoder answers late, so the same parking is written twice:
 *    once with coordinates and again with its street. Announcing both is the same car parked twice.
 *  - **Nothing to point at** — a state with no session or no coordinates cannot open a map.
 */
export function verdictFor(before: SharedCarState | undefined, after: SharedCarState | undefined): FanoutVerdict {
  if (!after) return { kind: 'skip', reason: 'state-deleted' };
  if (after.isParked !== true) return { kind: 'skip', reason: 'not-parked' };
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
