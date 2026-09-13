# PARKINGS-ON-THE-MAP-001 · Los parkings públicos ya existen el primer día, aunque no haya comunidad

**Estado:** ✅ Done — en master (hash en `MEMORY.md`, por construcción del cierre) · **es la F6a**;
la ocupación en tiempo real queda en [`parkings-live-occupancy-001.md`](parkings-live-occupancy-001.md).

> ⚠️ **Superado en parte por [NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001](nearby-one-store-follows-the-camera-001.md)** (13-09-2026): las tiendas de carril y de capa se fusionaron en UNA por sujeto, y la unión que pintaban mapa y lista desapareció. Lo que aquí se dice de dos tiendas / dos anclas es historia, no regla vigente.

> 🔁 **Rebasado DOS veces sobre master el 10-09** mientras otra sesión avanzaba el carril de
> gasolineras, y las dos veces la reconciliación fue a favor de master:
> - Mi `OverpassEndpoints`/`overpassQuery` se **borró** en favor de `OverpassEndpoints` +
>   `OverpassResponseHealth` + `OverpassClient` de [OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001]:
>   validan el espejo por su `timestamp_osm_base` en vez de por una lista negra escrita a mano, que
>   es una regla general donde la mía era un parche a un caso.
> - Mi `LayerRefetchPolicy` (cooldown tras fallo) se **borró** en favor de `ViewportRefetch` de
>   [FUEL-LAYER-FOLLOWS-THE-CAMERA-001]: la capa sigue a la CÁMARA, así que la tormenta de reintentos
>   que yo apagaba con un cooldown **deja de existir por construcción** — solo se pregunta en eventos
>   de cámara, que solo ocurren mientras el usuario mueve el mapa.
> - Y el fallo real que me dejó sin datos en el Oppo estaba ya diagnosticado ahí: **Overpass responde
>   406 al User-Agent por defecto de OkHttp**. Lo arregla `fuelHttpClient()`, que ahora usan los dos
>   carriles.
>
> Lo que sí sobrevivió como aportación propia: `LocationKeyedCache` (la clave por ubicación en UN
> sitio, usada por las dos cachés) y el menú de capas, que absorbe el toggle de gasolineras.

## Verificado en device — 10-09-2026, Oppo CPH2371

**prodRelease firmado** (`ac1964fe…` y luego `3f2d4232…`, sha device↔local comprobado, `install -r`
sin perder sesión): la app arranca con R8, el **menú de capas** pinta sus dos filas con su check, las
**tres secciones** salen en el orden fijo (plazas · gasolineras · parkings) y la de parkings dice
«no se pudieron cargar» — el fallo honesto — cuando Overpass la rechaza.

**mockDebug** (`…paparcar.mock`, otro applicationId, convive sin tocar la sesión de producción) para
lo que la red no dejaba ver. Las cuatro filas, cada una probando una decisión distinta:

| Fila | Qué demuestra |
|---|---|
| Parking Plaza Mayor · 283 m · Underground car park · **Paid** | nombre + tipo + tarifa declarada |
| Aparcamiento Estación · 602 m · Surface car park · **Free** | «gratis» solo cuando la fuente lo dice |
| **Multi-storey car park · 877 m** | ⭐ sin nombre → **titulado por su TIPO**; el subtítulo NO repite lo que el título ya gastó; sin tarifa, porque `UNKNOWN` calla |
| Ayuntamiento · 1,2 km · Surface car park | sin nombre → cae al operador |

**Peek del que no tiene nombre**: eyebrow `PUBLIC CAR PARK`, título `Multi-storey car park`, sin
subtítulo, **877 m · 2 min drive** (en coche, no andando), cuerpo con **`Max height 1.9 m` y nada
más**, y `Directions`. La cortedad del peek ES la respuesta honesta.

⏳ **Lo único sin ver en mano:** los marcadores «P» sobre tiles reales. El mock no carga tiles (sin
clave de Maps) y en prod Overpass estaba bloqueando esa IP. Los datos existen y están medidos (54
parkings en El Puerto, 72 en Madrid, con la consulta EXACTA que construye la app); falta la foto.

