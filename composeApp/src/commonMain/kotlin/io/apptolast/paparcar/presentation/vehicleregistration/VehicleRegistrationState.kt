package io.apptolast.paparcar.presentation.vehicleregistration

import io.apptolast.paparcar.domain.model.CarbodyType
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
    /**
     * Body-shape category. For CAR vehicles this is the primary classification
     * (size is derived from it via [CarbodyType.sizeCategory]). Null when
     * inference has not run yet or [vehicleType] is non-CAR.
     */
    val carbodyType: CarbodyType? = null,
    /**
     * Length-based size. For CAR vehicles, derived from [carbodyType]. For
     * MOTORCYCLE / SCOOTER / BIKE, forced to [VehicleSize.MOTORCYCLE]. Null
     * only while the form is still empty.
     */
    val sizeCategory: VehicleSize? = null,
    /**
     * True when the user explicitly picked a body type that differs from the
     * automatic inference. Drives the "manual override" badge on the Hero card
     * and disables re-inference until the user changes brand or model.
     */
    val isCarbodyManualOverride: Boolean = false,
    /**
     * High-level vehicle category. Drives detection-strategy resolution:
     * SCOOTER / BIKE bypass the Coordinator entirely. Required from the user
     * — no implicit CAR default in the UI; existing vehicles without a stored
     * type fall back to CAR silently via the data mapper. [BUG-SCOOTER-001]
     */
    val vehicleType: VehicleType? = null,
    val showBrandModelOnSpot: Boolean = false,
    val isSaving: Boolean = false,
    val isDeleting: Boolean = false,
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

    /** True when the vehicle is a car-like body and therefore expects a [carbodyType]. */
    val expectsCarbody: Boolean get() = vehicleType == VehicleType.CAR

    /**
     * CTA is enabled when:
     *  - brand is filled (catalog or custom)
     *  - if catalog brand, model is also filled (custom brands allow blank model)
     *  - for CAR: a carbody is selected (auto-inferred or manual)
     *  - for MOTORCYCLE / SCOOTER / BIKE: size is already forced to MOTORCYCLE
     */
    val canSubmit: Boolean
        get() = brand.isNotBlank() &&
                (isBrandOther || model.isNotBlank()) &&
                sizeCategory != null &&
                (!expectsCarbody || carbodyType != null)

    /** Drives inline error on the brand field — only after the user has started filling the form. */
    val brandError: Boolean
        get() = hasInteractedWithForm && brand.isBlank()
}
