import { initializeTestEnvironment, RulesTestEnvironment } from '@firebase/rules-unit-testing';
import { doc, setDoc } from 'firebase/firestore';

export const CAR = 'car-1';
export const OWNER = 'marta';
export const MEMBER = 'nacho';
export const OUTSIDER = 'laura';

export async function testEnv(): Promise<RulesTestEnvironment> {
  return initializeTestEnvironment({
    projectId: 'demo-paparcar',
    firestore: { host: '127.0.0.1', port: 8080 },
  });
}

/** El grupo ya montado, escrito SIN reglas: el sujeto del test es la escritura que viene después. */
export async function seedGroup(env: RulesTestEnvironment) {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, `sharedCars/${CAR}`), { carId: CAR, ownerUid: OWNER, displayName: 'Kamiq' });
    await setDoc(doc(db, `sharedCars/${CAR}/members/${OWNER}`), { uid: OWNER, displayName: 'Marta' });
    await setDoc(doc(db, `sharedCars/${CAR}/members/${MEMBER}`), { uid: MEMBER, displayName: 'Nacho' });
    await setDoc(doc(db, `sharedCars/${CAR}/state/current`), {
      sessionId: 's-1', authorUid: MEMBER, authorName: 'Nacho',
      latitude: 40.4, longitude: -3.7, isParked: true, updatedAtMs: 1_000,
    });
  });
}
