# DET-A-FIX-MUST-SAY-WHERE-IT-CAME-FROM-001 · un fix dice su precisión pero no de dónde salió

**Estado:** ✅ Done — en master (30-08-2026). ⏳ Sin instalar en device y sin conducir: el valor del
ticket sólo se cobra en el primer `parkdiag` con `src=` de un viaje real.

## Problema

Field-test de la noche del **29→30 de agosto de 2026**, Redmi Note 11 (`2201117TY`, uid
`itmGbBxaz8ZJkLUlwvOnWDnMDto1`). El viaje de la 01:20 produjo **38 fixes consecutivos descartados
por precisión** en once minutos de conducción real:

```
01:20:34  ⊘ ignoring driving-speed fix with poor accuracy (speed=6.377135 acc=92.816  > minGpsAccuracyForDriving=50.0)
01:20:45  ⊘ ignoring driving-speed fix with poor accuracy (speed=9.17     acc=117.978 > minGpsAccuracyForDriving=50.0)
…
01:22:25  ⊘ ignoring driving-speed fix with poor accuracy (speed=11.96    acc=245.678 > minGpsAccuracyForDriving=50.0)
…
01:23:35  ⊘ ignoring driving-speed fix with poor accuracy (speed=9.03     acc=151.391 > minGpsAccuracyForDriving=50.0)
```

Desenlace: dos prompts `weak_evidence` (01:24:58 y 01:34:29), un agujero de GPS de **135.910 ms**
con el coche visto por última vez a 11,81 m/s, y el aparcamiento salvado a la 01:49 como **zona de
250 m de radio con fiabilidad 0,5** (`path=unattended_zone_gap_anchor`, pin `825dcb60`) en lugar de
un pin exacto. La doctrina aguantó — no se plantó ninguna plaza fantasma — pero el usuario recibió
un círculo en vez de un punto.

**La pregunta que el log no puede contestar: ¿esos 38 fixes eran GNSS con mala geometría, o fixes de
red/WiFi que nunca debieron pesar como medición de conducción?** El guard se llama
`minGpsAccuracyForDriving` pero se aplica a ciegas sobre cualquier fix que entregue el
`FusedLocationProvider`, sin saber cuál de los dos mundos lo produjo.

El dato existe fuera de la app. `dumpsys location` del propio Redmi, leído el 30-08:

```
gps      → Location[gps 36.608432,-6.278171 hAcc=209.89 vel=0.0 sAcc=1.18 {Bundle[{satellites=4, maxCn0=33, meanCn0=27}]}]
network  → Location[network 36.608440,-6.278158 hAcc=14.85 alt=89.6 vAcc=1.0]
```

En ese instante el fix de red era **14× más preciso** que el de GNSS. Cualquier decisión que tomemos
sobre umbrales de precisión sin saber la procedencia es una decisión a ciegas.

## Doctrina violada

Ninguna de detección — este ticket **no cambia ni una decisión**. Viola la regla de diagnóstico de
[[feedback_detection_trigger_provenance]] un nivel más abajo: *en un diagnóstico hay que poder
identificar siempre qué produjo cada dato*. Está resuelto para el trigger que arma una sesión
(`armEvidence`) y para el camino que planta un pin (`detectionPath`), y **no lo está para la señal
más numerosa de todas**, el fix.

## Señales / datos disponibles

`GpsPoint` (`domain/model/GpsPoint.kt`) tiene cinco campos y ninguno dice de dónde viene:

```kotlin
data class GpsPoint(latitude, longitude, accuracy, timestamp, speed)
```

`android.location.Location` sí trae con qué distinguirlo:

- `provider` — `"gps"` · `"network"` · `"fused"` · `"passive"`. ⚠️ **Por sí solo no basta**: el
  `FusedLocationProviderClient` etiqueta casi todo como `"fused"`. Se registra crudo, no se confía
  en él como única prueba.
- `extras["satellites"]` — presente **solo** en fixes derivados de GNSS (verificado arriba en el
  `dumpsys` del Redmi: el fix `gps` lo trae, el `network` no). Es el discriminador real.

Por eso se capturan **los dos hechos crudos**, y la etiqueta se deriva de ellos — no se guarda una
conjetura del origen y se tira la evidencia.

## Diseño

**Un solo sitio.** La procedencia es una propiedad del fix, así que vive en `GpsPoint`, que es por
donde pasan absolutamente todos. Dos campos nullable con default, para que los cientos de sitios de
construcción existentes (tests, previews, trazas de replay) compilen sin tocarse:

```kotlin
data class GpsPoint(
    …,
    val provider: String? = null,
    val satelliteCount: Int? = null,
)
```

Los rellena `AndroidLocationDataSourceImpl` en sus tres puntos de mapeo (`createCallback`,
`getLastKnownLocation`, y el stream de UI queda fuera porque `UserLocationUi` no es un `GpsPoint`).
iOS los deja a `null` — `CLLocation` no expone provider, y ahí la pregunta no se plantea igual.

Una función pura de presentación de log deriva la etiqueta legible a partir de los dos hechos:
`gnss(4sat)` · `network` · `fused` · `passive` · `?`.

### Consumidores del log a cablear

