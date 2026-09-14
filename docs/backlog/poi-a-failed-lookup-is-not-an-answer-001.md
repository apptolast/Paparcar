# POI-A-FAILED-LOOKUP-IS-NOT-AN-ANSWER-001 · un POI que no se pudo preguntar no es un "aquí no hay nada"

**Estado:** ✅ Done · rebasado sobre master (`f79930d3`) · ⏳ sin ver en campo
· **(1) ✅ · (2) ↪️ mayormente resuelto por `OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001`,
queda `TIMEOUT_S` · (3) ✅**

## Problema

14-09-2026, El Puerto de Santa María. El padre del user aparca en el supermercado de Vistahermosa
(entre C. Autillo y Avda. de Fuentebravía) y el peek titula **«Diseminado 2.4 50»** — la dirección
del geocoder — en vez del nombre del sitio.

Hay DOS cosas distintas ahí dentro, y solo una es nuestra:

**(i) Ni con todo funcionando habría puesto «Carrefour».** Lanzada la query EXACTA de la app
(`around:80`, `[timeout:8]`) sobre ese punto, OSM devuelve **un único candidato**:

```
way 47486214   name="Supersol"   shop=supermarket   building=yes
bounds  lat 36.6019123–36.6025325   lon -6.2600802–-6.2595974
```

Carrefour compró Supersol en 2021 y rebrandeó la tienda; OSM no se ha enterado. Distancia del pin
al **borde** del polígono ≈ 14 m, muy dentro de `NAMING_RADIUS_METERS = 40`, así que la política
habría nombrado «Supersol». Tampoco hay `amenity=parking` mapeado en ese aparcamiento: no había
segundo candidato. **Eso se arregla editando OSM, no aquí** — y queda fuera del alcance de este
ticket, anotado al final.

**(ii) Pero es que ni preguntó, o preguntó y perdió la respuesta.** Medido contra
`overpass-api.de` con esa misma query, el mismo día:

```
attempt 1 -> 504  ttfb=10.9 s
attempt 2 -> 504  ttfb= 9.1 s
attempt 3 -> 504  ttfb= 7.9 s
attempt 4 -> 200  ttfb= 2.1 s   → {"name": "Supersol"}
```

y `overpass.kumi.systems` y `overpass.private.coffee` no contestaron **nada** en 60 s y 45 s. Con
los tres espejos así, el POI del aparcamiento se pierde — y por el fallo (1) de abajo, **se pierde
para siempre**: nada lo vuelve a pedir nunca.

## Doctrina violada

- **Un fallo de red no es un veredicto.** `OverpassPlacesDataSource` ya lo dice en su propio KDoc
  —*"a network failure is a `failure`, never `success(null)`"*— y lo cumple. Pero dos capas más
  arriba ese `failure` se convierte en `Result.success()` del worker, que es la misma mentira
  escrita en otro sitio: la sesión queda sellada como "se miró y no había nada".
- **Fallo asimétrico: mejor un hueco que un dato falso.** Aquí ni siquiera hay hueco — hay un dato
  ausente presentado como definitivo, sin reintento.
- **Sistemas, no parches.** El mismo error está escrito DOS veces, en Android y en iOS, con la misma
  forma (`var saved = false` que la fase 1 pone a true). Se arregla el invariante, no la vía que
  mordió.

## Señales / datos disponibles

- `AddressAndPlaceRepo` loguea `"Phase 2 result — placeInfo=… answered=…"`: `answered=false` es
  fallo de red, `answered=true, placeInfo=null` es la política diciendo "nada a <40 m".
- `OverpassPlaces` loguea `"N candidates near (lat, lon) → picked"`.
- `OverpassClient` loguea qué espejo pasó turno, por qué y con cuánta paciencia.
- En Room: `parking_sessions.placeInfoName IS NULL AND addressStreet IS NOT NULL` es exactamente la
  población afectada (dirección sí, POI no).

## Diseño

### (1) `addressSaved` no puede hablar por el POI — **el fallo principal**

`EnrichParkingSessionWorker.kt:56-83`:

```kotlin
var addressSaved = false
getAddressAndPlace(lat, lon).collect { info -> … .onSuccess { addressSaved = true } }
return when {
    addressSaved && routeDone -> Result.success()      // ← con placeInfo == null incluido
    runAttemptCount < MAX_RETRIES -> Result.retry()
```

