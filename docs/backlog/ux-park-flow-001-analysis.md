# UX-PARK-FLOW-001 — Análisis del flujo aparcar/desaparcar (AS-IS + objetivo)

> **Estado**: ANÁLISIS (entregable de la fase de definición del placeholder `ux-park-flow-001.md`).
> Rama `feature/UX-PARK-FLOW-001-park-flow-redesign`. Redactado 2026-07-18 sobre `master @ 781cb666`.
> **Re-verificado 2026-08-06 sobre `master @ b16a56bc`**: VEH-ACTIVE-FENCE-001 entró en master el
> 21-07 (3 días después de redactar esto) — §0 reescrita, C1/C2/C7 curados en código, §3/§4/§5
> actualizadas con el estado real; C3/C4/C5/C6 verificados como AÚN VIVOS. Referencias `fichero:línea`
> refrescadas donde el código se movió.
> Mapea el flujo REAL en código (pantalla × estado × acción) y propone el flujo OBJETIVO.
> No toca código de producción: define, no implementa.
> **Zoom de aparcar/desaparcar**. El flujo Home completo (mapa, búsqueda, plazas, zonas, peeks,
> detección) está en `home-flow-analysis.md` — este doc es el detalle del sub-flujo de sesión propia.

## 0. Dependencia dura — RESUELTA ✅ (VEH-ACTIVE-FENCE-001 en master desde el 21-07)

Este ticket asume que *"el vehículo activo ES la declaración; liberar = voy a conducir"* es un
invariante coherente. Cuando se redactó este análisis (18-07) **no lo era**; 3 días después
VEH-ACTIVE-FENCE-001 se mergeó a master (5/5 piezas). Re-verificado en código el 2026-08-06
sobre `b16a56bc`:

- **Release por `sessionId` explícito, sin fallback** ✅ — `ReleaseParking(sessionId, publishSpot)`
  (`HomeIntent.kt:71-74`); `releaseParking` resuelve
  `activeSessions.firstOrNull { it.id == sessionId }` y hace no-op con log si no existe
  (`HomeViewModel.kt:435-463`). El diálogo lleva el `sessionId` del peek pulsado
  (`HomeScreen.kt:758`). El release fantasma está muerto (commit c31fb0c0).
- **Atribución a la valla que NOMINÓ** ✅ — el detector ya no cierra `vehicleId` con
  `observeActiveVehicle().first()` a ciegas: resuelve vía
  `VehicleFenceOwnershipPolicy.resolveSessionVehicleId(nominatingVehicleId, activeVehicleId)`,
  priorizando la valla nominadora (`CoordinatorParkingDetector.kt:913-931`).
- **Diálogos de consecuencia** ✅ — set-active (`SetActiveConfirmDialog`,
  `VehiclesScreen.kt:264-276`) y release con `detectionNote` que explica si la detección se
  re-arma (`HomeReleaseDialog.kt:25-48`; copy por `vehicleIsActive` en `HomeScreen.kt:762-765`,
  commit 55ade4db).
- **"Estoy conduciendo" declara el coche** ✅ — `StartDrivingDetection(vehicleId)`
  (`HomeIntent.kt:52`) declara el vehículo activo ANTES de armar (`HomeViewModel.kt:588-597`,
  commit a76b70f3).
- **Evento diagnóstico `Released`** ✅ — `DetectionEvent.Released(sessionId, published, …)`
  (`DetectionEvent.kt:190-195`), emitido por `ReleaseActiveParkingSessionUseCase`
  (commit dae895ce). Los releases ya dejan traza.

**Único pendiente del modelo**: field-test de la Pieza 1 (vallas del activo, alto riesgo) — se
mergeó antes de validar en campo por decisión del user; fix-forward si hay regresión.

**Consecuencia para este análisis**: el bloque AS-IS (§2) se conserva como se redactó el 18-07 —
documenta POR QUÉ existe cada pieza del modelo — con notas ✅ donde el defecto ya está curado.
§3 refleja el estado real por defecto; los tickets hijos 🎨 de §5 están **DESBLOQUEADOS**.

