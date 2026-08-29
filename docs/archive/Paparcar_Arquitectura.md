# Paparcar — Documento de Arquitectura v4.0

> Proyecto KMP (Kotlin Multiplatform) · Clean Architecture · MVI · Koin · Firebase

---

## Changelog v4.0

| # | Cambio |
|---|--------|
| 1 | `AndroidLocationDataSource` dividida en interfaz + impl: `AndroidLocationDataSource` + `AndroidLocationDataSourceImpl` |
| 2 | `SpotDetectionForegroundService` llama a `ObserveLocationUpdatesUseCase` en vez de al repositorio |
| 3 | `LocationDataSource` renombrada a `PlatformLocationDataSource` (diferencia fuente GPS viva de Room) |
| 4 | `ActivityRecognitionManager` con interfaz + impl + reglas de buenas prácticas |
| 5 | `NotificationService` / `NotificationServiceImpl` → renombrado a `AppNotificationManager` / `AppNotificationManagerImpl` + casos de uso |
| 6 | `HomeScreen` completamente definida: State, Intent, Effect, ViewModel con datos reales |
| 7 | `Result` y manejo de errores: `PaparcarError` sealed class + `DomainResult<T>` en toda la cadena |
| 8 | Estructura `androidMain` reorganizada por feature/responsibility |
| 9 | Room implementado correctamente con `expect/actual` |

---

## 1. Visión General del Producto

**Paparcar** es una app comunitaria de plazas de aparcamiento. Cuando un usuario arranca el coche y abandona su plaza, la app detecta automáticamente el evento y publica la plaza liberada en un mapa en tiempo real para que otros usuarios la encuentren. Todo ocurre sin necesidad de que el usuario saque el teléfono del bolsillo.

### Nomenclatura clave

| Término | Significado |
|---|---|
| **Spot** | Plaza de aparcamiento (nunca "parking") |
| **Spot libre** | Plaza liberada por un usuario |
| **SpotDetectionForegroundService** | Obtiene ubicación y la guarda en Room |
| **SpotUploadForegroundService** | Sube el spot a Firebase cuando se confirma IN_VEHICLE |

---

## 2. Stack Tecnológico

### 2.1 Common (compartido entre plataformas)

| Tecnología | Uso |
|---|---|
| Kotlin Multiplatform (KMP) | Lógica de negocio, datos, dominio compartido |
| Compose Multiplatform | UI compartida entre Android e iOS |
| MVI + BaseViewModel | Patrón de presentación con State / Intent / Effect |
| Koin Multiplatform | Inyección de dependencias |
| Room KMP | Persistencia local — `expect/actual` para el builder, resto en commonMain |
| Firebase — GitLive SDK | Base de datos remota, auth, analítica (KMP nativo) |
| Kotlin Coroutines + Flow | Asincronía y streams de datos reactivos |
| kotlinx.serialization | Serialización de modelos |
| `kotlin.time.Clock` | Tiempo actual — `Clock.System.now().toEpochMilliseconds()` |

### 2.2 Android (exclusivo)

| Tecnología | Uso |
|---|---|
| Android Accelerometer API | Detección de vibración del motor |
| Google Activity Recognition API | Confirmación de transición `IN_VEHICLE` |
| `ActivityRecognitionManager` | Interfaz del contrato de registro de transiciones |
| `ActivityRecognitionManagerImpl` | Implementación con PendingIntent encapsulado |
| `SpotDetectionForegroundService` | Colecta ubicaciones vía `ObserveLocationUpdatesUseCase` → Room |
| `SpotUploadForegroundService` | Sube el spot a Firebase |
| `ActivityTransitionReceiver` | Solo enruta, sin lógica de negocio |
| FusedLocationProviderClient | Ubicación de alta precisión (`PRIORITY_HIGH_ACCURACY`) |
| `PlatformLocationDataSource` | Interfaz KMP — contrato de plataforma para ubicaciones en vivo |
| `AndroidLocationDataSource` | Interfaz androidMain — contrato Android para FusedLocation |
| `AndroidLocationDataSourceImpl` | Implementa `AndroidLocationDataSource` con `callbackFlow` |
| `AppNotificationManager` | Interfaz KMP — contrato para gestión de notificaciones |
| `AppNotificationManagerImpl` | Implementación Android |

### 2.3 iOS (futuro)

| Tecnología | Uso |
|---|---|
| CoreMotion | Equivalente al acelerómetro |
| CoreLocation | Localización de alta precisión |
| CMMotionActivityManager | Equivalente a Activity Recognition |

---

## 3. Arquitectura General — Clean Architecture + SOLID

```
┌─────────────────────────────────────────────────────────────────┐
│                        commonMain                               │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────┐   │
│  │ Presentation │   │    Domain    │   │       Data       │   │
│  │ BaseViewModel│   │  UseCases    │   │  Repos + DSources│   │
│  │  XxxScreen   │   │  Models      │   │  Mappers + Room  │   │
│  │  XxxState    │   │  PaparcarError│  │  expect/actual   │   │
│  └──────────────┘   └──────────────┘   └──────────────────┘   │
│  ┌────────────────────────────────────────────────────────┐    │
│  │                 DI (Koin)                              │    │
│  └────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────┐    ┌──────────────────────┐
│            androidMain               │    │       iosMain        │
│  detection/                          │    │  detection/          │
│    AccelerometerManager              │    │  CoreMotion          │
│    ActivityRecognitionManager(iface) │    │  CoreLocation        │
│    ActivityRecognitionManagerImpl    │    │  IosLocationDataSrc  │
│    SpotDetectionForegroundService    │    └──────────────────────┘
│    SpotUploadForegroundService       │
│    ActivityTransitionReceiver        │
│  location/                           │
│    AndroidLocationDataSource (iface) │
│    AndroidLocationDataSourceImpl     │
│  notification/                       │
│    AppNotificationManagerImpl        │
│  db/                                 │
│    AppDatabaseBuilder (actual)       │
│  di/                                 │
│    AndroidDetectionModule            │
└──────────────────────────────────────┘
```

