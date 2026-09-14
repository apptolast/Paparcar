# DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001 · una salida se data por el fix que cruzó la valla, no por el instante en que nos enteramos

**Estado:** ✅ Done · mergeado en master el 14-09-2026

## Problema

Reporte del user (14-09-2026): *"se han subido spots de Fernando al mapa bastante rato después de
haberse marchado… no deberían subirse plazas libres a usuarios 2 horas después"*.

Fernando (`8fhNJGctpaVvb08P9Hpye9a4oa33`, vehículo emparejado por BT) no tenía el gate de
diagnósticos encendido, así que **la hora real de aquella salida no se puede datar** (activado el
14-09 para el próximo viaje). No hace falta: el defecto está en el código y es estructural.

Existe un gate de frescura escrito exactamente para esto — `freedSpotIsStillThere`, 10 min,
[DET-RECONCILE-001] — y **no puede dispararse nunca en el carril de la geocerca**, porque el
instante con el que lo alimentamos es el instante en que procesamos el evento:

```kotlin
// CoordinatorDetectionService.kt:756 · :819 · :852
val now = System.currentTimeMillis()
DepartureDetectionWorker.buildRequest(geofenceId = id, exitTimestampMs = now)
```

`now - now = 0 ≤ 10 min` → publica **siempre**. El gate solo atrapa el retraso de *WorkManager*
entre encolar y correr; no atrapa el retraso del *trigger*, que es el modo de fallo dominante en
un móvil sin exención de batería (Doze / OEM-kill), que es el caso de cualquier usuario real.

La rama `:852` es la que más duele: es la del EXIT **entregado lejos**, y su propio comentario dice
que eso huele a entrega retrasada del sistema (*"the car is moving + OEM lag"*). El caso de "nos
hemos enterado tarde" está identificado, comentado… y fechado como si acabara de ocurrir.

### Y al publicar tarde, la plaza además se rejuvenece

`ReportSpotWorker.buildRequest:162` estampa `KEY_TIMESTAMP = now` al encolar, y de ahí salen las
dos cosas: `reportedAt` (la píldora de frescura dice "hace 1 min", `SpotDtoMapper:53`) y
`expiresAt = releasedAtMs + 2 h`. Secuencia completa: se va a las 10:00 · el móvil duerme · a las
12:05 llega el EXIT · `exitAtMs = 12:05` · "0 minutos de antigüedad, publica" · la plaza sale
**fresca** y sigue en el mapa **hasta las 14:05**. Cuatro horas después de existir.

El guard `SPOT-OFFLINE-TTL-001` (`ReportSpotWorker:76-80`) no lo tapa: mide desde el encolado, que
ya es el instante tardío.

## Causa raíz: un parámetro que responde a DOS preguntas

`exitTimestampMs` viaja por toda la cadena de salida respondiendo a dos preguntas distintas:

| Pregunta | Quién la hace | Qué instante quiere |
|---|---|---|
| *¿Cuándo se disparó este trigger?* — para que el fix que lo resolvió no pueda además corroborarlo | `classifyDepartureSpeed` vía `DetectParkingDepartureUseCase:158` y `VerifyDepartureEvidenceUseCase:89` [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001] | el de **AHORA**, el del manejo |
| *¿Cuándo se fue el coche?* — para saber si la plaza sigue ahí | `freedSpotIsStillThere` vía `RunDepartureCheckUseCase:203` | el **MEDIDO**, el del cruce |

Cuando el trigger llega a su hora las dos coinciden y nadie nota nada. Cuando llega tarde, la
primera respuesta se hace pasar por la segunda y la plaza fantasma sale.

⚠️ Por eso **no vale** con cambiar el significado del campo existente: si `exitTimestampMs` pasara a
ser el cruce medido, la puerta de eco (`fixTimestampMs - eventTimestampMs >= departureProofMinGapMs`)
quedaría trivialmente satisfecha en toda entrega tardía y aceptaría el fix del propio trigger como
prueba independiente — reabriendo [DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001]. Son dos hechos y
necesitan dos campos.

## Doctrina violada

- **«Una hora desconocida no es una hora reciente»** — la enuncia el propio
  `FreedSpotIsStillThere.kt:21-28`, y `freedSpotIsStillThere(null)` devuelve `false` a propósito.
  Dos call sites lavan el `null` a `now` **antes** de preguntárselo:
  - `ParkingSafetyNetWorker.kt:514` → `action.tripStartedAtMs ?: now`
  - `DepartureDetectionWorker.kt:40-41` → key ausente → `?: Clock.System.now()`
  - (iOS: `IosDetectionController.kt:669` → `action.tripStartedAtMs ?: nowMs`)
- **El evento NOMINA, solo el movimiento MEDIDO confirma.** El instante del cruce es un dato
  medido por Play Services; el instante del manejo es una propiedad de nuestro scheduler.
