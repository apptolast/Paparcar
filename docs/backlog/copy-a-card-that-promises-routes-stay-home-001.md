# COPY-A-CARD-THAT-PROMISES-ROUTES-STAY-HOME-001 · La tarjeta de compartir promete lo contrario de lo que hace el código

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 2.ª vuelta — hallazgo C3. **Verificado.**

## Problema

`shared_car_not_shared_body` —la tarjeta que explica qué verá el grupo ANTES de abrirlo— termina
diciendo *«Las rutas personales no se comparten nunca»* / *«Personal routes are never shared»*.

Era cierto cuando se escribió. Dejó de serlo con `e8baaf35`: la ruta viaja al grupo cuando el
interruptor del coche está encendido, **y venía encendido**. Así que la única superficie que explica
el trato antes de aceptarlo decía justo lo contrario, y contradecía la §2.9 de la política
desplegada el mismo día.

⛔ No es un problema de redacción: es el sitio donde el usuario da su consentimiento.

## Decisión del user (16-09): opción C — **preguntar, sin default**

Ni encendido ni apagado por omisión: compartir y unirse **preguntan**, y lo que se responde es lo
que se guarda. Con eso el copy deja de tener que disculpar un default.

## Diseño

- La confirmación de compartir y el diálogo de unirse llevan la elección de rutas.
- Lo elegido escribe `shareRoutesInGroup` (fila local) y `members/{uid}.sharesRoutes` — las dos
  escrituras que **ya existen** en `setShareRoutes`, sin estrenar camino.
- El copy describe una pregunta, no un interruptor con default.
- 9 locales, `LocaleParityGuardrailTest`.

## Criterio de éxito

Nadie comparte ni entra en un grupo sin haber visto la pregunta, y la tarjeta describe exactamente
lo que va a pasar.
