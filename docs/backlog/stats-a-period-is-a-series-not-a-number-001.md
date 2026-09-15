# STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001 · El gráfico dibuja la métrica que le pidas, no siempre la misma

**Estado:** ✅ Done (2026-09-15) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Rebasado sobre `3d1d80a6` (VEH-SHARED-001) antes de mergear, sin conflictos en este ticket.
⚠️ Los chips se vieron PARTIRSE en device y el arreglo (`LazyRow`) viajó en el commit de F3, que es
donde se hizo el pase — el estado final de master lo lleva.
**Abierto:** 2026-09-15 · sobre `8896e628` (F0) · **F1 de**
[`stats-intelligence-roadmap-001.md`](stats-intelligence-roadmap-001.md)

## Problema

El periodo tiene once cifras y **una sola forma**: el gráfico sólo sabe contar aparcamientos.

`buildActivityBuckets` devuelve `List<WeekDayStats>` con un `sessions: Int` por bucket, y los cuatro
constructores hacen lo mismo — `groupingBy { fecha }.eachCount()`. Los kilómetros del periodo existen
(salen en el título de la card), el tiempo buscando plaza existe (tiene su tile), y **ninguno de los
dos se puede ver repartido en el tiempo**: no se sabe si los 118 km fueron un viaje o doce, ni si la
hora perdida buscando se concentró en dos días.

Eso es exactamente lo que una estadística sirve para responder, y es también el cimiento de todo lo
que viene detrás (heatmap, insights, proyecciones): sin serie no hay tendencia, sólo un número.

## Doctrina violada

Ninguna hoy — el gráfico nunca prometió otra cosa. Las que este ticket **debe** respetar enteras:

- [UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001] — la ventana la sigue mandando
  `VehicleHistoryCalculator`; este ticket cambia QUÉ mide cada barra, jamás CUÁNTAS ni de cuándo.
- **Una superficie, un alcance** [STATS-UI-001] — la métrica elegida gobierna las barras Y el
  calificador del título de la misma card. No puede dibujarse una cosa y titularse otra.
- [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001] + F0 — las métricas medidas llevan cobertura; una
  barra no puede confesarla, así que la confiesa el título de la card (donde ya vive hoy la de km).

## ⛔ El hallazgo que decide QUÉ métricas hay

Lo obvio sería un chip por tile: gasto, litros, kWh, CO₂, búsqueda, km… **Cinco de ellos dibujarían
la misma curva.**

En la ficha se mira UN coche, y para un coche:

```
litros = km × consumo/100        CO₂ = litros × factor        gasto = litros × precio
```

Todo son múltiplos constantes de los kilómetros, así que sus barras tienen **exactamente la misma
forma** — sólo cambia el número impreso encima. Es la misma trampa que ya documentó
[STATS-A-COMPUTED-LITRE-WITH-NO-READER-001] con el «consumo medio»: una constante disfrazada de
medición. Un chip que no cambia el dibujo no es una vista, es un decorado.

Quedan **tres series realmente independientes**:

| Chip | Qué dibuja | Existe cuando |
|---|---|---|
| **Aparcamientos** | cuántas veces aparcaste en el bucket | siempre (es el gráfico de hoy) |
| **Kilómetros** | distancia MEDIDA del bucket | alguna sesión del periodo trae ruta |
| **Buscando plaza** | minutos perdidos en el bucket | alguna sesión del periodo trae la medida |

El gasto no pierde nada: su cifra sigue en su tile, con su precio y su origen.

## Diseño

1. **`ChartMetric`** (`presentation/vehicles/`, puro): las tres métricas, cada una con
   - su **reductor** `(List<UserParking>) -> Float` — y los dos medidos pasan por los lectores de F0
     (`sumMeasuredDistance` / `sumMeasuredSearchTime`), nunca por una suma escrita a mano;
   - su **suelo de escala**, porque `MIN_SCALE_MAX = 3` era un suelo en APARCAMIENTOS metido en el
     canvas: 3 tiene sentido para «veces», ninguno para metros o milisegundos. El suelo es de la
     métrica, como la unidad.
   - `availableChartMetrics(sessions)` — una métrica sin un solo dato no se ofrece. Sin chips que
     ofrecer (sólo aparcamientos), **la fila de chips no se compone**: un selector de una opción es
     ruido.
2. **`buildActivityBuckets(sessions, filter, metric, …)`** — los cuatro constructores dejan de
   contar y pasan a **agrupar sesiones por bucket y aplicar el reductor**. Un solo sitio decide
   qué mide una barra, que es lo que hace testeables las tres métricas de golpe.
3. **`WeekDayStats` → `ActivityBucket(label, value: Float, isCurrent)`**. El nombre ya mentía (hay
   buckets de semana y de mes desde hace dos tickets) y el campo `sessions: Int` no puede sobrevivir
   a una métrica que son metros. El canvas recibe además `formatValue: (Float) -> String` para la
   etiqueta sobre la barra — el formato es del locale, no del dibujo.
