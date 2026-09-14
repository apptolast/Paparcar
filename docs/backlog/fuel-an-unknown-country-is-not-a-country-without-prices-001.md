# FUEL-AN-UNKNOWN-COUNTRY-IS-NOT-A-COUNTRY-WITHOUT-PRICES-001 · un geocode que no pudo contestar se presenta como una dirección válida, y España acaba sin precios diciendo que no los publica

**Estado:** ✅ Done · en master, rebasado sobre `f2800a11` · ⏳ sin ver en campo
· **(1) ✅ · (2) ✅ · (3) ✅ pero con OTRA palanca (ver abajo) · (4) ⛔ descartada: código muerto,
demostrado — su versión viva es `FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001` · (5) ✅**

## Problema

Campo, ~12-09-2026, España. Alex instala la app **desde Play Store** y entra al carril de
gasolineras: salen gasolineras **sin precio**. Panea por el mapa y siguen sin precio. Cambia de
pantalla, vuelve a Home, y entonces sí: gasolineras **con precio**.

(La segunda mitad de lo que reportó —el sheet cerrado que no abría— está al final, **sin resolver**.)

### La cadena, con el código en la mano

`AndroidGeocoderDataSourceImpl.kt:62-72`:

```kotlin
override suspend fun getAddress(lat: Double, lon: Double): Result<AddressInfo> =
    runCatching {
        …
        address?.toAddressInfo()
            ?: AddressInfo(street = null, city = null, region = null, country = null)
    }
```

y en la vía de API 33+ (`:84-86`) el callback de error resuelve `null`:

```kotlin
override fun onError(errorMessage: String?) { cont.resume(null) }
```

O sea: **un ERROR del geocoder sale como `Result.success` con una dirección entera a nulos**,
`countryCode` incluido. Y el `Geocoder` de plataforma se apoya en un componente de red de Play
Services que falla precisamente en los primeros segundos tras una instalación limpia — que es la
situación exacta del reporte.

Lo que viene detrás no tiene forma de enterarse:

| Paso | Fichero | Qué pasa |
|---|---|---|
| 1 | `GetNearbyFuelStationsUseCase.kt:81-86` | `getOrNull()` NO es null (es la dirección vacía) → `countryCode = "".orEmpty()` |
| 2 | `FuelStationsRegistry.kt:30-31` | `national.firstOrNull { it.covers("") }` → null → **`return fallback.stationsNear(query)`** |
| 3 | — | Esa rama es la de «este país no tiene feed nacional», no la de recuperación: sale **sin `didFallBack`** |
| 4 | `HomeFuelLane.kt:99-107` | `!hasPrices && !didFallBack` → `home_fuel_no_prices` = «**Prices are not published in this country**» |

España publica precios de carburante para esa misma calle, sellados esa misma tarde, y la app le
dijo al usuario que España no los publica. Es **literalmente la foto que
`HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001` dejó escrita en el propio comentario
del `when`** — la frase existe desde entonces (`home_fuel_prices_unavailable`), pero por esta vía
no se alcanza: aquel ticket cubrió «el feed nacional contestó mal», no «no sé de qué país estamos
hablando».

### Por qué se quedó pegado (y por qué cambiar de pantalla lo arregló)

Tres cierres, en cadena:

- `CachingFuelStationsSource.kt:34-39` — **15 min** en memoria, con la clave incluyendo
  `countryCode` (`""`).
- `DiskBackedFuelStationsSource.kt:30-33` — además lo escribe en Room como respuesta buena.
- `HomeViewModel.refreshNearby` (`:795-801`) — `ViewportRefetch.shouldRefetch` solo re-pregunta al
  pasar `radius * REFETCH_AT_FRACTION`, y el radio de gasolineras es
  `FuelStationsQuery.DEFAULT_RADIUS_METERS = 5_000`. **Panear menos de 2,5 km no vuelve a preguntar
  nunca.** El `anchor` se estampa en el INTENTO (`:906-907`), así que la respuesta mala queda
  sellada para toda la zona.

