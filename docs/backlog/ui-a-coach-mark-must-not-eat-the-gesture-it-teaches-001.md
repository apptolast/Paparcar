# UI-A-COACH-MARK-MUST-NOT-EAT-THE-GESTURE-IT-TEACHES-001 · El foco que enseña a arrastrar el mapa se come el arrastre

**Estado:** ✅ Done · mergeado a master el **2026-09-03** (squash)

## Problema

Reportado por el user en device (03-09): *«cuando le doy a marca dónde has dejado el coche y aparece
la sombra negra que envuelve en círculo para colocar el coche, no puedo arrastrar con el mapa»*.

`PapSpotlight` —el coach mark del primer aparcamiento, el agujero circular sobre el pin central—
consume **todos** los eventos de puntero mientras está visible:

```kotlin
awaitPointerEventScope {
    while (true) {
        awaitPointerEvent().changes.forEach { it.consume() }   // ← se los queda todos
        onDismiss()
    }
}
```

Y su propio caption dice: *«Arrastra el MAPA hasta que quede sobre tu coche, luego confirma abajo»*.
El primer arrastre después de que aparezca **siempre se pierde**: el toque cierra el foco y no llega
al mapa, así que el usuario tiene que soltar y volver a empezar. En un móvil eso no se lee como "he
cerrado un aviso": se lee como que el mapa no responde.

Es un caso de manual de que la instrucción y el comportamiento se escribieron por separado — el
comentario que justifica el `consume()` dice literalmente *«so the gesture that closes the coach mark
never reaches the map as a pan»*, que es exactamente lo contrario de lo que el coach mark pide.

## Doctrina violada

- **El copy y el comportamiento tienen que estar de acuerdo**, la misma clase de defecto que
  `ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001` (un botón que decía «Ver plazas» y abría el
  flujo de avisar) y que `home_release_dialog_delete_only` (un botón que decía «BORRAR» y no
  borraba). Aquí el desacuerdo no está en las palabras: está entre lo que el texto pide y lo que la
  capa deja hacer.
- Y toca el gesto MÁS delicado del producto: el pin es el centro de la cámara
  [PARK-A-DRAGGED-PIN-MUST-OUTRANK-AN-AUTOMATIC-CAMERA-001], así que un arrastre perdido aquí es un
  aparcamiento que se marca donde no es.

## Diseño

**El foco no toca los eventos. Punto.** Es el agujero y el texto, nada más.

Se llegó ahí en dos pasos, y el primero fue un error que el device destapó:

1. *Observar sin consumir* (pase `Initial`, sin `consume()`). Razonamiento: el foco se entera del
   toque antes que el mapa y, al no consumirlo, el mismo arrastre sigue su camino. **Se instaló y
   seguía sin arrastrar**: el mapa es una VISTA NATIVA embebida en Compose, y una capa de Compose por
   encima le quita el táctil consuma o no. Observar solo basta cuando lo de debajo también es Compose.
2. Lo que hay: el overlay se queda **sin ningún `pointerInput`**, y quien lo cierra es el observador
   de gestos que el mapa YA tenía (`onUserMapGesture`, un `awaitFirstDown(requireUnconsumed = false)`
   en el pase Initial que no consume). El toque aterriza donde de verdad aterriza, el mapa se mueve
   desde el primer píxel y el foco se desvanece durante ese mismo gesto.

`spotlightSeen` sube en `HomeScreen` para declararse antes del mapa, porque ahora es el mapa quien lo
apaga.

Lo que NO cambia: sigue cerrándose al primer toque y sigue sin volver en esa sesión de modo
(`spotlightSeen` en `HomeScreen`).

## Criterio de éxito

- Con el foco en pantalla, un arrastre mueve el mapa **a la primera** y el foco se va durante ese
  mismo gesto. ✅ verificado en device (Redmi) por el user: el síntoma era literalmente *«el primer
  toque no arrastra»*.
- En device: entrar a marcar aparcamiento desde el checklist, arrastrar sin soltar, y ver que el mapa
  sigue el dedo desde el primer píxel.

## Consumidores auditados

- `PapSpotlight` tiene UN consumidor: `HomeScreen`, para el primer aparcamiento del checklist
  guiado. No hay otros coach marks en el repo (`grep` de `PapSpotlight` → 1 call site + previews).
- El `Modifier.layout` que comparte con el mapa no se toca: los bounds idénticos son lo que hace que
  el agujero caiga exactamente sobre el pin.