Primera mitad de la **F6 de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md) §F6), partida en
dos por decisión del user (09-09-2026):

- **F6a — esta ficha**: la capa 1 del roadmap, la **base mundial de Overpass**. Sin backend, sin
  Firestore, sin ocupación. Verificable en device en cualquier país el mismo día.
- **F6b — `PARKINGS-LIVE-OCCUPANCY-001`**, después: capa 2. `functions/parkings/` con scheduler,
  `adapters/madrid.ts` + `malaga.ts`, colección `publicParkings`, reglas e índices, fusión por
  proximidad con esta base, «hace X min» y rampa de ocupación propia. Es el **primer backend del
  proyecto** (hoy `firebase.json` declara solo `firestore` + `hosting`, y **no existe `functions/`**):
  estrena runtime, deploy, CI, secretos y Blaze. Ese coste no puede bloquear a la capa que sirve al
  mundo entero.

## Problema

El mapa de una ciudad sin comunidad está vacío. Las plazas las publica la gente, y el primer día no
hay gente: se abre Paparcar en Cuenca, o en Lyon, y no hay nada que mirar. Los parkings públicos son
el único contenido que **existe desde el minuto cero en todo el mundo** y que además responde la
pregunta de al lado — «no encuentro plaza, ¿dónde meto el coche?» —, que hoy la app no responde en
ninguna parte.

## Doctrina que aplica

- **Vocabulario** [COPY-SPOT-IS-NOT-A-PARKING-001]: nace un tercer concepto y hay que nombrarlo sin
  pisar los dos que ya existen. **plaza** = la que libera la comunidad · **aparcamiento** = tu sesión
  · **parking público** = una instalación de un tercero en la que se entra a aparcar. F6 añade su fila
  a la tabla de `CLAUDE.md`, ×9 locales.
- **Color** [UI-COLOR-DOCTRINE-001]: ⛔ la rampa 🟢🟡🔴 es EXCLUSIVA de la caducidad de plazas. Aquí
  no hay ni ocupación que graduar (eso es F6b): el marcador es `onSurface` y lo que sabe lo ESCRIBE.
- **Un caso de uso por veredicto**: `GetNearbyPublicParkingsUseCase` es un veredicto (es lo que la
  pantalla muestra). El filtro de qué es un parking de verdad es un **predicado suyo**, no un caso de
  uso.
- **Peek**: sin scroll interno (crash Infinity), `PeekState` guarda solo la identidad
  [BUG-PEEK-JITTER-001], nunca modal sobre modal, componentes de peek existentes.
- **Nunca en la superficie de detección** ni en aparcar/liberar: esto es browse, no sesión.
- **Detección intacta** — la skill `det-change` no aplica.

## Señales / datos disponibles — MEDIDO el 09-09-2026, no supuesto

Overpass `nwr(around:R)[amenity=parking][out center]`, cobertura de tags por ciudad. **`amenity=parking`
NO se parece a `amenity=fuel`**, y todo el diseño de abajo sale de estas cifras:

| | Madrid Sol | Málaga centro | Berlín Mitte | París Châtelet | Cuenca (2 km) |
|---|---|---|---|---|---|
| elementos crudos | 39 | **300 (tope)** | 223 | 68 | 90 |
| **A** · tras quitar calzada y privados | 22 | 32 | 14 | 22 | **76** |
| **B** · y además identificable | **21** | — ⚠️ | **11** | **19** | **13** |
| `name` sobre A | 72,7 % | 31,2 % | 14,3 % | 72,7 % | **2,6 %** |
| `fee` · `capacity` · `maxheight` (sobre A) | 16 · 2 · 2 | 19 · 6 · 6 | 7 · 10 · 1 | 17 · 6 · 2 | 11 · 0 · 0 |

Reparto por tipo tras A: París **`underground` 18** · Berlín **`surface` 12** · Cuenca `<none>` 59.
Lo que descarta cada filtro: Málaga tira **256 por calzada** y 12 por acceso; Berlín 123 y 86.
⚠️ Málaga no llegó a medirse en la pasada B (Overpass rechazó la conexión cuatro veces); sus cifras A
son de la pasada anterior y su B se completa antes de cerrar. `raw=300` es el tope de la consulta:
hay más.

