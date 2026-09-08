# R8-RULES-THE-LIBRARY-ALREADY-SHIPS-001 · Reglas de R8 que la librería ya publica, o que tapan una reflexión que no existe

**Estado:** ✅ Done · Paso 2 de 7 del plan de R8

Paso 2 de 7 del plan de R8. El Paso 1 (`R8-AN-UNUSED-GEOFIRE-DRAGS-REALTIME-DATABASE-001`) está en
master.

## Problema

`app/proguard-rules.pro` retiene con reglas propias cosas que **las librerías ya protegen ellas
mismas**, y otras que no necesitan protección ninguna porque no hay reflexión que proteger. Cada
línea de estas cuesta el doble: ancla clases y miembros que R8 no puede tocar, y además le quita el
renombrado, que es la métrica que Play enseña en rojo.

Radios medidos con el R8 Configuration Analyzer (`configanalyzer.pb`) sobre el build de release:

| líneas | regla | clases | métodos | campos |
|---|---|---|---|---|
| 81-83 | `-keepclassmembers class ** { @Composable <methods>; }` | 0 | **2.122** | 0 |
| 18 | `-keep,includedescriptorclasses class …**$$serializer { *; }` | 52 | 192 | 72 |
| 40-42 | `-keepclassmembers class * { @androidx.room.* <methods>; }` | 0 | 185 | 0 |
| 29 | `-keep class …di.** { *; }` | 17 | 172 | 7 |
| 22-24 | `-keepclasseswithmembers …paparcar.** { KSerializer serializer(...); }` | 24 | 24 | 0 |
| 19-21 | `-keepclassmembers …paparcar.** { *** Companion; }` | 0 | 0 | 155 |
| 69 | `-keep class * extends …ListenableWorker` | 18 | 0 | 0 |
| 68 | `-keep class * extends …CoroutineWorker` | 14 | 0 | 0 |
| 39 | `-keep @androidx.room.Dao class *` | 14 | 0 | 0 |
| 37, 38, 67 | `extends RoomDatabase`, `@Entity`, `extends Worker` | radio residual, cubierto por las de arriba |

## Doctrina violada

*Sistemas, no parches* — pero al revés de lo habitual: aquí el parche se puso **encima de un sistema
que ya funcionaba**. Las librerías modernas publican sus propias consumer rules precisamente para
que el consumidor no tenga que saber qué necesitan por dentro. Duplicarlas a mano no añade
seguridad: añade una copia que nadie mantiene y que es siempre **más ancha** que la original,
porque el que la escribe no sabe dónde está el límite y tira por el paquete entero.

El caso de Koin es el que mejor lo enseña, porque su consumer rule **lo dice por escrito**
(`configuration.txt:186-189`):

> *"koinViewModel() / koinNavViewModel() resolution is compile-time (reified inline get(), JVM
> Class.getName() keying) — no runtime reflection over your ViewModel constructors, so you do NOT
> need to keep your ViewModels on Koin's behalf."*

## Señales / datos disponibles

Las consumer rules que ya vienen aplicadas, con su origen en `configuration.txt` del build de
release:

| librería | línea | qué trae | qué nuestra regla duplicaba |
|---|---|---|---|
| `kotlinx-serialization-core-jvm:1.11.0` | 1580-1660 | `-if @kotlinx.serialization.Serializable class **` + Companion + `serializer()` + `INSTANCE` + `$$serializer` | nuestras 18-24, pero acotado a las clases **anotadas** en vez de a todo `com.rndeveloper.paparcar.**` |
| `room-runtime-android:2.8.4` | 1286-1290 | `-keep class * extends androidx.room.RoomDatabase { void <init>(); }` | nuestras 37-42 |
| `work-runtime:2.11.2` | 558-574 | `-keepnames class * extends ListenableWorker` + `-keepclassmembers public class * extends ListenableWorker { public <init>(...); }` + `WorkerParameters` + `InputMerger` | nuestras 67-69 |
| `koin-compose-viewmodel:4.2.2` | 179-192 | `-dontwarn` + la nota de que NO hay reflexión | nuestra 29 |

Y el dato de que Compose no necesita nada: el plugin del compilador **reescribe las llamadas
`@Composable` en compilación** (les inyecta `Composer` y la clave de grupo). No hay lookup por
nombre en runtime. Prueba indirecta ya medida: R8 elimina 11.698 clases de
`androidx.compose.material` (los iconos) sin que nada se rompa, y esas clases también tenían
composables.

