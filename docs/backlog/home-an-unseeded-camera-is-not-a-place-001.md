# HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001 · una cámara que nadie ha colocado no es un sitio

**Estado:** ✅ Done — en master.

## Problema

Fernando (`8fhNJGct…`, Nothing A001, 1.1.0) salió a navegar desde Rota el 16-09 y su Kamiq se
guardó **en el Golfo de Guinea**: el peek decía «Aparcado hace <1m · a 4119,6 km de ti» sobre un
mapa todo azul. 4119,6 km es exactamente la distancia Rota → (0, 0).

Del parkdiag que él mismo mandó (informe `1789553325216`, mensaje: *«Ubicación 0,0»*):

```
10:31:54  ExitInfo: ⚰ previous process death (TOO MANY EMPTY PROCS) 36s before this start
10:42:24  Bootstrap.invoke  ← abre la app desde el aviso "Marcar aparcamiento"
10:42:55.514  Confirm: ▶ ConfirmParking.invoke reliability=1.0 spotType=AUTO_DETECTED
10:42:55.536  Confirm:   → saveNewParkingSession sessionId=b9b9dacc-…
10:42:55.725  OneFix: fix lat=36.5939614 lon=-6.245688 acc=6.13m src=fused age=1s   ← 200 ms TARDE
10:43:05.919  SafetyNet: geof=b9b9dacc: distancia ambigua (d=4119721m) → no decido nada
```

El móvil **sí sabía dónde estaba**: el primer fix llegó 200 ms después de guardar el pin.

**No es un caso aislado.** Barrido de los 20 uids de producción (`parkingHistory` + `zones` + `spots`,
197 docs de usuario): **5 pines en Null Island, los 5 suyos, los 5 con `detectionPath=nudge`** —
07-09, 13-09, 14-09, 15-09 y 16-09. Ninguno publicó plaza (`publishedSpot=false`), así que la
comunidad no se contaminó. El del 16 aparece hoy como `user_moved` en (0.00086, 0.00175) porque
intentó corregirlo dos veces arrastrando… dentro del propio Golfo de Guinea.

## Doctrina violada

*Fallo asimétrico: mejor falso negativo que falso positivo.* Ante la duda se pregunta, no se planta
un pin — y aquí se plantó uno a 4119 km sin que nada dudase. El `pinCoordinates()` es, según su
propio KDoc, «el único guard compartido por report / parking / zone»: existía, y dejó pasar la
semilla de emergencia del mapa como si fuera una respuesta del usuario.

El aviso ya estaba escrito en `HomeUiController.kt:209`:

> ⛔ **Sin esto Home abre en (0, 0), el Golfo de Guinea.** La semilla del mapa es
> `cameraTarget ?: initialCamera ?: userLocation ?: Coordinates(0,0)`…

`centerOnParkingWithoutLocation` y `centerOnZoneWithoutLocation` nacieron de ese aviso — pero las
dos, y las otras tres puertas de cámara, **se retiran cuando hay un pin colocándose**
(`pinPlacementActive`). O sea: el modo en el que la cámara ES la respuesta es justo el único donde
nadie la saca de (0,0).

## Señales / datos disponibles

- `PaparcarMapView.kt:2116` calcula la semilla y por tanto es el **único sitio que sabe de dónde
  salió la posición de la cámara**. Se calcula una vez (`remember`): si se siembra en el origen, se
  queda ahí hasta que llegue un `cameraTarget`.
- `HomeUiController.cameraLat/cameraLon` ya existen y los escribe **solo** `onCameraMoved`, que hoy
  se llama con todos los frames, incluidos los de la semilla.
- Todas las puertas deliberadas de cámara (`goToPlace`, `framePlaces`, `centerInitialFocus`,
  `centerOnParkingWithoutLocation`, `centerOnZoneWithoutLocation`, `frameTheAsk`,
  `refocusOnParkingArrival`, `followDriver`, `resumeDriverFollow`, y el efecto `MoveCameraTo`)
  desembocan en `setTarget`/`setBoundsTarget`.
