# PUSH-THE-WORK-MUST-FINISH-INSIDE-THE-CALLBACK-001 · El servicio de mensajería lanza el trabajo y se va

**Estado:** 🔵 Abierto — 1.2.1
**Origen:** revisión independiente, 3.ª vuelta — M5.

## Problema

`PaparcarMessagingService.onMessageReceived` **lanza** una corrutina y retorna. Android da por
terminado el trabajo cuando ese método vuelve, así que a partir de ahí el proceso puede morir con la
valla a medio mover o la tarjeta sin pintar — y cuanto más hace el handler, más probable es.

Los filtros nuevos de `0fdf4514` agravan justo eso: ahora lee zonas y coches antes de decidir, o sea
más I/O dentro de una ventana que ya no está garantizada.

## Arreglo propuesto

Hacer el trabajo **dentro** del callback con `runBlocking` + `withTimeout(8 s)` — que es el contrato
que Android espera de `onMessageReceived` — y que `isAGroupOfMine` lea de `SharedCarDao` en vez de
un `Flow` remoto, para que el coste sea una lectura local y no una suscripción.

⚠️ Mirar también el caso de la instalación limpia: con Room aún vacío, un filtro que consulta local
puede descartar un aviso legítimo. Ese es el fallo mudo que hay que evitar al apretar esto.
