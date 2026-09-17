package com.rndeveloper.paparcar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.apptolast.baselogin.platform.ActivityHolder
import com.rndeveloper.paparcar.dev.DevRoot
import com.rndeveloper.paparcar.dev.ShotPreset
import com.rndeveloper.paparcar.dev.applyTo
import com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState
import com.rndeveloper.paparcar.fakes.MockScenario
import org.koin.android.ext.android.inject

/**
 * Launcher activity for the **mock** flavor only (declared in `src/mock/AndroidManifest.xml`, sources in `src/androidMock/kotlin`,
 * which also strips MainActivity's launcher filter). Hosts the Dev Catalog, which can mount the
 * real [App] graph under a chosen [MockScenario]. The prod MainActivity is untouched.
 */
class DevMainActivity : ComponentActivity() {

    private val scenario: MockScenario by inject()
    private val detectionRuntime: MutableDetectionRuntimeState by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001] Store-shot session: `--es shot <key>`
        // applies the scenario and mounts the real app straight away, dev chips off.
        val shot = ShotPreset.from(intent?.getStringExtra(EXTRA_SHOT))
        shot?.applyTo(scenario, detectionRuntime)
        // No keep-on-screen condition: the catalog is ready immediately, so the splash dismisses
        // on the first frame. Kept only so the API 31+ starting-window theme resolves correctly.
        installSplashScreen()
        // The baselogin library reads the current Activity from here for its auth UI.
        ActivityHolder.setActivity(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DevRoot(scenario = scenario, shotSession = shot != null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityHolder.clearActivity(this)
    }

    private companion object {
        const val EXTRA_SHOT = "shot"
    }
}
