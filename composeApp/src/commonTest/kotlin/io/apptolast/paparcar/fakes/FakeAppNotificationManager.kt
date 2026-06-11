package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.notification.AppNotificationManager

open class FakeAppNotificationManager : AppNotificationManager {

    var parkingSpotSavedCallCount = 0
    var parkingConfirmationCallCount = 0
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

    override fun showParkingConfirmation(score: Float, vehicleName: String?) {
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

    override fun showSpotPublished(latitude: Double, longitude: Double) = Unit

    override fun showSpotUploading() = Unit

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
