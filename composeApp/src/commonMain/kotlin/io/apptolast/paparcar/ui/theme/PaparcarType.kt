package io.apptolast.paparcar.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * Paparcar type system — the SINGLE source of truth for text styling in the feature layer.
 *
 * ## Why this exists
 * We have three families (Outfit / Inter / Barlow Condensed). The mistake that made typography
 * drift was letting each `Text` pick its family/size ad-hoc ("is this data or prose? Outfit or
 * Barlow?"). That question is subjective, so it drifted every time.
 *
 * The rule this file enforces: **the family and size are a property of the text's ROLE, decided
 * once, here.** A screen never chooses a font — it chooses a role. The question stops being
 * subjective ("which font?") and becomes objective ("which role?").
 *
 * ## The contract
 * - Feature code (the `presentation` and `ui.components` packages) styles text ONLY with a
 *   `PaparcarType` role: `Text(..., style = type.metadata)`.
 * - It does NOT use `MaterialTheme.typography.*` and NEVER sets `fontSize` / `letterSpacing` inline.
 *   If a size is missing, add/adjust a role here — do not override at the call site.
 * - `.uppercase()` stays a caller concern (Compose `TextStyle` has no text-transform). Roles that
 *   are conventionally caps (`sectionHeader`, `badge`, `sizeToken`) are uppercased by their callers
 *   / by `PapSectionHeader`.
 * - Allowed exceptions (documented, non-drifting): canvas / `TextMeasurer` map-marker labels, and
 *   already-tokenised chrome one-offs (bottom-nav, connectivity banner). These do not go through
 *   `PaparcarType`.
 *
 * ## Migration note
 * Every role below maps to the EXACT `TextStyle` already in use today, so adopting a role is a pure
 * rename with zero visual change. The former condensed data-typography holder is superseded by the
 * DATA roles here (identical values). `rememberAppTypography()` (MD3) stays as the Material baseline
 * for the framework; the app talks to `PaparcarType`, not to it.
 */
@Immutable
class PaparcarType(
    // ── IDENTITY · Outfit (rounded display face — screen/card titles, names) ────────────────────
    /** Top-bar / screen title. "Mis coches". (== the old `appBarTitle`.) */
    val screenTitle: TextStyle,
    /** Hero title on full-screen surfaces (onboarding, permissions, explainers). Resolves the old
     *  Black-vs-Bold / headlineMedium-vs-Small drift to one value. */
    val heroTitle: TextStyle,
    /** In-content section title — "Activity", "History". Bigger than [cardTitle] so a single-word
     *  section heading doesn't read as small. */
    val sectionTitle: TextStyle,
    /** Card / row title — vehicle name, spot street, peek title. (titleMedium weight-bumped to Bold.) */
    val cardTitle: TextStyle,
    /** Small title inside a row / list item, lighter than [cardTitle]. (== titleSmall.) */
    val rowTitle: TextStyle,

    // ── STRUCTURE · Inter (neutral face — navigation of the layout) ─────────────────────────────
    /** Section header eyebrow — "TUS VEHÍCULOS", "ACTIVIDAD". Uppercased by `PapSectionHeader`. */
    val sectionHeader: TextStyle,
    /** Primary CTA / button label. (labelLarge weight-bumped to Bold — the `PapFooterButton` recipe.) */
    val cta: TextStyle,
    /** Small standalone label / chip text (not a data token). (== labelMedium.) */
    val label: TextStyle,

    // ── PROSE · Inter (things you read as sentences) ────────────────────────────────────────────
    /** Prominent body — hero/onboarding subtitles, lead paragraphs. (== bodyLarge.) */
    val subtitle: TextStyle,
    /** Body copy — descriptions, helper paragraphs. (bodyMedium.) */
    val body: TextStyle,
    /** Secondary / caption text — subtitles, hints. (bodySmall.) */
    val caption: TextStyle,

    // ── DATA · Barlow Condensed (tokens that repeat in rows or fight a name for horizontal space) ─
    /** Dense metadata line of PURE data tokens — "30 min · 75 m", "179 m · 1 min · 2 en route".
     *  Text that leads with a place/address name reads as prose → use `caption` (Inter), NOT this.
     *  (== compactBody.) [CARD-ONE-BADGE-001] */
    val metadata: TextStyle,
    /** Status pin / count badge — "ACTIVO", "BLUETOOTH", "3 LIBRES", "FIABLE". Uppercased by caller. (== statusPin.) */
    val badge: TextStyle,
    /** Vehicle size token — "MEDIANO". Uppercased by caller. (== sizeBadge.) */
    val sizeToken: TextStyle,
    /** Prominent stat readout — "43", "92%". (== statNumber, fixed at 25sp — no per-call overrides.) */
    val statNumber: TextStyle,
    /** Distance / elapsed badge on the map pill — "12 min". (== distanceBadge.) */
    val distance: TextStyle,
    /** Chart axis label — month / day names under the bars. (== chartDayLabel.) */
    val chartLabel: TextStyle,
    /** Chart per-bar value — the small count above a bar. (== chartCountBadge.) */
    val chartValue: TextStyle,
) {
    companion object {
        /** The role table for the current composition. Read as `PaparcarType.current.metadata`.
         *  Provided by [PaparcarTheme]; reading it outside the theme is a programming error. */
        val current: PaparcarType
            @Composable @ReadOnlyComposable
            get() = LocalPaparcarType.current
    }
}

/** Composition-local carrying the active [PaparcarType]. Provided at the [PaparcarTheme] root. */
val LocalPaparcarType = staticCompositionLocalOf<PaparcarType> {
    error("PaparcarType not provided — is the content wrapped in PaparcarTheme?")
}

/**
 * Builds the role table for the current composition (fonts are resolved via the `@Composable`
 * family factories). [PaparcarTheme] provides the result through [LocalPaparcarType] — feature
 * code reads `PaparcarType.current.<role>` and never calls this directly.
 */
@Composable
fun rememberPaparcarType(): PaparcarType {
    val outfit = rememberOutfitFontFamily()
    val inter = rememberInterFontFamily()
    val barlow = rememberBarlowCondensedFontFamily()

    return PaparcarType(
        // ── IDENTITY · Outfit ───────────────────────────────────────────────────────────────────
        screenTitle = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp,
        ),
        heroTitle = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.Bold,
            fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp,
        ),
        sectionTitle = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.Bold,
            fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.2).sp,
        ),
        cardTitle = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.Bold,
            fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp,
        ),
        rowTitle = TextStyle(
            fontFamily = outfit, fontWeight = FontWeight.Medium,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),

        // ── STRUCTURE · Inter ───────────────────────────────────────────────────────────────────
        sectionHeader = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.0.sp,
        ),
        cta = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp,
        ),
        label = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Medium,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp,
        ),

        // ── PROSE · Inter ───────────────────────────────────────────────────────────────────────
        subtitle = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Normal,
            fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp,
        ),
        body = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Normal,
            fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp,
        ),
        caption = TextStyle(
            fontFamily = inter, fontWeight = FontWeight.Normal,
            fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp,
        ),

        // ── DATA · Barlow Condensed ─────────────────────────────────────────────────────────────
        metadata = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, lineHeight = 15.sp, letterSpacing = 0.sp,
        ),
        badge = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, letterSpacing = 0.6.sp,
        ),
        sizeToken = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, letterSpacing = 0.5.sp,
        ),
        statNumber = TextStyle(
            // Tight lineHeight + centred/trimmed line box so the digits' box hugs the glyphs and is
            // symmetric — a leading icon set to CenterVertically then lands on the numeral's optical
            // centre instead of floating high (the extra top leading was pushing it up). [CARD-ONE-BADGE-001]
            fontFamily = barlow, fontWeight = FontWeight.Bold,
            fontSize = 25.sp, lineHeight = 25.sp, letterSpacing = (-0.5).sp,
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        distance = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, letterSpacing = 0.sp,
        ),
        chartLabel = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.Normal,
            fontSize = 11.sp, letterSpacing = 0.sp,
        ),
        chartValue = TextStyle(
            fontFamily = barlow, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, letterSpacing = 0.sp,
        ),
    )
}
