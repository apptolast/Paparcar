# DET-PHYSICS-PEDESTRIAN-REACH-001 · P1.2 — la familia geométrica ×4 → una fórmula

**Estado:** ✅ Done (2026-08-24) · rama `refactor/DET-PHYSICS-PEDESTRIAN-REACH-001-p1-2` ·
worktree `../Paparcar-physics-2`

Paso **P1.2** de la Fase 1. Sigue a `36d91bd1` (P1.1).

## Qué mueve

El discriminante persona/coche, que es geometría:

```
d(base, fix)  >  pasos × zancada  +  acc(base)  +  acc(fix)  +  suelo
```

Estaba escrito **cuatro veces** en el coordinator, con `(base, pasos, suelo)` distintos. Lo único
que distingue una copia de otra **es el juego de parámetros** (06 §3-b, que ya lo había demostrado):

| Copia | base | pasos | suelo | Incidente de campo que cubre |
|---|---|---|---|---|
| `movementOutrunsSteps` | ancla | `stepCount` | `minEgressDisplacementMeters` | Gavia: 2 pasos espurios no rompen el ancla |
| `egressExceedsWalkReach` | ancla | `stepCount` | `egressBirthFloorMeters` | Calle Abeto 18-07: 26 pasos fantasma + coche a 500 m |
| `heldConfirmOutrunByVehicle` | pin retenido | `stepCount` | `egressBirthFloorMeters` | Osborne/Abeto 23-24/07: el hold liquidaba con el coche a 570 m |
| `escapesAnchorEnvelope` | ancla | **0** | `minEgressDisplacementMeters` | salida sin pasos: contador vivo en silencio + creep del coche |

Las cuatro **siguen existiendo con su nombre y su KDoc** — cada una cita su incidente y cada una
hace falta. Lo que sobraba eran cuatro sitios donde arreglar la aritmética y dejarla mal en tres.

## Decisión de diseño: la función pura no conoce el estado

`physics/PedestrianReach.kt` recibe `(base, fix, steps, stride, floor)` — primitivos y `GpsPoint`,
nada más. Las cuatro con nombre **se quedan en el coordinator** como adaptadores de tres líneas que
desempaquetan `ParkingDetectionState` + `config` y delegan.

La alternativa era llevárselas enteras a `physics/`, y entonces `physics/` dependería del estado del
coordinator — justo lo que la carpeta existe para evitar. Los nombres viven donde viven sus
llamadores; la fórmula vive sola.

`escapesAnchorEnvelope` entra como **el miembro de `pasos = 0`**: no preguntaba *«¿podría haberlo
andado?»* sino *«¿ha salido del envelope siquiera?»*, que es la misma función sin crédito de pasos.

## Frontera que NO se cruza

⚠️ `ParkingDetectionConfig.isBeyondPedestrianReach` **se parece y no es la misma**: construye su
envelope a partir del **tiempo** (`maxPedestrianSpeed × transcurrido`), no de pasos contados.
Unificarlas exigiría su propia demostración y 06 §3-b lo dejó dicho expresamente. Queda fuera, y
anotado en el KDoc de la función nueva para que la próxima pasada no lo intente por parecido.

Es el mismo criterio que en P1.1 con el gate de identidad BT: **el parecido no es prueba**.

## Doctrina

Ninguna tocada. **Cero cambio de conducta** — las cuatro son aritméticamente idénticas, incluida la
de `pasos = 0` (`0 × zancada = 0`).

## Tests

`PedestrianReachTest` (6) — uno por **término**, porque cada término está por un incidente y un
término es exactamente lo que se cae en una simplificación posterior:

- el caso que acusa y el que calla;
- el límite **estrictamente mayor**: justo en el alcance la respuesta es «no probado», que es el
  sesgo pro-persona del proyecto (acusar a un peatón cuesta una plaza fantasma; lo contrario, una
  pregunta);
- **la accuracy degradada infla el alcance** y hace fallar conservador en vez de acusar a un coche
  con ruido de GPS;
- el blip Doppler parado en el ancla, que sin crédito de pasos nunca puede cualificar;
- y **el suelo por sí solo da la vuelta al veredicto** con todo lo demás igual — que es la razón por
  la que las cuatro no se pueden fundir en un helper sin parámetros.

**Criterio de aceptación de la Fase 1 cumplido: la suite pasa sin editar un solo assert.**

**1.465 tests** (1.459 + 6), 0 fallos. `assembleMockDebug` ✅.

## Red de P0.4

```
tests 1465 - desaparecidos vs base: 0 - nuevos: 11 (P1.1 + P1.2)
```

Los 13 replays `Trace_*` pasan intactos, y son la verificación fuerte de este paso: estos cuatro
predicados se ejercitan a fondo en cada traza de campo.
