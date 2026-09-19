# iOS · Login con Apple y Google — guía de configuración

Ticket: [IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001](../backlog/ios-apple-sign-in-on-baselogin-3-001.md).
Se avanza **un paso cada vez**; cada paso se marca ✅ cuando está confirmado.

| Dato                                   | Valor                                                                            |
|----------------------------------------|----------------------------------------------------------------------------------|
| Bundle ID                              | `com.rndeveloper.paparcar`                                                       |
| Team ID                                | `3NXH5U7C5A` (en `DEVELOPMENT_TEAM` del pbxproj y `TEAM_ID` del xcconfig)        |
| Apple ID de la app (App Store Connect) | `6811710268`                                                                     |
| Proyecto Firebase                      | `pap-26`                                                                         |
| App iOS en Firebase                    | `1:431876996213:ios:a4e2fb61a75fbc3c7f4c3c`                                      |
| Google iOS client id                   | `431876996213-4g1pb9…apps.googleusercontent.com` (en `GoogleService-Info.plist`) |

## Paso 0 · Código (lo hace Claude) — ✅

BaseLogin 3.0.0, config de login con Apple + Google en iOS, coordinadores Swift, entitlements y URL
scheme. Detalle en el ticket.

## Paso 1 · Apple Developer: App ID con Sign in with Apple — ✅ (14-09-2026)

1. <https://developer.apple.com/account> → confirmar que la membresía (99 €/año) está activa.
   Apuntar el **Team ID** (Membership details, 10 caracteres).
2. **Certificates, Identifiers & Profiles → Identifiers**. Buscar `com.rndeveloper.paparcar`.
    - Si no existe: **+** → *App IDs* → *App* → Description `Paparcar`, Bundle ID **Explicit**
      `com.rndeveloper.paparcar`.
3. En la lista de *Capabilities* marcar **Sign in with Apple** → *Edit* → **Enable as a primary App
   ID** → *Save*.
4. No crear Services ID: solo hace falta para Apple en Android/web, que no montamos.
   ⚠️ La **Key** sí hace falta (corregido 14-09): la pide Firebase para revocar el token al borrar
   la cuenta. Se crea en el paso 3.

**Confirmar:** Team ID y que el App ID muestra *Sign in with Apple* activado.

## Paso 2 · Xcode: firma y capability en un iPhone real — 🟡 (14-09: arranca en el **simulador
**, salen los dos botones; sin iPhone real → TestFlight con fastlane, pendiente)

> 🔴 **19-09-2026 — aquí estaba el fallo que reportaron los testers.** El botón de Apple en iPhone
> físico daba un parpadeo de milisegundos y nada más. Causa: `iosApp.entitlements` **no tenía**
> `com.apple.developer.applesignin`. Este paso lo daba por puesto («sale del `iosApp.entitlements`»)
> y el diseño del ticket también, pero `git log --follow` sobre el fichero devuelve un único commit
> — el de push (`47b54044`, 16-09), que lo creó con `aps-environment` y nada más. `CODE_SIGN_ENTITLEMENTS`
> sí estaba en Debug y Release; lo que faltaba era la clave.
>
> Sin ella `performRequests()` falla en el acto con `ASAuthorizationError.unknown` (1000): la hoja
> del sistema nunca se presenta y sólo se ve encenderse y apagarse el estado de carga del botón.
> **Ése es el parpadeo.** Arreglado en la rama del ticket.

1. Xcode → **Settings → Accounts**: la Apple ID del equipo `3NXH5U7C5A` tiene que estar añadida.
2. Abrir `iosApp/iosApp.xcodeproj` desde el repo principal (`PaparcarKMP`, rama
   `feature/IOS-APPLE-SIGN-IN-ON-BASELOGIN-3-001`).
3. Target **iosApp → Signing & Capabilities**:
    - *Automatically manage signing* ✔, Team ya preseleccionado, Bundle Identifier `com.rndeveloper.paparcar`.
    - Debe aparecer **Sign in with Apple** como capability (sale del `iosApp.entitlements`). Si no
      sale: **+ Capability → Sign in with Apple**, y avisar — significa que Xcode no leyó el fichero.
    - Sin avisos rojos de *Provisioning profile* (el perfil lo crea Xcode al conectar el iPhone).
4. Conectar el iPhone por cable, elegirlo como destino y **Run** (⌘R).
    - Primera vez en el móvil: *Ajustes → General → VPN y gestión de dispositivos* → confiar en el
      desarrollador; y *Ajustes → Privacidad y seguridad → Modo desarrollador* activado.
5. Aún **no** probar los botones: falta activar Apple en Firebase (paso 3).

**Confirmar:** que la app arranca en el iPhone, se ve la pantalla de login con los botones de
Google y Apple, y cualquier aviso que salga en *Signing & Capabilities*.

### ⛔ 2b · Tras tocar el entitlement: regenerar el perfil de match

