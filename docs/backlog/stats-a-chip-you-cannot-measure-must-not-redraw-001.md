# STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001 · Un chip que esta cuenta no puede medir no repinta nada

**Estado:** ✅ Done (2026-09-16) · rama `bugfix/STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001-chip-fires-paywall`
· worktree `../Paparcar-stats-chip-measure` · abierto el 2026-09-16 desde master `e196358b`

## Problema

En una cuenta **free**, tocar cualquier chip de periodo (`Este mes`, `Mes pasado`, `3 meses`,
`Este año`, `Todo`) no abre el paywall: `StatsIntent.SetFilter` (`StatsViewModel.kt:125`) y
`VehiclesIntent.SetHistoryFilter` (`VehiclesViewModel.kt:305-330`) se limitan a re-resolver. Lo que
ocurre entonces es que **la mitad de la pantalla se repinta sobre una ventana que la cuenta no puede
ver, y la otra mitad no**. Las dos listas salen de la misma resolución por caminos distintos
(`HistoryState.kt:129` y `:133`):

- `filteredSessions` (la **gráfica**) = `gated.visible` ∩ ventana del **CHIP** → *7 días ∩ este mes*
- `statsSessions` (las **cifras**) = `gated.visible` ∩ `Last7Days` → los 7 días enteros

Ninguna de las dos es el periodo que el usuario pidió, y **no son la misma lista**. Cuatro fallos
medidos leyendo el código, no uno:

1. **La gráfica dibuja ceros falsos.** `buildActivityBuckets` construye el eje con la ventana del
   CHIP (`VehicleHistoryCalculator.kt`): «Este mes» pinta 4-5 barras semanales y free sólo puede
   llenar la última; «Este año» pinta 12 meses y llena uno. Eso no se lee como *«esto está oculto»*,
   se lee como *«no aparcaste»*.
2. **«Mes pasado» en free deja la gráfica VACÍA** (7 días ∩ mes pasado = ∅ salvo los días 1-7 del
   mes) mientras las tiles siguen llenas con los siete días.
3. **En el panel, el título del gráfico contradice sus propias barras.** `StatsScreen.kt:215` pasa
   `total = stats.parkings` (las CIFRAS, 7 días) sobre barras construidas con `filteredSessions` (el
   CHIP). La ficha hace lo correcto: `HistoryContent.kt:347`, `total = scopeTotal =
   filteredSessions.size`. Día 3 del mes con «Este mes»: el título dice «9 aparcamientos» encima de
   barras que suman 4. Y el `qualifierText` de km sí sale de `filteredSessions` → **dos sujetos en un
   mismo título**.
4. **Las conclusiones se prueban con otros datos.** `StatsViewModel.kt:182`: los `insights` se
   derivan de `filteredSessions` (chip) y se presentan encima de `periodStats` (7 días).

Y, encima de todo eso, la duda con la que nació este ticket: **nadie le explica al usuario, EN ESE
MOMENTO, por qué las cifras no se han movido.** Tiene que deducirlo de un título que se re-rotuló
solo («últimos 7 días», `vehicleStatsPeriodLabel`) y de una fila Plus que está más abajo.

### Lo que agrava el problema ahora

Al quitarse el gate de la fila «Ver desglose completo»
([MONEY-A-HIDDEN-DOOR-IS-A-HARD-GATE-001]), una cuenta free **entra al panel completo**. En la ficha
el desajuste tocaba dos superficies (gráfica + card de cifras); en el panel toca seis —recap,
insights, gráfica, tiles, pace, places/heatmap— y **dos de ellas mezclan los dos conjuntos en una
sola frase** (el título del gráfico y los insights). Abrir la puerta fue correcto por doctrina; lo
que ha hecho es subir el coste de no arreglar esto.

## Doctrina violada

- **Una historia que sólo está OCULTA no puede decir que está vacía.** El Historial de al lado ya lo
  respeta a mano (`HistoryContent.kt:288-295`, `PlusHistoryFooter` en vez de `EmptyHistoryState`), y
  es la misma familia que [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]. La gráfica no
  la respeta: pinta el cero de un día que el plan tapa exactamente igual que el de un día sin coche.