---

## 1. Actores del flujo (vocabulario)

| Término interno | Qué es | Dónde vive |
|---|---|---|
| **Sesión** (`UserParking`) | Un aparcamiento propio: vehículo, ubicación, geofenceId, activa/liberada | `activeSessions: List<UserParking>` |
| **Vehículo activo** | El coche que el usuario declara que conduce hoy (invariante 1-solo-activo) | `VehicleActiveStatePolicy` |
| **Sesión preferida** (`userParking`) | La sesión que el peek muestra por defecto — rankeada BT > Activo > Inactivo, con fallback a orden | `HomeState.kt:199` |
| **Sesión seleccionada** (`selectedSession`) | La sesión cuyo id coincide con `selectedItemId` (tap en card/marcador) | `HomeState.kt:203` |
| **Fase de detección** | Driving / Candidate / … — estado del servicio, se refleja en chips | `DetectionPhase`, `DetectionUiState` |

---

## 2. Inventario AS-IS — pantalla × estado × acción

### 2.1 Marcar aparcamiento manual

| Estado (mode/flags) | Pantalla / superficie | Acciones del usuario | Efecto |
|---|---|---|---|
| `Browse`, vehículo sin sesión | Chip vehículo + superficie detección `AwaitingFirstPark` (`DetectionUiState.kt:49`) | Tap **"Marcar aparcamiento"** (`HomeDetectionSurface.kt`, fila de acciones ~:148) | `EnterAddParkingMode(targetVehicleId=…)` → `mode=AddingParking` |
| `AddingParking`, `isCameraMoving` | Peek "Posicionar aparcamiento" + pin central + mapa arrastrable | Mover mapa; confirmar deshabilitado mientras la cámara se mueve | — |
| `AddingParking`, cámara quieta | mismo peek, botón confirmar activo | Tap **Confirmar** → `ConfirmAddParking` | `confirmAddParking()` (`HomeViewModel.kt:409`): valida pin, `isSavingParking=true`, `saveManualParking(...)` local-first [OFFLINE-PARK-001] |
| `AddingParking`, `isSavingParking` | peek con spinner | — | éxito → vuelve a `Browse`; fallo → se queda en modo con pin intacto + `ShowError` [BUG-8] |

**Confusión AS-IS**: no hay diálogo ni feedback de "para qué coche" al marcar — el `targetVehicleId`
se infiere del vehículo de la card, invisible para el usuario.

### 2.2 Detección + confirmación

| Estado | Superficie | Acciones | Efecto |
|---|---|---|---|
| Detección cruza umbral (servicio) | **Notificación del sistema** "¿Has aparcado?" (`NotifyParkingConfirmationUseCase.kt`, `ParkingConfirmationReceiver.kt:11`) | "Sí, he aparcado" → `ACTION_PARKING_CONFIRMED` · "Sigo conduciendo" → `ACTION_PARKING_DENIED` | coordinator confirma / reanuda |
| App en foreground, `pendingParkingGps != null` | **`ConfirmationBottomSheet`** in-app (host en `HomeScreen.kt`) — dirección + método (AR/BT + hace X) | "Sí, confirmar" → `ConfirmDetectedParking` · "Retirar" → `DismissConfirmation` | `confirmDetectedParking()` (`HomeViewModel.kt:400`) guarda; retirar limpia `pendingParkingGps` |
| Sin interacción 240 s | mismo sheet, **sin timer visible** | (nada) | auto-confirma en silencio (`CONFIRMATION_TIMEOUT_SECONDS=240` en `ConfirmationBottomSheet.kt:53`; cuenta atrás oculta a propósito, `:77-86`) |
| Post-guardado | notificación muta a "Toyota aparcado" con revertir (`ParkingConfirmationReceiver.kt:15`) | "Sí, confirmar" → ACK · "No, cancelar" → `ACTION_PARKING_REVERT` (+parkingId) | revert dispara release |

