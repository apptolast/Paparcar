# OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001 · La caminata de espejos abandona al único servidor vivo justo antes de que conteste, y los cinco carriles se pisan el cupo entre ellos

**Estado:** ✅ Done · en master (14-09-2026)

## Problema

El carril de parkings públicos «a veces tarda muchísimo o no da respuesta cuando debería». Medido
contra los endpoints reales el **14-09-2026**, con la consulta exacta de
`OsmPublicParkingParser.buildQuery` (Sol, r=1500 m) y nuestro User-Agent:

### Los tres espejos de `OverpassEndpoints.ALL`

| Espejo | Código | TTFB | `timestamp_osm_base` |
|---|---|---|---|
| `overpass-api.de` | 200 | **4,68 s** frío · 1,4 s caliente | 2026-09-14 ✅ |
| `overpass.private.coffee` | 200 | **52,0 s** | **2026-06-01** — 105 días atrás |
| `overpass.kumi.systems` | **504** | 54,9 s | — |

**Dos de los tres espejos son inservibles.** El failover que creemos tener no existe: hay un
servidor y dos placebos.

### El límite real de la API

`overpass-api.de/api/status` declara `Rate limit: 2`. Medido desde una sola IP:

| Escenario | Resultado |
|---|---|
| 6 peticiones seguidas | 2× 200 · **4× 429**, tardando 7–8,5 s en decirlo |
| 4 peticiones **en paralelo** | 1× 429 (0,7 s) · **3× 504 a los 14,1 s** — la misma consulta que sola tarda 1,4 s |
| 1 petición cada 12 s, ciudades distintas | Barcelona **504 @ 8,06 s** · Málaga 200 @ **7,72 s** · París 200 @ 3,90 s · Berlín **429 @ 9,48 s** |

⛔ El 429 **no trae `Retry-After`**. No hay forma de saber cuándo reintentar.

### La aritmética del fallo total

Con `[timeout:20]` declarado, `OverpassWalkPatience.attempt()` reparte así:

```
espejo 1 (overpass-api.de) : firstByte  8 s → se abandona a los 8 s… iba a contestar a los 7,7
espejo 2 (private.coffee)  : firstByte  8 s → tarda 52 s  → abandonado
espejo 3 (kumi.systems)    : firstByte 14 s → da 504 a 54 s → abandonado
                                             = 30 s exactos y fallo total
```

Eso es, literalmente, el «tarda muchísimo y no da respuesta» que reporta el user.

## Doctrina violada

**⛔ «Prohibición sin testigo no es chequeo»**, aplicado a un número: `QUIET_MIRROR_MS = 8_000` se
justifica en `OverpassWalkPatience.kt:87-90` con una afirmación sobre el comportamiento del
servidor — *«a busy one that is genuinely working starts streaming its response»* — que **es falsa y
nadie midió**. Medido hoy: `TTFB=4,68 s` con `total=4,74 s` y 31 KB de cuerpo. Overpass **bufferiza
entero**: no llega ni un byte hasta que la consulta ha terminado. El silencio no distingue a un
servidor saturado de uno trabajando, porque **todo servidor de Overpass está callado mientras
trabaja**. El umbral no es una válvula de seguridad, es una guillotina colocada en la mediana de la
latencia real.

**⛔ Sistemas, no parches**: el cupo de Overpass es **un recurso compartido por los cinco carriles**
(parkings, gasolineras-OSM, POI del sitio aparcado, nombres FR, red de calles del map-matcher) y
hoy no hay ningún sitio donde eso esté dicho. Cada carril cree que es el único que pregunta.

## Señales / datos disponibles

- `OverpassClient` ya loguea qué espejo pasó, por qué y con cuánta paciencia — el diagnóstico
  existe, lo que falta es que los números que reparte sean los correctos.
- `OverpassResponseHealth` ya sabe leer `timestamp_osm_base`; hoy solo comprueba que *parezca* una
  fecha, no que sea **reciente**, y por eso aprueba a `private.coffee` sirviendo datos de hace 105
  días como si fueran de hoy.
- `HomeTripController.kt:143-166` prueba que el map-matcher pide a Overpass **durante el viaje** —
  exactamente cuando el conductor abre el carril de parkings al llegar.

## Diseño

El invariante, en una frase:

> **Overpass es un cupo compartido de 2 peticiones por IP, y el silencio no significa nada.**

Tres sitios, uno por cada mitad del invariante:

1. **`OverpassWalkPatience`** — subir `QUIET_MIRROR_MS` por encima del p95 medido (7,7 s en Málaga
   desde fibra; en 4G hay que sumar RTT). El número deja de justificarse con una creencia sobre
   streaming y pasa a justificarse con la tabla de medidas de arriba, que se cita en el KDoc.
2. **`OverpassEndpoints` + `OverpassResponseHealth`** — sacar los dos espejos muertos, meter los
   que estén vivos hoy, y **que la frescura sea parte de la salud**: un `timestamp_osm_base` de
   hace meses es un servidor que miente igual que el que devuelve un contador.
3. **Un semáforo de concurrencia compartido**, del lado de `OverpassClient`, para que los cinco
   carriles no puedan estar preguntando a la vez. ⛔ Es una pieza nueva y **tiene que ser un
   `single` de Koin**, no un campo de cada fuente: hoy `DataModule.kt:185,234,257,273,276` crea
   **cinco `HttpClient` distintos** con la misma receta, y ese es justo el patrón que dejó el cupo
   sin dueño. Un cupo que cada carril instancia por su cuenta no es un cupo.

