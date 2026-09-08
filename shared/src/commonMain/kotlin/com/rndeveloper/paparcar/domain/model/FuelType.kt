package com.rndeveloper.paparcar.domain.model

/**
 * What the vehicle burns (or charges). Feeds the cost/CO₂ estimates of the stats pipeline and
 * NOTHING else — deliberately not a detection signal, not a size, not an identity colour.
 * [VEH-FUEL-001]
 *
 * Only [VehicleType.burnsFuel] types carry one; scooters and bikes persist null and never see the
 * selector. Not validated against brand/model on purpose: there is no reliable catalogue, and the
 * same model ships with several powertrains (a Clio E-Tech is a hybrid the regex would call
 * gasoline).
 */
enum class FuelType {
    GASOLINE,
    DIESEL,
    LPG_CNG,
    HYBRID,
    PLUG_IN_HYBRID,
    ELECTRIC,
    ;

    /** Burns litres (its consumption default and override are l/100 km). */
    val usesLiters: Boolean
        get() = when (this) {
            GASOLINE, DIESEL, LPG_CNG, HYBRID, PLUG_IN_HYBRID -> true
            ELECTRIC -> false
        }

    /** Draws from a battery (kWh/100 km). A PHEV answers BOTH questions true — it carries two
     *  consumption figures, one per energy. */
    val usesElectricity: Boolean
        get() = when (this) {
            PLUG_IN_HYBRID, ELECTRIC -> true
            GASOLINE, DIESEL, LPG_CNG, HYBRID -> false
        }

    /** Carries a SECOND consumption figure (kWh) besides its litres — the plug-in hybrid shape.
     *  Asked as a question so call sites never spell `== PLUG_IN_HYBRID`. */
    val hasSecondaryElectric: Boolean get() = usesLiters && usesElectricity
}
