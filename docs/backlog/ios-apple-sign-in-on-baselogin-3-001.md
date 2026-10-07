# IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001 · BaseLogin 3.0.0 + login con Apple y Google en iOS

**Estado:** 🟡 **En master (squash, 25-09) pero NO terminado** · el hash vive en `MEMORY.md` ·
guía de configuración: [`docs/release/IOS-SIGN-IN-SETUP.md`](../release/IOS-SIGN-IN-SETUP.md)

⛔ **La rama remota sobrevivió al squash cinco días más que la local.** Esta línea decía *«rama y
worktree borrados»* y era cierto **solo en local**: `origin/feature/IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001`
(tip `b227a9c4f`, 18 commits propios, 120 por detrás) siguió viva hasta el **07-10**, cuando el
barrido [DOCS-A-CLOSED-TICKET-MUST-NOT-SAY-IT-IS-IN-REVIEW-001] la verificó commit a commit antes
de borrarla. Los cuatro commits posteriores al squash **ya están en master por contenido**: el
entitlement `com.apple.developer.applesignin` (`iosApp/iosApp/iosApp.entitlements`), el
`Config.xcconfig` con `MARKETING_VERSION=0.1.1` / `CURRENT_PROJECT_VERSION=2`, y las 8 menciones del
Services ID en `IOS-SIGN-IN-SETUP.md`. El único blob distinto era `app-icon-1024.png`, y el de la
rama dibujaba la marca **DESCARTADA**: master lo regeneró desde el maestro en
[BRAND-THE-APPLE-SURFACES-ARE-OUTSIDE-THE-MASTER-001] (`d7041d32c`), así que la versión de la rama
era la vieja, no trabajo pendiente. ✅ Y su tip **no se perdió**: lo retiene el tag local
`prerebase/feature/IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001`, que esta misma ficha ya dejaba puesto
como vía de vuelta — pero ⛔ **ese tag no está en `origin`**, así que la vía de vuelta existe solo
en esta máquina.

⛔ **Lo que el merge NO significa.** El código está dentro y la mitad Android está verificada
(3 770 tests, `assembleMockDebug`, `compileProdDebugKotlin`), pero **nada de iOS se ha compilado**:
el rebase y el merge se hicieron en Windows, sin Xcode, y los targets `iosArm64`/`iosSimulatorArm64`
no se construyen ahí. Queda por hacer, y en este orden:

1. **Levantar el proyecto en el Mac** — es el gate. Si la framework no enlaza tras un rebase de 82
   commits de distancia, se sabe aquí.
2. **El bloqueo de Google en iOS de más abajo sigue sin cerrar** (la beta interna prueba el login de
   **Apple**, que es lo que arregló `0.1.1 build 2`; Google es otro camino). ⚠️ Su diagnóstico de
   entonces decía que no había logs porque *«en iOS nunca se inicializa Napier»*: **esa premisa ya es
   falsa** — este mismo trabajo instala `DebugAntilog` en debug y master instala `IosFileAntilog` en
   todos los builds, así que la excepción real ya se puede leer en Xcode y en el informe. Hay que
   volver a mirarlo con log delante antes de teorizar otra vez.
3. Productos de compra de iOS en App Store Connect: las IAP se revisan **con** la primera submission,
   y en `iosMain` hoy sólo hay canje de códigos.

📌 El tag `prerebase/feature/IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001` **se deja puesto**: es la vía de
vuelta a los SHAs de antes del rebase si el Mac dice que algo se rompió por el camino.

