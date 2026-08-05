# DET-BT-RECEIVER-EXPORT-001 — El receiver ACL Bluetooth nunca recibió un evento (exported=false)

> **Estado:** fix aplicado en rama `bugfix/DET-BT-RECEIVER-EXPORT-001` (worktree `../Paparcar-btreceiver`), 2026-08-06.
> Staged, sin commit. ⏳ Pendiente: APK + validación en device (reconectar BT emparejado → ver `▶ BT` en parkdiag).

## Síntoma (field 2026-08-01 y 03, Oppo, Skoda Kamiq)

Primer vehículo BT-emparejado real del proyecto. El usuario vinculó el Kamiq al BT del coche
(MAC `50:26:EF:16:1D:C0`, bien persistida en Room + Firestore) y aun así todas las detecciones
las hizo el Coordinator — y atribuyó los pins al Ford Focus (primario), no al Kamiq. El usuario
tuvo que corregir a mano (MANUAL_REPORT 01-08 17:45).

## Diagnóstico

Evidencia en `parkdiag.log` del Oppo (31-07 → 05-08):

1. **Cero líneas `PARKDIAG/BTReceiver` en toda la semana.** Ni siquiera el
   "no vehicle paired — ignoring" que se logea ante CUALQUIER dispositivo (cascos, reloj).
   Es decir: el sistema jamás entregó un evento ACL al receiver. Permiso BLUETOOTH_CONNECT
   concedido; receiver registrado (verificado por dumpsys).
2. **El resolver SÍ funcionó**: todos los `GEOFENCE_EXIT` desde el 01-08 18:04 →
   `strategy not COORDINATOR; not arming`. La estrategia resolvió BLUETOOTH; el carril EXIT
   se retiró; el receiver muerto dejó el hueco. Las detecciones llegaron por los carriles
   que NO consultan el resolver (SENTRY_WAKE / AR ENTER) — ver "Siguiente pieza".

**Causa raíz:** `BluetoothConnectionReceiver` estaba `android:exported="false"`. Los broadcasts
`ACL_CONNECTED`/`ACL_DISCONNECTED` los emite el **proceso del stack Bluetooth**
(`com.android.bluetooth`, uid `bluetooth` 1002 — visto en dumpsys), no `system_server`; un uid
no-system no puede entregar broadcasts a componentes no exportados de otra app
(`ActivityManager.checkComponentPermission`). El comentario del manifest presumía de un
"android:permission guard" que **no existía** en el XML. Consecuencias acumuladas:

- La estrategia determinista BT no se ha ejecutado NUNCA en ningún build.
- `BtConnectionStore` (identity-gate del safety-net, DET-BT-IDENTITY-GATE-001) nunca se ha
  estampado — se alimenta del mismo receiver.
- La arbitración BT sobre el Coordinator (DET-TIERS-001) nunca ha podido dispararse.

## Fix

`composeApp/src/androidMain/AndroidManifest.xml`:

```xml
<receiver
    android:name=".bluetooth.BluetoothConnectionReceiver"
    android:exported="true"
    android:permission="android.permission.BLUETOOTH_CONNECT">
```

Seguridad del `exported="true"`:
- Ambas acciones ACL son **broadcasts protegidos**: probado en device que un tercero no puede
  emitirlas (`am broadcast` desde shell → `SecurityException: Permission Denial: not allowed
  to send broadcast android.bluetooth.device.action.ACL_CONNECTED`).
- El `android:permission` restringe además los emisores a quien tenga BLUETOOTH_CONNECT
  (el stack BT lo tiene). Doble candado.
- El receiver ya ignora acciones que no sean las dos ACL (primera línea de `onReceive`).

## Validación (device)

1. Instalar APK debug en el Oppo.
2. Con un vehículo BT-emparejado en la app, conectar/desconectar cualquier dispositivo BT
   emparejado del teléfono (no hace falta el coche para la mitad del test):
   - Cascos cualesquiera → debe aparecer `▶ BT CONNECTED device=…` + `no vehicle paired — ignoring`.
   - El coche (Kamiq) → `matched vehicle=abf6c516… — starting BluetoothDetectionService`.
3. Field-test real: aparcar el Kamiq → flujo disconnect → debounce 30 s → fix GPS →
   walk-away ≥30 m → pin `path=bt` con `vehicleId` del Kamiq (fiab 0.95).

## Siguiente pieza (NO en esta rama — decidir con la rama DET-RESIDENT-FGS-001)

El gating por estrategia está copiado por carril y solo `handleGeofenceExit` lo hace;
`handleSentryWake()` y `handleArTransition()` arman el Coordinator aunque la estrategia sea
BLUETOOTH → misatribución de pins al vehículo primario (field 01-04/08: todos los pins
AUTO_DETECTED con `vehicleId` del Focus conduciendo el Kamiq). El invariante debe vivir en UN
punto (intake/`startParkingDetection`), y la residencia SENTRY debería ser consciente de la
estrategia (con BT no hace falta centinela residente: el ACL despierta el proceso por evento y
está exento de FGS-from-background en Android 12+). Se ticketizará al rebasar/mergear
DET-RESIDENT-FGS-001. ⚠️ Orden obligatorio: primero ESTE fix en campo; unificar el gate con el
receiver aún muerto = FN silencioso total.
