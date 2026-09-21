package com.rndeveloper.paparcar.di

import androidx.room.Room
import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.FakeLocationDataSource
import com.rndeveloper.paparcar.data.datasource.local.room.AppDatabase
import com.rndeveloper.paparcar.fakes.data.datasource.remote.FakeFirebaseDataSource
import com.rndeveloper.paparcar.data.datasource.remote.FirebaseDataSource
import com.rndeveloper.paparcar.domain.ActivityRecognitionManager
import com.rndeveloper.paparcar.domain.bluetooth.BluetoothScanner
import com.rndeveloper.paparcar.domain.connectivity.ConnectivityObserver
import com.rndeveloper.paparcar.data.geocoder.RoomLocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.data.repository.AddressAndPlaceRepositoryImpl
import com.rndeveloper.paparcar.data.repository.OpenSourceLicenseRepositoryImpl
import com.rndeveloper.paparcar.domain.geocoder.GeocoderDataSource
import com.rndeveloper.paparcar.domain.geocoder.LocalAddressAndPlaceDataSource
import com.rndeveloper.paparcar.domain.location.LocationDataSource
import com.rndeveloper.paparcar.domain.model.DeviceCapabilities
import com.rndeveloper.paparcar.domain.notification.AppNotificationManager
import com.rndeveloper.paparcar.domain.diagnostics.DeviceInfoProvider
import com.rndeveloper.paparcar.domain.diagnostics.UnknownDeviceInfoProvider
import com.rndeveloper.paparcar.domain.permissions.OemBackgroundReliabilityManager
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.places.PlacesDataSource
import com.rndeveloper.paparcar.domain.places.RoadNetworkDataSource
import com.rndeveloper.paparcar.data.fuel.fuelHttpClient
import com.rndeveloper.paparcar.data.places.OverpassRoadNetworkDataSource
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.AddressAndPlaceRepository
import com.rndeveloper.paparcar.domain.repository.DiagnosticsRepository
import com.rndeveloper.paparcar.domain.repository.OpenSourceLicenseRepository
import com.rndeveloper.paparcar.domain.repository.SpotRepository
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.UserProfileRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.domain.repository.ZoneRepository
import com.rndeveloper.paparcar.domain.sensor.StepDetectorSource
import com.rndeveloper.paparcar.domain.service.DepartureEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceEventBus
import com.rndeveloper.paparcar.domain.service.GeofenceManager
import com.rndeveloper.paparcar.domain.service.ParkingEnrichmentScheduler
import com.rndeveloper.paparcar.domain.service.ParkingSyncScheduler
import com.rndeveloper.paparcar.domain.service.ReportSpotScheduler
import com.rndeveloper.paparcar.notification.FakeAppNotificationManager
import com.rndeveloper.paparcar.data.session.RoomLocalSessionCache
import com.rndeveloper.paparcar.domain.session.LocalSessionCache
import com.rndeveloper.paparcar.fakes.data.repository.FakeActivityRecognitionManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeAppPreferences
import com.rndeveloper.paparcar.fakes.data.repository.FakeAuthRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeBluetoothScanner
import com.rndeveloper.paparcar.fakes.data.repository.FakeConnectivityObserver
import com.rndeveloper.paparcar.fakes.data.repository.FakeDepartureEventBus
import com.rndeveloper.paparcar.fakes.data.repository.FakeDiagnosticsRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeocoderDataSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeofenceEventBus
import com.rndeveloper.paparcar.fakes.data.repository.FakeGeofenceManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeOemBackgroundReliabilityManager
import com.rndeveloper.paparcar.fakes.data.repository.FakeParkingEnrichmentScheduler
import com.rndeveloper.paparcar.fakes.data.repository.FakeParkingSyncScheduler
import com.rndeveloper.paparcar.fakes.data.repository.FakePermissionManager
import com.rndeveloper.paparcar.fakes.data.repository.FakePlacesDataSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeReportSpotScheduler
import com.rndeveloper.paparcar.fakes.data.repository.FakeSpotRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeStepDetectorSource
import com.rndeveloper.paparcar.fakes.data.repository.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeUserProfileRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeVehicleRepository
import com.rndeveloper.paparcar.fakes.data.repository.FakeZoneRepository
import com.rndeveloper.paparcar.fakes.MockScenario
import com.rndeveloper.paparcar.BuildConfig
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val mockModule = module {
    // Dev scenario shared by the scenario-aware fakes below and by the Dev Catalog UI.
    single { MockScenario() }

    // [MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001] The auth ViewModels no longer live here: the
    // whole set arrives with `loginPresentationModule` in MockPaparcarApp. What DOES belong here is
    // the config they need, because mock skips `initLoginKoin` (Firebase) and that is what binds it
    // in production. Without this line the register screen compiles and then dies asking Koin for a
    // LoginLibraryConfig — the same crash the ticket is about, one dependency further in.
    //
    // Built through `paparcarLoginConfig` and given the REAL client id, never inlined: the Dev
    // Catalog exists to show the screens as they ship, and the library's own flags default to
    // offering methods Paparcar does not support. [AUTH-PROVIDERS-EXPLICIT-001]
    single { paparcarLoginConfig(googleWebClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID) }

    // DataSources
    // Pass the shared detection runtime so the mock location can "drive" while a trip is running. [DRIVE-SIM-001]
    single<LocationDataSource> { FakeLocationDataSource(get<com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState>()) }
    single<FirebaseDataSource> { FakeFirebaseDataSource() }
    single<AppNotificationManager> { FakeAppNotificationManager() }
    // Un móvil mock no tiene FCM ni APNs: sin esto, cerrar sesión no resuelve.
    // [PRIV-A-SIGNED-OUT-PHONE-STILL-HEARS-THE-LAST-ACCOUNT-001]
    single<com.rndeveloper.paparcar.domain.push.PushTokenProvider> {
        com.rndeveloper.paparcar.fakes.data.repository.FakeNoopPushTokenProvider()
    }
    // Ajustes resuelve DeviceInfoProvider (reporte de soporte). Solo lo ata androidPlatformModule,
    // que el build mock NO carga, asi que el tab CRASHEABA al abrirlo: Koin
    // NoDefinitionFoundException. El fallback de dominio ya existe y basta aqui.
    // [MOCK-SETTINGS-TAB-CRASHES-WITHOUT-DEVICE-INFO-001]
    single<DeviceInfoProvider> { UnknownDeviceInfoProvider }
    single<GeocoderDataSource> { FakeGeocoderDataSource() }
    single<PlacesDataSource> { FakePlacesDataSource() }
    // Real Overpass road source so the mock sim exercises live OSM map-matching on-device. [ROUTE-SNAP-001]
    // ⛔ Con `fuelHttpClient()`, igual que en produccion: es el cliente que trae el User-Agent con el
    // que Overpass contesta, y ahora ademas el que recorre los espejos.
    // [OVERPASS-A-SINGLE-MIRROR-HAS-NO-FAILOVER-001]
    single<RoadNetworkDataSource> { OverpassRoadNetworkDataSource(fuelHttpClient()) }
    single<PermissionManager> { FakePermissionManager(get()) }
    // Prod binds this in each PLATFORM module (Android true/true, iOS false/false), which the mock
    // graph never loads — EvaluateDetectionReliabilityUseCase resolves it with get(), so without
    // this line Home/Settings/Permissions crash on open. Same literals as the device the Dev
    // Catalog runs on. [IOS-DI-A-MOCK-GRAPH-ONLY-PROD-IS-VERIFIED-001]
    single { DeviceCapabilities(supportsBtStrategy = true, supportsBatteryExemption = true) }
    single<OemBackgroundReliabilityManager> { FakeOemBackgroundReliabilityManager(get()) }
    single<AppPreferences> { FakeAppPreferences(get()) }
    // Scenario-aware since [UI-MAP-PUCK-BELONGS-TO-THE-DRIVE-NOT-TO-ONE-LANE-001]: the connection is
    // what the BT trip surface reads, so the Dev Catalog lever has to drive it.
    single<BluetoothScanner> { FakeBluetoothScanner(get()) }
    single<ConnectivityObserver> { FakeConnectivityObserver(get()) }
    single<ActivityRecognitionManager> { FakeActivityRecognitionManager() }
    single<StepDetectorSource> { FakeStepDetectorSource() }
    single<GeofenceManager> { FakeGeofenceManager() }
    single<DepartureEventBus> { FakeDepartureEventBus() }
    single<GeofenceEventBus> { FakeGeofenceEventBus() }
    single<ParkingEnrichmentScheduler> { FakeParkingEnrichmentScheduler() }
    single<ParkingSyncScheduler> { FakeParkingSyncScheduler() }
    // Domain factories resolve the telemetry port with get(); the mock stack has no dataModule,
    // so the port must exist here or resolving ConfirmParkingUseCase crashes Home. [MOCKQA-001]
    single<com.rndeveloper.paparcar.domain.diagnostics.DetectionEventLogger> { com.rndeveloper.paparcar.domain.diagnostics.NoOpDetectionEventLogger() }
    single<com.rndeveloper.paparcar.domain.diagnostics.UiLocationLogger> { com.rndeveloper.paparcar.domain.diagnostics.NoOpUiLocationLogger() }
    // Product analytics never leaves the Dev Catalog. [ANALYTICS-EVENTS-001]
    single<com.rndeveloper.paparcar.domain.analytics.AnalyticsTracker> { com.rndeveloper.paparcar.domain.analytics.NoOpAnalyticsTracker() }
    // The REAL pinner: pinning the widget is launcher behaviour worth testing from the mock too.
    // [WIDGET-ACTIONS-002]
    single<com.rndeveloper.paparcar.domain.service.WidgetPinRequester> {
        com.rndeveloper.paparcar.widget.ParkedWidgetPinner(androidContext())
    }
    // El almacén de la ruta conducida vive en dataModule, que el arranque mock NO carga — así que
    // `HomeTripController` moría al resolverse en cuanto un viaje se ponía en marcha. Lo destapó el
    // verify del grafo mock, no una pasada a mano. Va el REAL, como el pinner del widget: desde
    // [TRIP-A-LONG-DRIVE-KEEPS-ITS-ORIGIN-001] es Room sobre la base in-memory del mock, y con un
    // falso el sim de conducción no redibujaría su propia ruta.
    // [DET-ROUTE-TRACK-001] [MOCK-A-SECOND-GRAPH-NEEDS-A-SECOND-VERIFY-001]
    single<com.rndeveloper.paparcar.domain.detection.ports.DrivingRouteStore> {
        com.rndeveloper.paparcar.data.detection.RoomDrivingRouteStore(dao = get())
    }
    // Misma regla del no-dataModule: `AppViewModel` y `HomeViewModel` resuelven esta señal con
    // get(), y sin ella el grafo entero no monta — o sea, TODAS las pantallas revientan al entrar.
    // Es un holder sin dependencias, así que el mock usa el de verdad.
    // [ZONE-AN-ALERT-IS-DELIVERED-WHERE-YOU-CAN-SEE-IT-001] [MOCK-A-SECOND-GRAPH-NEEDS-A-SECOND-VERIFY-001]
    single { com.rndeveloper.paparcar.domain.notification.MapVisibility() }
    // Same no-dataModule rule: ObserveZoneSpotAlertsUseCase resolves this with get(), so without it
    // the whole graph fails to build and EVERY screen crashes on entry (AppViewModel is a shared
    // dependency). Real Room impl over the in-memory DB, same pattern as DrivingRouteStore above.
    // [ZONE-AN-ALERT-NOBODY-IS-AWAKE-TO-RECEIVE-001] [MOCK-A-SECOND-GRAPH-NEEDS-A-SECOND-VERIFY-001]
    single<com.rndeveloper.paparcar.domain.repository.ZoneWatchRepository> {
        com.rndeveloper.paparcar.data.repository.RoomZoneWatchRepository(dao = get())
    }
    // Same no-dataModule rule, different feature: GetNearbyFuelStationsUseCase's price-watch path
    // resolves this with get(), so without it Home crashes on open. Real Room impl, same pattern.
    // [FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001] [MOCK-A-SECOND-GRAPH-NEEDS-A-SECOND-VERIFY-001]
    single<com.rndeveloper.paparcar.domain.repository.FuelWatchRepository> {
        com.rndeveloper.paparcar.data.fuel.RoomFuelWatchRepository(dao = get())
    }
    single<ReportSpotScheduler> { FakeReportSpotScheduler() }
    single<com.rndeveloper.paparcar.domain.detection.ports.ManualParkingDetection> {
        // Same shared runtime, so the Home "I'm driving" CTA starts the sim too. [DRIVE-SIM-001]
        com.rndeveloper.paparcar.fakes.data.repository.FakeManualParkingDetection(get<com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState>())
    }
    single<com.rndeveloper.paparcar.domain.detection.ports.ArrivalHandoffDetection> {
        // [DET-HANDOFF-NOT-MANUAL-001] Own fake: "the safety net started it" must never be able to
        // satisfy an assertion about the user starting it.
        com.rndeveloper.paparcar.fakes.data.repository.FakeArrivalHandoffDetection(get<com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState>())
    }
    single<com.rndeveloper.paparcar.domain.detection.ports.DepartureWatchResumer> {
        // Same shared runtime: tapping "Reactivate" on the interrupted-watch row flips presence to
        // Sentry, so the row heals in the mock build like it does on device. [DET-WATCH-REACTIVATE-001]
        com.rndeveloper.paparcar.fakes.data.repository.FakeDepartureWatchResumer(get<com.rndeveloper.paparcar.domain.detection.MutableDetectionRuntimeState>())
    }

    // Session
    single<LocalSessionCache> { RoomLocalSessionCache(get()) }

    // Repositories
    single<SpotRepository> { FakeSpotRepository() }
    single<AuthRepository> { FakeAuthRepository(get()) }
    single<VehicleRepository> { FakeVehicleRepository(get()) }
    // El grupo del coche compartido, sin backend. [VEH-SHARED-001]
    single<com.rndeveloper.paparcar.domain.repository.SharedCarRepository> {
        com.rndeveloper.paparcar.fakes.data.repository.FakeSharedCarRepository(get())
    }
    // Runtime-aware: while the driving sim runs, report no active session so readiness reaches Monitoring. [DRIVE-SIM-001]
    // Scenario-aware: the "own parked session" lever seeds an active session for the ACTIVE
    // vehicle so Home reaches the watching/parked state. [UX-PARKED-STATE-001]
    single<UserParkingRepository> {
        FakeUserParkingRepository(get<com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState>(), get<MockScenario>())
    }
    single<UserProfileRepository> { FakeUserProfileRepository() }
    single<ZoneRepository> { FakeZoneRepository(get()) }
    // Scenario-aware: la palanca "Plus activo" del Dev Catalog mueve todos los gates de Plus
    // en vivo, incluida la degradación Plus → free que la tienda real no deja ensayar. [MONEY-001]
    single<com.rndeveloper.paparcar.domain.repository.EntitlementRepository> {
        com.rndeveloper.paparcar.fakes.data.repository.FakeEntitlementRepository(get<MockScenario>())
    }
    // GetNearbyFuelStationsUseCase resolves this; same no-dataModule rule as above, and it is the
    // one that actually bit: entering Home died with NoDefinitionFoundException while the state
    // gallery kept rendering the section fine, because the gallery never builds a ViewModel.
    // [FUEL-STATIONS-LIST-001]
    single<com.rndeveloper.paparcar.domain.fuel.FuelStationsSource> {
        com.rndeveloper.paparcar.fakes.data.repository.FakeFuelStationsSource()
    }
    // Same reason, same breath: the public-parkings port is declared here the day it is written, not
    // the day it crashes Home. [PARKINGS-ON-THE-MAP-001]
    single<com.rndeveloper.paparcar.domain.parkings.PublicParkingsSource> {
        com.rndeveloper.paparcar.fakes.data.repository.FakePublicParkingsSource()
    }
    // ⛔ Las tres que faltaban, y ya no se buscan a mano: `MockGraphPortParityTest` compara los
    // conjuntos de puertos de `dataModule` y de este módulo, así que la próxima se caza en segundos
    // en vez de en la tercera pantalla. [MOCK-A-PORT-DECLARED-IN-PROD-MUST-EXIST-IN-MOCK-001]
    //
    // La primera es la que MATABA el tab Vehículos entero —y con él el panel de cifras—, porque
    // `ResolveFuelPriceUseCase` la resuelve dentro del cuerpo de su definición, donde `verify()` no
    // mira. Las otras dos no habían mordido todavía: se pedían solo desde módulos Android que el
    // catálogo no carga, y estaban a una pantalla de distancia de hacerlo.
    single<com.rndeveloper.paparcar.domain.repository.AverageFuelPricesRepository> {
        com.rndeveloper.paparcar.fakes.data.repository.FakeAverageFuelPricesRepository()
    }
    single<com.rndeveloper.paparcar.domain.repository.ZoneAlertSubscriptionRepository> {
        com.rndeveloper.paparcar.fakes.data.repository.FakeZoneAlertSubscriptionRepository()
    }
    single<com.rndeveloper.paparcar.domain.sharedcar.SharedCarMirror> {
        com.rndeveloper.paparcar.fakes.data.repository.FakeSharedCarMirror()
    }
    // DeleteAccountUseCase resolves this in its swept-repos list; the mock stack has no dataModule,
    // so the port must exist here or resolving it crashes. [ACCOUNT-DELETE-SWEEPS-DIAGNOSTICS-001]
    single<DiagnosticsRepository> { FakeDiagnosticsRepository() }
    // La pantalla de licencias moría al abrirse en mock: su repo solo estaba en dataModule. El REAL,
    // que no tiene dependencias y lee el fichero generado en build — un falso enseñaría una lista de
    // atribución inventada, que es justo lo que esa pantalla no puede hacer.
    // [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001] [MOCK-A-SECOND-GRAPH-NEEDS-A-SECOND-VERIFY-001]
    single<OpenSourceLicenseRepository> { OpenSourceLicenseRepositoryImpl() }
    // El log local del reporte de soporte lo ata androidPlatformModule, que el mock NO carga: "Enviar
    // reporte" moria al resolverse. El REAL, que solo necesita contexto — un falso enviaria un
    // reporte sin el log, que es LO UNICO que ese reporte lleva.
    // [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001] [MOCK-A-SECOND-GRAPH-NEEDS-A-SECOND-VERIFY-001]
    single<com.rndeveloper.paparcar.domain.diagnostics.LocalDiagnosticsLog> {
        com.rndeveloper.paparcar.logging.AndroidLocalDiagnosticsLog(androidContext())
    }
    // SendDiagnosticsReportUseCase resolves the uploader; same no-dataModule rule as above.
    // [SUPPORT-REPORT-SHIPS-THE-LOCAL-LOG-001]
    single<com.rndeveloper.paparcar.domain.diagnostics.DiagnosticsReportUploader> {
        com.rndeveloper.paparcar.fakes.data.repository.FakeDiagnosticsReportUploader()
    }
    single<AddressAndPlaceRepository> { AddressAndPlaceRepositoryImpl(get(), get(), get()) }
    // Real impl: static in-code data, no platform deps — the licenses screen shows as it ships.
    single<com.rndeveloper.paparcar.domain.repository.OpenSourceLicenseRepository> {
        com.rndeveloper.paparcar.data.repository.OpenSourceLicenseRepositoryImpl()
    }

    // Database (In-memory Room)
    single<AppDatabase> {
        Room.inMemoryDatabaseBuilder(androidContext(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    // DAOs
    single { get<AppDatabase>().parkingSessionDao() }
    single { get<AppDatabase>().userProfileDao() }
    single { get<AppDatabase>().vehicleDao() }
    single { get<AppDatabase>().spotDao() }
    single { get<AppDatabase>().zoneDao() }
    single { get<AppDatabase>().geocoderCacheDao() }
    single { get<AppDatabase>().tripRoutePointDao() }
    single { get<AppDatabase>().zoneWatchDao() }
    single { get<AppDatabase>().fuelWatchDao() }

    // Local datasources
    single<LocalAddressAndPlaceDataSource> { RoomLocalAddressAndPlaceDataSource(get()) }
}
