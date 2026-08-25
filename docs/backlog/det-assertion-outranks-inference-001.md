# DET-ASSERTION-OUTRANKS-INFERENCE-001 · un "Sí" del prompt nunca puede mudar el pin que el usuario ya afirmó

**Estado:** ✅ Done · mergeado a master 2026-08-25 (squash) · implementado en
`bugfix/DET-ASSERTION-OUTRANKS-INFERENCE-001-user-pin-veto`, abierto sobre `a2f2bff9` y rebasado
sobre el refactor de física antes de cerrar. ⏳ **Sin validar en campo** — necesita un viaje real
donde el prompt vuelva a aparecer con el coche ya aparcado.

## Problema

Field 2026-08-24, Oppo (`fiypNbElGlfFexLMpU9sNaMjRMD3`), El Puerto de Santa María, Calle Fragua.
Reporte del user: *«he confirmado parking con los prompt estando en coche, al poco tiempo he salido
y ha vuelto a saltar el prompt y le he dado a confirmar para ver qué pasaba y ha plantado un nuevo
pin falso un poco al lado»*.

Reconstrucción desde `diagnostics/fiyp…/sessions` (todas las horas en local, UTC+2):

| Hora | Evento |
|---|---|
| 20:38:53 | `ARM:GEOFENCE_EXIT (geof=5b0ef993 d=158m acc=11m dep=self_observed)` |
| 20:38:53–20:48:33 | viaje real: `9.9min · vmax 99km/h · drive 42/100fix`, `DECISION MOTOR_WITNESSED (motorBand=45000ms ≥11.1mps)` |
| 20:48:33 | `DECISION outcome=PROMPT_SHOWN pathLabel=low_medium(timeout=94993ms) confidence=0.55` |
| 20:48:43 | user contesta **Sí** → `DECISION CONFIRMED pathLabel=user confidence=1.0` |
| 20:48:43 | pin **`a9709e31`** `36.613605,-6.2089333` · acc **1,25 m** · `detectionPath=user` · `armEvidence=self_observed` · rel **1.0** — ✅ *el preciso* |
| 20:48:48 | `SESSION_ENDED outcome=confirmed_user` |
| 20:50:38 | `ARM:SIGNIFICANT_MOTION (sentry-wake geof=a9709e31)` |
| 20:50:39–20:51:30 | sesión de **51 s**: 25 fixes, **1 solo** por encima del umbral (5,33 m/s ≈ 19 km/h, el primero), resto 0,02–0,97 m/s; **57 pasos** alejándose |
| 20:51:22 | `DECISION outcome=CONFIRM_DEGRADED_PROMPT pathLabel=steps+egress reason=weak_evidence` |
| 20:51:36 | user contesta **Sí** → pin **`195e72f1`** `36.6136417,-6.2087783` · acc 2,08 m · `detectionPath=user` · rel 1.0 |
| 20:51:37 | `a9709e31.isActive = false` — **el pin bueno queda desactivado** |

Los dos pines distan **14 m**. El segundo es menos preciso, se planta en el ancla donde arrancó la
caminata (no donde está el coche) y **reemplaza** al que el usuario había afirmado 2 min 53 s antes.

## Doctrina violada

*Fallo asimétrico: ante la duda se PREGUNTA.* — pero **aquí no había duda**: el usuario acababa de
responder esa misma pregunta, en ese mismo sitio, hacía menos de tres minutos. Preguntar otra vez no
resuelve incertidumbre: la fabrica. Y el "Sí" es ambiguo por construcción — el usuario afirma *el
hecho* («sí, estoy aparcado»), y la app lo traduce a *«planta el pin AQUÍ»*, con una posición que
eligió la máquina, no él.

El invariante correcto ya está escrito en el proyecto, en `EvaluateHonestCloseUseCase`:

> **Una inferencia nunca depone una afirmación.** Un pin con `reliabilityUserConfirmed` lo colocó o
> lo confirmó EL USUARIO — la declaración más fuerte que este sistema puede sostener. Sólo
> conducción MEDIDA puede liberarlo. *(`REASON_USER_ASSERTED_PIN`, [DET-WALK-FLOOR-001], field
> 2026-07-26 Glorieta)*

Ese mismo día y en ese mismo móvil el guard funcionó **12 veces** — el `parkdiag` del Xiaomi que iba
al lado repite `honest close: aborted_false_enter stayed silent (user_asserted_pin; pinDist=…)`. El
invariante existe, es correcto y está probado. Simplemente **no se barrieron sus consumidores**.

## Señales / datos disponibles

Todo lo necesario está ya en memoria en el momento de la decisión:

- `userParkingRepository.getActiveSessionByVehicle(vehicleId)` → el pin activo, con
  `detectionReliability` y `location` (posición + timestamp).
