# R8-GITLIVE-NEEDS-ITS-SERIALIZERS-NOT-ITS-WHOLE-PACKAGE-001 · GitLive declara sus serializadores con `with =`, así que no hace falta anclarle el paquete

**Estado:** ✅ Done · Paso 4 de 7 del plan de R8

Paso 4 de 7 del plan de R8. Los Pasos 1 (`f5edf09b`), 2 (`aeacc493`) y 3 (`9904b72e`) están en master.

## Problema

```proguard
# GitLive Firebase SDK (Kotlin Multiplatform wrappers).
-keep class dev.gitlive.firebase.** { *; }
-dontwarn dev.gitlive.firebase.**
```

Radio medido: **360 clases, 2.082 métodos, 611 campos**; 99 KB de bytecode y **0 clases eliminadas**
por R8 en todo el paquete.

Este ticket se abrió con una hipótesis **distinta** a la de los Pasos 2 y 3, y es importante decirlo
porque la hipótesis resultó equivocada por el lado bueno. La idea era: *GitLive sí tiene una base
legítima, porque usa `kotlinx.serialization` por dentro, así que no se puede borrar a pelo — hay que
sustituir por `$$serializer` + `Companion` + `serializer(...)` acotados a su paquete*.

## Señales / datos disponibles

Auditadas las fuentes de `dev.gitlive:firebase-firestore:2.6.0`, `firebase-common:2.6.0` y
`firebase-auth:2.6.0` (los `-sources.jar` de la caché de Gradle, 50 ficheros Kotlin).

**1 · Reflexión: cero.** Ni `Class.forName`, ni `newInstance`, ni `kotlin.reflect`, ni
`ServiceLoader`, ni `javaClass`.

**2 · Y lo decisivo — GitLive NO usa serializadores generados.** Todos sus tipos serializables
declaran el serializador **explícitamente por `with =`**:

| tipo | declaración |
|---|---|
| `FieldValue` | `@Serializable(with = FieldValueSerializer::class)` |
| `GeoPoint` | `@Serializable(with = GeoPointSerializer::class)` |
| `Timestamp` | `@Serializable(with = TimestampSerializer::class)` (+ `Base…`, `ServerTimestampSerializer`) |
| `DocumentReference` | `@Serializable(with = DocumentReferenceSerializer::class)` |

Y esos serializadores son `public object … : KSerializer<T> by SpecialValueSerializer(...)`.

`@Serializable(with = X::class)` se resuelve **en compilación**: el compilador emite una referencia
directa al `INSTANCE` del objeto en cada sitio de uso. **No genera clases `$$serializer`, y no hay
lookup por `Companion.serializer()`.** Es decir: la regla que este ticket iba a escribir habría
anclado tres cosas que **no existen** en este paquete.

**3 · Nada resuelve serializadores en runtime.** `serializersModule` está expuesto como opción de
configuración y por defecto es `EmptySerializersModule()`; nuestro código no lo toca (grep de
`serializersModule`, `@Polymorphic` y `serializer(` dinámico en `shared/src`: cero resultados, ya
verificado en el Paso 2). Sin módulo polimórfico no hay resolución por descriptor en runtime.

## Doctrina violada

La del plan entero: *el `-keep` por si acaso*. Pero con una vuelta de tuerca que merece quedar
escrita, porque es la trampa en la que este mismo ticket estuvo a punto de caer:

**"La librería usa `kotlinx.serialization`" NO implica "hay que keepear sus serializadores".** Lo
que decide es *cómo* se resuelve el serializador. Si se resuelve por `with =` o por genérico
reificado, es enlace estático y R8 lo sigue. Solo hace falta regla cuando se resuelve **por nombre o
por descriptor en runtime**: `Companion.serializer()` buscado reflexivamente, `$$serializer`
generado, o un `SerializersModule` polimórfico. Escribir la regla "porque usa serialización" es
sustituir una suposición por otra, más fina pero igual de infundada.

## Diseño

Borrar el `-keep` y **no poner nada en su lugar**. Se queda el `-dontwarn`.

Es el mismo desenlace que el Paso 3, al que se llega por un camino distinto: allí no había
serialización en absoluto, aquí la hay pero está resuelta en compilación.

## Criterio de éxito

1. `:shared:testDebugUnitTest` en verde.
2. Ambos flavors con `allWarningsAsErrors`.
3. El DEX baja y `usage.txt` deja de reportar 0 eliminadas en `dev.gitlive`.
4. **En el Oppo, sobre el APK minificado y con la cuenta real ya logueada**, leer y escribir Firestore
   de verdad. Un serializador roto **no falla al compilar ni en los tests JVM**: falla en silencio en
   runtime, devolviendo `null` o un documento a medias. Es el criterio que manda.

## Consumidores auditados

**Comprobación empírica que cierra el argumento** (sobre el `mapping.txt` del build baseline, con la
regla todavía puesta):

- clases `dev.gitlive.**$$serializer` en el DEX: **0**. No existe ninguna, así que la regla que este
  ticket iba a escribir no habría anclado nada.
