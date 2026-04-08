package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.notification.AppNotificationManager

class FakeAppNotificationManager : AppNotificationManager {

    var parkingSpotSavedCallCount = 0
    var parkingConfirmationCallCount = 0

    override fun showParkingConfirmation(score: Float, vehicleName: String?) {
        parkingConfirmationCallCount++
    }

    override fun showParkingSpotSaved(latitude: Double, longitude: Double) {
        parkingSpotSavedCallCount++
    }

    override fun showSpotUploading() = Unit

    override fun showDebug(message: String) = Unit

    override fun dismiss(notificationId: Int) = Unit
}
