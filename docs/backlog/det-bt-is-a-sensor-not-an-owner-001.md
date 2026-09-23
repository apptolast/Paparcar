# DET-BT-IS-A-SENSOR-NOT-AN-OWNER-001 · el carril BT reclama el viaje y luego no lo confirma

**Estado:** 🔵 Abierto, sin empezar · nace como follow-up explícito de
[DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001] · issue
[#131](https://github.com/rndevelo/Paparcar/issues/131)

## Problema

Dos cosas distintas que comparten causa, ninguna de las cuales costó un pin el 22-09 pero las dos
violan doctrina:

### 1. Un trigger rechazado se DESCARTA, no se degrada

`CoordinatorDetectionService.kt:2202-2213`:

```kotlin
if (trigger != DetectionTrigger.MANUAL) {
    val strategy = strategyResolver.resolve()
    if (!coordinatorMayArm(strategy, trigger)) {
        logTrigger(trigger.name, TriggerDisposition.REFUSED_STRATEGY, detail = "owner=$strategy")
        return                                     // ⛔ el evento muere aquí
    }
}
```

La doctrina dice: *«Todo trigger dispara SIEMPRE… un evento viejo pierde autoridad directa (pasa al
evaluador), nunca se descarta.»* Aquí no pasa a ningún evaluador: se tira.

El 22-09 pasó **cinco veces** (`AR_VEHICLE_ENTER` ×5 + `ARRIVAL_HANDOFF` ×1, todos
`owner=BLUETOOTH`). ⚠️ **El barrido del ledger desmiente que costara pines**: las cinco veces el
coordinator armó igual por `GEOFENCE_EXIT` entre 30 s y 4 min después. El `AR_VEHICLE_ENTER` es la
señal MÁS TEMPRANA que tenemos (el embarque, antes de que exista conducción que medir), así que lo
que se pierde es precisión de arranque, no el viaje. Por eso este ticket no es urgente — pero es un
agujero de doctrina que un día morderá donde no haya geocerca que salve el arm.

### 2. La propiedad del viaje no se gana, se presume

`ParkingStrategyResolver.strategyFor()` concede `BLUETOOTH` con que el enlace ACL esté vivo **en ese
instante**. Con un BT *aftermarket* que parpadea (campo 22-09, Nothing A001: ~20 ciclos
connect/disconnect al día) el carril reclama el viaje y luego **no confirma nada**: sus 12 veredictos
de ese día fueron todos `bt_no_ride_ask` (enganche < `btMinRideDurationMs`) o
`bt_short_no_move_abort`. Cero pines.

## Lo que el usuario pide

> *«¿Es BT un trigger fiable, deberíamos depender de otro trigger y dejar FGS activo como la otra
> estrategia sin bt?»* · *«¿Podríamos confiar más en otras señales y menos el bt?»*

Es decir: **BT como SENSOR, no como dueño del viaje.** El FGS residente del Coordinator mide siempre;
el flanco determinista del BT aporta al confirmar, no silencia al que mide.

Tiene precedente nuestro: en iOS ya lo decidimos así
([IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001], `a3dd5938e` / `9e84430b1`).

## ⛔ El callejón que ya se descartó (no volver a proponerlo)

Exigir que el enlace tenga ≥ `btMinRideDurationMs` (90 s) antes de conceder `BLUETOOTH`. **No vale:**
con la radio de un coche sano el enlace se establece AL EMBARCAR, así que cuando llega el
`AR_VEHICLE_ENTER` es siempre joven. El listón haría armar al Coordinator en CADA viaje de TODO
usuario con BT — que es exactamente el fallo de campo del 01-08-2026 (los viajes del Kamiq clavados
en el Focus) que la puerta existe para evitar.

## El problema real a resolver: la ATRIBUCIÓN

Dejar medir al Coordinator durante la propiedad BT es fácil. Lo difícil —y lo que hundió el
01-08-2026— es que la sesión se atribuya al coche **conectado**, no al primario. Hoy el
`coordinatorVehicleId` sale de `detectionRuntime.trip.value?.departingVehicleId`, que es una
HIPÓTESIS de la geocerca. Mientras el BT esté conectado sabemos con certeza física en qué coche va el
teléfono; ese dato tendría que mandar sobre la hipótesis.

Boceto, sin comprometerse:
1. El Coordinator arma y mide aunque `strategyFor` diga `BLUETOOTH`.
2. La sesión se atribuye al coche del enlace ACL vivo (certeza), no al `isActive` (suposición).
3. El carril BT sigue con su caza y su desconexión; convergen donde siempre, en `ConfirmParkingUseCase`.
4. El arbitraje ya no tiene que MATAR: con los dos midiendo, decide quién firma el pin.
5. El trigger rechazado desaparece como concepto — no hay nada que rechazar.

⛔ **Carriles separados sigue INTACTO**: esto no mete señales BT en el scoring del Coordinator.
Cambia quién mide y quién firma, que es arbitraje, no puntuación.

## Criterio de éxito

- Un usuario con BT *aftermarket* que parpadea obtiene los mismos pines que uno sin BT.
- Un usuario con la radio del coche no ve ni un pin atribuido al vehículo equivocado (la regresión
  del 01-08-2026, que necesita su propio test de replay antes de tocar nada).
- Ningún `TriggerDisposition.REFUSED_STRATEGY` en el ledger de un viaje real.

## Riesgo

**Alto.** Toca la puerta única de armado (`DET-STRATEGY-GATE-001`) y la atribución de vehículo, con
un fallo de campo documentado detrás. No entrar sin un replay que reproduzca el 01-08-2026 primero.
