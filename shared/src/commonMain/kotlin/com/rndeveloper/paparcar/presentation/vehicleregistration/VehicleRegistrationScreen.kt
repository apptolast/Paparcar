package com.rndeveloper.paparcar.presentation.vehicleregistration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.presentation.vehicleregistration.data.VehicleCatalog
import com.rndeveloper.paparcar.ui.components.PapCollapsingTopBarScaffold
import com.rndeveloper.paparcar.ui.components.CarbodyInfoCard
import com.rndeveloper.paparcar.ui.components.CarbodyManualPicker
import com.rndeveloper.paparcar.ui.components.NonCarSizeBadge
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapBottomActionBar
import com.rndeveloper.paparcar.ui.components.PapDangerCard
import com.rndeveloper.paparcar.ui.components.PapDialogAccent
import com.rndeveloper.paparcar.ui.components.PapFooterButton
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.components.PapSwitchRow
import com.rndeveloper.paparcar.ui.components.PapTextField
import com.rndeveloper.paparcar.ui.components.VehicleColorSelector
import com.rndeveloper.paparcar.ui.components.label
import com.rndeveloper.paparcar.ui.components.vehicleSizeLabel
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.vehicle_reg_cd_back
import paparcar.composeapp.generated.resources.my_car_delete_cancel
import paparcar.composeapp.generated.resources.my_car_delete_confirm_action
import paparcar.composeapp.generated.resources.my_car_delete_confirm_message
import paparcar.composeapp.generated.resources.my_car_delete_confirm_title
import paparcar.composeapp.generated.resources.my_car_delete_vehicle
import paparcar.composeapp.generated.resources.veh_bt_recommendation_body
import paparcar.composeapp.generated.resources.veh_bt_recommendation_configure
import paparcar.composeapp.generated.resources.veh_bt_recommendation_skip
import paparcar.composeapp.generated.resources.veh_bt_recommendation_title
import paparcar.composeapp.generated.resources.vehicle_registration_brand_error
import paparcar.composeapp.generated.resources.vehicle_registration_brand_hint
import paparcar.composeapp.generated.resources.vehicle_registration_bt_cta
import paparcar.composeapp.generated.resources.vehicle_registration_bt_desc
import paparcar.composeapp.generated.resources.vehicle_registration_bt_title
import paparcar.composeapp.generated.resources.vehicle_registration_edit_title
import paparcar.composeapp.generated.resources.vehicle_registration_license_plate_label
import paparcar.composeapp.generated.resources.vehicle_registration_model_hint
import paparcar.composeapp.generated.resources.vehicle_registration_name_label
import paparcar.composeapp.generated.resources.vehicle_registration_name_placeholder
import paparcar.composeapp.generated.resources.vehicle_registration_preview_title
import paparcar.composeapp.generated.resources.vehicle_registration_save
import paparcar.composeapp.generated.resources.vehicle_registration_saving
import paparcar.composeapp.generated.resources.vehicle_registration_carbody_section
import paparcar.composeapp.generated.resources.vehicle_registration_section_color
import paparcar.composeapp.generated.resources.vehicle_registration_section_detection
import paparcar.composeapp.generated.resources.vehicle_registration_section_identity
import paparcar.composeapp.generated.resources.vehicle_registration_section_optional
import paparcar.composeapp.generated.resources.vehicle_registration_section_privacy
import paparcar.composeapp.generated.resources.vehicle_registration_section_size
import paparcar.composeapp.generated.resources.vehicle_registration_size_auto_detected
import paparcar.composeapp.generated.resources.vehicle_registration_size_hint
import paparcar.composeapp.generated.resources.vehicle_registration_size_required_hint
import paparcar.composeapp.generated.resources.vehicle_registration_title
import paparcar.composeapp.generated.resources.vehicle_show_on_spot
import paparcar.composeapp.generated.resources.vehicle_show_on_spot_desc

