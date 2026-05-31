package io.apptolast.paparcar.presentation.vehicleregistration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleSize
import io.apptolast.paparcar.presentation.vehicleregistration.data.VehicleCatalog
import io.apptolast.paparcar.ui.components.PapAlertDialog
import io.apptolast.paparcar.ui.components.PapCard
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapSectionHeader
import io.apptolast.paparcar.ui.components.PapTextButton
import io.apptolast.paparcar.ui.components.PapTextField
import io.apptolast.paparcar.ui.components.VehicleSizeSelector
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.icons.icon
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.veh_bt_recommendation_body
import paparcar.composeapp.generated.resources.veh_bt_recommendation_configure
import paparcar.composeapp.generated.resources.veh_bt_recommendation_skip
import paparcar.composeapp.generated.resources.veh_bt_recommendation_title
import paparcar.composeapp.generated.resources.vehicle_registration_brand_hint
import paparcar.composeapp.generated.resources.vehicle_registration_bt_cta
import paparcar.composeapp.generated.resources.vehicle_registration_bt_desc
import paparcar.composeapp.generated.resources.vehicle_registration_bt_title
import paparcar.composeapp.generated.resources.vehicle_registration_edit_title
import paparcar.composeapp.generated.resources.vehicle_registration_model_hint
import paparcar.composeapp.generated.resources.vehicle_registration_name_label
import paparcar.composeapp.generated.resources.vehicle_registration_name_placeholder
import paparcar.composeapp.generated.resources.vehicle_registration_other_option
import paparcar.composeapp.generated.resources.vehicle_registration_preview_subtitle_default
import paparcar.composeapp.generated.resources.vehicle_registration_preview_title
import paparcar.composeapp.generated.resources.vehicle_registration_save
import paparcar.composeapp.generated.resources.vehicle_registration_section_detection
import paparcar.composeapp.generated.resources.vehicle_registration_section_optional
import paparcar.composeapp.generated.resources.vehicle_registration_section_privacy
import paparcar.composeapp.generated.resources.vehicle_registration_section_size
import paparcar.composeapp.generated.resources.vehicle_registration_size_hint
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
        }
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
    val isEditing = state.editingVehicleId != null
    val brands = remember { VehicleCatalog.brands() }
    val otherLabel = stringResource(Res.string.vehicle_registration_other_option)

    var brandExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }

    val models = remember(state.brand, state.isBrandOther) {
        if (!state.isBrandOther && state.brand.isNotBlank()) VehicleCatalog.modelsFor(state.brand) else emptyList()
    }

    val nameError = state.hasInteractedWithForm &&
            state.name.isBlank() && state.brand.isBlank() && state.model.isBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditing) Res.string.vehicle_registration_edit_title
                            else Res.string.vehicle_registration_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(VehicleRegistrationIntent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                PapFooterButton(
                    label = if (state.isSaving) "Guardando..." else stringResource(Res.string.vehicle_registration_save),
                    onClick = { onIntent(VehicleRegistrationIntent.Save) },
                    enabled = state.canSubmit && !state.isSaving
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Vehicle Preview Card ─────────────────────────────────────────
            VehiclePreviewCard(
                name = state.name.ifBlank { stringResource(Res.string.vehicle_registration_preview_title) },
                details = if (state.brand.isNotBlank()) "${state.brand} ${state.model}" else stringResource(Res.string.vehicle_registration_preview_subtitle_default),
                size = state.sizeCategory
            )

            // ── Size Section ─────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_size))
                Text(
                    text = stringResource(Res.string.vehicle_registration_size_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                VehicleSizeSelector(
                    selected = state.sizeCategory,
                    onSelect = { onIntent(VehicleRegistrationIntent.SetSize(it)) },
                )
            }

            // ── Optional Details Section ─────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_optional))

                PapTextField(
                    value = state.name,
                    onValueChange = { onIntent(VehicleRegistrationIntent.SetName(it)) },
                    label = stringResource(Res.string.vehicle_registration_name_label),
                    placeholder = stringResource(Res.string.vehicle_registration_name_placeholder, state.defaultNamePlaceholderIndex),
                    isError = nameError,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Brand dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = brandExpanded,
                            onExpandedChange = { brandExpanded = it },
                        ) {
                            PapTextField(
                                value = if (state.isBrandOther) state.brand else state.brand,
                                onValueChange = {
                                    if (state.isBrandOther) onIntent(VehicleRegistrationIntent.SetCustomBrand(it))
                                },
                                readOnly = !state.isBrandOther,
                                label = stringResource(Res.string.vehicle_registration_brand_hint),
                                trailingIcon = {
                                    if (!state.isBrandOther) {
                                        IconButton(onClick = { brandExpanded = true }) {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
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

                    // Model dropdown
                    Box(modifier = Modifier.weight(1f)) {
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded && (models.isNotEmpty() || state.isBrandOther),
                            onExpandedChange = { if (models.isNotEmpty() || state.isBrandOther) modelExpanded = it },
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
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
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

            // ── Bluetooth Section ────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_detection))
                PapCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    padding = 12.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bluetooth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(Res.string.vehicle_registration_bt_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(Res.string.vehicle_registration_bt_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        PapTextButton(
                            label = stringResource(Res.string.vehicle_registration_bt_cta),
                            onClick = onConfigureBluetooth,
                        )
                    }
                }
            }

            // ── Privacy Section ──────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PapSectionHeader(title = stringResource(Res.string.vehicle_registration_section_privacy))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(Res.string.vehicle_show_on_spot),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(Res.string.vehicle_show_on_spot_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.showBrandModelOnSpot,
                        onCheckedChange = { onIntent(VehicleRegistrationIntent.SetShowOnSpot(it)) },
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun VehiclePreviewCard(name: String, details: String, size: VehicleSize?) {
    PapCard(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = size?.icon ?: PaparcarIcons.VehicleMedium,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = details,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

