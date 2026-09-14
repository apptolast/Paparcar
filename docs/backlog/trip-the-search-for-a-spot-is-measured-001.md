# TRIP-THE-SEARCH-FOR-A-SPOT-IS-MEASURED-001 · El tiempo buscando plaza se mide, se estampa y se enseña

**Estado:** ✅ Done (2026-09-14) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Secuela con ficha: `trip-a-journey-is-a-chain-of-trips-001.md` (proyección "viaje", UX por decidir).
**Verificado:** suite completa verde (`ParkingSearchTimeTest` 6/6 · `ConfirmParkingUseCaseTest`
53/53, +2 · `ComputePeriodStatsUseCaseTest` 14/14, +1 · baseline 4/4 con testigo 9→10) · el
`10.json` exportado lleva la columna · locale-parity con los 9 · verify mock · compiles
mock/prod/iosSimulatorArm64 OK. ⚠️ El ancla que cierra el reloj es el `location` original (fin
medido de conducción), no el reloj del confirm — el hold no se cobra como búsqueda.
**Abierto:** 2026-09-14 · desde master `efccb2d9` · fase 7 del pivote trayectos-como-producto

## Problema

La métrica estrella del pitch del pivote ("te dice lo que realmente te cuesta tu coche… tiempo
perdido buscando plaza") **ya tiene su asiento reservado y nadie la calcula**:
`PeriodStats.searchTimeMs` existe con el KDoc *"Minutes lost hunting — stamped by
DET-SEARCH-TIME-001 (F7). Null until then"* y `ComputePeriodStatsUseCase:162` lo fija a `null`.

Y hay una restricción física que decide TODO el diseño: **el polyline guardado no lleva
timestamps** (`PolylineCodec` codifica lat/lon). El tiempo de búsqueda solo puede medirse **en el
confirm**, cuando los puntos crudos de `DrivingRouteStore` aún llevan reloj — después es
irrecuperable. Igual que `routeDistanceMeters`: se mide en el momento en que el dato existe y se
estampa en la sesión.

## Doctrina violada

Ninguna hoy (un null honesto). La regla que este ticket debe respetar entera:
[STATS-A-COMPUTED-LITRE-WITH-NO-READER-001] — **nada se computa sin lector**. La cadena va completa
en la misma tarea: evaluador puro → campo de sesión (Room v10 + sync) → agregado → **tile del panel
de stats** (el lector, patrón `StatTileKey`).

## Diseño

1. **`ParkingSearchTime`** (puro, `domain/detection/`, junto a `DrivingRoute` — consume los mismos
   puntos): *la búsqueda empieza la ÚLTIMA vez que el viaje estuvo a más de
   [SEARCH_RADIUS=300 m] del ancla final, y termina en el ancla.* Directo al destino ≈ 30–60 s de
   suelo (documentado — lo que se mueve periodo a periodo es el delta); dar vueltas suma cada
   vuelta. Guards: puntos y ancla con timestamp real; nunca salió del radio → null (viaje corto,
   inmedible); resultado fuera de [0, 45 min] → null (anomalía, no cifra). Es un **proxy medido y
   calibrable** — el radio es constante nombrada, no verdad revelada.
2. **`UserParking.searchTimeMs`** — estampado en `ConfirmParkingUseCase` SOLO cuando
   `routePolyline` se adjuntó (misma frescura: una ruta demasiado vieja para adjuntarse tampoco
   puede medir la búsqueda). Sweep completo del campo (la ruta de `routeDistanceMeters`):
   modelo → Entity (**Room v10**, `MIGRATION_9_10` + testigo baseline) → mapper (4 sitios) →
   `ParkingHistoryDto` (el write `set(dto)` viaja solo; el read manual lo fuerza
   `FirestoreDeserializerParityTest`) → `UserParkingReconcile` → workData (DTO entero, viaja solo).
3. **Agregado**: `ComputePeriodStatsUseCase` suma los `searchTimeMs` de las sesiones del periodo
   (suma, no media: "tiempo PERDIDO en el periodo" es el coste que el pitch vende).
4. **Lector**: tile `SEARCH_TIME` en `VehicleStatsBlock` (icono Rounded, `durationText`, delta vs
   periodo anterior, visible solo con dato — mismo gate que el resto). String
   `stats_tile_search_time` en los 9 locales. Vocabulario: ES «Buscando plaza» (la palabra de lo
   comunitario es la correcta: buscas una plaza LIBRE), EN «Finding parking».

## Fuera de alcance, con ficha propia

La **proyección "viaje"** (cadena de trayectos con parada < umbral) es UX que merece diseño a la
vista, no improvisación → `docs/backlog/trip-a-journey-is-a-chain-of-trips-001.md` (abierta).

## Criterio de éxito

- Tests de `ParkingSearchTime` (directo ≈ suelo pequeño · vueltas suman · nunca-salió → null ·
  sin timestamps → null · anomalía → null) · stamping en `ConfirmParkingUseCaseTest` · suma en
  `ComputePeriodStatsUseCaseTest`.
- Suite completa verde + testigo `MIGRATION_9_10` + compiles mock/prod/iosSimulatorArm64.
- En device: un aparcamiento con vueltas enseña su tile.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `PeriodStats.searchTimeMs` (:162 null fijo) | cerrado — el asiento reservado se ocupa |
| Superficies del campo (`routeDistanceMeters` como plantilla): Entity/Dao/Dto/Mapper/Reconcile/Remote/workData | cerradas una a una (diseño §2); workData y write Firestore viajan solos por diseño previo |
| `EnrichParkingSessionWorker` (snap/pin-a-pin reescriben ruta) | exento: reescriben el POLYLINE, no la búsqueda — la medida del confirm es la única con reloj y no se toca |
| `hasMeasuredFigures` / gate de la tarjeta | cubierto: el tile entra por `statTileKeys` y cuenta como cifra medida |
| Previews / galería del bloque de stats | cubierto: paridad con un fake con searchTime |
| iOS | el confirm es común → sesiones iOS con ruta ganan la medida gratis; compile verificado |
