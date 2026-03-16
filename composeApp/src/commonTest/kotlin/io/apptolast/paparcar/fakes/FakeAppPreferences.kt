package io.apptolast.paparcar.fakes

import io.apptolast.paparcar.domain.preferences.AppPreferences

class FakeAppPreferences(initialCompleted: Boolean = false) : AppPreferences {

    private var _isOnboardingCompleted = initialCompleted
    override val isOnboardingCompleted: Boolean get() = _isOnboardingCompleted

    var setOnboardingCompletedCount = 0
        private set

    override fun setOnboardingCompleted() {
        _isOnboardingCompleted = true
        setOnboardingCompletedCount++
    }
}
