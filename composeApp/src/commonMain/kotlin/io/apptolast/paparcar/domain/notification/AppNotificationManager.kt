package io.apptolast.paparcar.domain.notification

/**
 * Domain interface for displaying user-facing notifications.
 *
 * Implementations live in the platform layer (androidMain) and are bound
 * via Koin's [AndroidPlatformModule]. The domain layer only depends on this
 * interface — never on the Android [NotificationManager] directly.
 */
interface AppNotificationManager {

    /**
     * Shows an ongoing notification asking the user to confirm they have parked.
     * Includes action buttons for "Yes" / "No" that send a broadcast back to the app.
     *
     * @param score       Confidence score (0f–1f) for display purposes (e.g. progress indicator).
     * @param vehicleName Name of the vehicle that triggered detection (e.g. "Toyota Corolla").
     *                    When provided, the notification title reads "Did you park your [name]?"
     *                    instead of the generic "Did you park?".
     */
    fun showParkingConfirmation(score: Float, vehicleName: String? = null)

    /**
     * Shows a transient notification confirming that a parking spot was saved.
     *
     * @param latitude  Latitude of the confirmed spot.
     * @param longitude Longitude of the confirmed spot.
     */
    fun showParkingSpotSaved(latitude: Double, longitude: Double)

    /**
     * Shows an ongoing notification while the spot-released report is being uploaded to Firebase.
     * Replace with [dismiss] using [UPLOAD_NOTIFICATION_ID] once the upload completes.
     */
    fun showSpotUploading()

    /**
     * Shows a transient debug notification. Only called when [BuildConfig.DEBUG] is true.
     *
     * @param message Diagnostic text to display.
     */
    fun showDebug(message: String)

    /**
     * Dismisses the notification with the given [notificationId].
     *
     * @param notificationId One of the [DETECTION_NOTIFICATION_ID], [UPLOAD_NOTIFICATION_ID],
     *                       [DEBUG_NOTIFICATION_ID], or [PARKING_CONFIRMATION_NOTIFICATION_ID] constants.
     */
    fun dismiss(notificationId: Int)

    companion object {
        /** ID for the foreground-service detection notification (channel: DETECTION / LOW priority). */
        const val DETECTION_NOTIFICATION_ID = 1001

        /** ID for the spot-uploading notification (channel: UPLOAD / DEFAULT priority). */
        const val UPLOAD_NOTIFICATION_ID = 1002

        /** ID for the debug diagnostic notification (channel: DEBUG / HIGH priority). */
        const val DEBUG_NOTIFICATION_ID = 2001

        /** ID for the parking confirmation notification (channel: DEBUG / HIGH priority). */
        const val PARKING_CONFIRMATION_NOTIFICATION_ID = 2002
    }
}
