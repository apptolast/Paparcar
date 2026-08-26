# BUILD-DEPS-AT-LATEST-STABLE-001 · Las dependencias al día antes de la prueba interna

**Estado:** ✅ Done · mergeado en master

## Problema

Estamos a las puertas de la prueba interna de Play Store. El catálogo (`gradle/libs.versions.toml`)
arrastra 12 artefactos con versión estable más nueva publicada, incluidos el toolchain entero
(Gradle, AGP, Kotlin, KSP) y Compose Multiplatform. Publicar una beta sobre un toolchain viejo
significa que cualquier bug de build que reportemos ya está arreglado upstream, y que el primer
bump post-beta se come de golpe todo el delta acumulado.

No es un bug de campo: es deuda de mantenimiento con fecha límite.

## Doctrina violada

Ninguna doctrina de detección. Sí una regla de proyecto: `gradle/libs.versions.toml` se declara
**fuente de verdad** del stack en `CLAUDE.md`, y ese bloque documenta "versiones reales a
2026-07-01" — dos meses desactualizado.

## Señales / datos disponibles

Versiones consultadas el 2026-08-26 contra los repositorios autoritativos
(`repo1.maven.org/.../maven-metadata.xml` y `dl.google.com/dl/android/maven2/.../group-index.xml`),
filtrando alpha/beta/rc. **El índice de `search.maven.org/solrsearch` está obsoleto** — devolvía
Kotlin 2.2.0 cuando ya vamos por 2.4.x. No usarlo para esto.

### Bumps aplicados

| artefacto | antes | después |
|---|---|---|
| Gradle wrapper | 9.5.1 | 9.7.1 |
| AGP | 9.2.1 | 9.3.2 |
| Kotlin (+ compose-compiler, serialization) | 2.4.0 | 2.4.10 |
| KSP | 2.3.9 | 2.3.11 |
| Compose Multiplatform (runtime/foundation/ui/material/components-resources) | 1.11.1 | 1.12.0 |
| Firebase BOM | 34.15.0 | 34.18.0 |
| Crashlytics plugin | 3.0.7 | 3.0.8 |
| GitLive Firebase (firestore/auth/common) | 2.4.0 | 2.6.0 |
| Coil 3 | 3.5.0 | 3.6.0 |
| Ktor (okhttp/darwin) | 3.5.1 | 3.5.2 |
| androidx.sqlite (bundled) | 2.6.2 | 2.7.0 |
| androidx.appcompat | 1.7.1 | 1.8.0 |

### Ya en la última estable — verificado, no se tocan

material3 JB 1.9.0 (**1.11 y 1.12 son solo alpha; no hay estable**), navigation-compose 2.9.2,
coroutines 1.11.0, datetime 0.8.0, serialization-json 1.11.0, Koin 4.2.2, Room 2.8.4, Napier 2.7.1,
Turbine 1.2.1, Konsist 0.17.3, Robolectric 4.16.1, geofire-android 3.2.0, JUnit 4.13.2,
play-services-location 21.4.0, google-services 4.5.0, appdistribution 5.3.0, material-icons-extended
1.7.3 (congelada upstream), y **todas** las androidx: core-ktx 1.19.0, lifecycle 2.11.0,
activity-compose 1.13.0, work 2.11.2, datastore 1.2.1, splashscreen 1.2.0,
com.google.android.material 1.14.0, test-ext-junit 1.3.0, espresso 3.7.0, test-core 1.7.0.

### Fuera de alcance por regla del proyecto

- `kmp-maps 0.9.1-puck4` — fork propio en Maven Central; su versión la manda nuestro repo, no upstream.
- `baselogin 1.1.0` — librería propia en JitPack; **no se toca desde Paparcar**
  (`feedback_no_cross_repo_sessions`).

## Diseño

No hay sistema que diseñar: es un cambio de números en un único sitio (`libs.versions.toml`) más el
`distributionUrl` del wrapper. El trabajo real es el **barrido de lo que se rompe**, y ese barrido
lo hacen los 1.657 tests, no la lectura.

Regla que sí se aplica: **solo estables**. Ni un alpha/beta/rc entra en el catálogo antes de una
publicación, por muy tentador que sea saltar de material3 1.9.0 a 1.12.0-alpha03.

## Criterio de éxito — resultado

| gate | resultado |
|---|---|
| `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid` | ✅ verde |
| `testProdDebugUnitTest` | ✅ **1.657 tests, 0 fallos, 0 skipped — cero asserts editados** |
| `assembleProdRelease` (R8 minify + shrinkResources + lintVital) | ✅ verde · APK 12,4 MB |
| `assembleMockDebug` | ✅ verde · APK 43,3 MB |
| warnings de compilación | ✅ **51 sitios, exactamente los mismos que antes del bump** — ni uno nuevo |
| `CLAUDE.md` | ✅ actualizado |

## Consumidores auditados

**No rompió nada.** El barrido no encontró un solo call site que hubiera que tocar: ni la subida de
Kotlin 2.4.0→2.4.10, ni AGP 9.2.1→9.3.2, ni Compose 1.11.1→1.12.0, ni GitLive 2.4.0→2.6.0
introdujeron un cambio de API que nos afecte. Cero ficheros de código modificados en este ticket.

De paso se corrigieron dos afirmaciones **falsas** del bloque de stack de `CLAUDE.md`, que era la
"fuente de verdad" y llevaba meses mintiendo:

- decía que `kmp-maps` vivía en **mavenLocal**, cuando desde el 19-08 está publicado en Maven Central
  como `io.github.rndevelo.kmpmaps:core:0.9.1-puck4` y Paparcar ya consume de ahí (`6db9bc7a`);
- listaba Room/SQLite, Firebase, Coil y Ktor con las versiones anteriores al bump.

## Follow-ups deliberadamente fuera de alcance

- Gradle 9.7.1 avisa: *"Deprecated Gradle features were used in this build, making it incompatible
  with Gradle 10"*. No se ha investigado con `--warning-mode all` porque no es un bump de versión
  sino limpieza de scripts → va a `BUILD-ZERO-WARNINGS-IS-ENFORCED-001`.
- `targetSdk` sigue en 36 aunque `compileSdk` ya está en 37. Subirlo cambia **conducta en runtime**,
  no compilación, así que no se esconde dentro de un commit de dependencias → ticket propio
  `BUILD-TARGETSDK-AT-ANDROID-17-001`.
- El aviso de KMP + `com.android.application` (AGP 9 exige partir el repo en `:shared` + `:app`) se
  aparca hasta después de la prueba interna, por decisión del user el 26-08.
