# DET-WALK-FLOOR-001 — suelo de distancia para el presupuesto de pasos + escudo del pin asertado por el usuario

**Estado:** ✅ implementado 2026-07-27 en `feature/DET-WALK-FLOOR-001` (encima de DET-FROZEN-COUNTER-001) · suite prod verde · ⏳ commit / APK / field-test
**Origen:** field-test 2026-07-26 tarde (glorieta Juan de Austria, Jerez) — primera noche con la build DET-FROZEN-COUNTER-001.

## Forense (telemetría pap-26, evento HONEST_CLOSE nuevo — el "sin zonas mudas" funcionó a la primera)

### FP — el honest-close pisa un pin MANUAL por el margen de UN paso (Oppo, 20:28, sesión `1785090518031`)
- 20:16:52 el user coloca el pin MANUAL correcto sobre el coche (36.604657,-6.230782, doc `81d15f01`)
  con el cuerpo a ~90 m (colocado en el mapa). El seal del presupuesto queda en el CUERPO (correcto,
  DET-STEP-BUDGET-ORIGIN); el pin queda lejos del seal — divergen legítimamente.
- 20:28 false-ENTER aborta bien en 9 s (13 pasos de detector, 0 conducción, vmax 2 km/h). El
  honest-close corre y su traza dice: `pinDistance=100.4` (pasa el suelo de 80 medido AL PIN),
  `walkDistance=31.8` (seal→abort), `requiredSteps=17`, `stepsDelta=16`, liveness OK (16 ≥ 13).
  16 < 17 → `trip_proven` → `closed_approximate_pin` en la posición del PEATÓN (doc `914a0474`),
  **soltando el pin manual correcto de 12 minutos**.
- Knock-on: la valla se recentró 100 m lejos del coche real para la siguiente salida.

### Agujeros (los tres, con un fix cada dos)
1. El gate de viaje mínimo se medía contra el PIN; el presupuesto contra el SEAL. Con un pin
   colocado en remoto divergen ~90 m: "pin lejos" no implica "cuerpo desplazado".
2. Sin suelo absoluto en el desplazamiento del cuerpo, a 31.8 m el requerido (17) queda dentro del
   ruido de cuantización del contador (16 contados) — el veredicto se decide por UN paso.
3. Una aserción del usuario (pin manual / "Sí, he aparcado") se soltaba por una inferencia
   estadística — violación de la asimetría (mejor FN que FP).

## Fix (dos gates de admisibilidad en `EvaluateHonestCloseUseCase`, cero maquinaria nueva)

- **Suelo del andar (`walk_too_short`)**: si el desplazamiento seal→abort ≤ envolventes de
  precisión + `honestCloseMinTripMeters` (misma vara que el guard too-close aplica al pin), el
  déficit de pasos NO es prueba de viaje → silencio. Se evalúa tras `walk_explains` (la razón más
  específica gana) y solo intercepta el que iba a ser `trip_proven`.
- **Escudo de pin asertado (`user_asserted_pin`)**: pin rancio con `detectionReliability ≥
  config.reliabilityUserConfirmed` (1.0 la estampa SOLO la confirmación del usuario; BT 0.95 y
  vehicle-exit 0.90 quedan debajo por invariante de config) → la escalera calla salvo conducción
  MEDIDA (`session_measured_driving`, evaluada antes del escudo). La red de seguridad sigue de
  backstop si el coche se fue de verdad.
- Telemetría: ambas razones viajan por `HonestCloseVerdict.reason` → evento `HONEST_CLOSE`
  (columna string existente, sin cambio de wire).

## Ficheros tocados
- `EvaluateHonestCloseUseCase.kt` (2 gates + 2 reasons + KDoc)
- `EvaluateHonestCloseUseCaseTest.kt` (+3: glorieta con pin AUTO → walk_too_short; glorieta con
  pin manual → user_asserted_pin; pin asertado + conducción medida → se suelta)
- `docs/detection/PARKING-DETECTION.md` (changelog)

## Pendiente
- ⏳ Commit (esperando go-ahead), APK, field-test (repro: pin manual colocado a distancia + paseo
  corto alrededor de la valla → el pin manual debe sobrevivir).
- Ticket hermano diferido: `docs/backlog/det-gap-anchor-001.md` (pin Redmi 72 m off por hueco GPS).
