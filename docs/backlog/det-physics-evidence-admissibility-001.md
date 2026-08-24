# DET-PHYSICS-EVIDENCE-ADMISSIBILITY-001 · P1.1 — la primera pieza a `physics/`

**Estado:** ✅ Done (2026-08-24) · rama `refactor/DET-PHYSICS-EVIDENCE-ADMISSIBILITY-001-p1-1` ·
worktree `../Paparcar-physics-1`

**Primer paso de la FASE 1** del refactor profundo (`docs/detection/10-plan-refactor.md`, paso P1.1).
Arranca F6 con las cuatro puertas abiertas: G1 campo validado hasta `1a4128d5` (dicho por el user el
24-08), G2 dado, G3 Fase 0 cerrada, G4 la sesión paralela sin tocar master desde el 23-08.

## Qué mueve

El filtro de **admisibilidad por nacimiento de sesión** [DET-SESSION-BIRTH-001]: *evidencia anterior
a la sesión describe el viaje que CREÓ la plaza, nunca la salida de ella*. Vivía como comparaciones
escritas a mano en cuatro casos de uso.

**El plan decía 4 copias. Son 5** — el safety net tiene dos (embarque y salida), no una:

| Sitio | Forma que tenía |
|---|---|
| `EvaluateArEnterArmUseCase` | `enterTrueTimeMs < session.location.timestamp` → rechaza — **escrita del revés** |
| `DetectParkingDepartureUseCase` | `enteredAt >= sessionStartMs` |
| `EvaluateSafetyNetCheckUseCase` (embarque) | `it >= sessionStartMs` |
| `EvaluateSafetyNetCheckUseCase` (salida) | `it >= sessionStartMs` |
| `VerifyDepartureEvidenceUseCase` | `sessionStartMs == null \|\| it >= sessionStartMs` — tolerante a nulo |

Una de ellas del revés y otra con semántica de nulo propia: cinco oportunidades de que la siguiente
edición arregle una y se deje cuatro.

## Lo que NO se colapsa, y por qué

⚠️ `EvaluateSafetyNetCheckUseCase:175` — el **gate de identidad BT**
[DET-BT-IDENTITY-GATE-001] — compara `lastBtConnectedAtMs < sessionStartMs`, que **tiene la misma
forma y hace otra pregunta**: no *«¿es admisible esta evidencia?»* sino *«¿conectó el Bluetooth de
este coche en o después de aparcar?»*. Y su nulo significa **lo contrario**: sin conexión ⇒ el gate
se activa. Colapsarlo por parecido habría cambiado conducta. Queda fuera, dicho en el KDoc de la
función nueva para que la próxima pasada no lo intente.

## La decisión de diseño que había que escribir

La firma que proponía `09` era `isAdmissibleEvidence(evidenceAtMs: Long?, sessionStartMs: Long)`.
**No cubre el quinto sitio**, donde el que puede faltar es el `sessionStart`. Los dos nulos
significan cosas opuestas, y eso es correcto, pero tenía que quedar explícito:

- `evidenceAtMs == null` → no hay evidencia, no hay nada que admitir → **false**. Falla cerrado.
- `sessionStartMs == null` → el nacimiento es desconocido, nada aquí puede refutar la evidencia →
  **true**. Falla abierto: no saber cuándo empezó la sesión no es motivo para tirar una señal real.

Es el fallo asimétrico del proyecto aplicado a un predicado: un falso negativo cuesta una pregunta,
un falso positivo cuesta una plaza fantasma.

## Doctrina

Ninguna tocada. **Cero cambio de conducta**: las cinco comparaciones son aritméticamente idénticas a
lo que hacían, incluida la del revés (`¬(a < b) ≡ a >= b`).

## Barrido de consumidores

`grep` de `sessionStartMs` / `session.location.timestamp` con comparación, en todo `commonMain`:

| Hit | Estado |
|---|---|
| Los 5 de la tabla | **cerrados** — enrutados por la función |
| `EvaluateSafetyNetCheckUseCase:175` (gate BT) | **exento con razón** — otra pregunta, nulo opuesto |
| `CPD:793` (`enterArmStepVetoMs`), `CPD:1332`/`1341` (presupuesto sin movimiento) | **exentos** — son presupuestos de tiempo transcurrido, no admisibilidad |
| `CPD:724` (`SessionStarted`) | **exento** — es la emisión del evento, no una comparación |

## Tests

- `EvidenceAdmissibilityTest` (5) — el límite **inclusivo** (la evidencia que arma una sesión suele
  llevar su propio instante de inicio: un límite exclusivo tiraría justo la señal que la abrió) y
  los dos nulos asimétricos, que son las tres cosas que cada llamador decidía por su cuenta.
- **Criterio de aceptación de la Fase 1 cumplido: la suite pasa sin editar un solo assert.**

**1.459 tests** (1.454 + 5), 0 fallos.

## La red de P0.4, estrenada

Diff de nombres de test contra `docs/detection/P0.4-baseline-tests.txt`:

```
ahora 1459 - base 1454
DESAPARECIDOS: 0
nuevos: 5   (los de EvidenceAdmissibilityTest)
```

El mecanismo funciona: cualquier test que se evapore durante F6 sale en ese diff, no de la memoria
de nadie. **Repetir este diff en cada paso de F6.**
