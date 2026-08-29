package io.apptolast.paparcar.domain.repository

/**
 * Remote detection/UI telemetry stored under the user's uid (`diagnostics/{uid}` sessions, events
 * and uiLocation samples, plus the `diagnostics_config/{uid}` opt-in flag).
 *
 * This is deliberately ONLY the erasure port: the telemetry is WRITTEN through the logger ports
 * ([io.apptolast.paparcar.domain.diagnostics.DetectionEventLogger] /
 * [io.apptolast.paparcar.domain.diagnostics.UiLocationLogger]), but that data has an owner, and
 * everything with an owner must be deletable with the owner — the [UserScopedRepository] contract
 * that account deletion sweeps. [ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001]
 */
interface DiagnosticsRepository : UserScopedRepository
