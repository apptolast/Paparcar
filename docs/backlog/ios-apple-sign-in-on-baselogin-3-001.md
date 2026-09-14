# IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001 · BaseLogin 3.0.0 + login con Apple y Google en iOS

**Estado:** 🔵 En progreso · rama `feature/IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001` ·
repo principal, sin worktree (decisión del user 14-09) · guía de configuración: [
`docs/release/IOS-SIGN-IN-SETUP.md`](../release/IOS-SIGN-IN-SETUP.md)

Sustituye a [IOS-SOCIAL-LOGIN-001](ios-social-login-001.md), que era el spec escrito a ciegas desde
Windows sobre BaseLogin 1.1.0.

## Problema

- En iOS no hay login social: `PaparcarIosBootstrap` pasaba `paparcarLoginConfig(googleWebClientId = null)`,
  así que solo había email/contraseña.
- Consumíamos BaseLogin 2.0.1, que en iOS **crashea con `EXC_BAD_ACCESS` en cada `Logger`** (p. ej.
  al cerrar sesión sin `signOutHandler`) y reporta cancelaciones como `OperationNotAllowed`.
  3.0.0 lo arregla y clasifica los errores por código.

## Doctrina violada

Ninguna de detección. Paridad de plataforma + App Review: guideline 4.8 (si hay login de terceros,
Apple es obligatorio) y 5.1.1(v) (la app borra cuentas → hay que revocar el token de Apple).

## Decisiones (user, 13-09-2026)

- Apple **solo en iOS**. En Android la librería usaría OAuth web de Firebase, que exige Services ID +
  key `.p8`; no se monta.
- Google entra en iOS en este mismo ticket.

## Diseño

- **Una sola fuente de la oferta** sigue siendo `paparcarLoginConfig` (commonMain). Gana
  `appleSignIn: Boolean` y Google se ofrece con el id web **o** el de iOS.
- **iOS lee `CLIENT_ID` de `GoogleService-Info.plist`** en runtime (`ios/auth/GoogleServiceInfo.kt`):
  el fichero ya es obligatorio para Firebase, sigue gitignored, y sin él el botón no aparece en vez
  de quedar muerto.
- **Swift**: `AppleSignInCoordinator.swift` y `KotlinInterop.swift` copiados tal cual del demo de
  BaseLogin 3.0.0 (sign-in con nonce + `revokeHandler`); `GoogleSignInCoordinator.swift` propio
  (sign-in + `signOutHandler`). Se registran en `AppDelegate` antes del bootstrap. El redirect de
  Google va por `.onOpenURL` (en apps de escenas `application(_:open:)` no se llama).
- **Xcode**: `iosApp.entitlements` (`com.apple.developer.applesignin`), `CODE_SIGN_ENTITLEMENTS` en
  Debug/Release (y fuera de la copia de recursos del grupo sincronizado), URL scheme
  `$(GOOGLE_REVERSED_CLIENT_ID)` definido en `Config.xcconfig`.

### ⚠️ Lo que el README de BaseLogin no cuenta: `export(libs.baselogin)`

Sin él, `GoogleSignInProviderIOS` / `AppleSignInProviderIOS` **no existen en `ComposeApp.h`**
(`cannot find 'GoogleSignInProviderIOS' in scope`): Kotlin/Native solo exporta los tipos de una
dependencia que menciona la API pública de `shared`, y nada menciona los providers. La demo de la
librería sí lo hace (`export(project(":baselogin"))`). Añadido en el bloque `framework` de
`shared/build.gradle.kts`.

## Verificación

- `:shared:testDebugUnitTest --tests *PaparcarLoginConfigTest*` + `:shared:compileKotlinIosSimulatorArm64` ✅
- `xcodebuild … -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO build` → **BUILD SUCCEEDED** ✅;
  el `Info.plist` del bundle trae el scheme resuelto y el `.entitlements` no se copia como recurso.

## Migración 3.0.0 — consumidores auditados

- `when` exhaustivo sobre `AuthError` / `RegisterEffect` / `SocialTokenResult`: **0** en Paparcar
  (solo los fakes construyen `AuthError.Unknown`).
