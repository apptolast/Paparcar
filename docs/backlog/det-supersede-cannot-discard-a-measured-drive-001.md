# DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001 · un supersede es la CONTINUACIÓN del mismo viaje, no un viaje nuevo

**Estado:** 🟢 Implementado, sin commitear · rama `bugfix/DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001-carry-drive-proof` · worktree `../Paparcar-supersede-drive-proof`

## Problema

Field test 25-08-2026, Oppo CPH2371 (uid `fiypNbElGlfFexLMpU9sNaMjRMD3`, coche activo **Ford Focus**,
sin BT → estrategia Coordinator). Trayecto real, contado por el user:

> gimnasio → gasolinera (había cola, me fui sin repostar) → hacia casa → **gasolinera de al lado de
> casa** (junto a C/ Góndola) → **casa, a escasos metros**.

El Redmi (control positivo, Coordinator puro) confirmó a las **20:02:51** en la gasolinera de al lado
de casa (`550cca8e`, 36.6089721,-6.2778915, `steps+egress` / `verified_late`) — los 77 m de la
captura de campo. **El Oppo no dejó ningún pin.**

Reconstrucción desde `diagnostics/{uid}/sessions` (el `parkdiag.log` del móvil ya había rotado — sólo
llega hasta 22:12; el viaje sólo existe en remoto):

| Hora local | Sesión | arm | Medido | outcome |
|---|---|---|---|---|
| 19:35:56 → **19:59:05** | `1787679356333` | `SIGNIFICANT_MOTION` (sentry-wake, geof=`550db43a`) | **23,2 min · vmax 98 km/h · 60/261 fixes conduciendo · 206 pasos** | **`ended`** |
| 19:59:05 → 20:00:24 | `1787680745542` | `AR_VEHICLE_ENTER` (geof=`a786c135`, `lag=149ms`, `dep=enter_at_car`) | 1,3 min · vmax 13,7 km/h · **0/17 fixes conduciendo** · 12 pasos | `aborted_false_enter` |

El `endedAt` de la primera (`…745472`) y el `startedAt` de la segunda (`…745482`) están a **10 ms**:
es `cancelDetectionJob()` dentro de `handleArTransition`.

Detalle del final de la sesión buena:

- **19:57:28** el coche para (`stoppedDurationMs` 2.003 → 95.405 ms) → **la gasolinera de al lado de casa**.
- 19:57:38–19:58:49 pasos 190→206: el user se baja.
- **19:59:05** el AR dispara `IN_VEHICLE ENTER`. **Es un ENTER VERDADERO** — el user se vuelve a montar.
- 19:59:05–19:59:41 (ya en la sucesora) el salto de pocos metros hasta casa, pico 13,7 km/h.
- 19:59:41 parada definitiva; 20:00:07+ pasos de egress → **el aparcamiento real, que no tiene nadie**.

### ⚠️ Corrección del diagnóstico inicial: la etiqueta la decidía una CARRERA

`CoordinatorParkingDetector` **ya tenía** una rama que estampa `"superseded"`: el `else` del guard de
propiedad en su `finally`. No se disparó, y la razón importa más que el síntoma.

`cancelDetectionJob()` hace `cancel()` **sin `join()`**. El `finally` del predecesor y el `invoke()`
de la sucesora (que reclama `currentSessionId` en su primera línea) corren sin orden garantizado:

- si el predecesor pierde la carrera → rama `else` → traza `superseded`;
- si la gana → rama de propiedad → `SessionEnded(epilogue.outcome)`, y su outcome nunca lo refinó
  nadie, así que sale **`Ended`, el valor de fábrica**.

El 25-08 la ganó. Una sesión de 23 minutos a 98 km/h se despidió con la etiqueta que significa
"nada que declarar", **y el traspaso no existía en ninguna de las dos ramas.**

## Doctrina violada

**«El evento NOMINA, sólo el movimiento MEDIDO confirma»** — aquí al revés, que es peor: una
**nominación destruyó evidencia medida**.

**«Todo trigger dispara SIEMPRE, con verificación tardía; un evento viejo pierde autoridad directa,
nunca se descarta.»** La sesión superseded no perdió autoridad: desapareció. No confirmó, no abortó,
no preguntó, no dejó nudge (`Ended.triggersHonestClose = false`).

**«Fallo asimétrico: ante la duda se PREGUNTA.»** No se preguntó nada. Y un parking perdido **con
datos** es bug nuestro, no excusa del OS: `exactHeartbeatLaneDead: false`, `batteryUnrestricted:
true`, 261 fixes.

## Señales / datos disponibles

Todo lo necesario existía en el instante del supersede:

- La predecesora tenía `DriveProof.proven = TRACK_WINDOW` y `provenMaxSpeedMps ≈ 27,2 m/s`.
- `DetectionEvent.SessionSuperseded` ya se emitía — la rama estaba instrumentada; lo que emitía era
  un obituario, no un traspaso.
