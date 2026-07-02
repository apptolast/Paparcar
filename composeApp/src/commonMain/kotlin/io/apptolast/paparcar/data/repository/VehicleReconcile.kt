package io.apptolast.paparcar.data.repository

import io.apptolast.paparcar.data.datasource.local.room.VehicleEntity

/**
 * Merges the local Room rows with the remote (Firestore) snapshot WITHOUT clobbering un-synced local
 * edits — the fix for the blind remote-wins overwrite (`deleteByUser` + `upsertAll`) that reverted an
 * offline change on the next cold-start sync. [SYNC-RECONCILE-001]
 *
 * Per id:
 *  - Local row is `pendingSync` AND strictly newer than remote → **keep local** (an offline edit the
 *    server hasn't caught up to yet). Once the server reflects it (`remote.updatedAt >= local.updatedAt`)
 *    the row is taken from remote again → the pending flag **self-heals** even if the coroutine that
 *    would have cleared it on ack died (process kill), and multi-device edits still converge.
 *  - Otherwise → **take remote**, preserving on-device-only fields (`licensePlate`) from the local row.
 *  - Local-only rows (absent from remote): kept ONLY if `pendingSync` (created offline, not yet
 *    uploaded); a clean local-only row means a remote deletion → dropped.
 *
 * The caller still re-applies the single-active invariant on the result.
 */
fun reconcileVehicles(
    local: List<VehicleEntity>,
    remote: List<VehicleEntity>,
): List<VehicleEntity> {
    val localById = local.associateBy { it.id }
    val remoteById = remote.associateBy { it.id }

    val merged = remote.map { r ->
        val l = localById[r.id]
        if (l != null && l.pendingSync && l.updatedAt > r.updatedAt) {
            l
        } else {
            // On-device-only fields never travel through Firestore — carry them over from local so a
            // sync doesn't blank them (e.g. licensePlate). [VEHICLES-001]
            r.copy(licensePlate = l?.licensePlate ?: r.licensePlate)
        }
    }

    val localOnlyPending = local.filter { it.id !in remoteById && it.pendingSync }

    return merged + localOnlyPending
}
