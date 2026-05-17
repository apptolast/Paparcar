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

    /** Fallback when [Zone.iconKey] doesn't match any known preset. */
    const val DEFAULT = OTHER

    val PRESETS: List<String> = listOf(HOME, WORK, FAMILY, FAVORITE, GYM, SCHOOL, SHOPPING, OTHER)
}