- **[ACTIVITY-CARD-TITLE-001]** — la card tiene un sujeto (el recuento) y un cualificador (los km).
  En el panel salen de conjuntos distintos, así que son dos sujetos.
- **[STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001]** — la conclusión y la prueba tienen que
  venir de la MISMA resolución.
- **[MONEY-001] gating soft**: *la acción dispara el paywall, el botón nunca se esconde*. Hoy el
  botón está y la acción **no dispara nada**; a cambio produce una respuesta falsa. El propio
  `stats-the-first-chip-is-the-one-that-answers-now-001.md:50` ya daba por escrito el comportamiento
  correcto: *«free ve "este año" y **se topa con el paywall al tocarlo**»*. El código nunca lo hizo.

⛔ Lo que **NO** se viola arreglando esto: *«nunca se gatea VER»*. Detrás de esos chips no hay nada
que ver — hay la MISMA respuesta sobre un eje más largo. Lo que se vende es **memoria** (más allá de
los 7 días de historial), que es literalmente el eje declarado en `plus-alerts-and-tiers-001.md`.

## Señales / datos disponibles

Con la ventana free de `FREE_HISTORY_WINDOW_DAYS` = 7:

| Chip | ¿Cabe en lo que free puede ver? |
|---|---|
| `Last7Days` | ✅ siempre (`hoy-6 00:00` ≥ `now-7d` para cualquier hora) |
| `ThisWeek` | ✅ siempre (la semana ISO nunca arranca hace más de 6 días) |
| `ThisMonth` | ✅ **sólo los días 1-7** del mes |
| `LastMonth` · `Last3Months` · `ThisYear` · `Year(n)` · `All` | ❌ nunca |

O sea: la fila tiene para free **dos chips vivos y medio**, y los demás repintan un eje falso. Free
además **gana** algo real con el arreglo — los días 1-7 tendrá un «Este mes» de verdad, que hoy le
sale re-rotulado a «últimos 7 días» con una gráfica mensual medio vacía.

La pregunta *«¿cubre mi ventana el arranque de este periodo?»* ya está escrita en el repo, pero
especializada a un solo periodo: `closedMonthIsFullyVisible` (`ClosedMonth.kt:94`,
`visibleFromMs == null || visibleFromMs <= window.startMs`), que es este predicado con `LastMonth`
metido dentro.

## Diseño

**Un chip que esta cuenta no puede medir no repinta nada: dispara el paywall y la selección no se
mueve.** Es el patrón soft canónico (el botón está, se pulsa, la acción abre el paywall), el mismo
de añadir coche o crear zona.

1. **Un solo predicado puro**, en `domain/entitlement/`, hermano de `historyVisibleFromMs`: dado el
   arranque de un periodo y la ventana visible, ¿está el periodo entero dentro? `All` (arranque
   `null` = principio de los tiempos) sólo cabe si la ventana es `null` (Plus).
   - `closedMonthIsFullyVisible` pasa a ser **una llamada a él**, no una segunda aritmética.
   - ⚠️ El predicado vive en `domain/`, pero `windowStartMs` vive en `presentation/vehicles/`
     (`VehicleHistoryCalculator`). El predicado recibe el `startMs` ya calculado — no importa
     `HistoryFilter` en domain.
2. **El veredicto, en el intent**, no en la barra de chips: `StatsIntent.SetFilter` y
   `VehiclesIntent.SetHistoryFilter` comprueban el predicado y, si el periodo no es medible,
   `sendEffect(NavigateToPaywall(PaywallReason.STATS))` **sin tocar el estado**. `HistoryFilterBar`
   no se entera de que existe el plan: cero cambios de UI, cero strings nuevos.
3. **El alineado del recuento del panel**: `StatsScreen.kt:215` pasa a `state.filteredSessions.size`,
   como la ficha. Un título no puede describir un conjunto distinto del de sus barras, ni siquiera
   cuando ambos coinciden — que es lo que pasará tras (2) y por lo que hay que atarlo ahora.