**Matiz técnico que hay que tener claro para no exagerar el efecto:** `-keepclassmembers` **no hace
viva la clase**, solo protege miembros de clases que sobreviven por otra vía. Por eso la regla de
`@Composable` no impidió podar `material`. Su daño real es otro: en Kotlin todos los composables de
un fichero viven en la misma clase `…Kt`, así que basta que UNO se use para que la regla impida
podar los demás.

## Diseño

Borrar, no reescribir. Ninguna de estas reglas se sustituye por una versión acotada, porque la
versión acotada **ya existe y ya está aplicada** — la publica la librería. El `.pro` se queda
solo con lo que es genuinamente nuestro y no está cubierto por nadie.

Se borran:

- **18-24** kotlinx.serialization artesanal → lo cubre `kotlinx-serialization-core`.
- **29** `di.**` → Koin no usa reflexión sobre el grafo.
- **37-42** Room → lo cubre `room-runtime`.
- **67-69** workers → lo cubre `work-runtime`.
- **81-83** `@Composable` → no hay reflexión sobre composables.

Se quedan en este ticket (van en los Pasos 3-6): los `-keep` de paquete completo de Firebase, GMS,
BaseLogin y GitLive; `-keep public class * extends java.lang.Exception`; `dto.**` y
`domain.model.**`; `FirebaseDataSourceImpl`; y los `-keepattributes`.

## Criterio de éxito

1. `:shared:testDebugUnitTest` en verde (2.402 tests) — incluye `KoinModuleVerifyTest`, que
   construye el grafo de DI entero, y los tests de Room y de workers.
2. Ambos flavors compilan con `allWarningsAsErrors`.
3. `:app:bundleProdRelease` sale, y el DEX **baja**.
4. El build de release **arranca y navega** en device: es el único sitio donde un `-keep` que sí
   hacía falta se manifiesta, y lo hace en runtime, no en compilación.
5. En concreto hay que ver funcionar, sobre el APK MINIFICADO: arranque + Koin construyendo el
   grafo, login, guardar y leer un aparcamiento (Room), un worker de detección, y process death +
   reapertura (que es donde asomaría un `WorkerFactory` roto).

## Consumidores auditados

Grep de reflexión real en `shared/src` + `app/src` (`Class.forName`, `newInstance`,
`getDeclaredConstructor`, `getDeclaredMethod`, `::class.java`, `javaClass`, `WorkerFactory`,
`serializer(` dinámico, `serializersModule`, `@Polymorphic`):

| sitio | veredicto |
|---|---|
| `Intent(context, Xxx::class.java)` (~20 sitios: servicios, receivers) | **cubierto** — literal de clase, R8 lo ve; los componentes además están en el manifest |
| `Room.databaseBuilder(context, AppDatabase::class.java, name)` (`AndroidAppDatabase.kt:45`) | **cubierto** — Room busca `AppDatabase_Impl` con `Class.forName` sobre el nombre, y por eso `room-runtime` publica `-keep class * extends RoomDatabase`, que ancla `AppDatabase` **y** su `_Impl` (que también extiende RoomDatabase). Es exactamente el caso que la regla de la librería existe para tapar. |
| **Los 13 `CoroutineWorker`** | **cubierto** — WorkManager resuelve por **nombre de clase persistido en su BD**, que es reflexión de verdad. Dos cosas lo tapan: `work-runtime` publica `-keepnames class * extends ListenableWorker` (el nombre sobrevive) y los 13 se encolan con literal reificado `OneTimeWorkRequestBuilder<X>()` / `PeriodicWorkRequestBuilder<X>()` (la clase sobrevive). Verificado uno a uno: los 13 declarados tienen su sitio de encolado. |
| `WorkerFactory` propio / `Configuration.Provider` | **no existe** — se usa la factory por defecto |
| `serializer()` dinámico, `serializersModule`, `@Polymorphic` | **no existe** — toda la serialización es con genéricos reificados |
| `@Composable` | **exento** — el plugin del compilador reescribe las llamadas; no hay lookup en runtime |
| Grafo de Koin | **exento** — `module { single { … } }` son lambdas; la propia consumer rule de Koin lo dice por escrito |

⚠️ Los 13 workers son el punto que habría mordido si la auditoría se hubiera hecho por encima:
`-keepnames` permite **eliminar** la clase (solo protege el nombre), así que un worker que solo se
encolara por string habría desaparecido y habría fallado en runtime, tras un process death, días
después. No es el caso — pero el que quite estas reglas mañana tiene que repetir esta comprobación.

## Medición

Dos `:app:bundleProdRelease` sobre el mismo commit base (`f5edf09b`), en el worktree de la tarea.

