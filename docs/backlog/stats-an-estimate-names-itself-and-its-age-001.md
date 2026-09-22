# STATS-AN-ESTIMATE-NAMES-ITSELF-AND-ITS-AGE-001 · Una estimación se llama «aprox.» y caduca

**Estado:** ✅ Done (22-09-2026) · mergeado a master vía squash · 3.641 tests verdes (`testAll`)

## Problema
Las dos decisiones de producto que la auditoría [STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001] dejó
abiertas (audit §6), ya resueltas por el user (22-09):
1. Los tiles «combustible» y «energía» son EXACTAMENTE tan estimados como «gasto aprox.» y
   «CO₂ aprox.» (misma fórmula: distancia × consumo), pero sus etiquetas no lo decían — dos tiles
   de la misma familia con dos niveles de honestidad distintos.
2. El precio medio nacional (tabla embebida / constantes kWh) precificaba el periodo con CUALQUIER
   antigüedad; la única mitigación era imprimir su fecha.

## Doctrina violada
[STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001] §4 del contrato: *las estimaciones se identifican*.
Y la regla rectora: un precio de hace un año presentado como base de un gasto es un dato que ya no
puede defenderse — mejor negar el tile que precificar con él.

## Diseño
1. **Etiquetas**: `stats_tile_fuel` y `stats_tile_energy` adoptan el calificador de estimación en
   los 9 locales, con la MISMA abreviatura que su propio `stats_tile_cost` ya usa en cada lengua
   (aprox./appross./approx./ca./ong./ok.) — el calificador es por-locale, no un sufijo universal.
2. **Caducidad**: `ResolveFuelPriceUseCase` gana un gate — un `NationalAverage` con `measuredOn`
   a más de `MAX_NATIONAL_AVERAGE_AGE_DAYS = 180` días no precifica (devuelve null → el tile de
   gasto se niega, como ya hace sin precio). El override del usuario y el peldaño de gasolinera
   NO caducan aquí (el del usuario es su palabra; el de estación tendrá su propia frescura en
   FUEL-STATIONS-001). Una fecha imparseable tampoco precifica: un precio que no sabe decir
   cuándo se midió no puede defenderse 6 meses después.
   Por debajo del umbral, todo sigue igual: se muestra CON su fecha (`fuelPriceNoteText`).
3. Los docs de contrato (`analytics-data-audit.md` §6, `analytics-data-contract.md`) se actualizan
   como resueltos.

## Criterio de éxito
- Tests del resolver: tabla fresca precifica · tabla de >180 días no · override inmune a la edad ·
  kWh misma regla · fecha imparseable no precifica.
- `LocaleParityGuardrailTest` verde (no hay keys nuevas, solo valores) · `testAll` verde.

## Consumidores auditados
- `ResolveFuelPriceUseCase.litre/kwh`: únicos productores de `NationalAverage` — **cerrados**.
- `fuelPriceNoteText` (nota con fecha): solo recibe precios que ya pasaron el gate — **cubierto**.
- `RunFuelPriceWatchUseCase` (avisos de gasolinera): tiene SU propia frescura
  ([FUEL-A-CHEAP-PRICE-WITH-NO-FLOOR-IS-THE-STALEST-ONE-001]), no pasa por este resolver — **exento**.
- `stats_tile_fuel`/`stats_tile_energy`: un solo lector (`VehicleStatsBlock`, el tile) — **cerrado**.
