package com.rndeveloper.paparcar.presentation.bluetooth

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.rndeveloper.paparcar.presentation.preview.FakeData
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

@Preview(name = "BtConfig — lista dispositivos · Claro", showBackground = true)
@Composable
private fun BtConfigDeviceListLightPreview() {
    PaparcarTheme(darkTheme = false) {
        BluetoothConfigContent(
            state = BluetoothConfigState(
                vehicleName = "Toyota Corolla",
                bondedDevices = FakeData.btDevices,
                selectedAddress = FakeData.btDevices.first().address,
                currentDeviceAddress = FakeData.btDevices.first().address,
                isBluetoothEnabled = true,
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "BtConfig — lista dispositivos · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BtConfigDeviceListDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        BluetoothConfigContent(
            state = BluetoothConfigState(
                vehicleName = "Toyota Corolla",
                bondedDevices = FakeData.btDevices,
                selectedAddress = null,
                isBluetoothEnabled = true,
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "BtConfig — sin dispositivos", showBackground = true)
@Composable
private fun BtConfigNoDevicesPreview() {
    PaparcarTheme(darkTheme = false) {
        BluetoothConfigContent(
            state = BluetoothConfigState(
                vehicleName = "Ford Transit",
                bondedDevices = emptyList(),
                isBluetoothEnabled = true,
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "BtConfig — BT desactivado · Claro", showBackground = true)
@Composable
private fun BtConfigBtOffLightPreview() {
    PaparcarTheme(darkTheme = false) {
        BluetoothConfigContent(
            state = BluetoothConfigState(
                vehicleName = "Toyota Corolla",
                isBluetoothEnabled = false,
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "BtConfig — BT desactivado · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BtConfigBtOffDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        BluetoothConfigContent(
            state = BluetoothConfigState(
                vehicleName = "Toyota Corolla",
                isBluetoothEnabled = false,
                isLoading = false,
            ),
        )
    }
}

@Preview(name = "BtConfig — cargando", showBackground = true)
@Composable
private fun BtConfigLoadingPreview() {
    PaparcarTheme(darkTheme = false) {
        BluetoothConfigContent(
            state = BluetoothConfigState(isLoading = true),
        )
    }
}
