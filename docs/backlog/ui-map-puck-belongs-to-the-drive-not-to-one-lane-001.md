# UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001 · el coche del mapa es del VIAJE, no del carril que lo detecta

**Estado:** ✅ Done · mergeado a master 2026-08-30 (squash) · implementado en
`feature/UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001-bt-drives-with-a-car`, abierto sobre
`db3a1f62` y rebasado sobre `0e3940bc` antes de cerrar. ⏳ **Sin ver en device**: ni los 44 dp ni un
viaje BT real (necesita el Kamiq).

## Problema

Reporte del user (30-08): *«para bt no convertimos el icono de location en coche, deberíamos de
hacerlo».* Confirmado, y no por un olvido de la UI: **el carril BT no tiene forma de encenderlo.**

El puck de coche (`LocationActiveMarker`) se dibuja cuando `HomeTripController` produce un
`DrivingPuck`, y eso pasa exactamente si `ObserveDetectionReadinessUseCase` emite `Monitoring`.
`Monitoring` se decide con `detectionRuntime.isRunning`, y `DetectionRuntimeState` lo escribe **sólo**
`CoordinatorDetectionService`:

| Escritor de `DetectionRuntimeState` | |
|---|---|
| `CoordinatorDetectionService` | `setRunning` / `setTrip` / `setPresence` |
| `CoordinatorParkingDetector` | `setPhase` vía `DetectionPhaseSink` |
| `bluetooth/` (los 4 ficheros) | **nada** |

