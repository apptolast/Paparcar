# TEST-PROMOTED-HARNESS-LEAVES-NEWER-TESTS-BEHIND-001 · los 4 replays que master añadió después siguen hablándole al harness borrado

**Estado:** ✅ Done · plegado con `--squash` sobre **`feature/IOS-F0-001-fase0`** el 03-09-2026 (no sobre master: en master `DetectionTraceIngestion` no existe)

⛔ **Este ticket NO sale de master.** El harness promovido solo existe en
`feature/IOS-F0-001-fase0` (`377b10ff`), que es de donde nace esta rama y adonde vuelve. Mergearlo a
master no significa nada: en master `DetectionTraceIngestion` no existe.

## Problema

`feature/IOS-F0-001-fase0` promueve el harness de replay de `commonTest` a `commonMain`:

| Antes (master) | Después (rama) |
|---|---|
| `replay.DetectionTraceReplayer` | `ingestion.DetectionTraceIngestion` |
| `TraceEvent.Kind = {FIX, STEP, VEHICLE_EXIT, BICYCLE_ENTER}` | `Kind = {FIX, STEP, ACTIVITY}` + campo `activity: Activity?` |
| `replay(emitFix, emitStep, emitVehicleExit)` | `replay(emitFix, emitStep, emitActivity(activity, trueTimeMs))` |
| `nowMs` arranca en el PRIMER evento listado | `nowMs` arranca en el evento MÁS TEMPRANO (`minOf`) |

La rama portó los 15 traces que existían el 27-08. Master ha seguido añadiendo replays encima, y
esos siguen resolviendo contra el fichero borrado. Tras el rebase del 03-09 sobre `7f56981f`:
**20 errores de compilación en `DetectionTraceReplayTest.kt`**, en 4 tests.

⛔ **El rebase no da un solo conflicto.** Git no puede verlo: son ficheros nuevos que nadie tocó a
la vez. Es la tercera vez que este mismo mecanismo muerde (ver `docs/backlog/ios-f0-001.md`), y por
eso el criterio de "rebase limpio" no vale aquí — hay que compilar.

## Doctrina violada

Ninguna de detección: el algoritmo no cambia. Lo que se rompe es la red que lo protege —
`shared/src/commonTest` es donde viven los 16 replays de campo, y **un replay que no compila no
guarda nada**. Roza [[project_test_a_green_suite_must_prove_it_looked_001]]: una suite que no
ejecuta un escenario no lo está cubriendo, y aquí ni siquiera llega a ejecutarlo.

## Señales / datos disponibles

Los 4 tests, con el ticket cuyo veredicto guardan:

| Línea | Test | Guarda |
|---|---|---|
| 1300 | `parafarmacia_2908_one_doppler_sample_must_not_replace_a_good_pin` | `DET-LONE-SAMPLE-IS-NOT-A-DRIVE` (**abierto**) |
| 1397 | `casa_gap_anchor_3008_the_zone_must_centre_on_the_rest_it_witnessed` | `DET-LONE-SAMPLE-IS-NOT-A-DRIVE` (**abierto**) |
| 1530 | `camelias_oppo_001_an_unanswered_prompt_draws_the_walk_in_doubt_as_a_zone` | `DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT` |
| 1596 | `camelias_oppo_001_a_user_yes_keeps_that_same_doubt_as_a_zone` | `DET-A-USER-YES-DOES-NOT-SHRINK-A-WALK-ENTERED-DOUBT` |

Reparto de los 20 errores: 4 × `DetectionTraceReplayer` sin resolver, 8 × *"Suspension functions can
only be called within coroutine body"* (la firma de `replay` cambió), 2 × `it` sin resolver, 4 ×
`timestamp` + 2 × `Cannot infer type for 'fix'` — estos últimos son cascada de la inferencia rota,
no un problema aparte.

Las dos trazas que solo necesitaban el `import` ya se arreglaron en `377b10ff`. Lo que queda es
exactamente lo que **no** es mecánico.

## Diseño

Regla de este ticket: **el port es una traducción de API, no una revisión de aserciones.** Si algún
test solo pasa relajando una aserción → no se relaja: se para y se anota como hallazgo de
`DET-LONE-SAMPLE-IS-NOT-A-DRIVE`, que sigue abierto. **No hizo falta.**

### ⛔ El riesgo que se temía NO aplica a estos 4 — y conviene saber por qué

La preocupación al abrir era que la API nueva entrega la actividad con **su tiempo observado**
(`emitActivity(activity, trueTimeMs)`) mientras la vieja la juzgaba contra el reloj de entrega, y
que portar "hasta que compile" dejara verde un test que ya no afirma lo mismo. Leídos los 4:
**ninguno usaba `emitVehicleExit`**. Las transiciones de AR no entran por el harness — se inyectan
directamente en el coordinator con su tiempo verdadero explícito:

```kotlin
env.coordinator.onVehicleExit(CASA_GAP_3008_FIRST_EXIT_AT_MS)   // dentro de emitFix
```

