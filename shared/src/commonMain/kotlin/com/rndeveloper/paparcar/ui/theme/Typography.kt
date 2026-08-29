package com.rndeveloper.paparcar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp


// ─── Typography ───────────────────────────────────────────────────────────────

/**
 * The MD3 baseline, resolved from the SAME [PapFontSet] as the roles.
 *
 * It is not decoration: every Material component that never receives a Paparcar role reads this
 * scale — the bottom-nav labels, `OutlinedTextField` labels and placeholders, any `Button` whose
 * `Text` carries no style. Leaving it pinned to Outfit/Inter meant those kept rendering the old
 * families after the app had adopted another one, and nothing said so.
 * [UI-TYPE-ONE-VOICE-REACHES-MATERIAL-001]
 */
@Composable
fun rememberAppTypography(fonts: PapFontSet = defaultFontSet()): Typography {
    val brand = fonts.brand
    val text  = fonts.text
    return Typography(
        // ── Display ──────────────────────────────────────────────────────────
        displayLarge = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.Normal,
            fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp,
        ),
        displayMedium = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.Normal,
            fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.Normal,
            fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp,
        ),
        // ── Headline ─────────────────────────────────────────────────────────
        headlineLarge = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.Bold,
            fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp,
        ),
        // ── Title ────────────────────────────────────────────────────────────
        titleLarge = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = brand, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),
        // ── Body ─────────────────────────────────────────────────────────────
        bodyLarge = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
        ),
        // ── Label ────────────────────────────────────────────────────────────
        labelLarge = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = text, fontWeight = FontWeight.Medium,
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

// The condensed "data" styles (charts, badges, stat numbers) now live as roles in
// [PaparcarType] (DATA group); the Barlow family factory above feeds them. [UI-TYPE-SYSTEM-001]
