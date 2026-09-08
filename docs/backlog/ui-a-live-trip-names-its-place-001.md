# UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001 · Un viaje en curso dice POR DÓNDE va, y lo dice desde el coche — no desde la cámara

**Estado:** ✅ Done — en master (squash 08-09-2026)

## Problema

Dos huecos que son el mismo hueco: **quién tiene derecho a nombrar el sitio de un coche**.

**(a) El chip y la card se quedan mudos en ruta.** El pie del chip de vehículo es la fila del hecho
accionable, y tiene tres ramas:

| Estado | `HomeVehicleChip` (2+ coches) | `HomeVehicleCard` (1 coche) |
|---|---|---|
| Aparcado | 📍 + `"Calle Mayor 4 · hace 2 h"` (`HomeParkingRow.kt:163-183`) | 📍 + `"Aparcado en Calle Mayor 4"` + `"hace 2 h · 180 m"` (`:291-309`) |
| Sin marcar | glifo + `"Sin marcar"` | CTA `"Marcar aparcamiento"` |
| **En ruta** | glifo de ruta + **`"En ruta"` y nada más** (`:151-162`) | glifo de ruta + **`"En ruta"` y nada más** (`:282-290`) |

Aparcado, el pie te dice DÓNDE. En ruta, el estado que más se mira, el pie solo repite lo que el
borde grueso, el halo y el glifo animado ya están diciendo: que el coche se mueve. La única
pregunta que el usuario tiene abierta mientras conduce — *¿por dónde vamos?* — no se contesta.
Palabras del user: *«agregar al chip de coche en home dirección de aparcado y cuando va en ruta,
igual que en peek»*.

**(b) El peek sí la dice, pero se la inventa la cámara.** La rama de conducción de `BrowsePeek`
resuelve su título desde `cameraInfo` (`BrowsePeek.kt:104-112`), que es
`HomeState.cameraAddressAndPlace` — *«geocoded address of the map camera centre (updated as the
user pans)»* (`HomeState.kt:121-122`). Parece correcto porque la cámara sigue al puck del viaje;
deja de serlo **en cuanto paneas el mapa conduciendo**: el peek pasa a nombrar la calle que estás
mirando y se la atribuye a tu coche, con el nombre del coche al lado en el eyebrow
(`"TOYOTA COROLLA · EN RUTA"`).

Ese defecto ya estaba diagnosticado y cerrado **para la rama aparcada**, tres líneas más abajo en el
mismo fichero (`BrowsePeek.kt:143-146`):

> *"Title/sub come from THE SESSION — static. The camera must never drag your parked car around
> ('one car parked in two places')."* [UI-SHEET-004]

La rama de conducción no recibió esa regla. Por eso (a) y (b) son un solo ticket: si arreglamos (a)
con la fuente honesta y dejamos (b) con la cámara, tendremos **dos superficies simultáneas en la
misma pantalla nombrando dos calles distintas para el mismo viaje** — el chip diría dónde está el
coche y el peek dónde mira el dedo.

## Doctrina violada

`UI-SHEET-004` — *la dirección la pone el SUJETO, no la cámara*. Escrita para la sesión aparcada,
nunca aplicada al viaje vivo, que también tiene un sujeto (el coche que se mueve) y también tiene
una posición propia.