Cuatro conclusiones que mandan sobre el diseño:

1. ⛔ **`amenity=parking` es sobre todo la CALLE** (`street_side`, `lane`): **256 de 300** en Málaga,
   35 de 70 en París. Eso no es un parking público — es exactamente el bordillo donde Paparcar busca
   plazas. Pintarlo sería duplicar nuestro producto con polígonos anónimos. Se filtra **en la consulta
   de Overpass**, no en el móvil: es la mayor parte del payload y no se gastan los datos del usuario
   en descargar 256 polígonos para tirarlos.
2. ⛔ **El nombre no puede ser la identidad de la fila.** La gasolinera se titula por su rótulo porque
   OSM la nombra al 100 %; el parking cae al **2,6 %** en Cuenca. Titula el TIPO cuando no hay nombre.
3. ⛔ **Quitar calzada y privados NO BASTA.** Cuenca seguía respondiendo **76 elementos, 2 con nombre
   y ninguno off-street**: 74 explanadas anónimas sin tipo, sin aforo, sin tarifa. Un mapa de eso es
   ruido sobre el mapa. De ahí el predicado de **identificabilidad** (off-street · o nombre · o
   tarifa declarada · o aforo declarado): en Madrid quita 1 de 22, en Cuenca deja 13 de 76 — los que
   alguien catalogó de verdad.
4. ⛔ **Ausencia ≠ negativo, otra vez.** Sin `fee` no es «gratis». Misma lección que produjo
   `FuelAvailability {SOLD, NOT_SOLD, UNKNOWN}`, y aquí muerde más: decir «gratis» por silencio manda
   al usuario contra una barrera que no esperaba.

**Y una excepción MEDIDA, no supuesta:** `access=customers` iba a excluirse con el resto. Los **tres**
que hay a 1 km de Sol son `fee=yes`, y dos son garajes públicos corrientes — uno operado por el
**Ayuntamiento de Madrid** («P 24h Tudescos»). Excluir el tag borraba garajes municipales del centro
de la capital, así que solo cae si además nadie dice que puedas pagar por entrar.

## Fuentes evaluadas (09-09-2026) — decidido: OSM. No rediscutir sin dato nuevo

Una fuente de parkings responde hasta tres preguntas y casi ninguna responde las tres: **dónde están**,
**cuántas plazas libres hay ahora** y **cuánto cuesta / se puede reservar**.

| Fuente | Dónde | Libres ahora | Precio | Coste real |
|---|---|---|---|---|
| **OSM / Overpass** ✅ elegida | mundial, gratis | no | no | cero, sin backend |
| Overture Maps Places | mundial, mejores nombres (OSM+Meta+Microsoft), CDLA permisiva | no | no | ⛔ es **descarga masiva GeoParquet, NO una API de proximidad** → exige backend propio |
| ParkAPI v3 / `parkapi-sources` | decenas de ciudades DE/CH/AT/DK | sí | no | servicio que hay que desplegar |
| Open data municipal (Madrid, Málaga, Zaragoza) | esas ciudades | sí | parcial | un adaptador por ciudad + Cloud Function |
| Google Places (`type=parking`) | mundial, mejor calidad, API de proximidad | no | no | ⛔ pago por consulta y la licencia **prohíbe cachear** los resultados y exige pintarlos sobre mapas de Google |
| TomTom · HERE · Parkopedia · INRIX | mundial | sí | sí | contrato comercial (~3-15 k$/año) |
| Parclick · Parkimeter · SpotHero | sus parkings | no | sí + comisión | alta como partner → `PARKING-AFFILIATE-001` |

**Por qué OSM para esta fase:** es la única mundial, gratuita, sin backend y sin atadura de licencia.
Su debilidad medida es el nombre (16 %), y por eso la fila se titula por el TIPO cuando no hay nombre.
Overture es mejor dato pero **no se puede consultar desde el móvil**: sirve el día que exista backend,
que es exactamente F6b.

## Diseño

El molde es el de F5b (`fuel`), pieza por pieza, porque ya está probado en device y porque F6b tiene
que poder enchufar una fuente con ocupación sin tocar la app.

