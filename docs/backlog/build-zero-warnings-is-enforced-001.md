# BUILD-ZERO-WARNINGS-IS-ENFORCED-001 · Cero warnings, y que no vuelvan

**Estado:** ✅ Done · mergeado en master

## Problema

Antes de la prueba interna de Play Store, el build acumulaba **51 warnings de compilación** y 5
avisos de Gradle. Ninguno era un error, y precisamente por eso llevaban meses ahí: un warning que
nadie rompe es un warning que nadie arregla.

Lo que convierte esto en deuda y no en ruido son dos hallazgos del barrido:

- **Dos deprecados no estaban sin arreglar: estaban SILENCIADOS.** `FirebaseDataSourceImpl` llevaba
  dos `@Suppress("DEPRECATION")` sobre el mismo overload deprecado de Firestore que en
  `FirestoreDetectionEventLogger` seguía avisando a gritos. El invariante ("no usar el overload
  vararg, existe `updateFields`") estaba parcheado en dos sitios y roto en un tercero — el patrón
  exacto que la regla *sistemas, no parches* persigue.
- **Gradle 9.7.1 avisa de que el build no es compatible con Gradle 10.** Eso no se veía en el
  resumen: hacía falta `--warning-mode all` y una config cache fría para que se imprimiera.

## Doctrina violada

*Sistemas, no parches.* Un `@Suppress` no arregla un invariante: lo esconde y garantiza que el
siguiente call site vuelva a nacer roto.

## Señales / datos disponibles

Línea base medida el 2026-08-26 sobre `compileProdDebug` + `compileMockDebug` +
`compileProdDebugUnitTest`, y confirmada **idéntica** antes y después del bump de dependencias de
`BUILD-DEPS-AT-LATEST-STABLE-001` — el toolchain nuevo no introdujo ni retiró un solo aviso.

### Warnings de compilación (51 sitios)

| familia | n | qué es |
|---|---|---|
| `quadraticBezierTo` → `quadraticTo` | 7 | renombrado en Compose, mismo comportamiento |
| `?.` / `!!` redundantes | 20 | Kotlin 2.4 propaga el smart-cast a través de un `val` booleano intermedio; el residuo se ve porque el segundo uso de la misma línea ya iba sin `!!` |
| `?.` / `!!` redundantes en tests | 11 | `assertNotNull` de kotlin.test lleva contrato → después ya es no-nulo |
| `Condition is always 'true'` | 4 | comprobaciones de nulo detrás de un guard que ya las garantiza |
| opt-in `ExperimentalCoroutinesApi` | 11 | un único fichero de test |
| `Icons.Rounded.*` → `Icons.AutoMirrored.Rounded.*` | 3 | iconos direccionales, RTL-aware |
| `MenuAnchorType` → `ExposedDropdownMenuAnchorType` | 1 | renombrado en Material3 |
| `update(vararg Pair)` → `updateFields` | 1 visible **+ 2 silenciados** | overload deprecado de GitLive |
| opt-in `FlowPreview` | 1 | `.sample()` sigue en preview upstream |

### Avisos de Gradle (5) — solo 2 accionables aquí

| aviso | destino |
|---|---|
| `Deprecated 'Android Style' Source Directory` (`src/mock/kotlin`) | ✅ este ticket |
| bloque `firebaseAppDistribution { }` deprecado dentro de `buildTypes` | ✅ este ticket |
| `val x by getting` en `sourceSets` (deprecado en **Gradle 9.6**) | ✅ este ticket — ver hallazgo 5 |
| KMP + `com.android.application` deprecado desde AGP 9 | ⛔ exige partir el repo en `:shared` + `:app` → ticket propio, aparcado hasta después de la beta por decisión del user (26-08) |
| `android.builtInKotlin=false` deprecado | ⛔ igual — ya documentado en `gradle.properties` como workaround de ese mismo combo |
| `android.newDsl=false` deprecado | ⛔ igual |

## Diseño

Tres movimientos, en este orden:

1. **Arreglar los 51**, no silenciarlos. Ni un `@Suppress` nuevo.
2. **Quitar los dos `@Suppress("DEPRECATION")` de Firestore** y migrar los tres call sites a
   `updateFields`. Los otros dos `@Suppress("DEPRECATION")` del árbol
   (`BluetoothConnectionReceiver`, `AndroidGeocoderDataSourceImpl`) **se quedan**: son la rama
   *legacy* de un fork explícito por API level que `minSdk 26` obliga a mantener, no deuda.
3. **`allWarningsAsErrors`** una vez el contador esté a cero, para que la deuda no se pueda volver a
   acumular en silencio.

Sobre el punto 3 hay un límite honesto: `allWarningsAsErrors` es del compilador de Kotlin, así que
**no** convierte en error los 3 avisos de Gradle que quedan aparcados. Eso es lo que hace viable
activarlo hoy sin bloquear el build por algo que sólo se puede arreglar partiendo el proyecto.

## Criterio de éxito — resultado

Medido sobre un `clean` completo, con `allWarningsAsErrors` **ya activo** (141 tareas, 92 ejecutadas):

| gate | resultado |
|---|---|
| warnings de compilación | ✅ **0** (línea base: 51) |
| errores | ✅ 0 |
| avisos del build script | ✅ 0 (el `android { }` deprecado, resuelto) |
| `testProdDebugUnitTest` | ✅ **1.662 tests, 0 fallos — cero asserts editados** |
| `assembleProdRelease` (R8 + shrink) | ✅ APK generado |
| `assembleMockDebug` | ✅ APK generado — el flavor sigue vivo tras mover su source dir |
| `@Suppress` en el árbol | ✅ **dos menos**, ninguno nuevo |

Que los 1.657 tests pasen sin tocar un assert es la parte que importa: demuestra que quitar 31
`!!`/`?.` y 4 condiciones no cambió una sola decisión de detección.

## Hallazgos durante la ejecución (lo que la lectura no anticipó)

1. **`updateFields` NO es el mismo overload renombrado.** El mensaje de deprecación dice
   *"Use `updateFields` instead"* y da a entender un cambio de nombre; en realidad toma un **DSL**
   (`FieldsAndValuesUpdateDSL.() -> Unit`) con un `infix to`. La sustitución mecánica falló a
   compilar, que es la manera barata de enterarse.
   El riesgo real estaba en `sendSpotSignal`: al pasar de `Pair<String, Any?>` al DSL, el valor
   `FieldValue.increment(1)` cambia de reificarse como `Any?` a reificarse como `FieldValue`, y un
   serializador distinto ahí habría corrompido los contadores de aceptación/rechazo de plazas **sin
   fallar**. Verificado en las fuentes de GitLive: `FieldValue` lleva
   `@Serializable(with = FieldValueSerializer::class)`, así que ambos caminos acaban en el mismo
   serializador. Además el propio overload deprecado delega en este DSL, así que la conducta es
   idéntica por construcción.
2. **El deprecado de `android { }` sólo se ve si tocas el script.** No aparecía en ninguna medida
   previa porque el build script estaba cacheado y sólo se recompila al modificarlo. Resultó
   arreglable sin tocar `android.newDsl=false`: configurando el `ApplicationExtension` tipado.
3. **Quitar un `?.` puede destapar otro.** Al pasar `pair?.first?.x` a `pair.first?.x`, el segundo
   `?.` pasó a ser redundante también. Hicieron falta dos vueltas de compilador.
5. **Dos tickets verdes por separado no son un ticket verde junto.** Al rebasar sobre el master que
   ya traía el bump, aparecieron **6 warnings nuevos** en el build script: `val x by getting` quedó
   deprecado en **Gradle 9.6**, y este ticket se había medido entero sobre 9.5.1. Sustituido por
   `getByName("…") { }`. Si se hubiera mergeado sin re-medir la combinación, `allWarningsAsErrors`
   habría entrado en master junto a 6 avisos recién nacidos.
4. **Los `Condition is always 'true'` no escondían lógica perdida**, sino comprobaciones de nulo
   detrás de un guard que ya las garantizaba. Kotlin 2.4 las ve porque ahora propaga el smart-cast
   a través de un `val` booleano intermedio (`val ok = x?.y == true` ⟹ dentro de `if (ok)`, `x`
   es no-nulo). Eran invisibles con el analizador anterior.

## Consumidores auditados

| sitio | asumía | estado |
|---|---|---|
| `FirebaseDataSourceImpl.retractSpot` | overload vararg deprecado, silenciado con `@Suppress` | ✅ DSL, suppress fuera |
| `FirebaseDataSourceImpl.sendSpotSignal` | ídem | ✅ DSL, suppress fuera |
| `FirestoreDetectionEventLogger.flushSession` | ídem, sin silenciar | ✅ DSL |
| `RemoteUserProfileDataSourceImpl` (4 llamadas) | usa el overload `update(Map)` | ✅ exento — ese no está deprecado |
| `BluetoothConnectionReceiver:77` | `@Suppress("DEPRECATION")` | ✅ exento — rama legacy de un fork por API level (`getParcelableExtra` pre-Tiramisu), obligatoria con `minSdk 26` |
| `AndroidGeocoderDataSourceImpl:89` | `@Suppress("DEPRECATION")` | ✅ exento — ídem (`Geocoder.getFromLocation` síncrono) |
| `composeApp/src/mock/kotlin` | layout "Android Style" deprecado | ✅ movido a `src/androidMock/kotlin`; `res/` y `AndroidManifest.xml` se quedan en `src/mock/`, que es donde AGP los espera |
| `CLAUDE.md`, `README.md`, skill `det-change`, `DevMainActivity` | citaban la ruta vieja | ✅ actualizados |

## Alcance de `allWarningsAsErrors` — límite declarado

Se activa en **`androidTarget`**, no en el bloque `kotlin { }` global. Eso cubre `commonMain` +
`androidMain` + `androidUnitTest`: exactamente la superficie que se puede compilar desde Windows.

**`iosMain` queda fuera a propósito.** Ponerlo global habría significado activar un `-Werror` sobre
código que no puedo compilar aquí para comprobarlo, y entregarle un build potencialmente roto a la
rama `IOS-F0-001`. Extenderlo al target de iOS es trabajo de una sesión con Mac, donde primero se
pueda medir si `iosMain` está a cero. → follow-up.

Tampoco convierte en error los 3 avisos de Gradle que quedan aparcados (KMP+AGP, `builtInKotlin`,
`newDsl`), porque `allWarningsAsErrors` es del compilador de Kotlin, no de Gradle. Es justo lo que
permite activarlo hoy sin bloquear el build por algo que sólo se arregla partiendo el proyecto.
