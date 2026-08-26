# DOCS-BACKLOG-TRUTH-001 · el backlog decía "pendiente" de 27 tickets que llevaban meses en master

**Estado:** ✅ Done (2026-08-26) · squash a master · rama y worktree borrados.
27 docs saneados, cero ficheros de código tocados.

## Problema

Al preguntar "¿qué tickets antiguos quedan pendientes?", `docs/backlog/` contestó con 27 docs que
declaraban una de estas cuatro cosas:

- *"implementado en rama `X`, sin commit"* / *"staged, sin commit"*
- *"pendiente device + merge"* / *"pendiente build/tests verdes"*
- *"EN CURSO"* / *"en implementación"*
- *"código en el working tree de master, sin commit"*

**Las 27 eran falsas.** Cada una de esas ramas ya no existe porque su trabajo entró en master —
en algunos casos hace casi dos meses. `git worktree list` da 6 worktrees y ninguno es el que citan.
Ninguno de esos 27 docs se actualizó al cerrar su ticket.

El caso extremo es `DET-ROUTE-ORIGIN-002`, que decía *"código en working tree de `master` (sin
commit)"* — con el árbol de master limpio desde entonces. Un lector honesto de ese doc concluiría
que hay trabajo perdido.

## Doctrina violada

`feedback_keep_backlog_docs_in_sync` — *"el doc de backlog se actualiza mientras avanza el trabajo,
no al final; el user lo usa como fuente de verdad entre sesiones"*. Y la regla de cierre de la skill
`nuevo-ticket` §C.2: **el doc marcado ✅ viaja DENTRO del commit de squash**. Esa regla se adoptó el
21-08-2026; los 27 docs son anteriores a ella y quedaron huérfanos.

Consecuencia medible: la memoria heredó la mentira. `MEMORY.md` listaba
`AUTH-PROVIDERS-EXPLICIT-001` como *"STAGED sin commit"* cuando está commiteado en `4cacdfb5`, y
la ficha del field 25-08 decía *"cero código"* de una rama con 347 líneas escritas.

## Método (verificación, no confianza)

Para cada doc, tres comprobaciones independientes:

1. `git log master --grep=<TICKET-ID>` → el commit real, con su hash.
2. `grep -rl <TICKET-ID> composeApp/src` → el marcador vive en el código de master.
3. `git merge-base --is-ancestor <hash> 1a4128d5` → el commit es anterior a la frontera de campo
   validado del 23-08-2026, luego su `⏳ field-test` está cubierto.

Los 27 pasaron las tres. Ninguno se cerró "porque parecía".

⚠️ **Tres no eran lo que su título sugería** y se anotan en su doc en vez de taparlo:

- `DET-SENTRY-COOLDOWN-001` y `DET-UNVERIFIED-CONFIRM-001` decían venir de la rama
  `bugfix/DET-WALKOUT-FP-001-walkout-false-positive`. Esa rama **nunca llegó a master con ese
  nombre**: cada ticket entró con ID propio (`eecef415`, `b36c1bcc`) y `DET-WALKOUT-FP-001` no
  existe en el código ni en el histórico.
- `DET-NUDGE-PERSIST-001` entró en `fb8c0724`, pero su conducta la reemplazó después
  `865f0f8a` [DET-ASK-STATE-001]. Cerrarlo sin decirlo dejaría el doc apuntando a un diseño muerto.

## Alcance — qué NO se ha tocado

- **Los docs sin línea de `**Estado:**`** (análisis, planes, specs). No mienten: no afirman nada.
- **Los docs ya marcados ✅ que además nombran su rama de origen** (`det-physics-*`, `det-stage-*`,
  el stack de julio). Nombrar la rama que produjo un commit es procedencia, no una promesa abierta.
- **Los tickets genuinamente abiertos.** Siguen abiertos y sin código, y este ticket no los cierra:
  `DET-BIKE-DEPARTURE-RELEASE-001` · `DET-BLIND-AFTER-LOST-PARK-001` · `DET-BT-BOARDING-ANCHOR-001` ·
  `DET-COARSE-FIX-DRIVE-PROOF-001` · `DET-RESUME-RECONCILE-001` · `UI-APPROXIMATE-ZONE-IN-HISTORY-001` ·
  `BUG-HOME-FAB-PADDING` · `INFRA-DATASTORE-MIGRATION-001` · `MOVING-CAR-NATIVE-MARKER` ·
  `VEH-ADD-PILL-SINGLE-VEHICLE` · `VEHICLES-MULTIMARKER` · `DET-EXIT-LINE-COUNTS-NOTHING-001` ·
  `DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001`.
- **Los bloqueados por medición, no por código**: `DET-BROADCAST-QUEUE-STALL-001` y
  `DET-HEARTBEAT-LANE-REPAIR-001` esperan a que el Oppo vuelva a fallar, no a un viaje.
- **Los parcialmente cerrados**, que se dejan a medias EXPLÍCITAMENTE en vez de marcarse ✅ enteros:
  `DET-RELIABILITY-001` (F1–F3 en master, **F4 sigue diferida**), `SYNC-RECONCILE-001` (el reconcile
  cerrado en los tres agregados, **Profile diferido** y geocoder-offline/read-only abiertos aparte),
  `DET-BT-OWNERSHIP-001` (cerró la ATRIBUCIÓN; la NOMINACIÓN volvió a morder el 25-08).

## Criterio de éxito

`grep -E '^\*\*Estado.*(sin commit|staged|pendiente de merge|pendiente device|en implementación|EN CURSO)'`
sobre `docs/backlog/*.md` no devuelve ningún doc cuyo trabajo esté en master. La pregunta "¿qué queda
pendiente?" se responde leyendo el backlog, sin auditar `git log` primero.

## Consumidores auditados

- `MEMORY.md` y las memorias que citaban estos tickets como pendientes → **pendiente de actualizar
  al cerrar** (skill `nuevo-ticket` §C.3).
- Cero ficheros de código tocados. Este ticket no cambia una sola línea de `composeApp/`.
