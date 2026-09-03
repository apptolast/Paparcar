# MAP-CLUSTER-SEED-MUST-NOT-USE-THE-AMBIGUOUS-REMOVEFIRST-001 · El clustering de plazas no puede usar el `removeFirst()` de Kotlin sobre una `List`

**Estado:** ✅ Done — rama `bugfix/MAP-CLUSTER-SEED-MUST-NOT-USE-THE-AMBIGUOUS-REMOVEFIRST-001-cluster-removefirst`
· worktree `../Paparcar-cluster-removefirst`

## Problema
Play Console, al revisar el AAB de la versión 1.0.0(4), reportó: *"Tu aplicación usa las funciones
de extensión `removeFirst()`/`removeLast()` de Kotlin, que entran en conflicto con las funciones de
Java en Android 15. Esto provocará que las aplicaciones fallen en dispositivos con Android 14 o
versiones anteriores."* La ubicación que Play mostró en el aviso (`dev.gitlive.firebase.firestore.
TimestampSerializer.<clinit>`) resultó ser una atribución equivocada o de un build anterior: al
desensamblar el dex real del APK/AAB compilado con `dexdump`, `TimestampSerializer` y su cadena de
inicialización no contienen ninguna llamada a `removeFirst`/`removeLast` en ningún punto.

El sitio real, confirmado por bytecode (`invoke-interface {v2}, Ljava/util/List;.removeFirst:()
Ljava/lang/Object;` — el patrón exacto que choca con el método por defecto que
`java.util.SequencedCollection` añade a `List` en Android 15/API 35), está en
`PaparcarMapView.kt:580`, dentro de `clusterSpots()`:

```kotlin
val remaining = spots.toMutableList()   // java.util.ArrayList real
...
val seed = remaining.removeFirst()      // ⛔ invoke-interface ambiguo
```

Al ser `remaining` un `MutableList` respaldado por `java.util.ArrayList`, el compilador resuelve la
llamada al método de interfaz de la plataforma en vez de a la extensión segura de Kotlin
(`CollectionsKt.removeFirst`, que internamente hace `removeAt(0)`). En un dispositivo con Android 14
o inferior ese método de interfaz no existe → `NoSuchMethodError` en tiempo de ejecución.

Se auditó también `CoordinatorParkingDetector.kt:523` (`creepWindow.removeFirst()`), la única otra
ocurrencia de este nombre de método en el código propio: **no aplica**, `creepWindow` es un
`kotlin.collections.ArrayDeque`, que declara `removeFirst()` como miembro propio de la clase — sin
ambigüedad con `java.util.List`, confirmado también en el dex (`invoke-virtual` sobre
`Lkotlin/collections/ArrayDeque;`, nunca `invoke-interface` sobre `Ljava/util/List;`).

## Doctrina violada
Ninguna de las reglas del proyecto directamente — es un problema de compatibilidad Kotlin/Android
15 (KT-71375), no de arquitectura. Se corrige donde vive el invariante roto: la única llamada
ambigua real, no la que señaló el aviso de Play.

## Señales / datos disponibles
- Aviso de Play Console sobre la versión 1.0.0(4), sección "Calidad técnica".
- `keytool`/`dexdump` del `app-prod-debug.apk` y del `app-prod-release.aab` firmados: confirmado que
  tras el fix, **ninguno** de los dos dex de la release contiene el patrón
  `invoke-interface ... Ljava/util/List;.removeFirst/removeLast`.

## Diseño
Cambio mínimo y local: `remaining.removeFirst()` → `remaining.removeAt(0)`, semánticamente
idéntico (elimina y devuelve el primer elemento) pero sin pasar por el método de interfaz
ambiguo. No se ha tocado `creepWindow` (uso ya seguro).

## Criterio de éxito
- El AAB firmado (`bundleProdRelease -PuploadCrashlyticsMapping=true`) recompilado tras el fix no
  contiene el patrón peligroso en ningún dex — verificado con `dexdump` línea a línea.
- Pendiente de confirmar en Play Console tras la nueva subida que el aviso desaparece.

## Consumidores auditados
`grep -rn "\.removeFirst()\|\.removeLast()"` sobre `shared/src` y `app/src`: 2 resultados totales,
ambos revisados arriba (uno corregido, uno exento por tipo `ArrayDeque`). Ningún otro sitio en el
código propio usa estos nombres de método.
