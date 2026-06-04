package io.apptolast.paparcar.presentation.vehicleregistration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.vehicleregistration.data.VehicleCatalog
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapDialogAccent
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.components.PapTextField
import io.apptolast.paparcar.ui.components.VehicleSizeSelector
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.icons.icon
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
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
import paparcar.composeapp.generated.resources.vehicle_registration_model_hint
import paparcar.composeapp.generated.resources.vehicle_registration_name_label
import paparcar.composeapp.generated.resources.vehicle_registration_name_placeholder
import paparcar.composeapp.generated.resources.vehicle_registration_other_option
import paparcar.composeapp.generated.resources.vehicle_registration_preview_title
import paparcar.composeapp.generated.resources.vehicle_registration_save
import paparcar.composeapp.generated.resources.vehicle_registration_saving
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
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van

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
        icon = Icons.Outlined.Bluetooth,
        title = stringResource(Res.string.veh_bt_recommendation_title),
        body = stringResource(Res.string.veh_bt_recommendation_body),
        primaryLabel = stringResource(Res.string.veh_bt_recommendation_configure),
        primaryLeadingIcon = Icons.Outlined.Bluetooth,
        onPrimary = onConfigure,
        cancelLabel = stringResource(Res.string.veh_bt_recommendation_skip),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleRegistrationContent(
    state: VehicleRegistrationState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onIntent: (VehicleRegistrationIntent) -> Unit = {},
    onConfigureBluetooth: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val isEditing = state.editingVehicleId != null
    val isNewVehicle = state.editingVehicleId == null
    val brands = remember { VehicleCatalog.brands() }
    val otherLabel = stringResource(Res.string.vehicle_registration_other_option)

    var brandExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val models = remember(state.brand, state.isBrandOther) {
        if (!state.isBrandOther && state.brand.isNotBlank()) VehicleCatalog.modelsFor(state.brand)
        else emptyList()
    }

    val bottomHint: String? = when {
        !state.canSubmit && state.sizeCategory == null ->
            stringResource(Res.string.vehicle_registration_size_required_hint)
        !state.canSubmit && state.hasInteractedWithForm ->
            stringResource(Res.string.vehicle_registration_brand_error)
        else -> null
    }

    Scaffold(
        containerColor = cs.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isEditing) Res.string.vehicle_registration_edit_title
                            else Res.string.vehicle_registration_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(VehicleRegistrationIntent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = cs.surfaceContainer,
                ),
            )
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
                .padding(padding)
                .verticalScroll(rememberScrollState()),
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
                                onValueChange = {
                                    if (state.isBrandOther) onIntent(VehicleRegistrationIntent.SetCustomBrand(it))
                                },
                                readOnly = !state.isBrandOther,
                                label = stringResource(Res.string.vehicle_registration_brand_hint),
                                isError = state.brandError,
                                trailingIcon = {
                                    if (!state.isBrandOther) {
                                        IconButton(onClick = { brandExpanded = true }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            if (!state.isBrandOther) {
                                ExposedDropdownMenu(
                                    expanded = brandExpanded,
                                    onDismissRequest = { brandExpanded = false },
                                ) {
                                    brands.forEach { brand ->
                                        DropdownMenuItem(
                                            text = { Text(brand) },
                                            onClick = {
                                                onIntent(VehicleRegistrationIntent.SelectBrand(brand))
                                                brandExpanded = false
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(otherLabel) },
                                        onClick = {
                                            onIntent(VehicleRegistrationIntent.SelectBrandOther)
                                            brandExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded && (models.isNotEmpty() || state.isBrandOther),
                            onExpandedChange = {
                                if (models.isNotEmpty() || state.isBrandOther) modelExpanded = it
                            },
                        ) {
                            PapTextField(
                                value = state.model,
                                onValueChange = {
                                    if (state.isModelOther || state.isBrandOther)
                                        onIntent(VehicleRegistrationIntent.SetCustomModel(it))
                                },
                                readOnly = models.isNotEmpty() && !state.isModelOther,
                                label = stringResource(Res.string.vehicle_registration_model_hint),
                                trailingIcon = {
                                    if (models.isNotEmpty() && !state.isModelOther) {
                                        IconButton(onClick = { modelExpanded = true }) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowForward,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                },
                                enabled = state.brand.isNotBlank() || state.isBrandOther,
                                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                            )
                            if (models.isNotEmpty() && !state.isModelOther) {
                                ExposedDropdownMenu(
                                    expanded = modelExpanded,
                                    onDismissRequest = { modelExpanded = false },
                                ) {
                                    models.forEach { model ->
                                        DropdownMenuItem(
                                            text = { Text(model) },
                                            onClick = {
                                                onIntent(VehicleRegistrationIntent.SelectModel(model))
                                                modelExpanded = false
                                            },
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text(otherLabel) },
                                        onClick = {
                                            onIntent(VehicleRegistrationIntent.SelectModelOther)
                                            modelExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── Size section — auto-detected or manual ────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_size))
                val sizeHint = if (state.isSizeAutoDetected && state.sizeCategory != null) {
                    val sizeLabel = when (state.sizeCategory) {
                        VehicleSize.MOTO   -> stringResource(Res.string.vehicle_size_moto)
                        VehicleSize.SMALL  -> stringResource(Res.string.vehicle_size_small)
                        VehicleSize.MEDIUM -> stringResource(Res.string.vehicle_size_medium)
                        VehicleSize.LARGE  -> stringResource(Res.string.vehicle_size_large)
                        VehicleSize.VAN    -> stringResource(Res.string.vehicle_size_van)
                    }
                    stringResource(Res.string.vehicle_registration_size_auto_detected, sizeLabel)
                } else {
                    stringResource(Res.string.vehicle_registration_size_hint)
                }
                Text(
                    text = sizeHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.isSizeAutoDetected) cs.primary.copy(alpha = AUTO_SIZE_LABEL_ALPHA)
                            else cs.onSurfaceVariant,
                )
                VehicleSizeSelector(
                    selected = state.sizeCategory,
                    onSelect = { onIntent(VehicleRegistrationIntent.SetSize(it)) },
                )
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
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PapShapes.card,
                        color = cs.surfaceContainerHigh,
                        border = BorderStroke(PapBorders.thin, cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(BT_ICON_BOX_SIZE)
                                    .clip(CircleShape)
                                    .background(cs.primaryContainer),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bluetooth,
                                    contentDescription = null,
                                    modifier = Modifier.size(BT_ICON_SIZE),
                                    tint = cs.primary,
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(Res.string.vehicle_registration_bt_title),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onSurface,
                                )
                                Text(
                                    text = stringResource(Res.string.vehicle_registration_bt_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
                                )
                            }
                            Button(
                                onClick = onConfigureBluetooth,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                shape = PapShapes.cardSmall,
                            ) {
                                Text(
                                    text = stringResource(Res.string.vehicle_registration_bt_cta),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }

            // ── Privacy section ──────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(horizontal = SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_privacy))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PapShapes.card,
                    color = cs.surfaceContainerHigh,
                    border = BorderStroke(PapBorders.thin, cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.vehicle_show_on_spot),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface,
                            )
                            Text(
                                text = stringResource(Res.string.vehicle_show_on_spot_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurface.copy(alpha = SUBTITLE_ALPHA),
                            )
                        }
                        Switch(
                            checked = state.showBrandModelOnSpot,
                            onCheckedChange = { onIntent(VehicleRegistrationIntent.SetShowOnSpot(it)) },
                        )
                    }
                }
            }

            // ── Delete section — only shown when editing and more than one vehicle ──
            if (!isNewVehicle && state.canDelete) {
                Surface(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SCREEN_H_PADDING),
                    shape = PapShapes.card,
                    color = cs.errorContainer.copy(alpha = DELETE_SECTION_BG_ALPHA),
                    border = BorderStroke(PapBorders.thin, cs.error.copy(alpha = DELETE_SECTION_BORDER_ALPHA)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Delete,
                            contentDescription = null,
                            tint = cs.error,
                            modifier = Modifier.size(DELETE_ICON_SIZE),
                        )
                        Text(
                            text = stringResource(Res.string.my_car_delete_vehicle),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.error,
                        )
                    }
                }
            }

            if (showDeleteDialog) {
                PapAlertDialog(
                    onDismiss = { showDeleteDialog = false },
                    icon = Icons.Outlined.Delete,
                    title = stringResource(Res.string.my_car_delete_confirm_title),
                    body = stringResource(Res.string.my_car_delete_confirm_message),
                    primaryLabel = stringResource(Res.string.my_car_delete_confirm_action),
                    primaryLeadingIcon = Icons.Outlined.Delete,
                    onPrimary = {
                        showDeleteDialog = false
                        onIntent(VehicleRegistrationIntent.DeleteVehicle)
                    },
                    cancelLabel = stringResource(Res.string.my_car_delete_cancel),
                    accent = PapDialogAccent.Destructive,
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

    val sizeLabel = when (state.sizeCategory) {
        VehicleSize.MOTO   -> stringResource(Res.string.vehicle_size_moto)
        VehicleSize.SMALL  -> stringResource(Res.string.vehicle_size_small)
        VehicleSize.MEDIUM -> stringResource(Res.string.vehicle_size_medium)
        VehicleSize.LARGE  -> stringResource(Res.string.vehicle_size_large)
        VehicleSize.VAN    -> stringResource(Res.string.vehicle_size_van)
        null               -> stringResource(Res.string.vehicle_registration_size_hint)
    }

    val sizeSelected = state.sizeCategory != null
    val iconTint = if (sizeSelected) cs.primary
                   else cs.onSurface.copy(alpha = HERO_ICON_INACTIVE_ALPHA)
    val nameColor = if (sizeSelected) cs.primary else cs.onPrimaryContainer
    val subtitleColor = if (sizeSelected) cs.primary.copy(alpha = HERO_SUBTITLE_ALPHA)
                        else cs.onSurfaceVariant

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PapShapes.cardLarge,
        color = cs.primaryContainer.copy(alpha = HERO_CARD_BG_ALPHA),
        border = BorderStroke(PapBorders.thin, cs.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = HERO_CARD_VERTICAL_PADDING),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = state.sizeCategory?.icon ?: PaparcarIcons.VehicleMedium,
                contentDescription = null,
                modifier = Modifier.size(HERO_ICON_SIZE),
                tint = iconTint,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = heroName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = nameColor,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = sizeLabel,
                style = MaterialTheme.typography.bodySmall,
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

    Surface(
        color = cs.surfaceContainer,
        shadowElevation = BOTTOM_BAR_SHADOW_ELEVATION,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SCREEN_H_PADDING, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = onSave,
                enabled = canSubmit && !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SAVE_BUTTON_HEIGHT),
                shape = RoundedCornerShape(SAVE_BUTTON_CORNER),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary,
                    disabledContainerColor = cs.primary.copy(alpha = BUTTON_DISABLED_BG_ALPHA),
                    disabledContentColor = cs.onPrimary.copy(alpha = BUTTON_DISABLED_FG_ALPHA),
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(SAVING_INDICATOR_SIZE),
                        strokeWidth = 2.dp,
                        color = cs.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.vehicle_registration_saving),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(SAVE_BUTTON_ICON_SIZE),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.vehicle_registration_save),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (hint != null && !isSaving) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurface.copy(alpha = HINT_TEXT_ALPHA),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// ── Layout tokens ─────────────────────────────────────────────────────────────

private val SCREEN_H_PADDING             = 16.dp
private val SECTION_SPACING              = 16.dp
private val CONTENT_TOP_SPACING          = 8.dp

// Hero card
private val HERO_ICON_SIZE               = 64.dp
private val HERO_CARD_VERTICAL_PADDING   = 24.dp
private const val HERO_CARD_BG_ALPHA     = 0.4f
private const val HERO_ICON_INACTIVE_ALPHA = 0.35f
private const val HERO_SUBTITLE_ALPHA    = 0.75f

// BT card icon
private val BT_ICON_BOX_SIZE             = 38.dp
private val BT_ICON_SIZE                 = 20.dp

// Section items
private const val SUBTITLE_ALPHA         = 0.55f
private const val AUTO_SIZE_LABEL_ALPHA  = 0.8f

// Bottom bar / CTA button
private val SAVE_BUTTON_HEIGHT           = 52.dp
private val SAVE_BUTTON_CORNER           = 14.dp
private val SAVE_BUTTON_ICON_SIZE        = 18.dp
private val SAVING_INDICATOR_SIZE        = 18.dp
private val BOTTOM_BAR_SHADOW_ELEVATION  = 8.dp
private const val BUTTON_DISABLED_BG_ALPHA  = 0.38f
private const val BUTTON_DISABLED_FG_ALPHA  = 0.6f
private const val HINT_TEXT_ALPHA        = 0.5f

// Delete section
private val DELETE_ICON_SIZE             = 20.dp
private const val DELETE_SECTION_BG_ALPHA     = 0.3f
private const val DELETE_SECTION_BORDER_ALPHA = 0.4f
