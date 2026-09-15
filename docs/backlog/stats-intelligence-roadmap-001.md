# STATS-INTELLIGENCE-ROADMAP-001 · Estadísticas inteligentes: series, sitios, insights y estimaciones

**Estado:** 🔵 Abierto (15-09-2026) — doc de PLAN, no de código. Manda en **dónde cae** cada pieza
de estadísticas nueva; cada fase entra con su ticket y su worktree, nunca por iniciativa propia.
Sucede a `plus-roadmap-001.md` §F3/F4 (que cerraron el agregado y el panel) y hereda el reparto
free/Plus de `plus-alerts-and-tiers-001.md`.

> ⛔ **Cómo se usa este doc**: igual que la lista de espera de Plus. Nada de aquí se implementa sin
> decidirlo; al cerrar una fase, recitar qué queda.

## De dónde partimos (15-09-2026, master)

| Pieza | Estado |
|---|---|
| `ComputePeriodStatsUseCase` → `PeriodStats` | ✅ 15 escalares, umbrales por cifra, periodo anterior adjunto |
| Tiles (`statTileKeys`) | ✅ 11: COST · FUEL · ENERGY · CO2 · SEARCH_TIME · AVG_DURATION · PEAK_HOUR · USUAL_DAY · USUAL_STREET · AUTO_DETECTED · SPOTS_SHARED |
| Gráfico | ✅ barras de **nº de aparcamientos** por bucket, ventana del filtro |
| Panel propio | ✅ `StatsScreen`, selector de coche, filtro, gráfico + cifras |
| Free / Plus | ✅ free ve TODAS las cifras sobre 7 días; Plus vende el periodo |

**Dos de las cifras que se piden ya existen**: *estancia media* = `avgParkedDurationMs`, *día
habitual* = `mostActiveDayOfWeek`. Lo que falta no son más cifras sueltas.

## Los cuatro huecos reales

| Hueco | Hoy | Consecuencia |
|---|---|---|
| **Series** | Todo es un escalar por periodo | El gráfico sólo sabe pintar «nº de aparcamientos»: no hay km/mes, ni búsqueda/día |
| **Cruce de dimensiones** | Ninguno | «los días que más tardas aparcando» es día × minutos-de-búsqueda: hoy es imposible |
| **Sitio** | `favoriteStreet`, igualdad de strings del geocoder | La misma plaza con dos nombres de calle son dos sitios; y una zona no es una calle |
| **Interpretación** | 11 tiles que el usuario interpreta solo | Cero frases, cero proyecciones: todo mira atrás |

Y una **deuda de honestidad viva**, que es por dónde se empieza: `searchTimeMs` se suma sobre las
sesiones que lo llevan y se pinta como total del periodo **sin declarar cobertura** — justo lo que
`ScopedDistance` existe para evitar [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001].

## Las fases

| Fase | Ticket | Qué añade | Tier | Est. |
|---|---|---|---|---|
| **F0** | `STATS-A-MEASURE-CARRIES-ITS-COVERAGE-001` | La cobertura viaja con TODA suma medida, y todo lector la enseña | — | ½ d |
| **F1** | `STATS-A-PERIOD-IS-A-SERIES-NOT-A-NUMBER-001` | `PeriodSeries` + gráfico con conmutador de métrica | free | 2-3 d |
| **F2** | `STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001` | Zonas frecuentes por clustering, con cifras propias por sitio | free | 3 d |
| **F3** | `STATS-THE-EXPENSIVE-HOUR-IS-MEASURED-001` | Mapa de calor día × franja (aparcamientos y minutos buscando) | free | 2-3 d |
| **F4** | `STATS-AN-INSIGHT-IS-A-VERDICT-WITH-ITS-EVIDENCE-001` | El motor de insights: frases ordenadas, cada una con su evidencia | free | 4 d |
| **F5** | `STATS-AN-ESTIMATE-SAYS-IT-IS-ONE-001` | Proyecciones declaradas (cierre de periodo, coste anual, ahorro) | free | 2-3 d |
| **F6** | `STATS-PLUS-REMEMBERS-AND-REPORTS-001` | Resumen de mes cerrado, tendencia 12 meses, insight de vigilancia | **Plus** | 3-4 d |

### F0 · La cobertura viaja con la medida
`ScopedDistance` se generaliza a `ScopedMeasure<T>` y **toda** suma medida la lleva: km, tiempo de
búsqueda, y las que vengan. Todo tile derivado de distancia medida (gasto, litros, kWh, CO₂) dice
sobre cuántos viajes habla. Sin esto, cada fase de abajo multiplica el mismo agujero.

