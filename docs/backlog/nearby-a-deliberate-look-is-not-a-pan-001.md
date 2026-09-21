# NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001 · Pedir un sitio no es arrastrar hasta él

**Estado:** 🔵 En revisión · rama `feature/NEARBY-A-DELIBERATE-LOOK-IS-NOT-A-PAN-001` ·
cierra la **issue #44**

## La pregunta de la issue

> «¿Por qué tenemos esta condición? ¿En qué nos ayuda? Si salgo de mi casa y hago un viaje y quiero
> ver los parkings o gasolineras de otra ciudad para cuando llegue…»

**Para qué existe** (`NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001`): el carril SIGUE a la cámara, y cada
asentamiento pasada media radio dispara una petición a un servicio público. Arrastrar el mapa media
España son cientos. El tope, además, evitaba una mentira concreta: enseñar las gasolineras de tu
ciudad sobre el mapa de otra.

**Qué estaba mal**: el tope estaba en **20 km** —la provincia, no el viaje— y, sobre todo, **no
distinguía arrastrar de pedir**. Quien busca «Cádiz» y abre el carril de parkings ha hecho UNA
pregunta sobre Cádiz; contestarle «demasiado lejos» es negarle lo único que pidió.

## Diseño

- **`isWorthAsking(..., deliberate)`**: abrir el carril (`NearbyTrigger.ENTER`) no tiene tope. Es
  una petición, no un reguero. El precalentamiento (`WARM_UP`) y el seguimiento (`CAMERA`) lo
  mantienen, que son los que se multiplican.
- **`MAX_FROM_USER_METERS`: 20 km → 150 km** — el destino de un viaje de hoy. Asumible porque la
  respuesta se **cachea por celda** (cobertura OSM en Room): una región preguntada una vez no se
  vuelve a preguntar.
- **Plazas: 2 km → 5 km** (`ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS`), el mismo
  alcance que las gasolineras. Era el carril que menos lejos veía, y es el producto de la app.
- La frase de «demasiado lejos» **deriva la cifra de la constante** (`radiusString`), así que pasa a
  decir 150 km sola, en los nueve idiomas y en millas donde toca.

## Criterio de éxito

- Buscar otra ciudad y abrir parkings/gasolineras **contesta**, esté donde esté.
- Arrastrar el mapa 300 km sigue sin disparar peticiones.
- Las plazas se ven en un radio mayor alrededor de donde mires.

## Preguntas (para la revisión)

1. **150 km** es una decisión de producto, no una medida: es «hora y media de coche». Si te sabe
   corto o largo, es una constante.
2. **Plazas a 5 km**: más documentos por consulta en Firestore. No es gratis, pero es el mismo
   alcance que ya tienen las gasolineras. Si ves el mapa cargado de pines, se baja.

## Segunda vuelta (review del user, 20-09): el tope se retira entero

> «No sé si estamos aplicando bien esta lógica. Deberíamos tener una distancia límite para volver a
> cargar desde la última ubicación en que se cargaron. Si cargo parkings y paneo 5 m, no veo por qué
> pedir otra vez. […] Seguimos sin poder ver parkings de otra ciudad. ¿Seguimos necesitando esto?
> ¿Es necesario?»

### Lo primero: ese umbral YA existe, y los parkings van aún más lejos

- **Gasolineras** → `ViewportRefetch.shouldRefetch`: sólo se re-pregunta pasada **media radio** desde
  el ancla de la última respuesta.
- **Parkings** → ni siquiera por distancia: re-preguntan al **cambiar de ZONA**
  (`ParkingZoneGrid.sameZone`), así que panear dentro de la zona no le debe nada al servidor
  [PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001].

O sea que **el caso de los 5 m no pedía datos ni antes ni ahora**.

### Y entonces, ¿era necesario el tope? **No. Y su argumento escrito era falso.**

El tope decía protegernos de esto: *«cada asentamiento de un arrastre cuesta una petición, y
arrastrar el mapa media España son cientos»*.

Se midió leyendo `scheduleNearbyRefresh`, y **no es así**: cancela el trabajo anterior y vuelve a
esperar `NEARBY_CAMERA_SETTLE_MS` (**600 ms**) en CADA movimiento de cámara. Un arrastre continuo,
cruce lo que cruce, dispara **una** petición: la del final. Para gastar cientos habría que hacer
cientos de pausas deliberadas de más de medio segundo.

Así que había **tres** guardas impidiendo la tormenta, y el tope no era ninguno de ellos:

| Guarda | Qué impide |
|---|---|
| Debounce de 600 ms | un arrastre entero = **una** petición |
| Media radio / zona entera | moverse poco no pregunta **nada** |
| Caché por celda/zona en Room | una región preguntada una vez no se vuelve a preguntar |

Y lo que el tope **sí** costaba era real: con el carril abierto no podías mirar los parkings de otra
ciudad arrastrando el mapa. Protegía de algo que no pasaba y cobraba justo la función de la issue.

### Qué se retira

- `MAX_FROM_USER_METERS` y el parámetro `deliberate` — sin tope no hay nada que eximir.
- `isWorthAsking` pasa a **`isAPlace(lat, lon)`**: la única razón que queda para no preguntar es que
  (0, 0) no es un sitio, es un mapa sin cámara. Un nombre que dice lo que hace.
- El estado **`tooFar`** de los dos carriles, sus dos frases en los 9 locales, `emptyNearbyAsTooFar`
  y las dos variantes de la galería. No describían el mundo: describían nuestro propio límite.
- `NearbyTrigger.FIX` se simplifica: ya sólo sirve para dar el centro que faltaba.

### Consumidores auditados

`grep -rn "tooFar\|isWorthAsking\|MAX_FROM_USER" shared/src app/src` → **0 restos** fuera del KDoc
que explica la retirada.

| Sitio | Estado |
|---|---|
| `HomeViewModel.refreshNearby` | cerrado — sólo `isAPlace` |
| `HomeFuelSlice` · `HomeParkingsSlice` | campo retirado |
| `HomeFuelLane` · `HomeParkingsLane` | rama retirada, imports huérfanos limpiados |
| `StateGalleryScreen` | dos variantes «Demasiado lejos de ti» retiradas |
| `home_fuel_too_far` · `home_parkings_too_far` | fuera de los 9 locales |
| `HomeSpotsController` | **exento** — su comentario cita el nombre viejo; actualizado |

### Tests

Reescritos como testigos del cambio, no adaptados al nuevo resultado:
- `should_askAboutTheNewCity_when_theCameraDragsFarFromTheUser` — arrastrar a París **pregunta** y
  enseña **lo de allí**, no las de tu ciudad sobre un mapa ajeno.
- `should_askAboutTheNewZone_when_theCameraDragsFarFromTheUser` — lo mismo en parkings.
- `should_followTheCameraAnywhere_when_theUserDragsToAnotherCity` — el predicado puro.
- `should_refuseToAsk_at_theGulfOfGuinea` — lo único que se sigue negando, más el caso que antes no
  estaba: el meridiano y el ecuador **sí** son sitios.

**3.498 tests verdes, 0 fallos** con `--rerun-tasks` (fuerza también los guards de Konsist y la
paridad de locales).

⏳ **Sin captura del estado final**: no hay device conectado.