**Confusión AS-IS**: dos superficies distintas (notificación vs. sheet in-app) con copys distintos
para el mismo evento (**C4, sigue viva** → UXP-c); el auto-confirm a 240 s no mostraba cuenta atrás
(**C5 — ✅ curada en esta rama 06-08**: caption con countdown m:ss bajo las acciones del sheet).

### 2.3 Liberar plaza (desaparcar)

| Estado | Superficie | Acciones | Efecto |
|---|---|---|---|
| Peek de sesión (`selectedSession != null`) | **`ParkingPeek`** (`ParkingPeek.kt`) — ubicación, duración, acciones | Tap **"Me voy"** (botón Logout, `ParkingPeek.kt:105`) → `HomeSheetAction.RequestRelease` | abre diálogo (`showReleaseDialog=true`) |
| `showReleaseDialog` | **`HomeReleaseDialog`** (`HomeReleaseDialog.kt:25-48`) "¿Me voy?" + `detectionNote` de consecuencia | "Publicar plazas libres" (`onPublishSpot`) · "Solo liberar" (`onDeleteOnly`) | `HomeReleaseDialogHost` (`HomeScreen.kt:758`) → `ReleaseParking(sessionId, publishSpot)` |
| `isReleasingParking` | diálogo en loading | — | éxito → limpia selección + (si publica) `SpotReported`; fallo → `ShowError` |

**Defecto AS-IS (el bug de campo) — ✅ CURADO (21-07, VEH-ACTIVE-FENCE-001)**: el diálogo NO
llevaba `sessionId`; `releaseParking` re-resolvía `selectedSession ?: userParking` y sin selección
válida caía a `firstOrNull()` → **liberaba la sesión equivocada** (release fantasma del Beat), y
además sin traza en diagnostics. Hoy el diálogo lleva el `sessionId` del peek pulsado, el release
resuelve por id sin fallback (no-op + log si no existe) y emite `DetectionEvent.Released` — ver §0.

### 2.4 "Estoy conduciendo"

| Estado | Superficie | Acciones | Efecto |
|---|---|---|---|
| `AwaitingFirstPark` (cold-start, flag on) | superficie detección, botón secundario (`HomeDetectionSurface.kt:148-158`) | Tap **"Estoy conduciendo"** → `StartDrivingDetection(vehicleId)` | declara el vehículo activo (`declareActiveVehicle`) y DESPUÉS arma `manualParkingDetection.start()` (`HomeViewModel.kt:588-597`) |
| Servicio arranca | chip vehículo muestra fase (Monitoring/Candidate) | — | pill efímera "EN RUTA"/"APARCANDO…" en el chip (`HomeSheetContent.kt:192`) |

**Defecto AS-IS — ✅ CURADO (21-07, VEH-ACTIVE-FENCE-001, commit a76b70f3)**: el tap no declaraba
qué coche y arrancaba detección genérica. Hoy el intent lleva `vehicleId` y el handler declara el
vehículo activo antes de armar; la atribución del pin usa la valla nominadora (§0).

### 2.5 Multi-vehículo

| Estado | Superficie | Acciones | Efecto |
|---|---|---|---|
| N vehículos, 0-N sesiones | cards por vehículo (parked/unmarked) | Tap card → selecciona sesión o entra AddingParking | `selectedItemId` / `EnterAddParkingMode` |
| 2 sesiones activas | peek muestra la **preferida** (rankeada), no "ambas" | — | el ranking solo decide QUÉ se muestra; release ya opera por `sessionId` explícito (bug §2.3 curado) |

**Confusión AS-IS** (re-verificado 06-08: **parcialmente viva**): ya existen acciones explícitas de
declaración (set-active con diálogo de consecuencia en Vehículos, "Estoy conduciendo" con coche,
liberar declara), pero en HOME sigue sin haber superficie que cuente qué coche es el activo y por
qué el peek muestra uno u otro — eso es C3, de este ticket.

---

## 3. Mapa de defectos y puntos de confusión (AS-IS)

