# STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001 · La app saca la conclusión, y enseña con qué

**Estado:** ✅ Done (2026-09-15) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Rebasado sobre `d33fe08c` justo antes de mergear, sin conflictos.
**Abierto:** 2026-09-15 · sobre `eb8e34ec` (F3) · **F4 de**
[`stats-intelligence-roadmap-001.md`](stats-intelligence-roadmap-001.md)

## Problema

Después de F0-F3 el panel tiene once cifras, tres series, una lista de sitios y una rejilla de 42
celdas — y **no dice ni una frase**. Toda la interpretación la pone el usuario: mirar la rejilla y
darse cuenta de que los martes por la tarde tarda el triple es trabajo suyo, hecho a ojo, sobre un
degradado.

Y es justo el trabajo que define lo que se vende: *free pregunta y la app responde; Plus vigila y
recuerda* [PLUS-ALERTS-AND-TIERS-001]. Una app que sólo dibuja obliga a preguntar bien; una que
concluye contesta lo que no le has sabido preguntar.

## Doctrina violada

Ninguna. Las que este ticket debe respetar entera, y son duras:

- **Ninguna frase sin su n.** Un insight es una afirmación sobre el usuario; sin muestra detrás es
  una ocurrencia. La evidencia viaja CON la frase y se enseña, no se documenta.
- **Nada bajo su umbral** [STATS-CORE-001]: bajo umbral el insight **no existe** — no se pinta en
  gris ni con un «aún no».
- **Un caso de uso por VEREDICTO, nunca por predicado** [DET-VERDICT-NOT-PREDICATE-001]: el
  veredicto es *«qué frases sostiene este periodo, ordenadas»* → UN caso de uso. Cada regla es un
  **predicado puro** en `domain/stats/insights/`, patrón `SentryWakeCooldown` — no doce casos de uso.
- **No copy con mecánica interna ni jerga** [feedback_no_internals_in_user_copy]: «los martes por la
  tarde tardas 12 min» sí; «celda 1×4 con z-score 2.1» no.
- El delta sólo compara periodos **comparables** [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]: la
  regla de tendencia hereda `coverageAllowsComparison`, no inventa otra.

## Diseño

1. **`domain/stats/insights/`** — cuatro predicados puros, cada uno con su test:
   | Regla | Frase | Existe cuando |
   |---|---|---|
   | `WorstTimeToPark` | «los martes de 16 a 20 tardas 12 min» | celda de la rejilla ≥ 2 medidas y ≥ 1,5× la media del periodo |
   | `EasiestTimeToPark` | «a las 8 encuentras en 2 min» | ídem, ≤ 0,6× la media — y sólo si NO es la misma celda |
   | `CostliestPlace` | «en Trabajo tardas 9 min; en el resto, 3» | ≥ 2 sitios con búsqueda medida y ≥ 1,5× el resto |
   | `SearchTimeTrend` | «este mes buscas 4 min más que el anterior» | ambos periodos **completos** (F0) y ≥ 20 % de cambio |
2. **`DeriveStatsInsightsUseCase`** — el veredicto. Ordena por **tamaño del efecto × tamaño de
   muestra** y devuelve como mucho [MAX_INSIGHTS]. Un insight lleva `evidence(samples, scope)`.
3. **Lector**: card al PRINCIPIO del panel — la conclusión va antes que la prueba, y lo que hay
   debajo (gráfico, cifras, sitios, rejilla) es exactamente la prueba. Sin datos suficientes, la
   card **no se compone**: una tarjeta que dice «aún no sé nada» ocupa el sitio de la que sí sabrá.
4. **Free/Plus**: no se gatea NADA. Sobre 7 días casi ningún insight pasa umbral **por
   construcción**, así que el free ve el motor funcionando con lo que su ventana sostiene — que es
   el paywall más honesto que hay y no esconde ni un botón [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001].

## Criterio de éxito

- **Test**: bajo umbral no hay insight (ni uno en gris); con dos celdas iguales no hay «peor
  momento»; la tendencia calla si un periodo no está completo.
