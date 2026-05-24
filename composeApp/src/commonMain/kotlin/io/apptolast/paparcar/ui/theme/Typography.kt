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
import paparcar.composeapp.generated.resources.inter_variable
import paparcar.composeapp.generated.resources.barlow_condensed_regular
import paparcar.composeapp.generated.resources.barlow_condensed_medium
import paparcar.composeapp.generated.resources.barlow_condensed_semibold
import paparcar.composeapp.generated.resources.barlow_condensed_bold

// ─── Font families ────────────────────────────────────────────────────────────

/** Outfit — display / headline / title. */
@Composable
fun rememberOutfitFontFamily() = FontFamily(
    Font(Res.font.outfit_regular,   weight = FontWeight.Normal),
    Font(Res.font.outfit_medium,    weight = FontWeight.Medium),
    Font(Res.font.outfit_semibold,  weight = FontWeight.SemiBold),
    Font(Res.font.outfit_bold,      weight = FontWeight.Bold),
    Font(Res.font.outfit_extrabold, weight = FontWeight.ExtraBold),
)

/** Inter variable — body / label (neutral, highly readable). */
@Composable
fun rememberInterFontFamily() = FontFamily(
    Font(Res.font.inter_variable, weight = FontWeight.Normal),
    Font(Res.font.inter_variable, weight = FontWeight.Medium),
    Font(Res.font.inter_variable, weight = FontWeight.SemiBold),
    Font(Res.font.inter_variable, weight = FontWeight.Bold),
    Font(Res.font.inter_variable, weight = FontWeight.ExtraBold),
)

/** Barlow Condensed — compact data slots (charts, badges, stats). */
@Composable
fun rememberBarlowCondensedFontFamily() = FontFamily(
    Font(Res.font.barlow_condensed_regular,  weight = FontWeight.Normal),
    Font(Res.font.barlow_condensed_medium,   weight = FontWeight.Medium),
    Font(Res.font.barlow_condensed_semibold, weight = FontWeight.SemiBold),
    Font(Res.font.barlow_condensed_bold,     weight = FontWeight.Bold),
)

// ─── Typography ───────────────────────────────────────────────────────────────

/** Full MD3 scale: Outfit (Display–Title) + Inter (Body–Label). */
@Composable
fun rememberAppTypography(): Typography {
    val outfit = rememberOutfitFontFamily()
    val inter  = rememberInterFontFamily()
    return Typography(
        // ── Display ──────────────────────────────────────────────────────────
        displayLarge = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.Normal,
            fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.Normal,
            fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.Normal,
            fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp,
        ),
        // ── Headline ─────────────────────────────────────────────────────────
        headlineLarge = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp,
        ),
        // ── Title ────────────────────────────────────────────────────────────
        titleLarge = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),
        // ── Body ─────────────────────────────────────────────────────────────
        bodyLarge = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
        ),
        // ── Label ────────────────────────────────────────────────────────────
        labelLarge = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Medium,
            fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        ),
    )
}

// ─── Extension slots ─────────────────────────────────────────────────────────

/** Unified top-bar title style — Outfit ExtraBold, tightened tracking. */
val Typography.appBarTitle: TextStyle
    get() = headlineSmall.copy(
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
    )

// ─── Data typography ─────────────────────────────────────────────────────────

/** Condensed styles for data-dense slots: charts, badges, stat numbers. */
class DataTypography(
    val chartDayLabel: TextStyle,
    val chartCountBadge: TextStyle,
    val distanceBadge: TextStyle,
    val statNumber: TextStyle,
    val sizeBadge: TextStyle,
)

@Composable
fun rememberDataTypography(): DataTypography {
    val barlow = rememberBarlowCondensedFontFamily()
    return DataTypography(
        chartDayLabel = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.Normal,
            fontSize = 11.sp, letterSpacing = 0.sp,
        ),
        chartCountBadge = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, letterSpacing = 0.sp,
        ),
        distanceBadge = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, letterSpacing = 0.sp,
        ),
        statNumber = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, letterSpacing = (-0.5).sp,
        ),
        sizeBadge = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp, letterSpacing = 0.5.sp,
        ),
    )
}
