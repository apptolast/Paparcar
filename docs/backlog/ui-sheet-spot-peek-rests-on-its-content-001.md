# UI-SHEET-SPOT-PEEK-RESTS-ON-ITS-CONTENT-001 · la tarjeta de una plaza seleccionada se queda anclada donde la dejó el estado anterior

**Estado:** ✅ Done · mergeado a master

## Problema

Al tocar una plaza (marcador o fila de la lista) el sheet se abre **muy por encima de su
contenido**: la tarjeta termina en «Se fue» y debajo queda un tercio de pantalla de superficie
vacía hasta la bottom-nav. Captura del user (Redmi, 29-08).

El valor no es arbitrario: el borde superior del sheet cae en **0,42 · (alto − bottom-nav)**, que es
exactamente `EXPANDED_MAP_VISIBLE_FRACTION`, el ancla «desplegado» del estado **Browse**.

### Cómo se produce

Cada productor de posición del sheet (`animateToExpanded`, `toggle`, el fling, el drag) recorta su
destino contra los anclas del frame **en el que se dispara**, y **nadie los revisa después**.
Seleccionar una plaza baja el techo un frame más tarde que el toque que la seleccionó:

| Frame | Qué pasa |
|---|---|
| N | `onSpotMarkerClick`: `SelectItem` + cámara + `motion.animateToExpanded()`. `positioning` es todavía el de Browse → destino `0,42 · container`. La animación despega. |
| N+1 | Recomposición con `selectedSpotId != null` → `capExpandAtPeek` pasa a `true` → `fullSnap = peek`. Pero **`peekOffsetPx` no se mueve** (el peek handle aún no ha medido el `SpotPeek`), así que `LaunchedEffect(peekOffsetPx)` ni se dispara. `LaunchedEffect(selection, mode)` sí, pero cae en la rama `else if (!sheetOffsetPx.isRunning …)` y la animación **está** corriendo → no hace nada. |
| N+2 | El peek handle mide el `SpotPeek` → `peekOffsetPx` cambia → se dispara la corrección de layout… que vuelve a exigir `!isRunning`. Sigue corriendo. No hace nada. |
| fin | El sheet aterriza en `0,42 · container`, un ancla que **ya no existe** en la geometría de este estado. |

`isParkingSelected` no sufre esto porque `resetToPeek = isPinning || isParkingSelected` lo mete en
la rama de corrección, que **sí** re-apunta una animación en vuelo. La plaza seleccionada era el
único estado que caía en el hueco.

## Doctrina violada

**Sistemas, no parches** — el invariante «este estado no tiene más ancla que el peek» estaba escrito
**dos veces y en dos sitios**:

- en `HomeScreen`, como `capExpandAtPeek = mode !is Browse || isParkingSelected || selectedSpotId != null`
- en `SheetTransitionEffects`, como `resetToPeek = isPinning || isParkingSelected`

La segunda lista **olvidó la plaza seleccionada** cuando [UI-PEEK-STEPS-BETWEEN-PINS-001] retiró la
lista de debajo del spot peek y lo convirtió en dueño de toda la superficie. Dos predicados a mano
para el mismo hecho: uno se actualizó, el otro no.

## Señales / datos disponibles

Geometría en memoria, sin telemetría de por medio: `fullSnapOffsetPx == peekOffsetPx` **es** el
hecho «el peek es dueño de la superficie». No hace falta re-enumerar estados para saberlo.

## Diseño

El invariante se lee de la **geometría**, en un solo sitio:

```kotlin
/** El peek es el único ancla: nada puede descansar por encima de él. */
internal fun SheetPositioning.capsAtPeek(): Boolean = fullSnapOffsetPx >= peekOffsetPx
```

Con eso:

1. `SheetTransitionEffects` deja de recibir `isParkingSelected` y de derivar `isPinning`/`resetToPeek`.
   La condición de corrección pasa a ser `selection == null || positioning.capsAtPeek()` — que cubre
   modos pin, parking seleccionado **y plaza seleccionada**, sin lista que mantener.
2. La rama de corrección (la que **sí** re-apunta una animación en vuelo) queda extraída en
   `Animatable.settleAtPeek(peek, tolerance)`, para que las dos efectos la compartan literalmente en
   vez de repetirla.
3. `isIntentionallyAbovePeek` sigue igual; su `canExpandAbovePeek` pasa a ser `!capsAtPeek()`, la
   misma expresión que ya calculaba a mano.

Consecuencia buscada: en un estado con techo en el peek, **una re-medida del peek re-apunta la
animación en vuelo** en lugar de cederle el paso. Es lo que ya hacían los modos pin; la plaza se une
a ellos. `animateToExpanded()` en el tap del marcador se queda: aporta el MOVIMIENTO (la subida
suave desde minimizado o desde la lista), y la corrección aporta el DESTINO.

## Criterio de éxito

- Tocar una plaza (marcador, fila de la lista o chevron ‹ / ›) desde peek, desde minimizado y desde
  la lista desplegada deja el sheet **a la altura exacta de la tarjeta**, sin aire muerto debajo.
- Tests puros nuevos sobre `capsAtPeek()` en `HomeSheetPositioningTest`.
- `:shared:testDebugUnitTest` verde.

### Medido (29-08, `assembleMockDebug`, emulator-5556)

Escenario «Con coches» + permisos `All`, las tres vías de entrada al spot peek:

| Vía | Antes | Después |
|---|---|---|
| Fila de la lista con el sheet desplegado (`Plaza del Arenal 1`, el de la captura) | borde superior en 0,42 · container, ~1/3 de pantalla vacía | tarjeta cerrada sobre la bottom-nav, sin hueco |
| Toque en el marcador del mapa (`Calle Corredera 8`) | mismo fallo | ✅ asentada |
| Chevron › entre plazas | ✅ ya iba (geometría ya capada al pulsar) | ✅ sin cambio |

`HomeSheetPositioningTest`: 13 → 16 casos, verde. `:app:compileProdDebugKotlin` +
`:app:compileMockDebugKotlin` verdes.

## Consumidores auditados

Todo lo que fija una posición del sheet, y si re-comprueba la geometría después:

| Sitio | Recorta contra | Veredicto |
|---|---|---|
| `SheetMotion.animateToExpanded` (tap marcador plaza / coche) | `positioning.value` del frame del tap | ⚠️ **la vía donde mordió** — cubierto por la corrección re-apuntadora |
| `SheetMotion.toggle` | `positioning.value` del frame del tap | cubierto por la misma corrección |
| `SheetTransitionEffects` · auto-open del prompt | `positioning.expandedOffsetPx`, ya recortado en `computeSheetPositioning` | cubierto |
| `SheetMotion.nestedScrollConnection` | `fullSnapOffsetPx` vivo, cada frame del gesto | cerrado |
| `HomeBottomSheet` drag | `dragSnap.fullSnap … minimized`, `remember(positioning)` | cerrado |
| `HomeSheetSnap.snapTarget` (fling/soltar) | `coerceIn(fullSnap, minimized)` | cerrado |
| `HomeScreen` `capExpandAtPeek` | — | es ahora la **única** enumeración de estados; `resetToPeek` se borra |
