# DET-AUDIT-REMEDIATION-001 — Plan de endurecimiento de detección (post-auditoría)

- **Rama:** `fix/DET-AUDIT-001-detection-hardening` (creada desde `refactor/DET-SOLID-001-evidence-system` @ `32ce4265`).
- **Origen:** auditoría 2026-07-04 (`docs/audits/AUDIT-2026-07-04-full.md`).
- **Alcance de ESTA rama:** solo **detección**. El resto de la auditoría (CI/distribución, Firestore rules, arquitectura, data/sync, i18n) se aborda **después** de cerrar detección, en ramas propias (ver §Fases posteriores). Decisión del usuario 2026-07-05: "por ahora centrarnos en la detección".
- **Estado del HEAD base:** DET-SOLID-001 + ANCHOR-LOCK-001 + SAFETY-NET-001 + SIGMOTION-001 + OEM-KILL-001 + SPOT-OFFLINE-TTL-001 + SYNC-UP-GUARD-001. **Pendiente de gate de device** (no mergeado).

---

## 0. Estado re-verificado de los hallazgos (contra `32ce4265`, no el snapshot)

La rama avanzó 9 commits tras la auditoría. Re-verificado leyendo el código actual:

| # | Hallazgo | Estado hoy | Evidencia |
|---|---|---|---|
| **A1** | Departure fall-through → plaza fantasma con ENTER espurio andando | 🔴 **VIVO** | `RunDepartureCheckUseCase.kt:85-98` sin cambios |
| **A2** | BT sin gate de velocidad/parada en el fix de park | 🔴 **VIVO** | `BluetoothParkingDetector.kt:63-65` (solo accuracy) |
| **A3** | Ruta BT completa sin tests | 🔴 **VIVO** | no existe test de `BluetoothParkingDetector`/receiver/service |
| **A4** | BT walk-away sin `withTimeoutOrNull` → FGS+GPS vivos indefinidamente | 🔴 **VIVO** | `BluetoothParkingDetector.kt:75-83` |
| **M1** | Race de relevo de sesión (finally del singleton pisa la nueva) | 🟡 **PARCIAL** | FGS-destroy mitigado por `thisJob` guard (`CoordinatorDetectionService.kt:474`, [DETECT-SERVICE-RACE-001]); el `reset()` del singleton sigue con `.cancel()` no `cancelAndJoin` (`:396`) |
| **M2** | Hold post-confirm pierde el park si muere el GPS / kill | 🔴 **VIVO** | hold íntegro dentro de `.collect` (`CoordinatorParkingDetector.kt:491-527`); no se finaliza en el `finally` (`:665`) |
| **M3** | "Sí" del usuario en el hold se guarda a 0.90, no 1.0 | 🔴 **VIVO** | `:505-510` usa `pending.reliability`/`pending.pathLabel` |
| **M4** | Release manual no elimina la geocerca (huérfana NEVER_EXPIRE) | 🔴 **VIVO** | `ReleaseActiveParkingSessionUseCase.kt:65` limpia sesión, no llama `removeGeofence` |
| **M5** | Ancla de egreso migra con el peatón (bestStopLocation) | 🟢 **RESUELTO** | ANCHOR-LOCK-001: `isAnchorLocked` (`:978`) + freeze (`:995-1005`) |
| **M7** | Watchdog OFF / pérdida de GEOFENCE_EXIT por Doze/OEM | 🟢 **RESUELTO** (mitigado) | OEM-KILL-001 + SAFETY-NET + SIGMOTION |

**Cerrados fuera de esta rama (capa datos, relacionados con detección):** race de UserParking offline (A5/R1 de la auditoría) por `SYNC-UP-GUARD-001` (`ca5faca4`); TTL de reportes de spot en cola por `SPOT-OFFLINE-TTL-001` (`db4bb669`).

**Discrepancia doc↔código de detección (matriz S5):** `docs/detection/PARKING-DETECTION.md` afirma que "enter-precedes-exit" cubre el ENTER espurio andando; **no lo cubre** (A1). Corregir la fila al cerrar A1.

---

## 1. Objetivo

Cerrar las dos vías de **plaza fantasma** que DET-SOLID-001 no atacó (A1 departure, A2 Bluetooth) y las fugas menores de fiabilidad/recursos (M1-M4), sin regresar el comportamiento validado por el replay harness. Cada fix nace con su fixture o test de comportamiento.

**Principio rector (heredado de HANDOFF):** fallo asimétrico. Ante la duda, un **falso negativo** (no publicar / preguntar) es siempre preferible a un **falso positivo** (publicar una plaza que no existe).

---

## 2. Tareas (orden propuesto)

### Prioridad 1 — vías de plaza fantasma

