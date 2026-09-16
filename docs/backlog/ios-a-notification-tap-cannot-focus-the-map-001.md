# IOS-A-NOTIFICATION-TAP-CANNOT-FOCUS-THE-MAP-001 · en iOS tocar la tarjeta solo abre la app

**Estado:** 🔵 Abierto, sin empezar · sin rama
**Sale de:** `IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001` (16-09), al escribir las tres tarjetas de push
en la superficie de iOS y encontrarse con que la carencia ya estaba.

## Problema

En Android, cuatro notificaciones llevan un `contentIntent` que abre el mapa **centrado en el punto**
(`buildFocusIntent` en `AppNotificationManagerImpl`): aparcamiento guardado, plaza en tu zona, el
grupo ha aparcado el coche, y gasolinera más barata. En iOS ninguna lo hace: tocar la tarjeta abre la
app donde estuviera.

No es una regresión de este ticket. `showParkingSaved` vive con ello desde que existe la superficie de
iOS, y las tres nuevas heredan lo mismo. Lo que sí cambia es que ahora **duele más**: dos de las
tarjetas nuevas responden literalmente a la pregunta *¿dónde está eso?*, y la respuesta se queda a un
toque de distancia.

## Diseño, cuando se haga

`IosNotificationActionHandler` ya recibe `UNNotificationDefaultActionIdentifier` —el toque en el
cuerpo— y hoy lo deja pasar al sistema. Lo que falta es:

1. Llevar la posición en el `userInfo` de las tarjetas que la tengan (como ya se hace con
   `parkingId` y `geofenceId`).
2. Un puerto de «enfoca el mapa aquí» que la capa de presentación escuche, equivalente a lo que en
   Android hace el extra del intent. ⛔ Ojo con
   [UI-A-PARKED-CARD-SAYS-WHERE-AND-OFFERS-THE-WAY-ACROSS-001]: `restoreState` restaura ARGUMENTOS
   VIEJOS, así que un destino que lleve las coordenadas en la ruta puede volver al punto anterior.

## Alcance

Las cuatro tarjetas a la vez, no solo las de push: es un invariante de la superficie
(«una tarjeta que nombra un sitio lleva a ese sitio»), y arreglarlo en dos de cuatro sería dejar la
mitad del sistema con la regla contraria.
