package io.apptolast.paparcar.presentation.vehicleregistration.data

/**
 * Static catalog of popular vehicle brands and their top models.
 *
 * Scope intentionally limited (~30 brands, ~5–10 models each). The "Other…"
 * option in the dropdowns covers everything not listed here.
 */
object VehicleCatalog {

    private val catalog: Map<String, List<String>> = linkedMapOf(
        "Audi"        to listOf("A1", "A3", "A4", "A6", "Q3", "Q5", "Q7"),
        "BMW"         to listOf("Serie 1", "Serie 3", "Serie 5", "X1", "X3", "X5"),
        "Citroën"     to listOf("C3", "C4", "C5 Aircross", "Berlingo", "Jumpy"),
        "Cupra"       to listOf("Born", "Formentor", "Leon", "Terramar"),
        "Dacia"       to listOf("Duster", "Sandero", "Logan", "Jogger", "Spring"),
        "DS"          to listOf("DS3", "DS4", "DS7"),
        "Fiat"        to listOf("500", "Panda", "Tipo", "Punto"),
        "Ford"        to listOf("Fiesta", "Focus", "Mustang Mach-E", "Puma", "Kuga", "Transit"),
        "Honda"       to listOf("Civic", "Jazz", "CR-V", "HR-V", "e:Ny1"),
        "Hyundai"     to listOf("i20", "i30", "Tucson", "Kona", "Ioniq 5", "Ioniq 6"),
        "Jeep"        to listOf("Renegade", "Compass", "Avenger", "Wrangler"),
        "Kia"         to listOf("Picanto", "Stonic", "Sportage", "Ceed", "EV6", "Niro"),
        "Land Rover"  to listOf("Defender", "Discovery", "Range Rover", "Freelander"),
        "Lexus"       to listOf("UX", "NX", "RX", "ES"),
        "Mazda"       to listOf("2", "3", "CX-3", "CX-5", "CX-60"),
        "Mercedes"    to listOf("Clase A", "Clase C", "Clase E", "GLA", "GLC", "Vito"),
        "Mini"        to listOf("Cooper", "Countryman", "Clubman"),
        "Mitsubishi"  to listOf("ASX", "Outlander", "Eclipse Cross"),
        "Nissan"      to listOf("Micra", "Juke", "Qashqai", "Leaf", "X-Trail", "Ariya"),
        "Opel"        to listOf("Corsa", "Astra", "Mokka", "Crossland", "Grandland"),
        "Peugeot"     to listOf("208", "308", "508", "2008", "3008", "5008"),
        "Porsche"     to listOf("911", "Cayenne", "Macan", "Taycan"),
        "Renault"     to listOf("Clio", "Megane", "Captur", "Kadjar", "Austral", "Zoe"),
        "Seat"        to listOf("Ibiza", "León", "Ateca", "Arona", "Tarraco"),
        "Skoda"       to listOf("Fabia", "Octavia", "Karoq", "Kodiaq", "Superb"),
        "Suzuki"      to listOf("Swift", "Vitara", "Jimny", "S-Cross"),
        "Tesla"       to listOf("Model 3", "Model Y", "Model S", "Model X"),
        "Toyota"      to listOf("Aygo X", "Yaris", "Corolla", "C-HR", "RAV4", "Prius", "bZ4X"),
        "Volkswagen"  to listOf("Polo", "Golf", "Tiguan", "Passat", "T-Roc", "ID.3", "ID.4"),
        "Volvo"       to listOf("XC40", "XC60", "XC90", "C40"),
    )

    fun brands(): List<String> = catalog.keys.toList()

    fun modelsFor(brand: String): List<String> = catalog[brand] ?: emptyList()
}
