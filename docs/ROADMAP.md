# Paparcar — Roadmap

> Documento consolidado. Sustituye a `Paparcar_Roadmap_TechDebt.md` y `Paparcar_Roadmap_Completo.md` (movidos a `docs/archive/`).
> Última auditoría: **2026-05-25**.

---

## ✅ Completado

### Fase 0–3 (foundation, detection MVP, sync)
- Clean Architecture + MVI + Koin + Room KMP + Firebase GitLive
- Estrategia dual de detección: `BluetoothParkingDetector` (determinista) y `ParkingDetectionCoordinator` (probabilístico)
- `ConfirmParkingUseCase` → Room + Firestore + Geofence + Notification + WorkManager enrichment
- `DetectParkingDepartureUseCase` validada por 3 señales (sesión activa + geofence + AR window)
- 6 workers de WorkManager con backoff exponencial y constraints diferenciados
- BaseLogin 1.0.16 integrado (Auth + offline-safe `getCurrentSession`)
- AppDatabase v3 (sin migraciones definidas — ver BUGS_AND_DEBT.md)

### Sprint reciente (mayo 2026)
- **AUTH-002** — fix lost parking on null session (BaseLogin 1.0.15)
- **ADD-PARKING-PIN-001 / ADD-ZONE-PIN-001** — `TeardropPinScaffold` unificado + `HomeMode.AddingParking` + `UpdateParkingLocationUseCase`
- **MULTI-PARKING-001** — per-vehicle cards en Home, sesiones independientes por vehículo
- **MARKERS-001 / MAP-TYPE-001 / HOME-DEPTH-001** — sistema de marcadores y picker de mapa
- **VEH-BT-001** — asignación manual de dispositivo BT por vehículo
- **ICON-LOGO-PALETTE-001/002** — adaptive icon repintado a la paleta dark theme
- **DS-TYPO-002** — Outfit + Inter + Barlow Condensed tipography system
- **RELEASE-001** (scaffold) — keystore, ProGuard, Firebase App Distribution **Android**
- **DS unification Phase A** — superficies opacas, `PapSectionHeader`, CTAs unificados
- **PERF-001/002 · DI-001 · DB-001 · A11Y-001 · DOZE-001 · GEOF-001** — auditoría P0/P1 completada 2026-05-24/25
- **§9 · §10 · §13 · §15 · §16** — auditoría Medium/Low completada 2026-05-25 (permission guard START_STICKY, collectAsStateLifecycleAware, BT scope inyectado, getParcelableExtra API 33+, HomeViewModel reset)
- **BUG-FGS-001 + BUG-FGS-002** — eliminados 24+5 crashes (9+1 usuarios). `PendingIntent.getForegroundService()` en `ActivityRecognitionManagerImpl`; lógica de routing IN_VEHICLE movida a `ParkingDetectionService.ACTION_VEHICLE_TRANSITION`; `ActivityTransitionReceiver` simplificado a STILL-only; `StartDetectionWorker` eliminado. Done 2026-05-25.
- **BUG-GEOFENCE-001** — `GeofenceManagerImpl.buildPendingIntent()` usaba `FLAG_IMMUTABLE`; en Android 12+ Play Services no puede escribir los extras de `GeofencingEvent` en el intent, `triggeringGeofences` llega null y el receiver no encola `DepartureDetectionWorker`. Fix: `FLAG_MUTABLE`. También: `DepartureDetectionWorker` ahora propaga `spotType`, `detectionReliability` y `sizeCategory` al `ReportSpotReleasedUseCase`. Done 2026-05-25.
- **BUG-PERMS-UX-001** — guía paso a paso antes de abrir Settings para ubicación en segundo plano (Android 11+ no muestra dialog, el usuario no sabe que debe elegir "Permitir siempre"). `PapAlertDialog` con instrucciones numeradas. Done 2026-05-25.
- **IOS-AR-001 · IOS-BUILD-001 · IOS-SYNC-001 · IOS-PLACES-001** — iOS completado al ~95% (solo IOS-DIST-001 y BGTask pending)
- **I18N-001** — strings EN/ES/IT/PT/FR/DE/NL/PL/RO sincronizadas; stale key `home_my_car_section_header` eliminada en DE/NL/PL/RO
- **BUG-LANG-002** — idioma guardado no se aplicaba al arrancar la app en frío. `LaunchedEffect(appState.selectedLanguage)` en `App.kt` llama a `applyAppLocale()` en la primera composición. Idempotente: no-op en API 33+ donde el sistema ya restaura el locale. Done 2026-05-26.

