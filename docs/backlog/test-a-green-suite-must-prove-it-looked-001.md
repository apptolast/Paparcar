# TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001 · una suite verde tiene que demostrar que miró

**Estado:** ✅ Done · mergeado en master. Barrido hecho sobre `fdba2b00`; master avanzó 3 commits
durante la tarea y la rama se rebasó sobre `e47240fd`, con la suite re-verificada DESPUÉS del rebase
(ninguno de los tres traía guardrails nuevos, así que la auditoría de más abajo sigue completa).

## Problema

Barrido completo de la suite (207 ficheros, 1830 `@Test`, 0 `@Ignore`, verde). El barrido no
buscaba fallos: buscaba tests que **pasan sin mirar nada**. Encontró cuatro cosas.

### 1 · Dieciséis prohibiciones sin testigo de población

El patrón que comparten seis ficheros de guardrail:

```kotlin
val violations = scope.files.filter { …paquete… }.filter { …regex… }
assertTrue(violations.isEmpty(), …)
```

Si la población que filtra el primer `.filter` se queda vacía —un rename de paquete, una carpeta
que se mueve, un módulo que se parte— `violations` es la lista vacía y el test pasa **para
siempre**, vigilando cero ficheros. La afirmación «no hay violaciones» y la afirmación «no hay
nada donde mirar» son indistinguibles desde fuera, y es la segunda la que se vuelve verdad sola.

No es hipotético en este repo: el árbol ya se ha movido entero dos veces desde que estos
guardrails se escribieron (`ARCH-HEALTH-001` partió `:app`/`:shared` y renombró el paquete raíz;
`DET-PACKAGE-CLUSTERS-001` reordenó los paquetes de detección). Cada uno de esos movimientos pudo
haber apagado un guardrail sin poner una sola línea en rojo.

Es además la MISMA lección que el proyecto ya ha aprendido tres veces por separado:

- `I18N-A-DEAD-KEY-PASSES-EVERY-PARITY-CHECK-001` — una key muerta pasaba todos los chequeos de
  paridad porque estar en los 9 locales no dice nada de que alguien la use.
- `DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001` — «`any { … }` no es un testigo».
- `UI-TYPE-SYSTEM-HYGIENE-001` — una allowlist sobre un componente MUERTO «no es una excepción, es
  un agujero».

Y el propio `StagePurityGuardrailTest` la tiene escrita en su KDoc: *«A guardrail that only forbids
is one refactor away from being satisfied by an empty package»* — pero solo puso el testigo en la
mitad de la propiedad (el ejecutor existe), no en la otra (el paquete de stages tiene ficheros).

**Sin testigo (16 tests / 6 ficheros):** `ArchitectureTest` (6) · `TypographyGuardrailTest` (4) ·
`ColorGuardrailTest` (3, vía el helper compartido `featureFiles()`) · `DividerGuardrailTest` (1) ·
`HomeSliceGuardrailTest` (1) · `StagePurityGuardrailTest` (1 de sus 2).

**Con testigo, y son el modelo:** `TriggerLaneGuardrailTest` (`.single { }` revienta si no está) ·
`HoldLaneGuardrailTest` (invertido: población vacía ⇒ todas las acciones «no emitidas» ⇒ rojo) ·
`FirestoreDeserializerParityTest` (`assertNotNull` sobre clase y función) ·
`StagePurityGuardrailTest` en su segundo test.

**Agravante:** la población «feature files» (commonMain ∩ (`presentation` ∪ `ui.components`)) está
copiada literal en tres ficheros —`ColorGuardrailTest`, `TypographyGuardrailTest`,
`DividerGuardrailTest`—, cada uno con su propia versión. Tres sitios donde arreglarla, que es
exactamente la forma que tiene un invariante de derivar.

### 2 · Un test cuyo nombre miente

`SplashViewModelTest.kt:121` — `isReady stays false for Authenticated until startRoute is resolved`
tiene el cuerpo **byte a byte idéntico** al de `isReady is false while auth state is Loading` de la
línea 108: construye el VM y asserta `assertFalse(vm.isReady)`. Nunca emite `AuthState.Authenticated`.
Cubre el caso `Loading`, que ya estaba cubierto, y deja sin cubrir el que promete: que un usuario
autenticado siga sin estar listo mientras `startRoute` no se resuelva.

