# AUTH-PROVIDERS-EXPLICIT-001 · La oferta de login se declara, no se hereda (y se apaga el SMS)

**Estado:** ✅ Done · mergeado en master
`../Paparcar-auth-providers` · pusheada a `origin` el 22-08-2026.
**Rebase 2026-08-22** sobre master `0a0832cf`, desde **66 commits por detrás**: **sin un solo
conflicto**, aunque master había movido bastante `PaparcarApp.kt`, `MainViewController.kt` y
`StateGalleryScreen.kt`. **1401 tests verdes** (1395 de master + los 6 propios, incluido
`PaparcarLoginConfigTest`); prod y mock compilan.
⏳ Lo único pendiente sigue siendo **ver la galería en device**.

## Problema

Paparcar construía su `LoginLibraryConfig` así:

```kotlin
LoginLibraryConfig(googleSignInConfig = GoogleSignInConfig(webClientId = …))
```

…y daba por hecho que eso significaba "solo Google". No es lo que significa: en la librería
`phoneEnabled` vale **`true` por defecto**, así que

```
AuthRepositoryImpl.getAvailableProviders() → [Google, Phone]
PaparcarAuthSlots pasa la lista tal cual → SocialLoginButtonsSection
  → is IdentityProvider.Phone -> PhoneSocialButton(...)
```

Es decir: **las pantallas de login y de registro llevaban un botón de acceso por SMS** que nunca
decidimos ofrecer. El proveedor Phone no forma parte de nuestro setup de Firebase, los SMS se
facturan, y el `defaultCountryCode` de la librería es `"+1"` para una app que arranca en España.

**No lo trajo la subida a 1.1.0.** Comparados campo a campo, el build anterior (`35a5e15fc4`) ya
traía exactamente los mismos defaults; el botón llevaba ahí desde que se integró la librería.

En iOS el mismo defecto era peor: `MainViewController` pasaba `LoginLibraryConfig()` sin nada, con
lo que la lista quedaba en **solo `[Phone]`** — el único botón ofrecido era el de SMS, y además
muerto, porque los handlers Swift (`sendCodeHandler` / `verifyCodeHandler`) tampoco están puestos.

## Doctrina violada

- **"No copy al usuario con mecánica que no sostenemos"**, en su versión más cara: ofrecer un
  método de acceso que no soportamos. La asimetría de fallo aquí es evidente — mejor no ofrecerlo
  que ofrecerlo y que falle al pulsarlo.
- **Dev Catalog en sync.** La galería mock pintaba `listOf(Google, Apple)` a mano y el
  `FakeAuthRepository` devolvía `emptyList()`. O sea: la galería prometía Apple (que no existe),
  escondía el SMS (que sí existía), y el flavor mock no enseñaba ningún botón. Justo lo que había
  que poder mirar antes de decidir esto no se podía mirar.

## Diseño — el invariante en UN sitio

`commonMain/di/PaparcarLoginConfig.kt`, único sitio donde se declara QUÉ ofrece Paparcar:

- `paparcarLoginConfig(googleWebClientId, googleIosClientId)` → construye el `LoginLibraryConfig`
  con `phoneEnabled = false` **explícito**. Si el web client id llega nulo o en blanco, no se
  registra Google (que es el caso de iOS hoy).
- `paparcarSocialProviders(config)` → la lista de botones. Replica las condiciones y el **orden**
  de `AuthRepositoryImpl.getAvailableProviders()`, que es quien manda en runtime pero necesita un
  repositorio que una preview no tiene. Va pegada a la config a propósito: si la librería cambia
  el mapeo, las dos mitades se leen juntas.

La regla queda: **la oferta se declara, nunca se hereda de los defaults de la librería.**

## Consumidores auditados

| Sitio | Antes | Después |
|---|---|---|
| `PaparcarApp.onCreate` (Android) | `LoginLibraryConfig(google…)` inline → Google + **SMS** | `paparcarLoginConfig(BuildConfig.GOOGLE_WEB_CLIENT_ID)` → solo Google |
| `MainViewController` (iOS) | `LoginLibraryConfig()` → **solo SMS**, y muerto | `paparcarLoginConfig(googleWebClientId = null)` → sin botones sociales |
| `StateGalleryScreen.loginScreen` (mock) | `listOf(Google, Apple)` a mano | derivado del mismo builder |
| `PaparcarAuthSlotsPreviews.MockLoginState` | `listOf(Google, Apple)` a mano | derivado del mismo builder |
| `FakeAuthRepository.getAvailableProviders` | `emptyList()` | derivado del mismo builder |
| `commonTest/FakeAuthRepository` | `emptyList()` | se deja: es un stub de tests que no pinta UI |
| `MockPaparcarApp` | no usa `LoginLibraryConfig` (bypass del DataModule de la librería) | sin cambios — ver `MOCK-AUTHPROVIDER-001` |

## Criterio de éxito

- Ni login ni registro muestran botón de SMS, en ninguna plataforma.
- La galería mock enseña exactamente los mismos botones que producción.
- `PaparcarLoginConfigTest` falla el día que alguien quite los flags explícitos y un default vuelva
  a colar un método que no soportamos.

## Verificación

| Qué | Resultado |
|---|---|
| `testProdDebugUnitTest` + `testMockDebugUnitTest` | ✅ 2272 tests, 0 fallos — incluidos los 6 nuevos de `PaparcarLoginConfigTest` |
| `compileMockDebugKotlinAndroid` + `assembleMockDebug` | ✅ BUILD SUCCESSFUL |
| Galería mock en device (Login (BaseLogin)) | ⏳ pendiente — mirar que salga Google y NO salga el de SMS |
| iOS | ❌ no compilable desde Windows; el cambio es un argumento en una llamada ya existente |

## Fuera de alcance

- Habilitar phone auth de verdad (`+34`, proveedor en Firebase, coste de SMS). Si algún día se
  quiere, se enciende **aquí**, en un sitio, y la galería lo refleja sola.
- `IOS-SOCIAL-LOGIN-001` (Google + Apple en iOS) y `MOCK-AUTHPROVIDER-001`.
