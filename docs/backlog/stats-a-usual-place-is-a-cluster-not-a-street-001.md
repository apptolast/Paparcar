# STATS-A-USUAL-PLACE-IS-A-CLUSTER-NOT-A-STREET-001 · Un sitio habitual es un racimo de aparcamientos, no una cadena de texto

**Estado:** ✅ Done (2026-09-15) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Rebasado sobre `3d1d80a6` (VEH-SHARED-001) antes de mergear: **un conflicto**, en
`VehiclesViewModel` — las zonas se enganchan ahora con un `combine` encadenado, porque el `combine`
tipado se acaba en cinco flujos y el coche compartido ya usa los cinco. Los racimos funcionan igual
sobre las sesiones del GRUPO: son sesiones.
⚠️ El pase en device movió la fila de sitios de la SUMA a la MEDIA; ese arreglo viajó en el commit
de F3.
**Abierto:** 2026-09-15 · sobre `bcc5a26f` (F1) · **F2 de**
[`stats-intelligence-roadmap-001.md`](stats-intelligence-roadmap-001.md)

## Problema

«Calle habitual» se decide hoy comparando **strings del geocoder**:

```kotlin
val favoriteStreet = ended
    .mapNotNull { it.address?.street?.takeIf { s -> s.isNotBlank() } }
    .groupBy { it }
    .maxByOrNull { it.value.size }
```

Tres formas de fallar, todas normales en campo:

1. **La misma plaza, dos nombres.** Aparcar a un lado y a otro de un cruce da «Calle de Alcalá» y
   «Calle de O'Donnell»: dos sitios de uno. Y el geocoder no es estable — la misma esquina responde
   a veces con la vía y a veces con la travesía.
2. **Dos sitios, un nombre.** «Gran Vía» son cuatro kilómetros. Repetir en dos extremos cuenta como
   repetir en el mismo sitio, que es justo lo contrario de lo que el usuario entiende al leerlo.
3. **Nunca se llama como tú lo llamas.** El usuario ya ha dicho cómo se llama ese sitio: tiene una
   `Zone` puesta ahí y le ha escrito «Trabajo». La estadística la ignora y le responde con el
   nombre de la calle.

Y al no existir el concepto de SITIO, tampoco puede existir ninguna cifra por sitio — que es lo que
convierte una lista en un hallazgo: *«en Trabajo tardas 9 minutos en aparcar; en Casa, 2»*.

## Doctrina violada

- **Sistemas, no parches**: la unidad de verdad de «dónde aparcas» es un LUGAR, y hoy está
  representada por el texto que devolvió un servicio de terceros esa tarde. Misma familia que
  [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001] («la unidad de verdad es el ÁREA»).
- **No copy con jerga ni datos que el usuario no reconoce**: si él ha nombrado el sitio, ese es su
  nombre.
- [DET-VERDICT-NOT-PREDICATE-001]: el racimo y sus cifras son un resultado citable, así que es una
  función pura de dominio con su test, no un `groupBy` dentro de un agregador.

## Señales / datos disponibles

- `UserParking.location` — **todas** las sesiones lo tienen, de los dos carriles. Es lo único que
  hace falta para agrupar.
- `UserParking.address.street` y `placeInfo.name` — para NOMBRAR, ya no para agrupar.
- `Zone(name, lat, lon, radiusMeters, isPrivate)` — el nombre que el usuario escribió.
- `endedAtMs`, `searchTimeMs`, `publishedSpot`, `detectionPath` — las cifras por sitio.

### ⚠️ Lo que cambia el carril BT (user, 15-09)

Ayer el carril BT pasó a exigir **conducción medida igual que el Coordinator**
(`213b4596` el grabador, `f744cd28` la ruta testificando en el veredicto). Dos consecuencias para
este ticket:

- **A favor**: las plazas BT ya traen ruta y `searchTimeMs`, así que las cifras por sitio tienen
  datos de los DOS carriles y no sólo del Coordinator. La cobertura parcial de F0 se cierra sola
  según entren sesiones nuevas.
- **En contra, y por eso el radio es generoso**: las plazas BT VIEJAS se muestrearon a veces
  después de la caminata [DET-BT-PIN-SAMPLED-AFTER-THE-WALK-001], así que su pin puede estar
  decenas de metros del sitio real. Un radio corto partiría en dos un sitio que es uno solo. Las
  nuevas, con el ancla congelada al fin de la conducción medida, ya no tienen ese sesgo.

