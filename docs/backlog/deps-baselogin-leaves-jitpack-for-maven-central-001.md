# DEPS-BASELOGIN-LEAVES-JITPACK-FOR-MAVEN-CENTRAL-001 · BaseLogin 1.1.0 (JitPack) → 2.0.0 (Maven Central)

**Estado:** 🟡 en rama `chore/DEPS-BASELOGIN-LEAVES-JITPACK-FOR-MAVEN-CENTRAL-001-maven-central`
· pendiente de que CI verifique el linkeo de iOS en el Mac

## Problema

BaseLogin 2.0.0 es un release breaking que hace dos cosas a la vez:

1. **Mueve la publicación de JitPack a Maven Central**, y con ella el grupo:
   `com.github.apptolast.BaseLogin:baselogin` → `io.github.apptolast:baselogin`.
   `io.github.*` y no `com.apptolast` porque el namespace se verifica contra la organización de
   GitHub; el grupo viejo de JitPack nunca habría podido existir en Central, que no concede
   namespaces bajo `com.github.*`.
2. **Renombra todo lo interno** de `custom-login` a `baselogin`: módulo Gradle, paquete Kotlin,
   namespace de Android y paquete de recursos. El artifact id raíz siempre fue `baselogin`.

Todo en 2.0.0 falla en **tiempo de compilación**, nunca en runtime: te enteras al subir el pin.

## Qué se cambió

- `gradle/libs.versions.toml` — pin `1.1.0` → `2.0.0`, coordenada al grupo nuevo, y el comentario
  de la sección reescrito: ya no aplica nada sobre JitPack, el grupo capitalizado ni el POM
  agregado. **Esto deja obsoleto a [DEPS-BASELOGIN-110](deps-baselogin-110.md)**, que documenta
  como vigente el grupo `com.github.apptolast.BaseLogin`.
- `settings.gradle.kts` — fuera el repositorio `jitpack.io`. Verificado antes de borrarlo:
  `baselogin` era la única coordenada `com.github.*` del catálogo, y tras el cambio no queda
  ninguna referencia a jitpack en el repo.
- **42 ficheros `.kt`** — `com.apptolast.customlogin` → `com.apptolast.baselogin`, en `commonMain`,
  `androidMain`, `iosMain`, `commonTest`, `androidUnitTest` y `app/src/mock`.

### ⚠️ El rename que el README de la librería NO documenta

El paquete generado de Compose Resources también cambió, y el `sed` que da el README no lo cubre:

```
login.custom_login.generated.resources  →  login.baselogin.generated.resources
```

Confirmado desempaquetando el AAR de 2.0.0 (`classes/login/baselogin/generated/resources/Res.class`
y `assets/composeResources/login.baselogin.generated.resources/`). Solo lo importa
`PaparcarAuthSlots.kt`, 15 líneas incluido el alias `Res as LoginRes`. Sin esto el build falla con
`Unresolved reference 'custom_login'` y `Unresolved reference 'LoginRes'`.

## Lo que NO hubo que tocar (verificado, no supuesto)

- **`CustomLoginAndroid` → `BaseLoginAndroid`**: 0 usos. Inicializamos vía `appContext` /
  `ActivityHolder`, no por ese entry point.
- **`GoogleSignInProviderIOS` pasa de `class`+companion a `object`, y `getClientId()` desaparece**:
  0 usos en `iosApp/` y en `shared/src/iosMain`. Los únicos `.swift` son `ContentView` y `iOSApp`,
  y ninguno nombra la librería.
- **Workarounds históricos**: no arrastrábamos ninguno — ni excludes de los sub-módulos iOS en
  `*CompileClasspath` / `*RuntimeClasspath`, ni exclude del grupo en `*CInterop`. El de CInterop
  tampoco aplicaría: `iosApp` usa **SPM** (`XCRemoteSwiftPackageReference`), no hay `Podfile` ni
  podspec.

## 🔑 Hallazgo: con JitPack, la metadata de iOS NUNCA resolvió

Comparando `:shared:compileCommonMainKotlinMetadata` contra master limpio:

- **Con 1.1.0 (JitPack)** la tarea ni siquiera resuelve:
  `Could not find custom-login-iosarm64-1.1.0.jar`, buscado en `jitpack.io`. Ese artefacto nunca
  existió allí.
- **Con 2.0.0 (Central)** resuelve y la tarea avanza hasta compilar de verdad.

Esto explica por qué los tres errores de `iosMain` de abajo pudieron vivir tanto sin que nadie los
viera desde el IDE: el camino de metadata estaba roto en la raíz.

