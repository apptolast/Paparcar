# UI-SEVEN-STRAYS-FROM-THE-CANON-001 · Siete desvíos del canon de color/componentes, un barrido

**Estado:** ✅ Done
**Abierto:** 01-09-2026 · **Cerrado:** 01-09-2026. El ritmo unificado de skeletons y el CTA nuevo se
revisaron en el run del 01-09 (Pixel 8 Pro, mock, capturas) y el user dio el visto al merge; si en
un móvil físico el ritmo chirría, se retoca el token en `PapShimmer.kt`, no los call sites.

## Problema

Un barrido completo de `ui/components/` (43 ficheros) + las tres pantallas principales, hecho a
raíz de la conversación sobre identidad de color en Home/Vehículos, confirmó que la estructura es
sólida (resolver único de identidad con 15 call sites coherentes, rampa con resolver único,
guardarraíles funcionando en `presentation/`) **pero encontró 7 desvíos concretos** del canon que
los propios docs (`COLOR-SYSTEM.md`, `PapColor.kt`, `PapShimmer.kt`) declaran.

## Las 7 incidencias (orden de ejecución)

1. **`HomeDetectionSurface` es un segundo resolver de identidad** (`:146-154`). Único fichero fuera
   de `ui/theme/` que lee `papCarBlue`/`papWatchGreen` crudos; su `methodTone(viaBluetooth: Boolean)`
   no puede representar `VehicleWatch.Off`. Ya mordió una vez («caught on device, not by the
   sweep»). `ColorGuardrailTest.LEG_REGEX` solo caza sufijos `*Light/*Muted/*Dark` → agujero.
   **Fix:** enrutar por `vehicleIdentityColor(watch)` + cerrar el agujero del guardarraíl
   (con testigo de población).
2. **`AddingParkingPeek.kt:132` tiñe el eyebrow entero** con la identidad (`eyebrowColor`) en vez
   de solo el nombre (`eyebrowHighlightColor`) como los otros 3 peeks. Si hay palabra de estado en
   el eyebrow, viola §3.1 («el estado nunca tiñe»).
3. **Hexes duplicados en `SpotPalette`** (`PaparcarMapMarkers.kt:662-663`): `Amber 0xFFE08200` y
   `Red 0xFFE0322F` duplican `PapSpotCoolingLight`/`PapSpotExpiringLight`. La pata verde ya se
   corrigió a token con comentario explicando exactamente este problema; estas dos quedaron.
4. **Tres ritmos de skeleton** — implementa el ticket ya abierto
   `UI-EVERY-SKELETON-BREATHES-THE-SAME-001` (ver su doc: opción (a) rampa de bloque grande en
   `PapShimmerBox` vs (b) primitivo hermano). ⚠️ NO portar tal cual: la rampa 0.15→0.40 es 2,5×
   la de lista.
5. **`SpotRowContent` re-implementa la anatomía de `PapListItem`** (`HomeSpotRows.kt:140-260`) en
   un fichero que ya lo importa y lo usa para otra fila.
6. **CTA del estado vacío de Vehículos a mano** (`VehiclesScreen.kt:483-510`): `PapColor.actionText`
   (rol de TEXTO, piso 4.5:1) usado como relleno + re-implementación de `PapPrimaryButton`.
7. **Roles de `PapColor` sin adoptar**: `PapColor.live` tiene 0 consumidores (los 9 call sites leen
   `PapLiveMap` crudo); selección/foco/progreso/atención/brandData leen `colorScheme` directo.
   Adopción por barrido, sin cambio visual (los roles resuelven hoy al mismo valor).

## Doctrina violada

- §7.2 COLOR-SYSTEM: «el color de un vehículo sale SOLO del resolver único» (incidencia 1).
- §3.1: «el estado nunca tiñe» (incidencia 2, condicional a qué haya en el eyebrow).
- §7 regla 5 «un hex, una historia» — su espíritu; los literales del mapa escapan a la letra (3).
- `PapShimmer.kt:20-26`: «Reuse this instead of hand-rolling» (4).
- `[UI-LIST-ITEM-001]`: no re-implementar filas icono+título+meta+trailing (5).
- `PapColor.kt` §7.1: el rol posee el trabajo; `actionText` es para PALABRAS (6, 7).

## Criterio de éxito

- `:shared:testDebugUnitTest` verde, incluidos los guardarraíles Konsist (con `--rerun-tasks` para
  los de imports), y `assembleProdDebug` + `assembleMockDebug` compilan.
- Cero cambio visual salvo: (4) el ritmo unificado de skeletons (a verificar en device) y (6) el
  relleno del CTA si el par action/onAction difiere del actual.
- Incidencia 1: guardarraíl nuevo que FALLE si un fichero fuera de `ui/theme/` nombra
  `papCarBlue`/`papWatchGreen`/`PapWatchGreen*`/`PapCarBlue*`, con testigo de población
  [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001].

## Consumidores auditados

- **Incidencia 1** — `viaBluetooth` tenía 5 consumidores: `DetectionStory.kt` (modelo+resolver),
  `HomeDetectionSurface`, `DetectionStoryTest`, `HomeDetectionSurfacePreviews`,
  `StateGalleryScreen` (mock). Todos migrados a `watch: VehicleWatch`. Ningún otro fichero
  construía `Driving`/`Watching`. `PeekTimeRowOptionsPreviews` (androidMain) usaba `papCarBlue`
  crudo y no estaba en el barrido original — enrutado.
- **Incidencia 4** — consumidores de `PapShimmerBox` previos (BrowsePeek, PapSheet,
  ParkingHistoryDetailScreen) intactos: el ritmo inline no cambia; solo se añade la escala.
