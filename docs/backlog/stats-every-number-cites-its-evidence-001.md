# STATS-EVERY-NUMBER-CITES-ITS-EVIDENCE-001 · Auditoría del sistema de estadísticas: ninguna cifra inventada

**Estado:** ✅ Done (22-09-2026) · mergeado a master vía squash · 3.630 tests verdes (`testAll`)

## Problema
La pantalla de Actividad muestra métricas (gasto aprox., combustible, CO₂, «buscando plaza»,
duración media, hora punta, día habitual, sitio habitual, «detectados solos», plazas cedidas,
comparativas ×N) cuya cadena de evidencia no está auditada. Caso concreto que lo demuestra: un
martes en el que el usuario fue directo a casa sin buscar plaza, el gráfico de actividad muestra
~6 min de «buscando plaza».

## Doctrina violada
La misma doctrina de detección extendida a las cifras: *fallo asimétrico — mejor un hueco («sin
datos») que un dato falso*. Regla nueva que esta tarea instaura:
> **ABSENCE OF EVIDENCE = ABSENCE OF DATA.** Toda cifra mostrada procede de un dato real, de una
> fórmula determinista sobre datos reales, o de una estimación identificada como tal. Sin datos
> suficientes → «Sin datos», nunca un default que parezca dato.

## Señales / datos disponibles
Evidencia remota (Firestore `users/{uid}/parkingHistory`, leída 22-09):
- El campo `searchTimeMs` existe **por sesión** y nació ~13-09 (todas las sesiones anteriores lo
  tienen ausente). No hay campo de «tiempo buscando» agregado: la barra semanal se deriva en cliente.
- **Martes 22-09, sesión `3d95ffc8`**: `detectionPath = user` (pin manual, 00:26) y
  `searchTimeMs = 387.236` (~6,5 min) → la barra del martes.
- **Martes 15-09, sesión `3b2dad8c`**: `detectionPath = unattended_timeout` (cierre por red de
  seguridad) y `searchTimeMs = 417.001` (~7 min).
- **Martes 15-09, sesión `632f6ee8`**: `kinematic+egress`, `searchTimeMs = 55.999` (~1 min).
- Es decir: dos de los tres caminos que hoy asignan búsqueda no son confirmaciones normales
  (manual y timeout) — el tiempo asignado mide LATENCIA DE CONFIRMACIÓN, no búsqueda.
- UI (mapeada): tiles en `VehicleStatsBlock.kt` (statTiles/statTileKeys), barra semanal en
  `HistoryWeeklyChart.kt` + `VehicleHistoryCalculator.buildActivityBuckets`, deltas ×N en
  `StatDeltaFormat.kt` (previous null/0 → sin badge). Política deliberada «campo null → sin tile»
  ya existe; el problema está aguas arriba: el dato llega non-null sin merecerlo.

## Diseño
**El invariante, en UN sitio:** *un reloj de sesión solo lleva instantes MEDIDOS; el reloj de
procesado es solo la cota «no más tarde de» cuando la hora es genuinamente desconocida.* Tres
aplicaciones del mismo invariante:
1. **`ParkingSearchTime` v2** — la parada se halla por geometría (último fix a ≤35 m del pin), no
   por el reloj del ancla; veredicto de tres valores: `null` no medible · `0` llegada directa
   MEDIDA (fin del «suelo» de 30–60 s) · `>0` vueltas demostradas (ratio 1.8 + exceso 250 m +
   ≥90 s), cronometradas hasta la parada. Independiente del carril que confirme.
2. **Inicio de sesión** — `ConfirmParkingUseCase` fecha el pin por el fix medido (acotado a
   plausibilidad); el supersede del DAO cierra al predecesor con el inicio medido del sucesor.
3. **Fin de sesión** — `processConfirmedDeparture(departedAtMs)` sin default; el worker pasa el
   instante medido del cruce; watchdog/still-parked declaran null.

