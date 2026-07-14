package io.apptolast.paparcar.presentation.home.sections.sheet.components

/**
 * Shared visual tokens of the sheet's meta lines — ONE source so the peek
 * variants and the list rows can't drift apart again (they had diverged to
 * 0.7 vs 0.6 value alphas). [HOME-ATOMIZE-001 F3]
 */
internal object SheetTokens {
    /** Separator between data tokens on a meta line ("80 m  ·  1 min"). */
    const val META_SEPARATOR = "  ·  "
    const val META_SEPARATOR_ALPHA = 0.3f
    /** Alpha of meta VALUE text. Unified on the peek's 0.7 (list rows were 0.6). */
    const val META_VALUE_ALPHA = 0.7f
}
