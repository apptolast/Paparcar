# DET-STAGE-NO-MOVEMENT-BUDGET-001 · P3.8 — la etapa que no implementa la interfaz, y lo dice

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STAGE-NO-MOVEMENT-BUDGET-001-p3-8` ·
worktree `../Paparcar-stage-8`

Paso **P3.8**. Sigue a `2aff3e60` (P3.7).

## Qué mueve

Una sesión que nunca condujo tiene un presupuesto, y aquí se le acaba. Supera a la atribución de
vehículo y a todas las vías de confirmación por la razón que fijó P0.1: **una sesión sin nada medido
está terminada antes de poder ser contestada** — un toque del usuario no puede hacer que un viaje
haya ocurrido.

Tres presupuestos, cada uno con su incidente:

| Presupuesto | Para qué |
|---|---|
| estándar (`maxNoMovementMs`) | un arm espurio corriente |
| **sonda CORTA** (`staleExitNoMovementMs`) | EXIT entregado tarde [DET-ZOMBIE-PROBE-001] — una entrega lejana real a mitad de conducción enseña fixes de conducción en segundos, un zombi no lo hará nunca |
| **EXTENDIDO** (`jamExtendedNoMovementMs`) | atasco [DET-JAM-WINDOW-001] — el coche SÍ salió de la plaza pero repta por debajo de velocidad de conducción pasado el presupuesto, y el plegado silencioso perdía el viaje entero |

**Los zombis de la vía stale nunca reciben la extensión**, que es lo que impide que los dos guards se
anulen entre sí.

## La etapa que NO implementa `SessionStage`

Su decisión necesita tres cosas que la firma común no puede llevar: **qué vía de entrega armó la
sesión**, **cuánto reptó la posición dentro de una ventana deslizante** y **si la extensión ya se
anunció**. Dos de ellas son contabilidad MUTABLE por sesión que el bucle mantiene en cada fix —
incluidos los fixes en los que esta etapa se salta.

Las dos alternativas eran peores:

1. **Inventar un campo de estado por medición** para satisfacer una interfaz es la cola meneando al
   perro.
2. **Implementar `evaluate` como una función que lanza** mete una mentira en el sistema de tipos —
   que es exactamente la clase de defecto que este refactor lleva encontrando: *algo que se lee como
   un contrato y no lo es*.

⚠️ El primer borrador de este paso hizo justo lo segundo. Estaba mal y queda anotado.

Así que se queda como clase normal, con su firma propia y **su sitio en `DetectionStage` declarado**.
Entra en la interfaz cuando la ventana de reptado tenga casa: [09 §5] la programa para ser absorbida
por el anillo de la prueba de conducción, que ya retiene fixes recientes para otra pregunta. Eso es
consolidación de **Fase 4** — dos ventanas con reglas de retención distintas es una fusión que
necesita su propio argumento, no ser pasajera de una mudanza.

## `EndSession` estrena uso

Y de paso le da al vocabulario de desenlaces un viaje de ida y vuelta: el efecto lleva la etiqueta
**SERIALIZADA**, porque eso es de lo que está hecho un contrato de trazas, y **exactamente una**
función la convierte de vuelta en `SessionOutcome`.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

Ni un assert editado. **6** de precedencia (P0.1) verdes, **18** replays verdes.
**1.629 tests**, 0 fallos. `assembleMockDebug` ✅.

**Ocho de diez etapas movidas.** Quedan `FalseEnterAbortStage` y `HoldResolutionStage` — la segunda y
la primera de la precedencia. Siguiente: **P3.9**.
