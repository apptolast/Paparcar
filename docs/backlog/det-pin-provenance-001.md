# DET-PIN-PROVENANCE-001 — provenance del pin: qué trigger/path colocó cada aparcamiento

> **Estado**: IMPLEMENTADO 2026-07-21, rama `feature/DET-PIN-PROVENANCE-001` (desde master). Sin merge.
> Room v12→v13 (detectionPath) + `armEvidence`/`detectionPath` espejados a Firestore; 18 ficheros,
> tests de mapper + reconcile ampliados, `testProdDebugUnitTest` verde, prod+mock compilan, schema
> 13.json generado. PENDIENTE: device + field-test + verificación iOS (cambio 2 líneas, mismo patrón).
> Origen: field-test 20-jul — hubo que RECONSTRUIR que el pin fantasma venía del backfill cruzando
> `reliability 0.5` + timing de sesiones. Prioridad: **P1** (herramienta de diagnóstico, no toca la
> lógica de detección). Ver [[feedback_detection_trigger_provenance]] y `project_det_field_2026_07_20`.

## Problema

El pin persistido (`users/{uid}/parkingHistory` en Firestore) **no lleva provenance**: solo
`detectionReliability`, que es ambiguo —
- `0.5` = backfill de la red de seguridad **o** unattended-timeout,
- `0.9` = `steps+egress` **o** `vehicle-exit+window+egress` **o** varios,
- `0.85` = kinematic, `0.95` = BT, `1.0` = user.

`armEvidence` y `tripMaxSpeedMps` viven en Room **local-only** (no se espejan a Firestore), y el
`pathLabel` de confirmación **no se persiste** en el pin en absoluto. Resultado: un diagnóstico
remoto (nuestro flujo habitual vía MCP) no puede atribuir un pin a su origen de un vistazo — hay
que cruzar a mano con las sesiones de `diagnostics/…`.

## Objetivo

Que cada pin declare, visible en Firestore:
1. **`detectionPath`** (nuevo) — el path de confirmación: `steps+egress` / `kinematic+egress` /
   `vehicle-exit` / `unattended_timeout` / `user` / `bt` / **`safety_net_backfill`**.
2. **`armEvidence`** (ya existe en dominio/Room, hoy local) — espejado a Firestore: el trigger de
   armado (`Manual` / `VerifiedBySpeed` / `VerifiedByVehicleEnter` / `Unverified`).

Un pin diría, p.ej.: `path=steps+egress · arm=VerifiedByVehicleEnter` (coordinator vivo real) vs
`path=safety_net_backfill · arm=Unverified` (red de seguridad) — el fantasma se identifica solo.

## Diseño

Paridad DTO end-to-end (ver [[feedback_dto_field_parity]]) — auditar TODOS los serializers:
1. **Dominio**: `UserParking` + `detectionPath: String?` (espejando `armEvidence`).
2. **`ConfirmParkingUseCase`**: aceptar `detectionPath: String?` y guardarlo en la `UserParking`
   (ya recibe `armEvidence`). Es el punto de convergencia único.
3. **Call sites** — pasar el path:
   - `CoordinatorParkingDetector.runConfirm` — ya tiene el `pathLabel`, propagarlo.
   - `ParkingBackfillWorker` — `detectionPath = "safety_net_backfill"`.
   - BT strategy — `"bt"`. Manual/user — `"manual"`/`"user"`.
4. **Room**: columna nueva en la entity de parking + mapper entity↔domain (ambos sentidos) +
   migración aditiva (v_N → v_N+1) + schema json versionado. Espejar `armEvidence` NO hace falta en
   Room (ya está); solo `detectionPath`.
5. **Firestore**: DTO `ParkingHistoryDto` + `detectionPath` **y** `armEvidence` (este último hoy
   ausente) + los dos mappers (`toParkingHistoryDto()` y su reverso) + `RemoteUserProfile…`.
   `Enum.valueOf`/parse en `runCatching` (tolerar valores desconocidos/legacy → null).
6. **Legacy/nulos**: pines viejos → `detectionPath=null` (desconocido). El diagnóstico lo trata como
   "pre-provenance".

## Fuera de alcance

- No cambia ninguna decisión de detección ni de reliability — es puramente observabilidad.
- No re-backfill de datos históricos (pre-release; los pines viejos quedan `null`).
- UI: de momento NO se muestra al usuario (es dato de diagnóstico). Si algún día se enseña, iría
  como sublínea de dato, no aquí.

## Validación

- `assembleProdDebug` + `assembleMockDebug` verdes; tests de mappers (round-trip conserva
  `detectionPath` + `armEvidence`); migración Room testeada (aditiva, no destructiva).
- Field-test: tras un aparcamiento real, el pin en Firestore debe traer `detectionPath` poblado; un
  backfill debe traer `safety_net_backfill`.

## Criterio de éxito

De un pin en Firestore, sin cruzar con sesiones, se sabe qué trigger/path lo colocó.
