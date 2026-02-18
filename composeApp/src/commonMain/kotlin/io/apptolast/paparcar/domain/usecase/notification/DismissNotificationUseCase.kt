package io.apptolast.paparcar.domain.usecase.notification

import io.apptolast.paparcar.data.notification.AppNotificationManager

class DismissNotificationUseCase(private val appNotificationManager: AppNotificationManager) {

    operator fun invoke(notificationId: Int) {
        appNotificationManager.dismiss(notificationId)
    }
}
