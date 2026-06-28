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

    override fun buildDetectionNotification(): Notification = buildDetectionNotificationWith(null)

    // endregion

    // region AppNotificationManager

    override fun updateDetectionVehicle(vehicleName: String, notifId: Int) {
        notificationManager.notify(notifId, buildDetectionNotificationWith(vehicleName))
    }

    override fun showParkingConfirmation(score: Float, vehicleName: String?) {
        val confirmedPi = PendingIntent.getBroadcast(
            context, RC_CONFIRM_YES,
            Intent(ParkingConfirmationReceiver.ACTION_CONFIRMED).apply {
                setClass(context, ParkingConfirmationReceiver::class.java)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val deniedPi = PendingIntent.getBroadcast(
            context, RC_CONFIRM_NO,
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
        val notification = NotificationCompat.Builder(context, ACTION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_confirmation_text))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_CONFIRMATION)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(buildOpenAppIntent(RC_CONFIRMATION))
            .addAction(0, context.getString(R.string.notif_action_yes_parked), confirmedPi)
            .addAction(0, context.getString(R.string.notif_action_no_not_parked), deniedPi)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID, notification)
    }

    override fun showParkingSaved(latitude: Double, longitude: Double) {
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_parking_saved_title))
            .setContentText(context.getString(R.string.notif_parking_saved_text))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(buildFocusIntent(RC_PARKING_SAVED, latitude, longitude))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.UPLOAD_NOTIFICATION_ID, notification)
    }

    /**
     * [REFACTOR-300] Single notification that replaces the "¿Has aparcado?" prompt at
     * the same ID. Two action buttons:
     *  - "Sí, confirmar" → ACTION_PARKING_ACK (just dismiss + stop service)
     *  - "No, cancelar"  → ACTION_PARKING_REVERT + parkingId extra → RevertParkingUseCase
     *
     * Posted on the ACTION channel (HIGH / heads-up) because it carries action buttons
     * and the user must be able to reverse a silent auto-confirm. To avoid a double buzz
     * when this MORPHS the "¿Has aparcado?" prompt already showing at the same ID, it sets
     * [NotificationCompat.Builder.setOnlyAlertOnce] — the auto-confirm path (nothing showing)
     * still alerts once; the morph path does not re-alert. Tap on the body opens MainActivity
     * focused on the parking location.
     */
    override fun showParkingSavedConfirm(
        parkingId: String,
        vehicleName: String?,
        latitude: Double,
        longitude: Double,
    ) {
        val ackPi = PendingIntent.getBroadcast(
            context, RC_SAVED_ACK,
            Intent(ParkingConfirmationReceiver.ACTION_ACK).apply {
                setClass(context, ParkingConfirmationReceiver::class.java)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // FLAG_UPDATE_CURRENT replaces the EXTRA on each new save; we only ever have one
        // post-save notification visible at a time so this is correct.
        val revertPi = PendingIntent.getBroadcast(
            context, RC_SAVED_REVERT,
            Intent(ParkingConfirmationReceiver.ACTION_REVERT).apply {
                setClass(context, ParkingConfirmationReceiver::class.java)
                putExtra(ParkingConfirmationReceiver.EXTRA_PARKING_ID, parkingId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = if (vehicleName != null) {
            context.getString(R.string.notif_parking_saved_confirm_title_vehicle, vehicleName)
        } else {
            context.getString(R.string.notif_parking_saved_confirm_title)
        }
        val notification = NotificationCompat.Builder(context, ACTION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_parking_saved_confirm_text))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(buildFocusIntent(RC_SAVED_FOCUS, latitude, longitude))
            .addAction(0, context.getString(R.string.notif_action_confirm), ackPi)
            .addAction(0, context.getString(R.string.notif_action_cancel_save), revertPi)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID, notification)
    }

    override fun showPermissionRevoked() {
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_permission_revoked_title))
            .setContentText(context.getString(R.string.notif_permission_revoked_text))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_DEBUG)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(buildOpenAppIntent(RC_PERMISSION_REVOKED))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.PERMISSION_REVOKED_NOTIFICATION_ID, notification)
    }

    override fun showConfirmationFailed() {
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_confirmation_failed_title))
            .setContentText(context.getString(R.string.notif_confirmation_failed_text))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_CONFIRMATION)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(buildOpenAppIntent(RC_CONFIRMATION_FAILED))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.CONFIRMATION_FAILED_NOTIFICATION_ID, notification)
    }

    override fun showStillParkedPrompt(geofenceId: String, latitude: Double, longitude: Double) {
        val leftPi = PendingIntent.getBroadcast(
            context, RC_STILL_PARKED_LEFT,
            Intent(ParkingConfirmationReceiver.ACTION_DEPARTURE_CONFIRMED).apply {
                setClass(context, ParkingConfirmationReceiver::class.java)
                putExtra(ParkingConfirmationReceiver.EXTRA_GEOFENCE_ID, geofenceId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, ACTION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_still_parked_title))
            .setContentText(context.getString(R.string.notif_still_parked_text))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_CONFIRMATION)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(buildFocusIntent(RC_STILL_PARKED_FOCUS, latitude, longitude))
            .addAction(0, context.getString(R.string.notif_action_ive_left), leftPi)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID, notification)
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

    private fun buildDetectionNotificationWith(vehicleName: String?): Notification {
        val bodyText = if (vehicleName != null) {
            context.getString(R.string.notif_detection_text_vehicle, vehicleName)
        } else {
            context.getString(R.string.notif_detection_text)
        }
        return NotificationCompat.Builder(context, DETECTION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_detection_title))
            .setContentText(bodyText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_detection_explainer)),
            )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(buildOpenAppIntent(RC_DETECTION))
            .setOngoing(true)
            .setShowWhen(false)
            .setColor(COLOR_DETECTION)
            .build()
    }

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

    /**
     * PendingIntent that opens MainActivity and requests a map focus on (lat, lon).
     * The coordinates are passed as extras and consumed by [MainActivity.onNewIntent].
     */
    private fun buildFocusIntent(requestCode: Int, lat: Double, lon: Double): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_FOCUS_LAT, lat)
                putExtra(MainActivity.EXTRA_FOCUS_LON, lon)
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
        val actionChannel = NotificationChannel(
            ACTION_CHANNEL_ID,
            context.getString(R.string.channel_action_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_action_desc)
        }
        val debugChannel = NotificationChannel(
            DEBUG_CHANNEL_ID,
            context.getString(R.string.channel_debug_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_debug_desc)
        }
        notificationManager.createNotificationChannels(
            listOf(detectionChannel, uploadChannel, actionChannel, debugChannel)
        )
    }

    companion object {
        const val DETECTION_CHANNEL_ID = "detection_channel"
        const val UPLOAD_CHANNEL_ID = "upload_channel"
        const val ACTION_CHANNEL_ID = "action_channel"
        const val DEBUG_CHANNEL_ID = "debug_channel"

        // PendingIntent request codes — must be unique across the app
        private const val RC_DETECTION = 10
        private const val RC_CONFIRMATION = 11
        private const val RC_PARKING_SAVED = 12
        private const val RC_DEBUG = 14
        private const val RC_PERMISSION_REVOKED = 15
        private const val RC_CONFIRMATION_FAILED = 17
        private const val RC_CONFIRM_YES = 200
        private const val RC_CONFIRM_NO = 201
        // [REFACTOR-300] post-save notification request codes
        private const val RC_SAVED_FOCUS = 202
        private const val RC_SAVED_ACK = 203
        private const val RC_SAVED_REVERT = 204
        // [DET-AR-REARM-001] watchdog "still parked?" prompt request codes
        private const val RC_STILL_PARKED_FOCUS = 205
        private const val RC_STILL_PARKED_LEFT = 206

        // Accent colors per notification type
        private val COLOR_DETECTION = Color.rgb(25, 118, 210)    // Blue   — GPS active
        private val COLOR_CONFIRMATION = Color.rgb(245, 124, 0)  // Orange — needs attention
        private val COLOR_SUCCESS = Color.rgb(56, 142, 60)       // Green  — success
        private val COLOR_DEBUG = Color.rgb(123, 31, 162)        // Purple — debug
    }
}
