# BUILD-KMPMAPS-VENDORED-MAVEN-001 · El fork de kmp-maps viaja DENTRO del repo, no en la máquina de nadie

**Estado:** ✅ Done · master `00797fa9` (ff-only 2026-08-19; rama y worktree borrados) ·
♻️ **SUPERADO el mismo día por [BUILD-KMPMAPS-CENTRAL-DEPENDENCY-001](build-kmpmaps-central-dependency-001.md)**:
publicado el fork en Maven Central, el repo vendorizado que describe este doc dejó de tener razón de
ser y se borró. Se conserva por su valor de arqueología — explica por qué el repo de la app llegó a
alojar 11 MB de binarios y por qué JitPack no servía (reescribe el grupo y rompe el Gradle Module
Metadata de KMP).

## Ejecución (2026-08-19)
- Fork republicado desde `../kmp-maps` (rama `paparcar/puck`, árbol limpio) con
  `-PVERSION_NAME=0.9.1-puck4` y firma desactivada vía init script temporal
  (`tasks.withType(Sign) { enabled = false }`) — el fork llama a `signAllPublications()`
  incondicionalmente y sin llave la publicación release falla; el árbol del fork NO se tocó.
- 38 ficheros / 11 MB copiados a `third_party/maven/` (6 módulos: core, core-android, core-jvm,
  core-iosarm64, core-iossimulatorarm64, core-iosx64 — los klibs iOS incluidos).
- `.gitignore`: añadida excepción `!third_party/maven/**` (los patrones globales `*.jar`/`*.zip`
  se tragaban los binarios vendorizados).
- ✅ Verificado: `testProdDebugUnitTest` + `compileMockDebugKotlinAndroid` +
  `compileProdDebugKotlinAndroid` en verde con mavenLocal fuera de la resolución;
  `:composeApp:dependencies` resuelve `com.swmansion.kmpmaps:core:0.9.1-puck4` del repo vendorizado.

## Problema
Master depende de `com.swmansion.kmpmaps:core:0.9.1-puck4-SNAPSHOT`, que solo existe en el
**mavenLocal** de la máquina del user (publicado desde el fork `../kmp-maps`, rama `paparcar/puck`).
Consecuencia real: a un compañero no le compila Paparcar en su máquina (2026-08-18).

## Doctrina violada
El repo debe ser autocontenido: `git clone` + build debe funcionar en cualquier máquina. Una
dependencia que vive fuera del repo (mavenLocal) rompe ese invariante.

## Señales / datos disponibles
- Upstream verificado 2026-08-19: última release sigue siendo v0.9.1 (25-03), PR #170 e issue #171
  abiertos con 0 respuestas tras 5 semanas; `main` solo tiene 2 commits de docs desde marzo.
  No hay rescate upstream a corto plazo.
- JitPack descartado: librería KMP multi-artifact con Gradle Module Metadata — la reescritura de
  grupo de JitPack rompe la resolución, compila en Linux (sin artifacts iOS) y cambiaría las
  coordenadas que habría que deshacer cuando upstream publique.

## Diseño
Repo Maven **vendorizado dentro del repo de Paparcar** (`third_party/maven/`):
1. Republicar el fork con versión **fija** `0.9.1-puck4` (sin `-SNAPSHOT` — resolución
   determinista, sin timestamps) y firma desactivada.
2. Copiar el árbol `com/swmansion/kmpmaps/**/0.9.1-puck4` al directorio vendorizado y commitearlo.
3. `settings.gradle.kts`: sustituir `mavenLocal()` por `maven(third_party/maven)` con content
   filter al grupo `com.swmansion.kmpmaps`.
4. `libs.versions.toml`: `kmp-maps = "0.9.1-puck4"`.

Las coordenadas `com.swmansion.kmpmaps:core` NO cambian: el día que upstream mergee el PR #170 y
publique, solo se repunta la versión y se borra `third_party/maven/`.

## Criterio de éxito
Compila `prodDebug` + `mockDebug` y pasan los tests **sin que exista la versión en mavenLocal**
(se verifica retirando la entrada de mavenLocal de la resolución). Un compañero con solo
`git pull` puede compilar.

## Consumidores auditados
- `settings.gradle.kts` — único sitio que declara `mavenLocal()` → se sustituye.
- `gradle/libs.versions.toml` — única referencia a la versión → se repunta.
- `.gitignore` — verificar que no excluye `*.aar`/`*.jar` dentro de `third_party/maven/`.
- Fork `../kmp-maps` — queda como fuente del artifact; la rama `paparcar/puck` tiene backup en
  `rndevelo/kmp-maps`.
