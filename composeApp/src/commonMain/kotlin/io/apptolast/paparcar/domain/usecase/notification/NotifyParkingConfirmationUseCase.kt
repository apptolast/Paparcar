package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.notification.AppNotificationManager

class NotifyParkingConfirmationUseCase(
    private val notificationPort: AppNotificationManager,
) {
    operator fun invoke(confidence: ParkingConfidence) {
        when (confidence) {
            is ParkingConfidence.Low -> notificationPort.showParkingConfirmation(0f)
            is ParkingConfidence.Medium -> notificationPort.showParkingConfirmation(confidence.score)
            else -> Unit
        }
    }
}