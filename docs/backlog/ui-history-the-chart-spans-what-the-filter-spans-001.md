# UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001 · El gráfico de actividad dibuja una ventana distinta de la que el filtro recorta

**Estado:** ✅ Done · rama `bugfix/UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001-window` ·
worktree `../Paparcar-history-chart`
**Abierto:** 2026-08-31 sobre master `748648fc`

## Problema

En el historial de Vehículos, el filtro de tiempo y el gráfico de actividad calculan su ventana
**por separado**, y no coinciden. El gráfico se alimenta de `filteredSessions` (bien), pero decide
sus barras con su propio reloj (`HistoryContent.kt:496-501`).

**"Esta semana" — barras que no pueden llenarse.**
El filtro corta desde el **lunes** 00:00 local (`VehicleHistoryCalculator.kt:34-39`: semana natural
ISO). El gráfico dibuja **7 días rodantes**, de hace 6 días a hoy (`HistoryContent.kt:479-486`).
Un miércoles, las barras de jueves–domingo son de la semana PASADA: su altura es 0 no porque no
aparcaras, sino porque esos días están fuera de lo que el filtro dejó pasar. Un lunes el gráfico son
seis barras vacías y una llena. Aquí no se pierden sesiones —el total y las barras suman lo mismo—
pero el gráfico afirma cuatro días sin actividad que nunca miró.

**"Últimos 3 meses" — sesiones que el total cuenta y ninguna barra muestra.**
El filtro son **90 días rodantes** (`MONTHS_3_MS`, `VehicleHistoryCalculator.kt:49-51`). El gráfico
son **3 meses naturales** (`buildMonthlyBuckets(months = 3)`, última barra = mes actual). Hoy 31-08
el desfase es de un día; **el 1 de septiembre son 28 días de junio**: sesiones que pasan el filtro,
cuentan en el "N parkings" del título y en los km, y no tienen barra donde aparecer. El desfase es
máximo a principio de mes y se cierra a final.

En "Todo" el recorte a 6 meses SÍ es deliberado y está documentado (`ALL_MONTHS_CAP`,
`HistoryContent.kt:465-466`: *"older sessions still count in the total"*). Este no: nadie lo
decidió.

**El código ya afirma que esto no pasa.** `HistoryContent.kt:171-173` dice *"buckets are built from
the SCOPED sessions with a granularity that matches the window... Its total/scope label come from
the same filter"*, y la línea 265 repite *"Scoped km follow the same filter as the bars"*. La
intención estaba escrita; la implementación no la cumple, y el comentario lleva desde
`VEHICLES-REDESIGN-001` tapando el hueco.

## Doctrina violada

**Sistemas, no parches**: la ventana temporal es UN concepto y hoy vive en DOS sitios que no se
hablan — `VehicleHistoryCalculator.filter` y `buildActivityBuckets`. Cualquier arreglo que ajuste
solo uno de los cuatro casos deja el hueco abierto para el siguiente filtro que se añada.

También roza la honestidad de superficie: una barra a 0 es una afirmación ("ese día no aparcaste")
que aquí se hace sobre días que el filtro nunca incluyó. Mismo espíritu que
`UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001`.

## Señales / datos disponibles

Todo es local, puro y determinista. No hace falta campo ni telemetría: el defecto se reproduce
fijando `nowMs`.

Obstáculo real: `buildWeeklyStats`, `buildMonthWeekBuckets` y `buildMonthlyBuckets` son funciones
privadas de `HistoryContent.kt` que llaman a `Clock.System.now()` **dentro** (líneas 473, 537). No
hay forma de testearlas hoy — por eso el desfase pudo vivir sin que ningún test lo notara, mientras
`VehicleHistoryCalculatorTest` sí cubre el filtro con `nowMs` inyectable.

## Diseño

**El invariante: la ventana la define el filtro; el gráfico solo elige la granularidad con la que la
divide.** Un solo sitio decide desde cuándo, y ese sitio es `HistoryFilter`.

1. Cada `HistoryFilter` expone su ventana (instante de inicio) y su granularidad (día / semana /
   mes). `VehicleHistoryCalculator.filter` y los constructores de buckets leen **la misma** ventana.
