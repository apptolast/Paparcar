# HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001 · Un esqueleto dice «estoy preguntando», y sólo la pregunta puede decirlo

**Estado:** ✅ Done — en master (el hash vive en `MEMORY.md`: este doc viaja DENTRO de su propio
commit, así que por construcción no puede citarlo).

## Problema

Dos capturas del user (25-09 20:52 y 26-09 00:59), las dos en el peek de Home:

1. **Gasolineras, 00:59** — el peek entero es el esqueleto (`PeekLocationSkeleton`: círculo de 26 dp,
   dos barras y una cápsula) mientras **el carril de abajo ya ha contestado**: 6 gasolineras con
   precio, ordenadas, y la pestaña con su cifra. El banner verde de la app dice *«Conexión
   restablecida»* — o sea, el esqueleto sobrevivió al corte de red **y a su reparación**.
2. **Parkings, 20:52** — la cabecera nombra el sitio correctamente, pero la **cápsula del contador**
   late en shimmer mientras la pestaña dice «5 Parkings» y la lista pinta las cinco filas.

El user lo resume en dos frases: *«se queda el shimmer del peek colgado… entiendo cuando no hay
internet y no da respuesta, pero que no se quede eternamente»* y *«aunque haya internet, parkings
sigue tardando un poco mucho»*.

## Doctrina violada

**Un esqueleto es una frase: «te estoy preguntando esto ahora mismo».** Quien puede decirla es la
pregunta en vuelo, nadie más. Los dos defectos son el mismo error dicho en dos superficies: el
shimmer se está dibujando a partir de *«no tengo nada que enseñar»*, que es un estado **sin salida**
cuando la respuesta llegó vacía.

Es además el gemelo exacto de dos reglas que el proyecto ya tiene escritas y aplicadas en otro sitio:

- `[POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001]` — «Overpass CONTESTÓ, con un sitio o con una nada
  medida» es distinto de «nadie contestó». El repositorio de geocodificación ya distingue las dos;
  la cabecera de Home no.
- `[FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]` / `failed` — un carril que no pudo preguntar lo
  DICE, nunca se queda en blanco esperando.

Y contradice a su propia hermana: los peeks de modo-pin (`AddingParkingPeek`, `ReportPeek`,
`AddingZonePeek`) ya resuelven esto bien con `cameraTitleWhileSettling(info, isSettling = isCameraMoving || isCameraGeocoding)`
→ cuando se asienta, `home_address_unknown`. **La cabecera de browse se inventó su propia regla**
(`hasContent`) y es la única de las cuatro que se puede quedar colgada.

## Señales / datos disponibles

- `HomeState.isCameraGeocoding` — **ya existe** y ya se limpia en un `finally`
  (`HomeGeocodingController`, `[BUG-GEOCODE-STUCK-001]`). Nadie lo consulta en `BrowsePeek`.
- `HomeState.cameraAddressAndPlace` — la respuesta. `AddressAndPlaceRepositoryImpl` **siempre emite**
  la fase 1, incluso vacía (`AddressInfo(null, null, null, null)`) cuando el geocoder se cae y no hay
  vecino en caché: eso es exactamente el estado de la captura 1 → `displayLine` nula, `placeInfo`
  nulo → `hasContent = false` → esqueleto perpetuo.
- `parkings.isLoading` / `parkings.parkings` — el carril **pinta a propósito** la respuesta recordada
  del disco mientras vuela la de red (`[PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]`),
  y `laneLoading` tapa la cifra de esas mismas filas. El KDoc de `laneAnsweringHere` ya llama a esto
  un defecto, con estas palabras: *«Que la cuenta del peek hiciera shimmer mientras las filas se
  pintaban asentadas ES este defecto, visto desde la otra punta»* — se arregló para las filas y no
  para la cápsula.
- `ConnectivityObserver` + `HomeViewModel.onlineAgain()` — **ya existen**, y hoy sólo los usa la
  suscripción de plazas. Nada re-pregunta la dirección ni los carriles al volver la red.

## Diseño

Un invariante, tres consumidores que lo incumplían.

> **Un esqueleto sólo lo dibuja una pregunta EN VUELO. Una pregunta contestada sin nombre es una
> respuesta, y se escribe.**

1. **`BrowsePeek`** deja de derivar «cargando» de `hasContent`. Recibe la verdad que ya viaja en la
   porción (`isCameraGeocoding`) y:
   - en vuelo y sin nada que enseñar → esqueleto (igual que hoy, pero acotado por la pregunta);
   - contestada y sin nombre → la cabecera con el fallback compartido (`home_address_unknown`), el
     MISMO resolutor que usan los otros tres peeks. Un sitio nombra un sitio.
2. **La cápsula del contador** (`PapSheetLead.NearbyCounter`) late sólo cuando no hay cifra que
   enseñar. Si las filas de debajo y la pestaña ya dicen «5», esconder ese 5 es contarle al usuario
   dos cosas distintas a 300 dp una de otra.
