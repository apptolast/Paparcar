# ONB-A-GUIDE-MUST-NOT-OUTLIVE-THE-STEP-THAT-ASKED-FOR-IT-001 · El realce de «Ver plazas» no se apaga

**Estado:** 🔵 Abierto, sin empezar · reportado por el user en campo (Oppo, 25-09), diagnosticado
sin arreglar para no mezclarlo con
[COPY-THE-APP-NEVER-SAYS-WHAT-IT-IS-FOR-001](copy-the-app-never-says-what-it-is-for-001.md)

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
