# Paparcar — Bugs detectados y deuda técnica

> Inventario auditado el **2026-05-24**. Sustituye a `docs/Gemini_Potential_Fixes.md` (archivado en `docs/archive/`).
> Severidad: **Critical** (rompe core / Main thread / data loss), **High** (degrada UX o estabilidad), **Medium** (perf / accesibilidad), **Low** (cosmético / TODO).

---

## §1 · ✅ RESUELTO 2026-05-24 — `runBlocking` en getters de DataStore [PERF-001]

**Archivo:** `composeApp/src/androidMain/kotlin/io/apptolast/paparcar/preferences/AndroidDataStoreAppPreferences.kt`

Antes: cada getter/setter envolvía el flow del DataStore con `runBlocking { ... }`, bloqueando el thread (Main incluido).

Fix aplicado: snapshot in-memory (`@Volatile private var snapshot: Preferences`) inicializado con **un solo** `runBlocking { store.data.first() }` en construcción, mantenido en sync por un `collect` en `CoroutineScope(IO)`. Setters actualizan el snapshot optimísticamente + lanzan el write asíncrono a DataStore. Resultado: **0 runBlocking por getter, 1 por ciclo de vida del proceso**.

---

## §2 · ✅ RESUELTO 2026-05-24 — Dos implementaciones de `AppPreferences` sin selección clara [DI-001]

`AndroidAppPreferences.kt` (SharedPreferences legacy) era código muerto — el DI ya bindeaba `AndroidDataStoreAppPreferences`. Borrado. La migración SharedPreferences → DataStore se mantiene vía `SharedPreferencesMigration` integrada en el `preferencesDataStore` delegate.

---

## §3 · ✅ RESUELTO 2026-05-24 — iOS Activity Recognition stubs sin implementar [IOS-AR-001]

`IosActivityRecognitionManagerImpl` ahora inyecta `DepartureEventBus` + `ParkingDetectionCoordinator` y emite las 3 señales:
- IN_VEHICLE/ENTER → `departureEventBus.onVehicleEntered(now)`
- IN_VEHICLE/EXIT  → `coordinator.onVehicleExit()`
- STILL/ENTER      → `coordinator.onStillDetected()`

**Pendiente fuera del scope:** el loop que llama a `coordinator.invoke(locations)` con un Flow de GPS en iOS (no hay foreground service equivalente). Trackeo en `docs/IOS_PLAN.md`.

**Nota colateral:** se añadieron a `IosAppPreferences` los miembros `hasSeenGpsAccuracyDisclaimer` + `setGpsAccuracyDisclaimerSeen()` que faltaban (paridad con Android, añadidos a la interface en un sprint anterior).

---

## §4 · ⚠️ PARCIAL 2026-05-24 — Maps API key en manifest sin restricción [SEC-001]

**Parte código (✅ done):**
- Build falla rápido en releases si falta `MAPS_API_KEY` (`composeApp/build.gradle.kts` — bloque `gradle.taskGraph.whenReady`).
- Documentado el modelo de seguridad y las acciones GCP requeridas en `docs/release/RELEASE-SECURITY.md`.

**Parte GCP Console (⚠️ pendiente usuario):**
- Rotar Maps API key (la actual estuvo hardcodeada en commits previos, recuperable via `git log`).
- Aplicar Application restrictions: package `io.apptolast.paparcar` + SHA-1 debug + release.
- Aplicar API restrictions: solo `Maps SDK for Android`.

Checklist completo en `docs/release/RELEASE-SECURITY.md §1`. (Inventario en memoria: `reference_api_keys_inventory.md`.)

---

## §5 · ✅ Parcial (2026-05-25) — Lambdas inline en listas [PERF-002]

**Done:**
- `HomeSheetContent.kt` — `vehiclesSection` ahora memoiza `onClick` con `remember(card.session?.id, card.vehicle.id, onParkingClick, onParkVehicle)`.
- `HomeZoneChips.kt` — los lambdas de `onClick` y `onDelete` por chip ahora se memoizan con `remember(zone.id, callback)`.

**Pendiente (no bloqueante):**
- `HomeSheetContent.kt:~359-371` (filter chips) — sin priorizar; el coste es marginal porque la lista es de 4-5 chips fijos. Reabrir si los profilers muestran recomposiciones reales del filter bar.

