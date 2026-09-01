# MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001 · En el flavor mock, salir del login mata la app

**Estado:** ✅ **Done — verificado en device el 01-09-2026** (Oppo `LNRCMZ8H6HBITWNJ`, hash del APK
comprobado en el móvil). Rama `bugfix/MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001-authvms`.
Descubierto el 29-08-2026 verificando UI-AUTH-HEADER-ALIGNS-WITH-ITS-FIELDS-001 · **solo afecta a
`mock`, producción sana**

## Lo que desbloqueó BaseLogin 2.0.0

⛔ **La causa raíz de abajo dejó de ser cierta**: el `presentationModule` de la librería **ya no es
`internal`**. Medido con `javap` sobre `baselogin-release.aar` de 2.0.0:

```
public static final Module getLoginPresentationModule()   ← público, sin manglar
public static final Module getPresentationModule()
public final class …screens.register.RegisterViewModel(AuthRepository, LoginLibraryConfig)
public final class …screens.forgotpassword.ForgotPasswordViewModel(AuthRepository)
```

Y en el `<clinit>`: `presentationModule = loginPresentationModule` — **son el mismo objeto**, y su
lambda registra los **siete** ViewModels de auth. Así que incluir uno basta y no hay que elegir.

## Diseño: incluir el módulo, no copiar ViewModels

Reponer a mano `RegisterViewModel` y `ForgotPasswordViewModel` habría cerrado las dos puertas que
duelen hoy **dejando la clase de bug intacta** — que es exactamente lo que pasó la primera vez, con
`LoginViewModel`. En su lugar `MockPaparcarApp` incluye `loginPresentationModule` y **desaparece la
lista por-ViewModel** que había que mantener en sync.

Las pantallas inalcanzables en mock (phone, magic link, reauth, reset por deep link) entran también,
y es inocuo: Koin resuelve un ViewModel **cuando se pide**, y el Dev Catalog no pide ninguna.

🔑 **El segundo eslabón, que compilar NO detecta**: `RegisterViewModel` necesita un
`LoginLibraryConfig`, y quien lo ata en producción es `initLoginKoin` — justo lo que mock se salta.
Sin atarlo, el registro compila y **muere pidiéndoselo a Koin**: el mismo crash, una dependencia más
adentro. Se ata en `MockModule` vía `paparcarLoginConfig(BuildConfig.GOOGLE_WEB_CLIENT_ID)`, nunca
inline [AUTH-PROVIDERS-EXPLICIT-001].

## Verificación

| comprobación | resultado |
|---|---|
| `:app:assembleMockDebug` · `:app:compileProdDebugKotlin` | ✅ |
| `loginPresentationModule` contiene `RegisterViewModel` | ✅ leído en el bytecode del AAR |
| `presentationModule` ≡ `loginPresentationModule` | ✅ `<clinit>` |
| Arranque de la app con el módulo incluido | ✅ sin `NoDefinitionFound` al cargar Koin |
| **Deslogueado → Entrar → «Regístrate»** | ✅ **la pantalla de registro se pinta entera** (Nombre · Correo · Contraseña · Confirmar · check de Términos · Registrarse · Google) · 0 crashes |
| **Deslogueado → Entrar → «¿Olvidaste tu contraseña?»** | ✅ **pantalla completa** (título, copy, campo, «Enviar enlace») · 0 crashes |

Las dos puertas que el ticket denuncia, recorridas en device con el `sha256` del APK verificado en el
móvil ANTES de tocar nada. `pm clear` no está disponible en ColorOS (`SecurityException`), así que se
usó `force-stop` sobre una instalación recién hecha.

⛔ **Y la parte que hay que leer antes de fiarse de nada de esto.** En mitad de la prueba vi un
`NoDefinitionFoundException: RegisterViewModel` y estuve a punto de darlo por fallo del diseño. El
`sha256` del APK **no coincidía con el del device**: llevaba varias pantallas probando un build
viejo. Es exactamente la trampa que ya teníamos documentada — *"adb install «Success» puede NO
reemplazar el APK → verificar sha256 en device"*. Tras reinstalar y **verificar hashes iguales**, el
emulador se cayó y no se pudo repetir el toque. **Ese crash no es evidencia de nada**, ni a favor ni
en contra.

## Follow-ups que deja abiertos (fuera de alcance a propósito)

1. **La galería de estados sigue sin grupo de Registro.** El ticket decía que el registro *"no se
   puede ver en el Dev Catalog hasta que esto se arregle"*; ya se puede recorrer a mano, pero
   `StateGalleryScreen` no lo tiene. Es trabajo de galería, no de DI.
