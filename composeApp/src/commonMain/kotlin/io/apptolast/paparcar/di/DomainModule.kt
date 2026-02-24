package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.usecase.location.GetAddressUseCase
import io.apptolast.paparcar.domain.usecase.location.GetStoredLocationsUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.location.SaveLocationToLocalUseCase
import io.apptolast.paparcar.domain.usecase.notification.DismissNotificationUseCase
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingSpotSavedUseCase
import io.apptolast.paparcar.domain.usecase.notification.NotifySpotUploadingUseCase
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ClearUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.DetectAndReportParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.GetAllParkingSessionsUseCase
import io.apptolast.paparcar.domain.usecase.parking.GetUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.SaveUserParkingUseCase
import io.apptolast.paparcar.domain.usecase.spot.GetNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import org.koin.dsl.module

val domainModule = module {

    // Spot UseCases
    factory { GetNearbySpotsUseCase(get()) }
    factory { ObserveNearbySpotsUseCase(get()) }
    factory { ReportSpotReleasedUseCase(get()) }

    // Location UseCases
    factory { GetAddressUseCase(get()) }
    factory { ObserveLocationUpdatesUseCase(get()) }
    factory { ObserveAdaptiveLocationUseCase(get()) }
    factory { SaveLocationToLocalUseCase(get()) }
    factory { GetStoredLocationsUseCase(get()) }

    // Parking UseCases
    single { ParkingDetectionConfig() }
    factory { CalculateParkingConfidenceUseCase(get()) }
    factory { SaveUserParkingUseCase(get()) }
    factory { GetUserParkingUseCase(get()) }
    factory { ObserveUserParkingUseCase(get()) }
    factory { GetAllParkingSessionsUseCase(get()) }
    factory { ClearUserParkingUseCase(get()) }
    factory {
        DetectAndReportParkingUseCase(
            calculateParkingConfidence = get(),
            saveUserParking = get(),
            geofenceService = get(),
            notifyParkingConfirmation = get(),
            notifyParkingSpotSaved = get(),
            config = get(),
        )
    }

    // Notification UseCases
    factory { NotifyParkingConfirmationUseCase(get()) }
    factory { NotifyParkingSpotSavedUseCase(get()) }
    factory { NotifySpotUploadingUseCase(get()) }
    factory { DismissNotificationUseCase(get()) }
}