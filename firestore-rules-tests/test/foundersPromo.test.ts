import { assertFails, assertSucceeds, RulesTestEnvironment } from '@firebase/rules-unit-testing';
import {
  collection,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  serverTimestamp,
  setDoc,
  updateDoc,
} from 'firebase/firestore';
import { afterAll, beforeEach, describe, expect, test } from 'vitest';
import { MEMBER, OUTSIDER, testEnv } from './helpers';

/**
 * La promo «Fundadores» vista desde un cliente. [MONEY-FOUNDERS-PLUS-PROMO-001]
 *
 * ⛔ Existe porque la prohibición «`promos/**` y `grants/**` son del servidor» **no tenía testigo**.
 * El `smoke.test.ts` prueba el catch-all con un cliente SIN autenticar y una colección inventada,
 * que es la mitad fácil: lo que no cubría nadie es un usuario con sesión mirando el cupo o
 * escribiéndose su propio regalo. [TEST-RULES-ARE-EXECUTED-NOT-READ-001]
 */

let env: RulesTestEnvironment;

const asUser = (uid: string) => env.authenticatedContext(uid).firestore();
const claim = (uid: string, owner = uid) => doc(asUser(uid), `foundersClaims/${owner}`);
const promoPath = 'promos/founders';

async function setPromo(overrides: Record<string, unknown> = {}) {
  await env.withSecurityRulesDisabled((ctx) =>
    setDoc(doc(ctx.firestore(), promoPath), {
      active: true,
      cap: 2000,
      granted: 0,
      durationMonths: 3,
      endsAt: new Date(Date.now() + 60 * 60 * 1000),
      ...overrides,
    }),
  );
}

beforeEach(async () => {
  env = env ?? (await testEnv());
  await env.clearFirestore();
});
afterAll(async () => {
  await env?.cleanup();
});

describe('promos y grants son del servidor', () => {
  test('un usuario autenticado no puede leer el cupo ni cambiarlo', async () => {
    await setPromo({ granted: 7 });
    // Leerlo publicaría cuántas plazas quedan; escribirlo sería subirse el cupo.
    await assertFails(getDoc(doc(asUser(MEMBER), 'promos/founders')));
    await assertFails(updateDoc(doc(asUser(MEMBER), 'promos/founders'), { cap: 99_999 }));
    await assertFails(updateDoc(doc(asUser(MEMBER), 'promos/founders'), { granted: 0 }));
  });

  test('nadie se puede escribir su propio grant — ni leerlo', async () => {
    // Si esto pasara, cualquiera se regalaría Plus sin pasar por RevenueCat… salvo que no: el
    // entitlement vive en la tienda [MONEY-001]. Lo que SÍ conseguiría es falsear el cupo.
    await assertFails(
      setDoc(doc(asUser(MEMBER), `grants/${MEMBER}`), { status: 'granted', attempts: 1 }),
    );
    await env.withSecurityRulesDisabled(async (ctx) => {
      await setDoc(doc(ctx.firestore(), `grants/${MEMBER}`), { status: 'granted', attempts: 1 });
    });
    await assertFails(getDoc(doc(asUser(MEMBER), `grants/${MEMBER}`)));
    await assertFails(deleteDoc(doc(asUser(MEMBER), `grants/${MEMBER}`)));
  });
});

describe('apertura de la promo', () => {
  test('sin documento de configuración no se puede crear una solicitud', async () => {
    await assertFails(setDoc(claim(MEMBER), { claimedAt: serverTimestamp() }));
  });

  test.each([
    { active: false },
    { endsAt: new Date(0) },
    { endsAt: '2026-11-30T23:00:00Z' },
    { endsAt: null },
    { durationMonths: 12 },
    { cap: 2001 },
  ])('se rechaza una solicitud con configuración inactiva o inválida: %j', async (overrides) => {
    await setPromo(overrides);
    await assertFails(setDoc(claim(MEMBER), { claimedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(claim(MEMBER)));
  });
});

describe('foundersClaims', () => {
  beforeEach(() => setPromo());
  test('cada uno toca el suyo, con el sello del servidor', async () => {
    await assertSucceeds(setDoc(claim(MEMBER), { claimedAt: serverTimestamp() }));
    await assertSucceeds(getDoc(claim(MEMBER)));
  });

  test('sin sesión no se toca', async () => {
    const anon = env.unauthenticatedContext().firestore();
    await assertFails(setDoc(doc(anon, `foundersClaims/${MEMBER}`), { claimedAt: serverTimestamp() }));
  });

  test('nadie entra en la promo en nombre de otro', async () => {
    await assertFails(setDoc(claim(OUTSIDER, MEMBER), { claimedAt: serverTimestamp() }));
  });

  test('la hora la pone el SERVIDOR: un reloj movido no decide cuándo entraste', async () => {
    await assertFails(setDoc(claim(MEMBER), { claimedAt: new Date(2020, 0, 1) }));
    await assertFails(setDoc(claim(MEMBER), { claimedAt: 1_700_000_000_000 }));
  });

  test('es un timbre, no un buzón: ningún campo de más', async () => {
    await assertFails(
      setDoc(claim(MEMBER), { claimedAt: serverTimestamp(), outcome: 'granted' }),
    );
    await assertFails(
      setDoc(claim(MEMBER), { claimedAt: serverTimestamp(), durationMonths: 120 }),
    );
    await assertFails(setDoc(claim(MEMBER), {}));
  });

  // ⛔ El delete es el que importa: `grantFoundersPlus` dispara con `onDocumentCreated`, así que
  // borrar y volver a crear sería re-disparar la concesión a voluntad.
  test('una vez tocado no se modifica ni se borra', async () => {
    await assertSucceeds(setDoc(claim(MEMBER), { claimedAt: serverTimestamp() }));
    await assertFails(updateDoc(claim(MEMBER), { claimedAt: serverTimestamp() }));
    await assertFails(updateDoc(claim(MEMBER), { outcome: 'granted', expiresAtMillis: 1 }));
    await assertFails(setDoc(claim(MEMBER), { claimedAt: serverTimestamp() }));
    await assertFails(deleteDoc(claim(MEMBER)));
  });

  test('tras el cierre, sólo el dueño puede consultar el resultado escrito por el servidor', async () => {
    await assertSucceeds(setDoc(claim(MEMBER), { claimedAt: serverTimestamp() }));
    await env.withSecurityRulesDisabled(async (ctx) => {
      await updateDoc(doc(ctx.firestore(), `foundersClaims/${MEMBER}`), {
        outcome: 'granted',
        expiresAtMillis: 1_804_588_000_000,
      });
    });
    await setPromo({ active: false });
    const ownClaim = await assertSucceeds(getDoc(claim(MEMBER)));
    expect(ownClaim.data()).toMatchObject({ outcome: 'granted', expiresAtMillis: 1_804_588_000_000 });
    await assertFails(getDoc(claim(OUTSIDER, MEMBER)));
    await assertFails(getDocs(collection(asUser(MEMBER), 'foundersClaims')));
  });
});
