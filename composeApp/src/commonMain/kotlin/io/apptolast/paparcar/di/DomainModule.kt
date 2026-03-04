package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.usecase.location.GetAddressUseCase
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
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
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.DetectAndReportParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.DetectParkingDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.GetAllUserParkingsUseCase
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
    factory { GetOneLocationUseCase(get()) }
    factory { ObserveAdaptiveLocationUseCase(get()) }
    factory { SaveLocationToLocalUseCase(get()) }
    factory { GetStoredLocationsUseCase(get()) }

    // Parking UseCases
    single { ParkingDetectionConfig() }
    factory { CalculateParkingConfidenceUseCase(get()) }
    factory { SaveUserParkingUseCase(get()) }
    factory { GetUserParkingUseCase(get()) }
    factory { ObserveUserParkingUseCase(get()) }
    factory { GetAllUserParkingsUseCase(get()) }
    factory { ClearUserParkingUseCase(get()) }
    factory { DetectParkingDepartureUseCase(get(), get(), get()) }
    factory {
        ConfirmParkingUseCase(
            saveUserParking = get(),
            geofenceService = get(),
            notifyParkingSpotSaved = get(),
            config = get(),
        )
    }
    single {
        DetectAndReportParkingUseCase(
            calculateParkingConfidence = get(),
            confirmParking = get(),
            notifyParkingConfirmation = get(),
            dismissNotification = get(),
            config = get(),
        )
    }

    // Notification UseCases
    factory { NotifyParkingConfirmationUseCase(get()) }
    factory { NotifyParkingSpotSavedUseCase(get()) }
    factory { NotifySpotUploadingUseCase(get()) }
    factory { DismissNotificationUseCase(get()) }
}