---

## 🚧 En progreso

| Ticket | Descripción | Estado | Blocker |
|--------|-------------|--------|---------|
| **RELEASE-001** | Primer beta a testers | Pre-beta audit | P0: restringir API Maps en GCP, audit reglas Firestore, mover Maps key fuera del manifest |
| ✅ **OFFLINE-LOGIN-GUARD-001** | Fail-fast + retry + error diferenciado — done 2026-05-25 | Cerrado | Offline: no sign-out, `BootstrapFailure.Offline`, `retry()` + `BootstrapOfflineDialog`. Fatal: sign-out + `ShowError` (previo). Tests: 2 casos nuevos en `SplashViewModelTest`. |
| **HOME-MARKERS-AUDIT** | ~~#1 TTL dead code~~ ✅ / ~~#3 SELECTED~~ ✅ / **#2 MANUAL decay** ⏳ deferred (product decision needed) | Backlog | #2: MANUAL spots stay blue forever — after N rejections? fraction of TTL? Needs design call before touching code |
| **VEH-MARKERS + VEH-NAME** | 7 tickets de vehículos + multi-parking markers | Diseño | Ver `docs/backlog/vehicles-multimarker-2026-05-19.md` |

---

## 📋 Plan de acción (auditoría 2026-05-24)

Tickets nuevos derivados del resumen ejecutivo. Detalle técnico en `docs/BUGS_AND_DEBT.md`.

### P0 — Critical (bloqueantes de beta)
| Ticket | Descripción | Estimado | Ref |
|--------|-------------|----------|-----|
| ✅ **PERF-001** | ~~Eliminar `runBlocking` en getters de `AndroidDataStoreAppPreferences`~~ — done 2026-05-24 | 2 h | BUGS §1 |
| ✅ **DI-001** | ~~Unificar `AppPreferences` — eliminar `AndroidAppPreferences` SharedPreferences legacy~~ — done 2026-05-24 | 2 h | BUGS §2 |
| ✅ **IOS-AR-001** | ~~Cablear los 3 TODO de `IosActivityRecognitionManagerImpl` con el coordinator~~ — done 2026-05-24 | 2 h | IOS_PLAN §3 |
| ⚠️ **SEC-001** | Parte código done (fail-fast + RELEASE-SECURITY.md). Pendiente: rotar key + restricciones GCP Console (acción usuario) | 1 h + GCP | BUGS §4 |
| ⚠️ **AUDIT-FIRESTORE-001** | Reglas mínimas documentadas en `docs/release/RELEASE-SECURITY.md §2`. Pendiente: desplegar + testear en Firebase Console (acción usuario) | 0 h código | RELEASE-001 |
| ✅ **BUG-WORKER-001** | Race condition `LocationUpdateSyncWorker` vs `ParkingSyncWorker` — resuelto 2026-05-25. `scheduleLocationUpdate()` usa `APPEND_OR_REPLACE` en la misma cadena `parking_chain_$sessionId`, garantizando `set()` antes de `update()`. | 1 h | worker-bugs-2026-05-25 |
| ✅ **BUG-WORKER-002** | `ParkingSyncWorker` cancelado por OEM (Redmi) — resuelto 2026-05-25. `withContext(NonCancellable)` protege las escrituras Firestore de cancelaciones externas del Job. | 1 h | worker-bugs-2026-05-25 |

### P1 — High (calidad / producción)
| Ticket | Descripción | Estimado | Ref |
|--------|-------------|----------|-----|
| ✅ **DOZE-001** | ~~Doze/MIUI mitigation~~ — done 2026-05-25. Battery opt exemption row en Permissions + `DetectionHeartbeatWorker` 15 min. | 4 h | BUGS §7 |
| ✅ **GEOF-001** | ~~Scheduler de TTL para geofences huérfanos~~ — done 2026-05-25. 24h TTL + `GeofenceJanitorWorker` cada 12h. | 2 h | BUGS §8 |
| ✅ **A11Y-001** | ~~contentDescription audit~~ — done 2026-05-25. Sólo 2 reales (back + password toggle); resto eran decorativos. Ver BUGS §6. | 1 h | BUGS §6 |
| ✅ **PERF-002** | ~~Memoizar lambdas inline en `HomeSheetContent.kt:174` y `HomeZoneChips.kt:70`~~ — done 2026-05-25 | 1 h | BUGS §5 |
| ✅ **DB-001** | ~~Política de migraciones Room~~ — done 2026-05-25. `fallbackToDestructiveMigrationFrom(true, 1, 2)` + KDoc contrato en `AppDatabase`. Migración real + `MigrationTestHelper` quedan para cuando llegue v4. | 1 h | BUGS §11 |

