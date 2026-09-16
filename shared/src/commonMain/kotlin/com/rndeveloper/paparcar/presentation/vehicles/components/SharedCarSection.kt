package com.rndeveloper.paparcar.presentation.vehicles.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapNavChevron
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.shared_car_code_body
import paparcar.composeapp.generated.resources.shared_car_code_new
import paparcar.composeapp.generated.resources.shared_car_code_none
import paparcar.composeapp.generated.resources.shared_car_code_title
import paparcar.composeapp.generated.resources.shared_car_member_unnamed
import paparcar.composeapp.generated.resources.shared_car_member_you
import paparcar.composeapp.generated.resources.shared_car_members_title
import paparcar.composeapp.generated.resources.shared_car_pairing_hint
import paparcar.composeapp.generated.resources.shared_car_routes_sub
import paparcar.composeapp.generated.resources.shared_car_routes_title
import paparcar.composeapp.generated.resources.shared_car_row_sub_joined
import paparcar.composeapp.generated.resources.shared_car_row_sub_not_shared
import paparcar.composeapp.generated.resources.shared_car_row_sub_owner
import paparcar.composeapp.generated.resources.shared_car_scope_group
import paparcar.composeapp.generated.resources.shared_car_scope_mine
import paparcar.composeapp.generated.resources.shared_car_share_cta
import paparcar.composeapp.generated.resources.shared_car_title
import paparcar.composeapp.generated.resources.shared_car_where_empty
import paparcar.composeapp.generated.resources.shared_car_where_left
import paparcar.composeapp.generated.resources.shared_car_where_parked_by
import paparcar.composeapp.generated.resources.shared_car_where_title

/**
 * El grupo de este coche, dentro de la página de este coche. [VEH-SHARED-001]
 *
 * ⛔ **No hay pantalla de grupo** (decisión del user, 15-09): un coche compartido es un coche más
 * del pager y lo que el grupo añade es una SECCIÓN suya, igual que las cifras o el historial. Una
 * pantalla aparte obligaba a salir del coche para saber quién lo conduce, que es justo lo que la
 * pestaña de Vehículos ya contesta.
 *
 * En un coche sin grupo la sección NO existe: solo queda la fila que lo ofrece, para que quien no
 * use la función no vea nada. El estado («lo dejó Marta») aparece solo cuando lo aparcó OTRO — si
 * lo dejaste tú, la card de arriba ya lo dice, y repetirlo sería la misma frase dos veces.
 */
@Composable
fun SharedCarSection(
    vehicle: Vehicle,
    sharedCar: SharedCar?,
    members: List<SharedCarMember>,
    currentUid: String?,
    isOwner: Boolean,
    mineOnly: Boolean,
    working: Boolean,
    onOfferShare: () -> Unit,
    onOpenMembers: () -> Unit,
    onCreateInvite: () -> Unit,
    onSetShareRoutes: (Boolean) -> Unit,
    onSetScope: (mineOnly: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (sharedCar == null) {
            PapOutlinedCard(modifier = Modifier.clickable(enabled = !working, onClick = onOfferShare)) {
                PapListItem(
                    title = stringResource(Res.string.shared_car_share_cta),
                    subtitle = stringResource(Res.string.shared_car_row_sub_not_shared),
                    leading = { PapIconTile(icon = Icons.Rounded.Group) },
                    trailing = { PapNavChevron() },
                )
            }
            return@Column
        }

        PapSectionHeader(stringResource(Res.string.shared_car_title))

        // Dónde está el coche, y solo cuando lo dejó OTRO: si lo aparcaste tú, la card de arriba
        // ya lo dice y repetirlo sería la misma frase dos veces. Un grupo recién abierto SÍ lo
        // cuenta — «nadie lo ha aparcado todavía» es una respuesta, un hueco no.
        val parked = sharedCar.state
        if (parked == null || parked.authorUid != currentUid) {
            PapOutlinedCard {
                PapListItem(
                    title = parked?.address?.displayLine
                        ?: stringResource(Res.string.shared_car_where_title),
                    overline = parked?.address?.displayLine?.let {
                        stringResource(Res.string.shared_car_where_title)
                    },
                    subtitle = when {
                        parked == null -> stringResource(Res.string.shared_car_where_empty)
                        !parked.isParked -> stringResource(Res.string.shared_car_where_left)
                        else -> stringResource(
                            Res.string.shared_car_where_parked_by,
                            parked.authorName.orUnnamed(),
                        ) + " · " + relativeTimeText(parked.parkedAtMs)
                    },
                    leading = { PapIconTile(icon = Icons.Rounded.LocationOn) },
                )
            }
        }

        // Los nombres se resuelven ANTES de la fila: `stringResource` es composable y no se puede
        // llamar dentro de un `joinToString`.
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
        PapOutlinedCard(modifier = Modifier.clickable(enabled = !working, onClick = onOpenMembers)) {
            PapListItem(
                title = stringResource(Res.string.shared_car_members_title),
                subtitle = memberLine,
                leading = { PapIconTile(icon = Icons.Rounded.Group) },
                trailing = { PapNavChevron() },
            )
        }

        if (isOwner) {
            PapOutlinedCard {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.shared_car_code_title),
                        style = PaparcarType.current.rowTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = sharedCar.activeInviteCode
                                ?: stringResource(Res.string.shared_car_code_none),
                            // La única cifra de esta feature que protagoniza su bloque: se dicta
                            // carácter a carácter. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
                            style = PaparcarType.current.statNumber,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        TextButton(
                            onClick = onCreateInvite,
                            enabled = !working,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Text(
                                text = stringResource(Res.string.shared_car_code_new),
                                style = PaparcarType.current.cta,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                    Text(
                        text = stringResource(Res.string.shared_car_code_body),
                        style = PaparcarType.current.caption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        PapOutlinedCard {
            PapListItem(
                title = stringResource(Res.string.shared_car_routes_title),
                subtitle = stringResource(Res.string.shared_car_routes_sub),
                leading = { PapIconTile(icon = Icons.Rounded.Route) },
                trailing = {
                    Switch(
                        checked = vehicle.shareRoutesInGroup,
                        onCheckedChange = { onSetShareRoutes(it) },
                        enabled = !working,
                    )
                },
            )
        }

        // Un coche compartido sin emparejar detecta los aparcamientos de los DEMÁS y ninguno tuyo:
        // la MAC del dueño no viaja, así que cada móvil trae el suyo. [VEH-SHARED-001]
        if (vehicle.bluetoothDeviceId.isNullOrBlank()) {
            Text(
                text = stringResource(Res.string.shared_car_pairing_hint),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
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

/** Alguien del grupo cuyo perfil no trae nombre: sigue siendo alguien, no un hueco. */
@Composable
private fun String?.orUnnamed(): String =
    this?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.shared_car_member_unnamed)
