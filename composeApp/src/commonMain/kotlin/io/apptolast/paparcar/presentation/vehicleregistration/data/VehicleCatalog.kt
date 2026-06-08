package io.apptolast.paparcar.presentation.vehicleregistration.data

import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleSize

/**
 * Static catalog of popular vehicle brands + models with their pre-mapped [CarbodyType].
 *
 * Two layers of lookup:
 *  1. **Exact-match** against the curated table below (~30 brands, ~5–10 models each).
 *     Highest precision — used to seed the Hero card on the registration screen.
 *  2. **Pattern fallback** using keyword `contains` over the lowercased brand+model
 *     string. Lets the inference still produce a sensible body type for marques we
 *     don't list (or for the "Other…" custom brand path) when the model name carries
 *     a recognisable cue (e.g. *anything* called Hilux → PICKUP).
 *
 * The fallback never overrides an exact catalog hit. Callers that need only the size
 * dimension can derive it via [CarbodyType.sizeCategory] — no separate API needed.
 */
object VehicleCatalog {

    private val catalog: Map<String, List<String>> = linkedMapOf(
        "Audi" to listOf("A1", "A3", "A4", "A6", "Q3", "Q5", "Q7"),
        "BMW" to listOf("Serie 1", "Serie 3", "Serie 5", "X1", "X3", "X5"),
        "Citroën" to listOf("C3", "C4", "C5 Aircross", "Berlingo", "Jumpy"),
        "Cupra" to listOf("Born", "Formentor", "Leon", "Terramar"),
        "Dacia" to listOf("Duster", "Sandero", "Logan", "Jogger", "Spring"),
        "DS" to listOf("DS3", "DS4", "DS7"),
        "Fiat" to listOf("500", "Panda", "Tipo", "Punto"),
        "Ford" to listOf("Fiesta", "Focus", "Mustang Mach-E", "Puma", "Kuga", "Transit"),
        "Honda" to listOf("Civic", "Jazz", "CR-V", "HR-V", "e:Ny1"),
        "Hyundai" to listOf("i20", "i30", "Tucson", "Kona", "Ioniq 5", "Ioniq 6"),
        "Jeep" to listOf("Renegade", "Compass", "Avenger", "Wrangler"),
        "Kia" to listOf("Picanto", "Stonic", "Sportage", "Ceed", "EV6", "Niro"),
        "Land Rover" to listOf("Defender", "Discovery", "Range Rover", "Freelander"),
        "Lexus" to listOf("UX", "NX", "RX", "ES"),
        "Mazda" to listOf("2", "3", "CX-3", "CX-5", "CX-60"),
        "Mercedes" to listOf("Clase A", "Clase C", "Clase E", "GLA", "GLC", "Vito"),
        "Mini" to listOf("Cooper", "Countryman", "Clubman"),
        "Mitsubishi" to listOf("ASX", "Outlander", "Eclipse Cross"),
        "Nissan" to listOf("Micra", "Juke", "Qashqai", "Leaf", "X-Trail", "Ariya"),
        "Opel" to listOf("Corsa", "Astra", "Mokka", "Crossland", "Grandland"),
        "Peugeot" to listOf("208", "308", "508", "2008", "3008", "5008"),
        "Porsche" to listOf("911", "Cayenne", "Macan", "Taycan"),
        "Renault" to listOf("Clio", "Megane", "Captur", "Kadjar", "Austral", "Zoe"),
        "Seat" to listOf("Ibiza", "León", "Ateca", "Arona", "Tarraco"),
        "Skoda" to listOf("Fabia", "Octavia", "Karoq", "Kodiaq", "Superb"),
        "Suzuki" to listOf("Swift", "Vitara", "Jimny", "S-Cross"),
        "Tesla" to listOf("Model 3", "Model Y", "Model S", "Model X"),
        "Toyota" to listOf("Aygo X", "Yaris", "Corolla", "C-HR", "RAV4", "Prius", "bZ4X"),
        "Volkswagen" to listOf("Polo", "Golf", "Tiguan", "Passat", "T-Roc", "ID.3", "ID.4"),
        "Volvo" to listOf("XC40", "XC60", "XC90", "C40"),
    )

