# TRIP-A-REFUSED-ROUTE-IS-NOT-A-BLANK-MAP-001 · una ruta rechazada no es un mapa en blanco

**Estado:** ✅ Done · rama `fix/TRIP-A-REFUSED-ROUTE-IS-NOT-A-BLANK-MAP-001` · issue #101

## Problema
Hueco encontrado **auditando** #41, no reportado por nadie — y comprobado que **no** es lo que le
pasó al user (en sus 40 sesiones no hay ni un `REJECTED`: todas `CONFIRMED` o null). Pero el camino
existe y muerde:

Un pin sin conducción medida pero con colocación de confianza recibe una ruta reconstruida pin a
pin, marcada **entera** como inferida, y la app pregunta *«¿condujiste por aquí?»*
[ROUTE-GAP-HONEST-001]. Si el usuario contesta **No**:

- `routeInferredResolution = REJECTED`
- `InferredRoute.split()` descarta los tramos inferidos → no queda ningún segmento
- pero `routePolyline` sigue ahí y `routeSnapped = true`

→ `routeAvailability()` decía **`Drawn`**, no salía ninguna píldora, y el mapa no dibujaba nada.
**Lienzo en blanco sin explicación**, que es exactamente el defecto que
[TRIP-A-ROUTE-THAT-ONLY-LIVES-HERE-SAYS-SO-001] acababa de cerrar para las otras situaciones.

## Doctrina violada
La misma, y agravada: de las cinco situaciones, ésta es **la única donde la ausencia la causó el
propio usuario**. Contestas y la línea desaparece; sin una frase, lo que ve es que la app se rompió
al contestarle. Quien acaba de interactuar es quien MÁS derecho tiene a una explicación.

## Diseño
Quinto estado, `RejectedByUser`, decidido **antes** que `Drawn`.

⛔ **La pregunta correcta no es «¿rechazó?» sino «¿queda algo que dibujar después de rechazar?»**.
Una ruta con tramos MEDIDOS y tramos inferidos rechazados sigue dibujando los medidos, y ésa no
está en blanco: no hay nada que explicar. El arreglo perezoso (`resolution == REJECTED` a secas)
habría puesto una píldora encima de una línea perfectamente visible.

⛔ **El veredicto y el dibujo salen del MISMO cálculo**: nace `drawableRouteSegments()`, y la
pantalla lo consume en vez de llamar a `InferredRoute.split` por su cuenta. Dos opiniones sobre si
hay línea es exactamente cómo se llega a una píldora que miente sobre lo que se está viendo.

⚠️ **Efecto lateral, y correcto**: el veredicto ahora exige una línea **utilizable**, no una cadena
no vacía. Un test viejo usaba `polyline = "abc"` y pasó a `LostOffDevice` — una polilínea que no
decodifica nunca se dibujó, así que llamarla `Drawn` era falso desde el principio.

## Criterio de éxito
4 testigos nuevos (10 en total en el fichero), **falsificados contra el arreglo perezoso**: con
`resolution == REJECTED` a secas cae `should_stillDraw_when_someStretchesAreMeasured_…`.
- ruta entera inferida + `REJECTED` → se dice;
- tramos medidos + inferidos rechazados → se dibuja y **no** se dice nada;
- `CONFIRMED` dibuja;
- sin resolución todavía → se dibuja y se pregunta, no se sentencia.

Strings en los **9 locales**. Variante en la galería de estados.

## ⛔ Fuera de alcance, a propósito
**No hay «deshacer»**. La issue lo mencionaba como *«si cabe»*. Hoy la frase explica, pero no
devuelve: para cambiar de opinión hay que poder re-contestar, y eso es una decisión de interacción
(¿la píldora es pulsable? ¿vuelve la pregunta?) que merece su propio ticket, no un botón colado en
un arreglo de honestidad.

## Consumidores auditados
`grep` de `InferredRoute.split` — **1 sitio** fuera del dominio (`ParkingHistoryDetailScreen`),
cerrado: pasa a `drawableRouteSegments()`. Nadie más parte tramos.
