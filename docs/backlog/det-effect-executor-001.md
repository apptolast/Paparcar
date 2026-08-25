# DET-EFFECT-EXECUTOR-001 · P3.11 — no era una mudanza: el ejecutor tenía que dejar de meter la mano en la sesión

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-EFFECT-EXECUTOR-001-p3-11` ·
worktree `../Paparcar-stage-11`

Paso **P3.11**. Sigue a `6028ec78` (P3.10, cierre de las diez etapas).

## El plan lo describe como una mudanza. No puede serlo.

`runConfirm` **leía cuatro campos del estado vivo** —la prueba de conducción, la evidencia del arm,
el último fix, el reloj de banda— y **escribía dos más**: el `sessionOutcome` y la fase de
confirmación.

Una clase que hace I/O **y** muta la sesión no es un ejecutor: es un segundo coordinator.

## La salida: informar en vez de aplicar

Cada método recibe el estado que necesita **como valor** y devuelve un `EffectOutcome` que el dueño
del estado aplica (`applyEffect`, un solo sitio).

Y eso cierra de paso **un acoplamiento que no se veía**: `saveUnattendedZone` decidía si la zona se
había GUARDADO **leyendo el campo de outcome que el `runConfirm` anidado acababa de escribir**. Dos
funciones hablándose por un campo mutable, sin que nada lo dijera en ninguno de los dos extremos.
Ahora la llamada anidada devuelve su outcome y quien llama lo lee de un valor.

## El criterio de aceptación, hecho cumplir

`StagePurityGuardrailTest` falla si cualquier fichero bajo `stages/` importa un repositorio o el
puerto de notificaciones.

Va con **su otra mitad** —que el ejecutor SÍ siga hablando con un repositorio— porque una prohibición
sola está a un refactor de quedar satisfecha por un paquete vacío.

| Neutralización | Resultado |
|---|---|
| meterle un `import` de repositorio a una etapa | 🔴 |

**La regla no es pulcritud.** Es que *una decisión tiene que poder afirmarse SIN ejecutarse*, y
`runConfirm` decidía y guardaba a la vez — que es exactamente por lo que el orden de las ramas estuvo
sin tests tanto tiempo y por lo que `DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001` no pudo escribir
los tests que se proponía escribir.

## Los dos guardrails del hold volvieron a disparar, y volvían a tener razón

La **puerta única** de la lane es ahora el ejecutor — que es el propósito del paso:
`DetectionEvent.Hold` se construye en exactamente un sitio, y ese sitio es el único que hace I/O en
el núcleo. La comprobación le sigue.

## Dos relojes, separados a propósito

- Las marcas de tiempo de diagnóstico usan el reloj **inyectado** (controlable en tests).
- La marca de la tarjeta de «aparcado» usa el reloj **de pared**, porque se compara contra el inicio
  de una sesión FUTURA para envejecer una notificación: un reloj de test que se reinicia por sesión
  haría que una tarjeta rancia pareciera fresca.

`savedConfirmPostedAt` se muda al ejecutor con eso escrito: es estado de **NOTIFICACIÓN**, no de
sesión, que es exactamente por lo que nunca perteneció al estado de la sesión y por lo que ahora
sobrevive **a propósito** en vez de por accidente [REFACTOR-300-FIX].

## ⚠️ Un aviso que merece quedar escrito

El primer borrador del ejecutor lo escribí **de memoria** y tenía **tres errores de conducta**:

| | Borrador | Real |
|---|---|---|
| score del prompt | `0.65f` | **`0.6f`** |
| `logHold` | pasaba `action.name` | pasa el **enum**, con nullables |
| `degradeToPrompt` | `PROMPT_SHOWN` + `degradedToPrompt()` | **`CONFIRM_DEGRADED_PROMPT`** + **`notified(now)`**, más una línea de log |

Los tres se cazaron releyendo el fuente antes de cablear, y **dos son strings de diagnóstico que la
suite no habría atrapado**.

**Transcribir I/O de memoria no es una operación segura.** Para lo que queda de refactor: mover
texto, no reescribirlo.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. **6** de precedencia (P0.1), **18** replays y los **2** del guardrail del hold,
verdes. `StagePurityGuardrailTest` (2) nuevo.

**1.631 tests**, 0 fallos. Coordinator **2.567 → 2.340 líneas**; el ejecutor son 414.
`assembleMockDebug` ✅.

Siguiente: **P3.12**, `DetectionDiagnosticsTap` — las 15 ramas mudas dejan de existir como ramas y
pasan a ser `DiagnosticNote` con nombre. ⚠️ Por defecto **replica exactamente la superficie remota
actual**: ampliar qué se emite es P4.2, no ese paso.
