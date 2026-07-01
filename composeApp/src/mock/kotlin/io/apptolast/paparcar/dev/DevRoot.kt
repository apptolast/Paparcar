package io.apptolast.paparcar.dev

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalConfiguration
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
 * Two persistent dev controls, clustered top-end (none of this exists in prod):
 * - A **theme toggle** (☀/🌙) present on *every* screen. It flips a global light/dark override that
 *   is applied by shadowing [LocalConfiguration]'s night mask, so it reaches every surface —
 *   catalog, state gallery, and the real [App] (which themes from `ThemeMode.SYSTEM →
 *   isSystemInDarkTheme()` by default in mock). `null` = follow the device.
 * - A **DEV** button, shown only away from the catalog (live app or gallery), that returns to it.
 */
@Composable
fun DevRoot(scenario: MockScenario) {
    var inApp by rememberSaveable { mutableStateOf(false) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    // Bumped on each entry so the ViewModelStoreOwner (and thus all app ViewModels) is recreated.
    var session by rememberSaveable { mutableIntStateOf(0) }
    // Global mock light/dark override: null = follow system.
    var darkOverride by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val effectiveDark = darkOverride ?: isSystemInDarkTheme()

    // Copy the real config and force only the night bits, so isSystemInDarkTheme() everywhere below
    // (catalog, gallery, and App's ThemeMode.SYSTEM path) reflects the toggle. Everything else is
    // left untouched.
    val baseConfig = LocalConfiguration.current
    val overriddenConfig = remember(baseConfig, effectiveDark) {
        Configuration(baseConfig).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (effectiveDark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
    }

    CompositionLocalProvider(LocalConfiguration provides overriddenConfig) {
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

            // Persistent dev controls, clustered top-end. Theme toggle is always present; the DEV
            // escape hatch only appears outside the catalog.
            PaparcarTheme(darkTheme = effectiveDark) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(8.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (inApp || showGallery) {
                        ElevatedButton(onClick = { inApp = false; showGallery = false }) {
                            Text("DEV", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    ElevatedButton(onClick = { darkOverride = !effectiveDark }) {
                        Text(if (effectiveDark) "☀" else "🌙")
                    }
                }
            }
        }
    }
}