**1 · Dominio** — `domain/parkings/`
- `PublicParking(id, name?, operator?, kind, latitude, longitude, capacity?, fee: FeeInfo, maxHeightM?, openingHours?, accessNote?)`.
- `PublicParkingKind { UNDERGROUND, MULTI_STOREY, SURFACE, GARAGE_BOXES, UNKNOWN }` — lo que se
  titula cuando no hay nombre, que es la mayoría de las veces.
- `FeeInfo { PAID, FREE, UNKNOWN }` — el mismo patrón de tres estados que `FuelAvailability`, por la
  conclusión 3. `UNKNOWN` no se pinta: se calla.
- `PublicParkingsSource` (puerto) con `covers(countryCode)` + `parkingsNear(query): Result<…>`, para
  que F6b añada fuentes con ocupación como entradas de un registro.

**2 · Datos** — `data/parkings/`
- `OsmPublicParkingParser`: query `nwr(around:R,lat,lon)[amenity=parking]; out center N;` (ways y
  relations resueltos por su centro, como el de gasolineras) + el **filtro medido**: fuera
  `access ∈ {private, no, permit, customers, employees, residents…}` y fuera
  `parking ∈ {street_side, lane, layby, on_kerb, half_on_kerb, verge}`. Lo demás entra, incluido
  `parking` ausente (la mayoría fuera de las capitales).
- `OsmPublicParkingsSource` (`covers = true`, el suelo del mundo) + `CachingPublicParkingsSource`
  (TTL 30 min, no 15: un precio cambia en el surtidor, un parking no se mueve).
- ⛔ **SIN registro por país**, y es una decisión, no un olvido. Fuel tiene registro porque cada feed
  nacional responde la pregunta ENTERA y basta enrutar por país. Aquí no: el feed de una ciudad
  traerá la ocupación de los cuatro parkings que gestiona y nada más, así que F6b tendrá que
  **enriquecer** esta base, no sustituirla. Montar hoy el registro sería inventar la junta
  equivocada antes de que exista la segunda fuente que le da forma. El puerto es el punto de
  extensión; la composición la decide F6b.
- **Radio**: el de gasolineras (5 km) es el radio de «me pilla de camino»; a un parking se va al
  DESTINO, y a 5 km en ciudad salen cientos (300 salieron a 1 km en Málaga). **1.500 m**, con tope de
  80 resultados, y se ajusta MIDIENDO en device antes de cerrar.
- **Caché**: la clave por ubicación (coordenadas redondeadas a ~110 m, radio exacto) se extrae a
  `data/cache/LocationKeyedCache.kt` y la usan las dos fuentes. Era lo único no obvio de
  `CachingFuelStationsSource` y una segunda copia a mano habría derivado de la primera.

**3 · Caso de uso** — `GetNearbyPublicParkingsUseCase(source, geocoder)` → `NearbyPublicParkings`
con distancia por `haversineMeters` y orden por cercanía. Sin filtro por vehículo en F6a (el gálibo
contra `maxHeightM` es un follow-up con su ticket: hoy solo el 3 % lo declara).

**4 · Pref y toggle** — decisión del user (09-09): **un FAB de capas con menú**, no un quinto círculo.
- `showPublicParkingsLayer` (Boolean, default false) en `AppPreferences` — 4 sitios: interfaz,
  DataStore Android, NSUserDefaults iOS, fakes. NO va en `USER_SCOPED_KEYS` (es del dispositivo).
- La columna de FABs del mapa ya tiene hasta cuatro círculos (`HomeMapFab.kt:77-129`: gasolinera,
  coche aparcado, midpoint, mi ubicación). El toggle de gasolineras de F5b-2 **se muda** dentro de un
  `MapCircleFab` de capas (`Icons.Rounded.Layers`) que despliega un menú anclado con las dos capas y
  su check. Verde cuando hay al menos una capa encendida, misma doctrina (verde = acción en curso).
  ⛔ No es modal sobre modal: es un menú anclado al FAB, no una superficie que tape la sheet.