| Sitio | Línea hoy | Qué le falta |
|---|---|---|
| `CoordinatorParkingDetector:855` | `─ loc#N lat= lon= speed= acc= sessionAge=` | `src=` |
| `StopTracking:283` | `⊘ ignoring driving-speed fix with poor accuracy (…)` | `src=` — **es la línea de este ticket** |
| `GetOneLocationUseCase` (`PARKDIAG/OneFix`) | `fix lat= lon= speed= acc= age=` | `src=` |
| `RunDepartureCheckUseCase:92` (`PARKDIAG/Depart`) | `attempt=N geof= speed= acc= → verdict` | `src=` |
| `DetectionEventDto.toDto()` | `base` ya lleva `lat/lon/accuracy/speed` | columna nueva para la procedencia |

⚠️ **La precisión NO hay que añadirla**: las cuatro líneas locales ya imprimen `acc=`, y `base` ya
manda `accuracy` a Firestore en todo evento que lleve localización. Lo único que falta es el origen.

### Remoto

`DetectionEventDto` no tiene columna libre que puedan compartir todos los eventos con localización
(`source` ya la usan `DepartureVerdict`, `GeofenceRegistration` y `Sentry` para otra cosa). Se añade
**una** columna nullable, rellenada en `base` — así la procedencia llega en TODO evento que lleve
un fix, no solo en `LOCATION_FIX`, y el replay de trazas la ve.

### Fuera de alcance, a propósito

- **No entra en ninguna decisión.** Ni un umbral se mueve, ni un guard mira la procedencia. Primero
  se mide una semana de campo; con los datos delante se decide si un fix de red puede o no sostener
  una prueba de conducción. Prometer el fix antes de medir es justo lo que la memoria del proyecto
  recoge como error repetido.
- **No se persiste en `Spot` ni en `UserParking`.** Sería un campo nuevo en Room + Firestore + sus
  serializers una semana antes de lanzar, para una pregunta que el diagnóstico ya contesta.
  Follow-up si la medición dice que merece la pena.

## Criterio de éxito

1. Un `parkdiag` nuevo permite responder, sin cable ni conjetura, si los fixes descartados por
   precisión de una ventana concreta eran GNSS o de red.
2. Los eventos de Firestore con localización llevan la procedencia → la misma pregunta se contesta
   en remoto, para móviles a los que no tenemos acceso físico (**esto es lo que hace la beta
   diagnosticable**).
3. Tests unitarios de la derivación de la etiqueta, incluidos los casos degenerados: sin provider,
   provider desconocido, `satellites=0`.
4. Suite verde y `assembleMockDebug` sin romper.

## Consumidores auditados

### Sitios que construyen un `GpsPoint` sin procedencia

Ninguno es un fix: los siete reconstruyen una POSICIÓN desde almacenamiento o la fabrican. Todos
quedan en `?`, y eso es precisamente lo correcto — es la razón de que la etiqueta desconocida exista
en vez de un default.

| Sitio | Qué reconstruye | Veredicto |
|---|---|---|
| `ParkingSessionMapper:25` | pin guardado ← Room/Firestore | ✅ `?` honesto |
| `SpotDtoMapper:20,104` | plaza comunitaria ← Firestore | ✅ `?` honesto |
| `CoordinatorDetectionService:1161` | última posición presenciada ← prefs | ✅ `?` honesto |
| `AndroidDetectionStepAnchors:78` | ancla de pasos ← prefs | ✅ `?` honesto |
| `OverpassRoadNetworkDataSourceImpl:38` | geometría de calle (OSM) | ✅ exento, no es un fix |
| Previews / `FakeData` / trazas de replay | datos sintéticos | ✅ `?` honesto |

⚠️ **La persistencia NO cambia**: `GpsPoint` no se serializa entero a ninguna parte. `Spot` y
`UserParking` viajan por mappers explícitos con columnas planas (`lat`/`lon`/`accuracy`), así que los
dos campos nuevos no tocan Room, ni Firestore, ni ningún serializer. Verificado leyendo los tres
mappers, no supuesto.

### Sitios que leen `accuracy` para juzgar conducción

`StopTracking` · `FixReduction` · `DriveProof` · `HoldResolutionStage` ·
`VerifyDepartureEvidenceUseCase` · `EvaluateSafetyNetCheckUseCase` · `EvaluateHonestCloseUseCase` ·
`EvaluateBtParkUseCase`.

**Los ocho quedan exentos a propósito y sin tocar.** Son los que un día podrían querer distinguir un
fix de red de uno de satélite, y son exactamente los que no se tocan hasta tener la semana de campo
medida. Enumerarlos aquí es el trabajo del barrido: cuando llegue esa decisión, la lista de a quién
barrer ya está escrita.

## Estado de ejecución

- ✅ `GpsPoint` + `FixProvenance.provenanceLabel()` + `AndroidLocationDataSourceImpl.toGpsPoint()`
  (las dos rutas de mapeo unificadas en una).
- ✅ Cuatro líneas de `parkdiag` con `src=`: `loc#N` · `⊘ ignoring driving-speed fix` · `OneFix` ·
  `Depart`.
- ✅ Columna `fixSource` en `DetectionEventDto`, rellenada en `base`.
- ✅ `FixProvenanceTest` — 8 casos, incluidos `satellites=0`, provider vacío y provider desconocido.
- ✅ **1.802 tests, 0 fallos** · `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` verdes.
- ⏳ Sin instalar en device y sin conducir.