### Principios SOLID aplicados

- **S** — Una responsabilidad por clase: Services solo coordinan, UseCases solo ejecutan lógica
- **O** — Repositorios implementan interfaces; se extiende sin modificar
- **L** — Implementaciones sustituibles sin romper contratos (`PlatformLocationDataSource`)
- **I** — Interfaces pequeñas y específicas: `PlatformLocationDataSource` solo provee ubicaciones, `AppNotificationManager` solo gestiona notificaciones
- **D** — Dependencias sobre abstracciones: `LocationRepositoryImpl` depende de `PlatformLocationDataSource` (interfaz), nunca de `AndroidLocationDataSourceImpl`

---

## 4. Estructura de Módulos KMP

```
Paparcar/
├── composeApp/
│   └── src/
│       ├── commonMain/kotlin/com/rndeveloper/paparcar/
│       │   ├── presentation/
│       │   │   ├── base/
│       │   │   │   └── BaseViewModel.kt
│       │   │   ├── home/
│       │   │   │   ├── HomeScreen.kt
│       │   │   │   ├── HomeViewModel.kt
│       │   │   │   ├── HomeState.kt
│       │   │   │   ├── HomeEffect.kt
│       │   │   │   └── HomeIntent.kt
│       │   │   ├── map/
│       │   │   │   ├── MapScreen.kt
│       │   │   │   ├── MapViewModel.kt
│       │   │   │   ├── MapState.kt
│       │   │   │   ├── MapEffect.kt
│       │   │   │   └── MapIntent.kt
│       │   │   └── history/
│       │   │       ├── HistoryScreen.kt
│       │   │       ├── HistoryViewModel.kt
│       │   │       ├── HistoryState.kt
│       │   │       ├── HistoryEffect.kt
│       │   │       └── HistoryIntent.kt
│       │   ├── domain/
│       │   │   ├── error/
│       │   │   │   └── PaparcarError.kt          ← sealed class centralizada
│       │   │   ├── model/
│       │   │   │   ├── Spot.kt
│       │   │   │   └── SpotLocation.kt
│       │   │   ├── repository/
│       │   │   │   ├── SpotRepository.kt
│       │   │   │   └── LocationRepository.kt
│       │   │   └── usecase/
│       │   │       ├── spot/
│       │   │       │   ├── ReportSpotReleasedUseCase.kt
│       │   │       │   ├── GetNearbySpotsUseCase.kt
│       │   │       │   └── ObserveNearbySpotsUseCase.kt
│       │   │       ├── location/
│       │   │       │   ├── ObserveLocationUpdatesUseCase.kt  ← NUEVO
│       │   │       │   ├── SaveLocationToLocalUseCase.kt
│       │   │       │   └── GetStoredLocationsUseCase.kt
│       │   │       └── notification/
│       │   │           ├── ShowSpotDetectionNotificationUseCase.kt  ← NUEVO
│       │   │           ├── ShowSpotUploadNotificationUseCase.kt     ← NUEVO
│       │   │           └── DismissNotificationUseCase.kt            ← NUEVO
│       │   ├── data/
│       │   │   ├── repository/
│       │   │   │   ├── SpotRepositoryImpl.kt
│       │   │   │   └── LocationRepositoryImpl.kt
│       │   │   ├── datasource/
│       │   │   │   ├── platform/
│       │   │   │   │   └── PlatformLocationDataSource.kt  ← antes LocationDataSource
│       │   │   │   ├── remote/
│       │   │   │   │   ├── FirebaseDataSource.kt
│       │   │   │   │   └── dto/SpotDto.kt
│       │   │   │   └── local/
│       │   │   │       ├── LocalLocationDataSource.kt
│       │   │   │       └── room/
│       │   │   │           ├── AppDatabase.kt          ← @Database Room
│       │   │   │           ├── AppDatabaseBuilder.kt   ← expect fun
│       │   │   │           ├── LocationDao.kt
│       │   │   │           └── LocationEntity.kt
│       │   │   ├── notification/
│       │   │   │   └── AppNotificationManager.kt  ← INTERFAZ commonMain
│       │   │   └── mapper/
│       │   │       ├── LocationEntityMapper.kt
│       │   │       └── SpotDtoMapper.kt
│       │   └── di/
│       │       ├── PresentationModule.kt
│       │       ├── DomainModule.kt
│       │       └── DataModule.kt
│       │
│       ├── androidMain/kotlin/com/rndeveloper/paparcar/
│       │   ├── detection/
│       │   │   ├── AccelerometerManager.kt
│       │   │   ├── ActivityRecognitionManager.kt      ← INTERFAZ androidMain
│       │   │   ├── ActivityRecognitionManagerImpl.kt  ← IMPLEMENTACIÓN
│       │   │   ├── SpotDetectionForegroundService.kt
│       │   │   ├── SpotUploadForegroundService.kt
│       │   │   └── ActivityTransitionReceiver.kt
│       │   ├── location/
│       │   │   ├── AndroidLocationDataSource.kt       ← INTERFAZ androidMain
│       │   │   └── AndroidLocationDataSourceImpl.kt   ← IMPLEMENTACIÓN
│       │   ├── notification/
│       │   │   └── AppNotificationManagerImpl.kt      ← IMPLEMENTACIÓN Android
│       │   ├── db/
│       │   │   └── AppDatabaseBuilder.kt              ← actual fun
│       │   └── di/
│       │       └── AndroidDetectionModule.kt
│       │
│       └── iosMain/kotlin/com/rndeveloper/paparcar/
│           ├── detection/
│           │   └── IosLocationDataSource.kt           ← implementa PlatformLocationDataSource
│           └── db/
│               └── AppDatabaseBuilder.kt              ← actual fun (iOS)
│
├── gradle/libs.versions.toml
└── composeApp/build.gradle.kts
```