O sea, estos tests ya llevaban el tiempo observado **a mano**, que es justo lo que la API nueva
sistematiza. La única diferencia semántica que sí les toca es que `nowMs` arranca ahora en el evento
más temprano (`minOf`) en vez de en el primero listado — y las 3 trazas implicadas están construidas
en orden, así que `first == min` y no cambia nada.

**Resultado: el port son 4 renombres de clase, cero cambios de lógica.** Los 20 errores eran uno
solo repetido: con `DetectionTraceReplayer` sin resolver, el tipo de `replayer` es erróneo y arrastra
la inferencia de los lambdas (`it`, `fix`, `timestamp`) y la detección de `suspend`.

📌 Lección para la próxima: **contar errores de compilación sobreestima el trabajo**. 20 errores en
4 tests parecían un port delicado; eran 4 líneas. Leer los tests antes de dimensionar.

### 🔴 Hallazgo: el error de compilación tapaba un fallo REAL

Con la suite ya ejecutable apareció un fallo que llevaba oculto desde el rebase — no lo causa este
ticket, lo **revela**:

```
KoinModuleVerifyTest > should_resolveEveryConstructorDependency_when_productionAndroidGraphIsAssembled
MissingKoinDefinitionException: Missing definition for '[field:'supportsBtStrategy' - type:'kotlin.Boolean']'
  in definition '[Singleton: 'DeviceCapabilities']'
```

El binding es correcto: `single { DeviceCapabilities(supportsBtStrategy = true, …) }` construye con
**literales**, porque los flags describen la plataforma y no hay nada que inyectar. Lo que falla es
`verify()`, que refleja el constructor sin ver dentro del lambda y lee el `Boolean` como dependencia
sin resolver — el mismo caso exacto que `Function0`/`Function1`, ya en la lista blanca del test.
Arreglado añadiendo `Boolean::class` a `extraTypes` con su comentario.

⚠️ **No debilita el guard**: un `Boolean` desnudo no puede ser un binding legítimo de Koin (nadie
escribe `single { true }`), así que detrás de esa entrada no puede esconderse ninguna definición
que falte de verdad. El testigo de población (`should_containTheDetectionRoots_…`) sigue intacto.

## Criterio de éxito — ✅ cumplido

- ✅ `./gradlew :shared:testDebugUnitTest` verde desde el worktree: **2178 tests, 0 fallos**
  (master, en su última corrida en disco, 2146 — la rama **añade** 32 tests, no pierde ninguno).
- ✅ Los 4 tests **ejecutados y verdes, comprobados por nombre en el XML de resultados**, no
  deducidos de un "BUILD SUCCESSFUL" — un test borrado también deja la suite verde
  ([[project_test_a_green_suite_must_prove_it_looked_001]]):
  ```
  OK  parafarmacia_2908_one_doppler_sample_must_not_replace_a_good_pin
  OK  casa_gap_anchor_3008_the_zone_must_centre_on_the_rest_it_witnessed
  OK  camelias_oppo_001_an_unanswered_prompt_draws_the_walk_in_doubt_as_a_zone
  OK  camelias_oppo_001_a_user_yes_keeps_that_same_doubt_as_a_zone
  ```
  (29 tests en `DetectionTraceReplayTest` en total)
- ✅ **Ni una aserción tocada.** El diff son 4 nombres de clase y una entrada en la lista blanca de
  `KoinModuleVerifyTest`. Ningún cuerpo de test, ningún número, ningún mensaje.
- ✅ `docs/backlog/ios-f0-001.md` ya no lista los 20 errores como pendientes: su sección del rebase
  del 03-09 queda cerrada y apuntando aquí.

## Consumidores auditados

```bash
grep -rn "DetectionTraceReplayer\|emitVehicleExit\|Kind\.VEHICLE_EXIT\|Kind\.BICYCLE_ENTER" \
  shared/src app/src tools --include=*.kt --include=*.py
```

**0 hits.** Clasificación:

| Consumidor | Estado |
|---|---|
| `DetectionTraceReplayTest.kt` | cerrado — 4 renombres |
| Las 17 trazas `Trace_*.kt` | cerrado — 15 las portó la rama, 2 en `377b10ff` |
| `tools/trace2fixture/trace2fixture.py` | **cerrado** — ya emite `Kind.ACTIVITY` + `activity=`; lo migró la rama. Era el que cierra el ciclo: si generase el modelo viejo, cada traza nueva volvería a nacer rota |
| `ActivityRecognitionQueryTest`, `Trace_Redmi2808RefutedStillness` | cerrados por la rama en el rebase del 21-08 |

## Fuera de alcance

- `iosMain` sigue sin compilar ni verificarse — es de la Mac, no de aquí
  ([[project_ios_commontest_never_compiled_001]]).
- El merge de `feature/IOS-F0-001-fase0` a master sigue bloqueado por decisión del user (14-08):
  antes toca APK de esa rama a campo. Este ticket no lo desbloquea, solo deja la rama verde.
