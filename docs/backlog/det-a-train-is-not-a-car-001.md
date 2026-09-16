# DET-A-TRAIN-IS-NOT-A-CAR-001 · Un tren no es un coche: la detección no tiene techo de velocidad

**Estado:** ✅ Done · mergeado a master vía squash.

## Problema

Campo **2026-09-15**, Samsung SM-A556B (Android 16), app 1.1.0, estrategia COORDINATOR, diagnósticos
remotos activos. Una tester aparca junto a la estación de El Puerto de Santa María, entra andando a
coger el AVE/Alvia a Madrid, y la app detecta el viaje en tren como un viaje en coche.

| Hora (UTC) | Sesión | armEvidence | Resultado |
|---|---|---|---|
| 11:21 | — | `self_observed` | ✅ pin correcto junto a la estación (`706ab1ba`) |
| 11:45:56 | `1789472756175` | `GEOFENCE_EXIT` d=1067 m, dep=`self_observed` | arma al cruzar la valla **andando hacia el andén** |
| 11:46:41 | ↑ | — | 🔴 aparcamiento CERRADO y **`publishedSpot=true`**: plaza anunciada a la comunidad con el coche todavía dentro |
| 16:16:00 | ↑ | — | 🔴 **FP #1**: `confirmed_steps+egress` · 270,1 min · **vmax 253 km/h (creíble 253)** · 769/1616 fixes de conducción (762 creíbles) · 191 pasos → pin `1f1dfd95` en **Atocha**, fiabilidad 0,9, ruta de 365 km |
| 16:23→16:37 | 5 sesiones | `SIGNIFICANT_MOTION` (centinela `geof=1f1dfd95`) | ⚪ la valla fantasma despertando dentro de la estación (`aborted_false_enter` ×4, `aborted_no_movement`) |
| 16:39→16:53 | `1789490342745` | `SIGNIFICANT_MOTION` | 🔴 **FP #2**: `confirmed_steps+egress` · 14,3 min · 55 km/h · 108 pasos → segundo pin fantasma en el centro de Madrid; el de Atocha queda retractado |

Estado final: el coche "está" en el centro de Madrid cuando está en Cádiz, y a la comunidad se le
ofreció una plaza en El Puerto que nunca se liberó.

## Doctrina violada

*El evento NOMINA, solo el movimiento MEDIDO confirma* — cumplida al pie de la letra y aun así
insuficiente, porque **el movimiento medido no se comprobó contra lo que un coche puede hacer**.
Todo el vocabulario cinemático del Coordinator tiene SUELO y no tiene TECHO:

| Barra | Valor | Un AVE a 253 km/h |
|---|---|---|
| `minimumDepartureSpeedKmh` | 10 km/h | ×25 |
| `minimumTripSpeedMps` | 5 m/s (18 km/h) | ×14 |
| `motorProofSpeedMps` | 11,1 m/s (40 km/h) | ×6,3 |

Un tren de alta velocidad no es que pase los umbrales: los pulveriza, y se lee como la conducción
más sólida que la app ha medido nunca.

⛔ **Y la app ya sabía el número.** `sustainedDepartureMaxRateMps = 55f` lleva escrito desde
[DET-CREDIBLE-DRIVE-001]: *"no urban drive sustains more than motorway pace. 55 m/s ≈ 200 km/h"*. El
mismo umbral que ahora convierte el tren en un veto se usaba para lo contrario: **descartar la
medición como teletransporte de caché**, es decir, tirar justo la prueba que cazaba el tren. El
invariante existía; estaba archivado bajo "GPS malo" en vez de bajo "no es tu coche".

## Señales / datos disponibles

Todo en la sesión, sin permisos nuevos ni red:

- `DriveProof.peakMps` y la maquinaria de bandas `creditSpeedBand(...)`, ya instanciada **dos** veces
  (banda de conducción y banda de motor). Una tercera y una cuarta son un parámetro, no un sistema
  nuevo.
- `DriveProof.maxExcursionMeters` — el suelo más lejano que la posición alcanzó desde el origen de la
  sesión, contado solo sobre fixes creíbles, y ya LATCHED — más `elapsedSinceArmMs`. Eso es el ritmo
  de suelo en línea recta del viaje.
