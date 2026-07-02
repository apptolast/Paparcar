# SYNC-RECONCILE-001 — reconciliar el sync inbound (fin del remote-wins ciego)

**Fecha:** 2026-07-01
**Estado:** BACKLOG (preparada — no empezada)
**Prioridad:** media-alta (corrige pérdida de escrituras locales offline; habilita mutaciones offline seguras)
**Relacionada:** banner de conexión [CONN-BANNER-001], solo-lectura offline (interino), [[project_arch_cleanup_001]] (VehicleActiveStatePolicy)

## Problema (escenario real)

Escritura local optimista pisada por un sync remoto. Ejemplo con vehículo activo:

1. Usuario cambia el vehículo activo **offline** → Room actualizado; la escritura a Firestore queda **en la cola de mutaciones offline del SDK** (aún no en el servidor).
2. Usuario reinicia la app.
3. Bootstrap corre `syncFromRemote` → lee Firestore **antes** de que la cola se vacíe → devuelve el valor **viejo**.
4. `syncFromRemote` hace **delete + replace ciego** → **pisa el cambio del usuario**. El vehículo activo revierte solo (y la atribución de detección apunta al coche equivocado).

## Causa raíz (con referencias)

`composeApp/src/commonMain/kotlin/io/apptolast/paparcar/data/repository/VehicleRepositoryImpl.kt`

- `syncFromRemote(userId)` (línea ~87) — **remote-wins ciego**:
  ```kotlin
  dao.deleteByUser(userId)   // ~107
  dao.upsertAll(normalized)  // ~108
  ```
  Comentario actual: *"pure remote sync — local state is overwritten by remote"* [VEHICLES-001]. Machaca Room entero con lo que devuelva Firestore, sin mirar si hay escrituras locales pendientes. Esto también **borra filas creadas offline** aún sin subir.
- `setActiveVehicle(id)` (línea ~152) — YA es offline-first correcto: escribe Room primero y luego **directo a Firestore** (`updateVehicleActiveFlag`/`updateDefaultVehicleId`), envuelto en `runCatching`. **No hay worker propio**: el SDK de Firestore (GitLive) ya encola la escritura offline y la reproduce ordenada al reconectar. El outbound NO es el problema; el problema es el inbound.

Nota GitLive/Firestore: `documentReference.set(...).await()` **completa al ACK del servidor** (no al escribir la cache local). Offline queda suspendido/pendiente hasta reconectar → eso es, de facto, la cola de salida y una **señal fiable de "confirmado"**.

## Qué NO hacer (descartadas, con motivo)

- **Encolar workers y que el sync los espere** (idea evaluada 2026-07-01): en cold-start **offline** la cola no puede vaciarse (no hay red) → "el sync espera a los workers" = espera a tener conexión = **bloquea el bootstrap** (o exige timeout). Además **duplica** la cola de mutaciones que Firestore ya tiene → conflictos. Rechazada.
- **Modal bloqueante tipo YouTube**: no aplica a una app offline-first con datos locales útiles + servicio de detección que escribe offline igual. Rechazada (ver notas de la sesión).

## Solución recomendada: reconciliar en el inbound (no sobrescribir)

El arreglo está en el **inbound**, no en el outbound (Firestore ya encola bien).

1. **Marca de pendiente + timestamp por fila.** Añadir a las entidades editables en device:
   - `updatedAt: Long` (client clock; idealmente monótono / server timestamp donde se pueda).
   - `pendingSync: Boolean` (o `pendingSince: Long?`) = fila con mutación local aún sin ACK del servidor.
2. **Al mutar local:** aplicar el cambio + `pendingSync=true` + `updatedAt=now`.
3. **`syncFromRemote` pasa de delete+replace a MERGE por id:**
   - Para cada fila: si local está `pendingSync` **o** su `updatedAt` es más nuevo que el remoto → **conservar local**; si no → tomar remoto.
   - **Nunca** `deleteByUser` a ciegas (borra filas creadas offline sin subir). Borrar solo filas que el remoto confirma eliminadas y que NO están pending.
   - **Reaplicar la invariante** post-merge (p. ej. single-active vía `VehicleActiveStatePolicy.normalizeSingleActive`).
