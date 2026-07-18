# VEH-ACTIVE-FENCE-001 — El vehículo activo es la declaración: vallas, armado manual y release por sesión

> **Estado**: EN CURSO en rama `feature/VEH-ACTIVE-FENCE-001-active-vehicle-model`.
> Origen: release fantasma del Chevrolet Beat + 6 FGS espurios (auditoría 2026-07-15).
> Decidido con el user 2026-07-16. Prioridad: **P1**.
>
> **Progreso**:
> - ✅ **Pieza 3 (release por `sessionId`)** — `ReleaseParking` lleva `sessionId`; el VM resuelve la
>   sesión en `activeSessions` y deriva la ubicación de ella; `null` → no-op + `PaparcarError.Parking.
>   ReleaseFailed` visible. Fuera el `?: userParking` y las coords de la intent. `RequestRelease`
>   lleva el id de la card; el host del diálogo usa `releaseTargetSessionId`. Test de regresión del
>   Beat: `should_noop_and_error_when_releasing_an_unknown_sessionId`. Prod+mock compilan, tests verdes.
> - ✅ **Pieza 2 (armado manual vehicle-scoped)** — `StartDrivingDetection(vehicleId)`; la superficie
>   cold-start pasa el vehículo (resolver activo-o-primero compartido con "Marcar aparcamiento"). El VM
>   pone ese coche activo (`setActiveVehicle`, idempotente si ya lo es) ANTES de `manualParkingDetection.
>   start()`, de modo que el pin se atribuye al coche declarado. Tests: declara-inactivo→setActive+arm,
>   ya-activo→solo-arm. Prod+mock compilan, tests verdes.
> - ✅ **Pieza 4 (diálogos de consecuencia)** — (a) set-active en Vehículos abre confirmación
>   (`SetActiveConfirmDialog`, copy "detectaremos dónde aparcas [coche]") en vez de cambiar en
>   silencio; (b) el diálogo de liberar añade la línea de consecuencia (detección re-arma); (c)
>   liberar un vehículo INACTIVO lo pone activo automáticamente (liberar = "lo conduzco", decidido
>   con el user 18-07: auto+copy, sin botón extra) y el copy lo comunica. Strings EN+ES (otros 7
>   locales = follow-up de paridad i18n). Test: `should_set_the_released_vehicle_active_when_it_was_
>   inactive`. Prod+mock compilan, tests verdes.
> - ✅ **Pieza 5 (telemetría)** — evento `DetectionEvent.Released` (published + location "desde
>   dónde"; quién = uid implícito en la ruta; qué sesión = sessionId) logueado en
>   `ReleaseActiveParkingSessionUseCase` (+DTO `RELEASED`, DI, fakes de test). Y estampado de
>   `outcome="superseded"` en el `SessionEnded` de la sesión superseída (antes el `finally` guardado
>   la dejaba sin outcome — gap de la auditoría 15-07): se loguea bajo `thisSessionId`, telemetría
>   pura sin tocar estado del sucesor. Tests: `should_log_a_Released_event...`. Prod+mock compilan.
> - ✅ **Pieza 1 (vallas solo del activo)** — code-complete (2a-2d), unit-green, **pendiente
>   field-test** (cambia registro de geocercas + atribución, el núcleo sensible). **2a**
>   `ConfirmParkingUseCase` no registra valla de inactivo-no-BT. **2b** atribución por nominador
>   (`invoke(nominatingVehicleId=trip.departingVehicleId)`). **2c** `SwapActiveVehicleFencesUseCase` +
>   `DeclareActiveVehicleUseCase` (los 3 puntos set-active pasan por aquí). **2d** janitor/restore
>   saltan inactivos. Núcleo puro `VehicleFenceOwnershipPolicy` + plan en
>   `veh-active-fence-001-piece1-plan.md`. Invariante final: `sesión activa-o-BT ⟺ valla registrada`.
> - ⏳ Pendientes: field-test de Pieza 1 + i18n 7 locales restantes de las strings de Pieza 4.
>
> **Corrección al spec**: la "triple fallback en `HomeBottomSheet.kt:196`" era referencia obsoleta
> (esa línea es padding). La resolución real estaba en `HomeReleaseDialogHost` (`HomeScreen.kt`) +
> `HomeViewModel.releaseParking`, ambas ya corregidas.

## Problema (evidencia de campo)

- **Release fantasma**: sesión manual del Beat (inactivo, Avda Sanlúcar 34) liberada 60 ms
  después del tap "Estoy conduciendo" (15-07 18:05:18.853 → .913). Causa:
  `HomeViewModel.releaseParking` → `target = selectedSession ?: state.userParking`, con
  `HomeState.userParking = activeSessions.firstOrNull()` [MULTI-PARKING-001]. Con 2 sesiones
  activas, un release sin selección válida mata la de otro vehículo. Triple fallback gemelo en
  `HomeBottomSheet.kt:196`. Además los releases no dejan traza en diagnostics.
- **Ruido de vallas de inactivos**: el EXIT de la valla del Beat armó FGS+notificación (1× ruta 2;
  + las 5 de la valla zombi de Rosa). El intake no mira de quién es la valla.
- **Atribución al coche equivocado**: el detector cierra `vehicleId` con
  `observeActiveVehicle().first()` al primer fix de conducción (`CoordinatorParkingDetector.kt:655`)
  ignorando qué valla nominó — conducir el inactivo plantaría el pin en el activo.

## Doctrina

El móvil no puede saber físicamente qué coche sin BT has cogido (dos vallas en el mismo garaje
son concéntricas: la distancia es ruido). Las únicas fuentes de identidad son la MAC Bluetooth y
**la declaración del usuario** — y "vehículo activo" ES esa declaración. Sin ajustes nuevos ni
"modo automático" falso: con BT = automático por coche (DET-TIERS); sin BT = el activo decide.

## Diseño

1. **Valla del OS solo para el vehículo activo** (o BT-emparejado, que trae identidad propia).
   Al confirmar aparcamiento de un inactivo NO se registra valla; al cambiar el activo se
   intercambian (registrar la del nuevo si tiene sesión, retirar la del saliente). Elegido
   *no registrar* frente a *filtrar en intake*: el filtro despierta igualmente el FGS con su
   flash de notificación; no registrar elimina el ruido en origen. La sesión del inactivo
   conserva pin, TTL y safety-net (que trabaja por sesiones con sus propios despertares y solo
   libera con prueba de viaje). `CureGeofence` debe saltarse sesiones de inactivos. El janitor
   barre derivas del swap.
2. **Armado manual vehicle-scoped**: "Estoy conduciendo" lleva `vehicleId`; si el vehículo es
   inactivo → se establece activo primero (`VehicleActiveStatePolicy`, invariante un-solo-activo)
   → swap de vallas → armar. El tap ES la declaración "hoy conduzco este".
3. **Release por `sessionId` explícito, sin fallback**: la intent de liberar lleva el id de la
   card pulsada; `null` → no-op + error visible. Eliminar `?: state.userParking` en
   `releaseParking` y el triple fallback de `HomeBottomSheet.kt:196`.
4. **Diálogos de consecuencia** (copy causa+consecuencia, sin mecánica interna
   [feedback_no_internals_in_user_copy]):
   - Establecer activo en Vehicles → diálogo de confirmación (no inmediato): "detectaremos
     automáticamente los aparcamientos de este vehículo".
   - "Liberar plaza" de vehículo ACTIVO → aviso: se inicia la detección del próximo aparcamiento.
   - "Liberar plaza" de vehículo INACTIVO → mismo aviso + opción de establecerlo activo (liberar
     es la misma declaración que "estoy conduciendo").
5. **Telemetría**: evento diagnóstico `Released` (quién, qué sesión, desde dónde) + estampar
   `outcome` en supersede (gap detectado en la auditoría: sesiones confirmadas con outcome=null).

## Validación

Tests del policy (swap de vallas en cambio de activo, armado manual activa, release sin id =
no-op) + replay de la secuencia Beat (2 sesiones activas, tap "Estoy conduciendo" → el Beat queda
INTACTO) + field-test multi-vehículo. Dev Catalog: reflejar en `MockScenario` el caso 2-vehículos
(1 activo + 1 inactivo, ambos aparcados) y los diálogos nuevos en la galería.

## Criterio de éxito

Con dos coches aparcados: cero FGS espurios de vallas del inactivo, imposible liberar el coche
equivocado, y conducir el inactivo tras declararlo (tap manual o liberar+activar) atribuye el
pin al coche correcto.
