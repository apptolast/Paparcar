# DET-A-MANUAL-RELEASE-LEAVES-THE-ARRIVAL-ENTER-IN-THE-BUS-001 · la liberación manual no resetea el bus de salidas

**Estado:** 🔵 Abierto, sin empezar · sin rama

## Problema

`ProcessConfirmedDepartureUseCase` resetea `DepartureEventBus` al cerrar una sesión
(`departureEventBus.reset()`, justo antes de quitar la geocerca). `ReleaseActiveParkingSessionUseCase`
—la liberación manual, «Me voy»— **no lo hace, y ni siquiera inyecta el bus**. Tras una liberación
manual el bus conserva el `IN_VEHICLE_ENTER` del viaje de LLEGADA.

Ese es exactamente el estado que `ConfirmParkingUseCase` limpia a propósito al guardar un
aparcamiento, con su motivo escrito: *sin ese reset, alejarse andando del coche dentro de los 30 min
de `vehicleEnterWindowMs` confirma una salida falsa* [BUG-WALK-DEPART-001]. La puerta manual quedó
fuera de esa limpieza.

## Procedencia

Segundo hallazgo del §5.1 de `docs/detection/07-duplicacion.md`, catalogado como «para adjudicar, NO
arreglar». El primero (la zona privada) se cerró en
**SPOT-A-PRIVATE-ZONE-DECIDES-THE-PUBLICATION-NOT-A-BUTTON-001**; este se dejó fuera de alcance a
propósito: es un bug de detección, no de publicación, y merece su propia medición.

## Sin decidir todavía — ¿bug o intención?

La pregunta del §5.1 sigue en pie y **no se ha contestado**. Antes de tocar nada:

1. ¿Puede una liberación manual dejar un ENTER dentro de la ventana? El usuario libera junto al
   coche y arranca — el ENTER *nuevo* es el que debería armar. ¿El viejo adelanta algo?
2. ¿Qué lee ese timestamp después de que la sesión ya no existe? Sin sesión activa no hay salida que
   confirmar, así que puede ser inocuo — o puede envenenar el arme del viaje siguiente.
3. Medirlo en una traza real antes de añadir el reset. Añadirlo «por simetría» sería el parche que
   la doctrina prohíbe.

## Criterio de éxito

Una respuesta medida a (1)-(3) y, si es bug, el reset en la puerta manual con su test —
o una línea en `PARKING-DETECTION.md` explicando por qué la asimetría es correcta.
