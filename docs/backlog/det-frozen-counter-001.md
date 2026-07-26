# DET-FROZEN-COUNTER-001 — contador congelado + zona desatendida acotada + trazas sin zonas mudas

**Estado:** ✅ implementado 2026-07-26 en `feature/DET-FROZEN-COUNTER-001` (encima de DET-ZOMBIE-PROBE-001) · 954 tests verdes · ⏳ commit / device / field-test
**Origen:** field-test 2026-07-25/26 (cena en Jerez, Redmi + Oppo, detección manual, batería al mínimo).

## Forense (telemetría pap-26)

### FP — pin dentro del restaurante (Redmi, 22:29)
- 22:23 pin correcto Calle Cobre 27 (`detectionPath=steps+egress`, sesión `1785010179015`); seal del presupuesto de pasos en el confirm.
- Caminata de ~150 m al restaurante → EXIT de la propia valla → sesión `1785011330665` (22:28), aborta BIEN en 44 s: `aborted_false_enter`, 8 pasos de detector, 0 fixes de conducción, vmax 2 km/h.
- El honest-close corre sobre el abort: distancia pin→abort 150 m > `honestCloseMinTripMeters` (80) y el delta del contador ACUMULATIVO ≈ 0 (< ~80 pasos requeridos = 150/0.75 × 0.4) → "viaje demostrado" → `closed_approximate_pin` en el restaurante (22:29:35), **pisando el pin bueno**.
- Raíz: el acumulativo MIUI se CONGELA en background y devuelve el último valor cacheado **no-cero** — pasa la guarda de contador mudo (que solo filtra el 0 absoluto) y su delta≈0 es exactamente la "prueba de viaje". Prueba diferencial: el Oppo tuvo el mismo abort (22:51, `1785012706298`) con contador vivo → silencio correcto.

### FN — vuelta a casa sin pin (Redmi, 00:17–00:50, sesión `1785017841365`)
- 92 fixes de conducción, vmax 160 km/h. AR vehicle-EXIT 00:34:20 → prompt ~00:35 (¡sin evento en la traza!) sin respuesta → departure liberó los spots viejos (00:41:48) → al timeout de 15 min `DET-ANCHOR-EGRESS-001` vio el egress nacido lejos del ancla → `aborted_unattended_egress_mismatch` + nudge que nadie vio. **Parking perdido con datos.**

### FN — Oppo, cero sesiones tras 22:53
- El EXIT de Calle Cobre nunca llegó: ColorOS OEM-kill (clase conocida, test-al-mínimo). Sin cambio de código aquí.

## Fix (3 piezas, un invariante cada una)

### 1. Honest-close: el presupuesto de pasos solo es admisible con contador probadamente VIVO
`EvaluateHonestCloseUseCase` recibe la evidencia de la PROPIA sesión abortada (`sessionStepEvents`, `sessionMaxSpeedMps`, expuestos por el coordinator como `lastSessionStepEvents`/`lastSessionMaxSpeedMps`/`lastSessionId`):
- **Cross-check de vida**: si el detector de la sesión contó ≥1 paso y el delta acumulativo es MENOR → contador CONGELADO → tratar como mudo → `KeepSilent(frozen_counter)`.
- **Velocidad medida manda**: `sessionMaxSpeedMps ≥ minimumTripSpeedMps` prueba el viaje directamente (hoy inalcanzable en los dos aborts que disparan la escalera; defensivo para futuros callers).
- El evaluador devuelve `HonestCloseVerdict` (decisión + reason + todos los números); `RunHonestCloseUseCase` devuelve `HonestCloseResult`.

### 2. Timeout desatendido: zona aproximada ACOTADA en vez de perder el parking
Los candados que refusaban el pin exacto ya no tiran el parking cuando la duda es **acotable** — guardan `UserParking` zona (reliability 0.5, `detectionPath=unattended_zone_<reason>`, outcome `confirmed_unattended_zone_<reason>`), radio ∈ [60, `unattendedZoneMaxRadiusMeters`=250]:
- `unpinned_anchor` — solo con contador vivo; radio = max(ancla↔actual, stepCount×zancada).
- `egress_mismatch` — centro según vida del contador (vivo → nacimiento del egress [Enamorados: el parking real]; mudo → ancla congelada [regresión: caminata muda de 260 m]); radio cubre nacimiento↔ancla.
- `walk_entered_anchor` — solo con `anchorSawStepsAtCapture`; radio = pasos de entrada × zancada.
- **Siguen nudge-only** (duda no acotable): mudo+unpinned, mudo+walk-entered, `vehicular_egress` (el coche provablemente SE FUE), `unattended_no_drive` (sin conducción medida — doctrina).
- La card "Vehículo aparcado" de `runConfirm` es la superficie de corrección; sin nudge extra.

### 3. Telemetría — "no queremos zonas mudas"
- Nuevo evento `HONEST_CLOSE` (verdict, reason, distanceMeters, walkDistanceMeters, stepsDelta, requiredSteps, sessionStepEvents, sessionMaxSpeedKmh, radiusMeters) bajo el id de la sesión abortada, desde el service.
- `PROMPT_SHOWN` (Decision) en los DOS carriles de prompt (Low/Medium `advanceLowMedium` y High candidate) con score y posición.
- `Decision` gana `distanceMeters`/`radiusMeters` (candados espaciales + zonas guardadas). DTO: columnas aditivas nullable (`reason`, `walkDistanceMeters`, `stepsDelta`, `requiredSteps`).

## Ficheros tocados
- `EvaluateHonestCloseUseCase` / `RunHonestCloseUseCase` (+verdict/result)
- `CoordinatorParkingDetector` (lastSession* snapshots, saveUnattendedZone, candados, PROMPT_SHOWN, runConfirm zoneRadiusMeters)
- `CoordinatorDetectionService.maybeRunHonestClose` (evidencia + evento HONEST_CLOSE)
- `DetectionEvent` (+HonestClose, Decision ampliado) / `DetectionEventDto` (+4 columnas, branch exhaustivo)
- `ParkingDetectionConfig` (+`unattendedZoneMaxRadiusMeters` 250, require)
- Tests: EvaluateHonestCloseUseCaseTest (verdicts + 3 casos nuevos), RunHonestCloseUseCaseTest (+restaurante), CoordinatorParkingDetectorTest (unpinned→zona, mute control), DetectionTraceReplayTest (Enamorados→zona).

## Pendiente
- ✅ Commit 15b48a07 + APK instalado ambos móviles 26-07.
- Field-test 26-07 (1ª noche): las piezas de este ticket funcionaron (silencio correcto por
  `mute_counter` a las 20:32; el evento HONEST_CLOSE hizo el forense en minutos) pero apareció un
  FP nuevo por otra clase de agujero — margen de 1 paso a 31.8 m sobre un pin MANUAL → fix en
  `docs/backlog/det-walk-floor-001.md` (DET-WALK-FLOOR-001, rama encima de esta).
- ⏳ Field-test de las zonas desatendidas y del cross-check frozen (no ejercitados el 26-07).
- ⏳ UI del círculo de zona aproximada (heredado de DET-HONEST-CLOSE-001).
- Idea diferida: copy de la card diferenciado para zonas ("zona aproximada — afina el punto").
