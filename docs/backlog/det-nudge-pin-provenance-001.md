# DET-NUDGE-PIN-PROVENANCE-001 — El pin confirmado desde el nudge de detección se estampa como plaza manual

**Estado:** ✅ EN MASTER (`cac6f73d`) · campo cubierto por la validación hasta `1a4128d5` (23-08-2026).
Rama y worktree `../Paparcar-nudgeprovenance` ya no existen.
**Fecha:** 2026-08-10

## Problema (observado en campo, viaje 07/08-08 Cañada del Real Tesoro)

Cuando la detección no puede autoconfirmar y degrada a nudge ("¿Dónde has dejado el coche?" /
"Marcar mi plaza" — notificación o fila del sheet), el usuario responde colocando el pin, y ese
flujo entra por el modo pin manual (`EnterAddParkingMode` → `SaveManualParkingUseCase` modo pin),
que estampa **siempre** `SpotType.MANUAL_REPORT` + `detectionPath="manual"`.

Consecuencia: al salir (aunque la salida SÍ la detecte el sistema), la plaza publicada a la
comunidad hereda `MANUAL_REPORT` → badge azul "Manual" + **TTL 15 min** (vs 2 h la auto). Todos
los aparcamientos asistidos-por-nudge mueren pronto y se leen como manuales. Verificado en
Firestore: sesiones 296b1018 (08-08) y bc10cc94 (08-05) con `detectionPath: "manual"` publicaron
plazas `MANUAL_REPORT` conf 1.0; las sesiones `bt`/`kinematic+egress` publican `AUTO_DETECTED` bien.

Asimetría existente: el prompt in-app "Sí, he aparcado" (`SaveManualParkingUseCase.confirmDetected`)
ya estampa `AUTO_DETECTED` + `detectionPath="user"`. El nudge —que también nace de la detección y
también es ground truth del usuario— no.

## Fix (sistema, un solo invariante)

El origen del modo pin viaja con la entrada; el use case estampa según origen:

| Entrada al modo pin | Origen | spotType | detectionPath |
|---|---|---|---|
| Notificación `showMarkParkingNudge` (coordinator unattended / honest-close / safety-net) | detección | `AUTO_DETECTED` | `nudge` |
| Fila nudge del sheet (`onMarkNudgeSpot`, DET-NUDGE-PERSIST-001) | detección | `AUTO_DETECTED` | `nudge` |
| Notificación `showFirstParkNudge` (onboarding) | usuario | `MANUAL_REPORT` | `manual` |
| CTA cold-start "Marcar mi plaza" del detection story | usuario | `MANUAL_REPORT` | `manual` |
| Pill "Aparcar" de la card de vehículo | usuario | `MANUAL_REPORT` | `manual` |
| Lápiz de edición (corregir mantiene sesión; re-park nueva) | usuario | (sin cambio) | (sin cambio) |

La fiabilidad sigue 1.0 (ground truth del usuario) y el `sealPoint` se mantiene.

## Piezas

1. `StartAddParkingEventBus` — la request lleva `fromDetectionNudge`.
2. `AppNotificationManagerImpl.buildAddParkingIntent(requestCode, fromDetection)` + extra nuevo en
   `MainActivity` → bus.
3. `HomeIntent.EnterAddParkingMode.fromDetectionNudge` (default false) + campo mode-scoped en
   `HomeState` (limpiado en `clearedModeFields()`).
4. `HomeViewModel.confirmAddParking` → `SaveManualParkingUseCase(fromDetectionNudge=…)`.
5. `SaveManualParkingUseCase` — estampa `AUTO_DETECTED`+`"nudge"` cuando viene del nudge.
6. Tests: `SaveManualParkingUseCaseTest` (nudge→AUTO+path nudge; pin espontáneo→MANUAL intacto) +
   `HomeViewModelTest` (flag viaja del intent al use case).
7. `docs/detection/PARKING-DETECTION.md` — tabla de provenance actualizada.

## Checklist

- [x] Implementación
- [x] Tests verdes (`testProdDebugUnitTest`: SaveManualParkingUseCaseTest 7/7, HomeViewModelTest 66/66, guardrails incluidos)
- [x] `assembleMockDebug` verde
- [x] Doc detección actualizado (`docs/detection/PARKING-DETECTION.md`, entrada 2026-08-10)
- [ ] Field-test: responder un nudge y verificar en Firestore `spotType=AUTO_DETECTED`, `detectionPath=nudge`, y al salir plaza AUTO con TTL 2 h
