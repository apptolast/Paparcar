package io.apptolast.paparcar.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.AirportShuttle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

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
    val Fuel: ImageVector get() = Icons.Outlined.LocalGasStation
    val Supermarket: ImageVector get() = Icons.Outlined.ShoppingCart
    val Mall: ImageVector get() = Icons.Outlined.LocalMall
    val Restaurant: ImageVector get() = Icons.Outlined.Restaurant
    val Cafe: ImageVector get() = Icons.Outlined.LocalCafe
    val Pharmacy: ImageVector get() = Icons.Outlined.LocalPharmacy
    val Hospital: ImageVector get() = Icons.Outlined.LocalHospital
    val ParkingPlace: ImageVector get() = Icons.Outlined.LocalParking
    val Bank: ImageVector get() = Icons.Outlined.AccountBalance
    val Hotel: ImageVector get() = Icons.Outlined.Hotel
    val School: ImageVector get() = Icons.Outlined.School
    val Gym: ImageVector get() = Icons.Outlined.FitnessCenter
    val PlaceGeneric: ImageVector get() = Icons.Outlined.Place

    // ── Vehicle sizes ────────────────────────────────────────────────────────
    // MOTO and VAN reuse Material outlined glyphs that already differ enough
    // from the cars to read at a glance. The three car sizes are drawn from
    // scratch since Material only ships a single generic "DirectionsCar".
    val VehicleMoto: ImageVector get() = Icons.Outlined.TwoWheeler
    val VehicleVan: ImageVector get() = Icons.Outlined.AirportShuttle
    val VehicleSmall: ImageVector by lazy { vehicleSmallVector() }
    val VehicleMedium: ImageVector by lazy { vehicleMediumVector() }
    val VehicleLarge: ImageVector by lazy { vehicleLargeVector() }


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
}

// ─── Custom vehicle silhouettes ──────────────────────────────────────────────
// Side-view silhouettes sharing one visual language: rounded body, two
// circular wheel hubs cut out with even-odd, a window band carved out of the
// cabin. Differences are size and proportion — Small is shorter, Medium is
// the canonical sedan, Large sits taller with bigger wheels (SUV / crossover).

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

/**
 * Small / compact car (e.g. hatchback). Lower body height, short cabin
 * compartment, wheels at (5, 18) and (19, 18).
 */
private fun vehicleSmallVector(): ImageVector = buildVehicleVector("PapVehicleSmall") {
    // Outer outline — rounded silhouette with one short cabin.
    moveTo(4f, 18f)
    lineTo(4f, 14f)
    curveTo(4f, 13.4f, 4.3f, 12.8f, 4.7f, 12.5f)
    lineTo(7.5f, 10.2f)
    curveTo(7.9f, 9.9f, 8.4f, 9.7f, 8.9f, 9.7f)
    lineTo(15.1f, 9.7f)
    curveTo(15.6f, 9.7f, 16.1f, 9.9f, 16.5f, 10.2f)
    lineTo(19.3f, 12.5f)
    curveTo(19.7f, 12.8f, 20f, 13.4f, 20f, 14f)
    lineTo(20f, 18f)
    close()
    // Cabin window cut-out (single, since this is a compact 2-door).
    moveTo(9f, 11.2f)
    lineTo(15f, 11.2f)
    lineTo(17f, 13.4f)
    lineTo(7f, 13.4f)
    close()
    // Wheel hub cut-outs — even-odd makes them transparent inside the body.
    moveTo(7f, 18f)
    curveTo(7f, 16.9f, 6.1f, 16f, 5f, 16f)
    curveTo(3.9f, 16f, 3f, 16.9f, 3f, 18f)
    curveTo(3f, 19.1f, 3.9f, 20f, 5f, 20f)
    curveTo(6.1f, 20f, 7f, 19.1f, 7f, 18f)
    close()
    moveTo(21f, 18f)
    curveTo(21f, 16.9f, 20.1f, 16f, 19f, 16f)
    curveTo(17.9f, 16f, 17f, 16.9f, 17f, 18f)
    curveTo(17f, 19.1f, 17.9f, 20f, 19f, 20f)
    curveTo(20.1f, 20f, 21f, 19.1f, 21f, 18f)
    close()
}

/**
 * Medium / sedan — canonical 4-door car silhouette. Slightly longer body
 * than the compact with a window divider hinting at the second row.
 */
