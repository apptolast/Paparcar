package com.rndeveloper.paparcar.presentation.home.sections.sheet.components.peek

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.domain.model.ZoneIcon
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheet
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetBanner
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetEyebrowTone
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.PapSheetLead
import com.rndeveloper.paparcar.presentation.util.radiusString
import com.rndeveloper.paparcar.presentation.util.zoneIconFor
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapClearIconButton
import com.rndeveloper.paparcar.ui.components.PapDialogAccent
import com.rndeveloper.paparcar.ui.components.PapDialogAction
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapFooterButtonStyle
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_release_dialog_cancel
import paparcar.composeapp.generated.resources.home_pin_no_place_sub
import paparcar.composeapp.generated.resources.home_pin_no_place_title
import paparcar.composeapp.generated.resources.home_zone_action_delete
import paparcar.composeapp.generated.resources.home_zone_delete_confirm_body
import paparcar.composeapp.generated.resources.home_zone_delete_confirm_title
import paparcar.composeapp.generated.resources.home_zone_edit_header_label
import paparcar.composeapp.generated.resources.home_zone_header_label
import paparcar.composeapp.generated.resources.home_zone_helper_primary_create
import paparcar.composeapp.generated.resources.home_zone_helper_primary_edit
import paparcar.composeapp.generated.resources.home_zone_helper_secondary
import paparcar.composeapp.generated.resources.home_zone_name_dialog_body
import paparcar.composeapp.generated.resources.home_zone_name_dialog_title
import paparcar.composeapp.generated.resources.home_zone_name_placeholder
import paparcar.composeapp.generated.resources.home_zone_private_hint
import paparcar.composeapp.generated.resources.home_zone_form_notify_label
import paparcar.composeapp.generated.resources.home_zone_private_label
import paparcar.composeapp.generated.resources.home_zone_radius_section
import paparcar.composeapp.generated.resources.home_zone_save_action
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PapIconSize
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing

// ═════════════════════════════════════════════════════════════════════════════
// AddingZonePeek — "Nueva zona habitual" (create + edit). [HOME-ATOMIZE-001 F3]
//
// Same anatomy as its siblings (report a spot / position a parking): a banner
// telling you what the map is for, ONE row of choices, the settings, one filled
// action — plus, in edit, the destructive escape behind a confirm.
//
// Qué se queda aquí y qué baja al diálogo lo decide UNA pregunta: ¿se decide MIRANDO el mapa?
// Sí ⇒ la hoja (dónde · qué icono · cuánto radio). No ⇒ el paso de guardar. El nombre ya vivía
// ahí por eso mismo ("es la última decisión"), y privada + aviso son lo mismo: propiedades de la
// zona guardada, no de la colocación. Estaban en la hoja costando 88 dp de mapa a una superficie
// cuya primera frase es "centra el mapa".
// [UI-ZONE-MANAGE-001] [UI-A-SHEET-THAT-ASKS-FOR-THE-MAP-MUST-NOT-EAT-IT-001]
// ═════════════════════════════════════════════════════════════════════════════

private const val ZONE_ICON_CHIP_DP = 40
private val ZONE_ICON_GLYPH_DP = 20.dp
private val SECTION_LABEL_ALPHA = PapAlpha.subtitle

/** The zone form's live values, projected from the peek slice by the orchestrator. */
@Immutable
internal data class ZonePeekForm(
    val name: String,
    val iconKey: String,
    val radius: Float,
    val isPrivate: Boolean,
    /** Plus zone-alert opt-in — the VM gates ENABLING it behind the entitlement. [MONEY-001] */
    val notifyOnSpot: Boolean,
    /** Non-null while an existing zone is being edited — it is also what "delete" acts on. */
    val editingZoneId: String?,
    val isSaving: Boolean,
    /** True while the delete write for [editingZoneId] is in flight — the confirm dialog stays up
     *  with its spinner until it lands. [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001] */
    val isDeleting: Boolean = false,
) {
    val isEditing: Boolean get() = editingZoneId != null
}

