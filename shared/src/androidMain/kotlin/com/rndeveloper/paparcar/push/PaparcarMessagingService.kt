package com.rndeveloper.paparcar.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * The receiving end of the zone push. [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
 *
 * ⛔ The message is a **data** one, so nothing is shown until this class says so — and that is the
 * whole reason it is not a notification message. Two routes can announce the same free space (this
 * one, and the live stream while the app is open), and what has already been said lives in exactly
 * one place: `zone_alerted_spots`. Letting FCM draw the card itself would have put a second copy of
 * that rule inside the notification tray, where nothing can check it.
 */
class PaparcarMessagingService : FirebaseMessagingService() {

    private val notificationPort: AppNotificationManager by inject()
    private val zoneWatch: ZoneWatchRepository by inject()
    private val subscriptions: ZoneAlertSubscriptionSync by inject()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * A token is not forever: Firebase rotates it on reinstall, restore and clear-data. A rotation
     * nobody wrote down is a device that keeps its subscription row and silently stops receiving
     * anything — the exact failure this whole ticket exists to avoid, one layer lower.
     */
    @Deprecated(
        "Flagged by firebase-messaging 25.1.3 as part of the FCM→FID registration migration. " +
            "There is no replacement callback yet and no removal date has been announced, so the " +
            "choice is between this and having no idea the token rotated. Revisit when Firebase " +
            "ships the successor — a rotation nobody hears about is a device that silently stops " +
            "receiving alerts.",
    )
    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        PaparcarLogger.d(TAG) { "FCM token rotated — resyncing subscriptions" }
        subscriptions.resyncNow()
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != TYPE_ZONE_SPOT) {
            PaparcarLogger.w(TAG, "unknown push type=${data["type"]}")
            return
        }
        val spotId = data["spotId"] ?: return
        val zoneId = data["zoneId"] ?: return
        val zoneName = data["zoneName"].orEmpty()
        val latitude = data["latitude"]?.toDoubleOrNull() ?: return
        val longitude = data["longitude"]?.toDoubleOrNull() ?: return
        val isProvisional = data["provisional"]?.toBooleanStrictOrNull() == true
        val expiresAt = data["expiresAt"]?.toLongOrNull() ?: 0L

        scope.launch {
            val now = System.currentTimeMillis()
            // Already dead by the time it reached us: a push about a space that no longer exists is
            // worse than no push at all.
            if (expiresAt != 0L && expiresAt <= now) {
                PaparcarLogger.d(TAG) { "· spot $spotId expired before delivery" }
                return@launch
            }
            // The single door. The stream may have said it already while the app was open.
            if (zoneWatch.hasAnnounced(zoneId, spotId)) {
                PaparcarLogger.d(TAG) { "· spot $spotId already announced for zone $zoneId" }
                return@launch
            }
            zoneWatch.recordAnnounced(zoneId, spotId, expiresAt, now)
            notificationPort.showZoneSpotAlert(
                zoneName = zoneName,
                latitude = latitude,
                longitude = longitude,
                isProvisional = isProvisional,
            )
            PaparcarLogger.i(TAG, "▶ zone push shown: zone=$zoneName provisional=$isProvisional")
        }
    }

    private companion object {
        const val TAG = "ZonePush"

        /** ⛔ Mirrored in `functions/src/index.ts`. */
        const val TYPE_ZONE_SPOT = "zone_spot"
    }
}
