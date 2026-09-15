# BUILD-DEPS-AT-LATEST-STABLE-002 · El catálogo vuelve a la última estable, y el puente de RevenueCat caduca

**Estado:** ✅ Done · rama `chore/BUILD-DEPS-AT-LATEST-STABLE-002-deps-al-dia` · worktree
`../Paparcar-deps002`

## Problema

`gradle/libs.versions.toml` se declara **fuente de verdad** del stack en `CLAUDE.md`, y ese bloque
dice «re-verificadas el 2026-08-30». Han pasado dos semanas y once artefactos tienen estable más
nueva publicada, incluido el toolchain (Kotlin, AGP, KSP) y Room.

Además hay una deuda con **fecha de caducidad escrita en el propio catálogo**: la línea
`purchases-android = "10.20.0"` es un puente declarado a mano, con la instrucción literal *«Al subir
`purchases-kmp` a una versión que ya pine ≥10.20.0, BORRAR esta línea y su alias»*. Esa condición
**se cumple hoy**: `purchases-kmp-core 3.8.0` pina `purchases` en 10.20.0.

No es un bug de campo: es mantenimiento con una caducidad que ya venció.

## Doctrina violada

Ninguna de detección. Dos reglas de proyecto:

- `libs.versions.toml` es fuente de verdad del stack y llevaba dos semanas mintiendo por once líneas.
- **Solo estables**: ni un alpha/beta/rc entra en el catálogo. Por eso Compose Multiplatform se queda
  en 1.12.0 (1.13.0 es alpha01) y material3 JB en 1.9.0 (1.12/1.13 solo alpha).

## Señales / datos disponibles

Versiones consultadas el **2026-09-15** contra los repositorios autoritativos
(`repo1.maven.org/.../maven-metadata.xml` y `dl.google.com/dl/android/maven2/.../group-index.xml`),
filtrando alpha/beta/rc. ⛔ `search.maven.org/solrsearch` sigue obsoleto: no usarlo.

### Bumps aplicados

| artefacto                                  | antes   | después |
|--------------------------------------------|---------|---------|
| Kotlin (+ compose-compiler, serialization) | 2.4.10  | 2.4.20  |
| AGP                                        | 9.3.2   | 9.4.0   |
| KSP                                        | 2.3.11  | 2.3.12  |
| Room (runtime + compiler + plugin)         | 2.8.4   | 2.8.5   |
| androidx.sqlite (bundled)                  | 2.7.0   | 2.7.1   |
| Firebase BOM                               | 34.18.0 | 34.19.0 |
| GitLive Firebase (firestore/auth/common/analytics) | 2.6.0 | 2.7.0 |
| Coil 3                                     | 3.6.0   | 3.6.2   |
| purchases-kmp (core)                       | 3.7.0   | 3.8.0   |
| Robolectric (solo test)                    | 4.16.1  | 4.17    |
| baselogin                                  | 2.0.1   | 3.0.0   |
| kmp-maps (fork propio)                     | 0.9.1-puck5 | 0.9.1-puck6 |

### El puente de RevenueCat, borrado

`purchases-kmp-core-android 3.8.0` declara en su POM `com.revenuecat.purchases:purchases:10.20.0`
(scope `runtime`) — **la misma versión que el puente forzaba a mano**. Se cumple la condición que el
catálogo puso para retirarlo, así que desaparecen:

- la versión `purchases-android` y su comentario de ocho líneas,
- el alias `purchases-android` de `[libraries]`,
- el `implementation(libs.purchases.android)` de `androidMain` en `shared/build.gradle.kts`.

Comprobado antes de borrar: **ningún `.kt` importa del artefacto Android crudo**. Los 20 imports de
RevenueCat del repo son todos `com.revenuecat.purchases.kmp.*` (wrapper KMP), que llega por
`purchases-core`. El artefacto Android solo entraba por debajo, como runtime.
[R8-REVENUECAT-ALREADY-NARROWED-ITS-RULES-001]

### BaseLogin 3.0.0 — major, y el diff del AAR dice que es aditivo

Publicada el **13-09-2026**, sin release notes (el repo no publica releases). Comparadas las 367
clases del AAR 2.0.1 contra las 382 del 3.0.0: **nada retirado** salvo una lambda interna
(`Platform_androidKt$getSocialIdToken$1`). Lo nuevo es todo suma:

- `AuthError`: +8 subtipos (`AccountExistsWithDifferentCredential`, `CredentialAlreadyInUse`,
  `ProviderNotConfigured`, `QuotaExceeded`, `RequiresRecentLogin`, `SignInCancelled`,
  `VerificationCodeExpired`…),
- `SocialTokenResult.Failed`, `RegisterEffect.EmailVerificationRequired`,
- `GoogleSignInProviderAndroid.PickerOutcome` (`Token` / `Cancelled` / `TryFallback`),
- `AuthFailureLogKt`, `AndroidAuthFailuresKt`.

