# DET-WATCH-REACTIVATE-001 · El botón "Reactivar" de la vigilancia detenida no reactiva nada

**Estado:** 🔵 En progreso · rama `bugfix/DET-WATCH-REACTIVATE-001-reactivate-cta` ·
worktree `../Paparcar-watch-reactivate`

**Rebase 2026-08-15:** sobre `master` = `1e94142f` (docs de UI-ZONE-MANAGE-001), sin conflictos.
El único commit del ticket pasa de `7b6fddbd` a `460d004f`; el árbol no cambia (master solo traía
docs), así que los 1143 tests verdes siguen siendo válidos.

## Problema

Reporte del user (2026-08-14, Ford Focus, tras instalar `prodDebug` sobre `prodRelease` — instalación
limpia: Room vacío, sesión iniciada de cero):

1. Sale el row rojo *"Vigilancia detenida de tu Ford Focus"* y no se sabe si la vigilancia se ha
   detenido de verdad.
2. Al pulsar el CTA aparece el diálogo de exención de batería; se acepta y **el row sigue ahí**.
3. En el segundo toque **el botón ya no hace nada**.

### Causa 1 — el CTA está cableado a la palanca equivocada

`ParkedWatchBadge.WATCH_INTERRUPTED` se enciende por UNA señal: `servicePresence == Dead`
(`ParkedWatchBadge.kt:58`), o sea el FGS residente (sentry) no está vivo. Pero su botón hace:

```kotlin
// HomeDetectionSurface.kt:230-239
ParkedWatchBadge.WATCH_INTERRUPTED -> ActionRow(
    primaryLabel = …home_det_watch_interrupted_cta,   // "Reactivar"
    onPrimary = onRequestBatteryExemption,            // ← solo abre el diálogo de batería
```

`requestIgnoreBatteryOptimizations()` lanza `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` y nada más:
**nunca arranca el servicio**. `presence` sigue `Dead` → el row no se va nunca. Y en el segundo toque
Android no pinta nada porque la activity del sistema se cierra al instante cuando el paquete ya está
en la whitelist → "el botón ya no hace nada". Un solo error de cableado explica los dos síntomas.

La palanca correcta ya existía y no se usaba desde la UI: `CoordinatorDetectionService.ACTION_RESUME_SENTRY`
→ `resolveIdleEpilogue` → `enterSentry` (presence pasa a `Sentry`).

### Causa 2 — el self-heal se pierde justo en la instalación limpia

`PaparcarApp.onCreate` → `resumeSentryIfCoordinatorParked()` (`PaparcarApp.kt:127`) leía
`observeActiveSessions().first()` — la **primera** emisión de Room. En una instalación limpia Room
está vacío porque la sesión aún no ha bajado de Firestore → `return@launch` → el sentry no arranca
nunca. Además solo corría en `Application.onCreate` (creación de PROCESO): si el FGS muere con el
proceso vivo, o si el arranque se refusa (estaba en `runCatching{}.onFailure`, silencioso), nada lo
reconstruye mientras la app está abierta. Exactamente el escenario del reporte.

Nota adicional: `Application.onCreate` es además el peor momento para un `startForegroundService`
(el proceso puede no ser aún foreground-elegible en A12+); la Activity ya resumida sí lo es.

## Doctrina violada

- **"Todo trigger dispara SIEMPRE"** — un CTA explícito del user es el trigger más fuerte que existe
  y no disparaba nada.
- **Copy: causa + consecuencia + remedio, sin mecánica interna** — *"Tu móvil detuvo la detección"*
  es falso por exceso: geocerca EXIT, AR ENTER, `ParkingSafetyNetWorker` (15 min) y el heartbeat
  exacto siguen armados; lo que cae es la vigilancia INMEDIATA.
- **Sistemas, no parches** — la regla "mientras haya un coche Coordinator aparcado, el vigilante debe
  estar vivo" estaba implícita en dos sitios distintos (epílogo del servicio y self-heal del
  Application) que podían divergir.

## Señales / datos disponibles

- `DetectionRuntimeState.presence` (`Dead`/`Sentry`/`Active`) — verdad del proceso, en memoria.
- `resolvePostDetectionLifecycle(autoDetect, hasParked, strategy)` — la regla PURA que el servicio ya
  usa para decidir sentry-vs-stop. Es el mismo gate que necesita el resumidor.
- `UserParkingRepository.observeActiveSessions()`, `ParkingStrategyResolver`, `AppPreferences.autoDetectParking`.

## Diseño

**El invariante en UN sitio:** *si el vigilante DEBERÍA estar vivo (`resolvePostDetectionLifecycle`
== `EnterSentry`) y `presence == Dead`, hay un hueco de vigilancia que la app cierra en cuanto tiene
un momento foreground legal.*

