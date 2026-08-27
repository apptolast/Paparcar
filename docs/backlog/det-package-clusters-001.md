# DET-PACKAGE-CLUSTERS-001 · el paquete detection agrupa por rol lo que consumía el servicio

**Estado:** ✅ Done · **1.708 tests, 0 fallos** (prod y mock) · 66 ficheros, +135/−103, solo `package`/imports/renames · raíz 37 → 20 · addendum 27-08 en `09-arquitectura-objetivo.md`

## Problema

`domain/detection/` tenía **37 ficheros en la raíz** además de `physics/` · `stages/` · `state/`.
El núcleo de sesión (coordinator, ejecutor, dispatcher, tap) convivía plano con tres clústeres que
responden a preguntas distintas y tienen consumidores distintos — el censo por capa (androidMain /
domain / ui, medido sobre `ab610847`) los separa solo:

| Clúster | Consumidor dominante | Pregunta que responde |
|---|---|---|
| Veredictos de wake/ciclo de vida del sentry | androidMain (service/receivers/workers) | "¿me despierto / me mato / superseído / ledger?" |
| Política de cercas | androidMain + geofence manager | "¿de quién es esta cerca y qué registro?" |
| Puertos que implementa la plataforma | androidMain (Impl) | contrato KMP puro, implementación Android |

## Diseño

Tres subpaquetes nuevos; la raíz queda con el núcleo de sesión + el vocabulario transversal que
leen UI y domain (`ParkingDetectionSource`, `ParkingStrategyResolver`, `DetectionRuntimeState`,
`DetectionTrigger`, `PendingParkNudge`, `PendingPromptWindow`, `DepartureProof`,
`DepartureSpeedProof`, `HumanPoweredRide`, `AssertedPinAuthority`, `DrivingRoute`…). La raíz baja
de 37 a 20 ficheros.

```
domain/detection/
├─ sentry/  (9)  SentryWakeCooldown · SentryWakeTriage · SentryLifecycleDecision ·
│                SessionSupersede · TriggerLedger · UserStopQuietPeriod ·
│                GhostFgsReapDecision · ExactHeartbeatHealth · PendingNudgeDecision
├─ fence/   (3)  FenceRegistrationPolicy · GeofenceRegistrationFailure ·
│                VehicleFenceOwnershipPolicy
├─ ports/   (5)  ArrivalHandoffDetection · DepartureWatchResumer · ManualParkingDetection ·
│                DrivingRouteStore · TripTrail
└─ physics/ · stages/ · state/ · raíz  — sin cambios
```

Cero conducta: solo `package` + imports. Los tests espejo de `commonTest` se mueven a los mismos
subpaquetes. `docs/detection/09-arquitectura-objetivo.md §3` marcaba estos ficheros `=` (sin cambio
de casa): este ticket lo enmienda con un addendum, no lo contradice en silencio.

## Por qué AHORA es barato

A 27-08 no queda **ninguna rama de detección sin mergear** (las 4 de los worktrees entraron hoy en
master); la única viva que toca detección es `IOS-F0-001`, bloqueada post-beta — el coste de rebase
del reorg es mínimo hoy y no lo será cuando vuelva a haber ramas.

## Criterio de éxito

Suite completa con los mismos tests que master, `compileProdDebugKotlinAndroid` +
`compileMockDebugKotlinAndroid` verdes, y el diff sin una sola línea que no sea `package`, un
`import` o un rename de fichero.

## Consumidores auditados

- Imports en androidMain / commonMain / iosMain / tests → sed mecánico por símbolo (lista cerrada de
  31 símbolos exportados por los 17 ficheros).
- Referencias sin import (mismo paquete hoy): `CoordinatorParkingDetector` → `inheritedArmEvidence`
  (sentry) y `DetectionEffectDispatcher` → `VehicleFenceOwnershipPolicy` (fence) ganan import; las
  demás coincidencias en raíz son KDoc (no compilan, no rompen).
- Los ficheros movidos que usaban símbolos de raíz sin import (`ArmEvidence`, `DetectionTrigger`,
  `ParkingStrategy`, `ServicePresence`) ganan el import inverso.
- Guardrails: `TriggerLaneGuardrailTest` importa `TriggerDisposition` → sed lo cubre;
  `StagePurityGuardrailTest` ancla su regex en `stages/`, que no se mueve;
  `HoldLaneGuardrailTest` importa `HoldAction`, que queda en raíz.
