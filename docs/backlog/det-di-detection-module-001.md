# DET-DI-DETECTION-MODULE-001 · Detección no tenía módulo Koin en el lado común

**Estado:** ✅ Done · en master · 1.657 tests en verde sin editar un solo assert.
⏳ **Falta arrancar en device** — ningún test cubre la resolución de Koin en runtime (ver
*"Lo que ningún test cubre"*). Follow-ups: `DET-KOIN-MODULE-VERIFY-001`,
`DET-COORDINATOR-NO-OPTIONAL-DEPS-001`.

## Problema

Tres cosas distintas, todas de cableado, ninguna de conducta.

**1 · Un caso de uso construido a mano, fuera del grafo.** `CoordinatorParkingDetector.kt:188`:

```kotlin
private val evaluateUnattendedParkingSave: EvaluateUnattendedParkingSaveUseCase =
    EvaluateUnattendedParkingSaveUseCase(config),
```

`EvaluateUnattendedParkingSaveUseCase` **no está registrado en Koin en ningún sitio**. Es el único
`Evaluate*UseCase` de detección que no aparece en `domainModule`: ningún otro consumidor puede pedir
la misma instancia y no se ve en el grafo. Los otros once (`EvaluateBtPark`, `EvaluateArEnterArm`,
`EvaluateGeofenceExit`, `EvaluateSafetyNetCheck`, …) sí están.

**2 · Tres dependencias con default `= null`, y el KDoc dice para qué.** `phaseSink:182`,
`finalizeDeducedDeparture:194`, `retractDeducedDeparture:198`, todas con la misma justificación
escrita: *"Nullable so existing test doubles need no change"*. Es DI moldeada por los tests: un call
site nuevo puede omitir las tres sin que nada se queje, y en producción eso es una sesión que no
finaliza ni retracta su departure deducido — en silencio. Las tres se resuelven con `get()` en
producción; ninguna es opcional de verdad.

**3 · El lado común de detección no tiene módulo.** `androidDetectionModule` existe (116 líneas,
bien delimitado) y `iosDetectionModule` también. El commonMain no: sus ~25 registros viven dentro de
`domainModule` (295 líneas) mezclados con usuario, spots, zonas y location. Detección es el
inquilino mayoritario del fichero y el único área grande sin casa propia.

## Doctrina violada

- **⛔ Un caso de uso por VEREDICTO** (`DET-VERDICT-NOT-PREDICATE-001`) —
  `EvaluateUnattendedParkingSaveUseCase` **es** un veredicto (su `UnattendedSaveReason` se cita en
  diagnóstico) y está bien que sea un caso de uso; lo que está mal es que se construya a mano en el
  constructor de su consumidor en vez de vivir en el grafo como sus once hermanos.
- **⛔ Sistemas, no parches** — los tres `= null` son tres parches con la misma causa: adaptar la
  firma de producción a lo que los tests no querían escribir.

## Señales / datos disponibles

- `grep "EvaluateUnattendedParkingSaveUseCase"` → 0 registros en Koin, 1 construcción a mano.
- 3 sitios construyen el coordinator en tests (`CoordinatorParkingDetectorTest`,
  `DetectionTraceReplayTest`, `StagePrecedenceCharacterizationTest`), uno por fichero.
- 4 entry points arrancan Koin: `PaparcarApp` (android prod), `MockPaparcarApp` (android mock),
  `MainViewController` + `MockMainViewController` (iOS). **Los cuatro incluyen `domainModule`.**

## Diseño

### `detectionModule` en `commonMain/di/DetectionModule.kt`

Gemelo común de los dos que ya existen por plataforma. Se lleva de `domainModule` todo lo que
cumpla: **depende de `ParkingDetectionConfig`, de `GeofenceManager`, del `DepartureEventBus`, del
`DetectionRuntimeState` o de otro registro de detección — o su único consumidor es una superficie de
detección.** Lo demás (usuario, spots, zonas, location genérico, queries de UI como
`ObserveParkedVehiclesUseCase`) se queda donde está.

**`domainModule` lo agrega con `includes(detectionModule)`, no se lista en los 4 entry points.**
Es la decisión deliberada del ticket: listarlo cuatro veces hace que un quinto entry point futuro
pueda olvidarlo, y un binding de detección ausente no lo caza ningún test — el proyecto no tiene
verificación de módulos Koin. Con `includes()` es estructuralmente imposible olvidarlo, y el
fichero sigue siendo el único sitio donde se lee qué necesita detección. El invariante en UN sitio.

### Las tres nullables: se quita el DEFAULT, no el tipo

