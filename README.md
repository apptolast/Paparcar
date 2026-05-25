# Paparcar 🚗🅿️

App KMP (Kotlin Multiplatform) de **compartición de plazas de aparcamiento en tiempo real** basada en comunidad. Cuando un usuario sale con el coche, la app detecta automáticamente el evento y publica la plaza recién liberada para que otros usuarios cercanos la encuentren.

Android es la plataforma principal. iOS está al ~70% (implementaciones nativas reales, pendiente wiring final de Activity Recognition y BGTaskScheduler).

---

## Estado del proyecto · 2026-05-24

**Pre-beta.** Funcionalidad core completa en Android, en proceso de hardening antes del primer release a testers vía Firebase App Distribution.

| Área | Estado |
|------|--------|
| Detección dual (BT + Coordinator) Android | ✅ Funcional |
| Sync offline-first (Room ⇄ Firestore) | ✅ Funcional |
| UI/UX (Home, Map, Vehicles, History, Settings) | ✅ Implementado, en pulido |
| Detección iOS | ⚠️ Implementaciones nativas listas, wiring AR → coordinator pendiente |
| iOS sync persistente (BGTaskScheduler) | ⚠️ Stub |
| Firebase App Distribution Android | 🚧 Scaffold listo, P0 pendiente (Maps key hardening + Firestore rules + keystore) |
| Firebase App Distribution iOS | ❌ Sin configurar (~6–9 h pendientes) |
| Doze Mode / MIUI hardening | ❌ Pendiente |

Roadmap completo en [`docs/ROADMAP.md`](./docs/ROADMAP.md).

---

## Stack

- **UI:** Compose Multiplatform 1.10.2 (Material3)
- **Arquitectura:** Clean Architecture + MVI
- **DI:** Koin 4.1.1
- **DB local:** Room KMP 2.8.4
- **Backend:** Firebase via GitLive 2.4.0 (Auth + Firestore + Crashlytics)
- **Maps:** KMP Maps (Software Mansion) — Google Maps (Android) + Apple Maps (iOS)
- **Auth:** BaseLogin 1.0.16 (librería propia, JitPack)
- **Logging:** Napier 2.7.1
- **Background:** WorkManager 2.11.1 (Android) — BGTaskScheduler pendiente en iOS

**Targets:** Android `minSdk 26 / target 36 / compile 37` · iOS `arm64 + simulatorArm64` · Kotlin 2.3.10 · JVM 17

---

## Cómo funciona la detección

Dos estrategias **independientes**, nunca se mezclan:

- **`BluetoothParkingDetector`** (determinista) — BT disconnect → GPS fix con accuracy ≤ 50m → distancia > 30m → confirma con `reliability=0.95`. Anti-bounce 30s contra paradas de tráfico.
- **`ParkingDetectionCoordinator`** (probabilístico) — Activity Recognition + GPS speed + still → `CalculateParkingConfidenceUseCase` → HIGH ≥ 0.75 auto-confirma · MEDIUM ≥ 0.55 pregunta al usuario · LOW reset.

Ambas convergen en `ConfirmParkingUseCase` → Room (sync) → Geofence + Notification + Firestore sync via WorkManager.

Detalle algorítmico y plan de mejora en [`docs/PARKING_DETECTION.md`](./docs/PARKING_DETECTION.md) y la spec canónica en [`docs/detection/PARKING-DETECTION.md`](./docs/detection/PARKING-DETECTION.md).

---

## Estructura del proyecto

```
composeApp/
├── src/commonMain/kotlin/io/apptolast/paparcar/
│   ├── domain/         Kotlin puro — entidades, UseCases, interfaces, coordinator
│   ├── data/           Repos + Room + Firestore + mappers
│   ├── presentation/   ViewModels MVI + screens Compose
│   ├── ui/             Design system (theme, componentes Pap*, mapa)
│   └── di/             Módulos Koin
├── src/androidMain/    WorkManager, FusedLocation, AR, Foreground Service, BT, Geofencing
└── src/iosMain/        CLLocation, CMMotion, CBCentralManager, UNUserNotification, NWPathMonitor

iosApp/                 SwiftUI shell (delegado a Compose vía MainViewController)
```

