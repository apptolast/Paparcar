# NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001 · Parkings y gasolineras: un solo almacén, como las plazas

**Estado:** ✅ Done — mergeado sobre `feature/IOS-F0-001-fase0` (hash en `MEMORY.md`, por construcción del cierre)

## Problema

Reportado por el user (13-09-2026): al desplazar el mapa con el carril de parkings o gasolineras
abierto, los pins desaparecen del mapa pero la lista sigue enseñándolos. Tendrían que comportarse
como las plazas: se carga lo del radio donde miras y mapa y lista enseñan lo mismo.

Análisis del código (master `5f537dde`):

1. **Dos almacenes por sujeto, sumados.** `parkings` + `parkingsLayer` y `fuel` + `fuelLayer`; mapa,
   lista y peek pintan la unión `distinctBy`. Cada almacén tiene su ancla, así que la unión mezcla
   ZONAS: la lista enseña cosas de un sitio que ya no está en pantalla, ordenadas con distancias
   medidas desde anclas distintas.
2. **Petición descartada, no reemplazada.** `maybeRefetchOpenSections` salía si había una petición en
   vuelo (`isLoading`). Overpass tarda hasta 20 s: un paneo durante esa ventana se perdía y los datos
   se quedaban en la zona anterior hasta el siguiente movimiento. Las plazas usan `flatMapLatest`.
3. **>20 km del usuario** se vaciaba la capa pero NO el carril → la unión seguía enseñando tu ciudad
   sobre otra, en mapa y lista.
4. **La capa de parkings ya no tenía disparador de cámara** (solo se pinta con la lente PARKINGS, y
   con esa lente no preguntaba): solo la despertaba cada fix de GPS — peticiones duplicadas, y tras un
   fallo **un reintento por fix**, la regresión que `should_notReaskOnEveryFix` decía cerrada.
5. **Taps de pin** (`HomeScreen`) resolvían contra la tienda del carril: un pin que solo vivía en la
   capa no respondía.
6. **Recuento del peek** leía solo el carril; la lista, la unión.
7. **Markers congelados al arrastrar** (`PaparcarMapView.allMarkers`) pero sus bitmaps
   (`customMarkerContent`) no: una respuesta a mitad de gesto desalineaba ambos.

## Doctrina violada

*Una pantalla no puede sostener dos respuestas a lo que el usuario lee como una sola pregunta*
[A-SECTION-ANSWERS-ABOUT-WHERE-YOU-LOOK-001]. La unión de dos tiendas con dos anclas es exactamente
eso, y la "regla" ⛔ de no fusionar tiendas existía por un bug (`fetchedFor`) que un solo almacén con
`fetchedFor` también cierra.

## Señales / datos disponibles

- `ViewportRefetch` (medio radio + `isWorthAsking`) ya es el molde puro.
- `HomeFuelSlice.fetchedFor` ya distingue respuesta con / sin combustible.
- `HomeState.pricedFuelType` + `FuelStation.prices` ya permiten reponer precio al proyectar.
- `userGpsPoint` ya está en el estado para medir distancias como las filas de plazas.

## Diseño

Decisiones del user (13-09): un almacén por sujeto; distancia desde el usuario; gasolineras con
precio primero (parkings sin prioridad, solo distancia); >20 km vacía mapa y lista y lo DICE.

- **Un solo almacén** por sujeto (`HomeFuelSlice`, `HomeParkingsSlice`). Se retiran
  `HomeFuelLayerSlice`, `HomeParkingsLayerSlice` y la unión. Mapa, lista, peek, stepper, selección y
  taps leen UNA proyección: `HomeState.fuelStationsShown` / `publicParkingsShown`.
- **Una sola vía de petición por sujeto** en el VM, *latest-wins*: la petición nueva cancela la que
  esté en vuelo (un `Job` por sujeto) y el resultado de un job cancelado no escribe estado.
- **Disparadores**: entrar en el carril · cámara asentada (600 ms, Browse, carril abierto, medio
  radio) · primer centro disponible (fix de GPS cuando el carril se abrió sin centro). ⛔ Un fix NO
  reintenta un fallo de red: eso lo hace el siguiente movimiento de cámara o volver a entrar.
- **Fallo**: el almacén se vacía, `failed = true`, ancla a null → cualquier movimiento reintenta,
  también en el mismo sitio. Mapa y lista coinciden (ninguno pinta nada; la lista dice por qué).
- **Carga**: se conservan los resultados anteriores mientras llega la respuesta (como las plazas);
  el esqueleto del carril solo aparece si no hay nada que enseñar.
- **Coche sin combustible**: se pregunta sin combustible, al mismo almacén, `fetchedFor = null`;
  el carril sigue diciendo `missingFuelType` y el mapa pinta cápsulas sin cifra (sin cambio visible).
  Al tener combustible, `fetchedFor` distinto ⇒ nueva petición.
- **Proyección** (pura, `presentation/home/model/NearbyOrdering.kt`): distancia recalculada desde el
  GPS del usuario (si no hay fix, la del centro de la petición); gasolineras con precio primero y
  luego por distancia; parkings por distancia.
- **>20 km** (`ViewportRefetch.isWorthAsking` falso por distancia, no por (0,0)): cancela, vacía,
  `tooFar = true`, suelta la selección del sujeto; carril con frase propia
  `home_fuel_too_far` / `home_parkings_too_far` (9 locales).
