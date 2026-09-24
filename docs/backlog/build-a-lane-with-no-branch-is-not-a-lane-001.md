# BUILD-A-LANE-WITH-NO-BRANCH-IS-NOT-A-LANE-001 · retirar el carril de Firebase App Distribution, que no ha disparado nunca

**Estado:** ✅ Done — en master (24-09-2026). El carril entero fuera del árbol: 2 workflows borrados, plugin y config retirados, 5 docs al día.

## Problema

El repo mantiene un carril de distribución a testers por **Firebase App Distribution** —dos
workflows, un plugin de Gradle, un bloque de config, una versión en el catálogo y una entrada en
`.gitignore`— que **no se ha ejecutado ni una vez**.

El dato que lo demuestra, medido el 24-09-2026 sobre `master ad101968a`:

```bash
$ git branch -a --list "*alpha*" "*beta*"
(vacío)
$ git for-each-ref refs/heads refs/remotes | grep -E '/(alpha|beta)$'
(vacío)
```

Los dos workflows disparan **solo** con `push` a `alpha` y a `beta`
(`distribute-alpha.yml:4-7`, `distribute-beta.yml:3-5`). Esas ramas no existen, ni locales ni en
`origin`, así que ninguno de los dos ha corrido jamás. `distribute-alpha.yml` tiene además
`workflow_dispatch`, pero no hay rastro de ejecución manual y el propio `RELEASE-PROCESS.md` describía
la subida como un `./gradlew :app:appDistributionUploadRelease` a mano.

Mientras tanto, la app **está publicada en Google Play** (1.3.0, versionCode 14). El canal real es la
tienda; lo que llega a nuestros propios móviles llega por `/run` (skill `instalar-apk`).

El coste no es el disco: es que **el repo declara un proceso que nadie sigue**. `RELEASE-PROCESS.md`
abría diciendo que era la fuente de verdad para "subir betas a Firebase App Distribution", con §0.3 y
§0.4 dedicadas a montar una auth y un grupo de testers que no se usan, y un §1.5 que era el paso que
de verdad publica — solo que al sitio equivocado. Cualquiera que abriese ese doc para sacar una
versión seguía un camino muerto.

## Doctrina violada

*Sistemas, no parches* por el lado que menos se mira: **un sistema que no se ejecuta no es un
sistema, es documentación falsa con sintaxis de YAML.** Es la misma familia que
[CI-A-GUARD-THAT-NEVER-STARTS-IS-NOT-A-GUARD-001] (un guard que no arranca no protege nada) y que
[DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001] (un doc que no coincide con el código es un bug del doc).
Aquí el artefacto está *vivo en el árbol* y *muerto en ejecución*, que es el estado más caro: se lee
como si funcionara.

Añade un riesgo propio: el plugin sigue aplicado en `app/build.gradle.kts`, así que **configura
tareas en cada build de release** y hay un `serviceCredentialsFile` apuntando a un JSON de service
account. Un camino de subida que nadie vigila, con credenciales asociadas, es superficie que no
compensa mantener.

## Señales / datos disponibles

- `git branch -a` / `git for-each-ref` — las ramas `alpha` y `beta` no existen (arriba).
- `git tag -l` — solo `v1.0.0` y dos `prerebase/*`: tampoco hay tags de 1.1.0 / 1.2.0 / 1.3.0, así
  que el §1.6 del doc viejo tampoco se seguía. **Fuera de alcance de este ticket**, pero anotado.
- `app/build/outputs/bundle/prodRelease/app-prod-release.aab` existe en el árbol: lo que se corta de
  verdad es un **AAB para Play**, no el APK que subían los workflows.
- Los tres docs (`README.md`, `docs/release/RELEASE-PROCESS.md`, `distribution/release-notes.txt`) ya
  se reescribieron alrededor de Play **en el árbol principal, sin commitear** (24-09). Este ticket es
  la mitad de código de ese mismo cambio.

