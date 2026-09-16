/**
 * Las consultas de producción que necesitan índice compuesto, declaradas UNA vez.
 * [OPS-AN-INDEX-IS-VERIFIED-AGAINST-PRODUCTION-001]
 *
 * ⛔ El emulador de Firestore **no exige índices compuestos**: una consulta que en producción
 * revienta con FAILED_PRECONDITION pasa en verde en los tests de reglas y en los unitarios. Por eso
 * cada consulta que los necesita vive aquí con el índice que exige, `indexes.test.ts` comprueba que
 * ese índice está declarado en `firestore.indexes.json`, y `verify-prod-queries.ts` la ejecuta
 * contra la base REAL antes de dar un despliegue por bueno.
 *
 * Esto nace de haber desplegado un `where(igualdad) + where(rango)` sin su índice: la función habría
 * fallado antes de repartir y las alertas de zona habrían dejado de llegar sin que nadie lo notara.
 */
export interface RequiredIndex {
  readonly collectionGroup: string;
  readonly fields: ReadonlyArray<{ fieldPath: string; order: 'ASCENDING' | 'DESCENDING' }>;
}

/** Plazas vivas de un autor — el tope anti-megáfono de `onSpotPublished`. */
export const LIVE_SPOTS_BY_AUTHOR: RequiredIndex = {
  collectionGroup: 'spots',
  fields: [
    { fieldPath: 'reportedBy', order: 'ASCENDING' },
    { fieldPath: 'expiresAt', order: 'ASCENDING' },
  ],
};

/**
 * Todas las que hay que verificar. Una consulta de un solo campo o de `array-contains` sola NO
 * necesita índice compuesto y por eso no está aquí — pero sí se ejecuta en `verify-prod-queries`,
 * porque lo que se comprueba allí es que la consulta CORRE, no que tenga índice.
 */
export const REQUIRED_INDEXES: ReadonlyArray<RequiredIndex> = [LIVE_SPOTS_BY_AUTHOR];
