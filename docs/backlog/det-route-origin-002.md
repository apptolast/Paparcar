# DET-ROUTE-ORIGIN-002 — El origen del viaje sobrevive a la muerte del proceso (se re-resuelve desde Room)

**Estado:** ✅ EN MASTER (`3149704b`, junto con la persistencia de ruta por aparcamiento) · campo
cubierto por la validación hasta `1a4128d5` (23-08-2026). El árbol de master está limpio: aquel
"working tree sin commit" se commiteó hace tiempo.

## El bug (campo Redmi, 2026-08-08)
El usuario reporta: al reiniciar/reabrir la app a mitad de trayecto, la línea de ruta **NO** nacía en
su plaza (el punto real de salida) sino **donde abría la app**.

## Causa (causa B del diagnóstico)
DET-ROUTE-ORIGIN-001 siembra el origen del viaje con `Monitoring.departurePoint`, pero ese punto
**solo vive en memoria**: `DetectionRuntimeState._trip` (un `StateFlow` en proceso), publicado
**únicamente** al armar (`CoordinatorDetectionService.startParkingDetection` → `setTrip`). No se
persiste. Cuando el OEM mata el proceso a mitad de trayecto (MIUI/Redmi es agresivo) o el usuario
cierra y reabre la app:
- el `StateFlow` vuelve a `null` y nadie vuelve a llamar `setTrip` con la plaza;
- `HomeTripController` resetea su trail a vacío en cada arranque en frío (`current = TripUpdate.IDLE`);
- resultado: trail vacío + `departurePoint == null` → el **primer fix vivo** (donde se abrió la app)
  se convierte en el origen (`departurePoint = trail.firstOrNull()`).

También cierra la carrera del primer-fix-antes-de-publicar-departurePoint: Room está disponible
inmediatamente (offline-first), aunque el `StateFlow` en memoria llegue tarde.

## El fix
La plaza aparcada del vehículo ya es una fuente de verdad **durable y offline-first**
(`UserParkingRepository.observeActiveSessions()`, Room). El `HomeTripController` ahora la observa y,
al sembrar un trail vacío, resuelve el origen así:

```
originHint = Monitoring.departurePoint            // servicio, si sigue en memoria
    ?: parkedOriginFor(puck.vehicleId, activeSessions)   // sesión aparcada del vehículo en Room
```

`parkedOriginFor` empareja por `vehicleId` del vehículo que sale; solo cae a "la única sesión activa"
si el vehículo del puck no está resuelto y hay exactamente una plaza aparcada (caso mono-coche).
**Nunca** siembra desde la plaza de OTRO coche (doctrina multi-vehículo de DET-ROUTE-ORIGIN-001).
El techo de plausibilidad de 5 km (`backdatedOrigin`) se sigue aplicando sobre el resultado.

100% capa de presentación: el punto sintético solo existe en el `TripUpdate` ensamblado; los
evaluadores de detección beben fixes medidos aguas arriba y nunca lo ven (invariante de
DET-ROUTE-ORIGIN-001 intacto).

## Ficheros
- `presentation/home/HomeTripController.kt` — inyecta `UserParkingRepository`, observa
  `activeSessions`, `parkedOriginFor()`, fallback en la siembra.
- `di/PresentationModule.kt` — pasa `userParkingRepository = get()`.
- Tests: `HomeTripControllerTest` (+2 casos: seed desde Room con departurePoint nulo; no-seed desde
  otro vehículo) · `HomeViewModelTest` (constructor).

## Relacionados
- DET-ROUTE-ORIGIN-001 (la siembra original, punto en memoria).
- ROUTE-LINE-PRO-001 (matcher v4 HMM que rutea el hueco plaza→primer-fix por las calles).
- Tarea hermana abierta: **línea de ruta que se sale de la carretera** (ver plan de map-matching).
