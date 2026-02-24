package io.apptolast.paparcar.notification

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import io.apptolast.paparcar.data.notification.AppNotificationManager
import io.apptolast.paparcar.detection.ParkingConfirmationReceiver

class AppNotificationManagerImpl(
    private val context: Context,
    private val notificationManager: NotificationManager
) : AppNotificationManager {

    init {
        createNotificationChannels()
    }

    override fun buildDetectionNotification(): Notification {
        return NotificationCompat.Builder(context, DETECTION_CHANNEL_ID)
            .setContentTitle("Detección en curso")
            .setSmallIcon(R.drawable.ic_menu_mylocation)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setProgress(0, 0, true)
            .setColor(ContextCompat.getColor(context, R.color.holo_purple))
            .build()
    }

    override fun buildUploadNotification(): Notification {
        return NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle("Paparcar")
            .setContentText("Subiendo nuevo spot...")
            .setSmallIcon(R.drawable.ic_menu_upload)
            .setOngoing(true)
            .build()
    }

    override fun showDebugNotification(message: String) {
        val notification = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setContentTitle("Paparcar Debug")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_dialog_alert)
            .build()
        notificationManager.notify(AppNotificationManager.DEBUG_NOTIFICATION_ID, notification)
    }

    override fun showParkingConfirmationNotification(score: Float) {
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
        val notification = NotificationCompat.Builder(context, DETECTION_CHANNEL_ID)
            .setContentTitle("¿Has aparcado?")
            .setContentText("Parece que has aparcado el coche. ¿Confirmamos la plaza?")
            .setSmallIcon(R.drawable.ic_menu_mylocation)
            .addAction(0, "Sí, he aparcado", confirmedPi)
            .addAction(0, "Sigo conduciendo", deniedPi)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID, notification)
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

//    private fun createNotificationChannel() {
//        val name = "Seguimiento de Trayecto"
//        val descriptionText = "Detección automática de aparcamiento en segundo plano"
//
//        // IMPORTANCE_LOW: Hace que no suene y aparezca en la sección inferior (silenciosas)
//        val importance = NotificationManager.IMPORTANCE_LOW
//
//        val channel = NotificationChannel("PARKING_DETECTION_CHANNEL", name, importance).apply {
//            description = descriptionText
//            // Opcional: Desactivar luces y vibración para asegurar silencio total
//            enableLights(false)
//            enableVibration(false)
//            setShowBadge(false) // No mostrar el puntito sobre el icono de la app
//        }
//
//        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//        notificationManager.createNotificationChannel(channel)
//    }

    companion object {
        const val DETECTION_CHANNEL_ID = "detection_channel"
        const val UPLOAD_CHANNEL_ID = "upload_channel"
        const val DEBUG_CHANNEL_ID = "debug_channel"
    }
}