⚠️ **Fuera de alcance, a propósito**: el rate limit por IP no se arregla desde el cliente. Con
CGNAT de operador móvil, miles de usuarios comparten IP pública y el 429 es el estado por defecto a
escala. La solución de fondo (Overpass propio, ~€40-70/mes) va en su propio ticket — ver
`docs/backlog/` y el análisis de alternativas. Este ticket recupera la mayoría de los fallos
**actuales**; no pretende escalar.

## Criterio de éxito

- Un test que fije la paciencia repartida a cada espejo contra la tabla medida, y que falle si
  alguien vuelve a bajar el corte por debajo del p95.
- Un test de `OverpassResponseHealth` con un `timestamp_osm_base` viejo → **no** confiable.
- Un test que pruebe que dos carriles simultáneos **se serializan** (el segundo no sale hasta que
  vuelve el primero).
- En campo: abrir el carril de parkings en Barcelona/Málaga deja de fallar cuando el servidor
  tarda 8-9 s.

## Qué se hizo

1. **`OverpassWalkPatience`** — el corte deja de ser una constante y pasa a ser **el tiempo que
   queda dividido entre los espejos sin preguntar**, con suelo en `SLOWEST_MEASURED_ANSWER_MS`
   (12 s = los 7,72 s de Málaga por fibra + lo que paga una radio móvil). Con la lista viva de dos,
   cada espejo recibe 15 s; añadir un tercero **re-reparte solo**, que es justo lo que no pasó la
   vez anterior. `isLast: Boolean` desaparece: con `mirrorsLeft = 1` el último hereda lo que queda
   sin necesitar caso especial.
2. **`OverpassEndpoints`** — de tres entradas (una viva) a **dos, ambas medidas hoy**. Los cinco
   descartes quedan listados con su síntoma exacto en `EXCLUDED_BY_MEASUREMENT`, para que nadie los
   vuelva a añadir por optimismo.
3. **`OverpassResponseHealth`** — la frescura entra en la salud. Deja de ser un chequeo de forma y
   pasa a parsear el instante: `116992` sigue cayendo, y ahora también cae el 200 que describe el
   trimestre pasado. `nowMs` es parámetro, no reloj interno.
4. **`OverpassQuota`** (nuevo) — un semáforo de 1 permiso, process-wide, que `OverpassClient` toma
   **alrededor de la caminata entera**. Soltarlo entre espejos dejaría colarse a otro carril a mitad
   del failover, que es exactamente el solapamiento que convierte 1,4 s en un 504 de 14 s.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `OsmPublicParkingsSource.kt:41` | `OverpassClient` propio | ✅ pasa por el cupo (default del constructor) |
| `OsmFuelStationsSource.kt:33` | ídem | ✅ ídem |
| `OsmStationNames.kt:56` (nombres FR) | ídem | ✅ ídem |
| `OverpassPlacesDataSource.kt:44` | ídem, `[timeout:8]` en vez de 20 | ✅ el presupuesto se sigue leyendo de la consulta |
| `OverpassRoadNetworkDataSource.kt:37` | ídem, **y pide durante el viaje** | ✅ ídem — es la mitad del par que se pisaba |
| Anidamiento (`fetch` dentro de `fetch`) | — | ✅ **ninguno**: los 5 son llamadas de primer nivel, así que un cupo no reentrante no puede bloquearse |
| `OverpassClientDeadlineTest` · `OverpassWalkPatienceTest` | los números viejos | ✅ reescritos |
| Fixtures con `timestamp_osm_base` **fijo** | que la salud solo miraba la FORMA | ✅ ver abajo |

### ⛔ El barrido encontró una bomba de relojería en la propia suite

Cuatro ficheros de test escribían el sello de base como una **fecha carvada**:
`2026-07-15T15:22:01Z` (×3), `2026-09-09T21:00:00Z` (×4), `2026-09-10T12:48:02Z`. Eran inofensivas
mientras la salud solo miraba la forma. En el momento en que empezó a leer la EDAD, esas fixtures se
convirtieron en relojes: las de julio ya nacían con 61 días de retraso —habrían roto la suite el
mismo día— y las de septiembre tenían **tres días de vida**.

Arreglado con `freshBase()` en `commonTest`: la fixture declara la PROPIEDAD («fresco») y la fecha
la sigue. Es el mismo movimiento que este ticket hace con la paciencia — **derivar el número en vez
de carvarlo**— aplicado al sitio que lo iba a comprobar.

## Verificación

- `:shared:testDebugUnitTest --rerun-tasks` → **2.803 tests, verde** (con `--rerun-tasks` para que
  los guardarraíles de Konsist se ejecuten de verdad y no salgan UP-TO-DATE).
- `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` → verde.
- Sin strings nuevos y sin pantalla ni estado nuevo, así que no hay locales ni Dev Catalog que tocar.

## Hallazgos laterales — NO se tocan aquí

Apuntados para no ampliar el encargo:

1. **`GetNearbyPublicParkingsUseCase.kt:49` espera al geocode sin necesitarlo.**
   `OsmPublicParkingsSource.covers()` devuelve `true` siempre e ignora `countryCode`/`regionName`,
   pero la petición no sale hasta que el geocoder responde. Y en API 33+
   (`AndroidGeocoderDataSourceImpl.kt:78-88`) es un `suspendCancellableCoroutine` **sin timeout**:
   si el callback no llega, el carril no llega a preguntar nunca.
2. **Cinco `HttpClient` para el mismo host** — cero reutilización de conexión TLS entre carriles.
3. **Los comentarios de `DataModule` dicen «el MISMO `fuelHttpClient()`»** cuando es la misma
   *receta*, no el mismo cliente. Un comentario que miente cuesta una auditoría.

## Rebase

14-09-2026, rebasada sobre master `84a1577b` (8 commits nuevos del user). **Sin conflictos**;
suite y ambos flavors verdes después. Rama independiente: no forma parte de la cadena de parkings.