- `SpotReportGate` ya distingue `NO_FIX` y **ya blindaba el report**: el modo plaza era el único
  inmune. Parking y zona no tenían nada.

## Diseño

**El invariante: una cámara que nadie ha colocado no es un sitio, y no se reporta como posición.**

Vive en el mapa, que es quien decide la semilla. `rememberCameraAnimationState` pasa a devolver,
junto a la posición, si la cámara **está posada en un sitio real**:

- la posó la semilla (`cameraTarget` · `initialCamera` · `userLocation` presentes en la primera
  composición), o
- llegó después cualquier `cameraTarget`, o
- está siguiendo al puck de conducción.

**Arrastrar NO posa**: un pan es relativo, hereda la legitimidad de dónde partió. Fernando arrastró
190 m *dentro* del Golfo de Guinea y seguía sin estar en ningún sitio. Por eso el gesto no entra en
la lista.

Mientras no está posada, **`onCameraMove` no se emite**. Con eso, sin tocar un solo consumidor:

- `pinCameraLat/Lon` siguen `null` → `pinCoordinates()` devuelve `null` → los **tres** confirms
  (report · parking · zona) quedan cerrados por el guard que ya existía;
- no se geocodifica (0,0) ni se piden plazas alrededor de (0,0) — hoy sí se hacía las dos cosas;
- `HomeUiController.cameraLat == null` pasa a significar exactamente «nadie ha colocado esta
  cámara», sin estado nuevo.

**Segunda mitad — que la cámara pueda salir de ahí.** `pinPlacementActive` retira el reencuadre
automático para proteger un pin arrastrado. *Un pin que no está en ningún sitio no tiene nada que
proteger*: el guard pasa a ser `pinPlacementActive && cameraLat != null`. Con esto, el fix que llegó
200 ms tarde habría llevado la cámara a El Puerto y el toque de Fernando habría caído en su calle.

**Y cuando no hay nada — ni permiso, ni fix, ni coche, ni zona.** La app ya dice que ahí manda el
buscador; faltaba la consecuencia visible. La semilla de emergencia deja de fingir calle: se siembra
**alejada** (`ZOOM_UNPLACED`), que se lee como «no sé dónde estás» y no como «tu coche está en el
mar». Y los peeks de parking y zona ganan la razón que el de plaza ya tenía: botón deshabilitado +
causa/consecuencia/remedio («No sabemos dónde estás» · «Busca una calle o activa la ubicación…»).
Buscar SÍ posa la cámara, así que el buscador es una salida de verdad, no un consejo.

## Criterio de éxito

- Arranque en frío sin fix, entrando directo en modo pin desde el aviso: el confirm está
  deshabilitado con su razón, y **no existe ningún toque que guarde un pin en (0,0)**.
- Llega el primer fix estando en modo pin **sin haber arrastrado**: la cámara vuela al usuario.
- Llega el primer fix estando en modo pin **después de arrastrar** desde un sitio real: la cámara
  NO se mueve (lo que [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001] protege sigue
  protegido).
- Tests: el guard del pin sin posar, y el de que un pan no posa.

## Consumidores auditados

`grep` de todo lo que consume un frame de cámara / `pinCoordinates()`:

| Consumidor | Veredicto |
|---|---|
| `confirmAddParking` (`HomeViewModel:1267`) | **Cerrado** — mordió aquí. 5 pines. |
| `confirmAddZone` (`HomeViewModel:1427`) | **Cerrado** — misma puerta, nunca disparó en campo. |
| `confirmReportSpot` (`HomeViewModel:1153`) | **Ya cubierto** por `SpotReportGate.NO_FIX`; ahora además no recibe el frame. |
| `geocodeCameraLocation` (`HomeViewModel:546`) | **Cerrado** por convergencia — dejaba de pedir la dirección de (0,0). |
| `scheduleNearbyRefresh` (`HomeViewModel:551`) | **Cerrado** por convergencia — pedía plazas en el Golfo de Guinea. |
| `spots.maybeRecenterOnPan` (`HomeViewModel:543`) | **Cerrado** por convergencia. |
| `HomeUiController.onCameraMoved` | **Cerrado** — es la vía; `cameraLat` pasa a ser la prueba de posado. |
| `HomeIntent.EnterAddZoneMode` (`HomeScreen:1236`) | **Cerrado** — ⚠️ hallazgo del barrido: `uiController.cameraLat ?: userGpsPoint?.latitude ?: 0.0`. El «+» de zonas metía el Golfo de Guinea **por la puerta**, saltándose todo lo anterior. `lat`/`lon` pasan a ser nullable: la ausencia viaja como ausencia. Único `?: 0.0` de todo `presentation/`. |
| `followDriver` / `resumeDriverFollow` | **Exento** — el puck es un sitio medido; posa. |
| Mapas `READ_ONLY` (historial, detalle de viaje) | **Exento** — ya no emitían (`isReadOnly`). |

## Lo hecho

- `PaparcarMapView` — `rememberCameraAnimationState` devuelve `MapCameraRender(position, isPlaced)`;
  `onCameraMove` no se emite sin posar; la semilla de emergencia abre en `ZOOM_UNPLACED` (mundo).
- `HomeUiController` — nace `pinToProtect = pinPlacementActive && cameraLat != null`, y sustituye a
  `pinPlacementActive` en las **5** puertas automáticas.
- `HomeIntent.EnterAddZoneMode` — `lat`/`lon` nullable; sin sitio no se emite `MoveCameraTo`.
- `HomePeekSlice.pinHasPlace` → `AddingParkingPeek` y `AddingZonePeek`: la razón sustituye al banner
  y los confirms se apagan (el de plaza ya lo hacía vía `SpotReportGate.NO_FIX`).
- Strings `home_pin_no_place_title` / `_sub` en los **9 locales**.
- Dev Catalog: las 8 variantes de pin existentes reciben cámara posada (no la ponían porque nadie la
  leía) + **2 variantes nuevas** para la cámara sin posar (parking y zona).
- **iOS viaja solo**: todo el cambio vive en `commonMain` (el mapa es el fork kmp-maps, el
  controller es Compose Multiplatform). No hay hueco de plataforma que documentar.

**Verificación:** `./gradlew testAll` → **3301 tests, 0 fallos**; los 5 guards de Konsist/strings
forzados con `--rerun-tasks` (paridad de locales 5, escapes 3, tipografía 5, color 8, imports 1);
`:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` + `:app:assembleMockDebug` OK.
6 tests nuevos/actualizados, comprobados uno a uno en los XML de resultados.

⏳ **Pendiente:** verlo en device.

## Fuera de alcance (tickets aparte)

- **`DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION`** — el MAC emparejado a su Kamiq no es un coche: 237
  enganches en 11 días, **mediana 17 s, máximo 57 s**, 235 descartados por `not a ride`. Cada
  desconexión aborta el coordinator (**36 sesiones**, varias justo tras `drive PROVEN`). Por eso
  vive del nudge: 32 de sus 42 confirmaciones son `reliability=1.0`.
- **`PARK-A-CORRECTION-MOVES-THE-PLACE-NOT-THE-CLOCK`** — `UpdateParkingLocationUseCase:60` estampa
  `timestamp = now`, mientras la misma hoja promete «Corregir mantiene el tiempo aparcado»
  (`home_add_parking_helper_secondary_edit`).
- **Safety-net sin techo** — `d=4119721 m` se clasificó como «distancia ambigua». Misma forma que el
  suelo-sin-techo de [DET-A-TRAIN-IS-NOT-A-CAR-001].