- **Test**: el orden lo decide efecto × muestra, y el tope es real.
- **Test**: la frase peor y la mejor nunca son la misma celda.
- Suite + guardarraíles con `--rerun-tasks` + 9 locales + galería (con y sin insights).
- Compila mock, prod e iOS.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `ParkingHeatmap` (F3) | ♻️ la fuente de las dos reglas de hora; `peak` ya estaba expuesto |
| `ParkingCluster.avgSearchTimeMs` (F2) | ♻️ la fuente de la regla de sitio |
| `PeriodStats.searchTime` + `previous` (F0) | ♻️ la fuente de la tendencia, con su regla de comparabilidad |
| `StatsScreen` | 🔧 una card más, la primera |
| `VehicleStatsBlock` (la ficha) | ✅ exento: la ficha resume, el panel concluye — meter frases en las dos es duplicar la conclusión |
| Room / DTO / sync | ✅ exentos: se deriva en memoria |

## Desviación sobre el diseño

**La regla de comparabilidad se mudó a dominio.** El plan decía pasarle al motor un
`searchTimeComparable` decidido fuera; al cablearlo, `coverageAllowsComparison` resultó ser
`internal` de `presentation` y el flavor mock no podía verla. Moverla a `domain/stats/ScopedMeasure.kt`
—junto al tipo del que habla— la pone al alcance de sus dos lectores y, de paso, **borra el
parámetro**: un motor que puede recibir `true` es un motor al que se le puede mentir.

## Verificación (15-09-2026, sin commitear)

- Suite completa verde. Testigo: `DeriveStatsInsightsUseCaseTest` tests="11" failures="0" — incluye
  que una rejilla plana no tiene «peor momento», que la misma celda no puede ser la peor y la mejor,
  que el baseline se pondera por visitas, y que la tendencia calla si los periodos no son comparables.
- Guardarraíles con `--rerun-tasks`: Typography 5 · Color 8 · Divider 1 · Import 1 · LocaleParity 5,
  todos a cero (7 keys nuevas ×9).
- `:app:compileProdDebugKotlin` y `:shared:compileKotlinIosSimulatorArm64` en verde.

## En device (Oppo, mock, sha256 verificado)

La card sale bajo la barra de filtros con **tres frases, cada una con su n**: «Estás buscando 7 min
más por viaje que el periodo anterior · sobre 5 aparcamientos medidos», «Sábado de 12–16: aparcas en
2 min · sobre 2», «Martes de 12–16: tardas 14 min en aparcar · sobre 3».

Dos defectos corregidos allí mismo:

1. ⛔ **La variante de galería no alcanzaba su caso, dos veces.** Primero porque las marcas de tiempo
   de los fakes caen en celdas distintas —y una celda necesita dos muestras—, así que **los saltos se
   hacen en múltiplos de SIETE DÍAS**, que es lo único que conserva día de la semana y hora. Y
   después porque **la galería arma `StatsState` a mano y no pasa por el ViewModel**: los insights
   hay que derivarlos también ahí, o el estado por defecto deja la card sin componer para siempre.
   [DEV-A-GALLERY-VARIANT-MUST-EXERCISE-ITS-CASE-001]
2. ⛔ **«Los Sábado»**. La plantilla anteponía «Los » a un nombre de día que llega en singular y
   capitalizado. Arreglado sin inventar 63 strings de días en plural: **la frase empieza por el
   nombre del día** («Sábado de 12–16: aparcas en 2 min»), que es correcto en los nueve idiomas y
   además pone la mayúscula donde toca.

## Hallazgo ajeno, ya resuelto

El pase destapó que el flavor mock reventaba al entrar en la app: `ObserveZoneSpotAlertsUseCase`
pedía un `ZoneWatchRepository` que **sólo estaba declarado en `DataModule`**, y el grafo mock no
carga ese módulo. Nada que ver con este ticket, y **el user ya lo arregló en master**
(`MockModule.kt`, binding propio). Queda sin cerrar la causa: nada comprueba que los dos grafos de
Koin declaren lo mismo, así que el próximo `single<…>` que nazca en `DataModule` volverá a matar el
mock — y sólo lo verá quien arranque la app.
