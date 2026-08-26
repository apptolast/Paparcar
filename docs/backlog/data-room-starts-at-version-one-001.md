# DATA-ROOM-STARTS-AT-VERSION-ONE-001 · La base local empieza en 1, sin migraciones

**Estado:** ✅ Done · mergeado en master · ⏳ falta la verificación en device del downgrade v20 → v1

## Problema

Paparcar va a la prueba interna de Play Store y **nunca ha estado en producción**. Aun así
`AppDatabase` declara `version = 20` y arrastra una cadena de 17 objetos `Migration` (257 líneas en
`Migrations.kt`) más 16 esquemas exportados (`schemas/…/5.json` … `20.json`).

Esa cadena describe la evolución de bases de datos que **solo han existido en nuestros propios
móviles de field-test**. No hay ni un instalador ahí fuera cuya v5 haya que llevar a v20. Es
mantenimiento puro: cada columna nueva de detección obliga a escribir un `ALTER TABLE`, bumpear la
versión y exportar un JSON, para preservar datos de nadie.

El user va a borrar todas las cuentas antes de la prueba interna. Es el único momento en que este
reset sale gratis.

## Doctrina violada

Ninguna doctrina de detección. Lo que se corrige es **coste sin beneficio**: 273 líneas y 16
ficheros que solo pueden causar bugs (un `ALTER TABLE` mal escrito rompe el arranque) y que no
protegen ningún dato real.

## Señales / datos disponibles

- `git log` confirma que la app nunca se ha publicado: `versionCode = 3`, `versionName = 1.0.0-beta02`,
  distribución solo por Firebase App Distribution al grupo `beta-paparcar`.
- Los únicos dispositivos con `paparcar.db` en v20 son los nuestros: Oppo, Redmi, el Xiaomi de la
  novia (uid `12ck5…`) y el 4º móvil de field-test.

## Diseño

Un solo movimiento, en los tres únicos sitios que lo conocen (barrido completo abajo):

1. `AppDatabase.version = 20 → 1`, con el comentario reescrito para decir **por qué** v1 y —
   importante — que la primera release pública cierra la puerta: a partir de ahí toda migración es
   obligatoria porque ya sí habrá datos de usuarios reales.
2. Borrar `Migrations.kt` entero y los 16 `schemas/*.json`. KSP regenera `1.json` en el próximo build.
3. Quitar `addMigrations(…)` de `AndroidPlatformModule` y de `IosPlatformModule`.
4. **Mantener** `fallbackToDestructiveMigration(dropAllTables = true)`. Ahora deja de ser una red
   teórica y pasa a ser el mecanismo real: nuestros móviles tienen un fichero v20 y la app va a
   pedir v1 — eso es un **downgrade**, y sin el fallback destructivo Room lanza
   `IllegalStateException` al abrir. Con él, borra y recrea.

Lo que NO se hace: no se toca `versionCode`/`versionName` ni la desinstalación de la app en los
móviles (regla: nunca desinstalar; el fallback tiene que demostrar que funciona solo).

## Criterio de éxito

- Compila prod + mock, y `testProdDebugUnitTest` verde **sin editar un assert**.
- `schemas/…/1.json` se regenera y contiene las 6 entidades.
- ⚠️ **Verificación en device, no por lectura**: instalar sobre un móvil que YA tiene la v20 sin
  desinstalar, y comprobar que arranca con la base vacía en vez de crashear. Es el único punto del
  ticket que no puede darse por bueno desde el código: el camino de downgrade de Room es justo el
  que nunca hemos ejercitado.
- Cero referencias a `MIGRATION_` en el árbol.

## Consecuencias operativas (fuera del código, pero parte del ticket)

Cuatro cosas que el reset arrastra y que no se ven leyendo el diff:

1. **⛔ Uid nuevo = ciego en remoto.** `FirestoreDetectionEventLogger` se auto-desactiva salvo que
   exista `diagnostics_config/{userId}.enabled == true`. Una cuenta recién creada no tiene ese
   documento, así que el primer viaje post-reset solo deja rastro en el `parkdiag` local — el mismo
   agujero que ya tenemos con la Xiaomi (uid `12ck5…`). **Crear el documento en cuanto se sepa el
   uid nuevo**, antes de conducir.
2. **⛔ Resetear Room no resetea nada si la cuenta es la misma.** El `Bootstrap` del arranque
   re-sincroniza vehículos, historial y zonas desde Firestore. El borrado de cuentas que hace el
   user es lo que convierte esto en un reset de verdad; sin él, Room se repuebla solo.
3. **Se pierde el pin del Kamiq `a786c135`**, que era el escenario exacto de
   `DET-BT-CAR-CANNOT-NOMINATE-A-COORDINATOR-SESSION-001`. Con datos frescos y un solo coche, el
   próximo viaje **no valida** ese fix — no contarlo como tal.
4. **⚠️ La Xiaomi de la novia también tiene la app instalada.** No pasarle este APK sin borrarle los
   datos antes, hasta que el punto de "verificación en device" de abajo demuestre que el downgrade
   se sobrevive solo.

Lo que **no** se ve afectado: los 14 replays `Trace_*.kt` llevan sus datos dentro del test, no tocan
Room, y siguen siendo la red de seguridad de detección.

## Consumidores auditados

`grep -rn "MIGRATION_\|addMigrations" --include=*.kt composeApp/src` → 3 ficheros, todos cerrados:

| fichero | qué asumía | estado |
|---|---|---|
| `data/datasource/local/room/AppDatabase.kt` | `version = 20` + comentario histórico v6…v20 | ✅ reescrito a v1 |
| `di/AndroidPlatformModule.kt` | 17 imports + `addMigrations(…)` | ✅ imports y llamada fuera |
| `di/IosPlatformModule.kt` | 17 imports + `addMigrations(…)` | ✅ imports y llamada fuera |

`AndroidDataStoreAppPreferences.kt` también contiene la palabra `Migration`, pero es
`SharedPreferencesMigration` de DataStore — **no tiene relación con Room** y se queda como está.
Igual que el comentario "Migration note" de `PaparcarType.kt` y el "Migration:" de
`IosAppPreferences.kt`. Exentos.
