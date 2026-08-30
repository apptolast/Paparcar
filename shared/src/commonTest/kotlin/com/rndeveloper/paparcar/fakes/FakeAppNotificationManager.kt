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
    ) {
        parkingSavedConfirmCallCount++
        confirmationNotifOps.add("savedConfirm")
    }

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
