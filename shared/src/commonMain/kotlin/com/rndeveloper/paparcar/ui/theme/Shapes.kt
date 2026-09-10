package com.rndeveloper.paparcar.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material 3 default shapes wired into PaparcarTheme. Components that use
// `MaterialTheme.shapes.medium` etc. resolve through this table.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Semantic corner-radius tokens for Paparcar surfaces.
 *
 * **Four radii, and each one has a job:**
 *
 *  - [row]:    12dp     — anything INSIDE a card: icon tiles, inner rows, buttons, skeletons
 *  - [card]:   16dp     — every card, whatever it holds
 *  - [sheet]:  top 20dp — the surfaces that own the bottom of the screen (sheet, dialog)
 *  - [chip]:   999dp    — pills: chips, badges, status capsules
 *
 * ⛔ **No hay `cardSmall` (14) ni `cardLarge` (18), y no vuelven.** Los tres radios de tarjeta
 * estaban a 2dp unos de otros: nadie distingue 14 de 16 de 18 en un móvil, pero cada componente
 * nuevo tenía que elegir entre los tres — y esa moneda al aire es lo que después se lee como
 * inconsistencia. Una escala solo sirve si sus escalones se ven.
 * [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
 *
 * Use these instead of `RoundedCornerShape(N.dp)` whenever a surface fits one of the tiers.
 */
object PapShapes {
    val chip = RoundedCornerShape(999.dp)
    val row = RoundedCornerShape(12.dp)
    val card = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    val dialog = RoundedCornerShape(20.dp)
}