4. **Limpiar `pendingSync`** SOLO cuando el outbound confirma (el `set().await()` retorna = ACK servidor). Mientras offline, la fila sigue pending → protegida en el siguiente bootstrap.
5. **(Complemento barato)** No correr el sync destructivo mientras haya escrituras pendientes: Firestore expone `metadata.hasPendingWrites`; o usar un listener (aplica pending por latency-compensation) en vez de un `get()` one-shot que lee estado de servidor viejo.

### Aplicabilidad por entidad
- **Reconcile** (el cliente las edita): `vehicles` (activo, matrícula, BT, color, size, carbody), `zones`, `user_profile` (defaultVehicleId, prefs sincronizadas), `user_parking` sesiones creadas/editadas en device.
- **Puede seguir remote-wins**: datos puramente del servidor que el cliente nunca edita (p. ej. `spots` de la comunidad).
- Aplicar por-entidad; no hay una única política global.

## Ficheros afectados (estimación)
- `data/repository/VehicleRepositoryImpl.kt` — `syncFromRemote` → merge; `setActive`/`save`/`delete`/`updateBluetoothDevice` → set `updatedAt`+`pendingSync`; limpiar pending al ACK.
- Room: `VehicleEntity` (+ `updatedAt`, `pendingSync`) + `VehicleDao` (upsert-merge, queries por pending) + migración de esquema.
- Repos análogos con el mismo patrón remote-wins: **auditar** `ZoneRepositoryImpl`, `UserParkingRepository` impl, `UserProfile` sync, y el `SpotRepository` (este probablemente se queda remote-wins).
- Tests: reconcile (local-pending gana a remoto viejo; remoto gana si local no-dirty y más viejo; no se borran filas pending; invariante single-active tras merge; limpiar pending al ACK).

## Riesgos
- **Skew de reloj** para el LWW por `updatedAt` client → mitigar con server timestamps donde se pueda, o un contador lógico monótono.
- **Migración Room** (columnas nuevas) → `fallbackToDestructiveMigration` ya configurado, pero validar que no se pierde cache al subir versión.
- Definir **política de conflicto por entidad** explícita (qué campo gana). Documentar.
- El **servicio de detección** sigue escribiendo offline (auto-park) → asegurar que el merge NO borra sesiones creadas offline por el servicio (crear = append, más seguro que editar, pero cuidarlo).
- Regresión del gate de cold-start (ver AUTH-002 offline-safe session, gating DET-READY).

## Criterio de aceptación
- Cambiar vehículo activo offline + reiniciar (antes de que la cola suba) → el activo **se conserva** (no revierte).
- Crear/editar coche o zona offline + reiniciar → **no se pierde** ni lo pisa el remoto viejo.
- Online normal cross-device sigue convergiendo (remoto más nuevo gana; reconcile es superset de remote-wins, no lo rompe).
- Invariante single-active intacta tras cualquier merge.
- Suite verde + tests nuevos de reconcile.

## Interino / hermano: SOLO-LECTURA OFFLINE (READONLY-OFFLINE, dentro de este ticket)

Mientras no exista el reconcile, la vía segura es **solo-lectura offline** para las mutaciones remote-dependientes: si no se muta offline, **no hay conflicto que reconciliar**. Según aterrice el reconcile, se **re-habilitan** esas mutaciones offline y el solo-lectura se encoge. Aquí decidimos qué parte hacer primero.

