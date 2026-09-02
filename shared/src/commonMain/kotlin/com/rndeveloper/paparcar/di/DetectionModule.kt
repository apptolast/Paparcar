package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.domain.detection.CoordinatorParkingDetector
import com.rndeveloper.paparcar.domain.detection.DetectionPhaseSink
import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.ParkingStrategyResolver
import com.rndeveloper.paparcar.domain.model.ParkingDetectionConfig
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateArEnterArmUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateBtArbitrationUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateBtParkUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateFirstParkNudgeUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.EvaluateGeofenceExitUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDepartureWatchGapUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReadinessUseCase
import com.rndeveloper.paparcar.domain.usecase.detection.ObserveDetectionReliabilityUseCase
import com.rndeveloper.paparcar.domain.usecase.notification.NotifyParkingConfirmationUseCase
import com.rndeveloper.paparcar.domain.usecase.notification.ResolveAskedStreetUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.CalculateParkingConfidenceUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ClearParkNudgeUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ConfirmParkingUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.DetectParkingDepartureUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateBackfillDeferralUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateHonestCloseUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateParkingDecisionUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateSafetyNetCheckUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.EvaluateUnattendedParkingSaveUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.FinalizeDeducedDeparture
import com.rndeveloper.paparcar.domain.usecase.parking.FinalizeDeducedDepartureUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ObserveParkedVehiclesUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ProcessConfirmedDepartureUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.ReleaseActiveParkingSessionUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.RetractDeducedDeparture
import com.rndeveloper.paparcar.domain.usecase.parking.RetractDeducedDepartureUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.RevertParkingUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.RunDepartureCheckUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.RunHonestCloseUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.SaveManualParkingUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.UpdateParkingLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.parking.VerifyDepartureEvidenceUseCase
import com.rndeveloper.paparcar.domain.usecase.vehicle.DeclareActiveVehicleUseCase
import com.rndeveloper.paparcar.domain.usecase.vehicle.SwapActiveVehicleFencesUseCase
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

/**
 * [DET-DI-DETECTION-MODULE-001] Everything the parking-detection area needs, in the one file that
 * answers "what does detection depend on?" — the common-side twin of `androidDetectionModule` and
 * `iosDetectionModule`, which already existed while this half lived scattered inside
 * `domainModule` alongside users, spots and zones.
 *
 * ## What belongs here
 *
 * A registration lives in this module if it depends on [ParkingDetectionConfig], on the geofence
 * service, on the departure bus, on [DetectionRuntimeState] or on another registration of this
 * module — **or** if its only consumers are detection surfaces. Everything else (user, spots,
 * zones, generic location) stays in `domainModule`.
 *
 * ## Why it is `includes`d rather than listed at startup
 *
 * `domainModule` pulls this one in with `includes(detectionModule)` instead of the four Koin entry
 * points (`PaparcarApp`, `MockPaparcarApp`, `MainViewController`, `MockMainViewController`) each
 * naming it. Listing it four times means a fifth entry point can forget it, and a missing detection
 * binding is caught by nothing — the project has no Koin module verification, so the first symptom
 * would be a `NoDefinitionFoundException` on a real drive. `includes` makes forgetting it
 * structurally impossible while keeping this file the single place the area is declared.
 *
 * ⚠️ The platform halves are NOT included from here: they are listed per entry point because each
 * platform binds its own (`androidDetectionModule` on Android, `iosDetectionModule` on iOS), and
 * `MockPaparcarApp` deliberately binds neither — its fakes come from `mockModule`.
 */
