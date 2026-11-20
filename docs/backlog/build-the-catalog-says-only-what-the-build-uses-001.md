# BUILD-THE-CATALOG-SAYS-ONLY-WHAT-THE-BUILD-USES-001 · El catálogo declara lo que el build usa, y nada más

**Estado:** ✅ Done · 20-11-2026 · `VersionCatalogGuardrailTest` es su testigo · follow-up declarado:
[`build-the-last-warnings-all-come-from-one-plugin-pair-001`](build-the-last-warnings-all-come-from-one-plugin-pair-001.md)

## Problema

`gradle/libs.versions.toml` acumula tres cosas distintas que nadie mide, y el barrido de versiones
([DEPS-REVENUECAT-3-10-AND-A-FULL-SWEEP-001], 21-10) sólo miraba la tercera:

1. **Alias muertos** — entradas que ningún `build.gradle.kts` nombra. Medido con
   `grep -rho 'libs\.[A-Za-z0-9._-]*' --include=*.gradle.kts` contra las declaraciones del catálogo:
   - `androidx-lifecycle-viewmodel`, `androidx-lifecycle-viewmodel-compose`,
     `androidx-lifecycle-runtime-compose` — tres librerías con comentario explicativo de tres
     líneas («usamos las tres variantes») y **cero consumidores**. Lo que sí se usa es
     `lifecycle-service`.
   - plugins `androidKmpLibrary` y `kotlinCocoapods` — declarados `apply false` en la raíz y
     **nunca aplicados** por ningún módulo.
2. **Dependencias que nadie importa** — no es alias muerto, es peso real en el grafo:
   - `org.jetbrains.compose.material:material` (Material **2**) entra como `api` en `commonMain`.
     Imports de `androidx.compose.material.*` excluyendo `material.icons` y `material3` en todo el
     repo: **cero**. Lo que se usa son los 392 imports de `material.icons`, que vienen de
     `material-icons-extended`, un artefacto distinto.
   - `com.google.android.material:material` (MDC) entra como `api` en `androidMain`. Imports: cero.
     El tema del manifest es `Theme.AppCompat.Light.NoActionBar`, que es appcompat, no MDC.
3. **Claves de versión duplicadas** — siete claves con el mismo valor para la misma línea de
   release: `compose-plugin`, `componentsResources`, `foundation`, `runtime`, `ui`,
   `uiToolingPreview` y `material` (JB) eran 1.12.2 las siete, y CLAUDE.md tenía que escribir la
   regla «las 7 claves, todas a la vez» porque el catálogo no la podía expresar. Igual
   `androidx-lifecycle` y `lifecycleService`: 2.11.0 las dos, para el mismo grupo.

Y, un mes después del último barrido, **14 claves habían derivado** más el wrapper de Gradle.

## Doctrina violada

[BUILD-DEPS-AT-LATEST-STABLE-002] (sólo estables, y al día) y **sistemas, no parches**: una regla
escrita en CLAUDE.md porque el catálogo no la puede expresar es un invariante en el sitio
equivocado. Dos claves con el mismo valor para la misma línea de release son dos sitios donde
olvidarse de una.

También la razón de ser de `allWarningsAsErrors` ([BUILD-ZERO-WARNINGS-IS-ENFORCED-001]): el
contador está a cero en el **compilador** y nadie miraba el de **configuración de Gradle**.

## Señales / datos disponibles

- Barrido contra `repo1.maven.org/.../maven-metadata.xml` y
  `dl.google.com/dl/android/maven2/.../maven-metadata.xml` (nunca `search.maven.org`), filtrando
  pre-releases, de las 43 coordenadas del catálogo.
- `./gradlew help --warning-mode=all` para los avisos de configuración, y
  `-Pandroid.debug.obsoleteApi=true` para saber **quién** llama a la API obsoleta.

## Diseño

**Una clave por línea de release, un alias por consumidor.** Lo que el build no nombra, sale.

- Las seis claves de Compose Multiplatform se colapsan en **una**, `compose-multiplatform`. La regla
  «las 7 a la vez» desaparece porque ya no hay 7.
- `lifecycleService` se colapsa en `androidx-lifecycle`.
- Los alias muertos y las dos dependencias sin importar se **borran** (no se comentan).
- Los avisos de configuración se arreglan donde son nuestros; donde son de KGP+AGP se dejan con su
  causa escrita, que ya está medida en `gradle.properties`.

