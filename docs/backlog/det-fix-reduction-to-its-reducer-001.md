# DET-FIX-REDUCTION-TO-ITS-REDUCER-001 · la otra mitad de la reducción del fix, a su reducer

**Estado:** ✅ Done — `FixReduction.kt` nuevo, coordinator −45 líneas, **cero tests existentes
editados** y 3 testigos nuevos verificados en rojo. Cero decisiones cambiadas.

## Problema

`updateStopTracking` se mudó a su propio fichero en P3.13 y quedó en 3 líneas de llamada. **Su
gemela, justo debajo, no.** El `updateAndGet { }` que decide qué probó el fix — los dos cruces
(`hasEverReachedDrivingSpeed`, `hasEverMoved`) y la prueba de conducción — seguía siendo un cuerpo
dentro del collector, con predicados y logs en línea. No se quedó por ninguna razón de diseño: se
quedó porque P3.13 paró ahí.

## Doctrina violada

La misma que la gemela [07 §4.2]: **un lambda de `update` es RETRYABLE por contrato** — se re-ejecuta
en contención del CAS — y éste hacía I/O: **cinco** llamadas a `PaparcarLogger`, todas describiendo
una TRANSICIÓN («→ true», «PROVEN by», «witnessed»). Bajo contención la traza podía anunciar dos
veces un cruce que ocurrió una.

## Diseño

`state/FixReduction.kt`, calcado de `StopTracking`: `DetectionSessionState.reduceFix(…)` →
`FixReduction(state, notes)`. Las cinco líneas vuelven como datos y el caller las dice UNA vez,
después del intento ganador. **El texto de las notas se MOVIÓ, no se retecleó** — misma regla que la
gemela, porque la suite no lee `parkdiag` y un desliz de transcripción es invisible hasta el día que
alguien lee una traza para explicar un viaje perdido.

### Una restricción que el movimiento disuelve

El KDoc de `DetectionSessionState.onFix` explicaba por qué la prueba de conducción se le pasa como
PARÁMETRO en vez de calcularla dentro: *«el coordinator necesita la prueba nueva para sus propios
logs de flanco… así que se calcula una vez, se loguea, y se entrega — en lugar de calcularse dos
veces y coincidir por suerte»*. Esa razón era real y **ya no existe**: la reducción que calcula la
prueba es la misma que emite las líneas. `onFix` conserva firma y significado (reglas 1 y 2,
indivisibles); simplemente tiene un solo caller ahora.

## Criterio de éxito

- Suite verde **sin editar un solo test existente** → ✅ **1.675 tests, 0 fallos**
  (1.672 + 3 nuevos), `compileMockDebugKotlinAndroid` verde bajo `-Werror`.
- Los 3 testigos nuevos, **verificados en rojo** (respaldando el fichero fuera de git antes de
  mutar, ver abajo):

| # | Mutación | Test que la caza |
|---|---|---|
| M1 | La nota deja de ser un flanco (cae el guard `!session.driveAuthorized`) | announce-each-crossing |
| M2 | Cae la puerta de credibilidad del fix [DET-SOLID-001] | refuse-a-crossing-too-vague |

`FixReductionTest` existe porque el movimiento **hace testeable algo que antes no lo era**: mientras
los logs salían desde dentro del lambda retryable, «¿cuántas veces se anunció esto?» no tenía
respuesta que un test pudiera leer. Ése es el motivo de que la mudanza no sea una limpieza.

## Números — y la corrección a lo que yo había estimado

| | Antes | Después |
|---|---|---|
| `collect { location -> … }` hasta `THE PRECEDENCE` | **159** | **115** |
| `CoordinatorParkingDetector.kt` | 1.378 | **1.333** |
| `FixReduction.kt` (nuevo) | — | 135 + 122 de test |

⚠️ Mi nota previa decía «~62 líneas» y «el `collect` pasa de 185 a ~70 con las dos». Ninguna de las
tres cifras era correcta: el bloque era de **159**, no 185, y esta mudanza le quita **44**, no 62.
La estimación venía de contar el bloque a ojo en vez de medirlo. Queda escrito para que la cifra de
la mudanza que falta (los marcadores de flanco al tap) se mida antes de prometerse.

## Consumidores auditados

`reduceFix` tiene un único call site (el collector). `DetectionSessionState.onFix` pasa de 2 callers
a 1 — el coordinator ya no lo llama, lo llama el reducer; `ReductionOrderTest` lo sigue ejerciendo
directamente y no se tocó. Tres imports del coordinator quedaron huérfanos al vaciarse el bloque
(`isCredibleFixAccuracy`, `DriveProof`, `DriveProofSource`) y se retiraron.

## Trampa de método, por si vuelve

Verificar en rojo con `sed` + `git checkout -- <fichero>` **borra tu propio trabajo** cuando el
fichero mutado es el que estás escribiendo (pasó en `DET-EXIT-LINE-COUNTS-NOTHING-001`). Aquí el
respaldo fue un `cp` al scratchpad y la restauración un `cp` de vuelta, verificada con `diff -q`.
