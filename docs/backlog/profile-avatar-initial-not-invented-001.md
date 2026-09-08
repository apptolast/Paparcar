# PROFILE-AVATAR-INITIAL-NOT-INVENTED-001 · El avatar sin nombre inventaba una "U" — y la sesión ahora llega fresca del proveedor

**Estado:** ✅ Done (08-09-2026) — mergeado a master vía squash; el hash vive en `MEMORY.md`

> Alcance ampliado a petición del user al cerrar: además de la inicial, este ticket cierra el
> circuito de frescura que PROFILE-PHOTO-STUCK-AND-STALE-001 dejó anotado como caveat, y absorbe
> la migración de `withTimeoutOrNull` a `Duration` que el user tenía en el árbol principal.

## Problema
`AvatarInitial` (nacido en PROFILE-PHOTO-STUCK-AND-STALE-001) caía en `?: "U"` cuando el nombre no
tenía primer carácter: una letra hardcodeada en presentación que solo significa "User" en inglés.
Además `ProfileCardV2` solo aplicaba el placeholder localizado al `displayName` **null** — un
`displayName = ""` (que un perfil puede traer) pasaba el `?:` y llegaba vacío al avatar.

## Doctrina violada
⛔ Strings nunca hardcoded en presentación — una letra de UI inventada es un string.

## Diseño
- `ProfileCardV2`: `displayName?.takeIf { it.isNotBlank() } ?: placeholder` — vacío y null caen en
  el mismo placeholder localizado (`settings_profile_name_placeholder`, en los 9 locales).
- `AvatarInitial`: inicial del nombre (con `trim()`); sin carácter usable → `Icons.Rounded.Person`
  tintado `onPrimary` (Nivel 1: plumbing de UI → Material Rounded con tint; color en call site).
  Ningún carácter inventado en ningún idioma.

## Criterio de éxito
Con `displayName` null o `""`, la fila muestra el placeholder localizado y el avatar su inicial;
la rama del icono queda como red para cualquier caller futuro con nombre en blanco. Sin strings
nuevos; suite y guardarraíles verdes.

## Parte 2 · La foto de Google llega en cualquier arranque, no solo tras re-login

El caveat de PROFILE-PHOTO-STUCK-AND-STALE-001: Firebase congela el `photoURL` top-level al crear
la cuenta y BaseLogin servía esa copia. Cerrado en las dos puntas:

- **BaseLogin 2.0.1** (repo propio, `develop` `5d17339`, publicado en Maven Central y liberado):
  el gateway prefiere la foto de `providerData` (saltando la entrada agregada `"firebase"`) con
  fallback al top-level; `refreshSession()` hace `reload()` del usuario antes de leer; el puerto
  gana `reloadCurrentUser()`. `displayName` conserva a propósito la precedencia top-level
  (`updateDisplayName` existe: ese campo es del usuario). 28/28 tests.
- **Paparcar**: bump del catálogo a 2.0.1 + `GetOrCreateUserProfileUseCase` pide primero
  `refreshSession()` con techo de 8 s (misma filosofía que el fetch remoto [AUDIT-DATA-001 M9]) y
  cae a la sesión cacheada si falla o tarda — offline nunca bloquea el splash. Con esto el
  `reconciledWith` de siempre recibe la identidad fresca en cada arranque con red.
- Además: `withTimeoutOrNull(Long)` → `withTimeoutOrNull(Duration)` en
  `UserProfileRepositoryImpl` (cambio del user, traído del árbol principal).

## Consumidores auditados
- `AvatarInitial`: 2 call sites, ambos dentro de `ProfileAvatar` (sin URL / error de carga).
- `settings_profile_name_placeholder`: ya existía en los 9 locales — sin trabajo i18n.
- Otros avatares con inicial: `grep` de `uppercaseChar` en presentación → solo este.
