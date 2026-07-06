package io.apptolast.paparcar.di

import io.apptolast.paparcar.domain.usecase.user.BootstrapUserDataUseCase
import io.apptolast.paparcar.domain.usecase.user.DeleteAccountUseCase
import io.apptolast.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import io.apptolast.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import io.apptolast.paparcar.domain.usecase.location.GetLastKnownLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.GetOneLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import io.apptolast.paparcar.domain.usecase.location.SearchAddressUseCase
import io.apptolast.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import io.apptolast.paparcar.domain.model.ParkingDetectionConfig
import io.apptolast.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import io.apptolast.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import io.apptolast.paparcar.domain.coordinator.CoordinatorParkingDetector
import io.apptolast.paparcar.domain.usecase.parking.DetectParkingDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.EvaluateSafetyNetCheckUseCase
import io.apptolast.paparcar.domain.usecase.parking.RunDepartureCheckUseCase
import io.apptolast.paparcar.domain.usecase.parking.VerifyDepartureEvidenceUseCase
import io.apptolast.paparcar.domain.usecase.parking.ProcessConfirmedDepartureUseCase
import io.apptolast.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import io.apptolast.paparcar.domain.usecase.parking.RevertParkingUseCase
import io.apptolast.paparcar.domain.usecase.parking.UpdateParkingLocationUseCase
import io.apptolast.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import io.apptolast.paparcar.domain.detection.DetectionRuntimeState
import io.apptolast.paparcar.domain.detection.MutableDetectionRuntimeState
import io.apptolast.paparcar.domain.detection.ParkingStrategyResolver
import io.apptolast.paparcar.domain.usecase.detection.EvaluateDetectionReliabilityUseCase
import io.apptolast.paparcar.domain.usecase.detection.EvaluateFirstParkNudgeUseCase
import io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import io.apptolast.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import org.koin.dsl.bind
import io.apptolast.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import io.apptolast.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import io.apptolast.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import io.apptolast.paparcar.domain.repository.UserParkingRepository
import io.apptolast.paparcar.domain.repository.UserProfileRepository
import io.apptolast.paparcar.domain.repository.VehicleRepository
import io.apptolast.paparcar.domain.repository.ZoneRepository
import io.apptolast.paparcar.domain.event.MapFocusEventBus
import io.apptolast.paparcar.domain.event.StartAddParkingEventBus
import io.apptolast.paparcar.domain.usecase.zone.SaveZoneUseCase
import org.koin.dsl.module

val domainModule = module {

    single { MapFocusEventBus() }
    single { StartAddParkingEventBus() }

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
    factory { ReportSpotReleasedUseCase(reportSpotScheduler = get(), getAddressAndPlace = get(), authRepository = get()) }
    factory { SendSpotSignalUseCase(get()) }

    // Zone UseCases
    factory { SaveZoneUseCase(repository = get(), authRepository = get()) }

    // Location UseCases
    factory { GetAddressAndPlaceUseCase(repository = get()) }
    factory { GetOneLocationUseCase(get()) }
    factory { GetLastKnownLocationUseCase(get()) } // [DET-AR-REARM-001] passive — no geofence provocation
    factory { ObserveAdaptiveLocationUseCase(get()) }
    factory { SearchAddressUseCase(get()) }

    // Parking UseCases
    single { ParkingDetectionConfig() }
    factory { CalculateParkingConfidenceUseCase(get()) }
    factory { EvaluateParkingDecisionUseCase(get()) }
    factory {
        DetectParkingDepartureUseCase(
            userParkingRepository = get(),
            departureEventBus = get(),
            config = get(),
        )
    }
    factory { VerifyDepartureEvidenceUseCase(departureEventBus = get(), config = get()) } // [DET-G-05]
    factory { EvaluateSafetyNetCheckUseCase(config = get()) } // [DET-SAFETY-NET-001]
    factory {
        ConfirmParkingUseCase(
            userParkingRepository = get(),
            vehicleRepository = get(),
            zoneRepository = get(),
            geofenceService = get(),
            enrichmentScheduler = get(),
            authRepository = get(),
            config = get(),
            departureEventBus = get(),
            appPreferences = get(),
            parkingSyncScheduler = get(),
            detectionEventLogger = get(),
        )
    }
    single {
        CoordinatorParkingDetector(
            calculateParkingConfidence = get(),
            confirmParking = get(),
            notifyParkingConfirmation = get(),
            notificationPort = get(),
            vehicleRepository = get(),
            stepDetector = get(),
            config = get(),
            detectionEventLogger = get(),
            evaluateParkingDecision = get(),
            phaseSink = get<MutableDetectionRuntimeState>(),
        )
    }

    // Notification UseCases
    factory { NotifyParkingConfirmationUseCase(get(), get()) }

    // Parking session lifecycle use cases
    factory { ReleaseActiveParkingSessionUseCase(reportSpotReleased = get(), userParkingRepository = get()) }
    // [REFACTOR-300] Revert flow for the post-save "No, cancelar" notification action.
    factory {
        RevertParkingUseCase(
            userParkingRepository = get(),
            geofenceService = get(),
            notificationPort = get(),
            detectionEventLogger = get(),
        )
    }
    factory {
        ProcessConfirmedDepartureUseCase(
            userParkingRepository = get(),
            reportSpotReleased = get(),
            geofenceService = get(),
            departureEventBus = get(),
            detectionEventLogger = get(),
        )
    }
    // [DET-SOLID-001] The departure-check seam, extracted from DepartureDetectionWorker.
    single<io.apptolast.paparcar.domain.detection.DepartureConfirmationListener> { get<CoordinatorParkingDetector>() }
    factory {
        RunDepartureCheckUseCase(
            detectParkingDeparture = get(),
            processConfirmedDeparture = get(),
            getOneLocation = get(),
            departureEventBus = get(),
            departureConfirmationListener = get(),
            config = get(),
            detectionEventLogger = get(),
        )
    }
    factory {
        UpdateParkingLocationUseCase(
            userParkingRepository = get(),
            geofenceService = get(),
            enrichmentScheduler = get(),
            config = get(),
            departureEventBus = get(),
        )
    }

    factory { ObserveParkedVehiclesUseCase(userParkingRepository = get(), vehicleRepository = get()) }

    // Strategy Resolution
    factory { ParkingStrategyResolver(get(), get()) }

    // Detection readiness [DET-READY-001]
    // Shared singleton: CoordinatorDetectionService mutates it, the use case observes it. [DET-READY-001c]
    single { MutableDetectionRuntimeState() } bind DetectionRuntimeState::class
    factory {
        ObserveDetectionReadinessUseCase(
            vehicleRepository = get(),
            userParkingRepository = get(),
            permissionManager = get(),
            detectionRuntime = get(),
            strategyResolver = get(),
            appPreferences = get(),
        )
    }
    factory {
        EvaluateFirstParkNudgeUseCase(
            observeDetectionReadiness = get(),
            appPreferences = get(),
        )
    }

    // Detection reliability — single evaluator every surface reads [DET-RELIABILITY-001]
    factory { EvaluateDetectionReliabilityUseCase() }
    factory {
        ObserveDetectionReliabilityUseCase(
            vehicleRepository = get(),
            permissionManager = get(),
            oemBackgroundReliabilityManager = get(),
            strategyResolver = get(),
            evaluateDetectionReliability = get(),
        )
    }

}
