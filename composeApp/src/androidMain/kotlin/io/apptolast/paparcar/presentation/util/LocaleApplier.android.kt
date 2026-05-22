package io.apptolast.paparcar.presentation.util

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.apptolast.customlogin.platform.ActivityHolder

actual fun applyAppLocale(tag: String) {
    val locales = if (tag == "auto") {
        LocaleListCompat.getEmptyLocaleList()
    } else {
        LocaleListCompat.forLanguageTags(tag)
    }
    val current = AppCompatDelegate.getApplicationLocales()
    if (current == locales) return
    AppCompatDelegate.setApplicationLocales(locales)
    // MainActivity extends ComponentActivity (not AppCompatActivity), so AppCompatDelegate
    // doesn't auto-recreate it. Force recreate so Compose Resources re-resolves strings
    // against the new configuration.
    ActivityHolder.getCurrentActivity()?.recreate()
}
