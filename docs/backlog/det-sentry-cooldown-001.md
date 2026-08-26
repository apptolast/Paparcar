# DET-SENTRY-COOLDOWN-001 — amortiguador de tormentas del sentry-wake: paseos seguidos refutados enfrían el re-armado del sensor

**Estado:** ✅ EN MASTER (`eecef415`) · campo cubierto por la validación hasta `1a4128d5` (23-08-2026).
Entró con ID propio, no bajo `DET-WALKOUT-FP-001` — esa rama nunca llegó a master con ese nombre.

> El amortiguador se corrigió después: `4d1d6716` [DET-COOLDOWN-MUST-NOT-BLIND-A-DRIVE-001] impide
> que la tormenta silencie al último nominador en pie. Hacen falta **las dos** puertas.
**Origen:** "ticket 4" pendiente del field 11-08, reactivado con urgencia por el field 13-08:
la tormenta es además la que compró la lotería del FP de
[DET-UNVERIFIED-CONFIRM-001](det-unverified-confirm-001.md).

## Evidencia de campo 13-08 (telemetría pap-26, uid `fiyp…`)

El sensor de movimiento significativo no distingue paseo de conducción: con el servicio residente
en CENTINELA, cada zancada re-dispara el ciclo *wake → armar Coordinator → abortar `false_enter`
(~12–30 s) → re-entrar CENTINELA → re-armar sensor → wake…*

- **20:27–20:38 UTC** (paseo de vuelta): una sesión armada-y-abortada cada **~18 s**, sin pausa.
- **21:27–21:56 UTC** (ya en casa): siguen los wakes (movimiento del móvil junto a la valla).
- Total del día: **≈130 sesiones** `aborted_false_enter` de arm `SIGNIFICANT_MOTION`.

Coste: batería (GPS + FGS activo a cada ciclo), inundación de telemetría (páginas enteras de
`diagnostics/…/sessions`), y — lo grave — **cada wake es un billete de lotería nuevo** para que un
primer fix de GPS frío con espejismo Doppler convierta el paseo en "conducción medida" (así nació
el FP del 13-08).

## Diseño implementado

**Política pura en commonMain** (`domain/detection/SentryWakeCooldown.kt`, testeable):

- `nextSentryWakeAbortStreak(prev, armedBySentryWake, outcome)` — reducer de la racha: solo un
  arm de sentry-wake refutado como aborto de paseo (`aborted_false_enter` / `aborted_no_movement`,
  labels compartidos en `DetectionSessionOutcomes`) la extiende; cualquier otra sesión terminada
  (otro trigger, confirm, prompt, outcome desconocido) la resetea.
- `sentryWakeRearmCooldownMs(streak, config)` — 0 por debajo del umbral
  (`sentryWakeAbortStreakForCooldown=3`: los primeros wakes de una salida real nunca se retrasan);
  desde el umbral, base 3 min doblando por cada refutación extra, techo 15 min
  (`sentryWakeCooldownBaseMs`/`MaxMs`).

**Enforcement en el monitor** (`SignificantMotionMonitor.applyRearmCooldown` + `sync`): el
deadline vive DENTRO del monitor porque tres espejos independientes llaman a `sync()` (epílogo del
service, tick del safety-net worker, heartbeat exacto) — un cooldown que cualquiera pudiera
saltarse no sería un cooldown. Con la pausa activa `sync(true)` se suprime (log + notif debug);
el primer espejo que pase el deadline re-arma solo. Desarmar se respeta siempre.

**Pliegue de la racha** (`CoordinatorDetectionService.resolveIdleEpilogue`): el service recuerda
qué trigger armó la sesión (`lastEndedArmTrigger`, consumido con null-out → un pliegue por sesión
aunque el epílogo tenga dos call sites) y entrega el cooldown al monitor ANTES de que
`enterSentry` re-arme. Telemetría: evento `SENTRY wake_cooldown` con `streak` y duración — la
explicación de campo de por qué la cadencia de arms se corta de golpe.

**Contrato de detección preservado** (⛔ todo trigger dispara siempre): durante la pausa solo
duerme el NOMINADOR de movimiento significativo — la geocerca EXIT (PendingIntent, funciona con
proceso muerto), el carril AR ENTER y la red de seguridad periódica siguen vigilando; una salida
real en mitad del cooldown se captura igual, solo se pierde el carril de inmediatez. Estado en
memoria a propósito: la tormenta solo existe con proceso residente vivo; una muerte de proceso
resetea racha y pausa a la vez.

Con la tormenta del 13-08: ~5–6 wakes evaluados en 40 min en vez de ~130.

## Tests

`SentryWakeCooldownTest` (9): reducer (extiende con los dos abortos de paseo; resetea con confirm,
con otro trigger y con outcome nulo) + mapping (0 bajo umbral, base en umbral, doblado, techo sin
overflow). Config validada con `require()`s.

Relacionado: [DET-UNVERIFIED-CONFIRM-001](det-unverified-confirm-001.md),
[DET-RESIDENT-FGS-001], [DET-SIGMOTION-001].
