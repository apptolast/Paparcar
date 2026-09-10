# FUEL-FRANCE-A-PRICE-CARRIES-ITS-OWN-HOUR-001 · Francia, y la hora que viaja con el precio

**Estado:** ✅ Done — en master · verificado en device (prodDebug `a5a0ed6e…`, GPS en Toulouse,
sha256 comprobado antes y después en el emulador compartido)

Paso 1 del orden MEDIDO que dejó [`fuel-stations-list-001.md`](fuel-stations-list-001.md)
(Francia → Alemania → Italia). Segundo adaptador nacional, primero después de España.

## Problema

Fuera de España la app sabe DÓNDE repostar (OSM) pero no A CUÁNTO. Francia tiene feed oficial,
abierto y sin clave, y es la mejor fuente del barrido — mejor que España. No usarla es dejar 9.805
gasolineras con precio real sirviéndose como puntos mudos.

## Lo medido contra el endpoint real (09/10-09-2026)

Fuente: `data.economie.gouv.fr`, dataset `prix-des-carburants-en-france-flux-instantane-v2`,
API Explore v2.1. Sin clave. **9.805 estaciones.** Licencia Open Licence (Etalab).

Lo bueno, confirmado con `curl`:
- **Radio nativo**: `where=distance(geom, geom'POINT(lon lat)', 5km)`. No hay que descargar el país.
- **`order_by=distance(...)`** ordena en el servidor → "la más cercana primero" no la calculamos.
- **Precios ya numéricos** (`gazole_prix: 2.25`), sin coma decimal ni cadena vacía.
- **Cada precio con SU hora** (`gazole_maj: "2026-09-10T00:01:00+00:00"`), ISO 8601.
- `X-RateLimit-Limit: 50000`/día por IP, `Cache-Control: max-age=300`. No es un cuello.

### ⛔ Trampa 1 — no trae nombre. Y el cruce **NO es por proximidad**

El feed no tiene ni un campo de nombre o marca: solo `adresse`, `ville`, `cp`. Campos totales
verificados: no existe `nom`, `marque` ni `enseigne`.

El diseño obvio —cruzar con OSM por cercanía— es el que
[`fuel-stations-list-001.md`](fuel-stations-list-001.md) ya deja prohibido, y **la medición dice
por qué era peor de lo que parecía**. Medido en Toulouse, París y una zona rural (161 estaciones
francesas contra 217 nodos `amenity=fuel` de OSM en los mismos radios de 8 km):

| Regla de cruce por proximidad | Resultado |
|---|---|
| distancia al OSM más cercano | p50 = **44 m**, p90 = 158 m, max = 1.270 m |
| estrictа (≤50 m y el 2.º a ≥100 m más) | nombra solo el **51-62 %** |
| laxa (≤150 m) | nombra el 87 %, pero **6-11 % son ambiguas** (2.º candidato a <100 m) |

O sea: por proximidad, o pierdes la mitad, o en una ciudad **etiquetas mal ~1 de cada 10**.

**Pero OSM trae el id oficial.** `ref:FR:prix-carburants` está en **173 de 217** nodos franceses
del muestreo, y su valor es exactamente el `id` del feed (`31200022`, `75007004`…). Cruzando por
ese id:

| Zona | FR | OSM (con ref oficial) | **Cruce EXACTO por id** | con nombre |
|---|---|---|---|---|
| Toulouse | 51 | 60 (52) | 49 (**96 %**) | 49/49 |
| París | 94 | 138 (105) | 91 (**97 %**) | 91/91 |
| Rural (Cher) | 16 | 19 (16) | 16 (**100 %**) | 16/16 |
| **Total** | **161** | **217 (173)** | **156 = 97 %** | **156/156 = 100 %** |

→ **El cruce va por identificador, no por distancia.** No es "la trampa documentada hecha con
cuidado": es otra operación. Un id oficial no se parece a otro id oficial, así que el modo de
fallo que hacía peligroso el cruce —confundir dos surtidores vecinos— **no existe aquí**.

### ⛔ Trampa 2 — la coordenada, y lo que el cruce por id reveló de ella

Confirmada la del brief y ampliada por medición:
- `latitude`/`longitude` son **cadenas escaladas ×100.000** (`'4361600'` = 43,616), y `geom` es el
  decimal bueno (`{"lat": 43.616, "lon": 1.421}`).
- ⛔ **Y no siempre son enteros**: 15 de 100 registros traen punto decimal DENTRO de la cadena
  escalada — `latitude: '4802880.70289'`, `'4464394.1'`. Un `toInt()` sobre ese campo **peta**;
  no es solo que `geom` sea más cómodo, es que la otra vía tiene registros ilegibles.
