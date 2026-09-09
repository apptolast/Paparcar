# DET-A-GRANTED-PERMISSION-MUST-REOPEN-THE-WATCH-001 · El resume del centinela dispara una vez antes de conceder permisos y nada reintenta al concederlos

**Estado:** ✅ Done (2026-09-09) — verificado en emulador con el build del worktree; suite 2534/0

## Problema

En una instalación limpia (o tras borrar datos) con una sesión aparcada que el bootstrap restaura
de Firestore, el carril de resurrección del centinela [DET-WATCH-REACTIVATE-001] gasta su único
disparo ANTES de que el usuario conceda la ubicación, y la concesión no re-dispara nada:

1. `MainActivity.closeDepartureWatchGapWhileVisible()` colecta `ObserveDepartureWatchGapUseCase`
   (sesiones ∧ pref ∧ estrategia ∧ presence). El bootstrap trae la sesión → gap `true` → `resume()`
   despacha `RESUME_SENTRY` — con la ubicación aún sin conceder.
2. El promote del FGS es rechazado (`SecurityException`, `REFUSED_MISSING_LOCATION_PERMISSION`) y
   el servicio muere sin cambiar `presence` (ya era `Dead`).
3. El usuario concede los permisos SIN salir de la app (así funciona el checklist post
   ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001). Ninguna de las 4 entradas del gap cambia →
   `distinctUntilChanged` no re-emite → no hay más intentos en toda la vida del proceso. El
   cooldown automático de 60 s (`DepartureWatchResumerImpl`) además quedó estampado por el intento
   condenado.
4. Resultado: permisos ✓, coche aparcado ✓, detección ON ✓ y **cero FGS centinela** hasta matar y
   reabrir la app (o hasta que un trigger externo resucite el servicio).

**Reproducido el 2026-09-09 en el emulador (prodDebug 1.1.0/12):**
- `16:28:36 resume(foreground-gap) → RESUME_SENTRY dispatched` →
  `✗ FGS promote refused — location permission missing` → `Service onDestroy`.
- Se conceden TODOS los permisos runtime con la app en primer plano → **ningún `resume` más en
  3 minutos**. (El emulador acabó residente de chiripa: un `GEOFENCE_EXIT` viejo arrancó el
  servicio y su epílogo entró en sentry — trigger que no existe con el usuario quieto.)
- Reporte del user: build de Play (release), borrar datos para grabar el vídeo de detección de la
  ficha → sin notificación FGS y sin detección pese a permisos concedidos y coche aparcado
  (sesión activa `4b19f9db…` verificada en Firestore).

El contraste que delata el agujero: el armado de AR en `MainActivity:102-121` SÍ combina
`permissionState` y se re-arma al conceder; el carril del centinela es el único que no mira
permisos. El propio KDoc del carril dice que «se re-chequea al volver al primer plano» — en una
instalación limpia nunca sales de la app.

## Doctrina violada

- *Todo trigger dispara SIEMPRE.* La concesión del permiso es el momento en que el gap se vuelve
  cerrable, y hoy ese flanco no dispara nada: el watch queda muerto con una sesión aparcada
  vigilable. Un parking perdido con datos disponibles es bug NUESTRO.
- [DET-WATCH-RESUME-RACE-001] «una pregunta, un contestador» — la pregunta nueva («¿puede ESTE
  momento de foreground cerrar el gap?») debe vivir junto al contestador del gap, no re-derivarse
  en la Activity.

## Señales / datos disponibles

- `PermissionManager.permissionState: StateFlow<AppPermissionState>` (commonMain), refrescado en
  `onResume`. `hasCorePermissions` = ubicación foreground — exactamente lo que el promote del FGS
  necesita con la app visible.
- El gap crudo ya está en `ObserveDepartureWatchGapUseCase` con su `current()` como gate del
  resumer.

## Diseño

**El invariante en UN sitio:** el carril de foreground no debe preguntar «¿hay gap?» sino «¿hay un
gap que ESTE momento pueda cerrar?». Esa pregunta se contesta donde ya vive el contestador del gap:

1. `ObserveDepartureWatchGapUseCase` gana `PermissionManager` y un segundo stream
   `closeableFromForeground(): Flow<Boolean>` = `invoke() ∧ permissionState.hasCorePermissions`,
   con `distinctUntilChanged`. El flanco de conceder la ubicación (con gap ya `true`) produce la
   emisión `true` que faltaba.
   - `invoke()` y `current()` quedan INTACTOS: el gate del resumer y el CTA `home-cta` (force)
     conservan su semántica — un gap sin permisos sigue siendo un gap real (es lo que Readiness
     reporta como `Blocked`), y un force sin permisos sigue despachando para que el servicio
     conteste con su rechazo honesto + notificación de permiso revocado.
2. `MainActivity.closeDepartureWatchGapWhileVisible()` colecta el stream nuevo. Efecto lateral
   bueno: el intento condenado pre-permiso DESAPARECE (ya no se despacha un RESUME_SENTRY que el
   OS va a rechazar), así que el cooldown de 60 s ya no queda estampado por un intento inútil y no
   hace falta tocarlo.
