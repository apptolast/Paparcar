package io.apptolast.paparcar.detection.services

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import io.apptolast.paparcar.data.notification.AppNotificationManager
import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.location.SaveLocationToLocalUseCase
import io.apptolast.paparcar.domain.usecase.notification.BuildSpotDetectionNotificationUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.android.ext.android.inject

class SpotDetectionForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val observeLocationUpdatesUseCase: ObserveLocationUpdatesUseCase by inject()
    private val saveLocationToLocalUseCase: SaveLocationToLocalUseCase by inject()
    private val buildSpotDetectionNotificationUseCase: BuildSpotDetectionNotificationUseCase by inject()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildSpotDetectionNotificationUseCase() as Notification
        startForeground(AppNotificationManager.DETECTION_NOTIFICATION_ID, notification)

        observeLocationUpdatesUseCase()
            .onEach { location -> saveLocationToLocalUseCase(location) }
            .catch { /* TODO: Handle errors, e.g., log them */ }
            .launchIn(serviceScope)

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
