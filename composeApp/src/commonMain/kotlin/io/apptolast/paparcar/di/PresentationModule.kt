package io.apptolast.paparcar.di

import io.apptolast.paparcar.presentation.app.SplashViewModel
import io.apptolast.paparcar.presentation.app.AppViewModel
import io.apptolast.paparcar.presentation.home.HomeViewModel
import io.apptolast.paparcar.presentation.map.ParkingLocationViewModel
import io.apptolast.paparcar.presentation.permissions.PermissionsViewModel
import io.apptolast.paparcar.presentation.bluetooth.BluetoothConfigViewModel
import io.apptolast.paparcar.presentation.vehicles.VehiclesViewModel
import io.apptolast.paparcar.presentation.settings.SettingsViewModel
import io.apptolast.paparcar.presentation.vehicleregistration.VehicleRegistrationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val presentationModule = module {
    viewModelOf(::SplashViewModel)
    viewModelOf(::AppViewModel)
    // Explicit constructor (not viewModelOf): HomeViewModel now has 23 ctor params and
    // viewModelOf's reflection helper only supports up to 22. [DET-TOGGLE-002]
    viewModel {
        HomeViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
    viewModelOf(::ParkingLocationViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PermissionsViewModel)
    viewModelOf(::VehicleRegistrationViewModel)
    viewModelOf(::VehiclesViewModel)
    // vehicleId parameter passed via koinViewModel(parameters = { parametersOf(vehicleId) })
    viewModel { params -> BluetoothConfigViewModel(params.get(), get(), get(), get()) }
}
