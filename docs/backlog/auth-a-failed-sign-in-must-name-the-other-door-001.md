# AUTH-A-FAILED-SIGN-IN-MUST-NAME-THE-OTHER-DOOR-001 · un login que falla tiene que nombrar la otra puerta

**Estado:** ✅ **Done — las dos mitades están en la app.** La A entró con el ticket; la B llega vía
**BaseLogin 3.0.2** (la 3.0.1 no llegó a publicarse: su CI murió por un paquete del SDK retirado),
ya liberada en Central y consumida desde `libs.versions.toml`. ⏳ Lo único pendiente es **verlo en
mano** y su captura.

## Problema

Le pasó al user el **30-09-2026** con `paparcar.review@gmail.com`, la cuenta de demo de los
revisores, y le costó la tarde:

1. La cuenta se había quedado **sin proveedor `password`** (entrar con Google en un email cuya cuenta
   de contraseña no estaba verificada borra la credencial; ver *Causa de fondo*).
2. Intentó entrar con email+contraseña → la app dijo **"Invalid email or password"**. Conclusión
   razonable y equivocada: *"me he equivocado de contraseña"*. Reintentó.
3. Pidió **"¿Olvidaste tu contraseña?"** → la app contestó **"Check Your Inbox — We've sent a
   password reset link to your email address."**
4. **No llegó nada, nunca**, y la app nunca dijo que no había enviado nada.

Los dos mensajes son falsos, y los dos se pueden falsificar sin device:

| Medición (30-09-2026, endpoints reales de `pap-26`) | Resultado |
|---|---|
| `accounts:signInWithPassword` con un email que **no existe** | `INVALID_LOGIN_CREDENTIALS` |
| …con un email que **existe solo con Google** (`pit240km@gmail.com`) | `INVALID_LOGIN_CREDENTIALS` ← **idéntico** |
| `accounts:createAuthUri` (= `fetchSignInMethodsForEmail`) con los dos, y con uno inexistente | `registered`, `signinMethods` y `allProviders` **vacíos en los tres casos** |
| `admin/v2/projects/pap-26/config` | `emailPrivacyConfig.enableImprovedEmailPrivacy: true` |

O sea: **la protección de enumeración de emails está activada** (y debe seguir estándolo), y con
ella la app *no puede* distinguir "contraseña mal" de "esta cuenta es de Google", ni preguntarlo.
Y `sendPasswordResetEmail` sobre una cuenta sin credencial de contraseña **responde OK y no envía
nada**: admitirlo filtraría si la cuenta existe.

⛔ **Como la lógica no puede saberlo, esto NO se arregla con lógica: se arregla con COPY.** No hay
señal que consultar. La única salida honesta es que los dos mensajes dejen de afirmar lo que no
saben y nombren la otra puerta.

## Doctrina violada