### F1 · Un periodo es una serie, no un número
`PeriodSeries`: los buckets que ya calcula `VehicleHistoryCalculator`, pero con **todas** las
métricas por bucket. `buildActivityBuckets` se generaliza con un selector; la card del gráfico gana
una fila de chips (aparcamientos · km · buscando · gasto). Una card, un alcance, cuatro lecturas.
⚠️ Depende de decidir el punto 4 de `trip-a-journey-is-a-chain-of-trips-001` («¿un viaje cuenta
como 1 o como N?»). Propuesta: la serie mide **trayectos** —la unidad medida—; el «viaje» es
proyección de presentación y no toca el gráfico.

### F2 · Un sitio habitual es un racimo, no una calle
`clusterParkings(sessions, radius ≈ 120 m)` puro, por rejilla geohash (O(n): el filtro «Todo» no
tiene tope). Nombre por prioridad: `Zone` del usuario que lo contiene → `placeInfo` → calle más
repetida. Cada racimo trae SUS cifras (veces · estancia media · búsqueda media · % automático ·
plazas cedidas). `favoriteStreet` queda **sustituido**, no acompañado: dos respuestas a la misma
pregunta es el choque de relojes que STATS-UI-001 ya pagó una vez. Privacidad: se deriva en local
del `location` que ya sincroniza; ningún agregado nuevo sube [PRIV-A-TRACE-IS-NOT-A-STAT-001].

### F3 · La hora cara se mide
Rejilla día × franja (7 × 6, no 7 × 24: con 24 columnas casi ninguna celda pasa umbral) sobre dos
medidas: nº de aparcamientos y minutos buscando. Umbral **por celda** — una celda con una muestra
no es un patrón, se pinta «sin datos», nunca como frío. Canvas en commonMain (Nivel 3).
⛔ **Necesita una rampa secuencial nueva y declarada** en `COLOR-SYSTEM.md`: la rampa
verde→amarillo→rojo es **exclusiva de la caducidad de plazas** [UI-COLOR-DOCTRINE-001].

### F4 · Un insight es un veredicto con su evidencia
`DeriveStatsInsightsUseCase` — UN veredicto: *qué frases sostiene este periodo, ordenadas*. Cada
regla es un **predicado puro** en `domain/stats/insights/`, patrón `SentryWakeCooldown`, no doce
casos de uso [DET-VERDICT-NOT-PREDICATE-001].
`StatsInsight { kind, magnitud, evidencia(n, ventana, cobertura), copyKey }` — **ninguna frase sin
su n**. Ranking = tamaño del efecto × tamaño de muestra, tope de 3 visibles. Bajo umbral un insight
no se pinta gris: no existe. Sin datos se dice qué falta y cuánto.

### F5 · Una estimación dice que lo es
Card **aparte** de las cifras medidas: una proyección no se mezcla con una medición. Cierre de
periodo (gasto · km · CO₂ · búsqueda) sólo con periodo transcurrido ≥ umbral; «a este ritmo, X
€/año»; «te habrías ahorrado Y repostando en la más barata de tu zona» (sale de precios que ya
tenemos). ⛔ Fuera: aviso de depósito/autonomía — necesita marcar repostajes, sigue en la lista de
espera de `plus-alerts-and-tiers-001`.

### F6 · Plus recuerda y avisa
Resumen de mes cerrado (pantalla + notificación local), tendencia a 12 meses, insight de vigilancia
(«esta zona te cuesta 4 min más que hace un mes»). Es el único bloque Plus por naturaleza: un
resumen que te llega sin pedirlo es la app trabajando por ti.

## Cómo cae en free/Plus sin romper doctrina

`STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001` no se toca: free ve **todas** las cifras sobre sus 7
días. La gracia es que **no hace falta gatear ni un insight**: casi todos exigen n ≥ 5 o ≥ 30 días,
así que sobre 7 días **no se disparan por construcción**. El free ve el motor funcionando con lo
que su ventana sostiene y lee «esto necesita 30 días» — el paywall más honesto que hay, y sigue
siendo **soft**: nada se esconde, lo que se compra es la ventana. Lo único Plus por naturaleza es
F6 (vigilancia y memoria), que es exactamente el eje de `plus-alerts-and-tiers-001`.

## Riesgos que se deciden antes de escribir cada fase

1. **Cobertura de `searchTimeMs`**: sólo se estampa con ruta medida fresca. Hasta que el Kamiq
   valide el carril BT, la cobertura real puede ser baja — F0 la hace visible en vez de mentir,
   pero conviene medirla en el corpus antes de montar F3/F4 encima.
2. **Coste de cómputo con filtro «Todo»**: hoy se recalcula todo en cada emisión de historial. Con
   racimos + heatmap + insights hay que memoizar por (coche, ventana) o bajar a agregados de Room.
3. **Umbrales**: cada uno, constante nombrada y calibrable, con su porqué en el doc de su fase. Es
   lo que más se va a tocar después de verlo en device.
4. **Copy ×9 locales** crece rápido en F4: una plantilla por familia sale más barata que una key
   por frase.
