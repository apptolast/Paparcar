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
