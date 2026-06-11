@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.notification

import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.notification.AppNotificationManager.Companion.CONFIRMATION_FAILED_NOTIFICATION_ID
import io.apptolast.paparcar.domain.notification.AppNotificationManager.Companion.DEBUG_NOTIFICATION_ID
import io.apptolast.paparcar.domain.notification.AppNotificationManager.Companion.HOME_PARKING_NOTIFICATION_ID
import io.apptolast.paparcar.domain.notification.AppNotificationManager.Companion.PARKING_CONFIRMATION_NOTIFICATION_ID
import io.apptolast.paparcar.domain.notification.AppNotificationManager.Companion.SPOT_PUBLISHED_NOTIFICATION_ID
import io.apptolast.paparcar.domain.notification.AppNotificationManager.Companion.UPLOAD_NOTIFICATION_ID
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
 * Permission acquisition is owned by [io.apptolast.paparcar.ios.permissions.IosPermissionRequester]
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

    override fun showParkingConfirmation(score: Float, vehicleName: String?) {
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

    override fun showHomeParkingLeft(label: String, lat: Double, lon: Double) {
        val content = UNMutableNotificationContent().apply {
            setTitle("You left $label")
            setBody("Your habitual spot is now visible to nearby drivers.")
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
        post(HOME_PARKING_NOTIFICATION_ID, content)
    }

    override fun showConfirmationFailed() {
        val content = UNMutableNotificationContent().apply {
            setTitle("Could not save parking")
            setBody("Open Paparcar to confirm manually.")
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
        post(CONFIRMATION_FAILED_NOTIFICATION_ID, content)
    }

    override fun showSpotPublished(latitude: Double, longitude: Double) {
        val content = UNMutableNotificationContent().apply {
            setTitle("Spot available for others")
            setBody("Your parking spot is now visible to nearby drivers.")
            setSound(platform.UserNotifications.UNNotificationSound.defaultSound)
        }
        post(SPOT_PUBLISHED_NOTIFICATION_ID, content)
    }

    override fun updateDetectionVehicle(vehicleName: String, notifId: Int) {
        // iOS foreground service notifications are not applicable — no-op.
    }

    override fun showSpotUploading() {
        val content = UNMutableNotificationContent().apply {
            setTitle("Paparcar")
            setBody("Uploading new spot…")
        }
        post(UPLOAD_NOTIFICATION_ID, content)
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
        center.setNotificationCategories(setOf(parkingCategory, savedConfirmCategory))
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
        const val EXTRA_PARKING_ID = "parkingId"
    }
}