## Criterio de éxito

- `grep` de alias del catálogo vs. `libs.*` en los `.gradle.kts`: cero huérfanos en los dos sentidos.
- `./gradlew testAll` verde con `--rerun-tasks` (las DLLs de Windows hacen que un verde incremental
  no pruebe nada) + `:app:assembleProdDebug`/`assembleMockDebug` + el klib de iOS.
- `./gradlew help --warning-mode=all` sin ningún aviso que sea NUESTRO.
- El AAB de release sale y pasa R8 con RevenueCat 4.x (Play Billing 9.1.0 nuevo en el classpath).

## Trabajo

### Barrido de versiones (20-11-2026) — 14 claves + wrapper

| Clave | De | A | Nota |
|---|---|---|---|
| `agp` | 9.4.1 | 9.5.1 | |
| `ksp` | 2.3.12 | 2.3.14 | esquema propio, independiente de Kotlin |
| `kotlinx-serialization` | 1.12.0 | 1.13.0 | |
| `ktor` | 3.6.0 | 3.6.1 | |
| `robolectric` | 4.17 | 4.18.1 | |
| `androidx-datastore` | 1.2.1 | 1.3.0 | |
| `androidx-lifecycle` | 2.11.0 | 2.11.1 | absorbe `lifecycleService` |
| `work-runtime` | 2.12.0 | — | ya estaba al día |
| `navigation-compose` | 2.9.2 | 2.10.0 | su POM pide Compose 1.11.0; tenemos 1.12.2 |
| `firebase-bom` | 35.0.2 | 35.2.0 | sólo en `dl.google.com` |
| `firebase-gitlive` | 2.7.0 | 2.8.0 | aditivo (release notes leídas: nada retirado) |
| `google-services` | 4.5.0 | 4.6.0 | |
| `facebook-sdk` | 19.0.0 | 19.0.1 | |
| `purchases-kmp` | 3.14.0 | 4.2.0 | **MAJOR** — ver abajo |
| Gradle wrapper | 9.7.1 | 9.9.2 | |

Al día y confirmado: Kotlin 2.4.21, Compose MP 1.12.2, Material3 (JB) 1.9.0, Koin 4.2.2, Room
2.8.5, sqlite 2.7.1, coroutines 1.11.0, datetime 0.8.0, coil 3.6.3, napier 2.7.1, konsist 0.18.1,
turbine 1.2.1, aboutlibraries 15.3.0, baselogin 3.0.2, play-services-location 21.4.0, glance 1.2.0,
crashlytics-plugin 3.0.8, appcompat 1.8.0, core-ktx 1.19.1, activity-compose 1.13.0, splashscreen
1.2.0, los cuatro de test de AndroidX, junit 4.13.2. `kmp-maps` 0.9.1-puck6 es la última del fork
(puck4/5/6 publicadas; **la manda nuestro repo**, no un índice).

#### `purchases-kmp` 4.0.0 — el major, leído antes de tocarlo

Migration guide (`migrations/4.0.0-MIGRATION.md`) + CHANGELOG 3.14.0→4.2.0. Tres exigencias y una
de ellas nos tocaba:

- **compileSdk ≥ 35** para la app y todo módulo que dependa del SDK → el nuestro es **37**. ✅
- **`PurchaseNotAllowedError` en vez de `StoreProblemError`** cuando la Play Store está bloqueada en
  el device. Paparcar **no nombra `PurchasesErrorCode` en ninguna línea** (`grep`), así que no hay
  `when` que se quede corto. ✅
- **Arrow 2 y Compose BOM 2026.06.01** vienen de `purchases-kmp-either` y `purchases-kmp-ui`, y no
  usamos **ninguno** de los dos artefactos: sólo `purchases-kmp-core`. ✅

Lo que sí entra de verdad: **Play Billing Library 9.1.0** (purchases-android 11.3.0) en el
classpath del APK. Ningún otro plugin de compras vive en la app, así que no hay quien choque — pero
es la razón de que el criterio de éxito incluya un AAB de release de verdad, no sólo los tests.

## El testigo, falsificado

