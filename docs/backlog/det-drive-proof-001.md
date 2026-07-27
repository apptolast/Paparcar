# DET-DRIVE-PROOF-001 — "conducción medida" exige hops corroborados, no un fix Doppler

**Estado:** ✅ implementado 2026-07-27 en `bugfix/DET-DRIVE-PROOF-001` (encima de DET-WALK-FLOOR-001) · ⏳ commit / APK / field-test
**Origen:** FP 2026-07-27 14:56 (Oppo, en casa, PARADO — sin salir a conducir), sesión `1785157018067`.

## Forense (telemetría pap-26, 110 eventos de la sesión)

Móvil quieto dentro de casa. El GPS produce una ráfaga-espejismo de 10 segundos:

| hora | fix | lat/lon | Doppler | acc | realidad |
|---|---|---|---|---|---|
| 14:56:58 | #1 | a 216 m de casa | **45 m/s (162 km/h)** | **5 m** | primer fix de sesión (sin prev) |
| 14:57:02 | #2 | a ~400 m | 40.6 m/s | 68.9 m | salto de ~220 m coherente con su Doppler, pero acc no credible |
| 14:57:07 | #3 | junto a #2 | **20.2 m/s** | 9.1 m | **se movió 12 m en 4.9 s** (2.4 m/s reales) — Doppler incoherente |
| 14:57:12+ | resto | casa | ~0 | — | deriva indoor normal durante 7 min, 1 solo paso |

Cadena del FP:
1. El fix #1 saca el móvil de la geocerca → `GEOFENCE_EXIT` arma (d=216 m, acc=5 m) y la
   verificación de salida se cree el mismo Doppler fantasma → `dep=verified_speed` (siembra
   `hasEverReachedDrivingSpeed`, por diseño de arm mid-trip).
2. El fix #1 (45 m/s, acc 5 ≤ 50) pasa `credibleSpeedFix` → **`maxSpeedMps=45` para toda la
   sesión** → `sessionSawDriving=true` en el evaluador. Un solo fix = "conducción medida".
3. La deriva indoor captura y CONGELA el ancla (el flag sembrado la deja pasar por parada
   drive-entered) en el borde de una excursión de deriva, a ~30 m de la posición real.
4. 7 min de deriva en banda peatonal (0.7–2.9 m/s, acc ≤ 50) acumulan
   `kinematicEgressFixes ≥ 6` y la distancia de deriva al ancla supera el suelo de egress
   (18 m) → `hasEgressDisplacement`.
5. 15:04:35 → `DECISION CONFIRMED kinematic+egress` (0.85) → **pin en casa con 1 paso**.

## Agujero

La vía kinematic (DET-KINEMATIC-EGRESS-001, contador mudo) está exenta a propósito del techo
peatonal y de los pasos; su única defensa es `sessionSawDriving` = `maxSpeedKmh ≥ 18`. Y
`maxSpeedMps` se alimentaba de **cualquier fix aislado** con Doppler alto y precisión declarada
credible — sin comprobar si el móvil recorrió terreno de verdad. DET-CREDIBLE-DRIVE-001 ya
estableció el principio ("believes no single fix — the corroboration is the track itself") para
el veredicto persona/coche, pero la estadística de sesión seguía creyéndose el Doppler desnudo.

## Fix (el invariante en UN sitio: la ingesta de la estadística de conducción)

`maxSpeedMps` — la respuesta a "¿esta sesión midió conducción?" — queda a CERO hasta que el
**track** prueba una conducción (`driveProven`, latch de sesión); entonces se promociona el peak
credible acumulado (`pendingMaxSpeedMps`), así que una sesión probada reporta el mismo vmax de
siempre. La prueba (`corroboratesDrive`, sobre un ring acotado de fixes recientes) se evalúa en
cada fix credible (acc ≤ 50) a velocidad de conducción (≥ 5 m/s), contra un fix de look-back de
edad 20–60 s (`driveProofWindowMinMs..MaxMs`):
- desplazamiento neto > ambas envolventes de precisión + `credibleDriveHopMarginMeters`;
- desplazamiento neto ≥ `minimumTripDistanceMeters` (150 m — terreno de viaje, no de deriva);
- ritmo de ventana ≤ `sustainedDepartureMaxRateMps` (los teleports de caché declaran ritmos absurdos);
- **progresión interna**: los fixes de la SEGUNDA mitad de la ventana deben haber dejado ya el
  origen (≥ 25 % del desplazamiento) — la firma del espejismo es *plano-y-salto* (todos los fixes
  en casa y el "movimiento" solo en la ráfaga); una conducción real progresa por su ventana.

Calibrado contra las trazas de campo del harness de replay (las 4 que un diseño por-hop rompía):
- **Calle Gavia** (detección CORRECTA, stream disperso): toda la conducción es UN salto de 255 m
  en 36 s sin testigos intermedios → pasa (ventana sin fixes internos = stream disperso legítimo).
- **Enamorados** (MIUI degradado): NINGÚN hop individual escapa sus envolventes conjuntas
  (~50 m reales vs ~60 m de ruido en cada hop), pero las ventanas de 25 s cubren ~200 m → pasa.
- **Espejismo de casa**: la ráfaga murió a los 10 s de sesión — no tiene ventana; un espejismo a
  mitad de sesión cae por progresión interna (sus fixes tardíos siguen en casa).

Herencia automática (sin tocar consumidores):
- `EvaluateParkingDecisionUseCase.sessionSawDriving` (gate de la vía kinematic + política de
  evidencia débil + guard scooter) — la vía kinematic muere para esta sesión.
- Save desatendido (`measuredDriving`, línea del timeout) y zona honest-close.
- `tripMaxSpeedMps` persistido (guard DET-SOLID de ConfirmParking + repark guard + honest-close).
- Veto de step del enter-arm (DET-SOLID B4).

NO se toca: la siembra del arm (evento nomina; verified arm mid-trip sigue sembrando
`hasEverReachedDrivingSpeed` para lifecycle/freeze), el summary forense de Firestore
(`FirestoreDetectionEventLogger` computa vmax/drivingFixes desde los fixes crudos — el glitch
sigue siendo visible en telemetría), y el clear de anclas por fix credible (LOC-002).

Con el fix, la sesión del FP muere en `aborted_unattended_no_drive` (nudge, no pin) — igual que
las otras 3 armadas falsas del mismo fin de semana que SÍ abortaron bien.

## Ficheros tocados
- `ParkingDetectionConfig.kt` (+2 campos: `driveProofWindowMinMs`, `driveProofWindowMaxMs` +
  validaciones; reutiliza `minimumTripDistanceMeters`, `credibleDriveHopMarginMeters`,
  `sustainedDepartureMaxRateMps`, `minGpsAccuracyForDriving`)
- `CoordinatorParkingDetector.kt` (estado `driveProven`+`pendingMaxSpeedMps`+`recentFixes`,
  helpers `corroboratesDrive`/`pruneRecentFixes`, ingesta de `maxSpeedMps` gated, 3 consts
  companion)
- `CoordinatorParkingDetectorTest.kt` (test replay del espejismo de casa + helper
  `emitCorroboratedDrive` para los 6 fixtures cuyo setup necesita conducción medida)
- `docs/detection/PARKING-DETECTION.md` (changelog)

Suite prod completa verde (958 tests), incluidos los 11 replays de trazas de campo.

## Pendiente
- ⏳ Commit (esperando go-ahead), APK ambos móviles, field-test (repro imposible a demanda —
  vigilar que los viajes reales sigan confirmando con vmax corroborado en el summary).