- 🆕 **La coordenada del feed va en rejilla de ~110 m para el 74 %** (3 decimales en `geom`).
  Medido contra el gemelo OSM **confirmado por id** (o sea, misma estación sin duda):
  **p50 = 43 m, p90 = 134 m, max = 2.359 m.**

Ese p50 de 43 m es la explicación de la tabla de arriba: los 44 m de mediana del "cruce por
proximidad" **no eran error de cruce, eran el error de la coordenada del feed**. Por eso un umbral
estricto de 50 m descartaba pares verdaderos.

⛔ Aun así **la coordenada que se publica es la del FEED**, no la de OSM. OSM aporta el nombre y
nada más: el feed es la autoridad de "esta estación existe y cobra X", y mezclar la posición de una
fuente con el precio de otra es justo la costura que la doctrina prohíbe. Una rejilla de 110 m
deja el pin en la manzana correcta; un objeto cosido de dos fuentes no se puede depurar.

### ⛔ Trampa 3 — el silencio en Francia NO es lo que parecía

El brief daba la ruptura por trampa; la medición nacional cambia el mapeo. Cada par
estación+carburante cae en exactamente un estado (comprobado: 9.541+247+17 = 9.805 en gazole):

| Carburante | precio | ruptura (de ellas, `definitive`) | **silencio** |
|---|---|---|---|
| gazole | 9.541 | 247 (12) | 17 |
| sp95 | 2.933 | 5.947 (5.272) | 925 |
| sp98 | 7.220 | 2.354 (1.257) | 231 |
| e10 | 7.285 | 2.171 (1.591) | 349 |
| e85 | 3.935 | 4.956 (4.544) | 914 |
| gplc | 1.497 | 7.160 (6.644) | 1.149 |

**Francia declara sus negativos por `rupture definitive`, no por silencio** — 6.644 estaciones
dicen así que no dan GLP, contra 1.149 que callan. Es la misma función que la cadena vacía en
España, con otro nombre. Entonces:

| Estado del feed | → | Por qué |
|---|---|---|
| precio presente | `SOLD` | el precio ya lo prueba (`availabilityOf` lo resuelve solo) |
| `rupture_type = "definitive"` | **`NOT_SOLD`** | negativo DECLARADO: dejó de venderlo (las hay desde 2019) |
| `rupture_type = "temporaire"` | `SOLD` | lo vende, hoy no tiene. Ocultarla sería falso mañana |
| ni precio ni ruptura | **`UNKNOWN`** | nadie ha dicho nada |

⛔ El silencio **no** se lee como negativo aunque tiente: son 1.149 estaciones en GLP, y ocultarlas
por una suposición rompe la regla del proyecto (se oculta SOLO con `NOT_SOLD`). `carburants_indisponibles`
no ayuda a desempatar: contiene los 110/110 silenciosos del muestreo *y* los rotos, es un derivado
de "no está en disponibles".

### ⛔ Trampa 4 (nueva) — mapear solo SP95 dejaría muda a Francia

España mapea `Gasolina 95 E5 → GASOLINE`. Copiarlo literal aquí sería un error medido: en Francia
la E10 ya sustituyó a la SP95 (5.272 estaciones declaran ruptura **definitiva** de SP95).

- solo E10 → 7.285 estaciones con precio de gasolina
- E10 con respaldo SP95 → **9.019 / 9.805 = 92 %** (las 1.734 que tienen SP95 y no E10)

→ `GASOLINE ← e10_prix, y si no hay, sp95_prix`. Ambas son grado 95; **SP98 queda fuera a
propósito**, como España deja fuera su 98: un precio premium presentado como "el precio de la
gasolina" invierte el orden de la lista, que es la única cosa que esta pantalla promete.

### 🔑 Lo que obliga a tocar el modelo: **la hora es del precio, no de la respuesta**

Medido sobre 100 estaciones de París (305 precios):

- antigüedad del precio: p50 = **2,2 h**, p90 = **111,8 h** (4,6 días), max = **9.800 h (13 meses)**
- dispersión DENTRO de una misma estación: p50 = 0,00 h, p90 = 10,5 h, max = 9.799 h

Un solo `reportedAt` para toda la respuesta miente en las dos direcciones y no hay tercera opción:
- **la más antigua** → una estación con un precio de hace 13 meses sella la lista entera como de
  hace 13 meses, y la lista está mayoritariamente fresca (p50 = 2,2 h).
- **la más nueva** → tapa exactamente el precio rancio que el usuario necesita descartar.

Y esto es la razón de ser de toda la función: FuelWide se rechazó por ir **doce horas** por detrás.
Aquí la cola es de trece meses. Un `reportedAt` por respuesta no es una simplificación: es el
mismo fallo que motivó la fase.

## Doctrina violada (si no se cambia el modelo)

