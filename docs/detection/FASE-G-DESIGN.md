# Fase G — Diseño: trigger por departure + UI de armado en frío

> Estado: **diseño, sin implementar.** Fase G es la única que cambia *comportamiento de usuario*
> (cuándo se arma la detección) y *UI*, y tiene una dependencia dura de device (FGS background-start).
> Este doc la deja lista para implementar tras el field test. Rama `refactor/DET-001-...`.

## Por qué G se trata distinto al resto del plan
- **Sus beneficios de bug ya están entregados.** El FP de Praga lo mata el gate de egreso (Fase A);
  el FGS huérfano lo cierran los guards de Fase B. La "muerte de Praga por construcción" que motiva
  D-5 es una **defensa estructural redundante**, no un fix pendiente. → urgencia baja.
- **Acopla motor + UI.** G-01 (motor) no se puede desplegar sin G-02 (UI) sin **regresar la
  detección del primer aparcamiento** (ver abajo).
- **Cambia comportamiento sobre un motor aún sin validar en campo.** A/C/D/E están verdes en tests
  pero no probados con hardware real todavía.

---

## DET-G-01 — Armado por departure confirmado (motor)

### Flujo actual
`AR IN_VEHICLE_ENTER` → `CoordinatorDetectionService(ACTION_VEHICLE_TRANSITION)` →
`HandleVehicleTransitionUseCase.handleEnter` → (a) `departureEventBus.onVehicleEntered(t)` +
(b) resuelve estrategia → si COORDINATOR → `StartCoordinatorDetection` → arranca el coordinator.

### Flujo objetivo (D-5)
`UserParking` activa → `GEOFENCE_EXIT` → `DepartureDetectionWorker` → `DetectParkingDepartureUseCase`
→ si `Confirmed`: libera plaza **Y arma la detección del próximo aparcamiento**. AR ENTER deja de
ser el trigger de armado.
- Sin plaza previa → sin geocerca → sin departure → **sin armado** → un AR ENTER espurio (Bolt en
  Praga) no arranca sesión. Esa es la "muerte por construcción".

### Los dos cambios y sus riesgos
1. **(a) Departure-confirmado arma detección.** En `DepartureDetectionWorker`, tras
   `processConfirmedDeparture(...)` exitoso, arrancar `CoordinatorDetectionService(ACTION_START_TRACKING)`
   — **respetando estrategia** (solo si `ParkingStrategyResolver.resolve() == COORDINATOR`; BT se
   arma por su disconnect; NONE no se arma).
   - ⚠️ **BLOQUEANTE DEVICE:** arrancar un FGS de tipo `location` desde el worker (background) en
     Android 12+ puede lanzar `ForegroundServiceStartNotAllowedException`. Opciones a evaluar:
     - **A.** Verificar en device si la entrega de geocerca/worker concede la exención (probable que NO).
     - **B.** No arrancar FGS desde el worker: armar vía un **estado persistido** ("detección
       pendiente") que el siguiente AR signal o un `JobScheduler`/heartbeat con exención promueva.
     - **C.** Mantener el armado por AR ENTER como *mecanismo de arranque del FGS* pero **gatear** el
       arranque por "existe-departure-reciente-o-plaza-previa" → recupera el beneficio Praga sin
       depender de un FGS-desde-worker. **(Recomendada: menos riesgo, reusa el arranque exento de AR.)**
2. **(b) AR ENTER deja de armar.** Quitar `StartCoordinatorDetection` de `handleEnter`. **Conservar
   `departureEventBus.onVehicleEntered(t)`** — la ventana de departure lo necesita.
   - ⚠️ **REGRESIÓN sin G-02:** el primer park de la vida del usuario (y tras cualquier liberación sin
     nuevo park) no tiene geocerca → nunca se arma. La UI de G-02 es la que bootstrapea ese caso.

### Opción recomendada para G-01 (resuelve el bloqueante)
**Opción C**: el FGS lo sigue arrancando AR ENTER (contexto exento), pero el coordinator solo
**confirma/persiste** si hay corroboración de "venimos de una salida real" — es decir, mover el gate
de "Praga por construcción" del *arranque del FGS* al *gate de sesión*. En la práctica, gran parte ya
está hecho: el gate de egreso (A) + el `maxNoMovementMs` ya descartan el AR ENTER espurio. La pieza
nueva sería pequeña: marcar la sesión como "armada por departure" cuando venga de un GEOFENCE_EXIT
confirmado, y exigir esa marca (o cold-start manual) para auto-confirmar. **Decisión a tomar con el
field test:** ¿hace falta (b) si A+B+C ya cubren Praga y el FGS? Posiblemente G-01 se reduzca a "(a)
opción C" o incluso se cierre como "ya cubierto".

### Tests (cuando se implemente)
- `DepartureDetectionWorker`: departure confirmado + estrategia COORDINATOR → intenta armar; BT/NONE → no.
- Estrategia-aware: BT-paired → no arma coordinator.
- Cold-start: sin geocerca previa → no arma (y G-02 cubre el bootstrap).

---

## DET-G-02 — UI de armado en frío + indicador de detección activa

### Problema
La geocerca solo existe si hay `UserParking` activa (la crea `ConfirmParkingUseCase`). Si el usuario
**no tiene plaza aparcada ni detección activa**, no hay forma de que ocurra una salida por geocerca →
el flujo de G-01 nunca arranca. La UI debe darle una forma de **registrar "he aparcado aquí"**.

### Infra que YA existe (reusar, no reinventar)
- `HomeMode.AddingParking` + `ParkingCenterPin` + `UpdateParkingLocationUseCase` + botón "Mover
  ubicación" (shipped, ADD-PARKING-PIN). Es el flujo manual de marcar plaza.
- `VehicleMonitoringStatus` (ARCH-MONITORING-002) — estado de monitorización por vehículo, ya con
  sitios de UI. Candidato para el indicador "detección activa".

### Estados de UI a definir
1. **Sin plaza + sin detección activa** → affordance prominente "He aparcado aquí" que entra en
   `HomeMode.AddingParking` → confirma → crea `UserParking` + geocerca → habilita la salida por geocerca.
2. **Detección activa** (coordinator/BT corriendo) → indicador no intrusivo ("Detectando
   aparcamiento…") vía `VehicleMonitoringStatus`. Decidir dónde: ¿chip en Home? ¿estado en Mi Coche?
3. **Plaza aparcada** → ya cubierto (card de plaza + "Mover ubicación").

### Preguntas que el field test debe responder antes de diseñar la UI
- ¿Con qué frecuencia los usuarios caen en el estado "sin plaza + sin detección" (primer uso, tras
  liberar)? → define cuán prominente debe ser el affordance.
- ¿La detección activa debe ser visible siempre o solo cuando hay incertidumbre? → el log remoto dirá
  cuántas sesiones acaban en `Inconclusive`/prompt.

---

## Orden de implementación (post-field-test)
1. Field test → confirmar si (b) hace falta o A+B+C ya cubren Praga/FGS.
2. Resolver el bloqueante FGS-background-start (probar opción C primero).
3. DET-G-01 (la variante que sobreviva a 1–2) + tests.
4. DET-G-02: surfacing del affordance manual (reusa AddingParking) + indicador de detección activa.

## Dependencias
- G-01 y G-02 son interdependientes: no desplegar (b) de G-01 sin G-02.
- Ambos dependen de A/B/C/D/E validados en campo.
