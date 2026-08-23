# DET-PRECEDENCE-MUST-BE-TESTABLE-001 · el orden de las ramas decide, y no lo fija ningún test

**Estado:** ✅ Done · mergeado a master por squash (2026-08-23) · 1.421 tests verdes ·
rama y worktree eliminados

Pasos **P0.1 y P0.2** del plan `docs/detection/10-plan-refactor.md`. **Solo `commonTest`: cero
líneas de producción.**

## Problema

El `collect` del `CoordinatorParkingDetector` evalúa ~10 ramas y **la primera que aplica gana**. Ese
orden es comportamiento puro: moverlo cambia qué pin se planta. Hoy:

1. **El orden no está escrito en ninguna parte ejecutable.** Es la posición física de los bloques
   dentro de un método de 700 líneas.
2. **El KDoc que lo documenta miente.** Su lista de nueve puntos dice que
   *«`userConfirmedParking` short-circuits everything»* (punto 4) y sitúa los aborts pre-conducción
   por encima. La realidad medida en el árbol de hoy:

   | # real | Rama | ¿En el KDoc? |
   |---|---|---|
   | 1 | **Resolución del hold** (`pendingConfirm != null`) | ❌ **no aparece** |
   | 2 | Abort por falso ENTER (pasos pre-conducción) | sí, como #1 |
   | 3 | Presupuesto de no-movimiento | sí, como #2 |
   | 4 | Atribución de vehículo | sí, como #3 |
   | 5 | Confirmación del usuario | sí, como #4 («short-circuits everything») |
   | 6 | Salto pre-conducción | sí, como #5 |
   | 7-10 | Timeout de respuesta · candidato · confirm rápido · puntuación | sí |

   La rama que va **primera** es precisamente la que el KDoc no menciona, y la que el KDoc declara
   soberana va **quinta**.
3. **Ningún test fija el orden.** Los que existen prueban cada rama por separado; ninguno construye
   un fix donde dos ramas sean aplicables a la vez y afirme cuál gana.

Consecuencia directa: **el refactor mueve esas ramas a una lista ordenada de etapas y hoy no hay red
que detecte una permutación.** Un despiste en el orden cambiaría la detección en silencio.

## Doctrina violada

Ninguna del dominio — no se cambia conducta. La que se incumple es la regla del propio refactor
(`09 §12.3`, `10 §0.3`): *ningún assert se edita durante F6*. Esa regla solo tiene fuerza si los
asserts que importan **existen antes**.

Y una segunda, del plan maestro: *ningún guard se toca sin demostración escrita*. El invariante
`[BUG-COORD-115]` («un tap del usuario siempre gana a un auto-confirm de la misma iteración») está
hoy demostrado por lectura, no por test.

## Señales / datos disponibles

Todo lo necesario está en el harness existente de `CoordinatorParkingDetectorTest`:

- `setup(config, clock, extraVehicles, defaultVehicleType)` → `TestEnv` con fakes.
- Reloj inyectable, así que las ramas por tiempo (hold, presupuesto) son deterministas.
- `FakeUserParkingRepository.saveNewParkingSessionCallCount` + `getActiveSession()` → el observable
  que discrimina: **qué se guardó, dónde y de qué coche**.
- `nominatingVehicleId` en `invoke(...)` → permite que el coche atribuido difiera del activo, que es
  la palanca para observar si la atribución corrió antes que el guardado.

## Diseño

Un fichero nuevo, `StagePrecedenceCharacterizationTest`, **separado** de
`CoordinatorParkingDetectorTest` a propósito: su trabajo no es probar una rama, es fijar **la
relación de orden entre dos**. Cuando F6 mueva las ramas a `stages/`, este fichero se convierte en
`StageOrderTest` sin reescribir sus asserts.

Cada test construye **un solo fix donde dos ramas son aplicables** y afirma cuál ganó.

### Regla de admisión: todo test debe ser discriminante

Un test de orden que pasaría con cualquier orden no vale nada — es exactamente el modo de fallo que
la auditoría registró como bug #8 (*tres tests que pasan con comentarios falsos*). Por eso **cada
test se verifica invirtiendo mentalmente el orden y comprobando que el assert cambia**, y el
comentario del test escribe qué valor daría el orden contrario.

Los pares que **no** se testean, y por qué:

| Par | Por qué no |
|---|---|
| hold ↔ abort por falso ENTER | **Inalcanzable**: abrir un hold exige conducción probada, y el abort exige que no la haya |
| hold ↔ zona por ancla de hueco | **Inalcanzable por diseño**: `DET-GAP-ANCHOR-001` impide que un ancla nacida en hueco abra un hold (con hueco hay prompt, no hold) |
| ramas 7-10 entre sí | Cubiertas por los replays de campo, que las cruzan de verdad |

## Criterio de éxito

- Los tests pasan contra master sin tocar producción.
- Cada uno **falla** si se permuta el par que fija (verificado a mano antes de cerrar).
- Cuando F6 mueva las ramas, estos asserts pasan **sin editarse**.

