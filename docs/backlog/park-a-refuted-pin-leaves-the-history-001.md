# PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001

> **Estado:** implementado 2026-08-31 · rama
> `feature/PARK-A-REFUTED-PIN-LEAVES-THE-HISTORY-001-retract` (base `29a9b0a5`)
> **Origen:** la **Pieza 8** candidata del rediseño (§9.2), con el alcance recortado tras leer el
> código. **Absorbe y cierra** `PARK-RETRACTED-BACKFILL-MUST-LEAVE-NO-PIN-001`.
> **La mitad de la Pieza 8 que NO se construye** (mover un pin ya colocado) y por qué: §5.

---

## 1. El bug

Las siete piezas del rediseño son **preventivas**: deciden mejor ANTES de plantar. Ninguna puede
tocar una fila ya escrita. Y hay dos días de campo que produjeron exactamente eso — un pin que la
propia app desmintió en segundos, que se queda en el histórico del usuario como un aparcamiento
normal:

| campo | qué pasó | vida del pin |
|---|---|---|
| **2026-08-27**, Oppo | El backfill plantó `724befda` a las 12:29:18; a las 12:29:36 la app emitió un EXIT de la valla de ESE pin; a las 12:30:21 confirmó la salida a 16,3 km/h | **63 s** |
| **2026-08-30**, Oppo, Calle del Verdugo | La misma forma, en la carretera, en marcha, con plaza comunitaria publicada | **52 s** |

`ClearActiveParkingSessionWorker` ponía `isActive=false` y **la fila se quedaba**: sin dirección, con
`detectionPath = safety_net_backfill`, indistinguible de un aparcamiento real. Así lo reportó el
usuario: *«un FALSO POSITIVAZO en Dia · Calle Ronda del Puerto 15»*.

`DET-BACKFILL-MUST-NOT-PIN-A-MOVING-CAR-001` (`29a9b0a5`) impide que nazca el siguiente. No alcanza
al que ya está escrito.

### 1.1 Y una segunda puerta que nadie había mirado

`RevertParkingUseCase` — el botón *«No, cancelar»* de la tarjeta de confirmación — hacía **lo mismo**:
cerrar la sesión y nada más. Su propio comentario ya decía *«the pin was wrong»*, y el pin equivocado
se quedaba en el histórico. Su KDoc llevaba escrito el `TODO-REVERT-P1`:

> *«añadir `deleteSession` para borrarla del histórico … semánticamente: "esto no era un
> aparcamiento, no quiero verlo en mi historial"»*

Es la misma capacidad que falta, con la autoridad más fuerte que existe detrás: la palabra del
usuario.

## 2. Doctrina violada

*Mejor un falso negativo que un falso positivo.* Un aparcamiento del que la app **ya ha concluido que
nunca ocurrió** no es un dato incierto que convenga conservar: es una afirmación que ella misma ha
retirado. Conservarla es afirmar algo que sabemos falso.

---

## 3. La decisión: ¿borrar o marcar?

El ticket original la dejaba abierta con una instrucción: *«no decidir esto sin mirar antes cómo lo
lee la UI del histórico»*. Mirado:

- El Historial lee `userParkingRepository.observeAllSessions()` → `dao.observeAll()` de **Room**, sin
  ningún filtro de estado. `computeStats` y la gráfica semanal filtran ya por `!isActive`.
- **El repo ya había contestado la pregunta una vez.** `SpotStatus`, para la plaza comunitaria, la
  razona en su propio KDoc: *«why a state and not a delete: a deleted document just stops arriving,
  taking the explanation with it»*.

**Se marca.** La fila sobrevive para el diagnóstico —es justo el pin que un informe de campo intenta
explicar— y desaparece de las lecturas del histórico.

### 3.1 ⚠️ Un instante, no un enum

