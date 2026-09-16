# DOCS-03-WORKERS-ENTRYPOINTS-MUST-MATCH-MASTER-001 · el inventario de actores describía un servicio borrado, y lo hacía con números de línea

**Estado:** ✅ Done · en master (squash). Rama
`chore/DOCS-03-WORKERS-ENTRYPOINTS-MUST-MATCH-MASTER-001-sweep`, worktree `../Paparcar-docs-03`, las
dos se borran al cerrar. Solo documentación: nada que ver en device.

## Problema

Cerrando [FGS-DATA-SYNC-IS-NO-LONGER-INJECTED-001] apareció, una línea por encima del párrafo que
tocaba, que `docs/detection/03-workers-entrypoints.md` seguía diciendo:

> Servicios: `CoordinatorDetectionService` y `BluetoothDetectionService`, ambos
> `foregroundServiceType="location"`, `exported="false"` (`:65-76`).

`BluetoothDetectionService` **está borrado** desde `d7880ac7` [DET-ONE-SERVICE-TWO-LANES-001], y el
manifiesto tiene en su lugar un comentario que lo dice. El doc es de un barrido de solo-lectura del
**18-08**; entre esa fecha y hoy pasaron por encima el carril BT unificado, la reorganización de
paquetes de detección y el pivote de trayectos.

## Doctrina violada

[DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]. Y una segunda, más útil: **un doc de arquitectura que cita
`fichero:línea` caduca en el commit siguiente y caduca en silencio.** Este tenía ~40 referencias del
tipo `:485`, `:1165`, `:394`; en septiembre casi ninguna apuntaba a lo que decía. Un nombre de
función también se puede quedar atrás, pero se rompe con el rename, y eso se ve.

## Diseño

Dos cosas, no una:

1. **Barrer el contenido contra master** — cada fila del inventario verificada contra el código, no
   contra el recuerdo del doc.
2. **Quitar la fuente de la podredumbre**: el doc pasa a citar **fichero + símbolo**, y lo dice en su
   cabecera como regla. El número de línea solo se admite cuando lo que importa es el tamaño
   ("2.728 líneas").

Además, la cabecera separa tres cosas que antes se confundían: lo que se verificó en agosto, lo que
se re-verificó el 16-09 (marcado **✅ 16-09**) y lo que sigue **NO VERIFICADO**.

## Qué estaba mal — hallazgos del barrido

| # | El doc decía | La verdad en master ✅ 16-09 |
|---|---|---|
| 1 | 2 servicios FGS propios, notificaciones 1001 y 1003 | **1** servicio y **1** notificación (1001). `BluetoothDetectionService` borrado; no existe ningún `_NOTIFICATION_ID = 1003` |
| 2 | 11 acciones del servicio, una de ellas `ACTION_BT_OVERRIDE` | **17** acciones. El trío `BT_OVERRIDE`/`BT_TRIP_START`/`BT_TRIP_STOP` está retirado por `ACTION_BT_CONNECTED`/`ACTION_BT_DISCONNECTED`; el arbitraje se resuelve DENTRO del intake |
| 3 | "Workers (11)" | **15** ficheros en `detection/worker/`: 12 de detección (nace `DeclinedBoardingRelookWorker`) + 3 que solo viven ahí (`FuelPriceWatchWorker`, `MonthRecapWorker`, `DeleteVehicleRemoteWorker`) |
| 4 | `DrivingRouteStoreImpl`, prefs `driving_route` | `RoomDrivingRouteStore` en **commonMain**, Room, una fila por fix y **sin cap** [TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001] |
| 5 | `GeofenceEventBusImpl`, `Channel(UNLIMITED)` | `SharedFlowGeofenceEventBus` en **commonMain**, `replay=0` + buffer 64: difunde a todos y descarta sin suscriptores [IOS-F0-04] |
| 6 | Cura de valla "throttled" + `clearCureThrottle` tras EXIT falso | Ya no hay throttle de reloj: `FenceRegistrationLedger` (§A, en memoria, un proceso nuevo siempre registra) + `markFenceStatePoisoned` (§B, stamp durable que consume la cura) [DET-FENCE-REREGISTER-BY-CAUSE-001] |
| 7 | "3 llamadores de `ConfirmParkingUseCase`" | **5**: `DetectionEffectExecutor`, `BluetoothParkingDetector`, `ParkingBackfillWorker`, `RunHonestCloseUseCase`, `SaveManualParkingUseCase` |
| 8 | `ParkingConfirmationReceiver` enruta 5 acciones | **6** (entra `ACTION_USER_STOP`), y el intent-filter del manifiesto sigue listando 4 |
| 9 | Faltaban del inventario | `FenceRegistrationLedger`, `UserStopStore`, `ArrivalHandoffDetectionImpl`, `AndroidDetectionSideRecords`, `BluetoothDepartureDetector`, `AndroidBtConnectStampReader`, `sensor/AndroidDetectionStepAnchors` + las dos fuentes de pasos, y el receiver `ParkedWidgetReceiver` del manifiesto |
| 10 | "initial trigger 0" como si fuera un valor cualquiera | Las **tres** vallas llevan `NO_INITIAL_TRIGGER` (=0), y `removeGeofence` borra las tres por id |
| 11 | Permisos FGS incluían `FOREGROUND_SERVICE_DATA_SYNC` | Corregido ya en [FGS-DATA-SYNC-IS-NO-LONGER-INJECTED-001], `4aada13d` |

