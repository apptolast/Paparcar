package io.apptolast.paparcar.domain.model

/**
 * Preset icon catalogue for [Zone.iconKey]. The string key is what gets
 * persisted; UI resolves it to an ImageVector at render time. Adding a
 * new preset is forward-compatible — old clients that don't recognise
 * the key fall back to [DEFAULT].
 *
 * Kept as a string-keyed companion (not a sealed/enum) so unknown keys
 * from future versions degrade gracefully instead of crashing
 * deserialisation.
 */
object ZoneIcon {
    const val HOME = "home"
    const val WORK = "work"
    const val FAMILY = "family"
    const val FAVORITE = "favorite"
    const val GYM = "gym"
    const val SCHOOL = "school"
    const val SHOPPING = "shopping"
    const val OTHER = "other"

    /**
     * Fallback when [Zone.iconKey] doesn't match any known preset, AND the icon
     * pre-selected when creating a new zone: [OTHER] is the generic location pin,
     * the safest neutral default. It leads [PRESETS] so it's the first chip in the
     * picker.
     */
    const val DEFAULT = OTHER

    val PRESETS: List<String> = listOf(OTHER, HOME, WORK, FAMILY, FAVORITE, GYM, SCHOOL, SHOPPING)
}
