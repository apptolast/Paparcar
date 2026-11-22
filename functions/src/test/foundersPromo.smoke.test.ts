import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { test } from 'node:test';
import { deleteApp, getApp } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { getFirestore, Timestamp, type QueryDocumentSnapshot } from 'firebase-admin/firestore';
import { addCalendarMonths, FOUNDERS_CLOSE_MILLIS } from '../foundersPromo';

const PROJECT = 'demo-paparcar';
const DEMO_KEY = 'sk_founders-smoke-fixture';
const DATABASE = '(default)';
const REVENUECAT_ORIGIN = 'https://api.revenuecat.com';

function emulatorOrigin(variable: string): URL {
  const address = process.env[variable];
  assert.ok(address, `Set ${variable} by running the Firebase emulators for ${PROJECT}`);
  const url = new URL(`http://${address}`);
  assert.ok(['localhost', '127.0.0.1', '[::1]'].includes(url.hostname));
  assert.ok(url.port && !url.username && !url.password && url.pathname === '/' && !url.search && !url.hash);
  return url;
}

async function jsonResponse<T>(url: URL, init: RequestInit, expected: number): Promise<T> {
  const response = await fetch(url, init);
  assert.equal(response.status, expected, `Unexpected HTTP status for ${url.pathname}`);
  return (await response.json()) as T;
}

