package com.rndeveloper.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Reports whether the hosting screen is **foreground** (visible + interactive) to [onChanged].
 * Emits `true` when the screen resumes and `false` when it pauses or leaves composition. [UI-LOC-FOREGROUND-001]
 *
 * Used by Home to scope its high-accuracy user-location request to when the map is actually on screen
 * (battery bound): under Compose Navigation the lifecycle owner is the destination's back-stack entry,
 * so this is `true` only while Home is the current destination AND the app is foreground.
 *
 * One implementation for both platforms: the multiplatform lifecycle artifact drives
 * `LifecycleResumeEffect` on iOS too (ComposeUIViewController wires the owner to UIApplication
 * state), so backgrounding the app finally emits the `false` edge that the old iOS actual — a
 * one-shot `true` — never produced, and Home's location request is released. [IOS-A-PARITY-TAIL-001]
 */
@Composable
fun MapForegroundEffect(onChanged: (Boolean) -> Unit) {
    LifecycleResumeEffect(Unit) {
        onChanged(true)
        onPauseOrDispose { onChanged(false) }
    }
}
