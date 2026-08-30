# DET-TWO-TIER-SENTRY-001 · un despertar compra un fix, no una sesión

**Estado:** ✅ Done · rama `feature/DET-TWO-TIER-SENTRY-001-two-tier-sentry` · worktree
`../Paparcar-two-tier` · apilada sobre `DET-FAIL-CLOSED-BY-CONSTRUCTION-001`

Pieza 5 del rediseño. Es la que cambia **batería y escala**, y la que el propio §1.4 señaló como el
reencuadre del problema: *no tenemos un problema de confirmación, tenemos un problema de armado*.

## Problema

Medido sobre el `parkdiag` de la noche del 29→30-08 (Redmi, 4 h 14 min, coche parado en la calle):

| medida | valor |
|---|---|
| intents `ACTION_SENTRY_WAKE` | **61** |
| frenados por el cooldown | 37 |
| que **armaron sesión completa** | **24** |
| armados totales de la noche | 28 (86 % desde significant motion) |
| de ellos, `⊘ false-ENTER abort` (el usuario andando) | **23** |
| pines útiles | **1** |

Los `honest close` de esos abortos sitúan al usuario entre **22 y 69 m** del coche, dentro de su
propia valla de **89 m**, a **0-4 km/h**. Cada armado es una sesión FGS con GPS a cadencia alta, dos
documentos en Firestore y **un billete de lotería para un espejismo de arranque en frío** — que es
justo como se coló el FP de la parafarmacia.

## El hallazgo: la puerta ya existía y no estaba puesta

`SentryWakeTriage` —el nivel 1— **ya estaba implementado y probado**. Sólo que corría
`if (triageOnly && …)`, es decir **únicamente dentro de un periodo de silencio**, después de que una
racha de abortos hubiera abierto uno. Fuera de él, cada despertar compraba una sesión entera.

Las 24 sesiones de esa noche fallan **las dos** pruebas de escalado (ni velocidad de conducción
creíble, ni cuerpo fuera de la valla). Cada una habría costado **un fix**.

## Diseño

Una línea de conducta: **el triage pasa a ser la única puerta**.

- **Nivel 1 — triage.** Despierta con todo. Un fix, dos preguntas: *¿velocidad de conducción
  creíble?* y *¿fuera de toda valla propia?* Sin stream, sin máquina de estados, **sin derecho a
  plantar**.
- **Nivel 2 — sesión.** Sólo por promoción del nivel 1. Es el único que enciende GPS a cadencia alta
  y el único con derecho a confirmar.

⚠️ **Las asimetrías que impiden que esto silencie una salida real son las del propio triage, y no se
tocan**: un fix que no llega **ESCALA**, un cuerpo fuera de toda valla propia **ESCALA**, y velocidad
de conducción creíble **ESCALA**. *Fallar hacia el ruido cuesta una sesión; fallar hacia el silencio
cuesta una plaza.*

`triageOnly` sobrevive como bandera de telemetría —dice si el despertar venía de un periodo de
silencio— pero ya no decide nada.

## La métrica, en la traza

La pieza se juzga por **promociones a nivel 2 por viaje real** (medido: 28/1; objetivo ≈1). Para que
eso sea contable sin cable, cada triage escribe en qué nivel se queda:

```
⏱ cheap wake triage [ordinary] → STAY_QUIET, stops at tier1 (one fix) (speed=1km/h acc=12m)
⏱ cheap wake triage [quiet-period] → ESCALATE, stops at tier2 (session) (speed=58km/h acc=11m)
```

y el evento remoto `WAKE_TRIAGE` lleva el mismo veredicto más el carril.

## Consumidores auditados

| sitio | clasificación |
|---|---|
| `handleSentryWake` | **cerrado** — el triage es la puerta |
| `cheapWakeVerdict` / `mayTriageSentryWake` | **sin tocar** — eran correctos; lo que fallaba era dónde estaban puestos |
| `DetectionTrigger.GEOFENCE_EXIT` · `AR_VEHICLE_ENTER` | **exentos**: otros carriles, con sus propias puertas. La noche del 22-08 el viaje se cazó por `GEOFENCE_EXIT` cuando el damper silenció al sentry — esa redundancia es deliberada y sigue |
| Significant motion (el sensor) | **exento por diseño**: no se toca. Es lo que nos salva de los FN, y cuesta ~cero armado |
| `ParkingSafetyNetWorker` | **exento**: otro reloj, otra puerta |

## Follow-up que este ticket destapa

🟡 **El cooldown (`SentryWakeCooldown`) pierde casi toda su justificación.** Existía para frenar la
tormenta de armados; ahora un armado ya no se compra con un despertar. Y tiene un coste conocido: el
22-08 silenció el sensor exactamente como estaba diseñado y el viaje se salvó **por otro carril**.
El propio KDoc del triage ya decía que el damper era *«the wrong axis»*. Retirarlo o rebajarlo es un
delta de conducta propio y **necesita un viaje real que lo mida** — no se hace a ciegas aquí.

🟡 Los **dos armados espurios** de `DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001` mueren en este triaje,
como anticipaba el cruce §9.3. El resto de su mitad de coste es otro worker.

## Criterio de éxito

- ✅ Replay de la noche: los 24 despertares (22-69 m, 0-4 km/h, dentro de la valla) → `STAY_QUIET`.
- ✅ Y su mitad contraria, para que esto no se confunda con «despertar menos»: el viaje real de las
  21:47, el cuerpo fuera de la valla y el fix que no llega → `ESCALATE`.
- ✅ **1.826 tests en verde**, `:app:compileProdDebugKotlin` en verde.
- ⏳ **La medida de verdad es de campo**: promociones a nivel 2 por viaje real, contadas sobre el
  `parkdiag` de la próxima noche con el coche parado.
