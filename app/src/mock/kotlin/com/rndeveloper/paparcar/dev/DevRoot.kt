package com.rndeveloper.paparcar.dev

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rndeveloper.paparcar.App
import com.rndeveloper.paparcar.fakes.MockScenario
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

/**
 * Root of the mock launcher. Shows the [DevCatalogScreen] first; entering mounts the **real**
 * [App] inside a fresh [ViewModelStoreOwner] (keyed by a session counter) so every entry gets a
 * brand-new `SplashViewModel` that re-resolves routing from the current [MockScenario]. That store
 * lives in [DevSessionStores], outside the composition, so *fresh per entry* does not also mean
 * *wiped on every rotation* — see there. [MOCK-A-DEV-HOST-MUST-ROTATE-LIKE-PROD-001]
 *
 * Two persistent dev controls, clustered on the left edge at mid-height (none of this exists in
 * prod) — see the note at the cluster for why not top-end:
 * - A **theme toggle** (☀/🌙) present on *every* screen. It flips a global light/dark override that
 *   is applied by shadowing [LocalConfiguration]'s night mask, so it reaches every surface —
 *   catalog, state gallery, and the real [App] (which themes from `ThemeMode.SYSTEM →
 *   isSystemInDarkTheme()` by default in mock). `null` = follow the device.
 * - A **DEV** button, shown only away from the catalog (live app or gallery), that returns to it.
 *
 * [PLAY-SHOTS-EVERY-LANGUAGE-SHOWS-ITS-OWN-UI-001] With [shotSession] the catalog is skipped (the
 * scenario already came in the launch intent) and **both chips are gone** — a Play capture cannot
 * carry dev scaffolding. ⛔ Hiding them with `alpha(0f)` does not work: the emoji glyph skips the
 * alpha layer. They have to not compose.
 */
@Composable
fun DevRoot(scenario: MockScenario, shotSession: Boolean = false) {
    var inApp by rememberSaveable { mutableStateOf(shotSession) }
    var showGallery by rememberSaveable { mutableStateOf(false) }
    // Bumped on each entry so the ViewModelStoreOwner (and thus all app ViewModels) is recreated.
    var session by rememberSaveable { mutableIntStateOf(0) }
    // Survives the Activity's recreation, so a rotation keeps the mounted graph. See the class.
    val sessionStores: DevSessionStores = viewModel()
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
                    // The store comes from [DevSessionStores], which is a ViewModel of the Activity
                    // and therefore OUTLIVES a recreation — the graph survives a rotation the way
                    // it does in prod. Retiring the previous session is what keeps a preset from
                    // inheriting a live graph, and it is now tied to ENTERING a new session rather
                    // than to the composition leaving, which is also what a rotation does.
                    // [MOCK-A-DEV-HOST-MUST-ROTATE-LIKE-PROD-001]
                    val owner = remember(session, sessionStores) {
                        object : ViewModelStoreOwner {
                            override val viewModelStore = sessionStores.storeFor(session)
                        }
                    }
                    LaunchedEffect(session) { sessionStores.retireAllBut(session) }
                    CompositionLocalProvider(LocalViewModelStoreOwner provides owner) {
                        App()
                    }
                }
            }

            // Persistent dev controls. Theme toggle is always present; the DEV escape hatch only
            // appears outside the catalog.
            //
            // They live on the LEFT EDGE, vertically centred, because top-end is where the APP puts
            // its own controls: Home's map-type toggle and the history detail map's sit exactly
            // there, and this cluster was covering both — the dev scaffolding was hiding the thing
            // a dev build exists to look at. The left edge at mid-height is the one region no screen
            // uses (map on Home and on the detail, margin on the lists).
            // [MOCK-THE-DEV-CHIPS-MUST-NOT-COVER-THE-APPS-CONTROLS-001]
            if (!shotSession) PaparcarTheme(darkTheme = effectiveDark) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (inApp || showGallery) {
                        // Leaving for the catalog is what stops the graph now — an explicit act,
                        // not a side effect of the composition going away (which a rotation also
                        // does). [MOCK-A-DEV-HOST-MUST-ROTATE-LIKE-PROD-001]
                        ElevatedButton(onClick = {
                            inApp = false; showGallery = false; sessionStores.retireAll()
                        }) {
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

/**
 * Holds the mounted App's [ViewModelStore] for each catalog session, **outside the composition**.
 * [MOCK-A-DEV-HOST-MUST-ROTATE-LIKE-PROD-001]
 *
 * ⛔ A store kept by `remember` cannot survive an Activity recreation, and a [ViewModelStore] is not
 * serialisable, so `rememberSaveable` cannot hold one either. Rotating therefore handed the app a
 * brand-new empty store and rebuilt every ViewModel — making the bench HARSHER than production,
 * where `MainActivity` keeps its own store across configuration changes. Measured on 22-11: rotating
 * mid-chain lost the detection invitation of the first-entry flow, a loss no phone would show.
 * A bench that is harsher than the product reports failures the user will never meet, which is the
 * same defect as one that hides real ones.
 *
 * Being a ViewModel **of the Activity** is what buys the survival. The session key is what keeps the
 * other half of the contract that [DevRoot] always had: entering from the catalog gets a brand-new
 * graph, with no state inherited from the preset before it.
 */
internal class DevSessionStores : ViewModel() {
    private val stores = mutableMapOf<Int, ViewModelStore>()

    fun storeFor(session: Int): ViewModelStore = stores.getOrPut(session) { ViewModelStore() }

    /** Entering a session retires every older one, so a preset never inherits a live graph. */
    fun retireAllBut(session: Int) {
        stores.keys.filterNot { it == session }.toList().forEach { stores.remove(it)?.clear() }
    }

    /** Leaving the app for the catalog stops the graph, exactly as closing it always did. */
    fun retireAll() {
        stores.values.forEach { it.clear() }
        stores.clear()
    }

    override fun onCleared() = retireAll()
}
