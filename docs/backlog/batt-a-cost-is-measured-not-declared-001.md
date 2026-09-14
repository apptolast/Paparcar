# BATT-A-COST-IS-MEASURED-NOT-DECLARED-001 · El coste de batería de cada trabajo se MIDE, no se declara

**Estado:** ✅ Done (2026-09-14) — mergeado a master con squash; el hash vive en `MEMORY.md`.
Secuelas: la línea medida de Ajustes (cuando haya corpus limpio) · primera pasada del rig con dos perfiles.
**Verificado:** suite completa verde (`TripBatteryGaugeTest` 5/5) · verify mock ·
compiles mock/prod/iosSimulatorArm64 OK.
**Abierto:** 2026-09-14 · desde master `f744cd28` · fase 5 del pivote trayectos-como-producto

## Problema

El pivote gasta batería a propósito (escalón Cruise, grabador BT) y todas las cifras que manejamos
son **estimaciones de literatura** — la sesión del 14-09 lo dijo con todas las letras ("estos
números hay que medirlos con vuestro rig"). Decisión del user: al usuario solo se le enseñan cifras
**medidas** (una promesa que el ajuste de batería del sistema contradiga destruye la confianza), y
para calibrar los perfiles hace falta el coste real por viaje, por dispositivo, en la flota.

Hoy ni el job de detección ni el grabador BT miden lo que cuestan: cero datos locales, cero
telemetría, cero forma de calibrar el perfil de muestreo con otra cosa que opinión.

## Doctrina violada

La de la casa entera, aplicada a la batería: **cifra declarada ≠ cifra medida** (la misma línea que
`routeDistanceMeters` inferida-sin-marca o el `speed` que miente). Ninguna regla de detección se
toca: esto es instrumentación pura — muestrear el contador al abrir y cerrar un job no decide nada.

## Señales / datos disponibles

- `BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER` (µAh, contador acumulado del fuel gauge) +
  `isCharging` — lectura de propiedad contra BatteryService, sin receiver, sin I/O.
- Los dos trabajos que gastan GPS ya tienen brackets naturales: `detectionJob`
  (`startParkingDetection` → su `finally`) y `btTrackingJob` (start → `invokeOnCompletion`).
- El ledger diario (`triggerLedgerSessionId`) ya da parent barrible a eventos fuera-de-sesión
  (precedente: `Trigger`, `ProcessDeath`), y `logTrigger` fija el patrón de emisión.

## Diseño

1. **`BatteryMeter`** (puerto, `domain/detection/ports/`): `chargeCounterMicroAh(): Long?` +
   `isCharging(): Boolean`. Impl Android (`AndroidBatteryMeter`); distinto de `ChargingStateSource`
   a propósito — aquel es la política de muestreo (fun interface, boost cargando), este es el
   instrumento de medida; compartir puerto acoplaría dos consumidores con contratos distintos.
2. **`TripBatteryGauge`** (puro, `domain/detection/`): `Sample(chargeMicroAh?, charging, atMs)` →
   `settle(start, end): Cost(deltaMicroAh?, clean, durationMs)`. **`clean` es el veredicto de
   honestidad**: ambos contadores presentes, SIN carga en ninguno de los dos extremos, delta ≥ 0
   (un delta negativo = se enchufó a mitad sin que lo viéramos) y duración ≥ 60 s (por debajo, el
   ruido del fuel gauge ±1-2 % manda). Una cifra no-clean se registra igual (es dato), pero jamás
   alimentará la línea de Ajustes.
3. **`DetectionEvent.Battery`** → wire: UNA columna nueva `deltaMicroAh: Long?` (precedente
   explícito: `fixSource` — "a NEW column when none is free"); el resto monta columnas existentes:
   duración → `sessionAgeMs` (la columna "cuánto vivió X"), `clean` → `success`, carril →
   `source` (`coordinator` / `bt_recorder`). Sin deserializador manual que tocar (telemetría
   write-only; el `when` exhaustivo de `toDto` fuerza la rama nueva por compilador).
4. **Servicio**: bracket en los DOS jobs; al cerrar, `settle` + línea PARKDIAG + evento remoto bajo
   el ledger (patrón `logTrigger`). Fire-and-forget: fallar en medir jamás toca el job.
5. **La línea de Ajustes queda FUERA a propósito** (decisión del user: "con datos limpios") — es
   secuela cuando haya corpus medido; este ticket construye el instrumento.

## Protocolo A/B del rig (calibración, manual — no es código)

Para comparar dos perfiles de muestreo con el mismo trayecto (dos móviles del banco, uno por perfil,
o el mismo móvil en dos pasadas):

```
1. Desenchufar el móvil. adb shell dumpsys batterystats --reset
2. Pantalla apagada, viaje real (o sim de conducción largo).
3. Al terminar: adb shell dumpsys batterystats --charged com.rndeveloper.paparcar > batt-<perfil>.txt
   → mirar: "Estimated power use" de la app, GPS time on, wakelocks.
4. Contraste con la telemetría BATTERY del mismo viaje (deltaMicroAh clean del job).
5. En Pixel: Perfetto con raíles de potencia (ODPM) como patrón oro.
⚠️ MIUI: los datos de batterystats sobreviven al viaje pero NO a un reinicio; volcar antes.
⚠️ Un delta por viaje tiene ±1-2 % de ruido de fuel gauge: comparar AGREGADOS (semana), no viajes sueltos.
```

## Criterio de éxito

- Tests de `TripBatteryGauge`: delta positivo limpio · charging en un extremo → no-clean ·
  contador ausente → delta null y no-clean · delta negativo (enchufe a mitad) → no-clean ·
  duración corta → no-clean.
- Suite completa verde + compiles mock/prod + iosSimulatorArm64 (el puerto es común; iOS sin impl —
  `getOrNull`, sin boost… sin gauge: el servicio es Android).
- En campo: cada viaje real deja su línea `BATTERY` en parkdiag y su evento en el ledger.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `DetectionEventDto` + `toDto` | cerrado: rama nueva forzada por `when` exhaustivo; columna nueva `deltaMicroAh` documentada en el DTO |
| `FirestoreDeserializerParityTest` | exento: no existe deserializador manual de `DetectionEventDto` (write-only) — verificado por grep |
| `FieldTraceGuardrailTest` | exento: no parsea `DetectionEvent` (verificado por grep) |
| `SessionRollup` / header de sesión | exento a propósito: el evento va al ledger diario, no al doc de sesión — el coordinator no se toca |
| `ChargingStateSource` | sin cambio: puerto hermano, contrato distinto |
| Jobs del servicio (`detectionJob`, `btTrackingJob`) | cerrados: bracket en ambos; fallo de medición = log, nunca afecta al job |
| iOS | exento: sin impl del puerto; el evento/`toDto` común compila (iosSimulatorArm64) |
