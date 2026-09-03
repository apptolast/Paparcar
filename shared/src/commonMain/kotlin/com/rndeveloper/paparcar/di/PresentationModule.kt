package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import com.rndeveloper.paparcar.presentation.app.SplashViewModel
import com.rndeveloper.paparcar.presentation.app.AppViewModel
import com.rndeveloper.paparcar.presentation.home.HomeGeocodingController
import com.rndeveloper.paparcar.presentation.home.HomeSearchController
import com.rndeveloper.paparcar.presentation.home.HomeSpotsController
import com.rndeveloper.paparcar.presentation.home.HomeTripController
import com.rndeveloper.paparcar.presentation.home.HomeViewModel
import com.rndeveloper.paparcar.presentation.map.ParkingHistoryViewModel
import com.rndeveloper.paparcar.presentation.permissions.PermissionsViewModel
import com.rndeveloper.paparcar.presentation.bluetooth.BluetoothConfigViewModel
import com.rndeveloper.paparcar.presentation.vehicles.VehiclesViewModel
import com.rndeveloper.paparcar.presentation.settings.SettingsViewModel
import com.rndeveloper.paparcar.presentation.vehicleregistration.VehicleRegistrationViewModel
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
            userParkingRepository = get(),
            // Android-only durable route store; iOS leaves it null → parked-spot seed is the fallback.
            drivingRouteStore = getOrNull(),
            permissionManager = get(),
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
            get(), get(), get(), get(), get(), get(), get(),
        )
    }
    viewModelOf(::ParkingHistoryViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::PermissionsViewModel)
    viewModelOf(::VehicleRegistrationViewModel)
    viewModelOf(::VehiclesViewModel)
    // vehicleId parameter passed via koinViewModel(parameters = { parametersOf(vehicleId) })
    viewModel { params -> BluetoothConfigViewModel(params.get(), get(), get(), get()) }
}
