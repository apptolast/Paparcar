# PRIV-A-REMOVED-MEMBER-LEAVES-THEIR-TRIPS-BEHIND-001 · Expulsar a alguien le deja los viajes dentro, y ni borrar su cuenta los recupera

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 2.ª vuelta — hallazgo C2. **Verificado.**

## Problema

`removeMember` (`SharedCarRepositoryImpl.kt`) hace **solo** `deleteMember`. Salir por tu propio pie
ya se lleva tus sesiones y tu nombre de la cabecera
[PRIV-LEAVING-A-GROUP-MUST-CLEAR-YOUR-NAME-FROM-ITS-HEAD-001]; que te echen, no.

Y es peor que quedarse a medias, porque **se cierra la puerta detrás**: el expulsado ve desaparecer
el espejo, su fila se desvincula, y desde ese momento su propio borrado de cuenta ya no encuentra
ese grupo — `deleteAllData` recorre los coches con `sharedCarId`, y el suyo ya no lo tiene. Sus
recorridos se quedan ahí **para siempre**, firmados por alguien que ya no está.

## Diseño

La misma salida, sin estrenar verbos: antes de `deleteMember`, `deleteSessionsOf` y
`clearMemberFromState`. Las reglas ya lo permiten al dueño — borrar una sesión es del autor **o**
del dueño, y `state` lo escribe cualquier miembro.

## Criterio de éxito

Tras expulsar, no queda ninguna sesión del expulsado ni su nombre en la cabecera. Test en
`SharedCarRepositoryImplTest`, con el testigo de que ANTES quedaban.

## Cola

- `ACCOUNT-DELETE-RUNBOOK.md` §2.1: el dueño que expulsa a mano hace lo mismo.
- Política §6 (EN y ES): «Si el dueño te saca de un grupo, tu parte se borra igual.»
