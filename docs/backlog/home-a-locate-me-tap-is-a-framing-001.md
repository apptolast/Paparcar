# HOME-A-LOCATE-ME-TAP-IS-A-FRAMING-001 · Pedir «mi localización» es pedir un encuadre, lo siga la cámara o no

**Estado:** ✅ Done · mergeado en master el 22-09-2026

## Problema

Issue **#115**: *«zoom de location en ruta sigue sin funcionar»*.

El «sigue» es literal: [HOME-A-LIVE-TRIP-DESERVES-THE-ZOOM-IT-NEEDS-001] (`baba6104f`) cerró la
issue #66 con el mismo síntoma, y arregló **la mitad automática**. Esta es la otra.

## Causa raíz

El FAB de localización hace **dos cosas distintas** según el estado, y sólo una de ellas encuadra
(`HomeScreen.kt:1536-1549`):

```kotlin
val puck = drivingPuck.value
if (puck != null) {
    uiController.resumeDriverFollow(puck.latitude, puck.longitude)   // ← sin zoom
} else {
    currentUserGpsPoint.value?.let {
        uiController.goToPlace(it.latitude, it.longitude, zoom = 16f) // ← con zoom
    }
}
```

Y `resumeDriverFollow` es `setTarget(lat, lon)` a secas. O sea: **parado, el botón te acerca;
en ruta, el mismo botón con el mismo icono en el mismo sitio sólo recentra.** Justo cuando la cámara
está más lejos de donde te interesa mirar.

El arreglo de `baba6104f` no llega aquí porque su `firstFollowPending` lo arma
`setDriverFollowActive(true)`, que sólo se llama cuando el viaje **empieza**
(`snapshotFlow { drivingPuck.value != null }`). Al pulsar el FAB el seguimiento ya está activo desde
hace rato: no hay primer encuadre que armar, y no había ningún otro camino que pusiera zoom.

## Doctrina violada

El zoom no es propiedad de la RAMA que atiende la pulsación, es propiedad del ACTO: pedir «llévame a
mi localización» es pedir un encuadre. Que la cámara esté siguiendo un coche es un detalle de
implementación del botón, no algo que el usuario haya pedido.

Y el argumento que justifica no tocar el zoom mientras se sigue —*«nadie quiere que la cámara le
deshaga el pellizco cada vez que llega un fix»*, escrito en `followDriver`— **no aplica a una
pulsación**: un fix de GPS no lo pidió nadie; el botón sí.

## Diseño

`resumeDriverFollow` encuadra con el mismo suelo que el primer seguimiento:

```kotlin
setTarget(lat, lon, zoom = FOCUS_DRIVING_ZOOM, zoomIntent = ZoomIntent.AtLeast)
```

- **El MISMO `FOCUS_DRIVING_ZOOM` (16)** que arma el primer seguimiento, no un número nuevo: es la
  misma pregunta —a qué distancia se mira un coche en marcha— y ya tiene respuesta escrita.
- **`AtLeast`, un SUELO.** A quien ya estaba más cerca no se le aleja; a quien mira de lejos se le
  acerca. Es la regla que [MAP-A-CAMERA-MOVE-MUST-NOT-COST-A-PINCH-001] fijó para `goToPlace`, y
  reanudar el seguimiento es un movimiento pedido igual que aquél.

Y el `16f` suelto del call site pasa a nombrar la constante que ya existe (`FOCUS_SEARCH_ZOOM`): era
el mismo número escrito dos veces, una de ellas inline contra la regla de magic numbers.

⛔ **Y reanudar CONSUME el encuadre que quedara armado.** `setDriverFollowActive(true)` deja
`firstFollowPending`; si la pulsación no lo consumiera, el siguiente fix volvería a aplicar el
suelo — y entre la pulsación y ese fix el usuario puede haber abierto el mapa a propósito.

## Tests

`HomeUiControllerTest`, 3 nuevos:
- **el testigo**: con el seguimiento ya en marcha, `resumeDriverFollow` encuadra a
  `FOCUS_DRIVING_ZOOM` con intención `AtLeast`;
- la mitad que ya era correcta y no se puede perder: el FAB sigue reenganchando el seguimiento que
  el usuario había soltado al tocar otro sitio;
- el fix siguiente a la pulsación NO reencuadra.

⚠️ **Falsificados**: reinyectado `setTarget(lat, lon)` a secas, **dos de los tres fallan** (el de
reenganche pasa, porque esa mitad nunca estuvo rota — y por eso está escrito aparte).

## Criterio de éxito

- En ruta, con el mapa alejado, pulsar el FAB de localización acerca hasta 16 y centra en el coche.
- En ruta, con el mapa ya más cerca de 16, pulsarlo centra y **no aleja**.
- Los fixes de GPS que llegan después siguen sin tocar el zoom (el pellizco del usuario se respeta).

## Consumidores auditados

`grep resumeDriverFollow|followDriver|setDriverFollowActive` — todos los caminos que mueven la
cámara hacia el coche en marcha:

| Camino | Quién lo dispara | Zoom | Veredicto |
|---|---|---|---|
| `setDriverFollowActive(true)` → primer `followDriver` | el viaje EMPIEZA (`snapshotFlow`) | `FOCUS_DRIVING_ZOOM`, `AtLeast` | correcto desde `baba6104f` |
| `followDriver` siguientes | cada fix de GPS | ninguno, a propósito | correcto — nadie lo pidió |
| `resumeDriverFollow` | **el FAB de localización** | ninguno | **el bug** → gana el suelo |
| `goToPlace(zoom = 16f)` | el mismo FAB, sin viaje | `16f` inline | correcto, pero el número se nombra |

`resumeDriverFollow` tiene **un solo call site** (`HomeScreen.kt:1542`), el del FAB — así que el
cambio no tiene otra puerta por la que escaparse.
