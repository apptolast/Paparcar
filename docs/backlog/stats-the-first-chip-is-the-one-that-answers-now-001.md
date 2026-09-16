# STATS-FIRST-CHIP-ANSWERS-NOW-001 · La fila de periodos empieza por AHORA y «Todo» es la salida del final

**Estado:** ✅ Done — en master

## Problema
El primer chip de la fila de periodos era **«Todo»**, y era además el filtro con el que abría el
Historial. Dos consecuencias:

1. **Lo primero que se lee no es lo primero que se quiere saber.** El usuario abre su coche para ver
   *qué ha pasado últimamente*, y la fila le ofrece de entrada el agregado de toda su vida — la
   pregunta que menos veces se hace y la única que no cambia de un día para otro.
2. **Las dos pantallas abrían en periodos distintos**: el Historial en `All`
   (`HistoryState.kt:188`) y el panel de Estadísticas en `ThisMonth` (`StatsState.kt:25`). Mismo
   coche, mismas cifras, dos ventanas por defecto según por dónde entres.

Y un tercer dato que lo decide: en cuenta **free** las cifras son SIEMPRE los últimos 7 días pulses
el chip que pulses (`HistoryState.kt:115`, [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001]). O sea que
para la mayoría de los usuarios el chip que gobierna de verdad las cifras estaba en segunda posición
mientras el primero mentía.

## Doctrina violada
Ninguna regla escrita — pero sí el criterio editorial de la app: **lo que pasa AHORA va primero**
(es lo que ordena la BottomNav: Home = el ahora, Vehículos = lo mío permanente). La fila de periodos
estaba ordenada al revés de su propia pantalla.

El comentario que fijaba el orden anterior (`HistoryFilter.kt:75`) se justificaba así: *«"All"
leads, as it always has: it is the one that answers before anything has happened»*. Es cierto que es
el único que responde con la historia vacía — pero **responde lo mismo que cualquier otro cuando no
hay nada**, así que no gana nada por ir delante.

## Diseño
Tres decisiones, cada una en UN sitio:

1. **El orden lo sigue mandando `availableFilters()`** — sigue siendo un dato derivado de las
   sesiones, no una constante. Cambia la secuencia: `Last7Days → ThisWeek → ThisMonth → LastMonth →
   Last3Months → ThisYear → años cerrados → All`. Se lee de lo reciente a lo lejano, y «Todo» cierra
   la fila como lo que es: la salida de emergencia, no la portada.
2. **«Todo» va DESPUÉS del desplegable de años.** Para eso `HistoryFilterBar` deja de partir la
   lista en dos grupos (chips / años) y **dibuja en el orden de la lista**, colapsando la racha de
   años cerrados en un solo chip en la posición donde aparece el primero. Así el orden de la fila es
   una decisión de `availableFilters` y de nadie más: la barra ya no puede reordenar por detrás.
3. **Un solo default: `HistoryFilter.Default` (= `Last7Days`).** El periodo con el que se abre
   estaba escrito CUATRO veces con dos valores distintos (`HistoryState`, `StatsState`, y dos
   fallbacks `?: HistoryFilter.All` en `VehiclesViewModel`). Pasa a existir una vez y las cuatro lo
   citan. **El chip encendido al abrir es, por construcción, el primero de la fila.**

### Lo que NO cambia
- La regla de qué chips se OFRECEN (derivada de las sesiones) es exactamente la misma.
- El gating: la fila se deriva de la lista COMPLETA, free ve «este año» y se topa con el paywall al
  tocarlo. [MONEY-001]
- Las proyecciones siguen siendo solo de periodos de CALENDARIO — `Last7Days` es rodante, así que
  abrir en él no estrena ninguna estimación.

## Criterio de éxito
- `availableFilters()` devuelve `Last7Days` primero y `All` último, con cualquier antigüedad de
  historia (cuenta nueva de 3 días y cuenta con años cerrados).
- El chip seleccionado al abrir es el primero de la fila, en las dos pantallas — con un test que lo
  ate a `availableFilters().first()` y no a un literal, para que reordenar la fila sin mover el
  default vuelva a ponerse en rojo.
- Historial y panel de Estadísticas abren en el MISMO periodo.

## Consumidores auditados
| Sitio | Qué asumía | Cierre |
|---|---|---|
| `HistoryFilter.availableFilters()` | `All` primero | ✅ reordenado |
| `HistoryFilterBar` | dos grupos: chips y luego años | ✅ dibuja en orden de lista |
| `HistoryState.activeFilter` | default `All` | ✅ `HistoryFilter.Default` |
| `StatsState.filter` | default `ThisMonth` | ✅ `HistoryFilter.Default` |
| `StatsState.periodFilters` (lista inicial) | `All` primero | ✅ reordenada, espejo del orden real |
| `VehiclesViewModel:226` y `:428` | `?: HistoryFilter.All` | ✅ `?: HistoryFilter.Default` |
| `vehicleStatsPeriodLabel` / `windowStartMs` / `previousWindow` / `buckets` | `when` exhaustivo | ✅ exento: no dependen del orden |
| `StatsScreen:237` `showLifetime` | compara con `All` | ✅ exento: compara identidad, no posición |
| Dev Catalog `resolvedHistory()` / `statsPanelState()` | defaults propios (`All` / `ThisMonth`) | ✅ → `HistoryFilter.Default`; si no, la galería encendía un chip y filtraba la lista por otro |
| Dev Catalog, variantes con filtro explícito | nombran su periodo | ✅ exento: son variantes, no el estado de apertura |
| `VehiclesPreviews.previewHistory` | resolvía con `All` | ✅ → `HistoryFilter.Default`, paridad con la galería |

**Sin estado ni pantalla nuevos**, así que la galería no gana entradas: lo que cambia es con qué
periodo abre, y eso es exactamente lo que sus variantes por defecto tienen que enseñar.

**Lado iOS:** nada que abrir. Todo esto vive en `commonMain` (`presentation/vehicles`,
`presentation/stats`), así que iOS recibe el mismo orden y el mismo default al compilar el módulo.

## Verificación
- `./gradlew testAll` ✅ · `:app:assembleMockDebug` ✅ · `:app:compileProdDebugKotlin` ✅
- Los dos tests nuevos **se ejecutaron** (comprobado en
  `TEST-…AvailableFiltersTest.xml`, no UP-TO-DATE): `should_leadWithTheRollingWeekAndCloseWithAll_…`
  y `should_openOnTheFirstChipOfTheRow_…`.
- ⏳ Pendiente: verlo en device.
