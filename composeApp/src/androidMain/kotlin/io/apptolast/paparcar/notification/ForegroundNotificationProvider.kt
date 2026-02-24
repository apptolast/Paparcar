package io.apptolast.paparcar.notification

import android.app.Notification

interface ForegroundNotificationProvider {
    fun buildDetectionNotification(): Notification
}