| # | Síntoma UX | Causa raíz | Ticket que lo cura | Estado 06-08 |
|---|---|---|---|---|
| C1 | Liberar puede matar el coche equivocado | release sin `sessionId`; fallback `firstOrNull()` | **VEH-ACTIVE-FENCE-001** (§3 spec) | ✅ curado (c31fb0c0) |
| C2 | "Estoy conduciendo" no dice qué coche | intent sin `vehicleId`; arranque genérico | **VEH-ACTIVE-FENCE-001** (§2 spec) | ✅ curado (a76b70f3) |
| C3 | "Vehículo activo" es un concepto invisible EN HOME | sin superficie ni copy que lo explique | **UX-PARK-FLOW-001** (este) | 🔴 vivo |
| C4 | Dos superficies/copys para confirmar (notif vs sheet) | caminos paralelos históricos | **UX-PARK-FLOW-001** | ✅ implementado EN ESTA RAMA (06-08): una sola voz — pregunta con coche + "Sí, he aparcado"/"No, no he aparcado" en ambas; notif DE/NL/PT/FR pasadas a tuteo |
| C5 | Auto-confirm a 240 s sin aviso visible | timer oculto a propósito | **UX-PARK-FLOW-001** | ✅ implementado EN ESTA RAMA (06-08): cuenta atrás visible bajo las acciones |
| C6 | No se distingue "vigilando" vs "necesita que declares el coche" | `DetectionUiState` se colapsa en pills sueltas | **UX-PARK-FLOW-001** | 🔴 vivo |
| C7 | Liberar no ofrece "y ahora conduzco este" | liberar y declarar están desacoplados | **VEH-ACTIVE-FENCE-001** (§4) + UX | ✅ curado (liberar declara + `detectionNote`, 55ade4db) |

C1/C2/C7 eran **de modelo** y VEH-ACTIVE-FENCE-001 los curó (✅ en master 21-07, pendiente solo
field-test Pieza 1). C3/C4/C5/C6 son **de narrativa UI** — el trabajo vivo de este ticket.

---

## 4. Flujo OBJETIVO — pantalla × estado × acción

> Marcado con 🔒 lo que **presuponía VEH-ACTIVE-FENCE-001** — todos los 🔒 están **✅ HECHOS** en
> master desde el 21-07; con 🎨 lo puramente UI de este ticket (lo que queda por hacer).

### 4.1 El vehículo activo como declaración (C3)
- 🎨 Superficie persistente y legible: "Vigilando **[coche]**" cuando hay activo con detección armada,
  vs. "Declara qué coche conduces" cuando no. Copy causa+consecuencia, sin mecánica interna
  (workers/frecuencias) [feedback_no_internals_in_user_copy].
