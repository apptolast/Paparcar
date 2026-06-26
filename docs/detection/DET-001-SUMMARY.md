# DET-001 — Resumen de sesión (técnico + negocio)

> Rama `refactor/DET-001-detection-decision-engine`, sin merge. Suite verde en cada paso
> (`testMockDebugUnitTest`). `iosMain` no compilado en este entorno (Windows; K/N iOS necesita macOS).
> Plan vivo: `REFACTOR-PLAN.md` · Diseño Fase G: `FASE-G-DESIGN.md`.

## Qué problema resolvía
La detección de aparcamiento es el activo central de Paparcar: si publica **plazas fantasma**, la
red comunitaria pierde confianza. Había un falso positivo real (viaje en Bolt en Praga → plaza
inexistente) y un FGS (notificación) que se quedaba colgado. Principio rector: **fallo asimétrico** —
un falso negativo (no detecto plaza) cuesta ~0; un **falso positivo (plaza fantasma) cuesta caro**.

---

## ✅ Implementado

| Fase | Técnico | Negocio |
|------|---------|---------|
| **A — Gate de egreso** | `minEgressDisplacementMeters=18f`; ningún auto-confirm sin que el fix actual esté ≥18 m del `bestStopLocation`. | Mata el FP de Praga: pasos en un atasco ya no publican plaza si el coche no se movió. |
| **0 — Log remoto** | `DetectionEventLogger` + `FirestoreDetectionEventLogger` (buffer Channel no bloqueante, gate por flag Firestore `diagnostics_config/{uid}.enabled`). Instrumentación coordinator: SessionStarted/LocationFix/Step/ActivityTransition/Candidate/Decision/SessionEnded. | Permite diagnosticar fallos **en campo** sin Android Studio y crear fixtures de replay. Opt-in, default OFF. |
| **B — FGS huérfano** | `onStartCommand`: intent nulo (restart) no promueve FGS; `Ignore`/acción desconocida → `stopIfIdle`. | Se acaba la notificación de detección colgada sin trabajo detrás (drena batería + confunde). |
| **C — Endurecer (C-01)** | Egreso = **precondición de TODO** auto-confirm del candidate (`!hasEgress -> false`). | STILL/dwell/AR-exit por sí solos ya no confirman: nunca se publica sin que el usuario se aleje del coche. |
| **D — Decisión pura** | `ParkingDecision` + `EvaluateParkingDecisionUseCase` puro (extraído del coordinator), inyectado por DI. 12 tests incl. replay de Praga y paths time-driven. | La lógica de confirmación es ahora testeable y replayable; reduce el riesgo de regresión silenciosa. |
| **F — Limpieza** | `reliabilityBluetooth` a config; rename simétrico `CoordinatorParkingDetector`/`CoordinatorDetectionService`. | Mantenibilidad; coherencia con la ruta BT. |

**Trigger de detección hoy:** sigue siendo **AR `IN_VEHICLE_ENTER`** (no geocerca). La geocerca dispara
la **liberación** de la plaza (departure), no el armado de la detección — ver Fase G abajo.

---

## ⚠️ Regresión introducida y revertida

**DET-E-01 (BT connect alimenta departure) → revertido.**
- *Qué hacía:* el BT connect llamaba `DepartureEventBus.onVehicleEntered`.
- *Por qué era un FP:* el fallthrough `BUG-WALK-DEPART-001` de `DepartureDetectionWorker` publicaba la
  plaza si había señal de "entró al vehículo" aunque la velocidad nunca confirmara. El BT connect
  dispara con **solo sentarse en el coche** → un usuario BT sentado en su coche aparcado + parpadeo de
  geocerca → **plaza publicada como libre con el coche sin moverse**. El error que más nos importa.
- *Negocio:* habría contaminado la red con plazas fantasma justo en los usuarios BT (los más fiables).
- *Decisión:* revertido. El AR `IN_VEHICLE_ENTER` (movimiento real) ya cubre a los BT-users y es mejor señal.

---

## 🔧 Fixes del code-review (high-effort, 45 agentes)

| # | Fix | Negocio |
|---|-----|---------|
| 1 | Revert DET-E-01 (arriba) | Evita plazas fantasma en usuarios BT. |
| 2 | El gate del log **no cachea fallos transitorios** | Un trayecto de test no se pierde si hubo un fallo de red momentáneo al arrancar. |
| 3 | `log()` cortocircuita si el gate está OFF | Los usuarios que no opt-in (toda producción) no pagan coste por evento en el hot-path. |
| 4 | `egressAnchor` → `bestStopLocation` (refinado por precisión) | El gate de egreso ya no depende de la precisión de un único primer fix → menos falsos confirm/negativos. |
| 5 | Path 8 (EXIT+steps) pasa por `EvaluateParkingDecisionUseCase` | Una sola fuente de verdad para egreso+mismatch+reliability; sin copia inline sin tests que pueda divergir. |
| 6 | `toDto()` con `when` exhaustivo | Añadir un campo a un evento ya no compila limpio escribiendo `null` silencioso (corrompía el replay). |
| 9 | Colapsado artefacto de 7.3 KB de espacios | Limpieza. |

**Refutados (6)** por la verificación: que `DepartureDecision` no existía (sí existe), que el
egreso-obligatorio era un bug (es intencional), estilo `when` vs `as?`, colisión Geofence/Bluetooth.

---

## ⏸️ Diferido (esperando datos del field test)

- **C-02** — auto-revert post-confirm si reaparece velocidad sostenida (C-01 ya lo hace casi imposible).
- **D-03** — reconvertir el confidence scorer a metadato (cambia *timing* del prompt).
- **DET-LOG-05** — traza de departure (geofence/BT) con su propio modelo de sesión.
- **Code-review #7** — park en garaje con GPS frío (coordinator) ya no auto-confirma (consecuencia
  *deliberada* del egreso obligatorio; la ruta BT sí cubre garaje). Validar frecuencia con datos.
- **Code-review #8** — `SessionEnded` puede huérfano si se dropeó `SessionStarted` (mitigado por `runCatching`).

---

## 🚧 Lo único grande que queda: Fase G

- **G-01 (motor)** — armado de la detección por **GEOFENCE_EXIT** en vez de AR ENTER. Diseñado
  (`FASE-G-DESIGN.md`). Bloqueante real: arrancar el FGS desde el worker/geocerca (background) puede
  lanzar `ForegroundServiceStartNotAllowedException` en Android 12+ → verificar en device.
- **G-02 (UI)** — affordance "he aparcado aquí" (reusa `HomeMode.AddingParking`) + indicador de
  detección activa, para el arranque en frío (primer park, sin geocerca previa).

## Estado del field test
Reglas Firestore desplegadas (`pap-26`). Flag `diagnostics_config/{tu-uid}.enabled = true` activado por
MCP. Falta: instalar el build de la rama en el móvil y conducir → leo las trazas por MCP.
