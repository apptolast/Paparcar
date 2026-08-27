# DET-BT-AUTONOMOUS-REPAIRING-ANDROID-17-001 · El sistema reconecta solo, y nosotros contamos conexiones

**Estado:** 🔵 Abierto, sin código · **bloqueado por medición** — hace falta un móvil con Android 17.
Sin rama: el spec vive aquí, en el backlog. Cuando empiece el código, worktree y rama nuevos.

## Problema

Android 17 introduce **re-emparejamiento Bluetooth autónomo**: cuando se pierde un bond, el sistema
lo restablece **solo y en background**, sin pasar por Ajustes ni avisar al usuario.
`ACTION_KEY_MISSING` pasa a emitirse **únicamente si ese intento automático falla**, y
`ACTION_PAIRING_REQUEST` gana `EXTRA_PAIRING_CONTEXT` para distinguir un emparejamiento normal de uno
autónomo.

Aplica a **todas** las apps en Android 17, independientemente del `targetSdk`. No lo desactiva bajar
el target: nos aplica el día que un tester tenga Android 17.

## Por qué esto NO es lo que parecía

La primera lectura fue "revisar qué asume la vía BT sobre la pérdida de bond". Al mirar el código,
esa preocupación **no aplica**:

```
grep ACTION_KEY_MISSING | ACTION_BOND_STATE_CHANGED | bondState  →  0 hits
```

`BluetoothConnectionReceiver` escucha **exactamente dos acciones** y ninguna es de bond:

- `BluetoothDevice.ACTION_ACL_DISCONNECTED` → `startForegroundService(ACTION_BT_DISCONNECTED)`
- `BluetoothDevice.ACTION_ACL_CONNECTED` → `startService(ACTION_BT_CONNECTED)`

`AndroidBluetoothScanner` usa `getBondedDevices()`, pero solo para ofrecer la lista al emparejar un
vehículo en Ajustes — no participa en la detección.

## El riesgo real, que es otro

**Nuestra vía BT no razona sobre bonds: cuenta conexiones.** Y el re-emparejamiento autónomo es,
por definición, un proceso que produce **desconexión y reconexión ACL en background sin que el
usuario haya tocado el coche**.

La doctrina dice *el evento NOMINA, solo el movimiento MEDIDO confirma*, y
`DET-BT-DISCONNECT-WITHOUT-RIDE-001` (`cd7a2cf2`) ya cerró la única vía que confirmaba sin conducción
medida. Así que un ACL espurio **no debería** plantar un pin. Pero sí puede:

1. **Despertar el servicio de detección** en cada ciclo de re-emparejamiento — coste de batería y
   ruido en `parkdiag`, justo lo que `DET-CHEAP-WAKE-INSTEAD-OF-SILENCE-001` intentaba acotar.
2. **Armar una sesión** por un `ACTION_BT_DISCONNECTED` que no corresponde a nadie bajándose.
3. **Cerrar una sesión viva** si un `ACTION_BT_CONNECTED` autónomo se interpreta como "ha vuelto al
   coche" mientras el usuario está andando por la calle.

El punto 3 es el que puede producir un FN, y los FN son lo que llevamos meses cazando.

## Doctrina en juego

*El evento NOMINA, solo el movimiento MEDIDO confirma* y *fallo asimétrico: mejor FN que FP*. Un ACL
generado por el sistema es **el caso extremo de un evento sin cuerpo humano detrás**: nadie se ha
movido. Si algún guard trata un ACL como prueba de presencia, este cambio lo destapa.

## Señales / datos disponibles

- `dumpsys bluetooth_manager` da el histórico A2DP por MAC, y **la FORMA de la caída dice si el
  módulo muere con el contacto** — ver `reference_dumpsys_bluetooth_a2dp_history`. Es la herramienta
  para distinguir una desconexión real de un ciclo de re-emparejamiento.
- El `parkdiag` local registra cada trigger con su origen, así que un ACL espurio deja rastro.

## Criterio de éxito

- Un ciclo de re-emparejamiento autónomo **no arma ni cierra** ninguna sesión.
- Si arma, la sesión muere sin pin por falta de conducción medida (comportamiento ya esperado, pero
  **sin verificar** contra este disparador).
- Queda un replay `Trace_*` del ciclo, para que la regresión no pueda volver en silencio.

## ⚠️ Bloqueado por medición, no por diseño

**No se toca código antes de observarlo.** Ninguno de nuestros móviles llega a Android 17 (Redmi =
Android 13/API 33), y escribir un guard contra un comportamiento que no hemos visto es exactamente el
patrón que este proyecto ya pagó caro: guards apilados contra hipótesis.

Lo que se necesita primero: un móvil con Android 17, provocar una pérdida de bond con el MAC del
coche, y leer el `parkdiag` + `dumpsys bluetooth_manager` para ver **cuántos ACL genera el ciclo y
con qué forma**.

## Consumidores a auditar cuando llegue el dato

| sitio | qué asume |
|---|---|
| `bluetooth/BluetoothConnectionReceiver.kt` | que un ACL corresponde a una acción del usuario |
| `BluetoothDetectionStrategy` | disconnect del MAC emparejado → arranca la secuencia de confirmación |
| `EvaluateBtParkUseCase` / `EvaluateBtArbitrationUseCase` | el BT como árbitro sobre el coordinator |
| `AndroidBluetoothScanner.getBondedDevices()` | ✅ exento — solo alimenta la UI de emparejar vehículo |
