# DET-A-VERIFIED-BOARDING-MUST-REACH-THE-VETO-001 · el embarque que verifica la salida no llega al juez que lo necesita

**Estado:** ✅ Done · en master (squash; el hash vive en `MEMORY.md` — el doc entra en el mismo commit
y no puede citarlo) · rama y worktree borrados

**Origen:** hallado el 25-09 construyendo el replay de
`DET-THE-CADENCE-CORPUS-MUST-BE-EXECUTABLE-001`, al preguntar por qué el arm decía `verified_enter`
si el veto de pedaleo se comportaba como si no hubiera habido embarque.

## Problema

La sesión `1790330246055` (Oppo, 25-09) armó con **`dep=verified_enter`**: o sea que
`VerifyDepartureEvidenceUseCase` leyó un AR `IN_VEHICLE ENTER` de
`departureEventBus.lastVehicleEnteredAt`, lo **corroboró contra desplazamiento** y lo consideró
suficiente para verificar la salida. Minutos después, el veto de pedaleo condenó esa misma sesión por
tracción humana y el aparcamiento se perdió.

Las dos cosas son ciertas a la vez porque **leen campos distintos**:

```
VerifyDepartureEvidenceUseCase  ──lee──>  departureEventBus.lastVehicleEnteredAt   → verified_enter
isHumanPoweredRide              ──lee──>  EgressEvidence.vehicleRideAtMs           → null
```

`vehicleRideAtMs` sólo se rellena por `CoordinatorParkingDetector.onVehicleRide(...)`, y ese método
tenía **un único call site**: `ActivityTransitionReceiver`, es decir entregas de AR **en vivo, dentro
de la sesión**. El ENTER que verificó la salida ocurrió ANTES del arm, así que no vuelve a
entregarse: la sesión tuvo 587 eventos y **cero `ACTIVITY_TRANSITION`**.

Resultado: un embarque que la app consideró bueno para autorizar la conducción era **invisible** para
el único juez que decide si esa conducción fue a motor.

⚠️ **En iOS era peor.** Allí un ENTER (`automotive` false→true) va a
`DepartureEventBus.onVehicleEntered` y **nunca** al coordinator, así que `vehicleRideAtMs` sólo se
rellenaba con un EXIT: la puerta de la revocación no existía hasta que AR entregara una salida.

## Doctrina violada

*El evento NOMINA, sólo el movimiento MEDIDO confirma* — y su corolario del lado del veto, que es lo
que `DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001` dejó escrito: **la cadencia cede ante un embarque
que AR sí atestiguó**. Aquí AR lo atestiguó, la app lo usó, y la revocación no se disparó porque el
dato nunca llegó al estado que la implementa. El 26-08 el motivo fue una rama que retornaba antes; en
el 25-09 el dato **no estaba en la habitación**.

Y es la forma de *sistemas, no parches*: el invariante —*lo que el arm dio por probado lo saben todos
los jueces de esa sesión*— no vivía en ningún sitio.

## Diseño

**El arm dice WHEN, y la semilla entra por la MISMA puerta que usa el receptor de AR.**

- **`ArmEvidence.boardingWitnessedAtMs(armedAtMs): Long?`** — `when` exhaustivo sobre el sellado, en
  el único sitio que clasifica arms (allí ya viven `driveAuthorization` y
  `confirmsSilentlyWithoutMeasuredDrive`, y hay un guard que prohíbe listas de etiquetas a mano en
  cualquier otro sitio). Un arm nuevo **no compila** hasta que su autor contesta.
  - `VerifiedByVehicleEnter` → `armedAtMs - enterToExitMs`: el hueco que el verificador ya midió.
  - `BoardingAtCar` → pasa a llevar `enterTrueTimeMs`. **Ya estaba disponible en el arm site**: el
    ladder de AR-first decide *con* `trueEpochMs` y lo tiraba al suelo en cuanto decidía.
  - Todo lo demás → `null`, enumerado.
- **`CoordinatorParkingDetector.invoke`** siembra `egress.onVehicleRide(...)` justo al lado del bloque
  que ya siembra `hasEverReachedDrivingSpeed` desde el arm. **No decide nada**: qué arms pueden
  contestar vive en el tipo, no en el detector.
- Al vivir todo en `commonMain`, **iOS queda reparado sin tocar `iosMain`** — la semilla es del
  detector compartido, no del receptor de plataforma.

### ⛔ Por qué NO basta con «cualquier arm verificado»

Es lo que decide el diseño, y viene del campo. **Field 16-08**: una bici de 59 min a Los Toruños
quedó sellada **`verified_speed`** —una bici se saca los 10 km/h sin esfuerzo—, rompió la geocerca del
coche a 352 m y re-pinchó un Mercedes a **4,8 km** del coche real. Si un arm verificado por velocidad
pudiera afirmar un embarque, esa bici recibiría exactamente la refutación que el veto existe para
negarle. Ese fallo cuesta **un coche**; el que este ticket arregla cuesta **una plaza**. Fallo
asimétrico decide la dirección.

