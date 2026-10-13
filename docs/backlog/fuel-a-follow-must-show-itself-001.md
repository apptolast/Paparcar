# FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001 · Seguir una gasolinera no se ve en ninguna lista, y lo que promete no puede llegar

**Estado:** ✅ Done (13-10-2026) · rama y worktree borrados al cerrar · ⏳ sin ver en mano
**Origen:** el user, en device: *«le damos a seguir y no aparece nada… y he puesto alerta pero
tampoco me avisa»*.

## Problema

Reportado por el user (13-10): *«cuando le damos a seguir una gasolinera no aparece nada en el item
de la lista de gasolineras o su modal, ni una estrellita o marca que me haga saber que está
siguiendo o no, y he puesto alerta pero tampoco me avisa de nada»*.

Tres fallos distintos, medidos en el código:

### 1 · La marca de «siguiendo» existe en UN solo sitio de la app

`followedFuelStationKeys` lleva en `HomeState` desde
[FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] y tiene **un** consumidor en toda la app:
`HomePeekHandle.kt:227`, que lo pasa al peek. Y en el peek lo único que lo dice es el **botón de
pie** (`FuelStationPeek.kt:217-228`): outlined, el último de la hoja, bajo «Cómo llegar».

- `HomeFuelStationRow` **no recibe** `isFollowed` — su `trailing` solo sabe de precio y badge, y
  `HomeFuelStationRows` no tiene por dónde enterarse: `HomeBrowseListSlice` no lleva el conjunto.
- La cápsula del mapa tampoco lo marca.
- El historial de precios que el follow empieza a grabar no se ve en **ninguna** pantalla:
  `observeHistoryFor` no tiene ni un consumidor fuera de la capa de datos.

O sea: la única superficie que distingue una gasolinera seguida de una que no lo es es el estado de
un botón que hay que ir a buscar al final de su propia hoja.

### 2 · ⛔ El precio de lo que sigues se mide donde estás TÚ, no donde está la gasolinera

`RunFuelPriceWatchUseCase` pide UNA respuesta, centrada en el fix del usuario
(`GetNearbyFuelStationsUseCase`, radio por defecto **5 km** —
`FuelStationsQuery.DEFAULT_RADIUS_METERS`), y graba muestras filtrando esa respuesta por las
seguidas:

```kotlin
val samples = answer.stations
    .filter { it.stationKey in followedKeys }   // ← solo las que caen en TU radio de 5 km
```

Una gasolinera seguida a más de 5 km de donde el móvil esté cuando el worker diario dispara **no
graba muestra ese día**. Y `EvaluateDailyPriceMoveUseCase` exige **dos muestras de días distintos**
(`current.observedDay == previous.observedDay → null`), las dos con sello del feed, mismo surtidor
y ≥0,5 c/l. Resultado: la serie de una gasolinera solo crece los días en que el usuario pasa cerca
de ella a la hora exacta del tick, y el aviso de movimiento es inalcanzable para justo las
gasolineras que uno sigue *porque no las tiene delante*.

Lo contradice el propio diseño del almacén: `FollowedFuelStationEntity` **copia la geografía** en
lugar de joinearla *«para que una seguida a 300 km siga siendo nombrable y ubicable»*. Se copió la
posición y luego no se usó para preguntar por ella.

### 3 · La vigilancia es Plus y calla sin decirlo

`RunFuelPriceWatchUseCase` abre con `if (!observeIsPlus().first()) return Skipped("not-plus")`. La
estrella es gratis **a propósito** (lo que vende Plus es el vigilar, no el marcar) pero ninguna
superficie del follow lo menciona: quien no tiene Plus sigue una gasolinera, no pasa nada nunca, y
no hay una sola línea que lo explique. Quien sí lo tiene tampoco sabe que el primer aviso no puede
llegar antes de mañana.

## Doctrina violada

- **Sistemas, no parches** — el invariante «una gasolinera seguida se ve donde se ve la gasolinera»
  no vive en ningún sitio: vive en el `if` de un botón.
- **Gating soft** (CLAUDE.md § Plus vs free) — *la acción dispara el paywall, el botón nunca se
  esconde*. Aquí no se esconde el botón: se esconde la **consecuencia**. El free no se encuentra un
  paywall, se encuentra silencio.
- **No copy al usuario con mecánica interna ni jerga** — pero tampoco **sin** causa + consecuencia:
  seguir no dice qué va a pasar ni cuándo.
- [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] — *«sin un conjunto de seguidas no hay
  gasolinera que siga existiendo cuando dejas de mirar»*. La serie se grabó con la regla contraria:
  solo existe mientras la mires.

## Señales / datos disponibles

- `HomeState.followedFuelStationKeys` ya es un `Set<String>` vivo del store (`observeFollowed()`),
  suscripción permanente, no respuesta de carril.
- `HomeState.isPlus` ya está en el estado (no en ningún slice todavía).
- `FollowedFuelStation` ya trae `latitude`/`longitude` copiadas.
- `GetNearbyFuelStationsUseCase` ya acepta `radiusMeters`.

## Diseño

### A · Una marca, un objeto, todas las superficies donde se ve una gasolinera

`FuelFollowMark` — estrella rellena, **una sola definición**, en la línea del NOMBRE:

- **fila** (`HomeFuelStationRow`): vía `titleSlot`, el mismo mecanismo con el que la fila de plaza
  mete su glifo POI inline [UI-LIST-ITEM-001]. No en `trailing`: ahí manda el precio, y el
  `trailing` es *una* afordancia compacta, no dos.
