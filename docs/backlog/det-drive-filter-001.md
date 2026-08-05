# DET-DRIVE-FILTER-001 — distanceFilter en conducción: CERRADO POR ANÁLISIS (no aplicable a nuestra arquitectura)

**Estado:** ❌ CERRADO 2026-07-30 sin código, por análisis del stream real. Reabrir SOLO si la
detección de parada dejara de ser GPS-based (p.ej. stop-detection por AR como Transistor).
**Origen:** Pieza 4 del plan Driversnote (`project_det_driversnote_learnings_plan`):
`distanceFilter: 20` en su config decompilada → menos updates parado en semáforos, traza limpia.

## Por qué funciona para Driversnote y no para nosotros

Transistor detecta la parada con la **Motion API (AR `still`) + stopTimeout** — el GPS mudo en un
semáforo no les ciega: el cierre de viaje no depende de fixes estacionarios.

Nuestro stream (`ObserveAdaptiveLocationUseCase`) es el INVERSO deliberado:
- **HIGH_ACCURACY 5 s cuando LENTO/parado** (≤11 km/h) — esos fixes a velocidad ~0 SON el
  detector de aparcamiento: abren `stoppedSince`/fase Candidate, capturan el ancla
  (`initialStopWindowMs`, `anchorFreezeStopMs` = 3 fixes estables), y miden el egress cinemático
  (≥6 fixes peatonales — el gotcha ya anotado en el plan).
- **BALANCED 30 s cuando rápido** (>18 km/h) — a esa velocidad recorres >150 m por beat: un
  filtro de 20 m **no suprime nada**. Cero ganancia donde sería seguro.

Un `setMinUpdateDistanceMeters` en el tramo lento/parado haría que el fused provider dejara de
entregar fixes justo cuando el coche se detiene → la parada se vuelve INVISIBLE (no hay fix de
velocidad 0 que abra el stop): rompe la apertura de Candidate, el ancla y el egress — es decir,
rompe la detección misma, no solo el egress del gotcha original. Y el parche (watchdog de
silencio → re-request sin filtro) añade un modo de fallo en la transición más crítica del sistema
a cambio de ahorrar unos fixes en semáforos.

## La intención original ya está cubierta

- **Batería en reposo** → SENTRY apaga el GPS aparcado (DET-RESIDENT-FGS-001) + BALANCED 30 s en
  conducción sostenida + red exact-alarm solo-mientras-aparcado (DET-EXACT-HEARTBEAT-001).
- **Traza limpia** → decimación 4 m de `MapTrail` + matcher v2 con routing por calles
  (DET-ROUTE-ORIGIN-001): la nube de jitter de un semáforo ni se dibuja.
- **Robustez en atascos** → DET-JAM-WINDOW-001 (el plan ya anotaba que el atasco NO se resuelve
  con distanceFilter).

## Relacionados
Pieza 4 de `project_det_driversnote_learnings_plan` · `reference_driversnote_detection_stack`.
