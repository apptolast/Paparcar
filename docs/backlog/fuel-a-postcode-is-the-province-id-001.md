# FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001 · el código postal YA trae el id de provincia del ministerio, y lo estamos tirando

**Estado:** ✅ **Done (planes A y B)** · en master, rebasado sobre `213b4596`
· **Paso 0 ✅ MEDIDO (12/12)** · ⚠️ medido en emulador, no en device (MIUI bloquea los APK de test)
· ⚠️ **Room v8 → v9** · ⏳ sin ver en campo

Sale de `FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001` (follow-up 0). Aquel arregla
que un geocode mudo **mienta**; éste es el siguiente escalón: que un geocode mudo —o torpe— deje de
**costar los precios**.

## Problema

Para preguntarle precios al ministerio hay que saber la provincia o el municipio: su API indexa por
**id**, no tiene endpoint geográfico, y sus dos listados (`/Listados/Provincias/`,
`/Listados/Municipios/`) traen ids y nombres y **cero coordenadas**. Así que hoy toda la vía depende
de emparejar **nombres** que vienen de un geocoder:

- `SpanishMunicipalities` tiene que quitar acentos **y mover el artículo** («Puerto de Santa María
  (El)» ↔ «El Puerto de Santa María»).
- `SpanishProvinces` tiene que quitar acentos y aun así falla cuando el geocoder da la comunidad
  autónoma («Andalucía» no es provincia) — el fallo entero de
  `HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001`.
- Y si el geocoder calla, no hay ni nombre ni provincia: no se puede preguntar en absoluto.

## El hallazgo

Medido en vivo el **14-09-2026** contra el propio listado del ministerio:

```
/Listados/Provincias/ → 52 filas, ids '01'…'52'
  11 → CÁDIZ · 28 → MADRID · 17 → GIRONA · 07 → BALEARS (ILLES)
  35 → PALMAS (LAS) · 38 → SANTA CRUZ DE TENERIFE · 51 → CEUTA · 52 → MELILLA
```

**Los ids del ministerio son los códigos INE**, y los códigos INE son por construcción **los dos
primeros dígitos del código postal español**. O sea:

> `postcode.take(2)` **ES** el `IDProvincia` que la API pide.

Sin tabla, sin dataset vendorizado, sin emparejar nada: se acaban los acentos, los artículos entre
paréntesis, las comarcas y las comunidades autónomas. Y es validable **gratis** contra
`/Listados/Provincias/`, que ya se descarga y se cachea una vez por proceso.

Esta vía no tiene que decidir ninguna frontera, porque no las inventa: usa la división que el
ministerio ya usa. Si el postal no llega (paso 0), hay **plan B** — abajo, y no es el que yo había
descartado.

## Doctrina violada

- **Una dirección viaja entera** (`FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001`). El geocoder
  de plataforma rellena `Address.postalCode` y `AddressInfo` **no lo lleva**, así que se tira en la
  frontera. Es el mismo hueco por el que se cayó el `countryCode`, con otro campo — y con la misma
  excusa implícita: nadie lo leía.
- **Sistemas, no parches.** Dos ficheros enteros (`SpanishMunicipalities`, `SpanishProvinces`)
  existen para emparejar nombres que el ministerio escribe de otra forma. Con la clave numérica
  dejan de ser el camino principal y pasan a ser la red de seguridad.

## ✅ Paso 0 — MEDIDO el 14-09-2026: 12/12

Sonda `PlatformGeocoderPostcodeProbe` (`shared/src/androidInstrumentedTest/`), 12 puntos elegidos
para atacar lo que podía romperlo — pueblo pequeño, área de autovía, polígono, isla y las dos
ciudades autónomas:

| punto | postal | → id | provincia del ministerio | `subAdminArea` del device |
|---|---|---|---|---|
| El Puerto de Santa María | 11500 | 11 | CÁDIZ | Cádiz |
| Madrid · Sol | 28013 | 28 | MADRID | Madrid |
| Sevilla · Plaza Nueva | 41001 | 41 | SEVILLA | Sevilla |
| Girona | 17002 | 17 | GIRONA | Girona |
| Elx (coord. de Alicante) | 03202 | 03 | ALICANTE | Alicante |
| León | 24003 | 24 | LEÓN | León |
| Trujillo (pueblo pequeño) | 10200 | 10 | CÁCERES | Cáceres |
| A-4 pk 300 (autovía) | 23700 | 23 | JAÉN | Jaén |
| Políg. San Fernando de Henares | 28830 | 28 | MADRID | Madrid |
| Palma de Mallorca | 07001 | 07 | BALEARS (ILLES) | Illes Balears |
| **Ceuta** | 51001 | 51 | CEUTA | **— ausente** |
| **Melilla** | 52001 | 52 | MELILLA | **— ausente** |

**12/12 con postal, y los 12 prefijos son un `IDProvincia` válido** del listado real del ministerio.
Donde el device da `subAdminArea`, coincide.

🔑 **Ceuta y Melilla no traen `subAdminArea`**: ahí el camino de hoy se queda **sin clave de
provincia** y el postal es el único que la tiene. Y `adminArea` confirma otra vez la premisa del bug
viejo — «Comunidad de Madrid», «Andalucía», «Catalunya»: comunidades, no provincias.

### ⚠️ Lo que esta medición NO dice

- **Es un emulador** (`Pixel_8_Pro`, API 37, `google_apis_playstore`, locale `en_US`), no un
  teléfono. Mide el **backend de geocodificación de Google**, que es quien decide si hay postal —
  la pregunta de fondo— pero **no** mide variación por OEM. ⛔ No se pudo correr en el Redmi: MIUI
  rechaza los APK **de test** con `INSTALL_FAILED_USER_RESTRICTED` (pide «depuración USB (ajustes de
  seguridad)»), y no se tocan los ajustes de los móviles. Confirmar en device sale gratis en el
  próximo field-test.
- **⛔⛔ El postal NO rescata un geocoder MUDO.** Un geocoder que no contesta no da postal, igual que
  no da ciudad ni provincia. Esto hay que decirlo claro porque el ticket nació del ticket del
  geocode mudo:

> **Plan A y plan B no son alternativas: contestan a fallos distintos.**
> El **postal** mata la fragilidad de emparejar NOMBRES (acentos, artículos, comarcas, comunidades,
> y las dos ciudades autónomas sin provincia) siempre que el geocoder conteste.
> El **plan B** (lat/lon → provincia) es lo único que rescata al geocoder **mudo**.

Así que el orden de valor cambia: el plan A es barato, está medido y arregla el caso frecuente; el
plan B sigue siendo el único que cubre el caso del reporte de campo. Se pueden hacer los dos, y el
plan A **no** es condición del plan B.

## Señales / datos ya disponibles

Photon `/reverse` medido el 14-09-2026 (8 puntos, sin `lang` para que devuelva el nombre local):

| punto | `postcode` | provincia deducida | `county` |
|---|---|---|---|
| El Puerto de Santa María | `11500` | ✅ 11 CÁDIZ | Bahía de Cádiz ⚠️ comarca |
| Madrid (Sol) | `28013` | ✅ 28 MADRID | *(ausente)* |
| Girona | `17002` | ✅ 17 GIRONA | Gironès ⚠️ comarca |
| Valencia | `46002` | ✅ 46 VALENCIA | Comarca de València ⚠️ |
| Ceuta | `51002` | ✅ 51 CEUTA | *(ausente)* |
| León | `24003` | ✅ 24 LEÓN | León ✅ |
| Sevilla (Pza. Nueva) | **null** | ❌ | Sevilla ✅ |
| Alicante | **null** | ❌ | el Baix Vinalopó ⚠️ comarca |

**6 de 8 con postal.** Y el `county` NO sirve como sustituto general: unas veces es la provincia y
otras la comarca, que es la trampa de «Andalucía» con otra ropa.

## ✅ Plan A — IMPLEMENTADO (14-09-2026)

1. **`AddressInfo` gana `postalCode`** y viaja entero: los dos geocoders lo mapean, las tres tablas
   de Room lo guardan (**migración `MIGRATION_8_9`**, v8 → v9, gemela de la de `province`), el DTO de
   Firestore y el `workData` del worker lo llevan.