`feedback_systems_not_patches` — el invariante ("cada sujeto nombra su sitio desde su propia
posición") tiene que quedar en UN sitio y barrer a sus tres consumidores, no arreglarse solo donde
mordió.

No hay invariante de detección implicado: esto es presentación pura. No toca
`PARKING-DETECTION.md`.

## Señales / datos disponibles

Ya lo tenemos todo en `HomeState`; **no hace falta una geocodificación nueva ni una llamada de red
más**:

- `HomeState.userAddressAndPlace` (`HomeState.kt:83`) — geocode de la posición del teléfono, que se
  dispara **en cada emisión de GPS** y está deduplicado por celda de ~11 m
  (`HomeGeocodingController.kt:83-94` + `SAME_POINT_EPSILON_DEG`). Conduciendo, el teléfono va
  dentro del coche: es la misma premisa sobre la que se sostiene toda la detección, así que **esta
  es la dirección del coche en movimiento**.
- `HomeState.drivingMeta` (`:89`) — qué vehículo y qué fase. Ya viaja a los dos slices.
- `HomeState.cameraAddressAndPlace` (`:122`) — sigue siendo la fuente legítima del sujeto ZONA.
- `HomeViewModel.tripRender` — la posición del puck. **Descartada a propósito** como fuente: está
  fuera de `HomeState` por diseño (cambia al ritmo del fix y recompondría todo Home,
  `HomeState.kt:84-88`) y geocodificarla sería una segunda cola de geocode para responder lo que la
  del usuario ya responde a 11 m de resolución.

Lo que **no** existe hoy: `HomeBrowseListSlice` (`HomeSlices.kt:216-242`) no lleva ninguna
dirección. Es el único hueco de fontanería.

## Diseño

### D1 · Una tabla de sujetos, tres consumidores

El invariante se escribe una vez: **cada sujeto nombra su sitio desde su propia posición.**

| Sujeto | Fuente de la dirección | Quién la pinta | Hoy |
|---|---|---|---|
| Sesión aparcada | `session.placeInfo/address` (estática) | chip · card · `ParkingPeek` · `BrowsePeek`(aparcado) | ✅ ya coherente |
| **Viaje vivo** | **geocode del propio coche = `userAddressAndPlace`** | chip · card · `BrowsePeek`(ruta) | ❌ chip/card mudos, peek con la cámara |
| Zona / mapa | `cameraAddressAndPlace` | `BrowsePeek`(zona) | ✅ correcto |

Nótese que la fila 1 ya está resuelta y **no se toca**: `ParkingPeek.kt:68-72` y `parkedName()`
(`HomeParkingRow.kt:334`) ya leen el mismo campo de la sesión. Peek, card y chip ya dicen lo mismo
aparcados; lo que cambia entre ellos es la DENSIDAD (peek: título + filas de distancia y duración;
card: título + meta; chip: una línea), no el hecho. Ese es el patrón que la fila 2 debe copiar.

### D2 · Fontanería: hoisting de una línea ya resuelta, no del objeto

Un tipo nuevo, mínimo, en `presentation/util/PlaceLine.kt`:

```kotlin
@Immutable data class PlaceLine(val line: String, val approximate: Boolean = false)
fun AddressAndPlace?.toPlaceLine(): PlaceLine?          // POI > calle; en blanco → null
fun AddressAndPlace?.secondaryPlaceLine(): String?      // la calle bajo el POI, o ciudad/provincia
@Composable fun placeLineText(place: PlaceLine?): String?   // aplica "Cerca de X"
```

`HomeBrowseListSlice` gana `drivingPlace: PlaceLine?`; `HomePeekSlice` gana ese mismo campo más
`drivingSecondaryLine: String?`. Las dos proyecciones lo resuelven igual:

```kotlin
drivingPlace = drivingMeta?.let { userAddressAndPlace.toPlaceLine() },
```

Cuatro decisiones deliberadas, cada una con su motivo:

1. **Se proyecta la línea ya resuelta, no el `AddressAndPlace`.** La igualdad estructural del slice
   es lo que da el skipping gratis (`HomeSlices.kt:26-32`). Un `AddressAndPlace` cambia con
   cualquier campo del geocode; la línea resuelta **solo cambia cuando cambias de calle**.
   Conduciendo por una avenida larga, el slice no se mueve. Cubierto por test.
2. **`approximate` viaja como flag, no como prosa.** "Cerca de X" es un string localizado y los
   slices se construyen fuera de composición; el render site lo dice con `placeLineText`. Así la
   respuesta prestada del caché de geocode sigue declarándose como tal
   [GEO-CACHE-ANSWERS-NEARBY-001] sin meter recursos en la proyección.
3. **Null salvo viaje vivo.** Aparcado o en reposo el campo es `null` y la lista de Home no ve ni
   una recomposición nueva respecto a hoy. El coste solo existe mientras hay viaje, que es cuando
   el dato se está mirando.
4. **Un solo resolver.** `cameraLine()` de `PeekShared.kt`, que tenía su propia copia de la misma
   cadena de fallback, pasa a delegar en `toPlaceLine()`. Dos copias de esa cadena son dos
   oportunidades de que dos superficies nombren distinto el mismo punto — que es el bug del ticket.

Los composables siguen tontos: reciben `PlaceLine?` por parámetro (`HomeSheetContent.vehiclesSection`
→ `HomeVehicleChip` / `HomeVehicleCard`; `HomePeekHandle` → `BrowsePeek`). Ninguno lee estado ni
decide fallbacks.

### D3 · Cómo se pinta en el chip (pie de 34 dp, una línea)

El pie del chip en ruta pasa de un `Text` suelto a **una sola línea de dos tramos**, igual anatomía
que la rama aparcada (`"Calle Mayor 4 · hace 2 h"`):

```
[glifo de ruta]  En ruta · Gran Vía 28
                 ▲ pulsa   ▲ opaca
```

El pulso **no puede teñir la dirección**: el estado late, el hecho no. Se resuelve con el patrón que
`BrowsePeek` ya usa del revés (`eyebrowHighlight`, `BrowsePeek.kt:125-133`): un `AnnotatedString`
donde el color base lleva el alfa de `rememberDrivingStatePulse()` y el tramo de la dirección lleva
su propio span opaco. Sin API nueva, sin segunda fila, sin crecer el pie.

**El rol del pie pasa de `label` a `caption`**, decidido al implementar y no antes. Dos motivos, y el
segundo es el que manda:

1. `caption` es lo que YA usan las otras dos ramas del pie (aparcado y sin marcar). En ruta era la
   única que iba en `label`; con la dirección dentro, esa excepción dejaba la misma dirección con
   dos tamaños según el estado del coche.
2. La alternativa —mantener `label` para la fase y `caption` para el sitio dentro de una línea—
   exige un `SpanStyle(fontWeight = …)` en el call site, **prohibido en `presentation/`** y cazado
   por `TypographyGuardrailTest`. La regla es correcta y no se toca: si hicieran falta dos pesos a
   un tamaño, serían dos roles, y aquí no hacen falta ninguno. **La fase se distingue por el pulso,
   no por el peso** — que es exactamente lo que la doctrina de color pide para un estado.

Si no hay dirección todavía (geocode en vuelo, primer arranque, sin GPS), la línea se queda en
`"En ruta"` exactamente como hoy. **Nunca un "Dirección desconocida"**: el pie tiene una línea y
gastarla en decir que no sabemos es peor que no decir nada.

### D4 · Cómo se pinta en la card (1 coche, hay sitio)

La card ya tiene columna de título + sublínea. En ruta:

- **título** = `"En ruta"` / `"Aparcando…"`, en `rowTitle` con su pulso — donde ya está hoy, sin
  moverlo;
- **sublínea** = la dirección, en `caption`/`onSurfaceVariant` — el mismo hueco donde aparcada vive
  `"hace 2 h · 180 m"`.

Es decir, la card no cambia de anatomía: rellena una sublínea que hoy está vacía. Y así la card en
ruta y la card aparcada tienen el mismo esqueleto (hecho arriba, detalle abajo).

⚠️ Deliberadamente **no** se invierte el orden (dirección arriba, "En ruta" abajo) aunque sea lo que
hace el peek: en la card el título es el hecho del coche, y el hecho de un coche que se mueve es que
se mueve. El peek puede permitirse lo contrario porque la fase la lleva en el eyebrow, encima.

### D5 · El peek de conducción se desengancha de la cámara

`BrowsePeek`, rama `drivingMeta != null`: `cameraInfo` → la misma dirección del sujeto. `PapSheet`
no cambia de forma; cambia de dónde saca el `title`/`subtitle`.

Requiere que `HomePeekSlice` reciba `userAddressAndPlace` (o la línea ya resuelta, por el mismo
argumento de D2). La rama de ZONA sigue leyendo `cameraAddressAndPlace` sin tocar, igual que
`cameraTitleWhileSettling` (`HomePeekHandle.kt:223`) y el shimmer de geocode: siguen siendo asuntos
de la cámara, que es lo que son.

> **Alcance separable.** D5 es lo que impide que el chip y el peek se contradigan; si se decide
> dejarlo fuera, el ticket sigue siendo coherente por sí solo (D1-D4) pero **hay que asumir por
> escrito** que paneando el mapa en ruta el peek y el chip nombrarán calles distintas.

## Criterio de éxito

- Conduciendo con 2+ coches: el chip del coche en viaje lee `"En ruta · <calle>"`, con "En ruta"
  latiendo y la calle opaca. Los demás coches no cambian.
- Conduciendo con 1 coche: la card lee `"En ruta"` sobre la dirección viva; la sublínea deja de
  estar vacía.
- Sin geocode todavía → `"En ruta"` a secas, sin salto de altura del pie ni texto de relleno.
- Aparcado y sin marcar: **ni un píxel de cambio** en chip, card ni peek.
- Paneando el mapa mientras conduces (D5): el peek sigue nombrando la calle del coche, no la del
  centro de la cámara; el chip y el peek dicen lo MISMO.
- La cadena solo cambia al cambiar de calle: en una avenida larga el slice no re-emite (verificable
  en test de proyección con dos `AddressAndPlace` distintos que resuelven a la misma línea).
- `ColorGuardrailTest` · `TypographyGuardrailTest` · `DividerGuardrailTest` (Konsist) verdes; suite
  completa verde.
- Sin strings nuevos: se reutilizan `home_det_monitoring` y
  `home_vehicle_chip_status_candidate`; el separador `" · "` ya es el del pie aparcado.

## Consumidores auditados

`grep` de todo lo que nombra un sitio de un vehículo:

| Sitio | Qué hace hoy | Veredicto |
|---|---|---|
| `HomeParkingRow.HomeVehicleChip` (ruta) | solo la palabra de fase | **cerrado** — D3 |
| `HomeParkingRow.HomeVehicleCard` (ruta) | solo la palabra de fase | **cerrado** — D4 |
| `BrowsePeek` rama conducción | título desde la CÁMARA | **cerrado** — D5 |
| `BrowsePeek` rama aparcado | título desde la sesión | **exento** — ya cumple el invariante [UI-SHEET-004] |
| `BrowsePeek` rama zona | título desde la cámara | **exento** — su sujeto ES la cámara |
| `ParkingPeek` | título desde la sesión | **exento** — misma fuente que el chip aparcado |
| `AddingParkingPeek` | dirección del pin que estás colocando | **exento** — su sujeto es el pin en el dedo, no el coche |
| `HomePeekHandle.cameraTitleWhileSettling` | título de cámara mientras se asienta | **exento** — asunto de cámara |
| `presentation/vehicles/*` (ficha del garaje) | no pinta fase de conducción | **exento** |
| Widget (`WIDGET-PARKED-001` / `WIDGET-ACTIONS-002`) | dirección de la sesión aparcada | **exento** — no pinta viaje vivo |

## Sistema de pruebas mock

- `StateGalleryScreen.kt`: las variantes de conducción (card 1 coche, chips 2+, peek en ruta) ya
  existían desde `UI-CHIP-ROUTE-GLYPH-001` pero **sin dirección** → se les da
  `userAddressAndPlace`. ⚠️ La del **peek** lo necesitaba de verdad, no por adorno: al mudar su
  fuente de la cámara al coche, sin ese campo la variante mostraba "dirección desconocida" con la
  cámara llena. Añadida además la variante **"Sheet · en ruta sin dirección todavía"**, el caso que
  no puede romperse.
- `HomeSheetPreviews.kt`: las tres previews de conducción reciben `PREVIEW_DRIVING_PLACE`; el chip
  candidato se queda **a propósito** sin dirección, para que el fallback se vea en la misma imagen.

## Verificación

- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` ✅
- `:shared:testDebugUnitTest` ✅ **2369 tests**, con 5 tests nuevos de proyección en `HomeSlicesTest`
  (fuente = coche y no cámara · POI sobre calle con la calle debajo · silencio sin geocode usable ·
  nada fuera del viaje · **el slice no re-emite mientras la calle no cambia**).
- Guardarraíles Konsist (`ColorGuardrailTest`, `TypographyGuardrailTest`, `DividerGuardrailTest`)
  ✅ relanzados con `--rerun-tasks`, que aquí **no es opcional**: los cambios de estilo no mueven el
  bytecode y Gradle los daría por UP-TO-DATE sin ejecutarlos.
- Un test se cayó a la primera y valió la pena: `AddressInfo.displayLine` cae
  `street → city → region → country`, así que un geocode que solo resolvió la provincia **sí** tiene
  línea. El fixture "sin dirección" tuvo que vaciarse entero. Consecuencia real: por la N-VI el pie
  puede decir "En ruta · Madrid" antes que callarse, y eso es correcto — es verdad.
- ⏳ Pendiente en mano: `/run` y mirar el pie del chip conduciendo (que la calle no baile con el
  pulso) y el peek con el mapa paneado (que siga nombrando el coche).

## Follow-up abierto: ¿y el widget? (planteado por el user, SIN decidir)

El widget no entra aquí y no comparte nada con esta fontanería, pero la pregunta es legítima. Lo
medido:

- `ParkedWidgetState` (`presentation/widget/ParkedWidgetState.kt`) tiene **dos** estados, `Empty` y
  `Parked`. **No existe un estado "en ruta"**: conduciendo, el widget enseña `Empty`, o sea "marca
  dónde aparcas" justo mientras vas al volante. Eso sí es un defecto, y es el único que yo cerraría.
- `ParkedWidgetRefresher` observa **Room** (`observeActiveSessions` + `observeVehicles`) y repinta
  solo cuando ese estado cambia. El viaje vivo no está en Room, así que haría falta una señal nueva
  alcanzable desde `:app` (a comprobar: `domain/detection/DetectionRuntimeState`).
- **La animación no es portable**: Glance pinta `RemoteViews`, que no animan. Simularla repintando
  sería un repintado por frame con la app en segundo plano — justo lo que la arquitectura del widget
  evita al repintar solo en cambios de estado.
- **La dirección en ruta tampoco la llevaría**: `subscribeGpsLocation` está gateado por
  `mapForeground` (`HomeViewModel.kt:958-963`), o sea que con la app cerrada —el caso del widget—
  **no hay geocode de usuario corriendo**. Habría que abrir uno en segundo plano, con red y batería
  durante todo el viaje, para nombrar una calle a alguien que está dentro del coche.

Propuesta si se abre ticket: **tercer estado `Driving`, sin dirección y sin animación** ("En ruta ·
te aviso al aparcar"). Dos repintados por viaje (arranque y aparcado), cero geocodes nuevos, y el
widget deja de pedir que marques un coche que estás conduciendo.

## Notas

- Sin cambios de dominio, ni de detección, ni de strings → no toca `PARKING-DETECTION.md`.
- La premisa "el teléfono va en el coche" es la misma que asume la detección entera; si algún día
  deja de valer (viaje seguido desde otro dispositivo), la fuente de la fila 2 de D1 se sustituye en
  UN sitio (`livePlaceLine`), que es exactamente el motivo de centralizarla.
