package io.apptolast.paparcar.data.datasource.remote

/**
 * Firestore paths of the diagnostics telemetry, shared by the writers
 * ([FirestoreDetectionEventLogger], [FirestoreUiLocationLogger]) and the account-deletion eraser
 * ([io.apptolast.paparcar.data.repository.DiagnosticsRepositoryImpl]). One source of truth so the
 * erasure sweep can never drift from where the loggers actually write.
 * [ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001]
 */
internal object DiagnosticsFirestoreSchema {
    /** `diagnostics/{userId}/…` — per-user telemetry root (the `{userId}` doc itself is never created). */
    const val COLLECTION_DIAGNOSTICS = "diagnostics"

    /** `diagnostics/{userId}/sessions/{sessionId}` — one header doc per detection session/ledger. */
    const val COLLECTION_SESSIONS = "sessions"

    /** `…/sessions/{sessionId}/events/{autoId}` — one doc per detection event. */
    const val COLLECTION_EVENTS = "events"

    /** `diagnostics/{userId}/uiLocation/{autoId}` — consumer map-location samples. [UI-LOC-FOREGROUND-001] */
    const val COLLECTION_UI_LOCATION = "uiLocation"

    /** `diagnostics_config/{userId}` — admin-toggled opt-in flag the client reads to self-gate. */
    const val COLLECTION_CONFIG = "diagnostics_config"

    /** `diagnostics_reports/{userId}/…` — user-initiated problem reports (the `{userId}` doc
     *  itself is never created). [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] */
    const val COLLECTION_REPORTS_ROOT = "diagnostics_reports"

    /** `diagnostics_reports/{userId}/reports/{createdAtMs}` — one header doc per report. */
    const val COLLECTION_REPORTS = "reports"

    /** `…/reports/{createdAtMs}/chunks/{n}` — base64 gzip pieces of the shipped local log. */
    const val COLLECTION_REPORT_CHUNKS = "chunks"

    /** Boolean field on `diagnostics_config/{userId}`: remote logging enabled for this uid. */
    const val FIELD_ENABLED = "enabled"
}
