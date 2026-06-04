package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.notification.AppNotificationManager

open class FakeAppNotificationManager : AppNotificationManager {

    var parkingSpotSavedCallCount = 0
    var parkingConfirmationCallCount = 0
    val dismissedIds: MutableList<Int> = mutableListOf()

    override fun showParkingConfirmation(score: Float, vehicleName: String?) {
        parkingConfirmationCallCount++
    }

    override fun showParkingSaved(latitude: Double, longitude: Double) {
        parkingSpotSavedCallCount++
    }

    override fun showSpotPublished(latitude: Double, longitude: Double) = Unit

    override fun showSpotUploading() = Unit

    override fun updateDetectionVehicle(vehicleName: String, notifId: Int) = Unit

    override fun showPermissionRevoked() = Unit

    override fun showDebug(message: String) = Unit

    final override fun dismiss(notificationId: Int) {
        dismissedIds.add(notificationId)
    }
}
