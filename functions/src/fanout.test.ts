import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  distanceMeters,
  isDeadToken,
  targetsFor,
  verdictFor,
  type PublishedSpot,
  type ZoneAlertSub,
} from './fanout';

/**
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * The cases the ticket listed as "reasoned but untested". They are all negatives — a fan-out that
 * tells the wrong person is worse than one that tells nobody, because the wrong person cannot tell
 * it was wrong.
 */

const NOW = 1_700_000_000_000;

// Madrid. 0,001° of latitude ≈ 111 m.
const ZONE_LAT = 40.4168;
const ZONE_LON = -3.7038;

function sub(over: Partial<ZoneAlertSub> = {}): ZoneAlertSub {
  return {
    uid: 'me',
    zoneId: 'z-1',
    zoneName: 'Casa',
    latitude: ZONE_LAT,
    longitude: ZONE_LON,
    radiusMeters: 500,
    cells: ['ezjmgx'],
    tokens: ['token-a'],
    expiresAt: NOW + 86_400_000,
    ...over,
  };
}

function spot(over: Partial<PublishedSpot> = {}): PublishedSpot {
  return {
    id: 's-1',
    latitude: ZONE_LAT,
    longitude: ZONE_LON,
    geohash: 'ezjmgxabc',
    reportedBy: 'someone-else',
    status: 'CONFIRMED',
    expiresAt: NOW + 7_200_000,
    ...over,
  };
}

test('announces a confirmed spot and names its cell', () => {
  const verdict = verdictFor(spot(), NOW);
  assert.equal(verdict.kind, 'announce');
  if (verdict.kind !== 'announce') return;
  assert.equal(verdict.cell, 'ezjmgx');
  assert.equal(verdict.isProvisional, false);
});

test('announces a PROVISIONAL spot, flagged — it is what push unlocks', () => {
  const verdict = verdictFor(spot({ status: 'PROVISIONAL' }), NOW);
  assert.equal(verdict.kind, 'announce');
  if (verdict.kind !== 'announce') return;
  assert.equal(verdict.isProvisional, true);
});

test('skips a spot with no usable geohash — there is nothing to look up', () => {
  assert.deepEqual(verdictFor(spot({ geohash: undefined }), NOW), {
    kind: 'skip', reason: 'no-geohash',
  });
  assert.deepEqual(verdictFor(spot({ geohash: 'ez' }), NOW), {
    kind: 'skip', reason: 'no-geohash',
  });
});

test('skips a RETRACTED spot — the app itself stopped believing in it', () => {
  assert.deepEqual(verdictFor(spot({ status: 'RETRACTED' }), NOW), {
    kind: 'skip', reason: 'retracted',
  });
});

test('skips a spot that died before the trigger ran', () => {
  assert.deepEqual(verdictFor(spot({ expiresAt: NOW - 1 }), NOW), {
    kind: 'skip', reason: 'already-expired',
  });
});

// [SEC-A-SPOT-IS-NOT-A-MEGAPHONE-001] Este test afirmaba lo contrario: que una plaza sin TTL se
// anunciaba igual, por prudencia ante un campo que el cliente aun no ponia. Escrito a mano, ese
// campo es una plaza que no caduca nunca y un aviso en el movil de desconocidos, asi que la
// prudencia cambia de lado: sin caducidad valida no se anuncia.
test('a spot with no TTL at all is not announced', () => {
  assert.deepEqual(verdictFor(spot({ expiresAt: 0 }), NOW), {
    kind: 'skip', reason: 'already-expired',
  });
});

test('never tells you about the space you just left', () => {
  const mine = spot({ reportedBy: 'me' });
  assert.deepEqual(targetsFor(mine, [sub({ uid: 'me' })], NOW), []);
});

test('the cell overfetches, the circle decides', () => {
  // ~1,1 km north of a 500 m zone, same geohash prefix.
  const far = spot({ latitude: ZONE_LAT + 0.01 });
  assert.deepEqual(targetsFor(far, [sub()], NOW), []);

  // ~333 m north: inside.
  const near = spot({ latitude: ZONE_LAT + 0.003 });
  assert.equal(targetsFor(near, [sub()], NOW).length, 1);
});

test('skips a subscription nobody has refreshed', () => {
  assert.deepEqual(targetsFor(spot(), [sub({ expiresAt: NOW - 1 })], NOW), []);
});

test('skips a subscription with no device to tell', () => {
  assert.deepEqual(targetsFor(spot(), [sub({ tokens: [] })], NOW), []);
  assert.deepEqual(targetsFor(spot(), [sub({ tokens: [''] })], NOW), []);
});

test('two overlapping zones of two people both get told', () => {
  const targets = targetsFor(
    spot(),
    [sub({ uid: 'a', zoneId: 'home' }), sub({ uid: 'b', zoneId: 'work' })],
    NOW,
  );
  assert.deepEqual(targets.map((t) => t.uid), ['a', 'b']);
});

test('distance is metres, not degrees', () => {
  // One thousandth of a degree of latitude is ~111 m anywhere on Earth.
  const d = distanceMeters(ZONE_LAT, ZONE_LON, ZONE_LAT + 0.001, ZONE_LON);
  assert.ok(d > 105 && d < 118, `expected ~111 m, got ${d}`);
});

test('only a genuinely dead token is dropped', () => {
  assert.equal(isDeadToken('messaging/registration-token-not-registered'), true);
  assert.equal(isDeadToken('messaging/invalid-registration-token'), true);
  // A blip must never cost a real device its subscription.
  assert.equal(isDeadToken('messaging/server-unavailable'), false);
  assert.equal(isDeadToken(undefined), false);
});
