package io.apptolast.paparcar.presentation.vehicle

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.PapPrimaryButton
import io.apptolast.paparcar.ui.components.VehicleSizeSelector
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.veh_bt_recommendation_body
import paparcar.composeapp.generated.resources.veh_bt_recommendation_configure
import paparcar.composeapp.generated.resources.veh_bt_recommendation_skip
import paparcar.composeapp.generated.resources.veh_bt_recommendation_title
import paparcar.composeapp.generated.resources.vehicle_registration_brand_hint
import paparcar.composeapp.generated.resources.vehicle_registration_edit_title
import paparcar.composeapp.generated.resources.vehicle_registration_model_hint
import paparcar.composeapp.generated.resources.vehicle_registration_save
import paparcar.composeapp.generated.resources.vehicle_registration_title
import paparcar.composeapp.generated.resources.vehicle_show_on_spot
import paparcar.composeapp.generated.resources.vehicle_show_on_spot_desc
import paparcar.composeapp.generated.resources.vehicle_size_label

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

    // Holds the id of a just-registered new vehicle while the user decides whether
    // to pair Bluetooth. Non-null → modal visible. Cleared on dismiss/confirm. [VEH-BT-001]
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
                        // Defer onRegistrationComplete until the user resolves the modal —
                        // either by going to BT_CONFIG (which navigates away itself) or by
                        // explicitly skipping.
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
    AlertDialog(
        // Skipping via back/scrim equals "later" — same as the explicit dismiss button,
        // so the registration flow always completes one way or the other.
        onDismissRequest = onSkip,
        icon = {
            Icon(
                Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = { Text(stringResource(Res.string.veh_bt_recommendation_title)) },
        text = { Text(stringResource(Res.string.veh_bt_recommendation_body)) },
        confirmButton = {
            TextButton(onClick = onConfigure) {
                Text(stringResource(Res.string.veh_bt_recommendation_configure))
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text(stringResource(Res.string.veh_bt_recommendation_skip))
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VehicleRegistrationContent(
    state: VehicleRegistrationState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onIntent: (VehicleRegistrationIntent) -> Unit = {},
) {
    val isEditing = state.editingVehicleId != null
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEditing) Res.string.vehicle_registration_edit_title
                            else Res.string.vehicle_registration_title,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(VehicleRegistrationIntent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            OutlinedTextField(
                value = state.brand,
                onValueChange = { onIntent(VehicleRegistrationIntent.SetBrand(it)) },
                label = { Text(stringResource(Res.string.vehicle_registration_brand_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.model,
                onValueChange = { onIntent(VehicleRegistrationIntent.SetModel(it)) },
                label = { Text(stringResource(Res.string.vehicle_registration_model_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = stringResource(Res.string.vehicle_size_label),
                style = MaterialTheme.typography.titleSmall,
            )

            VehicleSizeSelector(
                selected = state.sizeCategory,
                onSelect = { onIntent(VehicleRegistrationIntent.SetSize(it)) },
            )

            Spacer(Modifier.height(4.dp))

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

            Spacer(Modifier.height(8.dp))

            PapPrimaryButton(
                label = stringResource(Res.string.vehicle_registration_save),
                onClick = { onIntent(VehicleRegistrationIntent.Save) },
                enabled = state.sizeCategory != null,
                isLoading = state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
