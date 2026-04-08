package io.apptolast.paparcar.presentation.bluetooth

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.bt_config_bt_off
import paparcar.composeapp.generated.resources.bt_config_device_classic
import paparcar.composeapp.generated.resources.bt_config_device_dual
import paparcar.composeapp.generated.resources.bt_config_device_le
import paparcar.composeapp.generated.resources.bt_config_no_devices
import paparcar.composeapp.generated.resources.bt_config_none
import paparcar.composeapp.generated.resources.bt_config_remove
import paparcar.composeapp.generated.resources.bt_config_save
import paparcar.composeapp.generated.resources.bt_config_subtitle
import paparcar.composeapp.generated.resources.bt_config_title
import paparcar.composeapp.generated.resources.error_unknown

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothConfigScreen(
    vehicleId: String,
    onNavigateBack: () -> Unit,
    viewModel: BluetoothConfigViewModel = koinViewModel(parameters = { parametersOf(vehicleId) }),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(Res.string.error_unknown)

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BluetoothConfigEffect.NavigateBack -> onNavigateBack()
                is BluetoothConfigEffect.SavedSuccessfully -> onNavigateBack()
                is BluetoothConfigEffect.ShowError -> snackbarHostState.showSnackbar(errorFallback)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.bt_config_title)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.handleIntent(BluetoothConfigIntent.NavigateBack) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { viewModel.handleIntent(BluetoothConfigIntent.Save) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.hasChanges && !state.isSaving,
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(Res.string.bt_config_save))
                    }
                }
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            !state.isBluetoothEnabled -> BtOffState(
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(Res.string.bt_config_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }

                // "None" option — removes BT pairing
                item {
                    DeviceRow(
                        name = stringResource(Res.string.bt_config_none),
                        typeLabel = null,
                        selected = state.selectedAddress == null,
                        onClick = { viewModel.handleIntent(BluetoothConfigIntent.SelectDevice(null)) },
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                }

                if (state.bondedDevices.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.bt_config_no_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp),
                        )
                    }
                } else {
                    items(items = state.bondedDevices, key = { it.address }) { device ->
                        DeviceRow(
                            name = device.name ?: device.address,
                            typeLabel = device.typeLabel(),
                            selected = device.address == state.selectedAddress,
                            onClick = { viewModel.handleIntent(BluetoothConfigIntent.SelectDevice(device.address)) },
                        )
                    }
                }

                // Remove button (shown when currently paired)
                if (state.currentDeviceAddress != null) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        TextButton(
                            onClick = { viewModel.handleIntent(BluetoothConfigIntent.SelectDevice(null)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stringResource(Res.string.bt_config_remove),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                item { Spacer(Modifier.height(80.dp)) } // bottom bar clearance
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun BtOffState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.BluetoothDisabled,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(Res.string.bt_config_bt_off),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceRow(
    name: String,
    typeLabel: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (typeLabel != null) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceInfo.typeLabel(): String = when (type) {
    BluetoothDeviceType.CLASSIC -> stringResource(Res.string.bt_config_device_classic)
    BluetoothDeviceType.LE -> stringResource(Res.string.bt_config_device_le)
    BluetoothDeviceType.DUAL -> stringResource(Res.string.bt_config_device_dual)
    BluetoothDeviceType.UNKNOWN -> stringResource(Res.string.bt_config_device_classic)
}
