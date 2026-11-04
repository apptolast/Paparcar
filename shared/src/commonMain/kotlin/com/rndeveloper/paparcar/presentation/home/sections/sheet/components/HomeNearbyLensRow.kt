package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import com.rndeveloper.paparcar.ui.components.papGuideHighlight
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.subject_free_spots
import paparcar.composeapp.generated.resources.subject_fuel_stations
import paparcar.composeapp.generated.resources.subject_public_parkings

/**
 * WHICH nearby subject the sheet's lane is answering — the row that replaced three stacked foldable
 * sections. [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
 *
 * **Tabs, not chips, and the difference is what it SAYS.** The first build of this row used
 * [com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip] — the same pills as the size
 * filter right below it — and on device it read exactly like what those pills are: optional criteria
 * you add up. These three are not criteria, they are the three things Paparcar can show you around
 * here. So they take the full width, one third each, and only one is ever on.
 *
 * It is rendered as a **sticky header**, which is the other half of the point: coming back from the
 * bottom of a car-park list cannot mean scrolling up past everything first. The background is painted
 * explicitly (the sheet's own [MaterialTheme.colorScheme.surfaceContainer]) because a sticky item
 * floats over the rows it pins above — transparent, the list would read through it.
 *
 * ⛔ **The three are not peers, and the asymmetry is deliberate.** Free spots are the product; car
 * parks and fuel are complements to it ([PARKINGS-ON-THE-MAP-001]). Spots lead and are the lane every
 * cold start opens on. What they do NOT get any more is more vertical distance: hierarchy lives in
 * the default, in order, and **in width** — never again in "further down the scroll".
 *
 * ⛔ **La anchura entró porque las otras dos no bastaban.** Este KDoc decía «no son pares» mientras
 * la fila repartía `weight(1f)` a las tres, así que la asimetría que declara sólo existía en el
 * default y en el orden — y ninguno de los dos se ve al mirar la fila. Ahora las plazas llevan
 * [PRODUCT_TAB_WEIGHT]. [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 *
 * The count is shown **only where it is known**: spots always (the feed is a live subscription), a
 * complement only after its lane has actually been asked. ⛔ A `0` on a subject nobody asked yet is a
 * verdict about the world no source ever gave — [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
 * applies to a tab as much as to a sentence.
 */
@Composable
internal fun HomeNearbyLensRow(
    lens: HomeNearbyLens,
    spotCount: Int,
    fuelCount: Int,
    parkingsCount: Int,
    onSelect: (HomeNearbyLens) -> Unit,
    modifier: Modifier = Modifier,
    /** Tabs a checklist step is pointing at — outlined, never selected for the user.
     *  [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] */
    highlighted: Set<HomeNearbyLens> = emptySet(),
) {
    // El divisor viaja DENTRO de la fila, no como un item suelto debajo: esta fila es un
    // `stickyHeader`, así que un divisor puesto fuera se quedaría atrás mientras la fila flota sobre
    // la lista, y la separación desaparecería justo cuando hace falta — al scrollear.
    // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            LensTab(
                subject = HomeNearbyLens.SPOTS,
                label = stringResource(Res.string.subject_free_spots),
                icon = PaparcarIcons.CommunitySpot,
                count = spotCount,
                lens = lens,
                onSelect = onSelect,
                highlighted = highlighted,
                // ⛔ MÁS ancha que las otras dos, y eso ES la jerarquía que el ⛔ de abajo
                // declara. Hasta [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001] las tres iban a
                // `weight(1f)`, así que el KDoc decía «no son pares» y la fila las pintaba
                // idénticas: la asimetría vivía sólo en el default y en el orden, y ninguna de
                // las dos se ve al mirar la fila.
                modifier = Modifier.weight(PRODUCT_TAB_WEIGHT),
            )
            LensTab(
                subject = HomeNearbyLens.PARKINGS,
                label = stringResource(Res.string.subject_public_parkings),
                icon = PaparcarIcons.CarParkMark,
                count = parkingsCount,
                lens = lens,
                onSelect = onSelect,
                highlighted = highlighted,
                modifier = Modifier.weight(1f),
            )
            LensTab(
                subject = HomeNearbyLens.FUEL,
                label = stringResource(Res.string.subject_fuel_stations),
                icon = PaparcarIcons.Fuel,
                count = fuelCount,
                lens = lens,
                onSelect = onSelect,
                highlighted = highlighted,
                modifier = Modifier.weight(1f),
            )
        }
        // La línea que separa las pestañas de SU contenido. El indicador dice en cuál estás; esto
        // dice dónde empieza lo que enseña.
        PapDivider()
    }
}