La fase 1 (dirección, `placeInfo = null`) ya pone el flag a true. La fase 2 solo emite **si hay
POI**, así que un Overpass caído es indistinguible de un "aquí no hay nada": mismo flujo, mismas
emisiones, mismo `success`. Y `success` significa que WorkManager cierra el trabajo: la única
reentrada que existe en toda la app es arrastrar el pin a mano
(`UpdateParkingLocationUseCase.kt:79`).

**Invariante:** *un enriquecimiento solo está COMPLETO cuando las dos preguntas tienen respuesta;
"no pude preguntar" es un reintento, "pregunté y no hay nada" es una respuesta.*

Para distinguirlas hace falta que el repositorio diga si Overpass CONTESTÓ, cosa que hoy se pierde:
`AddressAndPlaceRepositoryImpl.kt:90` solo re-emite `if (placeInfo != null)`. Las dos formas
posibles:

- **A.** `AddressAndPlace` gana un `poiChecked: Boolean` (la fase 2 emite SIEMPRE al terminar, con
  el flag puesto). El worker exige `addressSaved && poiAnswered`.
- **B.** El worker no toca el repositorio y decide con lo que ya tiene: tras el `collect`, relee la
  sesión y comprueba `placeInfo == null` → reintentar. No distingue "no hay nada" de "no contestó",
  así que reintentaría 3 veces en cada calle sin POI. Descartada por coste.

→ **Va A.** El campo ya existe conceptualmente (es el `poiChecked` de la caché,
`RoomGeocoderCacheDataSource.kt:23`); lo que falta es que cruce la frontera hacia arriba en vez de
quedarse dentro del repositorio.

**Barrido:** el gemelo iOS `SyncOutboxDrainer.kt:114-126` tiene el fallo IDÉNTICO (`var saved`
puesto por la fase 1 → la op sale del outbox sin POI). Se arregla en la misma tarea.

#### ✅ Hecho — y el bucle se unificó, que era la causa de que hubiera dos copias del bug

- `AddressAndPlace` gana **`poiChecked`**. La fase 2 emite ahora **al contestar** (`if (poiAnswered)`),
  no solo al encontrar algo: el "no hay nada" dejaba el flujo en el mismo silencio que un espejo
  muerto.
- Una celda cacheada sube su propio sello; **`getNearest` lo baja a `false` a propósito** — el sello
  es una afirmación del punto que la MIDIÓ, y el vecino puede estar a 250 m. Misma razón por la que
  presta la calle y nunca el sitio.
- ⛔ **El bucle vive una sola vez**: `domain/geocoder/ParkingEnrichment.kt::enrichParkingSession`,
  en commonMain. Android y iOS llevaban el mismo bucle copiado *con el mismo `var saved`* — dos
  copias de una decisión de reintento es exactamente la forma que permitió que el fallo corriera en
  dos plataformas a la vez. Ahora el worker y el drainer solo aportan su `.catch` (cada uno sabe su
  tag y su nº de intento) y leen el veredicto.
- Escritura redundante evitada: la respuesta "sin POI" no reescribe lo que la fase 1 ya estampó —
  si no, cada aparcamiento sin sitio cerca (o sea, la mayoría) ensuciaría la fila dos veces y
  mandaría dos pushes idénticos a Firestore.

**Testigos (falsificados, no solo verdes):**

| Test | Se rompió al… |
|---|---|
| `ParkingEnrichmentTest.should_retry_when_theLookupNeverAnswered` | devolver `addressSaved` en vez de `addressSaved && poiAnswered` → **FAILED**, y solo ese |
| `AddressAndPlaceRepositoryImplTest.should emit a second time carrying poiChecked…` | volver a `if (placeInfo != null) emit` → **FAILED**, y solo ese |

`ParkingEnrichmentTest` deja adosados a propósito los dos casos que SON el invariante (`…never
answered` y `…answeredThereIsNoPlace`): idénticos salvo la emisión que los separa, así que si esa
emisión desaparece, uno de los dos cae.

Verificado tras el rebase junto al resto del ticket — ver **Verificación** al final.


### (2) La paciencia del POI — **master la arregló mientras esto se escribía, y mejor**