Ampliar un `sealed` rompe a quien hace `when` exhaustivo sobre él. **Paparcar no lo hace**: el único
consumidor de `AuthError` es `FakeAuthRepository` (commonTest), y solo *construye*
`AuthError.Unknown("stub")`. Cero `when`.

⚠️ `RegisterEffect.EmailVerificationRequired` es la mitad-librería de
`AUTH-EMAIL-VERIFICATION-AS-AN-OPT-IN-SETTING-001`. Este ticket **solo sube el pin**; cablear ese
efecto en Paparcar es trabajo de ese otro ticket. Decisión del user el 15-09: el bump entra aquí.

### Ya en la última estable — verificado hoy, no se tocan

Gradle wrapper 9.7.1 (`services.gradle.org/versions/current` confirma que 9.7.1 **es** la actual),
Compose Multiplatform 1.12.0, material3 JB 1.9.0, material-icons-extended 1.7.3 (congelada upstream),
navigation-compose 2.9.2, coroutines 1.11.0, datetime 0.8.0, serialization-json 1.11.0, Koin 4.2.2,
Ktor 3.5.2, Napier 2.7.1, Turbine 1.2.1, Konsist 0.17.3, JUnit 4.13.2, aboutlibraries 15.2.0,
play-services-location 21.4.0, google-services 4.5.0, crashlytics-plugin 3.0.8,
appdistribution-plugin 5.3.0, y **todas** las androidx: core-ktx 1.19.0, appcompat 1.8.0,
activity-compose 1.13.0, lifecycle 2.11.0, work 2.11.2, datastore 1.2.1, splashscreen 1.2.0, glance
1.2.0, test-core 1.7.0, test-ext-junit 1.3.0, espresso 3.7.0, com.google.android.material 1.14.0.

⚠️ `kotlinx-datetime` publica una línea `0.8.0-0.6.x-compat` que ordena *después* de `0.8.0`. No es
más nueva: es la variante de compatibilidad con la API 0.6. No confundirla con un bump.

### `kmp-maps` no sale de un índice — sale de nuestro repo

`0.9.1-puck5` → `0.9.1-puck6`, confirmado en Central (POM 200). Es el único artefacto del catálogo
cuya versión **no se descubre consultando upstream**: subirla significa que el fork publicó un tag
nuevo. El user lo tenía bumpeado sin commitear en el árbol principal y lo confirmó a mitad del
ticket, así que entra aquí y el comentario del catálogo lo dice, para que la próxima auditoría no
vuelva a buscarlo donde no está.

## Diseño

No hay sistema que diseñar en los números: es un cambio en un único sitio. Lo único con diseño es el
borrado del puente, y consiste en devolver una dependencia a su sitio natural (transitiva del
wrapper) en vez de mantenerla declarada a mano.

El trabajo real es el **barrido de lo que se rompe**, y lo hacen los tests, no la lectura.

## Criterio de éxito — resultado

Todo medido sobre la MISMA base (`2ec5f0c2`), con un segundo worktree en master para el baseline:
sin baseline, «no engorda» y «ni un warning nuevo» serían suposiciones.

| gate | resultado |
|---|---|
| `:shared:testDebugUnitTest --rerun-tasks` | ✅ **275 suites · 2.994 tests · 0 fallos · 0 skipped** — y master da los MISMOS 275/2.994: cero asserts editados |
| `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` | ✅ verde |
| `assembleProdRelease` (R8 + shrinkResources + lintVital) | ✅ verde |
| `assembleMockDebug` | ✅ verde |
| warnings de compilación: ni uno nuevo | ✅ **2 sitios `w:`, los mismos que master**, palabra por palabra (los dos de deprecación de plugins KMP/AGP que ya había). Los `WARNING` de Gradle/AGP también coinciden uno a uno |
| El APK de release no engorda | ✅ **+7.260 B (+0,06 %)**: 12.100.486 → 12.107.746. Mock debug **+40.359 B (+0,08 %)**. Once bumps, incluido un major de auth, por 7 KB de release |
| `purchases:10.20.0` sigue resolviendo sin el puente | ✅ leído en `:app:dependencies --configuration prodDebugRuntimeClasspath`: `purchases-kmp-core:3.8.0 → purchases:10.20.0`. Misma coordenada exacta que forzaba el puente, así que el resultado de R8 no puede cambiar por el borrado |
| `CLAUDE.md` con el stack re-verificado | ✅ + corregidas dos afirmaciones stale: BaseLogin ponía «JitPack» (está en Maven Central desde 2.0.0) y faltaba la línea de RevenueCat |

