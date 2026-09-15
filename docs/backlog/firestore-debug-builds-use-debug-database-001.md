# FIRESTORE-DEBUG-BUILDS-USE-DEBUG-DATABASE-001 · Los builds debug escriben en la base de datos `debug`, release en `(default)`

**Estado:** ✅ **Done — en master**, entregado dentro del squash `8c2daf09` (el merge de `IOS-F0-001`,
que arrastró también `PARK-RELEASE-THE-SCREEN-NEVER-SAW-001` y `NEARBY-ONE-STORE-FOLLOWS-THE-CAMERA-001`).
Verificado 15-09; rama y worktree ya no existen.

## Problema
Debug y release compartían la única base de datos Firestore del proyecto `pap-26`, `(default)`.
Cada field test, prueba de detección o dato basura de desarrollo acababa en los datos de
producción, junto a los usuarios reales. El 12-09-2026 se creó en consola una base de datos con
nombre `debug` (eur3, Standard) para separarlas, pero la app seguía usando `Firebase.firestore`
(= `(default)`) en todos los builds.

## Doctrina violada
Ninguna regla de producto. Es higiene de entornos: los datos de desarrollo no deben llegar a producción.

## Señales / datos disponibles
- `(default)` al 13-09-2026: 172 docs — `spots` 2, `users` 13, `users/*/parkingHistory` 140,
  `users/*/vehicles` 13, `users/*/zones` 4. Sin colecciones `diagnostics*` en ese momento.
- `debug` estaba vacía y con las reglas por defecto de consola (`allow read, write: if false`):
  una app debug apuntándole no habría podido leer ni escribir nada.
- Ninguna de las dos tiene índices compuestos (`firestore.indexes.json` vacío).
- GitLive 2.6.0 expone `Firebase.firestore(app, databaseId: String? = null)` en commonMain.
- `isDebugBuild` ya existe como `expect`: Android → `AppBuildInfo.isDebug` (= `BuildConfig.DEBUG`,
  fijado en `Application.onCreate` antes de arrancar Koin); iOS → `Platform.isDebugBinary`
  (configuración Debug/Release de Xcode).

## Diseño
- **Un solo sitio construye el `FirebaseFirestore`**: el `single` de `DataModule`. Todos los
  data sources (spots, perfil, diagnósticos, informes, ui-location) reciben esa instancia
  inyectada, así que decidir la base de datos ahí cubre a todos los consumidores a la vez.
- La decisión vive en `FirestoreDatabase.idFor(isDebugBuild)` (commonMain, pura, testeada):
  `true → "debug"`, `false → null` (= `(default)`). Android e iOS comparten el mismo camino.
- `firebase.json` pasa a la forma array: las MISMAS `firestore.rules` y `firestore.indexes.json`
  se despliegan a las dos bases de datos, para que debug nunca derive de producción.
  - Desplegar solo debug: `firebase deploy --only firestore:debug`
  - Solo producción: `firebase deploy --only "firestore:(default)"`
  - Ambas: `firebase deploy --only firestore`
- El flavor `mock` no cambia: no toca Firebase.

## Operaciones hechas fuera del código (13-09-2026)
1. **Migración de datos `(default)` → `debug`** vía REST API de Firestore (walk recursivo de
   colecciones/subcolecciones + `batchWrite`), copiando los `fields` tipados tal cual (timestamps,
   geopoints, mapas…). Verificado: 172/172 docs, 0 diferencias de contenido. `(default)` solo se
   leyó. Los metadatos `createTime`/`updateTime` de los docs copiados son los del momento de la copia.
2. **Reglas + índices desplegados a `debug`** (`firebase deploy --only firestore:debug`).
   Comprobado en la API de Rules que solo cambió la release `cloud.firestore/debug`; la de
   `(default)` sigue siendo la del 03-09. Las reglas desplegadas en `(default)` coinciden con
   `firestore.rules` del repo.

## Criterio de éxito
- `FirestoreDatabaseTest` en verde; `KoinModuleVerifyTest` en verde; compila prodDebug, prodRelease e iOS.
- En device con un APK debug, los datos que se escriben aparecen en la base de datos `debug` de la
  consola y no en `(default)`.

## Consumidores auditados
- `Firebase.firestore` en todo `shared/`, `app/`, `iosApp/`: único uso en `DataModule` → **cerrado**.
- Swift (`iosApp/`): no usa Firestore directamente → **exento**.
- `FirebaseDataSourceImpl`, `RemoteUserProfileDataSourceImpl`, `FirestoreDetectionEventLogger`,
  `FirestoreUiLocationLogger`, `FirestoreDiagnosticsReportUploader`, `DiagnosticsRepositoryImpl`:
  reciben la instancia inyectada → **cubiertos**.
- Flavor `mock` (`MockModule`): fakes, sin Firestore → **exento**.
- `tools/trace2fixture`: consume exports JSON, no conecta a Firestore → **exento**. Ojo al exportar
  trazas de un build debug: ahora están en la base de datos `debug`
  (`projects/pap-26/databases/debug/documents/diagnostics/...`).
- Skill `field-test` (lee diagnósticos por MCP): ahora dice qué base de datos mirar según el build → **cerrado**.
- `diagnostics_config/{uid}` (flag de diagnóstico que activa un admin): es por base de datos. Para
  un móvil con build debug hay que activarlo en `debug`, no en `(default)`.
