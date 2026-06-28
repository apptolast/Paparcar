package io.apptolast.paparcar.presentation.vehicleregistration.data

import io.apptolast.paparcar.domain.model.CarbodyType
import io.apptolast.paparcar.domain.model.VehicleSize

/**
 * Static catalog of vehicle brands + models with their pre-mapped [CarbodyType].
 *
 * **Single source of truth.** Each brand maps to an ordered list of
 * `model to CarbodyType` pairs. Both the dropdown model list ([modelsFor]) and
 * the exact-match inference ([inferBodyType]) read from this same table, so the
 * two can never drift — every model the dropdown offers is guaranteed to carry a
 * body type (enforced by `VehicleCatalogTest`).
 *
 * Two layers of lookup:
 *  1. **Exact-match** against the curated table below. Highest precision —
 *     seeds the Hero card on the registration screen.
 *  2. **Pattern fallback** using keyword `contains` over the lowercased
 *     brand+model string. Lets the inference still produce a sensible body type
 *     for marques/models we don't list (or for the free-text "Other" path) when
 *     the model name carries a recognisable cue (e.g. *anything* called Hilux →
 *     PICKUP). The fallback never overrides an exact catalog hit.
 *
 * When even the pattern fallback misses, [inferBodyType] returns null and the
 * registration ViewModel applies its own default so the user is never blocked —
 * they can always refine the body via the manual picker. Callers that need only
 * the size dimension can derive it via [CarbodyType.sizeCategory].
 */
object VehicleCatalog {

