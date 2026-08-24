# DET-PHYSICS-DRIVE-CORROBORATION-001 · P1.4 — creer a la traza, no al fix

**Estado:** ✅ Done (2026-08-24) · rama `refactor/DET-PHYSICS-DRIVE-CORROBORATION-001-p1-4` ·
worktree `../Paparcar-physics-4`

Paso **P1.4** de la Fase 1 — el primero marcado **`L`** (riesgo mayor). Sigue a `29694c33` (P1.3) y
va sobre `21beb0d5` (los docs del refactor ya en el repo).

## Qué mueve

La familia de **corroboración por desplazamiento**: cuatro funciones que existen todas por lo mismo
— *un fix aislado no es evidencia*. Su `speed` es una afirmación, su `accuracy` es una afirmación, y
las hace con la misma seguridad cuando el receptor está perdido. Lo que no se puede falsificar es
**terreno realmente cubierto entre dos posiciones**.

Las tres escalas de tiempo se mantienen separadas a propósito:

| Función | Escala | Para qué |
|---|---|---|
| `isCorroboratedVehicleHop` | un fix al siguiente | donde el Doppler no se puede creer (banda muda), y donde una "parada" declara cero mientras se mueve |
| `sustainedDepartureFromAnchor` | desde la parada del ancla hasta ahora | descongela el ancla cuando el OEM mata la precisión de cada fix individual |
| `corroboratesDrive` | ventana de look-back acotada | la estadística de "conducción medida" de la sesión |

## El riesgo `L` no se materializó, y merece explicación

El plan marcaba este paso como `L` porque `isSustainedDepartureFromAnchor` **lleva un
`PaparcarLogger` dentro**, y avisaba: *«al extraerlo el log sube al caller… cambia el momento en que
se emite una línea de log — invisible en conducta, visible en un diff de `parkdiag`»*.

**No cambia.** La función pura devuelve **la medición** (`SustainedDeparture(distancia, ritmo)`) en
vez de un booleano, así que el adaptador loguea en el mismo instante y **con los mismos números**.
`parkdiag` sale byte a byte idéntico. Devolver la medida en vez de un `Boolean` es precisamente lo
que permite que la geometría sea pura sin que el log tenga que moverse ni reescribirse.

Era un riesgo real y bien identificado; simplemente tenía una salida que el plan no había visto.

## `DriveProofBounds` — el acoplamiento hecho explícito

`pruneRecentFixes` y `corroboratesDrive` comparten un invariante que el plan resumía como *«viaja
con su ventana o divergen»*: **el anillo tiene que guardar fixes al menos tan viejos como la ventana
mira hacia atrás**. Si alguien afina un número sin el otro, la ventana deja de encontrar nada contra
lo que mirar y **una conducción real deja de probarse a sí misma en silencio, sin error en ninguna
parte**.

Van los ocho parámetros en **un solo objeto**, así que ese acoplamiento pasa de ser algo que hay que
recordar a algo que hay que editar junto. Y tiene su test propio.

## Doctrina

Ninguna tocada. **Cero cambio de conducta**: las cuatro conservan su aritmética, sus umbrales y sus
early-returns en el mismo orden.

## Tests

`DriveCorroborationTest` (14), construidos sobre **las trazas contra las que estas funciones se
calibraron**, que son las que una simplificación posterior rompería:

- **Galeote 16-07 pasa** (23,7 m en 5 s contra 9,9 m de precisión conjunta — el coche rodando al
  bordillo). Si falla, la deceleración final vuelve a envenenar un ancla correcta.
- **El swing de recuperación de Camelias 15-07 falla** (11,9 m contra 14,1 m de ruido: sus sobres se
  hinchan justo cuando "se mueve"). Que falle es lo que hace imposible el lavado del arrastre a casa.
- **Enamorados 15-07**: 366 m en 30 s con precisión 52,4 — la traza se cree aunque ningún fix
  individual se pueda creer.
- **El espejismo de casa 27-07**: plano-y-salto. La cláusula de progreso es lo que lo distingue de
  una conducción real, y es el término que más fácil parece redundante al que venga a ordenar esto.
- **Gavia**: la conducción entera es UN salto de 36 s y 255 m sin testigos en ventana — un stream
  disperso tiene que poder probarse.
- Y el invariante del anillo: **guarda fixes tan viejos como la ventana mira**.

**Criterio de aceptación de la Fase 1 cumplido: la suite pasa sin editar un solo assert.**

**1.487 tests** (1.473 + 14), 0 fallos. `assembleMockDebug` ✅.

## Red de P0.4

```
tests 1487 - desaparecidos vs base: 0 - nuevos: 33 (P1.1 a P1.4)
```

Los 13 replays `Trace_*` siguen intactos, y son la verificación fuerte: `corroboratesDrive` e
`isCorroboratedVehicleHop` se ejercitan en cada traza de campo.
