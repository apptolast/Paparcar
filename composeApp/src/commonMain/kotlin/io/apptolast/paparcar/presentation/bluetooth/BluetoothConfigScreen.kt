package io.apptolast.paparcar.presentation.bluetooth

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceInfo
import io.apptolast.paparcar.domain.model.bluetooth.BluetoothDeviceType
import io.apptolast.paparcar.presentation.util.collectAsStateLifecycleAware
import io.apptolast.paparcar.ui.components.PapFooterButton
import io.apptolast.paparcar.ui.components.PapOutlinedCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.bt_config_bt_off
import paparcar.composeapp.generated.resources.bt_config_cd_back
import paparcar.composeapp.generated.resources.bt_config_grant_permission
import paparcar.composeapp.generated.resources.bt_config_open_bt_settings
import paparcar.composeapp.generated.resources.bt_config_permission_rationale
import paparcar.composeapp.generated.resources.bt_config_device_classic
import paparcar.composeapp.generated.resources.bt_config_device_dual
import paparcar.composeapp.generated.resources.bt_config_device_le
import paparcar.composeapp.generated.resources.bt_config_no_devices
import paparcar.composeapp.generated.resources.bt_config_none
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
    val state by viewModel.state.collectAsStateLifecycleAware()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorFallback = stringResource(Res.string.error_unknown)
    val lifecycleOwner = LocalLifecycleOwner.current

    val requestPermission = rememberRequestBluetoothPermissionAction()
    val openBtSettings = rememberOpenBluetoothSettingsAction()

    // Refresh on every resume: catches paired-device changes, permission grants, BT on/off.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.handleIntent(BluetoothConfigIntent.RefreshState)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is BluetoothConfigEffect.NavigateBack -> onNavigateBack()
                is BluetoothConfigEffect.SavedSuccessfully -> onNavigateBack()
                is BluetoothConfigEffect.ShowError -> snackbarHostState.showSnackbar(errorFallback)
            }
        }
    }

    BluetoothConfigContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onIntent = viewModel::handleIntent,
        onRequestPermission = requestPermission,
        onOpenBtSettings = openBtSettings,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BluetoothConfigContent(
    state: BluetoothConfigState,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onIntent: (BluetoothConfigIntent) -> Unit = {},
    onRequestPermission: () -> Unit = {},
    onOpenBtSettings: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme

    Scaffold(
        containerColor = cs.surfaceContainer,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.bt_config_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { onIntent(BluetoothConfigIntent.NavigateBack) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(Res.string.bt_config_cd_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = cs.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Surface(
                color = cs.surfaceContainer,
                shadowElevation = BOTTOM_BAR_SHADOW_ELEVATION,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = SCREEN_H_PADDING, vertical = 12.dp),
                ) {
                    PapFooterButton(
                        label = stringResource(Res.string.bt_config_save),
                        onClick = { onIntent(BluetoothConfigIntent.Save) },
                        enabled = state.hasChanges,
                        isLoading = state.isSaving,
                    )
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
                onOpenBtSettings = onOpenBtSettings,
            )

            !state.hasBluetoothPermission -> BtPermissionState(
                modifier = Modifier.fillMaxSize().padding(padding),
                onRequestPermission = onRequestPermission,
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                item {
                    Text(
                        text = stringResource(Res.string.bt_config_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                }

                item {
                    DeviceRow(
                        name = stringResource(Res.string.bt_config_none),
                        typeLabel = null,
                        selected = state.selectedAddress == null,
                        onClick = { onIntent(BluetoothConfigIntent.SelectDevice(null)) },
                    )
                }

                if (state.bondedDevices.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(Res.string.bt_config_no_devices),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                } else {
                    items(items = state.bondedDevices, key = { it.address }) { device ->
                        DeviceRow(
                            name = device.name ?: device.address,
                            typeLabel = device.typeLabel(),
                            selected = device.address == state.selectedAddress,
                            onClick = { onIntent(BluetoothConfigIntent.SelectDevice(device.address)) },
                        )
                    }
                }
            }
        }
    }
}

// ─── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun BtOffState(
    modifier: Modifier = Modifier,
    onOpenBtSettings: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.BluetoothDisabled,
            contentDescription = null,
            modifier = Modifier.size(BT_OFF_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = BT_STATE_ICON_ALPHA),
        )
        Text(
            text = stringResource(Res.string.bt_config_bt_off),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onOpenBtSettings) {
            Text(stringResource(Res.string.bt_config_open_bt_settings))
        }
    }
}

@Composable
private fun BtPermissionState(
    modifier: Modifier = Modifier,
    onRequestPermission: () -> Unit = {},
) {
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(BT_OFF_ICON_SIZE),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = BT_STATE_ICON_ALPHA),
        )
        Text(
            text = stringResource(Res.string.bt_config_permission_rationale),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onRequestPermission) {
            Text(stringResource(Res.string.bt_config_grant_permission))
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    typeLabel: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    PapOutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (typeLabel != null) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothDeviceInfo.typeLabel(): String = when (type) {
    BluetoothDeviceType.CLASSIC -> stringResource(Res.string.bt_config_device_classic)
    BluetoothDeviceType.LE      -> stringResource(Res.string.bt_config_device_le)
    BluetoothDeviceType.DUAL    -> stringResource(Res.string.bt_config_device_dual)
    BluetoothDeviceType.UNKNOWN -> stringResource(Res.string.bt_config_device_classic)
}

// ── Layout tokens ─────────────────────────────────────────────────────────────

private val SCREEN_H_PADDING            = 16.dp
private val BOTTOM_BAR_SHADOW_ELEVATION = 8.dp
private val BT_OFF_ICON_SIZE            = 64.dp
private const val BT_STATE_ICON_ALPHA   = 0.4f
