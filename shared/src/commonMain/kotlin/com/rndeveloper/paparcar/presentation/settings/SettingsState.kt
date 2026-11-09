package com.rndeveloper.paparcar.presentation.settings

import com.rndeveloper.paparcar.appVersion as platformAppVersion
import com.rndeveloper.paparcar.domain.model.DetectionReliabilityLevel
import com.rndeveloper.paparcar.domain.model.UserProfile
import com.rndeveloper.paparcar.domain.permissions.RequiredPermission

data class SettingsState(
    val userProfile: UserProfile? = null,
    val autoDetectParking: Boolean = true,
    val notifyParkingDetected: Boolean = true,
    /** Live Plus entitlement — the Plus row shows ACTIVO instead of inviting. [MONEY-001] */
    val isPlus: Boolean = false,
    /** Master gate for the Plus zone-alert notifications. [MONEY-001] */
    val notifyZoneSpots: Boolean = true,
    /** Master gate for the Plus fuel-price alerts — a cheaper pump nearby, and what a followed
     *  station did overnight. [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] */
    val notifyFuelPrices: Boolean = true,
    /** El resumen del mes cerrado — el único aviso que la app da sin que nadie pregunte, y el
     *  único de los cuatro que no tenía interruptor.
     *  [MONEY-A-CARD-THAT-NEVER-APPEARS-IS-NEVER-SOLD-001] */
    val notifyMonthRecap: Boolean = true,
    /** Cloud copy of the ROUTE GEOMETRY — opt-in, Plus-gated, default OFF: trips stay on this
     *  phone unless the user says otherwise. [PRIV-A-TRACE-IS-NOT-A-STAT-001] */
    val syncRouteGeometry: Boolean = false,
    /** True when this platform + launcher can pin the parked-car widget from in-app — gates the
     *  "add the widget" row. [WIDGET-ACTIONS-002] */
    val canPinWidget: Boolean = false,
    /** This build has an ad lane — gates the "Ad measurement" row; no lane, no row.
     *  [MKT-AD-MEASUREMENT-ASKS-ITS-OWN-CONSENT-001] */
    val adMeasurementAvailable: Boolean = false,
    /** The user said yes to ad measurement on this install. Off until they do. */
    val adMeasurementAllowed: Boolean = false,
    // Single source: the platform build version. The old "1.0.0" literal was a second,
    // lying default that never matched the APK. [SETTINGS-AUDIT-REMEDIATION-001]
    val appVersion: String = platformAppVersion,
    val showDeleteAccountConfirmation: Boolean = false,
    val isDeletingAccount: Boolean = false,
    /** "Report a problem" consent dialog — says WHAT is shipped before anything leaves the
     *  device. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] */
    val showSendDiagnosticsConfirmation: Boolean = false,
    /** Upload in flight; the consent dialog stays up showing progress until it resolves. */
    val isSendingDiagnostics: Boolean = false,
    /** ¿Está confirmado el correo de la cuenta? `null` = la sesión aún no se ha leído, y un badge
     *  en blanco mentiría.
     *
     *  ⛔ Sale de la SESIÓN (`UserSession.isEmailVerified`), **nunca** de [userProfile]: duplicar
     *  en Firestore una verdad que vive en auth deja dos fuentes capaces de discrepar, y la del
     *  perfil puede servir el valor viejo tras un sync. [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001] */
    val emailVerified: Boolean? = null,
    /** Envío en vuelo: el CTA pasa a cargando. */
    val isSendingEmailVerification: Boolean = false,
    /** Ya se mandó en esta visita a Ajustes — el CTA queda deshabilitado hasta volver a entrar.
     *  Estado y no cuenta atrás **a propósito**: lo único que hay que evitar es que se machaque el
     *  botón, y un temporizador exigiría un tick por segundo en una pantalla de configuración.
     *  Si Firebase corta igualmente, su `TooManyRequests` sale por snackbar. */
    val emailVerificationSent: Boolean = false,
    /**
     * What the user is writing about the failure. Survives dismissing the dialog and a failed
     * upload — it is cleared only once the report is actually sent. Losing four typed sentences to
     * a stray tap outside the dialog, or to the network, is what stops someone from trying again.
     * [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001]
     */
    val diagnosticsMessage: String = "",

    // ── Detection & permissions (SETTINGS-REMODEL-001) ───────────────────────
    /** Detection-required permissions not yet granted (CORE + PRODUCER). Empty = all held. */
    val missingDetectionPermissions: Set<RequiredPermission> = emptySet(),
    /** System GPS master toggle. Detection can't work with location services off, even if the
     *  runtime permission is granted — surfaced in the health row alongside missing permissions. */
    val isLocationServicesEnabled: Boolean = true,
    /** Doze exemption (Android-only, DOZE-001). Optional "improve detection" row. */
    val isBatteryOptimizationExempt: Boolean = false,
    /** Active vehicle id — target for the "Configure Bluetooth" deep-link. Null = no vehicle yet. */
    val activeVehicleId: String? = null,
    /** The active vehicle has a paired car-Bluetooth device (`bluetoothDeviceId != null`). */
    val btDeviceConfigured: Boolean = false,
    /** How dependable detection is once permitted (single evaluator, [DET-RELIABILITY-001]).
     *  Only REDUCED gets proactive UI; GOOD is the pre-emission default so the health row never
     *  flashes amber before the first report arrives. */
    val detectionReliability: DetectionReliabilityLevel = DetectionReliabilityLevel.GOOD,
) {
    /** Detection permission health is green only when nothing is missing AND GPS is on. */
    val detectionHealthy: Boolean
        get() = missingDetectionPermissions.isEmpty() && isLocationServicesEnabled

    /**
     * What the master switch (and everything that hangs off it) is allowed to CLAIM: the user wants
     * detection AND detection can actually run. [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]
     *
     * [autoDetectParking] alone is the stored *intent*, and it defaults to true — so a switch bound
     * to it read "on" on a fresh install, right above its own health row saying "Missing background
     * location". Same shape as the zone-alerts row a section below (`isPlus && notifyZoneSpots`): a
     * switch is on only while what it promises is in force, and flipping it on when it isn't opens
     * the flow that grants what's missing.
     *
     * The intent itself is NOT overwritten — the detection lanes still gate on it (that is the
     * F3 sentry gate), and forgetting a deliberate OFF because a permission lapsed would silently
     * re-arm detection behind the user's back.
     */
    val autoDetectActive: Boolean
        get() = autoDetectParking && detectionHealthy

    /** Permissions fine but the device environment untrusted — the amber reliability case.
     *  Permission health takes precedence: a hard blocker outranks a reliability warning. */
    val detectionReliabilityReduced: Boolean
        get() = detectionHealthy && detectionReliability == DetectionReliabilityLevel.REDUCED
}
