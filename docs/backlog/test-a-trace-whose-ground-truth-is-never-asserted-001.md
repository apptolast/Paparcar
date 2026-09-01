# TEST-A-TRACE-WHOSE-GROUND-TRUTH-IS-NEVER-ASSERTED-001

> **Estado:** 📋 spec, sin arrancar y **sin rama**. Medido el 2026-09-01 sobre master `3adb08ae`.
> **Origen:** medición lateral al implementar
> `TEST-AN-ORPHANED-FIELD-TRACE-STILL-LOOKS-LIKE-COVERAGE-001`. Aquel cierra *«nadie reproduce este
> trace»*; éste es el escalón siguiente, y no se arregla con la misma regla.

---

## El defecto

Un replay puede correr entero y **no afirmar nada sobre dónde acabó el pin**. El trace declara su
ground-truth —dónde estaba el coche de verdad, dónde plantó el build de campo— y luego **nadie lee
esas constantes**. Lo que queda es un test que comprueba que la app no revienta con ese stream, con
la apariencia de un test que comprueba que acierta.

Es la misma familia que `DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001` (*«`any { … }` no es un
testigo»*): la diferencia entre *pasó por aquí* y *acertó aquí*.

## Lo medido (2026-09-01)

Seis constantes declaradas y **no referenciadas fuera de su propio fichero**:

| constante | fichero | dónde se la cita |
|---|---|---|
| `TraceCameliasOppo001.REAL_CAR_LAT` / `_LON` | `Trace_CameliasOppo001.kt` | en ningún sitio |
| `TraceCameliasOppo001.FIELD_PIN_LAT` / `_LON` | `Trace_CameliasOppo001.kt` | sólo en el KDoc de **otro** trace |
| `PARAFARMACIA_2908_FIELD_PIN_LAT` / `_LON` | `Trace_Parafarmacia2908.kt` | sólo en su propio KDoc |

⚠️ **No todas son el mismo caso, y por eso esto es una spec y no un barrido.** El test de
`camelias_oppo_001` afirma *0 pines y 1 pregunta* — que el coche no se pinte es su veredicto entero,
así que su ground-truth es CONTEXTO, no una aserción que falte. El de la parafarmacia es igual: el
veredicto es *0 pines*, y el pin de campo está ahí para que el lector sepa qué se evitó. **Puede que
las seis estén bien y lo que falte sea decirlo**; puede que alguna esconda un replay que no mira
dónde cae el pin. Hay que decidir caso por caso ANTES de escribir la regla.

⛔ No convertir esto en «toda constante de un trace debe usarse en un assert». Eso empujaría a
inventar aserciones para callar un guardarraíl, que es peor que la ausencia.

## Cómo se mide

```bash
# constantes de trace no referenciadas fuera de su fichero (excluye t0/T0 y listas componentes,
# que se usan DENTRO del propio fichero para construir el stream)
```
El script de la medición vive en el análisis del ticket hermano; se rehace en 20 líneas de Python
sobre `shared/src/**/*.kt`, con la misma cautela que allí: **cualificar los miembros de `object`** o
`REAL_CAR_LAT` casa con cualquier cosa.

## Decisión previa a implementar

1. Repasar las 6 y clasificar: *contexto legítimo* vs *aserción que falta*.
2. Si sobreviven ≥2 del segundo tipo → regla en `FieldTraceGuardrailTest`, con su falsación.
3. Si todas son contexto → **no hay guardarraíl**: la conclusión se escribe en el KDoc de los traces
   y este ticket se cierra refutado. Esa también es una respuesta.
