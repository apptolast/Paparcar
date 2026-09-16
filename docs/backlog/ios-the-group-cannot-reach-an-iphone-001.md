# IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001 · en iOS no hay FCM, así que ningún aviso llega

**Estado:** 🔵 Abierto, sin empezar · sin rama todavía
**Sale de:** `SHARED-A-PARKING-TELLS-THE-GROUP-001` / `SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001` /
`SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001`, al preguntar el user qué pasa con un miembro en
iOS (16-09).

## Problema

**En `iosMain` no hay mensajería. Ni token, ni recepción.** `grep` limpio: la única pieza de push del
proyecto (`PaparcarMessagingService`, `ZoneAlertSubscriptionSync`, `SharedCarMemberSync`) vive entera
en `androidMain`. Consecuencia, hoy:

| Aviso | Android | iOS |
|---|---|---|
| Plaza en tu zona (`zone_spot`) | push inmediato | **nada** |
| El grupo ha aparcado el coche (`shared_car_parked`) | push + valla movida al instante | **nada** — la valla se mueve en la siguiente pasada de reconciliación |
| Se han llevado el coche (`shared_car_left`) | push + valla levantada | **nada**, ídem |
| Combustible más barato / precio que se mueve | notificación local, ya funciona | igual (no es push) |

Y no es solo que no reciba: **el iPhone tampoco publica token**, así que para el reparto del servidor
ese miembro no tiene dispositivos (`targetsFor` lo descarta) y la Cloud Function nunca intenta
llegarle. El hueco es mudo por los dos lados — ni error, ni log, ni fila.

## Doctrina violada

Ninguna de detección. La que se rompe es de producto: **Plus = la app te avisa a ti**. Un usuario de
iOS con Plus pagaría por la vigilancia y no la tendría — y el eje de la tabla de precios es
exactamente quién trabaja cuando tú no miras.

## Lo que hay que hacer

1. **APNs + FCM en el target iOS** (`GoogleService-Info.plist`, capability de Push Notifications,
   `UNUserNotificationCenter`, la clave de APNs en la consola de Firebase).
2. El gemelo de `SharedCarMemberSync` y `ZoneAlertSubscriptionSync` en `iosMain` — ⚠️ mirar primero
   qué parte de los dos se puede subir a `commonMain`: la decisión de **qué** publicar y **cuándo**
   ya es común; lo específico es el lector del token y el ciclo de vida.
3. El gemelo del reparto de `PaparcarMessagingService`: el mismo orden que en Android —
   **primero la valla, después la tarjeta** (en iOS, `reconcileFences` de `IosDetectionController`).
4. Las 9 traducciones ya están: la copia es la misma, y el mensaje es **de datos** justo para que el
   texto lo ponga el cliente.

## Lo que NO es este ticket

⛔ **El carril Bluetooth de iOS no entra, porque no existe y no puede existir.** Apple no expone el
Bluetooth clásico (HFP/A2DP) —el que usan los coches— a apps de terceros;
`IosBluetoothScanner.getBondedDevices()` devuelve lista vacía por diseño y lo documenta en su
cabecera. Un iPhone cae SIEMPRE en Coordinator, y por eso un miembro en iOS nunca podrá anunciar que
se lleva el coche por la vía de `SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001`. Eso no es un hueco
que tapar aquí: es una capacidad que la plataforma no da. Lo que sí le llegaría con este ticket es
enterarse de lo que hacen los demás.

## Bloqueo

🧊 iOS está **congelado desde el 07-09** por decisión del user. Este ticket queda escrito para que el
hueco no se olvide, no para ejecutarlo ahora.