⚠️ `BoardedAwayFromCar` es la única exclusión **por el dato, no por la doctrina**: sí atestigua un
embarque AR, pero el instante del ENTER declinado no viaja por el re-look que arma, y fecharlo en el
arm lo colocaría MÁS TARDE de lo que pasó — la única dirección que gana indebidamente a una marca de
bici. Sin instante, no se afirma.

## Criterio de éxito

- [x] `fruteria_2509_the_arm_that_witnessed_the_boarding_carries_the_park` — el replay del campo,
      **sin stampear nada a mano**: guarda, y el pin cae a ≤60 m de donde el stream se asentó (20,5 m
      del pin que el user puso a mano). Era la characterization que afirmaba la pérdida; **invertida**.
- [x] `fruteria_2509_a_speed_verified_arm_gets_no_refutation_and_still_loses_it` — el **control
      negativo**: mismo stream, mismo ancla, misma valla, misma `DriveAuthorization`; la única
      variable es si el arm vio al usuario subirse a algo. Sigue perdiendo el aparcamiento
      (`aborted_unattended_human_powered`). Hace dos cosas: aísla la causa, y cierra la puerta de Los
      Toruños.
- [x] `ArmLabelTest`: el conjunto que puede atestiguar un embarque está **enumerado**
      (`VERIFIED_ENTER`, `ENTER_AT_CAR`) para que nadie lo sea por omisión; más el test explícito de
      que `verified_speed` y `bt_ride` nunca contestan; más la aritmética de las dos formas (hueco
      relativo vs. instante absoluto).
- [x] Los dos replays que armaban `BoardingAtCar` (parafarmacia 29-08, góndola 25-08) mantienen su
      veredicto con el embarque sembrado.
- [x] **3 761 tests en verde**, 0 fallos (`./gradlew testAll`).

## Consumidores auditados

`grep -rn "vehicleRideAtMs\|onVehicleRide" shared/src app/src`:

| sitio | veredicto |
|---|---|
| `isHumanPoweredRide` (`boardingWitnessed` + «gana el embarque más reciente») | **cerrado** — es el destinatario |
| `StageInputs.humanPoweredRide` | **cubierto por convergencia**: sólo reenvía el campo |
| `CoordinatorParkingDetector:1078` (tap de diagnóstico) | **cerrado, y a favor**: ya emitía un `ActivityTransition IN_VEHICLE/ENTER` sintético cuando el campo se rellena, así que **la traza ahora enseña el embarque** en vez de dejar un veto sin causa visible |
| `EgressEvidence.onVehicleExit` (`vehicleRideAtMs = maxOf(...)`) | **exento**: un EXIT ya implicaba un ride y usa `maxOf`, así que la semilla nunca puede retrasar un valor posterior |
| `ActivityTransitionReceiver.onVehicleRide` (Android) | **exento**: sigue siendo la vía de las entregas en vivo; la semilla entra por la misma puerta y no la sustituye |
| `IosActivityRecognitionManagerImpl` / `IosDetectionController` | **cerrado de rebote**: iOS no stampeaba ride NINGUNO (su ENTER va al bus); la semilla es de `commonMain` |
| `ArmEvidence.allArms` + `ArmLabelTest` | **cerrado** — la población del nuevo `when` tiene testigo |
| `CoordinatorDetectionService:1239` (único productor de `BoardingAtCar`) | **cerrado** — pasa `trueEpochMs` |
| `EvaluateArEnterArmUseCase`, `DeclinedBoardingRelook` | **exentos**: sólo citan el arm en KDoc, no lo construyen |

## Lo que esto NO resuelve

**No sustituye a `DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001`**, que sigue **abierto y
bloqueado** por la muestra de bici. La cadencia sigue condenando coches urbanos; un coche **sin ningún
embarque AR** —field 18-08, cero eventos AR entre 316— seguiría perdiendo su plaza. Esto es una red de
seguridad independiente: hace que la revocación funcione cuando el embarque existe.

**Riesgo residual, dicho en voz alta.** Una bici en la que AR diga `IN_VEHICLE` y **nunca**
`ON_BICYCLE`. Ese agujero **ya existía** para las entregas en vivo —este cambio sólo amplía la ventana
temporal— y lo tapan, cuando AR acierta a medias, la arbitración «gana el embarque más reciente» y el
perfil del vehículo (una bici registrada nunca auto-confirma). Las cuatro sesiones en bici registradas
(16-08 Samsung, 18-08 Oppo, 19-08 Oppo + Redmi) no produjeron ni un `IN_VEHICLE`: 16-08 armó
`verified_speed`, 19-08 armó `MANUAL` por el handoff, 18-08 no tuvo un solo evento AR, y otra entregó
`ON_BICYCLE` explícito.

## Relacionado

- `docs/backlog/det-human-powered-veto-must-be-revocable-001.md` (la arbitración que esto alimenta)
- `docs/backlog/det-pedal-cadence-cannot-convict-a-car-in-traffic-001.md` (el origen, abierto)
- `docs/backlog/det-the-cadence-corpus-must-be-executable-001.md` (donde se encontró)
- `docs/detection/PARKING-DETECTION.md` §2, entrada propia
