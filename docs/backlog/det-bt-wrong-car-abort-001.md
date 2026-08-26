# DET-BT-WRONG-CAR-ABORT-001 — El Bluetooth de OTRO coche propio aborta la sesión del Coordinator

**Estado:** ✅ EN MASTER (`10842797`) · campo cubierto por la validación hasta `1a4128d5` (23-08-2026)
**Origen:** field-test 2026-08-10 (Oppo, ida El Puerto → Polígono el Palmar en el Kamiq)

## El bug (evidencia parkdiag.log, certeza absoluta)

Doble pin del mismo aparcamiento físico, a 35 m y 75 s de distancia, atribuido a DOS coches:

1. 16:08 — Focus aparcado MANUALMENTE en casa (geocerca `7bd5082c`).
2. 19:31:01 — El usuario sale conduciendo el **Kamiq**. El teléfono cruza la geocerca del Focus →
   `ARM:GEOFENCE_EXIT` → 19:31:06 `vehicleId locked: Focus (nominator=Focus)`. La geocerca solo
   prueba que el TELÉFONO salió, no que ESE coche se moviera.
3. 19:44:18 — Usuario activa BT a mitad de viaje → `BT CONNECTED` Kamiq → arbitraje NoOp
   (CONNECT+Driving y además coche distinto).
4. 19:49:19 — `BT DISCONNECTED` Kamiq en destino → arbitraje `sameVehicle(Kamiq, Focus)=false` →
   **NoOp por el guard de vehículo** ("an edge from car A never vetoes a trip following car B").
   Nadie aborta al Coordinator. (El disconnect llegó 23 s ANTES del tentative confirm.)
5. 19:50:29 — BT confirma pin **Kamiq** 0.95 (y consume el route store).
6. 19:51:44 — El hold de 120 s [DET-C-02] vence → pin fantasma **Focus** `steps+egress` 0.9
   (`route store not fresh (1 pts, last 74s old)`), que **reemplaza la sesión real del Focus en casa**.

**Experimento de control (misma noche, vuelta):** el Coordinator re-armó con `nominator=null` →
`sameVehicle(Kamiq, null)=true` → el DISCONNECT de las 20:37:09 SÍ supersedió
(`⚡ SupersedeWithBluetooth — signalling abort`) → un solo pin `bt_timeout`. Lo único que separó el
doble pin del comportamiento correcto fue el guard de vehículo.

## El invariante (sistemas, no parches)

Todo evento que llega al árbitro es de un coche PROPIO emparejado (el receiver descarta MACs
desconocidas) y el teléfono solo puede ir en un coche. El `coordinatorVehicleId` es una HIPÓTESIS de
nominación; un edge BT de otro coche propio la REFUTA. Fix en UN sitio: `EvaluateBtArbitrationUseCase`.

## Cambio

Rama coche-propio-distinto (ambos ids non-null y !=) en el use case puro:

- **DISCONNECT → `SupersedeWithBluetooth`** (antes NoOp): el aparcamiento físico pertenece al coche
  BT; un pin del Coordinator sería siempre un duplicado mal atribuido.
- **CONNECT + Driving → `YieldToConnectedCar`** (verdict nuevo; antes NoOp): el usuario está
  demostrablemente en el otro coche → abortar ANTES de que se forme pin alguno (en el field habría
  matado la sesión a las 19:44, 6 min antes del pin). BLUETOOTH es dueño mientras conectado
  [DET-BT-CONNECTED-NOT-PAIRED-001].
- **CONNECT + Candidate → NoOp** (sin cambio): el pin pendiente se ganó con evidencia medida
  PRE-conexión (aparcar coche A, andar hasta coche B); abortar solo podría perder un aparcamiento real.
- Same-car / origen desconocido: sin cambios.

El service ejecuta cualquier verdict ≠ NoOp como abort (`ACTION_BT_OVERRIDE`) — sin cambios allí ni
en el receiver.

**Escenario adyacente cubierto por diseño:** el disconnect out-of-range de un coche propio aparcado
(salir conduciendo con el otro) no puede llegar a la rama coche-distinto con sesión viva — mientras
estás conectado a él el Coordinator no puede armar [DET-BT-CONNECTED-NOT-PAIRED-001], y si conecta
a mitad de sesión, la sesión cede primero (YieldToConnectedCar).

## Ficheros

- `domain/usecase/detection/EvaluateBtArbitrationUseCase.kt` — rama different-own-car + verdict
  `YieldToConnectedCar` + doc del invariante.
- `commonTest/.../EvaluateBtArbitrationUseCaseTest.kt` — 4 casos nuevos/actualizados (truth table
  completa; los dos NoOp viejos de coche-distinto ahora esperan supersede/yield).
- `docs/detection/PARKING-DETECTION.md` §1.1 — tabla de verdicts del árbitro + evidencia field.

## Pendiente

- [ ] Commit + merge a master (squash) con go-ahead del user.
- [ ] Field-test: repetir el escenario (Focus aparcado en casa, salir en Kamiq con BT activándose a
      mitad) → debe salir UN pin Kamiq y la sesión del Focus en casa debe sobrevivir.
- [ ] Restaurar a mano el aparcamiento del Focus (su sesión real en casa fue reemplazada el 10-08).
- [ ] Gap hermano (SIN ticket aún): viajes BT-owned no graban ruta (nadie puebla DrivingRouteStore
      cuando el Coordinator no corre) — decisión de producto pendiente.
