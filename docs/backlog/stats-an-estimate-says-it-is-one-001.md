# STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001 · Lo que va a costarte el mes, dicho como lo que es

**Estado:** ✅ Done (2026-09-15) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Rebasado sobre `d33fe08c` antes de mergear, sin conflictos.
**Abierto:** 2026-09-15 · sobre F4 · **F5 de**
[`stats-intelligence-roadmap-001.md`](stats-intelligence-roadmap-001.md)

## Problema

Todo lo que el panel sabe mira **hacia atrás**. El día 4 de mes la tarjeta dice «1,36 €» y es verdad,
y es inútil: nadie decide nada con el gasto de cuatro días. La pregunta que la gente se hace de su
coche —*¿cuánto me va a costar esto?*— no tiene respuesta en ninguna pantalla.

## Doctrina violada

Ninguna. Las que este ticket debe respetar, y son la razón de su nombre:

- **Una estimación DICE que lo es.** No se mezcla con lo medido: card aparte, verbo aparte
  («va a costarte», no «te ha costado»). El proyecto ya rotula «aprox.» el gasto derivado de un
  consumo declarado; una proyección está un escalón más lejos y tiene que notarse.
- **Nada sin su base** [STATS-CORE-001]: una proyección desde dos días no es una proyección, es una
  multiplicación. Se exige periodo transcurrido suficiente, y la card dice sobre qué proyecta.
- **La cobertura viaja** [STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001]: proyectar una suma parcial la
  subestima, así que la proyección hereda la cobertura de la medida de la que sale.

## ⛔ El hallazgo que decide el alcance

**Sólo se puede proyectar un periodo de CALENDARIO.** «Últimos 7 días» y «3 meses» son ventanas
RODANTES: siempre cubren el mismo tramo, nunca se «cierran», y proyectar su cierre no significa
nada — mañana la ventana se habrá movido con el usuario dentro. «Todo» ni siquiera tiene final.

Queda: **esta semana** y **este mes**. Y el año, que no es un periodo del filtro sino un ritmo
extrapolado — por eso se dice distinto: *«a este ritmo, X al año»*.

## Diseño

1. **`domain/stats/PeriodProjection.kt`** — puro, sin reloj: `projectPeriod(value, elapsedMs,
   windowMs)`. Reglas:
   - transcurrido **< 25 %** → null (no hay base);
   - transcurrido **> 90 %** → null (la proyección ES la cifra: decirla es decorar);
   - resultado = valor / fracción transcurrida.
2. **`VehicleHistoryCalculator.windowEndMs(filter, nowMs)`** — el final del periodo, donde ya vive su
   principio. Null para las ventanas rodantes y para «Todo», que es exactamente el gate del punto
   anterior expresado en tipos: sin final no hay proyección.
3. **Qué se proyecta**: gasto (la cifra accionable), kilómetros y tiempo buscando plaza. CO₂ y
   litros son múltiplos de los km — la misma razón por la que F1 no les dio una serie.
4. **El año** sale del ritmo diario del periodo × 365, y sólo desde «este mes»: una semana es una
   base demasiado corta para hablar de un año, y decirlo igualmente sería inventar precisión.
5. **Lector**: card «Si sigue este ritmo» **debajo** de las cifras medidas y encima de los sitios.

## Criterio de éxito

- **Test**: el día 2 de mes no hay proyección; el día 28 tampoco; el 15 sí y vale el doble.
- **Test**: los filtros rodantes y «Todo» no proyectan nunca.
- **Test**: la proyección de una suma parcial conserva la cobertura de su medida.
- Suite + guardarraíles con `--rerun-tasks` + 9 locales + galería (con proyección y sin ella).
- Compila mock, prod e iOS.

## Fuera de alcance, con su razón

**«Te habrías ahorrado X repostando en la más barata»** se queda fuera: necesita la gasolinera
habitual y los precios de su entorno, que son otra fuente y otra pregunta. Sigue en la lista de
espera de `plus-alerts-and-tiers-001`.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `PeriodStats` (`costEstimate`, `distance`, `searchTime`) | ♻️ las tres magnitudes que se proyectan; sin cambios |
| `VehicleHistoryCalculator.windowStartMs` | ♻️ su gemelo `windowEndMs` nace al lado, misma autoridad |
| `StatsScreen` / `StatsState` | 🔧 una card más y su cálculo |
| `VehicleStatsBlock` (ficha) | ✅ exento: la ficha resume lo que pasó; el panel es donde se proyecta |
| Room / DTO / sync | ✅ exentos |

## Verificación (15-09-2026)

- Suite completa: **292 suites, 3.122 tests, 0 fallos**. Testigos: `PeriodProjectionTest` 7/7
  (nuevo) · `VehicleHistoryCalculatorTest` +2 (las ventanas rodantes no tienen final; una semana
  mide exactamente siete días).
- Guardarraíles con `--rerun-tasks`: Typography 5 · Color 8 · Divider 1 · Import 1 · LocaleParity 5,
  todos a cero (6 keys nuevas ×9).
- `assembleMockDebug`, `:app:compileProdDebugKotlin` y `:shared:compileKotlinIosSimulatorArm64` en
  verde. Rebasado sobre `164ea025`.
- Galería: «Ritmo · a dónde va el mes» y «Ritmo · un periodo sin final (no proyecta)».

### En device (Oppo, mock, sha256 verificado)

La card sale con «cierre de mes · 2,76 EUR», «kilómetros · 23,0 km», «buscando plaza · 22 min» y su
base. La fila del AÑO no aparece, y es correcto: el periodo lleva 15 días y el ritmo anual exige 21.

⛔ **Y salió «Sobre el 49%% del periodo»** — el `%%` que [COPY-PERCENT-IS-NOT-ESCAPED-001] documenta
desde el badge de ahorro. El formateo de Compose Resources es un solo Regex sobre `%1$s`/`%1$d` y no
toca nada más, así que un `%` literal se escribe con UN signo. Corregido en los nueve locales y
vuelto a mirar en el móvil: «Sobre el 49% del periodo».

### Lo que este pase enseñó del propio master

`assembleMockDebug` reventó al mergear recursos por un apóstrofo SIN escapar en
`app/src/main/res/values-{it,fr}` — la regla opuesta a la de Compose Resources, porque ahí sí son
`android:strings`. Era de master (`1b690f45`) y el user lo estaba arreglando en paralelo
(`164ea025`); el rebase se lo llevó. Merece la pena recordar que **los tests y `compileKotlin` no
miran los recursos de Android**: sólo un `assemble` los merge.