test('Founders smoke with local Auth, client rules, the Admin ledger and exported handlers', async (t) => {
  assert.equal(process.env.GCLOUD_PROJECT, PROJECT, 'Use emulators:exec with --project demo-paparcar');
  assert.ok(Date.now() < FOUNDERS_CLOSE_MILLIS, 'This smoke uses the live emulator clock and must run before the campaign deadline');

  const authOrigin = emulatorOrigin('FIREBASE_AUTH_EMULATOR_HOST');
  const firestoreOrigin = emulatorOrigin('FIRESTORE_EMULATOR_HOST');
  process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: PROJECT });
  process.env.REVENUECAT_SECRET_KEY = DEMO_KEY;

  const { grantFoundersPlus, releaseFoundersSlot } = await import('../index');
  const db = getFirestore();
  const auth = getAuth();
  t.after(async () => {
    await db.terminate();
    await deleteApp(getApp());
  });
  assert.equal(grantFoundersPlus.__endpoint.eventTrigger?.eventType, 'google.cloud.firestore.document.v1.created');
  assert.equal(releaseFoundersSlot.__endpoint.eventTrigger?.eventType, 'providers/firebase.auth/eventTypes/user.delete');

  const nativeFetch = globalThis.fetch;
  const storeCalls: string[] = [];
  let promotionalExpiry: number | undefined;
  let storePath: string | undefined;
  t.mock.method(globalThis, 'fetch', async (...args: Parameters<typeof fetch>) => {
    const [input, init] = args;
    const url = new URL(typeof input === 'string' || input instanceof URL ? input : input.url);
    if (url.origin !== REVENUECAT_ORIGIN) {
      assert.ok([authOrigin.origin, firestoreOrigin.origin].includes(url.origin), 'Unexpected external request');
      return nativeFetch(...args);
    }

    assert.equal(init?.method, 'POST');
    assert.equal(new Headers(init.headers).get('Authorization'), `Bearer ${DEMO_KEY}`);
    if (url.pathname === `${storePath}/promotional`) {
      assert.equal(typeof init.body, 'string');
      const request = JSON.parse(init.body as string) as { end_time_ms: number };
      assert.ok(Number.isSafeInteger(request.end_time_ms));
      promotionalExpiry = request.end_time_ms;
      storeCalls.push('grant');
      return new Response('{}', { status: 201 });
    }
    assert.equal(url.pathname, `${storePath}/revoke_promotionals`);
    assert.ok(promotionalExpiry, 'The revocation corresponds to an existing promotional grant');
    promotionalExpiry = undefined;
    storeCalls.push('revoke');
    return new Response('{}', { status: 200 });
  });

  const promo = db.doc('promos/founders');
  await promo.set({
    active: true,
    cap: 2_000,
    granted: 0,
    durationMonths: 3,
    // In the past: the Auth account this smoke creates below is new relative to it.
    startsAt: Timestamp.fromMillis(Date.now() - 60_000),
    endsAt: Timestamp.fromMillis(FOUNDERS_CLOSE_MILLIS),
  });

  const { localId: uid, idToken } = await jsonResponse<{ localId: string; idToken: string }>(
    new URL('/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key', authOrigin),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        email: `founders-smoke-${randomUUID()}@example.invalid`,
        password: randomUUID(),
        returnSecureToken: true,
      }),
    },
    200,
  );
  assert.ok(uid && idToken);
  const user = await auth.getUser(uid);
  const claim = db.doc(`foundersClaims/${uid}`);
  const grant = db.doc(`grants/${uid}`);
  storePath = `/v1/subscribers/${encodeURIComponent(uid)}/entitlements/plus`;

  assert.equal((await claim.get()).exists, false);
  assert.equal((await grant.get()).exists, false);
  assert.equal((await promo.get()).get('granted'), 0);
  assert.deepEqual(storeCalls, []);

  const databasePath = `projects/${PROJECT}/databases/${DATABASE}`;
  const documentRoot = `/v1/${databasePath}/documents`;
  const ownerHeaders = { Authorization: `Bearer ${idToken}`, 'Content-Type': 'application/json' };
  await jsonResponse(
    new URL(`${documentRoot}:commit`, firestoreOrigin),
    {
      method: 'POST',
      headers: ownerHeaders,
      body: JSON.stringify({
        writes: [{
          update: { name: `${databasePath}/documents/${claim.path}`, fields: {} },
          updateTransforms: [{ fieldPath: 'claimedAt', setToServerValue: 'REQUEST_TIME' }],
          currentDocument: { exists: false },
        }],
      }),
    },
    200,
  );

  const snapshot = await claim.get();
  assert.ok(snapshot.createTime);
  assert.ok(snapshot.get('claimedAt') instanceof Timestamp);
  const event: Parameters<typeof grantFoundersPlus.run>[0] = {
    specversion: '1.0',
    id: 'founders-smoke-claim',
    source: `//firestore.googleapis.com/${databasePath}`,
    subject: `documents/${claim.path}`,
    type: 'google.cloud.firestore.document.v1.created',
    time: snapshot.createTime.toDate().toISOString(),
    location: 'europe-west1',
    project: PROJECT,
    database: DATABASE,
    namespace: DATABASE,
    document: claim.path,
    params: { uid },
    data: snapshot as QueryDocumentSnapshot,
  };

  const beforeGrant = Date.now();
  await grantFoundersPlus.run(event);
  const afterGrant = Date.now();
  await grantFoundersPlus.run(event); // At-least-once redelivery.
  const recordedGrant = await grant.get();
  assert.equal(recordedGrant.get('status'), 'granted');
  assert.equal(recordedGrant.get('attempts'), 1);
  assert.equal(typeof promotionalExpiry, 'number');
  assert.ok(promotionalExpiry! >= addCalendarMonths(beforeGrant, 3));
  assert.ok(promotionalExpiry! <= addCalendarMonths(afterGrant, 3));
  assert.equal(recordedGrant.get('expiresAtMillis'), promotionalExpiry);
  assert.equal((await promo.get()).get('granted'), 1);
  assert.deepEqual(storeCalls, ['grant']);

  const receipt = await jsonResponse<{ fields: {
    outcome: { stringValue: string };
    expiresAtMillis: { integerValue: string };
  } }>(new URL(`${documentRoot}/${claim.path}`, firestoreOrigin), { headers: ownerHeaders }, 200);
  assert.equal(receipt.fields.outcome.stringValue, 'granted');
  assert.equal(Number(receipt.fields.expiresAtMillis.integerValue), promotionalExpiry);

  await jsonResponse(
    new URL('/identitytoolkit.googleapis.com/v1/accounts:delete?key=demo-key', authOrigin),
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken }),
    },
    200,
  );
  await assert.rejects(auth.getUser(uid), { code: 'auth/user-not-found' });

  const deleteContext = {
    eventId: 'founders-smoke-delete',
    eventType: 'providers/firebase.auth/eventTypes/user.delete',
    timestamp: new Date().toISOString(),
  };
  await releaseFoundersSlot.run(user, deleteContext);
  await releaseFoundersSlot.run(user, deleteContext);
  await grantFoundersPlus.run(event); // Delayed grant delivery after account deletion.

  assert.equal((await claim.get()).exists, false);
  assert.equal((await grant.get()).exists, false);
  assert.equal((await promo.get()).get('granted'), 0);
  assert.equal(promotionalExpiry, undefined);
  assert.deepEqual(storeCalls, ['grant', 'revoke']);
});
