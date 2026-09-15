# STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001 · La cobertura viaja con la medida, y todo lector la enseña

**Estado:** ✅ Done (2026-09-15) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Rebasado sobre `3d1d80a6` (VEH-SHARED-001) justo antes de mergear, sin conflictos en este ticket.
**Abierto:** 2026-09-15 · desde master `b4e66e9c` · **F0 de**
[`stats-intelligence-roadmap-001.md`](stats-intelligence-roadmap-001.md)

## Problema

`PeriodStats` documenta el invariante en su propio KDoc:

> *Everything derived from distance (litres, kWh, cost, CO₂) is computed ONLY over sessions with a
> measured route; [distance] carries that coverage so no caller can print a subtotal as a total.*

**Y no se cumple en pantalla.** Sólo la línea de km del gráfico lee `ScopedDistance.isComplete`. La
tarjeta de cifras pinta cinco tiles derivados de esa misma distancia parcial —gasto, litros, kWh,
CO₂— como si fueran totales del periodo, sin una palabra de cobertura.

Y la métrica estrella del pivote es el caso peor, porque ni siquiera tiene el tipo:

```kotlin
searchTimeMs = sessions.mapNotNull { it.searchTimeMs }.takeIf { it.isNotEmpty() }?.sum()
```

Un `Long` pelado (`ComputePeriodStatsUseCase:165`). El tile dice «1 h 12 min buscando plaza» en un
periodo de 12 aparcamientos cuando la cifra sale de 7 — y `searchTimeMs` sólo se estampa cuando se
adjuntó una ruta MEDIDA fresca, así que la cobertura parcial no es el caso raro: es el normal
mientras el carril BT no traiga ruta en todos los viajes.

Peor que el número: la **comparativa**. `percentDelta` compara la suma de este periodo con la del
anterior sin mirar sus coberturas. Un «▲ 18 %» entre 7-de-12 y 3-de-10 no mide que hayas buscado
más — mide que este mes se midieron más viajes.

## Doctrina violada

- [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] — literal, y en cinco tiles a la vez.
- **Sistemas, no parches**: la cobertura existe como tipo desde STATS-CORE-001 y se quedó atada a
  UNA magnitud (la distancia) en vez de a la idea «suma medida». Por eso la siguiente magnitud
  medida —el tiempo de búsqueda— nació sin ella, y la siguiente nacería igual.
- Un `0` o un guion nunca se pintan donde no hay dato; una suma parcial disfrazada de total es la
  misma mentira, sólo que con mejores modales.

## Señales / datos disponibles

- `UserParking.routeDistanceMeters` — null en el carril BT antiguo y en filas legacy.
- `UserParking.searchTimeMs` — null salvo confirm con ruta medida fresca
  [TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001].
- `ScopedDistance(meters, fromParkings, ofParkings)` + `sumMeasuredDistance` ya existen y ya son la
  única implementación del invariante.
- El vocabulario de cobertura ya está escrito y traducido: `history_activity_distance_partial` →
  «40 km (3 viajes)».

## Diseño

**El invariante vive en UN tipo, y toda suma medida pasa por él.**

1. `domain/stats/ScopedMeasure.kt` (era `ScopedDistance.kt`):
   ```kotlin
   data class ScopedMeasure<out T : Any>(val value: T, val fromParkings: Int, val ofParkings: Int) {
       val isComplete: Boolean get() = fromParkings >= ofParkings
   }
   ```
   Un solo constructor privado de sumas (`scopedSum`) y dos lectores nombrados:
   `sumMeasuredDistance` y `sumMeasuredSearchTime`. Añadir una tercera magnitud medida es una línea,
   y nace con cobertura por construcción — que es el punto del ticket.
   ⛔ **Sin `typealias ScopedDistance`**: dos nombres para un tipo es la puerta por la que vuelve
   «esto es lo de la distancia» y la siguiente magnitud se escribe a mano.

2. `PeriodStats`: `distance: ScopedMeasure<Float>?` y **`searchTime: ScopedMeasure<Long>?`**
   (sustituye a `searchTimeMs: Long?`; el nombre pierde la unidad porque el tipo ya la lleva).

3. **Todo lector la enseña.** `StatTileData` gana `note: String?` y `PapStatTile` una tercera línea
   en voz LECTURA (`caption`), que sólo se compone cuando la medida es PARCIAL:
   - `SEARCH_TIME` → cobertura propia del tiempo de búsqueda.
   - `COST · FUEL · ENERGY · CO2` → cobertura de la **distancia**, que es de donde salen.
   String nueva `stats_tile_coverage` («%1$d de %2$d viajes»), misma palabra que ya usa la línea de
   km del gráfico.

