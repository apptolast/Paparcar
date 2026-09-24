# CI-A-GUARD-THAT-NEVER-STARTS-IS-NOT-A-GUARD-001 · el CI cabe en su cuota y corre la suite entera

**Estado:** ✅ Done · mergeado a master por squash.

⛔ **Queda una mitad que no es de código y bloquea la verificación:** el desbloqueo de facturación
en `github.com/organizations/apptolast/settings/billing`. Hasta entonces nada de esto se puede ver
correr en GitHub — solo en local, que es exactamente lo que este ticket deja escrito en las skills.

## Problema

El CI no arranca desde el **13-09-2026 10:01** (último run verde). Los ~100 runs siguientes mueren
en 1–7 s con `steps=0`, sin logs y con `output.title`/`output.summary` a `null`.

La causa estuvo 11 días sin confirmar porque no está donde se mira. No hay logs que descargar
(`gh run view --log-failed` → *"log not found"*) y el job solo dice `conclusion: failure`. Está en
la **anotación del check-run**:

```bash
gh api repos/apptolast/Paparcar/check-runs/107286116138/annotations
```
```
"The job was not started because recent account payments have failed or your spending
 limit needs to be increased. Please check the 'Billing & plans' section in your settings"
```

Los cuatro hechos que lo explican, todos medidos el 24-09:

| Hecho | Medida |
|---|---|
| Plan del org | `orgs/apptolast` → `plan.name = "free"` → **2.000 min/mes** |
| ¿Actions desactivado? | `actions/permissions` → `enabled: true` — **no**, es que no hay minutos |
| Repo privado desde | **08-09-2026** → desde ese día los minutos se **miden** |
| El job caro | `apple` corre en **macOS, que factura ×10**, en **cada pull request** desde el 29-08 |
| Consumo real | 110 runs de `pull_request` desde el 13-09, todos pidiendo el job de macOS |

2.000 minutos con un job macOS a ×10 son **~13 pull requests**. Se agotaron en cinco días.

## Doctrina violada

1. **Un guard que no arranca no es un guard.** Ya se pagó: `ci.yml` lleva el job `iOS framework`
   en cada PR desde el 29-08, y aun así **#36 dejó master sin compilar para iOS** (`b329770df`).
   El guard existía, no se ejecutaba, y nadie lo notó porque el rojo era indistinguible del rojo
   de fondo.
2. **[TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001] — la suite es `testAll`, y el CI la escribe
   a medias.** `testAll` existe precisamente para ser *un comando que no se puede escribir a
   medias*. Cinco consumidores lo escriben a medias igual, el CI incluido.
3. **Sistemas, no parches.** Desbloquear el pago sin bajar el gasto es programar la próxima muerte
   a mitad de octubre.

## Señales / datos disponibles

- `gh api repos/{o}/{r}/check-runs/<job_id>/annotations` es el ÚNICO sitio donde GitHub dice por qué
  un job no arrancó. Ni `runs/<id>`, ni `runs/<id>/jobs`, ni los logs lo cuentan.
- `gh api orgs/<org> --jq .plan` da el plan sin necesitar `admin:org` (el endpoint de *billing* sí
  lo exige, y no lo tenemos).

## Diseño

### 1 · El gasto: sacar macOS de cada PR, no de la vida

El job `apple` **no se borra** — se mueve a donde iOS cambia de verdad: `workflow_dispatch` y push a
`feature/IOS-**`. Lo que se pierde es el **enlazado** del framework y `xcodebuild`, que solo puede
romperse tocando iOS a propósito.

Lo que NO se pierde, y es la mitad que cazaba los fallos reales: **el klib de iOS sigue compilando
en cada run, gratis, en Ubuntu**, porque `testAll` lo incluye
(`:shared:compileKotlinIosSimulatorArm64`). Eso es lo que atrapa un `actual` que falta o una firma
que derivó — el agujero de `IosAppPreferences` del 21-09
[IOS-THE-ANDROID-SUITE-DOES-NOT-COMPILE-IOS-001]. O sea: el carril de iOS **gana** cobertura por
run al mismo tiempo que deja de costar ×10.

### 2 · La suite: un comando que no se puede escribir a medias, tampoco en YAML

