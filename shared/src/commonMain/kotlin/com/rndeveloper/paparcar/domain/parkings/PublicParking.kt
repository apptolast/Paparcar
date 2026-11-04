package com.rndeveloper.paparcar.domain.parkings

/**
 * What kind of facility this is — and the reason it is a first-class field rather than a detail.
 * [PARKINGS-ON-THE-MAP-001]
 *
 * OSM names only a **minority** of its car parks: measured on 09-09-2026 over five cities,
 * `name` sits at 72% in central Madrid and central Paris, 31% in Málaga, 14% in Berlin-Mitte and
 * **2,6% in Cuenca**. A fuel station is titled by its brand because OSM names 100% of what it holds;
 * a car park cannot be, so the kind is what a row says when nobody wrote a name: "underground car
 * park, 300 m" is a usable sentence, an empty title is not.
 */
enum class PublicParkingKind {
    UNDERGROUND,
    MULTI_STOREY,
    SURFACE,
    GARAGE_BOXES,

    /** Untagged. The majority outside big cities — 59 of 90 elements measured in Cuenca. */
    UNKNOWN,
    ;

    /**
     * True for the facilities you DRIVE INTO — the ones whose existence is the point of this layer.
     * A surface lot may be a real car park or an untagged patch of gravel, which is why it does not
     * qualify on its own (see `PublicParkingScope`).
     */
    val isOffStreet: Boolean
        get() = this == UNDERGROUND || this == MULTI_STOREY || this == GARAGE_BOXES
}

/**
 * Whether it charges — with "nobody has said" as a first-class answer, exactly like
 * [com.rndeveloper.paparcar.domain.fuel.FuelAvailability]. [PARKINGS-ON-THE-MAP-001]
 *
 * ⛔ Absence is NOT free. `fee` is tagged on 19% to 29% of the elements measured, and rendering the
 * silent 71% as "free" would send a driver into a barrier they were told not to expect. [UNKNOWN] is
 * therefore not rendered at all: the row says nothing about price, which is the truth.
 */
enum class FeeInfo { PAID, FREE, UNKNOWN }

/**
 * A car park a third party runs and anyone may drive into. [PARKINGS-ON-THE-MAP-001]
 *
 * ⛔ Not a `Spot` and not a `UserParking`: a **plaza** is what the community frees, an
 * **aparcamiento** is your own session, and this is a **public parking** — a facility.
 * [COPY-SPOT-IS-NOT-A-PARKING-001]
 *
 * Every optional field here is optional *because it is missing from most of the world's data*, not
 * as a convenience. Measured coverage, same five cities: `capacity` 1-16%, `maxheight` 0-7%,
 * `opening_hours` 0-3%. So each one is stated only where it exists and is silent everywhere else —
 * an invented capacity is worse than no capacity.
 */
data class PublicParking(
    /** Stable id WITHIN its source: an OSM element id today, a city feed's id under F6b. */
    val id: String,
    val name: String?,
    val operator: String?,
    val kind: PublicParkingKind,
    val latitude: Double,
    val longitude: Double,
    /** Declared number of spaces. Null is the norm, and it is never rendered as a zero. */
    val capacity: Int? = null,
    val fee: FeeInfo = FeeInfo.UNKNOWN,
    /** Clearance in metres, where declared — the one thing a van driver cannot guess from a map. */
    val maxHeightMeters: Double? = null,
    /** Opening hours as the source words them. Not parsed: a wrong parse closes an open car park. */
    val openingHours: String? = null,
    /**
     * Cuántas plazas quedan AHORA, donde alguna ciudad lo publique.
     * [PARKINGS-A-LIVE-COUNT-OUTRANKS-A-TARIFF-001]
     *
     * ⛔ **Null es la norma, como todo lo de arriba**: hoy la publican **24 parkings de Madrid** de
     * los 70.062 de España. No es un campo que falte por rellenar: es un campo que la mayor parte
     * del mundo no tiene, y por eso la escalera de [ParkingHeadline] tiene que saber callarse.
     *
     * ⛔ Y **no se persiste en Room**: la caña de disco guarda 30 días y un recuento de plazas libres
     * de hace tres semanas es exactamente la mentira que esto no puede contar. La base se recuerda;
     * la medida, nunca.
     */
    val availability: ParkingAvailability? = null,
) {
    /**
     * The name to show, or null when nobody wrote one — in which case the UI titles the row by
     * [kind]. Resolving that fallback here would mean putting a translatable string in domain.
     */
    val displayName: String? get() = name ?: operator
}
