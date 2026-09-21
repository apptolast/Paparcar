# DATA-A-DEAD-FIELD-THAT-LIES-IS-WORSE-THAN-A-DEAD-FIELD-001 · fuera `UserParking.spotId`

**Estado:** ✅ Done · rama `chore/DATA-A-DEAD-FIELD-THAT-LIES-IS-WORSE-THAN-A-DEAD-FIELD-001` · issue #109

## Problema
`UserParking.spotId` viajaba end-to-end —dominio, `UserParkingEntity`, `ParkingHistoryDto`, los
cuatro sitios de `ParkingSessionMapper`, la lectura de Firestore y su constante— **sin una sola línea
de KDoc**. Y su nombre miente: leyendo `session.spotId` cualquiera entiende *«la plaza en la que
aparqué»*. Es exactamente lo que yo asumí buscando un marcador de «aparcamiento asistido» para #107.

## Lo que se midió antes de tocarlo
Tres comprobaciones, las tres negativas:

| pregunta | respuesta |
|---|---|
| ¿lo escribe alguien? | **no**. El `spotId = session.id` de `FinalizeDeducedDepartureUseCase` es un **parámetro** de `reportSpotReleased(...)`, no una escritura del campo — me equivoqué al redactar #109 y lo corrijo aquí |
| ¿lo lee alguien? | **no**. Cero consumidores |
| ¿hay datos históricos? | **no**. 40 de 40 documentos de Firestore con `spotId = null`, **incluidos los 16 con `publishedSpot = true`** |

O sea: no es «la plaza que liberaste» ni ninguna otra cosa. **Nunca ha contenido nada.**

⛔ Un campo muerto no hace daño. Un campo muerto **con un nombre que afirma lo contrario de lo que
contiene** es una trampa: el siguiente que busque «¿en qué plaza aparcó?» lo encuentra, lo cree y
construye encima. Ya pasó una vez, hoy.

## Diseño
Se retira de los ocho sitios y de la tabla. Room **v17 → v18**.

⚠️ **Se recrea la tabla, no se suelta la columna.** `ALTER TABLE … DROP COLUMN` llegó a SQLite 3.35
(Android 12) y el minSdk es 26, que trae la 3.19: en media flota la sentencia simple no existe.
Copiar-y-renombrar es la única que funciona en todas.

⚠️ **El `INSERT` nombra las 40 columnas UNA a UNA**, no `SELECT *`. Con el asterisco el orden lo
decide la tabla vieja, y bastaría que una versión futura insertara una columna en medio para que los
valores entraran corridos — sin error y sin ruido. **Falsificado**: cambiándolo a `SELECT *` caen
los tres testigos.

⛔ Y no lleva un `DROP TABLE IF EXISTS parking_sessions_new` de cortesía por delante: si esa tabla
existiera, algo habría dejado una migración a medias, y borrarla en silencio taparía el rastro.

## Criterio de éxito
`Migration17To18Test`, **3 testigos que ejercitan la migración de verdad** sobre el esquema v17 real
copiado de `17.json`:
- una fila con valores distinguibles en los extremos y en el medio **sobrevive con sus valores**, la
  columna muerta desaparece y las otras 40 siguen ahí;
- no queda andamio (`parking_sessions_new`);
- una tabla vacía —quien nunca aparcó— no revienta.

⚠️ Corren con **Robolectric y `AndroidSQLiteDriver`**, que es el driver de PRODUCCIÓN. El primer
intento usó `BundledSQLiteDriver` en JVM y ni arrancó (`no sqliteJni in java.library.path`) — y
menos mal, porque el bundled trae un SQLite moderno que **sí** sabe soltar columnas: habría probado
justo lo que no corre en los móviles.

⛔ Esto importa porque el builder de producción lleva `fallbackToDestructiveMigration`: una
migración que lance no da error, **da una base de datos vacía**. Sin testigo, la pérdida sería
silenciosa.

## Consumidores auditados
| Sitio | Veredicto |
|---|---|
| `UserParking` · `UserParkingEntity` · `ParkingHistoryDto` · `ParkingSessionMapper` ×4 · `RemoteUserProfileDataSourceImpl` (lectura + `FIELD_SPOT_ID`) | **cerrado** |
| `FakeData` ×9 · `FakeParking` · `ParkingSyncWorkerTest` | **cerrado** |
| `ReportSpotWorker` · `WorkManagerReportSpotScheduler` · `ZoneWatch*` · `SyncOutboxDrainer` · `PushPayload` · `FirebaseDataSource` | **exentos** — su `spotId` es el de una PLAZA de verdad, otro tipo y otro significado. ⚠️ Un `sed` ciego se los llevó por delante en el primer intento y el compilador los devolvió |
