package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.notification.AppNotificationManager

class StubAppNotificationManager : AppNotificationManager {
    override fun showParkingConfirmation(score: Float, vehicleName: String?) {}
    override fun showParkingSpotSaved(latitude: Double, longitude: Double) {}
    override fun showSpotUploading() {}
    override fun showDebug(message: String) {}
    override fun dismiss(notificationId: Int) {}
}
