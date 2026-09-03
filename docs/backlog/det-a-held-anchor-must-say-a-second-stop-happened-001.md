# DET-A-HELD-ANCHOR-MUST-SAY-A-SECOND-STOP-HAPPENED-001 · un ancla mantenida tiene que decir que hubo una segunda parada

**Estado:** ✅ Done (03-09-2026) — mergeado a master por squash; el hash vive en `MEMORY.md`

## Problema

El caso de reaparcar — paras en A, rectificas, aparcas en B unos metros más allá, y el pin se queda
en A — **no deja una sola línea en la traza**. Ni en `parkdiag`, ni en Firestore.

Análisis completo del caso y de sus posibles arreglos:
[`det-a-repark-leaves-the-pin-at-the-first-stop-001.md`](det-a-repark-leaves-the-pin-at-the-first-stop-001.md).
Ese ticket **no** entra en 1.0 por decisión del user. Este solo instala la MEDICIÓN.

Ocurrió en el Redmi el 03-09-2026 y no se pudo mirar: el `parkdiag.log` local murió con la
desinstalación de la app y el gate remoto estaba apagado. Pero aunque hubiera habido log, **no
habría habido nada que leer**: el sistema atraviesa el caso en silencio.

Lo que sí se ve hoy en la traza son los efectos, y ninguno se puede atribuir a este caso:
`🔒 anchor FROZEN — ignoring walking-range speed …` durante el tramo A→B (indistinguible del veto
correcto a una caminata) y una confirmación silenciosa en A que se lee **exactamente igual** que un
aparcamiento correcto.

## Doctrina violada

Ninguna de detección — este cambio no decide nada. La que se estaba incumpliendo es la de
diagnóstico: *en diagnósticos hay que poder identificar SIEMPRE qué colocó cada pin*, y aquí no se
puede ni contar cuántas veces pasa ni con cuántos metros de error.

## Señales / datos disponibles

Todo lo que hace falta ya está en el estado en el instante en que la segunda parada se abre:

| señal | de dónde sale |
|---|---|
| la parada se ABRE en este fix | `s.anchorTrust.stopStartedAt == null` (mismo borde que ya usa `newStopGapMs`) |
| el ancla sigue clavada a OTRA parada | `pinnedToOtherStop` (ya calculado, `StopTracking.kt:182`) |
| nadie ha andado desde entonces | `s.egress.stepCount == 0` |
| a cuántos metros | `haversineMeters(anchor, location)` |

No hace falta estado nuevo.

## Diseño

Una nota de diagnóstico, y **nada más**, en la rama de fix parado de `reduceStopTracking`.

Las tres condiciones que la hacen inerte, cada una verificada contra el código antes de escribirla:

1. **`claim = null`.** `DiagnosticNote(text, claim)` tiene un campo que **sí es entrada de decisión**
   (`CoordinatorParkingDetector.kt:549` lee `notes.any { it.claim == NO_MOVEMENT_BUDGET_EXTENDED }`).
   El azúcar `notes += "…"` construye siempre `claim = null`, que es justo el contrato que pide el
   KDoc de `Claim`: *«a note gets a name only when a decision reads it»*.
2. **Edge-triggered, una vez por parada.** Sobre el fix que ABRE la parada, no sobre la madurez.
   Es el único borde que sobrevive a un stream con batching de OEM (el Redmi entrega fixes a 60-127 s:
   una ventana de 30 s puede no juntar nunca los 3 fixes del quórum de congelado) y no necesita
   estado nuevo. Sin borde, la condición es cierta en CADA fix de la parada y llena `parkdiag`
   (6 × 5 MB rotando), que es tanto como borrar el historial que se quiere leer.
3. **Calculada en la reducción, dicha por el caller.** Se acumula en `notes` y el caller la imprime
   una vez tras la reducción ganadora — la reducción puede reintentarse y no puede tener efectos.

Dos decisiones de forma que no son obvias:

- **`stepCount == 0` es parte de la condición, no un adorno.** Es lo que separa al conductor que
  sigue dentro del coche del peatón que hace una pausa camino de casa: sin ello, cada parada del
  paseo a casa dentro del radio dejaría una línea. Efecto colateral buscado: un ancla LOCKED (8
  pasos de egreso) nunca puede llegar a esta nota, solo una FROZEN (fin de conducción) — que es
  exactamente el caso del repark.
