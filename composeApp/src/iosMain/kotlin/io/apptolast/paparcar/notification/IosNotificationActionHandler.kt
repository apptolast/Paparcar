@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.apptolast.paparcar.notification

import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.notification.AppNotificationManager
import io.apptolast.paparcar.domain.usecase.parking.RevertParkingUseCase
import io.apptolast.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/**
 * iOS equivalent of Android's `ParkingConfirmationReceiver`. Routes user taps on the
 * Yes/No buttons of the parking-confirmation notifications into the same domain hooks.
 *
 * State A (pre-save prompt, category [IosAppNotificationManagerImpl.CATEGORY_PARKING_CONFIRMATION]):
 *  - `ACTION_CONFIRMED` → [CoordinatorParkingDetector.onUserConfirmedParking]
 *  - `ACTION_DENIED`    → [CoordinatorParkingDetector.onUserDeniedParking]
 *
 * State B (post-save card, category [IosAppNotificationManagerImpl.CATEGORY_PARKING_SAVED_CONFIRM]):
 *  - `ACTION_ACK`    → dismiss; user acknowledges the saved spot.
 *  - `ACTION_REVERT` → invoke [RevertParkingUseCase] with parkingId pulled from `userInfo`.
 *
 * Must be installed as a long-lived property on `MainViewController` because
 * `UNUserNotificationCenter.delegate` is a weak reference.
 */
class IosNotificationActionHandler(
    private val coordinator: CoordinatorParkingDetector,
    private val revertParkingUseCase: RevertParkingUseCase,
    private val notificationPort: AppNotificationManager,
) : NSObject(), UNUserNotificationCenterDelegateProtocol {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        val actionId = didReceiveNotificationResponse.actionIdentifier
        PaparcarLogger.d(TAG, "didReceive action=$actionId")
        when (actionId) {
            IosAppNotificationManagerImpl.ACTION_CONFIRMED -> coordinator.onUserConfirmedParking()
            IosAppNotificationManagerImpl.ACTION_DENIED -> coordinator.onUserDeniedParking()
            IosAppNotificationManagerImpl.ACTION_ACK ->
                notificationPort.dismiss(AppNotificationManager.PARKING_CONFIRMATION_NOTIFICATION_ID)
            IosAppNotificationManagerImpl.ACTION_REVERT -> {
                val parkingId = didReceiveNotificationResponse.notification.request.content
                    .userInfo[IosAppNotificationManagerImpl.EXTRA_PARKING_ID] as? String
                if (parkingId == null) {
                    PaparcarLogger.w(TAG, "ACTION_REVERT missing parkingId in userInfo")
                } else {
                    scope.launch { revertParkingUseCase(parkingId) }
                }
            }
            // Includes UNNotificationDefaultActionIdentifier (tap on body) and dismiss — let the OS handle.
        }
        withCompletionHandler()
    }
}

// File-level constant: Kotlin/Native forbids companion objects with fields inside
// subclasses of ObjC types (this class is a UNUserNotificationCenterDelegate). [IOS-BUILD-FIX]
private const val TAG = "IosNotificationActionHandler"
