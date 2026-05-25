package io.apptolast.paparcar

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.apptolast.customlogin.appContext
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import com.apptolast.customlogin.config.GoogleSignInConfig
import com.apptolast.customlogin.di.LoginLibraryConfig
import com.apptolast.customlogin.di.initLoginKoin
import io.apptolast.paparcar.detection.worker.DetectionHeartbeatWorker
import io.apptolast.paparcar.detection.worker.GeofenceJanitorWorker
import io.apptolast.paparcar.detection.worker.RegisterActivityTransitionsWorker
import io.apptolast.paparcar.di.androidDetectionModule
import io.apptolast.paparcar.di.androidPlatformModule
import io.apptolast.paparcar.di.dataModule
import io.apptolast.paparcar.di.domainModule
import io.apptolast.paparcar.di.presentationModule
import io.apptolast.paparcar.logging.FileAntilog
import org.koin.android.ext.koin.androidContext

class PaparcarApp : Application() {
    override fun onCreate() {
        super.onCreate()

        appContext = this
        if (BuildConfig.DEBUG) {
            Napier.base(DebugAntilog())
            Napier.base(FileAntilog(this))
            Napier.d("GOOGLE_WEB_CLIENT_ID = '${BuildConfig.GOOGLE_WEB_CLIENT_ID}'", tag = "PaparcarApp")
        }

        val loginConfig = LoginLibraryConfig(
            googleSignInConfig = GoogleSignInConfig(
                webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            ),
        )

        initLoginKoin(config = loginConfig) {
            androidContext(this@PaparcarApp)
            modules(
                presentationModule,
                domainModule,
                dataModule,
                androidDetectionModule,
                androidPlatformModule,
            )
        }

        val workManager = WorkManager.getInstance(this)

        // Re-register Activity Recognition transitions every 12h, but only if
        // the permission is already granted (existing users). New users trigger
        // enqueueKeep() from PermissionsScreen once they grant the permission.
        if (hasActivityRecognitionPermission()) {
            RegisterActivityTransitionsWorker.enqueueKeep(workManager)
        }

        // Re-register geofences for active sessions every 12h. Geofences have a 24h TTL to
        // prevent orphan accumulation after process kills; the janitor renews them in time. [GEOF-001]
        GeofenceJanitorWorker.enqueueKeep(workManager)

        // Heartbeat: restart the detection foreground service every 15 min if a session is active
        // and the service was killed by Doze / OEM battery management. [DOZE-001]
        DetectionHeartbeatWorker.enqueueKeep(workManager)
    }

    private fun hasActivityRecognitionPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
}
