# AUTH-EMAIL-VERIFICATION-AS-AN-OPT-IN-SETTING-001 · verificación de correo (post-1.0)

**Estado:** ✅ **Done — implementado por [AUTH-AN-UNCONFIRMED-EMAIL-MUST-SAY-SO-001]** (`auth-an-unconfirmed-email-must-say-so-001.md`),
01-10-2026. Anotada 03-09-2026, desbloqueada 15-09 por [BUILD-DEPS-AT-LATEST-STABLE-002].

Salió **en Ajustes**, como este ticket proponía y el user confirmó el 15-09, con una diferencia sobre
el boceto de abajo: el estado se muestra **siempre** — también cuando el correo ya está confirmado —,
por transparencia (decisión del user, 01-10-2026). Y **sin muro en el registro**, que es lo que este
ticket ya pedía: `RegisterEffect.EmailVerificationRequired` sigue sin usarse, porque la app no bloquea
la entrada por un correo sin confirmar.

⚠️ Lo que **no** cierra, y hay que saberlo: enviar el correo no verifica nada — hace falta que el
usuario **pulse** el enlace. Así que el agujero por el que una cuenta de contraseña pierde su credencial
al entrar con Google queda **cerrado solo para quien complete la verificación**. Cerrarlo del todo
exigiría el muro que se descartó a propósito.

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
