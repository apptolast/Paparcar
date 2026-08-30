# Paparcar — Plan iOS

> **Doc vivo.** Verificado contra master `46621e7f` el **2026-08-30** (`find shared/src/iosMain`,
> `ls iosApp/iosApp/`, `.github/workflows/`). Sustituye y amplía `ios-contracts.md`, **borrado** en
> este mismo barrido por estar reemplazado por completo (recuperable en el histórico de git).
>
> 🔵 **Quién valida iOS:** un compañero con Mac. Desde el entorno de desarrollo principal (Windows) no
> se puede compilar Kotlin/Native para iOS ni abrir Xcode — por eso todo lo de aquí se comprueba por
> lectura y por CI, nunca ejecutando.

---

## TL;DR

- **Todas las piezas nativas están implementadas.** `iosMain` tiene 11 ficheros de detección
  (`IosActivityRecognitionManagerImpl`, `IosGeofenceManagerImpl`, `IosStepDetectorSource`,
  `IosArrivalHandoffDetectionImpl`, los tres schedulers…) más location, bluetooth, notificaciones,
  permisos, connectivity y preferencias.
- **Cero stubs.** `grep "class Stub\|object Stub" shared/src/iosMain` no devuelve nada; el paquete
  `ios/stub/` ya no existe. Lo que falta no es rellenar huecos.
- **Lo que falta son dos cosas de ciclo de vida**, no de API:
  1. el **lazo que empuja el stream de GPS al coordinator** (no hay equivalente al foreground
     service de Android) → `IOS-F0-001` prepara los puertos, en rama sin mergear;
  2. **persistencia tras process death** en los schedulers → hace falta `BGTaskScheduler`.
- **`Info.plist` completo** con localización background, motion, bluetooth y `UIBackgroundModes`.
- ✅ **CI compila `iosMain` en `macos-latest`** desde `02a29f62` — antes **nadie compilaba iOS nunca**.
  Sin `xcodebuild`: el proyecto no tiene scheme compartido.
- ❌ **Firebase App Distribution iOS sigue a 0%**: sin `GoogleService-Info.plist`, sin certificados,
  sin lane de Fastlane.
- **Estimación primer beta iOS: 6–9 h** para subir un IPA que arranque; **~18 h** para que la
  detección funcione de punta a punta.

---

## 1. Estado de implementación nativa

### 1.1 Implementaciones reales en `shared/src/iosMain/`

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
| `IosParkingEnrichmentScheduler.kt` · `IosParkingSyncScheduler.kt` · `IosReportSpotScheduler.kt` | Coroutine scope + retry | ⚠️ Parcial — no sobreviven a process death; falta BGTaskScheduler |
| `IosStepDetectorSource.kt` · `IosArrivalHandoffDetectionImpl.kt` · `IosDepartureWatchResumerImpl.kt` · `IosManualParkingDetectionImpl.kt` · `IosDepartureEventBusImpl.kt` | Puertos de detección con impl real (el event bus es in-memory por diseño) | ✅ Real |
| `IosOverpassPlacesDataSourceImpl.kt` | NSURLSession + misma query Overpass que Android | ✅ Real |

### 1.2 Stubs

**Ninguno.** El paquete `ios/stub/` desapareció; `grep "class Stub\|object Stub" shared/src/iosMain`
no devuelve nada. Los tres que había (DepartureEventBus, PlacesDataSource, ParkingSyncScheduler)
tienen implementación real desde mayo.

### 1.3 Bloqueante real en iOS

**Un solo bloqueante de fondo, y no es una API que falte: es un ciclo de vida.** Nadie llama a
`coordinator.invoke(locations)` con un `Flow` real de GPS, porque iOS no tiene equivalente al
foreground service que en Android sostiene el stream. Todas las señales existen; falta el lazo.

`IOS-F0-001` (rama `feature/IOS-F0-001-fase0`, **sin mergear**) es la Fase 0 de ese trabajo: puertos,
capacidades y harness promovido. Su validación depende del compañero con Mac.

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

| Item | Estado (verificado 2026-08-30) |
|------|--------|
| `GoogleService-Info.plist` | ❌ Ausente — `FirebaseApp.configure()` falla **en silencio** en runtime |
| **Scheme compartido** en `iosApp.xcodeproj` | ❌ — sin él no hay `xcodebuild`, y por eso el job de CI se queda en compilar Kotlin |
| Provisioning profile (Ad Hoc / Distribution) | ❌ |
| Distribution certificate | ❌ |
| App Store / Ad Hoc deploy target | ❌ |
| TestFlight metadata | ❌ |

---

## 3. Tareas pendientes ordenadas por dificultad