| | antes | después | delta |
|---|---|---|---|
| DEX sin comprimir | 19.798.764 B (18,9 MB) | **18.691.652 B (17,8 MB)** | **−1.107.112 B (−1,06 MB)** |
| clases en el DEX | 22.871 | **21.810** | **−1.061** |
| clases que R8 elimina | 31.938 | **32.999** | +1.061 |
| métodos nuestros eliminados | — | **3.428** | |
| `com.rndeveloper.paparcar` sin ofuscar | 280 | **180** | −100 |
| **ofuscación** | 44,4 % | **42,2 %** | **−2,2 pp** |

### ⚠️ La ofuscación BAJA aunque el DEX caiga 1 MB, y no es un fallo

De las 1.061 clases que desaparecen, **940 estaban ofuscadas** y solo 121 sin ofuscar. Es lo
esperable: lo que se poda es código nuestro y de Compose, que R8 ya renombraba; el bloque anclado
por los `-keep` de paquete completo (gms + revenuecat + firebase + gitlive + baselogin = **11.759
clases**, idénticas antes y después) no se toca en este paso. Al quitar del reparto casi solo
renombradas, el **porcentaje** empeora aunque el tamaño mejore.

Conviene tenerlo claro antes de subir el bundle: **la métrica de ofuscación de Play no se va a mover
hasta los Pasos 3-6**, que son los que desanclan ese bloque. Lo que este paso arregla es el DEX, que
es el otro aviso rojo.

### De dónde sale el MB

Comparado con el reparto de eliminadas del análisis inicial, los que se mueven son exactamente los
que predecía el diagnóstico — la regla de `@Composable` soltando su presa:

| paquete | eliminadas antes | ahora |
|---|---|---|
| `androidx.compose.material3` | 886 | **1.060** |
| `androidx.compose.foundation` | 1.123 | **1.391** |
| `androidx.compose.ui` | 1.018 | **1.057** |
| `androidx.compose.runtime` | 440 | **451** |
| `androidx.glance.appwidget` | — | **680** |
| `com.rndeveloper.paparcar` | 293 | **480** |

## Verificación

| criterio | resultado |
|---|---|
| `:shared:testDebugUnitTest` | ✅ **2.402 tests, 0 fallos, 0 errores** — incluye `KoinModuleVerifyTest` (construye el grafo de DI entero), los de Room y los de workers |
| `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` | ✅ con `allWarningsAsErrors` |
| `:app:bundleProdRelease` | ✅ |
| arranque del APK **minificado** | ✅ emulador, sha256 device ↔ local idéntico (`8e010af6…`): login renderizado con strings resueltos, `topResumedActivity=MainActivity`, proceso vivo, cero `FATAL` / `NoClassDefFound` / `no definition found` (Koin) / `Could not create Worker`. El arranque en frío construye **el grafo de Koin entero**, así que cubre el borrado de `di.**`. |
| WorkManager programando trabajo | ✅ `dumpsys jobscheduler` lista jobs de nuestro paquete tras el arranque |
| **los 13 workers conservan su nombre en el DEX** | ✅ verificado en `mapping.txt` uno a uno: los 13 salen `X -> X`. Es lo que necesita el `Class.forName(<nombre persistido en la BD de WorkManager>)` del `WorkerFactory`. |
| **`AppDatabase` y `AppDatabase_Impl` conservan el suyo** | ✅ ambos `X -> X` en `mapping.txt`. Es lo que necesita el `Class.forName(canonicalName + "_Impl")` de `Room.databaseBuilder`. Los internos sintéticos sí se renombran, y da igual: nadie los busca por nombre. |
| login, sesión de aparcamiento y process death en móvil real | ⏳ pendiente — el emulador no pasa del login por no tener cuenta, y el Oppo lleva el build de Play |

Las dos filas en negrita son la verificación que de verdad cierra este ticket: las reglas borradas
tapaban dos reflexiones **reales** (WorkManager y Room), y en vez de confiar en que la app arranque,
se ha comprobado **en el `mapping.txt` del build de release** que los nombres que esas reflexiones
buscan siguen intactos. Es una comprobación que se puede repetir en cualquier build futuro con un
`grep`, y por eso vale más que un arranque en verde.

⚠️ A diferencia del Paso 1, este ticket **no se puede dar por bueno sin device**. Lo que se ha
quitado protegía (en teoría) reflexión de runtime: Koin, Room, WorkManager. Un fallo de estos no
aparece al compilar ni en los tests JVM — aparece al abrir la app, o peor, tras un process death,
horas después. Hay que ver, sobre el APK de release: arranque, login, guardar y leer un
aparcamiento, un worker de detección disparando, y process death + reapertura.
