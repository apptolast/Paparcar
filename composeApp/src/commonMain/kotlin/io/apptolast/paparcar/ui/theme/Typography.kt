package io.apptolast.paparcar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
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

/**
 * Inter variable — body / label (neutral, highly readable).
 *
 * Inter ships as a SINGLE variable file, so each weight must pin the `wght`
 * axis explicitly via [FontVariation]. Without it Compose resolves every entry
 * to the file's default instance (≈Regular) and applies synthetic bold
 * inconsistently — which made identical `PapSectionHeader` labels render at
 * different weights (e.g. "TUS VEHÍCULOS" vs "5 PLAZAS LIBRES CERCA DE TI").
 */
@Composable
fun rememberInterFontFamily() = FontFamily(
    interFont(FontWeight.Normal,    INTER_WGHT_NORMAL),
    interFont(FontWeight.Medium,    INTER_WGHT_MEDIUM),
    interFont(FontWeight.SemiBold,  INTER_WGHT_SEMIBOLD),
    interFont(FontWeight.Bold,      INTER_WGHT_BOLD),
    interFont(FontWeight.ExtraBold, INTER_WGHT_EXTRABOLD),
)

@Composable
private fun interFont(weight: FontWeight, axis: Int) = Font(
    Res.font.inter_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

private const val INTER_WGHT_NORMAL = 400
private const val INTER_WGHT_MEDIUM = 500
private const val INTER_WGHT_SEMIBOLD = 600
private const val INTER_WGHT_BOLD = 700
private const val INTER_WGHT_EXTRABOLD = 800

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
    // Small-caps status pin / count badge ("ACTIVO", "3 LIBRES") — condensed so these tight metadata
    // labels don't eat horizontal space next to names/titles. [HOME-VEH-REFINE-001]
    val statusPin: TextStyle,
    // Compact body for data-dense secondary lines (e.g. the parked-chip address) — condensed so a
    // long address fits in fewer lines. [HOME-VEH-REFINE-001]
    val compactBody: TextStyle,
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
            // 25sp per the design spec (.st-val) — consumers use the token as-is, no size overrides.
            fontFamily = barlow, fontWeight = FontWeight.Bold,
            fontSize = 25.sp, letterSpacing = (-0.5).sp,
        ),
        sizeBadge = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, letterSpacing = 0.5.sp,
        ),
        statusPin = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, letterSpacing = 0.6.sp,
        ),
        compactBody = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, lineHeight = 15.sp, letterSpacing = 0.sp,
        ),
    )
}
