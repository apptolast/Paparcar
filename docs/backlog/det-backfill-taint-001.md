# DET-BACKFILL-TAINT-001 — el backfill de la red no puede re-decidir una llegada que el coordinator resolvió como "solo preguntar"

**Estado:** ✅ IMPLEMENTADO 2026-08-04 en `bugfix/DET-BACKFILL-TAINT-001-net-honors-gap-anchor`.
El teardown del service estampa la resolución nudge-only (`arrival_resolution_at/pos` en prefs
del safety-net, un slot, sobrevive a muerte de proceso) al abortar `aborted_unattended_gap_anchor`;
`ParkingBackfillWorker` consulta el evaluador puro `EvaluateBackfillDeferralUseCase` (commonMain,
testeado: dentro/fuera de ventana 20 min, mismo/distinto trip 500 m, stamp futuro/ausente) antes
de colocar → skip + telemetría `BACKFILL_DEFERRED_TO_NUDGE`. Cadena de departure, cure/reseal y
guard `isRunning` intactos. La nota UX (visibilidad del nudge dominante en Redmi) sigue ⏳ como
investigación aparte. ⏳ Field-test.
**Origen:** field-test 30-07 ~20:42, Redmi — parking real en Jerez (Calle Arquímedes 13), sesión
`1785434857650` (diagnostics WZB7…), doc parkingHistory `57ea4afe`
(`detectionPath=safety_net_backfill`, rel 0.5).

---

## Qué pasó (evidencia de campo, timeline local)

1. **20:07→20:41** — trayecto real a Jerez (vmax 124 km/h, drive 36/366 fixes). Al aparcar, el
   stream MIUI venía agujereado → **GAP-ANCHOR-001 veta el pin**: outcome
   `aborted_unattended_gap_anchor`, nudge "marca tu plaza", SIN pin — "el error hacia delante es
   inacotable, ningún lugar es honesto" (razonamiento del propio taint).
2. **20:42 (1 minuto después)** — el ciclo del safety-net dispara la cadena
   `DepartureDetectionWorker → ParkingBackfillWorker` y el backfill **planta el pin igual**
   (rel 0.5, acc 38 m) en el último fix. Esta vez acertó (el agujero era corto); el día que el
   agujero sea de 2 km plantará el pin a 2 km con la misma seguridad.

## Raíz

El taint `anchorGapEnteredAtCapture` vive en el estado de la sesión del coordinator, que muere con
el abort. `ParkingBackfillWorker` (androidMain, `PATH_SAFETY_NET_BACKFILL`) solo comprueba
`detectionRuntime.isRunning` (guard DET-ARRIVAL-DOUBLE-PIN-001: "si hay sesión viva, difiere") —
**no existe ningún canal por el que la RESOLUCIÓN del coordinator ("esta llegada = solo nudge")
llegue a la red**. Dos deciders sobre la misma llegada; el segundo, con menos información,
contradice al primero.

## Invariante

*Una llegada que el coordinator ya RESOLVIÓ (nudge-only por ancla GAP-ENTERED) no puede ser
re-decidida por la red de seguridad.* Es la cara que DET-ARRIVAL-DOUBLE-PIN-001 dejó abierta:
aquel cerró "ambos colocan" (sesión viva + backfill); este cierra "el veto de uno, colocado por el
otro". La liberación del spot ANTIGUO (departure) sigue siendo correcta y no se toca — solo la
colocación NUEVA debe deferir al nudge ya mostrado.

## Fix candidato

1. Al abortar con `aborted_unattended_gap_anchor` (y en general al emitir un nudge de resolución
   de llegada), el coordinator estampa la resolución en estado PERSISTIDO (`DetectionRuntimeState`
   o prefs — sobrevive a la muerte del proceso):
   `lastArrivalResolution = { atMs, lat/lon del último fix, kind = NUDGE_ONLY_GAP_ANCHOR }`.
2. `ParkingBackfillWorker` (y/o el punto donde `EvaluateSafetyNetCheckUseCase` decide encadenar el
   backfill) lee la marca: si su wake cae dentro de
   `config.arrivalResolutionWindowMs` (propuesta ~20–30 min) de una resolución nudge-only y el fix
   del backfill corresponde al mismo trip (distancia al fix de la resolución dentro de un radio
   generoso), **skip placement** — log + telemetría (`backfill_deferred_to_nudge`), el nudge sigue
   siendo la vía y el CTA manual queda. La cadena del departure NO se toca.
3. Cure/reseal del safety-net sigue funcionando igual (el skip es solo de la COLOCACIÓN).

## Riesgo

Suprimir un backfill legítimo dentro de la ventana → el parking queda sin pin hasta que el user
responda el nudge (falso negativo acotado, doctrina asimétrica; hoy ese caso YA termina en nudge
cuando el guard isRunning difiere). Ventana corta y match por trip acotan el alcance.

## Nota UX (investigar en paralelo)

El backfill muestra la card ACK/REVERT (`showParkingSavedConfirm`) — el user no la vio anoche
(¿suprimida por MIUI? ¿pisada por el nudge anterior?). Con GAP-ANCHOR activo en Redmi el nudge
pasa a ser el camino DOMINANTE (los 2 trayectos reales del 30-07 acabaron en
`aborted_unattended_gap_anchor`; el 2º parking, 21:19, se PERDIÓ por nudge ignorado) → la
visibilidad/persistencia del nudge es ahora crítica de producto, no un edge case.

## Tests

- Unit: resolución estampada + wake dentro/fuera de ventana, mismo/distinto trip → skip/placement.
- Replay 30-07: sesión `1785434857650` + tick de la red a +1 min → `backfill_deferred_to_nudge`,
  cero docs `safety_net_backfill`.
- Regresión DET-ARRIVAL-DOUBLE-PIN-001 (guard isRunning intacto) y backfill legítimo sin
  resolución previa (2026-07-06 Oppo, 10 pasos) sigue colocando.