3. **Al volver la red se vuelve a preguntar.** `onlineAgain()` ya está en el ViewModel: lo que no se
   pudo contestar (dirección de cámara vacía, carril `failed`) se re-pregunta en la transición
   Offline→Online, que es justo el instante de la captura 1. Re-preguntar **no** es re-descargar: un
   carril con respuesta asentada que cubre la cámara no pregunta nada, y un punto ya nombrado
   tampoco.
4. **Sin permiso de ubicación nadie está preguntando** (lo encontró el barrido, misma pantalla):
   `HomeState.isLoading` se encendía al abrir la suscripción de GPS y sólo lo apagaba un fix — que
   sin permiso no llega nunca. El velo de carga del mapa y el esqueleto de filas del carril de plazas
   se quedaban puestos **para siempre**, y encima tapaban la frase que
   `[HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]` había escrito para ese momento («no se ha
   preguntado»), porque la rama de carga se comprueba antes. Con permiso y sin fix la espera SÍ es
   verdad y el esqueleto se queda.

Y una medida, porque la lentitud no se arregla a ojo: las dos líneas de log de los carriles llevan
ahora **cuánto tardó la pregunta** (`… in NNNN ms`, `failed after NNNN ms`). Era el único número que
no existía, y es el que distingue un mirror lento de una caminata agotada de la ranura única de
Overpass ocupada por otro carril.

Fuera de alcance de este ticket, y por medida antes que por corazonada: la latencia real del carril
de parkings cuando NO hay respuesta recordada. La sospecha —la ranura única de `OverpassQuota`
compartida con la fase 2 del geocodificador de cámara, donde el carril que el usuario está mirando
espera detrás de una consulta de POI de fondo— queda escrita, con su mecanismo y su plan de medida,
en `docs/backlog/nearby-the-lane-you-are-looking-at-waits-behind-a-background-poi-001.md`.

## Criterio de éxito

- Sin red y sin caché, la cabecera de browse dice el fallback honesto en vez de latir para siempre.
- Al restablecerse la conexión, la dirección y el carril fallido se vuelven a pedir sin tocar el mapa.
- Con filas recordadas en pantalla, la cápsula enseña su cifra: pestaña, filas y cápsula dicen lo mismo.
- Tests: un testigo por cada frase, y los tres tienen que poder ponerse ROJOS.

## Consumidores auditados

Barrido de **todo** lo que dibuja un esqueleto en la app (`grep PapShimmerBox`), clasificado por la
pregunta «¿de qué se deriva el latido?»:

| dónde | se deriva de | veredicto |
|---|---|---|
| `BrowsePeek.PeekLocationSkeleton` | «no tengo nombre» | 🔴 **arreglado** — el defecto de la captura 2 |
| `PapSheetLead.NearbyCounter(isLoading)` | sólo «preguntando» | 🔴 **arreglado** — escondía la cifra que las filas ya pintaban (captura 1) |
| `HomeState.isLoading` (velo del mapa + filas de plazas) | un fix que sin permiso no llega | 🔴 **arreglado** — mismo invariante, misma pantalla |
| `PapSheetLead.Vehicle(loading = vehicle == null)` (2 ramas de `BrowsePeek`) | «no tengo datos» | 🟢 acotado: `DeleteVehicleUseCase` **prohíbe** borrar un coche con sesión activa (`DeleteBlockedByActiveParking`), así que la sesión siempre tiene su coche en Room |
| `HomeFuelLane` / `HomeParkingsLane` | `isLoading && lista vacía` | 🟢 correcto — es la frase que los otros dos copian ahora |
| `StatsScreen` | `state.isLoading` | 🟢 correcto |
| `HistoryContent` | `resolved == null` (Room) | 🟢 acotado por la emisión de Room |
| `ParkingHistoryDetailScreen` | `FocusedParking.Unresolved` vs `NotFound` | 🟢 correcto — ya distingue «no resuelto» de «no existe», que es esta doctrina escrita en un tipo |
| `SettingsScreen` (avatar) | estado de carga de Coil | 🟢 acotado por Coil |
| `SharedCarGroupSurfaces` | `regenerating` (una acción en vuelo) | 🟢 correcto |

## Estado de la verificación

- `./gradlew testAll` en verde (3.788 en `:shared` + 4 en `:app`).
- Los cuatro testigos **falsificados uno a uno**: revertido cada invariante, cada test se puso ROJO
  y sólo el suyo. Un guard que no puede ponerse rojo no es un guard.
- Galería del Dev Catalog al día en la misma tarea: *«Peek · preguntando (skeleton)»* (ahora enciende
  el geocodificador, porque un estado vacío ya NO produce esqueleto), *«Peek · la respuesta llegó sin
  nombre»* (el estado de la captura) y *«PapSheet · browse expandido (PARKINGS recordados, aún
  preguntando)»* (la cápsula con su cifra sobre filas recordadas).
- ⏳ **Falta verlo en device**: las dos capturas del user se reproducen cortando la red sobre Home.
