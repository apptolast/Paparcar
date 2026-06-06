package io.apptolast.paparcar.presentation.vehicleregistration

import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType

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
    /**
     * High-level vehicle category. Drives detection-strategy resolution:
     * SCOOTER / BIKE bypass the Coordinator entirely. Required from the user
     * — no implicit CAR default in the UI; existing vehicles without a stored
     * type fall back to CAR silently via the data mapper. [BUG-SCOOTER-001]
     */
    val vehicleType: VehicleType? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isSaving: Boolean = false,
    val editingVehicleId: String? = null,
    /** True when sizeCategory was derived automatically from a catalog brand+model pair. */
    val isSizeAutoDetected: Boolean = false,
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
    /** Optional license plate (e.g. "1234 ABC"). On-device only — never sent to Firestore. */
    val licensePlate: String = "",
) {
    /**
     * The "Car N" placeholder shown in the name field and used as the default name when
     * all text fields are blank at submit time.
     */
    val defaultNamePlaceholderIndex: Int get() = existingVehicleCount + 1

    /** True when there is more than one vehicle — prevents deleting the last one. */
    val canDelete: Boolean get() = existingVehicleCount > 1

    /**
     * CTA is enabled when size is chosen and brand is filled.
     * For catalog brands, model is also required. For "Other" brand, model is optional.
     */
    val canSubmit: Boolean
        get() = sizeCategory != null &&
                brand.isNotBlank() &&
                (isBrandOther || model.isNotBlank())

    /** Drives inline error on the brand field — only after the user has started filling the form. */
    val brandError: Boolean
        get() = hasInteractedWithForm && brand.isBlank()
}