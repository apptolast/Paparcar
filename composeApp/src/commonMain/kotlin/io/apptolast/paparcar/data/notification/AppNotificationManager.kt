package io.apptolast.paparcar.data.notification

interface AppNotificationManager {
    fun buildDetectionNotification(): Any
    fun buildUploadNotification(): Any
    fun showDebugNotification(message: String)
    fun dismiss(notificationId: Int)

    companion object {
        const val DETECTION_NOTIFICATION_ID = 1001
        const val UPLOAD_NOTIFICATION_ID    = 1002
        const val DEBUG_NOTIFICATION_ID     = 2001
    }
}
