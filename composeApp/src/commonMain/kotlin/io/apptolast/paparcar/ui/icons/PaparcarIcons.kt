package io.apptolast.paparcar.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.automirrored.outlined.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ElectricScooter
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.ElectricScooter
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Hotel
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.LocalMall
import androidx.compose.material.icons.outlined.LocalParking
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.TwoWheeler
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
import androidx.compose.ui.graphics.vector.PathBuilder
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
 *    [PaparcarIcons.Pharmacy] instead of `Icons.Outlined.LocalPharmacy` and
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

    // ── Vehicle sizes ────────────────────────────────────────────────────────
    // All five vehicle sizes are custom vectors matching the casa-rodante.svg visual
    // language (flat filled silhouette, bezier wheel ovals, EvenOdd window cut-outs).
    val VehicleMoto: ImageVector by lazy { vehicleMotoVector() }
    val VehicleSmall: ImageVector by lazy { vehicleSmallVector() }
    val VehicleMedium: ImageVector by lazy { vehicleMediumVector() }
    val VehicleLarge: ImageVector by lazy { vehicleLargeVector() }
    val VehicleVan: ImageVector by lazy { vehicleVanVector() }

    // ── Vehicle types (high-level taxonomy, independent of size) ─────────────
    // Used in registration/edit to pick the user's vehicle category. Drives
    // detection strategy: SCOOTER / BIKE never enter the Coordinator algorithm.
    val VehicleCar: ImageVector get() = Icons.Filled.DirectionsCar
    val VehicleMotorcycle: ImageVector get() = Icons.Filled.TwoWheeler
    val VehicleScooter: ImageVector get() = Icons.Filled.ElectricScooter
    val VehicleBike: ImageVector get() = Icons.AutoMirrored.Filled.DirectionsBike


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

// ─── Custom vehicle silhouettes ──────────────────────────────────────────────
// Side-view silhouettes (right-facing) sharing one visual language: flat filled
// body polygon + rectangular EvenOdd window cut-outs + oval bezier wheel bumps
// that dip below the body floor. VAN is a direct conversion of casa-rodante.svg
// (same icon set); SMALL/MEDIUM/LARGE/MOTO follow the same visual language.

private const val VEHICLE_VIEWPORT = 24f
private val VEHICLE_DEFAULT_SIZE = 24.dp

private fun buildVehicleVector(name: String, build: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = VEHICLE_DEFAULT_SIZE,
        defaultHeight = VEHICLE_DEFAULT_SIZE,
        viewportWidth = VEHICLE_VIEWPORT,
        viewportHeight = VEHICLE_VIEWPORT,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            build()
        }
    }.build()

// Wheel helper — produces a downward oval bezier matching the casa-rodante style.
// The oval spans [x, x+width] at y=floor and dips ~3.5 dp below the viewport.
// Call once per wheel; EvenOdd fill is positive when it doesn't overlap the body.
private fun PathBuilder.wheelOval(x: Float, floor: Float, width: Float) {
    moveTo(x, floor)
    curveTo(x - 0.5f, floor + 3.5f, x + width + 0.5f, floor + 3.5f, x + width, floor)
    close()
}

/**
 * Small / compact hatchback. Steep C-pillar (rear left), short hood (front
 * right), single undivided cabin window. Wheels cx≈7 and cx≈18 at y=17.
 */
private fun vehicleSmallVector(): ImageVector = buildVehicleVector("PapVehicleSmall") {
    // Rear wheel (left, cx=7, width=5)
    wheelOval(x = 4.5f, floor = 17f, width = 5f)
    // Front wheel (right, cx=18, width=5)
    wheelOval(x = 15.5f, floor = 17f, width = 5f)
    // Cabin window — single pane (EvenOdd hole)
    moveTo(8f, 10f)
    lineTo(18f, 10f)
    lineTo(18f, 13.5f)
    lineTo(8f, 13.5f)
    close()
    // Body — steep rear hatch, flat roof, short hood
    moveTo(3f, 17f)
    lineTo(3f, 14f)
    lineTo(5f, 11f)
    lineTo(8f, 9f)
    lineTo(17f, 9f)
    lineTo(20f, 11f)
    lineTo(22f, 14f)
    lineTo(22f, 17f)
    close()
}

