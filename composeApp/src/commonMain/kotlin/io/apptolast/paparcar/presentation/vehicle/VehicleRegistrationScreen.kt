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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.VehicleSize
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.error_unknown
import paparcar.composeapp.generated.resources.vehicle_registration_brand_hint
import paparcar.composeapp.generated.resources.vehicle_registration_model_hint
import paparcar.composeapp.generated.resources.vehicle_registration_save
import paparcar.composeapp.generated.resources.vehicle_registration_title
import paparcar.composeapp.generated.resources.vehicle_show_on_spot
import paparcar.composeapp.generated.resources.vehicle_show_on_spot_desc
import paparcar.composeapp.generated.resources.vehicle_size_label
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_large_examples
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_medium_examples
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_moto_examples
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_small_examples
import paparcar.composeapp.generated.resources.vehicle_size_van
import paparcar.composeapp.generated.resources.vehicle_size_van_examples

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleRegistrationScreen(
    onRegistrationComplete: () -> Unit,
    onNavigateBack: () -> Unit = {},
    viewModel: VehicleRegistrationViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(Res.string.error_unknown)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is VehicleRegistrationEffect.SavedSuccessfully -> onRegistrationComplete()
                is VehicleRegistrationEffect.NavigateBack -> onNavigateBack()
                is VehicleRegistrationEffect.ShowError ->
                    snackbarHostState.showSnackbar(errorFallback)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.vehicle_registration_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.handleIntent(VehicleRegistrationIntent.NavigateBack) }) {
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
                onValueChange = { viewModel.handleIntent(VehicleRegistrationIntent.SetBrand(it)) },
                label = { Text(stringResource(Res.string.vehicle_registration_brand_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = state.model,
                onValueChange = { viewModel.handleIntent(VehicleRegistrationIntent.SetModel(it)) },
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
                onSelect = { viewModel.handleIntent(VehicleRegistrationIntent.SetSize(it)) },
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
                    onCheckedChange = {
                        viewModel.handleIntent(VehicleRegistrationIntent.SetShowOnSpot(it))
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.handleIntent(VehicleRegistrationIntent.Save) },
                enabled = state.sizeCategory != null && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(stringResource(Res.string.vehicle_registration_save))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Vehicle Size Selector ────────────────────────────────────────────────────

private data class SizeOption(
    val size: VehicleSize,
    val labelRes: @Composable () -> String,
    val examplesRes: @Composable () -> String,
)

@Composable
private fun VehicleSizeSelector(
    selected: VehicleSize?,
    onSelect: (VehicleSize) -> Unit,
) {
    val options = listOf(
        SizeOption(VehicleSize.MOTO,
            { stringResource(Res.string.vehicle_size_moto) },
            { stringResource(Res.string.vehicle_size_moto_examples) }),
        SizeOption(VehicleSize.SMALL,
            { stringResource(Res.string.vehicle_size_small) },
            { stringResource(Res.string.vehicle_size_small_examples) }),
        SizeOption(VehicleSize.MEDIUM,
            { stringResource(Res.string.vehicle_size_medium) },
            { stringResource(Res.string.vehicle_size_medium_examples) }),
        SizeOption(VehicleSize.LARGE,
            { stringResource(Res.string.vehicle_size_large) },
            { stringResource(Res.string.vehicle_size_large_examples) }),
        SizeOption(VehicleSize.VAN,
            { stringResource(Res.string.vehicle_size_van) },
            { stringResource(Res.string.vehicle_size_van_examples) }),
    )

    Column(modifier = Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = selected == option.size,
                        onClick = { onSelect(option.size) },
                        role = Role.RadioButton,
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = selected == option.size,
                    onClick = null, // handled by selectable
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = option.labelRes(),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = option.examplesRes(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}