2. **`SpanishProvinces.idFromPostalCode`** — clave **0** en Minetur, antes que el municipio por
   nombre y que la región. No descarga catálogo: hace aritmética. Valida la forma (cinco dígitos,
   prefijo `01`..`52`) para que un postal extranjero no se convierta en provincia por accidente.
3. **`FuelStationsQuery.postalCode`**, rellenado por `GetNearbyFuelStationsUseCase`.

### ⛔ Lo que apareció por el camino, y es la mitad del valor del ticket

**La pregunta «¿hay dirección aquí?» estaba escrita TRES veces y las tres discrepaban.**
`addressOrNull`, `SpotDtoMapper` y `ReportSpotWorker.toAddressInfo` enumeraban a mano los campos que
a alguien le parecieron los importantes — y la de las sesiones aparcadas **se dejaba fuera
`province`**, así que una dirección que solo tuviera provincia volvía como `null` y se tiraba. Un
bug vivo, de la misma familia que el ticket que añadió el campo.

Ahora vive en **`AddressInfo.isBlank`** y en ningún otro sitio, leyendo todos los campos por
construcción: un campo nuevo entra solo. Y se aplicó también al **cuarto lector** (`SpotDto.toDomain`),
que no tenía la regla: un documento antiguo de Firestore con un `address` a nulos volvía como una
dirección que no nombra nada, y aguas abajo `address != null` decía que sí la había.

### 🔑 Y el guardarraíl que faltaba

Room ya tenía uno por reflexión (`ParkingAddressSurvivesEnrichmentTest`) desde que se descubrió que
`countryCode` se había caído de cuatro vías a la vez. **Las otras dos no tenían ninguno** — y son
exactamente las mismas por las que se cayó. `AddressTravelsWholeTest` las cubre ahora: cualquier
propiedad nueva de `AddressInfo` pone el test en rojo hasta que alguien la rellene, y entonces los
viajes de ida y vuelta comprueban si de verdad sobrevive por las TRES vías. No hay lista de
exenciones que mantener: la enumeración la hace la reflexión, no una persona.

Falsificado quitando la escritura del postal en el `workData` → **FAILED**, que es precisamente el
fallo silencioso que nadie vio con `countryCode`.

### Verificación (plan A + plan B juntos)

`:shared:testDebugUnitTest --rerun-tasks` → **2.933 tests, 0 fallos** (sobre `213b4596`). Prod, mock,
`iosSimulatorArm64` y `assembleMockDebug` verdes. Sin strings nuevos ni pantallas nuevas → no hay
locales ni Dev Catalog que tocar. Testigos falsificados uno a uno: la tabla de provincias, el
enrutado por `claims`, la regla del vacío adivinado, la validación del postal, la clave 0 y la
paridad del `workData`.

## Diseño (borrador — se confirma tras el paso 0)

1. **`AddressInfo` gana `postalCode`**, y viaja entero por las vías de escritura de abajo. Sin
   excepciones por «nadie lo lee»: esa frase es exactamente la que costó el `countryCode`.
2. **`MineturStationsSource` gana una clave 0**, antes del municipio: el postal. Orden final —
   *postal → municipio → provincia por nombre*. Las dos últimas se quedan intactas como red.
3. **El id se valida contra `/Listados/Provincias/`** (ya cacheado) en vez de confiarse: un postal
   mal formado o extranjero no puede colarse como id.
4. **Photon `/reverse` como segunda opinión** SOLO cuando el geocoder de plataforma falla — que
   ahora es un `failure` de verdad y se puede detectar, gracias al ticket padre. Coste: una petición
   de red en un camino que hoy no tiene respuesta ninguna. ⛔ Y solo para el país y el postal: NO
   para nombrar sitios, que es lo que `POI-A-PLACE-IS-NAMED-ONLY-IF-YOU-ARE-AT-IT-001` acota.

> ⚠️ Decidir en el paso 1, no antes: si el postal se **persiste** o solo se usa en vuelo. Persistirlo
> cumple la doctrina y cuesta **migración de Room** (hoy `PAPARCAR_DB_VERSION = 7`, `MIGRATION_6_7`
> es la última) más los serializadores. No persistirlo evita la migración pero reabre el juicio
> campo por campo que la doctrina prohíbe. La recomendación es persistirlo; la decisión es del user,
> porque el coste es suyo.