Este punto nació de una aritmética propia: con `QUIET_MIRROR_MS = 8_000` clavado y
`WALK_CEILING_MS = 30_000`, subir el `[timeout:8]` de la consulta no compraba casi nada, así que el
diseño era **parametrizar el techo del walk** — 30 s interactivo, 60 s para el POI, «la paciencia es
de quien espera».

⛔ **Ese diseño está implementado y descartado.** Al rebasar apareció
`OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001` (`f511fcfe`, en master el 14-09), que atacó el
mismo problema con más datos y llegó más lejos:

- El corte por espejo deja de ser constante: es **el tiempo restante dividido entre los espejos sin
  preguntar**, con suelo en `SLOWEST_MEASURED_ANSWER_MS = 12 s`. Con la lista viva de dos, cada
  espejo recibe 15 s en vez de 8.
- Y midió que la premisa del 8 era falsa: **Overpass bufferiza la respuesta entera**, así que el
  silencio no distingue un servidor atascado de uno trabajando.

Con eso, el problema que este punto perseguía ya está resuelto: 8 s → 15 s por espejo, derivados.

⛔ **Y mi techo de 60 s habría sido activamente dañino.** El mismo ticket añadió `OverpassQuota`: un
semáforo process-wide de **UN permiso**, tomado alrededor de la caminata **entera**, con espera
acotada a 10 s. Un carril de fondo aguantando el único permiso 60 s dejaría al mapa —parkings,
gasolineras— esperando 10 s y fallando con *"another Overpass lane held the only slot"*. O sea:
habría reintroducido, por la puerta de atrás, el «tarda muchísimo o no da respuesta» que ese ticket
acababa de arreglar.

#### ✅ Lo que sí queda: `TIMEOUT_S` 8 → 20

Un solo número, y ahora con un motivo que antes no tenía. `OverpassWalkPatience` topa el turno de un
servidor en `serverBudget + NETWORK_GRACE`, así que con `[timeout:8]`:

| | turno derivado (reparto) | tope por presupuesto declarado | efectivo |
|---|---|---|---|
| Parkings, gasolineras, FR, calles (`timeout:20`) | 15 s | 25 s | **15 s** |
| POI del aparcamiento (`timeout:8`) | 15 s | **13 s** | **13 s** |

**El único carril cuya respuesta se guarda para siempre era el único que no podía usar su reparto.**
La tabla de consumidores de aquel ticket ya señaló el `[timeout:8]` como la excepción, y lo dejó en
pie; esto lo cierra. Medido el 14-09 en El Puerto: en la tirada que funcionó, el espejo sano contestó
**esta** consulta en **13,2 s** — justo por encima del tope de 13 s que imponía el 8.

**Testigo:** `OverpassPlacesDataSourceTest.should_grantTheServerTheSameBudgetAsEveryOtherLane`,
falsificado volviendo a `TIMEOUT_S = 8` → **FAILED**, y solo ese. Afirma sobre el **cuerpo de la
petición**, no sobre la constante: un test que leyera `TIMEOUT_S` no probaría que el número llega al
cable.

> Registrado como aviso: la aritmética que traía este doc («60 s permite un segundo barrido», «~5-6
> intentos») era **falsa por partida doble** —el walk recorre la lista una vez— y la corregí antes de
> escribir el código. No sirvió de nada: master ya había medido mejor. La lección es la de siempre
> aquí, **mirar master antes de razonar sobre números de red**.

### (3) Un "aquí no hay nada" se sella; un nombre se comparte

`AddressAndPlaceRepositoryImpl.kt:85-87` sella la celda con `poiChecked = true` **30 días**
(`RoomGeocoderCacheDataSource.kt:76`) en cuanto Overpass contesta, sea cual sea la respuesta. La
celda mide ~11 × 8 m y la respuesta se midió desde UN punto de dentro.

Para un POI encontrado eso da igual: un nombre a <40 m de un punto de la celda lo está también,
con error de ~14 m, de cualquier otro. Para un **negativo** no: 14 m sobre un umbral de 40 m es
justo lo que puede voltear el veredicto.