`testAll` = `:shared:testDebugUnitTest` + `:app:testMockDebugUnitTest` +
`:shared:compileKotlinIosSimulatorArm64`. Todo consumidor que "corre la suite" escribe `testAll`.
No se enumeran tareas a mano: el día que `testAll` crezca, crecen todos a la vez. Esa es la razón
de que la tarea exista.

### 3 · ⛔ `push: master` se QUEDA — la recomendación inicial era falsa aquí

Propuse quitar el disparo por push a master «porque lo que llega a master ya pasó por el PR».
**Medido, es al revés:**

```
git log --since=2026-09-13 master | wc -l          → 215 commits
git log --since=2026-09-13 --format=%s | grep -cE "#[0-9]+"  → 4 mencionan un PR
```

El flujo real de este repo (el que dicta la skill `nuevo-ticket` §C.2) es
`git merge --squash` **en local** + push directo a master. Las PRs son la excepción, no la vía.
Quitar `push: master` habría dejado master sin validar en 211 de 215 commits — que es la misma
enfermedad que estamos curando, con otro disfraz. La duplicación que yo creía ver no existe: los 90
runs de `push` y los 110 de `pull_request` son commits distintos, no el mismo commit dos veces, y
`concurrency: cancel-in-progress` ya colapsa las ráfagas.

## Criterio de éxito

- `./gradlew testAll --rerun-tasks` verde desde el worktree (lo que el CI va a correr, corriéndolo aquí).
- Ningún consumidor del repo enumera a mano las tareas de la suite (`grep` en §Consumidores).
- Un run completo cuesta **solo minutos de Ubuntu** (×1) salvo en ramas `feature/IOS-**`.
- ⛔ **Fuera de nuestro alcance y bloqueante:** el desbloqueo en
  `github.com/organizations/apptolast/settings/billing`. Hasta que el user lo haga, esto no se
  puede verificar en GitHub — solo en local. Y verificarlo en local es justamente el punto.

## Consumidores auditados

`grep` de todo el repo por invocaciones de Gradle que hagan de "suite":

| Sitio | Antes | Estado |
|---|---|---|
| `.github/workflows/ci.yml:68` | `:shared:testDebugUnitTest :app:testMockDebugUnitTest` | ✅ → `testAll` |
| `.github/workflows/distribute-beta.yml:42` | `:shared:testDebugUnitTest` **(¼ de suite, y es la puerta a testers EXTERNOS)** | ✅ → `testAll` |
| `.claude/skills/rebase-rama/SKILL.md:131` | `:shared:testDebugUnitTest` | ✅ → `testAll` |
| `.claude/skills/det-change/SKILL.md:61` | `:shared:testDebugUnitTest` | ✅ → `testAll` |
| `docs/ARCHITECTURE.md:348` | `:shared:testDebugUnitTest` | ✅ → `testAll` |
| `.claude/skills/det-change/SKILL.md:62` · `web/README.md:59` · `nuevo-ticket:115` | `--tests '<filtro>'` | ⚪ Exento: ejecución dirigida a un test, no la suite |
| `.github/workflows/distribute-alpha.yml` | sin gate de tests | ⚪ Exento: distribución interna de un debug; el gate vive en beta |
| `docs/detection/10-plan-refactor.md:159` | `:shared:testDebugUnitTest` | ⚪ Exento: doc histórico de un plan ya ejecutado |

## Riesgo declarado (lo único que no se puede verificar desde aquí)

`:shared:compileKotlinIosSimulatorArm64` **se ejecuta de verdad** en el host de desarrollo —
medido en este worktree: `BUILD SUCCESSFUL … 75 actionable tasks: 75 executed`, con la tarea
listada y emitiendo warnings de `iosMain`, no `SKIPPED`. Eso descarta que
`kotlin.native.ignoreDisabledTargets=true` la convierta en un no-op en un host sin Mac.

⚠️ Está medido en **Windows**, no en el **Ubuntu** del runner. Es la misma clase de host (klib
cross-compilation de Kotlin/Native, sin Mac) y debería comportarse igual, pero **no está probado**
y no puede estarlo mientras GitHub no arranque ningún job.

⛔ **Primera verificación cuando el CI vuelva a la vida:** abrir el log del paso *Run unit tests* y
comprobar que `:shared:compileKotlinIosSimulatorArm64` aparece **ejecutada**, no `SKIPPED`. Si sale
skipped, el carril de iOS se habría quedado sin guard por run justo cuando creíamos habérselo dado
— y eso es peor que no tenerlo, porque el verde mentiría.
