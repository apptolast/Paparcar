# Detection Readiness & Permission Tiering — Reference Document

**Status:** living document. Update when the readiness model, permission tiers, the navigation gate, or the onboarding permission flow change.

> ⚠️ **Revisado el 2026-08-30 contra master `46621e7f`** [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001].
> El **modelo** (§2 tiers · §3 gate · §4 `DetectionReadiness` · §5 diagrama) sigue siendo verdad: los
> ficheros existen y `DetectionReadiness` conserva sus cinco estados (`Disabled`/`Blocked`/`Ready`/
> `Monitoring`/`Parked`) con `DisabledReason` en `NO_VEHICLE` · `NON_PARKING_VEHICLE` · `TURNED_OFF`.
>
> **Lo que sí ha cambiado es la SUPERFICIE (§6) y por tanto la tabla de ficheros (§8):** ya no hay un
> `DetectionReadinessBanner` ni claves `detection_*`. La readiness se presenta a través de
> `presentation/home/model/DetectionUiState.kt` (8 variantes: `NoVehicle`, `Inactive`, `BlockedCore`,
> `Parked`, `Monitoring`, `AwaitingFirstPark`, `ArmedBluetooth`, `Silent`) y se pinta en
> `presentation/home/sections/sheet/components/HomeDetectionSurface.kt`, con copy bajo `home_det_*`.
> Es decir: dejó de ser una tira informativa y pasó a ser **la cabecera de la hoja de Home**, que
> además ofrece acción. Las filas de §6/§8 marcadas ⚠️ abajo son las que quedaron desfasadas.
**Audience:** solo developer + AI pair.
**Scope:** the *presentation/runtime* layer that decides **what the app tells the user about automatic detection** — distinct from how detection actually works (signals, scoring, geofences). For the algorithm see [`PARKING-DETECTION.md`](./PARKING-DETECTION.md) and [`SIGNAL-ARCHITECTURE.md`](./SIGNAL-ARCHITECTURE.md).

Epic: **DET-READY-001**. Implemented 2026-06-27.

---

## 1. The problem this solves

"No geofence and no parked car" is the *normal idle state*, not an error — but the old UI couldn't say so, and the old permission gate forced **all** permissions (foreground + background + activity recognition + notifications) before the user could use anything. Two consequences:

1. The map (consumer side) needlessly sat behind background-location, the permission users distrust most → drop-off at first launch.
2. There was no single source of truth for "is detection armed / running / blocked / not applicable", so the Home surface could not reassure or nudge.

DET-READY-001 splits permissions into **tiers**, gates the app on the minimum tier only, and models detection state as one **`DetectionReadiness`** stream rendered in a persistent Home banner.

> Product framing (do not regress): auto-detection is sold as **"automate your parking"** — remember where you parked, automatic history, free your spot on departure — personal value first, community second. It is **not** "work for free for the network".

---

## 2. Permission tiers

`RequiredPermission` (domain, `domain/permissions/`) tags each runtime permission with a `PermissionTier`:

| Permission | Tier | Why |
|---|---|---|
| `FOREGROUND_LOCATION` | **CORE** | The spot map (consumer side) is meaningless without it. |
| `NOTIFICATIONS` | **CORE** | Detection confirms + general UX. |
| `BACKGROUND_LOCATION` | **PRODUCER** | Needed only for auto-detection (publishing your spot / find-my-car). |
| `ACTIVITY_RECOGNITION` | **PRODUCER** | Same — the Coordinator strategy reads AR. |
| BT connect, battery exemption, OEM autostart | OPTIONAL | Reliability enhancers; never block anything. |

Helpers on `AppPermissionState`: `hasCorePermissions`, `hasProducerPermissions`, and `missingPermissions()` / `missingCorePermissions()` / `missingProducerPermissions()`.

> Android reality reinforcing the tiering: on Android 11+ background location **cannot** be requested in the same dialog as foreground — it is an incremental, partly Settings-driven flow. "Force everything up front" is not even technically available.

---