- **"No copy al usuario con mecánica interna ni jerga inventada — causa + consecuencia + remedio"**
  (CLAUDE.md § *Cosas que NO hacer*). Los dos mensajes dan una **causa falsa** ("tu contraseña está
  mal") y el segundo promete un **remedio que no ocurre**.
- **"Cero catch silenciosos"** (CLAUDE.md § *Error handling*) — aquí es peor que un catch silencioso:
  es un **éxito silencioso**. La operación dice que salió bien y no hizo nada.

## Señales / datos disponibles

**Ninguna, y eso es el hallazgo.** No hay nada que consultar: ni `fetchSignInMethodsForEmail`, ni un
código de error distinto, ni un campo en el perfil. Cualquier diseño que dependa de "detectar que
esta cuenta es de Google" es inviable mientras `enableImprovedEmailPrivacy` esté activada — y
apagarla para ganar un mensaje sería cambiar una protección real por un texto.

## Diseño

Dos mitades, y **viven en repos distintos**. Medido sobre el BaseLogin local
(`../BaseLogin`, `a39e435`, 13-09-2026):

### Mitad A · "Olvidé mi contraseña" → **cabe en Paparcar hoy, sin tocar la librería**

`ForgotPasswordScreenSlots` (`presentation/slots/AuthSlots.kt:212`) ya expone los dos slots que
hacen falta:
- `description` → hoy `DefaultForgotPasswordDescription`, con
  `forgot_password_screen_description` = *"Enter your email address and we'll send you a link to
  reset your password."* (promete).
- `successContent` → hoy `DefaultSuccessContent`, con `forgot_password_screen_success_title` =
  *"Check Your Inbox"* y `..._success_description` = *"We've sent a password reset link to your
  email address."* (afirma un envío que puede no haber ocurrido).

Se sustituyen en `ui/auth/PaparcarAuthSlots.kt`, junto a los slots que ya rellenamos, con copy que
no promete y que nombra la otra puerta. Sentido, no literal:
- descripción: *si hay una cuenta con contraseña para ese correo, le llega un enlace*;
- éxito: *comprueba tu correo. Si no llega nada, puede que te registraras con Google — entra con
  Google.*

⛔ Los strings son NUESTROS (`composeResources/values/strings.xml`), **no** se sobreescriben los de
la librería: con Compose Resources cada módulo tiene su propio `Res` generado
(`login.baselogin.generated.resources`) y **no hay merge de recursos** como en `android:strings`.
→ keys nuevas en **los 9 locales**, y ⚠️ apóstrofos **crudos**, nunca escapados:
[COPY-APOSTROPHE-IS-NOT-ESCAPED-001], que es exactamente el tipo de copy que lo pisa.

### Mitad B · el error del login → **NO cabe en Paparcar, hace falta BaseLogin**

`LoginScreen.kt:57-83` pinta el error en un **`SnackbarHostState` interno**, con el texto ya resuelto
en `getString(effect.error.toStringRes())`. `LoginScreenSlots` (`AuthSlots.kt:44`) **no tiene slot
para ese mensaje**: los `error: String?` de `emailField`/`passwordField` son errores de validación
del campo, no el de credenciales. El texto sale de `AuthErrorExt.kt:34`:
`AuthError.InvalidCredentials` → `auth_error_invalid_credentials` = *"Invalid email or password"*.

🔑 **Y la frase correcta YA EXISTE en la librería, en el sitio equivocado**:
`auth_error_account_exists_with_different_credential` = *"An account already exists with this email.
Sign in with the method you used before."* Ese `AuthError` solo se emite en el flujo de *linking*,
nunca cuando falla un login por contraseña — que es justo cuando el usuario necesita leerlo.

Dos vías, **a decidir con el user** (⛔ BaseLogin no se toca desde Paparcar):
- **B1 · cambiar el copy de `auth_error_invalid_credentials`** en la librería para que nombre la otra
  puerta. Una línea, arregla a todos sus consumidores, y no puede decir de más porque tampoco afirma
  que la cuenta exista.
- **B2 · añadir un slot** para el mensaje de error del login (o exponer el `SnackbarHostState`), y
  que cada app ponga su copy. Más flexible, más superficie de API, y obliga a versión nueva +
  publicación en Maven Central.

Recomendación: **B1**, y si algún día hace falta más control, B2 encima. B1 es el invariante en UN
sitio; B2 delega la misma frase en cada consumidor.

## Causa de fondo (fuera de alcance, ya tiene ticket)

Que la cuenta pierda la contraseña es **otro** problema, y su ticket es
`auth-email-verification-as-an-opt-in-setting-001.md` — el registro por email/contraseña **no exige
verificar el correo, a propósito** desde 1.0, así que toda cuenta de contraseña nace con
`emailVerified: false` y puede perder su credencial el día que su dueño entre con Google.
⚠️ Ese ticket está diseñado como **ajuste opt-in**, y como opt-in **no cierra el agujero**: hace
falta decidir si el `sendEmailVerification()` se manda siempre. Y mandarlo no basta — `emailVerified`
solo pasa a `true` cuando el usuario **pulsa** el enlace.
**Este ticket no espera a ese**: son independientes. Incluso con la verificación resuelta, un usuario
que se registró con Google y prueba con contraseña seguirá leyendo los dos mensajes falsos.

## Criterio de éxito

1. Pedir reset de un correo **sin credencial de contraseña** (p. ej. `pit240km@gmail.com`) deja al
   usuario sabiendo que puede no llegarle nada y que Google es una puerta. Hoy le dice "Check Your
   Inbox".
2. Fallar el login de ese mismo correo produce un mensaje que **nombra la otra puerta**.
3. Ningún mensaje afirma que la cuenta existe ni con qué proveedor → la protección de enumeración
   sigue intacta. `enableImprovedEmailPrivacy` **no se toca**.
4. Las keys nuevas, en los 9 locales (`LocaleParityGuardrailTest` verde) y con el apóstrofo crudo
   (`ComposeResourcesEscapeGuardrailTest` verde, con `--rerun-tasks`: el copy no cambia bytecode).
5. Los dos estados (formulario y éxito) **visibles sin provocar un reset real**.
   ⛔ **No van a la galería de estados del Dev Catalog, y es deliberado**: la regla de la galería
   pide espejar un `XxxContent(state=…)` nuestro, y esta pantalla es de la **librería** — no tiene
   uno. Sus dos estados no dependen de ningún ViewModel: son los slots. Su espejo real es
   `PaparcarAuthSlotsPreviews.kt`, que ya existía para el login y ahora cubre también este, en claro
   y oscuro. Meterlos en la galería habría exigido hacer públicos dos composables privados de
   `:shared` (la galería vive en `:app`, que no ve su `internal`) — ensuciar la API del módulo para
   ver un texto no lo vale.

## Estado de la implementación (30-09-2026, sin commitear)

### Hecho — mitad A, en este worktree
- **3 keys nuevas × 9 locales = 27**, insertadas tras `auth_forgot_password`:
  `auth_forgot_password_description`, `auth_forgot_password_sent_title`,
  `auth_forgot_password_sent_body`. Apostrofos **crudos** (`we'll`, `quell'account`, `n'arrive`).
  Registro calcado del que ya usa cada locale — **todos tutean**, PT incluido (77 `teu` frente a 7
  `o seu`).
- **`PaparcarAuthSlots.kt`**: nace `forgotPassword = ForgotPasswordScreenSlots(description,
  successContent)` con `CompactForgotPasswordDescription` y `CompactForgotPasswordSent`.
  ⛔ Solo esos dos slots: `header`, `emailField` y `submitButton` siguen siendo los de la librería
  porque **no son el encargo** — quedan como observación de abajo.
  ⛔ La maqueta del éxito **se reutiliza**: `DefaultSuccessContent` ya recibe `title`/`description`,
  así que lo nuestro es el texto, no un segundo dibujo del mismo estado.
- **`PaparcarAuthSlotsPreviews.kt`**: +4 previews (formulario y enviado, claro y oscuro) y su
  `MockForgotPasswordScreen`.

### Hecho — mitad B1, en el repo de BaseLogin (`../BaseLogin`, rama `main`, ÁRBOL LIMPIO al empezar)
- `auth_error_invalid_credentials` en **los 9 locales**: conserva la primera frase y añade *“Si
  creaste la cuenta con Google, entra con Google”*. Condicional: no afirma que la cuenta exista.
- Registro **medido en los strings de la propia librería**, que no es el de Paparcar: tutea en ocho,
  pero su **PT usa forma de cortesía** (`o seu`) — ahí va *“Se criou a conta com o Google, entre com o
  Google”*.

### La mitad B1, publicada — y el git flow que hubo que arreglar antes

`8cc40b8` se había commiteado **directo en `main`**, y `main` en BaseLogin es la rama de **release**.
Además `develop` iba **2 commits por detrás**: nunca recibió el merge-back del 3.0.0. Rehecho como
git flow manda, y siguiendo el estilo de merges que ya usa ese repo:

```
develop  ← ff a39e435                     (el merge-back del 3.0.0 que faltaba)
feature/auth-error-names-the-other-door → develop   (--no-ff)
main     ← reset a a39e435                (fuera el commit directo; main vuelve al release)
release/3.0.1 → main + tag 3.0.1 → merge-back a develop
```
Bump en `baselogin/build.gradle.kts` y en las **dos** coordenadas de instalación del README; las
secciones *“Migrating to 3.0.0”* son historia y no se tocan. Verificado antes de publicar: **286
tests, 0 fallos** (testigos: `StringResourcesLocaleParityTest` 5, `AuthErrorExtTest` 6) y
`iosArm64` + `iosSimulatorArm64` + `assembleRelease` compilando.

⛔ **No se publica a mano desde aquí, y no por falta de credenciales.** El intento local murió con
`Invalid token` (el par de `~/.gradle/gradle.properties` da 401 contra la API del Portal), pero la
vía correcta es otra: `.github/workflows/ci.yml` tiene un job `release` que dispara con los tags
`[0-9]+.[0-9]+.[0-9]+`, corre en **macos-latest** y usa los cuatro secrets del repo. Su propio
comentario explica por qué macOS aunque no haga falta técnicamente: la cross-compilación de klibs
*“still being stabilised”* y una versión en Central **no se puede borrar ni sobrescribir jamás** —
para el paso irreversible, el host sin salvedades. **Publicar = pushear el tag.**

### ✅ Cerrado: la mitad B ya la consume la app

1. **BaseLogin 3.0.2 liberada en Central** y aprobada por el user. El tag **3.0.1 quedó huérfano**:
   su run murió porque `android-actions/setup-android@v3` pide por defecto el paquete `tools`, que
   Google **retiró** del SDK — el job se cae en el *setup*, y como `release` depende de él queda
   **`skipped`**: el tag se pushea, el run sale rojo y **no publica nada sin decirlo**. Arreglado en
   la librería con `with: packages: platform-tools`.
2. **Bump a `3.0.2`** en `gradle/libs.versions.toml` **y en `CLAUDE.md`** (los `docs/backlog/` que
   citan 3.0.0 son registros fechados y no se tocan).
3. ⏳ Queda **verlo en mano** y la captura [PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001].

✅ **Y se comprobó que el copy viaja de verdad, no que «la versión subió»**: descomprimido el
`baselogin-release.aar` 3.0.2 que Gradle resolvió y decodificado su
`values*/strings.commonMain.cvr` (formato `string|clave|base64`, no XML), el texto publicado es:

| locale | `auth_error_invalid_credentials` en el artefacto |
|---|---|
| `values` | Invalid email or password. If you created the account with Google, sign in with Google. |
| `values-es` | Correo electrónico o contraseña incorrectos. Si creaste la cuenta con Google, entra con Google. |
| `values-it` | Indirizzo e-mail o password errati. Se hai creato l'account con Google, entra con Google. |
| `values-pt` | E-mail ou senha incorretos. Se criou a conta com o Google, entre com o Google. |
| `values-ro` | Adresă de e-mail sau parolă incorectă. Dacă ai creat contul cu Google, conectează-te cu Google. |

El apóstrofo de IT viaja **crudo** (`l'account`) y PT conserva la **forma de cortesía** de la
librería, que no es la de Paparcar.

### Verificación
| Qué | Resultado |
|---|---|
| `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` | ✅ `BUILD SUCCESSFUL`. Prueba además que `DefaultSuccessContent(title, description)` **existe en el 3.0.0 publicado**, no solo en el repo local |
| `testAll --rerun-tasks` | ✅ **3839 tests, 0 fallos** (382 ficheros de resultados) |
| Guards con **testigo**, no solo verde | `LocaleParityGuardrailTest` 5 · `ComposeResourcesEscapeGuardrailTest` 3 · `TypographyGuardrailTest` 5 · `ColorGuardrailTest` 8 — todos ejecutados, 0 fallos |
| `:shared:compileKotlinIosSimulatorArm64` | ✅ compila con el cambio dentro (los slots son `commonMain`, así que a iOS le llega) |
| Resolución de la dependencia 3.0.2 | ✅ `android` **y** `iossimulatorarm64` resuelven `io.github.apptolast:baselogin:3.0.2` (`:shared:dependencies`, los dos targets) |
| Suite con 3.0.2 | ✅ **3841 tests, 0 fallos** |
| En device / en mano | ⏳ **sin ver**. Los previews son el camino corto; el flujo real exige pedir un reset |

### Observación fuera de alcance
La pantalla de forgot password sigue con el **header y los campos de la librería**, mientras login y
registro llevan los nuestros (`PaparcarAuthHeader`, `CompactEmailField`, `CompactSubmitButton`). Se
nota al llegar desde el login. No se toca aquí porque este ticket es sobre lo que la app **dice**,
no sobre cómo se ve — pero es un hueco real y baratito: son tres slots más del mismo objeto que
este ticket ya rellena.

## Consumidores auditados

Sitios que asumen "reset pedido = email enviado" o "login fallido = contraseña mal":

| Sitio | Estado |
|---|---|
| `BaseLogin ForgotPasswordScreen` → `successContent` | **cubierto** por la mitad A (slot) |
| `BaseLogin ForgotPasswordScreen` → `description` | **cubierto** por la mitad A (slot) |
| `BaseLogin LoginScreen` → snackbar de `AuthError.InvalidCredentials` | **abierto** → mitad B, decisión del user |
| `BaseLogin MagicLinkScreen` → `magic_link_screen_success_description` | **exento**: el magic link sí se envía siempre (crea la cuenta si no existe) |
| `shared/.../ui/auth/PaparcarAuthSlots.kt` | es donde se aplica la mitad A |
| `strings.xml` × 9 locales | pendiente de las keys nuevas |
| Dev Catalog / `StateGalleryScreen.kt` | pendiente (criterio 5) |
| `enableImprovedEmailPrivacy` en Identity Platform | **exento a propósito**: no se toca, es la protección que causa el silencio y vale más que el mensaje |
