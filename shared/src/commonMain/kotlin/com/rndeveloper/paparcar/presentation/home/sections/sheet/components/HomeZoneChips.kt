package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import com.rndeveloper.paparcar.ui.components.papGuideHighlight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.util.MAP_FLOATING_SHADOW_DP
import com.rndeveloper.paparcar.presentation.util.zoneIconFor
import com.rndeveloper.paparcar.ui.components.GlassSurface
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.zoneIdentityColor
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_header_add_zone
import paparcar.composeapp.generated.resources.home_header_add_zone_dismiss_cd
import paparcar.composeapp.generated.resources.home_header_add_zone_hint
import paparcar.composeapp.generated.resources.home_zone_action_edit
import paparcar.composeapp.generated.resources.home_zone_private_label
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PapIconSize
import com.rndeveloper.paparcar.ui.theme.PapControlHeight

/**
 * Habitual-zone chip — a **glass stadium pill** that floats over the map in the
 * header, sharing the glass language of the search bar / map-type picker and the
 * fully-rounded pill shape of the vehicle tabs. Opaque at rest, translucent while
 * the map camera is dragged (via [GlassSurface]).
 *
 * Two gestures, neither destructive: **tap flies the camera to the zone**, the
 * trailing **pencil opens its modal**, where the zone is managed (icon, radius,
 * privacy, name — and deleting it, behind a confirm). Deleting was never worth a
 * 14dp bullseye floating over the map, and the edit long-press it replaces was
 * undiscoverable. [ZONE-CHIPS-GLASS-001] [UI-ZONE-MANAGE-001]
 *
 * @param isPrivate carries the SAME identity the map already gives this zone — neutral tint plus a
 *   lock. No default: a chip that forgets its zone is private is exactly the bug this parameter
 *   exists to make impossible. [UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001]
 */
// Public, like every other component the Dev Catalog renders (`HomePeekHandle`, `SpotFitRow`…):
// the state gallery lives in `:app` and cannot see `internal`. Being unreachable from the catalog
// is what let this chip go a whole life without anyone putting its two variants side by side.
// [UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001]
@Composable
fun ZoneChip(
    label: String,
    iconKey: String,
    isPrivate: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val zoneColor = zoneIdentityColor(isPrivate)
    GlassSurface(
        onClick = onClick,
        shape = RoundedCornerShape(ZONE_CHIP_RADIUS_DP.dp),
        // Same container colour, FAB shadow and (no resting) border as the map
        // FABs — every floating-over-map control shares one contract. [MAP-GLASS-001]
        shadowElevation = MAP_FLOATING_SHADOW_DP.dp,
        modifier = modifier,
    ) {
        Row(
            // Tighter end inset than start: the pencil's 28dp box already carries its own optical
            // margin around the 16dp glyph — y desde
            // [UI-A-TOUCH-TARGET-ALSO-PUSHES-SIDEWAYS-001] esa cuenta vuelve a ser cierta: el
            // mínimo táctil la había convertido en 48 por debajo, y este comentario seguía
            // hablando de 28. Un comentario no corrige un layout, pero sí fosiliza una cuenta.
            // The height is DECLARED, not left to whichever child happens to be tallest: the add
            // chip next door has to match it, and an emergent height gives it nothing to match.
            // [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
            //
            // ⛔ Y ya no lo declara ESTE fichero. La regla de arriba se descubrió aquí, para dos
            // chips vecinos, y resultó ser la de TODO control de la app: vive en
            // `PapControlHeight`, de donde salen también la píldora flotante del mapa y el
            // selector de vehículo, que antes importaban el 34 de este chip o copiaban un 36.
            // [UI-A-SCALE-NOBODY-USES-IS-NOT-A-SYSTEM-001]
            modifier = Modifier
                .height(PapControlHeight.chip)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PapGap.chipRow),
        ) {
            Icon(
                imageVector = zoneIconFor(iconKey),
                contentDescription = null,
                tint = zoneColor,
                modifier = Modifier.size(PapIconSize.withLabel),
            )
            Text(
                text = label,
                style = PaparcarType.current.label,
                color = cs.onSurface,
                maxLines = 1,
            )
            if (isPrivate) {
                // Same trailing lock the map label carries, sized down to the chip's scale. It sits
                // BEFORE the pencil: the lock describes the zone, the pencil is an action on it.
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    // Reuses the label the zone editor's privacy switch already carries in all 9
                    // locales — same concept, so it must not grow a second wording.
                    contentDescription = stringResource(Res.string.home_zone_private_label),
                    tint = zoneColor,
                    modifier = Modifier.size(ZONE_CHIP_LOCK_DP.dp),
                )
            }
            // The pencil is now the way INTO zone management, so it gets a real
            // touch target instead of the old ×'s 14dp sliver.
            Box(
                modifier = Modifier
                    // ⛔ **Sin `minimumInteractiveComponentSize()`, y es una decisión medida, no un
                    // descuido.** [UI-A-TOUCH-TARGET-ALSO-PUSHES-SIDEWAYS-001]
                    //
                    // El mínimo impone 48×48 de LAYOUT, pero el alto lo capa el chip (34 dp,
                    // declarado arriba), así que **sólo ganaba a lo ancho**: 48 para un glifo de 16
                    // = 16 dp de aire invisible por lado. Medido en device el 20-10 (Pixel_8, 420
                    // dpi): del glifo al borde del chip había 52 px = 19,8 dp, exactamente
                    // `16 + 4` del padding de abajo.
                    //
                    // Y esos 16 dp no compraban accesibilidad: **un mínimo táctil que sólo se
                    // cumple en un eje no es un mínimo, es aire.** El dedo tenía 34 dp en vertical
                    // con el modificador puesto y los sigue teniendo sin él; lo único que cambia es
                    // que el chip deja de reservar ancho que nadie veía. El área vuelve a ser la
                    // que este control tuvo siempre, 28×34, y la alternativa —subir el chip a 48
                    // para cumplirlo de verdad en los dos ejes— la descartó el user el 20-10:
                    // engorda los chips de Home y arrastra al de añadir, que debe igualarlos.
                    .size(ZONE_CHIP_EDIT_TAP_DP.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(Res.string.home_zone_action_edit),
                    tint = cs.onSurface.copy(alpha = ZONE_CHIP_TRAILING_ALPHA),
                    modifier = Modifier.size(ZONE_CHIP_EDIT_DP.dp),
                )
            }
        }
    }
}