## Diseño

1. **`domain/stats/ParkingClusters.kt`** — puro, testeable, sin relojes.
   `clusterParkings(sessions, zones): List<ParkingCluster>`, agrupación aglomerativa con **índice de
   rejilla**: cada sesión se asigna al racimo cercano (≤ `CLUSTER_RADIUS_METERS = 120`) buscando
   sólo en las 9 celdas vecinas, así que es O(n) y el filtro «Todo» no tiene tope.
2. **El nombre, por prioridad**: `Zone` que lo contiene → calle más repetida → POI más repetido →
   **sin nombre** (y un racimo sin nombre no se lista: una fila que no sabe decir dónde es no ayuda
   a nadie).
3. **Cifras por racimo**: veces · estancia media · tiempo buscando (con su `ScopedMeasure`, F0) ·
   plazas cedidas · última vez. Umbral `MIN_PARKINGS_FOR_PLACE = 3`, el mismo que ya usaba la calle
   habitual: un sitio es donde REPITES.
4. **`PeriodStats.favoriteStreet` MUERE y nace `places: List<ParkingCluster>`** (ordenada por veces).
   No se añade al lado: dos respuestas a «¿dónde aparcas?» es el choque de relojes que STATS-UI-001
   ya pagó una vez. El tile pasa a `USUAL_PLACE` y lee `places.first().name`.
5. **Lector nuevo**: sección «Tus sitios» en el panel de estadísticas — las cinco primeras con sus
   cifras. `PapListItem` dentro de `PapOutlinedCard` [UI-LIST-ITEM-001], sin esqueleto nuevo.

## Criterio de éxito

- **Test**: dos aparcamientos a 40 m con calles distintas son UN sitio; dos a 3 km con la misma
  calle son DOS.
- **Test**: una `Zone` que contiene el racimo le da su nombre, por encima de la calle.
- **Test**: por debajo del umbral no hay sitio (ni fila ni tile), nunca un sitio de un solo uso.
- **Test**: el tiempo de búsqueda por sitio llega con su cobertura, no como total pelado.
- Suite + guardarraíles con `--rerun-tasks` + 9 locales + galería.
- Compila mock, prod e iOS.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `ComputePeriodStatsUseCase` (`favoriteStreet`) | 🔧 calcula racimos; gana `zones` como parámetro |
| `PeriodStats.favoriteStreet` | 🔧 sustituido por `places` |
| `statTileKeys` / `statTiles` (`USUAL_STREET`) | 🔧 pasa a `USUAL_PLACE`, mismo icono (`Place`) |
| `HistoryTimeline.resolve` | 🔧 gana `zones`; sigue siendo la ÚNICA resolución |
| `VehiclesViewModel` · `StatsViewModel` | 🔧 observan `ZoneRepository` (ya hay `FakeZoneRepository`) |
| `PresentationModule` | 🔧 un `get()` más en `StatsViewModel` (`VehiclesViewModel` va por `viewModelOf`) |
| `stats_tile_favorite_street` (×9) | 🔧 sustituida por `stats_tile_usual_place`; sin otros consumidores |
| Galería / previews | 🔧 variantes de sitios y de «aún no repites» |
| Room / DTO / sync | ✅ exentos: un racimo se deriva en memoria y **no sale del móvil** [PRIV-A-TRACE-IS-NOT-A-STAT-001] |

## Decisiones que cambian un comportamiento anterior

1. **El umbral de «repetir» ya no se le pide al NOMBRE.** La calle tenía que aparecer 2+ veces
   porque el string ERA la identidad: sin esa regla, una visita a una calle era «calle habitual».
   Ahora la repetición la prueba la geometría (un racimo no existe por debajo de 3), así que al
   nombre sólo se le pide ser el mayoritario. Con la regla vieja, un sitio al que vas cada semana se
   quedaba SIN NOMBRE en cuanto el geocoder contestaba la esquina con dos vías — que es el fallo que
   este ticket viene a arreglar. Un test de `VehicleHistoryCalculatorTest` cambia de veredicto por
   esto, y lo dice en su propio comentario.
2. **Las zonas entran en el `combine`, no en un campo actualizado por fuera.** Llegan DESPUÉS de las
   sesiones: un sitio que se llama «Calle Serrano» y sólo pasa a «Trabajo» en la siguiente emisión
   ajena es la clase de caducidad que nadie reporta y todo el mundo ve.