**5 · Marcador** — `PublicParkingMarker` en `PaparcarMapMarkers.kt` (⛔ MISMO fichero: es la allowlist
del guardarraíl tipográfico, no se amplía). Cápsula tipo `ZoneMarker` con «P» + el tipo cuando lo hay.
Familia `public_parking_` en `MapMarkerIds` (prefijo disjunto + corpus del test + `MarkerTapTarget`),
con **todo el estado visual horneado en el `contentId`** — la caché de kmpmaps no refresca de otro modo.

**6 · Selección y peek**
- `HomeSelection.PublicParking(id)` + barrido OBLIGATORIO de los tres enumeradores:
  `capExpandAtPeek`, `isPureBrowsePeek` (`HomeScreen.kt`) y `showList` (`HomeBottomSheet.kt`).
- `PublicParkingPeek`: título = `name ?: operator ?: tipo`; meta = distancia y tiempo **en coche**
  (a un parking se va conduciendo, como se aprendió en el peek de gasolinera); cuerpo = solo lo que
  el dato afirma (plazas, tarifa, gálibo, horario) y silencio en lo demás; acción = `common_directions`
  con `walking = false`.

**7 · Sección en la sheet** — «Parkings cercanos» / «Public parkings» **bajo Gasolineras**, plegable
con el mismo molde, misma regla: se pliega el cuerpo, ⛔ el ORDEN nunca cambia. Fila = tipo + título
+ distancia; tap = mismo gesto que la cápsula (seleccionar + volar cámara + peek).
⚠️ Con esto la sheet tiene tres secciones. Se **mide en device** si pide pestañas (el roadmap las
permite); la apuesta de salida es que no, porque plegar ya resolvió lo mismo en F5b-2.

**8 · Transversales** — strings ×9 · galería de estados + escenario mock en la MISMA tarea ·
`MockKoinModuleVerifyTest` (⛔ puerto nuevo ⇒ declararlo también en `MockModule`, que es lo que
destapó cuatro agujeros en F5b-1) · atribución OSM en Licencias (verificar que ya la cubre) ·
fila de vocabulario en `CLAUDE.md`.

**Fuera de alcance, dicho a propósito:** ocupación en tiempo real y todo el backend (F6b),
clustering, filtro por gálibo del vehículo, tarifas detalladas, reserva y marketplaces
(`PARKING-AFFILIATE-001`).

## Un supuesto que NO se pudo medir — y por eso se construyó de forma que no haga falta

La primera versión metía calzada y acceso en la consulta como `["parking"!~"…"]`, y eso descansaba
sobre una regla del servidor que **no logré comprobar**: si un `!~` conserva los elementos que **no
llevan ese tag en absoluto**. La documentación dice que sí; Overpass empezó a rechazar conexiones por
exceso de consultas el 09-09 y me quedé sin medirlo.

**Por qué no bastaba con anotarlo:** en Cuenca **59 de 90** elementos no declaran `parking`. Si el `!~`
los borrase, la capa se vaciaría **justo en los pueblos** — donde no hay comunidad y donde esta fase
existe — y en Madrid se vería perfecta (19 de 22 son subterráneos con su tag puesto). Es decir: un
fallo que **pasa la verificación en device** y muere en el campo.

**Cómo se cerró, sin servidor:** la consulta pasa a ser una **unión de dos frases** — *sin tag alguno*
(`[!parking]`, sintaxis inequívoca) **o** *con tag y no de calzada*. Bajo cualquiera de las dos
semánticas el resultado es idéntico, porque una unión de Overpass deduplica por id de elemento. El
supuesto deja de existir en vez de quedar apuntado.
El filtro de **acceso baja al parser**: necesitaría el mismo desdoblamiento, quita mucho menos payload
que el bordillo (256 de los 300 de Málaga), y además `access=customers` **no se puede** decidir con un
filtro de tags — depende del `fee`, que solo se ve con el elemento entero delante.
Dos tests lo fijan, uno de ellos nombrando explícitamente por qué no se confía en el regex negado.

## 🐞 Encontrado EN DEVICE y arreglado — la capa que fallaba preguntaba cada 5 segundos

Verificando en el Pixel 8 (09-09, 18:23) con Overpass caído, el log dejó esto:

```
18:23:08 · 18:23:13 · 18:23:18 · 18:23:23 · 18:23:28   W HomeViewModel: public parkings lookup failed
```

