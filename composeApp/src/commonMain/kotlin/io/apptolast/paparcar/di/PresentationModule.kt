package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import io.apptolast.paparcar.presentation.app.SplashViewModel
import io.apptolast.paparcar.presentation.app.AppViewModel
import io.apptolast.paparcar.presentation.home.HomeGeocodingController
import io.apptolast.paparcar.presentation.home.HomeSearchController
import io.apptolast.paparcar.presentation.home.HomeSpotsController
import io.apptolast.paparcar.presentation.home.HomeTripController
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
    // Home feature controllers — self-contained pipeline owners the VM collects. Each carries its own
    // use cases so the VM constructor holds no pass-through deps. Trip takes the readiness use case
    // through a functional seam (it combines six collaborators — not cheaply fakeable in unit tests),
    // and getOrNull() for the OSM road source: Android-only, iOS leaves it null → map-matching skipped.
    // [HOMEVM-CTRL-002][ROUTE-SNAP-001]
    factory { HomeGeocodingController(getAddressAndPlace = get()) }
    factory {
        val observeDetectionReadiness = get<ObserveDetectionReadinessUseCase>()
        HomeTripController(
            observeDetectionReadiness = { observeDetectionReadiness() },
            locationDataSource = get(),
            roadNetworkDataSource = getOrNull(),
            vehicleRepository = get(),
            permissionManager = get(),
            userParkingRepository = get(),
        )
    }
    factory { HomeSearchController(searchAddress = get()) }
    factory { HomeSpotsController(permissionManager = get(), observeNearbySpots = get()) }
    // Explicit constructor (not viewModelOf): HomeViewModel has >22 ctor params and viewModelOf's
    // reflection helper only supports up to 22. [DET-TOGGLE-002]
    viewModel {
        HomeViewModel(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
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