> ♻️ **Rebase 25-09-2026 sobre master `4b90ae506`** (estaba 82 commits por detrás; los 18 propios se
> replicaron, `behind:0`). Backup en el tag `prerebase/feature/IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001`.
> Cinco conflictos, todos resueltos **a favor de master, que era superconjunto**, aplicando encima
> sólo la aportación real de cada commit:
> 1. `PaparcarLogger` + `PaparcarIosBootstrap` — master instala `IosFileAntilog` en TODOS los builds
>    (`IOS-A-DETECTION-THAT-FAILS-MUST-LEAVE-A-TRACE-001`); se le añade `DebugAntilog` en debug, que
>    es lo que lleva la línea a Xcode. Napier reparte a cada sumidero, así que **van los dos**.
> 2. `IosPermissionManagerImpl` — master ya lee precisión, Bluetooth y diálogos gastados
>    (`IOS-PERMISSIONS-A-ROW-MUST-BE-GRANTABLE-HERE-001`). De esta rama sobrevive **sólo** sacar
>    `locationServicesEnabled()` del hilo principal, con su publicación optimista.
> 3. y 4. `.gitignore` — las dos ramas descubrieron por separado que `fastlane/…` sin `**/` no tapaba
>    nada. Se conserva el bloque de master y se añaden las reglas nuevas (`**/fastlane/.env`, `*.p8`,
>    `screenshots.html`).
> 5. El rename de los 20 fakes a `:mockkit` contra el `FakeCarAudioRouteSource` que master añadió en
>    la ruta vieja: git lo reubicó solo y se aceptó.
>
> ⚠️ **Y el rebase destapó un defecto real, arreglado aquí**: `AndroidPlatformModule` (producción)
> bindeaba `FakeCarAudioRouteSource`, o sea el grafo de producción importando el grafo mock. Compilaba
> sólo mientras los fakes vivían en `:shared`. Nace `AndroidCarAudioRouteSource` — la implementación
> ANDROID del port, vacía por construcción porque aquí la presencia del coche la prueba el carril ACL,
> exactamente como el contrato del port ya prescribía. El split de `:mockkit` no rompió el binding: lo
> **reveló**. [IOS-STORE-SHOTS-WITHOUT-SHIPPING-THE-DEMO-CODE-001]
>
> Verificado tras el rebase: **3 770 tests en verde**, `assembleMockDebug` y `compileProdDebugKotlin`
> OK. ⛔ **La mitad iOS NO se ha podido verificar**: esta máquina es Windows, no hay Xcode, y los
> targets `iosArm64`/`iosSimulatorArm64` no se compilan aquí. Hace falta un Mac para saber si la rama
> sigue enlazando.

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

  > 🔴 **Esta línea describía una intención, no el repo (corregido el 19-09).** El
  > `CODE_SIGN_ENTITLEMENTS` sí entró en las dos configuraciones y la exclusión del grupo
  > sincronizado también, pero **la clave `com.apple.developer.applesignin` nunca llegó al
  > fichero**: `git log --follow iosApp/iosApp/iosApp.entitlements` devuelve un único commit, el de
  > push (`47b54044`, 16-09), que lo creó con `aps-environment` y nada más.
  >
  > Coste: el build 2 de TestFlight salió sin la capability y el botón de Apple no presentaba la
  > hoja del sistema —`performRequests()` devolvía `ASAuthorizationError.unknown` (1000) en el
  > acto—, que es el «parpadeo de milisegundos» que reportaron los testers en iPhone físico el
  > 19-09. La verificación del ticket era `xcodebuild -sdk iphonesimulator CODE_SIGNING_ALLOWED=NO`:
  > **un build sin firma no puede ver que falta un entitlement**, y el paso 4 de la guía dejó Apple
  > sin probar. Lo que habría cazado esto es abrir *Signing & Capabilities* una vez, que es
  > justamente lo que el paso 2 manda mirar y quedó en 🟡.
  >
  > Al reponerla hay que **reemitir el perfil** (`fastlane ios bootstrap_match`): un perfil no firma
  > un entitlement que no lleva dentro. Paso 2b de la guía.

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

### 17-09 · Rama lista para mergear y Apple listo para probar
- Rama rebasada sobre master (0 detrás, 9 delante). `3734bf2c`: el registro explícito de Ajustes
  absorbe el `signOut` que llegó en master. `09550251`: **fuera el `mavenLocal` temporal** —
  `io.github.rndevelo.kmpmaps:core:0.9.1-puck6` ya está liberado en Maven Central
  (índice `lastUpdated 20260915014801`). Era lo único que impedía mergear.