4. **Un delta compara dos sumas; si alguna no cubre su periodo, no es una comparación.** El ▲/▼ de
   esos cinco tiles existe sólo cuando **ambos** periodos están completos. Es la misma regla que
   `percentDelta` ya aplicaba cuando el periodo anterior era 0 («cualquier cambio desde cero es
   infinito, y eso no es una afirmación»), extendida a la otra forma de no poder comparar.
   Se corrige sola: según el carril BT gane ruta medida, la cobertura se completa y el delta vuelve.

### Lo que NO entra, y por qué

**Una media parcial no miente; una suma parcial sí.** `avgParkedDurationMs` se calcula sobre las
sesiones cerradas que tienen `endedAtMs` y por encima de su umbral — y una media sobre una muestra
sigue estimando la misma magnitud, mientras que una suma sobre una muestra estima una magnitud
MENOR en proporción a lo que falta. Por eso la cobertura se pega a las sumas medidas (km, litros,
kWh, CO₂, gasto, búsqueda) y la media de estancia se queda como está.

## Criterio de éxito

- **Test**: `sumMeasuredSearchTime` devuelve null sin ninguna medida, y cobertura 7/12 con siete.
- **Test**: un periodo con distancia parcial no produce delta en COST/FUEL/ENERGY/CO2, y sí lo
  produce cuando ambos periodos están completos.
- **Test**: los tiles de cobertura parcial llevan `note`; los completos, no (ni una línea vacía).
- Suite completa verde + Konsist (typography, color, divider, import) ejecutados con
  `--rerun-tasks` + locale-parity con los 9.
- Compila `mockDebug`, `prodDebug` y `iosSimulatorArm64`.
- Galería del Dev Catalog en paridad: una variante con cobertura parcial.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `domain/stats/ScopedDistance.kt` | 🔧 se generaliza y se renombra a `ScopedMeasure.kt` |
| `ComputePeriodStatsUseCase` (distancia :146, búsqueda :165) | 🔧 las dos sumas pasan por los lectores nombrados |
| `VehicleHistoryCalculator.sumDistanceMeters` | 🔧 delega igual, cambia el tipo de retorno |
| `HistoryContent` + `StatsScreen` (línea de km) | 🔧 `.meters` → `.value`; la lógica de parcial NO cambia |
| `VehicleStatsBlock` (`statTileKeys`, `statTiles`, `percentDelta`) | 🔧 `note` + regla del delta |
| `ui/components/PapStatTile` | 🔧 parámetro `note` opcional; sin `note` se compone exactamente lo de hoy |
| `presentation/preview/FakeData` + galería | 🔧 paridad con una variante parcial |
| `ConfirmParkingUseCase` (estampa `UserParking.searchTimeMs`) | ✅ exento: el CAMPO de la sesión no cambia, sólo el agregado |
| Room / DTO / mappers / workData | ✅ exentos: `PeriodStats` es un agregado en memoria, no se persiste |
| iOS | ✅ todo el cambio es commonMain; se verifica con `compileKotlinIosSimulatorArm64` |

## Verificación (15-09-2026, sin commitear)

- Suite completa `:shared:testDebugUnitTest` **BUILD SUCCESSFUL** (275 clases de test). Testigos:
  `ScopedMeasureTest` tests="5" failures="0" (nuevo) · `VehicleStatsBlockTest` tests="16"
  failures="0" (+3: delta incomparable, delta comparable, tile de búsqueda) ·
  `ComputePeriodStatsUseCaseTest` tests="15" failures="0" (+1 cobertura completa) ·
  `VehicleHistoryCalculatorTest` tests="16" failures="0".
- Guardarraíles ejecutados con `--rerun-tasks`, no cacheados: Typography 5 · Color 8 · Divider 1 ·
  Import 1 · **LocaleParity 5**, todos failures="0".
- `:app:compileMockDebugKotlin`, `:app:compileProdDebugKotlin` y
  `:shared:compileKotlinIosSimulatorArm64` en verde.
- Galería: variante nueva **«Cifras · cobertura parcial (nota, y sin delta)»** en el grupo
  Historial — una de cada tres sesiones conserva ruta y búsqueda, el resto no.
- ⏳ **Sin ver en device**: falta mirar cómo respira la tercera línea del tile en la rejilla de dos
  columnas (es la única duda de layout del ticket).
