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

test('announces the departure, naming whoever had parked it', () => {
  // [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001] The other half of the news, and the moment the rest
  // of the group has to take their shared fence off a spot the car has vacated.
  const v = verdictFor(state(), state({ isParked: false }));
  assert.equal(v.kind, 'announce-left');
  if (v.kind !== 'announce-left') return;
  assert.equal(v.authorUid, 'marta');
  assert.equal(v.authorName, 'Marta');
});

test('should_notReAnnounceTheDeparture_when_theDocumentIsWrittenAgain', () => {
  // A car nobody has moved since yesterday RESTS at isParked:false. Any later write of the
  // document — a name change, a late sync — must not say it just left.
  const parked = state({ isParked: false });
  assert.deepEqual(verdictFor(parked, parked), { kind: 'skip', reason: 'not-parked' });
});

test('should_nameTheDriverAndSpareThem_when_anotherMemberTookTheCar', () => {
  // [SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001] Antonio se lleva el coche que aparcó Marta.
  // Leer `authorUid` aquí nombraría a Marta y le mandaría el aviso a Antonio, que es quien conduce.
  const after = state({ isParked: false, movedByUid: 'antonio', movedByName: 'Antonio' });
  const v = verdictFor(state(), after);
  assert.equal(v.kind, 'announce-left');
  if (v.kind !== 'announce-left') return;
  assert.equal(v.authorUid, 'antonio');
  assert.equal(v.authorName, 'Antonio');
  // Y el reparto excluye a quien conduce, no a quien aparcó.
  const members = [member({ uid: 'marta' }), member({ uid: 'antonio' })];
  assert.deepEqual(targetsFor(members, v.authorUid).map((m) => m.uid), ['marta']);
});

test('should_notLeakAStaleDriver_when_theTakeoverFieldsWereCleared', () => {
  // `setStateParked` limpia `movedBy` al aparcar de nuevo; si no lo hiciera, la siguiente salida
  // nombraría a quien condujo hace dos viajes.
  const after = state({ isParked: false, movedByUid: null, movedByName: null });
  const v = verdictFor(state(), after);
  assert.equal(v.kind === 'announce-left' && v.authorUid, 'marta');
});

test('should_notCallItADeparture_when_itIsAnotherParkingBeingReplaced', () => {
  const before = state({ sessionId: 's-1' });
  const after = state({ sessionId: 's-2', isParked: false });
  assert.deepEqual(verdictFor(before, after), { kind: 'skip', reason: 'another-parking' });
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
