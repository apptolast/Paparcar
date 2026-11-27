# SYNC-THE-ACCOUNT-KNOWS-THE-APP-VERSION-001 · Sincronizar versión al abrir

**Estado:** ✅ Done · integrado en master

## Problema y contrato

La versión del perfil solo se publicaba con detección. Debe sincronizarse con el perfil sin depender de ubicación, Home o detección. Fuente: investigación de cuentas del 27-11-2026; no guardar identificadores personales aquí.

## Diseño

El repositorio recibe plataforma y versión desde DI. Incluye ambos al crear perfil; para uno existente compara con el remoto y actualiza solo esos dos campos si difieren. El bootstrap ya utiliza este repositorio. AppViewModel sincroniza además en entrada a primer plano, autenticación y reconexión, con cancelación al cambiar cuenta y exclusión mutua del repositorio para no competir con Splash. Fallo de publicación conserva el perfil y vuelve a intentarlo en la siguiente sincronización. No hay una marca persistente de éxito que silencie reintentos. En varios dispositivos el último escritor determina plataforma/versión: no es inventario por dispositivo ni fecha del snapshot de permisos.

## Límites y salvaguardas

No cambiar configuración, claves, inicialización ni concesiones de RevenueCat. Los cinco fallos de promoción requieren recuperación independiente; no afirmar que esta tarea los arregla. No cambios remotos ni despliegue. Sin cambios de detección, reglas, Room ni navegación.

## Criterio de éxito

Alta, versión ausente/antigua, misma versión sin escritura redundante, permisos denegados, reconexión y cambio de cuenta. Validar prod/mock y compilación común iOS; no equivale a ejecutar iOS en Mac.

## Consumidores y archivos

Rutas de producción bajo `shared/src/commonMain/kotlin/com/rndeveloper/paparcar/`:

- `data/datasource/remote/RemoteUserProfileDataSource.kt`: contrato de actualización parcial.
- `data/datasource/remote/RemoteUserProfileDataSourceImpl.kt`: escritura exclusiva de plataforma/versión; propagar error de lectura en vez de confundirlo con cuenta nueva.
- `data/datasource/remote/dto/UserProfileDto.kt`: significado actualizado del campo.
- `data/repository/UserProfileRepositoryImpl.kt`: identidad inyectada, comparación remota, creación con versión, mutex, cancelación y protección ante lectura fallida/sin caché.
- `domain/usecase/user/GetOrCreateUserProfileUseCase.kt`: propagar cancelación en refresco y operación.
- `presentation/app/AppViewModel.kt`: colector autenticación/primer plano/red, cancelado al salir o cambiar cuenta.
- `di/DataModule.kt` y `di/PresentationModule.kt`: bindings explícitos; no cambios a RevenueCat.

Pruebas: `UserProfileRepositoryImplTest.kt` y `AppViewModelTest.kt` cubren el contrato; se adapta únicamente el método nuevo en los dobles de `VehicleDeleteCascadeTest.kt`, `DeleteVehicleRemoteWorkerTest.kt`, `ParkingSyncWorkerTest.kt`, `UserParkingRepositoryImplTest.kt` y `SyncOutboxDrainerTest.kt`. El doble que prohíbe red conserva esa prohibición.

Documentación: este ticket, índice de backlog y `docs/ARCHITECTURE.md`.

Consumidores exentos: publicación de detección (sigue escribiendo su fotografía), DTO/domain/Room sin cambio de esquema, grafo mock que usa repositorio falso, adaptadores Android/iOS (hechos de build existentes). Reglas existentes permiten actualizar el perfil propio; no requieren cambio.

## Comprobaciones

- `git diff --check`: correcto antes de la revisión final.
- Primera compilación: fallo en dos argumentos `email` omitidos del test nuevo; corregido.
- Segunda ejecución: 4262 tests ejecutados, un fallo en el test nuevo porque su fake arrancaba autenticado; condición inicial corregida explícitamente.
- Validación final: `./gradlew testAll :app:compileMockDebugKotlin :app:compileProdDebugKotlin --console=plain`, mediante Git Bash instalado: **BUILD SUCCESSFUL en 1m 21s**. Las tareas de tests shared y app/mock, compilaciones prod/mock y klib iOS Simulator se ejecutaron, no solo UP-TO-DATE. AppViewModelTest: 43 tests, cero fallos/errores. UserProfileRepositoryImplTest: 8 tests. `git diff --check` final: correcto.
- Límites: sin ejecución en dispositivo ni compra real, ni enlace/ejecución iOS en Mac; los avisos previos de AGP/KMP/iOS continúan. Validación local no acredita concesión real de Plus ni recupera los cinco fallos de campaña.
- Sin commits, merge, despliegue ni escrituras a Firebase de producción. No se copian claves de RevenueCat ni firma de release. Configuración local de SDK/Firebase copiada a archivos ignorados del worktree sin leer contenido.