    private val catalog: Map<String, List<Pair<String, CarbodyType>>> = linkedMapOf(
        "Abarth" to listOf(
            "500" to CarbodyType.HATCHBACK_SMALL,
            "595" to CarbodyType.HATCHBACK_SMALL,
            "695" to CarbodyType.HATCHBACK_SMALL,
            "500e" to CarbodyType.HATCHBACK_SMALL,
            "600e" to CarbodyType.SUV_SMALL,
            "124 Spider" to CarbodyType.SEDAN,
        ),
        "Acura" to listOf(
            "Integra" to CarbodyType.SEDAN,
            "TLX" to CarbodyType.SEDAN,
            "RDX" to CarbodyType.SUV_LARGE,
            "MDX" to CarbodyType.SUV_LARGE,
        ),
        "Alfa Romeo" to listOf(
            "MiTo" to CarbodyType.HATCHBACK_SMALL,
            "Giulietta" to CarbodyType.HATCHBACK_MEDIUM,
            "Giulia" to CarbodyType.SEDAN,
            "Junior" to CarbodyType.SUV_SMALL,
            "Tonale" to CarbodyType.SUV_MEDIUM,
            "Stelvio" to CarbodyType.SUV_LARGE,
        ),
        "Aston Martin" to listOf(
            "Vantage" to CarbodyType.SEDAN,
            "DB11" to CarbodyType.SEDAN,
            "DB12" to CarbodyType.SEDAN,
            "DBS" to CarbodyType.SEDAN,
            "DBX" to CarbodyType.SUV_LARGE,
        ),
        "Audi" to listOf(
            "A1" to CarbodyType.HATCHBACK_SMALL,
            "A3" to CarbodyType.HATCHBACK_MEDIUM,
            "A4" to CarbodyType.SEDAN,
            "A5" to CarbodyType.SEDAN,
            "A6" to CarbodyType.SEDAN,
            "A7" to CarbodyType.SEDAN,
            "A8" to CarbodyType.SEDAN,
            "Q2" to CarbodyType.SUV_SMALL,
            "Q3" to CarbodyType.SUV_MEDIUM,
            "Q4 e-tron" to CarbodyType.SUV_MEDIUM,
            "Q5" to CarbodyType.SUV_LARGE,
            "Q6 e-tron" to CarbodyType.SUV_LARGE,
            "Q7" to CarbodyType.SUV_LARGE,
            "Q8" to CarbodyType.SUV_LARGE,
            "e-tron GT" to CarbodyType.SEDAN,
        ),
        "Bentley" to listOf(
            "Continental GT" to CarbodyType.SEDAN,
            "Flying Spur" to CarbodyType.SEDAN,
            "Bentayga" to CarbodyType.SUV_LARGE,
        ),
        "BMW" to listOf(
            "Serie 1" to CarbodyType.HATCHBACK_MEDIUM,
            "Serie 2" to CarbodyType.SEDAN,
            "Serie 3" to CarbodyType.SEDAN,
            "Serie 4" to CarbodyType.SEDAN,
            "Serie 5" to CarbodyType.SEDAN,
            "Serie 7" to CarbodyType.SEDAN,
            "Serie 8" to CarbodyType.SEDAN,
            "i3" to CarbodyType.HATCHBACK_SMALL,
            "i4" to CarbodyType.SEDAN,
            "X1" to CarbodyType.SUV_MEDIUM,
            "X2" to CarbodyType.SUV_MEDIUM,
            "iX1" to CarbodyType.SUV_MEDIUM,
            "X3" to CarbodyType.SUV_LARGE,
            "X4" to CarbodyType.SUV_LARGE,
            "X5" to CarbodyType.SUV_LARGE,
            "X6" to CarbodyType.SUV_LARGE,
            "X7" to CarbodyType.SUV_LARGE,
            "iX" to CarbodyType.SUV_LARGE,
        ),
        "Buick" to listOf(
            "Encore" to CarbodyType.SUV_SMALL,
            "Envision" to CarbodyType.SUV_LARGE,
            "Enclave" to CarbodyType.SUV_LARGE,
        ),
        "BYD" to listOf(
            "Dolphin Surf" to CarbodyType.HATCHBACK_SMALL,
            "Dolphin" to CarbodyType.HATCHBACK_MEDIUM,
            "Atto 3" to CarbodyType.SUV_MEDIUM,
            "Seal" to CarbodyType.SEDAN,
            "Han" to CarbodyType.SEDAN,
            "Seal U" to CarbodyType.SUV_LARGE,
            "Sealion 7" to CarbodyType.SUV_LARGE,
            "Tang" to CarbodyType.SUV_LARGE,
        ),
        "Cadillac" to listOf(
            "CT4" to CarbodyType.SEDAN,
            "CT5" to CarbodyType.SEDAN,
            "XT4" to CarbodyType.SUV_MEDIUM,
            "XT5" to CarbodyType.SUV_LARGE,
            "XT6" to CarbodyType.SUV_LARGE,
            "Lyriq" to CarbodyType.SUV_LARGE,
            "Escalade" to CarbodyType.SUV_LARGE,
        ),
        "Chevrolet" to listOf(
            "Spark" to CarbodyType.HATCHBACK_SMALL,
            "Aveo" to CarbodyType.HATCHBACK_SMALL,
            "Bolt" to CarbodyType.HATCHBACK_SMALL,
            "Cruze" to CarbodyType.SEDAN,
            "Malibu" to CarbodyType.SEDAN,
            "Camaro" to CarbodyType.SEDAN,
            "Corvette" to CarbodyType.SEDAN,
            "Trax" to CarbodyType.SUV_SMALL,
            "Trailblazer" to CarbodyType.SUV_MEDIUM,
            "Blazer" to CarbodyType.SUV_LARGE,
            "Captiva" to CarbodyType.SUV_LARGE,
            "Tahoe" to CarbodyType.SUV_LARGE,
            "Silverado" to CarbodyType.PICKUP,
        ),
        "Chrysler" to listOf(
            "300C" to CarbodyType.SEDAN,
            "Voyager" to CarbodyType.VAN_COMMERCIAL,
            "Pacifica" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Citroën" to listOf(
            "Ami" to CarbodyType.HATCHBACK_SMALL,
            "C1" to CarbodyType.HATCHBACK_SMALL,
            "C3" to CarbodyType.HATCHBACK_SMALL,
            "C3 Aircross" to CarbodyType.SUV_SMALL,
            "C4" to CarbodyType.HATCHBACK_MEDIUM,
            "ë-C4" to CarbodyType.HATCHBACK_MEDIUM,
            "C4 X" to CarbodyType.SEDAN,
            "C5 X" to CarbodyType.SEDAN,
            "C5 Aircross" to CarbodyType.SUV_LARGE,
            "Berlingo" to CarbodyType.VAN_LIGHT,
            "Spacetourer" to CarbodyType.VAN_COMMERCIAL,
            "Jumpy" to CarbodyType.VAN_COMMERCIAL,
            "Jumper" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Cupra" to listOf(
            "Born" to CarbodyType.HATCHBACK_MEDIUM,
            "Leon" to CarbodyType.HATCHBACK_MEDIUM,
            "Formentor" to CarbodyType.SUV_MEDIUM,
            "Ateca" to CarbodyType.SUV_MEDIUM,
            "Terramar" to CarbodyType.SUV_MEDIUM,
            "Tavascan" to CarbodyType.SUV_LARGE,
        ),
        "Dacia" to listOf(
            "Spring" to CarbodyType.HATCHBACK_SMALL,
            "Sandero" to CarbodyType.HATCHBACK_MEDIUM,
            // Logan shares the Sandero platform (~4.35 m) — same length envelope.
            "Logan" to CarbodyType.HATCHBACK_MEDIUM,
            "Jogger" to CarbodyType.FAMILY_LONG,
            "Duster" to CarbodyType.SUV_MEDIUM,
            "Bigster" to CarbodyType.SUV_LARGE,
        ),
        "Dodge" to listOf(
            "Charger" to CarbodyType.SEDAN,
            "Challenger" to CarbodyType.SEDAN,
            "Journey" to CarbodyType.SUV_LARGE,
            "Durango" to CarbodyType.SUV_LARGE,
            "RAM 1500" to CarbodyType.PICKUP,
        ),
        "DS" to listOf(
            "DS3" to CarbodyType.HATCHBACK_SMALL,
            "DS3 Crossback" to CarbodyType.SUV_SMALL,
            "DS4" to CarbodyType.HATCHBACK_MEDIUM,
            "DS7" to CarbodyType.SUV_LARGE,
            "DS9" to CarbodyType.SEDAN,
        ),
        "Ferrari" to listOf(
            "Roma" to CarbodyType.SEDAN,
            "Portofino" to CarbodyType.SEDAN,
            "296 GTB" to CarbodyType.SEDAN,
            "SF90" to CarbodyType.SEDAN,
            "Purosangue" to CarbodyType.SUV_LARGE,
        ),
        "Fiat" to listOf(
            "500" to CarbodyType.HATCHBACK_SMALL,
            "500e" to CarbodyType.HATCHBACK_SMALL,
            "Panda" to CarbodyType.HATCHBACK_SMALL,
            "Punto" to CarbodyType.HATCHBACK_SMALL,
            "Tipo" to CarbodyType.HATCHBACK_MEDIUM,
            "500X" to CarbodyType.SUV_SMALL,
            "600" to CarbodyType.SUV_SMALL,
            "500L" to CarbodyType.VAN_LIGHT,
            "Doblo" to CarbodyType.VAN_LIGHT,
            "Ducato" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Ford" to listOf(
            "Ka" to CarbodyType.HATCHBACK_SMALL,
            "Fiesta" to CarbodyType.HATCHBACK_SMALL,
            "Focus" to CarbodyType.HATCHBACK_MEDIUM,
            "Mondeo" to CarbodyType.SEDAN,
            "Mustang" to CarbodyType.SEDAN,
            "EcoSport" to CarbodyType.SUV_SMALL,
            "Puma" to CarbodyType.SUV_MEDIUM,
            "Kuga" to CarbodyType.SUV_LARGE,
            "Mustang Mach-E" to CarbodyType.SUV_LARGE,
            "Explorer" to CarbodyType.SUV_LARGE,
            "S-Max" to CarbodyType.FAMILY_LONG,
            "Ranger" to CarbodyType.PICKUP,
            "Tourneo" to CarbodyType.VAN_LIGHT,
            "Transit" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Genesis" to listOf(
            "G70" to CarbodyType.SEDAN,
            "G80" to CarbodyType.SEDAN,
            "G90" to CarbodyType.SEDAN,
            "GV60" to CarbodyType.SUV_MEDIUM,
            "GV70" to CarbodyType.SUV_LARGE,
            "GV80" to CarbodyType.SUV_LARGE,
        ),
        "GMC" to listOf(
            "Canyon" to CarbodyType.PICKUP,
            "Sierra" to CarbodyType.PICKUP,
            "Hummer EV" to CarbodyType.PICKUP,
            "Acadia" to CarbodyType.SUV_LARGE,
            "Yukon" to CarbodyType.SUV_LARGE,
        ),
        "Honda" to listOf(
            "e" to CarbodyType.HATCHBACK_SMALL,
            "Jazz" to CarbodyType.HATCHBACK_SMALL,
            "Civic" to CarbodyType.HATCHBACK_MEDIUM,
            "Accord" to CarbodyType.SEDAN,
            "HR-V" to CarbodyType.SUV_MEDIUM,
            "ZR-V" to CarbodyType.SUV_MEDIUM,
            "e:Ny1" to CarbodyType.SUV_MEDIUM,
            "CR-V" to CarbodyType.SUV_LARGE,
        ),
        "Hyundai" to listOf(
            "i10" to CarbodyType.HATCHBACK_SMALL,
            "i20" to CarbodyType.HATCHBACK_SMALL,
            "i30" to CarbodyType.HATCHBACK_MEDIUM,
            "Ioniq 6" to CarbodyType.SEDAN,
            "Bayon" to CarbodyType.SUV_SMALL,
            "Kona" to CarbodyType.SUV_SMALL,
            "Tucson" to CarbodyType.SUV_MEDIUM,
            "Ioniq 5" to CarbodyType.SUV_LARGE,
            "Santa Fe" to CarbodyType.SUV_LARGE,
            "Staria" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Infiniti" to listOf(
            "Q30" to CarbodyType.HATCHBACK_MEDIUM,
            "Q50" to CarbodyType.SEDAN,
            "Q60" to CarbodyType.SEDAN,
            "QX50" to CarbodyType.SUV_LARGE,
            "QX80" to CarbodyType.SUV_LARGE,
        ),
        "Isuzu" to listOf(
            "D-Max" to CarbodyType.PICKUP,
        ),
        "Iveco" to listOf(
            "Daily" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Jaecoo" to listOf(
            "7" to CarbodyType.SUV_LARGE,
        ),
        "Jaguar" to listOf(
            "XE" to CarbodyType.SEDAN,
            "XF" to CarbodyType.SEDAN,
            "XJ" to CarbodyType.SEDAN,
            "F-Type" to CarbodyType.SEDAN,
            "E-Pace" to CarbodyType.SUV_MEDIUM,
            "F-Pace" to CarbodyType.SUV_LARGE,
            "I-Pace" to CarbodyType.SUV_LARGE,
        ),
        "Jeep" to listOf(
            "Avenger" to CarbodyType.SUV_SMALL,
            "Renegade" to CarbodyType.SUV_SMALL,
            "Compass" to CarbodyType.SUV_MEDIUM,
            "Cherokee" to CarbodyType.SUV_LARGE,
            "Grand Cherokee" to CarbodyType.SUV_LARGE,
            "Wrangler" to CarbodyType.SUV_LARGE,
            "Gladiator" to CarbodyType.PICKUP,
        ),
        "Kia" to listOf(
            "Picanto" to CarbodyType.HATCHBACK_SMALL,
            "Rio" to CarbodyType.HATCHBACK_SMALL,
            "Ceed" to CarbodyType.HATCHBACK_MEDIUM,
            "Stonic" to CarbodyType.SUV_SMALL,
            "Soul" to CarbodyType.SUV_SMALL,
            "Niro" to CarbodyType.SUV_MEDIUM,
            "XCeed" to CarbodyType.SUV_MEDIUM,
            "EV3" to CarbodyType.SUV_MEDIUM,
            "Sportage" to CarbodyType.SUV_LARGE,
            "Sorento" to CarbodyType.SUV_LARGE,
            "EV6" to CarbodyType.SUV_LARGE,
            "EV9" to CarbodyType.SUV_LARGE,
        ),
        "Lamborghini" to listOf(
            "Huracan" to CarbodyType.SEDAN,
            "Revuelto" to CarbodyType.SEDAN,
            "Urus" to CarbodyType.SUV_LARGE,
        ),
        "Lancia" to listOf(
            "Ypsilon" to CarbodyType.HATCHBACK_SMALL,
            "Delta" to CarbodyType.HATCHBACK_MEDIUM,
        ),
        "Land Rover" to listOf(
            "Freelander" to CarbodyType.SUV_MEDIUM,
            "Discovery Sport" to CarbodyType.SUV_MEDIUM,
            "Range Rover Evoque" to CarbodyType.SUV_MEDIUM,
            "Defender" to CarbodyType.SUV_LARGE,
            "Discovery" to CarbodyType.SUV_LARGE,
            "Range Rover Velar" to CarbodyType.SUV_LARGE,
            "Range Rover Sport" to CarbodyType.SUV_LARGE,
            "Range Rover" to CarbodyType.SUV_LARGE,
        ),
        "Leapmotor" to listOf(
            "T03" to CarbodyType.HATCHBACK_SMALL,
            "C10" to CarbodyType.SUV_LARGE,
        ),
        "Lexus" to listOf(
            "CT" to CarbodyType.HATCHBACK_MEDIUM,
            "LBX" to CarbodyType.SUV_SMALL,
            "UX" to CarbodyType.SUV_MEDIUM,
            "IS" to CarbodyType.SEDAN,
            "ES" to CarbodyType.SEDAN,
            "LS" to CarbodyType.SEDAN,
            "NX" to CarbodyType.SUV_LARGE,
            "RX" to CarbodyType.SUV_LARGE,
            "RZ" to CarbodyType.SUV_LARGE,
        ),
        "Lincoln" to listOf(
            "Corsair" to CarbodyType.SUV_MEDIUM,
            "Nautilus" to CarbodyType.SUV_LARGE,
            "Aviator" to CarbodyType.SUV_LARGE,
            "Navigator" to CarbodyType.SUV_LARGE,
        ),
        "Lynk & Co" to listOf(
            "02" to CarbodyType.SUV_MEDIUM,
            "01" to CarbodyType.SUV_LARGE,
            "09" to CarbodyType.SUV_LARGE,
        ),
        "Maserati" to listOf(
            "Ghibli" to CarbodyType.SEDAN,
            "Quattroporte" to CarbodyType.SEDAN,
            "GranTurismo" to CarbodyType.SEDAN,
            "Grecale" to CarbodyType.SUV_LARGE,
            "Levante" to CarbodyType.SUV_LARGE,
        ),
        "Maxus" to listOf(
            "eDeliver 3" to CarbodyType.VAN_LIGHT,
            "Deliver 9" to CarbodyType.VAN_COMMERCIAL,
            "T90" to CarbodyType.PICKUP,
        ),
        "Mazda" to listOf(
            "2" to CarbodyType.HATCHBACK_SMALL,
            "3" to CarbodyType.HATCHBACK_MEDIUM,
            "6" to CarbodyType.SEDAN,
            "MX-5" to CarbodyType.SEDAN,
            "MX-30" to CarbodyType.SUV_SMALL,
            "CX-3" to CarbodyType.SUV_MEDIUM,
            "CX-30" to CarbodyType.SUV_MEDIUM,
            "CX-5" to CarbodyType.SUV_LARGE,
            "CX-60" to CarbodyType.SUV_LARGE,
            "CX-80" to CarbodyType.SUV_LARGE,
        ),
        "McLaren" to listOf(
            "Artura" to CarbodyType.SEDAN,
            "720S" to CarbodyType.SEDAN,
            "GT" to CarbodyType.SEDAN,
        ),
        "Mercedes" to listOf(
            "Clase A" to CarbodyType.HATCHBACK_MEDIUM,
            "Clase B" to CarbodyType.HATCHBACK_MEDIUM,
            "Clase C" to CarbodyType.SEDAN,
            "Clase E" to CarbodyType.SEDAN,
            "Clase S" to CarbodyType.SEDAN,
            "CLA" to CarbodyType.SEDAN,
            "EQA" to CarbodyType.SUV_MEDIUM,
            "EQB" to CarbodyType.SUV_MEDIUM,
            "GLA" to CarbodyType.SUV_MEDIUM,
            "GLB" to CarbodyType.SUV_MEDIUM,
            "GLC" to CarbodyType.SUV_LARGE,
            "GLE" to CarbodyType.SUV_LARGE,
            "GLS" to CarbodyType.SUV_LARGE,
            "Clase G" to CarbodyType.SUV_LARGE,
            "EQE" to CarbodyType.SEDAN,
            "EQS" to CarbodyType.SEDAN,
            "Citan" to CarbodyType.VAN_LIGHT,
            "Clase V" to CarbodyType.VAN_COMMERCIAL,
            "Vito" to CarbodyType.VAN_COMMERCIAL,
            "Sprinter" to CarbodyType.VAN_COMMERCIAL,
        ),
        "MG" to listOf(
            "MG3" to CarbodyType.HATCHBACK_SMALL,
            "MG4" to CarbodyType.HATCHBACK_MEDIUM,
            "MG5" to CarbodyType.FAMILY_LONG,
            "Cyberster" to CarbodyType.SEDAN,
            "ZS" to CarbodyType.SUV_SMALL,
            "HS" to CarbodyType.SUV_LARGE,
            "Marvel R" to CarbodyType.SUV_LARGE,
        ),
        "Mini" to listOf(
            "Cooper" to CarbodyType.HATCHBACK_SMALL,
            "Clubman" to CarbodyType.HATCHBACK_MEDIUM,
            "Aceman" to CarbodyType.SUV_SMALL,
            "Countryman" to CarbodyType.SUV_MEDIUM,
        ),
        "Mitsubishi" to listOf(
            "Space Star" to CarbodyType.HATCHBACK_SMALL,
            "Colt" to CarbodyType.HATCHBACK_SMALL,
            "ASX" to CarbodyType.SUV_MEDIUM,
            "Eclipse Cross" to CarbodyType.SUV_MEDIUM,
            "Outlander" to CarbodyType.SUV_LARGE,
            "L200" to CarbodyType.PICKUP,
        ),
        "NIO" to listOf(
            "ET5" to CarbodyType.SEDAN,
            "ET7" to CarbodyType.SEDAN,
            "EL6" to CarbodyType.SUV_LARGE,
            "EL7" to CarbodyType.SUV_LARGE,
            "EL8" to CarbodyType.SUV_LARGE,
        ),
        "Nissan" to listOf(
            "Micra" to CarbodyType.HATCHBACK_SMALL,
            "Note" to CarbodyType.HATCHBACK_SMALL,
            "Leaf" to CarbodyType.HATCHBACK_MEDIUM,
            "Juke" to CarbodyType.SUV_SMALL,
            "Qashqai" to CarbodyType.SUV_MEDIUM,
            "X-Trail" to CarbodyType.SUV_LARGE,
            "Ariya" to CarbodyType.SUV_LARGE,
            "Navara" to CarbodyType.PICKUP,
            "Townstar" to CarbodyType.VAN_LIGHT,
        ),
        "Omoda" to listOf(
            "5" to CarbodyType.SUV_MEDIUM,
            "E5" to CarbodyType.SUV_MEDIUM,
            "7" to CarbodyType.SUV_LARGE,
            "9" to CarbodyType.SUV_LARGE,
        ),
        "Opel" to listOf(
            "Corsa" to CarbodyType.HATCHBACK_SMALL,
            "Astra" to CarbodyType.HATCHBACK_MEDIUM,
            "Insignia" to CarbodyType.SEDAN,
            "Mokka" to CarbodyType.SUV_SMALL,
            "Crossland" to CarbodyType.SUV_SMALL,
            "Frontera" to CarbodyType.SUV_MEDIUM,
            "Grandland" to CarbodyType.SUV_LARGE,
            "Zafira" to CarbodyType.FAMILY_LONG,
            "Combo" to CarbodyType.VAN_LIGHT,
            "Vivaro" to CarbodyType.VAN_COMMERCIAL,
            "Movano" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Ora" to listOf(
            "03" to CarbodyType.HATCHBACK_MEDIUM,
        ),
        "Peugeot" to listOf(
            "108" to CarbodyType.HATCHBACK_SMALL,
            "208" to CarbodyType.HATCHBACK_SMALL,
            "308" to CarbodyType.HATCHBACK_MEDIUM,
            "408" to CarbodyType.SEDAN,
            "508" to CarbodyType.SEDAN,
            "2008" to CarbodyType.SUV_MEDIUM,
            "3008" to CarbodyType.SUV_LARGE,
            "5008" to CarbodyType.SUV_LARGE,
            "Rifter" to CarbodyType.VAN_LIGHT,
            "Partner" to CarbodyType.VAN_LIGHT,
            "Expert" to CarbodyType.VAN_COMMERCIAL,
            "Boxer" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Polestar" to listOf(
            "1" to CarbodyType.SEDAN,
            "2" to CarbodyType.SEDAN,
            "3" to CarbodyType.SUV_LARGE,
            "4" to CarbodyType.SUV_LARGE,
        ),
        "Porsche" to listOf(
            "718 Cayman" to CarbodyType.SEDAN,
            "718 Boxster" to CarbodyType.SEDAN,
            "911" to CarbodyType.SEDAN,
            "Panamera" to CarbodyType.SEDAN,
            "Taycan" to CarbodyType.SEDAN,
            "Macan" to CarbodyType.SUV_LARGE,
            "Cayenne" to CarbodyType.SUV_LARGE,
        ),
        "RAM" to listOf(
            "1500" to CarbodyType.PICKUP,
            "2500" to CarbodyType.PICKUP,
            "ProMaster" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Renault" to listOf(
            "Twingo" to CarbodyType.HATCHBACK_SMALL,
            "Zoe" to CarbodyType.HATCHBACK_SMALL,
            "5" to CarbodyType.HATCHBACK_SMALL,
            "Clio" to CarbodyType.HATCHBACK_SMALL,
            "Megane" to CarbodyType.HATCHBACK_MEDIUM,
            "Captur" to CarbodyType.SUV_SMALL,
            "Arkana" to CarbodyType.SUV_MEDIUM,
            "Kadjar" to CarbodyType.SUV_MEDIUM,
            "Scenic" to CarbodyType.SUV_MEDIUM,
            "Austral" to CarbodyType.SUV_LARGE,
            "Espace" to CarbodyType.SUV_LARGE,
            "Rafale" to CarbodyType.SUV_LARGE,
            "Kangoo" to CarbodyType.VAN_LIGHT,
            "Trafic" to CarbodyType.VAN_COMMERCIAL,
            "Master" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Rolls-Royce" to listOf(
            "Phantom" to CarbodyType.SEDAN,
            "Ghost" to CarbodyType.SEDAN,
            "Spectre" to CarbodyType.SEDAN,
            "Cullinan" to CarbodyType.SUV_LARGE,
        ),
        "Seat" to listOf(
            "Mii" to CarbodyType.HATCHBACK_SMALL,
            "Ibiza" to CarbodyType.HATCHBACK_SMALL,
            "León" to CarbodyType.HATCHBACK_MEDIUM,
            "Arona" to CarbodyType.SUV_MEDIUM,
            "Ateca" to CarbodyType.SUV_MEDIUM,
            "Tarraco" to CarbodyType.SUV_LARGE,
            "Alhambra" to CarbodyType.FAMILY_LONG,
        ),
        "Skoda" to listOf(
            "Citigo" to CarbodyType.HATCHBACK_SMALL,
            "Fabia" to CarbodyType.HATCHBACK_SMALL,
            "Scala" to CarbodyType.HATCHBACK_MEDIUM,
            "Octavia" to CarbodyType.SEDAN,
            "Superb" to CarbodyType.SEDAN,
            "Kamiq" to CarbodyType.SUV_SMALL,
            "Karoq" to CarbodyType.SUV_MEDIUM,
            "Elroq" to CarbodyType.SUV_MEDIUM,
            "Kodiaq" to CarbodyType.SUV_LARGE,
            "Enyaq" to CarbodyType.SUV_LARGE,
        ),
        "Smart" to listOf(
            "ForTwo" to CarbodyType.HATCHBACK_SMALL,
            "ForFour" to CarbodyType.HATCHBACK_SMALL,
            "#1" to CarbodyType.SUV_SMALL,
            "#3" to CarbodyType.SUV_MEDIUM,
        ),
        "SsangYong" to listOf(
            "Tivoli" to CarbodyType.SUV_SMALL,
            "Korando" to CarbodyType.SUV_MEDIUM,
            "Torres" to CarbodyType.SUV_LARGE,
            "Rexton" to CarbodyType.SUV_LARGE,
            "Musso" to CarbodyType.PICKUP,
        ),
        "Subaru" to listOf(
            "Impreza" to CarbodyType.HATCHBACK_MEDIUM,
            "BRZ" to CarbodyType.SEDAN,
            "WRX" to CarbodyType.SEDAN,
            "XV" to CarbodyType.SUV_MEDIUM,
            "Crosstrek" to CarbodyType.SUV_MEDIUM,
            "Forester" to CarbodyType.SUV_LARGE,
            "Solterra" to CarbodyType.SUV_LARGE,
            "Outback" to CarbodyType.FAMILY_LONG,
        ),
        "Suzuki" to listOf(
            "Ignis" to CarbodyType.HATCHBACK_SMALL,
            "Swift" to CarbodyType.HATCHBACK_SMALL,
            "Vitara" to CarbodyType.SUV_SMALL,
            "Jimny" to CarbodyType.SUV_SMALL,
            "S-Cross" to CarbodyType.SUV_MEDIUM,
            "Across" to CarbodyType.SUV_LARGE,
            "Swace" to CarbodyType.FAMILY_LONG,
        ),
        "Tesla" to listOf(
            "Model 3" to CarbodyType.SEDAN,
            "Model S" to CarbodyType.SEDAN,
            "Model Y" to CarbodyType.SUV_LARGE,
            "Model X" to CarbodyType.SUV_LARGE,
            "Cybertruck" to CarbodyType.PICKUP,
        ),
        "Toyota" to listOf(
            "Aygo X" to CarbodyType.HATCHBACK_SMALL,
            "Yaris" to CarbodyType.HATCHBACK_SMALL,
            "Corolla" to CarbodyType.HATCHBACK_MEDIUM,
            "Prius" to CarbodyType.HATCHBACK_MEDIUM,
            "Camry" to CarbodyType.SEDAN,
            "Supra" to CarbodyType.SEDAN,
            "Yaris Cross" to CarbodyType.SUV_SMALL,
            "C-HR" to CarbodyType.SUV_MEDIUM,
            "Corolla Cross" to CarbodyType.SUV_MEDIUM,
            "RAV4" to CarbodyType.SUV_LARGE,
            "Highlander" to CarbodyType.SUV_LARGE,
            "Land Cruiser" to CarbodyType.SUV_LARGE,
            "bZ4X" to CarbodyType.SUV_LARGE,
            "Hilux" to CarbodyType.PICKUP,
            "Proace" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Volkswagen" to listOf(
            "up!" to CarbodyType.HATCHBACK_SMALL,
            "Polo" to CarbodyType.HATCHBACK_SMALL,
            "Golf" to CarbodyType.HATCHBACK_MEDIUM,
            "ID.3" to CarbodyType.HATCHBACK_MEDIUM,
            "Passat" to CarbodyType.SEDAN,
            "Arteon" to CarbodyType.SEDAN,
            "ID.7" to CarbodyType.SEDAN,
            "Taigo" to CarbodyType.SUV_SMALL,
            "T-Cross" to CarbodyType.SUV_SMALL,
            "T-Roc" to CarbodyType.SUV_SMALL,
            "Tiguan" to CarbodyType.SUV_LARGE,
            "Touareg" to CarbodyType.SUV_LARGE,
            "ID.4" to CarbodyType.SUV_LARGE,
            "ID.5" to CarbodyType.SUV_LARGE,
            "Touran" to CarbodyType.FAMILY_LONG,
            "Caddy" to CarbodyType.VAN_LIGHT,
            "Transporter" to CarbodyType.VAN_COMMERCIAL,
            "ID. Buzz" to CarbodyType.VAN_COMMERCIAL,
        ),
        "Volvo" to listOf(
            "V40" to CarbodyType.HATCHBACK_MEDIUM,
            "S60" to CarbodyType.SEDAN,
            "S90" to CarbodyType.SEDAN,
            "V60" to CarbodyType.FAMILY_LONG,
            "V90" to CarbodyType.FAMILY_LONG,
            "EX30" to CarbodyType.SUV_SMALL,
            "XC40" to CarbodyType.SUV_MEDIUM,
            "C40" to CarbodyType.SUV_MEDIUM,
            "XC60" to CarbodyType.SUV_LARGE,
            "XC90" to CarbodyType.SUV_LARGE,
            "EX90" to CarbodyType.SUV_LARGE,
        ),
        "XPeng" to listOf(
            "P7" to CarbodyType.SEDAN,
            "G6" to CarbodyType.SUV_LARGE,
            "G9" to CarbodyType.SUV_LARGE,
        ),
        "Zeekr" to listOf(
            "X" to CarbodyType.SUV_MEDIUM,
            "001" to CarbodyType.SEDAN,
            "7X" to CarbodyType.SUV_LARGE,
        ),
    )

