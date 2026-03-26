package io.apptolast.paparcar

import android.app.Application
import androidx.work.WorkManager
import com.apptolast.customlogin.appContext
import com.apptolast.customlogin.config.AppleSignInConfig
import com.apptolast.customlogin.config.GoogleSignInConfig
import com.apptolast.customlogin.config.MagicLinkConfig
import com.apptolast.customlogin.di.LoginLibraryConfig
import com.apptolast.customlogin.di.initLoginKoin
import io.apptolast.paparcar.detection.worker.RegisterActivityTransitionsWorker
import io.apptolast.paparcar.di.androidDetectionModule
import io.apptolast.paparcar.di.androidPlatformModule
import io.apptolast.paparcar.di.dataModule
import io.apptolast.paparcar.di.domainModule
import io.apptolast.paparcar.di.presentationModule
import org.koin.android.ext.koin.androidContext

class PaparcarApp : Application() {
    override fun onCreate() {
        super.onCreate()

        appContext = this

        val loginConfig = LoginLibraryConfig(
            googleSignInConfig = GoogleSignInConfig(
                webClientId = "431876996213-3je39vbah4nctmod6nvckabu4vjmu0hh.apps.googleusercontent.com"
            ),
            appleSignInConfig = AppleSignInConfig(),
            githubEnabled = true,
            microsoftEnabled = true,
            twitterEnabled = true,
            facebookEnabled = true,
            magicLinkConfig = MagicLinkConfig(
                continueUrl = "https://apptolast.com/login"
            )
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

//        startKoin {
//            androidContext(this@PaparcarApp)
//            modules(
//                presentationModule,
//                domainModule,
//                dataModule,
//                androidDetectionModule,
//                androidPlatformModule,
//            )
//        }

        // Re-register Activity Recognition transitions every 12h.
        // KEEP policy: if already enqueued, don't restart the running job.
        RegisterActivityTransitionsWorker.enqueueKeep(WorkManager.getInstance(this))
    }
}
