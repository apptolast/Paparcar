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
    // Explicit constructor (not viewModelOf): HomeViewModel has >22 ctor params and viewModelOf's
    // reflection helper only supports up to 22. Last arg getOrNull(): the OSM road source is
    // Android-only (iOS leaves it null → map-matching skipped). [DET-TOGGLE-002][ROUTE-SNAP-001]
    viewModel {
        HomeViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            getOrNull(),
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
