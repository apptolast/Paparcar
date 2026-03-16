package io.apptolast.paparcar.ios.stub

import io.apptolast.paparcar.domain.preferences.AppPreferences

class StubAppPreferences : AppPreferences {
    override val isOnboardingCompleted: Boolean = true
    override fun setOnboardingCompleted() {}
}