### Acciones a BLOQUEAR offline (fallan/divergen de verdad)
- Avisar/ocupar plaza — `SendSpotSignalUseCase` (red inmediata, `SpotRepositoryImpl.sendSpotSignal` sin cola).
- Guardar/borrar zona — `ZoneRepository.saveZone/deleteZone` (Firestore awaited).
- Guardar/editar/borrar vehículo — `VehicleRepositoryImpl.saveVehicle/deleteVehicle` (Firestore awaited, `.getOrThrow()`).
- **Cambiar vehículo activo** — `setActiveVehicle` (mirror best-effort → **revert por `syncFromRemote`**, justo el problema de este ticket).
- Config Bluetooth — `updateBluetoothDevice` (Firestore awaited).
- **Borrar cuenta** (`DeleteAccountUseCase`) y **logout** (`authRepository.signOut()`, riesgo lockout).

### Acciones que se DEJAN (local, seguras)
Tema, unidades, idioma, toggles notif/auto-detect, tipo de mapa, filtros, búsqueda, selección, navegación, onboarding.

Matiz: aparcar/soltar/mover plaza son técnicamente offline-first (Room + WorkManager reintenta). El park manual **ya está bloqueado offline** (`HomeViewModel.kt:770`, `HomeEffect.OfflineActionBlocked`). Por coherencia se tratan como bloqueadas; decisión de producto si algún día se permiten local + defer publish.

### Alcance (análisis 2026-07-01): **~22 ficheros core (~28 con pulido), 0 en domain/data**
- **Home** ya inyecta `ConnectivityObserver` y tiene el patrón `OfflineActionBlocked`+snackbar → solo añadir `isOffline` al State + guards (release, spot-signal, zonas). Barato.
- **Vehicles / Settings / VehicleRegistration / BluetoothConfig** NO tienen connectivity → cablear DI + ctor + `isOffline` en su State. Grueso.
- **DI**: `PresentationModule` — los `viewModelOf` auto-resuelven al añadir el param, pero **`BluetoothConfigViewModel` usa factory explícita** → añadir `get()` a mano.
- Alternativa: pasar `AppState.isOffline` desde `App.kt` hacia abajo (evita inyectar por-VM), aunque el patrón actual es inyección por-VM.

### Mecanismo UI (recomendado): híbrido
1. **Control deshabilitado + hint** (`enabled = !isOffline`) — señal clara, evita el tap; el porqué lo da el **banner** global (no repetir explicación en cada botón).
2. **Snackbar `OfflineActionBlocked` como red de seguridad** para lo que quede tappable (p. ej. dentro de diálogos) — replicar el patrón de Home en las otras 4 features (nuevos `*Effect.OfflineActionBlocked`).
Dejar habilitado todo lo local.

### Geocoder offline (misma familia, va aquí)
`android.location.Geocoder.getFromLocation` es **network-backed** (la red la hace el SO, no hay Ktor en `getAddress`; POI/Overpass y el buscador Photon sí son HTTP explícito). Offline solo resuelve por el **cache de Room** (`RoomGeocoderCacheDataSource`); en cache-miss la Fase 1 devuelve vacío → `hasContent=false` en `CameraLocationRow` → **skeleton infinito** (el "no carga"). Fix: offline + sin contenido → cortar el shimmer y mostrar fallback (dirección cacheada si hay, si no **coordenadas** vía `peekTitle`, o "Dirección no disponible sin conexión"). Reusa el `isOffline`. Corregir además el comentario erróneo "local geocoder, no network" (`AddressAndPlaceRepositoryImpl.kt:35`).

### Diálogo de reintentar — ✅ HECHO (RETRY-GATE-001, 2026-07-02)
`SplashViewModel.bootstrap` ya no aborta a `BootstrapFailure.Offline` en cualquier cold-start offline: gateado a **offline + sin cache** (`vehicleRepository.hasVehicles`) → con cache salta el sync remoto, resuelve ruta desde Room y entra (banner informa); sin cache → diálogo retry. Además el diálogo (`App.kt`) **auto-continúa al reconectar** (`LaunchedEffect(isOffline)`→`retry()`) y da feedback "Sigue sin conexión" (`error_bootstrap_still_offline`, 9 locales) al tocar Reintentar estando offline. Tests en `SplashViewModelTest`. (El resto de este ticket sigue pendiente.)