## Consumidores auditados

No aplica en el sentido habitual: no se cambia ningún invariante. El barrido relevante es el
inverso — qué tests existentes ya cubren estos pares, para no duplicar:

| Test existente | Qué cubre | ¿Fija el orden? |
|---|---|---|
| `should_save_with_user_reliability_when_user_confirms_during_the_hold` | Que un «sí» durante el hold guarda con fiabilidad 1.0 | **No** — la fiabilidad sería 1.0 con cualquiera de las dos ramas; le falta la posición |
| `should_abort_session_when_steps_burst_before_driving_speed` | El abort por falso ENTER aislado | No — sin «sí» del usuario compitiendo |
| `should_abort_after_maxNoMovement_without_driving` | El presupuesto aislado | No — ídem |
| `should_attribute_the_park_to_the_nominating_vehicle_not_the_active_one` | La atribución | No — la atribución ocurre en un fix anterior al del guardado |
| `should_finalize_tentative_confirm_after_hold_when_car_stays_put` | El hold que vence por reloj | No — no hay segunda rama aplicable |

## Resultado

`StagePrecedenceCharacterizationTest`, **5 tests**. Los cinco pasaron a la primera, así que se
verificó uno a uno neutralizando en local la rama que cada uno afirma que gana:

| Test | Par que fija | Neutralización aplicada | ¿Falla? |
|---|---|---|---|
| `should_plant_the_held_pin_not_the_answer_fix_when_the_user_says_yes_during_a_hold` | hold (1) ↔ user-confirm (5) | el hold planta `location` en vez de `pending.location` | ✅ **falla** |
| `should_abort_the_false_enter_even_when_the_user_already_said_yes` | falso ENTER (2) ↔ user-confirm (5) | `if (false && …)` en el abort | ✅ **falla** |
| `should_fold_the_no_movement_budget_even_when_the_user_already_said_yes` | presupuesto (3) ↔ user-confirm (5) | `if (false && …)` en el fold | ✅ **falla** |
| `should_resolve_the_vehicle_before_confirming_within_the_same_fix` | atribución (4) ↔ user-confirm (5), **mismo fix** | `if (false && …)` en la atribución | ✅ **falla** |
| `should_pin_the_final_spot_when_a_hold_is_discarded_mid_window` | — | 3 neutralizaciones distintas | ❌ **sigue verde** |

**El cuarto test es además el de P0.2**: la atribución resuelve y **no corta la iteración**, así que
el user-confirm de la misma pasada guarda con lo que aquella acaba de resolver. Es el fall-through,
demostrado.

### El quinto no discrimina, y por qué eso es un hallazgo

Se intentaron tres neutralizaciones y **ninguna lo hace fallar**:

1. `drivingResumed = false` → sigue verde, **con el mismo pin** (40.015, fiabilidad 0,9). El
   descarte por rancio al asentar toma el relevo y produce un desenlace idéntico.
2. Matar el fall-through del descarte por rancio → sigue verde.
3. Matar el fall-through del descarte por conducción reanudada → sigue verde. El aparcamiento real
   se confirma en un fix POSTERIOR, así que nada aquí depende de la continuación en la misma pasada.

La causa de (1) no es un defecto del test: **el descarte por conducción reanudada no emite ningún
`DetectionEvent`.** Es una de las ramas mudas del catálogo de `04-diagnostico.md`, así que **no
existe observable externo que lo separe de su hermano** — ni en un test ni en campo. Es la propuesta
3 de la arquitectura objetivo (las ramas mudas pasan a ser notas que emite el tap) la que lo haría
distinguible.

Por eso el test **se queda, reetiquetado como guard de regresión** —con el precedente de
`should_keep_the_exact_pin_when_user_confirms_over_a_witnessed_anchor` de
`DET-USER-YES-IS-NOT-A-COORDINATE-001`, que también pasa en ambos casos a propósito— y su KDoc
escribe las tres neutralizaciones para que nadie lo lea como prueba de lo que no prueba.

## Estado final

- ✅ **1.421 tests verdes** (`testProdDebugUnitTest`, `--rerun-tasks`), incluidos los 5 nuevos.
- ✅ **Cero líneas de producción**: `git diff master -- commonMain androidMain` vacío.
- Sin strings, sin pantallas, sin estados → no toca i18n ni Dev Catalog.
- Sin `detectionPath` ni `armEvidence` nuevos: no se detecta nada distinto.

## Notas

Este ticket **no arregla el KDoc mentiroso**. Corregirlo es tocar producción y el KDoc muere con el
fichero en F6 de todos modos; lo que hace falta ahora es la red, no la prosa. Queda anotado.

Pendiente de P0.3, que no depende de mí: transcribir los dos trayectos del 22-08 a `Trace_*`. Sigue
siendo lo que más sube el valor de esta red.