Y hay un agravante de calendario: `warmUpHiddenLanes` (`:702-720`,
`HOME-THE-THREE-LANES-WARM-UP-ONCE-ON-ENTRY-001`, en master desde `f79930d3`) dispara la petición de
gasolineras en el **primer asentamiento de cámara de un arranque en frío** — la ventana en la que el
geocoder de plataforma es menos probable que conteste. Como estampa el anchor, entrar luego al
carril ya no re-pregunta: el usuario ve la respuesta del calentamiento, no una suya.

### El mismo fallo muerde a un segundo consumidor, que ya tenía la defensa escrita

`AddressAndPlaceRepositoryImpl.kt:74-92` **ya asume el contrato correcto**:

```kotlin
val fetchedAddress: AddressInfo? = withTimeoutOrNull(PHASE1_TIMEOUT_MS) {
    geocoder.getAddress(lat, lon).getOrNull()
}
// Phase 1 failed (offline / GMS mute): borrow the nearest cached street …
val nearest = if (fetchedAddress == null) local.getNearest(lat, lon) else null
val approximate = nearest != null
```

Ese comentario nombra el caso —*GMS mute*— y construye un mecanismo entero para él
(`GEO-CACHE-ANSWERS-NEARBY-001`: prestar la calle cacheada más cercana, marcada `approximate`). Con
la implementación de hoy **ese mecanismo no se ejecuta jamás en un fallo del geocoder**:
`fetchedAddress` nunca es null, así que nunca se presta nada, `approximate` nunca es true, y la
dirección vacía se emite como buena y acaba sellando la celda.

Dos consumidores, dos contratos inventados por separado, y `GeocoderDataSource.kt` **sin una sola
línea de KDoc** que diga cuál es el de verdad.

## Doctrina violada

- **Un fallo de red no es un veredicto** — la misma de `POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001`
  (`b7109239`), en el consumidor de al lado. Allí un `failure` se convertía en `success` una capa
  más arriba; aquí el `failure` **no llega a existir**: se fabrica en la propia fuente.
- **Fallo asimétrico: mejor un hueco que un dato falso.** «Este país no publica precios» es un
  veredicto sobre un país entero emitido por un geocoder que no contestó.
- **Sistemas, no parches.** El mismo error está escrito **dos veces**, Android e iOS, con la misma
  forma (`resume(null)` en el error + `?: AddressInfo(nulls)`). Exactamente el patrón que el ticket
  del POI documentó para el `var saved`. Se arregla el invariante, no la vía que mordió.
- **Un `AddressInfo` viaja entero** (`FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001`): ese
  ticket cerró que los campos no se cayeran por el camino. Este cierra el escalón de antes —
  que el objeto no debería existir cuando no hubo respuesta.

## Señales / datos disponibles

- `HomeViewModel` loguea `"fuel: N stations around lat,lon"` — no dice el país ni la fuente.
- `FuelStationsResult.source` distingue `es-minetur` de OSM, pero **no se loguea**.
- Ningún log dice que el geocode falló, porque para el código no falló.
- Reproducible en device sin esperar a un GMS frío: **modo avión con el mapa ya cacheado**, o
  revocar el permiso de ubicación (el geocode es del centro de cámara, no del fix, así que la vía
  sigue viva).

## Diseño

Tres invariantes, tres sitios, y ninguno es un guard "por si acaso".

### (1) Una búsqueda que no pudo preguntar no es una dirección — **el fallo principal**

`GeocoderDataSource.getAddress` pasa a tener contrato escrito: **`failure` cuando la búsqueda no
pudo contestar; `success` solo cuando contestó**, aunque conteste poco.

⛔ No basta con cambiar los dos `resume(null)`: hay que distinguir **error** de **sin resultados**.
Un punto en medio del mar contesta bien y no tiene dirección; eso es un `success` legítimo con
campos a null. Un `onError` / `NSError` no.

