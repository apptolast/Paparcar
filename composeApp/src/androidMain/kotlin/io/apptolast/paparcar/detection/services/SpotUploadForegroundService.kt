package io.apptolast.paparcar.detection.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.apptolast.paparcar.data.notification.AppNotificationManager
import io.apptolast.paparcar.domain.model.Spot
import io.apptolast.paparcar.domain.usecase.location.GetStoredLocationsUseCase
import io.apptolast.paparcar.domain.usecase.notification.BuildSpotUploadNotificationUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import java.util.UUID

class SpotUploadForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val getStoredLocationsUseCase: GetStoredLocationsUseCase by inject()
    private val reportSpotReleasedUseCase: ReportSpotReleasedUseCase by inject()
    private val buildSpotUploadNotificationUseCase: BuildSpotUploadNotificationUseCase by inject()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildSpotUploadNotificationUseCase() as Notification
        startForeground(AppNotificationManager.UPLOAD_NOTIFICATION_ID, notification)

        serviceScope.launch {
            val locationsResult = getStoredLocationsUseCase()
            locationsResult.getOrNull()?.lastOrNull()?.let { lastLocation ->
                Spot(
                    id = UUID.randomUUID().toString(),
                    location = lastLocation,
                    reportedBy = "dummyUser",
                    isActive = true
                ).also { spot ->
                    reportSpotReleasedUseCase(spot)
                }
                //Fixme: Stop the detection service, using the correct path
                val detectionServiceIntent = Intent(
                    this@SpotUploadForegroundService,
                    SpotDetectionForegroundService::class.java
                )
                stopService(detectionServiceIntent)

                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
