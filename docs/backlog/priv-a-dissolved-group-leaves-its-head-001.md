# PRIV-A-DISSOLVED-GROUP-LEAVES-ITS-HEAD-001 · Disolver el grupo dejaba la última posición del coche huérfana

**Estado:** ✅ Done (2026-09-16) — el hash vive en `MEMORY.md`. **Regresión introducida por `b3ae303b`.**
**Origen:** 4.ª vuelta — A1. **Reproducida en el emulador antes de arreglarla.**

## Problema

`match /state/{docId}` quedó con `allow delete: if isOwner()`, y `isOwner()` compara
`resource.data.ownerUid` — pero dentro de ese match **`resource` es el documento de ESTADO**, que no
tiene ese campo. La condición era falsa siempre: **nadie podía borrar la cabecera**.

`deleteCar` la borra con `runCatching` mudo, así que disolver un grupo devolvía éxito y dejaba
`sharedCars/{carId}/state/current` en pie — con la última posición conocida del coche, el nombre de
quien lo dejó y el de quien se lo llevó. Un resto de datos personales que sobrevive al objeto que lo
justificaba, y que además ya no gobierna ninguna regla: su documento padre no existe.

⛔ Y es exactamente el fallo que `PRIV-A-DELETED-ACCOUNT-LEAVES-TWO-TOKENS-BEHIND-001` vino a
cerrar, reabierto tres commits después por el intento de endurecer esa misma subcolección.

## Arreglo, en dos mitades

1. **La regla** mira al dueño donde está: `get(sharedCars/{carId}).data.ownerUid`.
2. **`deleteCar`** borra `state` **antes** que `members` —su regla se apoya en la pertenencia— y
   **registra el fallo**: un `runCatching` mudo es como estuvieron huérfanas hasta que una revisión
   las encontró.

## Testigo

`state.test.ts` · *A1 · el dueño puede borrar state/current al disolver* — en rojo contra las reglas
que estaban desplegadas.

## ⏳ Pendiente

Las cabeceras ya huérfanas (grupos disueltos entre las 23:00 y el arreglo) necesitan un barrido
manual: `sharedCars/*/state/current` cuyo `sharedCars/{carId}` no exista.
