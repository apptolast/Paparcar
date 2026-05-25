# Paparcar — Plan iOS

> Estado real auditado el **2026-05-24**. Sustituye y amplía `docs/ios-contracts.md` (movido a `docs/archive/`).

---

## TL;DR

- **8 / 8 implementaciones nativas reales** (Location, Activity Recognition, Geofence, Geocoder, Notifications, Permissions, Bluetooth, Connectivity)
- **3 stubs** restantes (DepartureEventBus por diseño, PlacesDataSource pendiente, ParkingSyncScheduler pendiente)
- **iOS app skeleton funcional** vía Compose Multiplatform (`MainViewController`)
- **`Info.plist` completo** con permisos de localización background, motion, bluetooth y `UIBackgroundModes`
- **Firebase App Distribution iOS: 0% configurado** — sin `GoogleService-Info.plist`, sin certificados de distribución, sin pipeline CI
- **Estimación primer beta iOS: 6–9 horas** (setup Apple Dev + certs + plist + Fastlane + CI)

---

## 1. Estado de implementación nativa

### 1.1 Implementaciones reales en `composeApp/src/iosMain/`

| Archivo | Tecnología nativa | Estado |
|---------|-------------------|--------|
| `IosLocationDataSourceImpl.kt` | `CLLocationManager` + callbackFlow, dual accuracy (best / balanced), `allowsBackgroundLocationUpdates=true`, `pausesLocationUpdatesAutomatically=false` | ✅ Real |
| `IosActivityRecognitionManagerImpl.kt` | `CMMotionActivityManager` — snapshots → transiciones sintetizadas, debounce de low-confidence | ✅ Real + wired al coordinator [IOS-AR-001 done 2026-05-24] |
| `IosGeofenceManagerImpl.kt` | `CLCircularRegion` + region monitoring del `CLLocationManager` | ✅ Real |
| `IosGeofenceEventBusImpl.kt` | `Channel`-backed event bus alimentado por delegate de CLLocationManager | ✅ Real |
| `IosAppNotificationManagerImpl.kt` | `UNUserNotificationCenter` + notification actions (Confirm / Deny) | ✅ Real |
| `IosPermissionManagerImpl.kt` | `CLLocationManager` + `CMMotionActivityManager` + `UNUserNotificationCenter` | ✅ Real |
| `IosGeocoderDataSourceImpl.kt` | `CLGeocoder` (forward + reverse, rate-limit ~50/min) | ✅ Real |
| `IosBluetoothScanner.kt` | `CBCentralManager` — solo estado; `getBondedDevices()` devuelve vacío (iOS no expone pairing por diseño) | ✅ Real (limitación de plataforma) |
| `IosConnectivityObserver.kt` | `NWPathMonitor` (iOS 12+) | ✅ Real |
| `IosAppPreferences.kt` | `NSUserDefaults` + migración perezosa desde clave legacy | ✅ Real |
| `IosParkingEnrichmentScheduler.kt` | Coroutine scope + retry (sin persistencia tras kill) | ⚠️ Parcial — BGTaskScheduler pendiente |
| `IosReportSpotScheduler.kt` | Coroutine scope + retry | ⚠️ Parcial — BGTaskScheduler pendiente |

### 1.2 Stubs intencionales

| Archivo | Razón |
|---------|-------|
| `ios/stub/StubDepartureEventBus.kt` | EventBus es in-memory singleton — no necesita API nativa, el stub es la impl correcta |
| ~~`ios/stub/StubPlacesDataSource.kt`~~ | ✅ Sustituido por `IosOverpassPlacesDataSourceImpl` 2026-05-25 — NSURLSession + Overpass API. [IOS-PLACES-001] |
| ~~`ios/stub/StubParkingSyncScheduler.kt`~~ | ✅ Sustituido por `IosParkingSyncScheduler` 2026-05-25 — coroutine+retry. Sin proceso-death persistence (BGTask deferred). [IOS-SYNC-001] |

### 1.3 Bloqueante real en iOS

- ~~**§3 BUGS_AND_DEBT.md**: `IosActivityRecognitionManagerImpl.kt` tiene TODOs~~  → **✅ RESUELTO** 2026-05-24 [IOS-AR-001]. La estrategia probabilística está completamente cableada en iOS.
- Pendiente: loop GPS que llama `coordinator.invoke(locations)` con un Flow real en iOS (no hay ForegroundService equivalente).

---

## 2. iOS app shell (Xcode project)

```
iosApp/
├── Configuration/Config.xcconfig
├── iosApp.xcodeproj/
└── iosApp/
    ├── iOSApp.swift              ← @main, AppDelegate, FirebaseApp.configure()
    ├── ContentView.swift         ← Bridge: SwiftUI → MainViewController() de Kotlin
    ├── Assets.xcassets/          (App icon 1024 ya generado)
    └── Info.plist
```

