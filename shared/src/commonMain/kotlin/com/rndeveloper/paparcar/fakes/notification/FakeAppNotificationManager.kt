package com.rndeveloper.paparcar.notification

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager

class FakeAppNotificationManager : AppNotificationManager {
    override fun showParkingConfirmation(
        score: Float,
        vehicleName: String?,
        candidate: GpsPoint?,
        street: String?,
    ) {}
    override fun showParkingSaved(latitude: Double, longitude: Double) {}
    override fun showPermissionRevoked() {}
    override fun showDebug(message: String) {}
    override fun dismiss(notificationId: Int) {}
    override fun updateDetectionVehicle(vehicleName: String, notifId: Int) {}
}
