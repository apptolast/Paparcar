# PRIV-AN-EXPIRED-SESSION-STILL-OWNS-THE-TOKEN-001 · Una sesión que caduca deja el token vivo

**Estado:** ✅ Done (2026-09-16) — el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 3.ª vuelta — hallazgo M2.

## Problema

`SignOutUseCase` cubre las salidas **por la puerta**: el logout de Ajustes y el abort del arranque.
No cubre la que nadie pide — que el token de Auth caduque o se revoque.

Esa transición tiene un único hook, `SplashViewModel.wipeLocalUserData()`, que su propio KDoc
describe como *«the single hook that guarantees no previous user's rows leak into the new session,
regardless of which path triggered the sign-out»*. Vaciaba Room y las geovallas, y **no tocaba el
token**: el servidor se quedaba con un token vivo apuntando a un móvil cuya sesión ya no existe.

## Arreglo

`retireToken()` al principio del barrido, **con plazo de 3 s**: aquí no hay nadie esperando en una
pantalla, así que una red mala no puede convertir esto en un arranque colgado.

⚠️ Solo se puede retirar el token LOCAL: las filas del servidor necesitarían la sesión que acaba de
caducar. Lo que las neutraliza es el filtro de destinatario de `PushMessageHandler`.