- `ArmEvidence` es literalmente el canal tipado para *"what proved that the vehicle actually drove
  **before this session started** looking for the next park"*.
- La sucesora se armó con `ArmEvidence.BoardingAtCar`, que **por diseño** no siembra
  `driveAuthorized` (*"the session must measure the drive itself"*). Correcto para un ENTER espurio
  junto a un coche parado; falso cuando la predecesora acaba de medir el viaje.

## Diseño

> **Una sesión que ha MEDIDO conducción no puede terminar en `Ended`.** O confirma, o pregunta, o
> **entrega su prueba a su sucesora**. Un supersede es la tercera opción, no una cuarta.

Cinco piezas, cada invariante en un solo sitio:

1. **`DriveAuthorization` (`ArmEvidence.kt`)** — enum de tres valores `None` / `OnTrust` / `Measured`,
   **declarado por cada arm**. Sustituye a `isVerifiedDeparture = this is A || this is B`, que era
   pertenencia por deletreo (el mismo accidente que `SessionOutcome` se tipó para evitar) y que
   además **no podía expresar el tercer caso**. `isVerifiedDeparture` sobrevive como
   `driveAuthorization != None`, así que ningún arm existente cambia de conducta.
2. **`ArmEvidence.InheritedDrive(maxSpeedMps, source)`** — label `inherited_drive`,
   `DriveAuthorization.Measured`. Lleva el `DriveProofSource` para que una traza de campo lea **qué**
   prueba se heredó en vez de inferirlo del pico.
3. **`SessionTelemetry.seededOnInheritedDrive()`** — hermano de `seededOnArmTrust()`, y
   deliberadamente NO la misma llamada: pone `authorizedOnArmTrustOnly = false`, que es lo único que
   `notifyDepartureDismissed` consulta antes de retractar. La confianza se puede retirar; una
   medición no. Reutilizar el otro seed habría **blanqueado una medición en confianza**.
4. **`inheritedArmEvidence(runningDrive)` (`SessionSupersede.kt`)** — el veredicto puro hermano de
   `shouldSupersedeRunningSession`: aquél dice *si* se cede el paso, éste *qué viaja*. Sólo hereda
   `DriveProof.proven` (latcheado, medido), **nunca la autorización**: la predecesora podía estar
   sembrada on-trust, y propagar eso encadenaría sesiones creyendo cada una que la anterior vio un
   coche sin que ninguna lo hiciera.
5. **`CoordinatorParkingDetector.notifySuperseded()`** — se llama **antes** del cancel, y ese orden
   es todo el arreglo. Sella `SessionOutcome.Superseded` (la carrera desaparece: ambas ramas dicen lo
   mismo) y devuelve lo que hereda la sucesora, porque después del `cancel()` ya no queda sesión a la
   que preguntar.

`SessionOutcome.Superseded` declara sus tres pertenencias: `triggersHonestClose = false` (la sucesora
es la dueña del pin de este viaje; correr la escalera aquí le cerraría el mundo por debajo) y
`RESETS` (el streak amortigua arms REFUTADOS; un supersede es lo contrario).

`inherited_drive` entra en `ArmEvidence.isVerifiedLabel`. **Es una decisión, no una inercia**: los dos
guards de `ConfirmParkingUseCase` leen el pico de la SUCESORA, que en el último salto de un viaje es
una velocidad de maniobra. Sin el bypass, el único arm que lleva una conducción medida sería el que
esos guards confunden con un peatón.

⚠️ **Lo que este ticket NO hace**: el tramo final (pocos metros a ≤13,7 km/h) sigue siendo
inconfirmable **por sí solo**, y debe seguirlo — es exactamente el movimiento que produce un falso
positivo. Lo que arregla es que ese tramo no llegue huérfano de la prueba que el viaje ya tenía.

## Criterio de éxito

✅ **Replay del viaje real** (`Trace_Gondola2508Supersede.kt`, 17 fixes + 12 pasos, cuadra con el
`drive 0/17fix · steps 12` que la propia sesión escribió al salir):

- con `BoardingAtCar` (el build de campo) → `aborted_false_enter`, 0 pins: **reproduce la pérdida**;
- con `InheritedDrive` → los 12 pasos ya no refutan nada y **la sesión sigue viva** al agotarse la traza.

⚠️ **Lo que el replay NO demuestra, y el test lo dice dentro**: el confirm. La sesión de campo murió
a los 78,9 s y en ese instante el móvil seguía a ~7 m del coche — el desplazamiento de egress que
un `steps+egress` necesita **no existe dentro de esa ventana** (el Redmi confirmó ~2 min más tarde).
La traza acaba `ended` por la razón honesta: se acabó la grabación, no se alcanzó un veredicto.
El confirm que el seed desbloquea se pinea en `TRACE_BUG_REPARK_WALK_001`, que sí contiene su momento:
`an_inherited_drive_unlocks_the_same_confirm_a_verified_departure_does`.