- *"Un precio sin su hora al lado es un precio que nadie puede pesar"* (`FuelStationsResult`), que
  hoy se cumple por respuesta y en Francia deja de cumplirse por precio.
- **Sistemas, no parches**: el invariante "todo precio lleva su hora" tiene que vivir en UN sitio
  —el propio precio— y no en un campo paralelo que un `Map` pueda desalinear.

## Diseño

### 1 · El precio deja de ser un `Money` suelto

`FuelStation.prices: Map<FuelType, Money>` → `Map<FuelType, StationPrice>`, donde

```kotlin
data class StationPrice(val amount: Money, val reportedAt: String?)
```

Hace **imposible** tener un precio sin su hora, que es el invariante. Y es la caja donde Italia
meterá `isSelf` sin volver a mover el modelo (33.483 pares con `self`/`servito` a 0,36 €/L de
diferencia — el siguiente país que ya sabemos que lo necesita).

- España: los tres precios reciben la ÚNICA hora del feed → no pierde ni gana información.
- OSM: sin precios, nada que sellar.
- Francia: cada uno la suya.

`reportedAtMs` es **epoch millis**, no la cadena del feed. Las dos fuentes la escriben distinto
(`"08/09/2026 23:52:46"` vs `"2026-09-10T00:01:00+00:00"`) y ninguna de las dos es una frase que
lea un conductor. Como instante se convierte en **edad**, que es lo único que el lector quiere, y
`relativeTimeText` ya la dice en los 9 locales → **cero copy nuevo por este lado**.

**Y cada superficie dice lo que su fuente SABE decir** (ésta es la regla, no dos parches):

| Superficie | España (sella la RESPUESTA) | Francia (sella cada PRECIO) |
|---|---|---|
| Pie de la sección | la hora del feed, verbatim (igual que hoy) | «Precio más antiguo: hace X» — una **cota**, no una afirmación de uniformidad |
| Filas del peek | edad por carburante (todas iguales) | edad por carburante (distintas de verdad) |

⛔ La cota no es «la más antigua» disfrazada: *«los precios son de hace 8 días»* sería falso — la
mediana es de 2,2 h. *«ninguno es más viejo que 8 días»* es cierto y es todo lo que una sola línea
puede prometer honestamente sobre una lista con esa dispersión.

`FuelStationsResult.reportedAt` se mantiene para el primer caso y **Francia lo deja en null**,
porque es la verdad: esa fuente no sella respuestas.

### 2 · `FranceFuelStationsSource` — un fichero en commonMain

- `covers("FR")`, entrada nueva en `national` de `FuelStationsRegistry`. Nada más cambia arriba.
- Transporte por `fuelHttpClient()` — ⛔ nunca `HttpClient()` a pelo
  [`fuel-layer-follows-the-camera-001.md`](fuel-layer-follows-the-camera-001.md).
- `select=` para no bajar 2,7 KB por estación: medido, París 8 km pasa de **275 KB a 13,7 KB**.
- `limit=100` es el techo de la API (101 → 400). Con `order_by` distancia, son las 100 más cercanas.
- `FranceStationParser` en commonMain con fixture real y test, como `MineturStationParser`.

### 3 · El nombre: `OsmStationNames`, best-effort y **jamás** causa de fallo

Consulta Overpass por los ids que ya tenemos y devuelve `id oficial → nombre`. Reglas:
- **Cruce por `ref:FR:prix-carburants`.** Nunca por distancia.
- Si Overpass falla, tarda o no lo conoce → la estación sale **sin nombre, con su dirección**, que
  es lo que ya hace la UI con las nodo-OSM anónimas. ⛔ Un fallo del nombre no puede tumbar un
  precio: el precio es el producto.
- **Guarda de cordura**: se acepta el nombre solo si el gemelo está a <1 km (1 par de 156 medido a
  2.359 m: o el feed o OSM está mal en ese registro, y no sabemos cuál).
- **Ref duplicada** (3 casos en París): si los candidatos no coinciden en nombre → sin nombre.
  Fallo asimétrico: mejor sin nombre que con el del vecino.

## Criterio de éxito

- Parser con **fixture real** del feed cubriendo: `geom` vs `latitude` con punto decimal, ruptura
  definitiva/temporal, silencio, E10 con respaldo SP95, hora por precio.
- Test de que una **ruptura no se pinta como precio 0 ni como "no lo vende"**: `temporaire` →
  visible sin precio; `definitive` → `NOT_SOLD`.
- Test de que el cruce de nombres es **por id** y que un Overpass caído deja el precio intacto.
- Suite verde · Konsist · `assembleMockDebug` + `prodDebug` · klib iOS · strings ×9 si hay copy.
- Verificación en device con GPS en Francia (⛔ emulador COMPARTIDO: sha256 antes y después, y la
  verdad es la **captura**, no `uiautomator dump`).

