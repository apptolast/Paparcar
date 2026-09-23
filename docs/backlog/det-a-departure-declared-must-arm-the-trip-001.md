# DET-A-DEPARTURE-DECLARED-MUST-ARM-THE-TRIP-001 · «Me voy» promete detectar el próximo aparcamiento y no arma nada

**Estado:** ✅ Done — en master

## Problema

El diálogo de liberación enseña, bajo el botón **«Me voy»**:

> **Empezaremos a detectar tu próximo aparcamiento.** (`home_release_dialog_detection_active`)

Y no empieza nada. Verificado en master `627df0386`:

- `HomeViewModel.releaseParking()` (`HomeViewModel.kt:1386`) hace dos cosas —
  `declareActiveVehicle` (condicional) y `releaseSession`— y **nunca** llama a
  `manualParkingDetection.start()`.
- Ese `start()` tiene **un único call site en toda la app**: `startDrivingDetection`
  (`HomeViewModel.kt:1662`), el botón «Voy conduciendo».
- Tras el release la sesión queda `isActive = false` y la geocerca se retira, así que ningún
  nominador automático puede armar después:
  - `AR_VEHICLE_ENTER` → `EvaluateArEnterArmUseCase` exige `session?.geofenceId`; sin sesión
    devuelve `NoSession` y no arma.
  - `GEOFENCE_EXIT` → la valla que habría disparado es justo la que se acaba de borrar.
  - `SENTRY_WAKE` → `CoordinatorDetectionService.kt:462`, literal:
    `⊘ SENTRY_WAKE — no parked session; standing down`.
  - `ARRIVAL_HANDOFF` → sale del safety net, que reconcilia sesiones **abiertas**.

Neto: **un coche sin Bluetooth queda ciego hasta que el usuario vuelva a marcar a mano**. Un coche
con MAC emparejada queda cubierto, pero no por esta vía: su carril BT caza el disconnect sin
necesitar sesión ni valla.

Detalle que lo remata: `declareActiveVehicle` crea la valla del coche entrante y `releaseSession`,
dos líneas después, la borra. Es el mismo coche.

## Doctrina violada