- **peek** (`FuelStationPeek`): la misma estrella en la misma relación con el título. `PapSheet`
  gana un slot `titleMark` aditivo (default null ⇒ los otros ocho peeks no cambian ni un píxel) en
  vez de que la hoja de gasolinera se dibuje su propia cabecera.

El color es el verde legible de acción (`PapColor.actionText`), el mismo que ya lleva el botón que
la enciende: la estrella es la huella de un gesto del usuario, no identidad de vehículo ni estado de
caducidad. [UI-COLOR-DOCTRINE-001]

### B · La muestra se toma donde está la gasolinera

`RunFuelPriceWatchUseCase` sigue pidiendo la respuesta centrada en el usuario —la necesita el
veredicto «más barata cerca», que es una frase sobre un desvío **desde aquí**— y, para las seguidas
que esa respuesta no cubra, pregunta **alrededor de la gasolinera misma**, reutilizando cada
respuesta para el resto: N seguidas en una provincia cuestan UNA consulta.

Tope de consultas por tick (`MAX_LOOKUPS_PER_RUN`), porque cada disco nuevo es una descarga de
provincia: el coste de datos de un follow no puede crecer sin techo. Se registra cuando muerde.

### C · El follow dice qué promete

Una línea bajo la estrella del peek, solo cuando la gasolinera está seguida:

- con Plus → «Te avisaremos si su precio cambia»
- sin Plus → «Vigilarla a diario es Plus» (la estrella **sigue siendo gratis**; esto es la frase que
  faltaba, no un gate nuevo)

⛔ Fuera de alcance deliberado, con su razón: el caso «Plus + interruptor de avisos apagado en
Ajustes» exigiría un observador nuevo en `AppPreferences` (interfaz + 3 impls) para una frase que ya
vive, con su propia explicación, en la fila de Ajustes que lo apagó. El interruptor viene encendido.

## Criterio de éxito

- La fila de la lista y la cabecera del peek llevan la estrella en cuanto la gasolinera está
  seguida, y la pierden al dejar de seguirla, sin recargar la lista.
- Una gasolinera seguida a 50 km del usuario graba su muestra diaria → al segundo día hay dos
  muestras de días distintos y el aviso de movimiento es posible. Test unitario del shell de I/O
  (`RunFuelPriceWatchUseCaseTest`, que no existía).
- Seguir sin Plus dice en una línea por qué no va a avisar.
- `./gradlew testAll` verde + `assembleMockDebug` + variante en la galería de estados.

## Consumidores auditados

`grep -rn "followedFuelStationKeys\|isFollowed"` + todos los sitios que pintan una gasolinera:

| Superficie | Antes | Ahora |
|---|---|---|
| `FuelStationPeek` (botón de pie) | único sitio que lo decía | **cubierto** — sigue siendo el toggle, y ahora también lleva marca y promesa |
| `HomeFuelStationRow` / `HomeFuelStationRows` | no lo recibía | **cerrado** — `isFollowed` + `followedStationKeys` |
| `HomeFuelLane` | no lo recibía | **cerrado** — lo pasa al listado |
| `HomeBrowseListSlice` / `toBrowseListSlice()` | no llevaba el conjunto | **cerrado** |
| `HomePeekSlice` / `toPeekSlice()` | llevaba el conjunto, no `isPlus` | **cerrado** — `isPlus` para la frase |
| Cápsula del mapa (`PaparcarMapMarkers`) | no lo marca | **follow-up abierto** — [`fuel-the-map-capsule-must-wear-the-follow-001`](fuel-the-map-capsule-must-wear-the-follow-001.md): se dibuja en canvas y `isFollowed` tiene que entrar en su clave de caché, con medida en device |
| Historial de precios grabado (`observeHistoryFor`) | cero consumidores en UI | **follow-up abierto** — [`fuel-a-recorded-series-must-be-readable-001`](fuel-a-recorded-series-must-be-readable-001.md): 30 días medidos y guardados que no se leen en ninguna pantalla |
| `RunFuelPriceWatchUseCase` (muestreo) | filtraba la respuesta del usuario | **cerrado** — pregunta por cada seguida donde está |
| `EvaluateCheaperStationNearbyUseCase` | candidatas = respuesta del usuario | **exento, y a propósito**: «más barata cerca» es un desvío desde donde estás; sus candidatas NO deben incluir discos remotos |
| Ajustes (interruptor de avisos de carburante) | ya gatea en blando con paywall | **sin cambios** |

## Lo medido

- `./gradlew testAll --rerun-tasks` → **3.895 tests, verde**. `assembleMockDebug` y
  `compileProdDebugKotlin` también.
- `RunFuelPriceWatchUseCaseTest` (nuevo, 6 casos) **falsificado**: con el muestreo anterior puesto
  —filtrar la respuesta del usuario— caen 3 de los 6, el primero el de la regresión. El shell de I/O
  no tenía ni un test, y por eso el fallo no rompía nada: una muestra que no se toma deja al
  evaluador sin nada que decir, que es indistinguible de un precio que no se movió.
- El doble del feed contesta **por radio** (`haversineMeters` contra `query.radiusMeters`), así que
  el 5 km de la respuesta del usuario y el 1 km de la consulta por una seguida son reales en el test
  y no un acuerdo entre fakes.
- `HomeSlicesTest` gana el caso de que el conjunto de seguidas llega **al carril** y al peek: era el
  dato que no llegaba, no un olvido de diseño de la fila.
- **La web no cambia**: `web/content/es.json:213` ya promete *«Aviso cuando baja el precio de una
  gasolinera que sigues»*. La promesa no se ha movido — ha empezado a cumplirse. Nada que
  reconstruir ni desplegar.