- los serializadores que sí existen son objetos escritos a mano, y R8 los mantiene por uso:
  `FieldValueSerializer`, `GeoPointSerializer`, `TimestampSerializer`, `BaseTimestampSerializer`,
  `ServerTimestampSerializer`, `DoubleAsTimestampSerializer`, `DocumentReferenceSerializer`,
  `FirebaseListSerializer`.

| sitio | veredicto |
|---|---|
| `FirebaseDataSourceImpl` — `get<String?>()`, `get<Double?>()`, `get<Boolean?>()` campo a campo | **cubierto** — genérico reificado, `serializer<T>()` inline: resolución en compilación |
| Tipos de GitLive con `@Serializable(with = …)` | **cubierto** — referencia directa al `INSTANCE` del objeto, emitida por el compilador |
| Nuestros 8 DTOs `@Serializable` | **exentos** de este ticket — los cubren las consumer rules de `kotlinx-serialization-core` (Paso 2) |
| `serializersModule` / `@Polymorphic` / `serializer(` dinámico en nuestro código | **no existe** — grep con cero resultados, ya verificado en el Paso 2 |
| Reflexión dentro de GitLive | **no existe** — 50 ficheros Kotlin auditados, cero resultados |

## Medición

Dos `:app:bundleProdRelease` sobre el mismo commit base (`9904b72e`), en el worktree de la tarea.

| | antes | después | delta |
|---|---|---|---|
| DEX sin comprimir | 18.346.204 B (17,5 MB) | **17.956.560 B (17,1 MB)** | **−389.644 B (−380 KB)** |
| clases en el DEX | 21.638 | **21.310** | −328 |
| **ofuscación** | 43,4 % | **44,3 %** | **+0,9 pp** |
| `dev.gitlive` eliminadas por R8 | **0** | **293** | +293 |
| `dev.gitlive` vivas en el DEX | 366 | **82** | −284 |
| …de ellas, **sin ofuscar** | **366** | **1** | −365 |

**De las 375 clases de GitLive, 293 eran peso muerto**: wrappers de productos Firebase que la app no
usa, retenidos únicamente por el `-keep`. Sobreviven 82, que son las que el código toca de verdad.

Y otra vez el mismo detalle: **la única que sigue sin ofuscar es
`dev.gitlive.firebase.analytics.FirebaseAnalyticsException`**, retenida por **nuestra**
`-keep public class * extends java.lang.Exception`. Es el segundo ticket seguido en que esa regla
deja su huella (en el Paso 3 fue `FirebaseAuthFailure`); buena señal de lo que va a soltar el Paso 6.

## Verificación

| criterio | resultado |
|---|---|
| `:shared:testDebugUnitTest` | ✅ **2.414 tests, 0 fallos, 0 errores** |
| ambos flavors con `allWarningsAsErrors` | ✅ |
| `:app:bundleProdRelease` | ✅ |
| **lectura** de Firestore en device | ✅ Oppo, APK minificado (sha device ↔ local `acb3ee40…`): arranque en frío → sesión, vehículo, dirección y distancia. Cero errores en el proceso. |
| **escritura** de Firestore en device | ✅ zona creada desde la app con `isPrivate` puesto a `true` **a propósito**, para que el valor NO fuese el default |
| **el documento escrito, leído desde Firestore** | ✅ **los 11 campos del `ZoneDto`, con sus tipos correctos** (ver tabla) |
| **viaje de vuelta** | ✅ `force-stop` + arranque en frío → el chip de la zona reaparece |

### El documento que escribió la app minificada, leído de `users/{uid}/zones`

| campo | valor | tipo |
|---|---|---|
| `id` | `49e04d82-5ac5-40ef-8cff-f112c9eb1acf` | string |
| `userId` | `0l7X59ptXeQ4WUMlpFOZHLPIJAI3` | string |
| `name` | `R8Test` | string |
| `lat` / `lon` | `36,6333006561033` / `−6,228157058358192` | double |
| `iconKey` | `other` | string |
| `createdAt` / `updatedAt` | `1788900848556` / `1788900848629` | integer |
| `radiusMeters` | `250` | double |
| **`isPrivate`** | **`true`** | boolean |
| `notifyOnSpot` | `false` | boolean |

**`isPrivate: true` es la prueba fina del ticket.** Un serializador que hubiera dejado de resolver
no habría reventado: habría escrito el documento sin ese campo, o con el default `false`. Ver el
valor no-default en Firestore es lo que distingue *"escribió algo"* de *"escribió bien"*, y es la
razón por la que este ticket exigía device y no se podía cerrar con la suite en verde.

> ⚠️ Nota de método: `firestore_query_collection` (endpoint `runQuery`) falla desde esta máquina con
> `The requested 'read_time' cannot be in the future` — desfase de reloj local, no un problema de
> datos. `list_collections` y `list_documents` usan otro endpoint y sí funcionan. Usar esos.

> 🧹 **Rastro dejado en la cuenta de pruebas:** la zona `R8Test` sigue creada. No se encontró la
> acción de borrado ni en Ajustes ni con long-press sobre el chip del mapa, y no se insistió a ciegas
> por estar cerca de la "ZONA DE PELIGRO". Borrarla a mano.