@Composable
fun VehicleRegistrationScreen(
    onRegistrationComplete: () -> Unit,
    onNavigateBack: () -> Unit = {},
    onConfigureBluetooth: (vehicleId: String) -> Unit = {},
    vehicleId: String? = null,
    viewModel: VehicleRegistrationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(Res.string.error_unknown)
    var pendingBtRecommendation: String? by remember { mutableStateOf(null) }

    LaunchedEffect(vehicleId) {
        if (vehicleId != null) {
            viewModel.handleIntent(VehicleRegistrationIntent.LoadVehicle(vehicleId))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VehicleRegistrationEffect.SavedSuccessfully -> {
                    if (effect.isNewVehicle) {
                        pendingBtRecommendation = effect.vehicleId
                    } else {
                        onRegistrationComplete()
                    }
                }
                is VehicleRegistrationEffect.NavigateBack -> onNavigateBack()
                is VehicleRegistrationEffect.ShowError ->
                    snackbarHostState.showSnackbar(errorFallback)
            }
        }
    }

    VehicleRegistrationContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::handleIntent,
        onConfigureBluetooth = {
            state.editingVehicleId?.let { onConfigureBluetooth(it) }
        },
    )

    pendingBtRecommendation?.let { newVehicleId ->
        BluetoothRecommendationDialog(
            onConfigure = {
                pendingBtRecommendation = null
                onConfigureBluetooth(newVehicleId)
            },
            onSkip = {
                pendingBtRecommendation = null
                onRegistrationComplete()
            },
        )
    }
}

