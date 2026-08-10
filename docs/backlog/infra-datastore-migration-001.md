# INFRA-DATASTORE-MIGRATION-001 — Migrar los stores de scratch de SharedPreferences a DataStore

**Estado:** 📋 backlog (propuesto 2026-08-10). No urgente — limpieza de infraestructura, project-wide.

## Motivación
`SharedPreferences` no está formalmente `@Deprecated`, pero Google recomienda **DataStore** (Jetpack)
para código nuevo. Problemas de SharedPreferences relevantes para nuestra capa de detección:
- `commit()` es síncrono (bloquea el hilo); `apply()` es async pero **se traga los errores**.
- Sin API reactiva (Flow) ni garantías transaccionales.
- En un **SIGKILL** duro (OEM sin whitelist de batería — nuestro escenario de campo), un `apply()`
  pendiente **puede perder las últimas escrituras**. DataStore es transaccional y más robusto.

## Alcance (project-wide, NO puntual)
Migrar TODOS los stores de scratch a la vez para no mezclar dos mecanismos de persistencia:
- `TripTrailImpl` (prefs `trip_trail`) — breadcrumbs one-shot forenses [DET-BREADCRUMBS-001].
- `DrivingRouteStoreImpl` (prefs `driving_route`) — borrador vivo de la ruta [DET-ROUTE-TRACK-001].
- Ancla de posición (`ANCHOR-PERSIST-001`).
- `PendingDetectionStore` — pending durable del arm [DET-NEVER-SILENT-001].
- `ManualParkingDetectionImpl`.
- (Auditar el resto de usos de `getSharedPreferences` en androidMain.)

## Por qué es barato
Las interfaces de dominio ya son **agnósticas al almacenamiento** (`DrivingRouteStore`,
`TripTrail`, … solo exponen `append`/`points`/`clear`/etc.). La migración toca **solo los `…Impl`**
de androidMain; interfaces y consumidores no cambian.

## Consideraciones
- **KMP:** DataStore tiene soporte multiplataforma (`androidx.datastore:datastore-preferences-core`
  en commonMain desde ~1.1) — evaluar si conviene subir estos stores a commonMain de paso (hoy son
  androidMain).
- **Blobs en hot-path:** DataStore reescribe el fichero entero por transacción (igual que
  SharedPreferences). Para el `DrivingRouteStore` (append frecuente de una lista creciente) la
  cadencia está decimada (~cada 12 m), así que reescribir unos KB por append es asumible; si se
  volviera pesado, valorar Room para ese caso concreto.
- **NO afecta al registro permanente:** la ruta definitiva ya vive en Room + Firestore
  (`UserParking.routePolyline`), transaccional y durable — fuera del alcance de este ticket.

## Relacionados
- [[project_route_line_onroad_track_2026_08_08]] · DET-ROUTE-TRACK-001 · DET-BREADCRUMBS-001.