## Consumidores auditados

`grep` de `station.prices` / `NearbyFuelStation.price` / `reportedAt` en producción — **todos
cerrados**, el compilador obligó a visitarlos uno a uno al cambiar el tipo:

| Sitio | Estado |
|---|---|
| `GetNearbyFuelStationsUseCase` (`NearbyFuelStation.price`) | ✅ pasa a `StationPrice?` |
| `FuelStationPeek` (filas de precio) | ✅ `perUnit` + edad por precio |
| `HomeFuelSection` (nota al pie) | ✅ dos ramas: hora de respuesta / cota de edad |
| `HomeFuelSlice` | ✅ gana `oldestPriceAtMs` (la cota) |
| `HomeFuelStationRows` (`FuelPriceLabel`) | ✅ `price.perUnit` |
| `PaparcarMapView` ×2 (cápsulas del mapa) | ✅ `it.perUnit.amount` |
| `MineturStationParser` | ✅ sella los 3 precios con la ÚNICA hora del feed, parseada en `Europe/Madrid` |
| `OsmFuelStationParser` (`prices = emptyMap()`) | ✅ exento: no produce ni un precio |
| `FakeFuelStationsSource` (grafo mock) | ✅ fecha los precios; si no, el mock escondía la línea de edad |
| `StateGalleryScreen` | ✅ al tipo nuevo **+ variante «Precio con hora PROPIA (Francia)»** |
| Tests: `MineturStationParserTest`, `FuelStationsRegistryTest`, `GetNearbyFuelStationsUseCaseTest`, `HomeViewModelTest` | ✅ barridos |

Extra de higiene, no un añadido: el bucle de failover de Overpass estaba **dentro** de
`OsmFuelStationsSource`, y Francia necesitaba el mismo para los nombres. Extraído a
`OverpassClient` en vez de copiarlo — dos copias de un retry son dos sitios donde confiar en un
mirror que no lo merece.

## Verificado

**Suite: 2.603 tests, 0 fallos** (`--rerun-tasks`, para que los guardarraíles Konsist se ejecuten
de verdad y no salgan UP-TO-DATE) · `assembleMockDebug` + `assembleProdDebug` · klib iOS
(`compileKotlinIosSimulatorArm64`) · paridad de los 9 locales verde con la key nueva.

Tests nuevos: `FranceStationParserTest` (**12**, fixture verbatim de 6 estaciones reales) y
`FranceFuelStationsSourceTest` (**11**, `MockEngine`, petición incluida).

### En device — prodDebug `a5a0ed6e…`, emulador compartido (10-09-2026)

sha256 comprobado en el device **antes y después**: idéntico, así que nada de lo que se vio era el
build de otra sesión. Verdad = **captura**, nunca `uiautomator dump`.

**Francia, GPS en Toulouse (1.4442, 43.6047):**
- La sección lista gasolineras reales ordenadas por distancia, **con nombre**: TotalEnergies, Total
  Access, Avia, Esso, Dyneff, Auchan, Esso Express… El feed **no trae ninguno** — vienen de OSM por
  `ref:FR:prix-carburants`, en producción, sobre datos reales.
- `55 BOULEVARD DE SUISSE · 1.990 EUR` es la estación **31200022** del fixture, con el precio de E10
  que dijo el feed: la cadena entera cuadra de punta a punta.
- Una fila sale **sin nombre, por su dirección** (`65 ROUTE D AGDE · Toulouse`): el ~3 % sin gemelo
  en OSM, degradando como se diseñó en vez de tomar prestado el nombre del vecino.
- Pie: **«Oldest price: 8 days ago»** — y eso mismo es la prueba de campo del ticket: dentro de 5 km
  de Toulouse conviven precios de horas con uno de 8 días. Un sello único habría mentido.
- Peek de `Esso · 90 Route de Revel`: `Gasoline 2.118 EUR / 17 h ago` y `Diesel 2.287 EUR / 17 h
  ago`, cada precio con su edad y **sin** línea de respuesta.

**España, GPS en Sol — sin regresión:** MOEVE, ALCAMPO, STAR PETROLEUM, BALLENOIL, REPSOL con sus
precios y el pie verbatim del Ministerio, `Prices reported at 10/09/2026 3:16:48`, exactamente como
antes de esta tarea.

## Fuera de alcance (apuntado, no hecho)

- La coordenada del feed va en rejilla de ~110 m para el 74 % de las estaciones. Con el id casado
  tendríamos la de OSM, más precisa, pero **no se toca**: el feed es la autoridad del objeto y coser
  posición de una fuente con precio de otra es la costura que la doctrina prohíbe. Si algún día
  molesta al navegar, es ticket propio y con medición propia.