### Lo que NO se toca, a propósito

- **La fila sigue ofreciendo todos los chips** derivados de los DATOS
  ([STATS-A-FILTER-THAT-CANNOT-ANSWER-IS-NOT-OFFERED-001] + [MONEY-001]). Nada se esconde.
- **Sin candado ni marca Plus en los chips.** 5 de 7 marcados en free se lee como muro; ningún otro
  gate de la app avisa antes del tap; y la `PlusStatsRow` bajo la card ya nombra la oferta. Es la
  parte reversible si en device se hace brusco.
- **La sustitución `statsFilter` → `Resolved.statsPeriod` (`HistoryState.kt:133`) y el re-rotulado
  del título SE QUEDAN.** Tras (2) deberían ser inalcanzables… salvo por un caso real: un free con
  «Este mes» seleccionado el día 7 a las 23:59 tiene un periodo que **deja de ser medible al pasar la
  medianoche**. La máquina se queda como guardarraíl estructural, no como redundancia.
- El `paceRows` ya lee `statsPeriod` y no el chip ([STATS-A-PACE-ROW-NAMES-THE-PERIOD-IT-PROJECTS-001]):
  correcto antes y después.

### Alternativas descartadas

- **Dejarlo como está** — hay tres fallos de corrección, no sólo una duda de UX.
- **Una explicación in situ junto a las cifras** cuando el chip pedido no es el medido — cura el
  síntoma más visible y deja intactos el eje falso, la gráfica vacía y el título contradictorio; y
  añade copy para justificar un comportamiento que no debería existir.

## Criterio de éxito

- Tocar `Este mes` (día 8+), `Mes pasado`, `3 meses`, `Este año`, un año cerrado o `Todo` siendo
  free: sale el paywall con `PaywallReason.STATS` y **el chip encendido no cambia**, en la ficha y
  en el panel.
- Tocar `Últimos 7 días` o `Esta semana` siendo free: no sale paywall, y el periodo se aplica.
- Tocar `Este mes` siendo free **el día 3**: no sale paywall y las cifras son las de ese mes, con el
  título nombrando el mes (no «últimos 7 días»).
- Plus: ningún cambio en ningún chip.
- Test del predicado con las siete familias de filtro y con la frontera del día 7/8 del mes.
- Test de que `closedMonthIsFullyVisible` sigue respondiendo lo mismo tras delegarle.
- Panel: el recuento del título del gráfico y la suma de sus barras no pueden discrepar.

## Consumidores auditados

| Sitio | Qué asumía | Cierre |
|---|---|---|
| `StatsViewModel.SetFilter` | todo chip es aplicable | ✅ gate + `NavigateToPaywall(STATS)`, sin tocar estado |
| `VehiclesViewModel.SetHistoryFilter` | ídem | ✅ mismo gate, mismo motivo |
| `StatsScreen` `total = stats.parkings` | cifras y barras son el mismo conjunto | ✅ → `state.filteredSessions.size`, como la ficha |
| `HistoryState.resolve` `statsFilter` | free pide periodos que no puede medir | ✅ se queda: el día 7 a medianoche un periodo medible deja de serlo |
| `closedMonthIsFullyVisible` | su propia aritmética | ✅ delega en `periodIsFullyVisible`; test de equivalencia |
| `StatsViewModel.insights` | chip y cifras coinciden | ✅ cubierto: en free el chip ya sólo puede ser un periodo medido |
| `vehicleStatsPeriodLabel` | free nunca nombra su periodo real | ✅ sin cambio — sigue nombrando la ventana, y ahora el día 3 del mes free tiene un mes de verdad |
| `StatsScreen` `showLifetime = !isPlus \|\| filter != All` | free nunca está en `All` | ✅ exento: compara identidad |
| `SettingsViewModel.detectionGapFocus()` | predicado privado suyo | ✅ → `detectionPermissionGap`, compartido con las dos superficies nuevas |
| `DetectionStateSnapshot.label()` | escalera propia intent→blockers | ✅ delega en `detectionPause`: una sola precedencia |
| `VehiclesState.isAutoDetectionOn` | el INTENT bastaba para decir «detecta» | ✅ → `detectionPause` (intent **y** blockers) |
| `StatsState.isAutoDetectionOn` | ídem | ✅ → `detectionPause` |
| `EmptyStatsCard` subtítulo condicional | la invitación a activar cabía en una frase | ✅ frase única; el remedio pasa al aviso CON botón, y `stats_empty_turn_on_detection` se borra ×9 |
| `HomeViewModel.EnableAutoDetection` | su propio encoding `"producer"`/`"all"` | 📋 **follow-up**: el mismo gesto con dos codificaciones (string vs `PermissionsFocus`). Fuera de alcance aquí |
| Dev Catalog | no había variante del aviso | ✅ 3 en el panel (apagada, bloqueada, apagada sin cifras) + 1 en la ficha |