---

## §6 · ✅ Resuelto parcial (2026-05-25) — Accesibilidad de iconos [A11Y-001]

Re-auditado: la cifra "~25" venía de un grep crudo de `contentDescription = null` sin distinguir uso. Sólo había **dos** `IconButton` realmente clickables sin alternativa textual:

- `VehicleRegistrationScreen.kt:172` (botón de back en TopAppBar) → ahora usa `vehicle_registration_cd_back`.
- `PaparcarAuthSlots.kt:236` (toggle de visibilidad de contraseña) → ahora usa `auth_cd_show_password` / `auth_cd_hide_password`.

Los restantes ~60 hits del grep son iconos decorativos junto a `Text` con label (en `Surface(onClick=)` rows, banners, empty states, leadingIcon de TextField, etc.). En esos casos `contentDescription = null` es **correcto** — TalkBack fusiona el Row en un único elemento accesible y lee el Text.

**Pendiente (A11Y-002 — TalkBack pass):**
- Verificar in-device con TalkBack que los Surface(onClick=) realmente fusionan el contenido (algunos requieren `Modifier.semantics(mergeDescendants = true)`).
- Tamaños mínimos de touch target (≥48dp) en chips e icon buttons pequeños.
- CDs en iconos de status (banners, badges) — opcional pero mejor UX si están aislados visualmente.

---

## §6b · High — GoogleService-Info.plist ausente

**Carpeta:** `iosApp/iosApp/`

`iOSApp.swift` llama `FirebaseApp.configure()` pero el plist no está en el proyecto. iOS build de Firebase fallará silenciosamente en runtime.

**Fix sugerido:**
- Crear proyecto iOS en Firebase Console (bundle id `io.apptolast.paparcar`)
- Descargar `GoogleService-Info.plist` y añadir a `iosApp/iosApp/` (`Copy items if needed`, target `iosApp`)
- Verificar con `Analytics.logEvent` que se conecta

---

## §7 · ✅ Resuelto (2026-05-25) — Doze Mode / MIUI mitigation [DOZE-001]