El único fichero BT que lo toca es `BluetoothConnectionReceiver`, y **sólo lo lee** — y si arbitra,
lo que manda es `ACTION_BT_OVERRIDE` para que el Coordinator **aborte**. Como bajo estrategia
BLUETOOTH el Coordinator está suprimido por diseño (`ParkingStrategyResolver`: *"when BLUETOOTH wins,
Coordinator is suppressed"*), `isRunning` nunca sube. **Todo el viaje en el coche con MAC se hace con
el punto azul de localización.**

Prueba de que esto se pensó y nunca llegó a funcionar: `HomeTripController.monitoredVehicle` tiene
una rama `ParkingStrategy.BLUETOOTH` para elegir qué coche pintar — **código muerto**, porque para
llegar ahí hace falta un `Monitoring` que sólo emite el Coordinator.

La ironía del reparto: el carril que **más seguro** está de en qué coche vas (un MAC emparejado, no
una inferencia probabilística) es el único que no puede decirlo en pantalla.

## Doctrina violada

Ninguna de las tres de detección — aquí no se coloca nada. La que se rompe es de reparto:

> **El puck pertenece al HECHO de estar conduciendo, no al carril que lo detecta.** Que el estado que
> lo alimenta sea propiedad privada de una de las dos estrategias es un accidente de implementación,
> no una decisión de producto.

⛔ Y el corolario que marca **cómo** se arregla: los carriles no se mezclan. La solución NO es que BT
escriba `DetectionRuntimeState` — serían dos escritores sobre un objeto cuya semántica es *"la sesión
del Coordinator"*, y encima uno le manda abortar al otro. El sitio donde las dos verdades se juntan
sin tocarse es el que ya existe para eso: `ObserveDetectionReadinessUseCase`, *"single source of
truth for the Home detection banner"*, que ya combina flota + sesiones + permisos + runtime + ajustes.

## Señales / datos disponibles

- `ObserveDetectionReadinessUseCase` **ya calcula** `strategyResolver.strategyFor(vehicles)`, y esa
  función devuelve `BLUETOOTH` exactamente cuando *"CONNECTED to a paired car (ACL up)"*. La señal ya
  está dentro del caso de uso.
- ⚠️ **Pero nadie vuelve a preguntar.** `strategyFor` es una lectura puntual (lee
  `BtConnectionStore` por SharedPreferences); la readiness sólo se recomputa cuando emite alguna de
  sus 5 fuentes, y **el edge ACL no es ninguna de ellas**. Sin fuente reactiva, el coche aparecería
  por casualidad, cuando otra cosa emitiera.
- `BtConnectionStore` ya mantiene el conjunto de vehículos conectados en disco
  (`markConnected` / `markDisconnected` / `connectedVehicleIds`), escrito por el receiver de
  manifiesto — sobrevive a los OEM-kill.
- `DetectionPhase.Candidate` ya existe y la UI ya sabe pintarlo (coche congelado + punto del peatón).

## Diseño

### 1 · El gemelo reactivo de una pregunta que ya sabíamos contestar

`BluetoothScanner` gana `observeConnectedPairedCars(): Flow<List<BtConnection>>`, al lado del
`isConnectedToPairedCar(...)` puntual que ya tiene (lleva el sello de conexión, ver §2-ter). Android: listener de cambios sobre las prefs
`bt_identity` (las mismas que ya son la fuente de verdad); iOS: flujo vacío, no hay carril BT allí.

### 2 · Dos fuentes para "estoy siguiendo un viaje", una sola regla de precedencia

En `resolve(...)`, `followingTrip` deja de ser sólo del Coordinator. La segunda fuente es el coche
BT conectado, y se le aplica **exactamente la misma regla** que ya rige para el Coordinator
[DET-READY-TRIP-OVER-PARKED-001]: *un coche cuya sesión sigue viva no está conduciendo todavía*.

Consecuencia asumida y deliberada: al conectar con el coche aún aparcado, la pantalla sigue diciendo
"aparcado"; el coche aparece cuando la salida se procesa y su sesión se cierra. Es lo correcto —
mostrar "conduciendo" mientras el coche está demostrablemente aparcado es una afirmación que no
podemos respaldar, y es el mismo listón que se le exige al otro carril. **No se añade una excepción
para BT.**

### 2-bis · Quién sigue el viaje y QUÉ COCHE es son dos preguntas distintas

Corrección tras revisión del user (30-08): *«el puck corresponde a cada coche, no debería aparecer un
puck cualquiera; bt tiene el suyo y coordinator el suyo, debería salir el que corresponda».*

La primera versión resolvía el conflicto por **orden de las ramas** (`isRunning ->` primero), o sea
que con los dos carriles vivos ganaba el que **infiere** el coche sobre el que lo **conoce por MAC**.
Peor: la condición era `isRunning`, no `coordinatorFollowing`, así que hasta una sesión del
Coordinator cuyo coche sigue aparcado le robaba el puck a un viaje BT real.

> **El coche lo nombra la evidencia más fuerte, nunca quien hable primero.** Un MAC conectado NOMBRA
> el coche; el Coordinator lo INFIERE de una valla o de un embarque de AR.

Y no es una preferencia estética: cuando discrepan, **la app ya eligió** —
`EvaluateBtArbitrationUseCase` hace que un edge del coche emparejado SUPERSEDA una sesión viva del
Coordinator. Si el mapa pintara el coche del Coordinator estaría contradiciendo a su propia
detección. Además el glifo del puck es una **identidad** ("cuál de mis coches es éste"), así que
pintar el equivocado no es un desliz cosmético: es una afirmación falsa [UI-COLOR-DOCTRINE-001].

**El payload va con el coche.** `phase` y `departurePoint` describen el coche que sigue el
Coordinator. En cuanto BT nombra otro, son hechos ajenos: un `Candidate` extranjero congelaría este
puck donde paró **otro** coche, y un `departurePoint` extranjero dibujaría el origen de este viaje en
la acera de otro. La regla es «el payload es ajeno **sólo** si BT nombró un coche distinto», y no «si
BT nombró alguno» — porque un arme MANUAL no atribuye coche pero su fase sí es real y sí tiene que
congelar el puck. Esa distinción es la diferencia entre corregir el bug y crear una regresión en el
carril que ya funcionaba; tiene test propio.

### 2-ter · Más de un coche por BT: se identifica, no se adivina

Segunda pregunta del user (30-08): *«¿y qué pasa si tenemos más de un coche por bt? ¿sabríamos
identificarlo?»*. Hay que separar las dos capas, porque **no estaban igual**:

| Capa | ¿Identifica el coche con N MACs? |
|---|---|
| **Detección** | ✅ **Siempre**. `BluetoothConnectionReceiver` resuelve `getVehicleByBluetoothDeviceId(deviceAddress)` en cada evento, así que el pin lo coloca el coche cuyo MAC se desconectó |
| **Mi lectura del puck** | ❌ `vehicles.firstOrNull { it.id in connectedIds }` — colapsaba el conjunto **por orden del repositorio**. Una adivinanza disfrazada de función |

Dos enlaces ACL pueden estar arriba a la vez (aparcas la furgo, andas hasta el coche, y la unidad de
la furgo sigue alimentada un rato). Regla:

> **El coche en el que te has metido el ÚLTIMO es el coche en el que vas.**

El dato ya estaba en disco: el receiver escribe `recordConnected` (sello por vehículo) y
`markConnected` (conjunto) en la misma llamada — sólo había que leerlos juntos. `BluetoothScanner`
pasa a exponer `observeConnectedPairedCars(): Flow<List<BtConnection>>` con su `connectedAtMs`.

⛔ **Y si la recencia no puede decidir —empate, o un enlace sin sello junto a otros— no se nombra a
NADIE.** Un enlace sin sello podría ser el más reciente, así que no pierde el ranking: lo invalida.
El glifo del puck es una identidad, o sea que el coche equivocado es una afirmación falsa mientras
que ningún coche es sólo menos información. Un único enlace no necesita orden y se devuelve tal cual,
tenga sello o no.

### 3 · La rama muerta revive

`Monitoring.departingVehicleId` pasa a llevar el coche BT conectado, así que el puck pinta el coche
correcto sin adivinar. La rama `BLUETOOTH` de `monitoredVehicle` se queda como fallback y **por
primera vez es alcanzable**.

### 4 · El tamaño (rider explícito del user)

`LOC_ACTIVE_CAR` 38 → **44 dp** y `LOC_ACTIVE_DIAM` 54 → **60 dp**. Se suben los dos: dejar la caja
en 54 con el coche a 44 convertiría el halo en un filo (proporción 0,70 → 0,81); subiendo ambos se
mantiene en 0,73.

### 5 · Lo que NO se hace

- **BT no publica fase `Candidate`.** Congelar el coche en el sitio donde paró mientras el detector
  BT busca el fix (hasta 90 s + 15 min de walk-away) exigiría que el detector publicara estado en
  vuelo. Hoy, al desconectar, el puck se apaga y vuelve el punto; cuando el pin se confirma entra
  `Parked`, que ya tiene su marcador. Es honesto y es lo que ya pasa. → follow-up si en campo se ve
  feo.
- **BT no escribe `DetectionRuntimeState`** (ver doctrina).

## Criterio de éxito

- BT conectado a un coche sin sesión activa → `Monitoring(strategy = BLUETOOTH,
  departingVehicleId = <ese coche>, phase = Driving)`.
- BT conectado a un coche **con** sesión activa → sigue `Parked` (no se inventa un viaje).
- BT conectado al coche A mientras el coche B está aparcado → `Monitoring` de A (la sesión ajena no
  enmascara el viaje — es el caso que [DET-READY-TRIP-OVER-PARKED-001] abrió para el Coordinator).
- Fase rancia: `phase = Candidate` en el runtime del Coordinator + viaje BT → `Driving`.
- Desconectar → deja de seguirse el viaje.
- **Verificación por falsación**: sin el flujo reactivo, el test de "conecto → Monitoring" tiene que
  quedarse en rojo (demuestra que la fuente puntual no bastaba).
- Visual: preview de `LocationActiveMarker` — hoy es **el único marcador del mapa sin ninguna** — y
  switch del carril BT en el Dev Catalog, que es la única forma de ver los 44 dp sin el Kamiq.

## Consumidores auditados

| Sitio | Qué asume | Clasificación |
|---|---|---|
| `ObserveDetectionReadinessUseCase` | `Monitoring` ⇔ Coordinator corriendo | ⛔ **donde mordió** — se cierra |
| `HomeTripController` (puck) | consume `Monitoring`; su rama BT era inalcanzable | ✅ cubierto por convergencia — revive sola |
| Banner de detección de Home | mismo `DetectionReadiness` | ⚠️ **cambia también**: en viaje BT dirá "siguiendo tu viaje" en vez de "listo". Es lo correcto y era el bug: el banner mentía sobre un carril activo |
| `ParkingStrategyResolver` | quién ARMA la detección | ✅ exento — sin tocar; sólo se le lee la respuesta que ya daba |
| `DetectionRuntimeState` y sus escritores | propiedad del Coordinator | ✅ exento con razón — sigue siéndolo |
| `EvaluateBtArbitrationUseCase` | BT supersede al Coordinator | ✅ exento — arbitra detección, no pintura |
| `FakeBluetoothScanner` ×2 (mock + tests) | implementan la interfaz | ⛔ se amplían |

## Estado de la implementación

| Fichero | Cambio |
|---|---|
| `domain/bluetooth/BluetoothScanner.kt` | `observeConnectedPairedCars(): Flow<List<BtConnection>>` + el tipo `BtConnection` (id + sello de conexión) |
| `bluetooth/BtConnectionStore.kt` | `observeConnected` — listener de las prefs que ya son la fuente de verdad, emparejando el conjunto con los sellos por vehículo que ya guardaba |
| `bluetooth/AndroidBluetoothScanner.kt` · `iosMain/IosBluetoothScanner.kt` | implementaciones (iOS: vacío, no hay carril BT) |
| `domain/usecase/detection/ObserveDetectionReadinessUseCase.kt` | `LaneSnapshot` (muere el `Triple`) · `btFollowing` como 2ª fuente · el coche lo nombra la evidencia más fuerte (`followedVehicleId`) · el payload del Coordinator sólo vale para SU coche (`payloadIsForeign`) · `carYouAreIn` desempata N enlaces por recencia y calla si no puede |
| `di/DetectionModule.kt` | `bluetoothScanner = get()` |
| `ui/components/PaparcarMapMarkers.kt` | `LOC_ACTIVE_CAR` 38 → 44 dp · `LOC_ACTIVE_DIAM` 54 → 60 dp |
| `androidMain/.../PaparcarMapMarkersPreviews.kt` | preview del puck (4 rumbos × 3 carrocerías) — no tenía ninguna |
| `fakes/.../FakeOtherDataSources.kt` · `FakeVehicleRepository.kt` | fake scanner scenario-aware + `ACTIVE_VEHICLE_ID` |
| `commonTest/fakes/FakeBluetoothScanner.kt` | respaldado por `MutableStateFlow` (mover el edge a media colección) |
| `app/src/mock/.../MockModule.kt` · `DevCatalogScreen.kt` | scanner con escenario + switch renombrado a lo que de verdad hace |
| `commonTest/.../ObserveDetectionReadinessUseCaseTest.kt` · `HomeViewModelTest.kt` | 11 tests nuevos (5 del carril + 3 del conflicto entre carriles + 3 de multi-coche) + firma |
| `docs/detection/PARKING-DETECTION.md` | entrada de §2 |

**Suite: 1.808 tests, 0 fallos.** `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` OK.

### Hallazgo colateral — el mock resolvía BLUETOOTH SIEMPRE

`FakeBluetoothScanner.isConnectedToPairedCar` devolvía `pairedVehicleIds.isNotEmpty()`, con el
comentario *"the mock only assigns a bluetoothDeviceId when the BT scenario is on"* — **falso**:
`mock_vehicle_002` y `_004` llevan MAC cableada. O sea que el mock resolvía `BLUETOOTH`
permanentemente y contaba las historias del Coordinator bajo la estrategia equivocada. Inofensivo
mientras la estrategia sólo elegía copy; con un coche conectado moviendo la superficie de viaje
habría dejado un puck de coche fijo en Home. Corregido: la conexión sigue la palanca del Dev
Catalog, y de paso el mock por fin modela la flota *emparejada pero no conectada* que es justo el
caso para el que existe [DET-BT-CONNECTED-NOT-PAIRED-001].

### Criterio de éxito — estado

- ✅ conectado sin sesión → `Monitoring(BLUETOOTH, departingVehicleId, Driving)` · ✅ conectado con
  su sesión viva → `Parked` · ✅ conectado con OTRO coche aparcado → `Monitoring` · ✅ fase rancia
  `Candidate` → `Driving` · ✅ desconectar → se deja de seguir
- ✅ **conflicto entre carriles**: Coordinator siguiendo el coche A + BT conectado al B → se pinta
  **B**, con `phase = Driving` y **sin** el `departurePoint` de A · ✅ los dos apuntando al MISMO
  coche → sobrevive el `Candidate` del Coordinator (no hay conflicto que arbitrar) · ✅ arme manual
  sin BT → su `Candidate` intacto (guarda de regresión del carril que ya funcionaba)
- ✅ **multi-coche**: dos enlaces vivos → gana el conectado más tarde · uno de los dos sin sello →
  no se nombra a nadie · enlace único sin sello → ése (no hay nada que ordenar)
- ✅ **falsación ×3**: fuente convertida en snapshot (`flowOf(value)`) → cae el test del edge y sólo
  ése; precedencia devuelta al Coordinator (`trip?.departingVehicleId ?: btVehicleId`) → cae el del
  conflicto y sólo ése; `carYouAreIn` degradado a `firstOrNull` → caen los DOS de multi-coche y sólo
  ésos. Las tres restauradas.
- ✅ preview del puck y palanca del Dev Catalog: los 44 dp se pueden ver sin el Kamiq.
- ⏳ **En mano, sin ver**: falta mirar el tamaño nuevo en device y el viaje BT real (necesita el
  Kamiq, igual que [DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001]).

### Sin impacto en

Strings (ninguno nuevo: `DetectionStory.drivingStory` ya tenía su `viaBluetooth`, tan inalcanzable
como la rama de `monitoredVehicle`), `detectionPath`, esquema de datos.
