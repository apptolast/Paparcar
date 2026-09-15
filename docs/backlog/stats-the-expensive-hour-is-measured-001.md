# STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001 · Qué días y a qué horas te cuesta aparcar

**Estado:** ✅ Done (2026-09-15) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Rebasado sobre `3d1d80a6` (VEH-SHARED-001) antes de mergear, sin conflictos en este ticket.
Este commit lleva además **el pase en device de toda la ola** (chips que se partían, variantes de
galería que no alcanzaban su caso, la suma que debía ser media y «7 h 0 min»), porque el `/run` se
hizo desde esta rama, que es la punta de la pila.
**Abierto:** 2026-09-15 · sobre `5bb0b930` (F2) · **F3 de**
[`stats-intelligence-roadmap-001.md`](stats-intelligence-roadmap-001.md)

## Problema

La pregunta que el user puso de ejemplo al pedir todo esto —«**los días que más tardas
aparcando**»— sigue sin poder responderse, y no por falta de datos: `searchTimeMs` se mide desde
[TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001] y F1 ya la sabe repartir en el tiempo.

Lo que falta es el **cruce de dos dimensiones**. Todo lo que hay hoy es una serie de una variable:
`peakHourBucket` dice la hora punta de todo el periodo, `mostActiveDayOfWeek` el día, y las dos por
separado no responden nada — un martes a las 9 y un sábado a las 20 pueden dar «martes» y «20 h»
sin que exista un solo martes a las 20.

Y la cifra que importa no es la misma que la de F1: repartir la SUMA de minutos por día ya lo hace
la serie, y sólo redibuja con qué frecuencia aparcas. *«¿Qué día tardo más?»* es una **media**.

## Doctrina violada

Ninguna hoy. Las que este ticket debe respetar:

- **Nada bajo su umbral** [STATS-CORE-001]: una celda con una sola muestra no es un patrón. Y el
  umbral no es el mismo para las dos medidas — ver el diseño.
- [UI-COLOR-DOCTRINE-001] + [UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001]: la rampa
  verde→amarillo→rojo es **exclusiva de la caducidad de plazas**. Un mapa de calor no puede
  estrenar una segunda rampa de tonos.
- [UI-TYPE-SYSTEM-HYGIENE-001]: la exención de tipografía del canvas es de `ActivityBarCanvas` **y
  de nada más**.

## Diseño

1. **`domain/stats/ParkingHeatmap.kt`** — puro: `buildParkingHeatmap(sessions, measure)` →
   rejilla **7 días × 6 franjas de 4 h**. Siete por veinticuatro deja 168 celdas donde casi ninguna
   pasa umbral; con seis franjas la rejilla cabe en un móvil y cada celda tiene masa.
2. **Dos medidas, dos estadísticos, dos umbrales** — y esto es el corazón del ticket:
   | Medida | Celda | Umbral | Por qué |
   |---|---|---|---|
   | Aparcamientos | **cuenta** | 1 | haber aparcado un martes a las 18 h ES un hecho |
   | Buscando plaza | **media** | **2 medidas** | una media de una muestra no es una media |
   La media es lo que hace que la pregunta se pueda contestar: la suma sólo volvería a dibujar la
   frecuencia.
3. **Color: intensidad, no tono.** El mapa se pinta con `PapColor.brandData` a **alpha variable**,
   que es lo que el gráfico de barras ya hace para decir «menos» (`INACTIVE_BAR_ALPHA`). Ni rampa
   nueva ni tono nuevo: un mapa de calor es la misma idea del gráfico en dos dimensiones. Una celda
   **sin dato** no se pinta pálida — se queda en el listón vacío, como el track de una barra.
4. **El canvas dibuja CELDAS, nunca texto.** Las etiquetas (días y horas) son `Text` normales
   alrededor del lienzo, así la exención del guardarraíl de tipografía sigue siendo de un solo
   fichero.
5. **Superficie**: card propia en el panel de estadísticas, debajo de «Tus sitios». La ficha ya es
   larga y esto es exactamente para lo que existe el panel [STATS-PANEL-001].

## Criterio de éxito

- **Test**: la misma sesión cae en su día y su franja en hora local; un martes a las 18 h y un
  sábado a las 20 h no producen ninguna celda «martes 20 h».
- **Test**: con buscar-plaza, una celda de UNA medida no existe; con dos, es la media (no la suma).
- **Test**: una celda sin dato es null, jamás 0 — el lector pinta listón vacío.
- Suite + guardarraíles con `--rerun-tasks` (typography incluido) + 9 locales + galería.
- Compila mock, prod e iOS.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `PeriodStats.peakHourBucket` / `mostActiveDayOfWeek` | ✅ se quedan: son el titular de una dimensión, y el mapa es el detalle de las dos |
| `ChartMetric` (F1) | ♻️ reutilizado para las etiquetas de los chips; el mapa sólo ofrece dos de las tres |
| `PapColor.brandData` | ♻️ mismo rol, alpha variable — sin token nuevo |
| `docs/design/COLOR-SYSTEM.md` | 🔧 una decisión registrada: el mapa es intensidad, no tono |
| `StatsState` / `StatsIntent` | 🔧 la medida elegida vive en el estado, como la del gráfico |
| Room / DTO / sync | ✅ exentos: se deriva en memoria |
| `PapShapes` | 🔧 token nuevo `cell` (6 dp): a 12 dp un cuadrado de 20 dp deja de leerse como cuadrado |

## Desviación sobre el diseño, y por qué

**El lienzo se cayó: son `Box`, no `Canvas`.** La rejilla necesita sus etiquetas de día y de hora, y
pintar texto en un `Canvas` obliga a un `TextMeasurer` con un `TextStyle` construido a mano — que es
exactamente lo que el guardarraíl de tipografía exime para `ActivityBarCanvas` **y para nada más**
[UI-TYPE-SYSTEM-HYGIENE-001]. Cuarenta y dos composables cuestan menos que una excepción: el mapa se
dibuja con `Box` + `background`, y el guardarraíl sigue vigilando un solo fichero.

## Verificación (15-09-2026, sin commitear)

- Suite completa: **278 suites, 3.018 tests, 0 fallos**. Testigo: `ParkingHeatmapTest` tests="8"
  failures="0" — incluye el caso que da nombre al ticket (un martes a las 18 h y un sábado a las
  20 h **no** crean una celda «martes 20 h») y la media contra la suma.
- Guardarraíles con `--rerun-tasks`: Typography 5 · Color 8 · Divider 1 · Import 1 ·
  LocaleParity 5, todos a cero (4 keys nuevas ×9).
- `:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin` y
  `:shared:compileKotlinIosSimulatorArm64` en verde.
- Galería: «Cuándo · buscando plaza».
- ⏳ **Sin ver en device**: si 42 celdas cuadradas caben con holgura a 360 dp con el aire elegido, y
  si el alpha mínimo (0,22) se distingue del listón vacío (0,06) en tema claro. Las dos cosas se
  deciden mirando, no razonando.

## Follow-up natural (F4)

La frase que esta rejilla hace posible —«lo peor: los martes de 16 a 20, 12 min»— es un INSIGHT, y
va con su motor en `STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001`. Aquí se deja la celda
máxima expuesta (`ParkingHeatmap.peak`) y nada más: escribir la frase en esta card sería inventar
un segundo sitio donde viven las conclusiones.
