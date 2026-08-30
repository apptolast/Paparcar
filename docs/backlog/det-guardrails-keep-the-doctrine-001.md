# DET-GUARDRAILS-KEEP-THE-DOCTRINE-001 · sin esto, las seis piezas duran hasta el próximo fix con prisa

**Estado:** 🔵 En progreso · rama `feature/DET-GUARDRAILS-KEEP-THE-DOCTRINE-001-guardrails` ·
worktree `../Paparcar-guardrails` · apilada sobre `DET-DOUBT-MUST-REACH-THE-SCREEN-001`

Pieza 7 del rediseño.

## Problema

Las seis piezas anteriores arreglan invariantes. Nada impide volver a romperlos, y esta misma sesión
lo demuestra: **tres derivas encontradas a mano**, todas de la misma familia (una cadena que alguien
deletreó y que nadie podía contrastar contra nada).

## Diseño

`DetectionDoctrineGuardrailTest`, al estilo de `ColorGuardrailTest` / `TypographyGuardrailTest`.
Tres reglas, **y cada una está aquí porque cazó algo real**:

| regla | el caso que la justifica |
|---|---|
| **un literal de `detectionPath` tiene que ser un `DetectionPath` declarado** | `"vehicle-exit"` en el KDoc, en el fake del repositorio y en los datos de preview — una procedencia que producción **nunca** ha escrito; y `"steps=3 kinematicFixes=7"` en la galería mock, jerga de diagnóstico que no llega jamás a `detectionPath` |
| **ningún `setOf(LABEL_…)` decide la fuerza de un armado** | el FP de la parafarmacia: `enter_at_car` no estaba en la lista, luego era fuerte por omisión, luego pin silencioso a 0.9 con el usuario a pie |
| **ninguna decisión de detección se toma por prefijo de cadena** | `detectionPath.startsWith("bt")`: dos caracteres decidiendo cuál de las dos estrategias se le dice al usuario que puso el pin |

### Ya cazó una

Al primer arranque encontró un **segundo `"vehicle-exit"`** en `StateGalleryScreen.kt:529` que se me
había escapado en el barrido de `DET-DOUBT-MUST-REACH-THE-SCREEN-001`. El guardarraíl pagó su coste
antes de estar terminado.

### Y dos falsos positivos que enseñaron cómo escribirlo

1. **Se acusaba a sí mismo.** Cada regla documenta la forma que prohíbe **citándola**, así que al
   escanear el texto crudo señalaba `ParkingDetectionSource` y `EvaluateParkingDecisionUseCase` por
   código que sólo vive dentro del comentario que explica por qué se quitó. Ahora descuenta
   comentarios.
2. **`pathLabel` está sobrecargado.** `RecordPromptShown` construye `"low_medium(" + … + ")"`: eso es
   una nota de diagnóstico sobre por qué salió un prompt, no una procedencia que llegue a
   `UserParking`. La regla salta los literales que sólo son la primera pieza de una concatenación.

## Falsación — obligatoria en un test de PROHIBICIÓN

*Un test de prohibición sin verlo fallar es un test que siempre pasa.* Se metió una sonda temporal
con las tres violaciones y **los tres se pusieron rojos**; retirada, **los tres verdes**.

## Lo que este ticket NO hace, y por qué

### El `DrivingEvidenceGuardrailTest` de la pieza, tal como está enunciado, es FALSO

Pide *«enumerar todas las rutas que terminan en `ConfirmParkingUseCase` y afirmar que ninguna es
alcanzable con `DrivingEvidence` distinta de `Measured`»*. Esa propiedad **no es cierta**: `manual`,
`inherited_drive` y `verified_speed` pueden confirmar en silencio sin que ESTA sesión mida
conducción, cada uno por una razón escrita en `ArmEvidence`, y deben poder.

La regla verdadera es *un confirm silencioso exige `Measured` **o** un armado que traiga su propia
medición* — que es una propiedad de **una expresión**, ya cubierta con tests unitarios donde vive, y
no una afirmación de alcanzabilidad que un escaneo estático pueda hacer. **Escribir la versión falsa
como test verde sería peor que no tenerlo**, y queda dicho en el KDoc del fichero.

### Los replays de campo (punto 4 de la pieza)

`Trace_Parafarmacia2908` y `Trace_CasaGapAnchor3008` siguen pendientes. El material existe —el
`parkdiag` completo de la noche, 6.464 líneas— y el método está establecido
(`DET-2208-TRIPS-BECOME-REPLAYS-001`: cada aserción se verifica neutralizando su guard). Es
transcripción larga y merece su propio ticket, no la cola de éste.

## Criterio de éxito

- ✅ Tres reglas, cada una con su caso de campo escrito.
- ✅ **Falsadas las tres**: rojas con la sonda, verdes sin ella.
- ✅ **1.829 tests en verde**, prod y mock compilando.
