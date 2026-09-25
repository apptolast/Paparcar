# DET-A-VERIFIED-BOARDING-MUST-REACH-THE-VETO-001 · el embarque que verifica la salida no llega al juez que lo necesita

**Estado:** 🟡 Abierto, **no bloqueado** · sin rama · sin worktree

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
tiene **un único call site**: `ActivityTransitionReceiver`, es decir entregas de AR **en vivo, dentro
de la sesión**. El ENTER que verificó la salida ocurrió ANTES del arm, así que no vuelve a
entregarse: la sesión tuvo 587 eventos y **cero `ACTIVITY_TRANSITION`**.

Resultado: un embarque que la app consideró bueno para autorizar la conducción es **invisible** para
el único juez que decide si esa conducción fue a motor.

## Doctrina violada

*El evento NOMINA, sólo el movimiento MEDIDO confirma* — y su corolario del lado del veto, que es lo
que `DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001` dejó escrito: **la cadencia cede ante un embarque
que AR sí atestiguó**. Aquí AR lo atestiguó, la app lo usó, y la revocación no se disparó porque el
dato nunca llegó al estado que la implementa. El 26-08 el motivo fue una rama que retornaba antes; en
el 25-09 el dato **no está en la habitación**.

Y es la forma de *sistemas, no parches*: el invariante —*lo que el arm dio por probado lo saben todos
los jueces de esa sesión*— no vive en ningún sitio.

## Señales / datos disponibles

- `arm_1790330246007` → `ARM:GEOFENCE_EXIT (geof=b15ad01b d=175m acc=34m exitLoc=… dep=verified_enter)`
- `ArmLabel.VERIFIED_ENTER` ya viaja **persistido** en la sesión, así que el HECHO del embarque no se
  pierde; lo que se pierde es su **instante**, que es lo que `isHumanPoweredRide` compara.
- `ArmEvidence.VerifiedByVehicleEnter(enterToExitMs)` lleva el hueco enter→exit: con el `triggerAtMs`
  del arm, el instante del embarque es **derivable** (`triggerAtMs - enterToExitMs`). No hace falta
  inventar nada ni ampliar el bus.

## Diseño (esbozo, NO decidido)

La reparación cabe en un sitio: **al armar, sembrar en `EgressEvidence` el embarque que el arm ya
corroboró**, derivando su instante del par (`triggerAtMs`, `enterToExitMs`). Entonces
`isHumanPoweredRide` ve `vehicleRideAtMs != null`, `boardingWitnessed` es cierto, y la cadencia cae por
la arbitración que YA existe — sin tocar un solo umbral.

Preguntas abiertas antes de escribir código:

- ¿Vale para **todas** las etiquetas de arm que implican embarque (`VERIFIED_ENTER`, `ENTER_AT_CAR`,
  `BOARDED_AWAY_FROM_CAR`, `BOARDING_AT_CAR`, `BT_RIDE`, `OWN_CAR_AUDIO`) o sólo para las que
  atestiguan un `IN_VEHICLE` de AR? ⛔ Ojo con el carril BT: **cero señales BT en el scoring del
  Coordinator**. Esto no es scoring, es una refutación — pero hay que decidirlo **explícitamente**, no
  por omisión, que es justo el modo de fallo de
  `VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001`.
- ¿Qué pasa con `VERIFIED_LATE`, que es una etiqueta **sin arm** (la escribe el worker de salida)?
- La ventana de memoria (`humanPoweredRideMemoryMs`, 3 h) no estorba: un embarque sembrado al armar es
  por construcción de esta sesión.

## Criterio de éxito

- `fruteria_2509_a_city_car_under_40_kmh_is_convicted_and_the_park_is_lost` **se invierte**: el mismo
  stream, sin stamp manual ninguno, guarda — porque el arm siembra lo que ya sabía. Hoy ese test
  afirma la pérdida, y su gemelo
  `fruteria_2509_the_boarding_the_arm_already_had_would_have_carried_it` ya demuestra que con el dato
  enchufado el aparcamiento se salva. Los dos viven en `DetectionTraceReplayTest`.
- Los dos casos del 26-08 siguen en verde, y Los Toruños (bici, 16-08) sigue vetado: una bici no
  produce `IN_VEHICLE ENTER`, así que por construcción no gana nada con esto.

## ⚠️ Lo que esto NO resuelve

**No sustituye a `DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001`.** Aquél es el ORIGEN (que la
cadencia no debería acusar a un coche urbano) y sigue bloqueado por la muestra de bici. Éste es una
**red de seguridad independiente**: hace que la revocación funcione cuando el embarque existe. Un
trayecto en coche sin embarque AR ninguno —el caso del 18-08— seguiría condenado, y por eso el padre
no se cierra con esto.

## Relacionado

- `docs/backlog/det-human-powered-veto-must-be-revocable-001.md` (la arbitración que esto alimenta)
- `docs/backlog/det-pedal-cadence-cannot-convict-a-car-in-traffic-001.md` (el origen)
- `docs/backlog/det-the-cadence-corpus-must-be-executable-001.md` (donde se encontró)
