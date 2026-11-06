package com.rndeveloper.paparcar.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import androidx.core.app.NotificationCompat
import com.rndeveloper.paparcar.MainActivity
import com.rndeveloper.paparcar.R
import com.rndeveloper.paparcar.detection.receiver.ParkingConfirmationReceiver
import com.rndeveloper.paparcar.domain.detection.PendingParkNudge
import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.notification.FuelMoveLane
import com.rndeveloper.paparcar.domain.preferences.AppPreferences

class AppNotificationManagerImpl(
    private val context: Context,
    private val notificationManager: NotificationManager,
    private val appPreferences: AppPreferences,
) : AppNotificationManager, ForegroundNotificationProvider {

    init {
        createNotificationChannels()
        // Clear the old separate nudge left in trays by versions before the single-slot flow.
        notificationManager.cancel(LEGACY_MARK_PARKING_NUDGE_NOTIFICATION_ID)
    }

    // region ForegroundNotificationProvider

    override fun buildDetectionNotification(): Notification = buildDetectionNotificationWith(null)

    // endregion

    // region AppNotificationManager

    override fun updateDetectionVehicle(vehicleName: String, notifId: Int) {
        notificationManager.notify(notifId, buildDetectionNotificationWith(vehicleName))
    }

    override fun showParkingConfirmation(
        score: Float,
        vehicleName: String?,
        vehicleId: String?,
        candidate: GpsPoint?,
        street: String?,
    ) {
        // [DET-ASK-STATE-001] Persist FIRST: this is the ONLY place the question is opened, so the
        // in-app row is written from the same call that words the tray notification — they cannot
        // drift. Never let a persist failure suppress the proven notification ask (same rule as the
        // nudge). The window closes itself in `dismiss` / `showParkingSavedConfirm` below.
        runCatching {
            appPreferences.setPendingPromptWindow(
                PendingPromptWindow(
                    shownAtMs = System.currentTimeMillis(),
                    vehicleName = vehicleName,
                    // [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] El id viaja por la MISMA escritura
                    // que el nombre, y por eso no puede contradecirlo: es quien permite que la
                    // pregunta se mude a la card de SU coche habiendo varios.
                    vehicleId = vehicleId,
                    // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The place AND the street
                    // travel with the wording, through the same single write, for the same
                    // anti-drift reason: the Home row must repeat this address, never re-derive it.
                    candidate = candidate,
                    street = street,
                ),
            )
        }
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
        // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Four wordings, not a concatenation: the
        // preposition before a street ("en", "at", "à", "przy"…) is part of the sentence, and gluing
        // strings would leave eight of the nine locales reading like a form field.
        val title = when {
            vehicleName != null && street != null ->
                context.getString(R.string.notif_confirmation_title_vehicle_street, vehicleName, street)
            vehicleName != null ->
                context.getString(R.string.notif_confirmation_title_vehicle, vehicleName)
            street != null ->
                context.getString(R.string.notif_confirmation_title_street, street)
            else -> context.getString(R.string.notif_confirmation_title)
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
            // Both wordings occupy one slot; updating that question must not buzz again.
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID, notification)
    }

    /**
     * [MONEY-001] Plus zone alert: a free spot just appeared in one of the user's subscribed
     * zones. Informative (UPLOAD channel, default priority) — same tier as "parking saved" —
     * and double-gated by the Settings master switch: the use case checks it at fire time and
     * this choke point re-reads it — the same double-gate every optional alert here uses.
     */
    override fun showZoneSpotAlert(
        zoneName: String,
        latitude: Double,
        longitude: Double,
        isProvisional: Boolean,
    ) {
        if (!appPreferences.notifyZoneSpots) return
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_zone_spot_title, zoneName))
            // ⛔ A deduced departure says so. The spot is still worth telling about — it is the
            // freshest thing on the map — but it can be withdrawn, and a card that hid that would
            // send someone to a space the app itself stopped believing in.
            // [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
            .setContentText(
                if (isProvisional) {
                    context.getString(R.string.notif_zone_spot_text_provisional)
                } else {
                    context.getString(R.string.notif_zone_spot_text)
                },
            )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(buildFocusIntent(RC_ZONE_SPOT_ALERT, latitude, longitude))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.ZONE_SPOT_ALERT_NOTIFICATION_ID, notification)
    }

    /**
     * Alguien del grupo ha aparcado el coche compartido.
     * [SHARED-A-PARKING-TELLS-THE-GROUP-001]
     *
     * Mismo intent que la alerta de zona —abre el mapa centrado en el punto—, porque la pregunta
     * que resuelve es la misma: *¿dónde está eso?*.
     */
    override fun showSharedCarParked(
        authorName: String?,
        addressLine: String?,
        latitude: Double,
        longitude: Double,
    ) {
        val title = authorName
            ?.let { context.getString(R.string.notif_shared_car_parked_title, it) }
            ?: context.getString(R.string.notif_shared_car_parked_title_unknown)
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(
                addressLine
                    ?.let { context.getString(R.string.notif_shared_car_parked_text, it) }
                    ?: context.getString(R.string.notif_shared_car_parked_text_unknown),
            )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(buildFocusIntent(RC_SHARED_CAR_PARKED, latitude, longitude))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(
            AppNotificationManager.SHARED_CAR_PARKED_NOTIFICATION_ID,
            notification,
        )
    }

    /**
     * El coche compartido se ha ido del sitio donde estaba.
     * [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
     *
     * Sin `contentIntent` a un punto: la noticia es que ese punto ya no vale.
     */
    override fun showSharedCarLeft(authorName: String?) {
        val title = authorName
            ?.let { context.getString(R.string.notif_shared_car_left_title, it) }
            ?: context.getString(R.string.notif_shared_car_left_title_unknown)
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_shared_car_left_text))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(buildOpenAppIntent(RC_SHARED_CAR_LEFT))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(
            AppNotificationManager.SHARED_CAR_LEFT_NOTIFICATION_ID,
            notification,
        )
    }

    /**
     * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] Plus fuel alert: a cheaper pump than
     * the driver's usual one, near them. Informative (UPLOAD channel), same tier and same
     * double-gating as the zone alert — the watch use case checks the switch at decision time and
     * this choke point re-reads it, because a notification method is where a gate cannot be skipped.
     */
    override fun showCheaperFuelNearby(
        stationName: String?,
        savingLabel: String,
        referenceName: String?,
        latitude: Double,
        longitude: Double,
        quotesPerGallon: Boolean,
    ) {
        if (!appPreferences.notifyFuelPrices) return
        val title = stationName
            ?.let { context.getString(R.string.notif_fuel_cheaper_title_named, it) }
            ?: context.getString(R.string.notif_fuel_cheaper_title)
        // Null reference = the saving was measured against what is normal around here, and that
        // sentence is a locale decision — which is why it is worded HERE and not in the worker.
        val referenceLabel = referenceName ?: context.getString(R.string.notif_fuel_reference_area)
        // The saving arrives already written in the unit this driver buys in, so the sentence has to
        // agree with it. [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
        val cheaperText = if (quotesPerGallon) {
            R.string.notif_fuel_cheaper_text_gallon
        } else {
            R.string.notif_fuel_cheaper_text
        }
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(cheaperText, savingLabel, referenceLabel))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(buildFocusIntent(RC_CHEAPER_FUEL, latitude, longitude))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.CHEAPER_FUEL_NOTIFICATION_ID, notification)
    }

    /**
     * [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] What the followed pumps did overnight
     * — ONE card for the whole set. Four followed stations must not become four cards, which is the
     * fastest way to teach someone to turn a vigilance feature off.
     */
    override fun showFuelPriceMove(
        stationName: String?,
        deltaLabel: String,
        wentUp: Boolean,
        otherMovesCount: Int,
        latitude: Double,
        longitude: Double,
        quotesPerGallon: Boolean,
        lane: FuelMoveLane,
    ) {
        if (!appPreferences.notifyFuelPrices) return
        // ⛔ El nombre sirve a los dos carriles — «Repsol Gran Vía ha cambiado de precio» es verdad
        // la sigas o no—, y lo que NO se comparte es la frase de cuando no hay nombre: ahí hay que
        // decir de quién se habla, y llamar «una que sigues» a una de tu alrededor sería regalar el
        // eje de Plus en una línea. [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
        val title = stationName
            ?.let { context.getString(R.string.notif_fuel_move_title_named, it) }
            ?: context.getString(
                when (lane) {
                    FuelMoveLane.FOLLOWED -> R.string.notif_fuel_move_title
                    FuelMoveLane.NEARBY -> R.string.notif_fuel_move_title_nearby
                },
            )
        // ⛔ Four whole sentences, not a direction word glued onto a stem: «cheaper/dearer» inflects
        // with what follows it in half the nine locales, and a composed string is where that breaks
        // silently. The count is a separate variant for the same reason.
        // ⛔ Y ocho, no cuatro: la unidad del surtidor multiplica las variantes porque tampoco es un
        // sufijo pegable. [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
        val gallon = quotesPerGallon
        val body = when {
            wentUp && otherMovesCount > 0 -> context.getString(
                if (gallon) R.string.notif_fuel_move_text_up_more_gallon else R.string.notif_fuel_move_text_up_more,
                deltaLabel, otherMovesCount.toString(),
            )
            wentUp -> context.getString(
                if (gallon) R.string.notif_fuel_move_text_up_gallon else R.string.notif_fuel_move_text_up,
                deltaLabel,
            )
            otherMovesCount > 0 -> context.getString(
                if (gallon) R.string.notif_fuel_move_text_down_more_gallon else R.string.notif_fuel_move_text_down_more,
                deltaLabel, otherMovesCount.toString(),
            )
            else -> context.getString(
                if (gallon) R.string.notif_fuel_move_text_down_gallon else R.string.notif_fuel_move_text_down,
                deltaLabel,
            )
        }
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification_logo)
            // A rise is not an error and a fall is not a success: the same neutral brand colour
            // either way. The direction is in the words. [UI-COLOR-DOCTRINE-001]
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(buildFocusIntent(RC_FUEL_PRICE_MOVE, latitude, longitude))
            .setAutoCancel(true)
            .build()
        // Una tarjeta por carril: los dos pueden tener algo que decir el mismo día.
        // [FUEL-A-NEARBY-PRICE-IS-FREE-NEWS-001]
        notificationManager.notify(
            when (lane) {
                FuelMoveLane.FOLLOWED -> AppNotificationManager.FUEL_PRICE_MOVE_NOTIFICATION_ID
                FuelMoveLane.NEARBY -> AppNotificationManager.NEARBY_FUEL_MOVE_NOTIFICATION_ID
            },
            notification,
        )
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
        userAnswered: Boolean,
    ) {
        // [DET-ASK-STATE-001] This card MORPHS the prompt at the same notification id: whatever the
        // question was, it is now answered ("parked — revert?"). Closing the window here is what
        // makes "the last op on this channel decides" true for the morph path too.
        runCatching { appPreferences.clearPendingPromptWindow() }
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
        val builder = NotificationCompat.Builder(context, ACTION_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_parking_saved_confirm_text))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(buildFocusIntent(RC_SAVED_FOCUS, latitude, longitude))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
        // [NOTIF-ONE-QUESTION-DESERVES-ONE-NOTIFICATION-001] The acknowledge exists for the SILENT
        // path, where this card is the first the user hears of the parking. If they just tapped
        // "Sí, confirmar" on the ask, re-offering it asks the same question twice — and the string
        // it used was literally the ask's own button. Their answer was the acknowledgement.
        if (!userAnswered) {
            builder.addAction(0, context.getString(R.string.notif_action_ack), ackPi)
        }
        // The undo survives both paths: "yes, I parked" is not a statement about WHICH vehicle.
        builder.addAction(0, context.getString(R.string.notif_action_cancel_save), revertPi)
        // [NOTIF-A-FLOW-SPEAKS-THROUGH-ONE-SLOT-001] Settings → "Parking detected · Alert when your
        // parking is saved" now governs THIS card, which is the one the user actually sees. It used
        // to gate only the legacy action-less duplicate, so turning the alert off silenced nothing.
        //
        // ⛔ It gates the ALERT, not the card: posting it silently keeps "No, cancelar" reachable,
        // and the undo is the anti-false-positive net that [SETTINGS-AUDIT-REMEDIATION-001] says is
        // never gated from app prefs. "Do not interrupt me" is not "hide my way out".
        //
        // And it does not buzz again for an answer the user just gave.
        if (userAnswered || !appPreferences.notifyParkingDetected) builder.setSilent(true)
        val notification = builder.build()
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
        // [DET-WATCHDOG-DEPARTURE-KNOWS-NO-HOUR-001] The second way out of the same question. "I've
        // left" now only CLOSES the session — it cannot advertise a plaza it has no hour for — so
        // the user who left three hours ago is left holding the useful half of the answer: their car
        // is parked somewhere else by now, and that pin is what the app actually needs. Confirming
        // it replaces the active session and drops the orphan fence, so both actions converge on a
        // clean state. DETECTION raised this ask, so the pin keeps detection provenance, same as the
        // mark-parking nudge. [DET-NUDGE-PIN-PROVENANCE-001]
        val markParkingPi = buildAddParkingIntent(RC_STILL_PARKED_MARK, fromDetection = true)
        val notification = NotificationCompat.Builder(context, ACTION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_still_parked_title))
            .setContentText(context.getString(R.string.notif_still_parked_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_still_parked_text)),
            )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_CONFIRMATION)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(buildFocusIntent(RC_STILL_PARKED_FOCUS, latitude, longitude))
            .addAction(0, context.getString(R.string.notif_action_ive_left), leftPi)
            .addAction(0, context.getString(R.string.notif_action_mark_parking), markParkingPi)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.STILL_PARKED_NOTIFICATION_ID, notification)
    }

    override fun showFirstParkNudge() {
        // Low-priority, gentle nudge on the UPLOAD channel (DEFAULT importance). Tap AND the action
        // both deep-link straight into manual add-parking mode (not just Home), matching the
        // "Marcar mi plaza" promise. [DET-TOGGLE-002] No detection event nominated this ask —
        // the confirmed pin is a plain manual report. [DET-NUDGE-PIN-PROVENANCE-001]
        val addParkingIntent = buildAddParkingIntent(RC_FIRST_PARK_NUDGE, fromDetection = false)
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_first_park_nudge_title))
            .setContentText(context.getString(R.string.notif_first_park_nudge_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_first_park_nudge_text)),
            )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(addParkingIntent)
            .addAction(0, context.getString(R.string.notif_first_park_nudge_action), addParkingIntent)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.FIRST_PARK_NUDGE_NOTIFICATION_ID, notification)
    }

    override fun showBackgroundReliabilityWarning() {
        // Contextual, evidence-backed ask [OEM-KILL-001][BATTERY-ASK-001]: fires only after the
        // safety net measured a real hours-long background blackout with a session active.
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_background_kill_title))
            .setContentText(context.getString(R.string.notif_background_kill_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_background_kill_text)),
            )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_CONFIRMATION)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(buildOpenAppIntent(RC_BACKGROUND_KILL))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.BACKGROUND_RELIABILITY_NOTIFICATION_ID, notification)
    }

    override fun showDebug(message: String) {
        val notification = NotificationCompat.Builder(context, DEBUG_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_debug_title))
            .setContentText(message)
            // Debug messages are full sentences (state + cause + what to expect) — BigText so the
            // field tester can read them whole on expand instead of a truncated single line.
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(R.drawable.ic_notification_debug)
            .setColor(COLOR_DEBUG)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(buildOpenAppIntent(RC_DEBUG))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.DEBUG_NOTIFICATION_ID, notification)
    }

    override fun showMarkParkingNudge(source: String?, vehicleId: String?, persistPending: Boolean) {
        // This manual-location question replaces an open "did you park?" prompt in the same slot.
        runCatching { appPreferences.clearPendingPromptWindow() }
        // [DET-NUDGE-PERSIST-001] Persist FIRST: the durable copy is what the Home banner renders
        // when the notification below is slept through (field 2026-07-25). Never let a persist
        // failure suppress the proven notification ask.
        if (persistPending) runCatching {
            appPreferences.setPendingParkNudge(
                PendingParkNudge(
                    createdAtMs = System.currentTimeMillis(),
                    source = source ?: "unknown",
                    vehicleId = vehicleId,
                ),
            )
        }
        // [DET-AR-FIRST-001] A detection session detected movement but could not place the car
        // (no measured driving) — HIGH-importance ACTION channel: the user is about to lose
        // their parking record if they ignore it. Tap and action both deep-link into
        // add-parking pin mode, same promise as the cold-start nudge. DETECTION nominated this
        // ask, so the confirmed pin keeps detection provenance. [DET-NUDGE-PIN-PROVENANCE-001]
        val addParkingIntent = buildAddParkingIntent(RC_MARK_PARKING_NUDGE, fromDetection = true)
        val notification = NotificationCompat.Builder(context, ACTION_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_mark_parking_title))
            .setContentText(context.getString(R.string.notif_mark_parking_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.notif_mark_parking_text)),
            )
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_CONFIRMATION)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(addParkingIntent)
            .addAction(0, context.getString(R.string.notif_mark_parking_action), addParkingIntent)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID, notification)
    }

    /**
     * The closed month, knocking once. [STATS-PLUS-THE-RECAP-KNOCKS-001]
     *
     * Same channel as the zone and fuel alerts and NOT the detection one: a monthly digest must be
     * silenceable without silencing the watch over the car. Tapping opens the app, where the recap
     * card is already the first thing on the stats panel.
     */
    override fun showMonthRecap(parkings: Int, spendText: String?, searchTimeMs: Long?) {
        // ⛔ Three whole sentences instead of gluing clauses: which figures exist varies (a car with
        // no fuel type has no spend, a month with no measured route has no hunting time), and a
        // composed string is where that breaks silently in half the nine locales.
        val minutes = searchTimeMs?.let { (it / MS_PER_MINUTE).toInt() }
        val body = when {
            spendText != null && minutes != null ->
                context.getString(R.string.notif_month_recap_text_both, spendText, minutes.toString())
            spendText != null -> context.getString(R.string.notif_month_recap_text_spend, spendText)
            minutes != null -> context.getString(R.string.notif_month_recap_text_search, minutes.toString())
            else -> context.getString(R.string.notif_month_recap_text_parkings, parkings.toString())
        }
        val notification = NotificationCompat.Builder(context, UPLOAD_CHANNEL_ID)
            .setContentTitle(context.getString(R.string.notif_month_recap_title))
            .setContentText(body)
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setColor(COLOR_SUCCESS)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setContentIntent(buildOpenAppIntent(RC_MONTH_RECAP))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AppNotificationManager.MONTH_RECAP_NOTIFICATION_ID, notification)
    }

    override fun dismiss(notificationId: Int) {
        // [DET-ASK-STATE-001] The single close point. Every way the question ends — the user answers
        // (from the tray OR the Home row: both hooks dismiss), an auto-confirm lands, the response
        // window times out, the user stops detection, a revert runs, or the session's `finally`
        // tears down — reaches the notification through here. Fifteen call sites converge for free,
        // and none of them had to learn about the persisted window.
        if (notificationId == AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID) {
            runCatching { appPreferences.clearPendingPromptWindow() }
        }
        notificationManager.cancel(notificationId)
    }

    // endregion

    /**
     * [COPY-THE-SERVICE-NOTIFICATION-IS-ONE-LINE-001] The shape both resident notifications share:
     * **one short line in the shade, and the long "what this is and how to switch it off" only on
     * expand**. Before this, the two were copy-paste twins that each spent two rows saying one
     * thing — the title named the subsystem ("Parking detection on") and the text said it again,
     * so the one fact the user cannot work out alone, *which car is being watched*, lived in the
     * line the shade truncates first.
     *
     * The rule lives in this signature instead of in a guardrail test: there is no parameter for a
     * `contentText`, so a caller has nowhere to put the second line. A third resident service will
     * inherit the shape by being unable to express its opposite — which is worth more than a
     * prohibition test nobody watches fail.
     *
     * Returns the builder, not the notification: the caller still owns what is genuinely its own
     * (the detection action button, the sentry's MIN priority).
     */
    private fun buildOngoingNotification(
        channelId: String,
        line: String,
        explainer: String,
    ): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setContentTitle(line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(explainer))
            .setSmallIcon(R.drawable.ic_notification_logo)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(buildOpenAppIntent(RC_DETECTION))
            .setOngoing(true)
            .setShowWhen(false)
            .setColor(COLOR_DETECTION)

    private fun buildDetectionNotificationWith(vehicleName: String?): Notification {
        // The single line answers "what is being watched right now", so the vehicle belongs in it —
        // it used to sit in the secondary row, which is the one that gets truncated.
        val line = if (vehicleName != null) {
            context.getString(R.string.notif_detection_title_vehicle, vehicleName)
        } else {
            context.getString(R.string.notif_detection_title)
        }
        // [DET-STOP-BUTTON-001] The way out of a trip the user does not want followed, where they
        // actually see it running: the phone is locked in a pocket, not showing Home. Routed through
        // the same receiver as the other notification answers.
        val stopPi = PendingIntent.getBroadcast(
            context, RC_DETECTION_STOP,
            Intent(ParkingConfirmationReceiver.ACTION_USER_STOP).apply {
                setClass(context, ParkingConfirmationReceiver::class.java)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return buildOngoingNotification(
            channelId = DETECTION_CHANNEL_ID,
            line = line,
            explainer = context.getString(R.string.notif_detection_explainer),
        )
            .addAction(0, context.getString(R.string.notif_action_stop_detection), stopPi)
            .build()
    }

    /**
     * [DET-RESIDENT-FGS-001 · F3] The sentry (resident watcher) FGS notification. Deliberately the
     * quietest thing the OS allows: its own MIN-importance channel (no sound, no vibration, collapsed
     * in the shade), copy in plain user language — what the app is doing for you and where to turn it
     * off — never internal mechanics.
     *
     * It is also the longest-lived thing the user sees: it sits in the shade for the whole time the
     * car is parked. That is why it is the one that most had to lose its second row.
     */
    override fun buildSentryNotification(): Notification =
        buildOngoingNotification(
            channelId = SENTRY_CHANNEL_ID,
            line = context.getString(R.string.notif_sentry_title),
            explainer = context.getString(R.string.notif_sentry_text),
        )
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

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

    /**
     * PendingIntent that opens MainActivity and asks Home to enter add-parking pin mode.
     * The flag is passed as an extra and consumed by [MainActivity] (onCreate for cold start,
     * onNewIntent when already running). [DET-TOGGLE-002]
     *
     * @param fromDetection true when a DETECTION nudge raised this ask — the confirmed pin then
     *   keeps detection provenance (`AUTO_DETECTED`, path `nudge`). [DET-NUDGE-PIN-PROVENANCE-001]
     */
    private fun buildAddParkingIntent(requestCode: Int, fromDetection: Boolean): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_START_ADD_PARKING, true)
                putExtra(MainActivity.EXTRA_ADD_PARKING_FROM_DETECTION, fromDetection)
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
        // [DET-RESIDENT-FGS-001 · F3] Separate MIN-importance channel so the resident watch can be
        // silenced/minimised independently of the active-detection notification.
        val sentryChannel = NotificationChannel(
            SENTRY_CHANNEL_ID,
            context.getString(R.string.channel_sentry_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.channel_sentry_desc)
            enableLights(false)
            enableVibration(false)
            setShowBadge(false)
        }
        notificationManager.createNotificationChannels(
            listOf(detectionChannel, uploadChannel, actionChannel, debugChannel, sentryChannel)
        )
    }

    // ⛔ Aquí vivía `quotesPerGallon()`, que leía el LOCALE del móvil para decidir si la frase decía
    // «galón». Era la misma pregunta equivocada que ponía «7.552 EUR» en una gasolinera de Cádiz: la
    // unidad es del MERCADO del que viene el precio, no de la región del teléfono, así que ahora
    // llega como parámetro pegada a la cifra que describe.
    // [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001]

    companion object {
        const val DETECTION_CHANNEL_ID = "detection_channel"
        const val UPLOAD_CHANNEL_ID = "upload_channel"
        const val ACTION_CHANNEL_ID = "action_channel"
        const val DEBUG_CHANNEL_ID = "debug_channel"
        const val SENTRY_CHANNEL_ID = "sentry_channel" // [DET-RESIDENT-FGS-001 · F3]
        private const val LEGACY_MARK_PARKING_NUDGE_NOTIFICATION_ID = 2008

        // PendingIntent request codes — must be unique across the app
        private const val MS_PER_MINUTE = 60_000L
        private const val RC_DETECTION = 10
        private const val RC_CONFIRMATION = 11
        private const val RC_PARKING_SAVED = 12
        private const val RC_DEBUG = 14
        private const val RC_MONTH_RECAP = 217
        private const val RC_PERMISSION_REVOKED = 15
        private const val RC_CONFIRMATION_FAILED = 17
        private const val RC_ZONE_SPOT_ALERT = 18
        private const val RC_CHEAPER_FUEL = 19
        private const val RC_FUEL_PRICE_MOVE = 20
        private const val RC_SHARED_CAR_PARKED = 21
        private const val RC_SHARED_CAR_LEFT = 22
        private const val RC_CONFIRM_YES = 200
        private const val RC_CONFIRM_NO = 201
        // [REFACTOR-300] post-save notification request codes
        private const val RC_SAVED_FOCUS = 202
        private const val RC_SAVED_ACK = 203
        private const val RC_SAVED_REVERT = 204
        // [DET-AR-REARM-001] watchdog "still parked?" prompt request codes
        private const val RC_STILL_PARKED_FOCUS = 205
        private const val RC_STILL_PARKED_LEFT = 206
        // [DET-WATCHDOG-DEPARTURE-KNOWS-NO-HOUR-001] "mark parking" action on the same prompt
        private const val RC_STILL_PARKED_MARK = 211
        // [DET-TOGGLE-002] cold-start nudge request code
        private const val RC_FIRST_PARK_NUDGE = 207
        // [OEM-KILL-001] background-kill warning request code
        private const val RC_BACKGROUND_KILL = 208
        // [DET-AR-FIRST-001] "where did you leave your car?" nudge request code
        private const val RC_MARK_PARKING_NUDGE = 209
        // [DET-STOP-BUTTON-001] "stop detection" action on the ongoing-detection notification
        private const val RC_DETECTION_STOP = 210

        // Accent colors per notification type
        private val COLOR_DETECTION = Color.rgb(25, 118, 210)    // Blue   — GPS active
        private val COLOR_CONFIRMATION = Color.rgb(245, 124, 0)  // Orange — needs attention
        private val COLOR_SUCCESS = Color.rgb(56, 142, 60)       // Green  — success
        private val COLOR_DEBUG = Color.rgb(123, 31, 162)        // Purple — debug
    }
}
