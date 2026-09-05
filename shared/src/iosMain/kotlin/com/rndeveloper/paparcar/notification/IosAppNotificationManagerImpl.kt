@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.notification

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.CONFIRMATION_FAILED_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.DEBUG_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.PARKING_CONFIRMATION_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.STILL_PARKED_NOTIFICATION_ID
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager.Companion.UPLOAD_NOTIFICATION_ID
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionDestructive
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNUserNotificationCenter

/**
 * iOS implementation of [AppNotificationManager] backed by [UNUserNotificationCenter].
 *
 * Permission acquisition is owned by [com.rndeveloper.paparcar.ios.permissions.IosPermissionRequester]
 * — this class assumes authorization has already been granted (or the OS will silently drop posts).
 *
 * Notification action routing (iOS equivalent of `ParkingConfirmationReceiver`) lives in
 * [IosNotificationActionHandler], registered as the system delegate from `MainViewController`.
 *
 * Strings are intentionally hardcoded in English to match the Android `notif_*` resources.
 * When notification copy is unified into `composeResources/strings.xml`, route both platforms
 * through `getString(Res.string.notif_confirmation_title)` instead.
 */
class IosAppNotificationManagerImpl : AppNotificationManager {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    init {
        registerCategories()
    }

    override fun showParkingConfirmation(
        score: Float,
        vehicleName: String?,
        candidate: GpsPoint?,
        street: String?,
    ) {
        val content = UNMutableNotificationContent().apply {
            setTitle(
                if (vehicleName != null) "Did you park your $vehicleName?" else "Did you park?",
            )
            setBody("Looks like you parked. Shall we confirm the spot?")
            setCategoryIdentifier(CATEGORY_PARKING_CONFIRMATION)
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
        post(PARKING_CONFIRMATION_NOTIFICATION_ID, content)
    }

    override fun showParkingSaved(latitude: Double, longitude: Double) {
        // Parity gap with Android [SETTINGS-AUDIT-REMEDIATION-001]: on Android this informative
        // notification is gated by AppPreferences.notifyParkingDetected at this same choke point.
        // This stub predates DI of prefs here — wire the gate when the iOS notification stack is
        // brought up for real (kept unchanged to avoid touching the iOS DI graph this ticket).
        val content = UNMutableNotificationContent().apply {
            setTitle("Parking saved")
            setBody("Your car has been parked. Tap to see it on the map.")
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
        post(UPLOAD_NOTIFICATION_ID, content)
    }

    override fun showParkingSavedConfirm(
        parkingId: String,
        vehicleName: String?,
        latitude: Double,
        longitude: Double,
    ) {
        val title = if (vehicleName != null) "$vehicleName parked" else "Vehicle parked"
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody("Confirm or cancel the saved parking spot.")
            setCategoryIdentifier(CATEGORY_PARKING_SAVED_CONFIRM)
            setUserInfo(mapOf<Any?, Any?>(EXTRA_PARKING_ID to parkingId))
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
        // Replaces the pre-save prompt at the same notification ID — see [REFACTOR-300] on the interface.
        post(PARKING_CONFIRMATION_NOTIFICATION_ID, content)
    }

    /** [IOS-F2-A-WAKE-MUST-QUERY-THE-PAST-001] The safety-net mesh's question — the assisted
     *  tier's core surface: when the mesh sees signs the car moved but cannot prove it, it ASKS
     *  instead of guessing. "I drove away" routes to a WITNESSED departure that frees the session
     *  (never publishes — the user attests the fact, not the hour); "Still parked" dismisses. */
    override fun showStillParkedPrompt(geofenceId: String, latitude: Double, longitude: Double) {
        val content = UNMutableNotificationContent().apply {
            setTitle("Are you still parked?")
            setBody("It looks like your car may have moved. If you drove away, let us know so your old spot can be freed.")
            setCategoryIdentifier(CATEGORY_STILL_PARKED)
            setUserInfo(mapOf<Any?, Any?>(EXTRA_GEOFENCE_ID to geofenceId))
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
        post(STILL_PARKED_NOTIFICATION_ID, content)
    }

    override fun showConfirmationFailed() {
        val content = UNMutableNotificationContent().apply {
            setTitle("Could not save parking")
            setBody("Open Paparcar to confirm manually.")
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
        post(CONFIRMATION_FAILED_NOTIFICATION_ID, content)
    }

    override fun updateDetectionVehicle(vehicleName: String, notifId: Int) {
        // iOS foreground service notifications are not applicable — no-op.
    }

    override fun showPermissionRevoked() = Unit // Android-only concept; iOS handles this via system UI

    override fun showDebug(message: String) {
        val content = UNMutableNotificationContent().apply {
            setTitle("Paparcar Debug")
            setBody(message)
        }
        post(DEBUG_NOTIFICATION_ID, content)
    }

    override fun dismiss(notificationId: Int) {
        val identifiers = listOf(identifierFor(notificationId))
        center.removePendingNotificationRequestsWithIdentifiers(identifiers)
        center.removeDeliveredNotificationsWithIdentifiers(identifiers)
    }

    private fun post(notificationId: Int, content: UNMutableNotificationContent) {
        val request = UNNotificationRequest.requestWithIdentifier(
            identifier = identifierFor(notificationId),
            content = content,
            trigger = null, // immediate delivery
        )
        center.addNotificationRequest(request, withCompletionHandler = null)
    }

    private fun registerCategories() {
        val confirmedAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_CONFIRMED,
            title = "Yes, I parked",
            options = UNNotificationActionOptionForeground,
        )
        val deniedAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_DENIED,
            title = "No, I haven't parked",
            options = UNNotificationActionOptionForeground,
        )
        val parkingCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_PARKING_CONFIRMATION,
            actions = listOf(confirmedAction, deniedAction),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone,
        )
        val ackAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_ACK,
            title = "Yes, confirm",
            options = UNNotificationActionOptionForeground,
        )
        val revertAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_REVERT,
            title = "No, cancel",
            options = UNNotificationActionOptionDestructive,
        )
        val savedConfirmCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_PARKING_SAVED_CONFIRM,
            actions = listOf(ackAction, revertAction),
            intentIdentifiers = emptyList<String>(),
            options = UNNotificationCategoryOptionNone,
        )
        val departedAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_DEPARTED,
            title = "I drove away",
            options = UNNotificationActionOptionForeground,
        )
        val stillParkedAction = UNNotificationAction.actionWithIdentifier(
            identifier = ACTION_STILL_PARKED,
            title = "Still parked",
            options = UNNotificationActionOptionNone,
        )
        val stillParkedCategory = UNNotificationCategory.categoryWithIdentifier(
            identifier = CATEGORY_STILL_PARKED,
            actions = listOf(departedAction, stillParkedAction),
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
        const val ACTION_STILL_PARKED = "paparcar_action_still_parked"
        const val EXTRA_PARKING_ID = "parkingId"
        const val EXTRA_GEOFENCE_ID = "geofenceId"
    }
}
