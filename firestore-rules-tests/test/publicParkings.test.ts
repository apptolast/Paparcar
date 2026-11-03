import { assertFails, assertSucceeds, RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, getDoc, setDoc, updateDoc } from 'firebase/firestore';
import { afterAll, beforeEach, describe, test } from 'vitest';
import { MEMBER, testEnv } from './helpers';

/**
 * La ocupación viva de los parkings públicos. [PARKINGS-LIVE-OCCUPANCY-001]
 *
 * Dos afirmaciones, y las dos tienen que ser EJECUTADAS y no leídas: que **cualquiera puede leerla
 * sin autenticarse** —porque quien la lee es una función anónima, y porque el dato es abierto— y
 * que **nadie puede escribirla desde un cliente**, ni siquiera autenticado, ni siquiera sobre un
 * documento que no existe.
 *
 * ⛔ La escritura es lo que importa: inventarse cuántas plazas quedan en un parking ajeno es
 * inyectar un dato que la app enseñaría **como oficial, con su licencia al lado**.
 */

let env: RulesTestEnvironment;

const KEY = 'madrid_3';
const asMember = () => doc(env.authenticatedContext(MEMBER).firestore(), `publicParkings/${KEY}`);
const asStranger = () => doc(env.unauthenticatedContext().firestore(), `publicParkings/${KEY}`);

const row = () => ({
  free: 360,
  total: 587,
  updatedAt: 1_762_000_000_000,
  source: 'madrid',
  name: 'Plaza de España',
  latitude: 40.4238,
  longitude: -3.7122,
  updatedAtIsFetch: false,
  licence: 'cc-by-4.0',
});

beforeEach(async () => {
  env = env ?? (await testEnv());
  await env.clearFirestore();
  // Lo siembra el admin, que es exactamente quien lo escribe en producción: `ingestOccupancy` usa
  // el SDK de admin y se salta estas reglas por definición.
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), `publicParkings/${KEY}`), row());
  });
});
afterAll(async () => { await env?.cleanup(); });

describe('publicParkings', () => {
  test('cualquiera la lee, incluso SIN autenticarse', async () => {
    // Quien la lee de verdad es `mergeCell`, que es anónima porque su respuesta la tiene que
    // cachear el CDN: si dependiera de quién pregunta, la función correría una vez POR USUARIO.
    await assertSucceeds(getDoc(asStranger()));
    await assertSucceeds(getDoc(asMember()));
  });

  test('⛔ nadie la escribe desde un cliente, ni autenticado', async () => {
    await assertFails(updateDoc(asMember(), { free: 0 }));
    await assertFails(setDoc(asMember(), row()));
    await assertFails(deleteDoc(asMember()));
  });

  test('⛔ ni sin autenticar, ni sobre un documento que no existe', async () => {
    await assertFails(setDoc(asStranger(), row()));
    const fresh = doc(env.authenticatedContext(MEMBER).firestore(), 'publicParkings/inventado_1');
    await assertFails(setDoc(fresh, row()));
  });
});
