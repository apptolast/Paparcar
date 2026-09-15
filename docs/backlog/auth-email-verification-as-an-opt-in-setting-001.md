# AUTH-EMAIL-VERIFICATION-AS-AN-OPT-IN-SETTING-001 · verificación de correo (post-1.0)

**Estado:** 🟡 sin rama ni código, pero **ya no bloqueado** · anotada 03-09-2026 · desbloqueada
15-09-2026 por [BUILD-DEPS-AT-LATEST-STABLE-002]

## 🔑 La mitad-librería ya existe: BaseLogin 3.0.0 expone el gancho

Este ticket decía *«ver si expone el gancho o hay que pedírselo al repo de la lib»*. Ya está pedido y
servido: **BaseLogin 3.0.0** (publicada el 13-09-2026, pin subido en
[BUILD-DEPS-AT-LATEST-STABLE-002]) trae en su AAR:

- `RegisterEffect.EmailVerificationRequired` — el efecto que el registro emite cuando el correo está
  sin verificar. Es el gancho que faltaba, y **no hay que tocar BaseLogin** para usarlo.
- `AuthError.VerificationCodeExpired` y `AuthError.RequiresRecentLogin` — los dos fallos que el
  reenvío y la reautenticación necesitan nombrar sin inventarse jerga.

Lo que queda es **todo del lado de Paparcar**, y el user lo confirmó el 15-09: la verificación se
implementa **en Ajustes**, como este ticket ya proponía (estado verificado/pendiente + reenviar), no
como muro en el registro. Sigue en pie el resto del boceto de abajo.

⚠️ Al cablearlo: `RegisterEffect` es un `sealed` que BaseLogin **amplía entre versiones** (3.0.0 le
sumó este caso sin retirar ninguno). Un `when` exhaustivo sobre él es una bomba de relojería para el
próximo bump — dejar rama `else` o mapear solo lo que nos importa.

## Qué

Hoy el registro por email/contraseña NO exige verificar el correo (se entra directo tras
`createUserWithEmailAndPassword`). Para 1.0 se deja así **a propósito**: menos fricción el día 1,
y sin acciones de dinero/identidad que lo exijan.

Para una versión siguiente, la idea del user: **meterlo como ajuste**, no como muro obligatorio.

## Cómo (boceto, sin comprometer diseño)

- Enviar `sendEmailVerification()` tras el registro — **el gancho ya existe**: consumir
  `RegisterEffect.EmailVerificationRequired` de BaseLogin 3.0.0 (⛔ la lib no se toca desde aquí).
- Banner suave y descartable en Home/Ajustes: "Verifica tu correo" + reenviar. NO bloquear el uso.
- Ajuste en Ajustes → sección cuenta: estado (verificado / pendiente) + botón reenviar.
- Considerar gatear SOLO acciones sensibles futuras (borrado de cuenta ya pide reautenticación;
  publicar plazas no debería exigir verificación — mataría la comunidad temprana).

## Por qué no ahora

- Muro obligatorio el día 1 = usuarios que se registran y no vuelven al no llegar el correo.
- Depende de tocar el flujo de BaseLogin (lib propia, fuera de este repo).

Relacionado: la pantalla de login/registro ya ganó el checkbox de Términos+Política
(AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001, worktree `../Paparcar-auth-consent`).
