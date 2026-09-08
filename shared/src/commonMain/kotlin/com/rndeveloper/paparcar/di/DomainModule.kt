package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.domain.usecase.diagnostics.SendDiagnosticsReportUseCase
import com.rndeveloper.paparcar.domain.usecase.user.BootstrapUserDataUseCase
import com.rndeveloper.paparcar.domain.usecase.user.DeleteAccountUseCase
import com.rndeveloper.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import com.rndeveloper.paparcar.domain.usecase.user.SetFirstStepsDismissedUseCase
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.usecase.location.GetOneLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.location.SearchAddressUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportManualSpotUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import com.rndeveloper.paparcar.domain.repository.DiagnosticsRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.event.MapFocusEventBus
import com.rndeveloper.paparcar.domain.event.StartAddParkingEventBus
import com.rndeveloper.paparcar.domain.usecase.entitlement.GetPlusOffersUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.PurchasePlusUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.RestorePurchasesUseCase
import com.rndeveloper.paparcar.domain.usecase.zone.ObserveZoneSpotAlertsUseCase
import com.rndeveloper.paparcar.domain.usecase.zone.SaveOrUpdateZoneUseCase
import com.rndeveloper.paparcar.domain.usecase.zone.SaveZoneUseCase
import org.koin.dsl.module

val domainModule = module {

    // [DET-DI-DETECTION-MODULE-001] Parking detection is declared in its own file — see
    // [detectionModule] for what belongs there and why it is included from here instead of being
    // listed at each of the four Koin entry points.
    includes(detectionModule)

    single { MapFocusEventBus() }
    single { StartAddParkingEventBus() }
    // The widget's "show this parking" ask — same conflated-bus shape. [WIDGET-ACTIONS-002]
    single { com.rndeveloper.paparcar.domain.event.ShowParkingAskEventBus() }

    // User UseCases
    factory { GetOrCreateUserProfileUseCase(get(), get()) }
    // [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001] Device preference +
    // account fact behind one verb, so Home and Settings cannot drift.
    factory { SetFirstStepsDismissedUseCase(get(), get(), get()) }
    factory { BootstrapUserDataUseCase(get(), get(), get()) }
    factory {
        DeleteAccountUseCase(
            authRepository = get(),
            // Diagnostics goes LAST: the loggers keep draining their channels while the chain runs,
            // so sweeping the telemetry right before the auth delete minimises the window in which
            // a straggler event could re-create a doc. [ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001]
            userScopedRepos = listOf(
                get<UserParkingRepository>(),
                get<VehicleRepository>(),
                get<UserProfileRepository>(),
                get<ZoneRepository>(),
                get<DiagnosticsRepository>(),
            ),
            spotRepository = get(),
        )
    }

    // Diagnostics UseCases — localLog is platform-bound (getOrNull → null where absent, e.g. iOS:
    // the report still ships, headers-only). [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
    factory {
        SendDiagnosticsReportUseCase(
            authRepository = get(),
            uploader = get(),
            localLog = getOrNull(),
            deviceInfo = get(),
        )
    }

    // Spot UseCases
    factory { ObserveNearbySpotsUseCase(get()) }
    factory { ReportSpotReleasedUseCase(reportSpotScheduler = get(), getAddressAndPlace = get(), authRepository = get(), analyticsTracker = get()) }
    // Home's manual "avisar plaza libre" policy (id, MANUAL type, carbody fallback). [HOME-ATOMIZE-001 F4]
    factory { ReportManualSpotUseCase(reportSpotReleased = get(), vehicleRepository = get()) }
    factory { SendSpotSignalUseCase(get()) }

    // Entitlement UseCases — Paparcar Plus [MONEY-001]
    factory { ObserveIsPlusUseCase(entitlementRepository = get()) }
    factory { PurchasePlusUseCase(entitlementRepository = get()) }
    factory { GetPlusOffersUseCase(entitlementRepository = get()) }
    factory { RestorePurchasesUseCase(entitlementRepository = get()) }

    // Zone UseCases
    factory { SaveZoneUseCase(repository = get(), authRepository = get()) }
    // Home's zone form (create + in-place edit). [HOME-ATOMIZE-001 F4]
    factory { SaveOrUpdateZoneUseCase(repository = get(), saveZone = get()) }
    // Plus zone alerts — collected by AppViewModel, delivered as a local notification. [MONEY-001]
    factory {
        ObserveZoneSpotAlertsUseCase(
            zoneRepository = get(),
            spotRepository = get(),
            observeIsPlus = get(),
            appPreferences = get(),
            authRepository = get(),
        )
    }

    // Location UseCases
    factory { GetAddressAndPlaceUseCase(repository = get()) }
    // tripTrail is platform-bound (getOrNull → null where absent, e.g. iOS). [DET-BREADCRUMBS-001]
    factory { GetOneLocationUseCase(get(), tripTrail = getOrNull()) }
    factory { ObserveAdaptiveLocationUseCase(get()) }
    factory { SearchAddressUseCase(get()) }

}