@Composable
internal fun AddingZonePeek(
    title: String,
    form: ZonePeekForm,
    isCameraMoving: Boolean,
    /** Si el pin está sobre un sitio real. [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001] */
    hasPlace: Boolean,
    onIntent: (HomeIntent) -> Unit,
) {
    val headerLabel = if (form.isEditing) {
        stringResource(Res.string.home_zone_edit_header_label)
    } else {
        stringResource(Res.string.home_zone_header_label)
    }
    val helperPrimary = if (form.isEditing) {
        stringResource(Res.string.home_zone_helper_primary_edit)
    } else {
        stringResource(Res.string.home_zone_helper_primary_create)
    }

    // Naming survives a failed save: the VM stays in AddingZone with the form intact,
    // so the dialog is still up for the retry. [BUG-8]
    //
    // ⛔ rememberSaveable, no remember: el aviso de plazas se pide DENTRO de este diálogo y en free
    // abre el paywall, que es una PANTALLA (`HomeEffect.NavigateToPaywall`). Con un `remember` la
    // vuelta del paywall recomponía la hoja sin diálogo, así que la decisión que lo abrió se
    // perdía justo al volver de pagarla. [UI-A-SHEET-THAT-ASKS-FOR-THE-MAP-MUST-NOT-EAT-IT-001]
    var naming by rememberSaveable { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    PapSheet(
        lead = PapSheetLead.GenericIcon(icon = zoneIconFor(form.iconKey)),
        eyebrow = headerLabel.uppercase(),
        eyebrowTone = PapSheetEyebrowTone.Action,
        title = title,
        onDismiss = { onIntent(HomeIntent.ExitAddZoneMode) },
        banner = {
            // La razón SUSTITUYE al banner. [HOME-AN-UNSEEDED-CAMERA-IS-NOT-A-PLACE-001]
            if (hasPlace) {
                PapSheetBanner(
                    title = helperPrimary,
                    subtitle = stringResource(Res.string.home_zone_helper_secondary),
                )
            } else {
                PapSheetBanner(
                    title = stringResource(Res.string.home_pin_no_place_title),
                    subtitle = stringResource(Res.string.home_pin_no_place_sub),
                    icon = Icons.Rounded.LocationOff,
                    maxLines = Int.MAX_VALUE,
                )
            }
        },
        // A row of icons IS the label — a "Icon" header above it only names what is
        // already visible, and the space is worth more to the form. [UI-ZONE-MANAGE-001]
        chips = {
            ZoneIconPickerRow(
                selectedKey = form.iconKey,
                onSelect = { onIntent(HomeIntent.UpdateAddingZoneIcon(it)) },
            )
        },
        content = {
            ZoneRadiusSlider(
                radius = form.radius,
                onRadiusChange = { onIntent(HomeIntent.SetZoneRadius(it)) },
            )
        },
        actions = {
            PapFooterButton(
                label = stringResource(Res.string.home_zone_save_action),
                leadingIcon = Icons.Rounded.Bookmark,
                onClick = { naming = true },
                style = PapFooterButtonStyle.Filled,
                // The pin has to be settled before the name is asked — a zone saved
                // mid-fling lands wherever the camera happened to be.
                enabled = !form.isSaving && !isCameraMoving && hasPlace,
                // No spinner here: this button OPENS the naming dialog, it does not save. The save
                // is fired from inside that dialog and reports its progress there, on the button
                // that was actually pressed. [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
                modifier = Modifier.fillMaxWidth(),
            )
            if (form.isEditing) {
                Spacer(Modifier.height(PaparcarSpacing.sm))
                // The one sanctioned destructive red, behind a confirm — same contract as
                // "delete record" in the parking modal. [UI-SHEET-004]
                PapFooterButton(
                    label = stringResource(Res.string.home_zone_action_delete),
                    leadingIcon = Icons.Rounded.Delete,
                    onClick = { confirmingDelete = true },
                    style = PapFooterButtonStyle.Outlined,
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.error,
                    enabled = !form.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )

    if (naming) {
        ZoneSaveDialog(
            form = form,
            onIntent = onIntent,
            onDismiss = { if (!form.isSaving) naming = false },
        )
    }

    if (confirmingDelete) {
        PapAlertDialog(
            onDismiss = { if (!form.isDeleting) confirmingDelete = false },
            accent = PapDialogAccent.Destructive,
            icon = Icons.Rounded.Delete,
            title = stringResource(Res.string.home_zone_delete_confirm_title),
            body = stringResource(Res.string.home_zone_delete_confirm_body),
            primaryLabel = stringResource(Res.string.home_zone_action_delete),
            primaryLeadingIcon = Icons.Rounded.Delete,
            onPrimary = {
                // Not dismissed here: the confirm holds its own progress until the write lands, and
                // the VM closes the whole peek once the zone is actually gone — which is also what
                // takes this dialog with it. [UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001]
                form.editingZoneId?.let { onIntent(HomeIntent.DeleteZone(it)) }
            },
            loadingAction = PapDialogAction.Primary.takeIf { form.isDeleting },
            cancelLabel = stringResource(Res.string.home_release_dialog_cancel),
        )
    }
}

/**
 * El paso de GUARDAR una zona: aquí se deciden las tres cosas que definen a la zona guardada —su
 * nombre, si es privada y si avisa— una vez el sitio, el icono y el radio ya están puestos sobre el
 * mapa de detrás.
 *
 * El nombre estaba aquí desde [UI-ZONE-MANAGE-001] porque es la última decisión y porque así la
 * hoja no tiene que criar un campo de texto encima del mapa. Los dos interruptores bajaron por la
 * misma razón, y por una que se mide: la hoja que pide *«centra el mapa en tu zona habitual»* se
 * comía el 60% de la pantalla, 88 de ellos en dos filas que no se deciden mirando el mapa.
 * [UI-A-SHEET-THAT-ASKS-FOR-THE-MAP-MUST-NOT-EAT-IT-001]
 *
 * El diálogo puede crecer sin esconder nada: [PapAlertDialog] inserta por IME y hace scroll de su
 * columna desde [SUPPORT-A-REPORT-MUST-SAY-WHAT-WENT-WRONG-001].
 */
@Composable
private fun ZoneSaveDialog(
    form: ZonePeekForm,
    onIntent: (HomeIntent) -> Unit,
    onDismiss: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val onNameChange: (String) -> Unit = { onIntent(HomeIntent.UpdateAddingZoneName(it)) }
    val onConfirm: () -> Unit = { onIntent(HomeIntent.ConfirmAddZone) }

    PapAlertDialog(
        onDismiss = onDismiss,
        icon = zoneIconFor(form.iconKey),
        title = stringResource(Res.string.home_zone_name_dialog_title),
        body = stringResource(Res.string.home_zone_name_dialog_body),
        primaryLabel = stringResource(Res.string.home_zone_save_action),
        primaryLeadingIcon = Icons.Rounded.Bookmark,
        onPrimary = onConfirm,
        primaryEnabled = form.name.isNotBlank(),
        loadingAction = PapDialogAction.Primary.takeIf { form.isSaving },
        cancelLabel = stringResource(Res.string.home_release_dialog_cancel),
        content = {
            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                placeholder = { Text(stringResource(Res.string.home_zone_name_placeholder)) },
                singleLine = true,
                enabled = !form.isSaving,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (form.name.isNotBlank() && !form.isSaving) onConfirm() },
                ),
                leadingIcon = {
                    Icon(
                        imageVector = zoneIconFor(form.iconKey),
                        contentDescription = null,
                        tint = PapColor.brandData,
                    )
                },
                trailingIcon = if (form.name.isNotEmpty()) {
                    { PapClearIconButton(onClick = { onNameChange("") }) }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            // El divisor separa la decisión que tiene el foco (el nombre) de lo que la zona SERÁ.
            Spacer(Modifier.height(PaparcarSpacing.lg))
            PapDivider()
            Spacer(Modifier.height(PaparcarSpacing.lg))
            ZonePrivacyToggle(
                isPrivate = form.isPrivate,
                enabled = !form.isSaving,
                onToggle = { onIntent(HomeIntent.SetZoneIsPrivate(it)) },
            )
            Spacer(Modifier.height(PapGap.block))
            // [MONEY-001] Visible to everyone (soft gate): enabling as free is the attempt
            // that routes to the paywall — the VM decides, this row only asks.
            ZoneNotifyToggle(
                notifyOnSpot = form.notifyOnSpot,
                enabled = !form.isSaving,
                onToggle = { onIntent(HomeIntent.SetZoneNotify(it)) },
            )
        },
    )
}

@Composable
private fun ZoneIconPickerRow(
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        // Aligned with the rest of the modal content (PapSheet already insets this
        // row). On scroll the icons clip at the content box. [ZONE-AREA-001]
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
    ) {
        items(items = ZoneIcon.PRESETS, key = { it }) { key ->
            val isSelected = key == selectedKey
            Box(
                // ⛔ El area de toque era 40 dp, por debajo del minimo. Lo pintado no cambia:
                // `minimumInteractiveComponentSize()` va ANTES del `.size()` pintado, igual que
                // `IconButton` de Material3. [UI-THE-SPACING-SWEEP-BY-VOLUME-001]
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(ZONE_ICON_CHIP_DP.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                    )
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = zoneIconFor(key),
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ZONE_ICON_GLYPH_DP),
                )
            }
        }
    }
}

