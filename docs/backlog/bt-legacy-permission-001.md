# BT-LEGACY-PERMISSION-001 · Configuración Bluetooth sin crash

**Estado:** ✅ Cerrado · corrección aprobada para integrar en master el 2026-11-28.

## Problema
Crashlytics b80a983d78aa680ef2e254c8122e0864: SecurityException por BLUETOOTH en isEnabled al abrir BluetoothConfigViewModel. Falta declaración legacy y la consulta de estado escapa al manejo de errores.

## Diseño e invariante
Android <=30 necesita BLUETOOTH normal declarado; >=31 usa CONNECT runtime. Comprobar permiso actual antes de acceder al adaptador y capturar SecurityException en el límite Android. PermissionManager debe reflejar el permiso legacy real.

## Consumidores auditados
BluetoothConfigViewModel consulta estado al iniciar, observar permisos y refrescar: cubierto por adaptador. Lecturas de dispositivos: cubiertas por mismo límite. Store de conexiones: sin cambios; no se modifica algoritmo ni receiver.

## Validación
- Ocho casos Robolectric (API 29/30/31/34) y BluetoothConfigViewModelTest ejecutados correctamente.
- Manifiestos prod/mock fusionados con BLUETOOTH limitado a API 30; hardware Bluetooth opcional.
- compileProdDebugKotlin y compileMockDebugKotlin ejecutados correctamente; klib iOS recuperada de caché.
- testAll: 4272 pruebas de shared, tres fallos: dos del nuevo ticket (formato e índice, corregidos) y PapIconTextRowTest por UnsatisfiedLinkError nativo. Revalidación dirigida de scanner, ViewModel y BacklogStatusGuardrailTest correcta con --rerun-tasks (70 tareas ejecutadas). El fallo nativo restante indica que Control de aplicaciones de Windows bloqueó libandroid_runtime.dll; no se cambiaron las protecciones del host. No se acredita suite global verde ni ejecución iOS en Mac.
- Consumidores adicionales: HomeViewModel, ParkingStrategyResolver y ParkingSafetyNetWorker cubiertos por límite Android; iOS y fakes conservan contratos.
- Sin resultado visual nuevo. Commit y merge squash autorizados por el usuario el 2026-11-28. Sin instalación ni publicación.
