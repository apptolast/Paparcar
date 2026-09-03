# PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001 · El primer fix de GPS vuela la cámara — y con ella el pin que el usuario estaba colocando

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)
> ⏳ Falta la comprobación en calle: arrancar en frío, arrastrar lejos, esperar al primer fix y
> confirmar. En banco quedó cubierto por tests.

## Problema

Reportado por el user en device (03-09): *«el pin que ponemos, aunque arrastremos, se planta en tu
localización»*. Arrastras el mapa hasta tu coche, confirmas, y el aparcamiento aparece donde estás
tú.

El pin **es** el centro de la cámara (`onCameraMove` → `HomeIntent.CameraPositionChanged` →
`pinCameraLat/Lon` → `pinCoordinates()` en el confirm). Por eso una cámara que se mueve sola no
mueve solo el mapa: **mueve el pin**.

Y una se mueve sola, sin guard ninguno, justo en la ventana del tutorial:

```
HomeScreen.kt:828   LaunchedEffect(state.userGpsPoint) { uiController.centerInitialFocus(...) }
HomeUiController.kt:149   if (centeredOnUser) return      ← el ÚNICO guard
```

Secuencia medida (arranque en frío, que es exactamente cuando se ve la checklist guiada):

1. Se abre la app sin fix todavía → `centeredOnUser = false`, el one-shot sigue pendiente.
2. El usuario pulsa «Marcar aparcamiento» y arrastra el mapa hasta su coche →
   `pinCameraLat/Lon` = el sitio correcto, `userMovedCameraManually = true`.
3. **Llega el primer fix de GPS** → `centerInitialFocus` no mira el pan manual ni el modo →
   `setTarget(user)` → tween de 700 ms hasta la posición del usuario.
4. El tween emite frames de cámara → `pinCameraLat/Lon` = **la posición del usuario**.
5. Confirmar planta el pin ahí. El arrastre del usuario se perdió sin dejar rastro.

Lo que NO es (descartado con dos tests nuevos en `HomeViewModelTest`, verdes a la primera):
`confirmAddParking` y `SaveManualParkingUseCase` respetan el sitio arrastrado, incluso con un fix de
GPS nuevo aterrizando entre el arrastre y el confirm. El estado estaba bien; la cámara no.

## Doctrina violada

- **[FOCUS-002], por su propio comentario**: *«True once the user pans/zooms by hand — disables every
  automatic re-frame thereafter»*. `refocusOnParkingArrival` sí lo honra
  (`HomeUiController.kt:205`); `centerInitialFocus` nunca lo hizo — y es el que se dispara con el
  primer fix, o sea el que más tarde llega y el único que puede caer encima de un pin en el aire.
- **El pin es un dato del usuario, no una vista.** Todo el resto de la máquina de foco razona sobre
  «qué mira la cámara». En modo pin la cámara ES la respuesta que el usuario está dando, así que un
  re-frame automático no es una molestia visual: corrompe el dato.
- Por qué salió AHORA y no antes: hasta `ONBOARDING-FIRST-STEPS-*` nada empujaba al usuario a
  colocar un pin en los primeros segundos de una app recién abierta. El bug es viejo; la ventana
  para pisarlo la abrió el tutorial.

## Señales / datos disponibles

- `HomeUiController` ya tiene la máquina de guards completa (`centeredOnUser`,
  `userMovedCameraManually`, `initialFocusWasParking`, `refocusedOnParking`, `framedAskAtMs`) y ya
  separa las dos puertas de cámara: `goToPlace`/`framePlaces` = **deliberadas** (el usuario pidió
  ver un sitio), `setTarget`/`setBoundsTarget` privadas = **automáticas**.
- `HomeScreen` ya calcula `isPinningMode` (Reporting · AddingZone · AddingParking) para el pin
  central, el dimmed de spots y el cap de la sheet. No hay que inventar el estado, solo dárselo al
  controller.

## Diseño

Dos reglas, las dos en `HomeUiController`, que es donde ya viven todas las demás:

1. **El pan manual también apaga el foco inicial.** `centerInitialFocus` gana el guard
   `userMovedCameraManually` que el resto de la máquina ya respetaba. Sin consumir el one-shot: el
   guard sigue siendo verdad en el fix siguiente, así que no hace falta más estado.
2. **Mientras un pin se está colocando, la cámara la manda el dedo.**
   `pinPlacementActive` (escrito por `HomeScreen` desde `isPinningMode`) bloquea las tres entradas
   AUTOMÁTICAS de cámara. Las deliberadas (`goToPlace`, `framePlaces`, `resumeDriverFollow`) siguen
   funcionando: si el usuario pulsa el FAB de su ubicación mientras coloca el pin, quiere ir ahí.

Sobre los one-shots que se saltan por la regla 2 — decidido, no accidental:
- `refocusOnParkingArrival`: al confirmar un pin colocado a mano la cámara YA está sobre el coche
  (el pin es su centro), así que el re-encuadre no tenía nada que aportar.
- `frameTheAsk`: una pregunta que se abre mientras el usuario coloca un pin pierde su encuadre (el
  efecto va keyed por pregunta y no se reintenta). Se acepta: confirmar un aparcamiento a mano
  cancela la detección en curso, así que el pin resuelve la pregunta de hecho. Si algún día muerde,
  el arreglo es reintentar el encuadre pendiente al salir del modo, no relajar este guard.

## Criterio de éxito

- `HomeUiControllerTest`: un `centerInitialFocus` tras un pan manual no produce `cameraTarget`
  (test escrito ANTES del arreglo, y **falla** en master: `HomeUiControllerTest.kt:255`).
- Ninguna entrada automática de cámara produce target mientras `pinPlacementActive`.
- Las deliberadas siguen produciéndolo en ese mismo estado.
- En device: arrancar en frío, entrar a marcar aparcamiento, arrastrar lejos, esperar a que entre el
  primer fix, confirmar → el pin se queda donde se dejó.

## Consumidores auditados

Cada puerta de cámara del controller, clasificada:

| Entrada | Tipo | Antes | Ahora |
|---|---|---|---|
| `centerInitialFocus` | automática | solo `centeredOnUser` — **el bug** | + pan manual + pin |
| `refocusOnParkingArrival` | automática | pan manual ✅ | + pin |
| `frameTheAsk` | automática | sin guard de pan (a propósito) | + pin |
| `followDriver` / `setDriverFollowActive` | automática | no toca `cameraTarget` | sin cambios |
| `goToPlace` | deliberada | — | sin cambios (debe seguir yendo) |
| `framePlaces` (FAB punto medio) | deliberada | — | sin cambios |
| `resumeDriverFollow` (FAB) | deliberada | — | sin cambios |

Escritores de `pinCameraLat/Lon` (los sitios que el bug corrompía): `updatePinDuringMode`
(`HomeViewModel.kt:351`, alimentado por la cámara) y las tres entradas de modo
(`EnterAddParkingMode`, reporte, zona). Todos leen del mismo sitio en el confirm
(`pinCoordinates()`), así que la corrupción entraba por un único punto: la cámara.