---

## 5. Manejo de Errores — `PaparcarError` + `DomainResult`

### 5.1 `PaparcarError` (dominio)

```kotlin
// domain/error/PaparcarError.kt (commonMain)
sealed class PaparcarError {

    // Errores de localización
    sealed class Location : PaparcarError() {
        data object PermissionDenied : Location()
        data object ProviderDisabled : Location()
        data class Unknown(val message: String) : Location()
    }

    // Errores de red / Firebase
    sealed class Network : PaparcarError() {
        data object NoConnection : Network()
        data object Timeout : Network()
        data class ServerError(val code: Int, val message: String) : Network()
        data class Unknown(val message: String) : Network()
    }

    // Errores de base de datos local
    sealed class Database : PaparcarError() {
        data object NotFound : Database()
        data class WriteError(val message: String) : Database()
        data class Unknown(val message: String) : Database()
    }

    // Errores de detección de actividad
    sealed class Detection : PaparcarError() {
        data object ActivityRecognitionUnavailable : Detection()
        data object PermissionDenied : Detection()
    }
}
```

### 5.2 `DomainResult<T>`

Usamos el `Result<T>` de Kotlin en toda la cadena de repositorio→usecase→viewmodel. Para errores de dominio tipados, el tipo de error se comunica como `PaparcarError` dentro del `Throwable`.

```kotlin
// Extensiones para mapear errores en repositorios/datasources
fun Exception.toPaparcarError(): PaparcarError = when (this) {
    is SecurityException -> PaparcarError.Location.PermissionDenied
    is IOException       -> PaparcarError.Network.NoConnection
    else                 -> PaparcarError.Network.Unknown(message.orEmpty())
}

// En repositorios: capturar y mapear
override suspend fun saveLocation(location: SpotLocation): Result<Unit> = runCatching {
    localLocationDataSource.insert(location.toEntity())
}.mapError { it.toPaparcarError() }

// Extensión helper para mapear el error a PaparcarError dentro de Result
inline fun <T> Result<T>.mapError(transform: (Throwable) -> PaparcarError): Result<T> =
    this.recoverCatching { throw PaparcarException(transform(it)) }

class PaparcarException(val error: PaparcarError) : Exception(error.toString())
```

### 5.3 Mapper de errores a UI

```kotlin
// presentation/base/ErrorMapper.kt (commonMain)
fun PaparcarError.toUserMessage(): String = when (this) {
    is PaparcarError.Location.PermissionDenied     -> "Necesitamos permiso de ubicación"
    is PaparcarError.Location.ProviderDisabled      -> "Activa el GPS para continuar"
    is PaparcarError.Network.NoConnection           -> "Sin conexión a internet"
    is PaparcarError.Network.Timeout                -> "La conexión ha tardado demasiado"
    is PaparcarError.Network.ServerError            -> "Error del servidor (${code})"
    is PaparcarError.Database.NotFound              -> "No se encontraron datos"
    is PaparcarError.Detection.PermissionDenied     -> "Permiso de actividad denegado"
    else                                            -> "Ha ocurrido un error inesperado"
}
```

---

## 6. Estructura de Capas

### 6.1 Presentation — BaseViewModel

```kotlin
// presentation/base/BaseViewModel.kt (commonMain)
abstract class BaseViewModel<S, I, E> {

    private val viewModelJob = SupervisorJob()
    protected val viewModelScope: CoroutineScope = CoroutineScope(Dispatchers.Main + viewModelJob)

    private val _state: MutableStateFlow<S> by lazy { MutableStateFlow(initState()) }
    val state = _state.asStateFlow()

    private val _effect: MutableSharedFlow<E> = MutableSharedFlow()
    val effect = _effect.asSharedFlow()

    abstract fun initState(): S
    abstract fun handleIntent(intent: I)

    protected fun updateState(handler: S.() -> S) {
        _state.update(handler)
    }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effect.emit(effect) }
    }

    fun onClear() {
        viewModelScope.cancel()
    }
}
```

### 6.2 HomeScreen — Definición Completa

#### HomeState.kt

```kotlin
// presentation/home/HomeState.kt (commonMain)
data class HomeState(
    val isLoading: Boolean = false,
    val nearbySpots: List<Spot> = emptyList(),
    val isDetectionActive: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val hasActivityPermission: Boolean = false,
    val userLocation: SpotLocation? = null,
    val error: PaparcarError? = null
)
```

#### HomeIntent.kt

```kotlin
// presentation/home/HomeIntent.kt (commonMain)
sealed class HomeIntent {
    data object LoadNearbySpots : HomeIntent()
    data object RefreshSpots : HomeIntent()
    data class SpotSelected(val spotId: String) : HomeIntent()
    data class PermissionResult(
        val locationGranted: Boolean,
        val activityGranted: Boolean
    ) : HomeIntent()
    data object StartDetection : HomeIntent()
    data object StopDetection : HomeIntent()
    data object DismissError : HomeIntent()
    data object OpenMap : HomeIntent()
}
```

#### HomeEffect.kt

```kotlin
// presentation/home/HomeEffect.kt (commonMain)
sealed class HomeEffect {
    data class NavigateToMap(val spotId: String? = null) : HomeEffect()
    data object RequestLocationPermission : HomeEffect()
    data object RequestActivityPermission : HomeEffect()
    data class ShowSnackbar(val message: String) : HomeEffect()
    data object ScrollToUserLocation : HomeEffect()
}
```

#### HomeViewModel.kt