Y hay quien sella sin persistir nada. `HomeGeocodingController.geocodeUserLocation` (`:83-94`)
geocodifica tu posición en cada fix con >11 m de separación. Conduciendo se cancela sola antes de
que la fase 2 termine (a 30 km/h se supera el epsilon en ~1,4 s), así que **el sello lo gana casi
siempre alguien parado** — o sea, tú, de pie junto al coche recién aparcado, a unos metros del pin.
Si tu punto cae a >40 m de la tienda y el del coche a <40 m, y comparten celda, la celda queda
sellada "sin POI" y el worker de enriquecimiento recibe un cache hit y **ni pregunta**. Una carrera
entre dos consumidores de la misma celda donde gana el que no iba a guardar nada.

**Invariante:** *un nombre lo puede aportar cualquiera; el "aquí no hay nada" solo lo sella quien
va a persistir la respuesta.*

- `success(POI)` → sella, venga de donde venga (cámara, usuario, worker).
- `success(null)` → sella **solo** si el que pregunta persiste (enriquecimiento, publicación de
  plaza). La cámara y la posición del usuario cachean la DIRECCIÓN y dejan `poiChecked = false`.

Se implementa con un parámetro explícito en `getAddressAndPlace` (`persisting: Boolean`, default
`false`), no con un `if` sobre el nombre del llamante. Sin migración de Room: el campo ya existe.

> Alcance honesto: (3) es el más estrecho de los tres. Solo muerde en la franja de ~14 m alrededor
> del umbral de 40 m. Va porque es barato y porque la carrera es real, no porque explique la foto.

#### ✅ Hecho — pero por el lado de LEER, no por el de ESCRIBIR

⚠️ **El diseño de arriba está descartado, y conviene saber por qué.** «Sellar solo quien persiste»
suena bien hasta que se cuenta lo que cuesta: el llamante no-persistente que recibe `success(null)`
no escribiría nada (`local.get` solo sirve celdas selladas, y el propio repositorio ya razona que
una escritura sin sellar es peso muerto y además compite con la celda recién sellada). O sea que
**cada paneo del mapa a una celda sin POI volvería a preguntar a Overpass** — a unos servidores que
este mismo ticket midió devolviendo 504. Se pagaría en tráfico, por todos, para proteger un caso
estrecho.

La misma doctrina dicha desde el lado de la lectura no cuesta nada de eso:

> **Un «aquí no hay nada» cacheado sirve para callar un título del mapa; nunca para sellar el
> nombre de un aparcamiento.**

- `getAddressAndPlace(lat, lon, persisting = false)` por defecto: el mapa conserva la caché entera,
  cero tráfico nuevo.
- Con `persisting = true`, un cache hit **sin POI** no corta el flujo: se vuelve a medir en SUS
  coordenadas. Un cache hit **con nombre** se sirve a todo el mundo — equivocarse 14 m sobre
  «Supersol está aquí» no cuesta nada; el frágil es solo el negativo.
- Coste real: **una consulta Overpass por aparcamiento** (no por paneo), en una vía que además
  tiene reintentos desde (1). Eso sí lo queremos gastar.
- Y disuelve la carrera entera: quien haya sellado la celda, el camino que persiste mide por su
  cuenta.

Llamantes con `persisting = true`: `EnrichParkingSessionWorker` y `SyncOutboxDrainer`
(`OutboxOp.EnrichSession`). **`ReportSpotReleasedUseCase` se queda en `false` a propósito**, aunque
persista: tiene 5 s, un solo intento y ningún reintento, así que la re-medición gastaría el
presupuesto en expirar y publicaría con MENOS de lo que la celda ya sabía. Y la apuesta es otra —
una plaza vive minutos, luego un «sin sitio» algo rancio cuesta una línea de copy durante un cuarto
de hora; el nombre de un aparcamiento se guarda para siempre. Razonado en su propio código.

**Testigo (falsificado):** `AddressAndPlaceRepositoryImplTest.should re-measure a cached no-place
answer when the caller will store it` — al devolver el corte a `if (cached != null)`, **FAILED**, y
solo ese. Lo acompañan los dos que fijan lo que NO debe cambiar: el llamante que solo pinta sigue
sin tocar la red, y un nombre cacheado tampoco cuesta un viaje.

`FakeAddressAndPlaceRepository` pasa a ser fiel al contrato nuevo (emite la fase 2 al CONTESTAR, no
al encontrar) y registra el flag. Eso destapó dos tests que afirmaban el número de emisiones viejo
— corregidos, que es exactamente para lo que sirve un fake fiel.

## Verificación