## Diseño

Retirar el carril entero, en un solo sitio por consumidor. No se sustituye por nada: el canal es
Play y ya está descrito.

1. **Workflows** — borrar `.github/workflows/distribute-alpha.yml` y `distribute-beta.yml`.
2. **`ci.yml`** — quitar `alpha` y `beta` de los triggers `push` y `pull_request` (líneas 7-8 y
   15-16). Son ramas que no existen; dejarlas ahí es la misma mentira en otro fichero.
3. **`app/build.gradle.kts`** — quitar el `import`, el `alias(libs.plugins.firebaseAppDistribution)`,
   el `val appDistributionCredentialsFile` y el bloque `firebaseAppDistribution { }` del buildType
   release.
4. **`gradle/libs.versions.toml`** — quitar `firebase-appdistribution-plugin = "5.3.0"` y el alias
   `firebaseAppDistribution`.
5. **`.gitignore:40`** — el comentario nombra "App Distribution CI uploads". La **regla se queda**
   (un service-account JSON no entra al repo venga de donde venga); se reescribe el comentario.
6. **`docs/release/RELEASE-SECURITY.md:143`** — *"antes de subir el primer APK a App Distribution
   público"* pasa a hablar de la primera subida a Play.

⛔ **Lo que NO se toca:**

- **`docs/IOS_PLAN.md` §4** (plan de App Distribution para iOS). Ahí el carril no está muerto: está
  *sin nacer*, y para iOS la pregunta real es App Distribution vs. TestFlight. Meterlo en este
  barrido sería decidir la beta de iOS de refilón. Queda como decisión aparte → ver *Follow-ups*.
- **`distribution/release-notes.txt`**. Nació para el plugin, pero ya es el changelog en texto plano
  del repo y `RELEASE-PROCESS.md §1.2` lo cita como tal.
- **Los `docs/backlog/*.md` históricos** que mencionan App Distribution: son registro de por qué
  existió, y los cerrados no se reescriben.
- **`docs/HYPOTHESIS.md:129`**. Entró en el plan como ✏ y se corrigió al abrir el fichero: se
  autodeclara *"retrospectiva fechada del 2026-05-24. No es un doc vivo ni un plan… se conserva tal
  cual en vez de reescribirse"*. Su fila `Distribution` dice lo que se pensaba en mayo, y eso es
  exactamente lo que tiene que seguir diciendo. Un registro fechado no se corrige: lleva su fecha.

## Criterio de éxito

- `grep -rn -i "appdistribution\|app distribution\|beta-paparcar\|alpha-paparcar"` sobre el árbol,
  excluyendo `docs/backlog/` y `docs/IOS_PLAN.md`, devuelve **solo** la nota ⛔ de
  `RELEASE-PROCESS.md` que explica por qué se retiró.
- `./gradlew :app:assembleProdDebug` y `./gradlew testAll` verdes desde el worktree — el plugin
  retirado no deja una tarea colgando ni rompe la resolución del catálogo.
- `./gradlew :app:bundleProdRelease --dry-run` resuelve: quitar el plugin no puede llevarse por
  delante el corte de release de verdad. (Sin `--dry-run` haría falta el keystore, que este worktree
  no tiene a propósito.)
- `.github/workflows/ci.yml` sigue siendo YAML válido y sus triggers solo nombran ramas que existen.

## Consumidores auditados

Barrido del 24-09-2026 (`--include=*.kts,*.toml,*.yml,*.md,.gitignore`, sin `node_modules` ni
`build/`):

