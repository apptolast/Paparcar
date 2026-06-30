package io.apptolast.paparcar.data.mapper

/**
 * Parses [this] string into enum [E], returning null when it is null, blank, or not a
 * valid constant name. Centralises the `runCatching { E.valueOf(it) }.getOrNull()` idiom
 * repeated across the mappers — tolerant of legacy/blank values synced from Firestore.
 */
internal inline fun <reified E : Enum<E>> String?.toEnumOrNull(): E? =
    this?.let { runCatching { enumValueOf<E>(it) }.getOrNull() }

/** Like [toEnumOrNull] but falls back to [default] when parsing fails. */
internal inline fun <reified E : Enum<E>> String?.toEnumOrDefault(default: E): E =
    toEnumOrNull<E>() ?: default