- **El techo es `config.egressBirthFloorMeters` (150 m), no una constante nueva.** No es
  reutilizar un número por parecido: por encima de él el sistema YA habla (`judgeEgressBirth` deja
  de llamar consistente al nacimiento → `BORN_AWAY`, más la maquinaria de walk-entered), y por
  debajo **no habla nadie**. Ese silencio es justo lo que la línea viene a rellenar, así que el
  límite de la línea es por definición el límite del silencio.

## Criterio de éxito

- La línea sale una vez, y solo una, en la forma del repark.
- No sale cuando hay pasos contados (peatón), ni cuando el ancla no está clavada, ni en los fixes
  siguientes de la misma parada.
- El estado devuelto por la reducción es **idéntico** con y sin la línea (mismo ancla, misma
  parada, mismos contadores): es una medición, no una decisión.
- En campo: la próxima vez que pase, la traza permite contar el caso y medir los metros de error.

## Consumidores auditados

`grep` de todo lo que podría convertir una nota en comportamiento:

| consumidor | qué hace | veredicto |
|---|---|---|
| `CoordinatorParkingDetector.kt:954` | `stopTracking.notes.forEach { PaparcarLogger.d(DIAG, it.text) }` | **cerrado** — solo imprime |
| `CoordinatorParkingDetector.kt:549` | `budgetVerdict.notes.any { it.claim == … }` | **cerrado** — lee `claim`, no texto; la nota nueva lo lleva `null`, y además es de otro productor (`StageVerdict`, no `StopTracking`) |
| `PaparcarLogger.d` → `FileAntilog` | `parkdiag.log`, 6 × 5 MB rotando | **cubierto** por el edge-trigger: una línea por parada |
| `FirestoreDetectionEventLogger` | escribe `DetectionEvent`, **no** notas | **exento** — una nota no cuesta cuota remota |
| `StopTrackingTest` | usa `notes` solo para el mensaje de fallo (`trace()`) | **cerrado** |
| `StageOrderTest:170`, `FixReductionTest:118` | `assertTrue(notes.isEmpty())` | **exentos** — otros productores (`StageVerdict`, `FixReduction`), no `StopTracking` |
| replays de campo (`Trace_*.kt`) | afirman outcome y posición del pin, no el texto de la traza | **cerrado** |

## Resultado (03-09-2026, sin commitear)

- `StopTracking.kt`: la nota + su import de `haversineMeters`. **+36 líneas, ninguna borrada.**
- `StopTrackingTest.kt`: 5 tests nuevos (la clase pasa de 24 a 29).
- `PARKING-DETECTION.md`: entrada en el log cronológico.
- Suite completa **2179/0** (195 clases) · `:app:compileProdDebugKotlin` y
  `:app:compileMockDebugKotlin` verdes.
- **Falsificado**: neutralizando la condición de la nota fallan 2 de los 5 tests nuevos
  (`should_report_a_second_stop_opening_near_the_anchor_it_holds` y
  `should_report_the_second_stop_once_and_not_again_within_the_same_stop`) — los otros 3 son
  negativos y pasan por construcción, que es justo lo que deben hacer. Sin la falsificación no
  habría prueba de que los asserts miran algo.

Checklist de `det-change`: doctrina intacta (no decide nada) · sin lógica en el service · barrido
arriba · tests verdes y falsificados · `PARKING-DETECTION.md` al día · **Dev Catalog N/A** (no hay
pantalla, estado MVI ni routing nuevos) · **strings N/A** (la nota es traza técnica, nunca la ve el
usuario, así que no va a los 9 locales) · **`detectionPath` N/A** (no hay camino de confirmación
nuevo).

## Fuera de alcance (a propósito)

- No se toca `effectiveDriving`, ni `AnchorTrust`, ni ningún umbral: el pin sigue quedándose en A.
  Eso es el otro ticket, y **no** va en 1.0.
- No se envía la línea a la traza remota. Elegir qué notas llegan a Firestore es una decisión con su
  propio presupuesto de escrituras [09 §7, P4.2], y esta nace en el log local.
