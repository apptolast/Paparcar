# UI-BROWSE-DRIVING-OVER-PARKED-001 · El coche monitorizado gana al aparcado como sujeto de Browse

**Estado:** ✅ Done · MASTER `3c2f5769` (ff-only tras rebase sobre `66c25d64`, 18-08, sin pushear;
rama y worktree borrados) · 1222 tests verdes (4 nuevos) + `compileMockDebug` OK ·
⏳ validar en campo (conducir el Focus con el Kamiq aparcado)

## Problema
Field 17-08 (Chema→Balsa): conduciendo el Ford Focus con el Skoda Kamiq aparcado, el peek de
Browse mostraba "SKODA KAMIQ · APARCADO" en vez del viaje vivo del Focus. Dos superficies fallan:

1. **Peek de Browse** (`BrowsePeek.kt`): la rama `parking != null` se evalúa ANTES que la rama
   `drivingMeta != null` y hace `return` — con cualquier coche aparcado, el viaje vivo nunca se
   pinta.
2. **Row de chips de vehículos** (sheet de Home, `HomeSheetContent.vehiclesSection`): el sort ya
   flota "driving" primero, pero entre aparcados ordena por `session != null` + rango de
   vigilancia — **sin recencia**. Con dos coches aparcados manda el orden de Room (inserción), no
   el último aparcado.

## Doctrina violada
La jerarquía de foco establecida por DET-READY-TRIP-OVER-PARKED-001 + UI-PREFERRED-SESSION-RECENCY-001:
*Monitoring gana a Parked* (readiness, puck, cámara) y *el último aparcado representa al usuario*.
El peek de Browse y el orden de chips quedaron con la prioridad contraria — el mismo invariante con
dos respuestas según la superficie.

## Regla (confirmada por el user)
1. Si estamos **activamente monitorizando** un vehículo (EN RUTA / APARCANDO…) → ese es el sujeto.
2. Si no → el **último usado** = sesión aparcada más reciente (`preferredParkingSession`).
3. Sin nada → cabecera de zona (peek) / orden por rango de vigilancia (chips).

Fuera de alcance (deliberado): extender "último usado" más allá de las sesiones aparcadas
(recordar el último vehículo seguido cuando la detección falló o se borró el pin).

## Diseño
El invariante "qué sesión aparcada me representa" ya vive en UN sitio:
`domain/model/PreferredParkingSession.kt`. Se extrae su comparador
(`parkedSessionPreference(vehicles)`) para que el sort de chips consuma EXACTAMENTE la misma
preferencia (recencia → rango de vigilancia) en vez de duplicarla.

- `BrowsePeek.kt`: la rama del viaje vivo (`drivingMeta`) pasa a evaluarse antes que la del coche
  aparcado.
- `HomeSheetContent.vehiclesSection`: orden = driving primero → aparcados por
  `parkedSessionPreference` (más reciente primero) → sin sesión por rango de vigilancia. Extraído a
  función pura testeable `vehiclesRowOrder(cards, drivingVehicleId)`.

## Criterio de éxito
- Test: con drivingMeta del coche B y sesión del coche A, el orden de chips pone B primero.
- Test: dos aparcados → el de sesión más reciente primero (no el orden de la lista).
- Campo: conduciendo el Focus con el Kamiq aparcado, el peek dice "FORD FOCUS · EN RUTA" y el chip
  del Focus va primero.
- Galería mock: variante nueva "Peek · en ruta gana al aparcado".

## Consumidores auditados
Superficies que eligen "qué coche me representa":
- `BrowsePeek` (peek Browse) → **corregido** (driving > parked).
- `vehiclesSection` (chips/card del sheet) → **corregido** (recencia entre aparcados vía comparador
  compartido).
- Readiness / puck / cámara de viaje (`ObserveDetectionReadinessUseCase`) → ya prefiere Monitoring
  desde DET-READY-TRIP-OVER-PARKED-001 — exento.
- FAB de coche (`HomeFabsSlice.selectedParkingWatch`) → cicla por la sesión SELECCIONADA, no usa
  preferencia — exento.
- Cámara inicial / release fallback / banner Parked → consumen `preferredParkingSession`, cuya
  semántica no cambia (solo se extrae el comparador) — exentos.
