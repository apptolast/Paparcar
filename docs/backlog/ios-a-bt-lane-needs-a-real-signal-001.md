# IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001 · El carril BT de iOS necesita una señal real, no un sucedáneo

**Estado:** ✅ Done · issue GitHub **#74** · PR **#122** (squash; el hash vive en MEMORY.md) ·
⏳ verificación visual en iPhone pendiente (desde Windows no hay simulador)

## Problema

La issue #74 pide el equivalente iOS del carril Bluetooth de detección. En Android el carril es
determinista: `ACTION_ACL_DISCONNECTED` de la MAC emparejada → fix GPS → alejarse ≥30 m →
confirma, y eso sostiene la promesa de nivel **"Automático"** frente al **"Asistido"** del
Coordinator. La pregunta que condiciona el ticket entero: **¿existe en iOS una señal de
desconexión del BT del coche que una app de terceros pueda recibir, especialmente en
background?** Un carril BT falso es peor que no tener carril BT (fallo asimétrico: mejor FN que
FP; la fiabilidad se promete por escrito).

## Doctrina en juego

- *El evento NOMINA, solo el movimiento MEDIDO confirma* — cualquier señal iOS solo podría
  nominar; la confirmación sigue exigiendo alejamiento medido.
- *Fallo asimétrico* — si la señal no es determinista, no puede vestir "Automático".
- *Carriles separados = slots + use cases separados, mismo host* — cero señales BT en el scoring
  del Coordinator [DET-ONE-SERVICE-TWO-LANES-001].
- *La DECISIÓN vive en use cases puros de commonMain* — el código iOS solo haría I/O.

## Investigación · qué puede ver de verdad una app de terceros en iOS (verificado 2026-09-22)

### Veredicto corto

**No existe equivalente del ACL receiver de Android.** Ninguna API de iOS entrega a una app de
terceros el evento de conexión/desconexión del Bluetooth clásico (A2DP/HFP, que es lo que usa el
equipo del coche) cuando la app está suspendida, y ninguna la despierta por ese evento. La única
ventana real es **AVAudioSession mientras el proceso ya está vivo** — sirve como *evidencia*
dentro de una sesión de detección ya despierta, no como *trigger* que arranca una.

### Vía 1 · `AVAudioSession.routeChangeNotification` (+ `currentRoute`)

- **Qué da**: al cambiar la ruta de audio del sistema, notificación con
  `AVAudioSessionRouteChangeReason` (`.newDeviceAvailable` al conectar, `.oldDeviceUnavailable`
  al desconectar) y la descripción del puerto anterior. Puertos relevantes: `.bluetoothA2DP`,
  `.bluetoothHFP`, `.carAudio` (CarPlay).
- **(a) ¿Background?** Es una `NSNotification` in-process: llega con la app en foreground o
  corriendo en background, **no llega suspendida y no se reentrega al volver** — hay que releer
  `currentRoute` al despertar. Peor: desde iOS 16 hay reportes sin respuesta de Apple de que
  incluso `currentRoute.outputs` devuelve **vacío en background** (forum thread 715244,
  reproducido también en iOS 17). **No despierta la app jamás.** Mantener el proceso vivo con el
  background mode `audio` sin reproducir audio real es motivo de rechazo en App Store y el
  sistema mata sesiones silenciosas → no es una opción.
- **(b) ¿Identidad?** `AVAudioSessionPortDescription.uid` para puertos BT ha contenido
  históricamente **la MAC real con sufijo de perfil** (`"00:02:5C:22:22:11-tacl"` para A2DP,
  `"…-tsco"` para HFP), observado aún en iOS 17 (forum 742571, 45255). Es decir: **la identidad
  SÍ es comparable con la MAC que ya guardamos** (strip del sufijo). ⚠️ Pero es comportamiento
  **no documentado ni garantizado** — Apple ya aleatorizó los identifiers de CoreBluetooth y
  puede cerrar este — así que cualquier diseño debe degradar con dignidad si el UID deja de ser
  una MAC (fallback: `portName`, que es el nombre BT del equipo, mismo dato que muestra Android
  al emparejar). No hay forma de **listar** los dispositivos clásicos emparejados: la identidad
  solo se puede capturar **mientras el coche está conectado y la app abierta** (onboarding
  distinto al de Android).
- **(c) ¿App Store?** Leer `currentRoute` y observar la notificación no exige background mode ni
  permiso; con categoría `playback` no hace falta ni `NSMicrophoneUsageDescription` (los puertos
  HFP como *entrada* exigirían `playAndRecord` + micro — no lo necesitamos: A2DP output basta).
  Riesgo de revisión: bajo.

