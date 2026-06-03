package io.apptolast.paparcar.notification

import io.apptolast.paparcar.domain.notification.AppNotificationManager

class FakeAppNotificationManager : AppNotificationManager {
    override fun showParkingConfirmation(score: Float, vehicleName: String?) {}
    override fun showParkingSaved(latitude: Double, longitude: Double) {}
    override fun showSpotPublished(latitude: Double, longitude: Double) {}
    override fun showSpotUploading() {}
    override fun showPermissionRevoked() {}
    override fun showDebug(message: String) {}
    override fun dismiss(notificationId: Int) {}
    override fun updateDetectionVehicle(vehicleName: String, notifId: Int) {}
}