    private val bodyTypes: Map<String, Map<String, CarbodyType>> = mapOf(
        "Audi" to mapOf(
            "A1" to CarbodyType.HATCHBACK_SMALL,
            "A3" to CarbodyType.HATCHBACK_MEDIUM,
            "A4" to CarbodyType.SEDAN,
            "A6" to CarbodyType.SEDAN,
            "Q3" to CarbodyType.SUV_MEDIUM,
            "Q5" to CarbodyType.SUV_LARGE,
            "Q7" to CarbodyType.SUV_LARGE,
        ),
        "BMW" to mapOf(
            "Serie 1" to CarbodyType.HATCHBACK_MEDIUM,
            "Serie 3" to CarbodyType.SEDAN,
            "Serie 5" to CarbodyType.SEDAN,
            "X1" to CarbodyType.SUV_MEDIUM,
            "X3" to CarbodyType.SUV_LARGE,
            "X5" to CarbodyType.SUV_LARGE,
        ),
        "Citroën" to mapOf(
            "C3" to CarbodyType.HATCHBACK_SMALL,
            "C4" to CarbodyType.HATCHBACK_MEDIUM,
            "C5 Aircross" to CarbodyType.SUV_LARGE,
            "Berlingo" to CarbodyType.VAN_LIGHT,
            "Jumpy" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Cupra" to mapOf(
            "Born" to CarbodyType.HATCHBACK_MEDIUM,
            "Formentor" to CarbodyType.SUV_MEDIUM,
            "Leon" to CarbodyType.HATCHBACK_MEDIUM,
            "Terramar" to CarbodyType.SUV_MEDIUM,
        ),
        "Dacia" to mapOf(
            "Duster" to CarbodyType.SUV_MEDIUM,
            "Sandero" to CarbodyType.HATCHBACK_MEDIUM,
            // Logan shares the Sandero platform (~4.35 m) — same length envelope.
            "Logan" to CarbodyType.HATCHBACK_MEDIUM,
            "Jogger" to CarbodyType.FAMILY_LONG,
            "Spring" to CarbodyType.HATCHBACK_SMALL,
        ),
        "DS" to mapOf(
            "DS3" to CarbodyType.HATCHBACK_SMALL,
            "DS4" to CarbodyType.HATCHBACK_MEDIUM,
            "DS7" to CarbodyType.SUV_LARGE,
        ),
        "Fiat" to mapOf(
            "500" to CarbodyType.HATCHBACK_SMALL,
            "Panda" to CarbodyType.HATCHBACK_SMALL,
            "Tipo" to CarbodyType.HATCHBACK_MEDIUM,
            "Punto" to CarbodyType.HATCHBACK_SMALL,
        ),
        "Ford" to mapOf(
            "Fiesta" to CarbodyType.HATCHBACK_SMALL,
            "Focus" to CarbodyType.HATCHBACK_MEDIUM,
            "Mustang Mach-E" to CarbodyType.SUV_LARGE,
            "Puma" to CarbodyType.SUV_MEDIUM,
            "Kuga" to CarbodyType.SUV_LARGE,
            "Transit" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Honda" to mapOf(
            "Civic" to CarbodyType.HATCHBACK_MEDIUM,
            "Jazz" to CarbodyType.HATCHBACK_SMALL,
            "CR-V" to CarbodyType.SUV_LARGE,
            "HR-V" to CarbodyType.SUV_MEDIUM,
            "e:Ny1" to CarbodyType.SUV_MEDIUM,
        ),
        "Hyundai" to mapOf(
            "i20" to CarbodyType.HATCHBACK_SMALL,
            "i30" to CarbodyType.HATCHBACK_MEDIUM,
            "Tucson" to CarbodyType.SUV_MEDIUM,
            "Kona" to CarbodyType.SUV_SMALL,
            "Ioniq 5" to CarbodyType.SUV_LARGE,
            "Ioniq 6" to CarbodyType.SEDAN,
        ),
        "Jeep" to mapOf(
            "Renegade" to CarbodyType.SUV_SMALL,
            "Compass" to CarbodyType.SUV_MEDIUM,
            "Avenger" to CarbodyType.SUV_SMALL,
            "Wrangler" to CarbodyType.SUV_LARGE,
        ),
        "Kia" to mapOf(
            "Picanto" to CarbodyType.HATCHBACK_SMALL,
            "Stonic" to CarbodyType.SUV_SMALL,
            "Sportage" to CarbodyType.SUV_LARGE,
            "Ceed" to CarbodyType.HATCHBACK_MEDIUM,
            "EV6" to CarbodyType.SUV_LARGE,
            "Niro" to CarbodyType.SUV_MEDIUM,
        ),
        "Land Rover" to mapOf(
            "Defender" to CarbodyType.SUV_LARGE,
            "Discovery" to CarbodyType.SUV_LARGE,
            "Range Rover" to CarbodyType.SUV_LARGE,
            "Freelander" to CarbodyType.SUV_MEDIUM,
        ),
        "Lexus" to mapOf(
            "UX" to CarbodyType.SUV_MEDIUM,
            "NX" to CarbodyType.SUV_LARGE,
            "RX" to CarbodyType.SUV_LARGE,
            "ES" to CarbodyType.SEDAN,
        ),
        "Mazda" to mapOf(
            "2" to CarbodyType.HATCHBACK_SMALL,
            "3" to CarbodyType.HATCHBACK_MEDIUM,
            "CX-3" to CarbodyType.SUV_MEDIUM,
            "CX-5" to CarbodyType.SUV_LARGE,
            "CX-60" to CarbodyType.SUV_LARGE,
        ),
        "Mercedes" to mapOf(
            "Clase A" to CarbodyType.HATCHBACK_MEDIUM,
            "Clase C" to CarbodyType.SEDAN,
            "Clase E" to CarbodyType.SEDAN,
            "GLA" to CarbodyType.SUV_MEDIUM,
            "GLC" to CarbodyType.SUV_LARGE,
            "Vito" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Mini" to mapOf(
            "Cooper" to CarbodyType.HATCHBACK_SMALL,
            "Countryman" to CarbodyType.SUV_MEDIUM,
            "Clubman" to CarbodyType.HATCHBACK_MEDIUM,
        ),
        "Mitsubishi" to mapOf(
            "ASX" to CarbodyType.SUV_MEDIUM,
            "Outlander" to CarbodyType.SUV_LARGE,
            "Eclipse Cross" to CarbodyType.SUV_MEDIUM,
        ),
        "Nissan" to mapOf(
            "Micra" to CarbodyType.HATCHBACK_SMALL,
            "Juke" to CarbodyType.SUV_SMALL,
            "Qashqai" to CarbodyType.SUV_MEDIUM,
            "Leaf" to CarbodyType.HATCHBACK_MEDIUM,
            "X-Trail" to CarbodyType.SUV_LARGE,
            "Ariya" to CarbodyType.SUV_LARGE,
        ),
        "Opel" to mapOf(
            "Corsa" to CarbodyType.HATCHBACK_SMALL,
            "Astra" to CarbodyType.HATCHBACK_MEDIUM,
            "Mokka" to CarbodyType.SUV_SMALL,
            "Crossland" to CarbodyType.SUV_SMALL,
            "Grandland" to CarbodyType.SUV_LARGE,
        ),
        "Peugeot" to mapOf(
            "208" to CarbodyType.HATCHBACK_SMALL,
            "308" to CarbodyType.HATCHBACK_MEDIUM,
            "508" to CarbodyType.SEDAN,
            "2008" to CarbodyType.SUV_MEDIUM,
            "3008" to CarbodyType.SUV_LARGE,
            "5008" to CarbodyType.SUV_LARGE,
        ),
        "Porsche" to mapOf(
            "911" to CarbodyType.SEDAN,
            "Cayenne" to CarbodyType.SUV_LARGE,
            "Macan" to CarbodyType.SUV_LARGE,
            "Taycan" to CarbodyType.SEDAN,
        ),
        "Renault" to mapOf(
            "Clio" to CarbodyType.HATCHBACK_SMALL,
            "Megane" to CarbodyType.HATCHBACK_MEDIUM,
            "Captur" to CarbodyType.SUV_SMALL,
            "Kadjar" to CarbodyType.SUV_MEDIUM,
            "Austral" to CarbodyType.SUV_LARGE,
            "Zoe" to CarbodyType.HATCHBACK_SMALL,
        ),
        "Seat" to mapOf(
            "Ibiza" to CarbodyType.HATCHBACK_SMALL,
            "León" to CarbodyType.HATCHBACK_MEDIUM,
            "Ateca" to CarbodyType.SUV_MEDIUM,
            "Arona" to CarbodyType.SUV_MEDIUM,
            "Tarraco" to CarbodyType.SUV_LARGE,
        ),
        "Skoda" to mapOf(
            "Fabia" to CarbodyType.HATCHBACK_SMALL,
            "Octavia" to CarbodyType.SEDAN,
            "Karoq" to CarbodyType.SUV_MEDIUM,
            "Kodiaq" to CarbodyType.SUV_LARGE,
            "Superb" to CarbodyType.SEDAN,
        ),
        "Suzuki" to mapOf(
            "Swift" to CarbodyType.HATCHBACK_SMALL,
            "Vitara" to CarbodyType.SUV_SMALL,
            "Jimny" to CarbodyType.SUV_SMALL,
            "S-Cross" to CarbodyType.SUV_MEDIUM,
        ),
        "Tesla" to mapOf(
            "Model 3" to CarbodyType.SEDAN,
            "Model Y" to CarbodyType.SUV_LARGE,
            "Model S" to CarbodyType.SEDAN,
            "Model X" to CarbodyType.SUV_LARGE,
        ),
        "Toyota" to mapOf(
            "Aygo X" to CarbodyType.HATCHBACK_SMALL,
            "Yaris" to CarbodyType.HATCHBACK_SMALL,
            "Corolla" to CarbodyType.HATCHBACK_MEDIUM,
            "C-HR" to CarbodyType.SUV_MEDIUM,
            "RAV4" to CarbodyType.SUV_LARGE,
            "Prius" to CarbodyType.HATCHBACK_MEDIUM,
            "bZ4X" to CarbodyType.SUV_LARGE,
        ),
        "Volkswagen" to mapOf(
            "Polo" to CarbodyType.HATCHBACK_SMALL,
            "Golf" to CarbodyType.HATCHBACK_MEDIUM,
            "Tiguan" to CarbodyType.SUV_LARGE,
            "Passat" to CarbodyType.SEDAN,
            "T-Roc" to CarbodyType.SUV_SMALL,
            "ID.3" to CarbodyType.HATCHBACK_MEDIUM,
            "ID.4" to CarbodyType.SUV_LARGE,
        ),
        "Volvo" to mapOf(
            "XC40" to CarbodyType.SUV_MEDIUM,
            "XC60" to CarbodyType.SUV_LARGE,
            "XC90" to CarbodyType.SUV_LARGE,
            "C40" to CarbodyType.SUV_MEDIUM,
        ),
    )

