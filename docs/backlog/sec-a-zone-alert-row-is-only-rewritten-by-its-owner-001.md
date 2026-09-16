# SEC-A-ZONE-ALERT-ROW-IS-ONLY-REWRITTEN-BY-ITS-OWNER-001 · Se puede sobrescribir la fila de alertas de otro

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 2.ª vuelta — hallazgo C6. **Verificado.**

## Problema

`firestore.rules` junta `create` y `update` de `zoneAlertSubs` en una sola condición que mira **solo
`request.resource.data.uid`** — lo que el escritor propone, nunca lo que el documento YA es:

```
allow create, update: if request.auth != null && request.resource.data.uid == request.auth.uid;
```

El id de la fila es `{uid}_{zoneId}`, y un uid no es un secreto: viaja en cada plaza publicada como
`reportedBy`. Así que cualquiera puede **reescribir encima** la suscripción de otro poniendo su
propio uid. No la lee —para eso la regla de `read` sí compara `resource`— pero la **destruye**: la
víctima deja de recibir los avisos que pagó, desde un servidor sin forma de enterarse. Y deja una
fila con tus tokens en el sitio de otro.

Es el mismo error que ya estaba resuelto tres líneas más abajo, en `delete`, que sí mira
`resource.data.uid`.

## Diseño

Separar los dos verbos, porque preguntan cosas distintas: en `create` no existe `resource`; en
`update` hay que exigir **las dos caras** — que la fila ya fuera tuya y que siga siéndolo.

## Criterio de éxito

Un `update` sobre la fila de otro uid es rechazado, comprobado **releyendo el ruleset desplegado**,
no el fichero local.
