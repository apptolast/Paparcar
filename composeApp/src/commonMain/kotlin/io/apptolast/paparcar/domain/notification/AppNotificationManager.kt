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
     * Shows a transient notification confirming that the user's car is parked.
     * Tapping opens the map centred on the parking location.
     *
     * @param latitude  Latitude of the confirmed parking spot.
     * @param longitude Longitude of the confirmed parking spot.
     */
    fun showParkingSaved(latitude: Double, longitude: Double)

    @Deprecated("Use showParkingSaved", ReplaceWith("showParkingSaved(latitude, longitude)"))
    fun showParkingSpotSaved(latitude: Double, longitude: Double) = showParkingSaved(latitude, longitude)

    /**
     * Shows a transient notification confirming the freed spot is visible to other drivers.
     * Tapping opens the map centred on the spot location.
     *
     * @param latitude  Latitude of the published community spot.
     * @param longitude Longitude of the published community spot.
     */
    fun showSpotPublished(latitude: Double, longitude: Double)

    /**
     * Shows an ongoing notification while the spot-released report is being uploaded to Firebase.
     * Replace with [dismiss] using [UPLOAD_NOTIFICATION_ID] once the upload completes.
     */
    fun showSpotUploading()

    /**
     * Updates the text of the foreground detection notification to show the vehicle being monitored.
     * Called from a coroutine immediately after the service calls startForeground().
     *
     * @param vehicleName Display name of the active vehicle (e.g. "Ford Focus").
     * @param notifId     [DETECTION_NOTIFICATION_ID] or [BT_DETECTION_NOTIFICATION_ID].
     */
    fun updateDetectionVehicle(vehicleName: String, notifId: Int)

    /**
     * Shows a notification prompting the user to re-grant location permission.
     * Tapping opens the app, which auto-redirects to the Permissions screen via the gate.
     * Called when the detection service starts (START_STICKY restart or explicit start)
     * and finds that location permission was revoked while the process was dead. [§9]
     */
    fun showPermissionRevoked()

    /**
     * Shows a transient debug notification. Only called when [BuildConfig.DEBUG] is true.
     *
     * @param message Diagnostic text to display.
     */
    fun showDebug(message: String)

    /**
     * Shows a transient error notification when automatic parking confirmation
     * fails (e.g. Room write error or geofence registration failure). The user
     * can then open the app and confirm manually.
     *
     * Has a default no-op so existing fakes and test doubles don't need changes.
     */
    fun showConfirmationFailed() {}

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

        /** ID for the spot-uploading / parking-saved notification (channel: UPLOAD / DEFAULT priority). */
        const val UPLOAD_NOTIFICATION_ID = 1002

        /** ID for the [BluetoothDetectionService] foreground notification — distinct from
         *  [DETECTION_NOTIFICATION_ID] so both services can coexist without overwriting each other. */
        const val BT_DETECTION_NOTIFICATION_ID = 1003

        /** ID for the "Spot published" notification shown after a spot release is uploaded. */
        const val SPOT_PUBLISHED_NOTIFICATION_ID = 1004

        /** ID for the debug diagnostic notification (channel: DEBUG / HIGH priority). */
        const val DEBUG_NOTIFICATION_ID = 2001

        /** ID for the parking confirmation notification (channel: DETECTION / LOW priority). */
        const val PARKING_CONFIRMATION_NOTIFICATION_ID = 2002

        /** ID for the permission-revoked notification shown when the service restarts without location access. */
        const val PERMISSION_REVOKED_NOTIFICATION_ID = 2003

        /** ID for the "parking confirmation failed" error notification. */
        const val CONFIRMATION_FAILED_NOTIFICATION_ID = 2004
    }
}