## 3. The navigation gate — CORE only

The app gate is driven by `AppState.isFullyOperational = permissionsGranted && locationServicesEnabled`, where `permissionsGranted` is now **`hasCorePermissions`** (was `allPermissionsGranted`). Effects:

- Losing **PRODUCER** (background/AR) while in Home **does not** eject the user — it surfaces as a `Blocked` banner instead.
- Losing **CORE** (foreground location) **does** route back to the permission gate (`App.kt` `MainAppNavigation` LaunchedEffect).
- Cold start (`SplashViewModel.resolveStartRoute`) gates on `hasCorePermissions`, so a returning CORE-only user lands straight on Home rather than being re-nagged.

Consumer-side Home gating (`HomeState.hasCorePermissions`, formerly `allPermissionsGranted`): the map/location stream, nearby-spots query, filter bar, personal blocks and zone chips all gate on **CORE**. AR registration (PRODUCER work) gates on `hasProducerPermissions` — in both `HomeViewModel` and `MainActivity`.

---

## 4. `DetectionReadiness` — the model

`domain/model/DetectionReadiness.kt`. **Orthogonal to `HomeMode`**: `HomeMode` is *what the user is doing* (Browse/Reporting/…); readiness is *what the detection background system is doing*. They coexist, so readiness is a **field** on `HomeState`, never a `HomeMode` variant. It replaces the thin `HomeState.allPermissionsGranted` boolean for detection purposes.

```kotlin
sealed class DetectionReadiness {
    data class Disabled(val reason: DisabledReason)        // NO_VEHICLE | NON_PARKING_VEHICLE
    data class Blocked(val missing: Set<RequiredPermission>)
    data class Ready(val strategy: ParkingStrategy)        // armed, idle — the "no geofence yet" state
    data class Monitoring(val strategy: ParkingStrategy)   // a tracking job is actively running
    data class Parked(val session: UserParking)            // parked, geofence watching for departure
}
```

Computed by `ObserveDetectionReadinessUseCase`, which `combine`s four reactive inputs and resolves with **first-match-wins precedence**:

```
inputs: vehicles · activeSessions · permissionState · detectionRuntime.isRunning
        (VehicleRepository) (UserParkingRepository) (PermissionManager) (DetectionRuntimeState)

precedence:
   vehicles empty .......................... Disabled(NO_VEHICLE)
   strategyFor(vehicles) == NONE ........... Disabled(NON_PARKING_VEHICLE)   // active vehicle is SCOOTER/BIKE
   missingPermissions().isNotEmpty() ....... Blocked(missing)
   an active session exists ................ Parked(session)                 // prefers one with a geofenceId
   detectionRuntime.isRunning .............. Monitoring(strategy)
   else .................................... Ready(strategy)
```

Why `Blocked` outranks `Parked`: a parked car with a revoked permission must still warn the user that departure detection won't fire — silence would be misleading.

`strategyFor(vehicles)` is a pure function extracted from `ParkingStrategyResolver` (single source of truth shared with the suspend `resolve()`), reading BT adapter state at call time.

### Runtime flag (`DetectionRuntimeState`)

`Ready` vs `Monitoring` needs to know whether a tracking job is live. `CoordinatorDetectionService` owns the job; it pushes the flag through the platform-agnostic `MutableDetectionRuntimeState` (a DI singleton, `bind DetectionRuntimeState`):

- `setRunning(true)` synchronously at the top of `startParkingDetection()`.
- `setRunning(false)` in the job's `finally` **only if it is still the current job** (`detectionJob === thisJob`) — so a superseded job from a re-arm never races the new job's `true` to `false`.
- `setRunning(false)` in `onDestroy()`.

---

## 5. State diagram

