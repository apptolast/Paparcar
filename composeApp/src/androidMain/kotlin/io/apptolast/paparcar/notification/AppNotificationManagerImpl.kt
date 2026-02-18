package io.apptolast.paparcar.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import io.apptolast.paparcar.data.notification.AppNotificationManager

class AppNotificationManagerImpl(
    private val context: Context,
    private val notificationManager: NotificationManager
) : AppNotificationManager {

    init {
        createNotificationChannels()
    }

    override fun buildDetectionNotification(): Notification {
        return NotificationCompat.Builder(context, DETECTION_CHANNEL_ID)
            .setContentTitle("Paparcar")
            .setContentText("Detección de spot iniciada")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .build()
    }

    override fun buildUploadNotification(): Notification {
        return NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle("Paparcar")
            .setContentText("Subiendo nuevo spot...")
            .setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true)
            .build()
    }

    override fun showDebugNotification(message: String) {
        val notification = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setContentTitle("Paparcar Debug")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .build()
        notificationManager.notify(AppNotificationManager.DEBUG_NOTIFICATION_ID, notification)
    }

    override fun dismiss(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    private fun createNotificationChannels() {
        val detectionChannel = NotificationChannel(
            DETECTION_CHANNEL_ID,
            "Detección de Spot",
            NotificationManager.IMPORTANCE_LOW
        )
        val uploadChannel = NotificationChannel(
            UPLOAD_CHANNEL_ID,
            "Subida de Spot",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val debugChannel = NotificationChannel(
            DEBUG_CHANNEL_ID,
            "Debug",
            NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannels(listOf(detectionChannel, uploadChannel, debugChannel))
    }

    companion object {
        const val DETECTION_CHANNEL_ID = "detection_channel"
        const val UPLOAD_CHANNEL_ID = "upload_channel"
        const val DEBUG_CHANNEL_ID = "debug_channel"
    }
}
