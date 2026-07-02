# DET-RESUME-RECONCILE-001 — reconciliar la sesión activa al revivir (salida perdida por móvil apagado)

**Fecha:** 2026-07-02
**Estado:** BACKLOG (preparada — no empezada)
**Prioridad:** media (correctitud del estado propio del usuario; la pérdida comunitaria es aceptable)
**Relacionada:** watchdog OEM-kill (backlog), la contingencia AR-ENTER/watchdog, [DET-G-04] (geocerca como trigger fiable), [DET-AR-REARM-001]

## Problema (escenario real)

Con la geocerca ya funcionando bien como trigger de salida ([DET-G-04]), el usuario puede ir **cambiando de plaza** y la app va liberando/guardando correctamente. Pero hay un agujero:

1. El usuario aparca en la plaza A → sesión activa + geocerca A registradas (todo persistido en Room).
2. **El móvil se queda sin batería.**
3. El usuario vuelve, se sube al coche y **conduce** con el móvil muerto.
4. Como no corre nada, **no salta `GEOFENCE_EXIT`** → la salida nunca se detecta:
   - La comunidad **nunca se entera** de que A quedó libre.
   - La app del usuario **sigue mostrando "aparcado en A"** (estado rancio) aunque ya no lo esté.

## Reencuadre clave: es reconciliar, no detectar

Con el móvil apagado **no corre nada** (ni geocerca, ni AR, ni el worker periódico). Por tanto **no es un problema de detección en tiempo real, es de reconciliación al revivir.** Y hay un **límite honesto**: al revivir no se puede saber *cuándo* se fue ni si *condujo o anduvo*. Esa información no existe.

## Por qué el watchdog actual NO lo cubre

`DetectionHeartbeatWorker` (hoy `WATCHDOG_ENABLED = false`) muestra un prompt "¿sigues aparcado?" de baja confianza cuando: hay sesión activa + detección idle + **`recentVehicleEnter`** (un `IN_VEHICLE_ENTER` dentro de `vehicleEnterWindowMs`) + posición a > `watchdogFarThresholdMeters` (300 m).

El gate `recentVehicleEnter` **es estructuralmente mudo en el apagón**: el `IN_VEHICLE_ENTER` de cuando el usuario se sube al coche para irse **ocurre con el móvil muerto** → nunca se registra (y además `lastVehicleEnteredAt` es en memoria, se pierde en la muerte del proceso). Sin ese corroborador, el watchdog no avisa. Cubre el EXIT perdido por Doze/OEM (móvil **vivo**), no el apagón.

## Solución propuesta: reconcile-on-resume

Lo único fiable tras un apagón (todo sobrevive en Room): (1) hay sesión activa en A, (2) la posición actual es B, (3) cuánto tiempo llevo sin "dar señales de vida".

1. **Heartbeat persistente.** Guardar en DataStore un `lastAliveAt` (lo pisa el worker periódico / el foreground de detección). Al revivir se calcula el **hueco**; un hueco grande = pudo pasar algo sin que lo viéramos.
2. **Reconcile en dos puntos de "vuelta":** `BootCompletedReceiver` (revive tras recargar) y al abrir la app. Disparo cuando: sesión activa **y** posición actual a > `watchdogFarThresholdMeters` **y** hueco de heartbeat grande.
3. **Nunca auto-liberar → preguntar**, y mejor **in-app al abrir** que como notificación de fondo: *"Tu última plaza: A, hace 6 h. ¿Sigues ahí?"* con **[Sigo aparcado / Ya me fui / Cambié de plaza]**. Baja fricción, solo cuando el usuario ya mira la app.
4. **"Ya me fui" → limpiar la sesión pero NO publicar** a la comunidad (o TTL inmediato + flag "reporte tardío"). Una plaza liberada hace horas es rancia; publicarla tarde es casi peor (fantasma). El estado correcto del usuario pesa más que una plaza comunitaria incierta.

## Cableado que ya existe (reaprovechar)

Solo cambia el **gate** que dispara el prompt; el resto de la tubería ya está:

- `AppNotificationManager.showStillParkedPrompt(...)` → `STILL_PARKED_NOTIFICATION_ID`
- `CoordinatorDetectionService.ACTION_DEPARTURE_CONFIRMED` → `handleWatchdogDeparture(geofenceId)` → `ProcessConfirmedDepartureUseCase` (libera + limpia sesión + quita geocerca + desregistra arming)
- `DetectionHeartbeatWorker` (periódico) y `BootCompletedReceiver` (boot)
- `ParkingDetectionConfig.watchdogFarThresholdMeters = 300f`
- `GetLastKnownLocationUseCase` (passive — no provoca la geocerca)

Falta: `lastAliveAt` persistente, el gate hueco-de-heartbeat + lejanía (sin depender de `vehicleEnterWindowMs`), y el prompt in-app de 3 opciones (hoy solo hay la notificación de 2 acciones).

## El límite que no se puede saltar

*Conduje-y-liberé* vs *andé-y-el-coche-sigue-ahí* es **indistinguible a posteriori** cuando nada registró la salida. Caso incómodo: aparcas → andas a casa (lejos) → el móvil muere en casa → recargas en casa → posición lejana + hueco grande, pero el coche **sigue aparcado**. Por eso **siempre es una pregunta, nunca una acción**.

Mitigadores de falsos prompts (opcionales):
- **Bluetooth emparejado al coche:** un CONNECT/DISCONNECT es señal fuerte de subir/bajar — pero solo si el móvil sobrevivió; en apagón total tampoco ayuda.
- **Proactivo:** en `ACTION_BATTERY_LOW` (~15%) con sesión activa, persistir un marcador "posible salida no observada" que **endurezca** (baje el umbral / suba la prioridad) el prompt al revivir. Marginal pero gratis.

## Asimetría (qué se pierde de verdad)

- **La comunidad no se entera de que A quedó libre** → falso negativo barato y aceptable (mejor no publicar una plaza rancia). Irrecuperable, y está bien.
- **La app muestra "aparcado en A" cuando ya no** → este es el daño real y es lo que arregla el reconcile-on-resume.

## Alcance / tareas

- [ ] `lastAliveAt` en DataStore + escritura desde heartbeat/foreground.
- [ ] Gate de reconcile (sesión activa + lejanía + hueco grande) en `BootCompletedReceiver` y en el arranque de app (HomeViewModel/bootstrap).
- [ ] Prompt **in-app** de 3 opciones (Sigo / Ya me fui / Cambié) además de la notificación existente.
- [ ] "Ya me fui" → `ProcessConfirmedDepartureUseCase` **sin publicar** (o con TTL inmediato + flag tardío) — decidir política de publicación.
- [ ] Marcador proactivo `ACTION_BATTERY_LOW` (opcional).
- [ ] Tests: reconcile dispara con lejanía+hueco; NO dispara con "andé lejos, coche sigue" salvo señal extra; "Ya me fui" limpia sin publicar.