- `ObserveDepartureWatchGapUseCase` (commonMain, nuevo) — combina sesiones + vehículos (estrategia) +
  toggle + `presence` y emite el hueco reusando `resolvePostDetectionLifecycle`. Fuente única.
- `DepartureWatchResumer` (interface commonMain + impl Android + no-op iOS) — dispara
  `ACTION_RESUME_SENTRY`. `force = true` para la acción explícita del user; las automáticas pasan por
  un throttle para que un flap patológico no entre en bucle.
- `MainActivity` colecciona el hueco en `repeatOnLifecycle(STARTED)` → cierra el hueco cuando la app
  está delante. Al ser un STREAM (no un `first()`), la instalación limpia se cura sola en cuanto el
  sync trae la sesión, sin carrera.
- `PaparcarApp.resumeSentryIfCoordinatorParked()` **se elimina**: queda subsumido (mismo gate, mejor
  momento, sin carrera).
- El CTA de `WATCH_INTERRUPTED` pasa a `HomeIntent.ResumeWatch` → `DepartureWatchResumer.resume(force)`.
  La exención de batería queda SOLO en `WATCHING_FRAGILE`, que sí se cura sola
  (`MainActivity.onResume` → `refreshPermissions` → reliability deja de ser REDUCED).
- Copy honesto: la vigilancia inmediata está pausada, el segundo plano sigue, la salida puede
  detectarse tarde.

## Criterio de éxito

- Tocar "Reactivar" con el vigilante muerto → `presence` pasa a `Sentry` y el row cambia solo a
  "Vigilando tu sitio". Si el arranque se refusa, el user ve un error, no un botón mudo.
- Instalación limpia + login: cuando el sync trae la sesión aparcada, el vigilante arranca sin que el
  user toque nada.
- Ningún caso enciende el diálogo de batería desde `WATCH_INTERRUPTED`.
- Tests: gap use case (aparcado/no, estrategia, toggle, presence) + intent del VM.

## Consumidores auditados

`grep -rn "requestIgnoreBatteryOptimizations\|RequestBatteryExemption\|ACTION_RESUME_SENTRY"`:

| Sitio | Estado |
|---|---|
| `HomeDetectionSurface` WATCH_INTERRUPTED | ✅ pasa a `onResumeWatch` → `HomeIntent.ResumeWatch` |
| `HomeDetectionSurface` WATCHING_FRAGILE | ✅ se queda con la exención — es su palanca correcta y se cura sola en `onResume` |
| `PermissionsScreen` / `PermissionsViewModel` (onboarding + ajustes) | ✅ exento: es setup-time, la exención SÍ es lo que pide |
| `PaparcarApp.resumeSentryIfCoordinatorParked` | ✅ **eliminado** (no duplicado): mismo gate, mejor momento, sin carrera |
| `MainActivity` | ✅ nuevo lane `repeatOnLifecycle(STARTED)` sobre el gap |
| `CoordinatorDetectionService` RESUME_SENTRY | ✅ decisión sin cambios; log ahora nombra al emisor (`EXTRA_RESUME_SOURCE`) |
| `SignificantMotionMonitor` (SENTRY→ACTIVE) | ✅ cubierto por convergencia: `enterSentry` re-arma el sensor, así que el tap recupera la vía inmediata |
| Dev Catalog / `StateGalleryScreen` | ✅ variante en paridad; el fake pasa presence→Sentry, la fila se cura al tocarla |
| Previews `HomeDetectionSurfacePreviews` | ✅ sin cambio de firma (los callbacks tienen default) |
| `detectionPath` / `armEvidence` | ✅ exento: no hay camino de confirmación nuevo — el resume reconstruye un VIGILANTE, no confirma plazas |

## Estado de verificación

- ✅ `compileProdDebugKotlinAndroid`, `compileProdReleaseKotlinAndroid`, `assembleMockDebug`
- ✅ `testProdDebugUnitTest`: **1143 tests, 0 fallos** (1126 antes + 17 nuevos: 10 del gap use case,
  3 del intent del VM, y los 4 restantes por el fixture compartido)
- ✅ `testMockDebugUnitTest --tests "…coordinator.*"`
- ✅ `docs/detection/PARKING-DETECTION.md` con su entrada en el log cronológico
- ⏳ **Pendiente: device.** Reproducir la fila en el Focus (Coordinator, sin BT) → tocar "Reactivar"
  → la fila debe pasar sola a "Vigilando tu sitio", y el log debe traer
  `RESUME_SENTRY (home-cta)`. Segundo caso: reinstalar limpio, iniciar sesión, esperar el sync → el
  vigilante debe levantarse solo con `RESUME_SENTRY (foreground-gap)`.

## Relacionados

- [[det-watch-honest-001]] (el badge honesto que introdujo el row) · `det-battery-exemption-nudge-001`
  (subsumido) · `det-resident-fgs-001` (el sentry) · `det-strategy-gate-001`.