### 2.1 `Info.plist` — permisos ✅

- `NSLocationWhenInUseUsageDescription` ✅
- `NSLocationAlwaysAndWhenInUseUsageDescription` ✅
- `NSMotionUsageDescription` ✅
- `NSBluetoothAlwaysUsageDescription` ✅
- `UIBackgroundModes`: `location`, `fetch`, `processing` ✅

### 2.2 Lo que falta en el shell

| Item | Estado |
|------|--------|
| `GoogleService-Info.plist` | ❌ Ausente — Firebase init fallará silenciosamente en runtime |
| Provisioning profile (Ad Hoc / Distribution) | ❌ |
| Distribution certificate | ❌ |
| App Store / Ad Hoc deploy target | ❌ |
| TestFlight metadata | ❌ |

---

## 3. Tareas pendientes ordenadas por dificultad

### 🟢 Fáciles (< 1 h cada una)

1. **Generar `GoogleService-Info.plist`** — Firebase Console → Add iOS app (bundle `io.apptolast.paparcar`) → descargar plist → drag a Xcode (target iosApp, Copy items if needed) — _**~20 min**_
2. **Limpiar TODOs de Bluetooth deprecation** — `BluetoothConnectionReceiver.kt:43-45` getParcelableExtra (Android, no iOS) — _**~10 min**_

### 🟡 Medias (1–3 h cada una)

3. **Wire AR → coordinator en iOS** — terminar `IosActivityRecognitionManagerImpl` para invocar `coordinator.onVehicleExit()` / `onStillDetected()`. Test manual en simulador con "Custom location" + freeway drive. — _**~2 h**_
4. **`StubPlacesDataSource` → MapKit** — sustituir por `MKLocalSearch` o endpoint HTTP. Reuse `PlaceCategory` mapping. — _**~2 h**_
5. **Apple Developer setup** — App ID `io.apptolast.paparcar`, Distribution Certificate, Provisioning Profile (Ad Hoc para beta). Instalar localmente en Xcode → Accounts. — _**~1.5 h**_

### 🟠 Difíciles (3–6 h cada una)

6. **`StubParkingSyncScheduler` → BGTaskScheduler** — implementar `BGProcessingTask` con `requiresNetworkConnectivity=true`. Registrar identifier en `Info.plist`. Persistir el `sessionId` pendiente en `NSUserDefaults` para sobrevivir al kill. — _**~4 h**_
7. **Fastlane iOS lane** — `fastlane init`, configurar `build_app` + `firebase_app_distribution`. Probar build local antes de CI. — _**~3 h**_
8. **CI iOS** — extender `.github/workflows/distribute.yml` (o crear `distribute-ios.yml`) con runner `macos-latest`. Subir secrets:
   - `APPLE_DEVELOPER_ID`
   - `APPLE_APP_SPECIFIC_PASSWORD`
   - `FIREBASE_APP_ID_IOS`
   - `FIREBASE_SERVICE_ACCOUNT_JSON`
   - `PROVISIONING_PROFILE_BASE64`
   - `DISTRIBUTION_CERTIFICATE_BASE64`
   - `DISTRIBUTION_CERTIFICATE_PASSWORD`
   — _**~3 h**_

### 🔴 Estratégicas (no bloqueantes para beta)

9. **MapKit native map view** — sustituir o complementar `kmp-maps` con view nativa iOS para mejor UX (gestos, dark mode automático, search bar nativa). Solo si el feedback de beta lo pide. — _**~8–12 h**_
10. **iOS Widget** — single parking session widget. — _**~6 h**_

---

## 4. Plan Firebase App Distribution iOS (paso a paso)

### Paso 1 — Setup Apple Developer (~1 h)
```
Apple Developer Portal → Certificates, IDs & Profiles
  ├─ App IDs → Register: io.apptolast.paparcar
  ├─ Certificates → Apple Distribution (.cer) → install in Keychain
  └─ Profiles → Ad Hoc (para beta) o App Store (para TestFlight)
      Devices: añadir UDIDs de testers
```

### Paso 2 — Configurar Firebase iOS (~30 min)
```
Firebase Console → Project Settings → Add app → iOS
  ├─ Bundle ID: io.apptolast.paparcar
  ├─ Download GoogleService-Info.plist
  └─ Drag to Xcode → iosApp target → Copy items if needed ✓
```

### Paso 3 — Configurar Xcode signing (~30 min)
```
iosApp.xcodeproj
  ├─ Signing & Capabilities
  │   ├─ Team: <Apple Developer Team>
  │   ├─ Bundle Identifier: io.apptolast.paparcar
  │   ├─ Provisioning Profile: (seleccionar el creado)
  │   └─ Background Modes: ✓ location, ✓ fetch, ✓ processing
  └─ Build Settings
      ├─ Code Signing Identity: Apple Distribution
      └─ Other Linker Flags: -ObjC (si el SDK lo pide)
```

