import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  addCalendarMonths,
  decideReservation,
  grantUrl,
  MAX_GRANT_ATTEMPTS,
  NETWORK_OUTCOME,
  outcomeFor,
  processFoundersClaim,
  processFoundersDeletion,
  revokeUrl,
  type FoundersLedger,
  type GrantRecord,
  type LedgerChange,
  type LedgerState,
  type PromoConfig,
  type StoreOutcome,
} from './foundersPromo';

/**
 * El testigo del cupo. [MONEY-FOUNDERS-PLUS-PROMO-001]
 *
 * ⛔ Existe porque la alternativa es probar el reparto creando cuentas reales y gastando plazas de
 * las 2.000, que es un recurso que no se puede devolver con un `git revert`.
 */

const OPEN: PromoConfig = {
  active: true,
  cap: 2000,
  granted: 0,
  durationMonths: 3,
  // 30-11-2026, final del día en Madrid (UTC+1 en noviembre) → 30-11 23:00Z.
  endsAtMillis: Date.UTC(2026, 10, 30, 23, 0, 0),
};

const BEFORE_CLOSE = Date.UTC(2026, 10, 25, 12, 0, 0);
const AFTER_CLOSE = Date.UTC(2026, 11, 1, 10, 0, 0);
const EXPIRY = Date.UTC(2027, 1, 25, 12);

function grantRecord(status: GrantRecord['status'], attempts = 1): GrantRecord {
  return { status, attempts, expiresAtMillis: EXPIRY, leaseUntilMillis: 0, possiblyGranted: false };
}

// ── El veredicto ────────────────────────────────────────────────────────────

test('reserva cuando la promo está viva, en fecha y con hueco', () => {
  assert.equal(decideReservation(OPEN, null, BEFORE_CLOSE), 'reserve');
});

test('no reserva sin documento de cupo — una promo sin configurar está apagada, no rota', () => {
  assert.equal(decideReservation(null, null, BEFORE_CLOSE), 'inactive');
});

test('no reserva con active false', () => {
  assert.equal(decideReservation({ ...OPEN, active: false }, null, BEFORE_CLOSE), 'inactive');
});

test('cierra el 30/11: un instante antes entra, un instante después no', () => {
  const lastMoment = OPEN.endsAtMillis! - 1;
  assert.equal(decideReservation(OPEN, null, lastMoment), 'reserve');
  assert.equal(decideReservation(OPEN, null, OPEN.endsAtMillis!), 'closed');
  assert.equal(decideReservation(OPEN, null, AFTER_CLOSE), 'closed');
});

test('la configuración incompleta o fuera de los límites de campaña falla cerrada', () => {
  for (const invalid of [
    { endsAtMillis: null },
    { endsAtMillis: Date.UTC(2026, 11, 1) },
    { cap: 2001 },
    { granted: -1 },
    { granted: NaN },
    { durationMonths: 4 },
  ]) {
    assert.equal(decideReservation({ ...OPEN, ...invalid }, null, BEFORE_CLOSE), 'inactive');
  }
});

test('la plaza 2000 entra y la 2001 no', () => {
  assert.equal(decideReservation({ ...OPEN, granted: 1999 }, null, BEFORE_CLOSE), 'reserve');
  assert.equal(decideReservation({ ...OPEN, granted: 2000 }, null, BEFORE_CLOSE), 'full');
  assert.equal(decideReservation({ ...OPEN, granted: 2001 }, null, BEFORE_CLOSE), 'full');
});

test('un grant concedido no se vuelve a conceder', () => {
  const grant: GrantRecord = grantRecord('granted');
  assert.equal(decideReservation(OPEN, grant, BEFORE_CLOSE), 'granted');
});

test('un grant reservado se REANUDA, no se lee como concedido', () => {
  const grant: GrantRecord = grantRecord('reserved');
  assert.equal(decideReservation(OPEN, grant, BEFORE_CLOSE), 'resume');
});

