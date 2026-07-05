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
     * Used for **manual** save paths only (manual report screen). For auto-detection
     * the unified [showParkingSavedConfirm] is used instead — it replaces the
     * confirmation prompt at the same notification ID so the user never sees two
     * notifications for the same event. [REFACTOR-300]
     *
     * @param latitude  Latitude of the confirmed parking spot.
     * @param longitude Longitude of the confirmed parking spot.
     */
    fun showParkingSaved(latitude: Double, longitude: Double)

    @Deprecated("Use showParkingSaved", ReplaceWith("showParkingSaved(latitude, longitude)"))
    fun showParkingSpotSaved(latitude: Double, longitude: Double) = showParkingSaved(latitude, longitude)

    /**
     * [REFACTOR-300] Unified post-save notification: replaces the "¿Has aparcado?"
     * prompt at [PARKING_CONFIRMATION_NOTIFICATION_ID] with a "Vehículo aparcado +
     * Confirmar / Cancelar" UI.
     *
     * Why merged with the prompt:
     * - Avoids the previous "prompt → dismissed → saved notif appears" double-notification
     *   the user flagged as redundant.
     * - Gives the user a reversal window even when auto-confirm fired silently while
     *   they were walking away — the visible card lets them say "no era yo".
     *
     * @param parkingId    Id of the saved [io.apptolast.paparcar.domain.model.UserParking].
     *                     Travels in the "No, cancelar" PendingIntent extras and is read
     *                     by [io.apptolast.paparcar.domain.usecase.parking.RevertParkingUseCase].
     * @param vehicleName  Optional display name of the parked vehicle (e.g. "Toyota Corolla").
     * @param latitude     Latitude — tap on the body opens the map focused here.
     * @param longitude    Longitude.
     */
    fun showParkingSavedConfirm(
        parkingId: String,
        vehicleName: String?,
        latitude: Double,
        longitude: Double,
    ) {}

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
     * Shows a low-confidence "still parked?" prompt when the safety net
     * ([io.apptolast.paparcar.detection.worker.ParkingSafetyNetWorker]) suspects a missed geofence
     * EXIT (user is far from the parked car without departure evidence, detection idle). NEVER
     * auto-releases — the single action lets the user release the spot if they actually drove away;
     * ignoring/swiping it leaves the session untouched. Default no-op so non-Android impls and fakes
     * need no change. [DET-SAFETY-NET-001]
     *
     * @param geofenceId Id of the active session's geofence; travels in the "I've left" action so
     *                   the departure can be processed for the right session.
     * @param latitude   Latitude of the parked car — tap on the body opens the map focused here.
     * @param longitude  Longitude of the parked car.
     */
    fun showStillParkedPrompt(geofenceId: String, latitude: Double, longitude: Double) {}

    /**
     * Shows a transient error notification when automatic parking confirmation
     * fails (e.g. Room write error or geofence registration failure). The user
     * can then open the app and confirm manually.
     *
     * Has a default no-op so existing fakes and test doubles don't need changes.
     */
    fun showConfirmationFailed() {}

    /**
     * Shows the gentle cold-start nudge — a low-priority reminder for users who enabled detection but
     * have never actually parked with it, prompting them to mark a spot once so the geofence cycle
     * takes over. Heavily throttled by EvaluateFirstParkNudgeUseCase and self-disabled after the first
     * confirmed park. Default no-op for non-Android impls and fakes. [DET-TOGGLE-002]
     */
    fun showFirstParkNudge() {}

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

        /** ID for the parking-saved notification (channel: UPLOAD / DEFAULT priority). */
        const val UPLOAD_NOTIFICATION_ID = 1002

        /** ID for the [BluetoothDetectionService] foreground notification — distinct from
         *  [DETECTION_NOTIFICATION_ID] so both services can coexist without overwriting each other. */
        const val BT_DETECTION_NOTIFICATION_ID = 1003

        /** ID for the debug diagnostic notification (channel: DEBUG / HIGH priority). */
        const val DEBUG_NOTIFICATION_ID = 2001

        /** ID for the parking confirmation notification (channel: DETECTION / LOW priority). */
        const val PARKING_CONFIRMATION_NOTIFICATION_ID = 2002

        /** ID for the permission-revoked notification shown when the service restarts without location access. */
        const val PERMISSION_REVOKED_NOTIFICATION_ID = 2003

        /** ID for the "parking confirmation failed" error notification. */
        const val CONFIRMATION_FAILED_NOTIFICATION_ID = 2004

        /** ID for the watchdog "still parked?" prompt (channel: ACTION). [DET-AR-REARM-001] */
        const val STILL_PARKED_NOTIFICATION_ID = 2005

        /** ID for the cold-start "park once to start auto-detection" nudge (channel: UPLOAD). [DET-TOGGLE-002] */
        const val FIRST_PARK_NUDGE_NOTIFICATION_ID = 2006
    }
}
