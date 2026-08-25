# DET-PHYSICS-SESSION-OUTCOME-001 · P1.11 — la etiqueta del desenlace deja de ser un string

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-PHYSICS-SESSION-OUTCOME-001-p1-11` ·
worktree `../Paparcar-physics-11`

Paso **P1.11**, el último de la Fase 1 y el único `L`. Sigue a `44f8ba5d` (P1.10).
Cierra el **bug #3** y, por construcción, el **bug #5**.

## El problema

Tres consumidores hacen tres preguntas distintas a la etiqueta terminal de la sesión, y los tres se
la hacían a un **String**:

| Consumidor | Pregunta | Cómo la hacía |
|---|---|---|
| el save de zona desatendida | ¿guardó de verdad? | `startsWith("confirmed_")` |
| la escalera del honest-close | ¿fue un abort silencioso? | igualdad contra dos constantes |
| el amortiguador de sentry-wake | ¿fue un abort andando? | igualdad contra las MISMAS dos |

La pertenencia la decidía **cómo estaba escrito el string**. Un solo nombre concedía o negaba tres
conductas a la vez, en silencio.

Y ya había pasado: `aborted_no_movement_jam` salió del set del honest-close **y** del set del streak
en el instante en que se introdujo, sólo porque dejó de ser igual a `aborted_no_movement`. Las dos
exclusiones resultan correctas —el barrido del 21-08 sobre 1.359 sesiones dio la cohorte de atasco
**VACÍA**, así que la pregunta del honest-close sigue siendo indecidible con datos y la exclusión se
mantiene (09 §14.4)— pero **nadie las eligió**.

## Qué hace

`physics/SessionOutcome.kt`: diez ramas, cada una declara sus tres pertenencias, **sin defaults**.
Un desenlace nuevo no compila hasta que su autor responde a las tres.

El efecto sobre el streak es un `enum SentryStreakEffect { EXTENDS, RESETS }` y no un booleano por
la misma razón: `resetsSentryStreak` era el `else` implícito de un `when` y no estaba declarado en
ninguna parte, pese a que `stopped_by_user` cae ahí por un motivo real
([DET-STOP-BUTTON-001]: no es una nominación refutada, es la máxima autoridad del sistema hablando).
Un booleano dejaría que la siguiente rama heredase `false` callándose — el accidente exacto que el
tipo existe para impedir.

El `when` del amortiguador queda partido como siempre se leyó: **pertenencia declarada**, luego
**cadencia medida**.

## El riesgo real, y cómo se acota

La serialización tiene que ser **byte a byte idéntica**: estos strings son contrato de trazas y los
diagnósticos de campo de `docs/backlog/` los citan. `SessionOutcomeTest` fija cada valor contra el
literal al que sustituye, **escrito a mano** y no derivado del tipo — un test que leyera
`SessionOutcome.Ended.serialized` y lo comparase consigo mismo pasaría cualquier renombrado.

### Verificado discriminante, no supuesto

| Neutralización | Resultado |
|---|---|
| renombrar `confirmed_` → `confirm_` | 🔴 **13 tests**, 11 de ellos preexistentes (CPD + replays) |
| devolver el atasco a los dos sets de pertenencia | 🔴 3 tests, **los 3 nuevos** |
| añadir una rama al sealed, completamente declarada | 🔴 el censo del test no compila |

Las dos primeras dicen cosas opuestas y las dos importan:

- El contrato de cable **ya estaba protegido por la suite** — los replays y los CPDTest afirman
  sobre las cadenas, así que un renombrado no podía colarse. El golden file añade el aviso legible,
  no la seguridad.
- La pertenencia del atasco **no tenía ningún test**. Por eso pudo volverse accidental: no había
  nada que se pusiera rojo cuando cambió.

## Lo que cae de propina

`UnattendedSaveReason.abortedOutcome` construye ahora su etiqueta a través del sealed, así que el
servicio ya no re-escribe `"aborted_unattended_gap_anchor"` a mano. Renombrar esa key cambiaba el
productor y **dejaba de casar con el consumidor en silencio**, reabriendo `DET-BACKFILL-TAINT-001`
sin que nada fallase.

Y **no hay `parse`**, a propósito: nada convierte un string de vuelta en tipo (el coordinator los
PRODUCE y reparte `serialized` para telemetría), así que no hace falta inventar una rama `Unknown`
que tendría que adivinar sus tres pertenencias.

## Lo que NO arregla, a propósito

`aborted_unattended_human_powered` sigue teniendo **dos productores** con un solo nombre (el
response-timeout de 15 min y el cierre temprano de [DET-HUMAN-POWERED-EARLY-CLOSE-001]), y la
convención de nombre codifica una procedencia que sólo es cierta para uno. Tipar la etiqueta no lo
resuelve y no debe fingir que sí: es el **bug #7** y tiene ticket propio. Los dos siguen emitiendo
la misma cadena para que las comparaciones de campo contra sesiones de bici anteriores sigan
cuadrando.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

`SessionOutcomeTest` (7). **1.535 tests** (1.528 + 7), 0 fallos, **0 asserts editados**.
`assembleMockDebug` ✅.

Los 12 call sites de `SentryWakeCooldownTest` pasan de constante a rama del sealed — argumentos, no
asserts: los números esperados de streak no se tocan.

## Red de P0.4

```
tests 1535 - desaparecidos: 5 (los renombrados de P1.8, ya justificados) - nuevos: 86
```

## Fase 1 cerrada

Once pasos, `36d91bd1` → aquí. Criterio de aceptación de la fase cumplido: **la suite pasa sin
editar un solo assert** en ninguno de los once.

Siguiente: **Fase 2**, los sub-estados uno por commit (`state/SessionTelemetry.kt` primero).
