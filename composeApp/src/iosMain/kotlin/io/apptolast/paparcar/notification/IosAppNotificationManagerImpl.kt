@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.notification

import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.notification.AppNotificationManager.Companion.DEBUG_NOTIFICATION_ID
import io.apptolast.paparcar.domain.notification.AppNotificationManager.Companion.PARKING_CONFIRMATION_NOTIFICATION_ID
import io.apptolast.paparcar.domain.notification.AppNotificationManager.Companion.UPLOAD_NOTIFICATION_ID
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
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
 * Notification action routing (the iOS equivalent of `ParkingConfirmationReceiver`) is **not yet
 * wired up**. The Yes/No buttons render correctly but tapping them only opens the app — there is
 * no `UNUserNotificationCenterDelegate` translating the response back into a Kotlin handler.
 * Hooking that up belongs with the iOS detection pipeline (Phase 6 ActivityRecognition + Geofence).
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
        center.setNotificationCategories(setOf(parkingCategory))
    }

    private fun identifierFor(notificationId: Int): String = "$ID_PREFIX$notificationId"

    private companion object {
        const val ID_PREFIX = "paparcar_"
        const val CATEGORY_PARKING_CONFIRMATION = "paparcar_parking_confirmation"
        const val ACTION_CONFIRMED = "paparcar_action_confirmed"
        const val ACTION_DENIED = "paparcar_action_denied"
    }
}