## Lo añadido sobre el encargo inicial — el aviso de detección pausada

Pedido por el user al abrir el ticket: *«si el usuario no tiene detección activa habría que
notificárselo con un botón en el apartado de stats sin pisarlas (por si tiene ya stats de otras
veces)»*.

- **Encima de las cifras y fuera de su card**, nunca sobre ellas: quien lo lee TIENE cifras, de
  cuando la detección sí corría. Lo que el aviso añade es lo único que los números no pueden decir
  —que han dejado de crecer—, y taparlos le quitaría lo que vino a ver.
- **Dos textos, no uno.** `DetectionPause` tiene dos entradas porque hay dos poblaciones con
  remedios OPUESTOS: quien apagó el interruptor, y a quien le falta un permiso —cuyo interruptor
  sigue diciendo «encendido» mientras no mide nada [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001].
  Decirle «actívala» al segundo es una instrucción que no se puede seguir.
- **El botón hace la MISMA acción única** que el banner de Home y el interruptor de Ajustes
  [DET-TOGGLE-001]: apunta la intención y abre el flujo que concede lo que falte. Un CTA que sólo
  encendiera el flag parecería no hacer nada justo en la cuenta para la que existe el aviso.
- **No es una card roja**: nada está roto ni se destruye, y el rojo es el tono destructivo de esta
  app, nunca el de un CTA [UI-COLOR-DOCTRINE-001]. Misma card outlined que las cifras, con el glifo
  que Ajustes ya gasta en un interruptor de detección apagado.
- Copy ×9 (6 keys nuevas), apóstrofo CRUDO, sin `%`.

## Lado iOS

Todo vive en `commonMain` (`domain/entitlement`, `domain/detection`, `presentation/*`), así que iOS
recibe el mismo veredicto, el mismo aviso y el mismo copy al compilar el módulo. **Nada que abrir.**

## Verificación

- `./gradlew testAll` ✅ · `:app:assembleMockDebug` ✅ · `:app:compileProdDebugKotlin` ✅
- **Los tests nuevos SE EJECUTARON** (comprobado en los XML, no UP-TO-DATE):
  `PeriodIsMeasurableTest` 7/7, `StatsViewModelTest` 13/13 (4 nuevos),
  `VehiclesViewModelTest` 22/22 (2 nuevos).
- **Los 19 guardarraíles forzados con `--rerun-tasks`**: 51 tests, 0 fallos — incluidos
  `LocaleParityGuardrailTest` (5) y `ComposeResourcesEscapeGuardrailTest` (3), que son los que ven
  los 6 strings nuevos ×9 y el borrado del viejo.
- ⏳ Device. ⚠️ El árbol principal tiene el parche local `TEMP_PLUS_FOR_ALL = true` en
  `ObserveIsPlusUseCase.kt` (temporal, del user, para recorrer Plus sin pagar): con eso puesto
  **free no se puede ejercitar**. La galería mock sí llega a las dos variantes sin tocarlo.