- **Incidencia 7** — barrido por rol (los containers se quedan en `colorScheme`: los roles solo
  poseen el ACENTO):
  - `live`: 10 sitios (PaparcarMapView ×3, PaparcarMapMarkers ×6, HomeMapFab ×1) → `PapColor.live`.
    Cero `PapLiveMap` crudos fuera de `ui/theme` + el PALETTE del guardrail (que lo declara).
  - `focus`: PapTextField. · `progress`: DetectionTierStatusCard ×3, PermissionTier, PermissionRow ×2.
  - `brandData`: HistoryWeeklyChart ×2, HistoryTimeline (punto de día), PaparcarFilterChip (icono
    leading), AddingZonePeek (icono de zona), PapSheet (dígito del SpotCounter con plazas).
  - `attention`/`onAttention`: SettingsScreen ×4, PermissionsContent ×4, HomeGpsAccuracyBanner,
    PapSheet (banner). El banner GPS toma también `danger`/`onDanger` en su pierna POOR.
  - `action`: HistoryTimeline («ver en mapa»), HomeSpotRows (icono del report card).
  - `selected`: VehicleTypeSelector ×3, VehicleColorSelector, PaparcarFilterChip ×2 (borde+label),
    HomeSpotRows (barra de selección). `VehicleSizeSelector` y `AddingZonePeek:299` NO se tocan:
    solo leen containers.

## Registro

- 01-09: worktree + doc abiertos. Orden elegido: 1→2→3→4→5→6→7 (invariantes de color primero,
  reuso de componentes después, adopción ancha al final para no ensuciar los diffs anteriores).
- 01-09 · **Incidencia 1 ✅** — fix de SISTEMA: `DetectionStory.Driving/Watching` dejan de proyectar
  el eje de vigilancia a un Boolean y llevan `watch: VehicleWatch`; `resolveDetectionStory` lo lee
  con `.watch()`. `HomeDetectionSurface` pierde su switch privado y construye el tono con
  `vehicleIdentityColor(watch)`; la fila de AwaitingAnswer fija `VehicleWatch.Assisted` (pregunta
  Coordinator-only, razón preservada del comentario original). Consumidores barridos: test,
  previews, StateGalleryScreen, y un 5º que el barrido inicial NO vio (`PeekTimeRowOptionsPreviews`
  usaba `papCarBlue` crudo — enrutado también). Guardarraíl nuevo
  `feature code never names the identity accents directly` con testigo de PARSER (el regex prueba
  que ve la forma infractora) y **falsado con `--rerun-tasks`**: import infractor temporal → rojo
  nombrando fichero y token → revertido → verde. Bonus honesto: el test del nombre del viaje
  destapó que un vehículo ni activo ni BT se pintaba verde asistido; ahora resuelve `Off` (gris),
  igual que su chip del garaje — expectativa fijada explícita con su porqué.
- 01-09 · **Incidencia 2 ✅ descartada como FALSO POSITIVO** — el eyebrow de `AddingParkingPeek` ES
  solo el nombre del vehículo (`displayName ?: genericHeader`), que es la excepción documentada de
  §3 COLOR-SYSTEM (sin glifo de método, el nombre viste el color); con vehículo null (loading) cae
  al genérico con `watch = Off` → `onSurfaceVariant`, idéntico al tono Neutral. No hay palabra de
  estado teñida y el propio código lo documenta en `:130-131`. Cero cambios.
- 01-09 · **Incidencia 3 ✅** — `SpotPalette.Amber/Red` pasan a leer `PapSpotCoolingLight`/
  `PapSpotExpiringLight` (mismos hexes por diseño: «una sola rampa, los mismos tres tonos»),
  terminando el trabajo que la pata verde ya había hecho con `PapSpotFreshPuck`.
- 01-09 · **Incidencia 4 ✅** — implementa `UI-EVERY-SKELETON-BREATHES-THE-SAME-001` con la opción
  (a) de su doc: `PapShimmerBlockScale = 0.4f` en `PapShimmer.kt`, ambos skeletons de lista portados
  a `PapShimmerBox`, duración única `PapMotion.Breathe`. Cambio visible: Home 900→600 ms, Historial
  0.18→0.16 de alfa. ⏳ device. Su doc cerrado con la resolución.
- 01-09 · **Incidencia 5 ✅** — `PapListItem` gana `titleSlot` (el hermano estructurado de `title`,
  espejo del contrato `subtitle`/`subtitleSlot` que ya existía) y `SpotRowContent` se monta sobre él:
  puck = leading, meta = subtitleSlot, píldora de edad = trailing. Cambio visible: el gap
  título→meta pasa de 2 a 4 dp (el canon del componente).
- 01-09 · **Incidencia 6 ✅** — el CTA del estado vacío de Vehículos es un `PapFooterButton(Filled)`.
  Corrige de paso un bug visual real: el relleno era `PapColor.actionText`, que en claro es el verde
  oscuro DE TEXTO — el único botón relleno más oscuro que el resto de la app. Sin icono «+» por
  [UI-BUTTON-ICONS-EARN-THEIR-PLACE-001]. Constantes muertas barridas.
- 01-09 · **Incidencia 7 ✅** — adopción de roles en ~20 ficheros (detalle en Consumidores).
  `PapColor.live` pasa de 0 a 10 consumidores. Un tropiezo del sed (`cs.primary` como prefijo de
  `cs.primaryContainer` en el chip) lo cazó el compilador. Sin cambio visual: cada rol resuelve hoy
  al mismo valor que el token que sustituye.
- 01-09 · **Verificación**: `:shared:testDebugUnitTest --rerun-tasks` VERDE (suite completa,
  Konsist incluidos) · `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` verdes.
  Guardarraíl nuevo falsado con infractor real (rojo → revert → verde).
