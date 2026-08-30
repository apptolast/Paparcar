# DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001 · los docs que describen "el estado actual" describían mayo

**Estado:** ✅ Done (2026-08-30) · squash a master · rama y worktree borrados.
50 ficheros de documentación, **cero ficheros de código tocados**.

## Problema

`docs/` tiene dos poblaciones que se comportan al revés y estaban mezcladas sin distinguir:

- **Registros con fecha** (backlog de tickets, auditorías, análisis de refactor). Son fotos: dicen lo
  que era verdad ese día y **no envejecen mal** mientras lleven su fecha.
- **Docs vivos** (README, ARCHITECTURE, ROADMAP, BUGS_AND_DEBT, IOS_PLAN, la checklist de lectura).
  Afirman en presente *"el proyecto es así hoy"*. Cuando se quedan atrás **mienten**, y encima con
  autoridad — son los que un lector abre primero.

Los vivos llevaban entre dos y tres meses sin tocar salvo por renames mecánicos:

| Doc | Última auditoría que declara | Deriva medida |
|---|---|---|
| `docs/ARCHITECTURE.md` | 2026-05-24 | CMP 1.10.2 (real 1.12.0) · Koin 4.1.1 (real 4.2.2) · kmp-maps 0.8.1 (real fork propio 0.9.1-puck4) · Kotlin 2.3.10 (real 2.4.10) · Room "v3 sin migraciones" (real **v1** baseline) · "dos `AppPreferences` rivales" (borrada una en mayo) · `domain/coordinator/` (hoy `domain/detection/`) · 6 workers (hoy 11) |
| `docs/ROADMAP.md` | 2026-06-05 | tablas P0/P1 de mayo con tickets cerrados hace tres meses; 4 entradas "pendiente commit" de commits que existen; nada de la superficie legal de Play, del split `:app`+`:shared`, del sistema de color ni del de tipografía |
| `docs/BUGS_AND_DEBT.md` | 2026-05-24 | 15 de 17 secciones ya resueltas; la tabla "resumen por severidad" cuenta bugs que no existen |
| `README.md` | 2026-07-16 | badge Kotlin 2.4.0 / CMP 1.11.1 · targetSdk 36 (real 37) · **"3 familias por rol: Outfit + Inter + Barlow"** cuando la app envía **una sola** (Plus Jakarta Sans) desde `d0fdc4ae`, y **18 roles** cuando hay 22 |
| `docs/IOS_PLAN.md` | 2026-05-24 | "CI iOS: 0%" cuando `02a29f62` compila `iosMain` en `macos-latest`; no menciona la rama `IOS-F0-001` sin mergear |
| `docs/CODE-READING-CHECKLIST.md` | — | manda leer `data/.../Migrations.kt` ("vas por v12") — **el fichero no existe**; `domain/coordinator/CoordinatorParkingDetector.kt` — el paquete no existe; "los 18 roles tipográficos" |
| `docs/detection/SIGNAL-ARCHITECTURE.md` | 2026-06-28 | afirma **"AR `IN_VEHICLE_ENTER` ya NO arma"**, que es exactamente lo contrario de lo que hace master desde `2a25219a` [DET-AR-FIRST-001] y de lo que dice `CLAUDE.md` |
| `docs/architecture/VEHICLE-CATEGORIZATION.md` | 2026-06-08 | "Room schema bumped to **v6**" + `MIGRATION_6_7` + `fallbackToDestructiveMigration` — nada de eso sobrevive a `DATA-ROOM-STARTS-AT-VERSION-ONE-001` |

Y tres ficheros de trabajo de una sola sesión seguían en la **raíz del repo**, donde se leen como si
fueran vigentes: `HANDOFF-refactor-deteccion.md` (junio), `refactor-detection-plan-180826.md` (el
prompt de arranque del refactor de agosto) y `docs/PARKING_DETECTION.md` (un duplicado de mayo de la
spec canónica, enlazado desde el README **junto** a la canónica).

## Doctrina violada

`feedback_keep_backlog_docs_in_sync` extendida más allá del backlog: un doc que afirma en presente es
una promesa, y el proyecto ya pagó por esto en `DOCS-BACKLOG-TRUTH-001` (27 tickets que decían
"pendiente" llevando meses en master). Aquel ticket saneó los **registros**; nadie había pasado por
los **vivos**, que son los que más daño hacen porque no llevan fecha visible en el cuerpo.

El caso peor no es una versión desfasada: es `SIGNAL-ARCHITECTURE.md` diciendo que el AR no arma. Un
lector que lo creyera concluiría que el carril AR-first es un bug y lo quitaría.

## Método (verificación, no confianza)

Ninguna afirmación se ha reescrito "porque sonaba antigua". Para cada una:

1. **Versiones** → `gradle/libs.versions.toml` (fuente de verdad declarada en `CLAUDE.md`).
2. **Rutas y nombres de fichero** → `ls` / `find` sobre el árbol, no memoria. Toda ruta citada en un
   doc vivo existe hoy.
