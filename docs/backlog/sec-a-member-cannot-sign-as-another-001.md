# SEC-A-MEMBER-CANNOT-SIGN-AS-ANOTHER-001 · Bastaba ponerse en movedByUid para firmar como otro

**Estado:** ✅ Done (2026-09-16) — el hash vive en `MEMORY.md`. **Regresión introducida por `b3ae303b`.**
**Origen:** 4.ª vuelta — A3. **Reproducida en el emulador.**

## Problema

`claimsOwnAuthorship()` aceptaba la escritura si **el documento resultante** llevaba el uid del
escritor en `authorUid` **o** en `movedByUid`. Así que un miembro podía escribir una cabecera entera
—`authorUid` y `authorName` de otra persona, posición inventada— y colarse por la segunda mitad
poniéndose a sí mismo en `movedByUid`.

Y desde el 16-09 esa escritura **dispara un push a todo el grupo**: «Marta ha aparcado en X», con la
X que quiera quien lo firme.

El agujero es más instructivo que el fallo: cerré una puerta (`write: if isMember()`) y dejé abierta
la de al lado, porque la condición miraba **si tu uid aparece**, no **qué estás diciendo**.

## Arreglo

Dos verbos separados, cada uno con lo que puede tocar:

- `writesAsAuthor()` — aparcar: firmas tú la cabecera.
- `takesTheCar()` — llevártelo: solo `isParked`, `movedByUid`, `movedByName` y `updatedAtMs`, y solo
  poniendo `isParked = false`. No se puede reescribir la posición ni el autor por esta vía.

## Testigo

`state.test.ts` · *A3 · un miembro no puede firmar una cabecera como otro poniéndose en movedByUid*.
