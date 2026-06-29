package io.apptolast.paparcar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.apptolast.customlogin.platform.ActivityHolder
import io.apptolast.paparcar.dev.DevRoot
import io.apptolast.paparcar.fakes.MockScenario
import org.koin.android.ext.android.inject

/**
 * Launcher activity for the **mock** flavor only (declared in `src/mock/AndroidManifest.xml`,
 * which also strips MainActivity's launcher filter). Hosts the Dev Catalog, which can mount the
 * real [App] graph under a chosen [MockScenario]. The prod MainActivity is untouched.
 */
class DevMainActivity : ComponentActivity() {

    private val scenario: MockScenario by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        // No keep-on-screen condition: the catalog is ready immediately, so the splash dismisses
        // on the first frame. Kept only so the API 31+ starting-window theme resolves correctly.
        installSplashScreen()
        // The customlogin library reads the current Activity from here for its auth UI.
        ActivityHolder.setActivity(this)
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DevRoot(scenario = scenario)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ActivityHolder.clearActivity(this)
    }
}