## Dos NO VERIFICADO que se pudieron cerrar

El doc de agosto marcaba como no verificado todo lo que vivía en `commonMain`, fuera del alcance de
aquel subagente. Dos se resolvieron leyendo el código:

1. **El intent-filter incompleto de `ParkingConfirmationReceiver` es inocuo.** Los seis
   `PendingIntent.getBroadcast` de `AppNotificationManagerImpl` hacen
   `setClass(context, ParkingConfirmationReceiver::class.java)`: son intents **explícitos** y el
   filter no participa en la entrega. Queda como ruido, no como bug.
2. **El doble cerrador de la salida (R3) es idempotente para el segundo pase.**
   `ProcessConfirmedDepartureUseCase` arranca con `getActiveSessionByGeofence(geofenceId)` y todo lo
   destructivo —publicar el spot, `clearActiveParkingSession`, `markProvisionalDeparture`,
   `retractParkingSession`— vive dentro de `if (session != null)`. El segundo cerrador encuentra
   `null` y solo repite dos colas idempotentes (`departureEventBus.reset()`, `removeGeofence`).
   ⚠️ Escrito con su límite: eso hace benigno el segundo PASE, no el solape — dos cerradores que leen
   antes de que ninguno escriba siguen siendo check-then-act.

Siguen **NO VERIFICADO** a propósito, porque no se pudieron cerrar leyendo: la sub-ventana del guard
`isRunning` del backfill (R1) y la ventana entre el confirm del coordinator y el STOP del pin manual
(R12).

## Consumidores auditados

`grep -rln "BluetoothDetectionService"` sobre `docs/` → **25 ficheros**. Fuera de alcance
deliberadamente: son docs de barridos fechados (`08-flujo-e2e.md`, `09-arquitectura-objetivo.md`,
`10-plan-refactor.md`, los refactors de agosto…) que describen el estado del sistema el día que se
escribieron y cuyo valor es justo ese. Este ticket arregla el que el user señaló, que es además el
único que se lee como inventario en presente. Si hay que barrer el resto, es su propio ticket y
conviene decidir primero cuáles son inventario y cuáles son historia.

## Criterio de éxito

Cada fila del inventario se puede comprobar contra master en una búsqueda por símbolo, y ninguna
nombra un actor que no existe. La cabecera dice con qué fecha se verificó cada cosa.

## Verificación

Solo markdown: ni código ni recursos, así que no hay build que romper. Cada afirmación nueva sale de
una lectura del árbol de la rama (mismo contenido que master, `e8baaf35`).
