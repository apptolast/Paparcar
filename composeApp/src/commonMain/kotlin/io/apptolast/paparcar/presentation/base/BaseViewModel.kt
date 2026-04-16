package io.apptolast.paparcar.presentation.base

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Abstract MVI base ViewModel for all screens.
 *
 * Provides a unidirectional data flow via:
 * - [state] — a cold [StateFlow] representing the full, immutable UI state.
 * - [effect] — a hot [SharedFlow] for one-shot side effects (navigation, dialogs).
 * - [handleIntent] — the single entry point for user actions.
 *
 * @param S State type — an immutable data class holding all UI data.
 * @param I Intent type — a sealed class of user-driven actions.
 * @param E Effect type — a sealed class of one-shot platform side-effects.
 */
abstract class BaseViewModel<S, I, E> : ViewModel() {
    private val viewModelJob = SupervisorJob()

    /** Coroutine scope backed by [Dispatchers.Main.immediate] and a [SupervisorJob] for structured concurrency. */
    protected val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main.immediate + viewModelJob)

    private val _state: MutableStateFlow<S> by lazy { MutableStateFlow(initState()) }

    /** Observed by the UI to render the current screen state. */
    val state = _state.asStateFlow()

    private val _effect: MutableSharedFlow<E> = MutableSharedFlow()

    /** Observed by the UI for one-shot side effects that should not be replayed on recomposition. */
    val effect = _effect.asSharedFlow()

    /** Returns the initial state for this ViewModel. Called lazily on first [state] access. */
    abstract fun initState(): S

    /** Processes a user [intent] and updates [state] or emits an [effect] as a result. */
    abstract fun handleIntent(intent: I)

    /**
     * Atomically applies [handler] to the current state and publishes the result.
     * Safe to call from any thread.
     */
    protected fun updateState(handler: S.() -> S) {
        _state.update(handler)
    }

    /**
     * Emits a one-shot [effect] to the UI.
     * Launches a coroutine on [viewModelScope]; suspends only if there are no active collectors.
     */
    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effect.emit(effect) }
    }

    /**
     * Cancels [viewModelScope] and cleans up resources.
     * Call this when the ViewModel is permanently destroyed (KMP lifecycle hook).
     */
    fun onClear() {
        viewModelJob.cancel()
    }

    /** Android lifecycle hook — delegates to [onClear] so the scope is always cancelled. */
    override fun onCleared() {
        super.onCleared()
        onClear()
    }
}
