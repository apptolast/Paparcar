package io.apptolast.paparcar.dev

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import io.apptolast.paparcar.App
import io.apptolast.paparcar.fakes.MockScenario
import io.apptolast.paparcar.ui.theme.PaparcarTheme

/**
 * Root of the mock launcher. Shows the [DevCatalogScreen] first; entering mounts the **real**
 * [App] inside a fresh [ViewModelStoreOwner] (keyed by a session counter) so every entry gets a
 * brand-new `SplashViewModel` that re-resolves routing from the current [MockScenario].
 *
 * A persistent "DEV" button (top-end) is shown whenever we're away from the catalog — both in the
 * live app and in the state gallery — and always returns to the catalog. None of this exists in prod.
 */
@Composable
fun DevRoot(scenario: MockScenario) {
    var inApp by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    // Bumped on each entry so the ViewModelStoreOwner (and thus all app ViewModels) is recreated.
    var session by rememberSaveable { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize()) {
        when {
            showGallery && !inApp -> StateGalleryScreen(onBack = { showGallery = false })

            !inApp -> PaparcarTheme(darkTheme = isSystemInDarkTheme()) {
                DevCatalogScreen(
                    scenario = scenario,
                    onEnter = { session++; inApp = true },
                    onOpenGallery = { showGallery = true },
                )
            }

            else -> key(session) {
                val owner = remember(session) {
                    object : ViewModelStoreOwner {
                        override val viewModelStore = ViewModelStore()
                    }
                }
                DisposableEffect(session) { onDispose { owner.viewModelStore.clear() } }
                CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                    App()
                }
            }
        }

        // Persistent escape hatch back to the Dev Catalog — always present outside the catalog.
        if (inApp || showGallery) {
            PaparcarTheme(darkTheme = isSystemInDarkTheme()) {
                ElevatedButton(
                    onClick = { inApp = false; showGallery = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp),
                ) {
                    Text("DEV", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