- `is AuthError.OperationNotAllowed` para detectar cancelación: **0**.
- `Res.string.auth_error_*` mostrados por nosotros: **0** (los pinta la librería).
- Build verde tras el bump: `:shared:linkDebugFrameworkIosSimulatorArm64` + `:app:compileProdDebugKotlin`.

## Criterio de éxito

- iPhone real: botones Google y Apple visibles; ambos llegan a Home.
- Primer login con Apple deja `displayName` en el perfil.
- Cerrar sesión con Google y volver a entrar deja elegir otra cuenta.
- Borrar una cuenta de Apple la quita de *Ajustes → Apple ID → Iniciar sesión con Apple*.
- Android sin cambios de comportamiento (`PaparcarLoginConfigTest`).

## 🔴 Bloqueo abierto (14-09-2026, noche) — Google en iOS entra y la app lo echa

**Síntoma** (simulador iOS, no iPhone real): flujo de Google completo → vuelve a la app → diálogo
*«Couldn't load your account — Something went wrong while loading your data. Please sign in again.»*
→ de vuelta al login.

**Lo que ya está claro, leyendo el código:**

- Ese diálogo es `error_bootstrap_fatal_*`: lo pinta `App.kt` al recibir `SplashEffect.ShowError`, que
  solo emite `SplashViewModel.abortBootstrap()` (y ahí se hace `signOut()`).
- O sea, **Google + Firebase Auth funcionaron**: hubo `AuthState.Authenticated`. Lo que falla es el
  bootstrap que va DESPUÉS, en uno de dos pasos: `getOrCreateUserProfile()` («profile sync FAILED»)
  o `bootstrapUserData()` («user-data bootstrap FAILED»), o una excepción no capturada de la cadena.
  Offline queda descartado: ese caso saca otro diálogo, con reintento.
- **Por qué los logs pegados no dicen nada:** en iOS nunca se inicializa Napier. `Napier.base(DebugAntilog())`
  solo existe en `app/.../PaparcarApp.kt` (Android). Todo `PaparcarLogger` de iOS se descarta, así
  que la excepción real no llega a la consola de Xcode.

**Hipótesis, de más a menos probable:**

1. **Fallo de Firestore en iOS al leer o crear el perfil o los datos** (reglas, deserialización GitLive en
   iOS, campos null de una cuenta Google nueva). No es específico de Google: cualquier login que
   llegue a `Authenticated` en iOS pasaría por ahí.
2. **App Check:** `FirebaseAppCheck` está linkado en SPM sin ningún provider configurado, así que usa
   DeviceCheck, que no existe en el simulador (`I-AUT000018`, `I-FST000001 AppCheck failed`). Poco
   probable como causa: Android no tiene App Check en el código y funciona, así que Firestore
   no debería estar exigiéndolo. Aun así, confirmarlo en la consola (*App Check → APIs → Cloud Firestore*
   en *Unenforced*).

**Plan para mañana, en este orden:**

1. Inicializar Napier en iOS (`PaparcarIosBootstrap`, solo en debug) → repetir el login con Google en el
   simulador → copiar la línea `SplashViewModel … FAILED` con su excepción.
2. Discriminar: entrar en iOS con **email/contraseña** con una cuenta que ya funcione en Android. Si
   también falla, el bug es del bootstrap de iOS y no del login social.
3. Firebase `pap-26`: comprobar en *Authentication → Users* que se creó el usuario de Google, que
   exista (o no) `users/{uid}` en Firestore, y el estado de App Check en Firestore.
4. Apple: no probado todavía (paso 3 de la guía pendiente: Key `.p8` + proveedor en Firebase).

**Dispositivo real:** la firma automática no sube a TestFlight. Mañana se monta **fastlane +
provisioning profile manual**, como en otras apps del equipo (Fliksy, Tu Armería, Kropia): mirar su
`fastlane/` como referencia. Un compañero hará las pruebas en su iPhone/iPad.

### 15-09 · Trazas puestas (sin commit)
- `PaparcarIosBootstrap.ensureStarted()` instala `Napier.base(DebugAntilog())` en binarios debug. En la
  consola de Xcode, filtrar por **`AUTHDIAG`** (el camino) y por **`ERROR`** (la excepción con su stack).
