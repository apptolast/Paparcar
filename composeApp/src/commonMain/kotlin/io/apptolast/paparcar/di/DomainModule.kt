package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.usecase.user.BootstrapUserDataUseCase
import io.apptolast.paparcar.domain.usecase.user.DeleteAccountUseCase
import io.apptolast.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import io.apptolast.paparcar.domain.usecase.location.GetLocationInfoUseCase
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.coordinator.ParkingDetectionCoordinator
import io.apptolast.paparcar.domain.usecase.parking.DetectParkingDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import io.apptolast.paparcar.domain.usecase.parking.UpdateParkingLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.event.MapFocusEventBus
import io.apptolast.paparcar.domain.usecase.zone.SaveZoneUseCase
import org.koin.dsl.module

val domainModule = module {

    single { MapFocusEventBus() }

    // User UseCases
    factory { GetOrCreateUserProfileUseCase(get(), get()) }
    factory { BootstrapUserDataUseCase(get(), get(), get()) }
    factory {
        DeleteAccountUseCase(
            authRepository = get(),
            userScopedRepos = listOf(get<UserParkingRepository>(), get<VehicleRepository>(), get<UserProfileRepository>(), get<ZoneRepository>()),
            spotRepository = get(),
        )
    }

    // Spot UseCases
    factory { ObserveNearbySpotsUseCase(get()) }
    factory { ReportSpotReleasedUseCase(reportSpotScheduler = get(), getLocationInfo = get(), authRepository = get()) }
    factory { SendSpotSignalUseCase(get()) }

    // Zone UseCases
    factory { SaveZoneUseCase(repository = get(), authRepository = get()) }

    // Location UseCases
    factory { GetLocationInfoUseCase(repository = get()) }
    factory { GetOneLocationUseCase(get()) }
    factory { ObserveAdaptiveLocationUseCase(get()) }
    factory { SearchAddressUseCase(get()) }

    // Parking UseCases
    single { ParkingDetectionConfig() }
    factory { CalculateParkingConfidenceUseCase(get()) }
    factory {
        DetectParkingDepartureUseCase(
            userParkingRepository = get(),
            departureEventBus = get(),
            config = get(),
        )
    }
    factory {
        ConfirmParkingUseCase(
            userParkingRepository = get(),
            vehicleRepository = get(),
            zoneRepository = get(),
            geofenceService = get(),
            notificationPort = get(),
            enrichmentScheduler = get(),
            parkingSyncScheduler = get(),
            authRepository = get(),
            config = get(),
        )
    }
    single {
        ParkingDetectionCoordinator(
            calculateParkingConfidence = get(),
            confirmParking = get(),
            notifyParkingConfirmation = get(),
            notificationPort = get(),
            vehicleRepository = get(),
            stepDetector = get(),
            config = get(),
        )
    }

    // Notification UseCases
    factory { NotifyParkingConfirmationUseCase(get(), get()) }

    // Parking session lifecycle use cases
    factory { ReleaseActiveParkingSessionUseCase(reportSpotReleased = get(), userParkingRepository = get()) }
    factory {
        UpdateParkingLocationUseCase(
            userParkingRepository = get(),
            geofenceService = get(),
            enrichmentScheduler = get(),
            config = get(),
        )
    }

    factory { ObserveParkedVehiclesUseCase(userParkingRepository = get(), vehicleRepository = get()) }

    // Strategy Resolution
    factory { ParkingStrategyResolver(get(), get()) }
}
