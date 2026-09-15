package com.rndeveloper.paparcar.domain.notification

import com.rndeveloper.paparcar.domain.model.GpsPoint

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
     * @param candidate   [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] WHERE the question is
     *                    about — the last witnessed car stop. Persisted with the window so the map
     *                    can show the place the app is asking about, and so a user who opens the
     *                    app inside the window sees the CAR rather than themselves. Null when the
     *                    asking path has no position to stand behind; never a guessed one.
     */
    fun showParkingConfirmation(
        score: Float,
        vehicleName: String? = null,
        candidate: GpsPoint? = null,
        /** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The street to name, already vetted by
         *  `ResolveAskedStreetUseCase`. Null = word the question without an address; never guess
         *  one here. */
        street: String? = null,
    )

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
     * @param parkingId    Id of the saved [com.rndeveloper.paparcar.domain.model.UserParking].
     *                     Travels in the "No, cancelar" PendingIntent extras and is read
     *                     by [com.rndeveloper.paparcar.domain.usecase.parking.RevertParkingUseCase].
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
     * @param notifId     [DETECTION_NOTIFICATION_ID] (the BT id was retired with its service —
     *                    [DET-ONE-SERVICE-TWO-LANES-001]).
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
     * ([com.rndeveloper.paparcar.detection.worker.ParkingSafetyNetWorker]) suspects a missed geofence
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
     * Asks the user WHERE the car ended up after a detection session that could not place it:
     * the session detected movement but never MEASURED driving, so placing a pin would only
     * mark wherever the pedestrian stopped (the living-room pin, field 2026-07-10). Tap and
     * action both deep-link straight into manual add-parking mode. Default no-op for
     * non-Android impls and fakes. [DET-AR-FIRST-001]
     *
     * [DET-NUDGE-PERSIST-001] Implementations MUST persist the ask as a durable
     * [com.rndeveloper.paparcar.domain.detection.PendingParkNudge] BEFORE posting the notification —
     * this method is the single choke point every ask path goes through, and the notification
     * alone does not survive being slept through (field 2026-07-25: nudge posted at 03:11, user
     * asleep, session lost with no in-app recovery). The Home banner renders the durable copy
     * until the user answers.
     *
     * @param source    Which detection path asked (session outcome / worker source) — diagnostics.
     * @param vehicleId Vehicle the lost trip belonged to, when the asking path knows it — lets
     *                  the banner auto-resolve if that vehicle gets a confirmed session again.
     * @param persistPending False ONLY when the ask already left its own durable trace — the
     *                  honest close saves an approximate pin/zone BEFORE nudging, so the mark on
     *                  the map is the surviving question and a pending record would resurface as
     *                  a ghost "car lost" banner after that session's normal release.
     */
    fun showMarkParkingNudge(source: String? = null, vehicleId: String? = null, persistPending: Boolean = true) {}

    /**
     * Warns that the OS/OEM blocked Paparcar's background execution for hours while a session was
     * active (detected by the safety net's heartbeat gap) — detection almost certainly missed
     * events during that window. Tapping opens the app so the user can fix it from Settings →
     * Detection health (autostart / battery whitelist). This is the CONTEXTUAL battery ask: it
     * only ever fires after a real observed failure, never upfront. Default no-op for non-Android
     * impls and fakes. [OEM-KILL-001][BATTERY-ASK-001]
     */
    fun showBackgroundReliabilityWarning() {}

    /**
     * A free community spot just appeared inside one of the user's alert-subscribed zones
     * (Paparcar Plus). Tapping opens the app on the map focused there. Client-side delivery
     * for 1.x — fed by ObserveZoneSpotAlertsUseCase while the app process is alive; see
     * ZONE-SUBSCRIBE-001 for background delivery. Default no-op for non-Android impls and
     * fakes. [MONEY-001]
     */
    fun showZoneSpotAlert(
        zoneName: String,
        latitude: Double,
        longitude: Double,
        /**
         * True when the departure behind this spot was DEDUCED, not witnessed — it lives twelve
         * minutes and may yet be withdrawn. [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
         *
         * ⛔ Said, never hidden. These are the freshest spots there are and the ones the old
         * fifteen-minute poll could not reach at all, so push is what makes them deliverable — but a
         * card that promised a confirmed space and sent someone to a retracted one would spend the
         * trust the whole alert runs on. Different words, not a different silence.
         */
        isProvisional: Boolean = false,
    ) {}

    /**
     * A pump near the user charges measurably less than the one they use (Paparcar Plus).
     * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
     *
     * ⛔ The saving is stated WITH what it is measured against — a bare «8 c/l cheaper» is a number
     * the driver cannot check, and the whole fuel layer exists because an unverifiable price ran
     * twelve hours behind and nobody could tell.
     *
     * @param stationName  The forecourt, as its source names it; null where the feed has no name.
     * @param savingLabel  The amount, already written at the pump's precision. ⛔ Not the SENTENCE:
     *                     wording is a locale decision and belongs to the platform layer, which is
     *                     the only place with the string table.
     * @param referenceName The followed station the saving is measured against; **null means the
     *                     reference was what is normal around here**, which the implementation words
     *                     in the user's language. A real answer, not a missing one.
     */
    fun showCheaperFuelNearby(
        stationName: String?,
        savingLabel: String,
        referenceName: String?,
        latitude: Double,
        longitude: Double,
    ) {}

    /**
     * A followed station's price moved overnight (Paparcar Plus).
     * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
     *
     * One notification for the whole set, never one per station: a driver who follows four pumps
     * must not wake up to four cards. [deltaLabel] describes the lead station; [otherMovesCount] is
     * how many more moved, so the copy can say so in one line.
     */
    fun showFuelPriceMove(
        stationName: String?,
        deltaLabel: String,
        wentUp: Boolean,
        otherMovesCount: Int,
        latitude: Double,
        longitude: Double,
    ) {}

    /**
     * The month that just closed, knocking. [STATS-PLUS-THE-RECAP-KNOCKS-001]
     *
     * ⛔ DOMAIN values, not a finished sentence: the words and their nine locales belong to the
     * implementation, which is the side that owns `R.string`. The same split every other alert here
     * already lives by.
     *
     * @param parkings    how many times the car was parked in the closed month
     * @param spendText   the month's approximate spend, already formatted with its currency, or null
     * @param searchTimeMs time lost hunting a spot over the month, or null when nothing was measured
     */
    fun showMonthRecap(parkings: Int, spendText: String?, searchTimeMs: Long?) {}

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

        /** The monthly recap. Its own id so it can never replace an alert about right now.
         *  [STATS-PLUS-THE-RECAP-KNOCKS-001] */
        const val MONTH_RECAP_NOTIFICATION_ID = 1012

        /** ID for the parking-saved notification (channel: UPLOAD / DEFAULT priority). */
        const val UPLOAD_NOTIFICATION_ID = 1002


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

        /** ID for the OEM background-kill warning (channel: UPLOAD). [OEM-KILL-001] */
        const val BACKGROUND_RELIABILITY_NOTIFICATION_ID = 2007

        /** ID for the "where did you leave your car?" nudge (channel: ACTION). [DET-AR-FIRST-001] */
        const val MARK_PARKING_NUDGE_NOTIFICATION_ID = 2008

        /** ID for the Plus zone-alert "free spot in your zone" notification. [MONEY-001] */
        const val ZONE_SPOT_ALERT_NOTIFICATION_ID = 2009

        /** ID for the Plus "a cheaper pump nearby" alert. [FUEL-A-PRICE-REMEMBERED-…-001] */
        const val CHEAPER_FUEL_NOTIFICATION_ID = 2010

        /** ID for the Plus "your station moved" alert — ONE card for the whole followed set. */
        const val FUEL_PRICE_MOVE_NOTIFICATION_ID = 2011
    }
}
