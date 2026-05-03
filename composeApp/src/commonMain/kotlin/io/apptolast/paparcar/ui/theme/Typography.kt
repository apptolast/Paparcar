package io.apptolast.paparcar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.Font
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.outfit_regular
import paparcar.composeapp.generated.resources.outfit_medium
import paparcar.composeapp.generated.resources.outfit_semibold
import paparcar.composeapp.generated.resources.outfit_bold
import paparcar.composeapp.generated.resources.outfit_extrabold
import paparcar.composeapp.generated.resources.plus_jakarta_sans_regular
import paparcar.composeapp.generated.resources.plus_jakarta_sans_medium
import paparcar.composeapp.generated.resources.plus_jakarta_sans_bold

// ─── Font families ────────────────────────────────────────────────────────────

/** Outfit — display / headline / title (geometric modern brand feel). */
@Composable
fun rememberOutfitFontFamily() = FontFamily(
    Font(Res.font.outfit_regular,   weight = FontWeight.Normal),
    Font(Res.font.outfit_medium,    weight = FontWeight.Medium),
    Font(Res.font.outfit_semibold,  weight = FontWeight.SemiBold),
    Font(Res.font.outfit_bold,      weight = FontWeight.Bold),
    Font(Res.font.outfit_extrabold, weight = FontWeight.ExtraBold),
)

/** Plus Jakarta Sans — body / label (readable with personality). */
@Composable
fun rememberPlusJakartaSansFontFamily() = FontFamily(
    Font(Res.font.plus_jakarta_sans_regular, weight = FontWeight.Normal),
    Font(Res.font.plus_jakarta_sans_medium,  weight = FontWeight.Medium),
    Font(Res.font.plus_jakarta_sans_bold,    weight = FontWeight.Bold),
)

// ─── Typography ───────────────────────────────────────────────────────────────

/** Full MD3 scale: Outfit (Display–Title) + Plus Jakarta Sans (Body–Label). */
@Composable
fun rememberAppTypography(): Typography {
    val outfit = rememberOutfitFontFamily()
    val jakarta = rememberPlusJakartaSansFontFamily()
    return Typography(
        // ── Display ──────────────────────────────────────────────────────────
        displayLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            letterSpacing = 0.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp,
        ),
        // ── Headline ─────────────────────────────────────────────────────────
        headlineLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp,
        ),
        // ── Title ────────────────────────────────────────────────────────────
        titleLarge = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        // ── Body ─────────────────────────────────────────────────────────────
        bodyLarge = TextStyle(
            fontFamily = jakarta,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = jakarta,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = jakarta,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp,
        ),
        // ── Label ────────────────────────────────────────────────────────────
        labelLarge = TextStyle(
            fontFamily = jakarta,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = jakarta,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = jakarta,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp,
        ),
    )
}
