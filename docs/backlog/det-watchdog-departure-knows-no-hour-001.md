# DET-WATCHDOG-DEPARTURE-KNOWS-NO-HOUR-001 · El usuario atestigua el HECHO, no la HORA

**Estado:** ✅ Done, en master · rebasada dos veces durante la sesión (`93e476e1`, luego `db3a1f62`)
· ⏳ pendiente de verse en device: el prompt sólo lo levanta la red de seguridad ante una salida no
observada

## Problema

La notificación del watchdog **«¿Sigues aparcado aquí?»** trae un único botón, *«Ya he salido»*, y
al pulsarlo **publica la plaza a la comunidad**, sin ninguna comprobación de antigüedad:

```kotlin
// CoordinatorDetectionService.kt:586
runCatching { processConfirmedDeparture(geofenceId, proof = DepartureProof.Witnessed) }
//                                      ↑ publishSpot = true, por defecto
```

Esa pregunta existe **precisamente porque nadie vio el EXIT**: la red de seguridad detecta al
usuario lejos del coche sin pruebas de salida. Por construcción, ese camino **no sabe cuándo se fue
el coche**. Puede haber sido hace 3 minutos o hace 3 horas.

Y la plaza sale con `reportedAt = ahora`, así que con la rampa de frescura
([SPOT-FRESHNESS-IS-AGE-NOT-A-COUNTDOWN-001]) se pinta 🟢 «hace menos de 10 min». Un hueco que puede
llevar horas ocupado, anunciado como lo más fresco del mapa, con `spotType` de sesión
(auto-detectada) y `confidence = session.detectionReliability`.

Reportado por el user (30-08-2026): *«lanzar un spot después de 3 h puede ser malo»*.

## Doctrina violada

**1 · Fallo asimétrico: mejor falso negativo que falso positivo.** Publicar una plaza que
probablemente ya no existe es exactamente la plaza fantasma que la doctrina prohíbe. El coste de
acertar son minutos para un desconocido; el de fallar, un viaje en balde.

**2 · La regla YA EXISTE, y este es el único camino que se la salta.** `RunDepartureCheckUseCase`
la aplica desde [DET-RECONCILE-001]:

```kotlin
// RunDepartureCheckUseCase.kt:159-163
val exitAgeMs = nowMs() - exitTimestampMs
val publishSpot = exitAgeMs <= config.spotPublishMaxAgeMs   // 10 min
```

con el comentario *«a departure recovered long after the fact … advertising it would sell ghosts»*,
escrito tras un incidente de campo (Redmi 2026-07-06) de una salida procesada 5 h tarde. El watchdog
es el otro camino de cierre y nunca recibió esa regla.

**3 · Sistemas, no parches.** El comentario de `handleWatchdogDeparture` dice hoy:

> *«The user's own statement is the strongest evidence there is — stronger than any fix — so this
> commits the departure in full.»* [DET-HANDOFF-NOT-MANUAL-001 §B]

Es verdad a medias, y la media que falta es la de este bug: **el usuario atestigua el HECHO de
haberse ido, no la HORA en que lo hizo**. La primera mitad justifica cerrar sesión y valla (sigue en
pie). La segunda no existe, y sin ella no se puede anunciar una plaza.

## Señales / datos disponibles

- `exitTimestampMs` — sólo lo tiene `RunDepartureCheckUseCase` (viene del EXIT de la geocerca).
- El camino del watchdog **no tiene ninguno**: los dos sitios que lanzan la pregunta
  (`ParkingSafetyNetWorker.kt:501` — arranque de FGS denegado — y `:530` —
  `SafetyNetAction.PromptStillParked`, *«moving far without anchor»*) se disparan justo cuando falta
  el ancla y la evidencia de salida. No hay instante que rescatar; **la ausencia es el dato**.
- `config.spotPublishMaxAgeMs` = 10 min (`ParkingDetectionConfig.kt:283`).

## Diseño