3. **Un racimo sin nombre no se lista, pero cuenta.** Suma en las cifras del periodo (estuviste
   allí) y no ocupa una fila que no sabría decir dónde es.

## Verificación (15-09-2026, sin commitear)

- Suite completa: **277 suites, 3.008 tests, 0 fallos**. Testigos: `ParkingClustersTest` tests="8"
  failures="0" (nuevo) · `VehicleHistoryCalculatorTest` tests="18" failures="0" (+1: por debajo del
  umbral no hay sitio) · `VehicleStatsBlockTest` 16 · `HistoryTimelineScopeTest` 6 (el rescoping del
  filtro sigue pasando con el nombre nuevo) · `StatsViewModelTest` 9 · `VehiclesViewModelTest` 20.
- Guardarraíles con `--rerun-tasks`: Typography 5 · Color 8 · Divider 1 · Import 1 ·
  **LocaleParity 5**, todos failures="0" (5 keys nuevas ×9 + la sustituida).
- `:app:compileMockDebugKotlin`, `:app:compileProdDebugKotlin` y
  `:shared:compileKotlinIosSimulatorArm64` con `--rerun-tasks`, en verde.
- Galería: «Sitios · nombrados por tus zonas», «Sitios · aún no repites» y, en la ficha, «el mismo
  punto con dos nombres de calle».
- ⏳ **Sin ver en device.**

## El radio, CALIBRADO contra el corpus (15-09-2026)

A petición del user. Medido sobre las **18 trazas de campo** de
`domain/detection/coordinator/replay` (script en el scratchpad de la sesión; el repo es público y
no se commitea dato de campo nuevo — las trazas ya estaban).

**1. ¿Cuánto se esparce UN solo aparcamiento?** Cola de fixes en reposo, sólo calidad de ancla
(precisión ≤ 25 m): **p50 5,8 m · p90 24 m · máximo 94,7 m**. Ese máximo es `HouseMirage001`, cuyos
fixes pasean por toda una casa. Es el SUELO: por debajo de ~95 m, dos visitas al mismo sitio pueden
caer en extremos opuestos de ese paseo y partirse en dos sitios.

**2. ¿Dónde está el hueco?** Todas las distancias entre anclas de trazas distintas, ordenadas:

```
0 4 6 6 6 9 9 10 11 13 13 13 13 30 30 31 38 42 58 │ 205 205 208 │ 543 545 548 …
```

**No hay ni un par entre 58 m y 205 m.** Los pares del mismo sitio se acaban en 58; los de sitios
distintos empiezan en 205.

**3. Barrido del radio** — en cuántos SITIOS colapsan los 18 stops:

| radio | 30 | 50 | **60** | 80 | 100 | **120** | 150 | **200** | 300 | 500 |
|---|---|---|---|---|---|---|---|---|---|---|
| sitios | 10 | 9 | **8** | 8 | 8 | **8** | 8 | **8** | 7 | 7 |

Hay una **meseta de 60 a 200 m**: cualquier valor ahí da la misma respuesta. Por debajo parte, por
encima funde.

**Conclusión: 120 m se queda** — cae en mitad de la meseta, por encima del peor esparcimiento
medido (94,7 m) y muy por debajo de la separación real más corta (205 m). El valor era una
suposición razonada y ha resultado estar donde había que estar; ahora lo respaldan dos tests nuevos
que fijan **las dos fronteras medidas**, no el número.

### ⚠️ Lo que este corpus NO puede decir

- **Casi no hay BT** (aviso del user): las trazas son del Coordinator, así que el esparcimiento
  medido es el de anclas BUENAS. El sesgo de la caminata BT no está en los datos — argumenta por
  conservar holgura, no por estrecharse hacia el suelo de 60 m.
- **El lado de la FUSIÓN está infra-muestreado**: un usuario, una ciudad, y el sitio distinto más
  cercano cae a 205 m. En un centro denso, dos sitios reales pueden estar a 80 m (dos lados de una
  plaza) y aquí no hay ningún caso así. Si aparece, se verá como dos sitios fundidos en uno — y la
  respuesta será calibrar con historial denso, no bajar el radio a ciegas.
- **18 stops → 8 sitios** es una muestra pequeña. La meseta es ancha, que es justo lo que hace que
  el número exacto importe poco.
