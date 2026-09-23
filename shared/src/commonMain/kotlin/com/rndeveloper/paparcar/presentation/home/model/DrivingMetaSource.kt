package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.model.DetectionReadiness
import com.rndeveloper.paparcar.domain.detection.ParkingStrategy
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.presentation.home.DrivingMeta

/**
 * ¿Hay un viaje que contar, y de qué coche? [HOME-A-TAP-THAT-ARMS-MUST-LOOK-ARMED-001]
 *
 * ⛔⛔ El viaje de la UI NO puede depender de que haya llegado un FIX. Lo dependía: `drivingMeta`
 * salía del PUCK, el puck nace del stream de ubicación, y hasta que ese stream emite su primera
 * posición la card del coche vuelve a decir «Sin marcar» — o sea **exactamente lo mismo que decía
 * antes de tocar el botón**, sólo que sin los botones. Reportado en campo: «le daba a *Voy
 * conduciendo* y no hacía nada». La detección SÍ se armaba; lo que no pasaba era nada en pantalla,
 * y en un móvil que tarda en dar el primer fix (interior, frío de GPS, ubicación aproximada de iOS)
 * eso puede ser medio minuto o no llegar nunca.
 *
 * El hecho de que hay un viaje lo dice la DETECCIÓN, no el GPS: `DetectionReadiness.Monitoring`
 * existe desde el instante del arme, en el mismo turno en que el servicio arranca. El fix sólo
 * añade DÓNDE, que es información del mapa.
 *
 * Orden, y por qué:
 *  1. **El puck manda cuando existe** — trae el coche que de verdad salió (resuelto por el servicio
 *     desde la sesión de la geocerca) y la fase congelada del `Candidate`, que es la que impide que
 *     el pin persiga al peatón. Ninguna de las dos cosas se puede re-derivar aquí.
 *  2. **Si no hay puck pero la detección vigila un viaje**, se cuenta igual, nombrando al coche que
 *     el carril vigila. La fase viene de la propia detección: `Driving` es, por definición del
 *     enum, «en movimiento **o recién armado**».
 *  3. Si la detección no está en un viaje, no hay viaje. Un arme que no cuaja se cae solo y esta
 *     línea se apaga con él — que es justo el trato: el botón arma, y lo que no era un viaje muere
 *     sin que nadie tenga que deshacerlo.
 */
fun resolveDrivingMeta(
    puck: DrivingMeta?,
    readiness: DetectionReadiness,
    vehicles: List<Vehicle>,
): DrivingMeta? {
    if (puck != null) return puck
    val monitoring = readiness as? DetectionReadiness.Monitoring ?: return null
    val vehicleId = monitoring.departingVehicleId
        ?: monitoredVehicle(vehicles, monitoring.strategy)?.id
        ?: return null
    return DrivingMeta(vehicleId = vehicleId, phase = monitoring.phase)
}

/**
 * El coche que un carril vigila cuando nadie ha nominado a uno concreto: el emparejado por
 * Bluetooth si el carril es ese, y el activo en cualquier otro caso.
 *
 * ⛔ Vive aquí, y no en el controlador del viaje, porque desde este ticket lo preguntan DOS sitios
 * —el puck del mapa y la línea de la card— y dos copias de esta elección es como el mapa acaba
 * dibujando un coche y la hoja nombrando otro.
 */
fun monitoredVehicle(vehicles: List<Vehicle>, strategy: ParkingStrategy): Vehicle? =
    when (strategy) {
        ParkingStrategy.BLUETOOTH ->
            vehicles.firstOrNull { it.bluetoothDeviceId != null } ?: vehicles.firstOrNull { it.isActive }
        else ->
            vehicles.firstOrNull { it.isActive } ?: vehicles.firstOrNull()
    }