🔑 **Un APK empaquetado incrementalmente MIENTE sobre su tamaño, y miente en megas.** La primera
lectura del mock debug dio **+5,48 MB (+11,10 %)** y casi entró en este doc como «esperable en un
build sin minificar». No lo era: desglosando el zip entrada por entrada, el contenido solo crecía
41 KB y el resto era **padding** — 5,97 MB de huecos, contra 523 KB en el APK de master. AGP
actualiza el zip en sitio al reempaquetar sobre un build previo y deja los agujeros dentro del
fichero. Con `--rerun-tasks` el mismo APK sale en +40.359 B.

⛔ Regla que queda: **una medida de tamaño solo vale sobre un empaquetado limpio**, y se comprueba
sumando `compress_size` de las entradas (el contenido) además del tamaño del fichero. Si las dos
cifras no concuerdan, la que miente es la del fichero.

Y un gate de más, porque salió gratis:

| gate | resultado |
|---|---|
| `:shared:compileKotlinIosSimulatorArm64` | ✅ verde en **Windows**, con `kotlin-native-prebuilt-windows-x86_64-2.4.20` descargado en el propio build. Esto cubre `iosMain` contra BaseLogin 3.0.0 y Kotlin 2.4.20 — no es solo Android |

### Lo que este ticket NO prueba

- **El LINK de iOS sigue necesitando un Mac.** Compilar `iosSimulatorArm64` sí funciona en Windows
  (arriba), pero Kotlin/Native deshabilita las tareas de link de binarios Apple en host no-Mac:
  `linkDebugFrameworkIosSimulatorArm64` sale `SKIPPED`, que no es verde.
- **El login no se ha probado en device.** BaseLogin 3.0.0 compila en Android y en iOS y la suite
  pasa, pero el flujo real de Google/email solo se prueba en el móvil.

## Consumidores auditados

**No rompió nada: cero ficheros de código modificados.** El barrido, por bump:

| bump | consumidores | veredicto |
|---|---|---|
| baselogin 3.0.0 | `AuthError` solo en `FakeAuthRepository` (commonTest), y solo construyendo `AuthError.Unknown`. Cero `when` exhaustivo sobre `AuthError` / `SocialTokenResult` / `RegisterEffect` en todo el repo | cerrado — ampliar un sealed no muerde a quien no lo descompone |
| purchases-kmp 3.8.0 + puente borrado | 20 imports de RevenueCat, **todos** `com.revenuecat.purchases.kmp.*`; ninguno del artefacto Android crudo | cerrado — el artefacto Android solo era runtime |
| Robolectric 4.17 | retira `AndroidVersions` y cambia la firma de `ShadowCameraCharacteristics.set`; ningún test los usa. 4.17 **añade soporte de SDK 37**, que es nuestro `compileSdk` | cerrado |
| Room 2.8.5 + sqlite 2.7.1 | patches; `copyRoomSchemas` y `kspDebugKotlinAndroid` verdes, esquema **v10** intacto (ningún cambio de versión de DB) | cerrado |
| Kotlin 2.4.20 / AGP 9.4.0 / KSP 2.3.12 | los 2.994 tests y los cuatro builds | cerrado — ni un warning nuevo |
| GitLive 2.7.0 · Firebase BOM 34.19.0 · Coil 3.6.2 | mismos gates | cerrado |

## Follow-ups deliberadamente fuera de alcance

- **`AUTH-EMAIL-VERIFICATION-AS-AN-OPT-IN-SETTING-001` queda DESBLOQUEADO**, y su doc ya lo dice.
  Ese ticket estaba parado en *«ver si BaseLogin expone el gancho»*; 3.0.0 lo expone
  (`RegisterEffect.EmailVerificationRequired`, más `AuthError.VerificationCodeExpired` y
  `RequiresRecentLogin`). Decisión del user el 15-09: la verificación se implementa **en Ajustes**.
  Aquí solo sube el pin — no se cablea nada, porque un commit de dependencias no lleva features
  dentro.
- **Compose Multiplatform sigue en 1.12.0** con Kotlin 2.4.20. Hay 1.13.0-alpha01, y la regla de
  «solo estables» lo deja fuera. No hay señal de incompatibilidad: compila y pasa sin un warning
  nuevo.
- `BUILD-ZERO-WARNINGS-IS-ENFORCED-001` sigue abierto: los dos `w:` de deprecación de plugins
  (`org.jetbrains.kotlin.android` y la compatibilidad KMP + `com.android.library`) son los mismos que
  ya había. Este ticket no los toca; son limpieza de scripts, no de versiones.
- El pin de `kmp-maps` a `0.9.1-puck6` lo tenía el user sin commitear en el árbol principal. **Su
  cambio, su commit** — este ticket deja el catálogo en puck5 para no pisárselo, y por eso `CLAUDE.md`
  también sigue diciendo puck5.
