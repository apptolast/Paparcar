import { assertFails, assertSucceeds, RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { deleteDoc, doc, setDoc, updateDoc } from 'firebase/firestore';
import { afterAll, beforeEach, describe, test } from 'vitest';
import { CAR, MEMBER, OUTSIDER, OWNER, seedGroup, testEnv } from './helpers';

let env: RulesTestEnvironment;
const state = (uid: string) => doc(env.authenticatedContext(uid).firestore(), `sharedCars/${CAR}/state/current`);

beforeEach(async () => { env = env ?? (await testEnv()); await seedGroup(env); });
afterAll(async () => { await env?.cleanup(); });

describe('sharedCars/{carId}/state/current', () => {
  // ── Lo que la app HACE y no puede dejar de poder hacer ──────────────────────────────────────
  test('putState: quien aparca firma su propia cabecera', async () => {
    await assertSucceeds(setDoc(state(MEMBER), {
      sessionId: 's-2', authorUid: MEMBER, authorName: 'Nacho',
      latitude: 41.0, longitude: -3.0, isParked: true, updatedAtMs: 2_000,
    }));
  });

  test('setStateTakenBy: otro del grupo se lo lleva', async () => {
    await assertSucceeds(updateDoc(state(OWNER), {
      isParked: false, movedByUid: OWNER, movedByName: 'Marta', updatedAtMs: 2_000,
    }));
  });

  test('setStateParked: el autor cierra su propia sesión', async () => {
    await assertSucceeds(updateDoc(state(MEMBER), {
      isParked: false, movedByUid: null, movedByName: null, updatedAtMs: 2_000,
    }));
  });

  test('clearMemberFromState: el autor borra su propio nombre al salir', async () => {
    await assertSucceeds(updateDoc(state(MEMBER), { authorName: null }));
  });

  // ── A2 · expulsar: lo hace el DUEÑO sobre el nombre de OTRO ─────────────────────────────────
  test('A2 · el dueño borra el nombre del expulsado', async () => {
    await assertSucceeds(updateDoc(state(OWNER), { authorName: null }));
  });

  // ── A1 · disolver el grupo borra la cabecera ────────────────────────────────────────────────
  test('A1 · el dueño puede borrar state/current al disolver', async () => {
    await assertSucceeds(deleteDoc(state(OWNER)));
  });

  // ── A3 · y lo que NO se puede hacer ─────────────────────────────────────────────────────────
  test('A3 · un miembro no puede firmar una cabecera como otro poniéndose en movedByUid', async () => {
    await assertFails(setDoc(state(MEMBER), {
      sessionId: 'falsa', authorUid: OWNER, authorName: 'Marta',
      latitude: 0.0, longitude: 0.0, isParked: true,
      movedByUid: MEMBER, updatedAtMs: 3_000,
    }));
  });

  test('un miembro no puede borrar la cabecera', async () => {
    await assertFails(deleteDoc(state(MEMBER)));
  });

  test('alguien de fuera del grupo no puede ni leerla ni escribirla', async () => {
    await assertFails(updateDoc(state(OUTSIDER), { authorName: null }));
  });
});
