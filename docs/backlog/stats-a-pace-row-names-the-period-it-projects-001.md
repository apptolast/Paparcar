# STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001 · Una proyección se llama como el periodo que proyecta, y un recap que no cubre el mes no es un recap

**Estado:** ✅ Done — mergeado a master. ⏳ Sin ver en device.

## Problema

El user, mirando el panel de estadísticas: *«cuando veía cierre del mes cambiaba de filtro y cambiaba
el cierre del mes»*. Y sí: cambia, y además la etiqueta miente.

La card «Si sigue este ritmo» proyecta el final del periodo **seleccionado** —eso es correcto y
deliberado—, pero rotula la fila con `stats_pace_close` = **«cierre de mes»**, una constante, sea cual
sea ese periodo (`StatsScreen.kt:332`). Los periodos que llegan a esa fila son los que tienen
`windowEndMs` y caen en la puerta 0,25–0,90 de `projectPeriod`:

| Filtro | Lo que proyecta | Lo que dice la fila |
|---|---|---|
| Esta semana | fin de **semana** (mar 18:00 → dom 07:12) | «cierre de mes» ❌ |
| Este mes | fin de mes | «cierre de mes» ✅ |
| Este año | fin de **año** (abr → 25 nov) | «cierre de mes» ❌ |
| Mes pasado · año cerrado | — (fracción > 0,90) | no sale |
| Últimos 7 días · 3 meses · Todo | — (`windowEndMs` = null) | no sale |

No es un caso raro: «Esta semana» está SIEMPRE en la fila de chips, así que la etiqueta es falsa
cinco días de cada siete para cualquier cuenta.

Y con «Este año» hay un segundo síntoma en la misma card: la fila **«un año a este ritmo»** se añade
sobre `projectYear(medido, elapsed)`, que con ese filtro es aritméticamente **el mismo número** que la
proyección de cierre. Dos filas seguidas, casi la misma cifra, una de ellas mal rotulada.

### El tercero, y el peor: la fila proyecta una ventana que nadie midió

Auditando lo anterior contra la rama de `STATS-FIRST-CHIP-ANSWERS-NOW-001` salió el fallo de verdad,
en la misma función y **en producción hoy**.

`HistoryTimeline.resolve` sustituye el periodo de una cuenta **free** por `Last7Days` sea cual sea el
chip encendido — sus cifras son siempre sus siete días [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001].
Esa sustitución ocurría **dentro** de la función y no salía por ningún lado. `paceRows`, en cambio,
sacaba `windowStartMs`/`windowEndMs` de `state.filter`, o sea del **chip**. Resultado en una cuenta
free con «Este mes» puesto:

> `stats.costEstimate` = gasto de **7 días** · `elapsed` = 16 días de mes · `window` = 30 días
> → proyección = gasto_7d / 0,53, rotulada **«cierre de mes»**

Una previsión de un mes calculada sobre una ventana que no es ese mes, infravalorada por la
diferencia entre las dos. La card de cifras de encima nunca tuvo el bug porque **su título se
re-rotula solo** (`vehicleStatsPeriodLabel` devuelve «últimos 7 días» para free); la fila de debajo no
podía saberlo.

Y es la misma causa que lo siguiente: **una resolución que reinterpreta su ventana en silencio**.

### Y de paso, dos cosas del recap del mes cerrado

Auditando esa card salió su vecina, `StatsMonthRecapCard` («El mes pasado»), con dos defectos que
comparten causa —una afirmación escrita en un KDoc que nunca fue cierta—:

1. **El gate de Plus no existe.** El KDoc de `StatsViewModel.closedMonthRecap` y el de la card dicen
   que un free devuelve null *«por construcción»*, porque su ventana son 7 días y *«un mes cerrado no
   puede existir para una cuenta free»*. Es falso: la card sólo se compone los días **1–7** del mes
   nuevo, y en esos días la ventana rodante de 7 días **solapa la cola del mes cerrado** (el 1 de
   octubre ve desde el 24 de septiembre). Un free vería «El mes pasado» resumiendo cuatro días de
   septiembre. Peor que enseñarlo: una cifra equivocada.
2. **La fila de gasto no puede pintarse nunca.** `closedMonthRecap` no pasa
   `fuelPricePerLiter`/`energyPricePerKwh`, y sin precio `ComputePeriodStatsUseCase` devuelve
   `costEstimate = null` → `StatsMonthRecapCard:92` no compone la fila. Lo mismo en
   `RunMonthRecapUseCase:65-69` → la notificación del recap no puede decir el gasto.

Los dos muerden también en la notificación, que es el otro consumidor del mismo veredicto.

## Doctrina violada

