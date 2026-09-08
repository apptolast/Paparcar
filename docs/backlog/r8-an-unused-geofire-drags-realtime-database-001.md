# R8-AN-UNUSED-GEOFIRE-DRAGS-REALTIME-DATABASE-001 · GeoFire no se usa, y arrastra Realtime Database entera al APK

**Estado:** ✅ Done · Paso 1 de 7 del plan de R8 (ver `docs/backlog/` para los siguientes)

## Problema

Play Console marca en rojo las métricas de calidad del bundle 11 (1.1.0): **43 % de optimización,
ofuscación y reducción**, y **18,6 MB de DEX sin comprimir** contra un umbral de alerta de 10 MB.

Al auditar por qué, aparece una dependencia que no usa nadie. `shared/build.gradle.kts:201` declara
`implementation(libs.geofire.android)` con el comentario *"GeoFirestore — proximity queries via
geohash"*. Pero el geohashing es **propio**: `com.rndeveloper.paparcar.data.geohash.encodeGeohash` y
`geohashQueryBounds`, consumidos en `FirebaseDataSourceImpl.kt:12` y
`FirebaseAnalyticsTracker.kt:3`.

Búsqueda sobre master `f8e0a665` en `shared/src` + `app/src` de
`geofire|GeoFire|GeoLocation|FirebaseDatabase`: **cero referencias**. Todos los aciertos de
"geohash" son nuestro propio campo y nuestra propia función.

El coste medido en el DEX del `.aab`:

| paquete | clases | bytecode | por qué está |
|---|---|---|---|
| `com.firebase.geofire` | 49 (sin ofuscar) | — | anclado por `-keep` (línea 61 del `.pro`) |
| `com.google.firebase.database` | **435** | **128.762 bytes** | dependencia POM de `geofire-android` |

`geofire-android-3.2.0.pom` (verificado en la caché de Gradle) depende de **`firebase-database`
20.0.5** — Realtime Database entera, en una app que solo usa Firestore. No hay una sola referencia a
`FirebaseDatabase` en el código.

Y no se poda: `usage.txt` del build de release dice que R8 eliminó **0** clases de
`com.google.firebase` y **0** de `com.firebase.geofire`, porque `app/proguard-rules.pro:55` y `:61`
las anclan con `-keep … { *; }`.

## Doctrina violada

Ninguna de las reglas de detección ni de UI. La que se rompe es la de higiene de build: **una
dependencia declarada es un contrato con el usuario final** — viaja en su descarga, ocupa su disco y
entra en las métricas que Play le enseña. Una que no se usa es peso muerto que además nadie va a
descubrir, porque compila y pasa los tests exactamente igual.

Corolario del mismo fallo: la regla `-keep` de las líneas 61-62 mantiene **viva** a `geofire`. Sin
esa regla, R8 la habría eliminado sola y el problema habría sido invisible en vez de permanente. Un
`-keep` sobre código muerto no protege nada: lo resucita.

## Señales / datos disponibles

Todo medido sobre `app/build/outputs/mapping/prodRelease/` del build de release (AGP 9.3.2 emite el
R8 Configuration Analyzer sin flags):

- `configanalyzer.pb` — radio de cada regla. `-keep class com.firebase.geofire.** { *; }` ancla
  **49 clases, 171 métodos, 360 campos**.
- `usage.txt` — 0 clases eliminadas en `com.firebase.geofire` y `com.google.firebase`.
- `dexdump -d` sobre los dos DEX del bundle — `com.google.firebase.database`: 435 clases, 2.556
  métodos, 128.762 bytes de bytecode.
- `geofire-android-3.2.0.pom` — la arista `geofire-android → firebase-database:20.0.5`.

## Diseño

No hay sistema que diseñar: se borra la dependencia y la regla que la sostenía. Lo que sí hay es
**dónde vive la decisión**, y son cuatro sitios que se tocan a la vez o el borrado queda a medias:

1. `shared/build.gradle.kts:200-201` — la declaración `implementation` y su comentario.
2. `gradle/libs.versions.toml:224` — el alias `geofire-android`.
3. `gradle/libs.versions.toml:107` — la versión `geofire = "3.2.0"`.
4. `app/proguard-rules.pro:60-62` — el bloque `-keep` + `-dontwarn`.

El `-dontwarn` se va con el resto: sin la dependencia en el classpath no hay nada de lo que R8
pueda avisar, y un `-dontwarn` huérfano es exactamente el tipo de línea que sobrevive años porque
nadie sabe si tapa algo.

**Fuera de alcance a propósito:** el resto del plan de R8 (borrar los `-keep` de paquete completo de
Firebase, GMS, BaseLogin y GitLive) va en tickets separados. Un cambio por bundle, para poder
bisecar si algo se rompe en producción.

## Criterio de éxito

