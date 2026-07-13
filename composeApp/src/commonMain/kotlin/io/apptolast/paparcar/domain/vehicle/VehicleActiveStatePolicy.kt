package io.apptolast.paparcar.domain.vehicle

/**
 * Pure decisions enforcing the "at most one active vehicle" invariant.
 *
 * Keeping these out of `VehicleRepositoryImpl` gives the invariant a single named home and lets it
 * be unit-tested in isolation — the repository only wires Room + Firestore IO around the verdicts.
 * [ARCH-CLEANUP-004]
 */
object VehicleActiveStatePolicy {

    /**
     * Enforces "at most one active item": when more than one is active, keep the FIRST active one
     * (insertion order) and deactivate the rest; otherwise returns [items] unchanged. Pure and
     * order-preserving. Used to reconcile a remote set that may carry several actives.
     */
    inline fun <T> normalizeSingleActive(
        items: List<T>,
        isActive: (T) -> Boolean,
        deactivate: (T) -> T,
    ): List<T> {
        if (items.count(isActive) <= 1) return items
        var kept = false
        return items.map { item ->
            when {
                !isActive(item) -> item
                !kept -> item.also { kept = true }
                else -> deactivate(item)
            }
        }
    }

    /**
     * The id to promote to active/default after the current default vehicle is deleted: the first
     * of [remainingIds], or `null` when none remain. Pure.
     */
    fun promotionTarget(remainingIds: List<String>): String? = remainingIds.firstOrNull()

    /**
     * [AUDIT-M11-001] Whether a vehicle being saved should end up ACTIVE:
     *  - **editing** an existing vehicle preserves its current active flag ([existingIsActive]);
     *  - **registering a new** vehicle becomes active only when it is the user's FIRST
     *    (`!userHasVehicles`) — the single-active invariant means a new car never steals the flag
     *    from an existing one silently.
     *
     * Was inlined in `VehicleRegistrationViewModel` as an untested `when`. Pure and testable here.
     */
    fun shouldBeActiveOnSave(
        isEditing: Boolean,
        existingIsActive: Boolean,
        userHasVehicles: Boolean,
    ): Boolean = if (isEditing) existingIsActive else !userHasVehicles
}
