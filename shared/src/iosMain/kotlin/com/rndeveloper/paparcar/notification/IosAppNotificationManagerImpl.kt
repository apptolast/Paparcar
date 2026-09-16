@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.notification

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.CONFIRMATION_FAILED_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.DEBUG_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.PARKING_CONFIRMATION_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.SHARED_CAR_LEFT_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.SHARED_CAR_PARKED_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.STILL_PARKED_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.UPLOAD_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.ZONE_SPOT_ALERT_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.notif_action_cancel_save
import paparcar.composeapp.generated.resources.notif_action_confirm
import paparcar.composeapp.generated.resources.notif_action_ive_left
import paparcar.composeapp.generated.resources.notif_action_mark_parking
import paparcar.composeapp.generated.resources.notif_action_no_not_parked
import paparcar.composeapp.generated.resources.notif_action_yes_parked
import paparcar.composeapp.generated.resources.notif_confirmation_failed_text
import paparcar.composeapp.generated.resources.notif_confirmation_failed_title
import paparcar.composeapp.generated.resources.notif_confirmation_text
import paparcar.composeapp.generated.resources.notif_confirmation_title
import paparcar.composeapp.generated.resources.notif_confirmation_title_street
import paparcar.composeapp.generated.resources.notif_confirmation_title_vehicle
import paparcar.composeapp.generated.resources.notif_confirmation_title_vehicle_street
import paparcar.composeapp.generated.resources.notif_parking_saved_confirm_text
import paparcar.composeapp.generated.resources.notif_parking_saved_confirm_title
import paparcar.composeapp.generated.resources.notif_parking_saved_confirm_title_vehicle
import paparcar.composeapp.generated.resources.notif_parking_saved_text
import paparcar.composeapp.generated.resources.notif_parking_saved_title
import paparcar.composeapp.generated.resources.notif_shared_car_left_text
import paparcar.composeapp.generated.resources.notif_shared_car_left_title
import paparcar.composeapp.generated.resources.notif_shared_car_left_title_unknown
import paparcar.composeapp.generated.resources.notif_shared_car_parked_text
import paparcar.composeapp.generated.resources.notif_shared_car_parked_text_unknown
import paparcar.composeapp.generated.resources.notif_shared_car_parked_title
import paparcar.composeapp.generated.resources.notif_shared_car_parked_title_unknown
import paparcar.composeapp.generated.resources.notif_still_parked_text
import paparcar.composeapp.generated.resources.notif_still_parked_title
import paparcar.composeapp.generated.resources.notif_zone_spot_text
import paparcar.composeapp.generated.resources.notif_zone_spot_text_provisional
import paparcar.composeapp.generated.resources.notif_zone_spot_title
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionDestructive
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation of [AppNotificationManager] backed by [UNUserNotificationCenter].
 *
 * Copy comes from `composeResources` (`notif_*`, MIRRORED from Android's live
 * `app/src/main/res` source and drift-guarded by `NotificationCopyParityTest`) — the surface
 * speaks the device's language, same keys and translations as Android.
 * [IOS-NOTIF-A-SURFACE-THAT-SPEAKS-ONE-LANGUAGE-001]
 *
 * `getString` is suspend, so every post runs in the manager's scope and first awaits the
 * category registration (whose action titles are resources too). Action routing lives in
 * [IosNotificationActionHandler], installed as the system delegate by `PaparcarIosBootstrap`.
 */
