package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.domain.notification.NotificationPort

class NotifySpotUploadingUseCase(
    private val notificationPort: NotificationPort,
) {
    operator fun invoke() {
        notificationPort.showSpotUploading()
    }
}