- **Paso 3 ✅ (user):** key `.p8` de Sign in with Apple creada y el proveedor Apple configurado en
  Firebase `pap-26` con Team ID + Key ID + clave privada. Con eso ya se puede revocar el token al
  borrar la cuenta (guideline 5.1.1(v)).
- **Firma verificada, no hace falta `bootstrap_match`:** el perfil `match AppStore
  com.rndeveloper.paparcar` (UUID `70c0ed51-…`, caduca 2027-06-07) **ya trae
  `com.apple.developer.applesignin`**, así que se creó DESPUÉS de activar la capability en el App ID.
  Comprobado descifrando el `.mobileprovision` que instala `fastlane ios match_certificates`.
- `.env` de fastlane completo salvo `FASTLANE_ITC_TEAM_ID`, que está vacío y **no hace falta**: las
  lanes pasan `api_key`, y `pilot` con API key no usa el itc team id.
- Pendiente: probar Apple (nunca se ha probado, ni en simulador) y la primera subida a TestFlight
  (`fastlane ios beta`) para que el compañero lo pruebe en iPhone real.

### 18-09 · Primera subida a TestFlight: tres bloqueos, ninguno del login
El paso 3 quedó ✅ (el user montó la key `.p8` y el proveedor Apple en Firebase), así que se lanzó
`fastlane ios beta`. Cayó tres veces, por tres causas distintas:

1. **Heap del link Release.** `linkReleaseFrameworkIosArm64` murió con
   `e: Compilation failed: Java heap space` en `CallGraphBuilder` (la fase de LTO), a los 8m36s.
   ⛔ **El compilador Kotlin/Native corre DENTRO del daemon de Gradle**, no en un proceso propio:
   medido con `ps` mientras la tarea corría — los únicos JVM vivos son el daemon y el wrapper. Por
   eso `kotlin.native.jvmArgs=-Xmx6g` no arregló nada (no hay proceso al que aplicarse; solo tardó
   13m45s en reventar en vez de 8m36s) y la pista buena estaba en el log: el `OutOfMemoryError`
   saltaba en `org.gradle.fileevents`, el file watcher, que vive en el daemon.
   **Arreglo: `org.gradle.jvmargs` 4096M → 8192M.** Con eso el link pasa en 14m52s. El framework
   debug del simulador nunca lo sufrió porque no lleva LTO.
2. **El perfil de firma iba atrasado.** `xcodebuild`: *«Provisioning profile "match AppStore
   com.rndeveloper.paparcar" doesn't include the aps-environment entitlement»*. Quitar el
   entitlement no era opción: la app declara push de verdad (`iosApp.entitlements` +
   `IosPushRegistry.kt`, [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]).
   Consultadas las capabilities del App ID con la API de ASC (`Spaceship::ConnectAPI::BundleId`):
   el portal ya tenía `PUSH_NOTIFICATIONS`, `APPLE_ID_AUTH` e `IN_APP_PURCHASE`. El desfasado era
   el perfil, creado el 14-09 antes de activar push.
   **Arreglo: `fastlane ios bootstrap_match`** (renueva SOLO el perfil de Paparcar, nunca el
   certificado compartido), verificado descifrando el `.mobileprovision`: ya trae `aps-environment`
   y `com.apple.developer.applesignin`.
   ⛔ **Regla que esto deja**: activar una capability en el App ID NO actualiza el perfil. Cada vez
   que se active una hay que pasar por `bootstrap_match` o el Release peta en firma. Hoy mordió por
   primera vez porque es el primer Release que se firma.
3. `FASTLANE_ITC_TEAM_ID` está vacío en el `.env` y **no hace falta**: las lanes pasan `api_key`, y
   `pilot` con API key no usa el itc team id.

La beta sube como **0.1.0** (decisión del user: es la primera de iOS; Android va por 1.3.0 y no se
busca paridad). `latest_testflight_build_number` no encuentra builds previos → build 1.
