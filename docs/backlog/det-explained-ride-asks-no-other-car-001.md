# DET-EXPLAINED-RIDE-ASKS-NO-OTHER-CAR-001 · una salida ya atribuida a un coche no puede además interrogarte por los otros

> ⚠️ **Cruzado con el rediseño (30-08): INTACTO.** Vive en el bucle multi-sesión de
> `ParkingSafetyNetWorker`; las siete piezas viven en la decisión de UNA sesión. Un evaluador puro no
> puede saber qué hicieron las otras sesiones del mismo tick — por construcción, ninguna pieza lo
> absorbe. Ver `docs/detection/REDESIGN-DETECTION-SYSTEM.md` §9.3.

**Estado:** ✅ Done (2026-09-02) · ⏳ pendiente validar en campo (salir con dos coches aparcados)
**Origen:** field 27-08 (fisio), Oppo. Hallazgo lateral al investigar la sesión zombi `a786c135`.

## Problema

Con **dos sesiones aparcadas a la vez** (Ford Focus en Calle Góndola 1, Skoda Kamiq en Calle
Góndola 7, a 30 m una de otra), el user se va conduciendo el Focus. En **el mismo tick** del
safety-net, las dos vallas ven el mismo desplazamiento de 2 km y llegan a conclusiones opuestas:

```
08-27 12:29:18.235  SafetyNet: ▶ moving far without anchor — still-parked prompt geofence=a786c135
08-27 12:29:18.248  SafetyNet: [detection-end]
    geof=e1cb2b34: LEJOS del coche (d=2008m) y CON pruebas de viaje → proceso tu salida,
                   la plaza se libera (avalada por 4 pasos desde el coche)
    geof=a786c135: LEJOS del coche (d=2001m) pero SIN pruebas de viaje → te pregunto
                   '¿sigues aparcado?' en vez de liberar
```

13 milisegundos separan las dos líneas. **La misma medición**, el mismo cuerpo, el mismo
desplazamiento: uno lo explica y el otro pregunta por él.

Y no se queda en el log — `ParkingSafetyNetWorker.kt:509` llama a
`notificationPort.showStillParkedPrompt(...)`, así que el user recibe una notificación real
preguntándole si sigue aparcado un coche que no ha tocado en seis días.

⛔ **No plantó ningún pin.** El fallo asimétrico funcionó: ante la duda preguntó. Lo que sobra es la
pregunta, no el pin.

## Doctrina violada

Ninguna de las tres reglas rectoras, literalmente. Lo que rompe es la regla de copy y de confianza:
**no molestar al user con una pregunta cuya respuesta la app ya tiene delante.** El desplazamiento
que motiva la pregunta es el MISMO que la línea de al lado acaba de dar por explicado, con pruebas
de viaje y 4 pasos de aval.

Una pregunta que el propio tick ya sabe contestar entrena al user a ignorar los prompts — y el
prompt es el instrumento del que depende todo el fallo asimétrico. Gastarlo en ruido es caro.

## Señales / datos disponibles — no hay que instrumentar nada

`ParkingSafetyNetWorker` ya **itera todas las sesiones en el mismo tick** y ya acumula estado
transversal: la variable `anyPromptActive` existe justo en esa rama. Lo que no hay es la señal
recíproca: *alguna otra sesión de este tick explicó este desplazamiento*.

| Señal | Dónde vive | Valor el 27-08 |
|---|---|---|
| `SafetyNetAction.DispatchDeparture.preconfirmed` | evaluador puro | `true` para `e1cb2b34` |
| `trustedStepsSinceAnchor` | ídem | 4 |
| `anyPromptActive` | `ParkingSafetyNetWorker`, ya en el bucle | — |
| distancia por sesión | ya calculada por sesión | 2008 m vs 2001 m |

Las dos vallas están a **30 m** una de otra, así que el desplazamiento es indistinguible por
construcción: 2008 vs 2001.

## Diseño (propuesta, a confirmar)

El invariante: **si en el mismo tick una salida se despacha con pruebas de viaje, el resto de
sesiones no preguntan por ese mismo desplazamiento.** Cuando el cuerpo se ha ido en un coche, el
hecho de que los demás sigan aparcados es la conclusión NORMAL, no una duda.

Vive en el **bucle del worker**, no en el evaluador puro: el evaluador decide sesión a sesión y no
puede saber lo que hicieron las otras. Es simétrico a `anyPromptActive`, que ya está ahí.

Bosquejo: recorrer las acciones primero, y si alguna es `DispatchDeparture(preconfirmed = true)`,
degradar los `PromptStillParked` de ese mismo tick a silencio (o a la anotación de debug sin
notificación).

