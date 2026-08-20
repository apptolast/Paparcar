# HISTORY-DETAIL-VEHICLE-SCOPE-001 · El stepper ‹/› del detalle histórico recorre el historial DEL COCHE, no el global

**Estado:** ✅ **Done** · master `9e5ee273` (ff-only 20-08-2026 tras rebase sobre
`0549f957` UI-MAP-TYPE-TOGGLE-001; rama + worktree borrados) · 1256 tests verdes (7 nuevos) ·
`compileMockDebug` + `compileProdDebug` OK · ✅ **validado en device por el user** (APK
`d97cfd3e498a3ee7…` en Oppo + Redmi)

## Problema

Reporte del user (20-08-2026): *"cuando accedo al detalle histórico y paso next y previous, se
mezclan Kamiq con Focus; debería ser el historial propio de cada vehículo en el que estoy."*

Entras al detalle desde el timeline de **un** coche (pestaña Historial del garaje, que está agrupada
por vehículo) y al tocar ‹ / › apareces en un aparcamiento **de otro coche**. Los dos historiales se
intercalan por timestamp, así que basta con que el otro coche tenga un aparcamiento entre medias
para saltar de vehículo sin haberlo pedido.

Reproducción: dos vehículos con aparcamientos alternados en el tiempo → abrir cualquier entrada del
Kamiq → ‹ → sale un aparcamiento del Focus.

## Doctrina violada

No hay un guard DET-* de por medio; lo que se rompe es la **regla editorial de navegación**: el
usuario está dentro del contexto "mío-permanente" de UN vehículo (Vehículos → garaje → historial de
ese coche) y la pantalla de detalle lo saca de ese contexto sin decírselo. El detalle hereda el
sujeto de la pantalla que lo abrió; no puede cambiarlo por su cuenta.

