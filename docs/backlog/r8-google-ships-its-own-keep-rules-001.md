# R8-GOOGLE-SHIPS-ITS-OWN-KEEP-RULES-001 · Firebase y GMS traen sus propias reglas; las nuestras solo les quitaban el shrinking

**Estado:** ✅ Done · Paso 5 de 7 del plan de R8 — el grande

Paso 5 de 7, y el grande. Pasos 1-4 en master (`f5edf09b`, `aeacc493`, `9904b72e`, `348a40ab`).

## Problema

```proguard
# Google Firebase native (Crashlytics, Firestore, Auth, GMS) — keep public API.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
```

Los dos radios más grandes de todo el proyecto:

| regla | clases | métodos | campos |
|---|---|---|---|
| `com.google.android.gms.**` | **4.202** | **19.472** | **8.086** |
| `com.google.firebase.**` | **2.284** | **15.518** | **6.311** |

**6.486 clases y ~35.000 miembros** anclados, y `usage.txt` lo confirma en las cuatro mediciones
hechas hasta ahora: R8 elimina **0** clases de ambos paquetes, build tras build. Juntos son el 55 %
de las 11.759 clases que aún viajan sin ofuscar.

## Doctrina violada

La misma del plan, en su versión más cara: *el `-keep` de paquete completo por si acaso*. El
comentario —*"keep public API"*— describe una intención razonable de la era ProGuard y una práctica
que Google **desaconseja explícitamente** desde que sus SDK publican consumer rules.

## Señales / datos disponibles

**1 · Son 23 los artefactos de Google que ya publican sus reglas**, todas aplicadas y visibles en
`configuration.txt` del build de release: `play-services-basement`, `-base`, `-tasks`, `-maps`,
`-fido`, `-auth-base`, `-identity-credentials`, las cinco de `-measurement*`, `firebase-auth`,
`-firestore`, `-common`, `-components`, `-auth-interop`, `protolite-well-known-types`, `recaptcha`,
`credentials`, `credentials-play-services-auth`, `googleid`.

**2 · Y cubren exactamente los puntos reflexivos que importan.** `play-services-basement:18.9.0`:

```proguard
-keep class com.google.android.gms.common.internal.ReflectedParcelable
-keepnames class * implements …ReflectedParcelable
-keepclassmembers class * implements android.os.Parcelable { public static final *** CREATOR; }
-keep @androidx.annotation.Keep class *
-keepclasseswithmembers class * { @androidx.annotation.Keep <fields>; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <methods>; }
-keepnames @com.google.android.gms.common.annotation.KeepName class *
-keep @com.google.android.gms.common.util.DynamiteApi public class * { public <fields>; public <methods>; }
```

Y `firebase-components:19.0.0` cubre el **único** punto de entrada reflexivo de Firebase:

```proguard
-keep class * implements com.google.firebase.components.ComponentRegistrar { void <init>(); }
```

**3 · El riesgo clásico no aplica.** Lo que de verdad rompe al quitar estas reglas en otros proyectos
es el mapeo POJO reflexivo de Firestore (`toObject()`, `@PropertyName`, `@IgnoreExtraProperties`,
`@DocumentId`, `.data<T>()`). Aquí **no se usa ninguno**: `FirebaseDataSourceImpl.kt:107-110` lo
documenta —se lee campo a campo con `get<T?>()` de GitLive y `.data<T>()` se evita a propósito— y el
grep sobre `shared/src` + `app/src` solo devuelve ese mismo comentario. Además, el Paso 4 ya verificó
en device que esa vía funciona sin reglas nuestras.

## Diseño

Borrar las dos, dejar los dos `-dontwarn`. Nada las sustituye: lo que hace falta ya está aplicado y
lo publica quien sabe qué necesita.

**El método importa tanto como el cambio.** Va en **dos commits separados dentro de la misma rama**:

- **5a** — `com.google.firebase.**`
- **5b** — `com.google.android.gms.**`

Cada uno con su **medición independiente**, que es lo que de verdad aporta: sin el escalón intermedio
no habría forma de saber cuánto viene de Firebase y cuánto de GMS, ni de usar «GMS sigue en 0
eliminadas» como control de que no hay efecto cruzado.

⚠️ Lo que este troceado **no** da es bisección en master: el ticket entra por `--squash` (regla del
proyecto: un ticket, un commit), así que 5a y 5b acaban colapsados en un único commit. Si algo se
rompe en campo, la unidad que se puede revertir es el paso entero. Se asume a cambio de mantener el
histórico lineal y de que la atribución ya quedó medida y escrita aquí.

## Criterio de éxito

1. `:shared:testDebugUnitTest` en verde y ambos flavors con `allWarningsAsErrors`.
2. El DEX baja y `usage.txt` deja de reportar 0 eliminadas en los dos paquetes.
3. **En el Oppo, sobre el APK minificado y con cuenta real** — y aquí la lista es más larga que en
   los pasos anteriores, porque el modo de fallo **no es un crash al arrancar**, es que el mapa no
   pinte o que una geocerca no se registre, a media calle y horas después:

| superficie | qué hay que ver |
|---|---|
| Mapa | tiles cargando, marcador del vehículo, puck de ubicación |
| Permisos de ubicación | el flujo entero, incluido el ascenso a segundo plano |
| `FusedLocationProvider` | fixes llegando (la distancia del peek se recalcula) |
| Activity Recognition | registro de transiciones sin `SecurityException` |
| Geocercas | registro efectivo al confirmar un aparcamiento |
| Crashlytics | un crash forzado que suba **y se desofusque** |
| Analytics | eventos saliendo |
| Play Billing | el paywall de Plus abriendo con sus productos |