### 3 · Un fake muerto

`commonTest/fakes/FakeParkingSyncScheduler.kt` (35 líneas): **cero usos** en toda la suite. El
`FakeParkingSyncScheduler` que sí se usa es otro y vive en producción, en
`commonMain/fakes/data/repository/FakeDetectionSources.kt:147`, para el flavor mock.

### 4 · Un caso de uso huérfano que el propio repo ya había sentenciado

`GetLastKnownLocationUseCase` no lo inyecta nadie: su única mención fuera de su fichero es el
registro Koin de `DomainModule.kt:84`. Historia medida en `git log`:

| Cuándo | Commit | Qué pasó |
|---|---|---|
| 2026-06-28 | `b41e8c6f` `[DET-READY-001]` | Nace con dos consumidores: el AR-proximity re-arm y el watchdog. |
| 2026-07-04 | `ebcd727b` `[DET-SOLID-001 C1b]` | *«purge the legacy AR-proximity arming path»* — muere el primero. |
| 2026-07-05 | `7a1dddc7` `[DET-SAFETY-NET-001]` | El fichero del segundo se va entero a `/dev/null`. |

Nadie quitó la clase ni su línea de Koin. La auditoría del refactor ya lo dictaminó por escrito:
`docs/detection/07-duplicacion.md` §P8 dice **«ELIMINAR»** y `09-arquitectura-objetivo.md` lo lista
bajo *ELIMINADOS*. `DET-AR-REARM-001` no se pierde con el borrado: su invariante vive en el guard
de frontera espacial de `SessionSupersede.kt`. No quedaba una decisión pendiente, quedaba una
ejecución pendiente.

### 5 · Lógica pura sin un solo test

| Qué | Tamaño | Menciones en test |
|---|---|---|
| `AnchorPredicates.kt` — 8 de sus 12 predicados | 270 líneas | `refinedParkLocation` 0 · `isAnchorPinned` 0 (12 usos en prod) · `isAnchorLocked` 0 · `isEgressBornAtAnchor` 0 · `sustainedDepartureFrom` 0 · `hasKinematicEgressSignal` 0 · `movementOutrunsSteps` 0 · `escapesAnchorEnvelope` 0 |
| `StopTracking.updateStopTracking` | 481 líneas, 34 ramas | 0 (solo indirecta, vía los 3 tests de `FixReductionTest`) |
| `CarbodyType.getParkingRules()` | 10 carrocerías, 3 umbrales | 0 |

Los ocho predicados del ancla son funciones puras sobre `DetectionSessionState` —testeables sin
ceremonia— y son exactamente la doctrina que más ha ardido en campo: *«el ancla se BLOQUEA con
pasos de egress o se CONGELA al final de la conducción, para que la caminata no arrastre el pin»*.
`refinedParkLocation` es literalmente la función que decide **dónde cae el pin**.

## Doctrina violada

- **CLAUDE.md · Testing** — «Toda UseCase nueva lleva test unitario». Los predicados del ancla no
  son casos de uso (son predicados, y ahí `DET-VERDICT-NOT-PREDICATE-001` dice bien que vivan como
  funciones puras), pero la razón por la que se les permite no ser casos de uso es precisamente que
  «sigue siendo directamente testeable, sin ceremonia de clase inyectada». Ocho de ellos cobraron la
  exención sin pagar el precio.
- **Sistemas, no parches** — la población «feature files» en tres copias.
- La lección de `I18N-A-DEAD-KEY-PASSES-EVERY-PARITY-CHECK-001`, `DET-THREE-EDGE-MARKERS-CANNOT-GO-SILENT-001`
  y `UI-TYPE-SYSTEM-HYGIENE-001`, que es una sola: **un chequeo que no puede distinguir «está bien»
  de «no hay nada» no es un chequeo.**

## Señales / datos disponibles

Todo el barrido es estático sobre el árbol en `fdba2b00`: censo de `@Test`, hash de cuerpos
normalizados para detectar duplicados exactos, conteo de referencias por símbolo entre
`commonMain`/`androidMain` y `commonTest`/`androidUnitTest`, y `git log -S` para la arqueología del
caso de uso huérfano.