test('un grant reservado se reanuda aunque la captación ya haya cerrado', () => {
  // La plaza ya está contada: si el reintento se denegara, el contador se quedaría con una plaza
  // gastada y sin dueño.
  const grant: GrantRecord = grantRecord('reserved', 2);
  assert.equal(decideReservation(OPEN, grant, AFTER_CLOSE), 'resume');
  assert.equal(decideReservation({ ...OPEN, active: false }, grant, AFTER_CLOSE), 'resume');
  assert.equal(decideReservation({ ...OPEN, granted: 2000 }, grant, BEFORE_CLOSE), 'resume');
});

test('un grant fallido es terminal y no se reintenta solo', () => {
  const grant: GrantRecord = grantRecord('failed');
  assert.equal(decideReservation(OPEN, grant, BEFORE_CLOSE), 'failed');
});

// ── Los tres meses de calendario ────────────────────────────────────────────

test('tres meses de calendario conservan el día y la hora', () => {
  const from = Date.UTC(2026, 10, 20, 9, 30, 0); // 20-11-2026 09:30Z
  assert.equal(addCalendarMonths(from, 3), Date.UTC(2027, 1, 20, 9, 30, 0)); // 20-02-2027
});

test('el día se recorta al último del mes destino, no desborda al siguiente', () => {
  // 31-08 + 3 = 30-11, NO el 1-12. Es el caso que `setUTCMonth` se come en silencio.
  assert.equal(
    addCalendarMonths(Date.UTC(2026, 7, 31, 12, 0, 0), 3),
    Date.UTC(2026, 10, 30, 12, 0, 0),
  );
  // 30-11 + 3 = 28-02 en año normal.
  assert.equal(
    addCalendarMonths(Date.UTC(2026, 10, 30, 12, 0, 0), 3),
    Date.UTC(2027, 1, 28, 12, 0, 0),
  );
  // 29-11 + 3 = 28-02 también: el 29 tampoco existe en febrero de 2027.
  assert.equal(
    addCalendarMonths(Date.UTC(2026, 10, 29, 12, 0, 0), 3),
    Date.UTC(2027, 1, 28, 12, 0, 0),
  );
});

test('en año bisiesto el 29 de febrero sí existe', () => {
  assert.equal(
    addCalendarMonths(Date.UTC(2023, 10, 29, 12, 0, 0), 3),
    Date.UTC(2024, 1, 29, 12, 0, 0),
  );
});

test('tres meses cruzan el año sin perderse', () => {
  assert.equal(
    addCalendarMonths(Date.UTC(2026, 10, 20, 0, 0, 0), 3),
    Date.UTC(2027, 1, 20, 0, 0, 0),
  );
  assert.equal(
    addCalendarMonths(Date.UTC(2026, 11, 31, 23, 0, 0), 3),
    Date.UTC(2027, 2, 31, 23, 0, 0),
  );
});

// ── El endpoint ─────────────────────────────────────────────────────────────