### Vía 2 · `ExternalAccessory`

**Descartada.** Solo habla con accesorios **MFi** cuyo fabricante haya declarado el protocol
string de NUESTRA app (doc oficial: "The manufacturer of an MFi accessory decides which
third-party apps may communicate with their accessories"). Un equipo de coche A2DP no es un
accesorio MFi de Paparcar. Sin fabricante aliado, no hay nada que escuchar. (a) irrelevante,
(b) nada, (c) exigiría partnership hardware.

### Vía 3 · CoreBluetooth (BLE + "BR/EDR")

**Descartada como carril.** El overview del framework dice "LE and BR/EDR (Classic)", pero el
soporte clásico es **GATT sobre transport bridging** (accesorios dual-mode tipo auriculares MFi):
un head unit A2DP/HFP **no expone GATT a apps de terceros y no aparece** en
`retrieveConnectedPeripherals(withServices:)` ni genera `connectionEventDidOccur`. Los coches en
general **no anuncian BLE conectable** para terceros (los que anuncian algo lo hacen con
servicios privados para la app del OEM). Además la identidad es inservible para nuestro modelo:
`CBPeer.identifier` es **un UUID aleatorio por teléfono y por app** — confirmado por ingeniero de
Apple en forum 734737: "It's just a random UUID assigned by Core Bluetooth… not the MAC address"
— o sea, ni comparable con `Vehicle.bluetoothDeviceId` ni con `SharedCar.btMacHash`, ni estable
entre reinstalaciones (cambió de comportamiento en iOS 16.6). (a) el background BLE
(`bluetooth-central` + state restoration) funciona, pero solo para peripherals BLE reales que el
coche no ofrece; (b) identidad incompatible; (c) el background mode habría que justificarlo con
un uso que no tenemos.

### Vía 4 · CarPlay

**Descartada como carril; anotada como señal futura.** Existe entitlement de categoría *parking*
(`com.apple.developer.carplay-parking`) que Apple concede bajo solicitud, y una escena CarPlay
conectada sería una señal fortísima de "estoy en MI coche". Pero: solo cubre coches con CarPlay
(y el usuario tiene que usarlo), el entitlement es un proceso de aprobación aparte, y la
detección de conexión/desconexión desde background sufre exactamente el mismo agujero que la
vía 1 (thread 715244 es, de hecho, sobre CarPlay). No puede ser el sustituto del carril; podría
ser evidencia de armado el día que tengamos el entitlement. Fuera de alcance de este ticket.

### Consecuencia

- **Trigger determinista de desconexión: NO EXISTE en iOS.** El carril BT de Android
  (disconnect → fix → ≥30 m) no se puede portar. `resolveStrategy` en iOS debe seguir
  devolviendo **Coordinator**, y la promesa de nivel al usuario en iOS no puede decir
  "Automático" por tener `bluetoothDeviceId` vinculado.
- **La identidad BT sí es capturable y comparable** (UID `MAC-tacl` mientras dure; `portName`
  como respaldo), pero solo con el coche conectado y el proceso vivo.
- Lo que iOS sí permite es usar la ruta de audio como **evidencia de pertenencia** ("este viaje
  es en MI coche") dentro del carril Coordinator ya despierto — el mismo asiento que en Android
  ocupa el trabajo de DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS. Si eso toca o no la doctrina de
  "cero señales BT en el Coordinator" se decide abajo, con el mapa del código delante.

## Mapa del código (barrido 2026-09-22, este worktree)

**El carril BT Android que habría que "portar":** `BluetoothConnectionReceiver` (manifest,
exported, ACL connect/disconnect → MAC → `getVehicleByBluetoothDeviceId`) + `BtConnectionStore`
(stamps por vehicleId) + slots `btHuntJob`/`btTrackingJob`/`btWatchJob` dentro de
`CoordinatorDetectionService` + decisión pura en `EvaluateBtParkUseCase` /
`EvaluateBtArbitrationUseCase` / `btDriveAwayIsMeasured` (commonMain). Provenance del carril:
`DetectionPath.Bt`/`BtTimeout` y `ArmLabel.BT_RIDE`. Los ≥30 m son
`ParkingDetectionConfig.btWalkAwayDistanceMeters = 30f`.

**Lo que ya protegía a iOS (dos barreras, ambas testadas):**
- `ParkingStrategyResolver.strategyFor` exige `isConnectedToPairedCar()` — y
  `IosBluetoothScanner` devuelve `false` estructural ⇒ iOS **nunca** resuelve BLUETOOTH.
  (⚠️ `resolveStrategy(vehicle, isBluetoothEnabled)` de CLAUDE.md era drift documental — la API
  real es `resolve()`/`strategyFor(vehicles)`; corregido en este ticket.)
- `DeviceCapabilities(supportsBtStrategy = false)` en los módulos iOS ⇒
  `EvaluateDetectionReliabilityUseCase` capa el tier a ASSISTED aunque el vehículo llegue con
  `bluetoothDeviceId` sincronizado ([IOS-F0-03], tests de capability-masking).

**Las tres mentiras que sí quedaban:**
1. `PermissionsContent`: la fila del permiso BT prometía «→ Automático» también en iOS.
2. `SettingsScreen`: la fila «Bluetooth del coche» llevaba a `BluetoothConfigScreen`, que en iOS
   lista 0 dispositivos (`getBondedDevices()` vacío por diseño de plataforma).
3. `IosBluetoothScanner` instanciaba `CBCentralManager` en el constructor solo para
   `isBluetoothEnabled()` — instanciarlo es lo que dispara el diálogo del permiso BT del sistema,
   o sea: **pedíamos un permiso que no desbloquea nada**.

**Identidad (el nudo, para el futuro):** Android guarda la MAC en `Vehicle.bluetoothDeviceId` y
`SharedCar.btMacHash = fnv1a64("carId:MAC")`. Hallazgo del barrido: **`btMacHash` se escribe pero
hoy no se compara en ningún sitio** (su único alimentador, `joinByCode`, recibe siempre
`bluetoothDeviceId = null`). El día que iOS capture identidad vía ruta de audio (UID `MAC-tacl`
→ strip del sufijo → la MISMA MAC), el token cuadraría sin re-keyar nada — pero hoy no hay
consumidor que lo necesite.

## Diseño

*El invariante en UN sitio:* «¿puede el emparejamiento BT cambiar cómo se detecta en esta
plataforma?» ya existía en dominio como `DeviceCapabilities.supportsBtStrategy`; lo que faltaba
era su espejo de UI. Nace **`expect val isBtStrategyRelevant`** (`Platform.kt`; `true` Android,
`false` iOS), el mismo patrón que `isBatteryOptimizationRelevant` de
[IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001] / [SETTINGS-REMODEL-001] — el KDoc de ambos
lados deja escrito que los dos hechos deben coincidir.

Consumidores del flag (todo lo demás queda igual):
- `PermissionsContent`: la fila BT solo se pinta si `isBtStrategyRelevant`; la sección opcional
  entera solo si `isBtStrategyRelevant || isBatteryOptimizationRelevant` (en iOS ambas son
  `false` y la sección desaparece — sin mejoras que ofrecer, la caja es humo); el `satisfied`
  del tier deja de exigir el permiso BT donde la fila no existe.
- `PermissionsScreen.ios.kt` descablea `onRequestBluetooth` y
  `PermissionsScreenParityGuardrailTest.DECLARED_ABSENT` lo documenta (el guard exige las dos
  mitades: ni callback ni fila).
- `SettingsScreen`: fila «Bluetooth del coche» + cabecera «mejora la detección» detrás del gate
  ⇒ `BluetoothConfigScreen` queda inalcanzable en iOS (su ruta sigue existiendo; solo Android
  navega a ella).
- `IosBluetoothScanner` pasa a stub honesto **sin CoreBluetooth**: `isBluetoothEnabled() = false`
  constante. Nada en iOS consume el estado del adaptador (ver barrido abajo), y no tocarlo
  elimina el diálogo de permiso espurio.
- `resolveStrategy`/tier: **sin cambios de código** — ya eran correctos; se añade el testigo con
  nombre en `ParkingStrategyResolverTest` (vehículo con pairing sincronizado desde Android +
  scanner con forma de iOS ⇒ COORDINATOR).

**Dev Catalog:** sin cambios. Las variantes que introduce este ticket están gateadas por
`expect val` de plataforma, no por estado MVI — la galería (flavor mock, Android) no puede
renderizar la forma iOS, exactamente igual que ya pasa con las filas de batería de
[SETTINGS-REMODEL-001]. No hay estado nuevo que espejar.

Lo que este ticket **no** hace, deliberadamente: ningún sucedáneo de carril con AVAudioSession.
La ruta de audio solo puede ser *evidencia con el proceso vivo* — el asiento que en Android ocupa
DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS (pertenencia del embarque, fuera del scoring) — y montar
eso exige primero un mecanismo iOS de captura de identidad (onboarding con el coche conectado) y
la decisión de doctrina correspondiente. Queda como vía futura documentada, no como deuda
implícita: si se abre, será su propio ticket con su propio `armEvidence`.

## Criterio de éxito

La issue #74 acepta explícitamente dos salidas; con los datos de arriba la salida es la **(B)** —
y queda cumplida así:
- El resolver sigue devolviendo Coordinator en iOS y ahora tiene **testigo con nombre**
  (`ParkingStrategyResolverTest`: pairing sincronizado desde Android + scanner con forma de iOS ⇒
  COORDINATOR). El techo ASSISTED ya estaba testado ([IOS-F0-03]).
- Ninguna superficie iOS promete ya el salto a «Automático» (fila de Permisos, fila de Ajustes,
  sección de mejoras) y la app deja de pedir en iOS un permiso BT que no desbloquea nada.
- Este doc es el análisis que la issue pedía (`IOS_PAPARCAR_DETECTION_ARCHITECTURE.md` de la
  issue = este fichero): matriz de las 4 vías con (a) background, (b) identidad, (c) App Store, y
  el hueco escrito con su vía futura (identidad por ruta de audio como evidencia, nunca carril).

## Consumidores auditados

Grep de `isBluetoothEnabled` / `permissions_tier_automatic` / `BluetoothConfig` /
`VehicleMonitoringStatus.Bluetooth` / `hasBluetoothConnect` / `btMacHash` sobre `shared/src`:

| Consumidor | Veredicto |
|---|---|
| `ParkingStrategyResolver.strategyFor:96` (`isBluetoothEnabled` en la conjunción BT) | **cerrado** — en iOS la conjunción muere en `isConnectedToPairedCar=false`; el stub constante no cambia ningún resultado. Testigo nuevo con nombre |
| `BluetoothConfigViewModel` (×3 `isBluetoothEnabled`) | **cubierto** — su pantalla queda inalcanzable en iOS (la única entrada, Ajustes, está detrás del gate) |
| `ParkingSafetyNetWorker:273` · `BluetoothConfigPreviews` | **exentos** — androidMain, el flag allí es `true` |
| `PermissionsContent` fila BT + `unlocks` «→ Automático» + `satisfied` del tier | **cerrado** — gate `isBtStrategyRelevant` (la sección entera cae si tampoco hay exención de batería) |
| `PermissionsScreen.ios.kt` `onRequestBluetooth` + `PermissionsScreenParityGuardrailTest` | **cerrado** — descableado y en `DECLARED_ABSENT` con razón; el guard vigila las dos mitades |
| `SettingsScreen` fila «Bluetooth del coche» + `MiniHeader` de mejoras | **cerrado** — mismo gate; en iOS la sección de mejoras desaparece completa |
| `DetectionTierStatusCard` (nombre/promesa del tier AUTOMATIC) | **exento** — solo se pinta si `tier == AUTOMATIC`, inalcanzable en iOS por el capability-masking ya testado ([IOS-F0-03]) |
| `ParkingHistoryDetailScreen` «Automático (Bluetooth)» | **exento con razón** — atribución histórica de un pin que SÍ confirmó el carril BT (en el Android de la misma cuenta); contar la verdad de un pin pasado no es prometer el carril en este device |
| `VehicleIdentity` / `VehicleMonitoringStatus.Bluetooth` (nombre azul del coche BT) | **exento con razón** — describe la configuración de vigilancia del COCHE a nivel de cuenta (quién lo vigila y cómo), no una promesa de este dispositivo. Si en campo iOS confunde, es su propio ticket de copy |
| `PermissionsState.hasBluetoothConnect` / `IosPermissionManagerImpl` (lectura de clase, sin diálogo) | **exento** — el estado se sigue poblando; en iOS ya no hay UI que lo pinte ni flujo que lo pida |
| `IosPermissionRequester.requestBluetooth()` + `SystemPermissionDialog.BLUETOOTH` (#76) | **cubierto** — quedan sin llamador en iOS (solo los disparaba la fila); se conservan para el día que una vía real necesite el permiso |
| `SharedCar.btMacHash` | **exento** — se escribe y no se compara en ningún sitio hoy (hallazgo del barrido: `joinByCode` recibe siempre `bluetoothDeviceId = null`); nada que gatear en iOS |

## Fuentes

- Apple · Responding to audio route changes (doc JSON, leída 2026-09-22)
- Apple · Core Bluetooth overview · `registerForConnectionEvents(options:)` · External Accessory overview
- Apple Developer Forums: 95485 (detect car in background — sin solución oficial, 2018-2024),
  715244 (currentRoute vacío en background iOS 16+, sin respuesta de Apple), 734737 (ingeniero de
  Apple: CBPeer.identifier es UUID aleatorio), 742571 + 45255 (UID `MAC-tacl` observable, iOS 17)
