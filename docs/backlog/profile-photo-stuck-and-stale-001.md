# PROFILE-PHOTO-STUCK-AND-STALE-001 · La foto de perfil nunca carga (disco verde) y nunca se refresca desde Google

**Estado:** ✅ Done (08-09-2026) — mergeado a master vía squash; el hash vive en `MEMORY.md`

**Hecho:** avatar migrado a `SubcomposeAsyncImage` (SettingsScreen) · `updateIdentityFields`
field-targeted en datasource · reconciliación en `getOrCreateProfile` (sesión sobreescribe, nunca
borra; best-effort con el mismo techo de timeout que el fetch) · 3 fakes de androidUnitTest
extendidos · `UserProfileRepositoryImplTest` nuevo (4 tests, verificados ejecutados en el XML de
resultados) · suite completa verde · `compileMockDebugKotlin` + `compileProdDebugKotlin` OK.
Sin strings nuevos, sin pantalla/estado nuevo → Dev Catalog sin cambios.

## Problema
Dos fallos con la misma víctima (la identidad del usuario en Ajustes):

1. **El avatar sale SIEMPRE como disco verde**, aunque el usuario tenga foto. Verificado en
   Firestore prod (`pap-26`, 08-09-2026): 7 de 9 docs de `users/` tienen `photoUrl` válida de
   `lh3.googleusercontent.com` — la URL llega bien hasta el composable y aun así no se pinta nunca.
2. **El perfil de Firestore es una foto fija del primer login.** Si el usuario cambia su foto (o
   nombre) en su cuenta de Google, la app sigue mostrando la del día que se creó el doc, para
   siempre.

## Causa raíz

### 1 · Deadlock documentado de Coil en `ProfileAvatar` (`SettingsScreen.kt`)
`rememberAsyncImagePainter(url)` sin tamaño explícito usa el size-resolver por defecto, que espera
al **primer draw del painter** para lanzar la petición. El `when` del avatar solo compone
`Image(painter)` cuando `painter.state` es `Success` → el painter no se dibuja nunca → la petición
no arranca nunca → el estado se queda en `Empty`/`Loading` eternamente. Lo que se ve es la rama de
carga: `Box` con `.background(cs.primary)` (verde marca) + `PapShimmerBox`, que es un velo
`onSurface` a alpha 0.15–0.40 — sobre verde es casi invisible. El fetcher de red
(`coil-network-ktor3` + engine OkHttp, service-loaded) está bien; nunca llega a usarse.

### 2 · `getOrCreateProfile` no reconcilia con la sesión
`UserProfileRepositoryImpl.getOrCreateProfile`: si el doc remoto existe, se devuelve tal cual. La
`session` fresca de Google solo se usa en el paso "crear doc nuevo". `photoUrl`, `displayName` y
`email` quedan congelados en su valor del primer login.

## Doctrina violada
- El avatar rompe «nunca un estado ilegible»: la UI enseña un estado de carga eterno como si fuera
  el estado final, sin error ni fallback (el fallback de inicial existe pero es inalcanzable).
- La reconciliación rompe el patrón ya establecido en este mismo datasource: updates
  **field-targeted** para no pisar campos ajenos (`defaultVehicleId`, `firstStepsDismissedAt`)
  [ONBOARDING-A-TUTORIAL-IS-REMEMBERED-BY-THE-ACCOUNT-NOT-THE-DEVICE-001]. El fix debe seguirlo.

## Señales / datos disponibles
- Firestore prod: 7/9 docs con `photoUrl` poblada; 2 con `photoUrl: null` (esos deben ver la
  inicial sobre verde — el comportamiento diseñado, hoy también inalcanzable).
- Cadena de datos auditada y sana: Firestore → `toUserProfileDto` (lee `FIELD_PHOTO_URL`) → Room
  (`UserProfileEntity.photoUrl`) → `toDomain` → `SettingsViewModel.observeProfile` → `state.userProfile.photoUrl`.

## Diseño

### 1 · Avatar (`SettingsScreen.kt` · `ProfileAvatar`)
Sustituir el `when` sobre `painter.state` por **`SubcomposeAsyncImage`**: slot `loading` →
`PapShimmerBox`, slot `error` → inicial sobre verde, éxito → foto `ContentScale.Crop`. El ciclo de
vida del request lo gestiona Coil; desaparece la dependencia circular draw↔state. Sin URL (null o
blank) → inicial directa, sin request.

### 2 · Reconciliación (`UserProfileRepositoryImpl.getOrCreateProfile`)
Tras traer el doc remoto: comparar `photoUrl` / `displayName` / `email` contra la `session`. Si la
sesión trae un valor **no-null distinto** al remoto → `updateIdentityFields` field-targeted en el
datasource (photoUrl, displayName, email, updatedAt) + servir el perfil fusionado a Room y al
caller. Regla de fusión: la sesión solo SOBREESCRIBE, nunca BORRA — un `null` en la sesión (p. ej.
proveedor sin foto) no pisa un valor remoto existente; deja el remoto en paz.

⚠️ Caveat conocido, fuera de alcance: si BaseLogin sirve `session.photoUrl` desde el
`currentUser.photoURL` cacheado de Firebase Auth, la foto nueva de Google solo llegará tras un
re-sign-in con credencial (no en cada arranque). Este ticket garantiza que **cuando la sesión trae
el dato fresco, Firestore y la UI lo siguen** — que hoy no ocurre nunca.

## Criterio de éxito
- Test unitario: `getOrCreateProfile` con doc remoto existente y sesión con foto distinta →
  actualiza los campos de identidad (field-targeted, verificable en el fake) y devuelve el perfil
  fusionado; con sesión `photoUrl = null` y remoto poblado → NO toca nada.
- En device: la foto de Google se ve en Ajustes (hoy: disco verde). Sin red: shimmer y luego
  inicial (error), nunca verde eterno.

## Consumidores auditados
- `rememberAsyncImagePainter`: **un solo call site** en todo el repo (el avatar) — no hay más
  superficies con el mismo deadlock. `AsyncImage`/`SubcomposeAsyncImage`: cero usos previos.
- `getOrCreateProfile`: llamado solo desde `GetOrCreateUserProfileUseCase` (splash/bootstrap) — un
  único punto de entrada, la reconciliación cubre todos los arranques con sesión.
- `createOrUpdateProfile(set)`: solo lo usa el paso "doc nuevo" — sin riesgo de pisado.
- Escritores del doc `users/`: `set` (solo creación), `updateDefaultVehicleId`,
  `updateFirstStepsDismissedAt` y el nuevo update de identidad — todos field-targeted entre sí.