El preview de la decisión hablaba de `status: ParkingStatus (ACTIVE / ENDED / RETRACTED)`. Al
implementarlo se ve por qué **no**: `isActive` ya responde otra pregunta («¿es el aparcamiento actual
de este coche?»), la leen **cinco** queries de Room y el cierre de Firestore, y una sesión sólo se
retira DESPUÉS de cerrarse. Un `status` o duplicaba `isActive` —dos fuentes de verdad, justo lo que
esta doctrina persigue— o forzaba migrarlas todas en un ticket cuyo alcance es *una fila saliendo de
una lista*.

`retractedAtMs: Long?` responde **si** y **cuándo** con una sola fuente. `isRetracted` se deriva.

---

## 4. El arreglo

**1. La pregunta se declara en el tipo.** `DetectionPath.mayBeWithdrawnByTheApp` — propiedad
abstracta, así que **un camino nuevo no compila hasta que su autor la responde**. Es `true` para uno
solo, y la razón no es «es el que nos quemó»: `SafetyNetBackfill` es el único pin colocado **sin
sesión viva detrás** — una reconstrucción a partir de evidencia rancia, que es precisamente la clase
de afirmación que una medición posterior puede tumbar. Todos los demás tuvieron una sesión que
observó algo.

**2. El predicado puro** `pinIsRefutedByItsOwnDeparture(path, parkedAtMs, departedAtMs, maxLifeMs)`
en `domain/detection/` — junto a `SentryWakeCooldown`, `VehicleFenceOwnershipPolicy`… No es un
veredicto (no produce `detectionPath`, `outcome` ni nada que el usuario lea), así que **no es un caso
de uso** [DET-VERDICT-NOT-PREDICATE-001]. Falla CERRADO en etiqueta desconocida, y una vida
**negativa** (reloj hacia atrás por NTP, backup restaurado) responde `false`: un orden en el que no
confiamos no es evidencia de nada.

**3. La ventana**: `refutedPinMaxLifeMs = 3 min`. **El número no es el guardia**: el backfill sólo
dispara ~15 min después del hecho, así que un recado real de dos minutos ya terminó antes de que
pudiera plantar nada. Los dos casos medidos (52 s y 63 s) caben 3×.

**4. Dos puertas, dos autoridades distintas:**
- `ProcessConfirmedDepartureUseCase` — la app se desmiente a sí misma: consulta la política.
- `RevertParkingUseCase` — **no consulta nada**. Un revert es una INSTRUCCIÓN, no un veredicto, y
  nada que la app mida supera la palabra del usuario [DET-ASSERTION-OUTRANKS-INFERENCE-001].

---

## 5. Lo que este ticket NO hace, y por qué

⛔ **No construye la mitad «mover» de la Pieza 8** (el pin nace provisional y se re-ancla al terminar
el viaje). El rediseño la enuncia con un caso de uso concreto —el pin a 142 m de casa— y **ese caso
dejó de existir el 30-08**: se midió que su reposo bueno existía **10 min ANTES** de plantar, así que
lo cierra preventivamente `DET-NO-CLOCK-PLANTS-A-PIN-001`. Construirla hoy sería añadir un ciclo de
vida en dos fases a `UserParking` + Firestore + mapa + geocerca **sin un fallo vivo que lo exija**.
Decisión del usuario, tomada con esto delante.

⛔ **No borra nada.** Ver §3.

⛔ **No retira un pin medido, ni uno del usuario, por su cuenta.** El `when` de
`mayBeWithdrawnByTheApp` lo hace imposible por construcción, y un test recorre los 11 caminos.

---

## 6. Barrido de consumidores (todos los sitios auditados)