class IosAppNotificationManagerImpl(
    private val appPreferences: AppPreferences,
) : AppNotificationManager {

    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Categories carry localized action titles → registered async; every post awaits this. */
    private val categoriesReady = scope.async { registerCategories() }

    override fun showParkingConfirmation(
        score: Float,
        vehicleName: String?,
        candidate: GpsPoint?,
        street: String?,
    ) {
        postLocalized(PARKING_CONFIRMATION_NOTIFICATION_ID) {
            val title = when {
                vehicleName != null && street != null ->
                    getString(Res.string.notif_confirmation_title_vehicle_street, vehicleName, street)
                vehicleName != null -> getString(Res.string.notif_confirmation_title_vehicle, vehicleName)
                street != null -> getString(Res.string.notif_confirmation_title_street, street)
                else -> getString(Res.string.notif_confirmation_title)
            }
            val body = getString(Res.string.notif_confirmation_text)
            setTitle(title)
            setBody(body)
            setCategoryIdentifier(CATEGORY_PARKING_CONFIRMATION)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
    }

    override fun showParkingSaved(latitude: Double, longitude: Double) {
        // Same choke point as Android [SETTINGS-AUDIT-REMEDIATION-001]: this is the INFORMATIVE
        // notification the user may silence. The anti-false-positive surfaces below (confirmation,
        // saved-confirm, still-parked) are never gated from app prefs, by doctrine.
        if (!appPreferences.notifyParkingDetected) return
        postLocalized(UPLOAD_NOTIFICATION_ID) {
            val title = getString(Res.string.notif_parking_saved_title)
            val body = getString(Res.string.notif_parking_saved_text)
            setTitle(title)
            setBody(body)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
    }

    override fun showParkingSavedConfirm(
        parkingId: String,
        vehicleName: String?,
        latitude: Double,
        longitude: Double,
    ) {
        postLocalized(PARKING_CONFIRMATION_NOTIFICATION_ID) {
            // Replaces the pre-save prompt at the same notification ID. [REFACTOR-300]
            val title = if (vehicleName != null) {
                getString(Res.string.notif_parking_saved_confirm_title_vehicle, vehicleName)
            } else {
                getString(Res.string.notif_parking_saved_confirm_title)
            }
            val body = getString(Res.string.notif_parking_saved_confirm_text)
            setTitle(title)
            setBody(body)
            setCategoryIdentifier(CATEGORY_PARKING_SAVED_CONFIRM)
            setUserInfo(mapOf<Any?, Any?>(EXTRA_PARKING_ID to parkingId))
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
    }

    /** [IOS-F2] The safety-net mesh's question. Same actions as Android's surface: "I've left"
     *  routes to a WITNESSED departure; "Mark parking" opens the app (foreground). */
    override fun showStillParkedPrompt(geofenceId: String, latitude: Double, longitude: Double) {
        postLocalized(STILL_PARKED_NOTIFICATION_ID) {
            val title = getString(Res.string.notif_still_parked_title)
            val body = getString(Res.string.notif_still_parked_text)
            setTitle(title)
            setBody(body)
            setCategoryIdentifier(CATEGORY_STILL_PARKED)
            setUserInfo(mapOf<Any?, Any?>(EXTRA_GEOFENCE_ID to geofenceId))
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
    }

    override fun showConfirmationFailed() {
        postLocalized(CONFIRMATION_FAILED_NOTIFICATION_ID) {
            val title = getString(Res.string.notif_confirmation_failed_title)
            val body = getString(Res.string.notif_confirmation_failed_text)
            setTitle(title)
            setBody(body)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
    }

    /**
     * Una plaza libre en una zona vigilada. [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
     *
     * Mismo doble gating que en Android: el caso de uso mira el interruptor cuando decide y este
     * punto de paso lo vuelve a leer, porque un método de notificación es donde una puerta no se
     * puede saltar. [SETTINGS-AUDIT-REMEDIATION-001]
     *
     * ⛔ Una salida DEDUCIDA lo dice. La plaza sigue mereciendo el aviso —es lo más fresco que hay
     * en el mapa— pero puede retirarse, y una tarjeta que lo escondiera mandaría a alguien a un
     * hueco en el que la propia app ha dejado de creer.
     */
    override fun showZoneSpotAlert(
        zoneName: String,
        latitude: Double,
        longitude: Double,
        isProvisional: Boolean,
    ) {
        if (!appPreferences.notifyZoneSpots) return
        postLocalized(ZONE_SPOT_ALERT_NOTIFICATION_ID) {
            val title = getString(Res.string.notif_zone_spot_title, zoneName)
            val body = if (isProvisional) {
                getString(Res.string.notif_zone_spot_text_provisional)
            } else {
                getString(Res.string.notif_zone_spot_text)
            }
            setTitle(title)
            setBody(body)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
    }

    /**
     * Alguien del grupo ha aparcado el coche compartido. [SHARED-A-PARKING-TELLS-THE-GROUP-001]
     *
     * ⛔ Sin gating de preferencias, igual que en Android: esto no es una notificación informativa
     * del propio aparcamiento, es la única vía por la que el resto del grupo se entera de dónde está
     * el coche.
     *
     * ⚠️ La posición viaja para que el día que iOS sepa abrir el mapa en un punto no haya que
     * volver a tocar el reparto — hoy tocar la tarjeta solo abre la app, la misma carencia que
     * `showParkingSaved` arrastra desde que existe.
     */
    override fun showSharedCarParked(
        authorName: String?,
        addressLine: String?,
        latitude: Double,
        longitude: Double,
    ) {
        postLocalized(SHARED_CAR_PARKED_NOTIFICATION_ID) {
            val title = if (authorName != null) {
                getString(Res.string.notif_shared_car_parked_title, authorName)
            } else {
                getString(Res.string.notif_shared_car_parked_title_unknown)
            }
            val body = if (addressLine != null) {
                getString(Res.string.notif_shared_car_parked_text, addressLine)
            } else {
                getString(Res.string.notif_shared_car_parked_text_unknown)
            }
            setTitle(title)
            setBody(body)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
    }

    /**
     * El coche compartido ya no está donde estaba. [SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001]
     *
     * Sin sitio al que ir: la noticia es justo que ese punto ya no vale.
     */
    override fun showSharedCarLeft(authorName: String?) {
        postLocalized(SHARED_CAR_LEFT_NOTIFICATION_ID) {
            val title = if (authorName != null) {
                getString(Res.string.notif_shared_car_left_title, authorName)
            } else {
                getString(Res.string.notif_shared_car_left_title_unknown)
            }
            val body = getString(Res.string.notif_shared_car_left_text)
            setTitle(title)
            setBody(body)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
    }

    override fun updateDetectionVehicle(vehicleName: String, notifId: Int) {
        // iOS foreground service notifications are not applicable — no-op.
    }

    override fun showPermissionRevoked() = Unit // Android-only concept; iOS handles this via system UI

    override fun showDebug(message: String) {
        postLocalized(DEBUG_NOTIFICATION_ID) {
            setTitle("Paparcar Debug") // debug-only surface, never user-facing copy
            setBody(message)
        }
    }

    override fun dismiss(notificationId: Int) {
        val identifiers = listOf(identifierFor(notificationId))
        center.removePendingNotificationRequestsWithIdentifiers(identifiers)
        center.removeDeliveredNotificationsWithIdentifiers(identifiers)
    }

    private fun postLocalized(
        notificationId: Int,
        build: suspend UNMutableNotificationContent.() -> Unit,
    ) {
        scope.launch {
            categoriesReady.await()
            val content = UNMutableNotificationContent()
            content.build()
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = identifierFor(notificationId),
                content = content,
                trigger = null, // immediate delivery
            )
            center.addNotificationRequest(request, withCompletionHandler = null)
        }
    }

    private suspend fun registerCategories() {
        // ⛔ Every suspend call happens BEFORE the first Objective-C call. A suspension inside an
        // ObjC argument list leaves the arguments already converted (the constant identifier's
        // NSString) as raw, unretained pointers in the coroutine frame; the run loop's autorelease
        // pool drains while `getString` is suspended, and the resumed call hands a freed object to
        // `+[UNNotificationAction actionWithIdentifier:title:options:]` → EXC_BAD_ACCESS in
        // `objc_retain` at launch. Resolve the copy first, then build.
        val yesParked = getString(Res.string.notif_action_yes_parked)
        val noNotParked = getString(Res.string.notif_action_no_not_parked)
        val confirm = getString(Res.string.notif_action_confirm)
        val cancelSave = getString(Res.string.notif_action_cancel_save)
        val iveLeft = getString(Res.string.notif_action_ive_left)
        val markParking = getString(Res.string.notif_action_mark_parking)

        val parkingCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_PARKING_CONFIRMATION,
            actions = listOf(
                UNNotificationAction.actionWithIdentifier(
                    identifier = ACTION_CONFIRMED,
                    title = yesParked,
                    options = UNNotificationActionOptionForeground,
                ),
                UNNotificationAction.actionWithIdentifier(
                    identifier = ACTION_DENIED,
                    title = noNotParked,
                    options = UNNotificationActionOptionForeground,
                ),
            ),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone,
        )
        val savedConfirmCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_PARKING_SAVED_CONFIRM,
            actions = listOf(
                UNNotificationAction.actionWithIdentifier(
                    identifier = ACTION_ACK,
                    title = confirm,
                    options = UNNotificationActionOptionForeground,
                ),
                UNNotificationAction.actionWithIdentifier(
                    identifier = ACTION_REVERT,
                    title = cancelSave,
                    options = UNNotificationActionOptionDestructive,
                ),
            ),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone,
        )
        val stillParkedCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_STILL_PARKED,
            actions = listOf(
                UNNotificationAction.actionWithIdentifier(
                    identifier = ACTION_DEPARTED,
                    title = iveLeft,
                    options = UNNotificationActionOptionForeground,
                ),
                UNNotificationAction.actionWithIdentifier(
                    identifier = ACTION_MARK_PARKING,
                    title = markParking,
                    options = UNNotificationActionOptionForeground,
                ),
            ),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone,
        )
        center.setNotificationCategories(setOf(parkingCategory, savedConfirmCategory, stillParkedCategory))
    }

    private fun identifierFor(notificationId: Int): String = "$ID_PREFIX$notificationId"

    companion object {
        const val ID_PREFIX = "paparcar_"
        const val CATEGORY_PARKING_CONFIRMATION = "paparcar_parking_confirmation"
        const val CATEGORY_PARKING_SAVED_CONFIRM = "paparcar_parking_saved_confirm"
        const val ACTION_CONFIRMED = "paparcar_action_confirmed"
        const val ACTION_DENIED = "paparcar_action_denied"
        const val ACTION_ACK = "paparcar_action_ack"
        const val ACTION_REVERT = "paparcar_action_revert"
        const val CATEGORY_STILL_PARKED = "paparcar_still_parked"
        const val ACTION_DEPARTED = "paparcar_action_departed"
        // [IOS-NOTIF] Android's real second action — the invented "still parked" action is gone.
        const val ACTION_MARK_PARKING = "paparcar_action_mark_parking"
        const val EXTRA_PARKING_ID = "parkingId"
        const val EXTRA_GEOFENCE_ID = "geofenceId"
    }
}