Visión arquitectónica completa en [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md).

---

## Documentación

| Documento | Cuándo leerlo |
|-----------|----------------|
| [`CLAUDE.md`](./CLAUDE.md) | **Reglas obligatorias del proyecto** — strings, magic numbers, commits, branches, modelos clave |
| [`docs/ROADMAP.md`](./docs/ROADMAP.md) | Estado real de features ✅🚧📋🔮 |
| [`docs/ARCHITECTURE.md`](./docs/ARCHITECTURE.md) | Capas, flujos de datos, paquetes, decisiones técnicas |
| [`docs/PARKING_DETECTION.md`](./docs/PARKING_DETECTION.md) | Estrategias dual, estados, problemas, plan de mejora |
| [`docs/BUGS_AND_DEBT.md`](./docs/BUGS_AND_DEBT.md) | Inventario de bugs (Critical → Low) y deuda técnica |
| [`docs/IOS_PLAN.md`](./docs/IOS_PLAN.md) | Estado iOS, stubs pendientes, plan Firebase App Distribution iOS |
| [`docs/HYPOTHESIS.md`](./docs/HYPOTHESIS.md) | Reflexión "¿cómo lo haría desde cero?" + 3 riesgos técnicos |
| [`docs/release/RELEASE-PROCESS.md`](./docs/release/RELEASE-PROCESS.md) | Proceso de keystore, signing y release Android |
| [`docs/detection/PARKING-DETECTION.md`](./docs/detection/PARKING-DETECTION.md) | Spec algorítmica canónica (referenciada en CLAUDE.md) |
| [`docs/backlog/*.md`](./docs/backlog/) | Tickets activos por iniciativa |
| [`docs/refactors/PIPE-001-confirm-parking-pipeline.md`](./docs/refactors/PIPE-001-confirm-parking-pipeline.md) | Refactor planeado: extraer side-effects del service a WorkManager |
| [`diagnostics/README.md`](./diagnostics/README.md) | Captura y procesado de logs PARKDIAG |
| `docs/archive/` | Docs históricos preservados (roadmaps antiguos, Gemini analysis, etc.) |

---

## Getting started

### Prerequisitos
- Android Studio Ladybug+
- Xcode (sólo si se trabaja en iOS)
- Kotlin Multiplatform Mobile plugin

### Setup
1. `git clone ...`
2. Añadir `composeApp/google-services.json` (Firebase Console → Project settings)
3. Crear `local.properties` con:
   ```
   MAPS_API_KEY=AIza...
   GOOGLE_WEB_CLIENT_ID=...
   ```
4. (Opcional para release) `keystore.properties` con `RELEASE_KEYSTORE_FILE`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
5. Sync Gradle y ejecutar el target `:composeApp` (Android) o `iosApp` (iOS desde Xcode)

### Permisos Android
La app necesita: `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` + `ACTIVITY_RECOGNITION` + `POST_NOTIFICATIONS` + `BLUETOOTH_CONNECT`. El flujo de onboarding los solicita en orden con rationales.

### Permisos iOS
`NSLocationAlwaysAndWhenInUseUsageDescription` + `NSMotionUsageDescription` + `NSBluetoothAlwaysUsageDescription` + `UIBackgroundModes: location, fetch, processing`. Ya configurados en `iosApp/iosApp/Info.plist`.

---

## Convenciones rápidas

- **Strings**: nada hardcoded, todo en `composeResources/values/strings.xml` con keys EN
- **Magic numbers**: `private companion object` con `UPPER_SNAKE_CASE`
- **Commits**: Conventional Commits con ticket ID — `feat(home): add per-vehicle cards [MULTI-PARKING-001]`
- **Logs**: Napier con tag, nunca `println`

Detalle completo en [`CLAUDE.md`](./CLAUDE.md).

---

*Built with ❤️ by the AppToLast Team.*