**Cambios:**
- `AppPermissionState.kt` + `PermissionManagerImpl.kt` — nuevo campo `isBatteryOptimizationExempt` vía `PowerManager.isIgnoringBatteryOptimizations()`.
- `PermissionsState/Intent/Effect/ViewModel/Content` — nueva fila opcional "Unrestricted battery usage" (mismo patrón que Bluetooth). Toca la fila → lanza `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` system dialog. Si OEM detectado se muestra hint textual sobre "Autostart" / "Background activity".
- `AndroidManifest.xml` — añadido `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission.
- `DetectionHeartbeatWorker.kt` (nuevo) — worker periódico cada 15 min. Si hay sesiones activas en Room, llama `startForegroundService(ACTION_START_TRACKING)`. El servicio ya deduplica (no-op si ya está corriendo). Catch de `ForegroundServiceStartNotAllowedException` — el próximo heartbeat reintenta.
- `PaparcarApp.kt` + `BootCompletedReceiver.kt` — `DetectionHeartbeatWorker.enqueueKeep()` en ambos puntos de entrada.

**Pendiente (no en beta-1):** test en device físico Redmi Note 11 + Oppo ColorOS para validar que el OEM hint efectivamente aparece.

---

## §8 · ✅ Resuelto (2026-05-25) — Geofence TTL + Janitor [GEOF-001]

**Cambios:**
- `GeofenceManagerImpl.kt` — `NEVER_EXPIRE` → `GEOFENCE_TTL_MS` (24 h). Geofences se auto-destruyen tras 24h sin proceso activo.
- `UserParkingDao.kt` — añadido `getAllActive(): List<UserParkingEntity>` (suspend, no Flow).
- `GeofenceJanitorWorker.kt` (nuevo) — worker periódico (12 h, KEEP policy) que re-registra las geofences de todas las sesiones activas en Room. Re-añadir una geofence existente es idempotente (`FLAG_UPDATE_CURRENT`, `setInitialTrigger(0)`).
- `PaparcarApp.kt` — `GeofenceJanitorWorker.enqueueKeep(workManager)` en `onCreate()`.

---

## §9 · ✅ Resuelto (2026-05-25) — `ParkingDetectionService` `START_STICKY` sin re-check de permisos

**Archivo:** `composeApp/src/androidMain/.../detection/service/ParkingDetectionService.kt:~76`

`START_STICKY` reanuda el service tras un kill, pero si el usuario revocó `ACCESS_BACKGROUND_LOCATION` entre kills, el service intentará operar sin permiso → crash o silent failure.

**Fix aplicado:**
- `intent == null` (START_STICKY restart) → tratado como `ACTION_START_TRACKING`
- `hasRequiredPermissions()` verifica `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` (API Q+)
- Si faltan: `stopSelf()` + `notificationPort.showPermissionRevoked()` (notificación con CTA)
- `AppNotificationManager.showPermissionRevoked()` añadido a interface + Android impl + iOS no-op
- `notif_permission_revoked_title/text` en strings.xml EN/ES

---

## §10 · ✅ Resuelto (2026-05-25) — `collectAsState` sin lifecycle en commonMain

Varias pantallas usan `collectAsState()` desde commonMain (porque `collectAsStateWithLifecycle` está en androidx-lifecycle-runtime-compose, **androidMain only**). En iOS esto sigue funcionando, pero en Android **no respeta el lifecycle** y sigue consumiendo el flow tras un `pause`.

**Fix aplicado:**
- `presentation/util/StateFlowExt.kt` (commonMain) — expect `collectAsStateLifecycleAware()`
- `StateFlowExt.android.kt` → `collectAsStateWithLifecycle()`
- `StateFlowExt.ios.kt` → `collectAsState()`
- 8 screens migradas: `BluetoothConfigScreen`, `HistoryScreen`, `HomeScreen`, `ParkingLocationScreen`, `SettingsScreen`, `VehicleRegistrationScreen`, `VehiclePageContent`, `VehiclesScreen`

---

## §11 · ✅ Resuelto (2026-05-25) — Política de migraciones Room [DB-001]

**Archivo:** `composeApp/src/commonMain/.../data/datasource/local/room/AppDatabase.kt`

**Estado anterior:** `version = 3` con `fallbackToDestructiveMigration(true)` global en ambos DI modules → cualquier bump futuro borraba silenciosamente los datos del usuario.

**Decisión:**
- **No** se escriben migraciones retroactivas 1→2 y 2→3 (las versiones 1 y 2 sólo corrieron en builds internas pre-beta — cero usuarios productivos).
- En `AndroidPlatformModule.kt` y `IosPlatformModule.kt`: `fallbackToDestructiveMigration(true)` → `fallbackToDestructiveMigrationFrom(true, 1, 2)`. El fallback queda restringido a los saltos legacy 1→3 / 2→3; cualquier bump futuro (v3→v4, etc.) requerirá `Migration` explícito o Room lanzará al startup.
- Eliminados schemas obsoletos `9.json` y `10.json` (artefactos de cuando el proyecto empezaba en v9 antes de la renumeración).
- Añadido contrato en KDoc de `AppDatabase` explicando el flujo (bump version → KSP genera schema → escribir Migration → `MigrationTestHelper`).

**Pendiente cuando llegue v4:** primer Migration real + `MigrationTestHelper` (no se hace ahora porque no hay bump en cola).

---

## §12 · ✅ Resuelto (2026-05-25) — Glass effect sin blur real [GLASS-001]

**Archivos creados/modificados:**
- `GlassBlur.kt` (commonMain expect) — `@Composable expect fun Modifier.glassBlur(radius: Dp): Modifier`
- `GlassBlur.android.kt` — API ≥ S: `android.graphics.RenderEffect.createBlurEffect` via `asComposeRenderEffect()`. API < S: no-op.
- `GlassBlur.ios.kt` — no-op (`UIVisualEffectView` interop requeriría un composable wrapper, no un Modifier; la opacidad de GlassSurface actúa de fallback).
- `GlassSurface.kt` — aplica `glassBlur(GlassDefaults.BLUR_RADIUS)` al modifier cuando `isInteracting == true`. Radio: 20 dp.

---

## §13 · ✅ Resuelto completamente (2026-06-02) — `BluetoothParkingDetector` scope huérfano + proceso no protegido [BT-REFACTOR-FGS-001]

**Fix parcial 2026-05-25:** Scope movido a Koin single (`btDetectorScope`) en vez de inline en el Receiver. El leak de instancias se reducía, pero el scope seguía sin dueño con ciclo de vida y el proceso podía ser matado durante los ~5 minutos de detección.

**Fix completo 2026-06-02:**
- `BluetoothConnectionReceiver` reducido a trabajo mínimo: lookup vehicleId + disparar Service.
- Nuevo `BluetoothDetectionService` (`LifecycleService`, `START_NOT_STICKY`, `foregroundServiceType="location"`) dueño del scope largo. El proceso no puede ser matado mientras detecta.
- `BluetoothParkingDetector` → stateless: sin `scope`, sin `detectionJob`. `onCarDisconnected()` → `suspend fun detectParking()`. Abort-on-reconnect via cancelación cooperativa del Service.
- `btDetectorScope` eliminado de `AndroidDetectionModule`.

Ver: `docs/refactors/BT-REFACTOR-FGS-001-bluetooth-detection-foreground-service.md`

---

## §14 · Medium — Stubs iOS bloquean features

**✅ Resuelto 2026-05-25 (parcial):**
- `StubParkingSyncScheduler` → `IosParkingSyncScheduler` (coroutine + retry). No process-death persistence; BGTask deferred. [IOS-SYNC-001]
- `StubPlacesDataSource` → `IosOverpassPlacesDataSourceImpl` (NSURLSession, misma query Overpass). [IOS-PLACES-001]

**Pendiente:**
- `StubDepartureEventBus.kt` — no-op por diseño (correcto)
- `IosParkingSyncScheduler` — no persiste a través de process death (BGTask deferred)

---

## §15 · ✅ Resuelto (2026-05-25) — TODOs abandonados

- `IosActivityRecognitionManagerImpl.kt` líneas ~18–20 → cubierto en §3 [IOS-AR-001]
- `BluetoothConnectionReceiver.kt:43-45` → `getParcelableExtra` migrado a API 33+ con compat guard: `if (SDK_INT >= TIRAMISU) getParcelableExtra(key, Class) else @Suppress("DEPRECATION") getParcelableExtra(key)`

---

## §16 · ✅ RESOLVED — Reset incompleto en `HomeViewModel`

**Archivo:** `composeApp/src/commonMain/.../presentation/home/HomeViewModel.kt:74-79`

`searchQueryFlow` y `reconnectTick` son `MutableStateFlow` internos que no se resetean en `onCleared()`. No es leak (el ViewModel se destruye con el scope), pero es buena práctica resetear al detach.

**Fix (2026-05-25):** Added `onCleared()` override resetting both flows before calling `super.onCleared()`.

---

## §17 · Low — `MapFab.kt` vs `HomeMapFab.kt` (no es duplicación)

Confirmado por exploración: `HomeMapFab` es un wrapper temático sobre el genérico `MapCircleFab` de `MapFab.kt`. Patrón adapter válido — **no es deuda**.

---

## Resumen por severidad

| Sev. | Cantidad |
|------|----------|
| Critical | 4 |
| High | 6 |
| Medium | 5 |
| Low | 3 |

---

## Riesgos arquitectónicos (no son bugs hoy pero pueden serlo mañana)

1. **Foreground service como pilar único de detección** — si Android refuerza más las restricciones (como hizo con `FOREGROUND_SERVICE_LOCATION` en 14+), el modelo se rompe. Considerar arquitectura híbrida: geofence pasivo + AR transitions + service solo en ventanas activas.

2. **GitLive Firebase KMP** — wrapping de tercero. Si Google evoluciona el SDK oficial Android y GitLive no sigue, quedaremos atrás. Plan B: usar Firebase Android oficial vía `expect/actual` propio si GitLive deja de mantenerse.

3. **Monolito growth** — 330+ ficheros. Aceptable hoy. Si supera 500 o builds >2 min, evaluar split `:core:detection` (ver `docs/archive/ARCH-002-modularization-review.md`).

4. **Room sin migraciones explícitas** — bomba de relojería para usuarios que se actualizan a través de saltos de versión.

5. **iOS está al ~70%** — implementaciones reales pero sin sync persistente. Cualquier release a TestFlight tendría detección incompleta sin §3 + §14.
