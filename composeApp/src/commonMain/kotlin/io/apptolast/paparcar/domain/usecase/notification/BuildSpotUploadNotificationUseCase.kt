package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.data.notification.AppNotificationManager

class BuildSpotUploadNotificationUseCase(private val appNotificationManager: AppNotificationManager) {

    operator fun invoke(): Any {
        return appNotificationManager.buildUploadNotification()
    }
}
