package io.apptolast.paparcar.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Paparcar motion system — the single source of truth for how things move.
 *
 * Tone: **sober & professional**. Most transitions are `tween` with
 * [Standard] easing in the [Medium] band; springs are damped (no playful
 * bounce) and reserved for elements that should feel "alive" (the monitoring
 * pill, a value landing into place). Keeping every screen on these tokens is
 * what makes the app read as one product instead of a patchwork of defaults.
 *
 * Rule of thumb:
 *  - Element appears/disappears, container changes content → [Medium] (240ms).
 *  - Floating-over-map / glass controls → asymmetric [Glass] band.
 *  - Tiny state flips (border, rotation, alpha of a control) → [Fast] (160ms).
 *  - Sheet/nav snaps that move a lot of pixels → [Emphasized] (300ms).
 *  - Looping "breathing" (skeleton, pulse) → [breathe] / explicit infinite spec.
 */
object PapMotion {

    // ── Duration bands (ms) ──────────────────────────────────────────────
    /** Quick state flips: border width/colour, chevron rotation, control alpha. */
    const val Fast = 160

    /** Default for appear/disappear and content swaps. The everyday duration. */
    const val Medium = 240

    /** Sheet snaps, tab transitions, anything moving a large distance. */
    const val Emphasized = 300

    /** Glass / floating-over-map controls: appear fast, linger out (covers drag). */
    const val GlassIn = 160
    const val GlassOut = 320

    /** Loading "breath" for skeleton shimmers — one canonical cadence. */
    const val Breathe = 600

    /** Looping pulse (live dot, active-session ring) — energetic but calm. */
    const val PulseExpand = 700
    const val PulseCollapse = 400

    /** Stagger step between siblings entering in sequence (stats, list rows). */
    const val StaggerStep = 45

    // ── Easings ──────────────────────────────────────────────────────────
    /** Standard decelerate-on-arrival curve. Use for almost everything. */
    val Standard: Easing = FastOutSlowInEasing

    /** Symmetric ease in/out — for reversible/looping motion (pulses, toggles). */
    val EaseInOut: Easing = CubicBezierEasing(0.4f, 0.0f, 0.2f, 1.0f)

    /** Constant speed — only for continuous loops where decel would read as a stop. */
    val Linear: Easing = LinearEasing

    // ── Reusable specs ───────────────────────────────────────────────────
    fun <T> fast(): FiniteAnimationSpec<T> = tween(Fast, easing = Standard)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(Medium, easing = Standard)
    fun <T> emphasized(): FiniteAnimationSpec<T> = tween(Emphasized, easing = Standard)

    /**
     * Damped spring for elements that should "settle" rather than snap — a stat
     * value landing, a pill arriving. No visible overshoot (DampingRatioNoBouncy)
     * to stay on the sober side; medium stiffness keeps it responsive.
     */
    fun <T> settle(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