- **El copy no promete lo que el código no hace.** Es el mismo defecto que
  [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001] y [ISSUE #121]: *una afirmación en un comentario —o en
  una frase de UI— no se ejecuta*. Aquí la frase lleva escrita desde que existe el diálogo.
- **Fallo asimétrico**: el resultado es un falso NEGATIVO garantizado (aparcamiento perdido con
  datos disponibles), que el contrato de detección clasifica como bug nuestro.
- **Sistemas, no parches**: la asimetría estaba a la vista y nadie la leyó, porque las dos mitades de
  «qué significa declarar una salida» viven en sitios distintos:

  ```
  startDrivingDetection → declareActiveVehicle + manualParkingDetection.start()
  releaseParking        → declareActiveVehicle + …
  ```

  Las dos afirman lo mismo físicamente: *estoy arrancando un viaje*.

## Señales / datos disponibles

Todo lo que hace falta está ya en el estado, en el mismo sitio donde el diálogo elige su nota:

| Señal | Dónde | Quién la usa hoy |
|---|---|---|
| `reason.isDeparture` | `ParkingReleaseReason` | `shouldDeclareActiveOnRelease` |
| `vehicle.bluetoothDeviceId != null` | `HomeState.vehicles` | `shouldDeclareActiveOnRelease` |
| `!detectionUiState.isDetectionStopped` | `HomeState.detectionUiState` | la nota del diálogo (`HomeScreen.kt:507`) |

## Diseño

**El invariante: una salida declarada arma el viaje que empieza — salvo que otro carril ya lo
vigile, o que no haya detección que armar.**

Vive en `VehicleFenceOwnershipPolicy`, pegado a `shouldDeclareActiveOnRelease`, como
`shouldArmTripOnRelease(reason, releasedVehicleIsBtPaired, detectionAvailable)`. No es un fichero
nuevo: ese objeto **ya es** el hogar de «qué implica la identidad de un coche» —aloja
`mayNominateDetection`, que es literalmente una decisión de armado— y las dos consecuencias de un
release tienen que leerse juntas o vuelven a divergir.

Tres condiciones, y las dos primeras son las MISMAS que ya veta la declaración de coche activo:

1. **Tiene que ser una salida.** Borrar un registro dice que el aparcamiento nunca ocurrió, no que
   empiece un viaje.
2. **El coche no puede tener MAC.** Un coche emparejado pertenece al carril BT, que caza su
   disconnect sin sesión ni valla. Armar el Coordinator además sería peor que redundante: por la
   condición 2 de `shouldDeclareActiveOnRelease` ese coche **no** pasa a ser el activo, así que la
   sesión que armásemos se atribuiría al coche activo —otro— y plantaría el pin en el coche
   equivocado. Es exactamente el campo 2026-08-11 (la valla del Kamiq nominando 8 viajes del Focus)
   entrando por una puerta nueva. ⛔ Y mezclar carriles es lo que CLAUDE.md prohíbe.
3. **La detección tiene que poder correr** (`detectionAvailable`). Es la misma puerta que el diálogo
   usa para elegir su nota: si no se puede detectar, la copia ya dice otra cosa y armar sería
   prometer dos veces.

El armado entra por el trigger `MANUAL`, **exento del strategy gate**
[DET-STRATEGY-GATE-001] — correcto aquí: el tap es intención humana explícita, igual que «Voy
conduciendo». La condición 2 hace que la exención nunca se ejerza sobre un coche BT.

**Orden**: se arma en el `onSuccess` del release, no antes. Con la escritura ya posada la sesión y
la valla han desaparecido, así que el Coordinator arranca limpio, sin un ancla que es justo la plaza
que se acaba de dejar. Y sólo se arma si el release de verdad ocurrió: una promesa que no se cumple
es la que abre este ticket.

### La segunda frase del mismo `when`

El diálogo tiene tres notas y la de en medio miente por el mismo motivo:

```
!vehicleIsActive && vehicleName != null → «%1$s pasará a ser tu coche activo, así que detectaremos…»
```

`vehicleIsActive` se lee del vehículo, no de la política — así que con un coche BT no activo el
diálogo anuncia una declaración que `shouldDeclareActiveOnRelease` veta. Se corrige leyendo la misma
política, con el precedente que este mismo diálogo ya tiene tres líneas más arriba
(`SpotReleasePolicy.privateZoneAt` en `HomeScreen.kt:496`, puesto ahí para que *«el diálogo no pueda
prometer una plaza que la política está a punto de retirar»*).

## Criterio de éxito

- `HomeViewModelTest`: tras `ReleaseParking(DEPARTURE)` sobre un coche sin MAC y con detección
  disponible, `FakeManualParkingDetection.startCallCount == 1`.
- …y `== 0` en los tres vetos: `RECORD_DELETED`, coche con `bluetoothDeviceId`, y
  `detectionUiState` parada.
- …y `== 0` cuando el release falla.
- `VehicleFenceOwnershipPolicyTest`: la tabla de verdad de `shouldArmTripOnRelease`.
- Campo: aparcar el Focus, «Me voy», conducir a otro sitio y aparcar → el pin sale sin tocar nada.

## Consumidores auditados

`grep` de todo lo que cierra una sesión activa, clasificado:

| Camino | ¿Es una salida declarada? | Veredicto |
|---|---|---|
| `HomeViewModel.releaseParking(DEPARTURE)` — «Me voy» del diálogo | ✅ con el móvil en la mano | **el bug** → arma |
| `HomeViewModel.releaseParking(RECORD_DELETED)` — «Borrar registro» del peek | ❌ dice que nunca ocurrió | cerrado por `isDeparture` |
| `RevertParkingUseCase` — «No, cancelar» de la notificación post-save | ❌ el pin era falso | exento, misma razón: no es una salida. Su propio KDoc ya lo dice: *«si la sesión nunca fue real, el siguiente IN_VEHICLE_ENTER repoblará el bus»* |
| `handleWatchdogDeparture` — «Ya me fui» del aviso *¿Sigues aparcado?* | ✅ el HECHO, ❌ la HORA | **exento con razón**: ese aviso salta porque nadie vio el EXIT y puede pasar horas en la bandeja. El viaje ya terminó y el usuario está en otro sitio — armar ahí encendería el GPS para un viaje que no existe. Es la MISMA razón por la que ese camino tampoco publica plaza [DET-WATCHDOG-DEPARTURE-KNOWS-NO-HOUR-001]. Su copy tampoco promete detectar nada |
| `ProcessConfirmedDepartureUseCase` vía `GEOFENCE_EXIT` / detección | ✅ pero automática | cubierto por convergencia: la sesión Coordinator que cierra la salida **es** la que la detectó; el re-armado lo gobierna [DET-AR-REARM-001] |

`grep` de `manualParkingDetection.start()`: dos call sites tras el fix (`startDrivingDetection`,
`releaseParking`), ambos con intención humana explícita y ambos en commonMain — el lado iOS entra
gratis por `IosManualParkingDetectionImpl.start()` → `controller.startTrackingManual()`.

## Lo que NO entra

- La valla que `declareActiveVehicle` crea y `releaseSession` borra dos líneas después. Es
  desperdicio, no un bug: la valla del coche que se va DEBE desaparecer. No se toca.
- Que un coche con MAC y el Bluetooth del móvil apagado se quede sin ningún carril. Eso es
  territorio de `ParkingStrategyResolver` y del follow-up abierto
  [DET-BT-IS-A-SENSOR-NOT-AN-OWNER-001], no de esta puerta.

## Resultado

**Suite: 3.742 tests, 0 fallos** (`./gradlew testAll`), más `:app:compileProdDebugKotlin` +
`:app:compileMockDebugKotlin` y `:shared:compileKotlinIosSimulatorArm64 --rerun-tasks` (el fix vive
entero en `commonMain`, así que el lado iOS entra por el mismo puerto `ManualParkingDetection`; no
hay hueco que escribir).

**Guard falsificado**, no sólo verde: con la línea del armado neutralizada
(`if (false && armsTheTrip) …`), `should_arm_the_trip_when_the_user_declares_a_departure` FALLA.
Restaurada, pasa.

Tests nuevos:

- `VehicleFenceOwnershipPolicyTest` ×5 — la tabla de verdad, más
  `should_gate_arming_on_the_same_two_conditions_as_the_identity_claim`, que afirma la simetría con
  `shouldDeclareActiveOnRelease` como invariante y no como coincidencia: si alguien toca una mitad
  sin la otra, ese test lo dice.
- `HomeViewModelTest` ×5 — arma en la salida; no arma al borrar registro, ni con coche BT, ni con la
  detección parada, ni cuando la escritura falla.

**Sin cobertura automática**: la nota del diálogo. Es copy dentro de un `@Composable` y no hay
`ScreenGroup` de este diálogo en la galería (tampoco tenía `*Previews.kt`), así que la sostienen el
compilador y la tabla de verdad de la política. No se añade estado ni variante nuevos — las tres
notas ya existían; lo que cambia es CUÁL sale para un coche BT no activo.

**Sin cambios en**: strings (ninguna key nueva), `paparcar.com` (no se toca un límite del plan free
ni una feature que la web nombre — la detección ya se anunciaba y sigue diciendo lo mismo), Dev
Catalog, `detectionPath` / `armEvidence` (no hay camino de CONFIRMACIÓN nuevo: lo que se añade es un
ARMADO, y queda registrado donde ya se registran los armados, en el `TriggerLedger` como `MANUAL`).

## Pendiente

- ⏳ **Campo**: aparcar el coche sin BT, «Me voy», conducir y aparcar en otro sitio → el pin tiene
  que salir sin tocar nada. Es el testigo que ningún test puede dar.
- ⏳ **En mano**: la nota del diálogo para un coche BT que no es el activo (dejaba de prometer
  «pasará a ser tu coche activo»). Requiere una flota con un coche BT emparejado y otro activo.
