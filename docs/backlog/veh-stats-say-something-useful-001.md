# VEH-STATS-SAY-SOMETHING-USEFUL-001 · Las stats del vehículo hablan del usuario, no del detector

**Estado:** ✅ Done — validado por el user en device (Redmi mock, 28-08, 3 iteraciones de diseño)
· 1.719 tests verdes (+13 nuevos) · `assembleMockDebug` ✅ · entrada en PARKING-DETECTION.md ✅
· strings en los 9 locales ✅ (retirada `vehicle_stats_reliability`, +4 keys)

## Problema
El bloque de stats de Vehículos dice tres cosas y media mentira:
- **"Fiabilidad 92%"** es la media aritmética del `detectionReliability` de las sesiones terminadas
  — la confianza que el detector se asignó a sí mismo (0.5–1.0 por path de config). Mecánica
  interna con un % delante: incomprensible, no accionable, y las confirmaciones manuales (1.0) la
  INFLAN cuanto peor detecta. La salud accionable ya vive en Ajustes (`DetectionReliabilityLevel`).
- `VehicleHistoryCalculator.computeStats` calcula además `avgSessionsPerWeek`,
  `mostActiveDayOfWeek` y `favoriteStreet` en cada emisión de Room, para cada vehículo — y
  **ninguna de las tres se pinta en ningún sitio**. Cálculo muerto.
- No existen las métricas que sí hablarían del usuario: plazas cedidas a la comunidad, km del
  periodo, autodetección — porque el modelo no persiste ni el momento de cierre ni si el cierre
  publicó plaza ni la longitud del viaje.

## Doctrina violada
- Copy sin mecánica interna [feedback_no_internals_in_user_copy] — "fiabilidad %" la imprime.
- Provenance diagnosticable [DET-PIN-PROVENANCE-001] — el CIERRE de una sesión hoy no deja rastro
  (ni cuándo ni si publicó); el pin lo tiene, su final no.
- No cambia ninguna decisión de detección: el evaluador no lee ninguno de los campos nuevos.

## Señales / datos disponibles
- `detectionPath`/`spotType` ya clasificados por `ParkingDetectionSource` (único resolver de
  "quién puso el pin") → métrica de autodetección sin sets de strings nuevos.
- `routePolyline` (snapped) ya persiste; su longitud no.
- `updatedAt` NO sirve como hora de cierre: sobrecargado por sync LWW y enrichment.

## Diseño
**El cierre de una sesión estampa su resultado en la propia fila** — un invariante, un choke point:

1. **`endedAtMs: Long?` + `publishedSpot: Boolean`** (entity + domain + DTO, synced).
   - Writers de `isActive=0` (barrido abajo): `clearActiveById` (gana params; `endedAtMs` con
     COALESCE — el primer cierre manda; `publishedSpot` con MAX — una promoción posterior puede
     confirmarlo), `clearActiveByVehicle` y `clearActiveOrphans` (supersede: `endedAtMs=now`).
   - Repo API: `clearActiveParkingSession(sessionId, endedAtMs, publishedSpot)`. Cada caller
     declara lo que sabe:
     - `ReleaseActiveParkingSessionUseCase` → `now`, `reason.publishesSpot`.
     - `ProcessConfirmedDepartureUseCase` (witnessed) → `now`, `publishesNow || (marker provisional
       && zona no privada)`.
     - `FinalizeDeducedDepartureUseCase` → `provisionalDepartureAtMs` (el momento REAL de la
       marcha), `privateZoneId == null`.
     - `RevertParkingUseCase` → `now`, `false`.
   - Remoto: el patch rápido de `ClearActiveParkingSessionWorker` sigue tocando solo `isActive`;
     los campos nuevos llegan a Firestore con el full-doc push del outbox drainer
     (`pendingSync=1` ya se estampa en el clear). Decisión deliberada: el fast-path existe por la
     corrección multi-device del flag activo; la hora de cierre no es time-critical.
2. **`routeDistanceMeters: Float?`** (entity + domain + DTO, synced): la longitud haversine de
   `routePolyline`, calculada en el **repo** — el choke point de TODAS las escrituras de ruta
   (`saveNewParkingSession` para la raw, `updateParkingSessionRoute` para snap/accept/pin-to-pin)
   — para que ruta y longitud no puedan divergir. Docs remotos legacy sin el campo: fallback
   `decode+sum` en `ParkingHistoryDto.toEntity()` (autocurativo en el sync).
