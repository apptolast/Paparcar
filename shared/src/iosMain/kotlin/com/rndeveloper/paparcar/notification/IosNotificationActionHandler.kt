@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.rndeveloper.paparcar.notification

import com.rndeveloper.paparcar.domain.detection.CoordinatorParkingDetector
import com.rndeveloper.paparcar.domain.detection.isCurrentFor
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.usecase.parking.RevertParkingUseCase
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
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
    /** Ratifying a saved parking shares the same guarded operation as Home and Android. */
    private val confirmStillParked: com.rndeveloper.paparcar.domain.usecase.parking.ConfirmStillParkedUseCase,
    private val preferences: com.rndeveloper.paparcar.domain.preferences.AppPreferences,
    private val startAddParking: com.rndeveloper.paparcar.domain.event.StartAddParkingEventBus,
    /** [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] "Yes, this is my car" writes the observed MAC
     *  onto the vehicle, the same one-shot Settings uses on Android. */
    private val vehicleRepository: com.rndeveloper.paparcar.domain.repository.VehicleRepository,
    /** [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] "No" is remembered so the same MAC is never
     *  offered again. */
    private val carLinkDeclineStore: com.rndeveloper.paparcar.domain.detection.ports.CarLinkDeclineStore,
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
            IosAppNotificationManagerImpl.ACTION_STILL_HERE,
            IosAppNotificationManagerImpl.ACTION_DEPARTED,
            IosAppNotificationManagerImpl.ACTION_MARK_PARKING,
            -> {
                val info = didReceiveNotificationResponse.notification.request.content.userInfo
                val geofenceId = info[IosAppNotificationManagerImpl.EXTRA_GEOFENCE_ID] as? String
                val shownAtMs = (info[IosAppNotificationManagerImpl.EXTRA_QUESTION_AT] as? String)?.toLongOrNull()
                val question = preferences.stillParkedQuestion()?.takeIf {
                    it.isCurrentFor(geofenceId, shownAtMs)
                }
                if (question != null) {
                    if (actionId == IosAppNotificationManagerImpl.ACTION_STILL_HERE) {
                        scope.launch {
                            confirmStillParked(question).onFailure {
                                PaparcarLogger.w(TAG, "Could not ratify the saved parking", it)
                                notificationPort.showConfirmationFailed()
                            }
                        }
                    } else {
                        startAddParking.request(
                            fromDetectionNudge = false, geofenceId = question.geofenceId,
                            questionShownAtMs = question.shownAtMs,
                        )
                    }
                }
            }
            IosAppNotificationManagerImpl.ACTION_LINK_CAR -> {
                // [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] Background write of the framed MAC.
                val info = didReceiveNotificationResponse.notification.request.content.userInfo
                val vehicleId = info[IosAppNotificationManagerImpl.EXTRA_LINK_VEHICLE_ID] as? String
                val deviceAddress = info[IosAppNotificationManagerImpl.EXTRA_LINK_DEVICE_ADDRESS] as? String
                if (vehicleId == null || deviceAddress == null) {
                    PaparcarLogger.w(TAG, "ACTION_LINK_CAR missing vehicleId/deviceAddress in userInfo")
                } else {
                    scope.launch { vehicleRepository.updateBluetoothDevice(vehicleId, deviceAddress) }
                }
                notificationPort.dismiss(AppNotificationManager.CAR_LINK_OFFER_NOTIFICATION_ID)
            }
            IosAppNotificationManagerImpl.ACTION_DISMISS_LINK -> {
                // Take "no" for an answer: remember the MAC so we never offer it again.
                val deviceAddress = didReceiveNotificationResponse.notification.request.content
                    .userInfo[IosAppNotificationManagerImpl.EXTRA_LINK_DEVICE_ADDRESS] as? String
                if (deviceAddress != null) scope.launch { carLinkDeclineStore.decline(deviceAddress) }
                notificationPort.dismiss(AppNotificationManager.CAR_LINK_OFFER_NOTIFICATION_ID)
            }
            // Includes UNNotificationDefaultActionIdentifier (tap on body) and dismiss — let the OS handle.
        }
        withCompletionHandler()
    }
}

// File-level constant: Kotlin/Native forbids companion objects with fields inside
// subclasses of ObjC types (this class is a UNUserNotificationCenterDelegate). [IOS-BUILD-FIX]
private const val TAG = "PARKDIAG/IosNotifAction"
