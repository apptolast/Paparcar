# DET-BT-OWNERSHIP-001 — El Coordinator nunca atribuye una sesión a un vehículo vinculado por Bluetooth

**Estado:** implementado en rama `bugfix/DET-BT-OWNERSHIP-001-coordinator-attribution` (working tree, sin commit) · ⏳ field-test
**Origen:** field-test 2026-08-11 (evidencia Firestore, diagnostics/{uid}/sessions)

## El bug (evidencia Firestore 11-08, certeza absoluta)

El usuario condujo el **Ford Focus** (vehículo ACTIVO, sin BT) durante todo el día. Los **8
aparcamientos** confirmados por el Coordinator se estamparon con el **Skoda Kamiq** (INACTIVO, con
`bluetoothDeviceId`). La cadena:

1. 10-08 — Viaje real en Kamiq (estrategia BT) → sesión aparcada del Kamiq **con geocerca**.
2. 11-08, cada viaje en Focus — cualquier armado (sentry-wake / AR ENTER / geofence exit) toma la
   sesión aparcada del Kamiq como "nominadora": `TripContext(session.location, session.vehicleId)`
   viaja hasta el detector como `nominatingVehicleId = Kamiq`.
3. En el lock del vehicleId (`CoordinatorParkingDetector`, primer fix a velocidad de conducción),
   `VehicleFenceOwnershipPolicy.resolveSessionVehicleId(nominating, active)` era
   `nominatingVehicleId ?: activeVehicleId` → **el nominador SIEMPRE gana al activo** → pin Kamiq.
4. Cada confirm crea una NUEVA sesión Kamiq con su geocerca → el siguiente armado vuelve a nominar
   al Kamiq → **cadena autoalimentada** (8 pins seguidos, todos mal atribuidos).

## La doctrina (sistemas, no parches)

- **El vehículo con `bluetoothDeviceId` pertenece EN EXCLUSIVA a la estrategia Bluetooth.** Su
  identidad solo la establece la MAC (conexión/desconexión ACL) — nunca una geocerca. El
  Coordinator es la estrategia del vehículo ACTIVO.
- **Una geocerca solo prueba que el TELÉFONO salió**, no que ESE coche se moviera (doctrina ya
  asentada en [DET-BT-WRONG-CAR-ABORT-001], `docs/backlog/det-bt-wrong-car-abort-001.md`).
- **El ARMADO no cambia.** Cualquier valla/sentry sigue despertando el Coordinator — si no, un día
  como el 11-08 no se detectaría nada. Lo que cambia es la **ATRIBUCIÓN** en el lock point.

## Cambio

Fix en UN sitio: la política pura `VehicleFenceOwnershipPolicy.resolveSessionVehicleId` recibe
además `nominatingVehicleIsBtPaired: Boolean` y **veta al nominador BT-vinculado** → la atribución
cae al vehículo activo:

```kotlin
nominatingVehicleId.takeUnless { nominatingVehicleIsBtPaired } ?: activeVehicleId
```

En el lock point de `CoordinatorParkingDetector` se resuelve el vehículo nominador UNA vez (barato
cuando es el activo; lookup en la lista de vehículos si difiere — el mismo lookup que ya alimentaba
el `vehicleType`) y se pasa el flag. La línea de log estampa la proveniencia del veto:

```
✓ vehicleId locked: <activo> type=CAR (nominator=<kamiq> vetoed: bt-owned)
```

Si el veto deja la resolución en `null` (nominador BT + sin activo), la sesión aborta
(`aborted_no_vehicle`) con el motivo en el log — mejor falso negativo que pin mal atribuido.

## Decisiones conscientes

- **Armado intacto.** El veto vive SOLO en el lock de atribución. Los lanes del service
  (`CoordinatorDetectionService`) y el `TripContext` no se tocan: el nominador sigue viajando como
  hipótesis, que la arbitración BT del receiver necesita (`sameVehicle(btCar, coordinatorVehicleId)`).
- **Activo BT-vinculado se mantiene.** Si el vehículo ACTIVO es él mismo el nominador BT-vinculado,
  el veto cae de vuelta sobre el mismo id → atribución sin cambio. Elección explícita del usuario
  (declaró conducir ese coche); mejor un pin posiblemente redundante que un parking perdido.
- **Arbitración BT positiva intacta.** `EvaluateBtArbitrationUseCase` (supersede / yield /
  veto-return) no cambia: los edges ACL siguen mandando sobre la sesión del Coordinator. Este
  ticket solo cierra el camino NEGATIVO (atribuir sin evidencia MAC).

## Ficheros

- `domain/detection/VehicleFenceOwnershipPolicy.kt` — parámetro `nominatingVehicleIsBtPaired` +
  KDoc con la doctrina.
- `domain/coordinator/CoordinatorParkingDetector.kt` — lock point: resolución del nominador,
  flag a la política, proveniencia del veto en logs (lock + abort).
- `commonTest/.../VehicleFenceOwnershipPolicyTest.kt` — veto → activo; veto sin activo → null;
  nominador no-BT sigue ganando (VEH-ACTIVE-FENCE-001 intacto); activo-BT conserva atribución.
- `commonTest/.../CoordinatorParkingDetectorTest.kt` — replay del caso de campo 11-08 (activo v-1,
  nominador BT-paired) → sesión guardada con el activo.
- `docs/detection/PARKING-DETECTION.md` §1.1 — regla de atribución BT-aware en el lock.

## Pendiente

- [ ] Commit + merge a master (squash) con go-ahead del user.
- [ ] Field-test: día completo en Focus con el Kamiq aparcado con geocerca → todos los pins deben
      ser Focus con `nominator=<kamiq> vetoed: bt-owned` en parkdiag.
- [ ] Revisar en Firestore/manual las 8 sesiones Kamiq mal atribuidas del 11-08.
