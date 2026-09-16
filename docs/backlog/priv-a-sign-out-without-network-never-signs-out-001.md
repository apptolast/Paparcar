# PRIV-A-SIGN-OUT-WITHOUT-NETWORK-NEVER-SIGNS-OUT-001 · Retirar los tokens antes de salir dejaba al usuario sin poder salir

**Estado:** ✅ Done (2026-09-16) — el hash vive en `MEMORY.md`.
**Origen:** revisión independiente, 3.ª vuelta — hallazgo A2. **Otro fallo de `0fdf4514`.**

## Problema

`SignOutUseCase` retira las alertas de zona, los tokens de cada grupo y el token del dispositivo, y
solo después llama a `signOut()`. El orden es correcto —las reglas exigen la sesión abierta— y la
protección elegida no: `runCatching`.

**Una escritura de Firestore sin red no falla: se queda esperando el ack del servidor.** El SDK
aplica el cambio en local y la `Task` no completa; GitLive la espera, la corrutina suspende y
`runCatching` —que solo atrapa excepciones— no ve nada. `signOut()` nunca llega.

Y muerde justo donde más duele: `SplashViewModel.abortBootstrap()`, que corre **precisamente cuando
algo ha ido mal**, típicamente sin red. El error que debía mostrarse tampoco se emitía.

⛔ El KDoc decía *«si el móvil está sin red, se sale igual»*. Era la intención, no el comportamiento.

## Arreglo

Cada paso remoto dentro de `withTimeoutOrNull(3 s)` mediante un `step()` que registra las dos
formas de no conseguirlo —fallar y no contestar—, y **`signOut()` fuera de todos los plazos**,
porque es local y es lo que el usuario pidió.

⚠️ **Lo que el arreglo no puede dar**, y ahora está escrito en el KDoc: lo que quede en la cola
offline de Firestore **solo sale si ese mismo usuario vuelve a entrar en este móvil** — la sesión
que autoriza esas escrituras es la que se está cerrando. Sin red, el token puede quedar vivo en el
servidor. Lo cubre la otra mitad, el filtro de destinatario de `PushMessageHandler`.

## Testigo

`should_stillSignOut_when_theServerNeverAnswers`, con un repositorio que hace `awaitCancellation()`.
⛔ Un fake que **falle** no habría servido: ese caso ya pasaba antes del arreglo. Lo que había que
reproducir era el que no contesta.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `SettingsViewModel` (logout del usuario) | 🔧 cubierto |
| `SplashViewModel.abortBootstrap()` | 🔧 cubierto, y era el más expuesto |
| `wipeLocalUserData()` (sesión caducada) | 🔧 ticket hermano: `priv-an-expired-session-still-owns-the-token-001` |
| Borrado de cuenta | ✅ camino distinto, ya cubierto |
