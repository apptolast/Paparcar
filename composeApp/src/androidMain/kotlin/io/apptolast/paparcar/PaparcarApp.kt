package io.apptolast.paparcar

import android.app.Application
import androidx.work.WorkManager
import io.apptolast.paparcar.detection.workers.RegisterActivityTransitionsWorker
import io.apptolast.paparcar.di.androidDetectionModule
import io.apptolast.paparcar.di.androidPlatformModule
import io.apptolast.paparcar.di.dataModule
import io.apptolast.paparcar.di.domainModule
import io.apptolast.paparcar.di.presentationModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class PaparcarApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidContext(this@PaparcarApp)
            modules(
                presentationModule,
                domainModule,
                dataModule,
                androidDetectionModule,
                androidPlatformModule,
            )
        }

        // Re-register Activity Recognition transitions every 12h.
        // KEEP policy: if already enqueued, don't restart the running job.
        RegisterActivityTransitionsWorker.enqueueKeep(WorkManager.getInstance(this))
    }
}
