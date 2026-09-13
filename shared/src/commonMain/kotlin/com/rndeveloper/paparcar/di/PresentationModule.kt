package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import com.rndeveloper.paparcar.presentation.app.SplashViewModel
import com.rndeveloper.paparcar.presentation.app.AppViewModel
import com.rndeveloper.paparcar.presentation.home.HomeGeocodingController
import com.rndeveloper.paparcar.presentation.home.HomeSearchController
import com.rndeveloper.paparcar.presentation.home.HomeSpotsController
import com.rndeveloper.paparcar.presentation.home.HomeTripController
import com.rndeveloper.paparcar.presentation.home.HomeViewModel
import com.rndeveloper.paparcar.presentation.licenses.LicensesViewModel
import com.rndeveloper.paparcar.presentation.map.ParkingHistoryViewModel
import com.rndeveloper.paparcar.presentation.paywall.PaywallViewModel
import com.rndeveloper.paparcar.presentation.permissions.PermissionsViewModel
import com.rndeveloper.paparcar.presentation.bluetooth.BluetoothConfigViewModel
import com.rndeveloper.paparcar.presentation.stats.StatsViewModel
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
            get(), get(), get(), get(), get(), get(), get(), get(), get(),
            // 34th: ReleaseAskEventBus [WIDGET-ACTIONS-002]
            get(),
            // 35th + 36th: ShowParkingAskEventBus and GetNearbyPublicParkingsUseCase.
            // ⛔ The COUNT is what matters, not the order: every argument is a bare `get()` whose
            // type comes from the constructor parameter it lands on. So a new dependency needs one
            // more of these AND must be declared at the END of the constructor — one inserted in
            // the middle silently shifts every argument after it. [PARKINGS-ON-THE-MAP-001]
            get(), get(),
        )
    }
    viewModelOf(::ParkingHistoryViewModel)
    viewModelOf(::PaywallViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::LicensesViewModel)
    viewModelOf(::PermissionsViewModel)
    viewModelOf(::VehicleRegistrationViewModel)
    viewModelOf(::VehiclesViewModel)
    // Panel de cifras: recibe por parametro el coche que estaba en pantalla. [STATS-PANEL-001]
    viewModel { params -> StatsViewModel(get(), get(), get(), get(), get(), params.getOrNull()) }
    // vehicleId parameter passed via koinViewModel(parameters = { parametersOf(vehicleId) })
    viewModel { params -> BluetoothConfigViewModel(params.get(), get(), get(), get()) }
}