**Verificación de que las aserciones discriminan** (método `DET-2208-TRIPS-BECOME-REPLAYS-001`):
neutralizando `InheritedDrive → DriveAuthorization.None` se ponen rojos los dos replays y
`should_declare_an_inherited_drive_measured_rather_than_lent_on_trust`.
`FalseEnterAbortStageInheritedDriveTest` **no** se pone rojo con esa neutralización — llama al seed
directamente, así que su discriminante es su test hermano (sin herencia → aborta), no el enum.

**Suite: 1.649 tests, 0 fallos** (master `2deff3c9`: 1.636).

Falta el criterio que ningún test cubre: **un viaje real con parada intermedia y re-embarque**
(repostar, recoger a alguien) debe dejar pin en el destino final.

## Consumidores auditados

Invariante: *cancelar una sesión viva no puede tirar evidencia medida*. Los 10 `cancelDetectionJob()`
de `CoordinatorDetectionService`:

| Línea | Vía | Clasificación |
|---|---|---|
| `:789` | GEOFENCE_EXIT supersede | 🟢 **cerrado** — `notifySuperseded()` antes del cancel; `armedWith = supersededDrive ?: armEvidence` |
| `:915` | AR_TRANSITION `ArmAtCar` | 🟢 **cerrado** — donde mordió |
| `:950` | AR_TRANSITION `ArmMidTrip` | 🟢 **cerrado** — misma precedencia |
| `:368` | SENTRY_WAKE | ⚪ inalcanzable con sesión viva: `handleSentryWake` retorna en `:347` si `detectionJob.isActive`. El cancel es defensivo |
| `:428` | START_TRACKING | ⚪ igual, guard en `:423` |
| `:447` | ARRIVAL_HANDOFF | ⚪ igual, guard en `:442` |
| `:483` | USER_STOP | ⚪ exento: `onUserStoppedDetection()` estampa `stopped_by_user` ANTES, así que ya nombra su final y no hay carrera. Un falso negativo PEDIDO no tiene sucesora |
| `:310` | STOP_TRACKING | ⚪ exento: cancel interno sin sucesora |
| `:1806` | `onDestroy` | ⚪ exento: muere el proceso, no hay a quién entregar |
| `:324` | **BT_OVERRIDE** | 🔴 **exento POR DOCTRINA, y hay que decirlo en voz alta** — es un supersede real (el carril BT toma el relevo), pero *"BT never enters coordinator scoring — it overrides"*. Pasarle la prueba de conducción del Coordinator a una sesión BT sería mezclar los dos carriles. La vía BT confirma con su propia evidencia determinista |

Invariante: *quién bypasea los guards de re-park*. `ArmEvidence.isVerifiedLabel` →
`ConfirmParkingUseCase:193` (assertion guard) y `:207` (repark guard). Ambos **cubiertos a propósito**
por la inclusión de `inherited_drive`, con la razón escrita en el KDoc de la función.

Invariante: *quién siembra `driveAuthorized`*. `armEvidence.isVerifiedDeparture` →
`CoordinatorParkingDetector:444`, ahora un `when` exhaustivo sobre `DriveAuthorization`. Único
consumidor; un arm nuevo no compila sin declarar su autorización.

## Follow-ups deliberadamente fuera de alcance

- **El ancla de la sucesora.** Sigue siendo la del pin que la nominó (en el campo, el del Kamiq), no
  el punto donde el coche paró en la parada intermedia. Con el seed no hace falta para confirmar,
  pero un `departureAnchor` heredado del `anchorTrust` de la predecesora haría además válida la
  prueba SHORT_HOP en el tramo final. No se toca aquí porque cambia otra decisión.
- **`cancelDetectionJob()` sigue sin `join()`.** El traspaso síncrono hace que ya no importe para la
  etiqueta ni para la prueba, pero la carrera del `finally` sigue existiendo para todo lo demás que
  esa rama hace (`retractDeducedDeparture`, epílogo). Medir antes de tocarla.

## Relacionado

- `DET-BT-CAR-CANNOT-NOMINATE-A-COORDINATOR-SESSION-001` — el otro ticket del mismo FN, y la razón de
  que el supersede se disparara **con 6.214 m de distancia**. Independientes: aun con el coche
  correcto, un re-embarque legítimo lejos del origen habría superseded igual.
- `DET-SUPERSEDE-001` (el guard original), `DET-AR-REARM-001`, `DET-AR-FIRST-001`, `DET-G-04`,
  `DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001` (de quien sale la distinción confianza/medición).