- **«Lo que no puede contestar distinto no se ofrece»** y su reverso: lo que contesta distinto tiene
  que **decir a qué contesta**. Una fila cuya etiqueta no depende de lo que la fila mide es el mismo
  fallo que el chip que dibuja lo que su vecino [STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001].
- **Barrido de consumidores.** `HistoryFilter.ThisWeek`/`ThisYear` llegaron DESPUÉS de que se
  escribiera `paceRows` ([STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001] es de F5; `ThisYear` nació en
  `00ccbd34`). El comentario del código todavía dice *«A year is not one of the filter's periods»* —
  la frase quedó obsoleta el día que se añadió el chip y nadie barrió el consumidor.
- **Una prohibición sin TESTIGO no es un chequeo.** La correspondencia «este periodo cierra» ↔ «este
  periodo tiene nombre de cierre» estaba implícita en dos `when` separados. Dos `when` sobre el mismo
  sealed derivan; uno de ellos lo hizo.
- **Nada de segundas aritméticas de fecha** [STATS-CORE-001]. `closedMonthWindow` y
  `windowStartMs/EndMs(LastMonth)` calculan **exactamente la misma ventana** en dos sitios.
- **El KDoc no es el gate.** Escribir «esto no puede pasar» no impide que pase
  [DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001, misma familia].

## Señales / datos disponibles

Todo está ya en el estado: `HistoryFilter` sabe qué periodo es, `windowEndMs` sabe si cierra,
`gateHistoryToEntitlement` sabe desde cuándo se ve, y `HistoryTimeline.resolve` ya resuelve gate +
precio + ventana anterior en un solo sitio. No hace falta dato nuevo; hace falta dejar de calcular
dos veces.

## Diseño

### 1 · El cierre de un periodo es un dato del periodo, no del texto

`HistoryFilter.close(): PeriodClose?` (`WEEK` · `MONTH` · `YEAR` · null) vive **junto al sealed**, al
lado de `availableFilters`. Null exactamente para los tres que no cierran —los mismos que
`windowEndMs` devuelve null—, y un test **testigo** recorre todos los filtros comprobando el
bicondicional `close() != null ⟺ windowEndMs() != null`. Si mañana entra un periodo nuevo, el `when`
exhaustivo obliga a decidir su cierre y el testigo obliga a que las dos respuestas coincidan.

La presentación mapea `PeriodClose` → `stats_pace_close_week|month|year`. `stats_pace_close` muere.

La fila «un año a este ritmo» sólo se añade cuando el cierre **no es** ya un año: su puerta de tres
semanas seguía dejándola pasar con «Este año» seleccionado, donde es la misma cifra con otro nombre.

### 1-bis · Una resolución que reinterpreta su ventana lo DICE

`HistoryTimeline.Resolved` gana **`statsPeriod`**: el periodo que las cifras cubren de verdad —el chip
para Plus, la ventana del plan para el resto—. Sin defecto, como todo lo demás de ese tipo: un defecto
aquí sería otra respuesta silenciosa. `paceRows` saca de ahí sus tres fechas y su etiqueta, y
`StatsState` lo transporta.

Consecuencia deliberada: **una cuenta free deja de recibir cualquier previsión**. Es lo correcto —sus
cifras nunca son un periodo de calendario, y una ventana rodante no tiene final que proyectar— y es
preferible a la estimación equivocada que daba antes.

Es la misma doctrina que la cobertura de una suma parcial: *una cifra que no puede describirse se
queda a merced de quien la lea* [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001].

### 1-ter · La entrada al panel deja de ser de Plus

Decisión del user, y la doctrina ya la pedía. `VehicleStatsBlock` escondía la fila **«Ver todas tus
cifras»** tras `isPlus &&`, con el argumento de que el desglose es donde se explora *cualquier
periodo* y un free no puede elegirlo. Pero el panel **no es un selector de periodo**: es donde viven
el gráfico, los sitios, el mapa de calor y las conclusiones, y todo eso es free sobre sus 7 días — el
panel ya estaba construido para una cuenta free, con su título honesto y su propia fila Plus dentro.

Esconder la puerta era un gate **duro sobre un botón**, que es justo lo que `MONEY-001` prohíbe, y
además gateaba **VER**, que es lo único que los planes no tocan nunca. Era el ÚNICO gate de todo el
camino: `OpenStatsPanel → NavigateToStats` nunca miró el plan.

Ahora salen las dos filas, y no es sobrecarga: una abre lo que ya es tuyo, la otra vende el periodo.

### 2 · El recap existe cuando el mes cerrado se ve ENTERO

El gate no es *«¿es Plus?»* sino *«¿alcanza lo que este usuario puede ver a cubrir el mes que
resumo?»* — que es la razón, y además se ajusta sola si la ventana free deja de ser 7 días:

- `historyVisibleFromMs(isPlus, nowMs)` sale a la luz en `HistoryEntitlementGate.kt` (el instante más
  viejo visible, null = todo), y `gateHistoryToEntitlement` lo usa: una aritmética, un sitio.
- `closedMonthIsFullyVisible(visibleFromMs, nowMs, tz)` en `ClosedMonth.kt`, puro, con su test.
- Lo llaman los **dos** consumidores: la card y `RunMonthRecapUseCase`.

### 3 · El recap se resuelve por donde se resuelve todo lo demás

Y el gate de cobertura **no se deja solo**: el resumen exige además que `statsPeriod` SEA el mes
cerrado. Porque `closedMonthIsFullyVisible` sigue a `FREE_HISTORY_WINDOW_DAYS`, pero la sustitución de
`resolve` no — el día que esa ventana creciera, un free pasaría el gate y leería sus últimos 7 días
bajo el título «El mes pasado», que es un título fijo y no puede re-rotularse como sí hace el panel.
Afirmar «se arregla solo» sin eso habría sido repetir exactamente el KDoc que este ticket desmiente.

`closedMonthRecap` deja de filtrar a mano y llama a `HistoryTimeline.resolve(filter = LastMonth)`.
`windowStartMs/EndMs(LastMonth)` y `previousWindow(LastMonth)` son **la misma ventana** que
`closedMonthWindow`/`previousToClosedMonthWindow` (verificado línea a línea), así que no cambia ni una
cifra — y a cambio el recap hereda el gate, el precio del combustible y la ventana anterior de la
autoridad única. Con eso la fila de gasto puede pintarse por primera vez.

### 4 · Un precio que se cita dice de dónde sale

`ResolveFuelPriceUseCase.forSessions(vehicle, sessions)` recoge el par litro/kWh y el `countryCode`
—hoy privado dentro de `HistoryTimeline`— para que el worker del recap pueda pedir lo mismo sin
copiarlo. Y la frase «media nacional medida hace X / tu precio» se extrae a `fuelPriceNoteText(price)`,
compartida por las tiles y por el recap: una card que estrena una cifra de gasto no puede estrenar
también una segunda forma de cualificarla [FUEL-AN-AVERAGE-MUST-SAY-WHEN-IT-WAS-MEASURED-001].

## iOS

Todo el cambio vive en `commonMain` (`HistoryFilter`, `ClosedMonth`, `HistoryEntitlementGate`,
`SessionFuelPrices`, `RunMonthRecapUseCase`, las tres pantallas de Compose Multiplatform y los nueve
`strings.xml`), así que iOS lo recibe entero por construcción — no hay hueco que abrir.
`:shared:compileKotlinIosSimulatorArm64` verde. Nada en `androidMain` salvo el worker, que no cambia:
ya sabía pintar `spendText` y lo único que le faltaba era que el veredicto se lo diera.

## Criterio de éxito

- Con «Esta semana» la fila dice *cierre de semana*; con «Este año», *cierre de año*; y con «Este
  año» **no** aparece además «un año a este ritmo».
- Una cuenta **free** no recibe ninguna previsión, con el chip que sea — nunca más una del mes
  calculada sobre siete días.
- Una cuenta **free** ve la fila «Ver todas tus cifras» y entra al panel, con la fila Plus debajo.
- Un free nunca compone «El mes pasado», ni el día 1 ni el día 7, y el worker no le notifica.
- La card del recap y la notificación pueden decir el gasto, con su origen escrito.
- Tests: mapeo de `close()` + testigo contra `windowEndMs`; `closedMonthIsFullyVisible` en los bordes
  (día 1 y día 7, free y Plus); `forSessions` eligiendo país.
- `./gradlew testAll` verde y guards re-ejecutados con `--rerun-tasks`.

### Verificado