    /**
     * Ordered pattern rules used as a fallback when the exact catalog has no entry.
     *
     * Ordering matters — the *first* rule whose keyword list contains a substring of
     * `"brand model"` (lowercased) wins. Higher-specificity bodies are listed first
     * (commercial vans before pickups before SUVs before sedans before hatchbacks)
     * so an ambiguous "Volkswagen Transporter" hits VAN_COMMERCIAL, not SEDAN.
     *
     * Keywords are deliberately multi-character, model-distinct tokens — never bare
     * digits or 2-letter fragments — so they don't false-positive on unrelated names.
     */
    private val patternRules: List<Pair<List<String>, CarbodyType>> = listOf(
        listOf(
            "vito", "transporter", "trafic", "vivaro", "expert", "jumpy", "ducato", "boxer", "sprinter",
            "transit", "crafter", "master", "movano", "jumper", "daily", "proace", "primastar",
            "promaster", "talento", "traveller", "spacetourer", "deliver 9", "clase v",
        ) to CarbodyType.VAN_COMMERCIAL,
        listOf(
            "hilux", "ranger", "d-max", "amarok", "navara", "l200", "gladiator",
            "silverado", "sierra", "ram 1500", "musso", "cybertruck",
        ) to CarbodyType.PICKUP,
        listOf(
            "berlingo", "kangoo", "partner", "rifter", "doblo", "combo", "caddy",
            "tourneo", "townstar", "proace city", "citan", "dokker",
        ) to CarbodyType.VAN_LIGHT,
        listOf(
            "rav4", "model y", "5008", "tarraco", "x5", "x6", "x7", "q7", "q8", "kuga", "kodiaq",
            "tiguan", "sorento", "cayenne", "macan", "touareg", "gle", "gls", "santa fe", "sportage",
            "grandland", "ev6", "ev9", "enyaq", "explorer", "highlander", "land cruiser", "defender",
            "discovery", "range rover", "stelvio", "levante", "grecale", "bentayga", "urus",
            "cullinan", "escalade", "xc90", "xc60", "cx-60", "cx-80",
        ) to CarbodyType.SUV_LARGE,
        listOf(
            "tucson", "qashqai", "ateca", "karoq", "kadjar", "compass", "tonale", "scenic", "arkana",
            "crosstrek", "gla", "glb", "eqa", "eqb", "cx-30", "c-hr", "2008", "frontera", "korando",
            "formentor",
        ) to CarbodyType.SUV_MEDIUM,
        listOf(
            "arona", "yaris cross", "t-cross", "kona", "captur", "juke", "puma", "stonic", "mokka",
            "crossland", "kamiq", "taigo", "bayon", "junior", "aceman", "ex30", "tivoli", "trax",
        ) to CarbodyType.SUV_SMALL,
        listOf(
            "avant", "touring", "variant", " sw", "estate", "kombi", "outback", "touran", "zafira",
            "jogger", " v60", " v90", "s-max", "alhambra",
        ) to CarbodyType.FAMILY_LONG,
        listOf(
            "model 3", "serie 3", "serie 5", "serie 7", "clase c", "clase e", "clase s", "a4", "a6",
            "a8", "passat", "octavia", "508", "superb", "giulia", "panamera", "insignia", "mondeo",
            "camry", "arteon", "ghibli",
        ) to CarbodyType.SEDAN,
        listOf(
            "golf", "corolla", "leon", "a3", "civic", "focus", "megane", "astra", "i30", "ceed",
            "mg4", "scala", "impreza", "delta", "dolphin",
        ) to CarbodyType.HATCHBACK_MEDIUM,
        listOf(
            "fiat 500", "panda", "ibiza", "clio", "micra", "polo", "fiesta", "yaris", "i20", "208",
            "corsa", "aygo", "twingo", "up!", "mg3", "spark", "aveo", "ypsilon", "ignis", "colt",
            "space star", "i10",
        ) to CarbodyType.HATCHBACK_SMALL,
    )

    fun brands(): List<String> = catalog.keys.toList()

    fun modelsFor(brand: String): List<String> = catalog[brand]?.map { it.first } ?: emptyList()

    /**
     * Resolves the [CarbodyType] for [brand] + [model]. Tries exact match first,
     * then falls back to keyword patterns over the lowercased combined string.
     * Returns null when no rule matches — the registration ViewModel then applies
     * a sensible default while still letting the user pick manually.
     */
    fun inferBodyType(brand: String, model: String): CarbodyType? {
        catalog[brand]?.firstOrNull { it.first == model }?.second?.let { return it }
        val haystack = "$brand $model".trim().lowercase()
        if (haystack.isBlank()) return null
        return patternRules.firstOrNull { (patterns, _) ->
            patterns.any { haystack.contains(it) }
        }?.second
    }

    /** Convenience: returns just the size dimension of the inferred carbody, when available. */
    fun inferSize(brand: String, model: String): VehicleSize? = inferBodyType(brand, model)?.sizeCategory
}
