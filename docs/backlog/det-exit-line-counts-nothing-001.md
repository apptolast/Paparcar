# DET-EXIT-LINE-COUNTS-NOTHING-001 · la línea de salida de la sesión cuenta siempre cero

**Estado:** 🔵 Abierto · sin rama · encontrado durante P3.13 (`DET-ORCHESTRATOR-ASSEMBLY-001`)

## Problema

La última línea de `CoordinatorParkingDetector.invoke()` es:

```kotlin
PaparcarLogger.d(DIAG, "■ coordinator.invoke() EXITED — locationCount=${_detectionState.value.session.fixCount} completed=$completedAtExit")
```

Se ejecuta **después** del `finally`, y el `finally` llama a `reset()`. Así que en la rama normal —
la sesión que sí era dueña del estado — `fixCount` ya vale 0 y **la línea imprime siempre
`locationCount=0`**, dijera lo que dijera el viaje.

En la rama de sesión SUPERSEDIDA no hay `reset()`, así que ahí imprime el contador **del sucesor**:
un número real que pertenece a otra sesión.

Ninguna de las dos lecturas es la que el lector espera.

## Por qué importa poco y aun así merece ticket

Es una línea de `parkdiag`, no una decisión: no entra en ningún veredicto y no mueve ningún pin. Pero
es exactamente el género de dato que se lee al final de una sesión de campo para saber *«¿cuántos
fixes vio este viaje?»*, y contesta con un cero que parece un síntoma. Un instrumento que miente
gasta el tiempo de quien diagnostica, que es el recurso escaso.

`completed`, la otra mitad de la misma línea, SÍ era real: era un `var` local que sobrevivía al
`reset()`. P3.13 lo conservó a propósito con un testigo leído antes del borrado
(`completedAtExit`) — el contador no, porque arreglarlo es un cambio y P3.13 era un movimiento
[10 §0 regla 4].

## Diseño

`SessionEpilogue` ya existe y ya se escribe en el instante correcto: dentro del `finally`, con el
estado completo y todavía vivo, justo antes de `reset()`. El arreglo es una fila más en ese valor
(`fixCount`) y leer la línea de salida de ahí. Cero estructura nueva.

La rama superseded seguiría sin escribir epílogo, que es correcto por [DET-AUDIT-002 T8] — y
entonces la línea debe decir explícitamente que no cuenta, no heredar el número del sucesor.

## Criterio de éxito

Un test que corra una sesión con N fixes, la cancele, y afirme que la línea de salida reporta N.
Hoy ese test sería rojo con cualquier N > 0, que es lo que lo hace discriminante.

## Consumidores auditados

Ninguno: la línea es sólo `PaparcarLogger.d`, no alimenta `DetectionEvent` ni ningún evaluador.

`grep locationCount` da dos sitios más y **ninguno es este bug**: el `val locationCount` del bucle y
el `loc#N` que estampa en cada fix. Ese lee el contador EN VIVO, durante la iteración, y es correcto.
El defecto está sólo en la lectura post-`reset()` de la línea de salida — mismo nombre, momento
distinto, que es justamente por lo que pasó desapercibido.