- Trazas `AUTHDIAG/*` en `SplashViewModel`, `GetOrCreateUserProfileUseCase` (resultado de
  `refreshSession`), `UserProfileRepositoryImpl` (qué rama sirvió el perfil) y
  `RemoteUserProfileDataSourceImpl.getProfile` (existe o no el doc; fallo de lectura o de decodificación).
- ⚠️ **Riesgo encontrado de camino (no es de este ticket):** `getProfile` convierte cualquier fallo en
  `null`, y `null` se lee como «usuario nuevo» → `set(profile)` **sobrescribe el doc entero** de un
  usuario existente si falla la lectura o la decodificación (y Room está vacío tras el wipe). Se
  pierden `defaultVehicleId` y `firstStepsDismissedAt`. Si la traza confirma este camino en iOS, abrir
  ticket propio.
- Verificado: `:shared:compileKotlinIosSimulatorArm64` ✅ · tests `UserProfileRepositoryImpl` (4),
  `GetOrCreateUserProfileUseCase` (7), `SplashViewModel` (32), `UserProfileMapper` (6),
  `PaparcarLoginConfig` (9) ✅.

### 15-09 · Primera traza (simulador, Google)
```
AUTHDIAG/GetOrCreateUserProfile - refreshSession result=Success
AUTHDIAG/RemoteUserProfile      - getProfile: users/{uid} exists=true
AUTHDIAG/UserProfileRepository  - getOrCreateProfile: remote profile found=true
❤️ ERROR AUTHDIAG/UserProfileRepository - getOrCreateProfile FAILED        (+10 ms)
❤️ ERROR SplashViewModel - Room wipe failed                                  (tras el signOut)
```
- **Auth, Google y la lectura de Firestore funcionan** en el simulador: el doc `users/{uid}` existe y se
  decodifica. Descartado el riesgo de sobrescritura de arriba en este caso.
- Falla 10 ms después, en el tramo `reconciledWith` → (`updateIdentityFields`) → `profileDao.insertOrUpdate`.
  Un round-trip a Firestore no cabe en 10 ms, y justo después falla también `Room wipe`: **la sospecha
  principal es Room en iOS**, no el login.
- **No es por no tener dispositivo físico**: Room (BundledSQLiteDriver) y Firestore corren igual en el
  simulador. Un build de TestFlight fallaría igual.
- Pendiente: el stack trace. `DebugAntilog` lo imprime en las líneas SIGUIENTES a `ERROR`, y el filtro de
  Xcode las oculta. Copiar sin filtro desde `getOrCreateProfile FAILED` hasta la siguiente línea con fecha.

### 15-09 · Causa: la base de datos del simulador es de un esquema que ya no existe
Medido en el `paparcar.db` del contenedor de la app en el simulador (copiado al scratchpad, en solo lectura):
- `PRAGMA user_version = 1`, `identity_hash = 03390c9c…`, último write el **30-08**. Ningún `schemas/N.json`
  tiene ese hash (el v1 real es `e8bc446b…`): es un «v1» de desarrollo **anterior al colapso de versiones**
  de DATA-ROOM-RETURNS-TO-VERSION-ONE-001.