| # | fichero | qué había | qué hay |
|---|---|---|---|
| 1 | `domain/detection/DetectionPath.kt` | 2 preguntas declaradas por caso | +`mayBeWithdrawnByTheApp` (abstracta → los 12 casos la responden) |
| 2 | `domain/detection/RefutedPin.kt` | — | **nuevo**: el predicado puro |
| 3 | `domain/model/ParkingDetectionConfig.kt` | — | +`refutedPinMaxLifeMs` + su `require` |
| 4 | `domain/model/UserParking.kt` | — | +`retractedAtMs` + `isRetracted` |
| 5 | `data/.../room/UserParkingEntity.kt` · `remote/dto/ParkingHistoryDto.kt` | — | +columna / +campo |
| 6 | `data/mapper/ParkingSessionMapper.kt` | 4 direcciones | las 4 llevan el campo |
| 7 | `remote/RemoteUserProfileDataSourceImpl.kt` | deserializador manual | +`FIELD_RETRACTED_AT_MS` (lo exige `FirestoreDeserializerParityTest`) |
| 8 | `room/UserParkingDao.kt` | — | +`retractById` (COALESCE: la primera retirada manda) |
| 9 | idem, **4 lecturas de histórico** + `getPreviousByVehicle` | sin filtro | `AND retractedAtMs IS NULL` |
| 10 | idem, **7 lecturas de diagnóstico/detección** | sin filtro | **sin tocar, a propósito** — ver §7 |
| 11 | `UserParkingRepository` + `Impl` | — | +`retractParkingSession` (mismo `enqueue` que el cierre → viaja a Firestore por el reconcile de siempre) |
| 11b | `data/repository/UserParkingReconcile.kt` | `onTakeRemote` preserva 6 campos | +`retractedAtMs = r ?: l` — **ver §6.1** |
| 12 | `ProcessConfirmedDepartureUseCase` | cerraba y ya | consulta la política y retira; +`config` **sin default** [DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001] |
| 13 | `RevertParkingUseCase` | cerraba y ya; su KDoc pedía el borrado | retira sin política; `TODO-REVERT-P1` **resuelto** con la otra respuesta |
| 14 | `di/DetectionModule.kt` | — | pasa `config` |
| 15 | los 2 fakes (`commonMain` mock + `commonTest`) | — | implementan la retirada; el mock la **oculta del histórico** como Room |
| 16 | `ParkingBackfillWorker` + `ParkingSafetyNetWorker` | `private const val PATH_SAFETY_NET_BACKFILL = "safety_net_backfill"` **duplicado** | `DetectionPath.SafetyNetBackfill.label` — la palabra es del tipo, y ahora es la clave de la política: una deriva entre las dos grafías habría dejado de retirar en silencio |
| 17 | `VehicleHistoryCalculator`, `VehiclesViewModel`, `ParkingHistoryViewModel` | leen sesiones | **sin tocar** — el filtro vive en la query, no en cada consumidor |
| 18 | Dev Catalog / galería de estados | — | **sin variante nueva**: una fila retirada no renderiza nada, es una fila que no está. El fake sí reproduce el bucle |

### 6.1 ⚠️ El hueco que destapó el rebase: la retirada se podía DESHACER sola

Master avanzó dos veces durante el ticket, y una de ellas —`SYNC-A-PARKING-MUST-TRAVEL-WHOLE-001`—
cae justo aquí. Dos consecuencias opuestas:

- 🟢 **Regala la mitad difícil.** Ese commit sustituyó la lista de 15 campos escrita a mano del
  payload de WorkManager por `Json.encodeToString(dto)`, así que `retractedAtMs` **viaja porque es
  parte del DTO**, no porque alguien se acordara. Cero trabajo por mi parte, y el defecto que ese
  ticket describe (*«ninguno de ellos preguntó por qué había que recordar un campo»*) era exactamente
  el que me habría mordido.
- 🔴 **Y destapa uno real.** `reconcileParkingSessions` preserva explícitamente, en su rama
  `onTakeRemote`, los campos que un documento remoto viejo traería a `null` (`zoneRadiusMeters`,
  `endedAtMs`, `routeDistanceMeters`, la procedencia…). `retractedAtMs` no estaba. Un documento
  remoto **más nuevo** —escrito antes de que el campo viajara, o por otro dispositivo que aún no ha
  recibido la retirada— gana el Last-Write-Wins y **habría devuelto la fila fantasma al histórico**.

