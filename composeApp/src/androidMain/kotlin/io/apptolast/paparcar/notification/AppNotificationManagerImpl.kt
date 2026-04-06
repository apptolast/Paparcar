package io.apptolast.paparcar.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import io.apptolast.paparcar.MainActivity
import io.apptolast.paparcar.R
import io.apptolast.paparcar.detection.receiver.ParkingConfirmationReceiver
import io.apptolast.paparcar.domain.notification.AppNotificationManager

class AppNotificationManagerImpl(
    private val context: Context,
    private val notificationManager: NotificationManager,
) : AppNotificationManager, ForegroundNotificationProvider {

    init {
        createNotificationChannels()
    }

    // region ForegroundNotificationProvider

    override fun buildDetectionNotification(): Notification {
        return NotificationCompat.Builder(context, DETECTION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_detection_title))
            .setContentText(context.getString(R.string.notif_detection_text))
            .setSmallIcon(R.drawable.ic_notification_detection)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(buildOpenAppIntent(RC_DETECTION))
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setColor(COLOR_DETECTION)
            .build()
    }

    // endregion

    // region AppNotificationManager

    override fun showParkingConfirmation(score: Float, vehicleName: String?) {
        val confirmedPi = PendingIntent.getBroadcast(
            context, 200,
            Intent(ParkingConfirmationReceiver.ACTION_CONFIRMED).apply {
                setClass(context, ParkingConfirmationReceiver::class.java)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val deniedPi = PendingIntent.getBroadcast(
            context, 201,
            Intent(ParkingConfirmationReceiver.ACTION_DENIED).apply {
                setClass(context, ParkingConfirmationReceiver::class.java)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (vehicleName != null) {
            context.getString(R.string.notif_confirmation_title_vehicle, vehicleName)
        } else {
            context.getString(R.string.notif_confirmation_title)
        }
        val notification = NotificationCompat.Builder(context, DETECTION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_confirmation_text))
            .setSmallIcon(R.drawable.ic_notification_parking_question)
            .setColor(COLOR_CONFIRMATION)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(buildOpenAppIntent(RC_CONFIRMATION))
            .addAction(0, context.getString(R.string.notif_action_yes_parked), confirmedPi)
            .addAction(0, context.getString(R.string.notif_action_keep_driving), deniedPi)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID, notification)
    }

    override fun showParkingSpotSaved(latitude: Double, longitude: Double) {
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_spot_saved_title))
            .setContentText(
                context.getString(
                    R.string.notif_spot_saved_text,
                    "%.5f".format(latitude),
                    "%.5f".format(longitude),
                )
            )
            .setSmallIcon(R.drawable.ic_notification_parking_saved)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(buildOpenAppIntent(RC_SPOT_SAVED))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.UPLOAD_NOTIFICATION_ID, notification)
    }

    override fun showSpotUploading() {
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_uploading_title))
            .setContentText(context.getString(R.string.notif_uploading_text))
            .setSmallIcon(R.drawable.ic_notification_upload)
            .setColor(COLOR_UPLOAD)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(buildOpenAppIntent(RC_UPLOADING))
            .setOngoing(true)
            .build()
        notificationManager.notify(AppNotificationManager.UPLOAD_NOTIFICATION_ID, notification)
    }

    override fun showDebug(message: String) {
        val notification = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_debug_title))
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_notification_debug)
            .setColor(COLOR_DEBUG)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(buildOpenAppIntent(RC_DEBUG))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.DEBUG_NOTIFICATION_ID, notification)
    }

    override fun dismiss(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    // endregion

    /**
     * PendingIntent that brings MainActivity to the foreground.
     * Uses SINGLE_TOP + CLEAR_TOP so the existing instance is reused,
     * not stacked on top of itself.
     */
    private fun buildOpenAppIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createNotificationChannels() {
        val detectionChannel = NotificationChannel(
            DETECTION_CHANNEL_ID,
            context.getString(R.string.channel_detection_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_detection_desc)
            enableLights(false)
            enableVibration(false)
        }
        val uploadChannel = NotificationChannel(
            UPLOAD_CHANNEL_ID,
            context.getString(R.string.channel_upload_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_upload_desc)
        }
        val debugChannel = NotificationChannel(
            DEBUG_CHANNEL_ID,
            context.getString(R.string.channel_debug_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_debug_desc)
        }
        notificationManager.createNotificationChannels(
            listOf(detectionChannel, uploadChannel, debugChannel)
        )
    }

    companion object {
        const val DETECTION_CHANNEL_ID = "detection_channel"
        const val UPLOAD_CHANNEL_ID = "upload_channel"
        const val DEBUG_CHANNEL_ID = "debug_channel"

        // PendingIntent request codes — must be unique across the app
        private const val RC_DETECTION = 10
        private const val RC_CONFIRMATION = 11
        private const val RC_SPOT_SAVED = 12
        private const val RC_UPLOADING = 13
        private const val RC_DEBUG = 14

        // Accent colors per notification type
        private val COLOR_DETECTION = Color.rgb(25, 118, 210)    // Blue   — GPS active
        private val COLOR_CONFIRMATION = Color.rgb(245, 124, 0)  // Orange — needs attention
        private val COLOR_SUCCESS = Color.rgb(56, 142, 60)       // Green  — success
        private val COLOR_UPLOAD = Color.rgb(25, 118, 210)       // Blue   — in progress
        private val COLOR_DEBUG = Color.rgb(123, 31, 162)        // Purple — debug
    }
}
