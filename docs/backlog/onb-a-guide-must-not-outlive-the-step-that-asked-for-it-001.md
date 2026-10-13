# ONB-A-GUIDE-MUST-NOT-OUTLIVE-THE-STEP-THAT-ASKED-FOR-IT-001 · El realce de «Ver plazas» no se apaga

**Estado:** ✅ Done · el realce muere **al usar el control**, una regla para los cuatro destinos
· ⏳ sin ver en mano

## Problema

En los primeros pasos, tocar **«Ver plazas»** deja el chip «Plazas» del carril **realzado para
siempre**: sigue encendido después de que el paso se complete, y solo se apaga al salir del modo
Browse. Visto en device con captura (Oppo, 25-09, 17:40).

## Doctrina violada

⛔ **Una afirmación en un comentario no se ejecuta.** El KDoc de `HomeScreen.kt:448-452` promete
**tres** interruptores de apagado:

> *se apaga en cuanto actúa — al usar el control, al completarse el paso, o al salir de Browse*

De los tres, **solo uno funciona de verdad** (salir de Browse). Los otros dos:

```kotlin
// HomeScreen.kt:462
val guidedStepStillAsked =
    firstStepGuide != FirstStepGuide.ADD_ZONE_CHIP || state.firstSteps.current == FirstStep.PRIVATE_ZONE
```

- **«al completarse el paso»** está escrito **solo para `ADD_ZONE_CHIP`**. Para `SPOTS_LANE`,
  `REPORT_SPOT_CARD` y `NEARBY_COMPLEMENT_TABS` el primer operando ya es `true`, la condición nunca
  es falsa, y el realce no se apaga jamás.
- **«al usar el control»** no existe: `grep firstStepGuide` da **dos** `LaunchedEffect` que lo
  limpian (salir de Browse, y el de arriba). Ninguno mira si el usuario tocó el control.

Es la misma familia que
[`DET-A-SAVED-CARD-MUST-NOT-OUTLIVE-ITS-TRIP-001`](det-a-saved-card-must-not-outlive-its-trip-001.md):
**una regla escrita dentro del único carril que la usa no es una regla del sistema.**

## Por qué muerde MÁS en «Ver plazas» que en la zona

`HomeSheetContent.kt:196` lo dice: *«The step still closes on this tap (it is knowledge)»*. O sea
`FIND_SPOT` se cierra **en el mismo toque** que enciende el realce. `PRIVATE_ZONE` no: exige una
zona real o un «no tengo garaje». Por eso el caso especial funcionaba para la zona y el genérico
nunca se escribió: el único carril que se probó era el único que no tenía el bug.

## ⛔ La trampa del arreglo obvio

Generalizar la condición a *«¿sigue siendo este paso el actual?»* para todos los destinos
**apagaría el realce al instante** en `FIND_SPOT` y `EXPLORE_NEARBY`, que se cierran en el toque —
matando la guía entera en vez de arreglarla. El mapeo destino→paso es trivial
(`SPOTS_LANE`/`REPORT_SPOT_CARD`→`FIND_SPOT`, `NEARBY_COMPLEMENT_TABS`→`EXPLORE_NEARBY`,
`ADD_ZONE_CHIP`→`PRIVATE_ZONE`), pero **no es la respuesta**.

La pregunta real es **cuál es el evento de apagado para un paso que ya está cerrado**, y hay que
decidirla antes de tocar una línea:

1. ¿El usuario **usa** el control (toca el chip, abre una plaza)? Es el que promete el KDoc y el
   que no existe.
2. ¿Un reloj? ⛔ **Ya se probó y se retiró**: los 6 s fijos dieron *«no capta la atención»*
   ([ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001], comentario en `HomeScreen.kt:449`). No reintroducirlo
   sin leer por qué murió.
3. ¿El siguiente toque en cualquier sitio?

## Criterio de éxito

- Tocar «Ver plazas», ver el realce, y que se apague por una causa **que el usuario provoca** —
  sin salir de Browse y sin esperar a que se aburra.
