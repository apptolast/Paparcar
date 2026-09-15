import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  targetsFor,
  verdictFor,
  type SharedCarMember,
  type SharedCarState,
} from './sharedCarFanout';

/**
 * [SHARED-A-PARKING-TELLS-THE-GROUP-001]
 *
 * Mostly negatives, for the same reason the zone fan-out's tests are: telling the wrong person is
 * worse than telling nobody, because the wrong person cannot tell that it was wrong. And a shared
 * car makes one of these mistakes personal — announcing the car parked to the very person who just
 * parked it reads as "somebody else has your car".
 */

function state(over: Partial<SharedCarState> = {}): SharedCarState {
  return {
    sessionId: 's-1',
    authorUid: 'marta',
    authorName: 'Marta',
    latitude: 40.4168,
    longitude: -3.7038,
    isParked: true,
    address: { street: 'Calle Mayor 3', city: 'Madrid' },
    ...over,
  };
}

function member(over: Partial<SharedCarMember> = {}): SharedCarMember {
  return { uid: 'antonio', displayName: 'Antonio', tokens: ['tok-a'], ...over };
}

test('announces when a member parks the shared car', () => {
  const v = verdictFor(undefined, state());
  assert.equal(v.kind, 'announce');
  if (v.kind !== 'announce') return;
  assert.equal(v.authorUid, 'marta');
  assert.equal(v.authorName, 'Marta');
  assert.equal(v.addressLine, 'Calle Mayor 3');
  assert.equal(v.sessionId, 's-1');
});

test('should_stayQuiet_when_theCarIsLeaving', () => {
  // The same document says "parked" and "gone". Announcing the second is exactly backwards.
  const v = verdictFor(state(), state({ isParked: false }));
  assert.deepEqual(v, { kind: 'skip', reason: 'not-parked' });
});

test('should_notAnnounceTwice_when_theGeocoderAnswersLate', () => {
  // The real shape of the second write: same parking, now with a street. This is the one that a
  // "did the doc change?" trigger gets wrong, and the user would see the car parked twice.
  const before = state({ address: null });
  const after = state({ address: { street: 'Calle Mayor 3', city: 'Madrid' } });
  assert.deepEqual(verdictFor(before, after), { kind: 'skip', reason: 'same-parking' });
});

test('announces the NEXT parking even though the previous one was also parked', () => {
  const before = state({ sessionId: 's-1' });
  const after = state({ sessionId: 's-2', authorUid: 'antonio', authorName: 'Antonio' });
  const v = verdictFor(before, after);
  assert.equal(v.kind, 'announce');
  if (v.kind !== 'announce') return;
  assert.equal(v.authorUid, 'antonio');
});

test('should_stayQuiet_when_thereIsNothingToPointAt', () => {
  assert.equal(verdictFor(undefined, state({ latitude: undefined })).kind, 'skip');
  assert.equal(verdictFor(undefined, state({ sessionId: undefined })).kind, 'skip');
  assert.equal(verdictFor(undefined, state({ authorUid: undefined })).kind, 'skip');
  assert.equal(verdictFor(undefined, undefined).kind, 'skip');
});

test('falls back to the city, and to nothing, without inventing a place', () => {
  const noStreet = verdictFor(undefined, state({ address: { street: '  ', city: 'Madrid' } }));
  assert.equal(noStreet.kind === 'announce' && noStreet.addressLine, 'Madrid');

  const noAddress = verdictFor(undefined, state({ address: null }));
  assert.equal(noAddress.kind === 'announce' && noAddress.addressLine, '');
});

test('should_excludeTheAuthor_when_pickingWhoHears', () => {
  const members = [member({ uid: 'marta' }), member({ uid: 'antonio' })];
  const targets = targetsFor(members, 'marta');
  assert.deepEqual(targets.map((m) => m.uid), ['antonio']);
});

test('should_skipMembers_when_theyHaveNoDevice', () => {
  // A member who joined on a phone that has since been wiped: the row is there, the tokens are not.
  const members = [member({ uid: 'antonio', tokens: [] }), member({ uid: 'lucia', tokens: undefined })];
  assert.deepEqual(targetsFor(members, 'marta'), []);
});
