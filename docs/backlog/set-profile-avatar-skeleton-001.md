# SET-PROFILE-AVATAR-SKELETON-001 · El avatar de perfil respira mientras carga, no muestra la inicial

**Estado:** ✅ Done · rama `feature/SET-PROFILE-AVATAR-SKELETON-001` (squash a master)

## Problema
`ProfileAvatar` (Settings) dibujaba siempre la inicial del nombre debajo, y superponía el
`AsyncImage` cuando `photoUrl` resolvía. Mientras la foto cargaba, el usuario veía la inicial en
vez de un estado de carga — el único lugar de la app que consume `photoUrl` no usaba el primitivo
de skeleton del sistema (`PapShimmerBox`, ya usado en Home sheet y en Vehículos/Historial).

## Doctrina violada
Ninguna regla dura; es una inconsistencia de UI (todo skeleton del sistema pasa por un único
primitivo, `PapShimmerBox` — `ui/components/PapShimmer.kt`) que este avatar no seguía.

## Diseño
`ProfileAvatar` ahora resuelve tres estados explícitos vía `rememberAsyncImagePainter` +
`AsyncImagePainter.State`:
- sin URL o `Error` → inicial en disco verde (igual que antes)
- `Loading` → `PapShimmerBox` (círculo, mismo tamaño que el disco)
- `Success` → `Image(painter)` crop-to-fill

Cambio acotado a `SettingsScreen.kt` (único consumidor de `photoUrl` en `presentation/`).

## Criterio de éxito
- Compila `:shared:testDebugUnitTest` y `:app:compileProdDebugKotlin`.
- Visualmente: el disco muestra el shimmer mientras la imagen de Google/Firebase resuelve, y cae a
  la inicial solo si de verdad falla o no hay URL.

## Consumidores auditados
`grep photoUrl shared/src/commonMain` → solo `SettingsScreen.kt` en `presentation/`; el resto son
domain/data (mapper, DTO, entity, repos, fakes). Nada más que auditar.
