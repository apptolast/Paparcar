import { assertFails, assertSucceeds, RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, setDoc, updateDoc } from 'firebase/firestore';
import { afterAll, beforeEach, describe, test } from 'vitest';
import { MEMBER, OUTSIDER, testEnv } from './helpers';

let env: RulesTestEnvironment;
const NOW = () => Date.now();
const HOUR = 60 * 60 * 1000;
const spot = (uid: string, id = 's-1') => doc(env.authenticatedContext(uid).firestore(), `spots/${id}`);

const body = (over: Record<string, unknown> = {}) => ({
  reportedBy: MEMBER, latitude: 40.4, longitude: -3.7, geohash: 'ezjmgr',
  status: 'CONFIRMED', expiresAt: NOW() + HOUR, ...over,
});

beforeEach(async () => { env = env ?? (await testEnv()); await env.clearFirestore(); });
afterAll(async () => { await env?.cleanup(); });

describe('spots', () => {
  test('publicar una plaza normal', async () => {
    await assertSucceeds(setDoc(spot(MEMBER), body()));
  });

  // [SEC-A-SPOT-IS-NOT-A-MEGAPHONE-001] Publicar dispara un aviso a desconocidos.
  test('sin caducidad no se publica', async () => {
    await assertFails(setDoc(spot(MEMBER), body({ expiresAt: 0 })));
  });

  test('una caducidad a un mes vista no se publica', async () => {
    await assertFails(setDoc(spot(MEMBER), body({ expiresAt: NOW() + 30 * 24 * HOUR })));
  });

  test('un estado que el cliente no escribe no se publica', async () => {
    await assertFails(setDoc(spot(MEMBER), body({ status: 'RETRACTED' })));
  });

  // [SEC-A-RELEASING-SPOT-IS-DENIED-AT-THE-DOOR-001] La plaza del embarque entra por CREATE —
  // es la primera escritura de su documento— y la lista blanca no la conocía: tres días sin que
  // saliera ni una, en silencio.
  test('la plaza de un embarque SÍ se publica', async () => {
    await assertSucceeds(setDoc(spot(MEMBER), body({ status: 'RELEASING' })));
  });

  test('la salida real promociona el MISMO documento, no publica otra plaza', async () => {
    await assertSucceeds(setDoc(spot(MEMBER), body({ status: 'RELEASING' })));
    await assertSucceeds(updateDoc(spot(MEMBER), { status: 'CONFIRMED' }));
  });

  test('el reintento del worker sobre una nominación ya escrita pasa', async () => {
    await assertSucceeds(setDoc(spot(MEMBER), body({ status: 'RELEASING' })));
    await assertSucceeds(setDoc(spot(MEMBER), body({ status: 'RELEASING' })));
  });

  test('firmada con el uid de otro no se publica', async () => {
    await assertFails(setDoc(spot(MEMBER), body({ reportedBy: OUTSIDER })));
  });

  // [SEC-AN-OWNER-CANNOT-MAKE-A-SPOT-IMMORTAL-001] `set()` sobre un doc existente evalúa UPDATE.
  test('el dueño republica dentro del techo', async () => {
    await assertSucceeds(setDoc(spot(MEMBER), body()));
    await assertSucceeds(setDoc(spot(MEMBER), body({ expiresAt: NOW() + 2 * HOUR })));
  });

  test('el dueño NO puede estirar la caducidad sin límite', async () => {
    await assertSucceeds(setDoc(spot(MEMBER), body()));
    await assertFails(setDoc(spot(MEMBER), body({ expiresAt: NOW() + 30 * 24 * HOUR })));
  });

  test('retractar es un update legítimo', async () => {
    await assertSucceeds(setDoc(spot(MEMBER), body()));
    await assertSucceeds(updateDoc(spot(MEMBER), { status: 'RETRACTED' }));
  });

  test('un tercero solo toca los contadores comunitarios', async () => {
    await assertSucceeds(setDoc(spot(MEMBER), body()));
    await assertSucceeds(updateDoc(spot(OUTSIDER), { enRouteCount: 3 }));
    await assertFails(updateDoc(spot(OUTSIDER), { latitude: 0.0 }));
  });

  test('cualquiera borra una plaza YA caducada, nadie una viva ajena', async () => {
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), 'spots/vieja'), body({ expiresAt: NOW() - 1 }));
    });
    await assertSucceeds(deleteDoc(spot(OUTSIDER, 'vieja')));
    await assertSucceeds(setDoc(spot(MEMBER, 'viva'), body()));
    await assertFails(deleteDoc(spot(OUTSIDER, 'viva')));
  });
});
