# FGS-DATA-SYNC-IS-NO-LONGER-INJECTED-001 · el permiso que AGP reinyectaba se fue con 9.4.0, y tres docs siguen contándolo

**Estado:** ✅ Done · en master (squash). La rama
`chore/FGS-DATA-SYNC-IS-NO-LONGER-INJECTED-001-agp940` y el worktree `../Paparcar-fgs-datasync` se
borran al cerrar. Solo documentación: no hay nada que ver en device.

## Problema

El user preguntó si `FOREGROUND_SERVICE_DATA_SYNC` seguía en la app, recordando que lo habíamos
quitado. La respuesta corta es que sí lo quitamos —
[DET-SAFETY-NET-FGS-IS-TYPED-DATA-SYNC-001], `2e777e3b`— y la larga es que aquel ticket dejó
escrito un hallazgo que **hoy ya no es cierto**: que AGP **9.3.2** reinyectaba el permiso después
del merge y que no estaba bajo nuestro control.

Con **AGP 9.4.0** (subido el 15-09 en `b72ba4e7`, [BUILD-DEPS-AT-LATEST-STABLE-002]) la inyección
**ha desaparecido**. Medido el 16-09, y no sobre artefactos incrementales:

| Superficie | Cómo se midió | FGS declarados |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | lectura | `FOREGROUND_SERVICE`, `_LOCATION` |
| Fusionado `prodRelease` y `prodDebug` | `processProd{Release,Debug}MainManifest` con **`--rerun-tasks`** | los mismos dos |
| `manifest-merger-prod-{debug,release}-report.txt` | `grep -c DATA_SYNC` | **0** en los dos |
| `app-prod-release.apk`, `app-prod-debug.apk` | `aapt2 dump permissions` | los mismos dos |
| `app-prod-release.aab` (candidato de 1.2.0) | manifiesto binario de `base/` | los mismos dos |

El fusionado de `prodRelease` queda en 16 permisos, y los dos `foregroundServiceType` que hay son
`location`: `CoordinatorDetectionService` y el `SystemForegroundService` de WorkManager (la Pieza 2
de aquel ticket — el `tools:node="merge"` — sigue en pie).

## Doctrina violada

[DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]. Un hallazgo medido caduca cuando cambia lo medido, y este
caducó en un bump de AGP. Tres sitios lo seguían afirmando en presente, y el peor de los tres no es
un doc de detección: es la chuleta con la que se rellena el formulario de Data Safety de Play, que
pedía justificar ante el Console un permiso que el AAB no lleva.

## Alcance

Solo texto. Cero código, cero strings, cero estados MVI, cero tests nuevos — el invariante real
(la promoción del safety net es de tipo `location`) ya lo cubre `2e777e3b` y no se toca aquí.

## Consumidores auditados

`grep -rn "DATA_SYNC\|dataSync"` sobre todo el repo → 7 ficheros con menciones vivas:

| Sitio | Qué decía | Acción |
|---|---|---|
| `app/src/main/AndroidManifest.xml:15-19` | ⚠️ "ya no se declara aquí, pero SIGUE APARECIENDO en el fusionado" | **corregido** — pasa a nota histórica con la medición de 9.4.0 |
| `docs/legal/DATA-SAFETY-FORM.md` (§ requisitos del Console, punto 2) | "el manifest fusionado lleva también `FOREGROUND_SERVICE_DATA_SYNC` inyectado por AGP (no eliminable): habrá que declararlo o justificarlo" | **corregido** — ya no hay nada que justificar ⚠️ editado en el árbol principal, ver *Nota de cierre* |
| `docs/detection/03-workers-entrypoints.md:73` | lo listaba entre los permisos FGS declarados | **corregido** — fuera de la lista |
| `docs/backlog/det-safety-net-fgs-is-typed-data-sync-001.md` | ⛔ "lo inyecta AGP por su cuenta" + "Pieza 3: el permiso NO se puede quitar" | **corregido** — el aprendizaje se conserva fechado, y la Pieza 3 se cierra |
| `docs/ANDROID-DETECTION-AUDIT.md:171,386` | lo marca **DESCARTAR** y manda quitarlo del manifest | **exento** — es la auditoría que ORIGINÓ el ticket, describe el estado de entonces y su veredicto se cumplió |
| `docs/detection/PARKING-DETECTION.md:8364-8366` y `docs/backlog/det-a-location-fgs-must-not-kill-the-app-001.md:91-100` | descartan `dataSync` como tipo neutro porque caduca por duración desde Android 15 | **exento** — sigue siendo verdad y sigue siendo la razón de no usarlo |
| `shared/.../ParkingSafetyNetWorker.kt:127-132` | narra en pasado por qué la promoción se tipa `location` | **exento** — el comentario es correcto: habla del bug que arregló |

## Trampa encontrada al escribir el comentario del manifiesto

⛔ **Dentro de un comentario XML no caben dos guiones seguidos.** Citar el flag `--rerun-tasks` en el
comentario del manifiesto revienta `processProdReleaseMainManifest` — no es un warning, es un fallo
de parseo del propio fichero. Está anotado en el sitio, junto al texto que lo provocó.

## Criterio de éxito

Ningún doc afirma en presente que el permiso esté en el APK, y la chuleta de Data Safety no manda
justificar ante Play algo que el AAB no pide. Si AGP volviera a inyectarlo, la nota histórica del
manifiesto dice exactamente qué medir y con qué comando.

## Nota de cierre

`docs/legal/DATA-SAFETY-FORM.md` estaba **modificado sin commitear en el árbol principal** (trabajo
vivo del user, `LEGAL-DATA-SAFETY-ANSWERS-FOR-1-2-0-001`). Traerlo a esta rama habría hecho que el
`merge --squash` se negara a pisar sus cambios locales, así que ese párrafo se corrigió **en sitio,
sin stagear**: viaja con el ticket legal del user, no con este commit.