Y para que el fallo no se pueda volver a escribir dos veces, la decisión vive **una sola vez**, en
commonMain, y las dos implementaciones la llaman en vez de copiarla — misma forma con la que el
ticket del POI unificó `enrichParkingSession`:

```kotlin
// domain/geocoder/GeocodeAnswer.kt (commonMain, puro y testeable)
/** Una dirección sin un solo campo no es una respuesta: es la forma que toma un fallo mudo. */
fun geocodeAnswer(address: AddressInfo?): Result<AddressInfo>
```

Así queda cubierto también el caso en que la plataforma devuelva un `Address` vacío en vez de un
error — que es la misma mentira con otra ropa.

### (2) Un país desconocido no es un país sin feed nacional

`FuelStationsRegistry.kt:30-31`: cuando **ningún** national cubre el país, hoy hay dos casos
distintos colapsados en uno:

- `countryCode = "DK"` → Dinamarca no tiene feed nuestro. El fallback ES la respuesta. `didFallBack`
  **false**, y la frase «este país no publica precios» es **verdad**.
- `countryCode = ""` → no sabemos dónde estamos. El fallback es un **sustituto**, y puede haber un
  feed nacional perfectamente vivo detrás. `didFallBack` tiene que ir a **true**, que es lo que hace
  que el carril diga «los precios oficiales no han cargado, vuelve a intentarlo».

La frase ya existe en los 9 locales (`home_fuel_prices_unavailable`) — **no hay strings nuevos**.

El gemelo `PublicParkingsRegistry` no tiene este problema (OSM cubre el mundo y no hay precio que
prometer), pero se audita igual.

### (3) Una respuesta que no pudo preguntar por el país no sella la zona

Lo que convirtió un fallo transitorio en veinte minutos de gasolineras sin precio. El `anchor` se
estampa en el intento y solo se limpia en `onFailure`, así que una respuesta-sustituto sellaba la
zona entera y panear menos de 2,5 km no volvía a preguntar jamás.

#### ⛔ Dos palancas descartadas antes de dar con la buena

**Soltar el `anchor`** era el diseño escrito arriba, y no vale: `HomeFuelSlice.anchorLat` es también
lo que decide `laneCount` (`fuel.stations.size.takeIf { fuel.anchorLat != null }`), o sea que un
anchor nulo deja la cápsula del peek diciendo «nadie ha preguntado» mientras hay filas en pantalla.

**Un contador de reintentos** («un sustituto se reintenta UNA vez») se implementó entero y lo tumbó
su propio testigo: el calentamiento de entrada pregunta (sustituto), abrir el carril gasta el
reintento, y para cuando el usuario panea ya no queda ninguno — justo la secuencia del reporte. Un
contador que se consume antes de que el usuario llegue no protege a nadie.

#### ✅ La palanca buena: se reintenta **en el ancla**, no donde derivó la cámara

La cadena de DI es `CachingFuelStationsSource(DiskBacked(Registry))`, con la caché de 15 min **por
fuera** y su clave — `locationCacheKey(lat, lon, radio, countryCode, regionName)` — incluyendo el
país. De ahí sale la regla, sin contadores ni cooldowns que calibrar:

- Mientras la respuesta en pantalla sea un sustituto, cada asentamiento de cámara vuelve a
  preguntar **en las coordenadas del ancla**, no en las de la cámara.
- Como la pregunta es idéntica, la clave de caché es idéntica: **cero red** mientras la situación no
  cambie.
- En cuanto el geocoder despierta, el `countryCode` entra en la clave, la caché falla, y el
  Ministerio contesta **con precios**. Se paga exactamente cuando hay algo nuevo que aprender.

El anti-tormenta no hubo que escribirlo: ya estaba en la suite. Quitando la condición `didFallBack`
de esa rama caen **11 tests**, toda la familia de «un carril que ya contestó no vuelve a preguntar».

