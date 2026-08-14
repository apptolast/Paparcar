# DEPS-BASELOGIN-110 · Subir BaseLogin del build por commit-hash a la release 1.1.0

**Estado:** ✅ Done · en `master` (squash, 14-08-2026) · rama y worktree borrados

## Problema

`gradle/libs.versions.toml` apuntaba a un build de JitPack **por hash de commit** de una rama de
la librería:

```toml
# JitPack commit-hash build of feature/improve-custom-login-integration (host-integration fix).
# TODO: switch to a release tag (e.g. 1.0.18) once the branch is merged + tagged.
baselogin = "35a5e15fc4"
```

Depender de un hash de rama no es reproducible ni auditable: no hay changelog, no hay tag, y la
rama puede reescribirse. El propio TODO pedía volver a un tag en cuanto existiera. Ya existe:
**1.1.0**, y contiene el host-integration fix que motivó el hash (en 1.1.0 `initLoginKoin` carga
los módulos en un contenedor Koin ya arrancado en vez de petar).

## Doctrina violada

Ninguna de detección. Es higiene de build: dependencia no reproducible + la trampa conocida del
grupo de JitPack (abajo).

## ⚠️ El grupo del artefacto (error ya cometido una vez)

```toml
baselogin = { module = "com.github.apptolast.BaseLogin:baselogin", version.ref = "baselogin" }
```

El grupo lleva el **nombre del repo capitalizado** (`…apptolast.BaseLogin`), no solo el usuario.
Con `com.github.apptolast:baselogin` JitPack **resuelve igual** pero devuelve un POM agregado sin
metadata de variantes y un metadata jar de KMP vacío: no falla con un 404, falla mucho más tarde
con símbolos sin resolver. El módulo del proyecto ya llevaba el grupo correcto; se documenta en el
catálogo para que no se "simplifique" en el futuro.

Verificado en el `.module` de 1.1.0 (JitPack):
`component.group = com.github.apptolast`, `module = BaseLogin`, con variantes `available-at` →
`custom-login-android` / `custom-login-iosarm64` / `custom-login-iossimulatorarm64`.

## Diseño / cambios

1. **`gradle/libs.versions.toml`** — `baselogin = "35a5e15fc4"` → `"1.1.0"`, y el comentario pasa
   de TODO a la nota permanente sobre el grupo.
2. **`iosApp/iosApp.xcodeproj/project.pbxproj`** — el host debe aportar las dependencias nativas
   que exige el cinterop de la librería (contrato del `CLAUDE.md` del repo BaseLogin):

   | Paquete SPM | Productos | Regla |
   |---|---|---|
   | `firebase/firebase-ios-sdk` | `FirebaseCore`, `FirebaseAuth`, `FirebaseFirestore`, `FirebaseAppCheck` | `upToNextMinor` desde **11.8.0** |
   | `google/GoogleSignIn-iOS` | `GoogleSignIn` | `upToNextMajor` desde **9.0.0** |

   - Antes: un solo paquete (firebase-ios-sdk, `upToNextMajor` desde 11.0.0) con `FirebaseAuth` +
     `FirebaseFirestore`.
   - El pin 11.8.x **no es arbitrario**: el cinterop de GitLive está compilado contra esa versión
     del SDK de Firebase para iOS; desviarse da errores de enlazado. Por eso `upToNextMinor`.
   - `FirebaseAuth` se enlaza aunque ningún fichero Swift lo importe: el klib de cinterop de
     `dev.gitlive:firebase-auth` exige `-framework FirebaseAuth` al enlazar. Lo mismo aplica a
     `FirebaseFirestore` (que ya estaba) por `dev.gitlive:firebase-firestore`.

## Lo que NO se ha tocado (y por qué)

- **`CustomLoginAndroid`** (punto de entrada nuevo en 1.1.0: `initialize` / `setApplicationContext`
  / `attachActivity` / `detachActivity`). Es una fachada fina sobre `appContext` + `ActivityHolder`,
  exactamente lo que Paparcar ya hace y en el sitio correcto (`PaparcarApp.onCreate`,
  `MainActivity.onCreate/onDestroy`, `DevMainActivity` igual). Migrar sería churn: los *lectores*
  (`BatteryOptimizationRequest` lee `appContext`, `LocaleApplier.android.kt` lee
  `ActivityHolder.getCurrentActivity()`) no tienen equivalente en la fachada, así que quedarían en
  la API vieja de todos modos.
- **El bypass de Koin en `MockPaparcarApp`.** No es un workaround de "arrancar Koin antes que la
  librería" (ese ya sobra en 1.1.0): existe para **no** cargar el DataModule de la librería, que
  depende de Firebase, y así entrar sin OAuth en el flavor mock. Sigue siendo necesario. 1.1.0
  abre una vía más limpia — `loginModules(config, authProvider = <fake>)` — pero exige implementar
  un `AuthProvider` falso; queda como follow-up.
