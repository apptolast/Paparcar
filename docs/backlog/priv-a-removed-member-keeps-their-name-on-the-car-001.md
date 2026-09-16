# PRIV-A-REMOVED-MEMBER-KEEPS-THEIR-NAME-ON-THE-CAR-001 · Expulsar dejó de borrar el nombre del expulsado

**Estado:** ✅ Done (2026-09-16) — el hash vive en `MEMORY.md`. **Regresión introducida por `b3ae303b`.**
**Origen:** 4.ª vuelta — A2. **Reproducida en el emulador.**

## Problema

`removeMember` corre con la sesión **del dueño** y llama a `clearMemberFromState` para quitar el
nombre **del expulsado**. La regla nueva exigía que la escritura se firmara a sí misma
(`claimsOwnAuthorship`) o que borrase el nombre propio (`clearsOnlyTheirOwnName`): ninguna de las dos
la cumple el dueño limpiando lo de otro.

Denegado, y `clearMemberFromState` se lo traga. O sea que
`PRIV-A-REMOVED-MEMBER-LEAVES-THEIR-TRIPS-BEHIND-001` —que existía justo para que expulsar limpiara
el rastro— quedó a medias **en el mismo commit** que endureció la regla.

## Arreglo

`ownerClearsNames()`: el dueño puede tocar `authorName`, `movedByUid` y `movedByName` **y solo para
ponerlos a null**. No puede escribir un nombre, solo borrarlo — que es la única operación que
expulsar necesita.

## Testigo

`state.test.ts` · *A2 · el dueño borra el nombre del expulsado*.
