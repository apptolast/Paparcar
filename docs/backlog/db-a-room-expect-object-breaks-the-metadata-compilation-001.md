# DB-A-ROOM-EXPECT-OBJECT-BREAKS-THE-METADATA-COMPILATION-001 · `compileCommonMainKotlinMetadata` no compila

**Estado:** 🟡 abierto, sin rama ni código · descubierto 31-08-2026 al migrar BaseLogin a Maven
Central ([[deps-baselogin-leaves-jitpack-for-maven-central-001]])

## Problema

```
:shared:compileCommonMainKotlinMetadata FAILED
e: AppDatabase.kt:38:8 Object 'AppDatabaseConstructor' is not abstract and does not
   implement abstract member: fun initialize(): T
```

`AppDatabase.kt:37-38`:

```kotlin
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>
```

Es el patrón que Room KMP documenta: el `actual object` con `initialize()` lo **genera KSP por
plataforma**, y el `@Suppress` calla la ausencia del `actual`. La compilación de *metadata* — la
vista solo-común, donde KSP no ha generado nada — ve un `object` concreto sin el miembro abstracto y
lo rechaza. El `@Suppress` cubre "falta el actual", no "el cuerpo está vacío".

## ⛔ Lo que este ticket NO es: Room funciona en iOS

Comprobado, no supuesto (31-08):

- `shared/build.gradle.kts:240-242` registra el compilador de Room para los tres:
  `kspAndroid`, `kspIosArm64`, `kspIosSimulatorArm64`.
- Las tareas `kspKotlinIosArm64` y `kspKotlinIosSimulatorArm64` existen y corren.
- **`:shared:compileKotlinIosSimulatorArm64` y `:shared:compileKotlinIosArm64` compilan en verde**,
  o sea que el `actual object` SÍ se genera para iOS y la ruta real de compilación de iOS está bien.

El fallo es **estrictamente** de la compilación de metadata. Nada de esto rompe el APK, el framework
de iOS ni los tests.

## Qué se pierde mientras tanto

`compileCommonMainKotlinMetadata` no está en ningún build que usemos (ni CI, ni `assemble`, ni
`testDebugUnitTest`), pero es el camino de:

- **análisis de `commonMain` en el IDE** — que es exactamente por lo que 3 errores de `iosMain`
  vivieron sin que nadie los viera (ver el ticket de la migración);
- cualquier **publicación** futura del módulo `shared` como librería KMP.

## Pistas para el arreglo (sin decidir)

Ninguna verificada todavía; hay que medirlas:

1. Comprobar si Room 2.8.x ya lo resuelve con una anotación distinta o si hay issue abierto en
   AndroidX — el patrón `expect object` + `RoomDatabaseConstructor` es suyo, no nuestro.
2. `@Suppress("EXPECT_ACTUAL_INCOMPATIBILITY")` / suprimir el diagnóstico concreto en el
   `expect object`, si el compilador lo admite ahí.
3. Excluir la tarea de metadata no es opción: es tapar el instrumento, no el defecto.

## Criterio de cierre

`./gradlew :shared:compileCommonMainKotlinMetadata` en verde **sin** desactivar la tarea ni añadir
supresiones que oculten otros errores de metadata — y sin tocar la ruta de iOS, que hoy funciona.
