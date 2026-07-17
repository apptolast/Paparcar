package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable

/**
 * Reports whether the hosting screen is **foreground** (visible + interactive) to [onChanged].
 * Emits `true` when the screen resumes and `false` when it pauses or leaves composition. [UI-LOC-FOREGROUND-001]
 *
 * Used by Home to scope its high-accuracy user-location request to when the map is actually on screen
 * (battery bound): under Compose Navigation the lifecycle owner is the destination's back-stack entry,
 * so this is `true` only while Home is the current destination AND the app is foreground.
 *
 * - **androidMain**: backed by `LifecycleResumeEffect` (resume → true, pause/dispose → false).
 * - **iosMain**: no lifecycle equivalent yet — reports `true` once (matches [collectAsStateLifecycleAware]).
 */
@Composable
expect fun MapForegroundEffect(onChanged: (Boolean) -> Unit)