/**
 * Medium / sedan. 3-box notchback: trunk step at rear left, longer hood at
 * front right. Two-pane window divided by a B-pillar at x≈12–14.
 * Wheels cx≈7 and cx≈18 at y=17.
 */
private fun vehicleMediumVector(): ImageVector = buildVehicleVector("PapVehicleMedium") {
    // Rear wheel (left, cx=7, width=5)
    wheelOval(x = 4.5f, floor = 17f, width = 5f)
    // Front wheel (right, cx=18, width=5)
    wheelOval(x = 15.5f, floor = 17f, width = 5f)
    // Rear window pane (EvenOdd hole)
    moveTo(6f, 10f)
    lineTo(11f, 10f)
    lineTo(11f, 13.5f)
    lineTo(6f, 13.5f)
    close()
    // Front window pane — B-pillar gap at x=11..13 (EvenOdd hole)
    moveTo(13f, 10f)
    lineTo(19f, 10f)
    lineTo(19f, 13.5f)
    lineTo(13f, 13.5f)
    close()
    // Body — 3-box sedan with trunk step at rear, longer hood at front
    moveTo(2f, 17f)
    lineTo(2f, 14f)
    lineTo(3f, 12f)
    lineTo(5f, 10f)
    lineTo(6f, 9f)
    lineTo(17f, 9f)
    lineTo(19f, 10f)
    lineTo(21f, 13f)
    lineTo(22f, 15f)
    lineTo(22f, 17f)
    close()
}

/**
 * Large / SUV-crossover. Taller cabin (roof at y=7), near-vertical A and C
 * pillars, boxier proportions. Two-pane window. Wider wheels (width=6)
 * with cx≈7 and cx≈18 at y=18.
 */
private fun vehicleLargeVector(): ImageVector = buildVehicleVector("PapVehicleLarge") {
    // Rear wheel (left, cx=7, width=6 — larger than car sizes)
    wheelOval(x = 4f, floor = 18f, width = 6f)
    // Front wheel (right, cx=18, width=6)
    wheelOval(x = 15f, floor = 18f, width = 6f)
    // Rear window pane (EvenOdd hole)
    moveTo(5f, 9f)
    lineTo(11f, 9f)
    lineTo(11f, 14f)
    lineTo(5f, 14f)
    close()
    // Front window pane — B-pillar at x=11..13 (EvenOdd hole)
    moveTo(13f, 9f)
    lineTo(20f, 9f)
    lineTo(20f, 14f)
    lineTo(13f, 14f)
    close()
    // Body — tall boxy SUV, near-vertical pillars, short overhangs
    moveTo(2f, 18f)
    lineTo(2f, 12f)
    lineTo(3f, 9f)
    lineTo(5f, 7f)
    lineTo(17f, 7f)
    lineTo(20f, 9f)
    lineTo(21f, 12f)
    lineTo(22f, 15f)
    lineTo(22f, 18f)
    close()
}

/**
 * Motorcycle. Two large wheels (width=7, cx≈7 rear and cx≈18 front) with a
 * minimal frame + tank hump between them. Rear wheel at left, front at right
 * (same right-facing orientation as the other silhouettes).
 */
private fun vehicleMotoVector(): ImageVector = buildVehicleVector("PapVehicleMoto") {
    // Rear wheel (left, cx≈7, width=7 — prominent, moto-scale)
    wheelOval(x = 3.5f, floor = 17f, width = 7f)
    // Front wheel (right, cx≈18, width=7)
    wheelOval(x = 14.5f, floor = 17f, width = 7f)
    // Frame + tank — fits in the gap between wheels (x≈10.5..14.5)
    // EvenOdd overlap at wheel edges creates subtle fork/swingarm detail
    moveTo(10.5f, 17f)
    lineTo(10f, 14f)
    lineTo(9f, 11f)
    lineTo(9f, 9f)
    lineTo(12f, 8f)
    lineTo(15f, 9f)
    lineTo(16f, 11f)
    lineTo(15.5f, 14f)
    lineTo(14.5f, 17f)
    close()
}

