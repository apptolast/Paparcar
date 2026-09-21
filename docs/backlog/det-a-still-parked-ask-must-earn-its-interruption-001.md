# DET-A-STILL-PARKED-ASK-MUST-EARN-ITS-INTERRUPTION-001 · el aviso «¿Sigues aparcado aquí?» sale más veces de las que se ha ganado

**Estado:** ✅ Done · mergeado en master (squash) el 21-09-2026

## Problema

Reporte del user (21-09-2026): *«la notificación de ¿Sigues aparcado ahí? parece que sale más veces
de las necesarias, incluso cuando estamos junto al coche»*.

Lo primero que hay que descartar es el umbral, y se descarta: **el aviso no puede levantarse con el
móvil medido junto al pin**. Todo pasa por la escalera de `EvaluateSafetyNetCheckUseCase`
(`invoke`): dentro del radio → `CureGeofence`; y «lejos» exige `d − accuracy > watchdogFarThreshold`
(300 m). Los dos únicos sitios que muestran la notificación en Android cuelgan de ahí.

Que la vea estando al lado del coche tiene entonces cinco causas posibles, y las cinco son defectos
nuestros:

1. **Una de las ramas se salta el throttle entero.** Las cuatro causas que nomina el evaluador
   (`StillParkedReason`) pasan por el bucle de prompts retenidos de `ParkingSafetyNetWorker`, que lee
   y estampa `prompt_<geofenceId>` con una ventana de 6 h. La rama de *«nadie puede coger la
   llegada»* (`ArrivalOwner.UserPrompt`, dentro del brazo `DispatchDeparture`) llama a
   `showStillParkedPrompt` sin leer ni estampar nada. Y es justo la que salta **cuando acabas de
   aparcar**: el net despacha tu salida, intenta arrancar el FGS del handoff, Android 12+/OEM lo
   deniega en background (`arrivalOwner(...)` → `else → UserPrompt`) y pregunta. Con la ventana de
   adjudicación en 5 min y el heartbeat exacto cada ~5 min, la misma situación puede repetir la
   pregunta cada cinco minutos. iOS no tiene este agujero: `IosDetectionController.executePrompt`
   throttlea siempre. **Un aviso, dos puertas, guardia en una sola.**

2. **Y en esa rama la pregunta es la equivocada.** El título dice *«¿Sigues aparcado aquí?»* y el
   cuerpo *«Parece que estás lejos, pero este aparcamiento sigue abierto»*, cuando lo que acaba de
   pasar es lo contrario: la salida YA se ha despachado y lo que falta por saber es **dónde has
   aparcado ahora**. Esa pregunta ya existe, con su copy, su id de notificación y su provenance:
   `showMarkParkingNudge`. La rama de llegada estaba usando la notificación de salida para una
   pregunta de llegada.

3. **`userPresent` no significa «el usuario ha abierto la app»**, significa «ha arrancado el
   proceso»: el `enqueueCheckNow(SOURCE_APP_START)` vive en `PaparcarApp.onCreate`, que corre también
   cuando despierta el proceso un receiver de geocerca/BT/AR, un worker, un push o el widget. La rama
   `USER_PRESENT_AND_BLIND` es la más laxa de las cuatro (lejos + ninguna prueba, sin exigir
   evidencia de nada) y su justificación escrita es *«no molesta porque ya están mirando la
   pantalla»*. En un ColorOS/MIUI que mata el proceso constantemente, esa rama corre en segundo
   plano: exactamente lo que su propio comentario dice que no hace.

4. **Sin contador de pasos fiable desaparece la defensa «he venido andando».** `stepsExplainWalk`
   exige `trustedStepsSinceAnchor != null`. Un reinicio del móvil resetea el contador acumulado → la
   comparación `cumulativeSteps >= anchorSteps` falla → el presupuesto queda `null` **para siempre**,
   porque el cero solo se re-sella volviendo DENTRO de la valla. Coche aparcado a 400 m de casa +
   móvil reiniciado = pregunta cada 6 h con todo perfecto. Lo mismo cuando el guard de contador
   congelado le quita la voz al contador.