4. **Selección**: `HistoryState.chartMetric` + `VehiclesIntent.SelectChartMetric` (ficha) y
   `StatsState.chartMetric` + `StatsIntent.SelectChartMetric` (panel). Si la métrica elegida deja de
   existir al cambiar de periodo, **cae a APARCAMIENTOS** en vez de dibujar un gráfico vacío.
5. **El calificador del título sigue a la métrica**: hoy es siempre km con su cobertura; pasa a ser
   el total de la métrica dibujada (km / minutos, con su cobertura; en APARCAMIENTOS se queda el de
   km, que es el de hoy). El sujeto («12 aparcamientos») no se toca: es de qué va la card.

## Criterio de éxito

- **Test**: el mismo periodo con la misma ventana da tres series distintas, y la de aparcamientos es
  idéntica a la de hoy (ni una barra se mueve donde no había métrica que elegir).
- **Test**: una métrica sin dato no se ofrece; y si la seleccionada desaparece al cambiar de filtro,
  el estado cae a APARCAMIENTOS.
- **Test**: las series medidas suman por los lectores de F0 (un bucket sin ruta vale 0 en la barra,
  pero la cobertura del periodo sigue saliendo en el título).
- Suite + guardarraíles con `--rerun-tasks` + 9 locales + galería (una variante por métrica).
- Compila mock, prod e iOS.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `VehicleHistoryCalculator.buildActivityBuckets` (4 constructores) | 🔧 agrupan y reducen, ya no cuentan |
| `WeekDayStats` (30 referencias, 5 ficheros) | 🔧 renombrado a `ActivityBucket` + `value: Float` |
| `ActivityBarCanvas.drawBarChart` / `MIN_SCALE_MAX` | 🔧 `Float` + formateador; el suelo se va a la métrica |
| `HistoryWeeklyChart.ActivityCard` | 🔧 fila de chips + calificador por métrica |
| `HistoryContent` (ficha) · `StatsScreen` (panel) | 🔧 los DOS pasan métrica y la ofrecen — o el panel sería una segunda interpretación de la misma card |
| `HistoryState` · `StatsState` + sus intents/VMs | 🔧 la selección vive en el estado, no en un `remember` |
| `FakeData` (buckets de preview) | 🔧 paridad |
| `HistoryTimeline.resolve` | ✅ exento: la serie se construye en presentación, donde ya se construían las barras; el agregado no cambia |
| Room / DTO / sync | ✅ exentos: nada se persiste |
| `history_activity_distance_partial` (plural ×9) | 🔧 renombrado a `history_activity_measure_partial`: su texto siempre fue «`<medida>` (`<n>` viajes)» y ahora también califica minutos — una key con el nombre de UNA magnitud es cómo un traductor la vuelve específica sin querer |
| `durationText` (privado en `VehicleStatsBlock`) | 🔧 sube a `presentation/util/TimeStringComposables`: ahora lo escriben DOS superficies, y dos redondeos iguales es cómo «1 h 12 min» y «72 min» acaban en la misma pantalla |

## Verificación (15-09-2026, sin commitear)

- Suite completa `:shared:testDebugUnitTest`: **276 suites, 2.999 tests, 0 fallos**. Testigos:
  `ChartMetricTest` tests="6" failures="0" (nuevo) · `VehicleHistoryCalculatorTest` tests="17"
  failures="0" (+1: una ventana, tres series) · `HistoryChartWindowTest` tests="5" failures="0" (la
  serie de aparcamientos sigue cuadrando barra a barra con lo que el filtro cuenta) ·
  `StatsViewModelTest` tests="9" failures="0".
- Guardarraíles con `--rerun-tasks`: Typography 5 · Color 8 · Divider 1 · Import 1 ·
  **LocaleParity 5**, todos failures="0" (la key renombrada y las dos nuevas, en los 9).
- `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin` y
  `:shared:compileKotlinIosSimulatorArm64` en verde.
- Galería: 3 variantes nuevas — serie de kilómetros · serie de tiempo buscando · un coche sin
  métricas que ofrecer (la fila de chips no se compone).
- ⏳ **Sin ver en device**: si dos filas de chips seguidas (periodo y métrica) pesan demasiado en la
  cabecera de la card. Es la única duda de layout del ticket.
- ⚠️ Cazado al vuelo: el sentinel `MINUTES_SLOT` se escribió una vez como **byte NUL crudo** y dejó
  el fichero marcado como binario — exactamente lo que `VehicleStatsBlock` documenta desde
  [STATS-A-COMPUTED-LITRE-WITH-NO-READER-001]. Corregido al escape ` ` antes de compilar.
