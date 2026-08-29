package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.usecase.user.BootstrapUserDataUseCase
import io.apptolast.paparcar.domain.usecase.user.DeleteAccountUseCase
import io.apptolast.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.usecase.location.GetLastKnownLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportManualSpotUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import io.apptolast.paparcar.domain.repository.DiagnosticsRepository
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.event.MapFocusEventBus
import io.apptolast.paparcar.domain.event.StartAddParkingEventBus
import io.apptolast.paparcar.domain.usecase.zone.SaveOrUpdateZoneUseCase
import io.apptolast.paparcar.domain.usecase.zone.SaveZoneUseCase
import org.koin.dsl.module

val domainModule = module {

    // [DET-DI-DETECTION-MODULE-001] Parking detection is declared in its own file — see
    // [detectionModule] for what belongs there and why it is included from here instead of being
    // listed at each of the four Koin entry points.
    includes(detectionModule)

    single { MapFocusEventBus() }
    single { StartAddParkingEventBus() }

    // User UseCases
    factory { GetOrCreateUserProfileUseCase(get(), get()) }
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

    // Spot UseCases
    factory { ObserveNearbySpotsUseCase(get()) }
    factory { ReportSpotReleasedUseCase(reportSpotScheduler = get(), getAddressAndPlace = get(), authRepository = get()) }
    // Home's manual "avisar plaza libre" policy (id, MANUAL type, carbody fallback). [HOME-ATOMIZE-001 F4]
    factory { ReportManualSpotUseCase(reportSpotReleased = get(), vehicleRepository = get()) }
    factory { SendSpotSignalUseCase(get()) }

    // Zone UseCases
    factory { SaveZoneUseCase(repository = get(), authRepository = get()) }
    // Home's zone form (create + in-place edit). [HOME-ATOMIZE-001 F4]
    factory { SaveOrUpdateZoneUseCase(repository = get(), saveZone = get()) }

    // Location UseCases
    factory { GetAddressAndPlaceUseCase(repository = get()) }
    // tripTrail is platform-bound (getOrNull → null where absent, e.g. iOS). [DET-BREADCRUMBS-001]
    factory { GetOneLocationUseCase(get(), tripTrail = getOrNull()) }
    factory { GetLastKnownLocationUseCase(get()) } // [DET-AR-REARM-001] passive — no geofence provocation
    factory { ObserveAdaptiveLocationUseCase(get()) }
    factory { SearchAddressUseCase(get()) }

}
