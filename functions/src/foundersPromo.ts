/**
 * Campaign rules and workflow for the Founders gift. [MONEY-FOUNDERS-PLUS-PROMO-001]
 * Firestore accounts for seats. RevenueCat owns access to Plus.
 */
export const PROMO_DOC_PATH = 'promos/founders';
export const CLAIMS_COLLECTION = 'foundersClaims';
export const GRANTS_COLLECTION = 'grants';
export const ENTITLEMENT_PLUS = 'plus';
export const FOUNDERS_CLOSE_MILLIS = Date.UTC(2026, 10, 30, 23);
// Measured 26-11: 5 attempts exhaust in ~74s of Eventarc's own backoff — nowhere near enough
// wall-clock time for the client's one-shot RevenueCat identify [MONEY-001] to land when a 404
// means "not yet", not "never". [MONEY-FOUNDERS-GRANT-A-MISSING-SUBSCRIBER-IS-NOT-A-REJECTION-001]
export const MAX_GRANT_ATTEMPTS = 20;

// Longer than the whole grant invocation. A duplicate can take over after a crashed invocation.
const GRANT_LEASE_MILLIS = 60_000;

export interface PromoConfig {
  readonly active: boolean;
  readonly cap: number;
  readonly granted: number;
  readonly durationMonths: number;
  readonly endsAtMillis: number | null;
  readonly startsAtMillis: number | null;
}

export type ClaimOutcome = 'granted' | 'failed' | 'closed' | 'full' | 'inactive' | 'notNew';

export interface GrantRecord {
  readonly status: 'reserved' | 'granted' | 'failed' | 'uncertain' | 'releasing';
  readonly attempts: number;
  readonly expiresAtMillis: number;
  readonly leaseUntilMillis: number;
  readonly possiblyGranted: boolean;
}

export type Reservation = 'reserve' | 'resume' | ClaimOutcome | 'uncertain' | 'releasing';

export function decideReservation(
  promo: PromoConfig | null,
  grant: GrantRecord | null,
  claimedAtMillis: number,
  accountCreatedAtMillis: number,
): Reservation {
  if (grant?.status === 'reserved') return 'resume';
  if (grant) return grant.status;

  if (
    !promo?.active ||
    !Number.isSafeInteger(promo.cap) ||
    promo.cap < 1 ||
    promo.cap > 2_000 ||
    !Number.isSafeInteger(promo.granted) ||
    promo.granted < 0 ||
    promo.durationMonths !== 3 ||
    !Number.isSafeInteger(promo.endsAtMillis) ||
    promo.endsAtMillis === null ||
    promo.endsAtMillis > FOUNDERS_CLOSE_MILLIS ||
    !Number.isSafeInteger(promo.startsAtMillis) ||
    promo.startsAtMillis === null ||
    promo.startsAtMillis >= promo.endsAtMillis
  ) {
    return 'inactive';
  }
  if (claimedAtMillis >= promo.endsAtMillis) return 'closed';
  if (promo.granted >= promo.cap) return 'full';
  // A founders seat rewards someone arriving BECAUSE of the campaign, not a pre-existing
  // account that happens to update and reopen the app. The cutoff is the campaign's own
  // start, not "today": an account created the day the campaign launched still counts.
  if (accountCreatedAtMillis < promo.startsAtMillis) return 'notNew';
  return 'reserve';
}

/** Add calendar months in UTC, clamping the day to the last day of the destination month. */
export function addCalendarMonths(fromMs: number, months: number): number {
  const target = new Date(fromMs);
  const day = target.getUTCDate();
  target.setUTCDate(1);
  target.setUTCMonth(target.getUTCMonth() + months);
  const lastDayOfTarget = new Date(
    Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0),
  ).getUTCDate();
  target.setUTCDate(Math.min(day, lastDayOfTarget));
  return target.getTime();
}

/** RevenueCat v1 treats the same expiry as a duplicate while that promotional grant is active. */
export function grantUrl(appUserId: string): string {
  return entitlementUrl(appUserId) + '/promotional';
}

export function revokeUrl(appUserId: string): string {
  return entitlementUrl(appUserId) + '/revoke_promotionals';
}

