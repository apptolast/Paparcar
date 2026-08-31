# VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001

> **Estado:** implementado 2026-08-31 · rama
> `refactor/VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001-declare` (base `70e4d297`)
> **Origen:** las dos membresías que la **Pieza 2** del rediseño de detección dejó abiertas
> (`docs/detection/REDESIGN-DETECTION-SYSTEM.md` §7.2, fallos #2 y #3). Resultaron ser **cuatro**.
> **Delta de comportamiento:** cero. Es un cambio de DÓNDE se responde, no de QUÉ se responde.

---

## 1. El bug

`VehicleType` es un enum de cuatro constantes que no declaraba nada sobre sí mismo. Todo lo que
implica se volvía a deducir en el sitio que lo necesitaba, deletreando las constantes:

| dónde | cómo se deletreaba | qué pregunta era en realidad |
|---|---|---|
| `HumanPoweredRide.kt:98` | `vehicleType == SCOOTER \|\| == BIKE` | ¿es músculo? |
| `ParkingStrategyResolver.kt` (companion) | `NON_PARKING_TYPES = setOf(SCOOTER, BIKE)`, leído en 2 sitios | ¿ocupa una plaza? |
| `EvaluateParkingDecisionUseCase.kt:264` | `input.vehicleType == CAR` (guard de mismatch) | ¿un viaje lento contradice el perfil? |
| `VehicleRegistrationState.kt:74` | `vehicleType == CAR` (`expectsCarbody`) | ¿tiene carrocería? |
| `VehicleRegistrationViewModel.kt` ×4 | `== CAR` / `!= CAR` (`inferIfCar`, `resolveSize`, `SelectModelOther`, `SetVehicleType`, el `body =` que persiste) | ídem |

**El fallo no es la repetición, es la herencia silenciosa.** Un tipo añadido mañana —una moped, una
e-bike, una furgoneta camperizada— llega al árbol siendo, sin que nadie lo decida:

- motorizado (`isHumanPowered` es falso por omisión) → **puede auto-confirmar**
- aparcable (`NON_PARKING_TYPES` no lo contiene) → **el Coordinator lo vigila**
- sin carrocería (`!= CAR`) → talla `MOTORCYCLE` y `carbodyType` nulo persistido
- con viajes lentos normales (`!= CAR`) → el guard de mismatch no lo mira

Ese es el sentido literal del título: un tipo nuevo **es un coche por omisión** en las dos preguntas
que abren la puerta al pin, y un no-coche por omisión en las dos del registro. Es el mismo defecto
que `DET-DRIVING-EVIDENCE-IS-THE-ONLY-GATE-001` midió con los arms (una lista escrita a mano falla
ABIERTO: lo nuevo es fuerte hasta el día que quema) y que `SessionOutcome`/`DetectionPath`/`ArmLabel`
ya curaron en sus vocabularios.

### 1.1 Lo que el plan no vio: son cuatro preguntas, no dos

La Pieza 2 listaba dos filas (`isHumanPowered`, `parkingStrategy`). Al barrer aparecieron dos más, y
la tentación evidente —una sola propiedad, porque sobre los cuatro tipos de hoy las columnas
coinciden— es exactamente el error que el ticket arregla:

| tipo | `isHumanPowered` | `parksInASpot` | `hasCarbody` | `slowTripContradictsProfile` |
|---|---|---|---|---|
| CAR | ❌ | ✅ | ✅ | ✅ |
| MOTORCYCLE | ❌ | ✅ | ❌ | ❌ |
| SCOOTER | ✅ | ❌ | ❌ | ❌ |
| BIKE | ✅ | ❌ | ❌ | ❌ |

- Las dos primeras son complementos exactos **hoy**, y dejarían de serlo con una moped (motorizada y
  no ocupa plaza de coche) o con una bici de carga (músculo y sí la ocupa).
- Las dos últimas son ciertas sólo para `CAR` **por razones que no tienen nada que ver**: una es el
  formulario preguntando por la forma de la carrocería; la otra es el guard de `BUG-SCOOTER-001` — un
  perfil `CAR` a velocidad de ciclomotor durante minutos es más probablemente un vehículo mal fichado
  que un coche en atasco. `MOTORCYCLE` responde `false` a la segunda **a propósito**: ya ES el
  vehículo pequeño y lento, así que la lentitud no contradice nada.

Fusionarlas pasaría todos los tests de hoy y sería falso en la constante siguiente.

### 1.2 Y una superficie que nadie había mirado

`VehicleTypeSelector` construía sus opciones con un `listOf` escrito a mano. Un tipo ausente de esa
lista es un tipo **imposible de elegir** mientras todos los carriles de abajo lo tratan — el mismo
fallo por omisión, en la dirección contraria, y sin nada que fallara.

---

## 2. El arreglo

Las cuatro preguntas se declaran en `VehicleType` como `when` exhaustivos, cada uno documentado con
SU razón (no con la lista de tipos que la cumplen hoy). **Criterio de aceptación de la Pieza 2:** un
caso nuevo no compila hasta que su autor responde — aquí, cuatro veces.

```kotlin
enum class VehicleType {
    CAR, MOTORCYCLE, SCOOTER, BIKE;

    val isHumanPowered: Boolean get() = when (this) { SCOOTER, BIKE -> true; CAR, MOTORCYCLE -> false }
    val parksInASpot: Boolean get() = when (this) { CAR, MOTORCYCLE -> true; SCOOTER, BIKE -> false }
    val hasCarbody: Boolean get() = when (this) { CAR -> true; MOTORCYCLE, SCOOTER, BIKE -> false }
    val slowTripContradictsProfile: Boolean get() = when (this) { CAR -> true; MOTORCYCLE, SCOOTER, BIKE -> false }
}
```

`NON_PARKING_TYPES` se borra entero (el `private companion object` de `ParkingStrategyResolver`
desaparece con él). `inferIfCar` pasa a `inferCarbody`: el NOMBRE decía coche donde quería decir
"tiene carrocería". El picker se construye con `VehicleType.entries.map { it.toOption() }` sobre un
`when` exhaustivo que le exige icono + sus dos strings.

---

## 3. Barrido de consumidores (todos los sitios auditados)

| # | fichero | qué había | qué hay | delta |
|---|---|---|---|---|
| 1 | `domain/model/VehicleType.kt` | enum mudo | 4 propiedades declaradas + KDoc por pregunta | — |
| 2 | `domain/detection/HumanPoweredRide.kt:98` | `== SCOOTER \|\| == BIKE` | `vehicleType?.isHumanPowered == true` | ninguno |
| 3 | `domain/detection/ParkingStrategyResolver.kt` (`strategyFor`) | `primary.vehicleType in NON_PARKING_TYPES` | `!primary.vehicleType.parksInASpot` | ninguno |
| 4 | idem (`isBtPairedAndParks`) | `vehicleType !in NON_PARKING_TYPES` | `vehicleType.parksInASpot` | ninguno |
| 5 | idem (companion + import + tabla del KDoc) | `NON_PARKING_TYPES` + `import VehicleType` | borrados (el import quedaba sin uso → `-Werror`) | — |
| 6 | `domain/usecase/parking/EvaluateParkingDecisionUseCase.kt` (`humanPowered`) | `== SCOOTER \|\| == BIKE \|\| humanPoweredRide` | `isHumanPowered == true \|\| humanPoweredRide` | ninguno |
| 7 | idem (`isMismatch`) | `== CAR &&` | `slowTripContradictsProfile == true &&` | ninguno |
| 8 | `presentation/vehicleregistration/VehicleRegistrationState.kt` | `expectsCarbody = vehicleType == CAR` | `vehicleType?.hasCarbody == true` | ninguno (null seguía sin ser coche) |
| 9 | `…/VehicleRegistrationViewModel.kt` `SelectModelOther` | `if (== CAR \|\| == null) null else MOTORCYCLE` | `if (== null \|\| hasCarbody) null else MOTORCYCLE` | ninguno |
| 10 | idem `SetVehicleType` | `if (newType == CAR)` | `if (newType.hasCarbody)` | ninguno |
| 11 | idem `inferIfCar` → `inferCarbody` | `if (type != null && type != CAR) return null` | `if (type != null && !type.hasCarbody) return null` | ninguno (+ rename) |
| 12 | idem `resolveSize` | `type != null && type != CAR ->` | `type != null && !type.hasCarbody ->` | ninguno |
| 13 | idem `saveVehicle` | `body = if (type == CAR) …` | `if (type.hasCarbody) …` | ninguno |
| 14 | `ui/components/VehicleTypeSelector.kt` | `listOf(TypeOption(CAR,…), …)` escrito a mano | `entries.map { it.toOption() }` + `when` exhaustivo; `label`/`examples` pasan de `@Composable () -> String` a `StringResource` | ninguno |
| 15 | `ui/icons/PaparcarIcons.kt` | `when (type)` exhaustivo → icono | **sin tocar** — el compilador ya lo guarda | — |
| 16 | `data/mapper/VehicleMapper.kt` | `toEnumOrDefault(CAR)` / `ifBlank { CAR.name }` | **sin tocar** — es el default de la migración Room v3→v4, un valor AUSENTE, no un significado | — |
| 17 | `presentation/…ViewModel` `?: VehicleType.CAR` ×5 | default del formulario | **sin tocar** — mismo motivo: el tipo aún no se ha elegido | — |
| 18 | fakes, previews, `StateGalleryScreen`, tests | construyen `VehicleType.X` | **sin tocar** — construcción, no comparación | — |

**Sitios auditados sin cambio y por qué**: 15–18. Tras el barrido no queda **ninguna** comparación
`== VehicleType.X` en código de producción (verificado por grep sobre todo el repo y por el
guardarraíl nuevo).

---

## 4. Tests

**Nuevos**
- `commonTest/domain/model/VehicleTypeQuestionsTest.kt` (5) — la tabla de 4×4 como censo (un tipo sin
  fila falla), y tres invariantes que **no** son la tabla: un tipo de músculo nunca llega al guard de
  mismatch, una carrocería implica plaza y motor, y **las cuatro columnas son distinguibles** (existe
  un tipo que aparca sin carrocería y uno que aparca pudiendo ser lento) — para que fusionarlas tenga
  que hacerse contra una afirmación explícita.
- `androidUnitTest/architecture/VehicleTypeVocabularyGuardrailTest.kt` (2) — `no production code
  compares a value to a VehicleType constant` y `no vehicle type question falls back to an else
  branch`.

**Testigo de población** [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]: el guardarraíl es una
prohibición sobre un conjunto filtrado, así que se le pide a `GuardrailScope` una población nueva —
`productionFilesMentioning("VehicleType", floor = 10)`, medida **21**, suelo a la mitad. Renombrar el
tipo tira la población a 0 y `population()` lo hace fallar en lugar de reportar el mismo verde.

**Falsación (⛔ un test de prohibición sin verlo fallar siempre pasa):** se inyectó el
`== SCOOTER || == BIKE` original en `HumanPoweredRide` y un `else -> false` en `VehicleType`; ambos
tests pasaron a **FAILED**. Restaurado, verdes.

**Suite completa:** `:shared:testDebugUnitTest` → **1992 tests, 0 fallos** (1985 + 7).
`:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` OK.

---

## 5. Lo que este ticket NO hace

- **No toca `DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001`** (§9.4 del rediseño), que sigue
  bloqueado por medición. Aquel calibra umbrales cinemáticos; éste sólo evita que un tipo nuevo
  herede la respuesta del perfil.
- **No unifica los defaults `?: VehicleType.CAR`** del registro ni de la migración Room. Son valores
  ausentes, no significados heredados — y el del mapper es la migración v3→v4 documentada.
- **No añade tipos.** El enum sigue teniendo cuatro constantes; lo que cambia es lo que le cuesta
  añadir la quinta (ahora: cuatro respuestas obligadas por el compilador y una fila en el censo).

---

## 6. Doctrina que aplica

- *Sistemas, no parches*: el invariante ("qué significa este tipo") se arregla en UN sitio y se barren
  **todos** sus consumidores — los 4 de detección, los 5 de registro y el picker.
- *Fallo asimétrico*: la herencia por omisión empujaba al lado equivocado en las dos preguntas que
  abren la puerta al pin (motorizado + aparcable = auto-confirm). Ahora no hay lado por omisión.
- *Un caso de uso por VEREDICTO*: ninguna de las cuatro es un caso de uso — son predicados, y viven
  en el tipo que los responde. No se creó ninguna clase.