**El invariante: una plaza sólo se anuncia si se sabe CUÁNDO quedó libre y ese instante es
reciente.** Hoy vive como una expresión inline dentro de un solo llamante. Se saca a donde puedan
verlo los dos.

### 1 · Predicado puro compartido — `domain/detection/FreedSpotIsStillThere.kt`

Es un **predicado, no un veredicto** ([DET-VERDICT-NOT-PREDICATE-001]): no produce `detectionPath`
ni `outcome`, sólo alimenta a los dos cierres. Dos consumidores → función pura de nivel superior en
`domain/detection/`, junto a `SentryWakeCooldown`, `HumanPoweredRide` y `VehicleFenceOwnershipPolicy`.
**No** un `Evaluate…UseCase` nuevo (arreglar un bug no justifica crear uno).

```kotlin
fun freedSpotIsStillThere(exitAtMs: Long?, nowMs: Long, config: ParkingDetectionConfig): Boolean
```

`exitAtMs = null` (**no se sabe cuándo**) → `false`. Eso convierte el caso del watchdog en una
consecuencia de la doctrina en vez de en una excepción escrita a mano: *una hora desconocida no es
una hora reciente*.

### 2 · `publishSpot` deja de tener valor por defecto

`ProcessConfirmedDepartureUseCase.invoke(publishSpot: Boolean = true)` es la trampa que produjo el
bug: un parámetro que publica **por omisión**, en un caso de uso al que se llega desde cierres muy
distintos. Se quita el default → el compilador obliga a todo camino de cierre, presente y futuro, a
responder la pregunta. Es la parte «sistema» del arreglo: no se puede volver a olvidar.

### 3 · La notificación gana la segunda acción

Hoy el único botón cierra sesión, quita la valla y publica. Después:

- **«Ya he salido»** → sigue cerrando sesión + valla (converger el estado local es el trabajo entero
  de la red de seguridad; si desaparece, el siguiente aparcamiento arranca sucio) — pero **sin
  publicar**.
- **«Marcar aparcamiento»** (nueva) → deep-link al modal de añadir aparcamiento, vía el
  `buildAddParkingIntent` que ya usan los dos nudges. Si te fuiste hace 3 h, el coche está aparcado
  en otro sitio y *eso* es lo que la app necesita saber; la plaza vieja no vale nada, el pin nuevo
  todo. Confirmar el pin nuevo ya reemplaza la sesión activa y suelta la valla huérfana
  (`replaceActiveSession`), así que las dos acciones convergen en un estado limpio.
- `fromDetection = true` — la detección nominó la pregunta, igual que en `showMarkParkingNudge`
  ([DET-AR-FIRST-001]), así que el pin conserva su provenance. [DET-NUDGE-PIN-PROVENANCE-001]

### 4 · El copy deja de prometer lo que ya no hace

`notif_still_parked_text` dice hoy *«Si ya te has ido, libera la plaza para la comunidad»*: promete
justo el publish que se retira. Se reescribe con causa + consecuencia + remedio y sin mecánica
interna, en los 9 locales.

⚠️ Trampa: estos strings viven en `app/src/main/res/`, que **son recursos Android** (`R.string`), no
Compose Resources. Aquí el apóstrofo **sí** va escapado (`\'`) — [COPY-APOSTROPHE-IS-NOT-ESCAPED-001]
aplica sólo a `composeResources`, y confundirlos rompe el build en un sentido y pinta la barra en el
otro.

### 5 · Lo que se decidió NO hacer: descartar el aviso al abrir el modal

La acción «Marcar aparcamiento» abre la app, y **un botón de acción de notificación no dispara
`setAutoCancel`** (sólo lo hace el tap en el cuerpo), así que el aviso se queda en la bandeja. La
tentación era descartarlo al consumir `EXTRA_START_ADD_PARKING` en `MainActivity`.