```kotlin
// presentation/home/HomeViewModel.kt (commonMain)
class HomeViewModel(
    private val getNearbySpots: GetNearbySpotsUseCase,
    private val observeNearbySpots: ObserveNearbySpotsUseCase,
    private val reportSpotReleased: ReportSpotReleasedUseCase
) : BaseViewModel<HomeState, HomeIntent, HomeEffect>() {

    override fun initState() = HomeState()

    init {
        observeSpots()
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            is HomeIntent.LoadNearbySpots  -> loadSpots()
            is HomeIntent.RefreshSpots     -> loadSpots()
            is HomeIntent.SpotSelected     -> sendEffect(HomeEffect.NavigateToMap(intent.spotId))
            is HomeIntent.OpenMap          -> sendEffect(HomeEffect.NavigateToMap())
            is HomeIntent.StartDetection   -> checkAndStartDetection()
            is HomeIntent.StopDetection    -> updateState { copy(isDetectionActive = false) }
            is HomeIntent.DismissError     -> updateState { copy(error = null) }
            is HomeIntent.PermissionResult -> handlePermissions(intent)
        }
    }

    private fun observeSpots() {
        viewModelScope.launch {
            observeNearbySpots()
                .catch { e ->
                    updateState { copy(error = (e as? PaparcarException)?.error
                        ?: PaparcarError.Network.Unknown(e.message.orEmpty())) }
                }
                .collect { spots ->
                    updateState { copy(nearbySpots = spots, isLoading = false) }
                }
        }
    }

    private fun loadSpots() {
        viewModelScope.launch {
            updateState { copy(isLoading = true, error = null) }
            getNearbySpots()
                .onSuccess { spots ->
                    updateState { copy(isLoading = false, nearbySpots = spots) }
                }
                .onFailure { e ->
                    val error = (e as? PaparcarException)?.error
                        ?: PaparcarError.Network.Unknown(e.message.orEmpty())
                    updateState { copy(isLoading = false, error = error) }
                    sendEffect(HomeEffect.ShowSnackbar(error.toUserMessage()))
                }
        }
    }

    private fun checkAndStartDetection() {
        val state = state.value
        when {
            !state.hasLocationPermission -> sendEffect(HomeEffect.RequestLocationPermission)
            !state.hasActivityPermission -> sendEffect(HomeEffect.RequestActivityPermission)
            else                         -> updateState { copy(isDetectionActive = true) }
        }
    }

    private fun handlePermissions(intent: HomeIntent.PermissionResult) {
        updateState {
            copy(
                hasLocationPermission = intent.locationGranted,
                hasActivityPermission = intent.activityGranted
            )
        }
        if (intent.locationGranted && intent.activityGranted) {
            updateState { copy(isDetectionActive = true) }
        }
    }
}
```

#### HomeScreen.kt

```kotlin
// presentation/home/HomeScreen.kt (commonMain)
@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Efecto lateral: permisos, navegación, snackbars
    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is HomeEffect.NavigateToMap           -> { /* navController.navigate(...) */ }
                is HomeEffect.RequestLocationPermission -> { /* lanzar launcher de permiso */ }
                is HomeEffect.RequestActivityPermission -> { /* lanzar launcher de permiso */ }
                is HomeEffect.ShowSnackbar            -> snackbarHostState.showSnackbar(effect.message)
                is HomeEffect.ScrollToUserLocation    -> { /* scroll en mapa */ }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.handleIntent(HomeIntent.LoadNearbySpots)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            HomeDetectionFab(
                isActive = state.isDetectionActive,
                onClick  = {
                    viewModel.handleIntent(
                        if (state.isDetectionActive) HomeIntent.StopDetection
                        else HomeIntent.StartDetection
                    )
                }
            )
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when {
                state.isLoading         -> HomeLoadingContent()
                state.error != null     -> HomeErrorContent(
                    error   = state.error!!,
                    onRetry = { viewModel.handleIntent(HomeIntent.RefreshSpots) },
                    onDismiss = { viewModel.handleIntent(HomeIntent.DismissError) }
                )
                else -> HomeContent(
                    spots         = state.nearbySpots,
                    isDetectionActive = state.isDetectionActive,
                    onSpotClick   = { viewModel.handleIntent(HomeIntent.SpotSelected(it.id)) },
                    onMapClick    = { viewModel.handleIntent(HomeIntent.OpenMap) }
                )
            }
        }
    }
}

@Composable
private fun HomeDetectionFab(isActive: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = {
            Icon(
                imageVector = if (isActive) Icons.Default.Stop else Icons.Default.DirectionsCar,
                contentDescription = null
            )
        },
        text = {
            Text(if (isActive) "Detener detección" else "Activar detección")
        },
        containerColor = if (isActive) MaterialTheme.colorScheme.error
                         else MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun HomeContent(
    spots: List<Spot>,
    isDetectionActive: Boolean,
    onSpotClick: (Spot) -> Unit,
    onMapClick: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        // Banner de estado de detección
        if (isDetectionActive) {
            DetectionStatusBanner()
        }
        // Contador de spots cercanos
        SpotCountHeader(count = spots.size, onMapClick = onMapClick)
        // Lista de spots
        if (spots.isEmpty()) {
            EmptySpotsContent()
        } else {
            LazyColumn {
                items(spots, key = { it.id }) { spot ->
                    SpotCard(spot = spot, onClick = { onSpotClick(spot) })
                }
            }
        }
    }
}

@Composable private fun HomeLoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable private fun HomeErrorContent(
    error: PaparcarError,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(error.toUserMessage(), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) { Text("Reintentar") }
    }
}
```

---

## 7. Domain — UseCases

### 7.1 `ObserveLocationUpdatesUseCase` ← NUEVO

```kotlin
// domain/usecase/location/ObserveLocationUpdatesUseCase.kt (commonMain)
class ObserveLocationUpdatesUseCase(
    private val locationRepository: LocationRepository
) {
    operator fun invoke(): Flow<SpotLocation> = locationRepository.locationFlow()
}
```