## ✅ Plan B — IMPLEMENTADO (14-09-2026)

Tres piezas, y ninguna toca persistencia (cero migración de Room):

- **`SpanishProvinceBounds`** (`data/fuel/`) — 52 filas generadas, 4,4 KB, con el comando que las
  reproduce en su KDoc. `idFor(lat, lon)` devuelve el `IDProvincia` o null.
- **`FuelStationsSource.claims(query)`** — nuevo, con `covers(countryCode)` por defecto, así que
  ninguna otra fuente escribe nada. ⛔ Reclamar por coordenadas es derecho de la FUENTE, no del
  registro: quien sabe hasta dónde llega su país es quien lo sirve. Si viviera en el registro, la
  geografía de ocho países acabaría dentro de un `when`.
- **`MineturStationsSource`** — `claims` reconoce España por coordenadas **solo si el país viene
  vacío** (si el geocoder dijo «PT», dijo PT: una caja rebosa fronteras y no está para corregir a
  quien sí contestó), y las coordenadas entran como **tercera clave** de provincia, detrás del
  municipio y del nombre de región.

### ⛔ La regla que hace que esto no mienta

**Una provincia adivinada no puede dictar un veredicto.** Si la provincia salió de las coordenadas
y la respuesta viene VACÍA, se lanza en vez de devolver la lista vacía — porque lo más probable no
es que no haya gasolineras, sino que hemos preguntado a la provincia de al lado. El registro cae
entonces al feed sin precios, que es exactamente lo que había antes de poder adivinar: no se pierde
nada y no se afirma «no hay ninguna a 5 km».

Con provincia **nombrada**, el vacío sí es respuesta y se respeta. Los dos tests van adosados.

### Lo que cambia para el usuario, medido

En la vía del geocoder mudo, que hoy da 0 % de precios:

| | |
|---|---|
| provincia correcta → **precios** | **94,6 %** |
| provincia vecina, con gasolineras reales a ≤5 km → **precios útiles** | 0,8 % |
| provincia vecina sin nada cerca → feed sin precios, marcado (= lo de hoy) | 4,6 % |

Sin regresiones: el peor caso es el estado actual.

### Verificación

`:shared:testDebugUnitTest --rerun-tasks` → **2.909 tests, 0 fallos**. Prod, mock e
`iosSimulatorArm64` compilan. Testigos falsificados uno a uno: la tabla (tumba 5 tests), el
enrutado por `claims` (1), y la regla del vacío adivinado (1).

⚠️ **Un test del ticket anterior cambió de fixture, no de intención.**
`should_notFetchTheProvinceCatalogue_when_thereIsNoRegionToLookUp` usaba Cádiz sin ciudad ni región,
que era un callejón y ahora lo rescatan las coordenadas. Lo que vigilaba no era el callejón sino la
descarga inútil del catálogo, así que el punto pasa a París — donde ninguna clave puede resolver — y
el invariante sigue exactamente igual de vigilado.

## Plan B — el diseño, y por qué la tabla se genera

Si el postal no llega, la provincia se saca de las coordenadas. Y lo que yo había descartado era una
tabla de geografía hecha a mano; **esto no es eso**, y la diferencia es todo el ticket:

Medido el 14-09-2026, cada fila de estación trae su propia adscripción:

```
GET /EstacionesTerrestres/FiltroProvincia/11   → 291 estaciones en Cádiz
  'IDProvincia' = '11'   'IDMunicipio' = '1762'
  'Latitud' = '36,460000'   'Longitud (WGS84)' = '-5,730944'
```

Así que la tabla **se genera, no se inventa**: una vez, en una máquina de desarrollo, se baja el
país entero (`/EstacionesTerrestres/`, ~12 MB, ~12.000 filas), se agrupan por `IDProvincia` y se
calcula la caja envolvente de cada una **a partir de coordenadas reales de gasolineras**. Se
vendoriza el RESULTADO: 52 filas × 4 números ≈ 1 KB en `commonMain`, con el comando que lo produce
escrito al lado para poder rehacerlo.