3. **Presentación**: `HistoryStatsData` pierde `avgSessionsPerWeek`/`avgReliabilityPct` y gana
   `spotsReleasedCount`, `autoDetectedCount`, `provenanceKnownCount`; la 3ª celda del hero pasa de
   fiabilidad a **Plazas cedidas**; la card de Actividad gana "· X km" en su título (suma de
   `routeDistanceMeters` de las sesiones filtradas) y un pie con día más activo · calle habitual ·
   autodetección X/Y, cada uno con su umbral de significancia (patrón `MIN_SESSIONS_FOR_PEAK`).
   Una métrica sin datos NO se pinta (ni "0" ni "—").
- Room: schema v1 editado en sitio, **sin migración** — decisión del user 27-08 (v1 aún no está
  instalada en ningún device).

## Criterio de éxito
- El hero muestra Sesiones · Última · Plazas cedidas; Actividad muestra km del filtro y el pie
  con umbrales; nada muestra "fiabilidad".
- Tests: calculador (nuevas sumas + umbrales), cierre de sesión estampa endedAt/published en los
  4 caminos, distancia escrita en cada vía de ruta, fallback del DTO.
- Suite completa verde + `assembleMockDebug`.

## Consumidores auditados
`isActive = 0` writers (grep `isActive = 0|clearActive`):
- `UserParkingDao.clearActiveById` → **cerrado** (params nuevos).
- `UserParkingDao.clearActiveByVehicle` / `clearActiveOrphans` (supersede en
  `replaceActiveSession`) → **cerrado** (`endedAtMs=now`; `publishedSpot` queda en su valor).
- `GeofenceJanitorWorker` (dedup de duplicados activos, llama al DAO directo — lo cazó el
  compilador, no el grep del use case) → **cerrado**: cierre tipo supersede, `endedAtMs=now`,
  `publishedSpot=false`.
- `reconcileParkingSessions` → **cerrado**: campos synced; si el doc remoto es legacy (sin
  campos), se preserva el conocimiento local (`?:` / OR).
- Firestore inbound (`toParkingHistoryDto` field-by-field) → **cerrado**: FIELD_ nuevos leídos.
- `ClearActiveParkingSessionWorker` (patch remoto de isActive) → **exento con razón**: el full-doc
  drainer lleva los campos; ver Diseño §1.
- `SaveNewParkingSessionWorker` (Data payload de sesión NUEVA) → **exento**: una sesión nueva
  siempre nace `endedAtMs=null, publishedSpot=false` (defaults del DTO/entity).
`detectionReliability` consumers → sin cambios (AssertedPinAuthority etc. siguen leyéndolo);
solo muere su AGREGADO de presentación.
`routePolyline` writers → cerrados vía choke point del repo (las 2 vías); `updateRouteResolution`
no toca la polyline → distancia intacta, correcto.

## Revisión en device (Redmi, 27/28-08) — iterado con el user, resultado: quedarse en la v3
1. **Color**: se probaron y REVOCARON dos variantes (stats del hero en neutro; chart vistiendo la
   identidad del coche con neutro-fuerte para el no vigilado). **Vigente: verde de marca en toda
   la página y todas las fichas; la identidad solo en borde + badge + punto + pill.** El episodio
   completo, con el porqué de cada revocación, en COLOR-SYSTEM.md §8 (2026-08-27/28).
2. **Tipografía**: el pie de facts se queda en `metadata` (Barlow), como el chart al que hace de
   pie — se probó `caption` (Inter) en device y el user prefirió el original. El "· X km" vive
   dentro del `cardTitle` (Outfit, color apagado); los números del hero en `statNumber`/`badge`.
3. **Copy**: el plural `history_activity_noun` pasa a minúscula tras el numeral ("46
   aparcamientos") en 8 locales; DE conserva mayúscula (sustantivos alemanes).

## Fuera de alcance (deliberado)
- Tiempo de viaje / tiempo aparcado agregados (fase 2; `endedAtMs` los deja desbloqueados).
- km máximo/mínimo por viaje (descartados en el análisis del 27-08).
- Retirar `avgReliabilityPct`... del DTO no aplica (nunca fue DTO); `detectionReliability` en sí
  NO se toca — sigue siendo insumo del detector y de la plaza publicada.