## Los 3 errores de `iosMain` que salieron al poder compilar

Preexistentes en master, no causados por la migración (comprobado con `git stash`: los mismos tres,
byte a byte, sin los cambios).

1. **`DetectionDiagnosticsTap.kt:46`** y **`DetectionEffectExecutor.kt:94`** —
   `Unresolved reference 'Volatile'`. Falta `import kotlin.concurrent.Volatile`. Compilaban en
   Android porque en JVM `kotlin.jvm.Volatile` entra por los default imports; en Native no existe
   tal cosa. El resto de commonMain ya lo importa explícitamente
   (`FirestoreDetectionEventLogger`, `CoordinatorParkingDetector`, `SplashViewModel`…).
2. **`PermissionsScreen.ios.kt:27`** — `when` no exhaustivo: faltan `RequestActivityRecognition` y
   `RequestNotifications`, los permisos por tarjeta de [ONB-CARDS-001], que se añadieron al sealed
   y a Android sin barrer iOS.

   ⛔ **No se tapó con un `else`**: eso habría compilado tragándose los dos efectos en silencio, y
   las tarjetas de iOS no pedirían nada. `IosPermissionRequester` **ya tenía**
   `requestNotifications()` y `requestActivityRecognition()` correctos, pero `private`, solo
   alcanzables vía `requestStep1`/`requestProducerSensors`, que disparan **los dos diálogos a la
   vez**. Se hacen públicos y cada rama llama al suyo: una tarjeta pide un permiso, sale un
   diálogo. Sin lógica nueva, solo alcanzabilidad.

## Verificación

| Comprobación | Resultado |
|---|---|
| `io.github.apptolast:baselogin:2.0.0` en Central | ✅ POM 200 · `maven-metadata.xml` con `release=2.0.0` |
| Resolución Android (`debugCompileClasspath`) | ✅ raíz → `baselogin-android:2.0.0` |
| Resolución iOS (`iosSimulatorArm64CompileKlibraries`) | ✅ raíz → `baselogin-iossimulatorarm64:2.0.0`, sin excludes |
| `:shared:compileKotlinIosSimulatorArm64` + `IosArm64` | ✅ |
| `:app:assembleProdDebug` · `:app:compileMockDebugKotlin` | ✅ |
| `:shared:testDebugUnitTest` | ✅ 2044 tests, 0 fallos, 0 errores |
| `:shared:linkDebugFrameworkIosSimulatorArm64` | ⏳ **solo en CI** — ver abajo |

Todo con `--rerun-tasks`, para que ningún verde saliera de caché.

### ⛔ El linkeo no se puede verificar en Windows

```
Skipping task ':shared:linkDebugFrameworkIosSimulatorArm64' as task onlyIf 'Task is enabled' is false.
```

Kotlin/Native **deshabilita** las tareas de link de binarios Apple en host no-Mac: cross-compila los
`.klib` desde Windows, pero el binario final necesita el toolchain de Xcode. `SKIPPED` no es verde.
Lo verifica el job `apple` de `.github/workflows/ci.yml` (`macos-latest`), que corre exactamente
`:shared:linkDebugFrameworkIosSimulatorArm64` — y **CI solo dispara en push a
`master`/`alpha`/`beta` o en pull request contra ellas**, por eso esto va por PR.

## Fuera de alcance (preexistente, ficheros no tocados por esta rama)

- **`:shared:compileCommonMainKotlinMetadata`** ahora llega a compilar y falla en `AppDatabase.kt:38`:
  `Object 'AppDatabaseConstructor' is not abstract and does not implement abstract member:
  fun initialize(): T`. Es el `@ConstructedBy` generado por Room KMP. No está en ningún build que
  usemos (ni CI, ni assemble, ni tests): es el camino de metadata/IDE.
- **`:shared:compileTestKotlinIosSimulatorArm64`** falla en ~14 sitios: nombres de test con
  backticks que llevan `,` y `()` (ilegales como símbolos Native), `System.currentTimeMillis`
  (JVM-only) y `@ExperimentalNativeApi`. `commonTest` está escrito para JVM por diseño — corre vía
  `:shared:testDebugUnitTest`. Hacerlo compilar en Native es un proyecto propio sobre 191 ficheros.
- **`FakeAuthRepository.kt:4`** tiene `import com.apptolast.baselogin.domain.model.*`, wildcard
  prohibido por CLAUDE.md. Preexistente: el `sed` solo le cambió el prefijo.
