package io.apptolast.paparcar.data.repository

import dev.gitlive.firebase.firestore.FirebaseFirestore
import io.apptolast.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_CONFIG
import io.apptolast.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_DIAGNOSTICS
import io.apptolast.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_EVENTS
import io.apptolast.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_REPORTS
import io.apptolast.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_REPORTS_ROOT
import io.apptolast.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_REPORT_CHUNKS
import io.apptolast.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_SESSIONS
import io.apptolast.paparcar.data.datasource.remote.DiagnosticsFirestoreSchema.COLLECTION_UI_LOCATION
import io.apptolast.paparcar.domain.repository.DiagnosticsRepository

/**
 * Erases the remote diagnostics telemetry of a user: every session header + its events
 * subcollection, every uiLocation sample, every problem report (+ its chunks), and the
 * `diagnostics_config/{uid}` opt-in flag. [ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001]
 *
 * Firestore does not cascade subcollection deletes from a client SDK, so the sweep iterates
 * `sessions → events` doc by doc — bounded volume: one uid's telemetry, already capped by the
 * 7-day retention sweep, and only opted-in uids emit at all. The `diagnostics/{uid}` parent doc
 * is never created by the loggers (its subcollections hang off a "missing parent"), so there is
 * nothing to delete at that path itself.
 */
class DiagnosticsRepositoryImpl(
    private val firestore: FirebaseFirestore,
) : DiagnosticsRepository {

    override suspend fun deleteAllData(userId: String): Result<Unit> = runCatching {
        val userRoot = firestore.collection(COLLECTION_DIAGNOSTICS).document(userId)

        for (session in userRoot.collection(COLLECTION_SESSIONS).get().documents) {
            session.reference.collection(COLLECTION_EVENTS).get().documents.forEach { event ->
                event.reference.delete()
            }
            session.reference.delete()
        }
        userRoot.collection(COLLECTION_UI_LOCATION).get().documents.forEach { sample ->
            sample.reference.delete()
        }
        // Problem reports carry the shipped local log — as owned as the telemetry itself.
        // [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
        val reportsRoot = firestore.collection(COLLECTION_REPORTS_ROOT).document(userId)
        for (report in reportsRoot.collection(COLLECTION_REPORTS).get().documents) {
            report.reference.collection(COLLECTION_REPORT_CHUNKS).get().documents.forEach { chunk ->
                chunk.reference.delete()
            }
            report.reference.delete()
        }
        firestore.collection(COLLECTION_CONFIG).document(userId).delete()
    }
}