## Diseño

**El testigo vive con la población, no con la prohibición.** Un helper que devuelve una población
vacía es el fallo; que lo detecte cada uno de los 16 tests por su cuenta sería el parche. Así que:

1. Un único sitio, `GuardrailScope.kt` en `architecture/`, que expone las poblaciones que los
   guardrails comparten (`featureFiles()`, `commonMainFiles()`, `filesInPackage(...)`,
   `filesUnderPath(...)`) y que **exige un mínimo** al construirlas: pedir una población y recibir
   menos ficheros de los declarados revienta ahí, con el nombre de la población y el número que
   esperaba. El guardrail que la usa no tiene que acordarse de nada.
2. Los tres ficheros que copiaban «feature files» pasan a pedirla. Deja de haber tres definiciones.
3. `ArchitectureTest` y `StagePurityGuardrailTest` piden sus poblaciones por el mismo camino.
4. Un test propio del helper, que **falsa** el mecanismo: pedir una población inexistente tiene que
   fallar. Un testigo sin ver fallar es otra vez el mismo bug, un nivel más arriba.

El resto es cobertura y limpieza, sin diseño que discutir: tests puros para los ocho predicados del
ancla, para `updateStopTracking` y para `getParkingRules`; borrado del fake y del caso de uso; y el
test de Splash pasa a emitir `Authenticated` de verdad.

## Criterio de éxito — MEDIDO

`:shared:testDebugUnitTest` verde con `--rerun-tasks`. **214 ficheros · 1940 `@Test`** (desde 207 /
1830). `:app:compileMockDebugKotlin` y `:app:compileProdDebugKotlin` verdes.
`grep FakeParkingSyncScheduler commonTest` → 0. `grep GetLastKnownLocationUseCase` (todo el repo) → 0.

### Falsación de los testigos — cada uno visto fallar

Un testigo que no se ha visto fallar es el mismo bug un nivel más arriba, así que ninguno se da por
bueno de palabra. Cuatro roturas deliberadas, revertidas después:

| Rotura | Simula | Tests que se pusieron ROJOS |
|---|---|---|
| Los 4 constantes de paquete → `com.rndeveloper.paparcar2.*` | el rename de `ARCH-HEALTH-001` | **14** — `ArchitectureTest` ×5, `ColorGuardrailTest` ×3, `TypographyGuardrailTest` ×4, `DividerGuardrailTest` ×1, y el meta-test de márgenes |
| `presentation.home.sections` → `…home.panels` | mover una carpeta | `HomeSliceGuardrailTest` |
| `/domain/detection/stages/` → `/…/pipeline/` | mover el paquete de stages | `StagePurityGuardrailTest` (regla 1) |
| `commonMain` → `sharedMain` en el helper | renombrar un source set | **10**, incluida la de `runBlocking` |
| Los tres fragmentos de source set de producción | lo mismo, para el guardrail de doctrina | `DetectionDoctrineGuardrailTest` ×3 |

Antes de este ticket, **ninguna** de esas roturas ponía en rojo un solo test: dejaban 16
prohibiciones pasando sobre listas vacías.

`GuardrailScopeTest` falsa además el mecanismo desde dentro: población inexistente por paquete, por
ruta, población encogida por debajo del suelo aunque no esté vacía, y `floor = 0` rechazado en la
puerta. Más un test de que el escaneo alcanza los DOS módulos, del que depende `PromptWindowGuardrailTest`.

### Falsación de los tests nuevos — por mutación de la producción

Nueve mutaciones, todas revertidas:

| Mutación | Tests muertos |
|---|---|
| `isAnchorPinned` pierde la mitad «frozen» | 2 (`…rest_froze_it…`, `…rest_of_a_pinned_anchor…`) |
| `escapesAnchorEnvelope` recupera el crédito de pasos | 1 (el test discriminador) |
| El techo peatonal usa el suelo estrecho | 1 (el test suelo-vs-techo) |
| `refinedParkLocation` deja de exigir pasos en el nacimiento | 1 |
| La refutación reinicia el RELOJ del stop, no solo la evidencia | 1 |
| El freeze deja de exigir conducción presenciada | 1 |
| Una muestra suelta a velocidad de viaje vuelve a desanclar | 1 |
| El egress cinemático deja de exigir la congelación | 1 |
| Se invierte la precedencia altura > anchura | 2 |

