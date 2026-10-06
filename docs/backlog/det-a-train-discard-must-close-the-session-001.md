# DET-A-TRAIN-DISCARD-MUST-CLOSE-THE-SESSION-001 · desde el timeout, el veredicto «esto no era un coche» no retira la pregunta ni estampa su final

**Estado:** 🔵 Abierto · sin rama · detectado el 06-10-2026 al barrer los consumidores de
`ResponseTimeoutStage` para [DET-A-DEGRADED-PROMPT-MUST-NOT-AUTO-CONFIRM-001]. **Preexistente y de
otro carril**, por eso no se arregló allí.

## Problema

`UnattendedParkingSave.Discard` es el veredicto de [DET-A-TRAIN-IS-NOT-A-CAR-001]: el movimiento fue
de un vehículo que ninguna carretera explica, así que no hay parada de coche que registrar. Su KDoc
dice *«Record nothing, ask nothing, end the session»*, y las otras dos etapas que lo alcanzan lo
cumplen emitiendo `DetectionEffect.CloseNotARoadVehicle`:

```
CandidateStage:117            ParkingDecision.CloseNotARoadVehicle -> DetectionEffect.CloseNotARoadVehicle(...)
ConfidenceScoringStage:101    ParkingDecision.CloseNotARoadVehicle -> DetectionEffect.CloseNotARoadVehicle(...)
ResponseTimeoutStage          is UnattendedParkingSave.Discard     -> null        🔴
```

Desde el timeout el efecto es `null`, con el comentario *«The session still ends below with every
other one — what it does not do is speak»*. Pero `closeNotARoadVehicle` hace **tres** cosas, no una,
y las otras dos se pierden:

1. **No retira la notificación pendiente.** Y aquí hay una por definición: este veredicto sólo se
   alcanza porque había un prompt esperando respuesta. El KDoc de `closeNotARoadVehicle` dice
   exactamente por qué importa — *«a question already on screen is as wrong as a new one, and more
   so — it is the one the user might actually answer»*. Un «sí» ahí planta el pin en el andén.
2. **No estampa `SessionOutcome.AbortedUnattended("not_a_road_vehicle")`.** Ninguna rama lo escribe,
   así que la sesión cierra con el `Ended` por defecto. Consecuencias medibles: la traza no dice por
   qué acabó, y `resolvesTheArrival` pasa de `true` a `false` — el backfill de la red de seguridad
   vuelve a decidir la misma llegada con menos información, que es justo el modo de fallo de
   [DET-BACKFILL-TAINT-001].

## Doctrina violada

*Un pin sin provenance no es diagnosticable en remoto*, y su corolario: una sesión que termina sin
decir por qué no se puede auditar. El veredicto existe y es correcto; lo que falla es que desde una
de sus tres puertas no llega a ejecutarse entero.

## Señales / datos disponibles

Todo hecho ya: `DetectionEffect.CloseNotARoadVehicle` y `DetectionEffectExecutor.closeNotARoadVehicle`
existen y están probados. Lo que falta es emitirlo desde el tercer sitio.

## Diseño

Mapear `is UnattendedParkingSave.Discard` a `DetectionEffect.CloseNotARoadVehicle(vehicleId, fix)`
en lugar de a `null`. El patrón ya lo sigue `CloseDegradedPrompt`, que nació en el ticket hermano
precisamente porque necesitaba las tres cosas.

⚠️ **Verificar antes**: puede existir una razón deliberada para el `null` que el comentario no
cuenta (p. ej. que otro camino estampe el outcome aguas arriba en este carril concreto). Si la hay,
el arreglo es escribirla, no cambiar el código.

## Criterio de éxito

- Un `Discard` desde el timeout retira la notificación de confirmación.
- La sesión cierra con `aborted_unattended_not_a_road_vehicle`, no con `ended`.
- Test en la línea de `ResponseTimeoutDegradedPromptTest`: mismo estado con
  `roadPlausibility = IMPOSSIBLE` → efecto `CloseNotARoadVehicle`, ni pin ni zona.

## Consumidores auditados

Pendiente — se hace al abrir la rama.
