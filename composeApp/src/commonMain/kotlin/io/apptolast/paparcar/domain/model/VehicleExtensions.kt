package io.apptolast.paparcar.domain.model

/**
 * Returns the best human-readable label for this vehicle.
 *
 * Priority: explicit [name] → "brand model" concatenation → [fallback].
 *
 * The [fallback] is intentionally caller-supplied so localized strings
 * (e.g. "Unnamed vehicle", "Car 1") can be resolved in the UI layer where
 * string resources are available, while the domain stays resource-free.
 */
fun Vehicle.displayName(fallback: String = ""): String =
    name?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(brand, model).joinToString(" ").takeIf { it.isNotBlank() }
        ?: fallback
