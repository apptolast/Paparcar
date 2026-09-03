# DATA-ROOM-RETURNS-TO-VERSION-ONE-001 · El escalón v1→v2 se retira antes de que exista el primer usuario

**Estado:** ✅ Done (03-09-2026) — mergeado a master por squash; el hash vive en `MEMORY.md`.

> ⏳ Queda el paso de banco, que es del lanzamiento y no del código: **`pm clear` en Oppo y Redmi
> antes del siguiente `/run`**. Sin eso, un `paparcar.db` v1 anterior a `retractedAtMs` queda
> rechazado en cada open — la tercera fila de la tabla de abajo.

## Problema

`AppDatabase` declara `version = 2` y arrastra `Migration1To2` (`ALTER TABLE parking_sessions ADD
COLUMN retractedAtMs`). Ese escalón existe por una razón **fechada y ya caducada**:

> `DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001` (01-09) lo añadió porque los móviles de banco tenían
> datos v1 que **debían sobrevivir** — había un coche aparcado y vigilado, y borrarlos estaba
> prohibido. El propio KDoc de `AppDatabase` lo dice: *"la puerta que su comentario prometía para
> «el primer release público» se cerró ANTES de tiempo"*.

Hoy (03-09) esa condición desaparece: el lanzamiento borra los datos de Firestore y limpia los
móviles de banco. Nada tiene que sobrevivir. Y lo que queda es un baseline con un escalón de más:
el primer usuario real de Play instalará una base que nace en **v2** con una migración desde un
**v1 que nunca existirá en ningún device del mundo**.

El dato que lo cierra — los dos esquemas exportados **ya son el mismo esquema**:

```
schemas/…AppDatabase/1.json  → version 1, identityHash e8bc446bd23663f9f0febbeda06c375e, retractedAtMs ✔
schemas/…AppDatabase/2.json  → version 2, identityHash e8bc446bd23663f9f0febbeda06c375e, retractedAtMs ✔
```

Mismo `identityHash`, mismas 6 tablas, misma columna. `1.json` se regeneró cuando la columna entró
con la versión todavía en 1 y nunca se revirtió. **No hay fold que hacer**: el baseline v1 ya
describe el esquema actual. Solo hay que retirar el escalón.

## Doctrina violada

Ninguna, hoy — es al revés: el escalón se mantiene por una razón que ya no se sostiene, y
`⛔ sistemas, no parches` dice que un invariante caducado se retira entero, no se deja "por si
acaso". Lo que **sí** se conserva es el mecanismo que compró
`DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001` con un fallo de campo real:

> *Todo cambio de esquema bumpea la versión y publica su `Migration` en la lista compartida
> `ALL_MIGRATIONS`, que ambas plataformas registran.*

Ese invariante **no se toca**. Se vacía la lista, no se borra la tubería.

## Señales / datos disponibles

- `1.json` y `2.json` con hash idéntico (medido arriba).
- `AppDatabaseDowngradeTest` ya mide que un fichero de versión **superior** se limpia y se recrea
  vía `fallbackToDestructiveMigration(dropAllTables = true)` — el caso `PAPARCAR_DB_VERSION + 1`
  pasa a ser, literalmente, *"un móvil de banco con la base v2 instala este build"*.
- `AppDatabaseMigrationTest` cita los dos hashes reales del banco:
  `03390c9c…` (v1 viejo, **sin** la columna) y `e8bc446…` (v1 nuevo, con ella).

## Diseño

1. `PAPARCAR_DB_VERSION = 1`. El `@Database` vuelve al baseline; `1.json` ya lo describe.
2. `ALL_MIGRATIONS` queda **vacío**, y `Migration1To2` se borra. `.addMigrations(*ALL_MIGRATIONS)`
   sigue en los dos builders (Android + iOS): el hueco se queda puesto para que el próximo cambio de
   esquema no tenga que redescubrir dónde va.
3. `2.json` se borra: describe una versión que no volverá a declararse.
4. `AppDatabaseMigrationTest` desaparece — no queda migración que atestiguar — pero **su medida no
   se pierde**: se convierte en `AppDatabaseV1BaselineTest`, que fija las TRES formas en que un
   fichero puede encontrarse con este baseline.

### El riesgo que esto crea, y por qué se mide en vez de suponerse

Bajar de 2 a 1 es un **downgrade**, y el destructivo lo cubre: se limpia. Pero hay una tercera
forma que el destructivo **no** cubre y que este cambio reabre:

| Fichero en el device | Qué pasa al instalar este build |
|---|---|
| v2 (build actual del banco) | downgrade → **se limpia y se recrea** ✔ |
| v1 nuevo, hash `e8bc446…` | mismo hash → **abre limpio** ✔ |
| v1 viejo, hash `03390c9c…` | misma versión + hash distinto → **rechaza el fichero en cada open, para siempre** ❌ |

La tercera fila es exactamente el fallo de `DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001`, y **no tiene
arreglo posible en código**: Room no puede migrar lo que no puede distinguir por versión. Por eso
no se supone, se atestigua — y por eso el cierre de esta tarea exige `pm clear` en los móviles de
banco, que es una acción, no una recomendación.

> Para Play es inocuo: el primer install de un usuario real no tiene fichero previo.

## Criterio de éxito

- `:shared:testDebugUnitTest` verde, con `AppDatabaseV1BaselineTest` midiendo las tres filas.
- `1.json` regenerado por el build sin cambiar de hash; `2.json` fuera del repo.
- Los móviles de banco arrancan tras `pm clear` + `/run`, sin *"cannot verify the data integrity"*.

## Consumidores auditados

| Sitio | Qué decía | Resolución |
|---|---|---|
| `AppDatabase.kt` | `PAPARCAR_DB_VERSION = 2` + KDoc de la puerta cerrada antes de tiempo | reescrito al baseline v1 |
| `AppDatabaseMigrations.kt` | `arrayOf(Migration1To2)` + el objeto | lista vacía, objeto borrado, doctrina conservada |
| `AndroidAppDatabase.kt` | *"las migraciones registradas GANAN al destructivo"* | conservado (sigue siendo cierto), reencuadrado a lista vacía |
| `IosPlatformModule.kt` | mismo comentario espejo | igual |
| `AndroidPlatformModule.kt:34` | *"la base empieza en v1 y no hay…"* | vuelve a ser literalmente cierto |
| `AppDatabaseDowngradeTest.kt` | caso `+1` reencuadrado como *"rollback de un APK sideloaded"* | vuelve a su encuadre original **y** gana el caso real: el banco a v2 |
| `AppDatabaseMigrationTest.kt` | testigo de la migración | sustituido por `AppDatabaseV1BaselineTest` |
| `schemas/…/2.json` | esquema v2 | borrado |
| `docs/ARCHITECTURE.md:296` | ya decía **v1** | la nota de "el primer release cierra la puerta" se hace exacta |
| `docs/BUGS_AND_DEBT.md:§4` | ya decía `version = 1` | igual |
| `docs/CODE-READING-CHECKLIST.md:86` | *"**No hay `Migrations.kt`**"* | vuelve a ser cierto en contenido (el fichero existe, vacío) |
| `docs/backlog/db-a-new-column-…-001.md` | ✅ Done | nota de cierre: su escalón se retira, su invariante no |
| `docs/backlog/data-room-starts-at-version-one-001.md` | ✅ Done | puntero a esta tarea |
| `docs/HYPOTHESIS.md:64` | *"ya vamos por v3"* | **exento**: es una retrospectiva fechada, no una afirmación de estado actual |