Sobre master `f79930d3` (rebasado), `:shared:testDebugUnitTest --rerun-tasks` → **2.873 tests, 262
clases, 0 fallos** (`--rerun-tasks` para que los guardarraíles de Konsist se ejecuten de verdad).
`:app:compileProdDebugKotlin`, `:app:compileMockDebugKotlin` y `:app:assembleMockDebug` verdes.
Sin strings ni pantalla/estado nuevos → no hay locales ni Dev Catalog que tocar.

Los cuatro testigos se **falsificaron** uno a uno: cada uno cae al revertir su propio cambio, y
solo ese.


## Criterio de éxito

- `EnrichParkingSessionWorker` devuelve `retry` cuando Overpass no contestó, y `success` cuando
  contestó "no hay nada". Test con un `PlacesDataSource` fake que falla → `Result.retry()`; que
  devuelve `success(null)` → `Result.success()`.
- Mismo par de tests para `SyncOutboxDrainer` (`OutboxOp.EnrichSession`).
- `OverpassClient` con techo inyectado: test que prueba que con 60 s se preguntan más espejos que
  con 30 s ante espejos que tardan 10 s en fallar.
- Un `success(null)` desde un llamante no-persistente deja `poiChecked = false` en la celda; desde
  uno persistente, `true`. Un `success(POI)` sella siempre.
- `OverpassPlaceParserTest` actualizado al nuevo budget declarado (testigo del cambio).
- En campo: aparcar en un sitio con POI conocido con red mala → el nombre acaba apareciendo (tarde,
  pero aparece) en vez de quedarse en la dirección para siempre.

## Consumidores auditados

| Sitio | Qué asume | Estado |
|---|---|---|
| `EnrichParkingSessionWorker.kt:56-83` | `addressSaved` habla por las dos fases | ✅ **mordió aquí** — delega en `enrichParkingSession` |
| `SyncOutboxDrainer.kt:114-126` (iOS) | idéntico `var saved` | ✅ **mismo fallo** — delega en la misma función |
| `ReportSpotReleasedUseCase.kt:83-93` | geocode inline con tope de **5 s** | ✅ exento y razonado en su código: 5 s sin reintento, y una plaza vive minutos |
| `HomeGeocodingController.kt:83-130` | puede sellar celdas | ✅ sigue sellando (no cuesta nada) pero ya no DECIDE por el pin: quien persiste re-mide |
| `AddressAndPlaceRepositoryImpl.kt:85-90` | sella con cualquier `isSuccess`; re-emite solo con POI | ✅ re-emite al CONTESTAR (1); el sello negativo deja de valer para quien persiste (3) |
| `OsmPublicParkingsSource`, `OsmFuelStationsSource`, `FranceFuelStationsSource`/`OsmStationNames`, `OverpassRoadNetworkDataSource` | ya declaraban `timeout:20` | ✅ intactos — el techo del walk NO se toca (ver (2)) y el cupo compartido sigue siendo de master |
| `ReportSpotReleasedUseCase` (otra vez, ahora por el techo) | tope propio de 5 s sobre el geocode | ✅ exento — su `withTimeoutOrNull(5_000)` lo protege: un techo de 60 s no puede retrasar la publicación de una plaza |
| `ResolveAskedStreetUseCase` | lee `AddressAndPlace` | ⚪ exento — consume, no sella |
| `UserParkingDao.updateAddressAndPlace:169-194` | sobrescribe los 8 campos en bloque | 🟡 anotado abajo, fuera de alcance |

## Fuera de alcance (follow-ups)

1. **OSM dice «Supersol» donde hay un Carrefour.** Editar OSM upstream. No es código.
2. **`UserParkingDao.updateAddressAndPlace` sobrescribe en bloque**: un re-enqueue del
   enriquecimiento (`ExistingWorkPolicy.REPLACE`) empieza por la fase 1 y **borra el POI que ya
   había** hasta que llegue la fase 2; si el proceso muere en ese hueco, la sesión se queda sin POI.
   Ticket propio.
3. **Nada rellena las sesiones ya perdidas.** No hay worker que busque
   `placeInfoName IS NULL AND addressStreet IS NOT NULL` y reintente. Este ticket evita perder las
   futuras; las pasadas siguen perdidas. Ticket propio si se decide que merece la pena.
