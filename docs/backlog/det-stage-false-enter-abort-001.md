# DET-STAGE-FALSE-ENTER-ABORT-001 · P3.9 — el único sitio donde se pasa por encima del usuario a propósito

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-FALSE-ENTER-ABORT-001-p3-9` ·
worktree `../Paparcar-stage-9`

Paso **P3.9**. Sigue a `502d92bf` (P3.8).

## Qué mueve

[BUG-FALSE-ENTER-WALKING] El reconocedor de actividad dispara un `IN_VEHICLE` ENTER **mientras el
usuario camina** —el caso clásico: acaba de bajarse del coche cargado de bolsas, a paso vivo— y la
sesión que se abre no tiene coche dentro.

Los pasos contados **ANTES** de cualquier velocidad de conducción son la refutación. Sin esto, la
misma sesión corre el presupuesto entero (~4 min) con la notificación de servicio en primer plano
pegada, y se repite cada vez que el AR vuelve a fallar.

## Por qué supera al propio toque del usuario

`should_abort_the_false_enter_even_when_the_user_already_said_yes` (P0.1) fija esta adyacencia, y
conviene enunciar la razón sin rodeos porque es **el único sitio del sistema donde se pasa por encima
del usuario a propósito**:

> **Un toque no puede hacer que un viaje haya ocurrido.**

Quien contesta «sí, he aparcado» a un prompt de una sesión que nunca tuvo coche está contestando
sobre **una sesión distinta de la que preguntó**. Guardarlo planta un pin donde la persona está de
pie, que es exactamente el fallo que la doctrina de fallo asimétrico existe para evitar: *mejor un
falso negativo que una plaza fantasma*.

No es desconfiar del usuario. Es la sesión admitiendo que no tenía nada que preguntar.

## Lo que esta etapa NO debe convertirse en

Lee `driveAuthorized`, la **NOMINACIÓN**. Así que una sesión cuyo arm SEMBRÓ la autorización por
confianza **nunca llega aquí**.

Eso no es un descuido: el seed significa que un worker de departure dijo que la conducción ya ocurrió,
y los pasos DESPUÉS de una conducción real son la caminata de egress, no una refutación.

Y si un dismissal retira ese seed más tarde [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001], este guard
**se re-arma con los pasos ya contados** — que es precisamente lo que necesitó la sesión del
2026-08-22, con nueve pasos peatonales contados dentro de casa antes de que llegara el veredicto.

Queda escrito en el KDoc porque es el tipo de cosa que alguien "arregla" convirtiéndolo en una lectura
de la prueba de conducción, y con eso rompe las dos mitades a la vez.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. **6** de precedencia (P0.1) verdes, **18** replays verdes.
**1.629 tests**, 0 fallos. Coordinator **−8 líneas**. `assembleMockDebug` ✅.

**Nueve de diez etapas movidas.** Queda **P3.10**, `HoldResolutionStage`: la PRIMERA de la
precedencia, la que manda sobre todas, y la más enredada — resuelve el confirm retenido, tiene su
watchdog hermano comparando por IDENTIDAD, y sus ramas mudas fueron `DET-HOLD-BRANCHES-MUST-SPEAK-001`.
