# IOS-SOCIAL-LOGIN-001 · Login social (Google + Apple) en iOS

**Estado:** 🔵 Abierto, sin código · **bloqueado hasta tener un Mac** — no se puede compilar ni
probar iOS desde Windows. Sin rama: el spec vive aquí, en el backlog, que es donde se lee lo
pendiente (DOCS-BACKLOG-TRUTH-001). Cuando empiece el código, worktree y rama nuevos.
Verificado el 27-08-2026: seguimos consumiendo `baselogin 1.1.0`, así que todo lo de abajo sigue
vigente, incluida la asimetría `.shared` / `.Companion.shared`.

## Problema

En iOS **no hay login social en absoluto**. `MainViewController.kt` arranca la librería con la
config vacía:

```kotlin
initLoginKoin(config = LoginLibraryConfig()) { modules(…) }
```

Los botones sociales los pinta la librería a partir de la config: sin `googleSignInConfig` no
aparece Google, sin `appleSignInConfig` no aparece Apple. Resultado: en iOS solo hay
email/password + magic link, mientras que en Android Google sí está (`PaparcarApp` pasa
`GoogleSignInConfig(webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID)`).

Es una **desparidad de plataforma**, no un bug de la librería: 1.1.0 trae todo lo necesario, solo
falta cablearlo en el host.

## Doctrina violada

Ninguna de detección. Es paridad de plataforma + un requisito de tienda (abajo).

## ⚠️ Requisito de la App Store, no opcional

La guideline 4.8 de Apple obliga a ofrecer **Sign in with Apple** si la app ofrece login social de
terceros. O sea: si en iOS metemos Google, **Apple entra en el mismo ticket**, no en el siguiente.
Por eso el ticket es "Google + Apple", no "Google".

## Señales / datos disponibles

- `dev.gitlive:firebase-auth` ya está en commonMain y el proyecto Xcode ya enlaza los frameworks
  nativos que hacen falta tras DEPS-BASELOGIN-110 (`FirebaseCore`, `FirebaseAuth`,
  `FirebaseFirestore`, `FirebaseAppCheck`, `GoogleSignIn`).
- El flujo de auth en commonMain (`App.kt` → `authRoutesFlow`, `PaparcarAuthSlots`,
  `SocialLoginButtonsSection`) es **compartido**: no hay que tocar UI. Al llegar la config, los
  botones aparecen solos.
- Android ya resuelve el `webClientId` vía `prop("GOOGLE_WEB_CLIENT_ID")` →
  `BuildConfig.GOOGLE_WEB_CLIENT_ID` (local.properties / keystore.properties / env). iOS necesita
  su propia vía: `BuildConfig` no existe en Kotlin/Native.

## Diseño

### 1 · Config (Kotlin, `iosMain`)

`MainViewController.kt` pasa la misma config que Android más el `iosClientId`:

```kotlin
initLoginKoin(
    config = LoginLibraryConfig(
        googleSignInConfig = GoogleSignInConfig(
            webClientId = …,   // el mismo que Android
            iosClientId = …,   // CLIENT_ID del GoogleService-Info.plist
        ),
        appleSignInConfig = AppleSignInConfig(),   // scopes por defecto: email, name
    ),
) { modules(…) }
```

`GoogleSignInProviderIOS.signIn()` usa `config.iosClientId ?: config.webClientId` — si solo
pasamos el web, GoogleSignIn iOS falla; el iOS client id es obligatorio de facto.

**De dónde salen los ids, sin hardcodearlos** (decisión a tomar):

- **(a) Leerlos del `GoogleService-Info.plist`** en runtime con `NSBundle` (`CLIENT_ID`). Es el
  fichero que ya hace falta para `FirebaseApp.configure()`, sigue gitignored, y no duplica el
  secreto en el código. **Preferida.**
- (b) Inyectarlos desde Swift al llamar a `MainViewController(webClientId:iosClientId:)`.
- (c) Generar un objeto Kotlin desde Gradle leyendo `local.properties`. Es lo más parecido a
  Android pero mete generación de código para dos strings.

### 2 · Handlers Swift (`iosApp/iosApp/iOSApp.swift`)

Se registran en el `AppDelegate`, **antes de que se pinte el primer Composable**, y hay que añadir
el callback de URL de Google:

```swift
func application(_ app: UIApplication, open url: URL,
                 options: [UIApplication.OpenURLOptionsKey: Any] = [:]) -> Bool {
    return GIDSignIn.sharedInstance.handle(url)
}
```

#### ⚠️ Asimetría `.shared` vs `.companion` (comprobado contra el fuente del tag 1.1.0)

