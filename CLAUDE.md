# Paparcar — CLAUDE.md

## Proyecto
Paparcar es una app KMP (Kotlin Multiplatform) de compartición de plazas de aparcamiento en tiempo real basada en comunidad. Android es la plataforma principal; iOS es target futuro. Cuando un usuario sale con el coche, la app detecta automáticamente el evento y publica la plaza recién liberada para que otros usuarios cercanos puedan encontrarla.

## Stack
- UI: Compose Multiplatform 1.8.0
- Arquitectura: Clean Architecture + MVI (State + Intent + Effect)
- DI: Koin 4.1.1
- DB local: Room KMP 2.8.4
- Backend: Firebase (GitLive KMP SDK 2.4.0)
- Auth: BaseLogin (librería propia, JitPack)
- Async: Coroutines + Flow
- Monitoring: Firebase Crashlytics

## Estructura
```
commonMain/  → domain/, data/, presentation/, di/, core/
androidMain/ → detection/, location/, bluetooth/, notification/, worker/, geofence/
iosMain/     → (futuro) CLLocation, CMMotion, CoreBluetooth, BGTask wrappers
```

## Arquitectura
- Domain layer es Kotlin puro — sin imports de Android/iOS
- Todo UseCase retorna `Flow<T>` o `AppResult<T>`
- ViewModels usan MVI: sealed class State, Intent, Effect
- Repositorios exponen interfaces en domain/, implementación en data/
- Persistencia dual: Room (offline-first local) + Firestore (sync real-time)

## Detección de aparcamiento — Dual Strategy
Dos estrategias independientes, NUNCA se mezclan:

### BluetoothDetectionStrategy (determinista)
- Escucha BT disconnect → GPS fix → distance check > 30m → auto-confirm
- Escucha BT connect → DetectDepartureUseCase
- Sin scoring, sin Activity Recognition — es directo
- Para usuarios con BT emparejado con su coche

### CoordinatorDetectionStrategy (probabilístico)
- Activity Recognition + GPS stream → confidence scoring
- HIGH (≥0.75) → auto-confirm | MEDIUM (≥0.55) → preguntar usuario | LOW → reset
- Fallback para usuarios sin BT o con BT del móvil apagado

### Resolución
```kotlin
fun resolveStrategy(vehicle: Vehicle, isBluetoothEnabled: Boolean): ParkingDetectionStrategy {
    return if (vehicle.bluetoothDeviceId != null && isBluetoothEnabled) {
        BluetoothDetectionStrategy(vehicle.bluetoothDeviceId)
    } else {
        CoordinatorDetectionStrategy()
    }
}
```

Ambas estrategias convergen en: ConfirmParkingUseCase → Room + Firestore + Geofence + Notification + WorkManager geocoding

---

## REGLAS DE CÓDIGO OBLIGATORIAS

### ⛔ Strings — NUNCA hardcoded
- Todo texto visible al usuario va en `composeResources/values/strings.xml`
- Usar `stringResource(Res.string.key)` en Compose
- Convención de key: `feature_component_description`
  - Ejemplo: `home_fab_report_spot`, `detection_parking_confirmation`
- Idiomas soportados: EN (base), ES, IT, PT, FR + futuros P2
- Cuando añadas un string, SIEMPRE añadirlo mínimo en EN y ES
- Keys siempre en inglés: `spot_available` no `plaza_disponible`

### ⛔ Magic numbers — NUNCA inline
- Las constantes van en `companion object` privado de la clase que las usa
- Si se comparte entre 2+ clases → extraer a archivo config del módulo
- Nombre descriptivo en UPPER_SNAKE_CASE
- Ejemplo:
```kotlin
// ✅ En GeofenceManager.kt
private companion object {
    const val GEOFENCE_RADIUS_METERS = 80f
}

// ✅ En CalculateParkingConfidenceUseCase.kt
private companion object {
    const val HIGH_CONFIDENCE_THRESHOLD = 0.75
    const val MEDIUM_CONFIDENCE_THRESHOLD = 0.55
}

// ❌ NUNCA
if (distance > 80f) { ... }
if (score >= 0.75) { ... }
```

### Error handling
```kotlin
sealed class AppResult<out T> {
    data class Success<T>(val data: T) : AppResult<T>()
    data class Error(val exception: Throwable) : AppResult<Nothing>()
}
```

### Testing
- Toda UseCase nueva debe tener test unitario
- Usar fakes sobre mocks: FakeAuthRepository, FakePermissionManager, FakeUserParkingRepository...
- Naming: `should_expectedBehavior_when_condition`

### Commits — Conventional Commits
```
feat(home): implement bottom sheet with nearby spots [HOME-002]
fix(detection): geofence departure not triggering spot publish [FND-004]
refactor(core): extract magic numbers to companion objects [FND-002]
test(domain): add tests for ConfirmParkingUseCase [FND-007]
chore(repo): remove build log files [FND-008]
feat(i18n): add Italian translations [FND-001]
```

### Ramas
```
feature/HOME-001-bottom-sheet
bugfix/FND-004-geofence-departure
refactor/FND-001-extract-strings
experiment/UI-003-glass-ui
chore/FND-008-repo-cleanup
```

### Cosas que NO hacer
- No usar `println` para logs → usar Logger con tag
- No usar wildcard imports (`import com.paparcar.*`)
- No commitear archivos de build: logs, .kotlin/metadata, build/
- No poner constantes en God Objects compartidos
- No escribir strings en español en el código — EN es siempre la base
- No mezclar señales Bluetooth dentro del Coordinator scoring
- No crear pantallas sin sus correspondientes State/Intent/Effect sealed classes

## Modelos de datos clave
- `Spot` — plaza comunitaria: location, type (AUTO_DETECTED/MANUAL_REPORT), status, confidence, enRouteCount, TTL
- `UserParking` — sesión propia: vehicleId, location, geofenceId, isActive, detectionMethod
- `Vehicle` — vehículo: brand, model, licensePlate?, bluetoothDeviceId?, isDefault
- `UserProfile` — perfil Firebase: userId, email, displayName, photoUrl

## Navegación
BottomNav con 4 destinos: Mapa | Historial | Mi Coche | Ajustes
Splash → Auth → VehicleRegistration → Onboarding → Permissions → Home

## i18n
- Base: EN (siempre completo)
- P0: ES
- P1: IT, PT, FR
- P2: DE, NL, PL, RO
- Excluidos por complejidad UI: idiomas RTL (AR, HE) y glifos complejos (ZH, JA, KO, TH, HI)
