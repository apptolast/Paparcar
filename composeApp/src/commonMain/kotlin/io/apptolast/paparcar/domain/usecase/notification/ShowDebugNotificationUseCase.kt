package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.data.notification.AppNotificationManager

class ShowDebugNotificationUseCase(private val appNotificationManager: AppNotificationManager) {

    operator fun invoke(message: String) {
        appNotificationManager.showDebugNotification(message)
    }
}