3. **Mitad B — la notificación tragada** (MEDIDO 09-09 en emulador): con el centinela residente y
   `POST_NOTIFICATIONS` denegado, la notificación FGS **ni entra en la Notification List** (se
   descarta al publicarla); tras `pm grant`, `isForeground=true` y **ningún `NotificationRecord`**
   — NO resucita sola. `watchSentryPreconditions` (service = solo I/O) gana un segundo carril: al
   flanco denegado→concedido de `hasNotificationPermission` con presencia Sentry, re-promociona con
   el mismo id (idempotente, la misma llamada de cada `onStartCommand`) → re-post in-place. El
   flanco llega porque `refreshPermissions()` corre en cada `onResume` del MainActivity y el
   StateFlow es el mismo singleton del proceso.

## Criterio de éxito

- Test unitario: con gap `true` y permiso denegado, `closeableFromForeground()` emite `false`; al
  conceder emite `true` (el flanco existe); `current()` sigue diciendo `true` sin permisos.
- En device/emulador: instalación limpia con sesión activa remota → conceder ubicación con la app
  abierta → la notificación de centinela aparece en segundos, sin reabrir la app.
- Suite completa verde.

## Consumidores auditados

`grep observeDepartureWatchGap|ObserveDepartureWatchGapUseCase|closeableFromForeground|departureWatchResumer.resume|buildSentryNotification`:

| Consumidor | Clasificación |
|---|---|
| `MainActivity.closeDepartureWatchGapWhileVisible` (app) | **cerrado** — pasa a `closeableFromForeground()` |
| `DepartureWatchResumerImpl.resume` gate (`current()`) | **exento con razón** — gate del despacho; cuando el carril automático lo llama, el permiso ya está (es lo que emitió). Ciego a permisos a propósito para no romper el force |
| `HomeViewModel.ResumeWatch` (`home-cta`, force=true) | **exento con razón** — un tap explícito debe despachar aunque falten permisos: el servicio contesta con el rechazo honesto + notificación de permiso revocado |
| `AndroidDetectionModule:80` (`observeDepartureWatchGap = get()` del resumer) | **exento** — wiring, sin cambio de semántica |
| `DetectionModule:299` (factory del use case) | **cerrado** — gana `permissionManager = get()`; bindings presentes en `AndroidPlatformModule:52` y `IosPlatformModule:61` |
| `CoordinatorDetectionService.enterSentry` (promote del sentry) | **cubierto** — sin cambio; la visibilidad tardía la cubre el carril nuevo de `watchSentryPreconditions` |
| `BluetoothDetectionService` (FGS BT) | **exento con razón** — vida corta (minutos): re-promociona en cada arranque; no acumula horas de notificación tragada |
| `ParkingSafetyNetWorker` (usa `DETECTION_NOTIFICATION_ID` para dismiss) | **exento** — solo dismiss, no publica FGS |
| `ObserveDetectionReadinessUseCase` (`Blocked`) | **exento** — ya modela permisos por su cuenta; sin cambio |
| `CoordinatorDetectionServiceTest` (Robolectric, sin Koin) | **cubierto** — el inject nuevo es lazy y ese test no lo toca |

## Verificación

**Del bug (pre-fix, emulador prodDebug 1.1.0/12, 09-09):**
- `resume(foreground-gap)` 16:28:36 → `REFUSED_MISSING_LOCATION_PERMISSION` → 0 reintentos en
  3 min tras conceder TODOS los permisos runtime por `pm grant`.
- Mitad B medida: FGS residente + `POST_NOTIFICATIONS` denegado → notificación descartada al post;
  `pm grant` posterior NO la resucita (0 `NotificationRecord` con `isForeground=true`).

**Del fix (build del worktree instalado en el emulador, 09-09):**
1. Arranque sin ubicación → **cero despacho** (antes: dispatch + rechazo). El cooldown no se estampa.
2. `pm grant` ubicación + onResume (refresh) → `resume(foreground-gap) → RESUME_SENTRY` a las
   17:24:58 → `enterSentry` → centinela residente. El flanco ES la emisión.
3. `pm grant` POST_NOTIFICATIONS con el servicio residente + onResume →
   `↻ notification permission granted while resident — re-posting the sentry notification` →
   `NotificationRecord` 0 → 1: la notificación de centinela se hace visible sin reiniciar nada.
- Suite completa: **2534 tests, 0 fallos** (4 nuevos en `ObserveDepartureWatchGapUseCaseTest`, 19
  en la clase). `:app:compileProdDebugKotlin`, `:app:assembleMockDebug` ✓.

Exentos del checklist det-change con razón: sin strings nuevos (no hay copy — solo logs PARKDIAG),
sin pantalla/estado nuevo (Dev Catalog sin cambios), sin camino de confirmación nuevo
(`detectionPath`/`armEvidence` intactos).
