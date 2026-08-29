package com.rndeveloper.paparcar

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.work.WorkManager
import com.apptolast.customlogin.appContext
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import com.apptolast.customlogin.di.initLoginKoin
import com.rndeveloper.paparcar.di.paparcarLoginConfig
import com.rndeveloper.paparcar.detection.worker.FirstParkNudgeWorker
import com.rndeveloper.paparcar.detection.worker.GeofenceJanitorWorker
import com.rndeveloper.paparcar.detection.worker.ParkingSafetyNetWorker
import com.rndeveloper.paparcar.detection.worker.RegisterActivityTransitionsWorker
import com.rndeveloper.paparcar.di.androidDetectionModule
import com.rndeveloper.paparcar.di.androidPlatformModule
import com.rndeveloper.paparcar.di.appModule
import com.rndeveloper.paparcar.di.dataModule
import com.rndeveloper.paparcar.di.domainModule
import com.rndeveloper.paparcar.di.presentationModule
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.preferences.ThemeMode
import com.rndeveloper.paparcar.domain.usecase.detection.isFirstParkNudgeSpent
import com.rndeveloper.paparcar.logging.FileAntilog
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext

class PaparcarApp : Application() {
    override fun onCreate() {
        super.onCreate()

        appContext = this
        // BuildConfig belongs to this module; shared code reads these via AppBuildInfo /
        // isDebugBuild. Must run before Koin starts and before any component is created.
        AppBuildInfo.isDebug = BuildConfig.DEBUG
        AppBuildInfo.versionName = BuildConfig.VERSION_NAME
        if (BuildConfig.DEBUG) {
            Napier.base(DebugAntilog())
            Napier.d("GOOGLE_WEB_CLIENT_ID = '${BuildConfig.GOOGLE_WEB_CLIENT_ID}'", tag = "PaparcarApp")
        }
        // The parkdiag sink runs in EVERY build, not just debug: "Report a problem" in Settings
        // ships this file, and a release user with a detection bug is exactly who needs it to
        // exist. App-private storage, PARKDIAG-tagged lines only, bounded by rotation (~30 MB),
        // gone on uninstall. Logcat mirroring (DebugAntilog) stays debug-only.
        // [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
        Napier.base(FileAntilog(this))

        // What Paparcar offers is declared in ONE place, shared with iOS and with the previews +
        // Dev Catalog gallery. Never inline a LoginLibraryConfig here: its flags default to
        // enabled and would put unsupported methods on the screen. [AUTH-PROVIDERS-EXPLICIT-001]
        val loginConfig = paparcarLoginConfig(googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)

        initLoginKoin(config = loginConfig) {
            androidContext(this@PaparcarApp)
            modules(
                presentationModule,
                domainModule,
                dataModule,
                androidDetectionModule,
                androidPlatformModule,
                appModule,
            )
        }

        // Push the in-app ThemeMode preference into AppCompat night mode BEFORE the
        // launcher activity's starting window is drawn, so the native splash
        // (windowSplashScreenBackground → @color/splash_background DayNight) resolves to
        // the user's choice rather than just the system dark setting. appPreferences is
        // synchronously available via the blocking DataStore warmup at Koin construction.
        val appPreferences = get<AppPreferences>()
        AppCompatDelegate.setDefaultNightMode(
            when (appPreferences.themeMode) {
                ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
                ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )

        val workManager = WorkManager.getInstance(this)

        // Re-register Activity Recognition transitions every 12h, but only if
        // the permission is already granted (existing users). New users trigger
        // enqueueKeep() from PermissionsScreen once they grant the permission.
        if (hasActivityRecognitionPermission()) {
            RegisterActivityTransitionsWorker.enqueueKeep(workManager)
        }

        // Restoration pass for the geofences of active sessions. NOT a TTL refresher: fences are
        // registered NEVER_EXPIRE [GEOF-001], so there is nothing to renew — this periodic is the
        // FLOOR under the causes we cannot detect (Play Services dropping fences on its own update,
        // location toggled off and back on). The causes we CAN detect have their own hooks in
        // BootCompletedReceiver. [DET-FENCE-REREGISTER-BY-CAUSE-001 §A]
        GeofenceJanitorWorker.enqueueKeep(workManager)
        // …and ONE unconditional restore pass right now: Play Services ERASES registered geofences
        // (it does not merely silence them) on force-stop — which is what OEM deep-kills amount
        // to — and on app update/reinstall. Only a manual app open revives a force-stopped app, so
        // every process start must assume the fences are gone and rebuild them from Room. Idempotent
        // (FLAG_UPDATE_CURRENT + no initial trigger); waiting up to 12 h for the KEEP periodic would
        // leave an active park blind to its departure for that whole window. [GEOF-RESTORE-001]
        GeofenceJanitorWorker.enqueueOnce(workManager, trigger = GeofenceJanitorWorker.TRIGGER_APP_START)

        // Parked-session safety net: every 15 min while parked, feed the geofencing engine an
        // active fix, cure a poisoned fence state, recover missed departures, and keep the
        // significant-motion trigger armed. [DET-SAFETY-NET-001][DET-SIGMOTION-001]
        ParkingSafetyNetWorker.enqueueKeep(workManager)
        // Immediate pass as well: app-open often happens right at the car (just parked / about to
        // leave) — a fresh fix now seeds the position anchor instead of waiting up to 15 min.
        ParkingSafetyNetWorker.enqueueCheckNow(workManager, source = ParkingSafetyNetWorker.SOURCE_APP_START)

        // Daily cold-start nudge for users who enabled detection but never parked with it — and the
        // removal of that clock once the nudge is permanently spent (park confirmed / cap
        // exhausted), so a job with no future work stops waking daily forever.
        // [DET-TOGGLE-002][DET-SPENT-NUDGE-MUST-STOP-WAKING-001]
        FirstParkNudgeWorker.syncSchedule(
            workManager,
            nudgeSpent = isFirstParkNudgeSpent(
                hasConfirmedFirstPark = appPreferences.hasConfirmedFirstPark,
                nudgeCount = appPreferences.firstParkNudgeCount,
            ),
        )

        // [DET-WATCH-REACTIVATE-001] The resident SENTRY watcher is NOT resurrected here any more.
        // This spot read the parked sessions ONCE (`observeActiveSessions().first()`), so a clean
        // install — Room still empty while the Firestore sync lands — saw "nothing parked" and left
        // the watch dead until the next process launch. It also fired before any Activity was
        // resumed, the least foreground-eligible moment for an FGS start on Android 12+.
        // MainActivity now watches the gap as a STREAM while it is visible; see
        // ObserveDepartureWatchGapUseCase + DepartureWatchResumer.
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