#### T1 · A1 — Departure fall-through no debe publicar en silencio
- **Dónde:** `RunDepartureCheckUseCase.kt:81-98`, `DetectParkingDepartureUseCase.kt`.
- **Problema:** tras 3 `Inconclusive`, si `lastVehicleEnteredAt != null` confirma por fall-through y publica. Un `IN_VEHICLE_ENTER` espurio andando (quirk `ParkingDetectionConfig.kt:303-324`) + cruzar el radio a pie satisface la condición → plaza fantasma **y** borra la sesión real.
- **Opción elegida (a validar):** exigir **corroboración de desplazamiento** en el fall-through — distancia del fix actual a la plaza `>` radio+margen y creciente entre intentos; si no la hay, **degradar a prompt** ("¿Te has ido?") en vez de publicar. Reutiliza el patrón `ParkingDecision.Prompt` ya introducido en el lado park.
- **Test:** fixture de replay con ENTER espurio andando + salida a pie → assert `outcome != Processed` y `saveSpot == 0`. Añadir al harness (`DetectionTraceReplayTest`).
- **Doc:** corregir la fila S5 de la matriz.

#### T2 · A2 — Gate de parada en el fix de park de Bluetooth
- **Dónde:** `BluetoothParkingDetector.kt:63-70`.
- **Problema:** `parkingFix` es el primer fix con accuracy ≤ 50 m, **sin comprobar velocidad**. Un drop de BT real conduciendo captura un fix en movimiento; el "walk ≥30 m" se satisface por el propio avance del coche → park fantasma en carretera → EXIT → departure con velocidad de coche → spot fantasma.
- **Fix:** exigir `parkingFix.speed < config.stoppedSpeedThresholdMps` (o N fixes parados consecutivos) antes de fijar el candidato; abortar si se observa velocidad de conducción sostenida durante la ventana de walk-away.
- **Nota:** el guard de repark del coordinator no aplica a BT (`tripMaxSpeedMps=null` bypass por diseño), así que la protección tiene que estar **aquí**.

#### T3 · A3 — Primera suite de tests de la ruta Bluetooth
- **Dónde:** nuevo `commonTest`/`androidUnitTest`.
- **Prerrequisito arquitectónico:** extraer la decisión del detector BT a una función pura (estilo `EvaluateParkingDecisionUseCase`), sustituyendo `Location.distanceBetween` por el `haversineMeters` de domain → testeable en commonTest y reutilizable en iOS (A9-kmp de la auditoría; primer paso barato).
- **Casos:** disconnect sin fix (timeout), fix con mala accuracy, disconnect en semáforo (<30 m, debounce), reconnect race (cancelación), disconnect a velocidad (T2), walk-away con timeout (T4).

#### T4 · A4 — Timeout en la fase de walk-away de Bluetooth
- **Dónde:** `BluetoothParkingDetector.kt:75-83`.
- **Fix:** envolver el segundo `observeLocation().first { … ≥30 m }` en `withTimeoutOrNull` (10-15 min) con aborto limpio + telemetría del outcome. Evita FGS+GPS pegados indefinidamente (clase BUG-FGS-1xx) si el usuario aparca y no se aleja 30 m con GPS utilizable (garaje).

### Prioridad 2 — fugas de fiabilidad/recursos

#### T5 · M4 — Release manual elimina la geocerca
- **Dónde:** `ReleaseActiveParkingSessionUseCase.kt:64-65` (o en `clearActiveParkingSession` a nivel repo, dado que revert/departure ya la quitan individualmente).
- **Fix:** llamar `removeGeofence(session.geofenceId)` en el release. Elimina la huérfana NEVER_EXPIRE que hoy se deja en cada release manual (contenida por orphan-clean, pero cuesta un arranque de FGS + flash de notificación).

#### T6 · M3 — "Sí" del usuario en el hold gana con fiabilidad 1.0
- **Dónde:** `CoordinatorParkingDetector.kt:505-510`.
- **Fix:** cuando el hold se finaliza por `state.userConfirmedParking`, usar `config.reliabilityUserConfirmed` (1.0) y `pathLabel = "user"` en vez de `pending.reliability` (0.90). Preserva el dato oro en telemetría y respeta la precedencia "user tap always wins" (`:76`) — evita que el guard de repark re-promptee una confirmación humana explícita.

#### T7 · M2 — El hold no pierde el park si muere el stream
- **Dónde:** `CoordinatorParkingDetector.kt:491-527` (+ `finally` `:665`).
- **Problema:** todas las decisiones del hold se evalúan al llegar OTRO fix; si el GPS muere tras el confirm tentativo (usuario entra en edificio/garaje — el caso común) o el proceso es matado, el `PendingConfirm` vive solo en memoria y el `finally` hace `reset()` sin finalizarlo → park nunca guardado, sin notificación, en silencio.
- **Fix:** en el `finally`, si hay `pendingConfirm` no descartado, finalizarlo (`NonCancellable` ya disponible ahí). Valorar además un ticker de reloj independiente del stream para cerrar el hold por tiempo aunque no lleguen fixes. Ojo interacción con SAFETY-NET (que ya cubre parte del "sesión activa sin actividad"): comprobar que no hay doble guardado.
- **Riesgo:** medio — toca el corazón del coordinator; apoyarse en el replay y en un test clock-driven nuevo.

