# ROUTE-START-AT-CAR-001 · La ruta guardada debe EMPEZAR en el pin del que salió el coche

**Estado:** 🟢 Código listo, sin commitear · rama `bugfix/ROUTE-START-AT-CAR-001-route-origin-previous-pin` · worktree `../Paparcar-route-start-at-car` · 1213 tests verdes · regresión verificada ROJA sin el fix

## Problema
Field 17-08 (Redmi, uid `WZB7…`): el pin de las 23:57 (`c6b19ee6`, Punta Bermeja, `steps+egress`)
lleva una ruta que ACABA a 8 m del pin (ROUTE-END-AT-CAR-001 ✅) pero NACE a **129,8 m** del pin
anterior (`428d5c28`, Constitución 22:02), ya rodando — el tramo pin→primer fix no existe. El user
lo reportó con capturas: "la ruta no nace desde este [el aparcamiento anterior], sino desde un poco
más alante".

## Doctrina violada
La propia de ROUTE-QUALITY-001: *"The trip's true ORIGIN is this vehicle's still-active previous
parking"* — el comentario ya declara que el origen del viaje es el aparcamiento anterior, pero la
implementación lo consulta con la pregunta equivocada.

## Causa raíz (telemetría, no especulación)
`ConfirmParkingUseCase.encodeFreshRoute` siembra el origen desde
`userParkingRepository.getActiveSessionByVehicle(vehicleId)`. En un viaje SANO la salida verificada
(`RunDepartureCheckUseCase` → `processConfirmedDeparture`) publica la plaza y **desactiva la sesión
minutos después de arrancar** → al confirmar el siguiente aparcamiento la consulta devuelve null y
la semilla nunca dispara. **La semilla solo vive cuando la detección de salida FALLÓ** (ej.: pin
manual del Oppo 18-08, cuyo EXIT llegó 11 h tarde → sesión aún activa → semilla a 4.489 m ✅).

Evidencia field 17-08: el sync inmediato del pin 23:57 no llevó clear del pin anterior
(`replaceActiveSession` → `previousId=null`) y el pin de las 22:02 quedó `isActive=true` en
Firestore hasta el reconcile diferido del 18-08 11:16Z → en Room ya no había sesión activa del
vehículo en el momento del confirm.

## Señales / datos disponibles
- `UserParkingRepository.getPreviousSession(vehicleId, beforeTimestamp)` ya existe (Room, ignora
  el flag activo — verificar semántica exacta del DAO).
- La ventana de plausibilidad ya existe: `MIN_ORIGIN_PREPEND_METERS=15` /
  `MAX_ORIGIN_PREPEND_METERS=5000` — 130 m entra de sobra.
- La polyline no lleva timestamps (lat/lon puros) → anteponer el pin no "retrodata" nada.

## Diseño
El invariante vive donde ya vivía (encodeFreshRoute, un solo sitio): **el origen es el ÚLTIMO
aparcamiento del vehículo, esté o no aún activo** — el coche no se mueve solo; su último pin es,
por definición física, donde empezó este viaje. La liberación de la plaza (comunidad) no borra el
hecho geométrico.

- `ConfirmParkingUseCase`: origen = sesión activa **?: sesión previa más reciente**
  (`getPreviousSession`). Mismas cotas 15–5000 m; sin cambios en el resto de la cadena
  (trim al ancla, extent mínimo, encode).
- Barrido: `HomeTripController` (fallback Room del origen en vivo, [DET-ROUTE-ORIGIN-002]) tiene el
  mismo agujero tras cold-restart post-release → mismo fallback.

## Criterio de éxito
- Test: `should_seed_route_origin_from_previous_session_when_active_session_was_released` (rojo sin
  el fix).
- Campo: en dos aparcamientos consecutivos con detección sana, la ruta del segundo empieza EN el
  pin del primero (como ya hace la del pin manual).

## Consumidores auditados
Grep `getActiveSessionByVehicle|getPreviousSession` en `src/`:
- `ConfirmParkingUseCase` L227 (origen de ruta) → **CERRADO** (activa ?: previa, mismas cotas 15–5000 m).
- `HomeTripController` fallback Room del origen en vivo [DET-ROUTE-ORIGIN-002] → **CERRADO**
  (`previousParkedOriginFor`, solo con vehicleId resuelto; techo 5 km de `backdatedOrigin` intacto).
- `ConfirmParkingUseCase` L184 (guard repark-plausibility) → **EXENTO**: pregunta por una sesión
  activa reemplazable; una sesión liberada no puede ser "reemplazada" por un FP peatonal.
- `SwapActiveVehicleFencesUseCase` → **EXENTO**: propiedad de geocercas = solo sesiones activas.
- `RunHonestCloseUseCase` / `CoordinatorDetectionService` (`stalePin`) → **EXENTOS**: limpian un pin
  ACTIVO obsoleto; una sesión liberada no es un pin colgado.
- `EnrichParkingSessionWorker` (ruta pin-a-pin del backfill) → **YA CORRECTO**: usa `getPreviousSession`.

## Verificación
- `should_seed_the_origin_from_the_released_previous_session…` **ROJA sin el fix** (única que falla,
  44/44 → 43+1F) y verde con él. Suite completa 1213 tests + `compileMockDebugKotlinAndroid` OK.
- No toca decisión de detección (solo procedencia de la ruta) → PARKING-DETECTION.md sin cambios.
- Sin strings nuevos, sin pantallas/estados nuevos (Dev Catalog n/a).
