# DET-BT-A-PARK-IS-CONFIRMED-NOT-ASSUMED-001 · La ruta grabada testifica en el veredicto BT, y un timeout de GPS deja de perder el aparcamiento

**Estado:** ✅ Done (2026-09-14) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Gate de campo pendiente (no bloqueante): viaje BT real con GPS malo en destino → nudge (Kamiq).
**Verificado:** suite completa verde (`EvaluateBtParkUseCaseTest` **37/37** — 31 existentes
intactos + 6 del testigo grabado) · verify mock · compiles mock/prod/iosSimulatorArm64 OK.
**Abierto:** 2026-09-14 · desde master `213b4596` · fase 4 del pivote trayectos-como-producto

## Problema

Dos, y los dos nacen de la misma premisa muerta. `EvaluateBtParkUseCase` razona — y su KDoc lo
declara — que *"the BT lane cannot measure speed: it holds no stream while you drive"*. Desde
`TRIP-THE-TRACKER-RIDES-THE-SENTINEL-001` eso es falso: el grabador SÍ mide el viaje. El evaluador
sigue sin saberlo:

1. **La prueba del viaje depende de un fix cacheado.** `evaluateRideDisplacement` mide el
   desplazamiento contra el *last known* del CONNECT — que puede faltar (`no_origin_fix`), estar
   rancio (`origin_fix_stale`) o venir de otro reloj. Cada uno de esos huecos degrada un viaje REAL
   a pregunta (`bt_unmeasurable_ask`), con la ruta medida del propio viaje sentada en el store.
2. **`bt_gps_timeout` pierde el aparcamiento en silencio.** Si la caza post-disconnect no consigue
   candidato (garaje, cañón urbano), el detector hace `return` sin sesión, sin nudge, sin nada —
   con un viaje MEDIDO en disco. Eso viola el contrato de detección: *parking perdido con datos =
   bug nuestro* — todo trigger dispara siempre, aunque sea degradado a pregunta.

Nota de alcance: el plan de fase 4 hablaba de "congelar el ancla al fin de conducción" —
**ya está cerrado estructuralmente** por `DET-BT-PIN-IS-SAMPLED-AFTER-THE-WALK-001` (`0e3940bc`:
la caza corre desde el disconnect y gana el candidato MÁS TEMPRANO). Y el "egress medido" ya
existe: el walk-away exige desplazamiento a ritmo peatonal desde el instante del candidato. Este
ticket aporta lo único que faltaba: **el testigo del viaje grabado**.

## Doctrina

- *Solo el movimiento MEDIDO confirma* — reforzada: la ruta grabada es medición NUESTRA, tomada
  DURANTE el engagement por nuestro propio stream — evidencia estrictamente mejor que un fix
  cacheado de antes del connect. Se consulta PRIMERO; el fix del connect queda de fallback.
- *Fallo asimétrico* — intacto: la ruta jamás coloca un pin. En el timeout, un viaje probado
  produce un **nudge** (preguntar), nunca una sesión. Un viaje de ida-y-vuelta al mismo sitio
  sigue dando `DidNotMove` → pregunta (mismas semánticas de desplazamiento entre extremos que la
  aritmética actual — a propósito).
- *Las estrategias no se mezclan* — la ruta la grabó el tracker (sin autoridad), y la consume el
  evaluador BT puro. Nada entra al scoring del Coordinator.

## Diseño

Todo en el evaluador puro (`EvaluateBtParkUseCase`, commonMain); el detector Android solo lee el
store y pasa la lista.

1. **`evaluateRideDisplacement` gana el testigo grabado** (parámetro `recordedRide: List<GpsPoint>`,
   y `candidate` pasa a nullable para el timeout):
   - Puntos usables: `timestamp >= connectedAtMs` (el grabador arranca en el connect; los restos de
     un viaje anterior quedan fuera por timestamp — y el store además se auto-segmenta).
   - Con ≥2 usables: desplazamiento primer-punto → candidato (o → último punto si no hay
     candidato). Umbral: `btMinRideDisplacementMeters + accuracy de ambos extremos` — **sin** el
     término peatonal por edad: los dos extremos son medidos durante el engagement, no cacheados.
     El veredicto de la ruta es TERMINAL (mejor evidencia): su `DidNotMove` no se re-litiga con el
     fix cacheado.
   - Sin ruta usable → aritmética actual del connect-fix, intacta.
   - `Moved`/`DidNotMove` llevan `evidence` (`RECORDED_ROUTE` / `CONNECT_FIX`) para calibración en
     campo vía logs/telemetría.
2. **El timeout de GPS honra el contrato**: sin candidato, se evalúa la prueba del viaje
   ruta-sola. Viaje probado → `showMarkParkingNudge` (fuente nueva `bt_gps_timeout`, verdict remoto
   `bt_gps_timeout_ride_ask`); sin viaje probado → silencio como hoy (un blip de garaje sin
   conducción no debe dar la lata).
3. KDoc del evaluador: reescribir la premisa muerta.
4. Docs de la saga: `det-a-bt-drive-leaves-no-trace-001.md` pasa a reflejar que la vía B está en
   master (fase 3) y solo queda la vía A (pin-a-pin retroactivo, gated por Kamiq).

## Criterio de éxito

- Tests del evaluador: ruta prueba Moved donde antes era `Unmeasurable` (sin origen / rancio) ·
  ruta `DidNotMove` es terminal frente a un fix cacheado que "movería" · filtro de puntos
  pre-connect · prueba ruta-sola sin candidato · fallback intacto con ruta vacía · las variantes
  existentes del connect-fix no cambian de veredicto.
- Suite completa verde + compiles mock/prod + iosSimulatorArm64.
- Campo (viaje Kamiq, no bloqueante del merge): un viaje BT real con GPS malo en destino termina
  en nudge, nunca en silencio.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `BluetoothParkingDetector` (único llamador de `evaluateRideDisplacement`) | cerrado: pasa `drivingRouteStore?.points()` y el connectedAtMs que ya reconstruye |
| Ramas de confirm (`bt`, `bt_timeout`) | sin cambio de forma: `Moved` sigue siendo la única puerta; solo mejora su alcance |
| `BtRideProof.Moved/DidNotMove` (shape) | campo `evidence` nuevo → único consumidor es el detector (logs); barrido por compilador |
| `DrivingRouteStore` en el carril BT | lectura pura post-disconnect; el grabador ya paró (STOP viaja en el mismo broadcast del disconnect) — sin carrera |
| `ConfirmParkingUseCase.encodeFreshRoute` | sin cambio: sigue leyendo el store al confirmar (el clear posterior no afecta a esta lectura previa) |
| iOS | exento: no hay carril BT en iOS; el evaluador común compila (verificado con iosSimulatorArm64) |
| Verdicts remotos | nuevos: `bt_gps_timeout_ride_ask`; los existentes conservan significado |