> **Por qué existe este UseCase:** `SpotDetectionForegroundService` no debe conocer a `LocationRepository` directamente. El UseCase es el punto de entrada único al dominio. Si en el futuro hay lógica (filtrar por accuracy, limitar la frecuencia), se añade aquí sin tocar el Service.

### 7.2 Casos de uso de notificación

```kotlin
// domain/usecase/notification/ShowSpotDetectionNotificationUseCase.kt (commonMain)
class ShowSpotDetectionNotificationUseCase(
    private val notificationManager: AppNotificationManager
) {
    operator fun invoke() = notificationManager.showDetectionNotification()
}

// domain/usecase/notification/ShowSpotUploadNotificationUseCase.kt (commonMain)
class ShowSpotUploadNotificationUseCase(
    private val notificationManager: AppNotificationManager
) {
    operator fun invoke() = notificationManager.showUploadNotification()
}

// domain/usecase/notification/DismissNotificationUseCase.kt (commonMain)
class DismissNotificationUseCase(
    private val notificationManager: AppNotificationManager
) {
    operator fun invoke(notificationId: Int) = notificationManager.dismiss(notificationId)
}
```

---

## 8. Data — DataSources

### 8.1 `PlatformLocationDataSource` (antes `LocationDataSource`)

Interfaz KMP en `commonMain`. Provee el stream de ubicaciones desde el hardware de la plataforma (GPS, FusedLocation, CoreLocation). Se llama "Platform" para diferenciarse de `LocalLocationDataSource` (Room).

```kotlin
// data/datasource/platform/PlatformLocationDataSource.kt (commonMain)
interface PlatformLocationDataSource {
    fun locationFlow(): Flow<SpotLocation>
}
```

### 8.2 `AndroidLocationDataSource` — Interfaz (androidMain)

Interfaz específica de Android que extiende `PlatformLocationDataSource`. Permite mockear en tests Android sin tocar commonMain.

```kotlin
// location/AndroidLocationDataSource.kt (androidMain)
interface AndroidLocationDataSource : PlatformLocationDataSource {
    fun locationFlow(): Flow<SpotLocation>
    fun isAvailable(): Boolean
}
```

### 8.3 `AndroidLocationDataSourceImpl` — Implementación

```kotlin
@file:OptIn(ExperimentalTime::class)

// location/AndroidLocationDataSourceImpl.kt (androidMain)
class AndroidLocationDataSourceImpl(
    private val context: Context
) : AndroidLocationDataSource {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY, 3_000L
    ).setMinUpdateIntervalMillis(1_500L)
     .setMaxUpdateDelayMillis(5_000L)
     .build()

    override fun isAvailable(): Boolean =
        LocationManagerCompat.isLocationEnabled(
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        )

    @SuppressLint("MissingPermission")
    override fun locationFlow(): Flow<SpotLocation> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    trySend(
                        SpotLocation(
                            latitude  = loc.latitude,
                            longitude = loc.longitude,
                            accuracy  = loc.accuracy,
                            timestamp = Clock.System.now().toEpochMilliseconds()
                        )
                    )
                }
            }
        }
        fusedClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }
}
```

### 8.4 `AppNotificationManager` — Interfaz (commonMain)

```kotlin
// data/notification/AppNotificationManager.kt (commonMain)
interface AppNotificationManager {
    fun showDetectionNotification()
    fun showUploadNotification()
    fun showDebugNotification(message: String)
    fun dismiss(notificationId: Int)

    companion object {
        const val DETECTION_NOTIFICATION_ID = 1001
        const val UPLOAD_NOTIFICATION_ID    = 1002
        const val DEBUG_NOTIFICATION_ID     = 2001
    }
}
```

### 8.5 `AppNotificationManagerImpl` — Implementación Android

```kotlin
// notification/AppNotificationManagerImpl.kt (androidMain)
class AppNotificationManagerImpl(
    private val context: Context
) : AppNotificationManager {

    private val nm = context.getSystemService(NotificationManager::class.java)

    private val channels = mapOf(
        DETECTION_CHANNEL_ID to ("Detección de Spot" to NotificationManager.IMPORTANCE_LOW),
        UPLOAD_CHANNEL_ID    to ("Subida de Spot"    to NotificationManager.IMPORTANCE_LOW),
        DEBUG_CHANNEL_ID     to ("Activity Debug"    to NotificationManager.IMPORTANCE_DEFAULT)
    )

    init {
        channels.forEach { (id, pair) ->
            val (name, importance) = pair
            if (nm.getNotificationChannel(id) == null) {
                nm.createNotificationChannel(NotificationChannel(id, name, importance))
            }
        }
    }

    override fun showDetectionNotification() {
        nm.notify(
            AppNotificationManager.DETECTION_NOTIFICATION_ID,
            buildNotification(
                channelId = DETECTION_CHANNEL_ID,
                title     = "Paparcar",
                text      = "Detectando movimiento del vehículo…",
                ongoing   = true
            )
        )
    }

    override fun showUploadNotification() {
        nm.notify(
            AppNotificationManager.UPLOAD_NOTIFICATION_ID,
            buildNotification(
                channelId = UPLOAD_CHANNEL_ID,
                title     = "Paparcar",
                text      = "Publicando plaza libre…",
                ongoing   = true
            )
        )
    }

    override fun showDebugNotification(message: String) {
        nm.notify(
            AppNotificationManager.DEBUG_NOTIFICATION_ID,
            buildNotification(
                channelId  = DEBUG_CHANNEL_ID,
                title      = "Activity Transition Debug",
                text       = message,
                ongoing    = false,
                autoCancel = true
            )
        )
    }

    override fun dismiss(notificationId: Int) = nm.cancel(notificationId)

    private fun buildNotification(
        channelId:  String,
        title:      String,
        text:       String,
        ongoing:    Boolean = false,
        autoCancel: Boolean = false
    ) = NotificationCompat.Builder(context, channelId)
        .setContentTitle(title)
        .setContentText(text)
        .setSmallIcon(R.drawable.ic_car)
        .setOngoing(ongoing)
        .setAutoCancel(autoCancel)
        .build()

    companion object {
        private const val DETECTION_CHANNEL_ID = "spot_detection_channel"
        private const val UPLOAD_CHANNEL_ID    = "spot_upload_channel"
        private const val DEBUG_CHANNEL_ID     = "activity_transition_debug_channel"
    }
}
```