**Una petición cada fix de GPS, indefinidamente.** `ensurePublicParkingsForLayer()` corre en cada fix
—que es lo que devuelve la capa tras un process death— y sólo se saltaba si había carga en vuelo o
datos ya cargados. Un fallo no deja ninguna de las dos cosas, así que el siguiente fix vuelve a
preguntar. Contra un endpoint gratuito y compartido eso no es un reintento, es una inundación: **es
exactamente lo que dejó a esta máquina bloqueada en Overpass a mitad de la verificación.**

⛔ **Y la capa de gasolineras tenía el mismo agujero**, porque copié su patrón. Se arregla como
sistema, no como parche:
- `mayAskForLayer(...)` en `presentation/home/model/LayerRefetchPolicy.kt` — **un solo sitio decide**
  si una capa encendida puede volver a preguntar. Los dos carriles lo llaman.
- `HomeFuelSlice.failed` y `HomeParkingsSlice.failed` pasan de `Boolean` a **`failedAtMs: Long?`**,
  con `failed` derivado: un booleano al lado de una hora son dos hechos que pueden discrepar.
- Un fallo compra **60 s** de silencio y luego se reintenta — nunca reintentar dejaría la capa muerta
  toda la sesión, y la vuelta de la red es justo el evento que el usuario espera.
- ⛔ Una acción **explícita** del usuario (encender la capa) limpia el fallo y pregunta ya: el
  cooldown gobierna el re-chequeo AUTOMÁTICO, no al usuario.
- Reloj inyectable en el VM (`nowMs`), porque un test que espere un minuto real es un test que nadie
  ejecuta. 6 tests de la política + 3 de VM, uno por cada mitad de la regla.

⚠️ Al mover el use case al constructor: **los parámetros nuevos van AL FINAL**. Koin construye este
VM con una lista posicional de `get()` pelados, así que uno insertado en medio desplaza todos los
demás — error cometido y corregido aquí, y anotado en el propio constructor.

## 🐞 Segundo hallazgo de device — Overpass bloquea por IP, y los móviles comparten IP

Verificando en el **Oppo** (prodRelease firmado, `ac1964fe…`) la sección dijo «no se pudieron
cargar». Las tres hipótesis se descartaron con medición, no con opinión:

| Hipótesis | Cómo se descartó |
|---|---|
| R8 se come la serialización del parser nuevo | ❌ Las **gasolineras cargan** en el MISMO APK: GM OIL 1.729, Q8EASY 1.829, precios reales del Ministerio |
| No hay red | ❌ Minetur contesta |
| La consulta nueva está mal formada | ❌ Espejo `private.coffee`: **HTTP 200, 54 parkings** en El Puerto de Santa María («Centro» subterráneo de pago, «Parking Calderón») |
| **`overpass-api.de` responde 429 a esa IP** | ✅ Reproducido: `HTTP 429` + `Dispatcher_Client … rate_limited` |

🔑 **Y eso no es sólo un problema del que desarrolla.** El límite es **por IP**, y los operadores
móviles meten cientos de abonados detrás de una sola dirección (CGNAT): un usuario puede quedarse sin
capa porque un desconocido de su misma operadora estuvo consultando. La capa de gasolineras **fuera
de España** cuelga del mismo endpoint.

**Arreglo (decisión del user):** el endpoint pasa a ser una **lista** en
`data/overpass/OverpassEndpoints.kt` — y ese fichero es ahora el ÚNICO que conoce la URL, donde antes
había **cinco copias** del literal repartidas por commonMain, androidMain e iosMain. `overpassQuery()`
las prueba en orden y cae al siguiente ante **429 / 5xx / fallo de red**, porque eso es una propiedad
del HOST, no de la pregunta. ⛔ **No** cae ante un **400**: una consulta rechazada es un bug nuestro y
todos los espejos la rechazarán igual — reintentar sólo lo escondería detrás de tres peticiones.
Los dos carriles OSM (parkings y gasolineras) usan el helper; los de POI/carreteras hablan
`HttpURLConnection`/`NSURLSession` y de momento sólo leen `PRIMARY` de la lista — anotado como
follow-up en vez de duplicar la lógica.

