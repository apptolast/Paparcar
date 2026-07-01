package io.apptolast.paparcar.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.PlaceCategory
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.domain.model.VehicleType

/**
 * Custom icon set for Paparcar.
 *
 * Single namespace replacing the loose emoji strings that used to live in
 * domain enums (`PlaceCategory`) and selector UIs (`VehicleSizeSelector`).
 * Every call site that needs a Paparcar icon pulls from here so the visual
 * language stays consistent across the app.
 *
 * Two sources of truth:
 *
 *  - **Material wrappers** — for categories where the Material outlined
 *    catalogue already provides the canonical glyph (gas pump, pharmacy
 *    cross, etc.). We re-export under our namespace so call sites read
 *    [PaparcarIcons.Pharmacy] instead of `Icons.Rounded.LocalPharmacy` and
 *    we can swap the source later without touching call sites.
 *  - **Custom canvas-declared `ImageVector`s** — for in-house marks (parking
 *    bay, brand pin) and the 4-wheeled vehicle sizes Material doesn't
 *    differentiate. Each is built with [ImageVector.Builder]; the path
 *    commands (moveTo / lineTo / curveTo) read like Compose Canvas drawing.
 *
 * Stylistic conventions for the custom set:
 *  - 24 × 24 dp viewport, matching Material outlined defaults.
 *  - Filled silhouettes (no stroke) with `EvenOdd` fill for inner cut-outs.
 *  - Black fill on the path; the host `Icon` composable retints to
 *    `LocalContentColor` (or an explicit `tint =`) at render time.
 */
object PaparcarIcons {

    // ── Place categories (Material wrappers) ─────────────────────────────────
    val Fuel: ImageVector get() = Icons.Rounded.LocalGasStation
    val Supermarket: ImageVector get() = Icons.Rounded.ShoppingCart
    val Mall: ImageVector get() = Icons.Rounded.LocalMall
    val Restaurant: ImageVector get() = Icons.Rounded.Restaurant
    val Cafe: ImageVector get() = Icons.Rounded.LocalCafe
    val Pharmacy: ImageVector get() = Icons.Rounded.LocalPharmacy
    val Hospital: ImageVector get() = Icons.Rounded.LocalHospital
    val ParkingPlace: ImageVector get() = Icons.Rounded.LocalParking
    val Bank: ImageVector get() = Icons.Rounded.AccountBalance
    val Hotel: ImageVector get() = Icons.Rounded.Hotel
    val School: ImageVector get() = Icons.Rounded.School
    val Gym: ImageVector get() = Icons.Rounded.FitnessCenter
    val PlaceGeneric: ImageVector get() = Icons.Rounded.Place

    // ── Vehicle types (high-level taxonomy, independent of size) ─────────────
    // Used in registration/edit to pick the user's vehicle category. Drives
    // detection strategy: SCOOTER / BIKE never enter the Coordinator algorithm.
    val VehicleCar: ImageVector get() = Icons.Rounded.DirectionsCar
    val VehicleMotorcycle: ImageVector get() = Icons.Rounded.TwoWheeler
    val VehicleScooter: ImageVector get() = Icons.Rounded.ElectricScooter
    val VehicleBike: ImageVector get() = Icons.AutoMirrored.Rounded.DirectionsBike


    /**
     * Top-down view of an empty parking bay: a portrait rounded rectangle with
     * two vertical lane-marking lines inside.
     *
     * Used both in the spot list rows and as the map-marker icon for community
     * spots, distinguishing them from the user's own car ([DirectionsCar]).
     *
     * Viewport: 24 × 24 dp
     * Outer rect: 5.5,2 → 18.5,22  (rx = 3)
     * Lane lines: x = 9 and x = 15, from y = 6.5 to y = 17.5
     */
    val ParkingBay: ImageVector by lazy {
        ImageVector.Builder(
            name = "ParkingBay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {

            // ── Outer bay outline ─────────────────────────────────────────────
            // Portrait rounded rectangle — the boundary of the parking space.
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            ) {
                moveTo(7f, 2f)
                lineTo(17f, 2f)
                curveTo(18.66f, 2f, 20f, 3.34f, 20f, 5f)
                lineTo(20f, 19f)
                curveTo(20f, 20.66f, 18.66f, 22f, 17f, 22f)
                lineTo(7f, 22f)
                curveTo(5.34f, 22f, 4f, 20.66f, 4f, 19f)
                lineTo(4f, 5f)
                curveTo(4f, 3.34f, 5.34f, 2f, 7f, 2f)
                close()
            }

            // ── Left lane marking ─────────────────────────────────────────────
            // Vertical line at ~1/3 of the bay width from the left wall.
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(9f, 6.5f)
                lineTo(9f, 17.5f)
            }