---

## 9. Data — Room con `expect/actual`

### 9.1 Regla de Room en KMP

- `@Database`, `@Entity`, `@Dao`, `TypeConverter`, `Mapper` → **commonMain** (sin cambios)
- Solo el **builder** del Room database necesita `expect/actual` (usa APIs de plataforma)
- En iOS, Room KMP genera código nativo vía KSP — el builder apunta a `NSDocumentDirectory`

### 9.2 `AppDatabase.kt` (commonMain)

```kotlin
// data/datasource/local/room/AppDatabase.kt (commonMain)
@Database(entities = [LocationEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
}
```

### 9.3 `AppDatabaseBuilder.kt` — expect (commonMain)

```kotlin
// data/datasource/local/room/AppDatabaseBuilder.kt (commonMain)
expect fun createAppDatabase(): AppDatabase
```

### 9.4 `AppDatabaseBuilder.kt` — actual Android

```kotlin
// db/AppDatabaseBuilder.kt (androidMain)
actual fun createAppDatabase(): AppDatabase {
    val context = KoinAndroidContext.get()   // o pasar via parámetro en Koin
    return Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "paparcar.db"
    )
    .fallbackToDestructiveMigration()
    .build()
}
```

> **Alternativa recomendada con Koin** — En lugar de `expect fun`, inyectar el `Context` vía Koin en `DataModule`:

```kotlin
// di/DataModule.kt (commonMain)
val dataModule = module {
    single<AppDatabase> { createAppDatabase() }   // llama al actual de la plataforma
    single { get<AppDatabase>().locationDao() }
    single { LocalLocationDataSource(get()) }
    single<SpotRepository>     { SpotRepositoryImpl(get(), get()) }
    single<LocationRepository> { LocationRepositoryImpl(get(), get(), get()) }
    single { FirebaseDataSource() }
}

// di/AndroidDetectionModule.kt (androidMain) — provee el Context al expect
actual val platformDatabaseModule = module {
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "paparcar.db"
        ).fallbackToDestructiveMigration().build()
    }
}
```

```kotlin
// di/IosDatabaseModule.kt (iosMain)
actual val platformDatabaseModule = module {
    single<AppDatabase> {
        val dbPath = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain  = NSUserDomainMask,
            appropriateForURL = null,
            create    = true,
            error     = null
        )!!.URLByAppendingPathComponent("paparcar.db")!!.path!!

        Room.databaseBuilder<AppDatabase>(
            name    = dbPath,
            factory = { AppDatabase::class.instantiateImpl() }
        ).build()
    }
}
```

### 9.5 `LocationEntity.kt` (commonMain)

```kotlin
// data/datasource/local/room/LocationEntity.kt (commonMain)
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude:  Double,
    val longitude: Double,
    val accuracy:  Float,
    val timestamp: Long,
    val uploaded:  Boolean = false
)
```

### 9.6 `LocationDao.kt` (commonMain)

```kotlin
// data/datasource/local/room/LocationDao.kt (commonMain)
@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LocationEntity)

    @Query("SELECT * FROM locations WHERE uploaded = 0 ORDER BY timestamp ASC")
    suspend fun getPending(): List<LocationEntity>

    @Query("DELETE FROM locations")
    suspend fun deleteAll()

    @Query("UPDATE locations SET uploaded = 1 WHERE id = :id")
    suspend fun markAsUploaded(id: Long)
}
```

---

## 10. Flujo de Detección — Android

### 10.1 Diagrama

```
REPOSO — AccelerometerManager (SENSOR_DELAY_NORMAL, siempre activo)
    │ vibración ≥ 4s
    ▼
SpotDetectionForegroundService arranca
    · ObserveLocationUpdatesUseCase().collect { spotLocation }
    · SaveLocationToLocalUseCase(spotLocation) → Room
    │
    │ (hilo paralelo, siempre activo)
    ▼
ActivityTransitionReceiver recibe IN_VEHICLE_ENTER
    · AppNotificationManager.showDebugNotification()
    · startForegroundService(SpotUploadForegroundService)
    │
    ▼
SpotUploadForegroundService
    · GetStoredLocationsUseCase() → List<SpotLocation> de Room
    · Construye Spot con locations.first() (= punto de salida)
    · ReportSpotReleasedUseCase(spot) → Firebase
    · Para SpotDetectionForegroundService
    · stopSelf()
```

### 10.2 `SpotDetectionForegroundService` actualizado

```kotlin
@file:OptIn(ExperimentalTime::class)

// detection/SpotDetectionForegroundService.kt (androidMain)
class SpotDetectionForegroundService : Service(), KoinComponent {

    private val observeLocationUpdates: ObserveLocationUpdatesUseCase by inject()
    private val saveLocationToLocal: SaveLocationToLocalUseCase by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private var startedAt = 0L
        fun getActiveTimeMs() =
            if (startedAt > 0L) Clock.System.now().toEpochMilliseconds() - startedAt else 0L
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startedAt = Clock.System.now().toEpochMilliseconds()
        startForeground(
            AppNotificationManager.DETECTION_NOTIFICATION_ID,
            // La notificación la construimos directamente aquí porque startForeground
            // necesita un Notification object, no solo el ID
            buildDetectionNotification()
        )

        serviceScope.launch {
            observeLocationUpdates()           // ← UseCase, no repositorio
                .catch { /* log error */ }
                .collect { spotLocation ->
                    saveLocationToLocal(spotLocation)
                }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        startedAt = 0L
        super.onDestroy()
    }

    private fun buildDetectionNotification(): Notification {
        val channelId = "spot_detection_channel"
        getSystemService(NotificationManager::class.java).let { nm ->
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Detección de Spot", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Paparcar")
            .setContentText("Detectando movimiento del vehículo…")
            .setSmallIcon(R.drawable.ic_car)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?) = null
}
```