test('la URL de concesión es la de la API v1 y escapa el uid', () => {
  assert.equal(
    grantUrl('demo-founder'),
    'https://api.revenuecat.com/v1/subscribers/demo-founder' +
      '/entitlements/plus/promotional',
  );
  assert.match(grantUrl('a/b'), /subscribers\/a%2Fb\//);
});

test('la URL de revocación apunta a revoke_promotionals', () => {
  assert.equal(
    revokeUrl('uid-1'),
    'https://api.revenuecat.com/v1/subscribers/uid-1/entitlements/plus/revoke_promotionals',
  );
});

// ── Qué se reintenta y qué no ───────────────────────────────────────────────

test('2xx es éxito y no se reintenta', () => {
  assert.deepEqual(outcomeFor(200), { ok: true, status: 200, retryable: false, possiblyApplied: false });
  assert.equal(outcomeFor(201).ok, true);
});

test('un 4xx NO se reintenta: es nuestro bug, no una avería', () => {
  for (const status of [400, 401, 403, 404]) {
    assert.deepEqual(outcomeFor(status), { ok: false, status, retryable: false, possiblyApplied: false });
  }
});

test('429 y 5xx sí se reintentan', () => {
  assert.equal(outcomeFor(429).retryable, true);
  assert.equal(outcomeFor(500).retryable, true);
  assert.equal(outcomeFor(503).retryable, true);
});

test('un fallo de red se reintenta y no finge un código', () => {
  assert.equal(NETWORK_OUTCOME.retryable, true);
  assert.equal(NETWORK_OUTCOME.ok, false);
  assert.equal(NETWORK_OUTCOME.status, 0);
});

class MemoryLedger implements FoundersLedger {
  state: LedgerState = {
    promo: OPEN,
    grant: null,
    claim: { id: 'claim-created-on-server', claimedAtMillis: BEFORE_CLOSE },
  };

  async transact<T>(_uid: string, decide: (state: LedgerState) => LedgerChange<T>): Promise<T> {
    const { promo, claim, grant } = this.state;
    const change = decide(this.state);
    this.state = {
      promo: promo && { ...promo, granted: promo.granted + (change.counter ?? 0) },
      claim: change.claim === null
        ? null
        : claim && { ...claim, outcome: change.claim ?? claim.outcome },
      grant: change.grant === undefined ? grant : change.grant,
    };
    return change.result;
  }
}

function foundersFixture() {
  const ledger = new MemoryLedger();
  const clock = { now: BEFORE_CLOSE };
  const expires: number[] = [];
  const user = { exists: true };
  let revokes = 0;
  return {
    ledger,
    clock,
    expires,
    user,
    revokes: () => revokes,
    claim: (
      store: () => Promise<StoreOutcome> = async () => outcomeFor(201),
      claimId = 'claim-created-on-server',
    ) => processFoundersClaim('founder', claimId, {
      ledger,
      now: () => clock.now,
      accountExists: async () => user.exists,
      grant: async (_uid, expiresAtMillis) => {
        expires.push(expiresAtMillis);
        return store();
      },
    }),
    release: (store: () => Promise<StoreOutcome> = async () => outcomeFor(200)) =>
      processFoundersDeletion('founder', {
        ledger,
        now: () => clock.now,
        revoke: async () => {
          revokes++;
          return store();
        },
      }),
  };
}

function pendingStore() {
  let entered!: () => void;
  let reply!: (outcome: StoreOutcome) => void;
  const started = new Promise<void>((resolve) => { entered = resolve; });
  const response = new Promise<StoreOutcome>((resolve) => { reply = resolve; });
  return {
    started,
    call: () => {
      entered();
      return response;
    },
    reply,
  };
}

test('duplicate deliveries reserve and call RevenueCat only once while the lease is active', async () => {
  const f = foundersFixture();
  const pending = pendingStore();
  const grant = f.claim(pending.call);
  await pending.started;
  await assert.rejects(f.claim(), /lease unavailable/);
  pending.reply(outcomeFor(201));
  assert.equal(await grant, 'granted');
  assert.equal(await f.claim(), 'skipped');
  assert.equal(f.ledger.state.claim?.outcome, 'granted');
  assert.equal(f.ledger.state.promo?.granted, 1);
  assert.deepEqual(f.expires, [EXPIRY]);
});

test('a known rejection refunds the seat exactly once and settles the claim', async () => {
  const f = foundersFixture();
  assert.equal(await f.claim(async () => outcomeFor(403)), 'failed');
  assert.equal(await f.claim(), 'skipped');
  assert.equal(f.ledger.state.claim?.outcome, 'failed');
  assert.equal(f.ledger.state.promo?.granted, 0);
  assert.equal(await f.release(), false);
  assert.equal(f.ledger.state.grant, null);
  assert.equal(f.ledger.state.claim, null);
  assert.equal(f.revokes(), 0);
});

test('retry uses original expiry and a permanent reject after an ambiguous result keeps the seat', async () => {
  const f = foundersFixture();
  await assert.rejects(f.claim(async () => NETWORK_OUTCOME), /will retry/);
  f.clock.now = AFTER_CLOSE;
  assert.equal(await f.claim(async () => outcomeFor(403)), 'uncertain');
  assert.deepEqual(f.expires, [EXPIRY, EXPIRY]);
  assert.equal(f.ledger.state.claim?.outcome, undefined);
  assert.equal(f.ledger.state.promo?.granted, 1);
  assert.equal(f.ledger.state.grant?.status, 'uncertain');
});

test('the last ambiguous attempt retains the seat and stops further automatic grant calls', async () => {
  const f = foundersFixture();
  for (let attempt = 1; attempt < MAX_GRANT_ATTEMPTS; attempt++) {
    await assert.rejects(f.claim(async () => outcomeFor(500)), /will retry/);
    f.clock.now += 61_000;
  }
  assert.equal(await f.claim(async () => outcomeFor(500)), 'uncertain');
  assert.equal(await f.claim(), 'skipped');
  assert.equal(f.expires.length, MAX_GRANT_ATTEMPTS);
  assert.equal(f.ledger.state.promo?.granted, 1);
});

test('cloud retries after a crashed invocation preserve the original expiry', async () => {
  const f = foundersFixture();
  await assert.rejects(f.claim(async () => { throw new Error('interrupted'); }));
  await assert.rejects(f.claim(), /lease unavailable/);
  f.clock.now += 61_000;
  assert.equal(await f.claim(), 'granted');
  assert.deepEqual(f.expires, [EXPIRY, EXPIRY]);
});

test('a timely claim can be delivered after closing and gets three months from reservation', async () => {
  const f = foundersFixture();
  f.clock.now = AFTER_CLOSE;
  assert.equal(await f.claim(), 'granted');
  assert.deepEqual(f.expires, [Date.UTC(2027, 2, 1, 10, 0, 0)]);
});

test('a late claim records closed without calling RevenueCat', async () => {
  const f = foundersFixture();
  f.ledger.state = {
    ...f.ledger.state,
    claim: { ...f.ledger.state.claim!, claimedAtMillis: AFTER_CLOSE },
  };
  assert.equal(await f.claim(), 'closed');
  assert.equal(f.ledger.state.claim?.outcome, 'closed');
  assert.deepEqual(f.expires, []);
});

test('deleted accounts and stale claim events cannot reserve a seat', async () => {
  const f = foundersFixture();
  assert.equal(await f.claim(undefined, 'stale-claim-event'), 'skipped');
  assert.ok(f.ledger.state.claim);
  f.user.exists = false;
  assert.equal(await f.claim(), 'skipped');
  assert.equal(f.ledger.state.claim, null);
  assert.equal(f.ledger.state.promo?.granted, 0);
  assert.deepEqual(f.expires, []);
});

test('delete during the grant waits to revoke before returning the seat', async () => {
  const f = foundersFixture();
  const pending = pendingStore();
  const grant = f.claim(pending.call);
  await pending.started;
  await assert.rejects(f.release(), /waiting for the in-flight grant/);
  assert.equal(f.ledger.state.claim, null);
  assert.equal(f.ledger.state.grant?.status, 'releasing');
  assert.equal(f.ledger.state.promo?.granted, 1);
  assert.equal(f.revokes(), 0);

  pending.reply(outcomeFor(201));
  assert.equal(await grant, 'skipped');
  await assert.rejects(f.release(async () => NETWORK_OUTCOME), /revocation returned/);
  assert.equal(f.ledger.state.grant?.status, 'releasing');
  assert.equal(f.ledger.state.promo?.granted, 1);
  assert.equal(await f.release(), true);
  assert.equal(await f.release(), false);
  assert.equal(f.ledger.state.grant, null);
  assert.equal(f.ledger.state.promo?.granted, 0);
  assert.equal(f.revokes(), 2);
});

test('delete during a definitively rejected grant returns its seat without revoking', async () => {
  const f = foundersFixture();
  const pending = pendingStore();
  const grant = f.claim(pending.call);
  await pending.started;
  await assert.rejects(f.release(), /waiting for the in-flight grant/);

  pending.reply(outcomeFor(403));
  assert.equal(await grant, 'skipped');
  assert.equal(await f.release(), true);
  assert.equal(f.ledger.state.promo?.granted, 0);
  assert.equal(f.ledger.state.grant, null);
  assert.equal(f.revokes(), 0);
});

test('delete just after an ambiguous grant response still waits out the lease', async () => {
  const f = foundersFixture();
  await assert.rejects(f.claim(async () => NETWORK_OUTCOME), /will retry/);
  await assert.rejects(f.release(), /waiting for the in-flight grant/);
  assert.equal(f.ledger.state.promo?.granted, 1);
  assert.equal(f.revokes(), 0);

  f.clock.now += 61_000;
  assert.equal(await f.release(), true);
  assert.equal(f.ledger.state.promo?.granted, 0);
  assert.equal(f.revokes(), 1);
});
