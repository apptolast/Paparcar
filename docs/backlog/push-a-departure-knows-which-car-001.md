# PUSH-A-DEPARTURE-KNOWS-WHICH-CAR-001 · El aviso de salida traía el coche y el móvil lo tiraba

**Estado:** ✅ Done (2026-09-16) — el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 3.ª vuelta — hallazgo A3.

## Problema

El servidor manda `carId` en el mensaje `shared_car_left` (`functions/src/index.ts`), y
`PushPayload.SharedCarLeft` **solo leía `authorName`**. Con el campo tirado, el filtro de
destinatario que entró en `0fdf4514` no podía preguntar «¿es de un grupo mío?» y se conformaba con
«¿pertenezco a algún grupo?».

Consecuencia: a cualquiera que esté en un grupo se le podía pintar la salida del coche de otro. Y
el KDoc afirmaba que el servidor «solo manda quién se lo llevó», que era falso — se escribió sin
mirar el emisor.

## Arreglo

`SharedCarLeft` lleva `carId`, el parser lo lee, y `isAGroupOfMine` usa el mismo criterio para los
dos tipos de noticia. ⛔ Un mensaje **sin** `carId` no se puede atribuir a ningún grupo, así que no
se pinta: es la respuesta correcta a un dato que falta, no una excepción.

## Criterio de éxito

Una noticia de salida de un coche que no es de mis grupos no se muestra, aunque yo esté en otros.