@Composable
private fun BluetoothRecommendationDialog(
    onConfigure: () -> Unit,
    onSkip: () -> Unit,
) {
    PapAlertDialog(
        onDismiss = onSkip,
        icon = Icons.Rounded.Bluetooth,
        title = stringResource(Res.string.veh_bt_recommendation_title),
        body = stringResource(Res.string.veh_bt_recommendation_body),
        primaryLabel = stringResource(Res.string.veh_bt_recommendation_configure),
        onPrimary = onConfigure,
        cancelLabel = stringResource(Res.string.veh_bt_recommendation_skip),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleRegistrationContent(
    state: VehicleRegistrationState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onIntent: (VehicleRegistrationIntent) -> Unit = {},
    onConfigureBluetooth: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val isEditing = state.editingVehicleId != null
    val isNewVehicle = state.editingVehicleId == null
    val brands = remember { VehicleCatalog.brands() }

    var brandExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCarbodyPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Model catalog surfaces only when the typed brand matches a known catalog entry — the
    // user might be in "custom" mode (isBrandOther=true) but if their text exactly matches
    // a catalog brand we still want to suggest its models.
    val modelsForBrand = remember(state.brand) {
        if (state.brand.isNotBlank() && state.brand in brands) VehicleCatalog.modelsFor(state.brand)
        else emptyList()
    }
    val filteredBrands = remember(state.brand) {
        if (state.brand.isBlank()) brands
        else brands.filter { it.contains(state.brand, ignoreCase = true) }
    }
    val filteredModels = remember(state.model, modelsForBrand) {
        if (state.model.isBlank()) modelsForBrand
        else modelsForBrand.filter { it.contains(state.model, ignoreCase = true) }
    }

    val bottomHint: String? = when {
        !state.canSubmit && state.sizeCategory == null ->
            stringResource(Res.string.vehicle_registration_size_required_hint)
        !state.canSubmit && state.hasInteractedWithForm ->
            stringResource(Res.string.vehicle_registration_brand_error)
        else -> null
    }

    PapCollapsingTopBarScaffold(
        title = stringResource(
            if (isEditing) Res.string.vehicle_registration_edit_title
            else Res.string.vehicle_registration_title,
        ),
        containerColor = cs.surfaceContainer,
        navigationIcon = {
            IconButton(onClick = { onIntent(VehicleRegistrationIntent.NavigateBack) }) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(Res.string.vehicle_reg_cd_back),
                )
            }
        },
        bottomBar = {
            VehicleRegistrationBottomBar(
                isSaving = state.isSaving,
                canSubmit = state.canSubmit,
                hint = bottomHint,
                onSave = { onIntent(VehicleRegistrationIntent.Save) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                // El padding va DENTRO del scroll: el formulario arranca bajo el título y pasa por
                // debajo de la cabecera al scrollear, no se recorta contra ella. [UI-TOPBAR-COLLAPSE-001]
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(SECTION_SPACING),
        ) {
            Spacer(Modifier.height(CONTENT_TOP_SPACING))

            // ── Hero preview card ─────────────────────────────────────────────
            VehicleHeroCard(
                state = state,
                modifier = Modifier.padding(horizontal = SCREEN_H_PADDING),
            )

            // ── Identity section — brand + model (required) ───────────────────
            Column(
                modifier = Modifier.padding(horizontal = SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_identity))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = brandExpanded,
                            onExpandedChange = { brandExpanded = it },
                        ) {
                            PapTextField(
                                value = state.brand,
                                onValueChange = { value ->
                                    onIntent(VehicleRegistrationIntent.SetCustomBrand(value))
                                    brandExpanded = true
                                },
                                label = stringResource(Res.string.vehicle_registration_brand_hint),
                                isError = state.brandError,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                ),
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                            )
                            if (filteredBrands.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = brandExpanded,
                                    onDismissRequest = { brandExpanded = false },
                                ) {
                                    filteredBrands.forEach { brand ->
                                        DropdownMenuItem(
                                            text = { Text(brand) },
                                            onClick = {
                                                onIntent(VehicleRegistrationIntent.SelectBrand(brand))
                                                brandExpanded = false
                                                focusManager.clearFocus()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded && modelsForBrand.isNotEmpty(),
                            onExpandedChange = {
                                if (modelsForBrand.isNotEmpty()) modelExpanded = it
                            },
                        ) {
                            PapTextField(
                                value = state.model,
                                onValueChange = { value ->
                                    onIntent(VehicleRegistrationIntent.SetCustomModel(value))
                                    if (modelsForBrand.isNotEmpty()) modelExpanded = true
                                },
                                label = stringResource(Res.string.vehicle_registration_model_hint),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next,
                                ),
                                keyboardActions = KeyboardActions(
                                    onNext = { focusManager.moveFocus(FocusDirection.Next) },
                                ),
                                enabled = state.brand.isNotBlank(),
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                            )
                            if (filteredModels.isNotEmpty()) {
                                ExposedDropdownMenu(
                                    expanded = modelExpanded,
                                    onDismissRequest = { modelExpanded = false },
                                ) {
                                    filteredModels.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                onIntent(VehicleRegistrationIntent.SelectModel(model))
                                                modelExpanded = false
                                                focusManager.clearFocus()
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── Carbody section — auto-inferred card + manual override picker ──
            Column(
                modifier = Modifier.padding(horizontal = SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_carbody_section))
                val expectsCarbody = state.expectsCarbody
                val carbody = state.carbodyType
                val size = state.sizeCategory

                when {
                    expectsCarbody && carbody != null -> {
                        CarbodyInfoCard(
                            carbody = carbody,
                            sizeLabel = size?.label() ?: "",
                            isManualOverride = state.isCarbodyManualOverride,
                            onChange = { showCarbodyPicker = true },
                        )
                    }
                    expectsCarbody -> {
                        // Brand or model still blank — nudge the user toward filling them.
                        Text(
                            text = stringResource(Res.string.vehicle_registration_size_hint),
                            style = PaparcarType.current.caption,
                            color = cs.onSurfaceVariant,
                        )
                    }
                    size != null -> {
                        NonCarSizeBadge(sizeLabel = size.label())
                    }
                }
            }

            // ── Colour section — recolours the vehicle body icon (CAR only) ────
            if (state.expectsCarbody) {
                Column(
                    modifier = Modifier.padding(horizontal = SCREEN_H_PADDING),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_color))
                    VehicleColorSelector(
                        selected = state.color,
                        onSelect = { onIntent(VehicleRegistrationIntent.SetColor(it)) },
                    )
                }
            }

            // ── Nickname section — optional ───────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_optional))
                PapTextField(
                    value = state.name,
                    onValueChange = { onIntent(VehicleRegistrationIntent.SetName(it)) },
                    label = stringResource(Res.string.vehicle_registration_name_label),
                    placeholder = stringResource(
                        Res.string.vehicle_registration_name_placeholder,
                        state.defaultNamePlaceholderIndex,
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Next) },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                PapTextField(
                    value = state.licensePlate,
                    onValueChange = { onIntent(VehicleRegistrationIntent.SetLicensePlate(it)) },
                    label = stringResource(Res.string.vehicle_registration_license_plate_label),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { focusManager.clearFocus() },
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // ── Bluetooth section — only shown when editing an existing vehicle ──
            // For new vehicles the post-save BluetoothRecommendationDialog handles BT pairing.
            if (!isNewVehicle) {
                Column(
                    modifier = Modifier.padding(horizontal = SCREEN_H_PADDING),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_detection))
                    PapOutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        // Was a hand-rolled row with its own 38dp circular icon box — now the
                        // canonical anatomy. [UI-LIST-ITEM-001] [SETTINGS-AUDIT-REMEDIATION-001]
                        PapListItem(
                            leading = { PapIconTile(icon = Icons.Rounded.Bluetooth) },
                            title = stringResource(Res.string.vehicle_registration_bt_title),
                            subtitle = stringResource(Res.string.vehicle_registration_bt_desc),
                            subtitleColor = cs.onSurface.copy(alpha = PapAlpha.subtitle),
                            trailing = {
                                Button(
                                    onClick = onConfigureBluetooth,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                    shape = PapShapes.cardSmall,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.vehicle_registration_bt_cta),
                                        // Button text → cta (Inter), the app's button convention. [TYPO-AUDIT-001]
                                        style = PaparcarType.current.cta,
                                    )
                                }
                            },
                        )
                    }
                }
            }

            // ── Privacy section ──────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_privacy))
                PapOutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Was a verbatim re-implementation of Settings' switch row; now the shared
                    // one — whole row toggleable, label announced by TalkBack.
                    // [SETTINGS-AUDIT-REMEDIATION-001]
                    PapSwitchRow(
                        label = stringResource(Res.string.vehicle_show_on_spot),
                        description = stringResource(Res.string.vehicle_show_on_spot_desc),
                        checked = state.showBrandModelOnSpot,
                        onCheckedChange = { onIntent(VehicleRegistrationIntent.SetShowOnSpot(it)) },
                        subtitleColor = cs.onSurface.copy(alpha = PapAlpha.subtitle),
                    )
                }
            }

            // ── Delete section — only shown when editing and more than one vehicle ──
            if (!isNewVehicle && state.canDelete) {
                // Unified with Settings' danger zone — one destructive grammar. Values moved to
                // PapDangerCard (bg 0.3→0.15, border thin@0.4→medium@0.7). [SETTINGS-AUDIT-REMEDIATION-001]
                PapDangerCard(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SCREEN_H_PADDING),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            tint = cs.error,
                            modifier = Modifier.size(DELETE_ICON_SIZE),
                        )
                        Text(
                            text = stringResource(Res.string.my_car_delete_vehicle),
                            // Action row = a button → cta (Inter), like SetActiveRow. [TYPO-AUDIT-001]
                            style = PaparcarType.current.cta,
                            color = cs.error,
                        )
                    }
                }
            }

            if (showDeleteDialog) {
                PapAlertDialog(
                    onDismiss = { if (!state.isDeleting) showDeleteDialog = false },
                    icon = Icons.Rounded.Delete,
                    title = stringResource(Res.string.my_car_delete_confirm_title),
                    body = stringResource(Res.string.my_car_delete_confirm_message),
                    primaryLabel = stringResource(Res.string.my_car_delete_confirm_action),
                    primaryLeadingIcon = Icons.Rounded.Delete,
                    onPrimary = {
                        // Don't close the dialog: the VM navigates away on success and
                        // resets isDeleting on failure (dialog stays open for retry).
                        onIntent(VehicleRegistrationIntent.DeleteVehicle)
                    },
                    isLoading = state.isDeleting,
                    cancelLabel = stringResource(Res.string.my_car_delete_cancel),
                    accent = PapDialogAccent.Destructive,
                )
            }

            if (showCarbodyPicker) {
                CarbodyManualPicker(
                    selected = state.carbodyType,
                    onSelect = { body -> onIntent(VehicleRegistrationIntent.SetCarbody(body)) },
                    onDismiss = { showCarbodyPicker = false },
                )
            }

            Spacer(Modifier.height(SECTION_SPACING))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero card
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleHeroCard(
    state: VehicleRegistrationState,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme

    val heroName = when {
        state.name.isNotBlank() -> state.name
        state.brand.isNotBlank() && state.model.isNotBlank() -> "${state.brand} ${state.model}"
        state.brand.isNotBlank() -> state.brand
        else -> stringResource(Res.string.vehicle_registration_preview_title)
    }

    val sizeLabel = state.sizeCategory?.let { vehicleSizeLabel(it) }
        ?: stringResource(Res.string.vehicle_registration_size_hint)

    val sizeSelected = state.sizeCategory != null
    // Selected → native multi-colour pictogram; not-yet-picked → dimmed flat
    // placeholder so the hero reads as "choose a size". [BOLT-MARKERS-001]
    val iconTint = if (sizeSelected) Color.Unspecified
                   else cs.onSurface.copy(alpha = HERO_ICON_INACTIVE_ALPHA)
    val nameColor = if (sizeSelected) cs.primary else cs.onPrimaryContainer
    val subtitleColor = if (sizeSelected) cs.primary.copy(alpha = HERO_SUBTITLE_ALPHA)
                        else cs.onSurfaceVariant

    PapOutlinedCard(
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardLarge,
        containerColor = cs.primaryContainer.copy(alpha = HERO_CARD_BG_ALPHA),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HERO_CARD_VERTICAL_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            com.rndeveloper.paparcar.ui.components.VehicleIcon(
                carbody = state.carbodyType,
                size = state.sizeCategory,
                tint = iconTint,
                // Show the chosen paint colour once a size is picked; before that the dim
                // placeholder tint takes over anyway. [VEH-COLOR-001]
                color = state.color.takeIf { sizeSelected },
                defaultCarbody = CarbodyType.HATCHBACK_MEDIUM,
                modifier = Modifier.size(HERO_ICON_SIZE),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = heroName,
                style = PaparcarType.current.sectionTitle,
                fontWeight = FontWeight.Bold,
                color = nameColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sizeLabel,
                style = PaparcarType.current.caption,
                color = subtitleColor,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom CTA bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleRegistrationBottomBar(
    isSaving: Boolean,
    canSubmit: Boolean,
    hint: String?,
    onSave: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme

    // Shared bar [SETTINGS-AUDIT-REMEDIATION-001]; the save CTA is text-only — an icon must
    // earn its place [UI-ONBOARDING-BUTTON-ICONS-EARN-THEIR-PLACE-001].
    PapBottomActionBar {
        PapFooterButton(
            label = stringResource(
                if (isSaving) Res.string.vehicle_registration_saving
                else Res.string.vehicle_registration_save,
            ),
            onClick = onSave,
            enabled = canSubmit && !isSaving,
            isLoading = isSaving,
        )

        if (hint != null && !isSaving) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = hint,
                style = PaparcarType.current.caption,
                color = cs.onSurface.copy(alpha = PapAlpha.muted),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ── Layout tokens ─────────────────────────────────────────────────────────────

private val SCREEN_H_PADDING             = 16.dp
private val SECTION_SPACING              = 16.dp
private val CONTENT_TOP_SPACING          = 8.dp

// Hero card
private val HERO_ICON_SIZE               = 88.dp
private val HERO_CARD_VERTICAL_PADDING   = 24.dp
private const val HERO_CARD_BG_ALPHA     = 0.4f
private const val HERO_ICON_INACTIVE_ALPHA = 0.35f
private const val HERO_SUBTITLE_ALPHA    = 0.75f

// Section items
private const val AUTO_SIZE_LABEL_ALPHA  = 0.8f

// Delete section
private val DELETE_ICON_SIZE             = 20.dp
