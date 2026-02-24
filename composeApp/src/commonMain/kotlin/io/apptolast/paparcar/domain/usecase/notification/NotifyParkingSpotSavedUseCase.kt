package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.domain.notification.NotificationPort

class NotifyParkingSpotSavedUseCase(
    private val notificationPort: NotificationPort,
) {
    operator fun invoke(latitude: Double, longitude: Double) {
        notificationPort.showParkingSpotSaved(latitude, longitude)
    }
}