# MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001 · En el flavor mock, salir del login mata la app

**Estado:** 🔴 Abierto, sin rama · descubierto el 29-08-2026 verificando en device
UI-AUTH-HEADER-ALIGNS-WITH-ITS-FIELDS-001 · **solo afecta al flavor `mock`, producción está sana**

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