⚠️ **Cuidado con el alcance.** Sólo debe callarse el prompt cuando la otra salida está avalada por
pruebas de viaje (`preconfirmed`). Un `DispatchDeparture` en vivo por velocidad no basta: ahí la
propia salida está aún por verificar.

### Alternativas descartadas

- **Subir el throttle del prompt.** Ya hay uno (`PROMPT_THROTTLE_MS`, persistido a disco) y funciona:
  en 3 días sólo salió UNA notificación. El problema no es la frecuencia, es que la pregunta está
  mal hecha aunque se haga una sola vez.
- **No preguntar nunca por una sesión que no es del vehículo activo.** Rompe el caso legítimo: si de
  verdad te llevaste el otro coche, quieres que te pregunte.

## Criterio de éxito

- Test: dos sesiones, mismo tick, una resuelve `DispatchDeparture(preconfirmed = true)` y la otra
  `PromptStillParked` → **no se notifica**. Los números de campo: 2008 m / 2001 m, 4 pasos.
- Test de regresión: una sola sesión, `PromptStillParked` y nada más en el tick → **sí notifica**
  (el caso bus/taxi que el prompt existe para cubrir).
- Verificar que el test discrimina, neutralizando el guard.
- Campo: salir de casa con dos coches aparcados y no recibir la pregunta.

## Consumidores auditados

`grep -rn "PromptStillParked\|showStillParkedPrompt\|anyPromptActive" shared/src app/src` (02-09):

| Sitio | Clasificación |
|---|---|
| `ParkingSafetyNetWorker` — rama `PromptStillParked` del bucle | **cerrado** — diferida al final del tick y resuelta contra el mute |
| `ParkingSafetyNetWorker` — prompt del `ArrivalOwner.UserPrompt` dentro de `DispatchDeparture` | **exento con razón** — es el fallback de arrival de la PROPIA sesión despachada (nada pudo tomar la llegada); pregunta otra cosa y no debe callarse |
| `AppNotificationManagerImpl.showStillParkedPrompt` (app) | **exento** — sink de I/O, no decide |
| `AppNotificationManager` (interfaz, default no-op) | **exento** — contrato |
| `EvaluateSafetyNetCheckUseCase` (produce la acción) | **exento con razón** — evaluador por-sesión, no puede saber el tick (§9.3 del rediseño); intacto a propósito |
| `DepartureDetectionWorker` / `FreedSpotIsStillThere` (leen `preconfirmed`) | **exentos** — no tocan el prompt |

## Cierre (2026-09-02)

Implementado como estaba bosquejado, con la decisión en código puro:

- **`domain/detection/ExplainedDeparture.kt`** — `stillParkedPromptsExplainedByDeparture(tickActions)`:
  predicado puro de tick (patrón `HumanPoweredRide`/`SentryWakeCooldown`, no un use case
  [DET-VERDICT-NOT-PREDICATE-001]). Solo muta un `DispatchDeparture(preconfirmed = true)`; un
  dispatch vivo aún debe pasar su re-verificación de velocidad y no calla nada.
- **Worker**: los `PromptStillParked` se RETIENEN durante el bucle (la sesión que explica puede ir
  después en el orden) y se resuelven al final del tick. Mute → línea de traza + verdict
  `safety_net_prompt_muted` (citable en diagnóstico) + línea de debug «la salida de otro coche en
  este mismo aviso ya explica el movimiento → no te pregunto», sin notificación, **sin estampar el
  throttle** (una pregunta nunca mostrada no gasta la ventana de una legítima futura) y sin marcar
  `anyPromptActive` (un prompt viejo sobre ese mismo desplazamiento es igual de caduco → se descarta).
- Tests (6, en `ExplainedDepartureTest`): los dos del criterio (dos sesiones → mute; prompt solo →
  pregunta), orden inverso dispatch/prompt, dispatch vivo NO muta, N prompts todos mutados, acciones
  ajenas ignoradas. **Discriminación verificada**: neutralizando el guard fallan 4/6.
- Suite **2.127/0** (2.121 previas + 6).

## Relación con otros tickets

Hermano de `DET-BT-VETO-MUST-NOT-ORPHAN-A-SESSION-001`, que ataca **por qué esa segunda sesión sigue
viva**. Éste ataca **por qué molesta**. Son independientes: aunque la sesión del Kamiq fuese
legítima y correcta (dos coches aparcados de verdad), la pregunta seguiría estando mal hecha.
