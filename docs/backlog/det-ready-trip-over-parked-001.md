# DET-READY-TRIP-OVER-PARKED-001 · Un segundo coche aparcado tapaba toda la UI del viaje en vivo

**Estado:** 🟡 Commiteado en rama, **sin mergear** · rama `bugfix/UI-PREFERRED-SESSION-RECENCY-001-preferred-session-recency` · worktree `../Paparcar-preferred-session`

> ⚠️ Comparte un **commit único** con [UI-PREFERRED-SESSION-RECENCY-001](ui-preferred-session-recency-001.md):
> este ticket nació de aquel y reutiliza su resolver, y se decidió cerrarlos en un solo squash.
**Rebase 16-08 sobre master `4400a583`** (que ya traía ROUTE-PASSIVE-FILL-001 y
DET-SHORT-HOP-PROOF-001): un único conflicto, en `docs/detection/PARKING-DETECTION.md`, porque los
dos lados añadían su entrada al final del log. Se conservan ambas en orden cronológico
(DET-SHORT-HOP-PROOF-001 14-08 → este 15-08). **Cero conflictos de código**: `DET-SHORT-HOP-PROOF-001`
toca la prueba de conducción por desplazamiento, no la readiness.

Suite completa verde tras el rebase (**1176 tests**, 0 fallos — los 11 nuevos vienen de master),
mock y prod compilando, tests del coordinator verdes. ⏳ Pendiente: field-test con los dos coches
(salir con el Focus teniendo el Kamiq aparcado → puck, ruta y cámara siguiendo).

## Problema
Field 15-08, dos coches dados de alta (Kamiq con BT + Focus sin BT): *"cuando me voy la detección va
bien pero no se muestra correctamente en UI: se publica aparcamiento y eso, pero no aparece en ruta
ni nada de eso"*. Durante todo el trayecto Home se quedaba visualmente parada — sin puck, sin
migas, sin línea de ruta, sin cámara siguiendo, sin relato "en ruta" — mientras la detección hacía
su trabajo perfectamente (plaza publicada, pin correcto, ruta persistida en la sesión).

## Doctrina violada
Ninguna de detección: no se toca ninguna vía de confirmación. Lo violado es **"sistemas, no
parches"** — una suposición de sesión única que `MULTI-PARKING-001` invalidó y que nunca se barrió.

## Señales / datos disponibles
- `DetectionRuntimeState.isRunning` — hay un job de tracking vivo.
- `DetectionRuntimeState.trip` (`TripContext`) — **incluye `departingVehicleId`**. Verificado: las
  4 vías de armado automático (geofence EXIT, AR-enter-en-valla, AR-enter-con-valla-rota, sentry
  wake) construyen `TripContext(session.location, session.vehicleId)`. Solo el armado MANUAL va sin.
- `observeActiveSessions()` — 0..N sesiones, una por coche aparcado.

## Diseño
`ObserveDetectionReadinessUseCase` resolvía *Disabled → Blocked → **Parked** → Monitoring → Ready*,
y `Parked` se disparaba con **cualquier** sesión activa. Con dos coches, la sesión del Kamiq no se
vaciaba nunca al salir con el Focus → `Parked` ganaba siempre → `Monitoring` era **inalcanzable**.
Y `HomeTripController` solo suscribe el stream de localización con `Monitoring` (a propósito, para
acotar batería), así que sin `Monitoring` no hay puck → sin puck no hay trail, ni map-matching, ni
driver-follow. El servicio de detección nunca consulta este use case: por eso detectaba bien.

Nueva precedencia: *Disabled → Blocked → **Monitoring** → Parked → Ready*, pero preguntando por el
coche **seguido**, no por "¿hay algo aparcado?":

```kotlin
val trackedVehicleId = trip?.departingVehicleId
val trackedCarStillParked = trackedVehicleId == null || sessions.any { it.vehicleId == trackedVehicleId }
val followingTrip = isRunning && !trackedCarStillParked
```

Sigue cumpliendo *el evento nomina, el movimiento medido confirma*: un armado **en** el coche (AR
ENTER esperando prueba de viaje) deja su sesión viva → sigue leyendo `Parked` y no canta un viaje
sin movimiento probado. Solo una salida **confirmada** limpia la sesión y desbloquea `Monitoring`.

**Barrido asociado**: tres sitios respondían distinto a "qué coche aparcado me representa". Ahora
comparten `preferredParkingSession()` (dominio, puro) — más reciente gana, rango de vigilancia solo
desempata. Se retira a propósito el "preferir sesión con geocerca": hacía que el badge honesto
describiera un coche que el user ni miraba.

## Criterio de éxito
- Test: dos coches, uno aparcado y el seguido ya salido → `Monitoring`.
- Test: el coche seguido aún con sesión viva → sigue `Parked` (no se canta viaje sin salida).
- Test: viaje sin `departingVehicleId` → sigue `Parked` (no adivinamos).
- Test: payload de `Parked` = sesión más reciente.
- En campo: salir con el Focus con el Kamiq aparcado → puck, ruta dibujada y cámara siguiendo.

## Consumidores auditados
`DetectionReadiness.Parked` / `.Monitoring` y sus proyecciones:
- `DetectionUiState.toUiState()` → mapea 1:1 ambos estados → **cubierto por convergencia**.
- `DetectionStory` (`Monitoring → drivingStory()`, `Parked → …`) → el relato "en ruta" ya existía y
  simplemente nunca se alcanzaba → **cerrado sin cambio** (sin strings nuevos).
- `DetectionUiState.isDetectionWorking` → incluye Monitoring **y** Parked, así que la transición
  Parked→Monitoring no dispara el snackbar de "detección detenida" → **exento con razón**.
- `HomeState.parkedWatchBadge` → keyed en `DetectionUiState.Parked`; durante el viaje pasa a null.
  Correcto: el badge habla de la vigilancia de un coche aparcado, y conduciendo no hay salida que
  vigilar → **cerrado, cambio de comportamiento deseado**.
- `HomeTripController` (`as? Monitoring`) → el consumidor que estaba muerto → **cerrado**.
- `HomeUiController.centerInitialFocus` / `refocusOnParkingArrival` → se inhiben con puck vivo para
  no abrir el mapa en el otro coche y saltar después → **cerrado**.
- `FakeUserParkingRepository` (mock) → soltaba TODAS las sesiones para alcanzar Monitoring; ahora
  suelta solo la del coche que sale → **cerrado, y el Dev Catalog reproduce el caso de campo**.
- Servicio de detección / workers / evaluadores → no consultan este use case → **exento con razón**.

## Provenance
Sin camino de confirmación nuevo: `detectionPath` / `armEvidence` intactos. Este ticket cambia lo
que Home **muestra** de un viaje, nunca lo que confirma una plaza.
