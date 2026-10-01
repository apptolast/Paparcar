# AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001 · el correo dice si está confirmado, y se puede confirmar

**Estado:** ✅ **Done, sin commitear** · rama `feature/AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001-verify-email` ·
worktree `../Paparcar-verify-email` · ⏳ queda **verlo en mano** y su captura

## Problema

Paparcar **nunca ha mirado si el correo de la cuenta está verificado**, ni ha ofrecido verificarlo.
Medido: el único `sendEmailVerification` del repo está en los **fakes** (`commonTest` y `:mockkit`),
que lo implementan porque la interfaz les obliga; **cero call sites de producción**, y nadie lee
`isEmailVerified`.

Eso tiene una consecuencia que no es cosmética, y que mordió de verdad el 30-09-2026 con la cuenta
de demo de los revisores ([AUTH-A-FAILED-SIGN-IN-MUST-NAME-THE-OTHER-DOOR-001]): con
«Una cuenta por dirección de correo» (el ajuste por DEFECTO de Firebase), entrar con Google en un
email cuya cuenta de contraseña está **sin verificar** hace que Firebase **borre la credencial de
contraseña**. La cuenta sin verificar pudo crearla cualquiera; la verificada gana.

Estado real de las cuentas del proyecto, medido el 01-10-2026:

| Cuenta | Proveedores | `emailVerified` | |
|---|---|---|---|
| `paparcar.review@gmail.com` | `google.com` + `password` | ✅ | conviven: inmune |
| `silviadurandeancos@gmail.com` | solo `password` | ❌ | **riesgo abierto**, y es la cuenta que más plazas publica |
| resto | solo `google.com` | ✅ | no aplica |

🔑 **Lo que hace que los dos proveedores convivan es tener el email verificado.** Por eso este
ticket no es «una fila más en Ajustes»: es la pieza que cierra el agujero del otro.

## Doctrina violada

Ninguna, y conviene decirlo: no hay regla incumplida, hay una **capacidad que la librería sirve y la
app no usa**. Lo que sí aplica son las de siempre — copy sin mecánica interna, strings en los 9
locales, estado por MVI, galería en la misma tarea.

## Señales / datos disponibles

**Todo servido por BaseLogin 3.0.2, sin tocar la librería:**
- `AuthRepository.sendEmailVerification(): Result<Unit>`
- `UserSession.isEmailVerified: Boolean`
- `AuthRepository.refreshSession(): AuthResult` → por debajo `gateway.reloadCurrentUser()` →
  `auth.currentUser?.reload()`. **Esta es la pieza crítica**, ver *Diseño*.
- `AuthError.TooManyRequests` para el reenvío abusado.

## Diseño

### Dónde: DENTRO de `ProfileCardV2`, no en una sección nueva

El email ya se pinta ahí (`ProfileCardV2(displayName, email, photoUrl, onLogout)`). Un estado sobre
un dato va **pegado al dato**: en una sección aparte obligaría a relacionar dos sitios de la misma
pantalla, y Ajustes ya tiene seis secciones — una nueva para una sola fila es ruido estructural.
La regla editorial de navegación ya manda esto a Ajustes (*si configura → Ajustes*).

### Qué se ve: SIEMPRE, también cuando está verificado

**Decisión del user (01-10-2026): por transparencia.** La alternativa —mostrarlo solo cuando falta—
se descartó: ahorra una línea a quien no tiene nada que hacer, pero deja al usuario sin saber si su
correo está confirmado hasta que algo va mal.

| `isEmailVerified` | Qué se pinta |
|---|---|
| `true` | badge **`VERIFICADO`** (`PapStatusBadge`, la variante semántica positiva que ya existe) |
| `false` | badge **`SIN CONFIRMAR`** + una línea de por qué + CTA **«Enviar enlace»** |
| `null` | nada: la sesión aún no se ha leído, y un badge en blanco mentiría |

⛔ **No hace falta saber qué proveedores tiene la cuenta.** Con `isEmailVerified` basta: las de solo
Google salen «verificado» solas, y el CTA solo aparece cuando el flag es `false`. Se evita así
depender de la lista de proveedores, que es justo lo que la protección de enumeración de emails
oculta.

### El flag sale de la SESIÓN, nunca del perfil

`state.userProfile` es **nuestro** modelo (Firestore); `isEmailVerified` vive en la `UserSession` de
BaseLogin. ⛔ **No se guarda en `UserProfile`**: duplicaría en Firestore una verdad que vive en auth,
con dos fuentes que pueden discrepar y una sincronización capaz de servir el valor viejo. El invariante
se queda en UN sitio. Por tanto, campo nuevo en el `State`, alimentado de `getCurrentSession()`.

### ⛔ Sin caso de uso nuevo, y el criterio NO es «no aporta valor»

`SettingsViewModel` ya usa las dos formas, y su propio código explica cuándo cada una:
`authRepository.getCurrentSession()` se llama **directo**, mientras `signOut` va por `SignOutUseCase`
con este comentario: *«Por el caso de uso y no por `authRepository.signOut()` a secas: salir tiene que
retirar antes los tokens de push»*.

🔑 El criterio es **¿orquesta consecuencias?**. `signOut` sí. `sendEmailVerification()` no toca Room,
ni Firestore, ni push, ni geocercas: es un envío y se acabó → **va directo al repositorio**.
Un caso de uso aquí sería un passthrough, y [DET-VERDICT-NOT-PREDICATE-001] ya dice que un caso de uso
se justifica por veredicto, no por simetría.

