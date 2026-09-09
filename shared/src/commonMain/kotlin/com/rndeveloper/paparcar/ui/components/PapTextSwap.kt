package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A piece of text being replaced by another — seen exactly ONCE. [UI-THE-KM-LINE-CHANGES-ONCE-001]
 *
 * ⛔ This exists because `Crossfade` is the wrong tool for text, and the reason is what it DOES
 * rather than how it looks: a crossfade keeps both children composed and draws them SUPERIMPOSED
 * for the whole fade. It does not chain, it overlaps. Two surfaces that replace each other (a map
 * FAB swapping its glyph, a chart replacing an empty state) are exactly what that is for — overlap
 * reads as a dissolve. Two SENTENCES do not dissolve: they interleave into a smear, and the user
 * reads a value that was never true. Reported on device 09-09-2026 about the Activity card's km
 * line: *«debería animarse con un solo cambio, no cambiar de texto x veces cada vez que cambiamos
 * de filtro»* — and there was only ever one change; the other readings were the overlap.
 *
 * Here the halves are SEQUENCED: the entry is delayed by exactly the exit's duration, so the old
 * text is at alpha 0 before the new one starts. At any instant there is one legible string.
 *
 * `using null` drops the default `SizeTransform`: callers own their layout (the Activity card
 * reserves its height by role — [UI-ACTIVITY-HEADER-RESERVES-ITS-HEIGHT-001]) and a transition must
 * not animate a size somebody else already decided.
 *
 * ⛔ Numbers do NOT come through here. A figure that can be interpolated climbs with
 * [rememberCountUp] — swapping it would throw away the one transition that carries meaning. Words
 * cross-fade, numbers climb; this is the "words" half, fixed to happen once. (User, 08-09-2026.)
 */
@Composable
fun <T> PapTextSwap(
    targetState: T,
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        label = label,
        modifier = modifier,
        transitionSpec = {
            fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS)) togetherWith
                fadeOut(tween(FADE_OUT_MS)) using null
        },
        content = { content(it) },
    )
}

/** Out first, then in — never at the same time, or the swap stops being one change. */
private const val FADE_OUT_MS = 110
private const val FADE_IN_MS = 170