- **Fallo asimétrico.** Publicar una plaza de hace 2 h le cuesta a un desconocido un viaje en
  balde; no publicarla cuesta una plaza que nadie podía creerse.

## Señales disponibles (ya las tenemos, no se usan)

- **`event.triggeringLocation.time`** — el fix que cruzó la valla. Se lee en
  `CoordinatorDetectionService:755` y se usan `latitude`/`longitude`/`accuracy` en cinco sitios;
  **`.time` no se lee en ninguno**. Salvo en un log: `GeofenceExitWitnessReceiver.kt:47` ya calcula
  `fixAge = System.currentTimeMillis() - it.time`. La app sabe que ese número significa algo, lo
  imprime y no decide con él.
- **`SafetyNetAction.DispatchDeparture.tripStartedAtMs`** — el evaluador YA data la salida bien
  (sello del ancla o boarding AR, `EvaluateSafetyNetCheckUseCase:402/438/466/478`). El despachador
  lo tira con un `?: now`.
- **`trueEpochMs`** del AR ENTER (`CoordinatorDetectionService:1119`) — el embarque medido, que en
  el carril `ArmMidTrip` ES el instante de la salida.

## Diseño

**Un hecho, un campo.** Se separan los dos instantes en toda la cadena de salida:

- `triggerAtMs` — cuándo se disparó/manejó este trigger. Es el de siempre (`now`), solo cambia el
  nombre, y sigue alimentando la puerta de eco. Renombrado en `RunDepartureCheckUseCase`,
  `DetectParkingDepartureUseCase` y `VerifyDepartureEvidenceUseCase` para que nadie vuelva a
  confundirlos por el nombre.
