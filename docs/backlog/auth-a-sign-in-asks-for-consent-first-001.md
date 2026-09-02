# AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001 · Entrar en la app es aceptar la política, y hasta hoy nadie lo preguntaba

**Estado:** 🔵 En progreso · rama `feature/AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001-consent-checkbox` · worktree `../Paparcar-auth-consent`

## Problema
La app crea una cuenta y empieza a recoger ubicación en background sin que el usuario haya visto
NUNCA la política de privacidad. El único enlace a la política vivía en Ajustes — es decir, después
de haber entrado, registrado un coche y concedido permisos. Play exige el enlace accesible, y el
RGPD exige que el consentimiento sea previo e informado, no retroactivo.

## Doctrina violada
Ninguna regla del repo lo prohibía explícitamente — es un hueco de producto, no una regresión. Lo
que sí aplica es la doctrina de copy: la frase no puede hablar de mecánica interna, y el enlace
tiene que llevar al documento real (`https://paparcar.com/privacy-policy`, vivo desde el 03-09).

## Señales / datos disponibles
- BaseLogin **ya expone** los ganchos: `LoginScreenSlots.submitButton`/`socialProviders` y, en
  registro, un `termsCheckbox` cuyo ViewModel ya gatea su propio submit (`state.termsAccepted`
  entra en `isFormValid`). No hace falta tocar la librería — que además está prohibido.
- No existía ninguna preferencia de consentimiento en `AppPreferences`.

## Diseño
**El consentimiento es del DISPOSITIVO, no de la sesión** — se pregunta una vez por instalación y
se persiste (`AppPreferences.hasAcceptedLegalConsent`, DataStore en Android / NSUserDefaults en
iOS). Ya aceptado ⇒ la fila no se dibuja y nada queda gateado: volver a preguntar en cada re-login
sería ruido, no protección.

**Por qué el gate va también en LOGIN y no solo en el formulario de registro** (la pregunta que
abrió el diseño): un primer *Sign in with Google* desde la pantalla de login **crea la cuenta** —
no hay paso por el formulario de registro. Si el checkbox viviera solo en Register, el camino real
por el que entra la mayoría de usuarios no pediría consentimiento jamás. Por eso, mientras no esté
marcado, se apagan **el submit y los botones de proveedor**.

Reparto:
- `AppPreferences` + sus 4 implementaciones (DataStore, iOS, fake mock, fake de tests) → el flag.
- `AppState.hasAcceptedLegalConsent` + `AppIntent.AcceptLegalConsent` → MVI, como el resto del root.
- `paparcarAuthSlots(hasAcceptedLegalConsent, onLegalConsentAccepted)` pasa a ser `@Composable` y
  recuerda el tick. Persiste **al marcar**, no al enviar: si el usuario acepta y abandona la
  pantalla, su aceptación no se pierde. Dentro de la composición actual la fila NO se oculta al
  marcarla (desaparecer bajo el dedo se lee como un glitch); desaparece a partir de la visita
  siguiente.
- En Register se rellena el `termsCheckbox` de la librería con la MISMA fila. Si el consentimiento
  ya estaba dado, un `LaunchedEffect` le dice `true` a la librería para desbloquear su submit sin
  volver a preguntar.
- El enlace es un `LinkAnnotation.Url` dentro del texto. Si una traducción pierde el `%1$s`, se
  degrada a texto plano en vez de romperse.

## Criterio de éxito
- **Pintado** — sin marcar: submit y proveedores apagados. Marcado: se encienden. Ya aceptado: sin
  fila. ✅ Verificado en emulador Pixel 8 (1080x2400 @420), 3 capturas de la galería mock.
- **Persistencia** — ⚠️ las capturas NO la prueban: la variante "Consentimiento ya dado" recibe el
  flag a mano y su callback es un no-op, así que enseña el estado, no el viaje. El viaje lo cubren
  3 tests en `AppViewModelTest`: arranca sin consentimiento · aceptar escribe estado Y preferencia ·
  **un ViewModel nuevo sobre las mismas preferencias ya nace con `true`** (el relanzamiento), y lo
  comprueba en construcción, no tras una emisión tardía — un valor que llegue tarde le enseñaría el
  checkbox un frame a quien ya aceptó.
- `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` ✅ · `:shared:testDebugUnitTest` ✅
  (19 tests en esa clase, 0 skipped) · guardarraíles Konsist ✅ (con `--rerun-tasks`).
- ⏳ Sin verificar end-to-end en device real: el login vivo en mock exige mergear antes
  `MOCK-AUTH-SCREENS-NEED-THEIR-VIEWMODELS-001`, y en prod hace falta OAuth real.

## Consumidores auditados
`paparcarAuthSlots()` tenía 3 call sites, los 3 actualizados:
- `App.kt` → `AuthNavigation(...)`, cableado al `AppViewModel` real. ⚠️ Los slots se construyen
  FUERA del builder del `NavHost`: ese lambda no es contexto composable.
- `PaparcarAuthSlotsPreviews.kt` (androidMain) → `false` fijo, que es el estado que interesa ver.
- `StateGalleryScreen.kt` (mock) → parámetro `consentAccepted` + variante nueva
  "Consentimiento ya dado", en paridad con las previews.
Implementaciones de `AppPreferences`: las 4 (Android DataStore, iOS, `FakeAppPreferences` de mock,
`FakeAppPreferences` de commonTest) — el interfaz no compila si falta ninguna.
Strings: 2 keys × 9 locales.

## Fuera de alcance (deliberado)
- **No hay versionado del consentimiento**: si la política cambia materialmente, hoy no hay forma
  de re-preguntar. Basta para el lanzamiento; el día que se monetice (la política tendrá que
  reescribirse para anuncios/premium) hará falta un `acceptedPolicyVersion` en vez de un booleano.
- El consentimiento no viaja a Firestore: es local al device. Si se quisiera auditar quién aceptó
  qué y cuándo, es otro ticket.
