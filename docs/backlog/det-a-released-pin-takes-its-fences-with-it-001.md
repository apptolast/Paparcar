# DET-A-RELEASED-PIN-TAKES-ITS-FENCES-WITH-IT-001 · Una valla ENTER huérfana dispara para siempre y nadie la quita

**Estado:** ✅ Done (01-09-2026) — mergeado a master por squash; el hash vive en `MEMORY.md`.

## Problema

Field 31-08 (Oppo, `parkdiag` líneas 24877-24890): el pin `d194668c` (Cañada) se liberó a las
21:22:44 (`Depart → Confirmed` → clear) y **su valla ENTER seguía registrada 12 minutos después**:

```
21:22:44  Depart attempt=2 → Confirmed          ← processConfirmedDeparture corre aquí
21:22:45  ClearActiveParkingSessionWorker SUCCESS
   (ni una línea de removeGeofence entre medias)
21:34:26  FenceEnter: ✓ re-entered own fence (enter_d194668c…) → enqueueing anchor re-seal check
```

Las vallas se registran `NEVER_EXPIRE`: una huérfana dispara **para siempre** — despertares
espurios, safety-net checks que no tocan, y material para el churn de re-entregas de GMS.

## Los dos defectos (medidos, no inferidos)

1. **El removal es MUDO en el carril que más se usa.** `ProcessConfirmedDepartureUseCase:211` hace
   `geofenceService.removeGeofence(geofenceId)` y **tira el `Result`** — ni log en fallo. Que los
   fallos existen está medido: el 30-08 21:27:34 otro call site (Confirm, que SÍ loguea) registró
   `⚠ removeGeofence(785dabe3) failed (continuing)`. Del 31-08 no se puede saber si el removal
   falló o ni corrió — **esa indistinguibilidad ES el defecto**: una prohibición sin testigo no es
   un chequeo. Barrido de call sites: 8; loguean el fallo 4 (`ConfirmParkingUseCase`,
   `ReleaseActiveParkingSessionUseCase`, `SwapActiveVehicleFencesUseCase`, el orphan-cleanup del
   servicio vía `runCatching`); mudos 4 (`ProcessConfirmedDepartureUseCase`,
   `FinalizeDeducedDepartureUseCase`, `RevertParkingUseCase:102` guarda el result pero ¿lo mira?,
   `UpdateParkingLocationUseCase`).
2. **El carril ENTER no tiene autolimpieza y el EXIT sí.** El EXIT limpia huérfanas desde
   2026-07-11 (`decision.orphanGeofenceIds` → remove + `OrphanCleaned` + ledger `ORPHAN`, con la
   lección de que un lookup FALLIDO no es huérfana). `GeofenceEnterReceiver` en cambio ni mira si
   la valla corresponde a una sesión viva: encola el check y ya. Una `enter_` huérfana no se cura
   NUNCA — su único observador la ignora.

⛔ **Por qué el janitor no puede cubrirlo**: `removeGeofence` hace `FenceRegistrationLedger.forget`
ANTES del removal de GMS (a propósito — mejor re-registrar una borrada que saltarse una viva). Si
el removal de GMS falla después del forget, el ledger ya la olvidó, GMS no expone lista de vallas →
**la única señal de que una huérfana existe es su propio disparo**. La limpieza tiene que vivir ahí.

## Doctrina

- «Una prohibición sin TESTIGO de población no es un chequeo» — el removal mudo.
- Sistemas, no parches: el EXIT ya resolvió este invariante («una valla que dispara sin sesión se
  quita»); el ENTER es el consumidor que quedó sin barrer.

## Diseño

1. **Testigo en el sitio único**: el log de fallo entra DENTRO de `GeofenceManagerImpl.removeGeofence`
   (un solo lugar cubre los 8 call sites, presentes y futuros), conservando el `Result` para quien
   quiera decidir más. Éxito a nivel debug con los tres ids.
2. **La cura en el disparo**: `GeofenceEnterReceiver` pasa los `requestId` que dispararon al
   `ParkingSafetyNetWorker` (que ya lee sesiones); el worker resuelve cada `enter_<id>` → si la
   lectura tiene ÉXITO y no hay sesión activa para `<id>`, `removeGeofence(<id>)` (quita las tres) +
   `OrphanCleaned`. **Fail-open en lookup fallido** — la lección del 2026-07-11: un read roto no
   convierte una valla viva en huérfana.

## Implementación (01-09)

- **Testigo en el sitio único**: `GeofenceManagerImpl.removeGeofence` loguea éxito (debug) y fallo
  (warn con causa) — cubre los 8 call sites de una vez; el `Result` se conserva.
- **`cleanOrphanEnterFences`** (commonMain, `domain/detection/OrphanEnterFences.kt`, testeable con
  los fakes existentes): filtra los ids entregados contra las sesiones activas, quita las huérfanas
  (`removeGeofence(baseId)` = las tres vallas) + `OrphanCleaned`. Best-effort: un removal fallido
  reintenta en el siguiente disparo de esa misma valla.
- **`GeofenceEnterReceiver`** extrae los base ids (strip `enter_`) y los monta en el
  `enqueueCheckNow`; **`ParkingSafetyNetWorker`** barre tras la lectura EXITOSA de sesiones (el
  early-return del read fallido ES el fail-open del 07-11), y ANTES del early-return de
  «sin sesiones» — sin sesiones es el caso más huérfano de todos.

## Criterio de éxito

- ✅ 4 tests (`OrphanEnterFencesTest`): huérfana barrida + evento; viva intacta; entrega mixta
  separa; lista vacía invisible. Falsación: sin el filtro → 2 rojos (el lado peligroso: barrer una
  viva). Suite completa **2088/0**, prod+mock compilan.
- ⏳ Campo: un `enter_` de pin liberado produce `OrphanCleaned` en vez de re-seal checks eternos.
- ⚠️ Sin cubrir (dicho): el hop receiver→workData→worker es I/O androidMain sin test, como el resto
  de carriles.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| 8 call sites de `removeGeofence` | ✅ cubiertos por el testigo único en el manager; sus logs propios quedan como contexto extra |
| Carril EXIT (`orphanGeofenceIds`) | sin cambios — ya tenía su barrido; este es su espejo ENTER |
| Vallas `witness_` | cubiertas por convergencia: `removeGeofence(baseId)` quita las tres |
| Wakes sin ids (periodic/sentry/detection-end) | exentos con test (`should_doNothing_when_noFenceIdsWereDelivered`) |
| Lookup de sesiones fallido | exento por construcción: return antes del barrido + comentario que lo ata al 07-11 |