---

## 11. Detection — Android

### 11.1 `ActivityRecognitionManager` — Interfaz (androidMain)

```kotlin
// detection/ActivityRecognitionManager.kt (androidMain)
interface ActivityRecognitionManager {
    fun registerTransitions()
    fun unregisterTransitions()
}
```

### 11.2 `ActivityRecognitionManagerImpl` — Implementación

```kotlin
// detection/ActivityRecognitionManagerImpl.kt (androidMain)
class ActivityRecognitionManagerImpl(
    private val context: Context
) : ActivityRecognitionManager {

    private val client = ActivityRecognition.getClient(context)

    // Lazy: el PendingIntent se crea una vez y se reutiliza
    // FLAG_IMMUTABLE obligatorio desde Android 12 (API 31)
    private val pendingIntent: PendingIntent by lazy {
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, ActivityTransitionReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private val transitionRequest: ActivityTransitionRequest by lazy {
        ActivityTransitionRequest(
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.IN_VEHICLE)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                    .build(),
                ActivityTransition.Builder()
                    .setActivityType(DetectedActivity.STILL)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
        )
    }

    override fun registerTransitions() {
        client.requestActivityTransitionUpdates(transitionRequest, pendingIntent)
            .addOnSuccessListener { /* registrado correctamente */ }
            .addOnFailureListener { e -> /* log: e.message */ }
    }

    override fun unregisterTransitions() {
        client.removeActivityTransitionUpdates(pendingIntent)
            .addOnFailureListener { e -> /* log: e.message */ }
    }

    companion object {
        private const val REQUEST_CODE = 1000
    }
}
```

---

## 12. DI — Koin

### 12.1 `DataModule.kt` (commonMain)

```kotlin
// di/DataModule.kt (commonMain)
val dataModule = module {
    // Room — provisto por platformDatabaseModule de cada plataforma
    single { get<AppDatabase>().locationDao() }
    single { LocalLocationDataSource(get()) }
    single { FirebaseDataSource() }
    single<AppNotificationManager> { /* provisto por plataforma */ }

    // Repositorios
    single<SpotRepository>     { SpotRepositoryImpl(get(), get()) }
    single<LocationRepository> { LocationRepositoryImpl(get(), get(), get()) }
}
```

### 12.2 `DomainModule.kt` (commonMain)

```kotlin
// di/DomainModule.kt (commonMain)
val domainModule = module {
    // Location
    factory { ObserveLocationUpdatesUseCase(get()) }
    factory { SaveLocationToLocalUseCase(get()) }
    factory { GetStoredLocationsUseCase(get()) }

    // Spot
    factory { GetNearbySpotsUseCase(get()) }
    factory { ObserveNearbySpotsUseCase(get()) }
    factory { ReportSpotReleasedUseCase(get()) }

    // Notification
    factory { ShowSpotDetectionNotificationUseCase(get()) }
    factory { ShowSpotUploadNotificationUseCase(get()) }
    factory { DismissNotificationUseCase(get()) }
}
```

### 12.3 `PresentationModule.kt` (commonMain)

```kotlin
// di/PresentationModule.kt (commonMain)
val presentationModule = module {
    viewModel { HomeViewModel(get(), get(), get()) }
    viewModel { MapViewModel(get()) }
    viewModel { HistoryViewModel(get()) }
}
```

### 12.4 `AndroidDetectionModule.kt` (androidMain)

```kotlin
// di/AndroidDetectionModule.kt (androidMain)
val androidDetectionModule = module {
    // Room
    single<AppDatabase> {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "paparcar.db"
        ).fallbackToDestructiveMigration().build()
    }

    // Location — registrado como interfaz de plataforma (D de SOLID)
    single<AndroidLocationDataSource> { AndroidLocationDataSourceImpl(androidContext()) }
    single<PlatformLocationDataSource> { get<AndroidLocationDataSource>() }

    // Activity Recognition
    single<ActivityRecognitionManager> { ActivityRecognitionManagerImpl(androidContext()) }

    // Notificaciones
    single<AppNotificationManager> { AppNotificationManagerImpl(androidContext()) }

    // Detection
    single { AccelerometerManager(androidContext()) }
}
```

---

## 13. `kotlin.time.Clock` — Regla global

```kotlin
// ❌ Prohibido
System.currentTimeMillis()

// ✅ Siempre — anotar el fichero entero
@file:OptIn(ExperimentalTime::class)
val now = Clock.System.now().toEpochMilliseconds()
```

---

## 14. Firebase

### 14.1 ¿Por qué Firebase?

- Escrituras puntuales (solo al liberar una plaza)
- Listeners en tiempo real → otros usuarios ven el spot al instante
- GitLive SDK es KMP nativo → `FirebaseDataSource` en `commonMain` sin `expect/actual`
- Setup mínimo para MVP

### 14.2 Funcionalidades recomendadas