`VersionCatalogGuardrailTest` (4 tests, en `:shared` androidUnitTest). Las tres prohibiciones se
vieron **rojas** antes de darlas por buenas — un `zz-dead-alias` sin consumidor, una
`zz-orphan-key` sin referencias y un `zz-prerelease = "2.0.0-beta3"` las hicieron fallar a la vez y
nombrando al culpable. La cuarta, la del testigo (suelos de población), no se puede falsificar en
aislamiento: cualquier forma de dejar al parser sin material tumba antes el build de Gradle, y eso
está escrito en su KDoc en vez de disimulado.

⛔ `--rerun-tasks` **no es opcional** para falsificarlo: tocar el TOML no cambia el bytecode, así
que Gradle da el test por UP-TO-DATE y no llega a ejecutarlo. Vale para todos los guards de este
paquete.

Verificación final: `./gradlew testAll --rerun-tasks` → **4.087 tests, 0 fallos**, con el klib de
iOS compilando. `:app:assembleProdDebug` + `assembleMockDebug` salen, y el APK de prod sigue
llevando sus 18 carpetas de `composeResources` con valores. `:app:bundleProdRelease` firmado pasa
R8 y `lintVital`, y el AAB lleva `AD_ID` y `BILLING` sin Topics ni Custom Audience
([RELEASE-THE-AAB-MUST-CARRY-AD-ID-001]).

## Consumidores auditados

| Sitio que asumía lo contrario | Estado |
|---|---|
| `shared/build.gradle.kts` — `api(libs.material)`, `api(libs.androidx.material)` | borrados |
| `shared/build.gradle.kts` — `val … by tasks.registering(JavaExec::class)` (aviso de Gradle 10) | reescrito a `tasks.register<JavaExec>(…)` |
| `mockkit/build.gradle.kts` — `export(project(":shared"))` (notación `Project` deprecada) | reescrito |
| `build.gradle.kts` (raíz) — `apply false` de `androidKmpLibrary` y `kotlinCocoapods` | borrados |
| `CLAUDE.md` — «la familia Compose Multiplatform (7 claves, todas a la vez)» | reescrito: una clave |
| `gradle.properties` — nota de `android.newDsl=false` / `builtInKotlin=false` | sigue viva y medida: ver abajo |

### Lo que NO se arregla, y por qué

Dos avisos de configuración no son nuestros y salen del **mismo** sitio: KGP aplicando el layout de
source sets de Android sobre `com.android.library` (`libraryVariants` obsoleta + «deprecated
compatibility with AGP `com.android.library`»). La traza lo dice entera:
`KotlinAndroidSourceSets.applyKotlinAndroidSourceSetLayout` → `LibraryExtension.getLibraryVariants`.
Se van el día que `:shared` y `:mockkit` puedan usar `com.android.kotlin.multiplatform.library`, que
es justo lo que `gradle.properties` tiene medido como **imposible hoy**: bajo ese plugin Compose
Multiplatform registra `copyAndroidMainComposeResourcesToAndroidAssets` sin `outputDirectory` y
TODOS los `composeResources` desaparecen del APK (medido en device: compila, instala, pasa los
tests y muere en el primer frame con `MissingResourceException`).

Consecuencia asumida de borrar el alias `androidKmpLibrary`: cuando llegue el día, es una línea que
se vuelve a añadir. El plugin sigue nombrado donde importa — el comentario de `shared/build.gradle.kts`
y la nota de `gradle.properties` —, que es donde se lee la razón, no en un `apply false` que no
aplica nada.

Con AGP 9.5.1 ese mismo nudo produce **cinco** avisos (los dos flags de `gradle.properties` ya
salen como deprecados, más `applicationVariants`/`testVariants`/`unitTestVariants` en `:app` y el
`org.jetbrains.kotlin.android` deprecado), así que el hueco tiene doc propio y criterio de éxito
medible — incluida la cuenta de `composeResources` **dentro del APK**, que es la comprobación que al
ticket original le faltaba:
[`build-the-last-warnings-all-come-from-one-plugin-pair-001`](build-the-last-warnings-all-come-from-one-plugin-pair-001.md).

### Y dos avisos de iOS que siguen ahí, a propósito

`compileKotlinIosSimulatorArm64` deja dos (`BetaInteropApi` en `IosGeofenceManagerImpl.kt:135`, un
cast imposible en `IosFileAntilog.kt:119`). No son de este ticket y no se cuelan por descuido:
`allWarningsAsErrors` está **deliberadamente** limitado al target Android, porque `iosMain` se
verifica en un Mac. [BUILD-ZERO-WARNINGS-IS-ENFORCED-001]