## Criterio de éxito

- Capa apagada por defecto; el menú de capas enciende/apaga cada una y la elección sobrevive al
  reinicio; con la capa apagada no sale una sola petición.
- Con la capa encendida en una ciudad cualquiera: marcadores «P» solo de parkings de verdad — ⛔ ni
  un `street_side`, ni un `access=private`.
- Un parking sin nombre (la mayoría) se lee igual de bien que uno con nombre.
- Nada se afirma por silencio: sin `fee` no dice «gratis», sin `capacity` no dice un número.
- Tap en marcador o en fila → el mismo peek, con cómo llegar en coche.
- Suite + Konsist + `assembleMockDebug` + `compileProdDebugKotlin` + klib iOS verdes; galería en
  paridad; strings ×9.
- Verificado en device con datos reales en dos sitios distintos (una capital y un pueblo).

## Consumidores auditados

- **`HomeSelection` (arm nuevo `PublicParking`)** — barridos los TRES enumeradores que la ficha de
  F5b-2 obliga a revisar con cada arm: `capExpandAtPeek` + `isPureBrowsePeek` (`HomeScreen.kt`) y
  `showList` (`HomeBottomSheet.kt`). Más `HomePeekHandle` (`PeekState.SelectedPublicParking`,
  identidad sola [BUG-PEEK-JITTER-001]) y `applyNewSpots` en `HomeStateTransitions` — este último lo
  cazó el compilador con el `when` sellado, que es justo para lo que está sellado.
- **`AppPreferences.showPublicParkingsLayer`** — los 4 sitios: interfaz, DataStore Android,
  NSUserDefaults iOS y los dos fakes (`FakeAppPreferences` de test y `FakeOtherDataSources` de mock).
  NO va en `USER_SCOPED_KEYS`: es preferencia de dispositivo, como el tipo de mapa.
- **`MapMarkerIds`** — familia `public_parking_` disjunta de las seis existentes;
  `MarkerTapTarget.PublicParking`, corpus del test ampliado (12 ids, floor 6) y la matriz de
  exclusión de prefijos con el caso que importa: `public_parking_` vs `parking_ask` contienen la
  misma palabra y un tap enrutado al ask no haría nada.
- **`HomeMapFab`** — el toggle de gasolineras **se mudó** dentro de `MapLayersFab`. Barridos sus dos
  call sites (`HomeMapSection`, `HomeScreen`); `map_cd_fuel_layer` deja de usarse ahí y el menú
  estrena `map_cd_layers` + los dos nombres de capa.
- **`HomeSlices`** — campos nuevos en las cuatro proyecciones (fabs, mapa, peek, sheet) y en ambas
  puntas de cada una; la lista de la capa se sirve vacía cuando la capa está apagada, para que el
  mapa nunca tenga que preguntar por qué.
- **`MockModule`** — `PublicParkingsSource` declarado en la MISMA tanda que el fake, no el día que
  reviente Home. Es el agujero que `MockKoinModuleVerifyTest` existe para cazar
  [FUEL-STATIONS-LIST-001].
- **`PresentationModule`** — un `get()` más en la lista posicional de `HomeViewModel`; ahí lo que
  importa es el NÚMERO, no el orden, porque cada argumento resuelve por el tipo del parámetro.
- **`CachingFuelStationsSource`** — reescrito sobre `LocationKeyedCache`: mismo comportamiento, una
  sola implementación de la clave por ubicación. Sus tests siguen verdes sin tocarse.
- **Detección** — sin tocar: ni estrategias, ni evaluadores, ni geofences, ni workers. La skill
  `det-change` no aplica.

## Follow-ups abiertos

- [`parking-availability-vendor-001.md`](parking-availability-vendor-001.md) — comprar plazas libres
  en tiempo real (TomTom / HERE). Diferido: **no hay acceso de autoservicio**, hay que pasar por
  ventas, así que ni se puede medir su cobertura antes de decidir.
- `PARKINGS-LIVE-OCCUPANCY-001` (F6b) — ocupación real de Madrid y Málaga por open data, con el
  primer backend del proyecto.
