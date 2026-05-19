package io.apptolast.paparcar.presentation.vehicle

import io.apptolast.paparcar.domain.model.VehicleSize

data class VehicleRegistrationState(
    /** Optional friendly name chosen by the user (e.g. "My Golf"). */
    val name: String = "",
    /** Selected brand — empty when the user picks "Other…". */
    val brand: String = "",
    /** Whether the user selected "Other…" for brand (shows free-text field). */
    val isBrandOther: Boolean = false,
    /** Selected model — empty when the user picks "Other…". */
    val model: String = "",
    /** Whether the user selected "Other…" for model (shows free-text field). */
    val isModelOther: Boolean = false,
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
    /** Number of vehicles already registered — used to generate the "Car N" default placeholder. */
    val existingVehicleCount: Int = 0,
    /** True after the user first interacts with the form — gates error-state display. */
    val hasInteractedWithForm: Boolean = false,
) {
    /**
     * The "Car N" placeholder shown in the name field and used as the default name when
     * all text fields are blank at submit time.
     */
    val defaultNamePlaceholderIndex: Int get() = existingVehicleCount + 1

    /**
     * CTA is enabled when size is chosen AND at least one of name/brand/model is non-blank.
     * Prevents saving a vehicle with no identifying information.
     */
    val canSubmit: Boolean
        get() = sizeCategory != null &&
                (name.isNotBlank() || brand.isNotBlank() || model.isNotBlank())
}