```
                       ┌──────────────────────────────┐
                       │  Disabled(NO_VEHICLE)         │◀── delete all vehicles
   add a parking ─────▶│                               │
   vehicle             └──────────────┬────────────────┘
                                      │ active vehicle is a car/motorbike
                                      ▼
                       ┌──────────────────────────────┐
   grant PRODUCER ────▶│  Ready(strategy)             │◀── departure: spot freed, geofence gone
   (or already armed)  │  armed & idle — "no geofence"│
                       └───┬───────────────────▲──────┘
       geofence-exit, drive│                   │ park confirmed (tentative→final)
                           ▼                   │
                       ┌───────────────┐   ┌───┴──────────────┐
                       │ Monitoring    │──▶│ Parked(session)  │
                       │ (job running) │   │ geofence watching│
                       └───────────────┘   └──────────────────┘

   revoke a required permission, from ANY of the above:
        ─────────────────────────────────────▶ Blocked(missing)  ──grant──▶ back to Ready/Monitoring/Parked

   active vehicle is SCOOTER/BIKE:
        ─────────────────────────────────────▶ Disabled(NON_PARKING_VEHICLE)
```

(`Blocked` is reachable in Home only because the gate is CORE-only — §3. Under the old all-or-nothing gate it could never appear there.)

---

## 6. The Home banner

> ⚠️ **Sección desfasada (2026-08-30).** El banner como *tira superior persistente* ya no existe:
> `ui/components/DetectionReadinessBanner.kt` no está en el árbol y **ninguna clave `detection_*` de
> esta tabla existe** en `strings.xml`. Hoy la readiness es la **cabecera de la hoja de Home**
> (`presentation/home/sections/sheet/components/HomeDetectionSurface.kt`), alimentada por
> `presentation/home/model/DetectionUiState.kt` y con copy bajo `home_det_*`
> (`home_det_core_title/sub/cta`, `home_det_monitoring`, `home_det_awaiting_*`, `home_det_driving_*`,
> `home_det_candidate_*`, `home_det_ask_*`…).
>
> Las 8 variantes de `DetectionUiState` — `NoVehicle` · `Inactive` · `BlockedCore` · `Parked` ·
> `Monitoring` · `AwaitingFirstPark` · `ArmedBluetooth` · `Silent` — son las que hoy deciden qué se
> muestra. **El razonamiento de esta sección sigue valiendo** (severidad adaptativa, CTA solo cuando
> hay handler, estados de acción-necesaria nunca ocultos); lo que cambió es dónde vive.
>
> Lo de abajo se conserva como registro del diseño de junio.

`ui/components/DetectionReadinessBanner.kt`. A **persistent top strip** anchored in a `Column(TopCenter)` in `HomeScreen`, **outside** the `overlayVisible` gate, so action-needed states are never hidden when the user drags the sheet or selects a spot. The wrapping column owns the status-bar inset (the search header no longer does).

Prominence is **adaptive by severity**:

| State | Severity | Copy key | CTA |
|---|---|---|---|
| `Ready` | subtle green | `detection_ready_armed` | — |
| `Monitoring` | subtle green | `detection_monitoring_active` (BT variant reuses `detection_banner_bt_active`) | — |
| `Parked` | subtle blue | `detection_parked_watching` | — |
| `Blocked` | amber | `detection_blocked_title` | `detection_blocked_cta` → activate detection |
| `Disabled(NO_VEHICLE)` | amber | `detection_disabled_no_vehicle` | `detection_disabled_no_vehicle_cta` → add vehicle |
| `Disabled(NON_PARKING_VEHICLE)` | grey info | `detection_disabled_non_parking` | — |

CTA handlers are **nullable**; the CTA renders only when a handler is provided (same pattern as `DetectionStatusBanner.onConfigureBluetooth`). `HomeScreen` wires them to navigation (`App.kt` HOME route):
- **Activate** → `navigate(Routes.PERMISSIONS)` — reuses the existing escalation (disclosure + launcher + Settings). The CORE-only gate routes the user back to Home once PRODUCER lands.
- **Add** → `VEHICLE_REGISTRATION?origin=vehicles` (completion pops back to Home).

Compose `@Preview`s (all states + 3 placement options) live in `androidMain/.../ui/components/DetectionReadinessBannerPreviews.kt`.

