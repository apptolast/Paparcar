# DET-STRATEGY-GATE-001 — La estrategia se decide en UN punto: con Bluetooth, el Coordinator no arma y el centinela no se queda residente

> **Estado:** ✅ EN MASTER 75614053 (2026-08-06, rebasado sobre F3 cab6e703 + ff-only; firma
> combinada `resolvePostDetectionLifecycle(autoDetectEnabled, hasParkedSession, strategy)`).
> ⚠️ NO está en el APK de campo instalado el 06-08 (solo lleva el fix del receiver, a propósito):
> el próximo APK de campo desde master lo incluirá — generarlo SOLO tras validar en device
> DET-BT-RECEIVER-EXPORT-001 (con el receiver BT muerto, este gate = FN silencioso total).
> "Gate" = puerta única de admisión: una función pura que decide si un trigger puede armar.

## Problema (field 2026-08-01 → 04, Oppo + Skoda Kamiq BT)

`ParkingStrategyResolver` resolvía BLUETOOTH correctamente, pero solo el carril GEOFENCE_EXIT lo
consultaba. `handleSentryWake()` (sentry sigmotion) y `handleArTransition()` (AR ENTER) armaban el
Coordinator igualmente → todos los pins AUTO_DETECTED del 01–04/08 se atribuyeron al Ford Focus
(primario) aunque se condujera el Kamiq. Es la misatribución exacta que ARCH-MONITORING-002
justifica suprimir. Además, el centinela residente (DET-RESIDENT-FGS-001) se quedaba vivo también
con estrategia BT, donde no aporta: el broadcast ACL despierta el proceso por sí solo (receiver de
manifest, exento de la restricción FGS-from-background de Android 12+).

## Cambios

1. **`coordinatorMayArm(strategy, trigger)`** — regla pura nueva en `ParkingStrategyResolver.kt`
   (commonMain): MANUAL siempre admitido (intención explícita del usuario + handoff de llegada del
   safety-net); cualquier trigger automático solo bajo COORDINATOR.
2. **Gate central** en `CoordinatorDetectionService.startParkingDetection()` (ahora `suspend`, igual
   que `handleStartTracking`): todo armado automático pasa por la regla; rechazo con traza PARKDIAG
   `⊘ arm refused — strategy=… [DET-STRATEGY-GATE-001]`. El carril EXIT conserva su short-circuit
   temprano (mismo resultado, se ahorra el trabajo de pre-arm).
3. **Residencia por estrategia**: `resolvePostDetectionLifecycle(sentryEnabled, hasParkedSession,
   strategy)` — EnterSentry solo con COORDINATOR. El epílogo re-resuelve la estrategia en cada
   ciclo → emparejar/desemparejar BT o apagar el adaptador se auto-corrige en el siguiente fin de
   detección (sin observadores nuevos).
4. Tests: 6 casos de residencia (`SentryLifecycleDecisionTest`) + 4 del gate
   (`ParkingStrategyResolverTest`). Suite completa verde.
5. Docs: §1.1 de `docs/detection/PARKING-DETECTION.md` (gate + residencia por estrategia).

## Decisiones asumidas (explícitas)

- **Flota mixta** (Focus sin BT + Kamiq con BT, adaptador ON): estrategia = BLUETOOTH para toda la
  flota → conduciendo el Focus NO hay detección automática (queda nudge/manual). Es la decisión
  ARCH-MONITORING-002 ya tomada (correr el Coordinator en paralelo atribuye el viaje del coche BT
  al primario — demostrado en campo 01-08). Refinamiento futuro posible ahora que el receiver vive:
  usar el sello de `BtConnectionStore` (CONNECT reciente = "vas en el coche BT") para decidir POR
  VIAJE en vez de por flota. Ticketizar aparte si el field-test de flota mixta duele.
- **Safety-net `DispatchDeparture` → `manualParkingDetection.start()`** entra como MANUAL y salta el
  gate a propósito: si el coche era el BT, la arbitración del disconnect supersede la sesión; si el
  FGS no puede arrancar, ya cae al prompt "¿sigues aparcado?". No se toca.
- El **safety-net/reconcile sigue siendo strategy-independent** (contrato: verificación tardía
  siempre); este ticket solo gobierna el ARMADO del coordinator y la RESIDENCIA del centinela.

## Validación pendiente

- [ ] Device: con Kamiq emparejado y adaptador ON, sigmotion/AR NO arman (`⊘ arm refused` en
      parkdiag) y el servicio NO queda residente tras el fin de detección.
- [ ] Device: sin vehículo BT (o adaptador OFF), conducta idéntica a la rama SENTRY (residente).
- [ ] Field: viaje real en Kamiq con receiver ya arreglado → pin `path=bt` del Kamiq, cero sesión
      coordinator paralela.
- [ ] Al mergear: rebase sobre DET-RESIDENT-FGS-001 (F3) y resolver el posible conflicto con el
      trabajo en curso de `det-stop-button-001` (toca `SentryLifecycleDecision.kt`).
