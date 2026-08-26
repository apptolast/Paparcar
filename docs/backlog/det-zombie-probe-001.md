# DET-ZOMBIE-PROBE-001 — probe corto para EXITs zombis (75 s en vez de 4,1 min de GPS)

**Estado:** ✅ EN MASTER (`ed1cce6f`) · campo cubierto por la validación hasta `1a4128d5` (23-08-2026)
**Origen:** field-test noche 24→25-07-2026.

## Evidencia de campo

Cada entrega zombi de un GEOFENCE_EXIT (el OS retiene el evento horas y lo entrega con el móvil
quieto en casa) armaba el coordinator y quemaba el presupuesto completo `maxNoMovementMs` (4 min de
GPS, 33–58 fixes) antes de abortar `aborted_no_movement`:

- Oppo 24-07: 22:25, 00:00 y 25-07 15:44 local — tres sesiones de 4,1 min con vmax ≤1 km/h, 0 pasos,
  todas armadas desde el carril far-delivered (`d=1931m / 1929m / 3773m`).
- Samsung 24-07 22:42 — 4,1 min, 58 fixes, vmax 3 km/h, 0 pasos.

Ese patrón (arranques de FGS + ráfagas de GPS sin resultado) alimenta el scoring de "abuso de
batería" de MIUI/ColorOS, que a su vez congela más fuerte — el círculo vicioso que el field-test
identificó. La entrega de eventos NO depende de nosotros; el coste por evento zombi, sí.

## La física que lo hace seguro

Un EXIT real entregado lejos lo está **porque el coche se mueve** (DET-RIDE-PROOF-001): sus
primeros fixes creíbles ya muestran velocidad de conducción, y además su evidencia suele salir
verificada (`verified_speed`/`verified_enter`), lo que siembra `hasEverReachedDrivingSpeed` y el
guard de no-movimiento **ni se consulta**. Una entrega zombi muestra un dispositivo estacionario
desde el primer fix y jamás podrá satisfacer el guard — esperar 4 min no aporta nada.

## Qué se hizo

- `ParkingDetectionConfig.staleExitNoMovementMs = 75_000` (validado `1..maxNoMovementMs`): margen
  para warm-up de GPS + primer fix de conducción.
- `CoordinatorParkingDetector.invoke(staleExitDelivery: Boolean = false)`: el guard de
  no-movimiento usa el presupuesto corto cuando el arm nació del carril far-delivered. Mismo
  outcome `aborted_no_movement` (honest-close y tooling siguen funcionando igual).
- `CoordinatorDetectionService`: el arm de GEOFENCE_EXIT pasa
  `staleExitDelivery = staleExits.any { it.first == id }` (la clasificación boundary/stale ya
  existía en `EvaluateGeofenceExitUseCase`). Manual y AR arms no cambian.
- Nada más cambia: el carril `getForegroundService` se conserva (BUG-FGS-001), el
  DepartureDetectionWorker y el reconcile siguen procesando la salida en paralelo, y el prompt
  "¿Sigues aparcado aquí?" del abort sigue saliendo donde ya salía.

## Riesgo residual aceptado

EXIT real far-delivered **no verificado** que aterriza justo durante una parada ≥75 s (semáforo
largo): la sesión viva aborta antes; el worker de salida (retries con muestreo de velocidad) y el
reconcile/backfill siguen cubriendo el release y el pin de llegada. Doctrina: mejor FN recuperable
por backstop que 4 min de GPS garantizados por cada zombi.

## Criterios de aceptación

- [x] Test: arm stale + sin movimiento → aborta a `staleExitNoMovementMs`, outcome
      `aborted_no_movement`, sin guardar sesión.
- [x] Test: dentro del probe no aborta (es presupuesto, no kill instantáneo).
- [x] Test: fix de conducción dentro del probe → la sesión sobrevive más allá de
      `maxNoMovementMs` (nunca `aborted_no_movement`).
- [x] Arm boundary (no stale) conserva los 4 min (test existente
      `should_abort_after_maxNoMovement_without_driving` sin tocar).
- [ ] Field-test: noche con EXITs zombis → sesiones `aborted_no_movement` de ~1,3 min máx en
      diagnósticos (vs 4,1 min).