- `EvaluateParkingDecisionUseCase` ya recibe `lastSpeedMps`, `sustainedDrivingMs`, `evidenceLabel`.
- `config.reparkPlausibilityWindowMs` (10 min) y `reparkPlausibilityRadiusMeters` (300 m) ya
  definen "fresco y cerca"; `reliabilityUserConfirmed` (1.0) ya define "afirmado".

## Diseño

**El invariante en UN sitio, y los cuatro consumidores decidiendo con él.** Hoy vive dentro de un
`if` de `EvaluateHonestCloseUseCase`; pasa a ser una función pura de nivel superior en
`domain/detection/`, el patrón ya establecido por `SentryWakeCooldown.kt`,
`SentryLifecycleDecision.kt`, `VehicleFenceOwnershipPolicy.kt` y `HumanPoweredRide.kt` — un
predicado compartido por 2+ veredictos, directamente testeable, sin ceremonia de clase inyectada
[DET-VERDICT-NOT-PREDICATE-001].

1. **`domain/detection/AssertedPinAuthority.kt`** (nuevo, puro)

   ```kotlin
   fun assertionBlocksRelocation(
       pinReliability: Float?, pinLocation: GpsPoint,
       candidate: GpsPoint, nowMs: Long,
       sessionSawDriving: Boolean,
       userConfirmedReliability: Float, freshWindowMs: Long, radiusMeters: Float,
   ): Boolean
   ```

   `true` cuando el pin activo lo afirmó el usuario, sigue fresco, el candidato cae dentro del radio
   de repark **y** la sesión que quiere mudarlo no ha medido conducción. Conducción medida siempre
   lo levanta — es la única cosa que supera a una afirmación.

2. **`EvaluateHonestCloseUseCase`** — su `if` actual pasa a llamar al predicado. Cero cambio de
   comportamiento; es la prueba de que el predicado es el mismo.

3. **`EvaluateParkingDecisionUseCase`** — entrada nueva `assertedPinBlocksRelocation: Boolean`
   (la calcula el llamante con el predicado). Cuando es `true` → **`ParkingDecision.Rejected`**,
   antes de cualquier rama de confirm o de prompt. `Rejected` y no `Prompt`: preguntar aquí sólo
   puede empeorar el estado, y descartar el candidato deja el pin bueno intacto y la sesión viva —
   si más tarde mide conducción de verdad, el predicado se apaga solo.

4. **`ConfirmParkingUseCase`** — guard hermano, más estrecho, justo encima del de repark. Un "Sí" a
   un prompt lleva la palabra del usuario sobre el **hecho**, pero una posición elegida por la
   máquina: no debe saltarse el guard que existe justo para eso. Mismas exenciones que el de repark
   (sin provenance de sesión → BT/manual/externos; arms verificados), y un pin puesto a mano no
   llega siquiera: viaja como `SpotType.MANUAL_REPORT`, que el guard ya excluye.
   ⚠️ **Hallazgo durante la implementación**: quitar el bypass por fiabilidad **no bastaba**. La
   otra cláusula del guard, `tripMaxSpeedMps < minimumTripSpeedMps`, también se abría — porque
   `tripMaxSpeedMps` es un **pico** y ese único fix de 5,33 m/s supera los 5 m/s del umbral. Por eso
   el guard nuevo lee `sessionSawDriving` (tiempo sostenido en banda, [DET-MOTOR-PROOF-001]) y no el
   pico. Sin esto el fix habría quedado verde en tests y roto en campo.

**Lo que NO se hace:** un contador de "hace cuánto pregunté" dentro del coordinator. Es un parche
temporal sobre un problema de autoridad, y no cubre las otras tres vías.

## Criterio de éxito

- ✅ `AssertedPinAuthorityTest` (8 casos) — el predicado con las coordenadas reales de campo: bloquea
  el caso 2026-08-24; no bloquea con conducción sostenida, con un pin automático (0,9), con
  fiabilidad nula (fila legacy), fuera de ventana, fuera de radio; bloquea sin límites (lectura de
  la honest close) y con reloj adelantado.
- ✅ `EvaluateParkingDecisionUseCaseTest`: sesión sentry-wake con prueba `steps+egress` completa y
  `sustainedDrivingMs = 0` → **`Rejected`**. Control con la MISMA evidencia sin pin afirmado →
  sigue saliendo `Prompt(WEAK_EVIDENCE)`: se quita UNA pregunta, no el mecanismo.
- ✅ `ConfirmParkingUseCaseTest`: el "Sí" con `tripMaxSpeedMps = 5,33` (por encima de
  `minimumTripSpeedMps`, que es como se escapó) y `sessionSawDriving = false` → `ImplausibleRepark`
  y **cero** llamadas a `saveNewParkingSession`. Con conducción sostenida → se guarda. Pin colocado
  a mano (`MANUAL_REPORT`) → se guarda. Llamante sin provenance de sesión (BT) → se guarda.