5. **El aviso solo se retira al final de un tick completo.** Tres `return` tempranos se saltan
   `dismissPrompt()`: sin permiso de ubicación, sin fix fresco en 15 s, y **la detección corriendo**.
   Ese último es el que muerde: te subes al coche → arranca la detección → el tick que iba a
   retirarlo se va antes, y la pregunta de hace horas te acompaña todo el viaje. Además «Marcar
   aparcamiento» es un botón de acción, no el `contentIntent`, así que `setAutoCancel` no lo cubre:
   respondes y la pregunta sigue en la bandeja hasta el siguiente tick bueno.

## Doctrina violada

- **Fallo asimétrico: ante la duda se PREGUNTA.** No está violada en su dirección peligrosa (no se
  planta ninguna plaza fantasma), pero sí en su coste: una pregunta que se repite cada cinco minutos,
  o que aparece con la app cerrada por un despertar de receiver, deja de ser una pregunta y pasa a
  ser ruido. Un aviso que no se ha ganado la interrupción gasta la credibilidad del que sí.
- **Sistemas, no parches.** El throttle es un invariante («este coche no se pregunta dos veces en
  6 h») y hoy vive en un solo brazo del `when`, no en la puerta. Lo mismo la pregunta: hay dos
  preguntas distintas y una sola notificación.
- **Copy sin mecánica interna, causa + consecuencia + remedio.** En la rama de llegada la causa que
  se enuncia («parece que estás lejos») es falsa: estás en el coche nuevo.

## Señales / datos disponibles

- `DepartureVerdict` con `verdict = "safety_net_prompt"` / `"safety_net_prompt_muted"` y
  `source = "safety-net:<source>"` en `diagnostics/{uid}/sessions/{geofenceId}/events`. **La rama de
  llegada no registra verdict propio** — solo una línea de log (`⊘ nothing left to take the
  arrival`), así que en remoto es invisible: otro motivo para que pase por la misma puerta.
- `parkdiag.log` en el device guarda la línea de cada tick («te pregunto '¿sigues aparcado?'» con su
  causa), que es lo que permite contar de verdad cuántas veces salió y por cuál de las cuatro
  razones.
- ⚠️ **No medido en campo todavía**: al auditar no había móvil de campo conectado (solo el emulador)
  y el MCP de Firestore no consulta subcolecciones anidadas a dos niveles. La frecuencia real queda
  pendiente de `parkdiag.log` del Oppo/Redmi.

## Diseño

**Una puerta por pregunta, y cada pregunta la hace quien tiene la respuesta que falta.**

- **«¿Sigues aparcado?» es una duda sobre la SALIDA.** Solo la nomina el evaluador
  (`SafetyNetAction.PromptStillParked`) y solo la muestra la puerta única, que lee y estampa la
  ventana de 6 h por valla.
- **«¿Dónde has aparcado?» es una duda sobre la LLEGADA.** Se hace con `showMarkParkingNudge`, que ya
  existe, persiste el `PendingParkNudge` (sobrevive a la bandeja dormida) y escribe provenance
  `nudge_<source>` en el pin que se confirme.
- El throttle es **uno por valla y compartido entre las dos preguntas**: el presupuesto que se agota
  no es «cuántas veces preguntamos por esta salida», es «cuántas veces interrumpimos por este
  coche». Vive en una función pura (`domain/detection/SafetyNetAskBudget.kt`), no en el brazo del
  `when`.
- **`userPresent` se mide, no se deduce del arranque del proceso**: se separan dos fuentes.
  `SOURCE_PROCESS_START` (desde `PaparcarApp.onCreate`, que es lo que de verdad ocurre ahí: sembrar
  el ancla) y `SOURCE_APP_START` (desde `MainActivity.onStart`, que es cuando hay UI delante). Solo
  la segunda enciende `userPresent`. En iOS la fuente ya era honesta (la manda el ciclo de vida de la
  app), así que el evaluador compartido no cambia de significado en ese lado.
