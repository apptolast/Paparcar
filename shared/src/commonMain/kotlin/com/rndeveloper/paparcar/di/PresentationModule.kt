package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import com.rndeveloper.paparcar.presentation.app.SplashViewModel
import com.rndeveloper.paparcar.presentation.app.AppViewModel
import com.rndeveloper.paparcar.presentation.home.HomeGeocodingController
import com.rndeveloper.paparcar.presentation.home.HomeSearchController
import com.rndeveloper.paparcar.presentation.home.HomeNearbyAlertController
import com.rndeveloper.paparcar.presentation.home.HomeSpotsController
import com.rndeveloper.paparcar.presentation.home.HomeTripController
import com.rndeveloper.paparcar.presentation.home.HomeViewModel
import com.rndeveloper.paparcar.presentation.licenses.LicensesViewModel
import com.rndeveloper.paparcar.presentation.map.ParkingHistoryViewModel
import com.rndeveloper.paparcar.presentation.paywall.PaywallViewModel
import com.rndeveloper.paparcar.presentation.paywall.PlusStatusViewModel
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
    // Explicit, not viewModelOf: `analyticsTracker` is a nullable constructor parameter, and the
    // reflection DSL resolves every parameter with get() — crashing where nothing binds it, same
    // trap `KoinOptionalDependencyGuardrailTest` caught for SettingsViewModel's widgetPinRequester.
    // AnalyticsTracker IS always bound (every flavour/platform), so get() stays correct here.
    viewModel {
        AppViewModel(
            permissionManager = get(),
            appPreferences = get(),
            connectivityObserver = get(),
            vehicleRepository = get(),
            zoneRepository = get(),
            userParkingRepository = get(),
            observeZoneSpotAlerts = get(),
            mapVisibility = get(),
            nearbySpotAlerts = get(),
            appNotificationManager = get(),
            publishDetectionState = get(),
            resolveInstallAttribution = get(),
            adMeasurementLane = get(),
            setAdMeasurementConsent = get(),
            observePlusGrant = get(),
            claimFoundersPromo = get(),
            authRepository = get(),
            analyticsTracker = get(),
        )
    }
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
    factory { HomeNearbyAlertController(authRepository = get(), nearbySpotAlerts = get()) }
    // Explicit constructor (not viewModelOf): HomeViewModel has >22 ctor params and viewModelOf's
    // reflection helper only supports up to 22. [DET-TOGGLE-002]
    viewModel {
        HomeViewModel(
            mapVisibility = get(),
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
            // 37th + 38th: ToggleFollowFuelStationUseCase and ObserveFollowedFuelStationsUseCase.
            // [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]
            get(), get(),
            // 39th: HomeNearbyAlertController.
            // [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
            get(),
            correctDetectedParking = get(),
            confirmStillParked = get(),
            answerRefuelStop = get(),
        )
    }
    viewModelOf(::ParkingHistoryViewModel)
    // Explicit for the same reason as SettingsViewModel below: the analytics tracker is a nullable
    // parameter, and viewModelOf would resolve it with get(). The reason rides parametersOf.
    viewModel { params ->
        PaywallViewModel(
            observePlusGrant = get(),
            getPlusOffers = get(),
            purchasePlus = get(),
            restorePurchases = get(),
            reason = params.get(),
            analyticsTracker = getOrNull(),
        )
    }
    viewModelOf(::PlusStatusViewModel)
    // Explicit, not viewModelOf: `viewModelOf` resolves EVERY constructor parameter with get(), a
    // nullable one included, so the widget door — bound only by :app on Android — crashed Settings on
    // iOS with NoDefinitionFoundException. getOrNull() is what makes the parameter optional.
    // [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001]
    viewModel {
        SettingsViewModel(
            prefs = get(),
            authRepository = get(),
            signOut = get(),
            userProfileRepository = get(),
            deleteAccountUseCase = get(),
            setFirstStepsDismissed = get(),
            permissionManager = get(),
            vehicleRepository = get(),
            observeDetectionReliability = get(),
            sendDiagnosticsReport = get(),
            observePlusGrant = get(),
            adMeasurementLane = get(),
            setAdMeasurementConsent = get(),
            widgetPinRequester = getOrNull(),
            analyticsTracker = get(),
        )
    }
    viewModelOf(::LicensesViewModel)
    viewModelOf(::PermissionsViewModel)
    // Constructor explícito (no `viewModelOf`): el último parámetro es la unidad del surtidor, que
    // trae su propio valor por defecto para que un test pueda sustituirlo. El DSL `…Of` intentaría
    // resolverlo del grafo en vez de dejarlo por defecto.
    // [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
    viewModel { VehicleRegistrationViewModel(get(), get(), get(), get()) }
    viewModelOf(::VehiclesViewModel)
    // Panel de cifras: recibe por parametro el coche que estaba en pantalla. [STATS-PANEL-001]
    viewModel { params ->
        StatsViewModel(get(), get(), get(), get(), get(), get(), get(), params.getOrNull())
    }
    // vehicleId parameter passed via koinViewModel(parameters = { parametersOf(vehicleId) })
    viewModel { params -> BluetoothConfigViewModel(params.get(), get(), get(), get()) }
}