1. `./gradlew :shared:testDebugUnitTest` en verde (1.762+ tests).
2. `:app:compileProdDebugKotlin` y `:app:compileMockDebugKotlin` compilan — el proyecto tiene
   `allWarningsAsErrors`, así que un import huérfano rompe el build.
3. `./gradlew :app:dependencies --configuration prodReleaseRuntimeClasspath | grep -i "database\|geofire"` → **vacío**.
4. El DEX del `.aab` baja, y `usage.txt` deja de listar `com.google.firebase.database` como vivo.
5. En device: las plazas cercanas siguen apareciendo (la query por bounds de geohash es lo único que
   podría haber dependido de GeoFire, y no depende).

## Consumidores auditados

`grep -rin "geofire|GeoFire|GeoLocation|firebase.database|FirebaseDatabase"` sobre `shared/src` y
`app/src` en master `f8e0a665`:

| sitio | veredicto |
|---|---|
| `data/geohash/` (`encodeGeohash`, `geohashQueryBounds`) | **exento** — implementación propia, no toca GeoFire |
| `FirebaseDataSourceImpl.kt:28-38, 160, 202, 219` | **exento** — usa `geohashQueryBounds` propio y el campo `"geohash"` de Firestore |
| `SpotDtoMapper.kt:61, 90` | **exento** — `encodeGeohash` propio |
| `FirebaseAnalyticsTracker.kt:15, 65, 71-72` | **exento** — celda geohash propia |
| `SpotRepositoryImpl.kt:65`, `ObserveZoneSpotAlertsUseCase.kt:103` | **exento** — comentarios |
| `AppDatabaseV1BaselineTest.kt:206` | **exento** — el nombre de la columna Room |
| imports de `com.firebase.geofire.*` | **cero** |
| usos de `FirebaseDatabase` | **cero** |

## Medición

Dos builds de `:app:bundleProdRelease` sobre el MISMO commit base (master `f8e0a665`), uno antes y
otro después del cambio, en el worktree de la tarea. El baseline importa: master ha crecido desde el
bundle 11 que reportó Play, y sin medir aquí el delta no sería atribuible.

| | antes | después | delta |
|---|---|---|---|
| DEX sin comprimir | 20.190.456 B (19,3 MB) | **19.803.716 B (18,9 MB)** | **−386.740 B (−378 KB)** |
| clases en el DEX | 23.308 | **22.868** | **−440** |
| clases sin ofuscar | 13.147 (56,4 %) | 12.719 (55,6 %) | −428 |
| ofuscación | 43,6 % | **44,4 %** | +0,8 pp |
| `com.firebase.geofire` vivas | 37 | **0** | −37 |
| `com.google.firebase.database` vivas | 429 | **25** | −404 |

Las 25 clases que sobreviven son `com.google.firebase.database.collection.*`, de
`firebase-database-collection:18.0.1`, que es dependencia de **Firestore** (sus mapas ordenados
inmutables) y no de Realtime Database. `firebase-database` ya no aparece en
`prodReleaseRuntimeClasspath`: verificado con
`./gradlew :app:dependencies --configuration prodReleaseRuntimeClasspath`, cuyo único acierto para
`geofire|firebase-database` es esa `-collection`.

El +0,8 pp de ofuscación no es que R8 renombre más: es que **desaparecen 428 clases que viajaban sin
ofuscar**, así que el denominador encoge. La ofuscación de verdad se mueve en los tickets que borran
los `-keep` de paquete completo.

## Verificación

| criterio | resultado |
|---|---|
| `:shared:testDebugUnitTest` | ✅ **2.402 tests, 0 fallos** |
| `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` | ✅ (con `allWarningsAsErrors`) |
| `:app:bundleProdRelease` | ✅ |
| `firebase-database` / `geofire` fuera del classpath | ✅ solo queda `firebase-database-collection` (Firestore) |
| pantalla de licencias OSS | ✅ se regenera sola — la lista es una proyección del grafo, y `OpenSourceAttributionGuardrailTest` va dentro de los 2.402 |
| arranque del build MINIFICADO | ✅ `assembleProdRelease` instalado en emulador (sha256 device ↔ local idéntico): login renderizado con strings resueltos, `topResumedActivity=MainActivity`, cero `FATAL` / `NoClassDefFound` |
| mapa y sesión en device real | ✅ **cerrado el 08-09** en el Oppo, sobre el APK minificado de master (sha device ↔ local `ae36f3cd…`), con cuenta real de Google: login, mapa con tiles y marcador del vehículo, y el peek trayendo la sesión de Firestore («FORD FOCUS · APARCADO · Lugar la Cañada 205 · hace 22h · a 20 m de ti»). La query de proximidad por bounds de geohash —lo único que podría haber dependido de GeoFire— sigue funcionando. |