            // ── Right lane marking ────────────────────────────────────────────
            // Vertical line at ~2/3 of the bay width from the left wall.
            path(
                fill = SolidColor(Color.Transparent),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
            ) {
                moveTo(15f, 6.5f)
                lineTo(15f, 17.5f)
            }

        }.build()
    }

    /**
     * Brand pin — map-pin teardrop with a "P" cut-out (evenOdd). Mirrors the
     * launcher foreground geometry so the app icon and the in-map markers are
     * visually the same mark. Single fill, tint with the desired status colour
     * via Icon(imageVector = …, tint = …).
     *
     * Viewport : 24 × 24 dp.
     * Pin head : center (12,9), tip (12,22).
     * P bar    : x 10–11, y 5–12.
     * P bowl   : outer arc r=2 peaking at (13,7); inner counter r=1 returns the
     *            bowl hole to filled so the P keeps its shape via XOR winding.
     */
    val SpotPin: ImageVector by lazy {
        ImageVector.Builder(
            name = "SpotPin",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.Black),
                pathFillType = PathFillType.EvenOdd,
            ) {
                // ── Pin teardrop ──────────────────────────────────────────────
                moveTo(12f, 2f)
                curveTo(8.13f, 2f, 5f, 5.13f, 5f, 9f)
                curveTo(5f, 14.25f, 12f, 22f, 12f, 22f)
                curveTo(12f, 22f, 19f, 14.25f, 19f, 9f)
                curveTo(19f, 5.13f, 15.87f, 2f, 12f, 2f)
                close()

                // ── Outer P (punches a P-shaped hole through the pin) ─────────
                moveTo(10f, 5f)
                lineTo(11f, 5f)
                arcTo(2f, 2f, 0f, false, true, 11f, 9f)
                lineTo(11f, 12f)
                lineTo(10f, 12f)
                close()

                // ── Inner counter (returns the bowl hole to filled) ───────────
                moveTo(11f, 6f)
                arcTo(1f, 1f, 0f, false, true, 11f, 8f)
                close()
            }
        }.build()
    }

    /**
     * The Paparcar "P" — the filled Fredoka glyph (`glyph-p`), the same mark drawn on the spot
     * pucks (`SPOT_P_PATH` in PaparcarMapMarkers). Exported as an [ImageVector] so every place that
     * shows the spot "P" (spot list item, peek reliability badge, report centre-pin) uses one
     * identical glyph. The host `Icon` tints the fill; viewport 48×48. [MAP-ICONS-V2]
     */
    val SpotParkingP: ImageVector by lazy {
        ImageVector.Builder(
            name = "SpotParkingP",
            defaultWidth = 48.dp,
            defaultHeight = 48.dp,
            viewportWidth = 48f,
            viewportHeight = 48f,
        ).addPath(
            // Filled Fredoka "P" with a counter (nonzero winding gives the hole) — the standalone
            // `glyph-p`, the same glyph drawn on the spot pucks. Icon tints the fill.
            pathData = PathParser().parsePathString(GLYPH_P_PATH).toNodes(),
            fill = SolidColor(Color.Black),
        ).build()
    }

    private const val GLYPH_P_PATH =
        "M11.46 11.08 a6.08 6.08 0 0 1 12.16 0 L23.62 36.92 a6.08 6.08 0 0 1 -12.16 0 Z " +
            "M11.46 17.16 a14.06 12.16 0 1 1 28.12 0 a14.06 12.16 0 1 1 -28.12 0 Z " +
            "M19.33 17.16 a6.19 5.35 0 1 0 12.37 0 a6.19 5.35 0 1 0 -12.37 0 Z"
}

// ─── Domain → icon mappers ────────────────────────────────────────────────────
// Live in the UI layer (not on the enum itself) so the domain models stay
// Compose-free. Importing PaparcarIcons gives you the .icon extension on both
// VehicleSize and PlaceCategory for drop-in use at any Icon(...) call site.

/**
 * Fallback glyph for a [VehicleSize]. Cars now render through the isometric
 * carbody pictograms (`vehicleIconPainter`/`VehicleIcon`); this extension only
 * feeds those pictograms' last-resort branch, reachable solely for
 * [VehicleSize.MOTORCYCLE] (which has no carbody and no iso pictogram → the
 * Material two-wheeler glyph). Every four-wheeled tier resolves a carbody first,
 * so its `VehicleCar` value is never actually reached.
 */
val VehicleSize.icon: ImageVector
    get() = when (this) {
        VehicleSize.MOTORCYCLE -> PaparcarIcons.VehicleMotorcycle
        else -> PaparcarIcons.VehicleCar
    }

/**
 * Paparcar icon for each [VehicleType]. Used by [VehicleTypeSelector] and any
 * call site that needs a quick glyph for the user's vehicle category.
 */
val VehicleType.icon: ImageVector
    get() = when (this) {
        VehicleType.CAR        -> PaparcarIcons.VehicleCar
        VehicleType.MOTORCYCLE -> PaparcarIcons.VehicleMotorcycle
        VehicleType.SCOOTER    -> PaparcarIcons.VehicleScooter
        VehicleType.BIKE       -> PaparcarIcons.VehicleBike
    }

/**
 * Icon counterpart to the legacy `PlaceCategory.emoji` string field. The
 * emoji field stays for now (some string-formatted display lines still embed
 * it inline); UI sites that render a dedicated icon slot can switch to this
 * extension and drop the inline emoji from their Text body.
 */
val PlaceCategory.icon: ImageVector
    get() = when (this) {
        PlaceCategory.FUEL        -> PaparcarIcons.Fuel
        PlaceCategory.SUPERMARKET -> PaparcarIcons.Supermarket
        PlaceCategory.MALL        -> PaparcarIcons.Mall
        PlaceCategory.RESTAURANT  -> PaparcarIcons.Restaurant
        PlaceCategory.CAFE        -> PaparcarIcons.Cafe
        PlaceCategory.PHARMACY    -> PaparcarIcons.Pharmacy
        PlaceCategory.HOSPITAL    -> PaparcarIcons.Hospital
        PlaceCategory.PARKING     -> PaparcarIcons.ParkingPlace
        PlaceCategory.BANK        -> PaparcarIcons.Bank
        PlaceCategory.HOTEL       -> PaparcarIcons.Hotel
        PlaceCategory.SCHOOL      -> PaparcarIcons.School
        PlaceCategory.GYM         -> PaparcarIcons.Gym
        PlaceCategory.OTHER       -> PaparcarIcons.PlaceGeneric
    }
