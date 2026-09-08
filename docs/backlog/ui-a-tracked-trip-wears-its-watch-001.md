# UI-A-TRACKED-TRIP-WEARS-ITS-WATCH-001 · El viaje que se está siguiendo lleva el color de CÓMO se le sigue; el azul de movimiento se queda para lo que no es tuyo

**Estado:** ✅ Done — en master (squash 08-09-2026)

## Problema

Sobre el mapa hay **dos azules casi iguales que significan cosas distintas**:

- `papCarBlue` — la identidad de un coche vigilado por **Bluetooth** (`PapCarBlueLight #0057CA`).
- `PapLiveMap #2F6BFF` — "esto se mueve": traza del viaje, punto de origen, halo del puck, FAB que
  sigue al coche… y también el pin de una plaza **ajena** a la que otro usuario va en camino.

Medido con la misma metodología que `UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001` (ΔE00, umbral ~15
para dos significados que conviven en la misma superficie):

| Par | ΔE00 | |
|---|---|---|
| `PapCarBlueLight #0057CA` vs `PapLiveMap #2F6BFF` | **10,0** | ⛔ por debajo del umbral, **ya hoy** |
| `PapCarBlueDark #5B9EFF` vs `PapLiveMap #2F6BFF` | 16,1 | al límite |

La primera fila **no la descubre este ticket**: está escrita como defecto abierto en
`UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001` §5 — *«coche BT y traza de viaje, ambos en el mapa»*— y
se quedó sin arreglar porque el arreglo obvio (mover un hex) no resolvía la pregunta de fondo: **por
qué el viaje de tu coche tiene un color que no dice nada de tu coche.**

Palabras del user: *«el azul de ruta confunde con el azul de BT, mejor que tenga identidad de tipo
de detección»*.

## Doctrina que cambia (no que se viola)