#### T8 · M1 — Cerrar la race de relevo de estado del singleton
- **Dónde:** `CoordinatorDetectionService.kt:396` (`cancelDetectionJob`).
- **Estado:** el facet de FGS-destroy ya está mitigado (`thisJob` guard). Queda el `reset()` del coordinator singleton: `.cancel()` es asíncrono, el `finally` de la sesión vieja puede correr tras arrancar la nueva y anular `currentSessionId`/seed.
- **Fix:** `detectionJob?.cancelAndJoin()` antes de relanzar (o encadenar el arranque en `invokeOnCompletion`), y/o hacer el reset del `finally` del coordinator condicional a "sigo siendo la sesión actual" (comparar sessionId). **Probabilidad baja**; hacerlo con cuidado para no bloquear el hilo Main del service.

### Prioridad 3 — deuda estructural (opcional en esta rama)

- **T9 · A12** — Extraer las fases del `invoke()` de ~400 líneas (`CoordinatorParkingDetector.kt:265-667`) a métodos privados (edge-detect AR, gating de fixes, dispatch por fase), como se hizo con `evaluateFix`. El replay hace el refactor seguro. Mejora la revisabilidad de todo lo anterior; puede ir **al final** de la rama o diferirse.
- **T10 · KDocs** — Limpiar KDocs que mienten sobre STILL/AR-rearm (`ParkingDetectionConfig.kt:17-21,97-113,466`, `ConfirmationPhase.kt:18-20`). Higiene barata.

---

## 3. Secuenciación respecto al gate de device

La auditoría recomienda A1 y A2 como los dos primeros tickets **post-gate**. Dos opciones:

- **Opción A (recomendada): fixes pre-gate.** T1/T2/T5/T6 son aditivos, bien acotados y con test → entran antes del gate y se validan en la MISMA salida de campo que DET-SOLID-001. Evita una segunda campaña de device. T7/T8 (tocan el core) y T3/T4 (BT, superficie sin field-test aún) pueden ir en una segunda tanda.
- **Opción B: gate primero.** Validar DET-SOLID-001 tal cual, mergear, y meter estos fixes después. Más conservador pero duplica el ciclo de campo.

**Ampliar el protocolo de device** (`docs/detection/PARKING-DETECTION.md:1238-1245`) con, como mínimo:
1. Aparcar → provocar/esperar un ENTER espurio andando → salir del radio a pie → **verificar que NO se publica plaza** (T1).
2. Aparcar y entrar en un edificio antes de 2 min (hold) → **¿se guarda el park?** (T7).
3. Si hay usuarios BT: drop de BT en marcha → **no debe confirmar park en carretera** (T2), y aparcar sin alejarse 30 m → el FGS no queda pegado (T4).

---

## 4. Fases posteriores (fuera de esta rama — "lo veremos al acabar detección")

Cada una en su propia rama + doc de backlog cuando toque. Referencia: `docs/audits/AUDIT-2026-07-04-full.md`.

1. **Infraestructura (crítico, ~medio día):** C1 (CI `testProdDebugUnitTest`), C2/C3 (rutas APK + keystore distribute), A7 (`-PuploadCrashlyticsMapping=true`), A8 (rotar Maps key). *Desbloquea la red de seguridad de todo lo demás.*
2. **Firestore rules (crítico):** C4 (`reportedBy=uid` + TTL server-side) + A4-rules (update por diff de claves). Contrato de `spots`.
3. **Data/sync:** A10 (tombstones de deletes), A11 (geoquery multi-celda), M5-data (`@Transaction` en merge), M7-data (retirar `fallbackToDestructiveMigration` antes de release público), M9/M10.
4. **Arquitectura/limpieza:** A6-vm (`Channel` en `BaseViewModel`), M6 (`runCatchingCancellable` + Konsist), M11 (lógica de dominio en VMs), M12 (regla Konsist pureza domain), A9-kmp (extraer orquestación a commonMain — habilita iOS).
5. **i18n/doc:** M8 (14 keys ES + huérfanas), higiene documental (archivar zombis, regenerar BUGS_AND_DEBT, reescribir CLAUDE.md §Detección tras el merge).
6. **iOS:** categoría propia (consumidor de geofence-exit, arranque del coordinator, BGTaskScheduler, firma/plist/Crashlytics). Depende de A9-kmp.

---

## 5. Registro de progreso

_(actualizar al cerrar cada tarea: commit + estado + validación)_

- [ ] T1 · A1 departure fall-through
- [ ] T2 · A2 gate de parada BT
- [ ] T3 · A3 tests ruta BT (+ extracción a función pura)
- [ ] T4 · A4 timeout walk-away BT
- [ ] T5 · M4 release quita geocerca
- [ ] T6 · M3 "Sí" del usuario a 1.0
- [ ] T7 · M2 hold no pierde park
- [ ] T8 · M1 race de relevo (cancelAndJoin / guard de sesión)
- [ ] T9 · A12 extraer fases del invoke() (opcional)
- [ ] T10 · KDocs STILL/AR-rearm (opcional)