⚪ **Lo que NO se toca:** la caché de 15 min. La clave incluye el `countryCode`, así que la respuesta
mala solo se sirve a otro geocode igual de fallido; en cuanto el geocoder contesta, la clave es otra
y el camino es el bueno. Invalidarla sería trabajo sin consecuencia observable.

### ⛔ (4) «El país se calcula, no se pregunta» — descartada, y conviene saber por qué

La idea era quitarle al geocoder la decisión de enrutado: una tabla de cajas envolventes para los
países con feed (ES, FR) resolvería el país desde lat/lon, y un punto en España iría **siempre** a
Minetur. Se pidió explícitamente. **No se implementa porque no puede cambiar ningún resultado**, y
la demostración es corta:

El único momento en que la caja se consultaría es con el geocode mudo. Pero un geocode mudo no
devuelve **ni ciudad ni región** — es la misma respuesta vacía —, así que `MineturStationsSource` se
queda sin las dos claves con las que sabe preguntar y lanza sin tocar la red. Los dos caminos acaban
en el mismo sitio:

| | sin (4) | con (4) |
|---|---|---|
| `countryCode` | `""` | `"ES"` |
| Ruta | ningún national cubre → fallback | Minetur → lanza (sin ciudad ni provincia) → `recoverCatching` |
| Resultado | fallback **marcado** por (2) | fallback **marcado** por el `recoverCatching` |

Misma respuesta, misma frase, más código. Y encima la caja de España se come Portugal, así que
añadiría un caso nuevo en el que nos equivocamos de ministerio.

**La versión que SÍ estaría viva es otra**, y es bastante más cara: una tabla de las **52 provincias
con sus cajas envolventes**, que dejaría a España respondiendo con el geocoder completamente mudo —
hoy imposible, porque el ministerio indexa por id de provincia/municipio y sus dos listados no
traen coordenadas. Eso es vendorizar un dataset geográfico y se decide aparte: queda como
follow-up, no colado aquí.

Medido de paso (Photon `/reverse`, 14-09-2026), por si se retoma: `countrycode` vuelve **siempre**,
pero `city` **no** (en Sevilla viene null) y `county` es unas veces la provincia («Sevilla») y otras
la **comarca** («Bay of Cádiz», «Gironès»). O sea la misma trampa de «Andalucía» con otra ropa:
Photon no sustituye al geocoder para España.

### (5) El catálogo de provincias que se descargaba para nada

Encontrado al auditar la vía del geocode mudo. `MineturStationsSource`:

```kotlin
?: SpanishProvinces.idFor(query.regionName, provinces())   // ← provinces() es un ARGUMENTO
```

Se evalúa **siempre** que no haya salido un municipio, aunque `regionName` sea null y `idFor` vaya a
devolver null sin mirar nada: 52 provincias descargadas para tirarlas. Es el MISMO error que el
comentario de tres líneas más arriba documenta para el catálogo de municipios, en el mismo fichero,
sin cazar. Y muerde justo en la vía que menos se lo puede permitir — la del geocode mudo.

## Verificación

Sobre master `64b546d6`, `:shared:testDebugUnitTest --rerun-tasks` → **2.887 tests, 0 fallos**
(`--rerun-tasks` para que los guardarraíles de Konsist se ejecuten de verdad).
`:app:compileProdDebugKotlin` y `:app:compileMockDebugKotlin` verdes con `--rerun-tasks`.
Y **`:shared:compileKotlinIosSimulatorArm64` verde**: Kotlin/Native cruza desde Windows, así que el
gemelo de iOS está verificado por el compilador, no solo leído. Sin strings nuevos (la frase
`home_fuel_prices_unavailable` ya existía en los 9 locales) y sin pantalla ni estado nuevos → no hay
locales ni Dev Catalog que tocar.

**Testigos falsificados uno a uno** — cada uno cae al revertir SU cambio, y solo ese:

| Test | Se rompió al… | Resultado |
|---|---|---|
| `GeocodeAnswerTest.should_fail_whenTheAddressNamesNothing` | devolver la dirección vacía como éxito | **FAILED**, y solo ese |
| `FuelStationsRegistryTest.should_markTheFallbackAsASubstitute_whenTheCountryCouldNotBeNamed` | quitar la marca del país en blanco | **FAILED**, y solo ese |
| `MineturStationsSourceTest.should_notFetchTheProvinceCatalogue_when_thereIsNoRegionToLookUp` | volver a pasar `provinces()` como argumento | **FAILED**, y solo ese |
| `HomeViewModelTest.should_askAgainAtItsAnchor_when_theAnswerOnShowIsASubstitute` | `provisional = false` | **FAILED** (con su pareja) |
| `HomeViewModelTest.should_stopReAsking_when_theSubstituteBecomesARealAnswer` | `provisional` sin la condición `didFallBack` | **FAILED**, con otros **10** |

Las dos últimas van adosadas a propósito, como el par del `ParkingEnrichmentTest`: son el mismo
camino de código y la diferencia entre ellas ES la regla, así que borrar la regla en cualquiera de
los dos sentidos tumba una. Y el segundo falsificado es el dato que hacía falta para no inventarse
un anti-tormenta: **quitar la condición rompe 11 tests**, o sea que el guardarraíl de «un carril que
ya contestó no vuelve a preguntar» ya estaba escrito y sigue vigilando.

## Criterio de éxito

- **Unitario**: geocoder fake que devuelve `failure` → `GetNearbyFuelStationsUseCase` produce un
  resultado con `didFallBack = true`. Falsificable: revertir (2) → rojo.
- **Unitario**: `geocodeAnswer(null)` y `geocodeAnswer(AddressInfo(todo null))` → `failure`;
  `geocodeAnswer(AddressInfo(city = "Cádiz"))` → `success`.
- **Unitario**: `FuelStationsRegistry` con `countryCode = ""` → `didFallBack = true`; con
  `countryCode = "DK"` → `didFallBack = false`. Son el mismo camino de código y **la diferencia ES
  el ticket**, así que van adosados, como los dos del `ParkingEnrichmentTest`.
- **Unitario**: `AddressAndPlaceRepositoryImpl` con el geocoder en `failure` presta la calle vecina
  y marca `approximate = true` — hoy verde por accidente con el fake, y por eso el fake pasa a ser
  fiel al contrato nuevo.
- **En campo**: instalación limpia + primer arranque en España → o salen los precios, o sale
  «los precios oficiales no han cargado», nunca «este país no publica precios». Y panear sin
  moverse de sitio vuelve a intentarlo.

## Consumidores auditados

| Sitio | Qué asume | Estado |
|---|---|---|
| `AndroidGeocoderDataSourceImpl.kt:62-72,84-86` | un error es una dirección vacía | ✅ **mordió aquí** — `onError` lanza y ambos caminos pasan por `geocodeAnswer` |
| `IosGeocoderDataSourceImpl.kt:32-35,47-50` | idéntico (`error != null → resume(null)` + `?: AddressInfo(null,null,null,null)`) | ✅ **mismo fallo, misma tarea**, y compilado |
| `GetNearbyFuelStationsUseCase.kt:81-86` | `getOrNull() == null` ⇒ país desconocido ⇒ fallback mudo | ✅ intacto: ahora recibe el `failure` de verdad y el registry marca |
| `FuelStationsRegistry.kt:30-31` | «nadie cubre este país» = «este país no tiene feed» | ✅ (2) — los dos casos separados |
| `MineturStationsSource.kt:70-74` | `provinces()` se evalúa aunque no haya región | ✅ (5) — descarga eliminada |
| `HomeViewModel.refreshNearby` | un sustituto sella la zona como cualquier respuesta | ✅ (3) — re-pregunta en el ancla |
| `AddressAndPlaceRepositoryImpl.kt:74-92` | `getOrNull() == null` ⇒ *GMS mute* ⇒ prestar calle vecina | ✅ **ya escribía el contrato correcto** — se reactiva solo con (1), sin tocarlo |
| `GetNearbyPublicParkingsUseCase.kt:63-70` | mismo `.orEmpty()` | ⚪ sin daño: OSM cubre el mundo y no promete precio. Se audita, no se cambia |
| `FranceFuelStationsSource.covers` / `MineturStationsSource.covers` | `countryCode` en cualquier caja | ⚪ intactos: `""` no casa con ninguno, que es correcto |
| `FuelStationsQuery.countryCode` (KDoc) | *"Empty when the lookup failed — which routes straight to the fallback"* | 🟡 el KDoc ya describía el síntoma como si fuera el diseño. Se reescribe con (2) |
| `SearchAddressUseCase` | usa `searchByName`, no `getAddress` | ⚪ exento |
| `HomeGeocodingController` | consume `AddressAndPlace` | ⚪ exento — hereda (1) por el repositorio |