- ⛔ **`motorDisplacementRateMps` NO sirve como corroborador**: está topado por
  `sustainedDepartureMaxRateMps` (55 m/s) precisamente para descartar teletransportes, así que un
  tren nunca puede asomar por ahí. Es el hallazgo de arriba visto desde el otro lado.
- AR **no** discrimina: Android no tiene clase ferroviaria y etiqueta el AVE como `IN_VEHICLE`. Por
  eso este veto no puede ser revocable por AR, al revés que el de la bicicleta.

## Diseño

Espejo exacto de [DET-BIKE-NOT-A-CAR-001] (`HumanPoweredRide.kt`). Aquél pregunta *"¿te movió un
músculo?"*; éste pregunta ***"¿te movió algo que no cabe en una carretera?"***. Un **VETO**: puede
contradecir, jamás armar ni confirmar. Predicado puro en `domain/detection/`, no caso de uso — no
produce `detectionPath` ni nada que el usuario lea [DET-VERDICT-NOT-PREDICATE-001].

### Dos bandas, no un umbral

| Banda | Medida | Veredicto |
|---|---|---|
| **DUDOSA** | ≥ **160 km/h** sostenidos `sustainedDriveProofMs` (30 s) | degrada a **prompt** |
| **IMPOSIBLE** | ≥ **200 km/h** sostenidos ≥ **5 min** **Y** ritmo de suelo en línea recta ≥ **100 km/h** sobre una línea base ≥ **60 min** | **cierre terminal sin preguntar** |

Las dos condiciones de IMPOSIBLE son independientes a propósito: la primera la roza un coche rápido
en una Autobahn sin límite, la segunda no la aguanta nadie por carretera. Juntas solo las cumple un
viaje de largo recorrido a velocidad ferroviaria.

### Por qué esos números (estadística oficial, no intuición)

- **Límite legal más alto de nuestros 9 mercados: 140 km/h** (Polonia). ES 120 · IT/FR/RO 130 ·
  NL 100 día / 130 noche · DE sin límite general en ~70 % de la Autobahn (recomendada 130).
- **Alemania, medición real** (IW, 1.900 millones de mediciones en Renania del Norte-Westfalia,
  may–ago 2024, tramos sin límite): 83 % por debajo de 130 · ~10 % entre 130 y 140 · **~1 % por
  encima de 160**. Los 160 km/h son, por tanto, el percentil 99 de la red más permisiva de Europa:
  un coche PUEDE estar ahí, así que se pregunta, nunca se pinta.
- **LAV Madrid–Sevilla (Adif)**: diseñada para **300 km/h**, explotación comercial 250→300. Los
  253 km/h medidos caen exactamente en la banda comercial del corredor del incidente.
- **Corpus de campo propio, 18 trazas de replay**: máximo real jamás grabado **36,49 m/s =
  131,4 km/h** (`Trace_MotorwayRedmi001`). La banda IMPOSIBLE queda 1,52× por encima de la
  conducción real más rápida que la app ha visto.
- **100 km/h en LÍNEA RECTA durante una hora**: la distancia por carretera es siempre ≥ la distancia
  geodésica, así que el ritmo geodésico es una cota INFERIOR conservadora de la velocidad real
  exigida. Con el índice de rodeo más prudente (1,15) implica ≥115 km/h reales durante una hora
  seguida, sin una sola parada. La sesión del incidente: ~476 km geodésicos en 270 min = **106 km/h**
  — por carretera habría exigido 630 km en 4,5 h, o sea 140 km/h de media puerta a puerta.

### Por qué IMPOSIBLE rechaza y no pregunta

Doctrina: *ante la duda se PREGUNTA*. Aquí no hay duda, y preguntar hace daño. Lo dice el propio
evaluador en `EvaluateParkingDecisionUseCase` ([DET-ASSERTION-OUTRANKS-INFERENCE-001]): *"una
pregunta cuyas dos respuestas posibles dañan el estado no debe hacerse"*. Un prompt en Atocha solo
puede mover el pin a 600 km del coche.

La asimetría se mantiene, pero cambia de sitio: el coste de un IMPOSIBLE equivocado es que el coche
se queda sin pin automático y el siguiente viaje lo vuelve a fijar — recuperable. El coste del falso
positivo es un pin a 600 km y una plaza fantasma — no recuperable por el usuario.