> ✅ **Ya cerradas** (no repetirlas): wire AR → coordinator [IOS-AR-001] · `StubPlacesDataSource` →
> `IosOverpassPlacesDataSourceImpl` [IOS-PLACES-001] · `StubParkingSyncScheduler` →
> `IosParkingSyncScheduler` [IOS-SYNC-001] · **job `macos-latest` en CI que compila `iosMain`**
> [CI-IOS-COMPILES-ON-A-MAC-NOT-ON-A-PROMISE-001].

### 🟢 Fáciles (< 1 h cada una)

1. **Generar `GoogleService-Info.plist`** — Firebase Console → Add iOS app (bundle `com.rndeveloper.paparcar`) → descargar plist → drag a Xcode (target iosApp, Copy items if needed) — _**~20 min**_. Verificado el 30-08: sigue ausente de `iosApp/iosApp/`.

### 🟡 Medias (1–3 h cada una)

2. **Mergear `IOS-F0-001`** — Fase 0 (puertos + capacidades + harness) lleva en rama desde entonces; necesita a alguien con Mac que la compile y la juzgue. — _**depende del compañero**_
3. **Cerrar el lazo GPS → coordinator** — un `CLLocationManager` en background alimentando `coordinator.invoke(locations)`, con el presupuesto de batería que iOS permite. Es el bloqueante real de §1.3. — _**~2 h + campo**_
4. **Apple Developer setup** — App ID `com.rndeveloper.paparcar`, Distribution Certificate, Provisioning Profile (Ad Hoc para beta). Instalar localmente en Xcode → Accounts. — _**~1.5 h**_

### 🟠 Difíciles (3–6 h cada una)

5. **BGTaskScheduler para los tres schedulers** — `BGProcessingTask` con `requiresNetworkConnectivity=true`, identifier en `Info.plist`, y el `sessionId` pendiente en `NSUserDefaults` para sobrevivir al kill. — _**~4 h**_
6. **Fastlane iOS lane** — `fastlane init`, configurar `build_app` + `firebase_app_distribution`. Probar build local antes de CI. — _**~3 h**_
7. **CI de distribución iOS** — hoy el CI **compila** `iosMain` pero no archiva ni firma. Extender con un job que ejecute Fastlane. ⚠️ Requiere antes un **scheme compartido** en el `.xcodeproj`: sin él no hay `xcodebuild`, que es justo por lo que el job actual se queda en la compilación de Kotlin. Secrets:
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
  ├─ App IDs → Register: com.rndeveloper.paparcar
  ├─ Certificates → Apple Distribution (.cer) → install in Keychain
  └─ Profiles → Ad Hoc (para beta) o App Store (para TestFlight)
      Devices: añadir UDIDs de testers
```

### Paso 2 — Configurar Firebase iOS (~30 min)
```
Firebase Console → Project Settings → Add app → iOS
  ├─ Bundle ID: com.rndeveloper.paparcar
  ├─ Download GoogleService-Info.plist
  └─ Drag to Xcode → iosApp target → Copy items if needed ✓
```

### Paso 3 — Configurar Xcode signing (~30 min)
```
iosApp.xcodeproj
  ├─ Signing & Capabilities
  │   ├─ Team: <Apple Developer Team>
  │   ├─ Bundle Identifier: com.rndeveloper.paparcar
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
Los workflows reales hoy son `ci.yml` (build + tests + compilación de `iosMain` en `macos-latest`),
`distribute-alpha.yml` y `distribute-beta.yml`. Añadir un job de distribución iOS al que corresponda:
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
| Generar `GoogleService-Info.plist` + añadirlo al target | 0.5 |
| Mergear y validar `IOS-F0-001` | depende del compañero |
| Cerrar el lazo GPS → coordinator | 2 + campo |
| BGTaskScheduler real en los tres schedulers | 4 |
| Scheme compartido + Fastlane iOS | 3 |
| Job de distribución en CI (el de compilación ya existe) | 2 |
| Smoke test en iPhone físico (el Redmi no vale) | 2 |
| **Total para un beta iOS con detección funcionando** | **~15 h + validación en Mac** |
| **Beta-bare** (IPA firmado que arranca, hace login y pinta el mapa) | **~6–9 h** |

La cifra de 6–9 h corresponde a *"subir un IPA firmado a Firebase App Distribution con la app
cargando"*. Para que la **detección funcione end-to-end en iOS**, hay que sumar el lazo GPS y el
BGTaskScheduler — que son los dos puntos de §1.3 y §2 de [`BUGS_AND_DEBT.md`](./BUGS_AND_DEBT.md).

⚠️ Ninguna de estas horas la puede ejecutar el entorno principal: **todo lo de Xcode necesita un Mac**.
Lo único que sí se puede sostener desde aquí es que `iosMain` **compile**, y eso ya lo garantiza el CI.
