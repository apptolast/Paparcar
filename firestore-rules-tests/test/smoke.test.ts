import { initializeTestEnvironment, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc } from 'firebase/firestore';
import { test, expect } from 'vitest';

test('el emulador corre y las reglas deniegan lo no listado', async () => {
  const env = await initializeTestEnvironment({
    projectId: 'demo-paparcar',
    firestore: { host: '127.0.0.1', port: 8080 },
  });
  const anon = env.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(anon, 'coleccion_inexistente/x')));
  await env.cleanup();
  expect(true).toBe(true);
});