- Los cuatro destinos de `FirstStepGuide` con la misma regla, no uno con la suya.
- El KDoc de `HomeScreen.kt:448` y el código diciendo lo mismo, que hoy no lo dicen.


---

## Resuelto (13-10-2026) — decisión del user: **usar el control**

De las tres candidatas que este doc dejó abiertas, el user eligió la **1**: el realce muere cuando
el usuario usa el control al que señala. Es la que el KDoc ya prometía, así que código y comentario
convergen sin reescribir la promesa. La 2 estaba muerta de antes (el reloj) y la 3 —el siguiente
toque en cualquier sitio— se descartó: apagar el realce al mover el mapa o abrir otra fila le quita
la función de **señalar** y lo vuelve aleatorio.

### La regla vive en el enum, no en la pantalla

`FirstStepGuide` gana tres cosas, y las tres estaban antes dispersas o implicadas en un `!=`:

- **`step`** — qué paso apunta aquí. El mapeo que este doc llamaba trivial, ahora declarado:
  `SPOTS_LANE`/`REPORT_SPOT_CARD`→`FIND_SPOT`, `NEARBY_COMPLEMENT_TABS`→`EXPLORE_NEARBY`,
  `ADD_ZONE_CHIP`→`PRIVATE_ZONE`.
- **`stepOutlivesTheTap`** — la sutileza entera. Solo `ADD_ZONE_CHIP` sobrevive a su toque; los
  otros tres se cierran en él. Es lo que impide **la trampa que este doc avisaba**: generalizar
  «apágalo cuando el paso deje de ser el actual» habría apagado el realce en el mismo fotograma
  para `FIND_SPOT` y `EXPLORE_NEARBY`, y no se vería nunca.
- **`isUsedBy(intent)` / `isUsedBy(action)`** — el interruptor nuevo. Dos sobrecargas porque los
  cuatro controles actúan por **dos** embudos: tres emiten `HomeIntent` (`SetNearbyLens`,
  `SelectItem(Spot)`, `EnterAddZoneMode`) y la tarjeta de avisar plaza emite
  `HomeSheetAction.RequestReportMode`. Preguntarla dos veces sigue siendo **una** regla; cuatro
  `if` repartidos por cuatro árboles de componentes no lo sería.

En `HomeScreen` queda `onIntentGuided`, que envuelve el embudo de intents una sola vez, y un
`onGuidedControlUsed` que `HomeSheetSection` llama desde su `onAction`. **El caso especial de
`ADD_ZONE_CHIP` desaparece**: la condición del paso pasa a leerse del destino.

### El testigo

`FirstStepGuideTest`, 5 tests. El que importa **recorre el enum entero**: si nace un quinto destino
y nadie le da su evento de uso, el `assertEquals` lo canta en vez de heredar un «sí» silencioso —
que es exactamente cómo nació este bug. Falsificado quitando `REPORT_SPOT_CARD` del mapa del test:
rojo, con el mensaje que explica por qué.

⚠️ **Lo que el test NO cubre**: el cableado de Compose. Que `onIntentGuided` llegue de verdad a la
cabecera y a la hoja, y que `onGuidedControlUsed` se dispare, no es comprobable en un test unitario
— eso se ve en el móvil. La regla sí lo es, y es donde estaba el defecto.

### Consumidores auditados

- Los **cuatro** destinos de `FirstStepGuide` — ✔ cubiertos por la misma regla y por el test.
- La selección de una plaza **desde el mapa** — ⛔ **exenta a propósito**: el realce señala al
  carril de la hoja, y un marcador del mapa no es ese control. Se apagará al salir de Browse.
- El Dev Catalog — ✔ exento: no hay pantalla ni estado nuevo, es el mismo realce apagándose. No
  aparece variante que añadir a la galería.
- El KDoc de `HomeScreen` — ✔ ya no promete tres interruptores teniendo uno: los enumera y dice qué
  hacía cada cual cuando este bug existía.