Honestidad de agregación: gates de cobertura en la card de recap (deltas + distancia solo
completa); `InsightValues.isUp` nullable (sin baseline no hay flecha); la card de insights salta
tendencias sin dirección.

Datos v1 ya almacenados (irrecuperables — la polilínea no guarda relojes): `MIGRATION_19_20` los
ANULA y marca `pendingSync` (la anulación viaja al espejo por el outbox); el reconcile de
`searchTimeMs` pasa a autoridad local para que la nube no los resucite.

Documentos: auditoría en `docs/analytics-data-audit.md` · contrato por métrica en
`docs/analytics-data-contract.md` · entrada en PARKING-DETECTION.md §2.

## Criterio de éxito
- Cada métrica de Actividad tiene su ficha en el contrato con fuente primaria + fórmula + fallback. ✅
- El caso «martes directo a casa» produce 0 medido (y la latencia de un pin manual/nudge/timeout
  no puede volver a facturarse como búsqueda), con tests que lo fijan. ✅
- `./gradlew testAll` verde.

## Consumidores auditados

**`searchTimeMs` (semántica nueva: null/0/>0):**
- Productor único: `ConfirmParkingUseCase` → `ParkingSearchTime.measure` — **cerrado** (v2).
- Portadores puros (mappers, DTOs, `SharedCarMirror`/`SharedCarHistory`, data sources remotos,
  `RouteGeometryPrivacy`): tipo `Long?` sin interpretar — **exentos**.
- `UserParkingReconcile` — **cerrado**: autoridad local si existe fila local.
- Agregadores (`sumMeasuredSearchTime`, `ParkingHeatmap`, `ChartMetric`, `ParkingClusters`,
  `ComputePeriodStats`, `DeriveStatsInsights`, `MonthRecapWorker`): un 0 medido suma/promedia
  como medida y cuenta en cobertura — exactamente lo deseado — **cubiertos por convergencia**.
- `FakeData` (previews 4/7 min): datos de galería — **exento**.

**`session.location.timestamp` (ahora = parada medida, antes = reloj del confirm):**
- `EvaluateArEnterArmUseCase` / `DetectParkingDepartureUseCase` (admisibilidad «estampado tras el
  nacimiento de la sesión» [DET-SESSION-BIRTH-001]): el nacimiento ahora es la parada REAL — el
  ENTER re-entregado del viaje que CREÓ el parking sigue siendo anterior → sigue inadmisible;
  un embarque entre parada y confirm pasa a admisible, que es verdad — **cerrado (mejora)**.
- `MeasuredDepartureInstant` (candidato > inicio): **mejora** (cota verdadera).
- `ParkingSafetyNetWorker` / iOS (`sessionAgeMs`): edad honesta — **mejora**.
- Guards de repark/aserción en Confirm (edad del pin previo): pin más «viejo» = guard menos
  agresivo, que es lo honesto; los caminos backfill/manual ya lo esquivaban — **aceptado**.
- `HistoryEntitlementGate`, ventanas de `VehicleHistoryCalculator`/`StatsPeriod`/recap, orden de
  historial, widget, filas de Home, `relativeTimeText` («aparcado desde»): pasan a decir la
  verdad — **mejora**, alineada con [PARK-A-CORRECTION-MOVES-THE-PLACE-NOT-THE-CLOCK-001].
- `EvaluateSharedCarWatchUseCase` + `SharedCarMirrorWriter` (`parkedAtMs`): ambos lados usan el
  mismo campo — **coherente**.
- `getPreviousSession`/`EnrichParkingSessionWorker` (orden temporal): orden preservado — **exento**.

**`clearActiveByVehicle`/`clearActiveOrphans` (endedAtMs propio):** llamadores =
`replaceActiveSession` (cerrado: inicio del sucesor) y `SharedCarFenceReconciler` (cerrado:
reloj del barrido como cota, la hora del grupo no la medimos nosotros).

**`processConfirmedDeparture`:** 3 llamadores de producción cerrados (worker con instante medido;
watchdog Android y still-parked iOS con null declarado) + tests actualizados.
