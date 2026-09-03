# DB-A-NEW-COLUMN-NEEDS-ITS-MIGRATION-001 · A schema change without its migration bricks every open, forever

**Estado:** ✅ Done (01-09-2026) — mergeado a master por squash; el hash vive en `MEMORY.md`.

## Problema

Medido en el banco (Redmi, 01-09 01:28, `parkdiag.log`), en el PRIMER install de un build
posterior a `d010b8c0`:

```
E PARKDIAG/SafetyNet: ✗ failed to read active sessions
  java.lang.IllegalStateException: Room cannot verify the data integrity. …
  Expected identity hash: e8bc446bd23663f9f0febbeda06c375e, found: 03390c9c44e62b92f2c0e85cf92e5eaa
```

`PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001` (`d010b8c0`, 31-08) añadió `retractedAtMs` a
`UserParkingEntity` con `version = 1` intacta. La DB del banco nació el 29-08 con el esquema
anterior; nadie instaló un build posterior hasta el run del 01-09, y el primero lo destapó: **misma
versión + esquema distinto** es la única forma que `fallbackToDestructiveMigration` NO cubre (solo
mira cambios de versión) — ni migra ni borra: **rechaza el fichero en cada open, para siempre**.
Con un coche aparcado vigilado, el safety net quedó ciego en todas sus lecturas.

## Doctrina violada

La escribió el propio `AppDatabase.kt` al bajar a v1 (`DATA-ROOM-STARTS-AT-VERSION-ONE-001`):
*«from then on there ARE users whose data must survive, so every schema change needs its own
Migration»*. Esa puerta se cerró ANTES de la primera release pública: desde el reset del 30-08 los
móviles del banco llevan datos v1 que deben sobrevivir (⛔ prohibido borrarles datos — estado de
field-test real). `d010b8c0` cambió el esquema sin contestarla.

## Diseño — el SISTEMA

- **`version = 2`** (`PAPARCAR_DB_VERSION`, constante única que leen la anotación y los tests).
- **`ALL_MIGRATIONS`** (commonMain, una sola lista) con `Migration1To2`: `ALTER TABLE
  parking_sessions ADD COLUMN retractedAtMs INTEGER`. **Idempotente** (pragma_table_info): en el
  campo hay DOS esquemas v1 — el del banco (sin columna) y el de cualquier DB creada FRESCA por un
  build post-cambio con la versión aún en 1 (con columna); un ALTER ciego moriría ahí con
  «duplicate column name».
- Registrada en **los dos builders** (Android + iOS). La lista única importa doblemente: con el
  fallback destructivo presente, un bump cuya migración falte en un builder no crashea — **borra
  ese device en silencio**. El in-memory del mock iOS no migra (DB fresca por construcción).
- La migración registrada GANA al fallback: los upgrades conocidos migran (el banco sobrevive), los
  downgrades pre-release que el fallback existe para cubrir (v20→v1, y ahora v3→v2) siguen borrando
  en vez de negarse a abrir.

## Criterio de éxito

1. ✅ `AppDatabaseMigrationTest` (Robolectric, **Room real contra la TABLA** — el defecto era
   invisible desde arriba): siembra el **esquema v1 REAL del banco** (DDL volcado de la DB del
   Redmi) + el hash de identidad REAL (`03390c9c…`) + una fila → tras `buildAppDatabase` la fila
   SOBREVIVE, `retractedAtMs = null`, versión = 2. Segundo test: v1 CON columna (hash `e8bc446…`)
   → abre sin crash.
2. ✅ **Falsaciones, vistas en rojo**: quitar `.addMigrations` → las 2 rojas (el fallback BORRA la
   fila: exactamente el silencio que la lista única previene); quitar el guard de idempotencia →
   roja la del v1-con-columna (duplicate column).
3. ✅ `AppDatabaseDowngradeTest` re-anclado a `PAPARCAR_DB_VERSION` (su seed «upgrade-from-3» pasó a
   ser el rollback desde `PAPARCAR_DB_VERSION + 1`: con migración para todo hueco ≤ actual, el caso
   frontera que le queda al fallback es el downgrade más cercano).
4. ✅ Suite completa: **2076 tests, 0 fallos**. `prod` + `mock` compilan.
5. ⏳ En device: instalar en el banco → el error de integridad desaparece del `parkdiag`, las
   sesiones se leen, la fila `retractedAtMs` existe (`pragma table_info` vía sqlite3 local).

## Consumidores auditados

| Sitio | Papel | Estado |
|---|---|---|
| `AppDatabase.version` | la puerta | ✅ v2 + constante + doctrina reescrita en el comentario |
| `buildAppDatabase` (Android) | builder real, con fallback destructivo | ✅ `addMigrations(*ALL_MIGRATIONS)` antes del fallback |
| `IosPlatformModule` | builder iOS, mismo fallback | ✅ ídem (la omisión aquí = wipe silencioso del device) |
| `IosMockModule` | in-memory | exento: DB fresca por construcción, sin fichero que migrar |
| `AppDatabaseDowngradeTest` | pinnaba «reabre a v1» | ✅ re-anclado a la constante; caso re-encuadrado |
| DBs v1 con columna ya presente | ALTER ciego = crash | ✅ guard idempotente + test |
| Próximo cambio de esquema | repetir el olvido | la constante + este doc + el patrón `ALL_MIGRATIONS`; el testigo estructural sigue siendo el install en banco |

## Epílogo — el escalón se retira, el invariante no (03-09-2026)

`DATA-ROOM-RETURNS-TO-VERSION-ONE-001` borró `Migration1To2` y devolvió la base a `version = 1`.
No es una marcha atrás de este ticket: la migración existía porque los datos del banco **debían
sobrevivir**, y el borrado de datos del lanzamiento quitó esa condición. La prueba de que el
escalón ya no describía nada: `1.json` y `2.json` compartían `identityHash e8bc446…`.

Lo que sí sobrevive intacto es lo que este ticket compró con un fallo de campo real:

- `ALL_MIGRATIONS` sigue existiendo (vacío) y **sigue registrado en los dos builders**.
- La regla —*todo cambio de esquema bumpea la versión Y publica su `Migration`*— sigue siendo la
  doctrina, ahora escrita en el KDoc de `AppDatabase` como la puerta que cierra el primer release.
- El agujero que este ticket midió (**misma versión + hash distinto = fichero rechazado para
  siempre**) tiene testigo permanente en `AppDatabaseV1BaselineTest`, que además es la razón por la
  que limpiar los móviles de banco es un paso del lanzamiento y no un consejo.

## Origen

- Destapado por el run de `DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001` (89266cfd) en el Redmi.
- DB del banco volcada en el scratchpad de la sesión (`redmi_paparcar.db`) — de ahí el DDL y el
  hash sembrados en el test.
