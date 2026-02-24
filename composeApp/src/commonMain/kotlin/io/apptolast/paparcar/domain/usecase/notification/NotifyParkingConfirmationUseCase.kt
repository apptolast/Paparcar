package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.domain.model.ParkingConfidence
import io.apptolast.paparcar.domain.notification.NotificationPort

class NotifyParkingConfirmationUseCase(
    private val notificationPort: NotificationPort,
) {
    operator fun invoke(confidence: ParkingConfidence) {
        when (confidence) {
            is ParkingConfidence.Medium -> notificationPort.showParkingConfirmation(confidence.score)
            else -> Unit
        }
    }
}