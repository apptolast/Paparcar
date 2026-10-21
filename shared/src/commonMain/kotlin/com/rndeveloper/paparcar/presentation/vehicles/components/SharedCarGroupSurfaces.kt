@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rndeveloper.paparcar.presentation.vehicles.components
import paparcar.composeapp.generated.resources.shared_car_routes_question_title
import paparcar.composeapp.generated.resources.shared_car_routes_question_subtitle
import com.rndeveloper.paparcar.ui.components.PapSwitchRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Route
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.SharedCarRole
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarInviteCode
import com.rndeveloper.paparcar.presentation.vehicles.JoinSharedCarDialog
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapShimmerBlockScale
import com.rndeveloper.paparcar.ui.components.PapShimmerBox
import com.rndeveloper.paparcar.ui.components.PapTextField
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_delete_cancel
import paparcar.composeapp.generated.resources.shared_car_error_already_joined
import paparcar.composeapp.generated.resources.shared_car_error_code_expired
import paparcar.composeapp.generated.resources.shared_car_error_code_not_found
import paparcar.composeapp.generated.resources.shared_car_error_code_superseded
import paparcar.composeapp.generated.resources.shared_car_error_code_used
import paparcar.composeapp.generated.resources.shared_car_error_group_full
import paparcar.composeapp.generated.resources.shared_car_error_too_many_joined
import paparcar.composeapp.generated.resources.shared_car_error_too_many_shared
import paparcar.composeapp.generated.resources.shared_car_error_not_owner
import paparcar.composeapp.generated.resources.shared_car_code_body
import paparcar.composeapp.generated.resources.shared_car_code_new
import paparcar.composeapp.generated.resources.shared_car_code_none
import paparcar.composeapp.generated.resources.shared_car_code_title
import paparcar.composeapp.generated.resources.shared_car_error_sync
import paparcar.composeapp.generated.resources.shared_car_pairing_hint
import paparcar.composeapp.generated.resources.shared_car_routes_sub
import paparcar.composeapp.generated.resources.shared_car_routes_title
import paparcar.composeapp.generated.resources.shared_car_join_body
import paparcar.composeapp.generated.resources.shared_car_join_cta
import paparcar.composeapp.generated.resources.shared_car_join_field
import paparcar.composeapp.generated.resources.shared_car_join_title
import paparcar.composeapp.generated.resources.shared_car_leave
import paparcar.composeapp.generated.resources.shared_car_member_no_routes
import paparcar.composeapp.generated.resources.shared_car_member_owner
import paparcar.composeapp.generated.resources.shared_car_member_remove
import paparcar.composeapp.generated.resources.shared_car_member_unnamed
import paparcar.composeapp.generated.resources.shared_car_member_you
import paparcar.composeapp.generated.resources.shared_car_members_title
import paparcar.composeapp.generated.resources.shared_car_stop_sharing

/**
 * Quién conduce este coche, y la puerta de salida. [VEH-SHARED-001]
 *
 * Una hoja y no una pantalla: se abre desde la fila de miembros de la página del coche, dice lo
 * poco que hay que decir y se cierra. Aquí viven las dos acciones que ACABAN con algo —salir y
 * disolver—, lejos del cuerpo de la página, porque una acción irreversible no se pone al lado de un
 * interruptor.
 */
