package com.rndeveloper.paparcar.di

import com.rndeveloper.paparcar.domain.usecase.diagnostics.SendDiagnosticsReportUseCase
import com.rndeveloper.paparcar.domain.usecase.user.BootstrapUserDataUseCase
import com.rndeveloper.paparcar.domain.usecase.vehicle.DeleteVehicleUseCase
import com.rndeveloper.paparcar.domain.usecase.user.DeleteAccountUseCase
import com.rndeveloper.paparcar.domain.usecase.user.GetOrCreateUserProfileUseCase
import com.rndeveloper.paparcar.domain.usecase.user.SetFirstStepsDismissedUseCase
import com.rndeveloper.paparcar.domain.usecase.user.SignOutUseCase
import com.rndeveloper.paparcar.domain.usecase.location.GetAddressAndPlaceUseCase
import com.rndeveloper.paparcar.domain.usecase.location.GetOneLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.location.ObserveAdaptiveLocationUseCase
import com.rndeveloper.paparcar.domain.usecase.location.SearchAddressUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ObserveNearbySpotsUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportManualSpotUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.ReportSpotReleasedUseCase
import com.rndeveloper.paparcar.domain.usecase.spot.SendSpotSignalUseCase
import com.rndeveloper.paparcar.domain.repository.DiagnosticsRepository
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneAlertSubscriptionRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.event.MapFocusEventBus
import com.rndeveloper.paparcar.domain.event.StartAddParkingEventBus
import com.rndeveloper.paparcar.domain.usecase.entitlement.GetPlusOffersUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.ObserveIsPlusUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.PurchasePlusUseCase
import com.rndeveloper.paparcar.domain.usecase.entitlement.RestorePurchasesUseCase
import com.rndeveloper.paparcar.domain.notification.MapVisibility
import com.rndeveloper.paparcar.domain.notification.NearbySpotAlerts
import com.rndeveloper.paparcar.domain.usecase.zone.ObserveZoneSpotAlertsUseCase
import com.rndeveloper.paparcar.domain.usecase.zone.SaveOrUpdateZoneUseCase
import com.rndeveloper.paparcar.domain.usecase.zone.SaveZoneUseCase
import org.koin.dsl.module
import com.rndeveloper.paparcar.domain.repository.AverageFuelPricesRepository
import com.rndeveloper.paparcar.domain.usecase.fuel.EvaluateCheaperStationNearbyUseCase
import com.rndeveloper.paparcar.domain.usecase.fuel.EvaluateDailyPriceMoveUseCase
import com.rndeveloper.paparcar.domain.usecase.fuel.GetNearbyFuelStationsUseCase
import com.rndeveloper.paparcar.domain.usecase.fuel.ObserveFollowedFuelStationsUseCase
import com.rndeveloper.paparcar.domain.usecase.fuel.RunFuelPriceWatchUseCase
import com.rndeveloper.paparcar.domain.usecase.stats.RunMonthRecapUseCase
import com.rndeveloper.paparcar.domain.usecase.fuel.ToggleFollowFuelStationUseCase
import com.rndeveloper.paparcar.domain.usecase.fuel.ResolveFuelPriceUseCase
import com.rndeveloper.paparcar.domain.usecase.parkings.GetNearbyPublicParkingsUseCase

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
    // Salir retira antes los tokens de push, con la sesion todavia abierta.
    // [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
    factory {
        SignOutUseCase(
            authRepository = get(),
            zoneAlertSubscriptions = get(),
            sharedCarRepository = get(),
            pushTokens = get(),
        )
    }
    // Borrar un coche disuelve o abandona el grupo que hubiera detras; vive en un caso de uso
    // porque el repositorio de coche compartido ya depende del de vehiculos y la dependencia
    // inversa seria un ciclo. [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
    factory { DeleteVehicleUseCase(vehicleRepository = get(), sharedCarRepository = get(), appPreferences = get()) }
    factory {
        DeleteAccountUseCase(
            authRepository = get(),
            // The order is the decision here, and two of its three reasons were learned the hard
            // way. [ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001][PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001]
            //
            //  1. Push tokens FIRST — zone alerts and shared cars both hold them. A phone that is
            //     still in a fan-out list while its account is being erased gets told about a spot
            //     or a parking mid-deletion.
            //  2. SharedCar BEFORE Vehicle — the vehicles are how the groups are found
            //     (`observeVehicles().filter { isShared }`). Wipe the cars first and the mirrors
            //     become unreachable, with the member doc and its tokens left inside.
            //  3. Diagnostics LAST — the loggers keep draining their channels while the chain runs,
            //     so sweeping telemetry right before the auth delete minimises the window in which
            //     a straggler event could re-create a doc.
            userScopedRepos = listOf(
                get<ZoneAlertSubscriptionRepository>(),
                get<SharedCarRepository>(),
                get<UserParkingRepository>(),
                get<VehicleRepository>(),
                get<UserProfileRepository>(),
                get<ZoneRepository>(),
                get<DiagnosticsRepository>(),
            ),
            spotRepository = get(),
        )
    }

    // Diagnostics UseCases — localLog se liga en la plataforma, y desde
    // [IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001] lo ligan LAS DOS. El `getOrNull` se queda:
    // un informe sin log sigue siendo un informe, y esa tolerancia es lo que hizo que la ausencia en
    // iOS durara sin que nadie se equivocara. [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
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

    // Stats — THE aggregator every stats surface derives from. [STATS-CORE-001]
    factory { com.rndeveloper.paparcar.domain.usecase.stats.ComputePeriodStatsUseCase() }

    // Entitlement UseCases — Paparcar Plus [MONEY-001]
    factory { ObserveIsPlusUseCase(entitlementRepository = get()) }
    factory { PurchasePlusUseCase(entitlementRepository = get()) }
    factory { GetPlusOffersUseCase(entitlementRepository = get()) }
    factory { RestorePurchasesUseCase(entitlementRepository = get()) }

    // Zone UseCases
    factory { SaveZoneUseCase(repository = get(), authRepository = get()) }
    // Home's zone form (create + in-place edit). [HOME-ATOMIZE-001 F4]
    factory { SaveOrUpdateZoneUseCase(repository = get(), saveZone = get()) }
    // Una sola señal de «¿el mapa está delante?», con un solo dueño: la escribe el corchete de
    // ciclo de vida del mapa y la leen Home y el reparto de avisos, que es quien elige si la plaza
    // se cuenta dentro de la app o por notificación.
    // [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001]
    single { MapVisibility() }
    // Una senal, un dueno: Home decide y AppViewModel entrega.
    // [ZONE-AN-ALERT-NEAR-YOU-IS-NOT-AN-ALERT-IN-YOUR-ZONE-001]
    single { NearbySpotAlerts() }

    // Plus zone alerts. DOS carriles y UNA memoria: el stream mientras la app vive, el PUSH
    // mientras no. Los dos apuntan en el MISMO repositorio, que es lo que impide que la misma
    // plaza se anuncie dos veces por dos rutas.
    // [MONEY-001] [ZONE-A-SPOT-TELLS-THE-SERVER-AND-THE-SERVER-TELLS-YOU-001]
    factory {
        ObserveZoneSpotAlertsUseCase(
            zoneRepository = get(),
            spotRepository = get(),
            watchRepository = get(),
            appPreferences = get(),
            authRepository = get(),
            // El gate VIVO de Plus: la pausa por caducidad.
            // [ZONE-A-PAID-ALERT-MUST-NOT-BE-DOCUMENTED-AS-FREE-001]
            observeIsPlus = get(),
        )
    }

    // Location UseCases
    factory { GetAddressAndPlaceUseCase(repository = get()) }
    // tripTrail is platform-bound (getOrNull → null where absent, e.g. iOS). [DET-BREADCRUMBS-001]
    factory { GetOneLocationUseCase(get(), tripTrail = getOrNull()) }
    // chargingStateSource is platform-bound (getOrNull → null = no charging boost, e.g. iOS).
    // [DET-ADAPTIVE-SAMPLING-EARNS-ITS-FIXES-001]
    factory { ObserveAdaptiveLocationUseCase(get(), chargingStateSource = getOrNull()) }
    factory { SearchAddressUseCase(get()) }

    // Gasolineras cercanas — el geocoder da país Y provincia de una sola consulta, que es lo
    // que enruta la petición. [FUEL-STATIONS-LIST-001]
    factory { GetNearbyFuelStationsUseCase(stations = get(), geocoder = get()) }

    // Vigilancia de precios [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001]. Los dos
    // veredictos son puros y sin dependencias: se instancian solos y se testean sin reloj ni radio.
    factory { EvaluateDailyPriceMoveUseCase() }
    factory { EvaluateCheaperStationNearbyUseCase() }
    factory { ToggleFollowFuelStationUseCase(repository = get()) }
    factory { ObserveFollowedFuelStationsUseCase(repository = get()) }
    factory {
        RunFuelPriceWatchUseCase(
            watchRepository = get(),
            vehicleRepository = get(),
            getNearbyFuelStations = get(),
            getOneLocation = get(),
            observeIsPlus = get(),
            appPreferences = get(),
            evaluateDailyPriceMove = get(),
            evaluateCheaperStationNearby = get(),
        )
    }

    // [STATS-PLUS-THE-RECAP-KNOCKS-001] El veredicto del resumen mensual. Sin `isPlus` propio: las
    // cifras salen del historial gateado por el plan, igual que la card del panel.
    factory {
        RunMonthRecapUseCase(
            userParkingRepository = get(),
            vehicleRepository = get(),
            observeIsPlus = get(),
            appPreferences = get(),
            // El resolver VIVO, no el offline por defecto: la notificación cita el mismo precio que
            // la card, y el de la card sale de la tabla que un refresco puede reemplazar.
            // [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
            resolveFuelPrice = get(),
        )
    }

    // El escalón de la media nacional se lee EN LA LLAMADA, no al construir el use case: si
    // capturase la tabla aquí, un refresco posterior no llegaría nunca a las pantallas ya vivas.
    // [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
    factory { ResolveFuelPriceUseCase(averages = get<AverageFuelPricesRepository>()::current) }

    // Parkings públicos cercanos. [PARKINGS-ON-THE-MAP-001]
    // ⛔ SIN geocoder, a diferencia del carril de gasolineras: no hay registro por país sobre este
    // puerto, así que el país no lo leía nadie — solo costaba latencia delante de cada pregunta y
    // una petición duplicada por zona al cambiar la clave de caché.
    // [PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001]
    factory { GetNearbyPublicParkingsUseCase(parkings = get()) }

}