### P2 — Medium (deuda + UX)
| Ticket | Descripción | Estimado | Ref |
|--------|-------------|----------|-----|
| **RELEASE-001** | Cerrar pre-beta audit + keystore + primer upload Firebase App Distribution Android | 4 h | en progreso |
| ✅ **GLASS-001** | ~~Decidir glass real~~ — done 2026-05-25. `expect/actual Modifier.glassBlur`: Android ≥ S usa `RenderEffect.createBlurEffect` (20 dp); <S + iOS son no-op. GlassSurface aplica blur cuando `isInteracting`. | 6 h | BUGS §12 |
| ✅ **I18N-001** | ~~Completar traducciones~~ — done 2026-05-25. All 8 locales (ES/IT/PT/FR/DE/NL/PL/RO) in sync with EN. Stale key `home_my_car_section_header` purged from DE/NL/PL/RO. | — | — |
| **A11Y-002** | TalkBack pass completo + tamaños mínimos de touch target | 4 h | BUGS §4 |

### P3 — iOS
| Ticket | Descripción | Estimado | Ref |
|--------|-------------|----------|-----|
| ✅ **IOS-BUILD-001** | ~~Reparar errores de compilación iOS~~ — done 2026-05-25. Único error real: `PermissionsScreen.ios.kt` no manejaba `RequestStepBluetooth` ni `RequestBatteryOptimizationExemption`. Añadido `requestBluetooth()` en `IosPermissionRequester` (CBCentralManager). Resto de ítems (getString, Dispatchers.IO, format, currentLocale) compilaban OK con Kotlin 2.3.10. | 1 h | — |
| **IOS-DIST-001** | Firebase App Distribution iOS (Fastlane + cert + provisioning) | 6–9 h | IOS_PLAN |
| ✅ **IOS-SYNC-001** | ~~Sustituir `StubParkingSyncScheduler`~~ — done 2026-05-25. `IosParkingSyncScheduler` con coroutine scope + retry (patrón `IosParkingEnrichmentScheduler`). No process-death persistence — BGTask deferred until Swift bridge solidifies. | 4 h | IOS_PLAN |
| ✅ **IOS-PLACES-001** | ~~Sustituir `PlacesDataSource` stub~~ — done 2026-05-25. `IosOverpassPlacesDataSourceImpl` con NSURLSession — misma query Overpass que Android, sin nuevas dependencias. | 3 h | IOS_PLAN |

### Housekeeping
| Ticket | Descripción | Estimado |
|--------|-------------|----------|
| ✅ **CLEAN-001** | ~~Decidir destino de logs y diagnósticos sueltos~~ — done 2026-05-25. `diagnostics/README.md` documenta la política: commits como evidencia canónica; `!diagnostics/**/*.log` ya en `.gitignore`. Pendiente de staging en próximo commit. | 30 min |
| **PIPE-004** *(deferred)* | Collapse `EnrichParkingSessionWorker` + `LocationUpdateSyncWorker` (reabrir cuando bugs de vehículos estén estables) | — |

---

## 🔮 Futuro / nice-to-have

- **Widget Android** de parking activo + tiempo desde aparcamiento
- **Android Auto integration** — surface de "soltar plaza" en infotainment
- **WearOS complication** — countdown TTL del último spot publicado
- **Confidence dashboard** in-app (debug) para visualizar el scoring del coordinator
- **Modularización** (ver `docs/archive/ARCH-002-modularization-review.md`) — solo si el monolito empieza a escalar mal (>500 archivos, builds >2 min)
- **Server-side detection ML** — entrenar modelo con eventos confirmados/denegados (privacy-first, federado)
- **Idiomas P2** — DE, NL, PL, RO (excluidos RTL y glifos complejos por complejidad UI)

---

## Convenciones de tickets

Formato de branch + commit:
```
feature/{TICKET}-descripcion-corta
fix/{TICKET}-descripcion-corta
chore/{TICKET}-descripcion-corta

feat(scope): descripción [TICKET]
fix(scope): descripción [TICKET]
```

Backlogs vivos en `docs/backlog/*.md` — un fichero por feature/iniciativa.