### Paso 4 — Build & archive (~30 min)
```bash
cd iosApp
xcodebuild -project iosApp.xcodeproj \
  -scheme iosApp \
  -configuration Release \
  -archivePath build/iosApp.xcarchive \
  archive

# ExportOptions.plist con method=ad-hoc + signingStyle=manual
xcodebuild -exportArchive \
  -archivePath build/iosApp.xcarchive \
  -exportOptionsPlist iosApp/ExportOptions.plist \
  -exportPath build/ \
  -allowProvisioningUpdates
```

### Paso 5 — Distribuir vía Firebase (~30 min)
Opción A — CLI directo:
```bash
firebase appdistribution:distribute \
  build/iosApp.ipa \
  --app <FIREBASE_APP_ID_IOS> \
  --groups "beta-paparcar" \
  --release-notes-file distribution/release-notes.txt
```

Opción B — Fastlane (recomendado para CI):
```ruby
# fastlane/Fastfile
lane :ios_beta do
  build_app(
    project: "iosApp/iosApp.xcodeproj",
    scheme: "iosApp",
    export_method: "ad-hoc",
    output_directory: "build"
  )
  firebase_app_distribution(
    app: ENV["FIREBASE_APP_ID_IOS"],
    ipa_path: "build/iosApp.ipa",
    groups: "beta-paparcar",
    release_notes_file: "distribution/release-notes.txt"
  )
end
```

### Paso 6 — CI (~2-3 h)
Extender `.github/workflows/distribute.yml` (o crear nuevo):
```yaml
distribute-ios:
  runs-on: macos-latest
  steps:
    - uses: actions/checkout@v4
    - name: Set up Xcode
      run: sudo xcode-select -s /Applications/Xcode_15.4.app
    - name: Install Apple cert & profile
      env:
        CERT_BASE64: ${{ secrets.DISTRIBUTION_CERTIFICATE_BASE64 }}
        CERT_PASSWORD: ${{ secrets.DISTRIBUTION_CERTIFICATE_PASSWORD }}
        PROFILE_BASE64: ${{ secrets.PROVISIONING_PROFILE_BASE64 }}
      run: ./scripts/install-apple-credentials.sh
    - name: Install Firebase CLI
      run: curl -sL https://firebase.tools | bash
    - name: Run Fastlane
      env:
        FIREBASE_APP_ID_IOS: ${{ secrets.FIREBASE_APP_ID_IOS }}
        FIREBASE_TOKEN: ${{ secrets.FIREBASE_TOKEN }}
      run: bundle exec fastlane ios_beta
```

---

## 5. Riesgos específicos de iOS

1. **`CMMotionActivityManager` requiere physical device** — el simulador no genera transiciones reales. QA debe hacerse en device real.
2. **`CLLocationManager.allowsBackgroundLocationUpdates` requiere `Always` permission** — si el usuario otorga sólo "While Using", la detección background no funciona. Mostrar upgrade prompt explícito.
3. **Apple rechaza apps con location en background sin justificación clara** — incluir en App Store description y en el rationale screen explicación de por qué se usa.
4. **BGTaskScheduler tiene budget** — solo se ejecuta cuando iOS decide (algunas horas o días). No se puede forzar. El sync no es realtime en iOS comparado con Android.
5. **`Info.plist` necesita registrar BGTask identifiers** — añadir `BGTaskSchedulerPermittedIdentifiers` array antes de empezar a usar BGTaskScheduler.
6. **iOS Bluetooth pairing es opaco** — `getBondedDevices()` siempre vacío. La estrategia BT en iOS depende de que el dispositivo se conecte como periférico o el usuario lo seleccione manualmente con un UUID conocido. Funcionalidad reducida frente a Android.

---

## 6. Estimación total

| Bloque | Horas |
|--------|-------|
| Setup Apple Developer + certs + profiles | 1.5 |
| Generar GoogleService-Info.plist + wire | 0.5 |
| Wire AR → coordinator | 2 |
| Reemplazar `StubPlacesDataSource` | 2 |
| Implementar BGTaskScheduler real | 4 |
| Fastlane iOS | 3 |
| CI GitHub Actions iOS | 3 |
| Smoke test en device real (Redmi no aplica, hace falta iPhone físico) | 2 |
| **Total para primer beta funcional iOS** | **~18 h** |
| Estimación TL;DR (sin sync real ni wire AR, beta-bare) | **~6–9 h** |

La estimación TL;DR de 6–9 h corresponde a "subir un IPA firmado a Firebase App Distribution con la app cargando y permitiendo login + ver mapa". Para que la **detección de aparcamiento funcione end-to-end en iOS**, sumar las ~9 h adicionales del bloque medio (#3 + #4 + #6).