2. 🔎 **Los chips DEV y de tema tapan el texto de las pantallas de auth** — visible en las capturas
   del registro y del olvido de contraseña: el chip `DEV` se come parte del título y del cuerpo. Es
   la misma clase que `MOCK-THE-DEV-CHIPS-MUST-NOT-COVER-THE-APPS-CONTROLS-001` (`ba46781d`), que
   los movió al borde izquierdo para no tapar los controles del mapa; en estas pantallas el borde
   izquierdo **también** tiene contenido. Descubierto al verificar esto, no tocado aquí.

## Problema

En el Dev Catalog (flavor `mock`), escenario **Deslogueado** → entrar a la app → pulsar **"Sign Up"**
en el login: la app se cae en el acto ("Paparcar keeps stopping"). Medido en el emulador
`emulator-5556`, 29-08 10:56:

```
FATAL EXCEPTION: main
Process: com.rndeveloper.paparcar.mock
org.koin.core.error.NoDefinitionFoundException: No definition found for type
'com.apptolast.customlogin.presentation.screens.register.RegisterViewModel' on scope '['_root_']'
```

## Causa

`MockPaparcarApp` arranca Koin a mano (`startKoin`) en vez de con `initLoginKoin`, a propósito, para
esquivar el `DataModule` de la librería, que depende de Firebase. El coste de esa decisión es que el
`presentationModule` **de BaseLogin** —que es `internal`, no se puede incluir desde fuera— no entra,
y con él se quedan fuera sus **siete** ViewModels de auth.

`MockModule.kt:65` cierra el agujero para uno solo:

```kotlin
viewModelOf(::LoginViewModel)   // "…otherwise the login screen crashes (NoDefinitionFound)"
```

El comentario que hay encima de esa línea describe exactamente el crash de este ticket. La lección se
aprendió con el login y no se barrió a sus hermanas: cualquier pantalla de auth alcanzable desde el
Dev Catalog necesita su ViewModel registrado, y solo una lo tiene.

**Producción no está afectada**: `PaparcarApp` sí llama a `initLoginKoin`, que carga el
`presentationModule` de la librería entero (`LoginViewModel`, `RegisterViewModel`,
`ForgotPasswordViewModel`, `ResetPasswordViewModel`, `PhoneAuthViewModel`, `MagicLinkViewModel`,
`ReauthViewModel`).

## Doctrina violada

- **Sistemas, no parches** — el invariante ("toda pantalla de auth alcanzable en mock necesita su
  ViewModel") se arregló para el caso que mordió y no se barrieron sus consumidores.
- ⛔ **El set de pruebas mock se mantiene en sync** (CLAUDE.md): hoy hay dos pantallas reales que en
  mock no se pueden abrir sin matar la app.

## Alcanzables desde el login de Paparcar — barrido

| Pantalla | ViewModel | ¿Alcanzable en mock? | Estado |
|---|---|---|---|
| Login | `LoginViewModel` | sí | ✅ registrado |
| Registro | `RegisterViewModel` | sí — enlace "Sign Up" | ❌ **crashea** |
| Olvidé la contraseña | `ForgotPasswordViewModel` | sí — enlace "Forgot password?" | ❌ crashea (misma causa, sin comprobar en device) |
| Reset password | `ResetPasswordViewModel` | no — llega por deep link de email | exento |
| Phone / OTP | `PhoneAuthViewModel` | no — `phoneEnabled = false` [AUTH-PROVIDERS-EXPLICIT-001] | exento |
| Magic link | `MagicLinkViewModel` | no — sin configurar | exento |
| Reauth | `ReauthViewModel` | no — Paparcar no monta hoy esa pantalla | exento |

## Diseño propuesto

Registrar en `mockModule` los ViewModels de las pantallas alcanzables (`RegisterViewModel`,
`ForgotPasswordViewModel`) junto al de login, y dejar en el comentario **por qué esa lista es esa** —
que es la parte que se perdió la primera vez. Verificar en device los tres saltos: login → registro,
login → olvidé contraseña, y vuelta.

Si más adelante se activa otro proveedor o pantalla, el criterio queda escrito: **alcanzable en el
Dev Catalog ⇒ su ViewModel se registra**.

## Criterio de éxito

En el flavor mock, escenario Deslogueado: pulsar "Sign Up" abre el registro y "Forgot password?" abre
su pantalla, sin `NoDefinitionFoundException` en `logcat -b crash`.