| Provider | Declaración Kotlin | Desde Swift |
|---|---|---|
| `AppleSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `GitHubSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `MicrosoftSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `TwitterSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `FacebookSignInProviderIOS` | `object` | `.shared.signInHandler` |
| `GoogleSignInProviderIOS` | `class` + `companion object` | `.companion.signInHandler` (≡ `.Companion.shared`) |

Kotlin/Native expone un `object` como `.shared` y un companion como `.companion` /
`.Companion.shared`. **En 1.1.0 —lo que consumimos— los KDoc de los cinco `object` y el README de
la librería escriben `.companion` para todos, y están mal**; la app demo del repo sí usa la forma
buena (`GoogleSignInProviderIOS.Companion.shared.signInHandler`). Si algo no compila en Swift,
mirar aquí antes que a ningún otro sitio.

> Nota (14-08-2026): en el repo de BaseLogin esa documentación **ya está corregida** por el spec
> `003-apple-signin-production`, pero vive en una rama local sin mergear ni publicar, así que el
> tag 1.1.0 sigue trayendo los KDoc malos. Cuando salga la release siguiente, esta tabla seguirá
> siendo válida (describe el comportamiento real de Kotlin/Native, no la doc). Lo que sigue vivo
> como deuda de la librería es la asimetría en sí: `GoogleSignInProviderIOS` es `class` y los
> otros cinco son `object`.

Para este ticket solo tocan dos: **Google** (`.companion`) y **Apple** (`.shared`).

#### Formato del token que devuelve el handler

Separadores literales, compartidos con Kotlin — cambiarlos rompe la integración:

```
Google:  idToken|||accessToken|||<accessToken>
Apple:   idToken|||rawNonce|||<rawNonce>
Apple+:  idToken|||rawNonce|||<rawNonce>|||displayName|||<nombre>
```

El segmento `displayName` **se añade, nunca sustituye**. Importa porque **Apple manda el nombre
completo solo en la PRIMERA autorización de cada usuario**: si no se persiste ahí, se pierde para
siempre y el perfil se queda sin nombre. Ojo al probar: para repetir la primera vez hay que
revocar la app en *Ajustes → Apple ID → Iniciar sesión con Apple*.

Apple además exige **nonce**: se manda `sha256(rawNonce)` en la request y se devuelve el
`rawNonce` en claro en el token, para que Firebase pueda validarlo.

### 3 · Configuración nativa / consola

- `GoogleService-Info.plist` en `iosApp/` (gitignored, hay que bajarlo de Firebase).
- URL scheme en `Info.plist` = `REVERSED_CLIENT_ID` del plist (sin esto GoogleSignIn no vuelve a
  la app).
- Capability **Sign in with Apple** en el target + proveedor Apple habilitado en la consola de
  Firebase (necesita cuenta de desarrollador de Apple: Service ID, key y team id).
- Ambos proveedores habilitados en Firebase Auth.

## Criterio de éxito

- En un iPhone/simulador: los botones de Google y Apple aparecen en la pantalla de login, y ambos
  completan sesión → `AuthState.Authenticated` → el `Splash` enruta igual que en Android.
- El `UserSession` trae `displayName` en el **primer** login con Apple.
- La sesión sobrevive a matar y reabrir la app.
- Android no se toca y sigue verde (la config de iOS vive en `iosMain`).

## Consumidores auditados

No hay invariante que barrer: el cambio es aditivo y aislado en `iosMain` + `iosApp/`. Los
consumidores de `AuthRepository`/`UserSession` (30 ficheros) ya tratan la sesión con
independencia del proveedor — `GetOrCreateUserProfileUseCase` construye el `UserProfile` desde
`UserSession`, así que un `displayName` nulo se propagaría a un perfil sin nombre. Es exactamente
el motivo del segmento `displayName` de Apple.

## ⛔ Bloqueo real

**Nada de esto se puede verificar desde Windows.** Compilar el framework de Kotlin/Native para
targets Apple exige macOS, igual que Xcode y el simulador. Se puede *escribir* el Kotlin y el
Swift a ciegas, pero no compilan ni se prueban aquí, y el login social es justo donde se ve si el
enlazado nativo está bien.

Además faltan credenciales que no están en el repo: `GoogleService-Info.plist`, el iOS client id,
y la parte de Apple (Service ID + key), que requiere cuenta de desarrollador de pago.

**Decisión pendiente del user:** escribir el código a ciegas ahora (queda listo para la primera
sesión en Mac, pero sin compilar ni una vez) o dejar el ticket abierto y documentado hasta
tener Mac delante.

## Follow-ups relacionados

- `MOCK-AUTHPROVIDER-001` — sustituir el bypass de `initLoginKoin` del flavor mock por
  `loginModules(config, authProvider = FakeAuthProvider)`.
</content>