Tres cosas que lo hacen mejor que una tabla de fronteras administrativas:

- **Está hecha de lo que buscamos.** No nos importa dónde acaba la provincia; nos importa dónde hay
  surtidores. Una caja derivada de los surtidores es exactamente la pregunta que hacemos.
- **Sale de la misma fuente que responde.** Si el ministerio reasigna un municipio, la tabla se
  regenera de su propio dato, no del recuerdo de nadie.
- **Nada que mantener a mano**, que era la única objeción seria contra esta vía.

⚠️ Su punto débil, dicho claro: **las cajas se solapan** (las provincias no son rectángulos), así
que un punto puede caer en varias. Se resuelve escogiendo, entre las cajas que lo contienen, la del
centroide más cercano — y un error de provincia cerca de una frontera no inventa precios: el filtro
por radio en el móvil (`withinRadius`) devuelve lista vacía y el camino ya sabe ensanchar. Fallo
asimétrico, como debe ser.

⛔ Lo que **no** es plan B: acumular un índice coordenada→provincia según se van cacheando
respuestas. Es tentador y mejora con el uso, pero en una instalación nueva está vacío — o sea que
no sirve justo en el caso que este ticket persigue. Vale como acelerador, nunca como cimiento.

## Criterio de éxito

- Un punto en Sevilla con postal `41004` y el geocoder dando «Andalucía» como región y ningún
  municipio reconocible → se pregunta `FiltroProvincia/41` y salen precios. Test con un geocoder
  fake, sin red.
- Un postal que no es de ninguna de las 52 filas → no se usa, se cae al camino de nombres. Test.
- Un geocoder mudo + Photon contestando `11500` → precios. Test con `MockEngine`.
- **(plan B)** un punto en Jerez sin postal, sin municipio y sin región → resuelve `11` por
  coordenadas y salen precios. Y un punto en Lisboa NO resuelve ninguna provincia española.
- `AddressInfo` con postal sobrevive a un ida y vuelta por Room, por el DTO de Firestore y por el
  `workData` del worker. Un test por vía — son las cuatro por las que se cayó el `countryCode`.

## Consumidores auditados (vías por las que viajó el `countryCode`)

| Sitio | Qué hace con los campos de dirección | Estado |
|---|---|---|
| `AndroidGeocoderDataSourceImpl.toAddressInfo` | enumera los campos que mapea | 🔵 **la fuente del postal** |
| `IosGeocoderDataSourceImpl.toAddressInfo` | idéntico (`CLPlacemark.postalCode`) | 🔵 gemelo, misma tarea |
| `GeocoderCacheEntity.addressCountryCode` | fila cacheada por celda | 🔵 columna nueva + migración |
| `UserParkingEntity.addressCountryCode` | sesión persistida | 🔵 ídem |
| `SpotEntity.addressCountryCode` | plaza publicada | 🔵 ídem |
| `UserParkingDao.updateAddressAndPlace:169-194` | SQL con lista EXPLÍCITA de columnas | 🔵 ⚠️ el que sobrescribe en bloque (follow-up 2 del ticket padre) |
| `SpotDto` + `SpotDtoMapper` | serialización a Firestore | 🔵 paridad end-to-end |
| `ReportSpotWorker:167,194-208` | `workData` campo a campo | 🔵 ⚠️ un `set()` sin merge ya mandó campos a null una vez |
| `RoomGeocoderCacheDataSource` | lee/escribe la celda | 🔵 |
| `SpanishProvinces` / `SpanishMunicipalities` | emparejan NOMBRES | ⚪ intactos: pasan a ser red de seguridad, no camino principal |
| `FranceFuelStationsSource`, `Osm*` | preguntan por lat/lon | ⚪ exentos: no necesitan provincia |

## Fuera de alcance

- **Otros países por código postal.** Francia no lo necesita (radio nativo) y el resto va por OSM.
  La aritmética postal→provincia es una propiedad del sistema postal ESPAÑOL, no una regla general:
  no convertirla en abstracción hasta que un segundo país la pida.