---

## 7. Onboarding — CORE-required, PRODUCER-optional

> ⚠️ **Corregido el 2026-08-30:** la pantalla de *priming* separada (`PermissionsRationaleScreen`, con
> `perm_rationale_title`) **ya no existe** — ni el fichero ni la clave. El onboarding de permisos es
> hoy una sola pantalla, `PermissionsScreen` + `PermissionsContent`, con `PermissionTier.kt`,
> `PermissionRow.kt` y `DetectionTierStatusCard.kt` (la tarjeta que dice qué nivel de detección
> desbloquea lo concedido). Lo demás de esta sección **sigue siendo exacto**: las dos claves que cita
> (`permissions_btn_activate_detection`, `permissions_continue_with_core`) existen y el flujo
> CORE-only es el vigente.

The permissions screen leads with personal value and lets the user enter with **CORE only**:

- The step-2 (background-location) CTA is labelled **"Turn on auto-detection"** (`permissions_btn_activate_detection`).
- A secondary **"Maybe later"** (`permissions_continue_with_core`) appears when `PermissionsState.canContinueWithCore` (CORE + GPS satisfied, PRODUCER incomplete) → `PermissionsIntent.ContinueWithCore` → `NavigateToHome` (guarded on CORE + GPS).
- The "grant everything → auto-navigate home" path (`allPermissionsGranted && GPS`) is untouched; the skip is an additional, explicit exit.

A new user who taps "Maybe later" reaches Home with the `Blocked` banner nudging them to enable detection — the contextual model, end to end.

---

## 8. Key files

| Concern | File |
|---|---|
| Permission tiers | `domain/permissions/RequiredPermission.kt`, `AppPermissionState.kt` |
| Readiness model | `domain/model/DetectionReadiness.kt` |
| Readiness use case | `domain/usecase/detection/ObserveDetectionReadinessUseCase.kt` |
| Runtime flag | `domain/detection/DetectionRuntimeState.kt`, `CoordinatorDetectionService.kt` |
| Strategy (pure) | `domain/detection/ParkingStrategyResolver.kt` (`strategyFor`) |
| Gate | `presentation/app/AppViewModel.kt`, `SplashViewModel.kt`, `App.kt` |
| **Superficie** (antes "Banner") | `presentation/home/sections/sheet/components/HomeDetectionSurface.kt` + `presentation/home/model/DetectionUiState.kt` — ⚠️ **ya no** `ui/components/DetectionReadinessBanner.kt` |
| Previews de diseño | `androidMain/.../ui/components/DetectionReadinessDesignPreviews.kt` |
| Home wiring | `presentation/home/HomeViewModel.kt`, `HomeTripController.kt`, `HomeState.kt`, `HomeScreen.kt` |
| Onboarding | `presentation/permissions/PermissionsViewModel.kt`, `PermissionsState.kt`, `PermissionsContent.kt` — ⚠️ el `PermissionsRationaleScreen.kt` que citaba esta fila ya no existe |
| Tests | `ObserveDetectionReadinessUseCaseTest`, `AppPermissionStateTest`, `HomeViewModelTest` |

> Verificado fichero a fichero el 2026-08-30. Las dos filas ⚠️ son exactamente lo que este barrido
> encontró roto: dos rutas que se citaban con confianza y no existían.

---

## 9. Open / deferred

- **Device validation** (reactive-flow-split-risk): green tests do not prove the runtime permission flow. Verify: (a) revoke PRODUCER in Home → not ejected, `Blocked` shown, map still works; (b) cold start with CORE only → lands on Home; (c) revoke foreground → ejected to gate; (d) onboarding "Maybe later" → Home; (e) banner "Activate" → grant → `Ready`.
- BT device label is not yet carried in `DetectionReadiness` — `Monitoring`/`Ready` BT variant differs only by icon, not the device name. Enrichment is a follow-up.
- Romanian onboarding copy was unified to the informal register (the source file was mixed). Revisit if a formal register is preferred.
