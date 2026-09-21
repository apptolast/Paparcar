# TRIP-A-ROUTE-THAT-ONLY-LIVES-HERE-SAYS-SO-001 · la ruta que sólo vive aquí lo dice

**Estado:** ✅ Done · rama `fix/TRIP-A-ROUTE-THAT-ONLY-LIVES-HERE-SAYS-SO-001` · issue #41

## Problema
«Las rutas no aparecen en el historial» (#41, abierta desde hace semanas). Y no era eso.

## Lo que se midió
25 sesiones reales de la cuenta del user en `pap-26`. El corte es limpio:

| | sesiones | rango |
|---|---|---|
| **Con** polilínea | 9 | 03-09 22:11 → **12-09 21:57** |
| **Sin** polilínea pero **con** `routeDistanceMeters` | 9 | **12-09 22:37** → 20-09 12:08 |

Distancias de las que no tienen geometría: **8791, 11840, 6111, 3238, 8142, 6703, 799, 3413, 796 m**.

`routeDistanceMeters` se estampa **en la misma escritura** que la polilínea
(`updateParkingSessionRoute` lo calcula con `PolylineCodec.lengthMeters(routePolyline)`), así que una
fila que dice 11,8 km sin un solo punto es una fila a la que le quitaron la geometría **después** de
medirla.

Quién se la quita está escrito y es intencionado: `withoutRouteGeometry()`
[PRIV-A-TRACE-IS-NOT-A-STAT-001]. Sin consentimiento de rutas en la nube —gateado además a Plus— las
CIFRAS viajan y la GEOMETRÍA se queda en el móvil. Encaja hasta en el detalle: la sesión del 12-09
22:37 **sí** tenía ruta y su `updateTime` es **15-09 17:29** — se re-empujó cuando el filtro ya
existía y la perdió; su gemela de las 21:57, no re-empujada, la conserva.

Por qué entonces no se ve en el móvil: la ruta era local-only y el móvil perdió su Room. Reinstalar
—obligatorio al pasar de release a debug (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`)— borra Room, el
historial se rehidrata desde Firestore, y allí la geometría estaba **en blanco a propósito**.

## Doctrina violada
Ninguna de las que suelen: el filtro de privacidad hace exactamente lo que promete. Lo que falla es
**decirlo**. «Este viaje no tuvo recorrido» y «este viaje tuvo recorrido y ya no lo tenemos» son dos
frases distintas y las dos se dibujaban igual: **en blanco**. Un usuario mirando un lienzo vacío no
puede distinguir un pin manual de una pérdida, y por eso el reporte llegó como «no se pintan».

⚠️ Y de ahí salió una conclusión falsa del propio user: *«entonces no tenemos ruta porque hay que
activar el Plus»*. **No**: la ruta se graba y se dibuja en el móvil siempre. Plus sólo respalda. Que
el producto dejara creer eso es el defecto.

## Diseño
Un **veredicto**, no cuatro condiciones sueltas en la pantalla:
`domain/session/RouteAvailability.kt` → `Drawn` · `Recalculating` · `LostOffDevice` · `None`.

⚠️ El orden de las ramas importa: `Recalculating` se decide **antes** que la distancia, porque un
viaje recién aparcado tiene puntos y distancia a la vez y no está perdido, está en cola. Al revés,
cada aparcamiento nuevo anunciaría una pérdida durante los minutos del snapping.

⛔ `(routeDistanceMeters ?: 0f) > 0f`, no `!= null`: cero metros no prueba que hubiera recorrido, y
acusar de pérdida a un viaje que nunca tuvo trazado es la mentira **simétrica**.

En pantalla: la misma píldora flotante que el recálculo (`RouteNoticePill`), porque las dos
contestan la misma pregunta —*¿por qué no hay línea?*— y dos formas distintas obligarían a aprender
dos. ⛔ El spinner sólo acompaña a lo que está en curso: una rueda girando junto a una frase
definitiva promete algo que no va a llegar.

Y el interruptor de Ajustes deja de vender conveniencia y dice la consecuencia. Antes:
> «Tus trayectos se guardan solo en este móvil. Actívalo para verlos también en tus otros dispositivos.»

Ahora:
> «Tus rutas se guardan solo en este móvil, así que reinstalar la app las pierde. Actívalo para
> guardarlas en tu cuenta y verlas en tus otros dispositivos.»

## Criterio de éxito
`RouteAvailabilityTest`, 6 testigos. **Falsificados**: quitando la rama de pérdida caen 2.
Cubren las cuatro situaciones, el orden de las ramas y `routeSnapped = true` con polilínea vacía —la
forma que escribe el filtro de privacidad— que no puede leerse como «hay línea».

Strings en los **9 locales**. Galería de estados con las dos píldoras.

## ⛔ Lo que este ticket NO hace
- **No recupera ni una ruta perdida.** Nunca salieron del teléfono.
- **No decide si el respaldo del recorrido debe estar gateado a Plus.** Ahora mismo privacidad y
  monetización se contestan con el MISMO interruptor, y son dos preguntas: *¿quiero que mi trazado
  salga del móvil?* y *¿pago por tener copia?*. Un usuario que quiere respaldo y no quiere pagar no
  tiene respuesta, y uno que paga no puede tener respaldo sin aceptar la subida. Decisión de
  producto, del user. Queda escrita aquí y en #41.

## Consumidores auditados
`grep` de `routeSnapped` / `routePolyline` en presentación:

| Sitio | Veredicto |
|---|---|
| `ParkingHistoryDetailScreen` (dibujo, chip, segmentos) | **cerrado** — deriva de `routeAvailability()` |
| `HistoryDetailSheet` / filas de historial | exento — hablan de la CIFRA (distancia), que sí viaja |
| `SharedCarHistory` · `SharedCarMirror` | exentos — su geometría la gobierna `sharesRoutes`, otra puerta y otro permiso [PRIV-A-ROUTE-LEAVES-THE-PHONE-BY-ONE-DOOR-001] |
| `UserParkingReconcile` | exento y **verificado correcto**: `r.routePolyline ?: l?.routePolyline` ya protege la copia local de un sync-back sin geometría |