- ✅ `EvaluateHonestCloseUseCase` mantiene su veredicto `user_asserted_pin` sin un solo test tocado
  (1.500 → 1.514 tests, los 1.500 previos intactos).
- ✅ **Verificado neutralizando cada guard por separado**: sin el `Rejected` cae el test del
  evaluador; sin el guard del confirm cae el del "Sí". Ninguno es un test verde decorativo.
- ⏳ Campo: aparcar, contestar "Sí" al prompt, bajarse y alejarse andando → **no** aparece un
  segundo pin.

## Estado de la implementación

| Fichero | Cambio |
|---|---|
| `domain/detection/AssertedPinAuthority.kt` | **nuevo** — `assertionBlocksRelocation(...)`, ventana y radio nullables |
| `domain/usecase/parking/EvaluateHonestCloseUseCase.kt` | su `if` pasa a llamar al predicado (sin límites → comportamiento idéntico) |
| `domain/usecase/parking/EvaluateParkingDecisionUseCase.kt` | input `assertedPinBlocksRelocation` → `Rejected` antes de confirmar o preguntar |
| `domain/coordinator/CoordinatorParkingDetector.kt` | param `activeParkedPin` + snapshot `currentAssertedPin`; se calcula en el único sitio que arma el input; `sessionSawDriving` al confirm |
| `domain/usecase/parking/ConfirmParkingUseCase.kt` | param `sessionSawDriving` (sostenida, no pico) + guard de afirmación sobre el de repark |
| `detection/service/CoordinatorDetectionService.kt` | resuelve el pin activo del vehículo nominado y se lo pasa al coordinator |
| `commonTest/…` | `AssertedPinAuthorityTest` nuevo + 2 en el evaluador + 4 en el confirm |
| `docs/detection/PARKING-DETECTION.md` | entrada de sección 2 |

**Suite: 1.514 tests, 0 fallos** (baseline de la rama: 1.500).

## Consumidores auditados

`grep -rn "reliabilityUserConfirmed" composeApp/src` + call sites de `ConfirmParkingUseCase`:

| Sitio | Hoy | Clasificación |
|---|---|---|
| `EvaluateHonestCloseUseCase:258` | veta (`REASON_USER_ASSERTED_PIN`) | ✅ ya cerrado — pasa a delegar en el predicado |
| `EvaluateParkingDecisionUseCase` (fase candidate) | no consulta el pin activo | ⛔ **donde mordió** — se cierra |
| `ConfirmParkingUseCase:179-200` (repark guard) | existe, pero se **bypassea** justo con rel 1.0 | ⛔ se cierra (bypass por posición, no por fiabilidad) |
| `EvaluateSafetyNetCheckUseCase` | no consulta el pin activo | ➡️ **DET-DEPARTURE-IS-NOT-ARRIVAL-001** (ticket hermano) |
| `SaveManualParkingUseCase` | posición elegida por el usuario | ✅ exento con razón |
| `UpdateParkingLocationUseCase` | mueve el pin existente, no crea otro | ✅ exento con razón |
| `RunHonestCloseUseCase` | delega en el evaluador | ✅ cubierto por convergencia |
| `BluetoothDetectionStrategy` → confirm | `tripMaxSpeedMps = null` → ya se salta el guard | ✅ exento con razón (carril determinista) |

## Contribuyente conocido, fuera de alcance

El fix único de 5,33 m/s que abre la sesión entera es **DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001**
(`docs/backlog/det-lone-sample-is-not-a-drive-001.md`), ya abierto. Este ticket no lo toca: cierra
la autoridad, no el umbral. Con los dos, la sesión ni siquiera se abriría.

## Notas de coordinación

El punto 3 toca el call site dentro de `CoordinatorParkingDetector` (2.600 líneas), que es
exactamente el fichero que el refactor F6 está desmontando en
`refactor/DET-PHYSICS-FENCE-CONTAINMENT-001-p1-7`. El predicado y los puntos 1-2-4 son
independientes; el 3 hay que decidir si entra antes o después de esa fase.

## Rebase

2026-08-25 · rama puesta al día sobre master `46b83012` (a2f2bff9 → 3377e78d · cfe2e025 ·
46b83012, las tres del refactor de física). Sin conflictos: el auto-merge de
`CoordinatorParkingDetector.kt` absorbió el movimiento de `SpeedBandClock`/`GapDoubt` a
`domain/detection/physics/`. Verde: 1.536 tests, 0 fallos, `compileMockDebug` + `compileProdDebug`.