| Funcionalidad | Tecnología | Beneficio |
|---|---|---|
| Mapa en tiempo real | Firestore SnapshotListener → Flow | Spots aparecen/desaparecen al instante |
| Validación en servidor | Firebase Functions | Sin exponer lógica en cliente |
| Expiración automática | Firestore TTL / Functions scheduled | Spots se eliminan solos (15 min) |
| Analítica de detección | Firebase Analytics | Medir precisión real |
| Monitorización | Firebase Crashlytics | Fallos en Services |
| Onboarding progresivo | Firebase Auth anónimo → cuenta | Sin registro obligatorio al inicio |
| Búsqueda cercana | GeoFirestore / Geohash | Queries por proximidad |

### 14.3 Estructura Firestore

```
spots/{spotId}/
  latitude, longitude, accuracy: Double/Float
  reportedAt, expiresAt: Timestamp
  reportedBy: String (Firebase Auth UID)
  isActive: Boolean

users/{userId}/
  spotsReported: Int
  lastActivity: Timestamp
```

---

## 15. Permisos Android

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="com.google.android.gms.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<application ...>
    <service android:name=".detection.SpotDetectionForegroundService"
             android:foregroundServiceType="location"
             android:exported="false" />
    <service android:name=".detection.SpotUploadForegroundService"
             android:foregroundServiceType="dataSync"
             android:exported="false" />
    <receiver android:name=".detection.ActivityTransitionReceiver"
              android:exported="false" />
</application>
```

---

## 16. Fases de Desarrollo

### Fase 1 — Fundamentos ✅
- [x] Setup KMP + Compose Multiplatform
- [x] Estructura de módulos y packages
- [x] Gradle con version catalog
- [x] `BaseViewModel` en `commonMain/presentation/base/`
- [ ] Koin DI completo (incluyendo módulos de plataforma)
- [ ] Room con `expect/actual` — builder por plataforma

### Fase 2 — Errores + Dominio
- [ ] `PaparcarError` sealed class
- [ ] Mappers de errores en repositorios
- [ ] `ErrorMapper.kt` en presentation

### Fase 3 — Detección (Android)
- [ ] `AccelerometerManager`
- [ ] `ActivityRecognitionManager` interfaz + `ActivityRecognitionManagerImpl`
- [ ] `PlatformLocationDataSource` interfaz en commonMain
- [ ] `AndroidLocationDataSource` interfaz + `AndroidLocationDataSourceImpl`
- [ ] `ObserveLocationUpdatesUseCase`
- [ ] `SpotDetectionForegroundService` — UseCase, no repositorio
- [ ] `ActivityTransitionReceiver` — solo enruta
- [ ] `SpotUploadForegroundService`
- [ ] `AppNotificationManager` interfaz + `AppNotificationManagerImpl`

### Fase 4 — Firebase
- [ ] `FirebaseDataSource` en commonMain
- [ ] `ReportSpotReleasedUseCase` → Firestore
- [ ] Expiración de spots

### Fase 5 — UI y Mapa
- [ ] `HomeScreen` conectada con ViewModel y datos reales
- [ ] `MapScreen` con Flow en tiempo real
- [ ] `HistoryScreen`

### Fase 6 — Pulido
- [ ] Firebase Analytics + Crashlytics
- [ ] Timeout de `SpotDetectionForegroundService` (5 min sin confirmar)
- [ ] Tests unitarios y de integración

---

## 17. Reglas y Decisiones de Diseño

1. **`Screen` no `View`** — Sufijo obligatorio para pantallas: `HomeScreen`, `MapScreen`, `HistoryScreen`.

2. **`BaseViewModel<S,I,E>`** — Todos los ViewModels heredan de él. Se cancela con `onClear()`.

3. **`Clock.System.now().toEpochMilliseconds()`** — Única fuente de tiempo. `@file:OptIn(ExperimentalTime::class)`. Prohibido `System.currentTimeMillis()`.

4. **`PlatformLocationDataSource` es una interfaz KMP** — Vive en `commonMain/data/datasource/platform/`. `LocationRepositoryImpl` depende de ella. `AndroidLocationDataSourceImpl` la implementa vía `AndroidLocationDataSource`. Koin: `single<PlatformLocationDataSource> { get<AndroidLocationDataSource>() }`.

5. **Doble interfaz para LocationDataSource en Android** — `PlatformLocationDataSource` (commonMain, KMP) ← `AndroidLocationDataSource` (androidMain, permite testear en Android sin commonMain) ← `AndroidLocationDataSourceImpl` (implementación real).

6. **Services solo llaman a UseCases** — `SpotDetectionForegroundService` → `ObserveLocationUpdatesUseCase` + `SaveLocationToLocalUseCase`. `SpotUploadForegroundService` → `GetStoredLocationsUseCase` + `ReportSpotReleasedUseCase`. Nunca tocan repositorios ni datasources.

7. **`ActivityRecognitionManager` es una interfaz** — `ActivityRecognitionManagerImpl` encapsula el PendingIntent con `lazy`. El Receiver no sabe nada del registro. Koin registra la impl como interfaz.

8. **`AppNotificationManager` es una interfaz KMP** — La implementación es por plataforma. Los UseCases de notificación son los únicos que la llaman. Los Services construyen la `Notification` directamente solo para `startForeground()`.

9. **`PaparcarError` sealed class en domain** — Toda la cadena (datasource→repo→usecase→viewmodel) propaga `Result<T>`. Los errores técnicos (Exception) se mappean a `PaparcarError` en la capa Data. La UI usa `toUserMessage()`.

10. **Room: `expect/actual` solo para el builder** — `@Database`, `@Entity`, `@Dao` en `commonMain`. El builder usa `actual fun` (o módulo Koin por plataforma) para acceder a `Context` (Android) o `NSDocumentDirectory` (iOS).

11. **`AndroidLocationDataSource` tiene interfaz propia en androidMain** — Para testabilidad Android-específica y cumplir ISP (Interface Segregation Principle).

12. **`ActivityTransitionReceiver` solo enruta** — Sin coroutines, sin lógica de negocio. Recibe el evento, lanza `SpotUploadForegroundService`, termina.