**Un perfil de aprovisionamiento no puede firmar un entitlement que no lleva dentro**, así que
añadir la capability no basta: hay que volver a emitir el perfil. Y aquí no falla el botón — falla
la **firma**, con un *"Provisioning profile … doesn't include the com.apple.developer.applesignin
entitlement"*.

- **Release / TestFlight** firma manual con `match AppStore com.rndeveloper.paparcar`
  (`PROVISIONING_PROFILE_SPECIFIER` del pbxproj), así que el perfil lo tiene que renovar match:

  ```bash
  cd iosApp && bundle exec fastlane ios bootstrap_match
  ```

  Es exactamente para lo que existe esa lane — su propia descripción dice *«Once (or **after
  changing App ID capabilities**)»*. `force` sólo renueva perfiles; nunca recrea el certificado de
  distribución compartido.
- **Debug por cable** va con *Automatically manage signing*: Xcode regenera el perfil de desarrollo
  solo al darle a Run. Si sale un aviso rojo, *Signing & Capabilities → Try Again*.
- **Orden que importa**: la capability tiene que estar activa en el App ID (paso 1) **antes** de
  emitir el perfil. Un perfil emitido antes de activarla no la lleva, aunque el fichero local sí.

## Paso 3 · Apple Key (.p8) + proveedores en Firebase — ✅ (17-09-2026, lo montó el user)

Por qué la key: `AppleSignInCoordinator` revoca con `Auth.auth().revokeToken(withAuthorizationCode:)`
(guideline 5.1.1(v)). Firebase solo puede llamar al endpoint de revocación de Apple si en la
*OAuth code flow configuration* del proveedor tiene Team ID + Key ID + clave privada. Sin ella el
login funciona, pero **borrar la cuenta falla** — y es justo lo que mira App Review.

### 3a · Crear la key en Apple Developer

1. **Certificates, Identifiers & Profiles → Keys → +**.
2. Key Name `Paparcar Sign in with Apple`, marcar **Sign in with Apple** → *Configure* → Primary App
   ID `com.rndeveloper.paparcar` → *Save* → *Continue* → *Register*.
3. **Download** el `.p8` (⚠️ solo se descarga UNA vez) y apuntar el **Key ID** (10 caracteres).
   El `.p8` NO va al repo: guardarlo en el gestor de secretos del equipo.

### 3b · Firebase console (`pap-26`) → Authentication → Sign-in method

1. **Apple** → *Enable*.
    - *Services ID*: vacío (solo para web/Android).
    - *OAuth code flow configuration*: Apple Team ID `3NXH5U7C5A`, Key ID del 3a, y pegar el
      contenido completo del `.p8` (incluidas las líneas `BEGIN/END PRIVATE KEY`).
    - *Save*.
2. **Google** → comprobar que está *Enabled* (ya lo usa Android). No hace falta tocar nada: el
   client iOS lo creó Firebase al registrar la app iOS (`IS_SIGNIN_ENABLED = true` en el plist).

**Confirmar:** Apple y Google en *Enabled* y que la configuración de OAuth code flow se guardó.

## Paso 4 · Prueba del login — 🟡 Google ✅ en simulador (el fallo *«Couldn't load your
account»* era la BD vieja del simulador, no el login) · **Apple: falló en iPhone real (19-09) por el
entitlement, ver paso 2; pendiente de re-probar con un build firmado con el perfil nuevo**

Con la app ya instalada del paso 2 (no hace falta recompilar):

1. **Google** → elegir cuenta → llega a registro de vehículo / Home.
2. Cerrar sesión (Ajustes) → **Google** otra vez → debe dejar elegir otra cuenta.
3. **Apple** → Face ID → primera vez: compartir nombre → comprobar `displayName` en el perfil.
4. Borrar la cuenta de Apple desde Ajustes → en el iPhone, *Ajustes → [tu nombre] → Iniciar sesión
   y seguridad → Iniciar sesión con Apple*: Paparcar ya no aparece.

**Confirmar:** cada punto, y si algo falla, el texto exacto del error o el log de la consola de Xcode.

### Cómo sacar la traza cuando el botón «no hace nada»

`AppleSignInCoordinator` escribe **todos** sus fallos con `NSLog` bajo el prefijo `[AppleSignIn]`.
Con el iPhone conectado por cable: **Console.app → el dispositivo → filtrar `AppleSignIn`**, y
reproducir. Lectura:

| Lo que sale | Qué significa |
|---|---|
| `Failed: … (1000)` | entitlement / capability / perfil, o el iPhone sin sesión de iCloud |
| `Cancelled by the user.` | la hoja SÍ se presentó y se cerró: el problema es otro |
| nada en absoluto | el handler no se registró — `AppleSignInCoordinator.shared.register()` corre en `didFinishLaunching`, antes del bootstrap |

Dos requisitos del **dispositivo** que dan el mismo 1000 y no son del proyecto: la sesión de iCloud
iniciada y **2FA activada** en ese Apple ID (Sign in with Apple la exige). Y en *Tiempo de uso*,
*Cambios de cuenta* no puede estar restringido.