    /**
     * Ordered pattern rules used as a fallback when the exact catalog has no entry.
     *
     * Ordering matters — the *first* rule whose keyword list contains a substring of
     * `"brand model"` (lowercased) wins. Higher-specificity bodies are listed first
     * (commercial vans before pickups before SUVs before sedans before hatchbacks)
     * so an ambiguous "Volkswagen Transporter" hits VAN_COMMERCIAL, not SEDAN.
     */
    private val patternRules: List<Pair<List<String>, CarbodyType>> = listOf(
        listOf("vito", "transporter", "trafic", "vivaro", "expert", "jumpy", "ducato", "boxer", "sprinter")
            to CarbodyType.VAN_COMMERCIAL,
        listOf("hilux", "ranger", "d-max", "amarok", "navara", "l200")
            to CarbodyType.PICKUP,
        listOf("berlingo", "kangoo", "partner", "rifter", "doblo", "combo", "caddy")
            to CarbodyType.VAN_LIGHT,
        listOf("rav4", "model y", "5008", "tarraco", "x5", "x7", "q7", "q8", "kuga", "kodiaq", "kodiac", "tiguan", "sorento")
            to CarbodyType.SUV_LARGE,
        listOf("tucson", "sportage", "qashqai", "ateca", "karoq", "kadjar", "compass", "tiguan allspace")
            to CarbodyType.SUV_MEDIUM,
        listOf("arona", "yaris cross", "t-cross", "kona", "captur", "juke", "puma", "stonic", "mokka", "crossland")
            to CarbodyType.SUV_SMALL,
        listOf("avant", "touring", "variant", " sw", "estate", "kombi")
            to CarbodyType.FAMILY_LONG,
        listOf("model 3", "serie 3", "serie 5", "clase c", "clase e", "a4", "a6", "passat", "octavia", "508", "superb")
            to CarbodyType.SEDAN,
        listOf("golf", "corolla", "leon", "a3", "civic", "focus", "megane", "astra", "i30", "ceed")
            to CarbodyType.HATCHBACK_MEDIUM,
        listOf("fiat 500", "panda", "ibiza", "clio", "micra", "polo", "fiesta", "yaris", "i20", "208", "corsa", "aygo")
            to CarbodyType.HATCHBACK_SMALL,
    )

    fun brands(): List<String> = catalog.keys.toList()

    fun modelsFor(brand: String): List<String> = catalog[brand] ?: emptyList()

    /**
     * Resolves the [CarbodyType] for [brand] + [model]. Tries exact match first,
     * then falls back to keyword patterns over the lowercased combined string.
     * Returns null when no rule matches — the registration screen then asks the
     * user to pick manually.
     */
    fun inferBodyType(brand: String, model: String): CarbodyType? {
        bodyTypes[brand]?.get(model)?.let { return it }
        val haystack = "$brand $model".trim().lowercase()
        if (haystack.isBlank()) return null
        return patternRules.firstOrNull { (patterns, _) ->
            patterns.any { haystack.contains(it) }
        }?.second
    }

    /** Convenience: returns just the size dimension of the inferred carbody, when available. */
    fun inferSize(brand: String, model: String): VehicleSize? = inferBodyType(brand, model)?.sizeCategory
}