- **Handlers Swift de login social en iOS.** Hoy iOS no tiene login social cableado en absoluto
  (`MainViewController.kt` llama a `initLoginKoin(config = LoginLibraryConfig())`, sin
  `googleSignInConfig` ni `iosClientId`, y no hay `GoogleService-Info.plist` en el repo — está
  gitignored). Escribir los handlers sin config ni posibilidad de probarlos sería inventar. Ver
  follow-up.

### ⚠️ Asimetría `.shared` vs `.companion` (para el follow-up de iOS)

Comprobado **contra el código fuente del tag 1.1.0**, no contra el README:

| Provider | Declaración Kotlin | Desde Swift |
|---|---|---|
| `AppleSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `GitHubSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `MicrosoftSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `TwitterSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `FacebookSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `GoogleSignInProviderIOS` | `class` + `companion object` | `.companion.signInHandler` |

El README y los KDoc de la propia librería escriben `.companion` para los seis — **están mal para
los cinco `object`**; Kotlin/Native expone un `object` como `.shared`. La fuente manda.

Formato del token que devuelve el handler (separadores literales, compartidos con Kotlin):

```
Google:  idToken|||accessToken|||<accessToken>
Apple:   idToken|||rawNonce|||<rawNonce>
Apple+:  idToken|||rawNonce|||<rawNonce>|||displayName|||<nombre>
```

`displayName` se **añade**, nunca sustituye — Apple manda el nombre completo solo en la PRIMERA
autorización de cada usuario; si no se persiste ahí, se pierde para siempre.

## Criterio de éxito

- Android compila (prod + mock) y la suite pasa entera.
- `custom-login-android:1.1.0` está de verdad en el compile classpath de Android (no basta con que
  Gradle "resuelva": con el grupo mal puesto resuelve y luego no compila).
- iOS: proyecto Xcode con los paquetes SPM y el pin correctos.

## Verificación

| Qué | Resultado |
|---|---|
| `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid` | ✅ BUILD SUCCESSFUL |
| `testProdDebugUnitTest` + `testMockDebugUnitTest` | ✅ 2226 tests (1113 × 2 flavors), 0 fallos |
| `assembleProdDebug` + `assembleMockDebug` | ✅ BUILD SUCCESSFUL |
| `dependencyInsight --configuration prodDebugCompileClasspath` | ✅ `com.github.apptolast.BaseLogin:custom-login-android:1.1.0` presente vía `baselogin:1.1.0` |
| Exclusión `custom-login-iosarm64` en configuraciones hoja | ❌ **no hizo falta** — Gradle resolvió el classpath de Android sin tocar las variantes de iOS |
| Compilar el framework de iOS / arrancar en simulador / login social real | ❌ **NO verificado** — host Windows (Kotlin/Native para targets Apple exige macOS) y sin `GoogleService-Info.plist` |
| Que Xcode acepte el `.pbxproj` editado a mano | ❌ **NO verificado** — solo chequeo estructural (llaves/paréntesis balanceados, todos los UUID referenciados están definidos, sin colisiones) |

## Consumidores auditados

Todos los sitios que importan `com.apptolast.customlogin.*` (30 ficheros) compilan sin cambios:
`AuthRepository` (repos, use cases, workers, servicios), `domain.model.*` (UserSession, AuthState,
AuthResult, Credentials, AuthError, IdentityProvider, PasswordResetData, PhoneAuthResult,
SignUpData), `presentation.slots.*` + `DefaultAuthContainer` + `SocialLoginButtonsSection`
(PaparcarAuthSlots, previews, galería mock), `presentation.navigation.*` (App.kt),
`presentation.screens.login.LoginViewModel` (MockModule), `platform.ActivityHolder`, `appContext`,
`config.GoogleSignInConfig`, `di.LoginLibraryConfig` + `di.initLoginKoin`. **Cero cambios de
código de app**: 1.1.0 es aditivo sobre lo que Paparcar usaba.

## Follow-ups (fuera de alcance, ticket aparte)

- `IOS-SOCIAL-LOGIN-001` — cablear Google/Apple en iOS: `googleSignInConfig` (webClientId +
  iosClientId) en `MainViewController.kt`, handlers Swift (ojo `.shared` vs `.companion`),
  URL scheme en `Info.plist`, `GoogleService-Info.plist`. Requiere Mac.
- `MOCK-AUTHPROVIDER-001` — sustituir el bypass de `initLoginKoin` en el flavor mock por
  `loginModules(config, authProvider = FakeAuthProvider)`.
</content>