- 🔒✅ Cambiar el activo desde Vehículos → **diálogo de consecuencia** ("detectaremos automáticamente
  los aparcamientos de este vehículo") — hecho: `SetActiveConfirmDialog` (`VehiclesScreen.kt:264-276`).

### 4.2 Marcar / confirmar (C4, C5)
- 🎨✅ Unificar la voz de notificación y sheet in-app — hecho en esta rama (06-08): la pregunta
  nombra al coche en ambas superficies, acciones "Sí, he aparcado"/"No, no he aparcado" idénticas,
  y las notificaciones DE/NL/PT/FR dejan el usted (el resto de la app tutea). De paso se corrigió
  la semántica: el botón del sheet decía "Publicar plaza" en 6 idiomas cuando confirmar NO publica.
- 🎨✅ Cuenta atrás visible del auto-confirm — hecho en esta rama (06-08): caption "Se confirmará
  sola en m:ss" bajo las acciones del sheet, en los 9 locales.
- 🎨 Al marcar manual, indicar para QUÉ coche se marca.

### 4.3 Liberar (C1, C7) — ✅ COMPLETA (modelo + copy)
- 🔒✅ Release por `sessionId` explícito de la card pulsada; sin sesión → no-op + log (`HomeViewModel.kt:435-463`).
- 🔒✅ Liberar coche **activo** → aviso "se inicia la detección del próximo aparcamiento".
- 🔒✅ Liberar coche **inactivo** → mismo aviso + declaración (liberar = declarar, `HomeViewModel.kt:445-451`).
- 🎨✅ El diálogo publish/solo-liberar hereda esa consecuencia en el copy (`detectionNote` i18n,
  `HomeReleaseDialog.kt:29-31` + `HomeScreen.kt:762-765`). Revisar solo el TONO al unificar la voz (UXP-c).

### 4.4 Estados visibles de detección (C6)
- 🎨 Un solo relato de estado en Home: **Vigilando [coche] · Conduciendo · Aparcando · Necesita que
  declares el coche · Bloqueado (permiso)** — jerarquía clara, no pills sueltas contradictorias.
- 🔒✅ "Estoy conduciendo" lleva `vehicleId`; declara activo → swap vallas → armar
  (`HomeViewModel.kt:588-597`).

---

## 5. Secuenciación recomendada

```
VEH-ACTIVE-FENCE-001 (modelo de fondo)       ← ✅ EN MASTER 21-07 (5/5 piezas;
        │  release por sessionId · vallas del activo · armado vehicle-scoped     pendiente solo
        │  · diálogos set-active/liberar · evento Released                       field-test Pieza 1)
        ▼
UX-PARK-FLOW-001 (narrativa UI)              ← este ticket, DESBLOQUEADO. Tickets hijos:
        UXP-a 🎨 Relato único de estado de detección (C6)      ← ✅ IMPLEMENTADO en esta rama
                                                                 (06-08): DetectionStory; spec en
                                                                 docs/backlog/ux-detection-story-001.md
        UXP-b 🎨 Superficie "vehículo activo = declaración" (C3) ← en gran parte SUBSUMIDO por la
                                                                 línea "Vigilando [activo]" de UXP-a;
                                                                 evaluar en device si hace falta más
        UXP-c 🎨 Confirmación unificada (C4)                   ← ✅ IMPLEMENTADO en esta rama
                                                                 (06-08), junto a la cuenta atrás C5
        UXP-d 🎨 Copy de consecuencia en liberar               ← ✅ absorbido por VEH-ACTIVE-FENCE
                                                                 (queda revisar tono en UXP-c)
```

**Estado tras la re-verificación (06-08)**: la condición que este doc imponía ("cerrar
VEH-ACTIVE-FENCE-001 antes de los tickets 🎨") **ya se cumple**. El modelo que la UI tiene que
"contar" es real: release por id, liberar declara, conducir declara, atribución por valla
nominadora. Los tickets hijos UXP-a/b/c pueden abrirse como specs propias y ejecutarse; UXP-d
quedó absorbido por los diálogos de consecuencia del modelo (solo revisar tono al unificar la voz).
Orden recomendado: **UXP-a primero** (relato único, el más transversal), UXP-b se apoya en él,
UXP-c cierra la confirmación.

---

## 6. Dev Catalog / StateGallery (obligatorio al implementar)

Cada estado nuevo debe reflejarse (regla ⛔ del CLAUDE.md):
- `MockScenario`: caso **2 vehículos (1 activo + 1 inactivo, ambos aparcados)** — escenario de
  regresión del bug de release (curado 21-07) y banco de pruebas de la narrativa multi-coche.
- `StateGalleryScreen`: variantes de la superficie de detección (Vigilando/Conduciendo/Aparcando/
  Declara-coche/Bloqueado) + diálogos nuevos (set-active, liberar activo/inactivo, confirmación con
  cuenta atrás).
- Paridad con los `*Previews.kt` correspondientes.

---

## 7. Entregable de esta fase

Este documento ES el "documento de flujo (pantalla × estado × acción) + propuesta priorizada" que
pedía el placeholder. VEH-ACTIVE-FENCE-001 ya está en master, así que los tickets hijos que quedan
(UXP-a, UXP-b, UXP-c) pueden abrirse como specs propias YA. Sincronizar Dev Catalog con cada estado
nuevo en la MISMA tarea que lo implemente.
