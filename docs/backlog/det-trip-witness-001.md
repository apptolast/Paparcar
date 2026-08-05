# DET-TRIP-WITNESS-001 — honest-close: un delta de pasos rancio no prueba un viaje

**Estado:** ✅ IMPLEMENTADO 2026-08-04 en `bugfix/DET-TRIP-WITNESS-001-honest-close-witness`
(gate de edad del seal, fix preferido del spec). El seal es ahora un triple (contador, posición,
**momento**): ambos selladores estampan `anchor_seal_at_<id>` atómico con el baseline;
`EvaluateHonestCloseUseCase` recibe `sealAgeMs` (sin default) y devuelve `KeepSilent/stale_seal`
si la edad supera `honestCloseMaxSealAgeMs` (2 h) **o es desconocida** (seal legacy sin fecha).
Replay 30-07 cubierto en `EvaluateHonestCloseUseCaseTest` + `RunHonestCloseUseCaseTest` (pin de
Angelita se conserva, cero docs, cero nudge). **Nota resuelta:** el "opener" vs la noche del 29-07
fue el testigo — 29-07 la sesión vio 7 pasos de detector → `frozen_counter` silenció; 30-07 vio 0
→ cross-check ciego. No fue el refactor GAP-ANCHOR. ⏳ Field-test.
**Origen:** field-test 30-07 ~17:53, Redmi — FP "Glorieta Juan de Austria" (= la casa del user),
sesión `1785426554477` (diagnostics WZB7…), doc parkingHistory `00d513ed`
(`detectionPath=closed_approximate_pin`, rel 0.5, coords 36.60387,-6.23029 = el teléfono).

---

## Qué pasó (evidencia de campo, timeline local)

1. **01:47 (30-07)** — pin real en Calle la Angelita (valla `22df7d52`). El seal de pasos se
   estampa; el user camina a casa (~200 m). El teléfono queda FUERA de esa valla desde entonces.
2. **17:49** — MIUI entrega un **EXIT eco/initial-trigger** de esa valla con el móvil quieto en
   casa desde hacía horas (`ARM:GEOFENCE_EXIT d=195m exitLoc=casa dep=self_observed`).
3. La sesión mide **NADA**: 25 fixes clavados en casa (acc ~16 m), vmax 1 km/h, 0 pasos propios,
   4.3 min → aborta `aborted_no_movement`.
4. **HONEST_CLOSE** (`EvaluateHonestCloseUseCase`): `reason=trip_proven`,
   `verdict=closed_approximate_pin` a 198.66 m del pin → **pin aproximado EN EL TELÉFONO (casa)**.
   El coche seguía en Angelita. El pin bueno fue liberado y sustituido por el FP.

## Raíz

La escalera llegó a `trip_proven` porque el **delta del contador acumulado desde el seal
(16 horas antes) llegó ≈ 0** pese a la caminata a casa de anoche — contador MIUI
congelado/batching a través de muerte de proceso y noche (patrón ya documentado en
DET-FROZEN-COUNTER-001). El cross-check de liveness (`sessionStepEvents > 0 && steps < sessionStepEvents`,
`EvaluateHonestCloseUseCase.kt` ~L228) **no tenía testigo**: la sesión abortante vio 0 step events
(el user estaba quieto en casa), así que el contador congelado pasó por vivo → "198 m sin pasos =
viaje" → pin.

Nótese la doble debilidad: (a) el "viaje" nunca fue presenciado (ningún tránsito en sesión: el
teléfono nunca estuvo dentro de la valla ni se movió); (b) el presupuesto pasos-vs-desplazamiento
comparó posiciones separadas por **16 horas** con un contador no fiable en ese lapso.

## Invariante

*El presupuesto pasos-vs-desplazamiento solo es interpretable dentro de una ventana en la que el
contador acumulado es fiable. Un delta que abarca horas (sueño, muertes de proceso, batching MIUI)
no puede probar un viaje — y un viaje jamás se prueba sin tránsito presenciado.*

Es el simétrico de DET-GAP-ANCHOR-001 ("el ancla necesita un reposo presenciado") aplicado al
cierre honesto: **el viaje necesita un tránsito presenciado (o un presupuesto fresco), no un delta
rancio**. Y el gemelo de DET-DEPART-PROOF-001: allí el eco del EXIT publicaba plaza fantasma; aquí
el eco RECOLOCA el propio pin.

## Fix candidato (preferido): gate de edad del seal

En `EvaluateHonestCloseUseCase` (use case puro → replayable):

- Nueva entrada `sealAgeMs` (edad del seal del pin rancio en el momento del abort).
- Si `sealAgeMs > config.honestCloseMaxSealAgeMs` (propuesta ~2 h) → `KeepSilent` con
  `REASON_STALE_SEAL` (nueva constante + telemetría estándar de la escalera).
- Racional del umbral: los casos legítimos que motivaron la escalera (D2 return 15-07, Camelias
  14-07) cierran a MINUTOS del viaje real; a las 2 h el contador acumulado ya no es interpretable
  (siesta, proceso muerto, batching).

Alternativa más ambiciosa (evaluar en implementación, no apilar ambas de entrada
[feedback_systems_not_patches]): exigir tránsito presenciado en sesión (cruce dentro→fuera de la
valla o movimiento medido) para cualquier `trip_proven`. Cubre también ecos "frescos" (<2 h), pero
toca más estado del coordinator.

## Riesgo del gate

Un EXIT real entregado >2 h tarde con proceso dormido → silencio en vez de zona aproximada.
Acotado: el safety-net (worker 15 min + verificación tardía) sigue siendo el backstop declarado de
la escalera para ese caso (igual que ya lo es para mute/frozen counter). Falso negativo acotado >
falso positivo — doctrina asimétrica.

## Tests

- Replay de la sesión `1785426554477` (30-07 17:49 Redmi): seal 16 h → `KeepSilent/stale_seal`,
  ningún doc creado, el pin de Angelita se conserva.
- Regresión: replays existentes de DET-HONEST-CLOSE (D2, Camelias), DET-FROZEN-COUNTER,
  DET-WALK-FLOOR y DET-STEP-BUDGET-ORIGIN siguen verdes (todos con seal fresco).
- Unit: seal justo bajo/sobre el umbral.

## Nota

Mismo estímulo la noche anterior (29-07 23:49, mismo geof, `aborted_no_movement`, APK viejo) NO
creó pin. Al implementar, identificar qué abrió la conducta en el APK del 30-07 (¿ordenación del
cierre tras el refactor GAP-ANCHOR? ¿steps 7 vs 0?) y cubrirlo en el replay.
