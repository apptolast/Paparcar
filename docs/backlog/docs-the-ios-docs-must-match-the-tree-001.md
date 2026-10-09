# DOCS-THE-IOS-DOCS-MUST-MATCH-THE-TREE-001 · el plan de iOS pedía trabajo ya entregado

**Estado:** ✅ Done · `docs/IOS_PLAN.md` y `docs/ROADMAP.md` re-verificados contra master `eabefa14c`

## Problema

Los dos docs vivos de más arriba del árbol llevaban semanas desmentidos, y lo dijo el barrido del
README de la raíz el 30-09 ([DOCS-THE-README-MUST-MATCH-THE-TREE-001](docs-the-readme-must-match-the-tree-001.md),
`0c75c3878`) sin que nadie los tocara. Medido hoy:

| Lo que decía | Lo que dice el árbol |
|---|---|
| `IOS_PLAN`: «el proyecto no tiene **scheme compartido**» (×2, y como el bloqueo del CI) | ✅ `iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` |
| `IOS_PLAN`: «**Firebase App Distribution iOS sigue a 0%**: sin certificados, sin lane de Fastlane» | ✅ **cinco lanes** en `iosApp/fastlane/Fastfile` (`bootstrap_match`, `match_certificates`, `build`, `beta`, `screenshots`), firma por `match`, y la app **estuvo en TestFlight** (0.1.1, build 2) |
| `IOS_PLAN`: «`GoogleService-Info.plist` ❌ Ausente» | ⚪ **gitignored a propósito**; `GoogleServiceInfo.clientId()` lo lee del bundle |
| `IOS_PLAN`: «hace falta `BGTaskScheduler`» y «~4 h» | ✅ lo usan 4 ficheros de `iosMain` y el `Info.plist` declara sus dos identifiers |
| `IOS_PLAN`: «el lazo de GPS → `IOS-F0-001` prepara los puertos, **en rama sin mergear**» | ✅ `IosDetectionController`, uno de los **17** ficheros de `iosMain/.../detection/` |
| `ROADMAP`: «Detección iOS 🧊 **Aparcada por el user (07-09)**… sin mergear» | ✅ en master (`8c2daf096`); el user **desaparcó iOS el 22-09** |
| `ROADMAP`: «Room KMP en **v10**» | ✅ **v20** — diez migraciones de diferencia |
| `ROADMAP`: «**F8** coche compartido: etapa 1 en rama, etapas 2-3 sin empezar» | ✅ en master (`3d1d80a67`), con `SharedCar.kt` en `commonMain` |
| `ROADMAP` § **En vuelo**: tabla con **4 ramas** | ⛔ **no existe ninguna**; dos estaban en master (`1cff6f266`, `56d63e1f8`) |

## Doctrina violada

`project_docs_living_docs_must_match_master_001`: un doc vivo que afirma el estado del árbol tiene
que poder comprobarse contra el árbol. Y la de esta misma semana: **una afirmación sobre git tiene
que ser verificable contra git** [MEM-A-DESCRIPTION-MUST-BE-VERIFIABLE-AGAINST-GIT-001].

⛔ El coste no es cosmético: un plan que pide `fastlane init` cuando hay cinco lanes, o un scheme
compartido que ya existe, **hace trabajar en balde**. Y la tabla de «en vuelo» invitaba a mergear
ramas que no existen.

## Señales / datos disponibles

Todo comprobable en un comando, que es lo que hace sangrante que llevara tres semanas:
`ls iosApp/fastlane/` · `ls iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/` ·
`grep BGTaskScheduler shared/src/iosMain` · `grep PAPARCAR_DB_VERSION` ·
`git for-each-ref refs/heads refs/remotes`.

## Diseño

Corregir **en línea**, dejando escrito qué decía antes: un doc de plan que cambia sin decir por qué
se vuelve a "arreglar" al revés la próxima vez. Las dos cabeceras pasan a citar el hash y la fecha
contra los que se verificaron.

⛔ **La tabla de «ramas en vuelo» no se reescribe: se retira.** Su propio aviso decía *«esta tabla
caduca en horas»* — y aun así se quedó tres semanas con cuatro ramas muertas. En su lugar van los
comandos que lo miden en el momento (`git worktree list`, `git for-each-ref`,
`tools/backlog/branch_tell.py`), porque un inventario a mano de algo que cambia cada hora es una
mentira con fecha de caducidad.

## Criterio de éxito

Ninguna rama citada por `docs/ROADMAP.md` que no exista, salvo las que el texto declara
explícitamente como borradas. Comprobado: las dos menciones restantes de
`feature/IOS-F0-001-fase0` son las notas que dicen que **ya no existe** y que su punta solo vive en
el tag local `prerebase/`.

## Consumidores auditados

- `docs/backlog/docs-the-readme-must-match-the-tree-001.md` — ✔ **era quien lo declaró abierto**;
  este ticket cierra sus dos 🔵.
- `README.md` de la raíz — ✔ ya se corrigió en `0c75c3878`, sin cambios hoy.
- `CLAUDE.md` — ✔ exento: no afirma el estado de iOS, solo la doctrina (y ya dice que en iOS el
  carril BT es estructuralmente imposible).
- `docs/backlog/ios-*.md` — ⛔ **fuera de alcance**: son 7 docs y el barrido del 07-10 ya los
  clasificó; lo que se corrige aquí son los dos docs de PLAN, que son los que se leen para decidir
  qué hacer.

## Lo que sigue sin medir

Que la detección iOS **funcione** en device. Está escrita y nunca se ha ejecutado, y desde el 08-10
este Windows ni la compila: **App Control bloquea la DLL de stubs de Kotlin/Native**, así que
`:shared:compileKotlinIosSimulatorArm64` muere antes de llegar al compilador. Mientras siga así,
decir «iOS compila» es tan falso como lo que este ticket acaba de corregir.
