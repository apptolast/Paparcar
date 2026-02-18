package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.usecase.location.GetStoredLocationsUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveLocationUpdatesUseCase
import io.apptolast.paparcar.domain.usecase.location.SaveLocationToLocalUseCase
import io.apptolast.paparcar.domain.usecase.notification.BuildSpotDetectionNotificationUseCase
import io.apptolast.paparcar.domain.usecase.notification.BuildSpotUploadNotificationUseCase
import io.apptolast.paparcar.domain.usecase.notification.DismissNotificationUseCase
import io.apptolast.paparcar.domain.usecase.notification.ShowDebugNotificationUseCase
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
    factory { ObserveLocationUpdatesUseCase(get()) }
    factory { SaveLocationToLocalUseCase(get()) }
    factory { GetStoredLocationsUseCase(get()) }

    // Notification UseCases
    factory { BuildSpotDetectionNotificationUseCase(get()) }
    factory { BuildSpotUploadNotificationUseCase(get()) }
    factory { DismissNotificationUseCase(get()) }
    factory { ShowDebugNotificationUseCase(get()) }
}
