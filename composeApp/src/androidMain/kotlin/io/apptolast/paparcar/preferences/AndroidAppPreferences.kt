package io.apptolast.paparcar.preferences

import android.content.Context
import io.apptolast.paparcar.domain.preferences.AppPreferences
import androidx.core.content.edit

private const val PREFS_NAME = "paparcar_prefs"
private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

class AndroidAppPreferences(context: Context) : AppPreferences {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override val isOnboardingCompleted: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false)

    override fun setOnboardingCompleted() {
        prefs.edit { putBoolean(KEY_ONBOARDING_COMPLETED, true) }
    }
}
