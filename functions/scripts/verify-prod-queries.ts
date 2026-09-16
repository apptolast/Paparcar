/**
 * Ejecuta las consultas de producción contra la base REAL y falla si alguna no puede correr.
 * [OPS-AN-INDEX-IS-VERIFIED-AGAINST-PRODUCTION-001]
 *
 * ⛔ Esto no lo puede sustituir ningún test: el emulador **no exige índices compuestos**, así que
 * una consulta sin índice pasa en verde en local y revienta con FAILED_PRECONDITION en producción,
 * tumbando el trigger entero. Pasó el 16-09 con el tope anti-megáfono: sin este script, lo que
 * habría avisado es que dejaran de llegar las alertas de zona.
 *
 * Uso: `npm --prefix functions run verify:queries` DESPUÉS de desplegar índices y funciones, y
 * DESPUÉS de que los índices estén READY — uno recién creado tarda, y hasta entonces falla igual.
 */
import { initializeApp, applicationDefault } from 'firebase-admin/app';
import { getFirestore } from 'firebase-admin/firestore';

initializeApp({ credential: applicationDefault() });
const db = getFirestore();
const now = Date.now();

/** Cada una con el nombre que aparecerá si falla: quien lea el error tiene que saber QUÉ se rompió. */
const QUERIES: ReadonlyArray<{ name: string; run: () => Promise<unknown> }> = [
  {
    name: 'onSpotPublished · plazas vivas por autor (indice compuesto reportedBy+expiresAt)',
    run: () => db.collection('spots')
      .where('reportedBy', '==', '__verificacion__')
      .where('expiresAt', '>', now)
      .count().get(),
  },
  {
    name: 'onSpotPublished · suscripciones de una celda (array-contains)',
    run: () => db.collection('zoneAlertSubs')
      .where('cells', 'array-contains', '__verificacion__')
      .limit(1).get(),
  },
  {
    name: 'onSharedCarParked · miembros de un coche',
    run: () => db.collection('sharedCars').doc('__verificacion__').collection('members').limit(1).get(),
  },
];

let broken = 0;
for (const query of QUERIES) {
  try {
    await query.run();
    console.log(`  OK   ${query.name}`);
  } catch (err) {
    broken += 1;
    console.error(`  ROTA ${query.name}`);
    console.error(`       ${err instanceof Error ? err.message : String(err)}`);
  }
}

if (broken > 0) {
  console.error(`\n${broken} consulta(s) de produccion no pueden correr. NO publiques el APK.`);
  process.exit(1);
}
console.log('\nTodas las consultas de produccion corren.');
