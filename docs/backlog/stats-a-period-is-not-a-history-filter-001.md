# STATS-A-PERIOD-IS-NOT-A-HISTORY-FILTER-001 · `HistoryFilter` no filtraba el historial ni era un filtro

**Estado:** ✅ Done (2026-09-16) · rama `chore/STATS-A-PERIOD-IS-NOT-A-HISTORY-FILTER-001-rename`
· worktree `../Paparcar-period-rename`

## Problema

El nombre fallaba dos veces:

- **«History»** — lo único que ese tipo NO gobernaba era el historial. Lo decía el propio código
  (`HistoryContent.kt`): *«The History timeline is ALWAYS the complete list — the time filter scopes
  only the Activity chart, never the timeline below it»*. Gobierna la gráfica, la card de cifras, el
  panel entero y la proyección. La lista de abajo, jamás.
- **«Filter»** — no era un filtro, era un **periodo**. Filtrar es lo que hace
  `VehicleHistoryCalculator.filter(sessions, period, nowMs)` **con** él.

Y el código ya lo sabía: todo lo que lo rodea se llama *period* — `periodFilters`, `periodStats`,
`PeriodClose`, `vehicleStatsPeriodLabel`, `availableFilters()` documentado como «la fila de
periodos». El síntoma más claro era una línea nacida en el ticket de la fila de ritmo:

```kotlin
val statsPeriod: HistoryFilter   // el nombre del campo y el del tipo se contradecían
```

## Diseño

| Antes | Después |
|---|---|
| `HistoryFilter` | `StatsPeriod` |
| `HistoryFilter.kt` | `StatsPeriod.kt` |
| `HistoryFilterBar` / `HistoryFilterBar.kt` | `StatsPeriodBar` / `StatsPeriodBar.kt` |
| `availableFilters()` | `availablePeriods()` |
| `HistoryState.activeFilter` | `activePeriod` |
| `AvailableFiltersTest` | `AvailablePeriodsTest` |

`ActivityPeriod` era la alternativa (es el nombre de la SECCIÓN en la ficha), pero `statsPeriod` ya
estaba acuñado y el panel «Tus cifras» es quien es dueño del concepto.

### Lo que NO se renombra, a propósito

Las **8 claves de string `history_filter_*`** (`history_filter_this_month`…). Son identificadores de
recurso, no los ve nadie, y renombrarlas cuesta tocar los 9 ficheros de locale para cero beneficio
de usuario. Si alguna vez se tocan, que sea por otra razón y en otro ticket.

## Alcance medido (2026-09-16)

- **25 ficheros** sobre el master final, 316 ocurrencias antes del cambio, **0 residuos después**
  (`grep -rn "HistoryFilter\|availableFilters\|activeFilter"` sobre `.kt` → vacío).
- **Cero serialización y cero persistencia**: ni `@Serializable`, ni DataStore, ni SharedPrefs, ni
  `.name` a analytics dependían del nombre. Rename puro, sin formato de wire en riesgo.
- **Cero cambios de comportamiento**: la suite entera pasa sin tocar una sola aserción.

## Cómo se cerró

El primer intento salió del tip de la rama del chip y quedó sobre una base que no era master. **Ese
diff no se rebasó: se tiró y se volvió a ejecutar el script** sobre el master que ya llevaba dentro
los dos tickets anteriores. Es lo correcto para un rename mecánico — rebasar 25 ficheros para llegar
exactamente al mismo sitio es trabajo y riesgo a cambio de nada.

El procedimiento, por si hay que repetirlo:

1. Worktree nuevo desde master.
2. Reemplazo textual en este orden — **el orden importa**: `HistoryFilterBar`→`StatsPeriodBar` va
   ANTES que `HistoryFilter`→`StatsPeriod`, o el primero queda medio renombrado. Luego
   `availableFilters`→`availablePeriods` y `activeFilter`→`activePeriod`.
3. `git mv` de los tres ficheros de la tabla.
4. `grep` de residuos (vacío) + `testAll` + `assembleMockDebug` + `compileProdDebugKotlin`.

⚠️ **Y el doc del ticket se escribe en el worktree que se va a commitear.** Este estuvo a punto de
no viajar: vivía en el worktree del primer intento, que se borró con `--force` al empezar de cero.

## Verificación

- `./gradlew testAll` ✅ · `:app:assembleMockDebug` ✅ · `:app:compileProdDebugKotlin` ✅
- `grep` de residuos del nombre viejo en `.kt`: **vacío**.

## Lado iOS

Todo vive en `commonMain` (`presentation/vehicles`, `presentation/stats`): iOS recibe el mismo
rename al compilar el módulo. Nada que abrir.
