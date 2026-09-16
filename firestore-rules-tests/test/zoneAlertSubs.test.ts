import { assertFails, assertSucceeds, RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, setDoc, updateDoc } from 'firebase/firestore';
import { afterAll, beforeEach, describe, test } from 'vitest';
import { MEMBER, OUTSIDER, testEnv } from './helpers';

let env: RulesTestEnvironment;
const sub = (uid: string, id: string) => doc(env.authenticatedContext(uid).firestore(), `zoneAlertSubs/${id}`);
const row = (uid: string) => ({ uid, zoneId: 'z1', cells: ['ezjm'], tokens: ['t'], expiresAt: 9_999 });

beforeEach(async () => { env = env ?? (await testEnv()); await env.clearFirestore(); });
afterAll(async () => { await env?.cleanup(); });

describe('zoneAlertSubs', () => {
  test('cada uno escribe y borra la suya', async () => {
    await assertSucceeds(setDoc(sub(MEMBER, `${MEMBER}_z1`), row(MEMBER)));
    await assertSucceeds(updateDoc(sub(MEMBER, `${MEMBER}_z1`), { tokens: ['t2'] }));
    await assertSucceeds(deleteDoc(sub(MEMBER, `${MEMBER}_z1`)));
  });

  // [SEC-A-ZONE-ALERT-ROW-IS-ONLY-REWRITTEN-BY-ITS-OWNER-001] El id es `{uid}_{zoneId}` y el uid
  // no es un secreto: viaja en cada plaza como `reportedBy`.
  test('nadie puede reescribir encima la fila de otro', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `zoneAlertSubs/${MEMBER}_z1`), row(MEMBER));
    });
    await assertFails(setDoc(sub(OUTSIDER, `${MEMBER}_z1`), row(OUTSIDER)));
    await assertFails(deleteDoc(sub(OUTSIDER, `${MEMBER}_z1`)));
  });

  test('nadie puede leer la de otro ni barrer el índice', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `zoneAlertSubs/${MEMBER}_z1`), row(MEMBER));
    });
    const { getDoc } = await import('firebase/firestore');
    await assertFails(getDoc(sub(OUTSIDER, `${MEMBER}_z1`)));
  });
});
