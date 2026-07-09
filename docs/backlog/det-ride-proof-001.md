# DET-RIDE-PROOF-001 — Ninguna autoridad sin prueba de movimiento

**Estado:** implementado (rama `feature/DET-BREADCRUMBS-001-trip-trail`), pendiente field-test.
**Origen:** field-test 2026-07-09 — falsos positivos en cascada (Redmi) + salidas reales mudas (Oppo).

## El principio (el sistema, no los parches)

Un evento del OS (AR `IN_VEHICLE_ENTER`, EXIT de geocerca, pareja EXIT∧ENTER) solo **nomina**
una salida. Lo único que la **confirma** es movimiento medido:

1. Un fix a velocidad de conducción con precisión creíble (regla canónica ya existente), o
2. **Alcance peatonal superado** (`ParkingDetectionConfig.isBeyondPedestrianReach`): la posición
   está más lejos del coche de lo que las piernas permiten en el tiempo transcurrido
   (`maxPedestrianSpeedMps × Δt + radio_valla + precisión`). Una sola función, usada por todos
   los puntos de decisión.

Lo que rompió el field-test del 09-07 fueron tres vías que decidían con nominaciones sin física:

| Vía | Fallo de campo | Arreglo |
|---|---|---|
| `verified_enter` pre-arm (conjunción EXIT∧ENTER) | ENTER fantasma andando (11:53) → plaza liberada + sesión sembrada → parking falso en la peluquería (12:11) | El ENTER solo verifica si el desplazamiento supera el alcance peatonal desde el embarque; sin fix → `Unverified` (fail closed) |
| Fall-through del departure worker (`admissibleBoarding`) | 4 intentos `Inconclusive` (speed=0) y aun así publicó la plaza a las 11:55 | `admissibleBoarding` exige la misma corroboración; AR + no-fix ya no confirma (`Confirmed` solo por velocidad creíble) |
| Step-budget con contador congelado | Contador clavado en 307 todo el día → delta=0 sobre 354 m andados = "te llevaron" → backfill fantasma (12:39) | delta ≤ `frozenCounterSuspectSteps` (5) sobre desplazamiento andable ⇒ contador MUDO (pierde delta y frescura de ancla); el backfill usa `trustedStepsSinceAnchor` del evaluador, nunca la lectura cruda |

La conjunción del safety-net lleva también la cota peatonal (los viajes de cine 1470 m/<5 min
la pasan de sobra; un paseo a la peluquería jamás).

## Trigger uniforme (deroga el triaje de DET-EXIT-TRUST-001)

El triaje por distancia de entrega estaba invertido respecto a la realidad: una salida real en
coche entrega el EXIT **lejos** de la valla (te estás moviendo + lag del OEM) y salir andando lo
entrega **en el borde**. Resultado: las salidas reales iban a un reconcile que no puede escalar,
y las de andar conservaban autoridad. Ahora un EXIT entregado lejos:

- encola el **mismo** `DepartureDetectionWorker` (velocidad en vivo decide: conduciendo →
  confirma y publica; parado/andando → descarta),
- **arma el coordinator** (Unverified, guards anti-andar activos) aprovechando que el service ya
  está vivo — un zombi cuesta un abort de ~4 min de GPS,
- y sigue registrándose para la conjunción del reconcile (el respaldo para viajes que terminan
  dentro del lag de entrega).

Lo único que pierde un EXIT lejano es la liberación instantánea, nunca el deber de mirar.
(Restaura el contrato: todo trigger dispara SIEMPRE + verificación tardía.)

## Escalada solo en ventanas exentas (hecho verificado en docs de Android)

`startForegroundService` desde background solo está permitido en exenciones documentadas; entre
ellas: **recibir un evento de geofencing o de transición de Activity Recognition**, y que el
usuario haya desactivado la optimización de batería. Un worker de WorkManager NO está exento
(denegación observada en campo: 13:55, `mAllowStartForeground=false`; a las 12:55 el mismo start
funcionó porque ocurrió ~1 s después del broadcast del geofence).

- `ActivityTransitionReceiver`: ENTER **fresco** (lag ≤ ventana de pareja) + EXIT-lejano
  registrado reciente + detección parada → arranca el tracking DENTRO de la ventana de exención
  del evento AR (la mitad viva de la conjunción). El arm es Manual (sin semilla): un ENTER
  espurio cuesta un abort sin movimiento.
- `ParkingSafetyNetWorker`: si el start falla, el prompt "¿sigues aparcado?" ahora marca
  `anyPromptActive=true` — antes el propio worker lo borraba milisegundos después de mostrarlo
  (por eso el usuario no vio ninguna notificación el 09-07).

## Riesgo residual aceptado

- Salida real con delta de pasos ≤ 5, desplazamiento andable, sin AR ENTER y sin conjunción:
  no se libera sola (queda para el prompt / apertura de app). Es indistinguible de un contador
  muerto; preferimos el falso negativo.
- ENTER fantasma + EXIT andando entregado >300 m + a >alcance peatonal: ventana residual muy
  estrecha; el coordinator armado en vivo la corta (abort por pasos).

## Validación pendiente

- Field-test en ambos móviles (Oppo: comprobar que las salidas reales vuelven a armar; Redmi:
  paseo a pie sin liberar plaza ni parking fantasma).
- Extraer `parkdiag.log` del Oppo para confirmar que sus salidas reales caían al bucket lejano.