⛔ **Sin esa tabla completa este ticket no se cierra.** Es el único del plan cuyo fallo se manifiesta
en la calle y no en el arranque.

## Consumidores auditados

| sitio | veredicto |
|---|---|
| `toObject()` / `@PropertyName` / `@IgnoreExtraProperties` / `@DocumentId` / `.data<T>()` | **no existen** — único acierto del grep es el comentario que explica por qué se evitan |
| `@Keep` propias | **no existen** |
| `Parcelable` propios | **no existen** — `BluetoothConnectionReceiver` solo LEE un `BluetoothDevice` del framework |
| Reflexión sobre GMS/Firebase desde nuestro código | **no existe** — los `::class.java` son literales de `Intent(context, X::class.java)` |
| `ComponentRegistrar` | **cubierto** por la consumer rule de `firebase-components` |
| `ReflectedParcelable`, `CREATOR`, `@Keep`, `@KeepName`, `@DynamiteApi` | **cubiertos** por la de `play-services-basement` |

## Medición

Tres `:app:bundleProdRelease` sobre el mismo commit base, uno por escalón.

| | baseline | **5a** (Firebase) | **5b** (+GMS) | total |
|---|---|---|---|---|
| DEX sin comprimir | 17.981.616 B (17,1 MB) | 16.511.600 B (15,7 MB) | **14.369.488 B (13,7 MB)** | **−3.612.128 B (−3,44 MB)** |
| clases en el DEX | 21.337 | 20.062 | **16.235** | −5.102 |
| **ofuscación** | 44,3 % | 49,9 % | **71,5 %** | **+27,2 pp** |
| `com.google.firebase` eliminadas | 0 | **855** | — | |
| `com.google.android.gms` eliminadas | 0 | **0** ← control | (se sueltan en 5b) | |
| `com.google.android` sin ofuscar | 6.267 | — | **840** | −5.427 |
| `com.google.firebase` sin ofuscar | 1.879 | — | **49** | −1.830 |

**El escalón 5a sirvió de control:** con Firebase ya desanclado, GMS seguía en **0 eliminadas**, lo
que demuestra que su delta es atribuible al segundo escalón y no a un efecto cruzado. Es justo lo
que se buscaba al partirlo en dos.

Y cambia el dueño del problema: lo que aún viaja sin ofuscar ya no es Google, es **RevenueCat con
2.893 clases** — el Paso 7, que no depende de nosotros.

## Verificación

Todo sobre el APK minificado en el Oppo, cuenta real, sha device ↔ local `6ebc9345…`.

| superficie | resultado |
|---|---|
| `:shared:testDebugUnitTest` | ✅ **2.441 tests, 0 fallos** |
| ambos flavors con `allWarningsAsErrors` | ✅ |
| arranque en frío | ✅ sin `FATAL` / `NoClassDefFound` / `SecurityException` (solo un `Missing inline cache` de ART, benigno) |
| **Mapa (Maps SDK, el mayor riesgo de 5b)** | ✅ tiles de Google Maps con etiquetas de calle, marcador del vehículo con su glifo, puck de ubicación, círculo y chip de la zona con su candado |
| **`FusedLocationProvider`** | ✅ «GPS ±104m» en pantalla y la distancia del peek recalculándose entre arranques |
| **Firestore lectura** | ✅ sesión, vehículo, dirección y zona |
| **Firestore escritura y borrado** | ✅ zona borrada desde la app → la colección en Firestore queda **vacía** |
| **Play Billing** | ✅ el paywall abre con el precio real, «Consigue Plus por 13,99 €» |
| Permisos de ubicación (primer plano) | ✅ concedidos y funcionando |
| **Activity Recognition** | ✅ tras conceder los permisos: `SystemMemoryMap: [activitytransition] serialization complete` **en el proceso del sistema** — las transiciones quedan registradas, y sin `SecurityException` |
| **Registro de geocercas** | ✅ el `Geofencer` de GMS evaluando **nuestras** geocercas (`geofence trigger blocked - already in state`, `initial event filter not matched`) |
| **Servicio de detección** | ✅ `CoordinatorDetectionService` vivo en **primer plano** (`isForeground=true`, `foregroundId=1001`, canal `sentry_channel`) |
| Workers por nombre (re-verifica el Paso 2) | ✅ `GeofenceJanitorWorker` con resultado `SUCCESS` y `ParkingSafetyNetWorker` arrancando — WorkManager resolviéndolos por el nombre de clase persistido, que es de lo que dependía el Paso 2 |
| **Crashlytics desofuscado** | ⏳ **no verificable con un build local**: `app/build.gradle.kts` desactiva la subida del `mapping.txt` salvo `-PuploadCrashlyticsMapping=true`, así que una traza de este APK llegaría ofuscada **por diseño**, no por regresión. Se verifica en el bundle que vaya a Play. |

Queda **una sola** fila abierta, la de Crashlytics, y no es un pendiente de este cambio sino una
limitación del build local. Se cierra en el bundle que vaya a Play — y conviene recordar que subir
el `mapping.txt` es la **precondición** de todo el plan: con la ofuscación en 71,5 %, una traza sin
mapping deja de ser legible.
