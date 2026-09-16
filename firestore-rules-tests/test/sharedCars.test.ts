import { assertFails, assertSucceeds, RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';
import { afterAll, beforeEach, describe, test } from 'vitest';
import { CAR, MEMBER, OUTSIDER, OWNER, seedGroup, testEnv } from './helpers';

let env: RulesTestEnvironment;
const as = (uid: string) => env.authenticatedContext(uid).firestore();
const NOW = () => Date.now();

beforeEach(async () => { env = env ?? (await testEnv()); await seedGroup(env); });
afterAll(async () => { await env?.cleanup(); });

describe('sharedCars · pertenencia e historial', () => {
  test('solo los miembros leen el coche', async () => {
    await assertSucceeds(getDoc(doc(as(MEMBER), `sharedCars/${CAR}`)));
    await assertFails(getDoc(doc(as(OUTSIDER), `sharedCars/${CAR}`)));
  });

  test('se entra con una invitación viva y no con una caducada', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'sharedCarInvites/VIVA'), {
        code: 'VIVA', carId: CAR, ownerUid: OWNER, claimedByUid: null, expiresAtMs: NOW() + 60_000,
      });
      await setDoc(doc(ctx.firestore(), 'sharedCarInvites/MUERTA'), {
        code: 'MUERTA', carId: CAR, ownerUid: OWNER, claimedByUid: null, expiresAtMs: NOW() - 1,
      });
    });
    await assertSucceeds(setDoc(doc(as(OUTSIDER), `sharedCars/${CAR}/members/${OUTSIDER}`),
      { uid: OUTSIDER, inviteCode: 'VIVA' }));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await deleteDoc(doc(ctx.firestore(), `sharedCars/${CAR}/members/${OUTSIDER}`));
    });
    await assertFails(setDoc(doc(as(OUTSIDER), `sharedCars/${CAR}/members/${OUTSIDER}`),
      { uid: OUTSIDER, inviteCode: 'MUERTA' }));
  });

  test('cada uno mantiene SU ficha y no la de otro', async () => {
    await assertSucceeds(updateDoc(doc(as(MEMBER), `sharedCars/${CAR}/members/${MEMBER}`),
      { uid: MEMBER, tokens: ['t'] }));
    await assertFails(updateDoc(doc(as(MEMBER), `sharedCars/${CAR}/members/${OWNER}`),
      { uid: OWNER, tokens: ['robado'] }));
  });

  // [PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001] y su hermano de expulsión.
  test('una sesión la borra su autor o el dueño, nadie más', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `sharedCars/${CAR}/sessions/s-nacho`),
        { sessionId: 's-nacho', authorUid: MEMBER });
    });
    await assertFails(deleteDoc(doc(as(OUTSIDER), `sharedCars/${CAR}/sessions/s-nacho`)));
    await assertSucceeds(deleteDoc(doc(as(MEMBER), `sharedCars/${CAR}/sessions/s-nacho`)));
  });

  test('salir es cosa de uno; enseñar la puerta, del dueño', async () => {
    await assertSucceeds(deleteDoc(doc(as(MEMBER), `sharedCars/${CAR}/members/${MEMBER}`)));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `sharedCars/${CAR}/members/${MEMBER}`), { uid: MEMBER });
    });
    await assertSucceeds(deleteDoc(doc(as(OWNER), `sharedCars/${CAR}/members/${MEMBER}`)));
  });
});

describe('sharedCarInvites · el código ES el secreto', () => {
  test('se lee de uno en uno y no se puede listar', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'sharedCarInvites/ABC123'), {
        code: 'ABC123', carId: CAR, ownerUid: OWNER, claimedByUid: null, expiresAtMs: NOW() + 60_000,
      });
    });
    await assertSucceeds(getDoc(doc(as(OUTSIDER), 'sharedCarInvites/ABC123')));
    const { getDocs, collection } = await import('firebase/firestore');
    await assertFails(getDocs(collection(as(OUTSIDER), 'sharedCarInvites')));
  });

  test('un código se reclama UNA vez', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'sharedCarInvites/UNA'), {
        code: 'UNA', carId: CAR, ownerUid: OWNER, claimedByUid: null, expiresAtMs: NOW() + 60_000,
      });
    });
    await assertSucceeds(updateDoc(doc(as(OUTSIDER), 'sharedCarInvites/UNA'), { claimedByUid: OUTSIDER }));
    await assertFails(updateDoc(doc(as(MEMBER), 'sharedCarInvites/UNA'), { claimedByUid: MEMBER }));
  });
});
