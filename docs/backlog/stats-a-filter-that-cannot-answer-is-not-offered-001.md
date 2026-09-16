# STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001 · Los periodos no crecen con el usuario, y «Todo» miente al que más tiene

**Estado:** ✅ Done · rama `feature/STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001-periods-that-grow` ·
worktree `../Paparcar-periods`
**Abierto:** 2026-09-16 · desde master `937985c7`

## Problema

Hay cinco periodos, fijos, los mismos el primer día que el tercer año:

```kotlin
sealed class HistoryFilter { All · ThisWeek · Last7Days · ThisMonth · Last3Months }
```

Plus se vende como **memoria** —«todo tu historial, y lo que aprende de él»— y esa memoria no tiene
con qué mirarse:

1. **Entre «Últimos 3 meses» y «Todo» no hay nada.** Un usuario de tres años no puede mirar un año,
   ni comparar 2025 con 2024. El salto es de 90 días a *todo*.
2. **«Todo» miente justo al que más tiene.** `ALL_MONTHS_CAP = 12`: el gráfico dibuja como mucho doce
   barras mensuales, así que a los tres años el chip dice «Todo» y enseña el último año. Las CIFRAS
   sí son de todo el historial — es el dibujo el que está capado, y nada lo dice.
3. **`projectYear` existe y no hay forma de llegar a él.** Se escribió en
   [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001] con su umbral de 21 días, y sólo tiene sentido sobre un
   **año natural**, que no es ninguno de los cinco filtros. Código inalcanzable desde la UI.
4. **El mes cerrado sólo se puede mirar siete días.** La card del resumen se retira la primera semana
   [STATS-PLUS-THE-RECAP-KNOCKS-001] y después no queda ningún periodo que diga «el mes pasado».

## Doctrina violada

- **Plus = memoria** [plus-alerts-and-tiers-001]: se cobra por recordar, y el selector no deja
  preguntar por lo recordado.
- **Nada se enseña por debajo de su umbral, y lo que no se puede medir no se ofrece** — la regla que
  ya gobierna las métricas del gráfico (`availableChartMetrics`: *una métrica sin datos no se
  ofrece*) y las tiles (*una tile existe sólo cuando existe su cifra*). El selector de periodo es el
  único sitio del panel que ofrece lo mismo tenga o no algo que contestar.

## Diseño — el periodo se DERIVA de los datos, no de una lista fija

**El invariante, en una frase:** *un filtro que no puede contestar no se ofrece; y el que contesta
dibuja en la unidad de su propio span.*

### 1 · El conjunto lo dicen las sesiones

`availableFilters(sessions, now)` —función pura, al lado de `HistoryFilter` y con la misma forma que
su hermana `availableChartMetrics`, que vive junto a `ChartMetric`— devuelve, en orden de span:

| Periodo | Se ofrece cuando | Proyectable |
|---|---|---|
| Últimos 7 días · Esta semana · Este mes | siempre (son el suelo del panel) | sólo los de calendario |
| **Mes pasado** (nuevo) | hay al menos una sesión en el mes natural anterior | no: está cerrado |
| Últimos 3 meses | hay datos de más de un mes atrás | no: es rodante |
| **Este año** (nuevo) | hay datos de más de tres meses atrás | **sí** — es lo que despierta `projectYear` |
| **2025, 2024…** (nuevo) | hay datos en ese año natural, y no es el actual | no: cerrado |
| Todos | siempre | no |

Así una cuenta nueva ve cuatro chips y ninguno muerto; la de tres años ve siete y todos contestan.
La fila ya hace scroll horizontal con su fundido, que es exactamente para esto.

⛔ **Los años cerrados salen de los datos, no de una lista** — una constante `YEARS = [2024, 2025]`
sería una bomba de relojería con fecha de caducidad conocida.

⛔ **Y van detrás de UN chip, no uno por año** (decisión del user, 16-09): el chip dice «Por años»,
se despliega con los años que existen, y **pasa a decir el año elegido** en cuanto se elige — un chip
seleccionado que siguiera diciendo «Por años» sería el único de la fila que no dice qué seleccionó.
Cuatro años de coche empujarían fuera del borde los periodos que hablan de AHORA.

### 2 · «Todo» deja de ser un año disfrazado

La unidad del cubo sigue al span, que es la regla que el gráfico ya aplica por debajo (días →
semanas → meses):

- span ≤ 12 meses → barras **mensuales** (lo de hoy).
- span > 12 meses → barras **anuales**, tantas como años haya.

Con eso `ALL_MONTHS_CAP` deja de ser un tope silencioso y pasa a ser el punto donde cambia la unidad.

### 3 · Lo que NO se toca

- **Free sigue viendo sus 7 días.** El gate es `gateHistoryToEntitlement` y no se mueve: los periodos
  nuevos son de la misma naturaleza que «Últimos 3 meses», y disparan el mismo paywall blando.
