# DET-PHYSICS-HONEST-ZONE-RADIUS-001 · P1.6 — un solo radio para el círculo honesto

**Estado:** ✅ Done (2026-08-24) · rama `refactor/DET-PHYSICS-HONEST-ZONE-RADIUS-001-p1-6` ·
worktree `../Paparcar-physics-6`

Paso **P1.6** de la Fase 1. Sigue a `cdd7c0b6` (P1.5).

## Qué mueve

Cuando la app sabe que el coche está aparcado pero no exactamente dónde, guarda un **área** en vez
de un punto engañosamente preciso. Este es el radio de esa área, y responde a dos testigos de la
misma duda:

- la **precisión** del centro — el fix puede estar desviado eso por sí solo;
- la cota que el llamador **midió** — metros andados desde que se selló el contador, o metros que una
  persona pudo cubrir dentro de un hueco de GPS.

Se queda con el mayor y luego lo acota entre un suelo y un techo. Por debajo del suelo un "área" es
un punto con pasos de más; por encima del techo pinta medio barrio y deja de significar nada. En el
techo el artefacto **se guarda igual** y el nudge pasa a ser la petición de afinar — perder la plaza
entera es la peor respuesta.

## Las dos formas eran la misma

```
CPD:     minOf(techo, maxOf(suelo, center.accuracy, duda.toFloat()))
EvalHC:  maxOf(abortFix.accuracy, andado).coerceIn(suelo, techo)
```

`EvalHC` ya había absorbido la precisión dentro de su `duda`, así que las dos son
`clamp(max(precisión, duda), suelo, techo)`. Hay un **barrido de >100.000 combinaciones** que compara
las dos escrituras, para que la afirmación sea medida y no dicha.

⚠️ **El orden de operaciones se conserva a propósito**: la duda se estrecha a `Float` ANTES de las
comparaciones, exactamente como lo hacía el coordinator, para que el resultado sea idéntico bit a bit
y no meramente parecido. Queda escrito en el KDoc que no se "limpie" a un `max` en dominio `Double`.

Detalle menor y anotado: `coerceIn` lanza si suelo > techo y la escritura del coordinator no; el
config ya lo impide con un `require`, así que nunca divergieron.

## Por qué este paso ya no era `C`

La adjudicación `09 §14.5` lo clasificaba como cambio de conducta porque el cierre honesto guardaba
zonas **sin techo** (bug #2). Dos commits posteriores lo arreglaron por otros motivos —`f42e393b`
metió el `coerceIn(suelo, techo)` y `8bf6f02b` extrajo `approximateZoneRadius`—, así que lo que
quedaba era **duplicación aritméticamente idéntica**. El plan ya lo había bajado a `M`; esto lo
confirma en el árbol.

**Un paso de riesgo menos de los que el checkpoint contaba.**

## Doctrina

Ninguna tocada. **Cero cambio de conducta**, con barrido además de suite.

## Tests

`HonestZoneRadiusTest` (6):

- el caso del 21-08 (206 pasos × 0,75 m = 154 m: la zona contiene el coche donde antes había una
  mentira de 3,6 m);
- suelo y techo, con el techo **guardando igual** en vez de perder la plaza;
- **el barrido de equivalencia** contra la escritura `coerceIn`;
- y la simetría de los dos testigos: ninguno puede encoger lo que el otro se ha ganado — un fix malo
  no tapa una caminata larga, y una caminata larga no tapa un fix malo.

**Criterio de aceptación de la Fase 1 cumplido: la suite pasa sin editar un solo assert.**

**1.500 tests** (1.494 + 6), 0 fallos. `assembleMockDebug` ✅.

## Red de P0.4

```
tests 1500 - desaparecidos vs base: 0 - nuevos: 46 (P1.1 a P1.6)
```

Quedan P1.7 a P1.10 para cerrar la Fase 1.
