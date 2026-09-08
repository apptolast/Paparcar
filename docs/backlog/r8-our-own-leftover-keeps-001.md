# R8-OUR-OWN-LEFTOVER-KEEPS-001 · Los restos: código propio anclado por reglas que ya no protegen nada — y una que sí paga algo

**Estado:** ✅ Done · Paso 6 de 7 del plan de R8

Paso 6 de 7. Pasos 1-5 en master (`f5edf09b`, `aeacc493`, `9904b72e`, `348a40ab`, `16cea83e`).

## Problema

Quedan cuatro `-keep` sobre código propio, todos con comentarios que describen mecanismos que no
existen:

| línea | regla | radio | comentario que la justifica |
|---|---|---|---|
| 7 | `-keep public class * extends java.lang.Exception` | **388 clases** (de TODO el classpath, no solo nuestras) | *"Crashlytics — keep source line numbers"* |
| 28 | `-keep class …FirebaseDataSourceImpl { *; }` | 1 / 21 / 37 | bajo la cabecera de Koin |
| 42 | `-keep class …data.datasource.remote.dto.** { *; }` | 38 / 594 / 233 | *"used by reflection when (de)serialising documents"* |
| 43 | `-keep class …domain.model.** { *; }` | 78 / 951 / 500 | idem |

Las tres últimas caen por lo ya establecido en pasos anteriores: **no hay reflexión sobre los DTOs**
—se leen campo a campo con `get<T?>()`, verificado en device en el Paso 4— y su serialización la
cubren las consumer rules de `kotlinx-serialization-core` desde el Paso 2. `FirebaseDataSourceImpl`
es una clase que se instancia desde el módulo de Koin, es decir, enlace estático.

La de Crashlytics tampoco se sostiene **como está escrita**: para desofuscar una traza, Crashlytics
no necesita nombres reales, necesita `SourceFile,LineNumberTable` —que se queda, línea 6— más el
`mapping.txt` subido.

## Pero hay un consumidor real, y es lo interesante de este ticket

`GeofenceFailureMapping.kt:23-24` compone la línea de diagnóstico de un fallo de geocerca así:

```kotlin
fun Throwable.geofenceFailureDetail(): String =
    "${toGeofenceRegistrationFailure().label} (${this::class.simpleName}: ${message ?: "no message"})"
```

Y su comentario dice para qué existe: *"the message is what turns an UNKNOWN into a named case on the
next release"*. Es material de diagnóstico que el proyecto **lee** para clasificar fallos de
geocerca.

Ese `this::class.simpleName` es legible hoy **por la regla de la línea 7**: es la que mantiene sin
ofuscar a `ApiException` de GMS incluso después de que el Paso 5 quitara el `-keep` del paquete. Es
exactamente el mismo efecto que se observó en los Pasos 3 y 4, donde `FirebaseAuthFailure` y
`FirebaseAnalyticsException` sobrevivían sin ofuscar sin que nadie los anclara explícitamente.

Borrar la línea 7 a secas **no rompe nada funcional** —el `label` viene del `statusCode` y el
`message` es un String, los dos sobreviven— pero degrada esa línea de log: pasaría a decir `a` en vez
de `ApiException`.

## Doctrina violada

Ninguna, y por eso este ticket es distinto de los cinco anteriores: aquí **no basta con demostrar que
la regla no protege reflexión**. La línea 7 sí paga algo — legibilidad de diagnóstico — y la decisión
es un intercambio, no una corrección. Lo honesto es medir el precio antes de decidir.

## Diseño

Las líneas 28, 42 y 43 se borran sin sustituto.

Para la 7 se miden **dos variantes** y decide el dato:

- **A** — borrarla del todo. Máximo ahorro, `simpleName` de excepciones ilegible en los logs.
- **B** — `-keepnames class * extends java.lang.Exception`. `-keepnames` es `-keep,allowshrinking`:
  R8 **puede eliminar** las excepciones que no se usan (que es de donde viene el grueso del ahorro)
  y solo fija el nombre de las que sobreviven. Conserva el diagnóstico.

Si la diferencia A↔B es pequeña, gana B. Si es grande, la decisión es del usuario.

## Criterio de éxito

1. `:shared:testDebugUnitTest` en verde y ambos flavors con `allWarningsAsErrors`.
2. Medición de baseline, A y B sobre el mismo commit base.
3. En el Oppo, sobre el APK minificado y con la cuenta y permisos ya configurados:
   - lectura y escritura de DTOs (Firestore),
   - **un error real que recorra `PaparcarError` → `Effect.ShowError` → `SnackbarHost`**, que es lo
     que toca la regla de la línea 7.