- `departedAtMs: Long?` — **el instante MEDIDO de la salida, o `null`**. Sin valor por defecto
  ([DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001]: un default aquí contestaría "acaba de
  pasar" para siempre, que es el bug). Es lo único que ve `freedSpotIsStillThere`.

**⛔ Prohibido `?: now` sobre un instante de salida.** Un `null` que llega al gate ya significa lo
correcto: no se publica.

**El predicado compartido**, en `domain/detection/` como el resto de la política pura
([DET-VERDICT-NOT-PREDICATE-001] — no produce `detectionPath` ni `outcome`, y lo consumen cuatro
sitios):

```kotlin
fun measuredDepartureAtMs(candidateAtMs: Long?, sessionStartMs: Long?, nowMs: Long): Long?
```

Devuelve el candidato solo si es admisible para ESTA sesión (`isAdmissibleEvidence`, que ya mata la
clase "evidencia anterior al aparcamiento", [DET-SESSION-BIRTH-001]) y no está en el futuro; si no,
`null`. Un reloj de proveedor desviado no puede rejuvenecer una plaza.

## Consumidores auditados

| Sitio | Qué recibe ahora `departedAtMs` | Clasificación |
|---|---|---|
| `CoordinatorDetectionService:819` (EXIT en el borde) | `triggerLoc?.time` vía `measuredDepartureAtMs` | **cerrado** |
| `CoordinatorDetectionService:852` (EXIT entregado lejos) | idem | **cerrado** — la vía donde mordió |
| `CoordinatorDetectionService:1165` (AR ENTER mid-trip) | `trueEpochMs` vía `measuredDepartureAtMs` | **cerrado** |
| `CoordinatorDetectionService:717` (watchdog) | ya pasaba `exitAtMs = null` | **cubierto** — sin cambio |
| `ParkingSafetyNetWorker:514` | `action.tripStartedAtMs`, sin `?: now` | **cerrado** |
| `DepartureDetectionWorker:40` | key nueva ausente → `null`, no `now` | **cerrado** |
| `IosDetectionController:452` | (pendiente, ver abajo) | **cerrado** |
| `IosDetectionController:669` | `action.tripStartedAtMs`, sin `?: nowMs` | **cerrado** |
| `IosDetectionController:429` | ya pasaba `event.timestamp` (el instante medido de la región) | **cubierto** — el carril iOS ya lo hacía bien en esta puerta |
| `DetectParkingDepartureUseCase:158`, `VerifyDepartureEvidenceUseCase:89` | siguen con el instante del trigger | **exento con razón** — es la puerta de eco, quiere el del manejo |
| `EvaluateSafetyNetCheckUseCase:208` (`exitDeliveredAtMs`) | sin cambio | **exento con razón** — es un hecho sobre la ENTREGA (mitad de la conjunción [DET-CONJUNCTION-001]) y su ventana `exitEnterPairWindowMs` está calibrada contra instantes de entrega |
| `ReleaseActiveParkingSessionUseCase` ("Me voy") | sin cambio | **exento con razón** — es el usuario pulsando ahora |
| `FinalizeDeducedDepartureUseCase` (promoción) | sin cambio | **fuera de alcance, ticket propio** → `SPOT-A-PROMOTION-INHERITS-THE-AGE-OF-ITS-DEPARTURE-001` |

## Criterio de éxito

- Un EXIT cuyo `triggeringLocation.time` sea de hace más de `spotPublishMaxAgeMs` **cierra la sesión
  y la valla pero NO publica**, y lo dice en el log con la edad real.
- Un EXIT entregado a su hora se comporta exactamente igual que hoy.
- Un despacho del safety net sin `tripStartedAtMs` no publica (hoy publica).
- La puerta de eco sigue rechazando el fix del propio trigger en una entrega tardía (test de
  regresión: es lo que se rompería si se hubiera cambiado el significado del campo en vez de
  separarlo).
- Campo: en el próximo viaje de Fernando con el gate ya activo, `SESSION_ENDED` permite contrastar
  la salida real contra `endedAtMs`.

## Estado de la ejecución (14-09-2026)

Implementado y verificado desde el worktree:

- `measuredDepartureAtMs` + `MeasuredDepartureInstantTest` (6 tests).
- `RunDepartureCheckUseCase` parte el parámetro en `triggerAtMs` + `departedAtMs: Long?`;
  `DetectParkingDepartureUseCase` y `VerifyDepartureEvidenceUseCase` renombrados a `triggerAtMs`
  para que el nombre deje de invitar a la confusión.
- `DepartureDetectionWorker` gana `KEY_DEPARTED_AT` con centinela `-1L` (`workData` no tiene Long
  nullable, y una key ausente leída con default es el mismo "ausente = todo bien" que este ticket
  mata).
- Productores: geocerca (borde y lejana) por `triggeringLocation.time`, AR mid-trip por
  `trueEpochMs`, safety net por `tripStartedAtMs` sin `?: now`. iOS en espejo.
- 3 tests de regresión nuevos en `RunDepartureCheckUseCaseTest`, uno por dirección:
  trigger fresco + salida de hace 2 h → **no** publica (el caso reportado) · trigger tardío + salida
  medida hace 2 min → **sí** publica · hora desconocida → **no** publica.

**Suite: 2815 tests verdes, 0 fallos** (255 clases, `:shared:testDebugUnitTest --rerun-tasks`).
Compilan `:shared:compileKotlinIosSimulatorArm64`, `:app:compileProdDebugKotlin` y
`:app:compileMockDebugKotlin`.

**Dev Catalog:** sin cambios — este ticket no introduce pantalla, estado MVI, variante ni condición
de routing. **Strings:** ninguno nuevo. **Provenance:** ningún `detectionPath` ni `armEvidence`
nuevo; el cambio no crea caminos de confirmación, solo data los que ya había.

**Riesgo asumido.** Ninguna puerta pasa a publicar más que antes: el cambio solo puede CALLAR
publicaciones que antes salían. El coste es perder alguna plaza real cuyo cruce no quedó medido —
un falso negativo, el lado que elige la doctrina.

⚠️ **La pérdida concreta, dicha en voz alta:** el despacho **vivo** del safety net (`preconfirmed =
false`, es decir "lejos del coche y a velocidad de conducción AHORA") llevaba `tripStartedAtMs =
null` y por tanto pasa a **no publicar**, donde antes publicaba. Es correcto —que el coche se mueva
ahora no dice cuándo dejó la plaza; un viaje largo llevaría 90 min fuera— y es el carril de respaldo,
no el principal: la salida normal la sigue dando el EXIT de la geocerca, que ahora lleva su instante
medido y publica igual que siempre.

Hay una alternativa medida que NO se ha tomado, y se deja escrita para no volver a descubrirla:
en ese mismo punto el evaluador tiene `lastSeenNearCarAtMs` (el sello del ancla), que sí acota la
salida — el coche se fue en algún momento entre ese instante y ahora. Usarlo como `departedAtMs`
daría la salida **más antigua posible**, o sea la estimación más conservadora de la edad de la
plaza, y seguiría siendo del lado seguro. Se descarta por ahora porque `null` es la respuesta
honesta ("no lo medimos") y porque ese carril no tiene corpus de campo con el que calibrar cuánta
plaza real se está perdiendo. **Revisar con el primer corpus que traiga el gate de Fernando.**

## Fuera de alcance (tickets propios)

- **`DET-A-BT-CONNECT-IS-A-DEPARTURE-001`** — el carril Bluetooth **no tiene puerta de salida**:
  `BluetoothConnectionReceiver:155-192` en `ACTION_ACL_CONNECTED` solo sella `BtConnectionStore` y
  apaga el servicio. El CONNECT del coche emparejado es la señal determinista de "la plaza se está
  liberando AHORA" y no cierra nada. Toda salida de un usuario BT se procesa por el carril del
  Coordinator — el de este ticket.
- **`SPOT-A-PROMOTION-INHERITS-THE-AGE-OF-ITS-DEPARTURE-001`** — `FinalizeDeducedDepartureUseCase`
  republica el mismo documento con TTL **completo de 2 h** desde el instante de la promoción, sin
  preguntar la edad de `deducedAtMs`.
