# STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001 · Free ve todas las cifras de su coche, de los últimos 7 días

**Estado:** 🟡 En revisión · rama `feature/STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001` ·
GitHub issue [#14](https://github.com/apptolast/Paparcar/issues/14)

## Problema
Issue #14: hoy ocultamos la mayoría de estadísticas del vehículo a un usuario free; a partir de ahora
se muestran todas, pero solo de los últimos 7 días, y la card de Plus bajo las cifras pasa a vender
eso: con Plus las ve en el periodo que elija.

Estado anterior (`VehicleStatsBlock`): `StatTileGrid(veilFromIndex = if (isPlus) null else 1)` —
free veía la primera cifra y el resto con una barra de redacción (`PapStatTile.veiled`). El enlace
«Ver desglose completo» solo salía a Plus. Las cifras de free YA se calculaban sobre la historia
recortada a 7 días (`HistoryTimeline.resolve` → `gateHistoryToEntitlement`) y el título de la card ya
decía «Últimos 7 días».

## Doctrina
«Free = tú preguntas · Plus = la app vigila y **recuerda** por ti» (CLAUDE.md). Tapar cifras que la
app ya calculó no era memoria, era esconder; limitar el periodo sí lo es. El cambio encaja mejor con
el eje que la regla anterior.

## Diseño
- **Todas las cifras para todos**: fuera `veilFromIndex`, el parámetro `veiled`/`veiledDescription` de
  `PapStatTile`, `RedactedValue` y el string `stats_veiled_value` (×9, lo exige el guard de «todo
  string se lee»). La primera cifra sigue siendo la destacada (`emphasized`) para todos.
- **Sin cambios de cálculo**: la ventana de 7 días ya la aplica `HistoryTimeline.resolve`; el título
  (`vehicleStatsPeriodLabel`) ya nombraba «Últimos 7 días» para free.
- **Card Plus** (`PlusStatsRow`, misma posición y acción → paywall `STATS`): título «Elige el periodo»
  y subtítulo «Gratis ves tus últimos %1$d días; con Plus, el periodo que elijas» con
  `FREE_HISTORY_WINDOW_DAYS`.
- **Paywall**: fila de cifras «Todas, últimos 7 días» / «Cualquier periodo»; `paywall_reason_stats`
  «Con Plus ves tus cifras en el periodo que elijas».
- **Enlace al panel completo solo para Plus** (decisión de la revisión).
- **Los chips de periodo gobiernan la gráfica; las cifras de free son SIEMPRE los últimos 7 días**:
  `HistoryTimeline.resolve` calcula `periodStats` con `Last7Days` para free, sea cual sea el chip
  (la gráfica y el recuento siguen el chip). Plus: el chip manda en ambas.
- **Chip nuevo «Últimos 7 días»** (`HistoryFilter.Last7Days`, móvil: hoy y los 6 días anteriores), junto a
  «Esta semana» (natural, desde el lunes). Ventana, periodo anterior y barras diarias en
  `VehicleHistoryCalculator`, el único dueño de las ventanas.
- **Estado vacío**: un periodo sin aparcamientos ya no enseña «0 PLAZAS CEDIDAS». La card muestra
  «Esperando a tu primer viaje» (si el coche nunca aparcó) o «Sin aparcamientos en este periodo», y
  debajo «Activa la detección automática para que midamos tus trayectos y aparcamientos» si la
  detección está apagada (`AppPreferences.observeAutoDetectParking`, en `VehiclesViewModel` y
  `StatsViewModel`) o «Tus cifras aparecerán aquí en cuanto aparques».
- La línea «desde siempre» se enseña siempre en free (sus cifras nunca son «todo»).
- Strings ×9, comentarios/KDoc, tests renombrados, variantes de galería renombradas, docs anteriores
  (`stats-ui-001`, `stats-panel-001`, `plus-roadmap-001`, `plus-alerts-and-tiers-001`) marcados como
  superados en esta regla.

## Criterio de éxito
- Una cuenta free ve en la ficha del vehículo y en el panel todas las cifras que existan, tituladas
  «Últimos 7 días», y debajo la card Plus de elegir periodo.
- Plus sin cambios (sin card Plus, filtro de periodo manda).
- Galería: «Panel de cifras → Free · todas las cifras, últimos 7 días» y «Historial → Cifras · free».

## Consumidores auditados
| Consumidor | Estado |
|---|---|
| `VehicleStatsBlock` (grid, enlace al panel, card Plus) | cambiado |
| `PapStatTile` (`veiled`, `RedactedValue`) | borrado — solo lo usaba la grid |
| `StatsScreen` / `HistoryContent` / `StatsIntent` KDoc | corregidos |
| `PaywallScreen` `TIER_ROWS` + `paywall_reason_stats` | copy nuevo |
| `HistoryTimeline.resolve`, `HistoryEntitlementGate` | sin cambio — ya aplican los 7 días |
| `StatsViewModelTest`, `VehicleStatsBlockTest` | renombrados (no probaban el velo) |

## Decisiones de la revisión (13-09-2026)
1. Los chips de periodo siguen visibles, pero solo aplican a la gráfica; las cifras de free son los
   últimos 7 días. Hecho.
2. Las comparativas ▲/▼ se quedan visibles en free.
3. El enlace al panel completo se oculta en free. Hecho.
4. Añadido el chip «Últimos 7 días» además de «Esta semana». Hecho.
5. Sin viajes: «Esperando a tu primer viaje» y, con la detección apagada, la invitación a activarla,
   en lugar de «0 PLAZAS CEDIDAS». Hecho.
