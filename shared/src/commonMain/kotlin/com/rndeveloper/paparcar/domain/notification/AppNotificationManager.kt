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
        /** [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] WHICH car is being asked about. Travels beside
         *  the name through the same single call that words the tray, for the same anti-drift
         *  reason — and because the name alone cannot pick that car's card out of the lane. */
        vehicleId: String? = null,
        candidate: GpsPoint? = null,
        /** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The street to name, already vetted by
         *  `ResolveAskedStreetUseCase`. Null = word the question without an address; never guess
         *  one here. */
        street: String? = null,
    )

    /**
     * Replaces the arrival question with a saved-parking status on the same notification slot.
     * View opens this parking; Correct offers continuing the trip, rejecting it, or moving its pin.
     * [parkingId] binds both actions to the saved session, including after a cold app launch.
     * [userAnswered] suppresses a second alert after an explicit arrival answer.
     */
    fun showParkingSavedConfirm(
        parkingId: String,
        vehicleName: String?,
        latitude: Double,
        longitude: Double,
        /**
         * [NOTIF-ONE-QUESTION-DESERVES-ONE-NOTIFICATION-001] Did the user ALREADY answer the ask?
         *
         * The card was written for the silent path — auto-confirm fired while the user walked away
         * — where it is the first thing they hear about the parking, so it offers an acknowledge
         * and an undo. On the path where they tapped "Sí, confirmar" on "¿Has aparcado?" one second
         * earlier, the same card re-alerts and offers to confirm what they just confirmed. That is
         * the double notification the owner reported (#34): not two ids, one question asked twice.
         *
         * `true` drops the acknowledge action (their answer WAS the acknowledgement) and posts
         * without alerting. The undo stays: saying yes to "did you park" is not the same as
         * vouching for WHICH car, and that is what the revert is for.
         */
        userAnswered: Boolean = false,
    ) {}

    /** A BT arrival is already saved. Offer viewing and correction without asking for approval. */
    fun showBluetoothParkingSaved(
        parkingId: String,
        vehicleName: String?,
        latitude: Double,
        longitude: Double,
    ) {
        showParkingSavedConfirm(parkingId, vehicleName, latitude, longitude)
    }

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
     * Asks whether an EXISTING saved car is still parked here when the safety net observes the
     * phone away without enough departure evidence. The durable question also appears in Home.
     * Yes ratifies this session; No opens its saved pin editor for a correction or new parking.
     * Opening/cancelling that editor and ignoring/swiping the notification leave the session and
     * question pending. No answer here declares a departure or publishes a spot.
     * [DET-SAFETY-NET-001][HOME-PARKING-QUESTIONS-KEEP-THE-MAP-001]
     *
     * @param geofenceId Owner of the question, paired with its persisted asking instant.
     * @param latitude Latitude of the saved car, never a replacement phone fix.
     * @param longitude Longitude of the saved car.
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
     * [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] Offers to link a car's Bluetooth to a vehicle
     * after a trip framed exactly one A2DP port (present at the arm, gone at the park). A two-button
     * ask — "yes, this is my car" links the MAC, "no" dismisses. iOS-only in practice: it is the
     * opportunistic half of the onboarding that replaces Android's bonded-device list, which iOS
     * does not expose. Default no-op so every other platform and the fakes ignore it.
     *
     * NOT a [com.rndeveloper.paparcar.domain.detection.PendingParkNudge]: this is a low-stakes
     * convenience, not a lost car. If the user misses it, the next trip offers again — so it needs
     * no durable in-app recovery, unlike the park nudge.
     *
     * @param vehicleId     Which vehicle to write the MAC onto if the user says yes.
     * @param deviceAddress The car's BT MAC, parsed from the audio-route port UID.
     * @param deviceName    The port's human name ("SKODA BT") — what the user sees to recognise it.
     */
    fun showCarLinkOffer(vehicleId: String, deviceAddress: String, deviceName: String?) {}

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
     * Alguien del grupo acaba de aparcar el coche que compartís.
     * [SHARED-A-PARKING-TELLS-THE-GROUP-001]
     *
     * ⛔ Nunca se manda a quien aparcó: la decisión de a quién se le cuenta se toma en el servidor
     * (el autor se excluye del reparto), no aquí — un móvil no puede saber si el mensaje era para
     * otro. Lo que sí decide este lado es CÓMO se dice, que es lo que permite decirlo en los nueve
     * idiomas sin que el servidor sepa ninguno.
     *
     * @param authorName quién lo dejó, o null si su perfil no tiene nombre: la tarjeta dice
     *   entonces «han aparcado el coche compartido». ⛔ Callarse por no saber el nombre sería
     *   perder el aviso entero por el campo menos importante que trae.
     * @param addressLine la calle donde lo dejó, o null si el geocoder aún no ha contestado: la
     *   tarjeta dice entonces «toca para ver dónde está», que sigue siendo cierto.
     */
    fun showSharedCarParked(
        authorName: String?,
        addressLine: String?,
        latitude: Double,
        longitude: Double,
    ) {}

    /**
     * El coche compartido ya no está donde estaba: se lo ha llevado quien lo había aparcado.
     * [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
     *
     * ⛔ **Sin sitio al que ir.** La otra tarjeta abre el mapa en el punto porque hay un punto; esta
     * dice justo que ya no lo hay, y mandar al usuario al sitio del que el coche acaba de salir
     * sería contarle lo contrario de lo que pone. Abre la app, y punto.
     *
     * @param authorName quién se lo ha llevado, o null si su perfil no tiene nombre.
     */
    fun showSharedCarLeft(authorName: String?) {}

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
        /** Whether [savingLabel] is quoted per gallon, so the sentence can say the same unit the
         *  figure is written in. ⛔ It travels WITH the figure instead of being asked to the device:
         *  the unit is a property of the market the price came from, and a phone set to another
         *  region would otherwise word the sentence in a unit the number is not in.
         *  [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001] */
        quotesPerGallon: Boolean = false,
    ) {}

    /**
     * A watched station's price moved overnight.
     * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
     *
     * One notification per LANE, never one per station: a driver who follows four pumps must not
     * wake up to four cards. [deltaLabel] describes the lead station; [otherMovesCount] is how many
     * more moved in that same lane, so the copy can say so in one line.
     */
    fun showFuelPriceMove(
        stationName: String?,
        deltaLabel: String,
        wentUp: Boolean,
        otherMovesCount: Int,
        latitude: Double,
        longitude: Double,
        /** Whether [deltaLabel] is quoted per gallon. Same contract as in [showCheaperFuelNearby].
         *  [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001] */
        quotesPerGallon: Boolean = false,
        /**
         * Which lane is speaking. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
         *
         * ⛔ Un TIPO y no un booleano, y no por estilo: decide dos cosas a la vez —el sujeto que
         * nombra el título y la tarjeta en la que sale— y un `isFollowed: Boolean` en una llamada
         * de siete parámetros es exactamente la forma de llamar «la que sigues» a una gasolinera
         * que el usuario nunca estrelló. Mismo patrón que `SpotReleaseLane`.
         */
        lane: FuelMoveLane = FuelMoveLane.FOLLOWED,
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
     * @param notificationId One of the [DETECTION_NOTIFICATION_ID], [DEBUG_NOTIFICATION_ID], or
     *                       [PARKING_CONFIRMATION_NOTIFICATION_ID] constants.
     */
    fun dismiss(notificationId: Int)

    companion object {
        /** ID for the foreground-service detection notification (channel: DETECTION / LOW priority). */
        const val DETECTION_NOTIFICATION_ID = 1001

        /** The monthly recap. Its own id so it can never replace an alert about right now.
         *  [STATS-PLUS-THE-RECAP-KNOCKS-001] */
        const val MONTH_RECAP_NOTIFICATION_ID = 1012

        /** ID for the debug diagnostic notification (channel: DEBUG / HIGH priority). */
        const val DEBUG_NOTIFICATION_ID = 2001

        /**
         * **The parking flow's only slot.** [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001]
         *
         * Whatever this app has to say about the car you just left — "¿Has aparcado?", "Kamiq
         * aparcado + deshacer" — it says HERE, so the newest statement always replaces the
         * previous one and a single `dismiss` silences the conversation.
         *
         * ⛔ There used to be a second one. `UPLOAD_NOTIFICATION_ID = 1002` carried a legacy
         * action-less "Aparcamiento guardado" card that **nobody ever dismissed** — a `grep
         * dismiss(` over the repo returned 16 hits for this id and zero for that one. Field
         * 2026-09-22 22:37 (issue #132): a card about a parking superseded 34 minutes earlier
         * sitting in the tray next to the card about the current one. The id is gone rather than
         * merely unused, so the compiler is what stops the next parking card from landing in a
         * slot with no off switch.
         */
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

        /**
         * The manual-location question replaces the parking prompt in its single ACTION slot when
         * GPS cannot place the car, so one trip cannot leave two competing questions in the tray.
         * [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001]
         */
        const val MARK_PARKING_NUDGE_NOTIFICATION_ID = PARKING_CONFIRMATION_NOTIFICATION_ID

        /** ID for the Plus zone-alert "free spot in your zone" notification. [MONEY-001] */
        const val ZONE_SPOT_ALERT_NOTIFICATION_ID = 2009

        /** El aviso de que alguien del grupo ha aparcado el coche compartido.
         *  [SHARED-A-PARKING-TELLS-THE-GROUP-001] */
        const val SHARED_CAR_PARKED_NOTIFICATION_ID = 2012

        /** Y el aviso de que se lo han llevado. Id PROPIO: enterarse de que el coche se va no
         *  puede borrar de la barra la tarjeta que dice dónde está otro coche del grupo.
         *  [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001] */
        const val SHARED_CAR_LEFT_NOTIFICATION_ID = 2013

        /** ID for the Plus "a cheaper pump nearby" alert. [FUEL-A-PRICE-REMEMBERED-…-001] */
        const val CHEAPER_FUEL_NOTIFICATION_ID = 2010

        /** ID for the Plus "your station moved" alert — ONE card for the whole followed set. */
        const val FUEL_PRICE_MOVE_NOTIFICATION_ID = 2011

        /**
         * ID for the FREE "a pump near you moved" alert — one card for that whole lane.
         * [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
         *
         * ⛔ Su propio id, no el 2011: los dos carriles pueden tener algo que decir el mismo día, y
         * compartir id significa que el segundo **sobreescribe** al primero. El que se perdería es
         * el de pago.
         */
        const val NEARBY_FUEL_MOVE_NOTIFICATION_ID = 2017

        /** [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] "Is this your car?" — the opportunistic
         *  link offer after a trip framed one BT audio port. iOS-only in practice. */
        const val CAR_LINK_OFFER_NOTIFICATION_ID = 2014
    }
}