`phaseSink`, `finalizeDeducedDeparture` y `retractDeducedDeparture` pasan a **parámetros
obligatorios de tipo nullable**: sin `= null`. Producción sigue pasando `get()`; los tests pasan
`null` explícito, con el comentario de qué vía no ejercitan. Un call site nuevo ya no puede omitir
las tres por descuido, porque no compila.

**Por qué no se hacen no-nulables del todo**, que sería lo ideal: los 3 setups de test tendrían que
construir `FinalizeDeducedDepartureUseCase`/`RetractDeducedDepartureUseCase` reales sobre fakes, y
eso mete en 1.657 tests conducta que hoy no ejercitan — exactamente el riesgo de tocar asserts que
la Fase 3 de F6 cerró con cero. Queda como follow-up separado, no se cuela en un ticket de cableado.

### `EvaluateUnattendedParkingSaveUseCase`

`factory { EvaluateUnattendedParkingSaveUseCase(config = get()) }` en `detectionModule`; el
parámetro del coordinator pierde su valor por defecto y pasa a `get()` en DI y a
`EvaluateUnattendedParkingSaveUseCase(config)` explícito en los 3 tests — la misma instancia que se
construía antes, con el mismo config. Cero cambio de conducta.

### Lo que este ticket NO toca

- **Los stages siguen construyéndose dentro del coordinator.** Se evaluó y se descartó: 7 de los 11
  no tienen dependencias, los 4 que sí reciben use cases que el coordinator ya tiene inyectados, y
  nadie fuera del coordinator construye un stage. Meterlos en Koin son 11 `single` para re-enhebrar
  3 use cases, y se pierde que `sessionStages` sea un mapa resuelto en compilación (hoy un
  `DetectionStage` sin stage revienta en `stageFor`, no al arrancar una sesión). Ensamblar piezas
  puras y sin estado es trabajo del coordinator, no del contenedor.
- Ningún evaluador, umbral, guard ni orden de precedencia. **Cero cambios de conducta.**

## Criterio de éxito

| | |
|---|---|
| `EvaluateUnattendedParkingSaveUseCase` resoluble por Koin; cero construcciones a mano | ✅ |
| Cero `= null` como default en el constructor del coordinator (salvo `clock`, que sí tiene un valor obviamente correcto) | ✅ |
| `detectionModule` existe y `domainModule` no contiene registros de detección | ✅ |
| `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid` | ✅ |
| **1.657 tests en verde sin editar un solo assert** | ✅ 1.657 / 0 failures / 0 errors |
| App arrancando en device sin `NoDefinitionFoundException` | ⏳ **no verificado** — ver abajo |

### Verificación del traslado

Los 4 entry points de Koin quedan intactos, así que el único riesgo real del split es **perder o
duplicar un registro**. Comprobado por diff de conjuntos entre `master:DomainModule.kt` y la unión
de los dos ficheros nuevos:

```
=== PERDIDOS ===          (vacío)
=== NUEVOS ===            EvaluateUnattendedParkingSaveUseCase
before=50 after=51
```

Exactamente el delta buscado: nada perdido, nada duplicado, un registro añadido.

### Lo que ningún test cubre

El proyecto **no tiene verificación de módulos Koin** (no hay `koin-test` ni `checkModules`), así
que la resolución en runtime no la prueba nada: se comprueba arrancando la app. Queda pendiente de
un `/run`, que no se hace en este ticket porque sobreescribiría el APK que los móviles llevan para
el field-test en curso. Anotado como follow-up: **DET-KOIN-MODULE-VERIFY-001**.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `DomainModule.kt` | detección vive aquí | ✅ vaciado, `includes(detectionModule)` |
| `PaparcarApp` / `MockPaparcarApp` / `MainViewController` / `MockMainViewController` | `domainModule` trae detección | ✅ **cubierto por `includes()`** — no se tocan, sigue siendo cierto |
| `CoordinatorParkingDetectorTest` | 3 defaults `null` + unattended por defecto | ✅ los 4 explícitos |
| `DetectionTraceReplayTest` | ídem | ✅ los 4 explícitos |
| `StagePrecedenceCharacterizationTest` | ídem | ✅ los 4 explícitos |
| `androidDetectionModule` / `iosDetectionModule` | no cambian | exento — siguen listados por plataforma |
| `docs/detection/PARKING-DETECTION.md` | log de cambios algorítmicos | exento — **cero cambios de conducta**, no hay decisión nueva que registrar |
| Dev Catalog / galería de estados | pantallas y estados | exento — no hay pantalla, estado ni string nuevos |