- Reproducida la cadena `ALL_MIGRATIONS` 1→7 sobre la copia y comparada con `7.json`: todas las
  migraciones pasan, pero queda **`parking_sessions` sin `retractedAtMs`**. Room valida el esquema tras
  migrar, falla («Migration didn't properly handle»), y a partir de ahí **todo acceso a Room lanza**:
  el `insertOrUpdate` del perfil y el wipe. `fallbackToDestructiveMigration` no cubre este caso: solo
  actúa cuando falta una migración, no cuando la validación falla.
- **No es un fallo de la implementación de Room en iOS.** Comparado con Munición (mismo patrón:
  `Room.databaseBuilder` + ruta en Documents + `BundledSQLiteDriver` + `@ConstructedBy`). La única
  diferencia es que Munición fija `setQueryCoroutineContext(Dispatchers.Default)`; Room ya usa
  `Dispatchers.IO` por defecto en native, así que no explica esto.
- No afecta a usuarios: iOS nunca se ha distribuido, y un TestFlight es una instalación limpia.
- **Arreglo:** borrar la app del simulador (`xcrun simctl uninstall booted com.rndeveloper.paparcar`) y
  volver a lanzarla.

### 15-09 · Login ✅ en simulador → siguiente crash: kmp-maps en iOS no es compatible con CMP 1.12
Tras borrar la app del simulador, **Google entra y el bootstrap completa** (perfil, 1 vehículo, 2 aparcamientos,
zonas). Al salir del tutorial y pintar el mapa:
```
Uncaught Kotlin exception: kotlin.internal.IrLinkageError: Function 'encodeToData' can not be called:
No function found for symbol 'org.jetbrains.skia/Image.encodeToData|encodeToData(org.jetbrains.skia.EncodedImageFormat;kotlin.Int){}[0]'
  at com.swmansion.kmpmaps.core#toUIImage__at__androidx.compose.ui.graphics.ImageBitmap()   ← MarkerSnapshotter
```
- **Causa (medida):** `io.github.rndevelo.kmpmaps:core:0.9.1-puck5` está compilado contra **Compose 1.9.0**
  (`gradle/libs.versions.toml` del tag `v0.9.1-puck5`: `compose = "1.9.0"`, `kotlin = "2.3.0"`).
  Paparcar resuelve **Skiko 0.150.1** (CMP 1.12.0). En Skiko 0.9.37.4 y 0.144.6 la firma es
  `encodeToData(format, quality)`, y en **0.150.1** pasa a `encodeToData(format, quality, pngCompressionLevel)`.
  El símbolo de 2 parámetros ya no existe. Kotlin/Native enlaza igual (*partial linkage*) y revienta **en
  runtime** la primera vez que se pinta un marcador.
- El código fuente del fork (`core/src/iosMain/.../Extensions.kt:592`, `image.encodeToData(EncodedImageFormat.PNG)`)
  compila tal cual contra la firma nueva gracias a los parámetros por defecto: **basta con recompilar el
  fork contra CMP 1.12** y publicar `0.9.1-puck6`. En Android no ocurre (JVM, otra ruta de render).
- No es de simulador: en un iPhone/TestFlight crashea igual.
- Intentado y descartado: `-Xpartial-linkage-loglevel=ERROR` en el bloque `framework` (con y sin caché
  nativa, `--rerun-tasks`) → **BUILD SUCCESSFUL**. Con `isStatic = true` la comprobación no salta en Gradle,
  así que no sirve como guardia en compilación y no se ha dejado. Detectar esto sigue exigiendo ejecutar
  en iOS.

### 15-09 · Fork kmp-maps recompilado: `0.9.1-puck6`
- `~/StudioProjects/Gonzalo/kmp-maps`: descartados los cambios locales (autorizado por el user), rama puesta en
  `origin/paparcar/puck` (`d4755cf`, posterior al tag `v0.9.1-puck5`), commit **`28f052f`**
  `build: rebuild on Kotlin 2.4.10 + Compose Multiplatform 1.12.0 as 0.9.1-puck6`:
  compose 1.9.0→1.12.0 · kotlin 2.3.0→2.4.10 · lifecycle 2.9.4→2.11.0 · coroutines→1.11.0 · serialization→1.11.0 ·
  **fuera `iosX64`** (CMP 1.12 ya no lo publica) · `google-maps/MapDelegate.kt`: Kotlin 2.4 convierte en error el
  `is GMSCircle/GMSPolygon/GMSPolyline` sobre `GMSOverlay` («always false») → se amplía a `Any` antes del `when`.
- Primer push rechazado (403). Tras dar acceso de colaborador al user: **push** `d4755cf..28f052f` a
  `paparcar/puck`, tag anotado **`v0.9.1-puck6`** y GitHub Release. El Release no disparó `publish.yml`, así
  que se lanzó con `workflow_dispatch` sobre el tag (igual que se publicó puck5):
  run `34906435938`. `publishToMavenCentral(automaticRelease = false)`: la subida deja un deployment
  **pendiente** en el Central Portal y liberarlo a Maven Central es un paso manual del dueño del namespace
  `io.github.rndevelo`.
- Verificado: `publishToMavenLocal` de todos los módulos ✅ (firma desactivada con un init script local, solo
  para `mavenLocal`). En el klib, el símbolo pasa de `encodeToData(EncodedImageFormat;Int)` (puck5) a
  `encodeToData(EncodedImageFormat;Int;Int)` (puck6). Paparcar: `:shared:linkDebugFrameworkIosSimulatorArm64` +
  `:app:compileProdDebugKotlin` ✅ con puck6.
- Paparcar consume puck6 **desde `mavenLocal`** mediante un bloque TEMPORAL en `settings.gradle.kts` (limitado al
  grupo `io.github.rndevelo.kmpmaps`). **Quitarlo en cuanto puck6 esté en Maven Central.** Sube también
  `lifecycle-runtime-compose` resuelto de 2.11.0-beta01 a 2.11.0 estable.
- `Publish` run `34906435938` → **success** (15-09, 01:02). Aun así `repo1.maven.org/.../core/0.9.1-puck6/` da
  **404**: el deployment está esperando en el Central Portal (`automaticRelease = false`). Falta que el dueño
  del namespace `io.github.rndevelo` pulse **Publish** en central.sonatype.com → *Deployments*. Hasta entonces
  se mantiene el `mavenLocal` temporal de `settings.gradle.kts`.

### 15-09 · Aviso de Xcode en `IosPermissionManagerImpl`
«This method can cause UI unresponsiveness if invoked on the main thread» en
`CLLocationManager.locationServicesEnabled()`. Es una consulta bloqueante a `locationd`, y todos los que llaman a
`refreshPermissions()` son ViewModels en Main. Ahora se lee en una cola global y se publica con `update {}`,
y las otras escrituras (incluida la de notificaciones, que ya llegaba desde otro hilo) también pasan a
`update {}` para no pisarse. Para no mostrar «GPS apagado» un instante: un estado autorizado ya implica
Location Services activo (con el interruptor global apagado, CoreLocation devuelve `denied` a todas las apps),
así que `isLocationServicesEnabled = hasLocation || valor anterior` hasta que llega la respuesta.
`:shared:compileKotlinIosSimulatorArm64` ✅.

### 15-09 · Mapa ✅ con puck6 (mavenLocal) → crash al abrir Ajustes en iOS
`InstanceCreationException: SettingsViewModel` ← `NoDefinitionFoundException: WidgetPinRequester`.
- **Causa:** `SettingsViewModel(widgetPinRequester: WidgetPinRequester? = null)` estaba registrado con
  `viewModelOf(::SettingsViewModel)`. `viewModelOf` resuelve **todos** los parámetros con `get()`, también los
  nullables, y `WidgetPinRequester` solo lo registra `:app` (`AppModule`/`MockModule`) en Android.
- **Por qué `KoinModuleVerifyTest` no lo vio:** `verify()` sí considera opcional un parámetro nullable. El test y
  el runtime no están de acuerdo.
- **Arreglo:** registro explícito con `widgetPinRequester = getOrNull()`. Barrido de todos los
  `viewModelOf/singleOf/factoryOf/scopedOf` de ambos módulos: el mismo patrón en
  `PaywallViewModel(analyticsTracker: AnalyticsTracker?)`. Hoy no crashea (`AnalyticsTracker` está en
  `DataModule` común), pero se registra explícito igual.
- **Guardia:** `KoinOptionalDependencyGuardrailTest` (Konsist, androidUnitTest). Ninguna clase registrada por
  referencia a constructor puede tener un parámetro nullable. Tiene testigos de población (ficheros DI ≥ 6,
  registros ≥ 3) y comprueba que cada registro resuelve a una clase. **Falsificada** con `--rerun-tasks`:
  al devolver Settings a `viewModelOf` → rojo, `::SettingsViewModel(widgetPinRequester: WidgetPinRequester?)`.
- Verde: `KoinOptionalDependencyGuardrailTest` (1), `KoinModuleVerifyTest` (2), `SettingsViewModelTest` (56),
  `PaywallViewModelTest` (6) · `:shared:compileKotlinIosSimulatorArm64` ✅.
