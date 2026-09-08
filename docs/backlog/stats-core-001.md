# STATS-CORE-001 · Las cifras, en dominio, sin pantalla

**Estado:** ✅ Done · rama `feature/STATS-CORE-001-the-numbers-live-in-domain`

Fase **F3 de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md)).

## Problema

Las estadísticas que F4 quiere pintar (gasto, CO₂, duración media, hora punta…) no existen; las
cuatro que SÍ existen (día punta, calle habitual, plazas cedidas, % autodetección) viven en
`VehicleHistoryCalculator.computeStats` — presentación — y F4 necesitaría o duplicarlas o
importar presentación desde el panel. Un solo agregador, en dominio, testeable, que la UI derive.

## Doctrina violada

Ninguna previa. Las que este ticket aplica:
- **No crear un segundo cálculo**: `ComputePeriodStatsUseCase` pasa a ser EL agregador;
  `computeStats` se elimina y `HistoryStatsData` se DERIVA de `PeriodStats` (mapper). La suma de
  distancia también se muda a dominio y el calculador DELEGA (la ventana del filtro sigue siendo
  del calculador — `windowStartMs` no se toca).
- **Umbral de significancia por campo** (patrón `MIN_SESSIONS_FOR_PEAK`): null bajo el umbral,
  nunca "0"/"—" como cifra.
- **[UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001]**: todo lo derivado de distancia (litros, kWh,
  gasto, CO₂) hereda la cobertura — se calcula SOLO sobre sesiones con ruta medida y el value
  object lo dice.
- **Las stats de un free se calculan sobre lo que puede ver** (siempre tras
  `gateHistoryToEntitlement`): lo contrario sería un paywall con agujero.

## Diseño

- `domain/model/Money.kt` (amount + currency; el formateo es de presentación con el locale).
- `domain/stats/ScopedDistance.kt` — MOVIDO de presentación (2 usos, barridos); +
  `sumMeasuredDistance(sessions)` como única implementación (el calculador delega).
- `domain/stats/EmissionFactors.kt`: kg CO₂/l por `FuelType` (gasolina 2,31 · diésel 2,68 ·
  GLP 1,66; híbridos usan el factor de su combustible) + `KG_CO2_PER_KWH_EU_MIX` para la parte
  eléctrica. Constantes nombradas.
- `domain/stats/PeriodStats.kt`: `parkings`, `endedParkings`, `distance: ScopedDistance?`,
  `fuelLiters?`, `energyKwh?`, `costEstimate: Money?`, `co2Kg?`, `avgParkedDurationMs?` (umbral 3),
  `peakHourBucket?` (0-23, umbral 5), `mostActiveDayOfWeek?` (umbral 5), `favoriteStreet?`
  (umbral 3), `spotsReleased`, `autoDetectedCount`/`detectionKnownCount` (umbral 5),
  `searchTimeMs? = null` (lo estampa F7), `previous: PeriodStats?`.
- `domain/usecase/stats/ComputePeriodStatsUseCase` (puro, síncrono, sin reloj propio — ni `nowMs`
  recibe: toda fecha sale de las sesiones):
  `invoke(sessions, vehicles, previousSessions?, fuelPricePerLiter?, energyPricePerKwh?)`.
  Las sesiones llegan YA gateadas y YA recortadas al periodo (la autoridad de ventanas sigue en
  `windowStartMs`; el calculador gana `previousWindow(filter, nowMs)` para que F4 recorte la
  comparativa). Consumo por sesión: distancia medida × (override ?? `FuelDefaults`) del vehículo
  de ESA sesión; PHEV suma litros y kWh (aproximación declarada — el panel dirá "aprox.").
  Gasto solo si cada energía consumida tiene su precio (parcial ⇒ null, nunca inventar).
  Sin precio (hasta F5) ⇒ `costEstimate = null`.
- Presentación: `PeriodStats.toHistoryStatsData()`; `HistoryTimeline.resolve` llama al agregador
  de dominio (sin vehículos ⇒ los campos de combustible quedan null y el mapper ni los lee).
- DI: factory en domainModule.

## Criterio de éxito

Tests del agregador: umbrales por campo (bajo/en/encima) · PHEV (litros + kWh) · EV (kWh sin
litros) · sesión activa excluida de duración y consumo · comparativa con periodo anterior vacío ·
free vs Plus con el MISMO dataset da cifras distintas (gate + agregador compuestos) · gasto null
con precio parcial · cobertura de distancia arrastrada. Los tests de `computeStats` migran sin
perder ningún caso. Suite + Konsist + assembles + klib iOS en verde. Cero strings nuevas (F3 no
tiene pantalla).

**Cumplido (08-09-2026):** `ComputePeriodStatsUseCaseTest` = 13 tests (energía con override y
default · PHEV ambas energías · EV solo kWh + CO₂ de mix · sin ruta medida ⇒ sin energía ·
cobertura arrastrada · gasto con precio completo / parcial ⇒ null / sin precios ⇒ null · activa
excluida de duración · umbral de duración · comparativa adjunta sin anidar · anterior vacío ⇒ null
· free vs Plus sobre el mismo dataset). Los 8 casos de `computeStats` NO se movieron de fichero: se
migraron en sitio vía el helper `legacyComputeStats` (agregador + mapper, la misma derivación que
producción), así la mudanza no pudo perder un umbral. + 3 tests de `previousWindow` (All ⇒ null ·
ventanas adyacentes sin hueco ni solape · semana anterior = 7 días exactos).
Testigos: `ComputePeriodStatsUseCaseTest` tests="13" failures="0" ·
`VehicleHistoryCalculatorTest` tests="16" failures="0" · suite completa, `assembleProdDebug`,
`assembleMockDebug` y `compileKotlinIosSimulatorArm64` BUILD SUCCESSFUL.

## Consumidores auditados

- `VehicleHistoryCalculator.computeStats` → eliminado; único caller (`HistoryTimeline.resolve`)
  cambiado al agregador ✔
- `sumDistanceMeters` → delega en `domain/stats`; su caller (`HistoryContent`) intacto ✔
- `ScopedDistance` → movido a domain; imports barridos (2 ficheros) ✔
- `windowStartMs`/buckets/chart → NO se tocan (la ventana sigue teniendo un solo dueño) ✔
- `VehicleHistoryCalculatorTest` → casos de computeStats migran a `ComputePeriodStatsUseCaseTest` ✔
