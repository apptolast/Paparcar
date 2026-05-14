package io.apptolast.paparcar.presentation.vehicle

import io.apptolast.paparcar.domain.model.VehicleSize

data class VehicleRegistrationState(
    val brand: String = "",
    val model: String = "",
    val sizeCategory: VehicleSize? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isSaving: Boolean = false,
    val editingVehicleId: String? = null,
    /**
     * UUID generado en el primer intento de guardado de un vehículo nuevo (no edición).
     * Se memoiza aquí para que un reintento tras fallo de red (mismo VM, otro tap) reuse el
     * mismo id en lugar de generar uno nuevo — Firestore `.set()` con el mismo doc id es
     * idempotente y no duplica. Reset implícito al destruir el VM. [VEHICLES-002]
     */
    val pendingNewVehicleId: String? = null,
)