/**
 * One third of the row: the subject's own mark, its name, its count when there is one, and the
 * underline that says which one you are in.
 *
 * A tab sends the lens it IS, never a toggle: tapping the one you are on is a no-op instead of a way
 * to end up somewhere you did not choose.
 *
 * Selection is carried by COLOUR and the indicator — never by weight, which the type doctrine
 * reserves for the role. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
 */
@Composable
private fun LensTab(
    subject: HomeNearbyLens,
    label: String,
    icon: ImageVector,
    count: Int,
    lens: HomeNearbyLens,
    onSelect: (HomeNearbyLens) -> Unit,
    highlighted: Set<HomeNearbyLens>,
    modifier: Modifier = Modifier,
) {
    val selected = lens == subject
    // ⛔ El verde NO pinta la pestaña activa: la pinta el INDICADOR, y la etiqueta sube a
    // `onSurface`. Se probó lo contrario (user, 10-09) y en device fue peor: con el glifo y la
    // etiqueta en verde, la pestaña competía con la única cosa verde que tiene que llamar. El
    // subrayado ya dice «estás aquí» sin gastar la tinta más cara de la app.
    // [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
    //
    // Lo apagado que el user vio NO era esto: eran los glifos de las FILAS y la cifra del peek, que
    // se arreglan donde estaban. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
    //
    // Animated so switching lanes reads as ONE row changing its mind, not three widgets blinking.
    val content by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = PapMotion.medium(),
    )
    val indicator by animateColorAsState(
        targetValue = if (selected) PapColor.selected else Color.Transparent,
        animationSpec = PapMotion.medium(),
    )

    Column(
        modifier = modifier
            .papGuideHighlight(subject in highlighted, RoundedCornerShape(GUIDE_HIGHLIGHT_RADIUS_DP.dp))
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = { onSelect(subject) },
            )
            .padding(top = TAB_TOP_PADDING_DP.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 4.dp).height(TAB_CONTENT_HEIGHT_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (count > 0) {
                Text(
                    text = count.toString(),
                    style = PaparcarType.current.badge,
                    color = content,
                    modifier = Modifier
                        .clip(RoundedCornerShape(COUNT_RADIUS_DP.dp))
                        .background(content.copy(alpha = COUNT_BG_ALPHA))
                        .padding(horizontal = COUNT_H_PADDING_DP.dp, vertical = COUNT_V_PADDING_DP.dp),
                )
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = content,
                    modifier = Modifier.size(TAB_ICON_DP.dp),
                )
            }
            Spacer(Modifier.width(TAB_ICON_LABEL_GAP_DP.dp))
            Text(
                text = label,
                style = PaparcarType.current.label,
                color = content,
                // ⛔ One line, always: a tab that wraps changes the height of the whole row, and this
                // row is a sticky header — it would push the list under the user's finger. The names
                // are the SHORT form of each subject for exactly this reason, in all nine locales.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(TAB_INDICATOR_GAP_DP.dp))
        // The indicator, drawn for every tab and coloured only for the selected one — so the row
        // reserves its height once and nothing moves when the lane changes.
        Box(
            modifier = Modifier
                .padding(horizontal = INDICATOR_INSET_DP.dp)
                .fillMaxWidth()
                .height(INDICATOR_H_DP.dp)
                .clip(RoundedCornerShape(topStart = INDICATOR_H_DP.dp, topEnd = INDICATOR_H_DP.dp))
                .background(indicator),
        )
    }
}

/**
 * Lo que la pestaña de PLAZAS ocupa frente a cada complemento.
 * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 *
 * ⛔ **1,25 y no 1,5.** Medido sobre el ancho real de la fila: a 390 dp, con 1,25 las plazas se
 * llevan 136 dp y cada complemento 109, que es un cuarto más sin que ninguna de las dos etiquetas
 * largas —«Gasolineras», «Parkings»— pierda sitio. A 1,5 los complementos bajan a 100 dp y
 * «Gasolineras» empieza a elidir en los locales que la escriben largo, y una pestaña que no se
 * puede leer no es una jerarquía: es una esquina recortada.
 */
private const val PRODUCT_TAB_WEIGHT = 1.25f

private const val TAB_TOP_PADDING_DP = 14
private const val TAB_CONTENT_HEIGHT_DP = 24
private const val TAB_ICON_DP = 18
private const val TAB_ICON_LABEL_GAP_DP = 8
private const val TAB_INDICATOR_GAP_DP = 12
private const val INDICATOR_H_DP = 3
private const val INDICATOR_INSET_DP = 16
private const val COUNT_RADIUS_DP = 6
private const val COUNT_H_PADDING_DP = 5
private const val COUNT_V_PADDING_DP = 1
/** The count sits on its own tinted pill — the same colour as its tab, faded to a background. */
private const val COUNT_BG_ALPHA = 0.16f

// Corner of the tutorial's outline around a tab. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001]
private const val GUIDE_HIGHLIGHT_RADIUS_DP = 12
