# SETTINGS-REMODEL-001 — pendientes de validación (iOS + device)

**Fecha:** 2026-07-03
**Estado:** ✅ EN MASTER (merge `8d47bf48`) — solo queda **validación en plataforma**, sin código pendiente
**Prioridad:** 🟠 P1 device físico · 🟡 P2 iOS (target futuro)
**Relacionado:** [[project_settings_remodel_001]], `docs/backlog/ios-stubs-2026-06-10.md`, [UI-LIST-ITEM-001/002]

## Contexto
Rediseño de Settings (reorden por importancia + sección **Detección y permisos** con salud de permisos
y filas de mejora BT/batería + dependencia notif↔detección + reagrupado card-por-sección). Implementado,
mergeado a master y **validado en EMULADOR Android claro+oscuro** (adb + mock Dev Catalog):
- ✅ orden de secciones + cards agrupadas por sección
- ✅ salud verde "All set" (perms All) y **ámbar "Missing Background location" + Fix** (perms Core)
- ✅ "Fix" abre permisos con `focus=Producer` (AUTO-DETECTION), sin la sección essential
- ✅ filas BT / batería con estado real (Set up / Configured ✓)

Lo que queda es **field-test en hardware real** y el **gate de iOS**, que no compila en Windows.

---

## SET-DEVICE-001 · Validar en device físico (Oppo/ColorOS) ⚪ 🟠 P1

**Qué probar (regresión visual + funcional):**
1. Orden de secciones: Cuenta · Detección y permisos · Notificaciones · Apariencia · Mapa · Acerca de · Peligro.
2. **Fila de salud** con un permiso realmente revocado en Ajustes del sistema:
   - Revocar "Ubicación en segundo plano" → debe salir ámbar "Falta Ubicación en segundo plano" + "Arreglar".
   - Revocar reconocimiento de actividad / notificaciones → misma fila ámbar.
   - Apagar el GPS del sistema (services off, permiso concedido) → ámbar y **"Arreglar" debe ir a la
     sección CORE** (donde vive "Enable GPS"), NO a Producer. (Fix aplicado en `b4a5a867`.)
3. **"Arreglar"** navega a la pantalla de permisos con el `focus` correcto y, al volver, la salud se
   recomputa (refresh en `LaunchedEffect` → `permissionManager.refreshPermissions()`).
4. **Fila Bluetooth**: con un coche sin BT → "Configurar" navega a `bt_config/{vehicleId}`; con BT
   emparejado → "Configurado ✓". Sin vehículo → debe llevar a Vehículos (añadir coche primero).
5. **Fila Batería**: sin exención → "Configurar" abre permisos (Producer, que hospeda la exención Doze);
   con exención concedida → "Configurado ✓". Ojo Doze agresivo de ColorOS.
6. **Dependencia notif↔detección**: master detección OFF → sub "Aparcamiento detectado" atenuado + switch
   bloqueado; "Plaza disponible cerca" sigue activo.
7. Claro/oscuro en device (el ámbar = `colorScheme.secondary` = PapAmber; verificar contraste real).

**Notas:** el emulador ya cubrió lo visual; el device aporta permisos reales + OEM killer + BT real.

---

## SET-IOS-001 · Compilar y validar Settings en iOS ⚪ 🟡 P2

iOS es target futuro y **no compila en Windows** — pendiente de sesión en Mac.

**Puntos a verificar al retomar iOS:**
1. **Fila de batería oculta**: se añadió `expect/actual isBatteryOptimizationRelevant`
   (`Platform.kt` / `.android.kt`=true / `.ios.kt`=false). En iOS la fila "Batería sin restricciones"
   + su divisor **no deben renderizarse** (Doze es Android-only; iOS no tiene exención de fondo
   user-grantable). Confirmar que el `if (isBatteryOptimizationRelevant)` en `DetectionSectionCard`
   deja la card de Detección coherente (master · salud · MEJORA · solo BT).
2. **PermissionManager iOS** (`IosPermissionManagerImpl`): confirmar que `isBatteryOptimizationExempt`
   no se usa para nada visible (ya gateado) y que `missingPermissions()` mapea a los permisos iOS
   reales (location always / motion) para la fila de salud. Revisar textos "Missing X".
3. **BT en iOS** (CoreBluetooth, futuro): hoy la fila BT usa `bt_config/{vehicleId}` común; verificar
   que el flujo de emparejamiento existe/está stubbeado en iOS antes de dar por buena esa fila.
4. Tipografía/tema: la pantalla es commonMain (Skia) — validar visual Outfit/Inter/Barlow + ámbar.

---

## Descartado (no hacer salvo petición)
- **Nombre del dispositivo BT** en la fila "Configurado" (la spec decía "muestra dispositivo vinculado"):
  requiere inyectar `BluetoothScanner` + resolver `bluetoothDeviceId`→nombre con `getBondedDevices()`
  (necesita `BLUETOOTH_CONNECT`), y **no es criterio de aceptación** (basta el estado configurado/no).
  Follow-up opcional de bajo valor.
- **Radio de aviso de plazas** en Settings: descartado a propósito — el toggle `notifySpotFreed` no tiene
  lógica de disparo y no hay concepto de radio. Si se hace, va en **Home** reusando el slider de zona
  (`HomePeekHandle.kt`), no en Settings.
