# AUTH-EMAIL-VERIFICATION-AS-AN-OPT-IN-SETTING-001 · verificación de correo (post-1.0)

**Estado:** 🟡 idea, sin rama ni código · anotada 03-09-2026 a petición del user · **post-1.0**

## Qué

Hoy el registro por email/contraseña NO exige verificar el correo (se entra directo tras
`createUserWithEmailAndPassword`). Para 1.0 se deja así **a propósito**: menos fricción el día 1,
y sin acciones de dinero/identidad que lo exijan.

Para una versión siguiente, la idea del user: **meterlo como ajuste**, no como muro obligatorio.

## Cómo (boceto, sin comprometer diseño)

- Enviar `sendEmailVerification()` tras el registro (BaseLogin es quien envuelve Auth — ⛔ no se
  toca desde Paparcar; ver si expone el gancho o hay que pedírselo al repo de la lib).
- Banner suave y descartable en Home/Ajustes: "Verifica tu correo" + reenviar. NO bloquear el uso.
- Ajuste en Ajustes → sección cuenta: estado (verificado / pendiente) + botón reenviar.
- Considerar gatear SOLO acciones sensibles futuras (borrado de cuenta ya pide reautenticación;
  publicar plazas no debería exigir verificación — mataría la comunidad temprana).

## Por qué no ahora

- Muro obligatorio el día 1 = usuarios que se registran y no vuelven al no llegar el correo.
- Depende de tocar el flujo de BaseLogin (lib propia, fuera de este repo).

Relacionado: la pantalla de login/registro ya ganó el checkbox de Términos+Política
(AUTH-A-SIGN-IN-ASKS-FOR-CONSENT-FIRST-001, worktree `../Paparcar-auth-consent`).
