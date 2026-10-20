package com.rndeveloper.paparcar.presentation.vehicles.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapControlHeight
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PapPageRhythm
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapNavChevron
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import com.rndeveloper.paparcar.ui.theme.PapMotion
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.shared_car_member_unnamed
import paparcar.composeapp.generated.resources.shared_car_member_you
import paparcar.composeapp.generated.resources.shared_car_members_title
import paparcar.composeapp.generated.resources.shared_car_row_sub_joined
import paparcar.composeapp.generated.resources.shared_car_row_sub_not_shared
import paparcar.composeapp.generated.resources.shared_car_row_sub_owner
import paparcar.composeapp.generated.resources.shared_car_scope_group
import paparcar.composeapp.generated.resources.shared_car_scope_mine
import paparcar.composeapp.generated.resources.shared_car_share_cta

/**
 * El grupo de este coche, dentro de la página de este coche. [VEH-SHARED-001]
 *
 * ⛔ **No hay pantalla de grupo** (decisión del user, 15-09): un coche compartido es un coche más
 * del pager y lo que el grupo añade es una SECCIÓN suya. Una pantalla aparte obligaba a salir del
 * coche para saber quién lo conduce, que es justo lo que la pestaña de Vehículos ya contesta.
 *
 * ⛔ **En la página vive lo que se MIRA; en la hoja, lo que se GESTIONA**
 * [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]. Aquí quedan dos cosas y solo dos: quién
 * conduce el coche, y el alcance de las cifras e historial de debajo. Lo demás se mudó:
 *
 * - **«Dónde está el coche» se borró.** Lo decía por segunda vez —y peor: la hero card lee la sesión
 *   local y esta card leía el espejo, así que cuando lo aparcaba otro las dos se contradecían—. Su
 *   único dato propio, quién lo dejó, viaja ahora dentro de la hero card («lo aparcó Marta»).
 * - **El código y el interruptor de rutas se fueron a la hoja de miembros**: son gestión, y el
 *   código además solo lo ve el dueño.
 * - **El encabezado «COCHE COMPARTIDO» desapareció.** Con el resto mudado encabezaría una sola
 *   fila, y arriba de la hero card competiría con «Mis vehículos» y las pills — tres niveles de
 *   cabecera antes del contenido. Que el coche es compartido ya lo dicen el glifo junto al nombre
 *   y esta misma fila.
 *
 * Los chips de alcance SÍ se quedan: gobiernan las cifras y el historial que vienen justo debajo, y
 * moverlos los alejaría de lo que mandan.
 */
@Composable
fun SharedCarSection(
    sharedCar: SharedCar?,
    members: List<SharedCarMember>,
    currentUid: String?,
    isOwner: Boolean,
    mineOnly: Boolean,
    working: Boolean,
    onOfferShare: () -> Unit,
    onOpenMembers: () -> Unit,
    onSetScope: (mineOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Un coche pasa a compartido —o deja de serlo— con la pantalla delante, y el bloque entero se
    // sustituye por otro de altura distinta. Sin transición eso es un salto.
    // [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
    //
    // `SizeTransform` es la mitad que importa: lo que se nota no es el fundido, es que la altura
    // cambie de golpe y empuje el contenido de debajo. Con `clip = false` el contenido no se recorta
    // contra la caja mientras esta crece.
    //
    // ⛔ Fundido simple y no un deslizamiento: aquí las dos caras SON la misma cosa en dos estados,
    // no dos sitios entre los que se navega. Un slide diría que llegas a otra parte.
    AnimatedContent(
        targetState = sharedCar != null,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(PapMotion.medium()) togetherWith fadeOut(PapMotion.fast()))
                .using(SizeTransform(clip = false) { _, _ -> PapMotion.medium() })
        },
        label = "shared-car-section",
    ) { isShared ->
        // ⛔ El hueco se pide por lo que se VE, no por lo que se escribe: debajo de la card viene
        // una fila de CHIPS, y un chip de 34 dp arrastra 7 dp de aire invisible por el mínimo
        // táctil de Material. Con `spacedBy(8)` se veían 15 — casi el doble que entre dos cards de
        // Ajustes, que es exactamente lo que el user señaló aquí.
        // [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001] [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(
                PapGap.betweenControls(
                    visible = PapPageRhythm.block,
                    controlHeight = PapControlHeight.chip,
                    sides = 1,
                ),
            ),
        ) {
            if (!isShared) {
                PapOutlinedCard(
                    modifier = Modifier.clickable(enabled = !working, onClick = onOfferShare),
                ) {
                    PapListItem(
                        title = stringResource(Res.string.shared_car_share_cta),
                        subtitle = stringResource(Res.string.shared_car_row_sub_not_shared),
                        leading = { PapIconTile(icon = Icons.Rounded.Group) },
                        trailing = { PapNavChevron() },
                    )
                }
                return@Column
            }

            // Los nombres se resuelven ANTES de la fila: `stringResource` es composable y no se
            // puede llamar dentro de un `joinToString`.
            val me = stringResource(Res.string.shared_car_member_you)
            val unnamed = stringResource(Res.string.shared_car_member_unnamed)
            val roleLine = stringResource(
                if (isOwner) Res.string.shared_car_row_sub_owner else Res.string.shared_car_row_sub_joined,
            )
            val memberLine = members.joinToString(", ") { member ->
                when {
                    member.uid == currentUid -> me
                    else -> member.displayName?.takeIf { it.isNotBlank() } ?: unnamed
                }
            }.ifBlank { roleLine }
            PapOutlinedCard(
                modifier = Modifier.clickable(enabled = !working, onClick = onOpenMembers),
            ) {
                PapListItem(
                    title = stringResource(Res.string.shared_car_members_title),
                    subtitle = memberLine,
                    leading = { PapIconTile(icon = Icons.Rounded.Group) },
                    trailing = { PapNavChevron() },
                )
            }

            // El alcance de lo que hay DEBAJO —cifras e historial— dicho justo encima de ellos.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PaparcarFilterChip(
                    selected = !mineOnly,
                    onClick = { onSetScope(false) },
                    label = stringResource(Res.string.shared_car_scope_group),
                )
                PaparcarFilterChip(
                    selected = mineOnly,
                    onClick = { onSetScope(true) },
                    label = stringResource(Res.string.shared_car_scope_mine),
                )
            }
        }
    }
}
