package io.apptolast.paparcar.ui.illustrations

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.illus_automation
import paparcar.composeapp.generated.resources.illus_automation_dark
import paparcar.composeapp.generated.resources.illus_onb_how
import paparcar.composeapp.generated.resources.illus_onb_how_dark
import paparcar.composeapp.generated.resources.illus_onb_privacy
import paparcar.composeapp.generated.resources.illus_onb_privacy_dark
import paparcar.composeapp.generated.resources.illus_onb_welcome
import paparcar.composeapp.generated.resources.illus_onb_welcome_dark

/**
 * Nivel 3 — hero ilustrados del flujo de onboarding/permisos, exportados desde el design system
 * como VectorDrawable (solo `path`, sin dashes/filtros/texto → cumplen la regla de CLAUDE.md para
 * VectorDrawable). Traen su propio color de marca: NO se tintan.
 *
 * Theme-aware por luminancia de `surface` (no `isSystemInDarkTheme()`, que devuelve la variante
 * clara cuando el tema forzado no coincide con el del sistema), espejando [VehicleTopdownIcon].
 */
enum class OnboardingHero { WELCOME, HOW, PRIVACY, AUTOMATION }

private const val HERO_DARK_LUMINANCE = 0.5f

private fun heroDrawable(hero: OnboardingHero, dark: Boolean): DrawableResource = when (hero) {
    OnboardingHero.WELCOME -> if (dark) Res.drawable.illus_onb_welcome_dark else Res.drawable.illus_onb_welcome
    OnboardingHero.HOW -> if (dark) Res.drawable.illus_onb_how_dark else Res.drawable.illus_onb_how
    OnboardingHero.PRIVACY -> if (dark) Res.drawable.illus_onb_privacy_dark else Res.drawable.illus_onb_privacy
    OnboardingHero.AUTOMATION -> if (dark) Res.drawable.illus_automation_dark else Res.drawable.illus_automation
}

@Composable
fun OnboardingHero(
    hero: OnboardingHero,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < HERO_DARK_LUMINANCE
    Image(
        painter = painterResource(heroDrawable(hero, dark)),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
