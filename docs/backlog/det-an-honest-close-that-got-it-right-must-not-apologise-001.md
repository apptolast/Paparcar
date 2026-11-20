# DET-AN-HONEST-CLOSE-THAT-GOT-IT-RIGHT-MUST-NOT-APOLOGISE-001 · el cierre honesto guarda sin interrumpir cuando la duda cabe en la valla

**Estado:** ✅ Done · 2026-11-20 · implementación validada; commit y merge squash autorizados por el usuario.

## Problema y doctrina

El usuario reportó demasiadas preguntas aunque el coche ya aparecía bien situado. El trabajo
previo registró 33 avisos de cierre honesto en tres cuentas con detección; este turno no ha
consultado datos remotos. Se conserva esa conclusión agregada y se retiran del borrador los
identificadores personales y detalles de sesiones.

El cierre guardaba un artefacto aproximado y siempre notificaba con texto de coche sin localizar.
Además, un pin inferido sin radio se dibujaba como exacto. La pregunta debe ganarse la interrupción;
el mapa debe expresar la incertidumbre aunque no haya aviso.

## Diseño implementado

- `HonestCloseVerdict` conserva la incertidumbre sin limitarla al radio dibujado. Si cabe dentro
  de `geofenceRadiusFor(size, accuracy)`, guarda sin avisar; si la supera, solicita el aviso.
  No cambian las pruebas de movimiento ni la protección del pin afirmado por el usuario.
- `RaiseParkingAskUseCase` es singleton: serializa lectura, comprobación, escritura y emisión con
  Mutex. Comparte seis horas por vehículo entre StillParked, WhereDidYouPark y RefineApproximateMark.
  Se estampa antes de emitir. Un fallo de lectura/escritura no abre una vía sin límite.
- Android usa SharedPreferences con commit síncrono; iOS usa NSUserDefaults y la misma instancia
  de IosSafetyNetRecords para el controlador y el presupuesto. La limpieza consulta vehículos,
  no sesiones, y no borra presupuestos si falla esa consulta.
- Cambiar o cerrar una geovalla no reinicia el presupuesto. Las antiguas claves por geovalla no
  se pueden atribuir directamente a las nuevas claves por vehículo; no se promete migración de
  ese histórico. Los registros nuevos sí sobreviven a reinicio y a cambios de sesión.
- El cierre usa el reloj del aviso, no el timestamp del fix. Si falla guardar, no notifica ni
  gasta el presupuesto. Si guarda, `persistPending=false`: la propia sesión es el registro durable.
- `UserParking.isArea` describe geometría; `isApproximate` une geometría y provenance inferida.
  La proyección del mapa lleva esa incertidumbre. Corregir el pin retira la duda mediante el
  camino existente de edición, que conserva el reloj del aparcamiento.
- Texto específico de ubicación aproximada guardada, con remedio, en los nueve idiomas para
  Android y Compose Resources. iOS implementa el aviso antes heredado como no-op, con acción y
  toque en el cuerpo que abren la selección de ubicación. Los avisos sin ubicación conservan su texto.

## Consumidores auditados

| Consumidor | Resolución |
|---|---|
| RunHonestCloseUseCase | cerrado: guarda primero, compara duda, presupuesto compartido, sin pendiente |
| Safety net Android: permanencia y llegada sin dueño | cerrado: ambas preguntas usan el mismo caso de uso por vehículo |
| Safety net iOS: permanencia | cerrado: usa el presupuesto común; llegada se entrega a detección como antes |
| DetectionEffectExecutor: unattended sin artefacto | exento: recuperación sin ubicación, se conserva |
| Watchdog de pending huérfano | exento: recuperación sin ubicación, se conserva |
| BluetoothParkingDetector: GPS timeout / unmeasurable | exento: recuperación sin ubicación, se conserva |
| CoordinatorDetectionService: bt_handoff_failed | exento: recuperación sin ubicación, se conserva |
| ObserveParkedVehicles / mapa y caché de marcadores / historial | cerrado: aproximación independiente del radio |
| ParkingPeek / HomeParkingRow / BrowsePeek / VehiclePageContent / HistoryTimeline | cubierto por isApproximate; fila ajustable también sin radio |
| EvaluateSpotReleaseNomination / SpotReleasePolicy / ProcessConfirmedDeparture | geometría y fiabilidad conservadas; no sustituir forma por incertidumbre visual |
| DTO / Room / espejo compartido | sin nuevo campo persistido; provenance y radio existentes conservan el contrato |
| DI prod Android / iOS y mock Android / iOS | almacén registrado; fake común en mockkit |
| Previews / Dev Catalog | variante de pin aproximado sin radio; sin nuevo routing ni escenario global |
| Web / ficha publicada | exento: no cambia una promesa comercial ni el build publicado |

## Regresiones y validación

Pruebas de frontera (igual al radio no avisa), duda antes del clamp, guardado fallido, pin sin
radio, corrección a pin exacto, publicación de un punto inferido, tres tipos compartiendo límite,
vehículos independientes, reinicio del caso de uso, carrera real entre threads y geovalla reemplazada.

Ejecuciones iniciales de `./gradlew testAll --console=plain` detectaron y permitieron corregir:
import de Flow en iOS, binding ausente del presupuesto mock y recuento del índice de backlog.
La última de esas ejecuciones completó 4096 tests shared, con un único fallo del recuento documental.
Validación final (2026-11-20), ejecutada mediante Git Bash desde este worktree:

- `./gradlew testAll --console=plain --rerun-tasks`: BUILD SUCCESSFUL, ejecución forzada.
- `./gradlew testAll --console=plain`: BUILD SUCCESSFUL sobre el estado final; 4096 tests
  shared y 4 tests mock, cero fallos y cero omitidos. Incluye 13 regresiones nuevas.
- `./gradlew :app:compileMockDebugKotlin :app:compileProdDebugKotlin :app:assembleMockDebug --console=plain`:
  BUILD SUCCESSFUL.
- `git diff --check`: correcto. Se revisaron estado, worktrees, diff y archivos nuevos.
- iOS: compilación klib de la suite correcta. Persisten warnings previos de opt-in de
  IosGeofenceManagerImpl y cast de IosFileAntilog; no se han cambiado esos archivos.
- Cierre: commit y merge squash locales autorizados por el usuario. Sin push, instalación ni despliegue.

El render del IDE de HomeSheetPreviews falló por referencias del proyecto/classpath sin resolver.
Pendientes de verificar en hardware: entrega real de avisos, toque desde arranque frío y apariencia
final en mapa Android/iOS. Compilar klib iOS en Windows no enlaza ni ejecuta la app Apple.
