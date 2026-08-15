# ROUTE-PASSIVE-FILL-001 · Tap pasivo de localización: heredar gratis los fixes de otras apps

**Estado:** ✅ Done · master `9625c0ed` (ff-only, 15-08-2026) · rama y worktree borrados · ⏳ field con nav app activa

## Problema
Field 14-08: MIUI durmió el GPS de NUESTRA petición 7 minutos en plena conducción (Redmi, vuelta a
Valdelagrana) → agujero de 4,6 km en la ruta grabada que el matcher tuvo que reconstruir
(ROUTE-GAP-HONEST-001). Si el usuario lleva Waze/Maps navegando — lo habitual conduciendo — esos
fixes existen en el dispositivo, pero no los aprovechábamos.

## Doctrina violada
Ninguna — es una mejora de densidad de datos a coste cero. La restricción que SÍ se respeta:
los fixes pasivos alimentan SOLO la ruta persistida (`DrivingRouteStore.append`, con su gate de
accuracy y decimación), NUNCA el stream de decisión del Coordinator (las decisiones de detección
siguen sobre su propio stream medido).

## Diseño
- `LocationDataSource.observePassiveLocation()` — `PRIORITY_PASSIVE` del FusedLocationProvider:
  recibe los fixes que piden OTRAS apps, sin disparar muestreo propio (cero batería, no puede
  provocar geocercas ni alimentar el power-abuse scoring del OEM). Silencioso si nadie más pide.
- iOS: CoreLocation no tiene proveedor pasivo → `emptyFlow()` por diseño.
- `CoordinatorDetectionService`: `passiveRouteTap` = launch hijo del job de detección que colecta
  el stream pasivo → `drivingRouteStore.append`; cancelado en el mismo `finally` que el heartbeat.

## Criterio de éxito
- Compila prod+mock; tests verdes (sin lógica nueva testeable en puro — el gate de accuracy y la
  decimación que absorben estos fixes ya están testeados en `DrivingRouteTest`).
- Campo: repetir un viaje con Maps/Waze navegando en el Redmi → la ruta grabada sin agujeros
  aunque MIUI duerma nuestra petición.

## Consumidores auditados
- `LocationDataSource` implementaciones: Android (real), iOS (emptyFlow), FakeLocationDataSource
  (commonMain mock) y FakeLocationDataSource (commonTest) — las 4 actualizadas. ✔
- El stream pasivo NO entra en `observeAdaptiveLocation` ni en el coordinator (grep: único consumer
  = passiveRouteTap del servicio). ✔