private fun vehicleMediumVector(): ImageVector = buildVehicleVector("PapVehicleMedium") {
    moveTo(3f, 18f)
    lineTo(3f, 13.5f)
    curveTo(3f, 12.9f, 3.3f, 12.3f, 3.7f, 12f)
    lineTo(6.5f, 9.5f)
    curveTo(6.9f, 9.2f, 7.4f, 9f, 7.9f, 9f)
    lineTo(16.1f, 9f)
    curveTo(16.6f, 9f, 17.1f, 9.2f, 17.5f, 9.5f)
    lineTo(20.3f, 12f)
    curveTo(20.7f, 12.3f, 21f, 12.9f, 21f, 13.5f)
    lineTo(21f, 18f)
    close()
    // Cabin glass — left half (front passenger).
    moveTo(8f, 10.5f)
    lineTo(11.5f, 10.5f)
    lineTo(11.5f, 12.8f)
    lineTo(5.6f, 12.8f)
    close()
    // Cabin glass — right half (rear passenger).
    moveTo(12.5f, 10.5f)
    lineTo(16f, 10.5f)
    lineTo(18.4f, 12.8f)
    lineTo(12.5f, 12.8f)
    close()
    // Wheels (r = 2).
    moveTo(8f, 18f)
    curveTo(8f, 16.9f, 7.1f, 16f, 6f, 16f)
    curveTo(4.9f, 16f, 4f, 16.9f, 4f, 18f)
    curveTo(4f, 19.1f, 4.9f, 20f, 6f, 20f)
    curveTo(7.1f, 20f, 8f, 19.1f, 8f, 18f)
    close()
    moveTo(20f, 18f)
    curveTo(20f, 16.9f, 19.1f, 16f, 18f, 16f)
    curveTo(16.9f, 16f, 16f, 16.9f, 16f, 18f)
    curveTo(16f, 19.1f, 16.9f, 20f, 18f, 20f)
    curveTo(19.1f, 20f, 20f, 19.1f, 20f, 18f)
    close()
}

/**
 * Large / SUV — taller body and slightly larger wheels. Same overall length
 * as the sedan but the cabin sits higher and the window line runs straighter.
 */
private fun vehicleLargeVector(): ImageVector = buildVehicleVector("PapVehicleLarge") {
    moveTo(3f, 18f)
    lineTo(3f, 12.5f)
    curveTo(3f, 11.9f, 3.3f, 11.3f, 3.7f, 11f)
    lineTo(5.5f, 9.5f)
    curveTo(5.9f, 9.2f, 6.4f, 9f, 6.9f, 9f)
    lineTo(17.1f, 9f)
    curveTo(17.6f, 9f, 18.1f, 9.2f, 18.5f, 9.5f)
    lineTo(20.3f, 11f)
    curveTo(20.7f, 11.3f, 21f, 11.9f, 21f, 12.5f)
    lineTo(21f, 18f)
    close()
    // Cabin glass band — taller and straighter than the sedan.
    moveTo(7f, 10.4f)
    lineTo(11.5f, 10.4f)
    lineTo(11.5f, 12.5f)
    lineTo(5.4f, 12.5f)
    close()
    moveTo(12.5f, 10.4f)
    lineTo(17f, 10.4f)
    lineTo(18.6f, 12.5f)
    lineTo(12.5f, 12.5f)
    close()
    // Wheels (r = 2.2).
    moveTo(8.2f, 18f)
    curveTo(8.2f, 16.78f, 7.22f, 15.8f, 6f, 15.8f)
    curveTo(4.78f, 15.8f, 3.8f, 16.78f, 3.8f, 18f)
    curveTo(3.8f, 19.22f, 4.78f, 20.2f, 6f, 20.2f)
    curveTo(7.22f, 20.2f, 8.2f, 19.22f, 8.2f, 18f)
    close()
    moveTo(20.2f, 18f)
    curveTo(20.2f, 16.78f, 19.22f, 15.8f, 18f, 15.8f)
    curveTo(16.78f, 15.8f, 15.8f, 16.78f, 15.8f, 18f)
    curveTo(15.8f, 19.22f, 16.78f, 20.2f, 18f, 20.2f)
    curveTo(19.22f, 20.2f, 20.2f, 19.22f, 20.2f, 18f)
    close()
}