| Consumidor | Qué asume | Destino |
|---|---|---|
| `.github/workflows/distribute-alpha.yml` | rama `alpha` + grupo `alpha-paparcar` | 🗑 borrar |
| `.github/workflows/distribute-beta.yml` | rama `beta` + grupo `beta-paparcar` | 🗑 borrar |
| `.github/workflows/ci.yml:7-8,15-16` | ramas `alpha` / `beta` existen | ✂ quitar de triggers |
| `app/build.gradle.kts:2,22,51,198-205` | plugin + service account + grupo | ✂ quitar |
| `gradle/libs.versions.toml:65,255` | versión + alias del plugin | ✂ quitar |
| `.gitignore:40` | el JSON es "de App Distribution" | ✏ reescribir comentario, regla intacta |
| `docs/release/RELEASE-SECURITY.md:135-143` | "beta cerrada", "10 testers", subir a App Distribution | ✏ reescrito: el canal es Play |
| `docs/HYPOTHESIS.md:129` | fila `Distribution` | ⏸ exento — retrospectiva fechada, no se reescribe |
| `docs/release/RELEASE-PROCESS.md:11` | — | ✅ ya reescrito: es la nota que explica la retirada |
| `README.md` | — | ✅ ya reescrito alrededor de Play |
| `docs/IOS_PLAN.md:28,144,196-216,275` | beta de iOS por App Distribution | ⏸ exento — decisión aparte |
| `docs/backlog/*.md` (7 ficheros) | historia de por qué existió | ⏸ exento — registro cerrado |
| `docs/ARCHITECTURE.md:357` | el CI son TRES workflows | ✏ reescrito: queda uno, y por qué |
| `docs/IOS_PLAN.md:224` (§4 paso 6) | «los workflows reales hoy son…» | ✏ solo el puntero; el plan NO se rehace |
| `docs/audits/AUDIT-2026-07-04-full.md` | C2/C3 sobre esos workflows | ⏸ exento — auditoría fechada |

⚠️ **Las dos primeras filas no salieron en el barrido de apertura.** El grep inicial buscaba
`appdistribution|app distribution|beta-paparcar|alpha-paparcar`, y esos dos docs no nombran el
producto: nombran los **ficheros**. Hicieron falta dos greps, no uno — `grep
"distribute-alpha\|distribute-beta"` es el que los destapó. Anotado porque es el fallo de método,
no del ticket: al borrar un fichero hay que grepear su NOMBRE, no solo su tema.

## Verificación (24-09-2026, worktree sobre `ad101968a`)

| Criterio | Resultado |
|---|---|
| `grep` del tema, excluyendo exentos | ✅ solo la nota ⛔ de `RELEASE-PROCESS.md` que explica la retirada |
| `grep` de los NOMBRES de los workflows | ✅ solo las menciones que narran su borrado |
| `grep APP_DISTRIBUTION_CREDENTIALS_FILE` | ✅ cero |
| `./gradlew :app:assembleProdDebug :app:compileMockDebugKotlin testAll` | ✅ BUILD SUCCESSFUL |
| `./gradlew :app:bundleProdRelease --dry-run` | ✅ el grafo resuelve entero — quitar el plugin no toca el corte de release |
| `ci.yml` parseado como YAML | ✅ válido · `push: [master, feature/IOS-**]` · `pull_request: [master]` |

⚠️ La primera pasada de `testAll` salió verde **contra el fichero anterior** a un arreglo de
indentación en `app/build.gradle.kts` (el borrado del bloque dejó `}        }` en una línea). Se
repitió con el árbol final: verde otra vez, y la configuración se releyó (`Configuration cache entry
stored`). Lo verde corresponde a lo que se commitea.

## Follow-ups (no entran aquí)

- **Canal de beta para iOS**: App Distribution vs. TestFlight, con `IOS_PLAN.md §4` escrito sobre la
  primera. Hoy da igual —la distribución iOS está a 0%—, pero cuando se decida, ese §4 se reescribe
  entero.
- **Tags de release**: solo existe `v1.0.0`. O se retoma el paso de `RELEASE-PROCESS.md §1.6`, o se
  quita del doc. Hoy el doc promete algo que las tres últimas versiones no hicieron.
