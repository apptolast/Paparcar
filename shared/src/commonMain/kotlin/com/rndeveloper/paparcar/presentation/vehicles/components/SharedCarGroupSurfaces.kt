@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.rndeveloper.paparcar.presentation.vehicles.components
import paparcar.composeapp.generated.resources.shared_car_routes_question_title
import paparcar.composeapp.generated.resources.shared_car_routes_question_subtitle
import com.rndeveloper.paparcar.ui.components.PapSwitchRow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.error.PaparcarError
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.SharedCarRole
import com.rndeveloper.paparcar.domain.sharedcar.SharedCarInviteCode
import com.rndeveloper.paparcar.presentation.vehicles.JoinSharedCarDialog
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapTextField
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_delete_cancel
import paparcar.composeapp.generated.resources.shared_car_error_already_joined
import paparcar.composeapp.generated.resources.shared_car_error_code_expired
import paparcar.composeapp.generated.resources.shared_car_error_code_not_found
import paparcar.composeapp.generated.resources.shared_car_error_code_used
import paparcar.composeapp.generated.resources.shared_car_error_group_full
import paparcar.composeapp.generated.resources.shared_car_error_too_many_joined
import paparcar.composeapp.generated.resources.shared_car_error_too_many_shared
import paparcar.composeapp.generated.resources.shared_car_error_not_owner
import paparcar.composeapp.generated.resources.shared_car_error_sync
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
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
                                else Icons.Rounded.Group,
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

            // Salir y disolver son la misma forma de decisión — «he terminado con este grupo» — así
            // que comparten sitio, y cuál se ve lo dice quién eres.
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
        icon = Icons.Rounded.Group,
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
    PaparcarError.SharedCar.GroupFull -> stringResource(Res.string.shared_car_error_group_full)
    PaparcarError.SharedCar.TooManySharedCars ->
        stringResource(Res.string.shared_car_error_too_many_shared)
    PaparcarError.SharedCar.TooManyJoinedCars ->
        stringResource(Res.string.shared_car_error_too_many_joined)
    PaparcarError.SharedCar.AlreadyJoined -> stringResource(Res.string.shared_car_error_already_joined)
    PaparcarError.SharedCar.NotTheOwner -> stringResource(Res.string.shared_car_error_not_owner)
    else -> stringResource(Res.string.shared_car_error_sync)
}
