# DET-BT-CONNECTED-NOT-PAIRED-001 — La estrategia BT debe mandar solo si el coche BT está CONECTADO, no meramente emparejado

> Estado: 📋 SPEC (sin empezar) · Creado 2026-08-08 · Prioridad: ALTA (afecta la detección en sí, por encima del banner)
> Rama propuesta: `bugfix/DET-BT-CONNECTED-NOT-PAIRED-001-strategy-by-connection`

## Bug (field 08-08 Málaga, confirmado en device)
Con dos coches — **Kamiq (BT emparejado)** y **Focus (sin BT, activo)** — y el Bluetooth del móvil
encendido, `ParkingStrategyResolver.strategyFor()` resuelve **BLUETOOTH global** por el mero
emparejamiento del Kamiq (línea 82-85: `vehicles.any { isBtPairedAndParks() } && isBluetoothEnabled()`).
Consecuencia: conduces el **Focus**, pero:
1. El **Coordinator se suprime** (`coordinatorMayArm` = false para triggers automáticos bajo BLUETOOTH).
2. El BT vigila la desconexión **del Kamiq**, que nunca ocurre (no lo conduces).
3. → **La conducción del Focus NO se detecta nunca.**

Verificado en el Oppo: `bluetooth_on=1`, Kamiq entre los bonded, y el log repite hoy
`⊘ arm refused — strategy=BLUETOOTH owns detection [DET-STRATEGY-GATE-001]` con el Focus activo.
Es la causa dominante de los fallos de detección del Focus hoy — **independiente de la whitelist de
batería** ([[project_det_battery_whitelist_2026_08_08]]).

Era un diseño deliberado (`ARCH-MONITORING-002`: BT supersede para no atribuir viajes del Kamiq al
Focus), pero su contrapartida rompe el caso multi-coche (BT + no-BT). Decisión del user (08-08):
**es un bug, hay que arreglarlo.**

## Diseño (seguro — verificado el modelo)
**La detección de la desconexión NO depende del resolver:** `BluetoothConnectionReceiver` (receiver
de manifiesto) caza ACL_DISCONNECTED y resuelve el coche por su **MAC**, arrancando
`BluetoothDetectionService` con total independencia de `strategyFor()`. El resolver solo decide si el
**Coordinator** puede armar. Por tanto gatear la estrategia por conexión es seguro: el Kamiq se sigue
detectando por su receiver.

Cambio: **BLUETOOTH solo cuando el móvil está conectado AHORA a la MAC de un coche emparejado**
(no por emparejamiento). Resultado:
- Focus (no conectado al Kamiq) → **COORDINATOR** → detecta el Focus ✅
- Kamiq (conectado) → **BLUETOOTH**, Coordinator suprimido; receiver caza el aparcamiento ✅

### Guardarraíl obligatorio (no reintroducir ARCH-MONITORING-002)
Al aparcar el Kamiq y desconectarse, quedaría "no conectado" → el resolver querría COORDINATOR justo
en la **ventana de walk-away** en que el flujo BT está confirmando la plaza → riesgo de doble
detección / mala atribución. Solución: BLUETOOTH cuando **conectado _o_ desconectado hace < VENTANA**
(la misma ventana de walk-away del flujo BT). `BtConnectionStore` ya registra la CONEXIÓN
(`recordConnected`); añadir el timestamp de DESCONEXIÓN y considerar "BT activo" = conectado o
último edge de desconexión dentro de la ventana.

## Alcance (archivos)
- `domain/bluetooth/BluetoothScanner.kt` (interfaz): nuevo método, p.ej.
  `fun isCarBluetoothActive(pairedDeviceIds: Set<String>, windowMs: Long): Boolean`
  (conectado ahora OR desconectado hace < windowMs).
- `androidMain/.../AndroidBluetoothScanner.kt`: implementar con `BluetoothManager.getConnectedDevices`
  / estado ACL de perfiles (A2DP/HEADSET) + lectura de `BtConnectionStore`.
- `androidMain/.../BtConnectionStore.kt`: `recordDisconnected(vehicleId, ts)` + lectura del último edge.
- `androidMain/.../BluetoothConnectionReceiver.kt`: en ACL_DISCONNECTED, además de lo actual,
  `BtConnectionStore.recordDisconnected(...)`.
- `iosMain/.../IosBluetoothScanner.kt`: stub (iOS es target futuro).
- `commonMain/.../ParkingStrategyResolver.kt`: `strategyFor` gatea BLUETOOTH por
  `isCarBluetoothActive(pairedIds, WINDOW)` en vez de `isBluetoothEnabled()` solo. Mantener el orden
  (NONE para SCOOTER/BIKE; COORDINATOR si no hay BT activo). `hasBtPairedParkingVehicle` (fiabilidad/
  tier) se queda: es el hecho de SETUP, no de runtime.
- `fakes/FakeBluetoothScanner.kt` + `FakeOtherDataSources.kt`: soportar el nuevo método.
- Tests: `ParkingStrategyResolverTest` — casos nuevos (BT paired pero NO conectado → COORDINATOR;
  conectado → BLUETOOTH; recién desconectado dentro de ventana → BLUETOOTH; fuera de ventana →
  COORDINATOR).
- Doc: `docs/detection/PARKING-DETECTION.md` (regla de resolución) — misma tarea.

## Efecto colateral positivo (encaja con el banner)
Con esto, conducir el Focus resuelve a COORDINATOR → fiabilidad puede ser REDUCED (sin BT activo) →
el banner de [[det-battery-exemption-nudge-001]] aparece correctamente. Coherente.

## Validación
Requiere **device con los dos coches**: (1) conducir el Focus con Kamiq emparejado + BT on → debe
salir `enterSentry … resident` (Coordinator), no `arm refused strategy=BLUETOOTH`; (2) conducir el
Kamiq → BLUETOOTH y desconexión confirma; (3) aparcar el Kamiq y caminar → sin doble pin.

## Relacionados
det-strategy-gate-001 · det-tiers-001 (ARCH-MONITORING-002) · det-bt-receiver-export-001 ·
[[project_det_battery_whitelist_2026_08_08]] · det-battery-exemption-nudge-001.
