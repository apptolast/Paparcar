# Paparcar — Worker bugs diagnosticados 2026-05-25

Origen: análisis de logs en `diagnostics/2026-05-14/` y `diagnostics/2026-05-18/`.
Ambos bugs son P0 — bloquean parking sessions en remoto. Reproducibles en todos los dispositivos.

## Status legend
✅ **Done** — merged to master
🔵 **Branch ready** — work complete, awaiting merge
⚪ **Pending** — not started
🔴 **P0** — bloquea beta

---

## BUG-WORKER-001 · Race condition LocationUpdateSyncWorker → NOT_FOUND 🔴

**Ticket:** `BUG-WORKER-001`
**Rama:** `bugfix/BUG-WORKER-001-location-update-race`
**Prioridad:** P0 — crítico | **Esfuerzo:** Pequeño
**Estado:** ⚪ Pending

### Root cause

`WorkManagerParkingSyncScheduler` encola `ParkingSyncWorker` (crea el doc en Firestore via `set()`)
y `LocationUpdateSyncWorker` (actualiza dirección via `update()`) de forma **independiente** en paralelo.
`LocationUpdateSyncWorker` dispara antes de que el documento exista → `FirebaseFirestoreException: NOT_FOUND`.

Logs: `diagnostics/*/oppo.log` y `diagnostics/*/redmi-note-11.log`:
```
W PARKDIAG/LocationUpdateSyncWorker: ⚠ retrying session=... attempt=0/3
  FirebaseFirestoreException: NOT_FOUND: No document to update: .../parkingHistory/...
```

### Impacto

El campo `address` y `placeInfo` **nunca llega a Firestore**. El historial remoto queda sin geocoding.
Afecta al 100% de las sesiones auto-detectadas con `EnrichParkingSessionWorker`.

### Fix

En `WorkManagerParkingSyncScheduler.kt`, encadenar los workers con WorkManager `.then()`:

```kotlin
// Antes — paralelo, sin dependencia:
workManager.enqueueUniqueWork("parking_sync_$sessionId", REPLACE, syncRequest)
// ...luego, en scheduleLocationUpdate():
workManager.enqueueUniqueWork("location_update_$sessionId", REPLACE, locationRequest)

// Después — cadena garantizada:
workManager.beginUniqueWork("parking_chain_$sessionId", REPLACE, syncRequest)
    .then(locationRequest)
    .enqueue()
```

Esto garantiza que `LocationUpdateSyncWorker` solo corre después de que `ParkingSyncWorker`
complete con éxito. Si `ParkingSyncWorker` falla y entra en backoff, `LocationUpdateSyncWorker`
espera también.

### Archivos a tocar
- `WorkManagerParkingSyncScheduler.kt` — refactor de `schedule()` + `scheduleLocationUpdate()`
  para usar `beginUniqueWork().then()` en lugar de dos `enqueueUniqueWork` independientes.
- `ParkingSyncWorkerTest.kt` — añadir test que verifica que `LocationUpdateSyncWorker`
  no se encola sin `ParkingSyncWorker` como prerequisito.

---

## BUG-WORKER-002 · ParkingSyncWorker cancelado — JobCancellationException 🔴

**Ticket:** `BUG-WORKER-002`
**Rama:** `bugfix/BUG-WORKER-002-sync-worker-cancellation`
**Prioridad:** P0 — crítico | **Esfuerzo:** Pequeño
**Estado:** ⚪ Pending

### Root cause

`ParkingSyncWorker` lanza `kotlinx.coroutines.JobCancellationException` en el primer intento (0/5).
El Job del CoroutineWorker se cancela mientras la escritura a Firestore está en vuelo.
Causa probable: battery optimization agresiva del OEM (Redmi) mata el proceso, WorkManager cancela
el Job del CoroutineWorker antes de que la corutina termine.

Logs: `diagnostics/2026-05-14/redmi-note-11.log`:
```
W PARKDIAG/SyncWorker: ⚠ retrying session=a19685e8... attempt=0/5
  kotlinx.coroutines.JobCancellationException: Job was cancelled; job=JobImpl{Cancelling}@544a73b
```

### Impacto

La sesión de parking **nunca llega a Firestore**. El aparcamiento se guarda en Room local pero
el usuario no aparece en el mapa para otros usuarios. Bug de pérdida de datos.

### Fix

Envolver la escritura crítica en `withContext(NonCancellable)` para que no se interrumpa
aunque el Job padre sea cancelado:

```kotlin
// En ParkingSyncWorker.doWork():
return runCatching {
    withContext(NonCancellable) {
        previousSessionId?.let { prevId ->
            userProfileDataSource.updateParkingSessionActiveFlag(userId, prevId, false)
        }
        userProfileDataSource.saveParkingSession(userId, newSession)
    }
}.fold(...)
```

`NonCancellable` garantiza que una vez iniciada la transacción Firestore, se completa aunque
WorkManager intente cancelar el Job (ej. proceso muerto por OEM). El worker igualmente
puede ser re-encolado por WorkManager si falla, pero no se cortará a medias.

### Archivos a tocar
- `ParkingSyncWorker.kt` — añadir `withContext(NonCancellable)` alrededor del bloque de escritura.
- `ParkingSyncWorkerTest.kt` — test que verifica que una cancelación externa no impide la escritura.

---

## Relación con PIPE-004

PIPE-004 propone fusionar `EnrichParkingSessionWorker` + `LocationUpdateSyncWorker` en un solo worker.
**BUG-WORKER-001 debe resolverse antes de PIPE-004**, ya que el encadenamiento `.then()` que propone
BUG-WORKER-001 sería el contrato que PIPE-004 simplificaría al fusionar ambos workers.

## Pendiente: activar Crashlytics en MCP

El MCP de Firebase no cargó herramientas de Crashlytics porque `firebase.json` solo declara Firestore.
Para activarlas, añadir al `.mcp.json`:
```json
"args": ["-y", "firebase-tools@latest", "mcp", "--only", "core,firestore,crashlytics"]
```
y reiniciar Claude Code. Una vez activo, hacer un segundo diagnóstico contra los crashes reportados
automáticamente por Crashlytics en producción.