function entitlementUrl(appUserId: string): string {
  return (
    'https://api.revenuecat.com/v1/subscribers/' +
    encodeURIComponent(appUserId) +
    '/entitlements/' +
    ENTITLEMENT_PLUS
  );
}

export interface StoreOutcome {
  readonly ok: boolean;
  readonly status: number;
  readonly retryable: boolean;
  readonly possiblyApplied: boolean;
}

export function outcomeFor(status: number): StoreOutcome {
  const ok = status >= 200 && status < 300;
  const possiblyApplied = status === 0 || status === 408 || status >= 500;
  // A 404 on the promotional-grant call means RevenueCat does not know this app_user_id YET — the
  // client's own identify can still be in flight, or have missed its one best-effort attempt
  // [MONEY-001]. Retrying gives it room to catch up instead of returning the seat on the first try.
  // [MONEY-FOUNDERS-GRANT-A-MISSING-SUBSCRIBER-IS-NOT-A-REJECTION-001]
  return { ok, status, possiblyApplied, retryable: possiblyApplied || status === 429 || status === 404 };
}

export const NETWORK_OUTCOME = outcomeFor(0);

export interface ClaimRecord {
  readonly id: string;
  readonly claimedAtMillis: number;
  readonly outcome?: ClaimOutcome;
}

export interface LedgerState {
  readonly promo: PromoConfig | null;
  readonly grant: GrantRecord | null;
  readonly claim: ClaimRecord | null;
}

export interface LedgerChange<T> {
  readonly result: T;
  readonly counter?: 1 | -1;
  readonly grant?: GrantRecord | null;
  readonly claim?: ClaimOutcome | null;
}

export interface FoundersLedger {
  transact<T>(uid: string, decide: (state: LedgerState) => LedgerChange<T>): Promise<T>;
}

interface GrantDependencies {
  readonly ledger: FoundersLedger;
  readonly now: () => number;
  /** null when the account does not exist. */
  readonly accountCreatedAtMillis: (uid: string) => Promise<number | null>;
  readonly grant: (uid: string, expiresAtMillis: number) => Promise<StoreOutcome>;
}

type GrantResult = ClaimOutcome | 'uncertain' | 'skipped';

type ClaimAction =
  | { readonly kind: 'stop'; readonly result: GrantResult }
  | { readonly kind: 'wait' }
  | { readonly kind: 'grant'; readonly attempt: GrantRecord };