/**
 * The "add a zone" control — the last thing on the zone row, in both of its states.
 *
 * [expanded] is an invitation, not a different widget: the same pill, showing its title, its hint
 * and a × to put it away. Collapsed es una píldora de 42×34 — exactamente el alto de los chips de
 * al lado y sus mismos casquetes — y la transición entre los dos estados se anima porque es LA
 * MISMA cosa cambiando de tamaño. Fue un círculo hasta que se vio en la fila: con el mismo hueco
 * de 8dp que sus vecinos, un casquete redondo se lee separado porque se curva alejándose de ellos.
 *
 * It replaced two separate composables (a 56dp invitation card and a 32dp `MapCircleFab`) that
 * swapped with `if (dismissed) … return` — an instant cut between two heights that never matched
 * the row, positioned by two different paddings, so the "+" jumped the moment you saved your first
 * zone. One component, one height, one geometry.
 * [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
 *
 * @param expanded show the invitation. Callers pass `false` once there is any zone to point at.
 * @param onDismiss collapse it. Only reachable while [expanded].
 */
@Composable
fun AddZoneChip(
    expanded: Boolean,
    onAddZone: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** A checklist step is pointing at this chip. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] */
    highlighted: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    GlassSurface(
        onClick = onAddZone,
        shape = RoundedCornerShape(ZONE_CHIP_RADIUS_DP.dp),
        shadowElevation = MAP_FLOATING_SHADOW_DP.dp,
        modifier = modifier.papGuideHighlight(highlighted, RoundedCornerShape(ZONE_CHIP_RADIUS_DP.dp)),
    ) {
        Row(
            // `heightIn`, not `height`: COLLAPSED it must be exactly the row's height, but expanded
            // it carries two 16sp lines (32dp) that a fixed 34dp box would clip the moment the user
            // raises their font scale. The invitation is also the only thing on the row while it
            // shows — there is nothing beside it to be taller than.
            modifier = Modifier
                .heightIn(min = PapControlHeight.chip)
                .animateContentSize(PapMotion.medium()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // El "+" va en una PÍLDORA, no en un círculo: mismo alto que la fila y el mismo padding
            // horizontal que lleva el glifo de un chip de zona (12dp), así que colapsado mide 42×34
            // y sus casquetes tienen el mismo radio que los de al lado.
            //
            // Era un cuadrado de 34 — un círculo exacto — y en la fila se leía SEPARADO aunque el
            // hueco fuera el mismo 8dp que entre los demás: un casquete redondo se curva alejándose
            // de su vecino, así que el aire ÓPTICO es mayor que el geométrico. Es la proporción que
            // ya usa el "+" de Vehículos ([PaparcarAddChip]: padding horizontal alrededor del
            // glifo); se copia la proporción, no el componente, porque este flota sobre el mapa y
            // necesita el contrato de cristal + sombra de FAB. [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001]
            //
            // De paso se va el inset animado: el glifo queda a 12 + 9 = 21dp del borde, el mismo
            // sitio que el icono de un chip de zona, y ahora en LOS DOS estados. No hay 4dp que
            // recorrer al abrirse porque no hay dos geometrías.
            Box(
                modifier = Modifier
                    .height(PapControlHeight.chip)
                    .padding(horizontal = ADD_ZONE_GLYPH_PAD_H_DP.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(Res.string.home_header_add_zone),
                    tint = cs.primary,
                    modifier = Modifier.size(PapIconSize.withLabel),
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(PapMotion.medium()) + expandHorizontally(PapMotion.medium()),
                exit = fadeOut(PapMotion.fast()) + shrinkHorizontally(PapMotion.medium()),
            ) {
                // The × carries 6dp of optical margin inside its 28dp touch box and the row ended
                // flush, so it had 6dp of air where the zone chip's pencil has 10. Same trailing
                // inset as that chip: the two pills end the same way.
                // [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001]
                Row(
                    modifier = Modifier.padding(end = ADD_ZONE_TRAILING_PAD_DP.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.padding(vertical = ADD_ZONE_TEXT_PAD_V_DP.dp)) {
                        Text(
                            text = stringResource(Res.string.home_header_add_zone),
                            style = PaparcarType.current.label,
                            color = cs.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(Res.string.home_header_add_zone_hint),
                            style = PaparcarType.current.caption,
                            color = cs.onSurface.copy(alpha = ADD_ZONE_HINT_ALPHA),
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.width(ADD_ZONE_TEXT_GAP_DP.dp))
                    Box(
                        modifier = Modifier
                            .size(ZONE_CHIP_EDIT_TAP_DP.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(Res.string.home_header_add_zone_dismiss_cd),
                            tint = cs.onSurfaceVariant,
                            modifier = Modifier.size(ZONE_CHIP_EDIT_DP.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * THE height of everything that rides the zone row — chips and the add chip alike.
 *
 * It used to be emergent (3 + the pencil's 28dp touch box + 3 = 34), which is why the add "+"
 * shipped at 32: there was no number to match, so someone picked one. Declared here, next to the
 * chip that defines the row's rhythm, so the two can only agree.
 * [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
 */

private const val ZONE_CHIP_RADIUS_DP = 999
private const val ZONE_CHIP_LOCK_DP = 13
private const val ADD_ZONE_TEXT_GAP_DP = 8

/**
 * Air above and below the invitation's two lines.
 *
 * Both lines are 12sp in a 16dp line box, so the text block is a solid 32dp — at 5dp it left the
 * pill 42dp tall with the lines running the full height of a shape whose ends are round, and the
 * first and last line read as pressed against the curve. 7dp is the smallest bump that reads as
 * breathing (46dp); more would start making the invitation a card instead of a chip.
 *
 * Only the EXPANDED state pays it: this padding lives inside the invitation's own
 * `AnimatedVisibility`, so collapsed the chip is still exactly the [PapControlHeight.chip] circle the
 * row is built on. [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
 */
private const val ADD_ZONE_TEXT_PAD_V_DP = 7

/**
 * Aire a cada lado del "+", el mismo que un chip de zona da a su icono principal.
 *
 * Colapsado hace la píldora de 12 + 18 + 12 = 42dp sobre un alto de [PapControlHeight.chip], y expandido
 * deja el glifo a 21dp del borde — donde está el del vecino. Un solo número para los dos estados:
 * antes eran un cuadrado (círculo) y un inset animado de 4dp que existía sólo para corregirlo.
 * [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001] [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
 */
private const val ADD_ZONE_GLYPH_PAD_H_DP = 12

/** The same trailing inset [ZoneChip] gives its pencil — 4 + the glyph's own 6 = 10dp of air. */
private const val ADD_ZONE_TRAILING_PAD_DP = 4
private val ADD_ZONE_HINT_ALPHA = PapAlpha.muted
private const val ZONE_CHIP_EDIT_DP = 16
private const val ZONE_CHIP_EDIT_TAP_DP = 28
private val ZONE_CHIP_TRAILING_ALPHA = PapAlpha.muted