Y el test de Splash: romper la fila `Authenticated` de `isReady` a `-> true` lo pone rojo a él y a
nadie más. Con el cuerpo viejo esa misma rotura no fallaba nada, porque nunca autenticaba.

## Consumidores auditados

Barrido de TODA prohibición de la suite (`grep "violations.isEmpty()\|offenders.isEmpty()"`), y de
los guardrails que no usan Konsist:

| Fichero | Estado |
|---|---|
| `ArchitectureTest` (6) · `TypographyGuardrailTest` (4) · `ColorGuardrailTest` (3) · `DividerGuardrailTest` (1) · `HomeSliceGuardrailTest` (1) · `StagePurityGuardrailTest` (1) | ❌ sin testigo → **cerrados** vía `GuardrailScope` |
| `DetectionDoctrineGuardrailTest` (3) | ❌ sin testigo → **cerrado**. No estaba en el inventario inicial: aterrizó en `4e5ff00a`, dos días antes, con el punto ciego ya puesto |
| `TriggerLaneGuardrailTest` (2) | ✅ ya se autotestifica — `.single { }` revienta si el servicio no está, y `builders.size == 1` |
| `HoldLaneGuardrailTest` (2) | ✅ testigo INVERTIDO — población vacía ⇒ toda acción «no emitida» ⇒ rojo |
| `FirestoreDeserializerParityTest` (1) | ✅ `assertNotNull` sobre clase y función |
| `PromptWindowGuardrailTest` (3) | ✅ `.first { }` lanza si el adaptador no aparece |
| `LocaleParityGuardrailTest` (5) | ✅ testigo por construcción — `readText()` revienta si falta el `strings.xml` de cualquiera de los 9 locales, y `repoRoot` hace `error(...)` |
| `TypographyGuardrailTest` regla 1 (`DataTypography`) | ✅ vacía A PROPÓSITO: su población son los ficheros que citan una API borrada, y debe seguir vacía para siempre. No hay nada que testificar |

**Rectificación de alcance encontrada durante la ejecución:** al mover `HomeSliceGuardrailTest` a una
población genérica por paquete, la regla se ensanchó a todos los source sets y acusó a
`HomeSheetPreviews.kt` (androidMain). No era una violación: los `@Preview` construyen el estado
completo para derivar el slice que pintan, y la regla siempre estuvo acotada a `commonMain` a
propósito. Se corrigió el helper (`commonMainFilesInPackage`), no el fichero de previews. Esa
distinción quedó escrita en el KDoc para que no se vuelva a perder.

## Lo que el barrido descartó (para no volver a mirarlo)

- **0 tests con `@Ignore`.**
- **0 tests sin assert.** Los 5 candidatos (`should_not_emit_…`, `NavigateToHome NOT emitted…`) usan
  `expectNoEvents()` de Turbine, que sí asserta. Es un falso positivo del detector, no un hueco.
- `StagePrecedenceCharacterizationTest` vs `StageOrderTest` **no son duplicados**: el primero corre
  el detector de verdad fijando pares adyacentes, el segundo fija la lista declarada — y lo cita por
  nombre. Complementarios.
- Los 3 nombres repetidos entre `VehicleReconcileTest` y `ZoneReconcileTest` son el mismo invariante
  sobre repositorios distintos. Legítimo.
- Los 10 ViewModels tienen test. Los últimos commits traen el suyo.

## Fuera de alcance (follow-ups)

- `UserProfileRepositoryImpl` (72 líneas) y `DiagnosticsRepositoryImpl` (52) — únicos repos sin
  test.
- `SessionEpilogue`, `ParkedVehicleSummary`, `StepsSinceSeal`, `EffectOutcome`, `StagePass` — cero
  referencias en test, tamaño pequeño.
- `ObserveDetectionReliabilityUseCase`, `ClearParkNudgeUseCase`, `DeclareActiveVehicleUseCase` — sin
  fichero de test propio; hoy solo cubiertos de refilón desde otros.