## Consumidores auditados

| sitio | veredicto |
|---|---|
| `PaparcarError : Exception()` y sus subclases | **cubierto** — todo el consumo es `is PaparcarError.X` (type-check), que R8 reescribe consistentemente al renombrar. Cero dependencias del nombre. |
| `GeofenceFailureMapping.kt:24` — `this::class.simpleName` | ⚠️ **el que paga**: no se rompe, se vuelve ilegible. Es lo que decide entre A y B. |
| `BluetoothConnectionReceiver.kt:126` — `verdict::class.simpleName` | **ya afectado hoy, y no por este ticket**: `verdict` es un value object de dominio que no extiende `Exception`, así que la línea 7 nunca lo protegió — ese `reason` ya sale ofuscado en release. Se apunta como hallazgo, no se toca aquí. |
| DTOs y `domain.model` | **cubiertos** — sin reflexión; serialización vía consumer rules de kotlinx (Paso 2), verificada en device en el Paso 4 |
| `FirebaseDataSourceImpl` | **cubierto** — lo construye el módulo de Koin por enlace estático |

## Medición

Tres `:app:bundleProdRelease` sobre el mismo commit base (`16cea83e`).

| | baseline | **A** (borrar las 4) | **B** (`-keepnames` en excepciones) |
|---|---|---|---|
| DEX sin comprimir | 14.369.020 B | 14.014.184 B | **14.049.704 B (13,4 MB)** |
| ahorro | — | −354.836 B | **−319.316 B (−312 KB)** |
| clases en el DEX | 16.235 | 15.925 | **16.034** |
| ofuscación | 71,5 % | 74,0 % | **72,8 %** |
| clases `*Exception`/`*Error` legibles | 315 | **46** | **204** |
| `ApiException` | legible | **`hk`** | legible |
| `PaparcarError` | legible | ofuscado | legible |

### Se elige B, y la razón es el precio

**B cuesta 35.520 bytes más que A** — 35 KB, un 0,25 % del DEX, sobre los 6,1 MB que el plan ya
había recortado. A cambio, el diagnóstico de fallos de geocerca sigue diciendo `ApiException` en vez
de `hk`, y las trazas de `PaparcarError` siguen siendo legibles a simple vista.

En un proyecto cuyo trabajo de campo se sostiene sobre leer logs de detección, cambiar legibilidad
de diagnóstico por un 0,25 % del DEX es mal negocio. Volver a A, si algún día el tamaño aprieta, es
cambiar una palabra.

Detalle de por qué `-keepnames` es tan barato: equivale a `-keep,allowshrinking`, así que R8 **sí
elimina** las excepciones que no se usan —de ahí que las legibles bajen de 315 a 204, no que se
queden en 315— y solo renuncia a renombrar las que sobreviven.

## Verificación

Todo sobre el APK minificado de la variante B en el Oppo, cuenta real y permisos completos, sha
device ↔ local `0d0b16d8…`.

| criterio | resultado |
|---|---|
| `:shared:testDebugUnitTest` | ✅ **2.452 tests, 0 fallos** |
| ambos flavors con `allWarningsAsErrors` | ✅ |
| **lectura de DTOs** | ✅ Home con vehículo, sesión, dirección y distancia tras arranque en frío |
| **escritura de DTOs** | ✅ zona creada con `isPrivate = true` (no-default) → leída de Firestore con **los 11 campos y sus tipos** |
| **borrado** | ✅ zona eliminada → la colección queda vacía en el servidor |
| **`PaparcarError` → `Effect.ShowError` → snackbar** | ✅ con la red cortada, la app muestra **«Sin conexión»**, con su traducción correcta. Es el camino que dependía de la regla de la línea 7, y funciona con ella ya sustituida por `-keepnames`. |
| errores en el proceso | ✅ cero `FATAL` / `NoClassDefFound` / `ClassNotFound` |

> 🧹 Sin rastro: la zona de prueba `Paso6` se creó y se borró dentro de la verificación; la colección
> `zones` de la cuenta quedó vacía, comprobado contra Firestore.

> ⚠️ Método: para provocar el error de red se desactivaron datos y wifi por `adb` y **se restauraron
> en el mismo comando**. Un primer intento falló por pulsar `back` antes de tiempo, que sacó el foco
> de la app y mandó los toques a otra aplicación del móvil — sin efecto, pero conviene arrancar la
> Activity explícitamente y no navegar a ciegas.
