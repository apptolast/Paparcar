# DET-A-PARKED-BT-CAR-NEEDS-NO-RESIDENT-WATCHER-001 · El coche BT aparcado se despierta solo

**Estado:** 🔵 En revisión · rama `bugfix/DET-A-PARKED-BT-CAR-NEEDS-NO-RESIDENT-WATCHER-001` ·
cierra la **issue #57**

## Problema

Reportado por el product owner (#57): la notificación del FGS está **siempre** visible, incluso para
quien sólo tiene un vehículo con Bluetooth. *«Creía que habíamos acordado despertar el servicio por
BT receiver.»*

El acuerdo existía y estaba implementado: `DET-STRATEGY-GATE-001` metió la residencia del centinela
detrás de `strategy == COORDINATOR`.

## Causa raíz — dos tickets correctos que chocan en un momento concreto

`DET-BT-CONNECTED-NOT-PAIRED-001` redefinió `BLUETOOTH` como «**conectado ahora mismo**», para que un
coche BT aparcado en casa no secuestrase la estrategia mientras conduces otro. Correcto por separado.

Pero el epílogo que decide la residencia corre **justo después de aparcar**, que es exactamente
cuando el coche acaba de **desconectarse**. Así que `resolve()` devolvía COORDINATOR siempre y el
gate era inalcanzable **en el único momento en que se le preguntaba**.

Ningún test lo cazó: los de `resolvePostDetectionLifecycle` pasaban la estrategia a mano, así que
probaban una rama que producción ya no podía alcanzar.

## Doctrina

No la viola: la decisión sigue siendo una función **pura de commonMain** y el servicio sólo hace I/O
[DET-VERDICT-NOT-PREDICATE-001]. Los carriles siguen separados — esto no mete señal BT en el scoring
del Coordinator, decide **quién paga un vigilante**.

## Diseño

- `parkedCarNeedsWatcher(parkedVehicleIds, vehiclesThatNeedNoWatcher)` — pura, testeable, por COCHE.
- Un coche no necesita vigilante si **está emparejado por BT** (su próximo ACL CONNECT es su salida,
  vía receptor de manifiesto que revive un proceso muerto) o si **nunca aparca en plaza**
  (patinete/bici — la red que el gate daba antes con `NONE`).
- `resolvePostDetectionLifecycle` pierde el parámetro `strategy`: la pregunta que hacía ya está
  contestada, mejor, por coche.
- Ante la duda se **vigila**: una sesión sin coche conocido, o una flota ilegible, cuentan como que
  necesitan centinela. Quedarse residente cuesta una notificación silenciosa; no quedarse cuesta una
  salida perdida, que es la clase de fallo crónico por la que existe el centinela.

## Consumidores auditados

| Sitio | Qué hacía | Decisión |
|---|---|---|
| `CoordinatorDetectionService.resolveIdleEpilogue` | `strategyResolver.resolve()` | ✅ lee la flota y decide por coche |
| `ObserveDepartureWatchGapUseCase` | misma regla para el diagnóstico de «hueco» | ✅ misma entrada; el `strategyResolver` queda **sin usar y se retira** del constructor y del módulo Koin |
| `DepartureWatchResumer` (port) | sólo lo cita en KDoc | ✅ el KDoc sigue siendo cierto |
| `BluetoothDepartureDetector` | sólo lo cita en KDoc | ⚠️ su KDoc decía «bajo BLUETOOTH, `resolvePostDetectionLifecycle` responde Stop» — sigue siendo el efecto, ahora por el motivo correcto |
| `SentryLifecycleDecisionTest` | pasaba `strategy` a mano | ✅ reescrito, + 6 casos nuevos |

## Riesgo asumido

Un coche BT del que te alejas **sin que el móvil se conecte** (BT apagado, manos libres que no
engancha) ya no tiene proceso residente. No queda ciego: la geocerca sigue registrada y su EXIT
nomina igual, y `ParkingSafetyNetWorker` pasa cada 15 minutos. Es exactamente la cobertura que tenía
la estrategia BLUETOOTH antes de que el gate se volviera inalcanzable.

## Verificación

`./gradlew testAll --rerun-tasks` → **3.491 tests, 0 fallos**.

## Criterio de éxito (en device, lo tiene que ver el user)

- Un solo coche, emparejado por BT, aparcado: la notificación permanente **desaparece** tras aparcar.
- Al volver al coche y conectar el manos libres, la detección arranca igual (receptor de manifiesto).
- Con un segundo coche sin BT aparcado, la notificación **sigue** — es el que necesita el vigilante.
