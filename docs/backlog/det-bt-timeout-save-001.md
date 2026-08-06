# DET-BT-TIMEOUT-SAVE-001 — Aparcar con Bluetooth y no alejarse 30 m (casa) debe guardar TU sesión igualmente

> **Estado:** ✅ EN MASTER (2026-08-06, rebase + ff-only; suite verde). ⏳ field-test (casa → pin
> 0.85 `bt_timeout` a los 15 min; calle → pin 0.95 `bt` intacto).

## Problema (field 2026-08-06 01:30, Oppo + Kamiq — primera ejecución real de la cadena BT)

El flujo BT funcionó entero: DISCONNECT → debounce 30 s → fix estacionario a 8.1 m → vigilancia de
alejamiento… y a los 15 min exactos abortó limpio (`bt_walkaway_timeout`) porque el usuario aparcó
EN CASA y entró sin llegar a cubrir 30 m. Resultado: **no se guardó ni la sesión propia** — el
"dónde está mi coche" se perdió. Es una regresión funcional respecto al Coordinator, que sí
confirma aparcamientos en casa (pasos+egress). El candado de los 30 m protege la confianza
COMUNITARIA (que un corte BT en marcha no plante un pin de 0.95); no debe costarle al usuario su
pin privado.

## Cambio

En `BluetoothParkingDetector`, la rama de timeout de la vigilancia pasa de abortar a **guardar la
sesión propia**:

- `confirmParking(parkingFix, config.reliabilityBluetoothTimeoutSave = 0.85f, vehicleId,
  detectionPath = "bt_timeout", sealPoint = parkingFix)` + notificación `showParkingSaved` +
  veredicto remoto `bt_timeout_save` (o `bt_timeout_save_refused`).
- Constante nueva en `ParkingDetectionConfig` con `require` que la acota en
  `[reliabilityUnattendedSave, reliabilityBluetooth]` — falta solo la corroboración del paseo, así
  que baja del 0.95 al nivel de `reliabilityKinematicEgress` (0.85).
- `sealPoint = parkingFix` es honesto aquí: el cuerpo pasó los 15 min dentro del radio de 30 m del
  pin (a diferencia del caso egress que prohíbe sellar en el pin, DET-STEP-BUDGET-ORIGIN-001).

## Por qué es seguro (doctrina FP)

- En el confirm NO se publica nada comunitario: las plazas se publican en la SALIDA, con sus
  propios guardas. Este cambio solo afecta al pin privado.
- Un corte BT en marcha no puede llegar al timeout: un fix en conducción nunca es aceptado como
  candidato (paso 2, `DrivingAbort`), y desplazamiento a ritmo de vehículo durante la vigilancia
  aborta sin guardar (`bt_walkaway_driving_abort`). Timeout ⇒ 15 min quieto dentro de 30 m.
- Garaje sin GPS: sigue abortando (no hay candidato) — limitación conocida, sin cambio.

## Validación

- [ ] Build + suite verde.
- [ ] Field (caso casa): aparcar el Kamiq en casa, entrar sin alejarse → a los 15 min notificación
      "aparcamiento guardado" + pin del Kamiq con `detectionPath=bt_timeout`, fiabilidad 0.85.
- [ ] Field (caso calle): aparcar fuera y alejarse ≥30 m → pin `path=bt` 0.95 (flujo intacto).
- [ ] Firestore: veredicto `bt_timeout_save` en diagnostics.
