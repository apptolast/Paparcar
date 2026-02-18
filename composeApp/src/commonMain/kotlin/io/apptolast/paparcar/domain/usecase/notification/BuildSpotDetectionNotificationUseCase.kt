package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.data.notification.AppNotificationManager

class BuildSpotDetectionNotificationUseCase(private val appNotificationManager: AppNotificationManager) {

    operator fun invoke(): Any {
        return appNotificationManager.buildDetectionNotification()
    }
}