export async function processFoundersClaim(
  uid: string,
  claimId: string,
  deps: GrantDependencies,
): Promise<GrantResult> {
  const accountCreatedAtMillis = await deps.accountCreatedAtMillis(uid);
  const action = await deps.ledger.transact<ClaimAction>(uid, ({ claim, grant, promo }) => {
    const skip: ClaimAction = { kind: 'stop', result: 'skipped' };
    if (!claim || claim.id !== claimId) return { result: skip };
    if (accountCreatedAtMillis === null) return { result: skip, claim: null };
    if (claim.outcome) return { result: skip };

    const decision = decideReservation(promo, grant, claim.claimedAtMillis, accountCreatedAtMillis);
    const now = deps.now();
    if (decision === 'reserve') {
      const attempt: GrantRecord = {
        status: 'reserved',
        attempts: 1,
        expiresAtMillis: addCalendarMonths(now, promo!.durationMonths),
        leaseUntilMillis: now + GRANT_LEASE_MILLIS,
        possiblyGranted: false,
      };
      return { result: { kind: 'grant', attempt }, counter: 1, grant: attempt };
    }
    if (decision === 'resume' && grant) {
      if (grant.leaseUntilMillis > now) return { result: { kind: 'wait' } };
      if (grant.attempts >= MAX_GRANT_ATTEMPTS || grant.expiresAtMillis <= now) {
        // The last invocation lost its response or died before recording it. Keep the seat counted.
        return {
          result: { kind: 'stop', result: 'uncertain' },
          grant: { ...grant, status: 'uncertain', leaseUntilMillis: 0, possiblyGranted: true },
        };
      }
      const attempt: GrantRecord = {
        ...grant,
        attempts: grant.attempts + 1,
        leaseUntilMillis: now + GRANT_LEASE_MILLIS,
        possiblyGranted: grant.possiblyGranted || grant.leaseUntilMillis > 0,
      };
      return { result: { kind: 'grant', attempt }, grant: attempt };
    }
    switch (decision) {
      case 'granted':
      case 'failed':
      case 'closed':
      case 'full':
      case 'inactive':
      case 'notNew':
        return { result: { kind: 'stop', result: decision }, claim: decision };
      default:
        return { result: skip };
    }
  });

  if (action.kind === 'stop') return action.result;
  if (action.kind === 'wait' || action.attempt.leaseUntilMillis <= deps.now()) {
    throw new Error('Founders grant lease unavailable, will retry');
  }

  const outcome = await deps.grant(uid, action.attempt.expiresAtMillis);
  const result = await deps.ledger.transact<GrantResult | 'retry'>(uid, ({ claim, grant }) => {
    const attempt = action.attempt;
    if (!grant || grant.attempts !== attempt.attempts ||
        grant.leaseUntilMillis !== attempt.leaseUntilMillis) {
      return { result: 'skipped' };
    }
    if (grant.status === 'releasing') {
      // A delete arrived during the POST. Release owns the final state and any revocation.
      return outcome.possiblyApplied
        ? { result: 'skipped' }
        : {
          result: 'skipped',
          grant: { ...grant, leaseUntilMillis: 0, possiblyGranted: grant.possiblyGranted || outcome.ok },
        };
    }
    if (grant.status !== 'reserved' || claim?.id !== claimId) return { result: 'skipped' };

    if (outcome.ok) {
      return {
        result: 'granted',
        claim: 'granted',
        grant: { ...grant, status: 'granted', leaseUntilMillis: 0 },
      };
    }
    const possiblyGranted = grant.possiblyGranted || outcome.possiblyApplied;
    // A timed-out request could still be processing. Keep the lease through the same grace window
    // even if the failure was recorded just before a delete arrives.
    const leaseUntilMillis = outcome.possiblyApplied ? grant.leaseUntilMillis : 0;
    if (outcome.retryable && grant.attempts < MAX_GRANT_ATTEMPTS) {
      return {
        result: 'retry',
        grant: { ...grant, possiblyGranted, leaseUntilMillis },
      };
    }
    if (possiblyGranted) {
      return {
        result: 'uncertain',
        grant: { ...grant, status: 'uncertain', possiblyGranted, leaseUntilMillis },
      };
    }
    return {
      result: 'failed',
      claim: 'failed',
      grant: { ...grant, status: 'failed', leaseUntilMillis: 0 },
      counter: -1,
    };
  });

  if (result === 'retry') throw new Error(`RevenueCat grant returned ${outcome.status}, will retry`);
  return result;
}

interface ReleaseDependencies {
  readonly ledger: FoundersLedger;
  readonly now: () => number;
  readonly revoke: (uid: string) => Promise<StoreOutcome>;
}

/** Purge a deleted account's claim and return its seat after RevenueCat has revoked the gift. */
export async function processFoundersDeletion(
  uid: string,
  deps: ReleaseDependencies,
): Promise<boolean> {
  const awaitingRevoke = await deps.ledger.transact<GrantRecord | null>(uid, ({ claim, grant }) => {
    const removeClaim = claim ? { claim: null } as const : {};
    if (!grant || grant.status === 'failed') {
      return { ...removeClaim, result: null, grant: grant ? null : undefined };
    }
    if (grant.status === 'releasing') return { ...removeClaim, result: grant };
    const releasing: GrantRecord = {
      ...grant,
      status: 'releasing',
      possiblyGranted: grant.possiblyGranted || grant.status === 'granted',
    };
    return { ...removeClaim, result: releasing, grant: releasing };
  });

  if (!awaitingRevoke) return false;
  if (awaitingRevoke.leaseUntilMillis > deps.now()) {
    throw new Error('Founders deletion is waiting for the in-flight grant');
  }
  if (awaitingRevoke.possiblyGranted || awaitingRevoke.leaseUntilMillis > 0) {
    const outcome = await deps.revoke(uid);
    if (!outcome.ok) throw new Error(`RevenueCat revocation returned ${outcome.status}, will retry`);
  }

  return deps.ledger.transact(uid, ({ grant }) => {
    if (grant?.status !== 'releasing') return { result: false };
    return { result: true, grant: null, counter: -1 };
  });
}
