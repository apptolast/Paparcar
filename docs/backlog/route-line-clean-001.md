# ROUTE-LINE-CLEAN-001 — La línea de la ruta sin ruido: fixes desviados se corrigen a la calle (y línea más gruesa)

**Estado:** ✅ EN MASTER `b9d76a33` (ff-only 2026-08-07, rama borrada); ⏳ field-test con el APK
de campo entregado el 06-08 (master + este ticket — el primer APK con todo el paquete de agosto).

## El problema (capturas del user, 30-07 20:17)

Las capturas se tomaron CON el matcher v1 activo (ROUTE-SNAP-001 es del 30-06, un mes antes):
el ruido fotografiado es exactamente la limitación residual de v1, no un APK viejo.

1. Un fix a >30 m de cualquier calle se quedaba CRUDO → pincho visible que dobla la línea fuera
   de la vía.
2. El snap punto-a-punto independiente podía saltar un fix ruidoso a una calle paralela más
   cercana → zigzag entre calles.
3. La línea a 20 px seguía pareciendo fina al user (≈6.5 dp en los móviles de campo ~3×).

## La decisión — v3 del `TrailMapMatcher` (mismo seam único, cero cambios de pipeline)

- **Snap con continuidad (Viterbi ligero).** Por fix, candidatos = mejor proyección sobre cada
  calle a ≤`MAX_SNAP_METERS` (30→60 m, multipath urbano), máx 6 calles. Se elige el camino que
  minimiza `distancia fix→calle + TRANSITION_WEIGHT × |paso snapped − paso medido|`. Saltar a una
  paralela añade recorrido que el GPS no midió → penalizado; girar una esquina real (nodo
  compartido) no. El fix "un poco alejado" se corrige a la calle que se venía siguiendo.
- **Pinchos descartados.** Rachas de 1–2 fixes sin calle cercana ENTRE vecinos bien situados se
  eliminan; el gap-fill v2 (A*) rutea el hueco por calles si es largo. Rachas largas fuera de vía
  (parking, descampado) y extremos del trail (el origen retrodatado puede estar en un parking) se
  conservan crudos — honestidad: ahí no hay calle.
- **Grosor 20→28 px** (`TRIP_TRAIL_WIDTH`, ≈9 dp en los móviles de campo ≈ peso de ruta de
  Google Maps).

Todo visual/presentación: la evidencia de detección, `TripTrailImpl` forense y la decimación
`MapTrail` no se tocan.

## Validación

- 4 tests nuevos en `TrailMapMatcherTest` (9 total): pincho descartado, racha larga cruda, origen
  fuera de vía conservado, fix ruidoso se queda en la calle seguida y no salta a la paralela
  (costes verificados a mano: ~57 vs ~90).
- Device/field-test: ruta limpia pegada a la vía en los tramos de las capturas del 30-07.

## Ficheros

- `domain/matching/TrailMapMatcher.kt` — v3 (Viterbi + descarte de pinchos).
- `ui/components/PaparcarMapView.kt` — `TRIP_TRAIL_WIDTH` 20→28.
- `commonTest/domain/matching/TrailMapMatcherTest.kt` — 4 tests nuevos.
- `docs/detection/PARKING-DETECTION.md` — changelog (misma tarea).

## Relacionados

- ROUTE-SNAP-001 (v1, per-point) · DET-ROUTE-ORIGIN-001 (v2, gap-fill A* + origen retrodatado).
- ROUTE-SMOOTH-002 (spline solo del trail crudo; el matched se dibuja directo — sin cambios).
