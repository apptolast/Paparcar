# IOS-A-SILENT-PUSH-CAN-ARRIVE-LATE-OR-NEVER-001 · en iOS el aviso llega cuando Apple quiere

**Estado:** 🔵 Abierto, sin empezar · sin rama
**Sale de:** `IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001` (16-09), como límite conocido y asumido al
cerrarlo.

## Problema

Los tres avisos del proyecto viajan como mensajes **de datos**, y esa decisión es correcta y está
razonada en dos sitios (`functions/src/index.ts`, `PushMessageHandler`): el texto en nueve idiomas y
el dedup de lo ya anunciado viven en el cliente, y un mensaje de notificación los dejaría al
servidor, que no sabe ninguno de los dos.

El precio lo paga iOS y no Android:

| | Android | iOS |
|---|---|---|
| Prioridad de entrega | `high` — despierta de Doze | `apns-priority: 5`, la ÚNICA legal para `apns-push-type: background` |
| Presupuesto | sin límite por app | Apple presupuesta los `content-available` por app y dispositivo, y los recorta en bajo consumo |
| App cerrada por el usuario | el push llega igual | **no llega ninguno** hasta que el usuario vuelva a abrirla |

O sea: una plaza libre que vale minutos puede llegar tarde, o no llegar. No es un bug del código —
es la contrapartida de que el mensaje sea de datos.

## Lo que habría que medir antes de tocar nada

Cuánto tarda de verdad, en un iPhone real, con y sin bajo consumo, y con la app en segundo plano y
cerrada. Sin ese dato no hay ticket: puede que la latencia típica sea de segundos y no haya nada que
arreglar.

## La salida, si el dato dice que hace falta

Una **Notification Service Extension**: el servidor manda un push de ALERTA con `mutable-content: 1`
y la extensión reescribe título y cuerpo con las cadenas de la app antes de que se pinte. Con eso el
aviso recupera la prioridad 10 y sigue hablando en el idioma del usuario.

⛔ Lo que NO resuelve, y hay que decirlo al decidir: la extensión puede reescribir el texto, pero **no
puede callar una tarjeta** — el dedup de `zone_alerted_spots` y el silencio del propio viaje seguirían
sin poder aplicarse. Cambiar a alerta es cambiar «a veces tarde» por «a veces repetido».

## Lo que NO es este ticket

Volver a mensajes de notificación puros. Eso pondría la copia en el servidor y rompería el motivo por
el que el reparto está escrito como está.
