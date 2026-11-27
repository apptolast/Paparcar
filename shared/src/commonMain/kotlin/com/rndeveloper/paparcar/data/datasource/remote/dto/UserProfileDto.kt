package com.rndeveloper.paparcar.data.datasource.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDto(
    val userId: String = "",
    val email: String? = null,
    val displayName: String? = null,
    val photoUrl: String? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val defaultVehicleId: String? = null,
    /** When the user closed the guided checklist, or null. Absent on every doc written before
     *  [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] → null → "still
     *  wants the tutorial", which is what those accounts were. */
    val firstStepsDismissedAt: Long? = null,

    // ── Detection state. [DET-A-SWITCH-THAT-IS-OFF-LEAVES-NO-TRACE-001] ──────────────────────────
    //
    // ⛔ WRITE-ONLY, and the asymmetry is the design. These four are read back here so the DTO stays
    // a faithful mirror of the document (a `set(profile)` from a DTO that dropped them would wipe
    // them — the exact class of bug `FirestoreDeserializerParityTest` exists to catch), but they do
    // NOT reach `UserProfile` in domain and nothing in the app consumes them. The moment something
    // did, a stale remote value would start competing with the live permission state of the device,
    // which is the one source of truth that cannot be wrong.
    //
    // All four nullable with the same meaning of absence as `firstStepsDismissedAt`: missing on
    // every doc written before this ticket → null → **unknown**, never "off".

    /** Stored intent of the Settings master switch. Never read as "detection works" — cross it with
     *  [detectionBlockers] or the two populations collapse.
     *  [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001] */
    val autoDetectParking: Boolean? = null,
    /** PRODUCER permissions still missing, by `RequiredPermission` name. Empty list = nothing
     *  blocks detection; null = never synced. This is what separates "turned it off" from
     *  "cannot run". */
    val detectionBlockers: List<String>? = null,
    /** [DOZE-001] Battery whitelist granted. Absent on iOS, where the concept does not exist. */
    val batteryOptimizationExempt: Boolean? = null,
    /** When this snapshot was taken. A snapshot with no date cannot be told apart from a permission
     *  revoked three months ago, which would make the other three unreadable. */
    val detectionStateUpdatedAt: Long? = null,

    // ── Identidad del install [DIAG-A-ROW-MUST-SAY-WHICH-PLATFORM-WROTE-IT-001] ─────────────────
    //
    // Misma regla write-only y mismo significado de la ausencia que los cuatro de arriba: null =
    // cuenta que todavía no ha publicado su estado desde una versión con esto.

    /** `"android"` o `"ios"`. Firestore lo comparten las dos plataformas y hasta este ticket ningún
     *  documento decía de cuál venía — ni siquiera se podía deducir, porque los campos que se le
     *  parecen (`batteryOptimizationExempt`) hablan de otra cosa. */
    val platform: String? = null,
    /** Last app version synced by this account's device, also refreshed independently of detection.
     *  With multiple devices the last writer wins; this is not the version of a historic snapshot.
     *  [SYNC-THE-ACCOUNT-KNOWS-THE-APP-VERSION-001] */
    val appVersion: String? = null,

    // ── Procedencia de la instalación [MKT-INSTALL-ATTRIBUTION-CHANNEL-001] ──────────────────────
    //
    // Mismo patrón WRITE-ONLY que el bloque de detección de arriba: se leen de vuelta sólo para que
    // un `set(profile)` no los borre; el dominio nunca los consume. Null = instalación anterior a
    // este ticket, o el referrer de Play nunca se pudo leer (sin Play Store en el dispositivo).

    /** [com.rndeveloper.paparcar.domain.attribution.InstallChannel.wire] — "meta_paid",
     *  "meta_organic_ig", "owned_link", "play_organic", "unknown", o null si no se ha resuelto. */
    val installChannel: String? = null,
    val installSource: String? = null,
    val installMedium: String? = null,
    val installCampaign: String? = null,
    /** Sólo no-null cuando el referrer llevaba el bloque de Meta y se descifró con éxito. */
    val installMetaCampaignId: String? = null,
    val installMetaAdsetId: String? = null,
    val installMetaAdId: String? = null,
    /** La query cruda del referrer de Play, sin parsear — para poder re-clasificar en el futuro
     *  sin volver a leer Play (sólo se puede leer una vez por instalación). */
    val installRawReferrer: String? = null,
    val installAttributionResolvedAt: Long? = null,
)