Esto **modifica** `UI-COLOR-DOCTRINE-001`, no lo incumple. La regla vigente dice: *«`PapLiveMap`
solo para mapa en movimiento»*, y de ella cuelga una decisión explícita en
`PaparcarMapMarkers.kt` — *«el en-route es la MISMA semántica que el coche conduciendo y la traza,
así que lee el compartido `PapColor.live`»*— y otra en `ui-fab-car-identity-001.md`, que dejó el FAB
de `MyLocation` **exento a propósito** (*«no es identidad de vehículo, es 'el mapa sigue al coche en
movimiento'»*).

La regla nueva parte ese "movimiento" en dos, que es lo que nunca estuvo separado:

> **Un coche TUYO al que se le está siguiendo la pista lleva el color de su método de vigilancia.
> `PapLiveMap` se queda para el movimiento que NO es tuyo.**

Con eso, el par de ΔE00 10,0 desaparece por construcción: en un viaje BT, la traza **es** el azul de
identidad BT — dejan de ser dos azules con dos significados y pasan a ser uno con uno.

## Señales / datos disponibles

- `vehicleIdentityColor(watch)` (`ui/theme/VehicleIdentity.kt`) — el resolver único que ya visten el
  chip, la card, la ficha y el peek. Asistido → `papWatchGreen`; BT → `papCarBlue`; sin vigilar →
  gris.
- `HomeState.drivingMeta.vehicleId` + `vehicles` → el `VehicleMonitoringStatus` del coche que se
  está siguiendo. Mismo patrón que `HomeFabsSlice.selectedParkingWatch`, que ya existe.
- En el histórico, la sesión ya sabe su `vehicleId`, así que una ruta guardada puede vestir la
  identidad del coche que la recorrió.

### El verde de identidad NO colisiona con las plazas

Era mi objeción y **estaba mal**: razoné con el verde de marca, no con el token real. La identidad
de un coche asistido es `papWatchGreen` (un teal), que existe precisamente porque
`UI-COLOR-EVERY-HUE-EARNS-ITS-MEANING-001` lo separó del verde de marca. Medido:

| Par | ΔE00 | |
|---|---|---|
| `PapWatchGreen #0FBF9A` vs plaza fresca `PapSpotFresh #8FE83C` | 24,9 | ✅ |
| `PapWatchGreen #0FBF9A` vs puck de plaza `PapSpotFreshPuck #4FA80A` | 21,7 | ✅ |
| `PapWatchGreenLight #05876D` vs plaza fresca claro `PapSpotFreshLight #5FBF1F` | 27,7 | ✅ |
| `PapWatchGreenLight #05876D` vs plaza deep `PapSpotFreshDeep #398701` | 19,7 | ✅ |

Los cuatro por encima del umbral. La rampa de frescura sigue siendo exclusiva de las plazas.

## Diseño

### D1 · Qué se tiñe de identidad y qué NO

`PapColor.live` aparece en **9 usos**. No todos son tu coche, y ahí está la línea:

| Uso | Fichero | Sujeto | Veredicto |
|---|---|---|---|
| Traza del viaje — segmentos medidos / inferidos / matched / crudos (3 usos) | `PaparcarMapView.kt:1466,1474,1482` | tu coche | **identidad** |
| Punto de origen del tag (`originDot`) | `PaparcarMapMarkers.kt:279` | tu coche | **identidad** |
| Halo del puck de conducción (`LocationActiveMarker`) | `:434` | tu coche | **identidad** |
| Punto de salida (`DepartureDotMarker`) | `:469` | tu coche | **identidad** |
| FAB `MyLocation` mientras sigue al coche | `HomeMapFab.kt:95` | tu coche | **identidad** |
| Tier en-route de una plaza + su dot + su puck (3 usos) | `PaparcarMapMarkers.kt:772,911,914,920` | **otro usuario** yendo a una plaza | **se queda `PapLiveMap`** |

⚠️ **Decisión que se aparta de la letra del encargo** (el user dijo "esos 8 sitios"): los pines de
plaza en-route **no** se tiñen. Ahí el que se mueve es un desconocido hacia una plaza de la
comunidad; no hay ningún coche tuyo ni ninguna detección tuya que describir, y teñirlos con la
identidad de TU coche diría algo falso. Al quedarse ellos con el azul de movimiento y tu viaje pasar
a identidad, el azul deja de estar sobrecargado — que es justo el objetivo. Si el user lo quiere
igualmente, es una línea.

Sin vigilancia (`VehicleWatch.Off`) la identidad es gris, que sobre tiles no se ve: en ese caso la
traza cae de vuelta a `PapColor.live`. En la práctica un viaje seguido implica Asistido o BT — el
fallback existe para no depender de esa afirmación.

### D2 · Fontanería

Un parámetro nuevo, del mismo tipo que ya viaja por toda la app:

- `PaparcarMapView(tripWatch: VehicleWatch? = null)` → resuelve `tripAccent` con el resolver único y
  lo baja a la traza y a los tres marcadores. Se resuelve en el cuerpo composable (los polilines se
  construyen en un `LaunchedEffect`, donde no se puede llamar a `@Composable`) y entra en el
  `snapshotFlow` del muestreo, para que un cambio de vigilancia a mitad de viaje repinte.
- `HomeMapSlice.drivingWatch` y `HomeFabsSlice.drivingWatch` — derivados de
  `drivingMeta.vehicleId` + `vehicles`, exactamente como el `selectedParkingWatch` que ya existe.
- **Histórico**: `ParkingHistoryDetailScreen` también pinta rutas. Una ruta guardada viste la
  identidad del coche que la recorrió — si no, la misma ruta tendría un color en vivo y otro al
  recordarla.

### D3 · Documentación de doctrina (parte del ticket, no un extra)

- `docs/design/COLOR-SYSTEM.md` — la fila de `PapLiveMap` pasa a decir "movimiento que **no es
  tuyo**" y se añade la historia del viaje propio.
- `CLAUDE.md` — el bullet *«`PapLiveMap` solo para mapa en movimiento»* se reescribe.
- El comentario de `SpotPalette` que declara la equivalencia *en-route ≡ coche conduciendo* deja de
  ser cierto y se corrige donde está.
- `ColorGuardrailTest` — revisar que su PALETTE siga declarando el significado correcto.

## Criterio de éxito

- Viaje con coche BT → traza, origen, halo del puck y FAB en `papCarBlue`. **Un solo azul en pantalla
  con un solo significado.**
- Viaje con coche asistido → los mismos cuatro en `papWatchGreen`, distinguible de cualquier plaza
  (medido arriba, ΔE00 ≥ 19,7).
- Plaza a la que va otro usuario → sigue azul `PapLiveMap`, no cambia.
- Ruta guardada en el histórico → el color del coche que la hizo.
- Vehículo sin vigilancia → traza azul de movimiento, nunca gris invisible.
- `ColorGuardrailTest` verde **con `--rerun-tasks`** (un cambio de color no mueve el bytecode).
- ⏳ En device: `papWatchGreenLight #05876D` sobre tiles de terreno claro — es el peor caso de
  contraste y no se decide en la maqueta.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `PaparcarMapView` (traza) | **cerrado** — D1 |
| `PaparcarMapMarkers`: origen, halo del puck, punto de salida | **cerrado** — D1 |
| `PaparcarMapMarkers`: tier en-route, dot y puck de plaza | **exento por diseño** — no es tu coche |
| `HomeMapFab` `MyLocation` | **cerrado** — deja de estar exento; su exención se escribió cuando identidad = verde de marca |
| `HomeMapFab` FAB del coche aparcado | **ya cumple** — usa `vehicleIdentityColor` desde `UI-FAB-CAR-IDENTITY-001` |
| `ParkingHistoryDetailScreen` | **cerrado** — D2 |
| Chip / card / peek / ficha | **ya cumplen** — misma identidad, otro sitio |
| Punto de posición nativo del mapa (la persona) | **exento** — no es un vehículo |

## Verificación

- `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` ✅
- `:shared:testDebugUnitTest` ✅ suite completa verde.
- `ColorGuardrailTest` · `TypographyGuardrailTest` · `DividerGuardrailTest` · `MapMarkerIdsTest`
  relanzados con `--rerun-tasks` ✅ — obligatorio aquí: un cambio de color no mueve el bytecode y
  Gradle los daría por UP-TO-DATE sin ejecutarlos.
- **El caché de bitmaps era el riesgo real, no el color.** kmpmaps cachea el raster por `contentId`,
  así que teñir sin re-keyar habría dejado el halo y los puntos con el color anterior hasta que
  cambiara otra cosa. La vigilancia entra en la clave de los tres marcadores
  (`departureContentId`, `arrivalContentId`, `locationActiveContentId`), en las `remember(...)` que
  construyen la lista y el puck, y en el corpus de `MapMarkerIdsTest` — que además se amplió porque
  seguía ejercitando las claves viejas y habría pasado sin mirar lo nuevo.
  [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]
- `VehicleWatch.Off` se deja **fuera** del bucle del corpus a propósito: su clave ES la de
  movimiento, y listarlo emitiría un duplicado que el chequeo de distinción leería como colisión.
- ⏳ En device: `papWatchGreenLight #05876D` sobre tiles claros (peor caso de contraste) y un viaje
  BT real para ver el azul único.

## ⚠️ Lo que la medición dice después del cambio: en CLARO la colisión se muda, no muere

Verificado en el emulador muestreando el píxel (no a ojo): traza `#5B9EFF` = `papCarBlue` dark;
pin en-route de la comunidad `#2F6BFF` = `PapLiveMap`, intacto. El objetivo del ticket se cumple.

Pero el par de ΔE00 10,0 que veníamos a matar **reaparece en otro sitio en tema claro**, porque
`papCarBlue` es theme-aware y `PapLiveMap` es fijo:

| Vecinos en el mapa | Tema | ΔE00 | |
|---|---|---|---|
| Traza de un viaje **BT** vs pin en-route de la comunidad | oscuro (`#5B9EFF` vs `#2F6BFF`) | 16,1 | justo por encima del umbral |
| Traza de un viaje **BT** vs pin en-route de la comunidad | **claro** (`#0057CA` vs `#2F6BFF`) | **10,0** | ⛔ por debajo |

Antes del cambio ese 10,0 estaba entre la traza y el TAG del coche BT; ahora está entre la traza y
el pin de la comunidad. En balance neto: en oscuro mejora (16,1), en claro **se queda igual de
apretado, con otros dos vecinos**. Y lo que sí se gana en los dos temas es que el par apretado ya no
es una CONTRADICCIÓN (dos significados) sino dos cosas que de verdad son distintas — tu viaje y el
de otro — con formas distintas (línea gruesa vs pin con "P").

**No se abre tarea por esto** (decisión del user, 08-09): el en-route es una feature de comunidad y
hoy no hay usuarios, así que ese vecindario no le quita atención a nadie. La medición queda
aparcada en `docs/backlog/ui-en-route-blue-sits-close-to-bt-blue-001.md` 🧊 para cuando la feature
importe.

## Notas

- No toca dominio ni detección: lee `monitoringStatus()`, que ya existe. No toca
  `PARKING-DETECTION.md`.
- Hermano de `UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001` (el viaje dice DÓNDE va): aquel le dio al viaje su
  sitio, este le da su identidad. Misma pregunta debajo — *un viaje vivo es un sujeto con derecho a
  nombre y a color*.
