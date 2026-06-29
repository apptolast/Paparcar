package io.apptolast.paparcar.dev

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
 * brand-new `SplashViewModel` that re-resolves routing from the current [MockScenario]. A floating
 * "DEV" button returns to the catalog. None of this exists in the prod flavor.
 */
@Composable
fun DevRoot(scenario: MockScenario) {
    var inApp by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    // Bumped on each entry so the ViewModelStoreOwner (and thus all app ViewModels) is recreated.
    var session by rememberSaveable { mutableIntStateOf(0) }

    if (!inApp && showGallery) {
        StateGalleryScreen(onBack = { showGallery = false })
    } else if (!inApp) {
        PaparcarTheme(darkTheme = isSystemInDarkTheme()) {
            DevCatalogScreen(
                scenario = scenario,
                onEnter = { session++; inApp = true },
                onOpenGallery = { showGallery = true },
            )
        }
    } else {
        key(session) {
            val owner = remember(session) {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            DisposableEffect(session) {
                onDispose { owner.viewModelStore.clear() }
            }
            CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                Box(Modifier.fillMaxSize()) {
                    App()
                    DevReturnButton(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 0.dp),
                        onClick = { inApp = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun DevReturnButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    ElevatedButton(
        onClick = onClick,
        modifier = modifier,
    ) {
        Text("DEV", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}
