# DATA-ROOM-DOWNGRADE-MUST-NOT-CRASH-001 · La v20 de tu móvil abriendo una app que pide v1

**Estado:** ✅ Done · mergeado en master

## Problema

`DATA-ROOM-STARTS-AT-VERSION-ONE-001` colapsó `AppDatabase` de `version = 20` a `version = 1`. Eso
deja a cada instalación pre-release — los móviles de field-test — con un fichero cuya versión es
**más alta** que la que la app pide. Room llama a eso un **downgrade**, y es un camino que este
proyecto nunca había ejercitado: la cadena de migraciones solo iba hacia arriba.

El ticket se cerró con ese punto explícitamente **sin medir**, y con dos lecturas que se
contradecían:

- Una nota escrita al planificar el reset afirmaba que *"un device con la BD vieja y el APK v1
  crashea al abrir salvo borrado de datos previo"*.
- Leyendo el fuente de Room, lo contrario: `fallbackToDestructiveMigration(dropAllTables = true)`
  pone `requireMigration = false`, y entonces `isMigrationRequired` devuelve `false` para
  **cualquier** par de versiones, downgrades incluidos → borra y recrea en vez de lanzar.

Ninguna de las dos es evidencia. Y el coste de equivocarse es que el primer arranque de cada tester
interno crashee.

## Doctrina violada

Ninguna de detección. La que aplica es la del proyecto: *un invariante que no se puede citar en un
test no está protegido*. El invariante aquí es "la app abre una base de datos de cualquier versión
anterior sin crashear", y no tenía testigo.

## Señales / datos disponibles

Intento fallido primero, porque acota lo que se puede medir y lo que no:

- **El end-to-end en emulador no sirve para esto.** Se instaló el APK v20 sobre un Pixel 8 Pro
  (Android 17 / API 37) y se lanzó. La base se quedó en `user_version = 9` y la app acabó parada en
  `com.google.android.gms/.auth.uiflows.minutemaid.MinuteMaidActivity` — la pantalla de Google.
  **Room no se abre hasta que hay sesión iniciada**, así que sin credenciales reales el camino no se
  ejerce. (De paso: el AVD traía 2 GB y el `lowmemorykiller` mató la app a los 12 s; con `-memory
  6144` deja de pasar, pero no cambia el bloqueo de auth.)
- Lo que sí se puede medir de forma determinista y repetible: **Robolectric**, que ya está en el
  proyecto y ejecuta SQLite de verdad.

## Diseño

1. **Extraer `buildAppDatabase(context, name)`** a `androidMain/.../room/AndroidAppDatabase.kt`.
   Inline en el módulo de Koin, la configuración era **intestable por construcción**: un test
   tendría que reescribir el builder, y un builder reescrito pasa tan feliz el día que alguien
   quita `fallbackToDestructiveMigration` del de verdad. Ahora hay **un solo sitio** que dice cómo
   se abre la base, y el testigo lo llama a él.
2. **`AppDatabaseDowngradeTest`** siembra un fichero deliberadamente hostil, con las **tres** formas
   que Room tiene de negarse a abrir un fichero:
   - `user_version` más alto que el declarado,
   - un `room_master_table` con un `identity_hash` que no corresponde a ningún esquema nuestro,
   - una tabla que el esquema actual no declara y que debe desaparecer.

   Una v20 real de un móvil presenta las dos primeras exactamente así.
3. Tres casos: downgrade desde 20, que las 6 tablas se recreen y la basura no sobreviva, y
   **upgrade** desde una v3 sin migración registrada — porque el reset borró todos los `Migration`,
   así que el fallback es ahora lo único que separa un fichero viejo de un crash.

## Criterio de éxito — resultado

| gate | resultado |
|---|---|
| `AppDatabaseDowngradeTest` | ✅ **3 tests, 0 fallos** |
| suite completa | ✅ **1.668 tests, 0 fallos** (1.665 + 3) |
| warnings | ✅ 0, con `-Werror` activo |
| **prueba de mutación** | ✅ quitando `fallbackToDestructiveMigration` del builder real, **los 3 fallan** con `IllegalStateException` en `RoomConnectionManager.kt:224` |

La prueba de mutación es la parte que hace que esto valga algo: un test que pasa igual con y sin la
cosa que vigila no vigila nada.

## Veredicto — y una nota de memoria que queda corregida

**El downgrade v20 → v1 se sobrevive.** `fallbackToDestructiveMigration(dropAllTables = true)` borra
y recrea; no lanza. La afirmación de que un device con la BD vieja crashearía **era falsa**: se
escribió como deducción y se leyó después como si fuera medición.

Consecuencia práctica: **no hace falta borrar datos a mano en ningún móvil** antes de instalar el
APK v1 — ni en el Oppo, ni en el Redmi, ni en la Xiaomi. La app se limpia sola en el primer arranque.

Lo que **no** cambia: borrar la base local sigue sin ser un reset de verdad si la cuenta es la
misma, porque el `Bootstrap` re-sincroniza vehículos, historial y zonas desde Firestore.

## Consumidores auditados

| sitio | qué asumía | estado |
|---|---|---|
| `di/AndroidPlatformModule.kt` | construía el `AppDatabase` inline | ✅ delega en `buildAppDatabase(...)` |
| `di/IosPlatformModule.kt` | construye el suyo con `BundledSQLiteDriver` | ⚠️ **exento, no equivalente** — es otro driver y otro `databaseBuilder`. Este testigo NO lo cubre; extraerlo y probarlo es trabajo de una sesión con Mac |
| `AppDatabase.kt` | `version = 1` | ✅ sin cambios |

## Follow-up

- El camino de iOS (`BundledSQLiteDriver`) sigue sin testigo. No se puede compilar ni ejecutar desde
  Windows, así que se deja anotado en vez de fingir que está cubierto.