- **Mapa**: se congela también `customMarkerContent` mientras el dedo arrastra, en pareja con los
  markers.

## Criterio de éxito

- Mapa y lista del carril abierto muestran SIEMPRE el mismo conjunto (test de slices).
- Un paneo durante una petición en vuelo acaba preguntando por el sitio nuevo (test VM).
- Más allá de 20 km: mapa y lista vacíos y `tooFar` (test VM).
- Tras un fallo, los fixes de GPS no reintentan; un movimiento de cámara sí (test VM).
- Gasolineras con precio primero; distancias desde el usuario (test de la proyección).
- `:shared:testDebugUnitTest` + Konsist, `compileMockDebugKotlin` + `compileProdDebugKotlin`.
- Device: abrir parkings, arrastrar → los pins y la lista cambian juntos.

## Consumidores auditados

- **`HomeFuelLayerSlice` / `HomeParkingsLayerSlice`** — retirados; `HomeState.fuelLayer` /
  `parkingsLayer` también. `grep` sin restos en `shared/` ni `app/src/mock`.
- **`HomeState.fuelStationsShown` / `publicParkingsShown`** — la única proyección
  (`NearbyOrdering`): la leen `toMapSlice`, `toPeekSlice` (y su `fuelStationStep`),
  `toBrowseListSlice`, `selectedFuelStation`, `selectedPublicParking`.
- **`HomeScreen` taps de marker** — resuelven contra `mapSlice.fuelStations` / `publicParkings`
  (antes: solo la tienda del carril → pin muerto). Cerrado.
- **`HomeViewModel`** — `askFuelForLane`, `loadFuelStations`, `maybeFetchFuelViewport`,
  `scheduleFuelViewportRefetch`, `askParkingsForLane`, `loadPublicParkings`,
  `maybeFetchParkingsViewport`, `scheduleParkingsViewportRefetch`, `scheduleSectionsRefetch`,
  `maybeRefetchOpenSections` → sustituidos por `refreshNearby` (ENTER / CAMERA / FIX) +
  `fetchFuelStations` / `fetchPublicParkings` (latest-wins) + `emptyNearbyAsTooFar`.
- **Fix de GPS** — ya no llama a las capas; `NearbyTrigger.FIX` solo aporta centro pendiente o
  re-evalúa el límite de 20 km. Nunca reintenta un fallo.
- **`viewportCentreOrGps`** — ignora una cámara en (0,0) y cae al fix.
- **`HomeFuelLane` / `HomeParkingsLane`** — frase `tooFar` primero; esqueleto solo sin filas.
- **`HomePeekSlice.laneCount`** — sigue exigiendo ancla: con `tooFar` o fallo (ancla null) no hay
  recuento. `HomeNearbyLensRow` solo pinta recuentos > 0. Exento.
- **`PaparcarMapView`** — `customMarkerContent` se mantiene como superconjunto mientras el dedo
  arrastra, en pareja con `allMarkers`.
- **Peeks (`FuelStationPeek`, `PublicParkingPeek`)** — reciben el elemento de la proyección, así que
  su distancia pasa a ser desde el usuario. Cubierto.
- **Dev Catalog** — `StateGalleryScreen`: variantes «Demasiado lejos de ti» y «Cargando otra zona
  (conserva filas)» en ambos carriles.
- **Strings** — `home_fuel_too_far`, `home_parkings_too_far` en los 9 locales, con la cifra de
  `ViewportRefetch.maxFromUserKm` (no escrita a mano).
- **Sin cambio de comportamiento visible (decidido):** coche sin combustible — el carril sigue
  diciendo `missingFuelType` y el mapa pinta cápsulas sin cifra.

### Fallos preexistentes en master arreglados de paso

Los tres venían del mismo commit (`385a6782`, límite 50 → 20 km + cabecera nueva) y dejaban la
suite en rojo:
- `should_askAroundTheSettledCamera…` y `should_refetchTheOpenLane…`: destinos a ~29 km y ~21 km,
  ya fuera del límite → puntos a ~11 km / ~10 km.
- `ViewportRefetchTest.should_followTheCamera_onlyWithinTheUsersOwnRegion`: Alcalá (~29 km) ya no
  está dentro → `false`, y Getafe (~13 km) como caso dentro.
- `LocaleParityGuardrailTest`: `home_nearby_section_header` faltaba en 7 locales → añadida.

### Tests

- Nuevos (VM): `should_answerForTheLatestPlace_when_theCameraSettlesWhileAnAskIsInFlight`,
  `should_emptyTheMapAndTheList_andSayTooFar_when_theCameraLeavesTheFollowRange`,
  `should_askOnTheFirstFix_when_theLaneWasEnteredWithoutACentre`,
  `should_listWhatTheMapPaints_forEveryStepOfAPan`,
  `should_retryOnTheNextCameraMove_butNeverOnAFix_when_theAskFailed` (sustituye a
  `should_putTheAnchorBack_when_theViewportAskFails`, que exigía reintentar en un fix).
- Nuevo: `NearbyOrderingTest` (precio primero, distancia desde el usuario, sin fix).
- Ajustados a una tienda: `HomeSlicesTest` (5) y `HomeViewModelTest` (fuel-less, too far, prune).
