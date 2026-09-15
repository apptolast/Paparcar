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

    /**
     * Which product this car actually buys at the pump.
     * [FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001]
     *
     * **A hybrid does not fill up with "hybrid": it fills up with petrol, and so does a plug-in
     * hybrid.** No feed on earth emits a HYBRID price — Spain, France, Germany and OSM all quote the
     * three products a forecourt sells — so a powertrain used as a lookup key answers null, silently,
     * everywhere. That is one bug with three faces, and this is the one place it gets answered:
     * the cost tile already knew it privately, the CO₂ table knew it privately, and the station
     * lane knew it nowhere, which is why every hybrid driver saw a priceless list and a priceless
     * map beside a peek quoting both pumps.
     *
     * ⛔ It is NOT the key of [com.rndeveloper.paparcar.domain.fuel.FuelStation.prices], which stays
     * indexed by product. This is the CAR→PRODUCT conversion, applied where a car becomes a lookup,
     * and it is idempotent by construction. A surface that itemises the pumps a station spoke about
     * must keep asking with products.
     *
     * An [ELECTRIC] car answers ELECTRIC: it buys nothing at a pump, and saying "petrol" to make the
     * lookup succeed would put a litre price on a car with no tank.
     *
     * A `when` and not a map, so a new [FuelType] cannot be added without answering this.
     */
    val pumpProduct: FuelType
        get() = when (this) {
            HYBRID, PLUG_IN_HYBRID -> GASOLINE
            GASOLINE, DIESEL, LPG_CNG, ELECTRIC -> this
        }
}