## Segunda mitad del reporte: **sin resolver** — el sheet cerrado que no abría

Del mismo viaje: *«al moverse por el mapa la modal estaba cerrada bloqueada y no se podía abrir; he
tenido que cambiar de pantalla y volver»*. **No lo he podido reproducir desde el código y no va en
este ticket.** Lo que sí está acotado, para quien lo retome:

- La geometría que produce EXACTAMENTE ese síntoma es `SheetPositioning.capsAtPeek()`
  (`fullSnapOffsetPx >= peekOffsetPx`): ahí `SheetMotion.toggle` no puede abrir y, si hay
  `minimized` por debajo, el tap **encoge** el sheet en vez de abrirlo, y el arrastre rebota.
- En Browse puro eso solo lo produce `capExpandAtPeek` (`HomeScreen.kt:509-513`), que se calcula con
  el **id** de la selección, mientras que `showList` (`HomeBottomSheet.kt:182-186`) y
  `HomePeekHandle` se calculan con la selección **resuelta** contra la lista. Un id que sobreviva a
  su estación deja el sheet capado enseñando una cabecera Browse normal que no abre.
- Pero el ViewModel poda esa selección en el `onSuccess` **y** en el `onFailure` de cada fetch
  (`HomeViewModel.kt:931-934`, `:957`), y `NearbyOrdering` no recorta la lista, así que no hay
  camino conocido que deje el id huérfano. **No se afirma como causa.**
- Encaja con «cambié de pantalla y volvió» que sea estado de Compose (el `Animatable`, la
  geometría), que se recrea al volver a Home.
- Para atraparlo hace falta device: carril de gasolineras con red mala, tocar una estación y panear
  hasta que llegue una respuesta nueva, con un log de `capsAtPeek()` + `selectedFuelStationId` + la
  selección resuelta.

Si al instrumentar aparece, ticket propio: es otra capa y otro invariante (*una geometría que capa
el sheet y una que le quita la lista tienen que leer la MISMA pregunta*).

## Fuera de alcance (follow-ups)

0. **Que España responda con el geocoder mudo** — la versión viva de la parte (4). Ya tiene ticket
   propio: **`FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001`**, y por un camino mejor que la tabla de cajas
   que se proponía aquí — los ids del ministerio son los códigos INE, o sea **los dos primeros
   dígitos del código postal** (verificado en vivo contra `/Listados/Provincias/`). Allí está también
   su plan B, con la tabla GENERADA a partir de las coordenadas del propio feed.
1. **El calentamiento pregunta en el segundo más frío del proceso.** `warmUpHiddenLanes` dispara la
   petición de gasolineras en el primer asentamiento de cámara, que es cuando el geocoder de
   plataforma es menos fiable. Con (1)+(3) deja de ser dañino —la respuesta se marca y se vuelve a
   preguntar— pero sigue siendo una petición tirada a la basura. Merece medirse aparte.
2. **Nada loguea qué fuente contestó.** `FuelStationsResult.source` existe y no se imprime en
   ningún sitio; un diagnóstico de precios hoy no puede decir si contestó el Ministerio o OSM.
