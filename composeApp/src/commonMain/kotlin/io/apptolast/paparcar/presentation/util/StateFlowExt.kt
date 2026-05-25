package io.apptolast.paparcar.presentation.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import kotlinx.coroutines.flow.StateFlow

/**
 * Lifecycle-aware collector for [StateFlow] in Compose Multiplatform.
 *
 * - **androidMain**: delegates to `collectAsStateWithLifecycle()` so the flow
 *   is paused when the owning composable's lifecycle drops below STARTED (e.g. app in background).
 * - **iosMain**: delegates to `collectAsState()` — iOS has no equivalent lifecycle concept.
 *
 * Drop-in replacement for `.collectAsState()` in commonMain screens. [§10]
 */
@Composable
expect fun <T> StateFlow<T>.collectAsStateLifecycleAware(): State<T>