### Y la salida se DESHACE — el par que le faltaba a la bici

Rehusar el pin fantasma deja el estado *menos mal*, no *bien*: el coche sigue olvidado y la plaza
sigue ofrecida a un desconocido. El undo ya existía a medias, y lo que faltaba era la otra mitad del
par:

| | salida **DEDUCIDA** (bici, 19-08) | salida **PRESENCIADA** (tren, 15-09) |
|---|---|---|
| Qué publica | provisional, TTL corto | la plaza de verdad |
| Qué se lleva | **nada** — sesión y valla se quedan | **el coche** — cierra la sesión y quita la valla |
| Quién lo deshace | `RetractDeducedDepartureUseCase` (ya existía) | **`RestoreRefutedDepartureUseCase`** (este ticket) |

Por eso la bici no necesitaba restaurar nada y esto sí: el primer fix creíble de conducción llegó a
los **45 s** del armado —un tren saliendo del andén acelera como nada que el móvil pueda ver— así
que `ProcessConfirmedDepartureUseCase` tomó la rama presenciada mucho antes de que ningún techo
pudiera saber qué la estaba moviendo (la banda imposible necesita 5 min; el corroborador, una hora).

Deshace en el orden que importa si el proceso muere a medias:

1. **La plaza primero** — es la única parte del estado sobre la que actúan terceros, y la única con
   fecha de caducidad. Mismo `retractSpot` que la bici.
2. **La sesión se reabre** — `isActive=1`, `endedAtMs` a NULL (para que la salida REAL pueda fecharse
   cuando llegue), `publishedSpot` a false.
3. **La valla se re-registra** — un pin restaurado sin valla es un coche que ya no puede anunciar su
   siguiente salida. El campo del 19-08 ya tasó esta omisión en la vía deducida.

Dos negativas, ambas con test: si el coche **ya tiene sesión activa** (lo que la haya puesto sabe más
que un viaje refutado, [DET-ASSERTION-OUTRANKS-INFERENCE-001]) y si el pin está **retirado** (una
retirada es la app o el usuario diciendo que ese pin era ficción; los dos undos no deben pelearse).

⛔ **Trampa de sincronización esquivada.** `restoreParkingSession` NO encola
`enqueueClearActiveParkingSession`, que es lo que hace toda escritura vecina. Ese worker no es un
«sincroniza esta sesión»: llama a `clearParkingSessionActiveFlag`, que **fuerza `isActive=false` en
remoto**. Encolarlo habría programado un trabajador cuyo único cometido es deshacer la reapertura, y
habría ganado en cuanto volviera la red. Viaja por `pendingSync` en el empuje de documento completo.

⚠️ Room **no sube de versión**: `reopenById` es una `@Query` nueva, no un cambio de esquema.

### iOS

Todo el cambio vive en `commonMain` (predicado puro + config + evaluadores). **iOS lo hereda por
construcción**, sin pieza nueva en `iosMain`. [feedback_implement_the_ios_side_of_every_ticket]

## Criterio de éxito

1. Replay de la traza del incidente (`Trace_AveAtocha1509`, sintetizada del perfil de velocidad de la
   sesión real) → **ningún pin**, outcome de cierre por carretera imposible.
2. `Trace_MotorwayRedmi001` (131 km/h reales) sigue confirmando exactamente igual — testigo de no
   regresión de la banda DUDOSA.
3. Una sesión que toca 170 km/h sostenidos degrada a prompt, no a pin ni a cierre.
4. La suite entera verde (`./gradlew testAll`).

## Consumidores auditados

Barrido por la pregunta correcta — no *"quién lee la velocidad"* sino **"quién puede plantar un
pin"**, que es lo que el FP produjo:

| Sitio | Clasificación |
|---|---|
| `EvaluateParkingDecisionUseCase` | **cerrado** — entrada nueva + brazo terminal, el primero del `when` |
| `EvaluateUnattendedParkingSaveUseCase` | **cerrado** — vetar solo el confirm es lo que dejó al FP de la bici aterrizar aquí en agosto. Con anchor limpio esta entrada era un `SaveExact` |
| `StageInputs` (ambos constructores) | **cerrado** — un solo envoltorio para los dos, igual que `humanPoweredRide` |
| `CandidateStage` / `ConfidenceScoringStage` | **cerrado** — despachan el terminal, y el scorer además SUPRIME el prompt Low/Medium en banda IMPOSIBLE |
| `ResponseTimeoutStage` | **cerrado** — cuarto desenlace `Discard`: el único que no produce efecto (el cierre ES la acción) |
| `RunHonestCloseUseCase` | **cerrado por construcción** — la escalera puede plantar un pin aproximado en el instante del aborto, que es exactamente nuestra terminal. No dispara: `AbortedUnattended.triggersHonestClose = false`. Verificado, no supuesto |
| Backfill del safety-net | **cerrado por construcción** — `AbortedUnattended.resolvesTheArrival = true`, así que no coloca el pin que el coordinator acaba de rechazar [DET-BACKFILL-TAINT-001] |
| `ParkingSafetyNetWorker` | **cubierto por convergencia** — trabaja desde el pin ACTIVO del vehículo; tras el cierre no hay ninguno que reconciliar |
| Carril BLUETOOTH | **exento con razón** — determinista y atado a la MAC, y fija donde está el COCHE, no donde acaba el pasajero: el disconnect ocurre al dejarlo. Los carriles no se mezclan |
| `sustainedDepartureMaxRateMps` | **exento con razón** — sigue descartando teletransportes; su trabajo es la calidad del fix, no la clasificación del vehículo. Ahora está escrito que son dos preguntas sobre el mismo número |
| `UserParkingRepository` / DAO / sync | **cerrado** — primitiva `restoreParkingSession` + `reopenById`; ⛔ deliberadamente SIN el worker de «clear», que fuerza `isActive=false` en remoto |
| Copy / 9 locales | **nada que hacer, verificado** — `nudgeSource` y `reason.key` viajan como provenance a telemetría y a `PendingParkNudge`; ningún `when` de UI los lee, el texto del nudge es fijo |
| Dev Catalog / galería | **nada que hacer** — sin pantalla, estado MVI ni condición de routing nuevos |
| `detectionPath` / `armEvidence` | **nada que hacer** — no hay camino de confirmación nuevo: este ticket solo REHÚSA. Lo nuevo entra en el vocabulario de outcomes |

## Estado final

✅ Implementado y verde. `./gradlew testAll` completo en verde; tests nuevos:

| Fichero | Qué prueba | nº |
|---|---|---|
| `RoadPlausibleRideTest` | el predicado: cada banda, la **Y** por ambos lados, el orden y los bordes | 8 |
| `DriveProofRoadBandsTest` | que la MEDIDA existe sobre un stream: bandas, credibilidad, línea base, y que un tramo rápido no arrastra un viaje lento | 7 |
| `EvaluateParkingDecisionUseCaseTest` | DUDOSA→prompt con su propio nombre · IMPOSIBLE→cierre · precedencia sobre los demás prompts · **no-regresión a 131,4 km/h** | +4 |
| `EvaluateUnattendedParkingSaveUseCaseTest` | `Discard` y `Ask` en el timeout | +2 |
| `RestoreRefutedDepartureUseCaseTest` | el undo: reabre · retira la plaza · re-registra la valla · y sus **tres negativas** | 7 |
| `DetectionTraceReplayTest` | replay end-to-end contra el detector real: el tren no fija ni pregunta, **muere por este guard** (outcome asertado) **y devuelve el coche a su valla de origen** | +1 |
| ↑ mismo fichero | **el control**: la misma geometría a velocidad de carretera SÍ confirma | +1 |

El control no es decorado: sin él, «no hay pin» es un verde que también da un replay roto.

## Fuera de alcance (follow-up escrito, no solo mencionado)

- **`DET-A-COMMUTER-TRAIN-IS-NOT-A-CAR-001`** — Cercanías, metro y autobús (40–160 km/h) son
  indistinguibles de un coche por cinemática. En esta traza no importan porque los FP #2 y el resto
  cuelgan del FP #1 (todo armado va atado a una valla de vehículo: con el pin correcto en El Puerto,
  **ninguna sesión puede armar en Madrid**), pero el hueco existe y necesita un discriminador que no
  sea la velocidad.
