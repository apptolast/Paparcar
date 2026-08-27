# DET-EXIT-LINE-COUNTS-NOTHING-001 · la línea de salida de la sesión cuenta siempre cero

**Estado:** ✅ Done — arreglado con un testigo local, NO con la fila en `SessionEpilogue` que
proponía este doc (abajo, «lo que el código corrigió del diseño»). Encontrado durante P3.13
(`DET-ORCHESTRATOR-ASSEMBLY-001`).

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

---

## ⚠️ Lo que el código corrigió del diseño de este doc

**La fila en `SessionEpilogue` no arreglaba la mitad del bug.** `epilogue` es un campo `@Volatile`
**del detector**, no de la invocación — tiene que serlo, porque el servicio lo lee DESPUÉS de que
`invoke` retorne. En la rama superseded no se escribe epílogo (correcto, [DET-AUDIT-002 T8]), así
que leer de ahí en esa rama devuelve el epílogo de **otra sesión**: exactamente el mismo género de
mentira que el doc venía a matar, con otro número.

El arreglo es un `var fixCountAtExit: Int?` local, hermano de `completedAtExit` y por la misma
razón, sellado en el mismo statement. `null` = «esta invocación no fue dueña del final», y la línea
escribe `n/a (superseded)` en vez de heredar un número ajeno. `SessionEpilogue` no se toca: sus
cinco campos existen para la escalera de honest-close, y meter un campo que sólo alimenta una línea
de log lo habría convertido en un cajón.

## ⚠️ El hallazgo que vale más que el arreglo: la línea NO se imprime al cancelar

Medido, no supuesto — la primera versión del test cancelaba la corrutina y falló con «la sesión
debe anunciar su propio final». La línea está **después** del `try/finally`, así que una
`CancellationException` pasa de largo por ella: sólo se ejecuta cuando `invoke()` retorna sola, o
sea cuando el `takeWhile { !completed }` termina el flujo.

Y en producción la vía normal de parar una sesión ES la cancelación (`cancelDetectionJob()`). O sea
que la línea de salida **falta en el `parkdiag` de buena parte de las sesiones**, y cuando aparecía,
mentía. No queda la traza coja: el `SessionEnded` remoto sí se emite desde dentro del `finally`, que
es lo que cierra la sesión en Firestore. Pero conviene no leer la ausencia de esta línea como
síntoma de nada.

Fuera de alcance aquí: mover la línea dentro del `finally` la haría fiable, y es un cambio de
conducta del bucle, no un arreglo de instrumento.

## Testigo

`should_report_on_the_exit_line_the_fixes_this_trip_actually_saw` en
`CoordinatorParkingDetectorTest`. El único observable es la propia línea, así que el testigo es un
`Antilog` de registro sobre `Napier.base` (`performLog` es `protected`; `Antilog.log` no), retirado
en un `finally` porque es estado global. La sesión termina **sola** — tres fixes, confirmación del
usuario, y un cuarto fix que dispara el `takeWhile` sin llegar a contarse.

**Verificado en rojo**: devolviendo la línea a `_detectionState.value.session.fixCount` el test
falla reproduciendo el bug literal — `locationCount=0 completed=true`.

⛔ **La rama `n/a (superseded)` NO tiene testigo** y no se finge que lo tenga: exige una sesión que
pierda la propiedad del estado Y aun así termine por `takeWhile` en vez de por cancelación. Es
alcanzable pero no se ha construido; queda dicho aquí antes que escribir un test que no lo pruebe.
