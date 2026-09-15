package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.domain.repository.ZoneAlertSubscriptionRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import com.rndeveloper.paparcar.domain.zone.ZoneAlertSubscription
import dev.gitlive.firebase.firestore.FirebaseFirestore
import kotlinx.serialization.Serializable

/**
 * The wire shape of a row in `zoneAlertSubs`.
 * [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * ⛔ Field names are a CONTRACT with `functions/src/index.ts`, which reads them by hand. Renaming
 * one here and not there does not fail a build, a test or a lint — it silently stops every alert.
 */
@Serializable
internal data class ZoneAlertSubDto(
    val uid: String = "",
    val zoneId: String = "",
    val zoneName: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 0.0,
    val cells: List<String> = emptyList(),
    val tokens: List<String> = emptyList(),
    val expiresAt: Long = 0L,
)

/** Firestore behind [ZoneAlertSubscriptionRepository]. */
class FirestoreZoneAlertSubscriptionRepository(
    private val firestore: FirebaseFirestore,
) : ZoneAlertSubscriptionRepository {

    private val collection get() = firestore.collection(COLLECTION)

    override suspend fun replaceAll(
        uid: String,
        subscriptions: List<ZoneAlertSubscription>,
    ): Result<Unit> = runCatching {
        // ⛔ Read constrained to our own uid — the ONLY query the security rules will accept here.
        // An `array-contains` sweep of a cell is rejected before it runs, which is what keeps this
        // index from being a map of where people live.
        val existing = collection
            .where { "uid" equalTo uid }
            .get()
            .documents
            .map { it.id }
            .toSet()

        val wanted = subscriptions.associateBy { it.id }

        // The deletions are the half that matters: turning a zone off, silencing the channel or
        // losing Plus all arrive here as a SHORTER list, and a write-only sync would leave the old
        // rows on the server pushing about a zone the user already closed.
        (existing - wanted.keys).forEach { staleId ->
            collection.document(staleId).delete()
        }

        wanted.values.forEach { sub ->
            collection.document(sub.id).set(
                ZoneAlertSubDto(
                    uid = sub.uid,
                    zoneId = sub.zoneId,
                    zoneName = sub.zoneName,
                    latitude = sub.latitude,
                    longitude = sub.longitude,
                    radiusMeters = sub.radiusMeters.toDouble(),
                    cells = sub.cells,
                    tokens = sub.tokens,
                    expiresAt = sub.expiresAt,
                ),
            )
        }

        PaparcarLogger.d(TAG) {
            "zone alert subs synced: ${wanted.size} kept, ${(existing - wanted.keys).size} removed"
        }
    }

    private companion object {
        const val TAG = "ZoneAlertSubs"

        /** ⛔ Mirrored in `firestore.rules` and in `functions/src/index.ts`. */
        const val COLLECTION = "zoneAlertSubs"
    }
}
