package io.apptolast.paparcar.domain.model

/**
 * POI category. UI renders each via the dedicated Material Rounded icon
 * (`PlaceCategory.icon` in `ui/icons/PaparcarIcons.kt`) — never an emoji.
 */
enum class PlaceCategory {
    FUEL,
    SUPERMARKET,
    MALL,
    RESTAURANT,
    CAFE,
    PHARMACY,
    HOSPITAL,
    PARKING,
    BANK,
    HOTEL,
    SCHOOL,
    GYM,
    OTHER,
}