- **Un contador reseteado sigue dando una cota INFERIOR de pasos.** Tras un reinicio los pasos
  anteriores son irrecuperables, pero la lectura actual es un suelo: «has andado al menos esto desde
  el ancla». Entra como `stepsSinceAnchorAtLeast` y se usa **solo** en `stepsExplainWalk`, que es la
  única pregunta que un suelo puede contestar — si estos pasos ya cubren el desplazamiento, los
  desconocidos solo lo cubren más. Ninguna rama de liberación lo ve: probar un viaje necesita un
  TECHO («demasiado pocos pasos para haberlo andado») y un suelo nunca lo da.
- ⛔ **Descartado: usar la física peatonal como explicación en la rama ciega.** Era la idea obvia
  (`isBeyondPedestrianReach` en negativo) y es falsa por el otro lado: con un ancla de horas un
  peatón «puede» cubrir cualquier distancia, así que la rama se habría quedado muda incluso para el
  incidente que la justifica (2026-07-11, Redmi: app abierta a 1 912 m, ancla caducada, tres ticks
  silenciosos). Un guard que silencia su propio caso fundacional no es un fix.
- **El aviso se retira también cuando la situación deja de ser una duda**: con la detección corriendo
  (alguien está midiendo ese viaje) y al abrir el flujo de marcar aparcamiento desde la notificación.
  Con permiso denegado o sin fix fresco NO se retira: ahí no sabemos nada, y borrar una pregunta
  honesta porque falló el GPS es perderla.

## Criterio de éxito

- Ningún camino muestra el aviso sin pasar por la ventana de 6 h por valla — falsificable con un test
  de la función pura y con el barrido de llamantes de `showStillParkedPrompt`.
- La rama de llegada pregunta «marca dónde has aparcado», no «¿sigues aparcado?», y su pin conserva
  provenance (`nudge_safety_net_arrival_unowned`) citable en diagnóstico.
- Un arranque de proceso en background no enciende `userPresent`; abrir la app sí.
- Un móvil reiniciado con el coche aparcado a 400 m no recibe la pregunta ciega (la cota inferior de
  pasos ya explica la caminata), y esa misma cota no libera ninguna plaza.
- Subirse al coche retira el aviso pendiente en el primer tick, en vez de arrastrarlo todo el viaje.

## Resultado

**3512 tests verdes, 0 fallos** (`./gradlew testAll --rerun-tasks`), con tres ficheros de test
tocados: `SafetyNetAskTest` (nuevo, 6 casos), `EvaluateSafetyNetCheckUseCaseTest` (+3: la cota
explica, no explica de más, y nunca libera) y `DetectionPathTest` (source nuevo en el vocabulario de
nudges).

Sin strings nuevos: la pregunta de llegada reutiliza el copy que ya existía en los 9 locales. Sin
pantalla, estado MVI ni condición de routing nuevos → el Dev Catalog no cambia.

## Consumidores auditados

| Sitio | Qué asume | Estado |
|---|---|---|
| `ParkingSafetyNetWorker` · bucle de `heldPrompts` | el aviso pasa por el throttle | **cerrado** — ahora es la puerta única |
| `ParkingSafetyNetWorker` · `ArrivalOwner.UserPrompt` | el aviso de salida vale para la llegada | **cerrado** — pasa a `showMarkParkingNudge` |
| `IosDetectionController.executePrompt` | throttle propio, mismo criterio | **cubierto** — ya throttleaba; hereda el helper puro |
| `CoordinatorDetectionService.handleWatchdogDeparture` | retira el aviso al responder «ya he salido» | **cubierto** |
| `MainActivity.handleNotificationFocusIntent` | el flujo de marcar aparcamiento no retiraba nada | **cerrado** |
| `DetectionPathTest` · vocabulario de `showMarkParkingNudge` | cada source de nudge resuelve a `DetectionPath.Nudge` | **cerrado** — el source nuevo entra en la lista |
| `PaparcarApp.onCreate` | `app-start` = usuario delante | **cerrado** — pasa a `process-start` |

## Pendiente

- Contar en `parkdiag.log` del Oppo/Redmi cuántas veces salió y por cuál causa, para saber cuál de
  las cinco pesaba más en el reporte del user.
