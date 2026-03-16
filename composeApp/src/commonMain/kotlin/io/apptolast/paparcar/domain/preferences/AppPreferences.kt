package io.apptolast.paparcar.domain.preferences

interface AppPreferences {
    val isOnboardingCompleted: Boolean
    fun setOnboardingCompleted()
}