`testAll --rerun-tasks`: **316 suites, 3.302 tests, 0 fallos**, con los guards de Konsist forzados a
ejecutarse (`LocaleParityGuardrailTest` y `ComposeResourcesEscapeGuardrailTest` incluidos — importa,
porque «fin d'année» lleva **apóstrofo crudo**, que es lo que `composeResources` exige). Compilan
`:app:compileMockDebugKotlin`, `:app:compileProdDebugKotlin` y `:shared:compileKotlinIosSimulatorArm64`.

⛔ **Y los testigos se falsificaron uno a uno, no se dieron por buenos porque salieran verdes**
[TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]:

| Se rompió a propósito | Falló |
|---|---|
| `ThisWeek.close()` → `null` | `should_nameItsClose_exactlyWhenThePeriodHasOne` + `should_mapEachCalendarPeriod_toItsOwnUnit` |
| el gate de cobertura del worker → `if (false)` | `should_notKnock_when_theUserCannotSeeTheWholeClosedMonth` |
| `fuelPricePerLiter` del worker → `null` | `should_priceTheClosedMonth_soTheNotificationCanSayWhatItCost` |
| `statsPeriod = filter` (leer el chip, el bug original) | `should_sayWhichPeriodTheFiguresActuallyCover` + `should_leaveAFreeAccountWithNothingToProject` |

Tests nuevos: `PeriodCloseTest` (5) · `RunMonthRecapUseCaseTest` (4, no existía ninguno) ·
`SessionFuelPricesTest` (4) · 3 en `ClosedMonthTest` · 1 en `ClosedPeriodWindowTest` (la equivalencia
de las dos ventanas, que era una afirmación de KDoc y ahora es una comprobación).

### Sin ver en device

La fila con «Esta semana» y con «Este año», y la nota de precio bajo el resumen del mes —que **nunca
se ha renderizado en producción**, porque hasta ahora no podía—. La galería del Dev Catalog ya trae
las dos variantes que faltaban («Ritmo · a dónde va el año» y el precio en «Resumen · el mes pasado»),
así que se ven sin esperar al día 1.

### Preguntas que deja abiertas, no cerradas a la fuerza

1. **El gráfico de una cuenta free con un chip de calendario.** Se dibuja el eje del MES pero sólo hay
   filas de 7 días, así que los días anteriores del mes salen como barra vacía — y una barra vacía en
   un día PASADO es una afirmación («no aparcaste»), no un hueco. Es una decisión explícita y previa
   (`resolve`: *los chips gobiernan el gráfico para todos*), no un descuido de este ticket, y tocarla
   es un cambio de producto. Ticket aparte si se quiere.
2. **La card de ritmo ya no aparece al abrir el panel**, porque desde `STATS-FIRST-CHIP-ANSWERS-NOW-001`
   se abre en «7 días», que es rodante. No es un fallo de ninguna de las dos ramas: es la consecuencia
   correcta de que una ventana sin final no se pueda proyectar. Pero significa que la única estimación
   que da la app vive detrás de un toque, y eso es una decisión de producto que conviene tomar a
   propósito.
3. **Las variantes de ritmo de la galería dependen de la FECHA** (la puerta 0,25–0,90 de
   `projectPeriod`): «a dónde va el mes» necesita pasar del día ~8, «a dónde va el año» sólo vive de
   abril a finales de noviembre. El 3 de enero las dos se ven verdes sin componer su card. Previo a
   este ticket y de la forma de `projectPeriod`, pero deja la card sin vigilancia parte del año.

### Fuera de alcance, visto de paso

`StatsMonthRecapCard.kt` declaraba un `private const val PERCENT = 100` que no usaba nadie. Borrado a
petición del user.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `StatsScreen.paceRows` | el cierre siempre es de mes | corregido |
| `StatsScreen.paceRows` (fechas) | el chip ES el periodo medido | corregido → `statsPeriod` |
| `HistoryTimeline.resolve` | podía sustituir la ventana sin decirlo | corregido → `Resolved.statsPeriod` |
| `VehicleStatsBlock` / `vehicleStatsPeriodLabel` | idem | exento: su título YA se re-rotula para free |
| `ActivityCard` / `buildActivityBuckets` | idem | exento **por decisión explícita**: `resolve` dice que *«los chips gobiernan el GRÁFICO para todos»* y lo dibuja sobre `filteredSessions` (el chip, sobre las filas visibles). Queda una pregunta abierta, abajo |
| Galería, `statsPanelState` | no transportaba el periodo medido | corregido + variante free nueva |
| `VehicleStatsBlock` fila «Ver todas tus cifras» | el panel es de Plus | corregido: se ofrece siempre que haya cifras |
| `VehiclesViewModel.OpenStatsPanel` / `NavigateToStats` | — | exento: nunca miró el plan; el gate estaba solo en la card |
| Galería, helper `history()` | no pasaba `onOpenStatsPanel` | corregido: la fila no se componía en NINGUNA variante |
| `StatsScreen.paceRows` (fila anual) | «un año no es un periodo del filtro» | corregido |
| `StatsViewModel.closedMonthRecap` | el plan gatea solo; sin precios | corregido |
| `StatsMonthRecapCard` (KDoc + fila de gasto) | idem | corregido |
| `RunMonthRecapUseCase` | idem, y sin precios | corregido |
| `HistoryTimeline.resolve` | resolvía el precio en privado | extraído a `forSessions` |
| `VehicleStatsBlock` (nota de precio) | única dueña de la frase | extraída a `fuelPriceNoteText` |
| `StateGalleryScreen` (recap) | construía `PeriodStats` a mano | revisado |
| `VehicleHistoryCalculator.windowEndMs` | — | es la contraparte del testigo |