No se hace: **abrir el modal no es responder**. Si el usuario se vuelve atrás sin poner el pin, el
`PROMPT_THROTTLE_MS` de la red de seguridad es de **6 h** (`ParkingSafetyNetWorker.kt:862`) — habría
silenciado una pregunta viva durante toda la tarde para ahorrar un aviso de más. Se deja al barrido
que ya existe: en cuanto la sesión desaparece (confirmar el pin nuevo la reemplaza), la siguiente
pasada del worker —≤ 15 min— entra por `if (!anyPromptActive) dismissPrompt()`. Es la misma conducta
que ya tienen los dos nudges hermanos, y falla del lado de preguntar. [DET-ASK-STATE-001]

## Criterio de éxito

- Pulsar «Ya he salido» en el prompt del watchdog **no publica ninguna plaza**; la sesión y la valla
  se cierran igual.
- El camino con timestamp (`RunDepartureCheckUseCase`) sigue publicando exactamente igual que antes
  dentro de los 10 min, y sigue sin publicar fuera.
- Un tercer camino de cierre no compila sin decidir si publica.
- Tests unitarios del predicado (incluido el caso `null`).

### Verificado

- `:shared:testDebugUnitTest` → **1.802 tests, 0 fallos** (incluye `LocaleParityGuardrailTest`, que
  desde `93e476e1` vigila **las dos** superficies de strings — también `app/src/main/res`).
- `FreedSpotIsStillThereTest` (5 tests) **validado por falsación**: con `exitAtMs ?: return true`,
  `should_not_publish_when_the_hour_of_the_departure_is_unknown` falla. Un test de prohibición que no
  se ha visto fallar es un test que siempre pasa.
- Retirar el default de `publishSpot` hizo fallar la compilación de 10 llamadas en tests, ninguna en
  producción: la red que se buscaba, funcionando ya en la primera pasada.
- `:app:compileProdDebugKotlin` + `:app:assembleMockDebug` verdes. Sin pantalla, estado MVI ni
  condición de routing nuevos → **el Dev Catalog no cambia** (las notificaciones no viven en la
  galería de estados).
- ⏳ **Sin ver en device.** El prompt sólo lo levanta la red de seguridad ante una salida no
  observada; hace falta provocarlo o esperar a campo.

## Consumidores auditados

`grep -rn "processConfirmedDeparture\|ProcessConfirmedDepartureUseCase\|publishSpot" shared/src app/src --include=*.kt`

| Sitio | Veredicto |
|---|---|
| `RunDepartureCheckUseCase:160` | **cerrado** — ya calculaba la edad; ahora la delega en el predicado, mismo resultado |
| `CoordinatorDetectionService:586` (`handleWatchdogDeparture`) | **cerrado** — era el agujero; pasa `exitAtMs = null` |
| `ProcessConfirmedDepartureUseCase:63` (`= true`) | **cerrado** — se retira el default |
| `ProcessConfirmedDepartureUseCase:170` (`published = publishSpot && …`) | **exento** — `UserParking.location` es `GpsPoint` no-nulo, así que la expresión es equivalente a `publishesNow` en el camino Witnessed; no hay divergencia que arreglar |
| `ReleaseActiveParkingSessionUseCase` / `ParkingReleaseReason` | **exento** — cierre in-app, con el usuario junto al coche y saliendo AHORA; ahí publicar es honesto, y ya ofrece las dos opciones (`DEPARTURE_PUBLISHED` / `DEPARTURE_UNPUBLISHED`) |
| `FinalizeDeducedDepartureUseCase` (promoción de deducida) | **cubierto por convergencia** — reescribe un documento que ya salió; no abre plaza nueva |
| Copias `publishSpot` en tests | recompilados con el argumento explícito |

### Follow-up fuera de alcance

`app/src/main/res/` nunca se barrió con [COPY-SPOT-IS-NOT-A-PARKING-001]: `notif_mark_parking_action`
y `notif_first_park_nudge_action` dicen *«Marcar mi plaza»* — la frase que la doctrina prohíbe
literalmente — y `notif_confirmation_failed_text` llama «parking spot» al aparcamiento propio. Es
otro invariante (vocabulario), así que va en su propio ticket:
`docs/backlog/copy-notification-layer-still-says-plaza-001.md`.
