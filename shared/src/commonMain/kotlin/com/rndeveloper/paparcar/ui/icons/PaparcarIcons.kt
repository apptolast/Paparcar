package com.rndeveloper.paparcar.ui.icons

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.ElectricScooter
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalMall
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.NearMe
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathData
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
     * El ACTO de ceder una plaza — la mano + corazón. Acompaña SIEMPRE a un texto que nombra el
     * acto: el tile «plazas cedidas» de las cifras y la línea «la comunidad te la cedió» del
     * detalle de historial. No es el OBJETO: eso es [CommunitySpot].
     * [SPOT-A-FREE-SPOT-IS-A-GAP-NOT-A-GIFT-001]
     */
    val SpotGiven: ImageVector get() = Icons.Rounded.VolunteerActivism
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

    // ── Conceptos del producto ───────────────────────────────────────────────
    // [ICON-A-CONCEPT-KEEPS-ONE-GLYPH-ON-EVERY-SURFACE-001]
    //
    // Un concepto que la app NOMBRA se dibuja igual en todas partes, y por eso su glifo se decide
    // aquí y no en el call site. Antes no: «cifras» salía con dos dibujos distintos en la misma
    // pantalla de paywall, y `Group` significaba a la vez un coche compartido y los conductores
    // que van a por una plaza. Pedir el Material crudo para uno de estos conceptos es lo que
    // permite que la próxima superficie elija otro.
    //
    // ⛔ Y no sólo dentro de la app: `web/lib/glyphs.generated.mjs` se GENERA de aquí, porque la
    // web ya llegó a servir una plaza y un parking que la app había retirado.

    /** El coche que conducen varios [VEH-SHARED-001]. Suyo en exclusiva: los conductores en
     *  camino a una plaza llevan [EnRoute], que es movimiento y no pertenencia. */
    val SharedCar: ImageVector get() = Icons.Rounded.Group

    /** Lo que ya pasó: la lista de sesiones y el gate que la alarga. */
    val History: ImageVector get() = Icons.Rounded.History

    /**
     * Las cifras como CONCEPTO — el bloque entero, no una magnitud suelta (cada tile sigue
     * llevando la suya: `Payments`, `Cloud`, `TravelExplore`…).
     *
     * ⛔ Barras, y nada más. Las dos alternativas obvias meten una segunda idea donde ya está
     * gastada: `Insights` lleva la chispa de `Icons.Rounded.AutoAwesome`, que en ESA misma rejilla
     * significa «detectado automáticamente», y `Leaderboard` es un podio, que dice *ranking* donde
     * no se compite contra nadie. La lupa tampoco está libre: es `TravelExplore` = tiempo buscando
     * plaza.
     */
    val Figures: ImageVector get() = Icons.Rounded.BarChart

    /**
     * Conductores yendo a por una plaza («3 en camino»). La flecha, no el grupo: lo que cuenta la
     * cifra es gente MOVIÉNDOSE hacia allí, y el grupo ya es el coche compartido [SharedCar].
     */
    val EnRoute: ImageVector get() = Icons.Rounded.NearMe


    // ── Marcas de infraestructura "cerca de ti" ──────────────────────────────
    // [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
    //
    // Las superficies de infraestructura usan el MISMO lenguaje que las marcas que ya tiene la app
    // —los pictogramas de coche—: silueta RELLENA, viewport 24×24, un solo path para que el `Icon`
    // anfitrión las tiña. ⛔ El parking público no es plumbing de UI, es un producto que la app
    // enseña, y con un glifo genérico las pestañas se leían como un filtro más.
    //
    // Son DOS marcas propias: [CarParkMark] y [CommunitySpot]. La gasolinera nunca dejó de ser
    // `Icons.Rounded.LocalGasStation`, y ésa es toda la fila de pestañas: dos cosas nuestras y un
    // surtidor. [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001] [SPOT-A-FREE-SPOT-IS-A-GAP-NOT-A-GIFT-001]

    /**
     * Plaza libre de la comunidad: **la PLAZA PINTADA**. Un trapecio en perspectiva —como se ve una
     * plaza desde la calle— dibujado a rayas, con el punto de su posición en medio.
     * [SPOT-A-FREE-SPOT-IS-A-GAP-NOT-A-GIFT-001]
     *
     * Es el único token del OBJETO: de aquí beben las cinco superficies de una plaza —puck del mapa,
     * puck de lista, lead del peek, pin de reporte y pestaña del carril—. El ACTO de ceder una plaza
     * es otra cosa y tiene el suyo, [SpotGiven]; cambiar uno no mueve al otro, que es justo lo que
     * este glifo vino a arreglar: la mano + corazón afirmaba que alguien había CEDIDO la plaza
     * también en el pin de un reporte manual, donde nadie cedió nada — se fue alguien que ni usa la
     * app, y un testigo lo vio.
     *
     * ⛔ Ninguna «P»: ésa es del parking público [UI-THE-CAR-PARK-WEARS-OUR-OWN-P-001] y una plaza no
     * es un parking [COPY-SPOT-IS-NOT-A-PARKING-001].
     *
     * **Las rayas son piezas RELLENAS**, no un `stroke-dasharray`: cada una es una cápsula (dos
     * rectas y dos arcos). Eso es lo que la hace compatible con el set —un `ImageVector` que el
     * `Icon` anfitrión tiñe— en vez de obligar a un Canvas, que es lo que exige un trazo discontinuo
     * de verdad.
     *
     * Encaje: la tinta ocupa **21,9 × 17,5** del cuadro de 24, con trazo de **2,5**. Ese grosor no es
     * estético, es el mínimo que sobrevive en blanco sobre el disco de color a los ~16,6 dp del puck
     * del mapa; y la regla que ordena las rayas es que **todo hueco mida al menos lo que el trazo**,
     * porque un hueco más estrecho que su raya no se ve y la figura se lee como una línea continua.
     */
    val CommunitySpot: ImageVector by lazy {
        ImageVector.Builder(
            name = "CommunitySpot",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        )
            .addPath(
                pathData = PathParser().parsePathString(SPOT_BAY_PATH).toNodes(),
                fill = SolidColor(Color.Black),
            )
            .build()
    }

    // El trapecio punteado, en la rejilla de 24. Trece piezas: la raya de arriba, dos por lado más su
    // codo, dos abajo y el punto del centro. Generado desde su geometría (trapecio + reparto de rayas
    // por fracción de arista), no trazado a mano: por eso es simétrico al milímetro respecto de x=12.
    private const val SPOT_BAY_PATH =
        "M9.85 5.75L14.15 5.75A1.25 1.25 0 0 0 14.15 3.25L9.85 3.25A1.25 1.25 0 0 0 9.85 5.75ZM5.34 5.62L5.15 6.23A1.25 1.25 0 0 0 7.53 6.98L7.73 6.37A1.25 1.25 0 0 0 5.34 5.62ZM4.02 9.82L3.55 11.33A1.25 1.25 0 0 0 5.93 12.08L6.41 10.57A1.25 1.25 0 0 0 4.02 9.82ZM2.43 14.92L1.11 19.13A1.25 1.25 0 0 0 3.49 19.87L4.81 15.67A1.25 1.25 0 0 0 2.43 14.92ZM2.30 20.75L3.66 20.75A1.25 1.25 0 0 0 3.66 18.25L2.30 18.25A1.25 1.25 0 0 0 2.30 20.75ZM16.27 6.37L16.47 6.98A1.25 1.25 0 0 0 18.85 6.23L18.66 5.62A1.25 1.25 0 0 0 16.27 6.37ZM17.59 10.57L18.07 12.08A1.25 1.25 0 0 0 20.45 11.33L19.98 9.82A1.25 1.25 0 0 0 17.59 10.57ZM19.19 15.67L20.51 19.87A1.25 1.25 0 0 0 22.89 19.13L21.57 14.92A1.25 1.25 0 0 0 19.19 15.67ZM21.70 18.25L20.34 18.25A1.25 1.25 0 0 0 20.34 20.75L21.70 20.75A1.25 1.25 0 0 0 21.70 18.25ZM6.85 20.75L9.20 20.75A1.25 1.25 0 0 0 9.20 18.25L6.85 18.25A1.25 1.25 0 0 0 6.85 20.75ZM14.80 20.75L17.15 20.75A1.25 1.25 0 0 0 17.15 18.25L14.80 18.25A1.25 1.25 0 0 0 14.80 20.75ZM10.05 12.70A1.95 1.95 0 0 1 13.95 12.70A1.95 1.95 0 0 1 10.05 12.70Z"

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
