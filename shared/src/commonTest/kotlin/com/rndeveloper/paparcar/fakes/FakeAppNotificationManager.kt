package com.rndeveloper.paparcar.fakes

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager

open class FakeAppNotificationManager : AppNotificationManager {

    var parkingSpotSavedCallCount = 0
    var parkingConfirmationCallCount = 0

    /** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The place the last posted question was about. */
    var lastPromptCandidate: GpsPoint? = null

    /** The street the last posted question named, or null when it named none. */
    var lastPromptStreet: String? = null
    var parkingSavedConfirmCallCount = 0
    val dismissedIds: MutableList<Int> = mutableListOf()

    /**
     * Ordered log of operations targeting [AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID].
     * Each entry is either `"dismiss"` or `"savedConfirm"` — the [showParkingSaved] (manual save
     * on a different id) and other notifications are not recorded here.
     *
     * Used to assert that after auto-confirm, the post-save card is the *last* op on the id,
     * i.e. nothing dismissed it afterwards. [REFACTOR-300 follow-up]
     */
    val confirmationNotifOps: MutableList<String> = mutableListOf()

    override fun showParkingConfirmation(
        score: Float,
        vehicleName: String?,
        candidate: GpsPoint?,
        street: String?,
    ) {
        lastPromptCandidate = candidate
        lastPromptStreet = street
        parkingConfirmationCallCount++
    }

    override fun showParkingSaved(latitude: Double, longitude: Double) {
        parkingSpotSavedCallCount++
    }

    override fun showParkingSavedConfirm(
        parkingId: String,
        vehicleName: String?,
        latitude: Double,
        longitude: Double,
        userAnswered: Boolean,
    ) {
        parkingSavedConfirmCallCount++
        confirmationNotifOps.add("savedConfirm")
        // [NOTIF-ONE-QUESTION-DESERVES-ONE-NOTIFICATION-001] Recorded because the whole fix is in
        // this flag: the card is the same, what changes is whether it asks again.
        savedConfirmUserAnswered.add(userAnswered)
    }

    /** One entry per [showParkingSavedConfirm], in call order. */
    val savedConfirmUserAnswered = mutableListOf<Boolean>()

    /** [DET-AR-FIRST-001] "Where did you leave your car?" nudge invocations. */
    var markParkingNudgeCallCount = 0

    /** [DET-NUDGE-PERSIST-001] Context of the last nudge ask, for provenance assertions. */
    var lastMarkParkingNudgeSource: String? = null
    var lastMarkParkingNudgeVehicleId: String? = null
    var lastMarkParkingNudgePersistPending: Boolean? = null

    override fun showMarkParkingNudge(source: String?, vehicleId: String?, persistPending: Boolean) {
        markParkingNudgeCallCount++
        lastMarkParkingNudgeSource = source
        lastMarkParkingNudgeVehicleId = vehicleId
        lastMarkParkingNudgePersistPending = persistPending
    }

    // ── Push surfaces [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001] ─────────────────────────────────
    /** Zone name of every free-spot card posted, in order. */
    val zoneSpotAlerts: MutableList<String> = mutableListOf()

    /** `authorName to addressLine` of every "the group parked it" card, in order. */
    val sharedCarParkedCards: MutableList<Pair<String?, String?>> = mutableListOf()

    /** `authorName` of every "the car is on the move" card, in order. */
    val sharedCarLeftCards: MutableList<String?> = mutableListOf()

    override fun showZoneSpotAlert(
        zoneName: String,
        latitude: Double,
        longitude: Double,
        isProvisional: Boolean,
    ) {
        zoneSpotAlerts.add(zoneName)
    }

    override fun showSharedCarParked(
        authorName: String?,
        addressLine: String?,
        latitude: Double,
        longitude: Double,
    ) {
        sharedCarParkedCards.add(authorName to addressLine)
    }

    override fun showSharedCarLeft(authorName: String?) {
        sharedCarLeftCards.add(authorName)
    }

    override fun updateDetectionVehicle(vehicleName: String, notifId: Int) = Unit

    override fun showPermissionRevoked() = Unit

    override fun showDebug(message: String) = Unit

    final override fun dismiss(notificationId: Int) {
        dismissedIds.add(notificationId)
        if (notificationId == AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID) {
            confirmationNotifOps.add("dismiss")
        }
    }
}