También es el patrón *sistemas, no parches*: el invariante ("la lista sobre la que camina el stepper
es la del coche enfocado") tiene que vivir en UN sitio, no repartido entre la navegación y el VM.

## Señales / datos disponibles

Todo el material necesario ya está en el estado — no hace falta ni una query nueva ni un nav-arg
nuevo:

- `ParkingLocationViewModel.kt:33-37` ya observa `observeAllSessions()` (historial completo,
  todos los coches, ordenado por timestamp desc).
- `UserParking.vehicleId` viaja en cada sesión, así que la sesión enfocada **ya dice de qué coche
  es**. El detalle puede auto-scoparse sin que nadie le pase el `vehicleId`.
- `VehiclesViewModel.kt:41` ya agrupa por `vehicleId` (`allSessions.groupBy { it.vehicleId }`) para
  construir un `HistoryState` por coche: ese es el scope que el detalle debe respetar.
- Existe `UserParkingRepository.observeSessionsByVehicle(vehicleId)` (impl `:76`, DAO `:55`), pero
  **no se usa**: obligaría a arrastrar el `vehicleId` por la navegación y a re-suscribirse en cada
  paso. Descartado a favor del filtro derivado en el estado.

## Causa raíz

`ParkingLocationViewModel.kt:33-37` mete el historial **entero** en `orderedSessions`:

```kotlin
userParkingRepository.observeAllSessions()
    .map { sessions -> sessions.sortedByDescending { it.location.timestamp } }
    .onEach { sessions -> updateState { copy(orderedSessions = sessions) } }
```

y `stepFocus(±1)` (`:91-101`) camina esa lista global. `HistoryTimeline.kt:190` solo pasa
`lat/lon/sessionId` al navegar (`App.kt:571-586`), así que el `vehicleId` se pierde por el camino y
la pantalla ni siquiera tenía con qué re-scoparse... salvo por el propio `focusedSession.vehicleId`,
que sí tiene.

## Diseño

**Un solo sitio: `ParkingLocationState`.** El estado guarda el historial crudo y **deriva** el
universo del stepper a partir del coche de la sesión enfocada:

- `allSessions` — historial completo, newest → oldest. Fuente cruda; nadie camina sobre ella.
- `focusedSession` — se resuelve contra `allSessions` (así el deep-link a una sesión cualquiera
  sigue funcionando sea cual sea su coche).
- `orderedSessions` — **derivado**: `allSessions.filter { it.vehicleId == focusedSession.vehicleId }`.
  `hasNewer` / `hasOlder` / `stepFocus` siguen operando sobre `orderedSessions` sin tocarse, y el
  stepper queda encerrado en el coche por construcción, no por un `if` en el handler.

Se auto-scopa sea cual sea la puerta de entrada (timeline del garaje hoy; mapa, notificación o
deep-link mañana) porque el scope lo dicta el dato, no el argumento de navegación.

### Decisiones explícitas del user (20-08) — NO se tocan

Se plantearon como posibles cambios y el user las adjudica como **comportamiento querido**:

1. **El filtro de rango del timeline (semana/mes/…) NO se hereda.** Dentro del detalle se puede
   caminar todo el historial del coche aunque el timeline estuviera filtrado. *"Debería poder ir a
   donde me dé la gana."*
2. **La sesión activa cuenta como una entrada más** del stepper. *"La sesión actual también
   cuenta."* `observeAllSessions()` no filtra por `isActive` y así se queda.

Ambas quedan escritas en el KDoc de `orderedSessions` para que nadie las "arregle" luego por
simetría con el timeline.

## Criterio de éxito

- ✅ Tests unitarios en `ParkingLocationViewModelTest` — 7 nuevos sobre el historial de dos coches
  intercalado en el tiempo (`kamiq-new 4000 · focus-new 3000 · kamiq-old 2000 · focus-old 1000`):
  `should_scope_the_stepper_list_to_the_focused_vehicle`,
  `should_skip_the_other_vehicle_when_stepping_older` / `_newer`,
  `should_report_no_older_at_the_focused_vehicle_oldest_entry`,
  `should_report_no_newer_at_the_focused_vehicle_most_recent_entry`,
  `should_rescope_the_stepper_when_focus_moves_to_another_vehicle`,
  `should_include_the_active_session_in_the_focused_vehicle_stepper` (decisión 2).
  Los 10 tests previos del stepper siguen verdes sin tocarlos (usan `vehicleId = null`, un solo
  bucket) — la prueba de que el cambio no altera el comportamiento dentro de un mismo coche.
- Comportamiento observable: abrir una entrada del Kamiq y machacar ‹ hasta el fondo → todas las
  paradas son del Kamiq, y el chevron se apaga en la más antigua del Kamiq aunque queden entradas
  más viejas del Focus.

## Consumidores auditados

`grep -rn "orderedSessions\|ParkingLocationViewModel\|HistoryParkingDetailScreen\|PARKING_HISTORY_DETAIL"`

| Sitio | Veredicto |
|---|---|
| `ParkingLocationState.kt:15-36` | **Origen del bug** — corregido (campo crudo `allSessions` + `orderedSessions` derivado). |
| `ParkingLocationViewModel.kt:33-37` | Corregido: escribe `allSessions`. |
| `ParkingLocationViewModel.kt:91-101` (`stepFocus`) | **Sin cambios** — ya operaba sobre `orderedSessions`; hereda el scope gratis. |
| `ParkingLocationScreen.kt:103-135` (`HistoryParkingDetailScreen`) | Sin cambios: consume `focusedSession` / `hasOlder` / `hasNewer`, no la lista. |
| `App.kt:571-602` (ruta + navegación) | Sin cambios: **no hace falta nav-arg de vehículo**, el scope sale del dato. |
| `HistoryTimeline.kt:190` (`onViewOnMap`) | Sin cambios: sigue pasando solo `lat/lon/sessionId`. |
| `VehiclesViewModel.kt:36-64` | Exento: ya agrupaba por `vehicleId`; es la referencia de scope, no un consumidor roto. |
| `StateGalleryScreen.kt` (`parkingDetailSheet`) | Exento: llama a `HistoryDetailSheet` con `hasOlder`/`hasNewer` sueltos, no construye `ParkingLocationState`. Sin cambios ni variantes nuevas (no hay estado ni pantalla nueva). |
| `UserParkingRepository.observeSessionsByVehicle` / `getPreviousSession` | Siguen sin usarse desde el detalle — decisión consciente, ver §Diseño. |
