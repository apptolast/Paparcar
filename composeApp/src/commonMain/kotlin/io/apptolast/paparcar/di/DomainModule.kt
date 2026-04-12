package io.apptolast.paparcar.di

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
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import org.koin.dsl.module

val domainModule = module {

    // User UseCases
    factory { GetOrCreateUserProfileUseCase(get(), get(), get()) }

    // Spot UseCases
    factory { ObserveNearbySpotsUseCase(get()) }
    factory { ReportSpotReleasedUseCase(reportSpotScheduler = get(), getLocationInfo = get()) }

    // Location UseCases
    factory { GetLocationInfoUseCase(geocoder = get(), placesPort = get()) }
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
            geofenceService = get(),
            notificationPort = get(),
            enrichmentScheduler = get(),
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
            config = get(),
        )
    }

    // Notification UseCases
    factory { NotifyParkingConfirmationUseCase(get(), get()) }
}
