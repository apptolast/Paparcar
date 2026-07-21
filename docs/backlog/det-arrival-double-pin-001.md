# DET-ARRIVAL-DOUBLE-PIN-001 — el backfill de la red de seguridad duplica la llegada del coordinator vivo

> **Estado**: FIX NÚCLEO IMPLEMENTADO 2026-07-21 en la rama `bugfix/DET-ARRIVAL-DOUBLE-PIN-001`
> (guard `isRunning` en `ParkingBackfillWorker.doWork()`). Field-test PENDIENTE. Sin merge.
> Origen: field-test 20-jul madrugada (Redmi, Citroën C5). Prioridad: **P1** (FP visible al usuario,
> bajo riesgo — un solo guard que espeja el que ya usa el propio worker de la red de seguridad).

## Problema (evidencia de campo)

Madrugada 20-jul, Redmi. Un único aparcamiento físico generó **dos** pines en el historial, a
~96 m uno de otro:

| Hora (ES) | Pin (verdad de tierra) | Coords | Fiab. | Origen |
|-----------|------------------------|--------|-------|--------|
| 02:14:02 | Calle Pantoque 2B | 36.60566, −6.27383 | **0.5** | `ParkingBackfillWorker` (red de seguridad) — **FALSO POSITIVO** |
| 02:17:03 | Avenida Rosa de los Vientos 35 | 36.60553, −6.27276 | 0.9 | coordinator vivo, sesión `…442292` — real |

Firestore `diagnostics` + `users/{uid}/parkingHistory` muestran **dos pipelines confirmando la
MISMA llegada**:

- El **coordinator vivo** (sesión `1784506442292`) se armó a las 02:14:02, siguió egress medido
  (pasos 1→164, fixes convergiendo acc 23→9) y confirmó en el ancla asentada (Rosa, 0.9) a las 02:17.
- La **red de seguridad de 15 min** corrió en la ventana idle entre el fin de la sesión anterior
  (Star Petroleum, `…506107058`, 02:11:37) y el arme de ésta (02:14:02) — así que
  `detectionRuntime.isRunning` era `false` en su tick. Vio LEJOS del ancla vieja + presupuesto de
  pasos fiable (`preconfirmed && backfillBounded`), despachó la salida y **encadenó
  `ParkingBackfillWorker`**, que plantó un pin 0.5 en su fix de despertar grueso (Pantoque).

`ParkingSafetyNetWorker` ya salta todo el check cuando la detección corre (guard `isRunning`,
`ParkingSafetyNetWorker.kt:137`, comentario *"a live coordinator session owns the situation"*).
Pero ese guard se evalúa en el **tick**, no cuando el backfill encadenado se **ejecuta**. La
sesión viva se armó 300 ms después, dentro de la ventana de carrera, y el backfill **nunca
re-comprobaba** antes de escribir.

## Doctrina

*Exactamente UN pipeline puede COLOCAR la llegada.* El invariante `DET-ARRIVAL-HANDOFF-001`
protegía contra "ninguno" (la llegada huérfana), pero no contra "AMBOS". Cuando la detección viva
está siguiendo la llegada, ella es la dueña de la posición (calidad plena, ancla de egress); la
red de seguridad es el backstop para cuando NO hay detección viva.

## Diseño (fix)

Cerrar la carrera **en el punto de escritura**, no en el tick. `ParkingBackfillWorker.doWork()`
re-lee `DetectionRuntimeState.isRunning` al ejecutarse y **cede** (salta el pin) si hay una sesión
de coordinator viva — el mismo skip `isRunning` que usa la red de seguridad, aplicado en el sitio
real de colocación. La salida ya se despachó antes de la cadena, así que la plaza VIEJA se libera
igual; la sesión viva (o, si aborta, su nudge de "marca tu plaza") coloca la NUEVA.

Cambio único:

```kotlin
// ParkingBackfillWorker.doWork(), primera sentencia
if (detectionRuntime.isRunning.value) {
    // live detection owns the arrival → defer, skip backfill [DET-ARRIVAL-DOUBLE-PIN-001]
    return Result.success()
}
```

Por qué no es un parche redundante: es el enforcement del invariante de "un solo colocador" en el
segundo sitio de colocación (el primero, la red de seguridad, ya lo tiene). No añade umbrales ni
lógica nueva — reutiliza el runtime state existente.

## Residual (aceptado, bajo riesgo)

El orden inverso — que el backfill se complete **antes** de que la sesión viva se arme — dejaría
el pin de backfill en el historial cuando el confirm vivo (que reemplaza la sesión activa) corra.
Exige que el arme vivo se retrase respecto al mismo movimiento más que la duración del
`DepartureDetectionWorker` encadenado; el orden de campo no lo exhibe (el arme es casi instantáneo
sobre el trigger, el backfill llega segundos después). Diferido hasta que haya datos que lo pidan.
Si aparece, el cierre completo sería que `ConfirmParkingUseCase` **borre** (no solo desactive) un
pin AUTO de backfill muy reciente (< Ns) y cercano al que reemplaza.

## Fuera de alcance — falsos negativos Oppo (device-side, no es bug de lógica)

La misma noche el Oppo (Ford Focus) se saltó el viaje entero de las 02:00 y el parking actual.
Sesiones estampadas `requiresOemBatteryFreeze=true`, `batteryUnrestricted=false`,
`strategy=COORDINATOR` (sin BT del coche): OEM kill de ColorOS. Sin sesión ninguna entre las 01:18
y las 09:25 = proceso congelado. Remedio = setup del dispositivo (exención de batería + autostart,
y sobre todo **emparejar el Bluetooth del coche** → `BluetoothDetectionStrategy`, que revive el
proceso muerto vía el receiver ACL del manifest), no lógica de detección. El Redmi
(`requiresOemBatteryFreeze=false`) sobrevivió y capturó todo.

## Validación

- Field-test: repetir un viaje con parada intermedia (repostaje) + destino, ambos móviles, y
  confirmar UN solo pin por aparcamiento en el Redmi.
- Diagnóstico esperado: cuando el backfill se salte, log `PARKDIAG/Backfill: ■ live detection
  running — deferring…`; el pin de la llegada nace del coordinator vivo (rel 0.9), no del backfill
  (rel 0.5).
- No-regresión: en un viaje que ocurre íntegro con el proceso muerto (sin sesión viva jamás), el
  backfill sigue colocando su pin 0.5 (isRunning=false en la ejecución).

## Criterio de éxito

Nunca más dos pines para un mismo aparcamiento cuando el coordinator vivo ya lo está siguiendo;
cero regresión del backfill legítimo (viaje entero dormido → un pin 0.5 con su tarjeta reversible).
