package com.rndeveloper.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Reports whether the HOSTING composable is **foreground** (visible + interactive) to [onChanged].
 * Emits `true` when it resumes and `false` when it pauses or leaves composition. [UI-LOC-FOREGROUND-001]
 *
 * One mechanism, two scopes — not two mechanisms:
 * - Called from [com.rndeveloper.paparcar.presentation.home.HomeScreen] (inside Compose
 *   Navigation), the lifecycle owner is the destination's back-stack entry: `true` only while Home
 *   is the current destination AND the app is foreground. Used to scope Home's high-accuracy
 *   user-location request to when the map is actually on screen (battery bound).
 * - Called from the app ROOT composable (outside any NavHost), the lifecycle owner is the whole
 *   composition: `true` whenever the app itself is foreground, regardless of which tab is open.
 *   Used to scope the zone-alert Firestore listener to when a human could possibly see its result
 *   — a listener the resident sentry FGS would otherwise keep alive indefinitely.
 *   [BATT-002][ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
 *
 * One implementation for both platforms and both scopes: the multiplatform lifecycle artifact
 * drives `LifecycleResumeEffect` on iOS too (ComposeUIViewController wires the owner to
 * UIApplication state), so backgrounding the app emits the `false` edge on every platform.
 * [IOS-A-PARITY-TAIL-001]
 */
@Composable
fun ForegroundEffect(onChanged: (Boolean) -> Unit) {
    LifecycleResumeEffect(Unit) {
        onChanged(true)
        onPauseOrDispose { onChanged(false) }
    }
}
