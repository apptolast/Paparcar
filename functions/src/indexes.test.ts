import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { REQUIRED_INDEXES } from './queries';

// [OPS-AN-INDEX-IS-VERIFIED-AGAINST-PRODUCTION-001] Barato y tonto: que lo declarado en el código
// esté declarado también en el fichero que se despliega. No prueba que el índice EXISTA en
// producción -- eso solo lo sabe producción, y de eso va `verify-prod-queries.ts`.
test('cada consulta que necesita indice compuesto lo tiene declarado', () => {
  const declared = JSON.parse(readFileSync(join(__dirname, '..', '..', 'firestore.indexes.json'), 'utf8'));
  for (const required of REQUIRED_INDEXES) {
    const match = (declared.indexes ?? []).find((i: any) =>
      i.collectionGroup === required.collectionGroup &&
      required.fields.every((f, n) => i.fields?.[n]?.fieldPath === f.fieldPath && i.fields?.[n]?.order === f.order));
    assert.ok(
      match,
      `Falta en firestore.indexes.json el indice de ${required.collectionGroup}: ` +
      required.fields.map((f) => f.fieldPath).join(' + '),
    );
  }
});
