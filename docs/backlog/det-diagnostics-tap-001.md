# DET-DIAGNOSTICS-TAP-001 · P3.12 — un dedup que nunca fue un dedup

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-DIAGNOSTICS-TAP-001-p3-12` ·
worktree `../Paparcar-stage-12`

Paso **P3.12**. Sigue a `895d4a59` (P3.11).

## Qué entra

`DetectionDiagnosticsTap` pasa a ser **el único emisor**: sostiene el id de sesión, tira lo que no
pertenece a ninguna, y posee los marcadores de una-vez-por-sesión. El ejecutor y el coordinator pasan
los dos por él, así que *«¿habló esta rama?»* tiene un solo sitio donde mirarse.

## Primero, una corrección al enunciado del plan

El plan dice que **las 15 ramas mudas dejan de existir como ramas**. No pueden, y no deben, en este
paso:

- Esas quince están en el **SERVICIO y los workers**, no en el bucle.
- Las **2–6 ya las tapó el libro de triggers** (`fb817e19`, `DET-EVERY-TRIGGER-LEAVES-A-TRACE-001`).
- Y el propio plan lo zanja: *«por defecto el tap replica exactamente la superficie remota actual.
  Ampliar qué se emite es P4.2, no este paso. Cero cambio observable aquí.»*

Las ramas mudas **del coordinator** se fueron haciendo hablar de una en una entre P3.1 y P3.10, por
el canal de notas y por los efectos tipados del hold.

Así que este paso es **la puerta única y los dedups**, no telemetría nueva. Cero cambio observable
es el criterio, y se cumple.

## El hallazgo: uno de los dos «dedups de log» no es un dedup

| Marcador | ¿Qué es de verdad? |
|---|---|
| `loggedPedalCadence` | **dedup puro** — nadie más lo lee, existe para que la traza lleve una línea y no una por fix → pasa a ser un `Latch` con nombre en el tap |
| `jamExtensionLogged` | **NO** — el presupuesto de no-movimiento elige `aborted_no_movement_jam` sobre `aborted_no_movement` **a partir de él** |

El segundo es un **INPUT DE VEREDICTO con nombre de logging**. Es el outcome distinto de
[DET-JAM-WINDOW-001], dejado como instrumento para dimensionar esa cohorte.

Meterlo en el tap habría **enterrado una decisión dentro del diagnóstico**. Se queda con el bucle que
decide, y la trampa queda escrita donde la va a encontrar el siguiente — en el KDoc del propio tap,
no en un ticket que nadie abrirá.

## El guardrail del hold siguió al código por tercera vez

**coordinator → ejecutor → tap.** Y las tres veces se movió **la comprobación**, no el código de
vuelta.

La propiedad —**una sola puerta**— no cambió nunca; solo cambió la dirección. Esa distinción está
ahora en el KDoc del propio test, porque **un guardrail que se «arregla» bajándole el listón es peor
que no tenerlo**.

## El bonus medible

`PaparcarLogger.d` gana un overload **`inline` perezoso** guardado por `Napier.isEnable`.

El bucle de detección monta **~47 strings interpolados por fix** haya o no un antilog de debug
instalado. En un build de release eso son unas decenas de builders desechables **por muestra de GPS**,
durante todo un viaje, en un dispositivo al que la feature ya le está pidiendo mantener la radio
despierta.

`inline` es load-bearing: sin él la lambda es una asignación propia y el arreglo queda en tablas.

## Verificado discriminante

| Neutralización | Resultado |
|---|---|
| que los marcadores se filtren entre sesiones | 🔴 `should_forget_its_markers_when_a_new_session_opens` |

Esa fuga silenciaría un veto de bicicleta real en el **segundo viaje del día**.

## Doctrina

Ninguna tocada. **Cero cambio observable.**

## Tests

`DetectionDiagnosticsTapTest` (5). **1.636 tests**, 0 fallos. **6** de precedencia, **18** replays y
los **4** guardrails verdes, sin editar un assert. `assembleMockDebug` ✅.

---

## Lo que queda: P3.13

El último paso de la Fase 3, y el de más riesgo: **ensamblar el coordinator nuevo y BORRAR el
viejo**. Su criterio de aceptación es el de toda la fase — los ~3.350 líneas de tests del coordinator
y los replays pasan **sin un assert editado**.

Tres cosas que esa sesión necesita saber y no están en el plan:

1. **El snapshot muerde** — tres veces ya (P3.1 `pendingConfirm`, P3.3 la línea de frescura, P3.6 la
   atribución de vehículo). **P3.13 es su cura**: el bucle de un solo escritor.
2. **`sessionOutcome` y `completed` siguen FUERA del estado** a propósito: deben sobrevivir al
   `reset()` del `finally`. Se mudan **juntos** cuando P3.13 haga explícito el snapshot de fin de
   sesión — está prometido desde P1.11 y P2.2.
3. **El canal de notas de `StageVerdict` es `List<String>` provisional.** Nació en P3.1 porque el
   tap estaba programado el último y la primera etapa ya lo necesitaba. Con el tap ya en su sitio,
   P3.13 (o P4) puede tiparlo.

Y una regla de método que este refactor se ganó a pulso en P3.11: **transcribir I/O de memoria no es
una operación segura.** Mover texto, no reescribirlo.