Es la misma forma y el mismo argumento que la línea de `zoneRadiusMeters` justo encima: tomar el
`null` remoto afirma MÁS de lo que sabemos (que el aparcamiento era real), que es la dirección del
fallo asimétrico que no tomamos nunca. **Una vez retirada, retirada** — y la retirada sí viaja en el
otro sentido (test propio).

---

## 7. La otra mitad de la regla, y por qué tiene su propio test

Un barrido bienintencionado que añadiera el filtro a TODAS las queries rompería el ticket: escondería
la fila del diagnóstico —llevándose la explicación, que es justo el fallo que «marcar en vez de
borrar» evita— y dejaría a `getPendingSync` sin ver la retirada, con lo que **nunca llegaría a
Firestore**. Por eso el guardarraíl tiene dos reglas, no una:

- `every history read excludes withdrawn parkings` (5 lecturas)
- `no diagnostic read hides a withdrawn parking` (7 lecturas)

---

## 8. Tests

**Nuevos**
- `commonTest/domain/detection/RefutedPinTest.kt` (8) — los **dos casos de campo replayados con sus
  cifras** (63 s y 52 s), las tres condiciones, el fallo cerrado, el reloj hacia atrás, el censo de
  `mayBeWithdrawnByTheApp` sobre los 11 caminos + su testigo de población (≥6) y la prohibición sobre
  todo lo que colocó el usuario.
- `androidUnitTest/architecture/HistoryReadsGuardrailTest.kt` (2) — las dos mitades de §7, con la
  población pedida a `GuardrailScope` (que rechaza la vacía).
- `ProcessConfirmedDepartureUseCaseTest` (+3) — retira el backfill refutado; **no** retira un camino
  medido; **no** retira un backfill que sobrevivió a la ventana.
- `RevertParkingUseCaseTest` (+2) — retira, y retira **sea cual sea** el camino y la antigüedad.
- `UserParkingReconcileTest` (+2) — §6.1: la retirada sobrevive a un snapshot remoto MÁS NUEVO que
  la precede, y una retirada remota alcanza a un local que no se ha enterado.

**Falsación (⛔ un test de prohibición sin verlo fallar siempre pasa)** — cinco inyecciones:
1. quitado `retractedAtMs IS NULL` de `observeAll` → `every history read…` **FAILED**;
2. añadido a `getById` → `no diagnostic read…` **FAILED**;
3. borrado el guardia de reloj negativo → `should_keepTheParking_when_theDepartureLandsBeforeThePark`
   **FAILED**;
4. desactivada la retirada en cada puerta → los 3 tests de esa puerta **FAILED** (2 en el revert, 1
   en la salida confirmada);
5. borrada la línea `retractedAtMs = r ?: l` del reconcile → `a withdrawal survives taking a newer
   remote snapshot that predates it` **FAILED**.

Restaurado todo, verde.

**Suite completa:** `:shared:testDebugUnitTest` → **2033 tests, 0 fallos** tras rebasar sobre
`9aab82eb` (17 tests nuevos de este ticket).
`:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` OK.

⚠️ **Room**: columna nueva sin migración, y es correcto — la BD está en **v1 con
`fallbackToDestructiveMigration` y sin release publicada** [DATA-ROOM-STARTS-AT-VERSION-ONE-001]. El
primer release público congela esto.

---

## 9. Doctrina que aplica

- *Mejor un FN que un FP*: la app deja de afirmar lo que ya sabe falso, y la ventana corta erra por
  el lado de conservar de más.
- *Sistemas, no parches*: la capacidad («retirar una fila») se añade en UN sitio y se barren sus DOS
  puertas, sus 4+7 lecturas y las 4 capas de persistencia.
- *Un caso de uso por VEREDICTO*: la política es un PREDICADO y vive con los demás predicados puros;
  no se creó ninguna clase.
- *Una prohibición sin testigo de población no es un chequeo*: el guardarraíl pide su población y el
  censo del tipo trae su suelo.
