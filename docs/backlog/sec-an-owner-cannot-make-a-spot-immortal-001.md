# SEC-AN-OWNER-CANNOT-MAKE-A-SPOT-IMMORTAL-001 · La caducidad se validaba al crear y se podía estirar al editar

**Estado:** ✅ Done (2026-09-16) — reglas desplegadas; el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 3.ª vuelta — hallazgo M1.

## Problema

`SEC-A-SPOT-IS-NOT-A-MEGAPHONE-001` puso techo a `expiresAt` en el `create`. La rama del dueño del
`update` no lo comprobaba, y **`set()` sobre un documento que ya existe evalúa UPDATE**
(`FirebaseDataSourceImpl`), así que la puerta que cerraba el `create` se reabría por detrás: el
dueño podía estirar la caducidad sin límite.

Una plaza inmortal es un pin que nadie puede quitar del mapa de los demás — ni siquiera el barrido
pasivo, que solo borra lo que ya caducó.

## Arreglo

La rama del dueño exige lo mismo que el `create`: `expiresAt` entero y por debajo del techo de 3 h.
El conjunto de `status` sí es distinto — aquí entra `RETRACTED`, porque retractar una plaza es un
update legítimo y no se puede crear una ya retractada.

## Consumidores auditados

`retractSpot`, `refreshSpot` y la republicación con `SpotTtlPolicy` — las tres escriben un
`expiresAt` dentro del techo y un `status` de los tres permitidos.
