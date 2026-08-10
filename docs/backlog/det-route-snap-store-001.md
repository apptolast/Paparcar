# DET-ROUTE-SNAP-STORE-001 — Guardar la ruta YA ajustada a la calzada (snap una vez, no re-lógica al mostrar)

**Estado:** 🟡 rama `feature/DET-ROUTE-SNAP-STORE-001` (worktree `../Paparcar-routesnap`, desde master
cbc17ac4). ⏳ tests + device/field.

## Petición del usuario (2026-08-10)
"Obtenemos fixes crudos, los ajustamos a la calzada como Driversnote/Google/Waze… **eso** es lo que
hay que guardar. Recalcular solo lo que NO esté; no re-aplicar lógica cuando ya tenemos la ruta bien.
Si tarda en ponerla bien, que quede en estado **recalculando**." → El historial dibujaba los fixes
CRUDOS y (en un intento previo) re-snappeaba en cada apertura. Mal: snappear UNA vez y guardar eso.

## Diseño
- **`routeSnapped: Boolean`** nuevo en `UserParking` + entity + DTO + 4 mappers + deserializer
  Firestore (`FIELD_ROUTE_SNAPPED`). **Migración Room v17** (`routeSnapped INTEGER NOT NULL DEFAULT 0`).
- **Al confirmar** (`ConfirmParkingUseCase`): `routePolyline` = crudo, `routeSnapped = false` (default)
  — entrada para el ajuste, NO lo que se dibuja.
- **Worker post-aparcar** (`EnrichParkingSessionWorker`, ya corre y ya usa Overpass): tras geocodificar,
  `snapRoute()` lee la ruta cruda de la sesión (`getSessionById`) → fetch calles → `TrailMapMatcher.snap`
  → `updateParkingSessionRoute(id, snappedPolyline, snapped=true)`. Best-effort: si no hay red para las
  calles → `Result.retry()` (backoff); tras `MAX_RETRIES` acepta el crudo como final (`snapped=true`)
  para que el historial no se quede "recalculando" eterno. Snap **una vez**, nunca al dibujar.
- **Repo**: `getSessionById` + `updateParkingSessionRoute(id, polyline, snapped)` (Room `updateRoute`
  + `enqueueSaveNewParkingSession` → sincroniza polyline+flag a Firestore, local+remoto).
- **Historial** (`ParkingLocationScreen`): dibuja la ruta **solo si `routeSnapped`**; si
  `routePolyline != null && !routeSnapped` → chip **"Recalculando ruta…"** (9 locales); si null → nada.

## Por qué no reusar la ruta ya-ajustada en vivo
La línea ajustada en vivo vive en la capa UI (`HomeTripController`); el aparcamiento se persiste desde
el SERVICIO (auto-detección confirma en background, sin UI). Cruzar esa frontera en el instante del
park es una carrera poco fiable → el worker recalcula UNA vez, determinista. El crudo es entrada
transitoria (segundos), no una copia permanente que se re-procese.

## Ficheros
- Modelo/persistencia: `UserParking`, `UserParkingEntity`, `ParkingHistoryDto`, `ParkingSessionMapper`
  (4), `RemoteUserProfileDataSourceImpl` (deserializer+FIELD), `AppDatabase` v17, `Migrations`
  (`MIGRATION_16_17`), `AndroidPlatformModule`+`IosPlatformModule` (registro).
- DAO `updateRoute`; repo interface+impl (`getSessionById`, `updateParkingSessionRoute`); 2 fakes.
- Worker `EnrichParkingSessionWorker` (snap). UI `ParkingLocationScreen` (chip) + strings 9 locales.
- Tests + doc.

## Relacionados
DET-ROUTE-TRACK-001 (persistencia base, master) · ROUTE-LINE-ONROAD-001 (matcher v5) ·
[[feedback_dto_field_parity]] (auditar serializers al añadir campo).