3. **Cifras contables** (roles tipográficos, use cases, workers, tests, locales) → contadas con
   `grep -c` / `find | wc -l` en el momento de escribir, y se dice **contra qué commit** se contaron.
4. **Estado de un ticket** → `git log master --grep=<ID>`, distinguiendo un commit de código de un
   commit `docs(backlog):` que solo ABRE el ticket. Esa distinción es la que evita el falso positivo
   inverso: 25 docs del backlog dicen "Abierto" y **lo están**, aunque su ID aparezca en el log.

### ⚠️ El grep de `DOCS-BACKLOG-TRUTH-001` se dejaba fuera media población

Aquel ticket buscó estados con `grep -E '^\*\*Estado'`. Pero el backlog tiene **dos formas** de
escribir esa línea: `**Estado:**` al margen y `> **Estado**:` dentro de un blockquote. El patrón
anclado a `^` no ve la segunda, y ahí vivían **7 tickets más** que decían *"Implementado, sin
commitear"* / *"EN CURSO en rama"* / *"CODE-COMPLETE"* llevando semanas en master. El propio barrido
cayó en la trampa en su primera pasada.

Método que sí cubre el espacio, y que es el que hay que repetir en el futuro:

```bash
# 1) cualquier forma de la línea de estado, con o sin blockquote
grep -rniE '^\s*>?\s*\*\*(Estado|Status)' docs/backlog/*.md

# 2) el chivato que no depende de cómo se redacte el estado: la RAMA citada
#    para cada rama nombrada en las 6 primeras líneas → ¿existe todavía?
git show-ref --verify --quiet refs/heads/<rama> || echo "rama borrada"
```

La comprobación (2) es la buena: **una rama borrada con un doc que la cita como viva es una mentira,
se redacte como se redacte**. Cruzadas así las 88 ramas citadas en cabeceras del backlog, la única
viva es la de este ticket.

## Qué se BORRA, y por qué tan poco

Decidido con el user tras medir. La tentación era limpiar el backlog: **285 ficheros, 30.386 líneas**.
No se toca **ni uno** de los 195 cerrados. No son grasa: cada uno guarda *por qué existe un guard
concreto*, que es exactamente lo que consultan `PARKING-DETECTION.md` y la skill `det-change` antes de
tocar detección. Borrarlos sería cambiar la memoria del proyecto por espacio en disco que no molesta a
nadie — no se cargan en contexto, no se leen salvo a propósito.

Lo que sí se borra son **7 ficheros de `docs/archive/` (3.409 líneas)** cuyo reemplazo está completo y
cuyo único efecto hoy es que alguien los lea como vigentes — el fallo exacto que originó este ticket:

| Borrado | Reemplazado por |
|---|---|
| `Paparcar_Arquitectura.md` (1.343) | `ARCHITECTURE.md` |
| `Paparcar_Roadmap_Completo.md` (700) · `Paparcar_Roadmap_TechDebt.md` (526) | `ROADMAP.md` |
| `Paparcar_UX_Audit_Brief.md` (343) | nada — **el propio doc dice** *"NO es documentación permanente del proyecto"* |
| `diag-session-2026-05-11.md` (375) | sus 5 hallazgos se convirtieron en tickets propios |
| `Gemini_Potential_Fixes.md` (46) | `BUGS_AND_DEBT.md` |
| `ios-contracts.md` (76) | `IOS_PLAN.md` |

Y un octavo, del backlog, por decisión del user: **`map-types-001.md`** (91 líneas). Era el único
ticket "abierto" que describía una pantalla **muerta** — su propuesta entera era rediseñar un popup de
3 opciones que `UI-MAP-TYPE-TOGGLE-001` ya había retirado (20-08) en favor de un toggle Terreno ⇄
Híbrido. Reescribir esa spec cuesta más que redactarla de cero sobre el toggle.

Lo suyo que **seguía siendo cierto no se pierde**: el default sigue en `MapType.TERRAIN` (verificado
en `HomeState.kt` y `PaparcarMapView.kt`) y el JSON de marca solo rinde sobre `NORMAL`. Ese hallazgo
se movió a `home-flow-analysis.md` §H2, que es donde se detectó y que ya lo listaba como defecto
propio — no a un doc nuevo. Sus 3 referencias entrantes se reescribieron: el defecto queda **sin
dueño y dicho**, en vez de apuntando a un fichero borrado.

Git conserva todo lo borrado. Sobrevive `ARCH-002-modularization-review.md`, que `ARCHITECTURE.md` sigue citando
como el análisis de referencia de modularización, y los tres archivados hoy, que ya llevan cabecera
diciendo qué son. Las cuatro referencias entrantes se reescribieron antes de borrar.

## Lo que de verdad estorbaba no era el volumen

285 ficheros **sin índice** no contestan *"¿qué hay en el backlog?"*. Eso no se arregla borrando: se
arregla con [`docs/backlog/README.md`](README.md), que lista **solo lo abierto** agrupado por área
(detección con evidencia · bloqueados por medición · arquitectura · UI/copy/mock · specs · iOS), más
dos secciones que evitan reaperturas por error: los ✅ que arrastran un ⏳ residual, y los **once**
tickets **cuyo doc viaja dentro de una rama sin mergear** y que por tanto el backlog de master no ve.