- **Ninguna proyección sobre un periodo rodante** [STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001]: `windowEndMs`
  devuelve null para los rodantes y eso es lo que impide proyectarlos. «Este año» es de calendario,
  así que entra por la puerta que ya existe; «Mes pasado» y los años cerrados no proyectan nada
  porque ya han terminado.

## Criterio de éxito

- Tests puros de `availableFilters`: cuenta nueva → 4; con un mes cerrado → aparece «Mes pasado»;
  con 14 meses → aparecen «Este año» y el año cerrado; sin datos de 2024 → 2024 no se ofrece.
- «Todo» con 3 años de datos dibuja 3 barras anuales, no 12 mensuales.
- «Este año» proyecta (y con menos de 21 días transcurridos, NO proyecta).
- Strings nuevos en los 9 locales · variantes en la galería de estados · `testAll` · device.

## Lo que salió por el camino

- **La ventana necesitaba sus DOS extremos.** Mientras todos los periodos llegaban hasta «ahora»,
  filtrar por el principio era lo mismo que filtrar por los dos; con un periodo que CIERRA en el
  pasado, «mes pasado» habría ido creciendo todo este mes. `filter()` ya usa `windowEndMs`.
- **La franja de la barra de filtros**, que era el otro fleco anotado: el color de la página estaba
  hardcodeado al de Vehículos, y en el panel —cuyo `Scaffold` es `surface`— se veía como una banda a
  lo ancho. Ahora `containerColor` es un parámetro **sin valor por defecto**, así que la tercera
  pantalla no puede heredar el fallo callando. Entra aquí porque es el mismo componente que este
  ticket reescribe; los otros tres flecos de UI siguen en su ticket.
- **La galería no podía comprobar un selector.** Es un catálogo de estados y su `onFilterSelected`
  no hace nada — correcto para todo lo demás, inútil justo para un control cuyo trabajo es cambiar
  de periodo: «Por años» se desplegaba y no había forma de ver que lo elegido llegaba a las barras.
  Las variantes de periodo (y sólo ésas) se re-resuelven con la elección: sin ViewModel, porque
  `HistoryTimeline.resolve` es pura y el estado es un `remember` de la propia pantalla.
- **Un fixture roto se lee como un bug**: al mover las sesiones dos años atrás moví sólo su
  comienzo, y la galería enseñó «9686 h de duración media». Se mueve también `endedAtMs`.

## Verificación

- `testAll` ✅ · guardarraíles con `--rerun-tasks` ✅ · `:shared:compileKotlinIosSimulatorArm64` ✅ ·
  `assembleMockDebug` ✅.
- Tests nuevos: `AvailableFiltersTest` (6 casos: cuenta nueva → 4 chips; mes cerrado vacío → sin
  «Mes pasado»; dentro del trimestre → sin «Este año»; años cerrados sólo los que tienen datos; el
  año en curso nunca sale como cerrado) y `ClosedPeriodWindowTest` (7 casos: los dos extremos de la
  ventana, el año cerrado, la comparación contra el año anterior, doce barras por año, y «Todos» por
  años sólo al pasar de doce meses).
- **Redmi 2201117TY**, mock con sha256 verificado (`a0cd1f01…`):
  - Panel *Tus cifras*: **la franja ha desaparecido** — medido, la fila de filtros es ya `#0D1117`,
    el mismo tono de la página.
  - Variante «Tres años»: **«Todos» dibuja 2024 · 2025 · 2026** (8 aparcamientos cada uno, 24 en
    total, 2026 como actual) en vez de doce meses.
  - «Por años» se despliega con 2025 y 2024; al elegir 2025 el chip **pasa a decir 2025** y el
    gráfico se redibuja como los doce meses de 2025, con las 8 sesiones en septiembre.

## Consumidores auditados

| Punto | Qué asume hoy | |
|---|---|---|
| `HistoryFilter` | cinco casos cerrados | 🔧 gana `LastMonth`, `ThisYear`, `Year(value)` |
| `VehicleHistoryCalculator` | `windowStartMs/EndMs` por caso + `ALL_MONTHS_CAP` | 🔧 ventanas nuevas + unidad por span |
| `HistoryFilterBar` | lista fija de 5 con sus iconos | 🔧 recibe el conjunto ya derivado |
| `vehicleStatsPeriodLabel` | etiqueta por caso | 🔧 etiqueta de año |
| `StatsScreen` / `HistoryContent` | ambos construyen la barra | 🔧 pasan el conjunto |
| `projectPeriod` / `projectYear` | sólo calendario | ✅ sin cambios: por fin se alcanza |
| Galería de estados | variantes por filtro | 🔧 variante de año y de mes pasado |
| Free gate | 7 días | ✅ sin cambios |
