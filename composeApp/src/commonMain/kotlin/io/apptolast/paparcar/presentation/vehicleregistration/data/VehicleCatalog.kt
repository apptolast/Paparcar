package io.apptolast.paparcar.presentation.vehicleregistration.data

import io.apptolast.paparcar.domain.model.VehicleSize

/**
 * Static catalog of popular vehicle brands and their top models, with pre-mapped sizes.
 *
 * Scope intentionally limited (~30 brands, ~5–10 models each). The "Other…"
 * option in the dropdowns covers everything not listed here.
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

    private val sizes: Map<String, Map<String, VehicleSize>> = mapOf(
        "Audi" to mapOf(
            "A1" to VehicleSize.SMALL,
            "A3" to VehicleSize.MEDIUM,
            "A4" to VehicleSize.MEDIUM,
            "A6" to VehicleSize.LARGE,
            "Q3" to VehicleSize.MEDIUM,
            "Q5" to VehicleSize.LARGE,
            "Q7" to VehicleSize.LARGE
        ),
        "BMW" to mapOf(
            "Serie 1" to VehicleSize.MEDIUM,
            "Serie 3" to VehicleSize.MEDIUM,
            "Serie 5" to VehicleSize.LARGE,
            "X1" to VehicleSize.MEDIUM,
            "X3" to VehicleSize.LARGE,
            "X5" to VehicleSize.LARGE
        ),
        "Citroën" to mapOf(
            "C3" to VehicleSize.SMALL,
            "C4" to VehicleSize.MEDIUM,
            "C5 Aircross" to VehicleSize.LARGE,
            "Berlingo" to VehicleSize.VAN,
            "Jumpy" to VehicleSize.VAN
        ),
        "Cupra" to mapOf(
            "Born" to VehicleSize.MEDIUM,
            "Formentor" to VehicleSize.MEDIUM,
            "Leon" to VehicleSize.MEDIUM,
            "Terramar" to VehicleSize.MEDIUM
        ),
        "Dacia" to mapOf(
            "Duster" to VehicleSize.MEDIUM,
            "Sandero" to VehicleSize.MEDIUM,
            "Logan" to VehicleSize.MEDIUM,
            "Jogger" to VehicleSize.MEDIUM,
            "Spring" to VehicleSize.SMALL
        ),
        "DS" to mapOf(
            "DS3" to VehicleSize.SMALL,
            "DS4" to VehicleSize.MEDIUM,
            "DS7" to VehicleSize.LARGE
        ),
        "Fiat" to mapOf(
            "500" to VehicleSize.SMALL,
            "Panda" to VehicleSize.SMALL,
            "Tipo" to VehicleSize.MEDIUM,
            "Punto" to VehicleSize.SMALL
        ),
        "Ford" to mapOf(
            "Fiesta" to VehicleSize.SMALL,
            "Focus" to VehicleSize.MEDIUM,
            "Mustang Mach-E" to VehicleSize.LARGE,
            "Puma" to VehicleSize.MEDIUM,
            "Kuga" to VehicleSize.LARGE,
            "Transit" to VehicleSize.VAN
        ),
        "Honda" to mapOf(
            "Civic" to VehicleSize.MEDIUM,
            "Jazz" to VehicleSize.SMALL,
            "CR-V" to VehicleSize.LARGE,
            "HR-V" to VehicleSize.MEDIUM,
            "e:Ny1" to VehicleSize.MEDIUM
        ),
        "Hyundai" to mapOf(
            "i20" to VehicleSize.SMALL,
            "i30" to VehicleSize.MEDIUM,
            "Tucson" to VehicleSize.LARGE,
            "Kona" to VehicleSize.MEDIUM,
            "Ioniq 5" to VehicleSize.LARGE,
            "Ioniq 6" to VehicleSize.LARGE
        ),
        "Jeep" to mapOf(
            "Renegade" to VehicleSize.MEDIUM,
            "Compass" to VehicleSize.MEDIUM,
            "Avenger" to VehicleSize.SMALL,
            "Wrangler" to VehicleSize.LARGE
        ),
        "Kia" to mapOf(
            "Picanto" to VehicleSize.SMALL,
            "Stonic" to VehicleSize.MEDIUM,
            "Sportage" to VehicleSize.LARGE,
            "Ceed" to VehicleSize.MEDIUM,
            "EV6" to VehicleSize.LARGE,
            "Niro" to VehicleSize.MEDIUM
        ),
        "Land Rover" to mapOf(
            "Defender" to VehicleSize.LARGE,
            "Discovery" to VehicleSize.LARGE,
            "Range Rover" to VehicleSize.LARGE,
            "Freelander" to VehicleSize.MEDIUM
        ),
        "Lexus" to mapOf(
            "UX" to VehicleSize.MEDIUM,
            "NX" to VehicleSize.LARGE,
            "RX" to VehicleSize.LARGE,
            "ES" to VehicleSize.LARGE
        ),
        "Mazda" to mapOf(
            "2" to VehicleSize.SMALL,
            "3" to VehicleSize.MEDIUM,
            "CX-3" to VehicleSize.MEDIUM,
            "CX-5" to VehicleSize.LARGE,
            "CX-60" to VehicleSize.LARGE
        ),
        "Mercedes" to mapOf(
            "Clase A" to VehicleSize.MEDIUM,
            "Clase C" to VehicleSize.MEDIUM,
            "Clase E" to VehicleSize.LARGE,
            "GLA" to VehicleSize.MEDIUM,
            "GLC" to VehicleSize.LARGE,
            "Vito" to VehicleSize.VAN
        ),
        "Mini" to mapOf(
            "Cooper" to VehicleSize.SMALL,
            "Countryman" to VehicleSize.MEDIUM,
            "Clubman" to VehicleSize.MEDIUM
        ),
        "Mitsubishi" to mapOf(
            "ASX" to VehicleSize.MEDIUM,
            "Outlander" to VehicleSize.LARGE,
            "Eclipse Cross" to VehicleSize.MEDIUM
        ),
        "Nissan" to mapOf(
            "Micra" to VehicleSize.SMALL,
            "Juke" to VehicleSize.MEDIUM,
            "Qashqai" to VehicleSize.LARGE,
            "Leaf" to VehicleSize.MEDIUM,
            "X-Trail" to VehicleSize.LARGE,
            "Ariya" to VehicleSize.LARGE
        ),
        "Opel" to mapOf(
            "Corsa" to VehicleSize.SMALL,
            "Astra" to VehicleSize.MEDIUM,
            "Mokka" to VehicleSize.MEDIUM,
            "Crossland" to VehicleSize.MEDIUM,
            "Grandland" to VehicleSize.LARGE
        ),
        "Peugeot" to mapOf(
            "208" to VehicleSize.SMALL,
            "308" to VehicleSize.MEDIUM,
            "508" to VehicleSize.LARGE,
            "2008" to VehicleSize.MEDIUM,
            "3008" to VehicleSize.LARGE,
            "5008" to VehicleSize.LARGE
        ),
        "Porsche" to mapOf(
            "911" to VehicleSize.MEDIUM,
            "Cayenne" to VehicleSize.LARGE,
            "Macan" to VehicleSize.LARGE,
            "Taycan" to VehicleSize.LARGE
        ),
        "Renault" to mapOf(
            "Clio" to VehicleSize.SMALL,
            "Megane" to VehicleSize.MEDIUM,
            "Captur" to VehicleSize.MEDIUM,
            "Kadjar" to VehicleSize.LARGE,
            "Austral" to VehicleSize.LARGE,
            "Zoe" to VehicleSize.SMALL
        ),
        "Seat" to mapOf(
            "Ibiza" to VehicleSize.SMALL,
            "León" to VehicleSize.MEDIUM,
            "Ateca" to VehicleSize.MEDIUM,
            "Arona" to VehicleSize.MEDIUM,
            "Tarraco" to VehicleSize.LARGE
        ),
        "Skoda" to mapOf(
            "Fabia" to VehicleSize.SMALL,
            "Octavia" to VehicleSize.MEDIUM,
            "Karoq" to VehicleSize.MEDIUM,
            "Kodiaq" to VehicleSize.LARGE,
            "Superb" to VehicleSize.LARGE
        ),
        "Suzuki" to mapOf(
            "Swift" to VehicleSize.SMALL,
            "Vitara" to VehicleSize.MEDIUM,
            "Jimny" to VehicleSize.SMALL,
            "S-Cross" to VehicleSize.MEDIUM
        ),
        "Tesla" to mapOf(
            "Model 3" to VehicleSize.MEDIUM,
            "Model Y" to VehicleSize.LARGE,
            "Model S" to VehicleSize.LARGE,
            "Model X" to VehicleSize.LARGE
        ),
        "Toyota" to mapOf(
            "Aygo X" to VehicleSize.SMALL,
            "Yaris" to VehicleSize.SMALL,
            "Corolla" to VehicleSize.MEDIUM,
            "C-HR" to VehicleSize.MEDIUM,
            "RAV4" to VehicleSize.LARGE,
            "Prius" to VehicleSize.MEDIUM,
            "bZ4X" to VehicleSize.LARGE
        ),
        "Volkswagen" to mapOf(
            "Polo" to VehicleSize.SMALL,
            "Golf" to VehicleSize.MEDIUM,
            "Tiguan" to VehicleSize.LARGE,
            "Passat" to VehicleSize.LARGE,
            "T-Roc" to VehicleSize.MEDIUM,
            "ID.3" to VehicleSize.MEDIUM,
            "ID.4" to VehicleSize.LARGE
        ),
        "Volvo" to mapOf(
            "XC40" to VehicleSize.MEDIUM,
            "XC60" to VehicleSize.LARGE,
            "XC90" to VehicleSize.LARGE,
            "C40" to VehicleSize.MEDIUM
        ),
    )

    fun brands(): List<String> = catalog.keys.toList()

    fun modelsFor(brand: String): List<String> = catalog[brand] ?: emptyList()

    /** Returns the pre-mapped size for a catalog brand+model pair, or null for "Other" entries. */
    fun sizeFor(brand: String, model: String): VehicleSize? = sizes[brand]?.get(model)
}