@Composable
fun SharedCarMembersSheet(
    members: List<SharedCarMember>,
    currentUid: String?,
    isOwner: Boolean,
    working: Boolean,
    onRemove: (String) -> Unit,
    onLeave: () -> Unit,
    onStopSharing: () -> Unit,
    onDismiss: () -> Unit,
    /** El código vivo del coche. Solo el dueño lo ve: un miembro ya está dentro, y leerlo no le
     *  cambia nada. Null = todavía no hay ninguno. */
    inviteCode: String? = null,
    onCreateInvite: () -> Unit = {},
    /** Se esta pidiendo un codigo nuevo AHORA. Solo esta accion: un «algo esta corriendo» pondria el
     *  hueco a brillar al tocar el interruptor de rutas.
     *  [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001] */
    regenerating: Boolean = false,
    /** Que el grupo vea MIS recorridos en este coche. Es una preferencia mía, no del grupo, y por
     *  eso vive aquí y no en la página. */
    shareRoutes: Boolean = true,
    onSetShareRoutes: (Boolean) -> Unit = {},
    /** Sin emparejar, este móvil detecta los aparcamientos de los demás y ninguno tuyo: la MAC del
     *  dueño no viaja, cada móvil trae la suya. [VEH-SHARED-001] */
    showPairingHint: Boolean = false,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
        ) {
            Text(
                text = stringResource(Res.string.shared_car_members_title),
                style = PaparcarType.current.sectionTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            members.forEach { member ->
                val isMe = member.uid == currentUid
                PapOutlinedCard {
                    PapListItem(
                        title = when {
                            isMe -> stringResource(Res.string.shared_car_member_you)
                            else -> member.displayName
                                ?: stringResource(Res.string.shared_car_member_unnamed)
                        },
                        subtitle = when {
                            member.role == SharedCarRole.OWNER -> stringResource(Res.string.shared_car_member_owner)
                            // Por qué a esta persona no se le dibujan rutas: dicho, no adivinado.
                            !member.sharesRoutes -> stringResource(Res.string.shared_car_member_no_routes)
                            else -> null
                        },
                        leading = {
                            PapIconTile(
                                icon = if (member.role == SharedCarRole.OWNER) Icons.Rounded.DirectionsCar
                                else PaparcarIcons.SharedCar,
                            )
                        },
                        trailing = if (isOwner && !isMe && member.role != SharedCarRole.OWNER) {
                            {
                                TextButton(onClick = { onRemove(member.uid) }, enabled = !working) {
                                    Text(
                                        text = stringResource(Res.string.shared_car_member_remove),
                                        style = PaparcarType.current.cta,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        } else null,
                    )
                }
            }

            // ── El código, compacto: título, la cifra y su acción en UNA línea, y el cuerpo debajo.
            // Solo el dueño. Respiraba de más para lo que dice (user, 16-09).
            // [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
            if (isOwner) {
                PapOutlinedCard {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(CODE_PAD_DP.dp),
                        verticalArrangement = Arrangement.spacedBy(CODE_GAP_DP.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.shared_car_code_title),
                            style = PaparcarType.current.rowTitle,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(CODE_ROW_GAP_DP.dp),
                        ) {
                            // La fila reserva SU altura pase lo que pase, porque las tres caras del
                            // hueco —código, shimmer y «sin código»— miden distinto y la card se
                            // estiraba al cambiar de una a otra.
                            // [UI-ACTIVITY-HEADER-RESERVES-ITS-HEIGHT-001]
                            Box(
                                modifier = Modifier.height(CODE_SLOT_HEIGHT_DP.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) {
                                when {
                                    // ⛔ El shimmer es de ESTA acción: `regenerating` mira la acción
                                    // nombrada, no un «algo está corriendo» que lo encendería al
                                    // tocar el interruptor de rutas.
                                    // [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
                                    regenerating -> PapShimmerBox(
                                        modifier = Modifier
                                            .width(CODE_SHIMMER_WIDTH_DP.dp)
                                            .height(CODE_SHIMMER_HEIGHT_DP.dp),
                                        shape = RoundedCornerShape(CODE_SHIMMER_CORNER_DP.dp),
                                        alphaScale = PapShimmerBlockScale,
                                    )

                                    inviteCode != null -> Text(
                                        text = inviteCode,
                                        // La única cifra de esta feature que protagoniza su bloque:
                                        // se dicta carácter a carácter.
                                        // [UI-TYPE-TWO-VOICES-ONE-ROW-001]
                                        style = PaparcarType.current.statNumber,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )

                                    // ⛔ La AUSENCIA de código no es una cifra, así que no lleva la
                                    // voz CIFRA. Pintada a 25sp no cabía junto al botón, partía en
                                    // dos líneas y estiraba la card — que es el salto feo que se vio
                                    // en device al pedir un código nuevo (user, 16-09).
                                    else -> Text(
                                        text = stringResource(Res.string.shared_car_code_none),
                                        style = PaparcarType.current.body,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            TextButton(onClick = onCreateInvite, enabled = !working) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(CODE_ICON_DP.dp),
                                )
                                Text(
                                    text = stringResource(Res.string.shared_car_code_new),
                                    style = PaparcarType.current.cta,
                                    modifier = Modifier.padding(start = CODE_ICON_GAP_DP.dp),
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

            // ── Mis rutas en este coche: una preferencia MÍA, aquí junto a lo demás que gestiono.
            PapOutlinedCard {
                PapListItem(
                    title = stringResource(Res.string.shared_car_routes_title),
                    subtitle = stringResource(Res.string.shared_car_routes_sub),
                    leading = { PapIconTile(icon = Icons.Rounded.Route) },
                    trailing = {
                        Switch(
                            checked = shareRoutes,
                            onCheckedChange = onSetShareRoutes,
                            enabled = !working,
                        )
                    },
                )
            }

            if (showPairingHint) {
                Text(
                    text = stringResource(Res.string.shared_car_pairing_hint),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = HINT_PAD_DP.dp),
                )
            }

            // ── La salida, separada de verdad.
            // ⛔ Este bloque existía para que una acción irreversible no quedara al lado de un
            // interruptor — y al mudar el de rutas DENTRO de la hoja, esa frase dejó de ser cierta.
            // Así que se separa con un divisor y estrena confirmación: disolver el grupo de cuatro
            // personas no puede seguir a un solo toque.
            // [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
            Spacer(Modifier.height(EXIT_GAP_DP.dp))
            PapDivider()
            TextButton(
                onClick = { if (isOwner) onStopSharing() else onLeave() },
                enabled = !working,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(
                        if (isOwner) Res.string.shared_car_stop_sharing else Res.string.shared_car_leave,
                    ),
                    style = PaparcarType.current.cta,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Teclear los seis caracteres que alguien te ha dictado. [VEH-SHARED-001]
 *
 * ⛔ El rechazo va DEBAJO del campo y no en un snackbar: el usuario sigue mirando lo que acaba de
 * escribir, y «ese código ha caducado» es una instrucción para reescribir, no un aviso que se va
 * solo. Cada motivo trae su remedio — pedir otro código, o mirar bien el que hay.
 */
@Composable
fun JoinSharedCarDialogContent(
    dialog: JoinSharedCarDialog,
    onCodeChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    /** La pregunta de rutas, aquí y no después: entrar es el momento en que se decide qué compartes.
     *  [COPY-A-CARD-THAT-PROMISES-ROUTES-STAY-HOME-001] */
    onShareRoutesChanged: (Boolean) -> Unit,
) {
    PapAlertDialog(
        onDismiss = onDismiss,
        icon = PaparcarIcons.SharedCar,
        title = stringResource(Res.string.shared_car_join_title),
        body = stringResource(Res.string.shared_car_join_body),
        primaryLabel = stringResource(Res.string.shared_car_join_cta),
        onPrimary = onSubmit,
        primaryEnabled = dialog.canSubmit,
        cancelLabel = stringResource(Res.string.my_car_delete_cancel),
        content = {
            PapTextField(
                value = dialog.code,
                onValueChange = onCodeChanged,
                label = stringResource(Res.string.shared_car_join_field),
                // Caracteres, no frases: un código no tiene palabras que capitalizar ni corrector
                // que pueda ayudar.
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                maxChars = SharedCarInviteCode.LENGTH,
                isError = dialog.error != null,
                errorMessage = dialog.error?.let { sharedCarErrorMessage(it) },
                enabled = !dialog.working,
                modifier = Modifier.fillMaxWidth(),
            )
            PapSwitchRow(
                label = stringResource(Res.string.shared_car_routes_question_title),
                description = stringResource(Res.string.shared_car_routes_question_subtitle),
                checked = dialog.shareRoutes,
                onCheckedChange = onShareRoutesChanged,
                enabled = !dialog.working,
            )
        },
    )
}

/**
 * Cada rechazo con su remedio. [VEH-SHARED-001]
 *
 * Un «algo ha ido mal» compartido sería lo contrario de lo que son estos casos: cinco cosas
 * distintas sobre las que el usuario SÍ puede hacer algo. [feedback_no_internals_in_user_copy]
 */
@Composable
fun sharedCarErrorMessage(error: PaparcarError): String = when (error) {
    PaparcarError.SharedCar.InviteNotFound -> stringResource(Res.string.shared_car_error_code_not_found)
    PaparcarError.SharedCar.InviteExpired -> stringResource(Res.string.shared_car_error_code_expired)
    PaparcarError.SharedCar.InviteAlreadyUsed -> stringResource(Res.string.shared_car_error_code_used)
    PaparcarError.SharedCar.InviteSuperseded -> stringResource(Res.string.shared_car_error_code_superseded)
    PaparcarError.SharedCar.GroupFull -> stringResource(Res.string.shared_car_error_group_full)
    PaparcarError.SharedCar.TooManySharedCars ->
        stringResource(Res.string.shared_car_error_too_many_shared)
    PaparcarError.SharedCar.TooManyJoinedCars ->
        stringResource(Res.string.shared_car_error_too_many_joined)
    PaparcarError.SharedCar.AlreadyJoined -> stringResource(Res.string.shared_car_error_already_joined)
    PaparcarError.SharedCar.NotTheOwner -> stringResource(Res.string.shared_car_error_not_owner)
    else -> stringResource(Res.string.shared_car_error_sync)
}

// Card del codigo, compacta: la del artifact respira menos que la que tenia la pagina.
private const val CODE_PAD_DP = 14
private const val CODE_GAP_DP = 4
private const val CODE_ROW_GAP_DP = 4
private const val CODE_ICON_DP = 18
private const val CODE_ICON_GAP_DP = 6
/** Alto reservado del hueco del codigo: lo que ocupa la voz CIFRA, para que las tres caras
 *  —codigo, shimmer y «sin codigo»— no cambien la altura de la card. */
private const val CODE_SLOT_HEIGHT_DP = 34
private const val CODE_SHIMMER_WIDTH_DP = 124
private const val CODE_SHIMMER_HEIGHT_DP = 24
private const val CODE_SHIMMER_CORNER_DP = 6
private const val HINT_PAD_DP = 4
/** Aire antes del divisor que aisla la salida del resto de la hoja. */
private const val EXIT_GAP_DP = 8
