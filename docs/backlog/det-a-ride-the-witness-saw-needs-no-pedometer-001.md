# DET-A-RIDE-THE-WITNESS-SAW-NEEDS-NO-PEDOMETER-001 · 580 m en 74 s entre dos testigos no necesita contador de pasos

**Estado:** 🔵 En progreso · rama `bugfix/DET-A-RIDE-THE-WITNESS-SAW-NEEDS-NO-PEDOMETER-001-witnessride`
· worktree `../Paparcar-witnessride` · abierto 01-09-2026 sobre master `9aa00c6`

## Problema

Field 31-08 T3 (Redmi, Góndola — el aparcamiento que se perdió en LOS DOS móviles). Del `parkdiag`:

```
22:33:10  SafetyNet: sigues junto al coche (d=50m, radio 150m)      ← testigo: cuerpo EN el pin
22:33:58  ExitWitness: ⚑ EXIT emitted loc=36.6086805,-6.2780963    ← ya en Góndola (¡48 s después!)
22:34:20  ⊘ false-ENTER abort — 16 steps before driving speed
22:34:24  ⑊ honest close: stayed silent (stale_seal; pinDist=580m steps=null/null sessionSteps=22)
22:34:42  ⑊ honest close: stayed silent (stale_seal; pinDist=581m …)   ← segunda pasada, ídem
```

**~530 m en 74 s = ~7 m/s sostenidos entre dos observaciones independientes.** Ningún peatón hace
eso. El viaje estaba PROBADO por física — y el honest close se calló igual, porque su única vara es
el presupuesto de pasos y el contador del Redmi estaba MUDO (`cumulative steps read → 0 → treated
as unknown` toda la noche): sin sello utilizable → `stale_seal` (la rama `sealAgeMs == null`, no la
caducidad — el pin tenía 54 min y el tope son 2 h) → silencio. Resultado: cero artefacto en Góndola.

## Doctrina

- El propio KDoc de `EvaluateHonestCloseUseCase` lo declara deuda: *«The AR-boarding /
  pedestrian-physics proofs for mute counters are a documented follow-up»*. Este ticket es esa
  mitad (la física); el T3 es su caso de campo.
- Fallo asimétrico intacto: el peldaño nuevo produce ZONA + nudge (nunca pin exacto, nunca en
  silencio hacia el usuario final: el honest close ya nudgea sus artefactos).

## El dato clave: los insumos YA llegan al evaluador

`EvaluateHonestCloseUseCase` ya recibe `lastWitnessedFix` + `witnessAgeMs` (el slot en disco que el
safety net refresca — a las 22:33:10 estaba recién escrito) y hoy los usa SOLO en negativo
(`unwitnessed_displacement`: refutar el abort fix cuando implica velocidad imposible). El mismo par
sirve en positivo, y la banda queda definida por los dos guards existentes:

> desplazamiento testigo→abort **> alcance peatonal** en `witnessAgeMs` (con envolventes de
> precisión — `isBeyondPedestrianReach`, la misma física que ya usan el fall-through de boarding y
> el ride-proof) **y < techo físico** (`honestCloseMaxImpliedTravelSpeedMps`, que ya corre antes)
> ⇒ el cuerpo fue TRANSPORTADO desde donde el testigo lo vio ⇒ viaje probado sin contador.

## Diseño

- Peldaño nuevo en `EvaluateHonestCloseUseCase`, DESPUÉS del guard `unwitnessed_displacement`
  (que ya descartó el teleporte) y ANTES de `stale_seal`/`mute_counter` (los que hoy silencian este
  caso): `REASON_WITNESS_RIDE = "witness_ride"` con los números en el veredicto (witnessDistance,
  witnessAge, la cota peatonal calculada).
- Artefacto SIEMPRE `ApproximateZone`, nunca pin: sin pasos no hay cota de la caminata posterior a
  dejar el coche; el radio usa la duda que sí está medida (accuracy + alcance peatonal en la
  ventana del testigo), con el clamp `honestZoneRadius` existente.
- Sin `fenceRadiusMeters` en esta física (no hay valla en juego): slack = accuracies de ambos
  extremos, vía el mismo `isBeyondPedestrianReach` con `fenceRadiusMeters = 0f`.

## Implementación (01-09) — un matiz que el plan no tenía

⛔ **Fallback, no peldaño previo.** La primera versión colocaba el witness-ride ANTES del
presupuesto de pasos, y un test EXISTENTE la refutó
(`should_prove_the_trip_when_the_displacement_sits_under_the_implied_speed_ceiling`): con contador
VIVO y testigo a la vez, preemptar degrada el artefacto (el presupuesto con 0 pasos gana un PIN;
la física del testigo solo puede dibujar zona). Forma final: `witnessRide` se computa tras el gate
de coherencia y se devuelve SOLO donde el contador se rinde — `stale_seal`, `mute_counter`,
`frozen_counter`, `no_seal_origin`. Un contador que contesta (walk_explains, walk_too_short,
trip_proven) sigue siendo el juez.

- Condición cargante: el testigo debe estar DENTRO de la valla del pin (`isWithinFence`) — sin
  ella, cuerpo visto en casa + bus = liberar un pin que el coche nunca dejó (clase D2-return). Con
  ella, el sobre residual (bus abordado junto a tu coche) es el mismo que el presupuesto ya acepta.
- Física: `isBeyondPedestrianReach(d_testigo→abort, witnessAgeMs, fence=0, acc ambos extremos)`;
  techo ya puesto por `unwitnessed_displacement` (15 m/s) que corre antes.
- Artefacto: SIEMPRE `ApproximateZone`; radio = alcance peatonal en la ventana del testigo +
  accuracy, clamp `honestZoneRadius` (60–250 m).
- `REASON_WITNESS_RIDE = "witness_ride"`, con witnessDistance/witnessAge en el veredicto.

## Criterio de éxito

1. ✅ Replay del T3: witness at-car 74 s + abort 580 m + steps null/null → `ApproximateZone`
   `witness_ride` (antes: silencio `stale_seal`). Radio dentro de [60, 250].
2. ✅ Siguen callando: testigo LEJOS del coche (bus-desde-casa → `stale_seal`), paseo cubrible en
   la ventana (580 m/10 min → `stale_seal`), teleporte (`unwitnessed_displacement`, test previo
   intacto), y el contador vivo sigue mandando (test previo intacto + test nuevo explícito).
3. ✅ Falsaciones: sin `witnessAtTheCar` → rojo el bus-desde-casa; sin el fallback en `stale_seal`
   → rojo el replay T3. Suite completa **2088/0**, prod+mock compilan.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `RunHonestCloseUseCase` / servicio | sin cambios: mapean por TIPO de decisión (`ApproximateZone` ya existente); el reason viaja en el veredicto y llega a telemetría como hasta ahora |
| Telemetría `HonestClose` | tiene campo `reason` propio, cableado desde `verdict.reason` (`CoordinatorDetectionService:1259`) — `witness_ride` aparece sin tocar nada |
| Guard `unwitnessed_displacement` | corre ANTES y define el techo de la banda — intacto, su test previo verde |
| Guard `user_asserted_pin` | corre ANTES: la aserción sigue por encima de esta inferencia |
| Peldaños del contador | intactos; el fallback solo ocupa sus CUATRO negativas |