/**
 * VAN / motorhome. Direct pixel-exact conversion of casa-rodante.svg (24×24).
 * Two-section body: tall living-quarters coach (left) + shorter cab (right).
 * Coach window + cab windshield are EvenOdd cut-outs. Wheels dip below y=21.
 *
 * SVG path breakdown:
 *  – Sub-paths 1–2  : rear and front wheel ovals at y=21 (extend past viewport)
 *  – Sub-path 3     : cab windshield (angled pane, right side)
 *  – Sub-path 4     : coach window detail (small rect, fills back via EvenOdd)
 *  – Sub-path 5     : rear cab lower panel (EvenOdd hole)
 *  – Sub-path 6     : main body (coach tall left + cab step right, rounded corners)
 *  – Sub-path 7     : large coach side window (EvenOdd hole, x=2..10, y=5..11)
 */
private fun vehicleVanVector(): ImageVector = buildVehicleVector("PapVehicleVan") {
    // Rear wheel (left, x=3.058..7.942 at y=21, dips to y≈25 — partially clipped)
    moveTo(3.058f, 21f)
    curveTo(2.471f, 24.954f, 8.530f, 24.952f, 7.942f, 21f)
    close()
    // Front wheel (right, x=16.058..20.942 at y=21)
    moveTo(20.942f, 21f)
    curveTo(21.529f, 24.954f, 15.470f, 24.952f, 16.058f, 21f)
    close()
    // Cab windshield (EvenOdd hole) — angled pane on the right side of cab
    moveTo(16f, 7f)
    lineTo(20.723f, 7f)
    lineTo(23.341f, 11.582f)
    curveTo(23.419f, 11.717f, 23.481f, 11.859f, 23.541f, 12f)
    lineTo(15f, 12f)
    lineTo(15f, 8f)
    arcTo(1f, 1f, 0f, false, true, 16f, 7f)
    close()
    // Coach window detail (EvenOdd fills back) — small rect at x=4..8, y=7..9
    moveTo(4f, 7f)
    lineTo(8f, 7f)
    lineTo(8f, 9f)
    lineTo(4f, 9f)
    close()
    // Rear cab lower panel (EvenOdd hole) — x=15..24, y=14..19
    moveTo(24f, 14f)
    curveTo(24f, 14.021f, 24f, 14.042f, 24f, 14.062f)
    lineTo(24f, 19f)
    lineTo(15f, 19f)
    lineTo(15f, 14f)
    close()
    // Main body — coach (tall, left x=0..13) + cab step (shorter, right x=13..24)
    moveTo(13f, 8f)
    arcTo(3f, 3f, 0f, false, true, 16f, 5f)
    lineTo(24f, 5f)
    arcTo(4f, 4f, 0f, false, false, 20f, 1f)
    lineTo(4f, 1f)
    arcTo(4f, 4f, 0f, false, false, 0f, 5f)
    lineTo(0f, 15.414f)
    lineTo(3.586f, 19f)
    lineTo(13f, 19f)
    close()
    // Coach side window (EvenOdd hole) — x=2..10, y=5..11
    moveTo(2f, 11f)
    lineTo(2f, 5f)
    lineTo(10f, 5f)
    lineTo(10f, 11f)
    close()
}

// ─── Domain → icon mappers ────────────────────────────────────────────────────
// Live in the UI layer (not on the enum itself) so the domain models stay
// Compose-free. Importing PaparcarIcons gives you the .icon extension on both
// VehicleSize and PlaceCategory for drop-in use at any Icon(...) call site.

/**
 * Replaces the legacy `vehicleSizeEmoji(...)` helper. Returns the Paparcar
 * icon matching a given [VehicleSize] so call sites render
 * `Icon(size.icon, ...)` instead of a Text+emoji pair.
 */
val VehicleSize.icon: ImageVector
    get() = when (this) {
        VehicleSize.MOTORCYCLE   -> PaparcarIcons.VehicleMoto
        VehicleSize.MICRO_SMALL  -> PaparcarIcons.VehicleSmall
        VehicleSize.MEDIUM_SUV -> PaparcarIcons.VehicleMedium
        VehicleSize.LARGE_SEDAN  -> PaparcarIcons.VehicleLarge
        VehicleSize.VAN_HIGH    -> PaparcarIcons.VehicleVan
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