### El refresco, o la fila se queda colgada para siempre

Si el usuario pulsa el enlace del correo y vuelve a la app, **nadie se lo dice**: `isEmailVerified`
viene de la sesión cacheada, y la fila seguiría diciendo «sin confirmar» eternamente. Por eso se llama
`refreshSession()` al entrar en Ajustes, **best-effort**, con el mismo patrón que ya usa
`GetOrCreateUserProfileUseCase` (envuelto en `runCatching`, sin bloquear la pantalla si falla).
Un fallo de red deja el flag como estaba; no se invierte ni se borra.

### El reenvío: estado, no temporizador

Tras enviar, el CTA pasa a **«Enlace enviado»** y se deshabilita hasta que se vuelve a entrar en
Ajustes. **Deliberadamente sin cuenta atrás**: un temporizador exige un reloj en el ViewModel y un tick
por segundo para una pantalla de configuración, y lo único que hay que evitar es que se machaque el
botón — que el estado ya evita. Si Firebase corta igualmente, `AuthError.TooManyRequests` sale por
`SettingsEffect.ShowError` → snackbar, como el resto de errores.

## Criterio de éxito

1. Una cuenta verificada muestra `VERIFICADO` en Ajustes. Una sin verificar muestra `SIN CONFIRMAR`,
   el por qué y el CTA.
2. Pulsar el CTA envía el correo y el botón queda en «Enlace enviado»; un segundo intento no se puede
   lanzar sin salir y volver.
3. Tras confirmar en el correo, **volver a Ajustes muestra `VERIFICADO`** sin reinstalar ni cerrar
   sesión — esto es lo que prueba que el `refreshSession()` está puesto.
4. Un fallo de envío sale por snackbar y **no** cambia el badge.
5. Keys nuevas en los 9 locales (`LocaleParityGuardrailTest`) con apóstrofo crudo
   (`ComposeResourcesEscapeGuardrailTest`).
6. Variantes en la galería de estados (el grupo `Settings` ya existe) y en `SettingsPreviews.kt`.
7. `SettingsViewModelTest` cubre: envío OK, envío con error, y el refresco al arrancar.

## Verificación

| Qué | Resultado |
|---|---|
| `testAll --rerun-tasks` | ✅ **3848 tests, 0 fallos** |
| `SettingsViewModelTest` | ✅ **68 tests, 0 fallos** — 7 nuevos: verificado, sin verificar, recarga al abrir, envío OK, envío fallido, el badge no se mueve al enviar, y no se envía dos veces |
| Guards con testigo | `LocaleParity` 5 · `ComposeResourcesEscape` 3 · `Typography` 5 · `Color` 8 · `MockKoinModuleVerify` 2 |
| `prodDebug` + `mockDebug` | ✅ compilan |
| iOS | ✅ `compileKotlinIosSimulatorArm64` **con `--rerun-tasks`**, para que la tarea se ejecutara de verdad: un `UP-TO-DATE` no prueba nada, y `touch` tampoco lo fuerza (Gradle compara hashes de contenido, no fechas) |
| En device / en mano | ⏳ **sin ver** |

## Lo que se descubrió por el camino

⛔ **El `when` de errores de Ajustes colapsaba TODA la rama `PaparcarError.Auth`** en el mensaje de
«no se pudo borrar la cuenta» (`SettingsScreen.kt`). Mandar ahí un fallo de envío habría dicho al
usuario que no se pudo borrar su cuenta. De ahí que el error tenga **caso propio**
(`Auth.VerificationEmailFailed`) y que su rama vaya **antes** de la genérica. El comentario del
propio código ya lo anticipaba: *«anything new falls back to the generic message rather than
vanishing»*.

⚠️ **El `@BeforeTest` de `SettingsViewModelTest` ya construye un ViewModel.** El test de la recarga
afirmaba `refreshSessionCallCount == 1` y salía 2 porque construía un segundo. Se arregló
**quitando el `buildVm()` del test**, no relajando el número: dos aperturas de Ajustes no son lo que
el producto hace.


## Consumidores auditados

| Sitio | Estado |
|---|---|
| `SettingsState` | campo nuevo `emailVerified: Boolean?` + `isSendingEmailVerification` + `emailVerificationSent` |
| `SettingsIntent` | `SendEmailVerification` |
| `SettingsEffect` | **sin tocar**: se reutiliza `ShowError(PaparcarError)` |
| `ProfileCardV2` | params nuevos; es el único call site |
| `UserProfile` / Firestore | **exento a propósito**: el flag no se duplica ahí (ver *Diseño*) |
| `FakeAuthRepository` (**solo commonTest**) | `sendEmailVerification` pasa a configurable + contador, y `authenticatedSession()` acepta `isEmailVerified` (default **false**, como una cuenta de contraseña recién creada) |
| `FakeAuthRepository` de `:mockkit` | **exento, y no por olvido**: la galería pinta `SettingsContent(state = …)` directamente, así que los tres estados se consiguen con el State y el fake no entra en juego |
| `StateGalleryScreen` grupo `Settings` · `SettingsPreviews.kt` | variantes nuevas |
| `auth-email-verification-as-an-opt-in-setting-001.md` | **este ticket lo implementa**; se cierra ahí |
| iOS | gratis: todo es `commonMain`. Verificar con `compileKotlinIosSimulatorArm64` |
