package com.rndeveloper.paparcar.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.VolunteerActivism
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
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.PlaceCategory
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.model.VehicleType

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
 *  - Filled silhouettes (no stroke); los huecos salen del SENTIDO de dibujo (winding no-cero), no de
 *    `EvenOdd` — ver el ⛔ de [PaparcarIcons.CarParkMark].
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
    /**
     * A free spot of the community — the hand + heart of a spot someone GAVE, the same glyph the
     * stats tile uses for "spots shared". One glyph for the whole life of a spot: the lane tab, the
     * map puck (selected, en route, report pin), the list row and the peek.
     * [SPOT-ICON-IS-THE-GIVEN-SPOT-HAND-HEART-001]
     */
    val CommunitySpot: ImageVector get() = Icons.Rounded.VolunteerActivism
    val Bank: ImageVector get() = Icons.Rounded.AccountBalance
    val Hotel: ImageVector get() = Icons.Rounded.Hotel
    val School: ImageVector get() = Icons.Rounded.School
    val Gym: ImageVector get() = Icons.Rounded.FitnessCenter
    val PlaceGeneric: ImageVector get() = Icons.Rounded.Place
    // ⛔ Aquí NO hay un `ParkingPlace` de Material: el parking público —capa del mapa y categoría de
    // POI, que son el mismo `amenity=parking` de Overpass— lo dibuja [CarParkMark], nuestra P. Había
    // dos P en la app y ninguna era nuestra. [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001]

    // ── Vehicle types (high-level taxonomy, independent of size) ─────────────
    // Used in registration/edit to pick the user's vehicle category. Drives
    // detection strategy: SCOOTER / BIKE never enter the Coordinator algorithm.
    val VehicleCar: ImageVector get() = Icons.Rounded.DirectionsCar
    val VehicleMotorcycle: ImageVector get() = Icons.Rounded.TwoWheeler
    val VehicleScooter: ImageVector get() = Icons.Rounded.ElectricScooter
    val VehicleBike: ImageVector get() = Icons.AutoMirrored.Rounded.DirectionsBike


    // ── Marcas de infraestructura "cerca de ti" ──────────────────────────────
    // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
    //
    // Las superficies de infraestructura usan el MISMO lenguaje que las marcas que ya tiene la app
    // —los pictogramas de coche—: silueta RELLENA, viewport 24×24, un solo path para que el `Icon`
    // anfitrión las tiña. ⛔ El parking público no es plumbing de UI, es un producto que la app
    // enseña, y con un glifo genérico las pestañas se leían como un filtro más.
    //
    // Queda UNA marca propia, [CarParkMark]. Las otras dos que este bloque anunciaba ya no existen y
    // el comentario las siguió prometiendo un tiempo: la plaza dibujada como HUECO entre marcas
    // viales (`ParkingBay`) se retiró sin un solo call site cuando la plaza pasó a la mano + corazón
    // [SPOT-ICON-IS-THE-GIVEN-SPOT-HAND-HEART-001], y la gasolinera nunca dejó de ser
    // `Icons.Rounded.LocalGasStation`. [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001]

    /**
     * Parking público: **nuestra P**. Es la letra rellena que los pucks de plaza llevaron hasta que
     * la plaza pasó a la mano + corazón [SPOT-ICON-IS-THE-GIVEN-SPOT-HAND-HEART-001], y desde
     * entonces estaba libre. No es la P de Material ni un edificio en miniatura: es la ÚNICA P de la
     * app, y es del parking público en todas sus superficies — marcador del mapa, fila de lista,
     * pestaña de carril, peek y la categoría de POI `PlaceCategory.PARKING`, que es el mismo
     * `amenity=parking` de Overpass. [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001] [COPY-SPOT-IS-NOT-A-PARKING-001]
     *
     * Sustituye al volumen-con-P que vivió un día (13→14-09-2026): en un marcador sólo hay hueco
     * para UNA idea, y "instalación, no plaza" ya lo dice la SILUETA del puck —cuerpo de papel con
     * borde verde, frente al puck relleno de frescura de una plaza—. La casita repetía con tinta lo
     * que la forma ya había resuelto, y lo pagaba justo donde más se mira: a 18 dp en la pestaña de
     * carril y al 60 % en el mapa, el volumen se comía la letra.
     *
     * El dibujo es [GLYPH_P_PATH] intacto, en su rejilla original de 48; el grupo sólo lleva su
     * TINTA al cuadrado de 24 del set. La caja es propiedad del icono, no del call site: nadie
     * tiene que escalarlo al pintarlo, y por eso no se reescribieron los números del path —
     * corregir una letra a mano es como se pierde una letra.
     *
     * ⛔ Relleno por winding NO-CERO (el defecto), nunca `EvenOdd`: el ojo es una elipse dibujada al
     * revés que el bol, y el asta lo pisa por delante en el mismo sentido. Con `EvenOdd` el asta
     * abriría un segundo hueco donde cruza el bol y la P se partiría en dos.
     */
    val CarParkMark: ImageVector by lazy {
        ImageVector.Builder(
            name = "CarParkMark",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .addGroup(scaleX = P_SCALE, scaleY = P_SCALE, translationX = P_DX, translationY = P_DY)
            .addPath(
                pathData = PathParser().parsePathString(GLYPH_P_PATH).toNodes(),
                fill = SolidColor(Color.Black),
            )
            .clearGroup()
            .build()
    }

    // Filled Fredoka "P" with a counter (nonzero winding gives the hole) — asta, bol y ojo, en una
    // rejilla de 48. Es el glifo que llevaban los pucks de plaza hasta
    // [SPOT-ICON-IS-THE-GIVEN-SPOT-HAND-HEART-001]; vuelve tal cual, sin tocar un número.
    private const val GLYPH_P_PATH =
        "M11.46 11.08 a6.08 6.08 0 0 1 12.16 0 L23.62 36.92 a6.08 6.08 0 0 1 -12.16 0 Z " +
            "M11.46 17.16 a14.06 12.16 0 1 1 28.12 0 a14.06 12.16 0 1 1 -28.12 0 Z " +
            "M19.33 17.16 a6.19 5.35 0 1 0 12.37 0 a6.19 5.35 0 1 0 -12.37 0 Z"

    // La tinta de [GLYPH_P_PATH] ocupa x 11.46–39.58, y 5–43 de su rejilla de 48 (el asta aporta las
    // dos semicircunferencias de los extremos, el bol el ancho). Estos números la llevan a una
    // mancha de 20 centrada en el cuadro de 24 — la misma que tienen los glifos Material del set, que
    // es lo que hace que la P no cante al lado de un surtidor en la misma fila.
    private const val P_INK_HEIGHT = 38f    // 43 − 5
    private const val P_INK_CENTER_X = 25.52f  // (11.46 + 39.58) / 2
    private const val P_INK_CENTER_Y = 24f     // (5 + 43) / 2
    private const val P_TARGET_HEIGHT = 20f    // alto de la mancha dentro del cuadro de 24
    private const val P_SCALE = P_TARGET_HEIGHT / P_INK_HEIGHT
    private const val P_DX = 12f - P_INK_CENTER_X * P_SCALE
    private const val P_DY = 12f - P_INK_CENTER_Y * P_SCALE
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
        // El mismo `amenity=parking` que pinta la capa de parkings del mapa, así que el mismo glifo:
        // una P, la nuestra. [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001]
        PlaceCategory.PARKING     -> PaparcarIcons.CarParkMark
        PlaceCategory.BANK        -> PaparcarIcons.Bank
        PlaceCategory.HOTEL       -> PaparcarIcons.Hotel
        PlaceCategory.SCHOOL      -> PaparcarIcons.School
        PlaceCategory.GYM         -> PaparcarIcons.Gym
        PlaceCategory.OTHER       -> PaparcarIcons.PlaceGeneric
    }