@Composable
private fun ZoneRadiusSlider(
    radius: Float,
    onRadiusChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PapSectionHeader(
            title = stringResource(Res.string.home_zone_radius_section),
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            // ⛔ The slider's own figure is a distance too: it said "250 m" to someone who had asked
            // the app for miles. [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]
            text = radiusString(radius),
            style = PaparcarType.current.label,
            color = PapColor.actionText,
        )
    }
    Slider(
        value = radius,
        onValueChange = onRadiusChange,
        valueRange = Zone.MIN_RADIUS_METERS..Zone.MAX_RADIUS_METERS,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ZonePrivacyToggle(
    isPrivate: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(PapIconSize.withLabel),
            )
            Column {
                Text(
                    text = stringResource(Res.string.home_zone_private_label),
                    style = PaparcarType.current.body,
                )
                Text(
                    text = stringResource(Res.string.home_zone_private_hint),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = SECTION_LABEL_ALPHA),
                )
            }
        }
        Switch(
            checked = isPrivate,
            onCheckedChange = onToggle,
            enabled = enabled,
        )
    }
}

@Composable
private fun ZoneNotifyToggle(
    notifyOnSpot: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PaparcarSpacing.sm),
        ) {
            Icon(
                imageVector = Icons.Rounded.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(PapIconSize.withLabel),
            )
            Text(
                text = stringResource(Res.string.home_zone_form_notify_label),
                style = PaparcarType.current.body,
            )
        }
        Switch(
            checked = notifyOnSpot,
            onCheckedChange = onToggle,
            enabled = enabled,
        )
    }
}