2. Alinear las dos ventanas discrepantes a **calendario natural**, que es lo que ya hacen
   `ThisWeek` (filtro) y `ThisMonth` (ambos):
   - `ThisWeek` → 7 barras lunes→domingo; los días aún no alcanzados se pintan como track vacío,
     igual que ya hace `buildMonthWeekBuckets` con las semanas futuras del mes
     (`HistoryContent.kt:503-506`). Un día futuro vacío no miente; uno pasado y fuera de rango sí.
   - `Last3Months` → filtro por **3 meses naturales** en vez de 90 días rodantes, para casar con
     las barras que ya son naturales. (La alternativa —gráfico rodante de 90 días— rompería la
     coherencia con `ThisMonth` y daría barras mensuales parciales sin etiqueta honesta.)
3. Mover la construcción de buckets a `VehicleHistoryCalculator` con `nowMs` inyectable, para que
   quede bajo test como ya lo está el filtro.

⚠️ Consecuencia asumida en el punto 2: en "Últimos 3 meses" el usuario verá **menos** historial que
hoy a principio de mes (3 meses naturales < 90 días). Es correcto: hoy ve un número que incluye
sesiones que no puede localizar.

## Criterio de éxito — cumplido

`HistoryChartWindowTest` (5 tests, `nowMs` inyectado) mide el invariante como **una suma**, no como
un layout: *toda sesión que el filtro conserva tiene una barra donde ser contada*.

- Lunes 31-08 (el peor caso del gráfico rodante: 6 de sus 7 barras eran de la semana pasada) → 7
  barras lunes→domingo y ninguna anterior al inicio de la ventana.
- Miércoles → lunes/martes/miércoles llenos, jueves→domingo vacíos. Un día FUTURO vacío no afirma
  nada; un día PASADO fuera de la ventana sí lo hacía.
- 1 de septiembre, "Últimos 3 meses" → barras `jul·ago·sep` y la suma **iguala** el total.
- El invariante corrido sobre los tres filtros acotados a la vez. `All` queda exento
  explícitamente: su recorte a 6 meses sí es deliberado y estaba documentado.

⛔ **Validado por falsación**: repuesta la ventana rodante de 90 días, **3 de los 5 tests fallan**.

El comentario mentiroso de `HistoryContent.kt:171-173` ahora dice lo que el código hace, y remite al
único sitio donde se decide la ventana.

Sin strings nuevos.

## Consumidores auditados

- `VehicleHistoryCalculator.kt` — **el único sitio donde se decide una ventana**: `windowStartMs`
  nuevo, y `filter` pasa a leerlo en vez de calcular la suya. `MONTHS_3_MS` (90 días rodantes)
  eliminado.
- `HistoryContent.kt` — los tres constructores privados de barras **movidos** al calculador con
  `nowMs` inyectable. Eran privados de un fichero de Compose y llamaban a `Clock.System.now()`
  dentro: por eso ningún test podía alcanzarlos, y por eso el desfase vivió sin que nada lo notara
  mientras el filtro sí estaba cubierto. Sus constantes (`DAYS_PER_WEEK`, `WEEKS_IN_MONTH`,
  `MONTHS_PER_YEAR`, `ALL_MONTHS_CAP`) se fueron con ellos; `MONTHS_PER_YEAR` desapareció al
  sustituir la aritmética manual de meses por `LocalDate.minus(n, MONTH)`.
- `VehicleHistoryCalculatorTest.kt` — `should_dropOlderThan3Months_forLast3MonthsFilter` sigue
  verde sin tocarlo: su fixture (−10 d vs −100 d) cae del mismo lado con meses naturales.
- `HistoryState.kt`, `HistoryFilterBar.kt`, `VehiclesViewModel.kt` — sin cambios; el filtro se
  aplica igual, sólo cambió de dónde saca su corte.
- `VehiclesPreviews.kt` + `StateGalleryScreen.kt` — sin cambios: las variantes construyen
  `HistoryTimeline` con sesiones fijas y no dependen de la ventana. La galería no puede enseñar
  "es lunes" ni "es 1 de mes" porque el caso lo define el RELOJ, no el estado — por eso el testigo
  es un test con `nowMs`, que es donde de verdad se puede fijar.

## Relacionados

- `UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001` — el otro defecto del mismo bloque de cabecera (los
  km). Independiente: uno es la ventana, otro la cobertura del dato. Se pueden hacer por separado,
  pero tocan las mismas líneas de `HistoryContent.kt` (264-298) — si se hacen a la vez, un solo
  worktree.
- `VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001` — hermano de la misma revisión, sin solape.