val detectionModule = module {

    // ── Config: the one object every evaluator below reads its thresholds from ────────────────
    single { ParkingDetectionConfig() }

    // ── Pure verdicts [DET-VERDICT-NOT-PREDICATE-001] ─────────────────────────────────────────
    // Config-only evaluators: no I/O, no side effects, each one owning a line of the diagnostic
    // vocabulary. Registered here rather than built by their consumers, so a second consumer
    // never ends up with a second instance.
    factory { CalculateParkingConfidenceUseCase(get()) }
    factory { EvaluateParkingDecisionUseCase(get()) }
    factory { EvaluateSafetyNetCheckUseCase(config = get()) } // [DET-SAFETY-NET-001]
    factory { EvaluateBackfillDeferralUseCase(config = get()) } // [DET-BACKFILL-TAINT-001]
    factory { EvaluateGeofenceExitUseCase(config = get()) } // [AUDIT-A9-KMP-001]
    factory { EvaluateBtParkUseCase(config = get()) } // [DET-AUDIT-002 T2/T3]
    factory { EvaluateBtArbitrationUseCase() } // [DET-TIERS-001] BT-as-arbiter over the coordinator
    factory { EvaluateArEnterArmUseCase(config = get()) } // [DET-AR-FIRST-001]
    // [DET-WALK-ENTERED-ANCHOR-ZONE-001] What the unattended timeout should do with the session.
    // [DET-DI-DETECTION-MODULE-001] It was the ONE detection verdict never registered here: the
    // coordinator built it by hand in a constructor default, so it was invisible in the graph and
    // no second consumer could have shared it.
    factory { EvaluateUnattendedParkingSaveUseCase(config = get()) }
    // [DET-HONEST-CLOSE-001] Honest-close ladder: pure evaluator + orchestration.
    factory { EvaluateHonestCloseUseCase(config = get()) }
    factory {
        RunHonestCloseUseCase(
            userParkingRepository = get(),
            confirmParking = get(),
            notificationPort = get(),
            evaluateHonestClose = get(),
            config = get(),
        )
    }

    // ── Confirmation: where both strategies converge ──────────────────────────────────────────
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
            // Android-only (no iOS impl yet) — getOrNull so other platforms bind null and the
            // honest-close budget simply stays unsealed there. [DET-HONEST-CLOSE-001]
            detectionStepAnchors = getOrNull(),
            // Android-only route store — getOrNull so iOS binds null (no route snapshot). [DET-ROUTE-TRACK-001]
            drivingRouteStore = getOrNull(),
        )
    }
    factory { ResolveAskedStreetUseCase(get()) }
    factory { NotifyParkingConfirmationUseCase(get(), get(), get()) }

    // ── The coordinator (probabilistic strategy) ──────────────────────────────────────────────
    single {
        CoordinatorParkingDetector(
            calculateParkingConfidence = get(),
            confirmParking = get(),
            notifyParkingConfirmation = get(),
            resolveAskedStreet = get(),
            notificationPort = get(),
            vehicleRepository = get(),
            stepDetector = get(),
            config = get(),
            detectionEventLogger = get(),
            evaluateParkingDecision = get(),
            phaseSink = get(),
            // [DET-DI-DETECTION-MODULE-001] Was a hand-built default inside the constructor.
            evaluateUnattendedParkingSave = get(),
            // [DET-HANDOFF-NOT-MANUAL-001 §B] The deduced departure's commit, deferred to the
            // moment this session measures a drive.
            finalizeDeducedDeparture = get(),
            // [DET-HANDOFF-NOT-MANUAL-001 §B.3] …and its withdrawal, at the moment this session
            // ends having measured none.
            retractDeducedDeparture = get(),
        )
    }
    // [DET-SOLID-001] The departure-check seam, extracted from DepartureDetectionWorker.
    single<com.rndeveloper.paparcar.domain.detection.DepartureConfirmationListener> { get<CoordinatorParkingDetector>() }

    // ── Parking session lifecycle ─────────────────────────────────────────────────────────────
    factory { ReleaseActiveParkingSessionUseCase(reportSpotReleased = get(), userParkingRepository = get(), geofenceService = get(), detectionEventLogger = get()) }
    // [VEH-ACTIVE-FENCE-001 · 2c] Active-vehicle declaration + geofence swap.
    factory { SwapActiveVehicleFencesUseCase(userParkingRepository = get(), vehicleRepository = get(), geofenceService = get(), config = get()) }
    factory { DeclareActiveVehicleUseCase(vehicleRepository = get(), swapFences = get()) }
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
        UpdateParkingLocationUseCase(
            userParkingRepository = get(),
            geofenceService = get(),
            enrichmentScheduler = get(),
            config = get(),
            departureEventBus = get(),
        )
    }
    // Home's user-confirmed pin: create / move / detected-prompt confirm. [HOME-ATOMIZE-001 F4]
    factory {
        SaveManualParkingUseCase(
            confirmParking = get(),
            updateParkingLocation = get(),
            notificationPort = get(),
            manualParkingDetection = get(),
        )
    }
    factory { ObserveParkedVehiclesUseCase(userParkingRepository = get(), vehicleRepository = get()) }
    factory { ClearParkNudgeUseCase(appPreferences = get(), notificationPort = get()) } // [DET-NUDGE-PERSIST-001]

    // ── Departure: detect, verify, commit / retract ───────────────────────────────────────────
    factory {
        DetectParkingDepartureUseCase(
            userParkingRepository = get(),
            departureEventBus = get(),
            config = get(),
        )
    }
    factory { VerifyDepartureEvidenceUseCase(departureEventBus = get(), config = get()) } // [DET-G-05]
    // [DET-HANDOFF-NOT-MANUAL-001 §B] Promotes a provisional spot + releases the car once a drive
    // is measured — the other half of a deduced departure. Bound also as its seam, which is the
    // type the coordinator asks for. [DET-COORDINATOR-NO-OPTIONAL-DEPS-001]
    factory {
        FinalizeDeducedDepartureUseCase(
            userParkingRepository = get(),
            reportSpotReleased = get(),
            geofenceService = get(),
            detectionEventLogger = get(),
        )
    } bind FinalizeDeducedDeparture::class
    // [DET-HANDOFF-NOT-MANUAL-001 §B.3] Withdraws that same provisional spot when the trip ends
    // having measured no drive at all — the losing half of the same pair.
    factory {
        RetractDeducedDepartureUseCase(
            userParkingRepository = get(),
            spotRepository = get(),
            detectionEventLogger = get(),
        )
    } bind RetractDeducedDeparture::class
    factory {
        ProcessConfirmedDepartureUseCase(
            userParkingRepository = get(),
            reportSpotReleased = get(),
            geofenceService = get(),
            departureEventBus = get(),
            detectionEventLogger = get(),
            config = get(),
        )
    }
    factory {
        RunDepartureCheckUseCase(
            detectParkingDeparture = get(),
            processConfirmedDeparture = get(),
            getOneLocation = get(),
            departureEventBus = get(),
            departureConfirmationListener = get(),
            detectionRuntime = get(),
            config = get(),
            detectionEventLogger = get(),
        )
    }

    // ── Strategy resolution ───────────────────────────────────────────────────────────────────
    factory { ParkingStrategyResolver(get(), get()) }

    // ── Runtime state + readiness [DET-READY-001] ─────────────────────────────────────────────
    // Shared singleton: CoordinatorDetectionService mutates it, the use case observes it. [DET-READY-001c]
    // Also bound as DetectionPhaseSink: the coordinator consumes that contract, and hiding the
    // identity inside a typed `get<MutableDetectionRuntimeState>()` kept the sink invisible to
    // graph verification. [DET-KOIN-MODULE-VERIFY-001]
    single { MutableDetectionRuntimeState() } binds arrayOf(DetectionRuntimeState::class, DetectionPhaseSink::class)
    factory {
        ObserveDetectionReadinessUseCase(
            vehicleRepository = get(),
            userParkingRepository = get(),
            permissionManager = get(),
            detectionRuntime = get(),
            strategyResolver = get(),
            appPreferences = get(),
            bluetoothScanner = get(),
        )
    }
    factory {
        EvaluateFirstParkNudgeUseCase(
            observeDetectionReadiness = get(),
            appPreferences = get(),
        )
    }

    // ── Reliability — single evaluator every surface reads [DET-RELIABILITY-001] ──────────────
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

    // ── Departure-watch gap — "the watcher should be live but the service is dead" [DET-WATCH-REACTIVATE-001] ──
    factory {
        ObserveDepartureWatchGapUseCase(
            userParkingRepository = get(),
            vehicleRepository = get(),
            strategyResolver = get(),
            appPreferences = get(),
            detectionRuntime = get(),
        )
    }

}