## Alcance — qué NO se toca

- **`docs/backlog/*.md`** salvo uno. Son registros fechados; que hablen de un diseño superado es
  historia, no mentira. La única excepción es la que sí mentía en presente (abajo).
- **`docs/audits/`, `docs/archive/`, `docs/detection/01-11-*.md`** — auditorías y análisis con fecha y
  commit-base anclados en su cabecera. Cumplen ya la regla.
- **`docs/detection/PARKING-DETECTION.md`** — el log cronológico canónico, al día por construcción
  (cada ticket de detección lo actualiza en su propio commit, por la skill `det-change`).
- **`docs/release/play-listing/`** — sin commitear en el árbol del user; es trabajo suyo en curso.

## Consumidores auditados

| Consumidor | Resultado |
|---|---|
| `docs/backlog/det-safety-net-fgs-is-typed-data-sync-001.md` | ❌ decía *"🔵 En progreso · rama `bugfix/…-location-type` · worktree `../Paparcar-fgs-type`"*. La rama no existe y el trabajo está en master (`2e777e3b`) → **cerrado ✅ Done** |
| `docs/refactors/PIPE-001-confirm-parking-pipeline.md` | ❌ decía *"Status: pending — open the branch when ready"* de un refactor cerrado en `55db3434`, con `PIPE-002`/`PIPE-003` ya construidos encima. Verificado además en el árbol: `CoordinatorDetectionService` no menciona `ConfirmParkingUseCase` → **✅ shipped** |
| `docs/detection/DETECTION-READINESS.md` | ⚠️ el **modelo** era correcto, la **superficie** no: citaba `ui/components/DetectionReadinessBanner.kt`, `PermissionsRationaleScreen.kt` y 6 claves `detection_*` que **no existen**. Hoy es `HomeDetectionSurface` + `DetectionUiState` (8 variantes) + copy `home_det_*` → §6, §7 y §8 anotadas |
| `docs/detection/DET-001-SUMMARY.md` · `FASE-G-DESIGN.md` | ❌ *"rama sin merge"* (está en `9b80f9ce`) y *"diseño sin implementar"* (`DET-G-04` `58b2c5f1`, `DET-G-05` `54cac751`) → anotados como registros fechados |
| **7 docs del backlog con el estado en blockquote** (invisibles al grep de `DOCS-BACKLOG-TRUTH-001`) | ❌ todos decían trabajo vivo sobre ramas borradas → **cerrados ✅** con su hash: `det-bt-car-cannot-nominate…` (`5d6a941f`) · `det-cadence-steps-are-invisible…` (`c692d61c`) · `det-supersede-cannot-discard…` (`8b996fef`) · `det-ready-2026-06-26` (`b41e8c6f`) · `ui-vehicle-icon-skeleton-001` (`61411be3`) · `veh-active-fence-001` (`49f02777`) · `ui-color-doctrine-001` (doctrina en master: guardarraíl + tokens) |
| `veh-active-fence-001-piece1-plan.md` | ❌ *"Wiring DIFERIDO"* de un wiring hecho: `VehicleFenceOwnershipPolicy` tiene **13 consumidores** en producción → ✅ ejecutado |
| `data-room-starts-at-version-one-001.md` | ⚠️ su ⏳ *"falta verificar el downgrade"* seguía leyéndose como riesgo abierto **estando medido** en `AppDatabaseDowngradeTest` → anotado |
| Los otros 25 docs del backlog con estado "Abierto" | ✅ verificados uno a uno contra `git log`: abiertos de verdad. Los que aparecen en el log lo hacen por su commit `docs(backlog):` de apertura |
| `README.md` → índice de documentación | ✅ reescrito: cada fila apunta a un fichero que existe, y desaparece la fila duplicada de detección |
| `CLAUDE.md` | ✅ corregido el mapa de source sets (los tests viven en `commonTest`, no en `androidUnitTest`) |
| Docs que enlazaban a `docs/PARKING_DETECTION.md` | ✅ barridos con `grep -rl`; ahora apuntan a la canónica |
| `MEMORY.md` | pendiente de actualizar al cerrar (skill `nuevo-ticket` §C.3) |

## Criterio de éxito

Tres preguntas que antes exigían auditar `git log` primero y ahora se contestan leyendo:

1. *¿Qué versiones usa el proyecto?* → README y ARCHITECTURE coinciden entre sí y con el catálogo.
2. *¿Cómo se arma la detección hoy?* → ningún doc contradice a `CLAUDE.md` ni a la spec canónica.
3. *¿Qué queda por hacer?* → ROADMAP lista lo abierto de verdad, con su doc de backlog al lado.

Y una regla nueva, escrita en el propio ROADMAP para que la próxima sesión la vea: **todo doc vivo
declara en su cabecera la fecha y el commit contra el que fue verificado.** Sin eso no se distingue
un doc al día de uno que nadie ha mirado.
