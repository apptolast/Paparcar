# DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001 · El fix que dispara la salida no puede además probarla

**Estado:** ✅ Done — en master (1433 tests verdes). ⏳ Falta validarlo en campo: dormir una noche
en casa tras aparcar en la puerta sin que aparezca un segundo pin.
Deja abierto **DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001**.

## Problema

Field 22-08-2026, Oppo (uid `fiypNbElGlfFexLMpU9sNaMjRMD3`). A las **20:38:17** se confirma un pin
correcto en C. Frutos (`ce3bb858…`, `36.60772,-6.2679683`, `steps+egress` / `self_observed`). El
usuario entra en su casa. A las **20:54:31** la app planta un segundo pin **dentro de la casa**
(`e17ee361…`, `36.6081233,-6.26774`, `steps+egress` / **`verified_speed`**, fiabilidad 0.9), a 49 m
del bueno — y **reemplaza al correcto borrándole la geocerca**
(`→ removing replaced session's orphan geofence=ce3bb858`).

El Redmi, en el mismo viaje y a la misma hora, hizo el pin correcto (`f741c0bb…`, 20:38:57) y **no
generó nada más**: el espejismo fue del GPS del Oppo.

Traza local (`files/parkdiag.log`, sobrevivió al apagón por batería):

```
20:50:37.809  OneFix: 36.6086317,-6.2678967  speed=10.0 m/s (36 km/h)  acc=5.5 m  age=0s
20:50:37.829  GEOFENCE_EXIT — arming (geof=ce3bb858 d=101m acc=5m dep=verified_speed)
20:50:38.264  ✓ verified_speed → seed hasEverReachedDrivingSpeed=true            [DET-G-04]
20:50:38.603  Depart attempt=0  29.6 km/h → Inconclusive(exit_echo)              [DET-DEPART-PROOF-001]
20:50:53      attempt=1  0,95 km/h → Inconclusive
20:51:23      attempt=2  0,0  km/h → Inconclusive
20:52:23      attempt=3  0,0  km/h → "attempts exhausted, no admissible vehicle signal — DISMISSED"
20:52:26      ▶ steps+egress (steps=9) → fast confirm     ← los guards anti-caminata seguían desarmados
20:54:31      confirmParking → pin dentro de la casa
```

El teléfono estaba **quieto dentro de la casa**. Un único fix fantasma (36 km/h, acc 5,5 m, a 101 m
al norte) rompió la valla y **se acreditó a sí mismo** como conducción para toda la sesión.

## Doctrina violada

> *El evento NOMINA, solo el movimiento MEDIDO confirma.*

Un solo sample Doppler no es conducción medida. La regla que lo dice **ya existe** —
`ParkingDetectionConfig.departureProofMinGapMs = 20 s`, DET-DEPART-PROOF-001 — y su comentario
describe literalmente este mismo modo de fallo (*field 2026-07-27 18:30, Oppo at home: one 14 km/h
fix at 121 m fired the EXIT and re-confirmed itself 140 ms later*). Mismo móvil, misma casa, misma
forma, un mes después.

Pero la puerta se instaló **sólo en la vía del worker** (`DetectParkingDepartureUseCase`). La vía
del **arm** (`VerifyDepartureEvidenceUseCase`) mira el mismo fix **sin la puerta**. Por eso, con
760 ms de diferencia, el worker lo llamó `exit_echo` y el arm lo llamó `verified_speed`.

Es exactamente el corolario que dejó DET-DRIVE-PROOF-001 → DET-DEPART-PROOF-001: *cerrar sólo la
vía donde mordió NO basta*. Y está admitido por escrito en `CoordinatorParkingDetector.kt:927`:

> «Arm seeding and session lifecycle (hasEverReachedDrivingSpeed) are **deliberately untouched**.»

Segunda mitad del agujero: la evidencia del arm es una **hipótesis** que el worker adjudica durante
~45 s, pero `DepartureConfirmationListener` sólo tiene `notifyDepartureConfirmed()`. **La semilla
sube y no baja**: el `Dismissed` de las 20:52:23 no pudo retirar lo que el arm sembró a las 20:50:38.

## Señales / datos disponibles

- `parkdiag.log` local del Oppo (22-08 14:09 → 23-08 15:36) con la traza completa.
- Firestore: los dos pines con `detectionPath` + `armEvidence` (provenance ya estampada).
- En 25 h de log hay **exactamente 2 arms `verified_speed`**:
  - `ce3bb858` (20:50) → **este FP**.
  - `e17ee361` (23-08 00:17) → viaje **real**… que **no necesitaba la semilla**: su propio stream
    se ganó `sustained drive` (30 s en banda) a los 30 s y `MOTOR witnessed` a los 35 s, y confirmó
    bien 14 min después en el destino real.

  → La semilla fue **dañina una vez y redundante la otra**. Ese es el argumento de que quitarla no
  cuesta detección.

## Diseño

**El invariante vive en UN sitio: una regla pura compartida, no un guard por vía.**

### 1 · `domain/detection/DepartureSpeedProof.kt` (nuevo, función pura de nivel superior)

Predicado compartido por 2+ veredictos ⇒ va a `domain/detection/`, no dentro de un caso de uso
(patrón `SentryWakeCooldown` / `HumanPoweredRide`, doctrina DET-VERDICT-NOT-PREDICATE-001).
**No es un caso de uso nuevo** — no aparece en el vocabulario de diagnóstico por sí solo.

```
DepartureSpeedVerdict = Independent | Echo | NotDriving
```

Regla: velocidad creíble (`isCredibleDrivingSpeed`, que ya cubre umbral + precisión) **Y** el fix
POSTDATA al evento en ≥ `departureProofMinGapMs`. Un fix contemporáneo al trigger es `Echo`.

### 2 · Los dos consumidores llaman a la MISMA regla

- `DetectParkingDepartureUseCase` (worker) — sustituye su `speedIsIndependent && credibleSpeed`
  inline. Comportamiento idéntico; deja de ser el único dueño de la regla.
- `VerifyDepartureEvidenceUseCase` (arm) — recibe `currentFixTimestampMs` y aplica la regla.
  `Echo` cae a la escalera de AR ENTER y, si tampoco, a `Unverified`.

**Consecuencia asumida y honesta:** los dos call sites del arm
(`CoordinatorDetectionService:729` EXIT y `:900` AR-ENTER-mid-trip) pasan `exitTimestampMs = now` y
muestrean el fix milisegundos antes ⇒ **`VerifiedBySpeed` deja de ser alcanzable desde el arm**. No
se borra el tipo (`verified_speed` está persistido en pines existentes y el parámetro admite un
evento genuinamente antiguo), pero en la práctica la vía de velocidad del arm **muere por física,
no por política**. La semilla pasa a llegar sólo de donde hay medición:

| Caso real | Quién siembra ahora | Retraso |
|---|---|---|
| Salida a media conducción | worker `notifyDepartureConfirmed` (`verified_late`), reintento a ~15 s | ≤15 s |
| Salto corto entre dos plazas | `DET-SHORT-HOP-PROOF-001` (desplazamiento desde el pin) | inmediato |
| Cualquier viaje más largo | el propio stream (`BUG-SHORT-TRIP`) | inmediato |

Con la semilla retirada, la sesión del FP habría abortado igual que la de un minuto antes:
`⊘ false-ENTER abort — 8 steps before driving speed`.

### 3 · La semilla del arm se vuelve retractable

`DepartureConfirmationListener` gana `notifyDepartureDismissed(geofenceId)`.
`RunDepartureCheckUseCase` lo llama en sus DOS salidas `Dismissed` (rechazo e intentos agotados).

El coordinator retracta **sólo si**:
- la sesión viva la armó **esa** valla (`armingGeofenceId`, nuevo parámetro de `invoke()`), y
- la semilla sigue **sin ganarse**: la puso el arm y **no** la ha respaldado ninguna medición
  posterior (`driveProven` / `hasEverMoved` / velocidad medida en el stream).

Esto cubre también la vía que el punto 2 **no** toca: un arm `VerifiedByVehicleEnter` (AR ENTER
corroborado por distancia) al que el worker luego responde `Rejected`/`Dismissed` — hoy conserva su
semilla para siempre.

## Criterio de éxito — ✅ cumplido en verde (1433 tests)

- ✅ Un EXIT cuyo fix es contemporáneo al evento arma `Unverified`, no `VerifiedBySpeed`
  (`VerifyDepartureEvidenceUseCaseTest`, + `DepartureSpeedProofTest` sobre la regla pura).
- ✅ `RunDepartureCheckUseCase` → `Dismissed` (las **dos** salidas) retira la semilla de esa valla.
- ✅ Regresión: una sesión que YA midió conducción **no** pierde la semilla en un `Dismissed`;
  una valla ajena tampoco la toca.
- ✅ Regresión DET-G-04: la salida corta sigue confirmando (los dos tests DET-G-04 intactos, y el
  replay de contraste `same_trace_with_speed_verified_arm_confirms_at_the_stop_anchor`).
- ✅ Replay `TRACE_HOUSE_MIRAGE_001` (la traza real del 22-08 20:50, 51 fixes + 12 pasos).
- ⏳ Campo: dormir una noche en casa tras aparcar en la puerta sin que aparezca un segundo pin.

## Lo que el replay corrigió del diseño

⚠ **El replay refutó una premisa mía.** Yo daba por hecho que bastaba con la honestidad del arm.
Medido:

| Variante del replay | outcome | saves |
|---|---|---|
| arm `verified_speed`, sin `Dismissed` (**código de antes**) | `confirmed_steps+egress` | **1** ← el FP |
| arm `verified_speed` + `Dismissed` (con el fix) | `aborted_false_enter` | 0 |
| arm `Unverified`, sin `Dismissed` (sólo con el fix del arm) | `ended` | 0 |

Dos lecturas:

1. La traza **reproduce el FP fielmente**, así que el fixture vale como guard permanente.
2. La variante `Unverified` acaba en `ended`, **no** en `aborted_false_enter`. Sondeado:
   `movedAfterFirstFix = true` — el espejismo no se acabó en el trigger, y el **primer fix de la
   sesión** (8,2 m/s, acc 5,6 m) basta para poner `hasEverReachedDrivingSpeed` por la vía del
   STREAM, desarmando el guard false-ENTER incluso sin semilla. Lo que salva esa variante es la
   ETIQUETA del arm: `self_observed` mantiene despierto el guard de plausibilidad de re-aparcamiento
   en `ConfirmParkingUseCase`, del que las etiquetas verificadas se libran.

   → Es decir: **las dos mitades son portantes**, y aun así queda **un solo guard** entre un
   espejismo y un pin. Ese residuo es la mitad que DET-DRIVE-PROOF-001 dejó abierta a propósito y
   se abre como ticket propio: **DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001**
   (`docs/backlog/det-lone-sample-is-not-a-drive-001.md`). No se amplía aquí porque subir el listón
   de `hasJustReachedSpeed` toca la semántica de BUG-SHORT-TRIP y exige pasar todo el harness de
   replays.

## Consumidores auditados

`grep -rn "isCredibleDrivingSpeed"` — ¿usa alguien un fix tomado en el instante de un evento para
acreditar ESE evento?

| Call site | Rol del fix | Veredicto |
|---|---|---|
| `VerifyDepartureEvidenceUseCase:66` | acredita el EXIT que lo produjo | ❌ **cerrado por este ticket** |
| `DetectParkingDepartureUseCase:148` | acredita el EXIT, con gate de 20 s | ✅ ya cerrado (DET-DEPART-PROOF-001); pasa a usar la regla compartida |
| `EvaluateSafetyNetCheckUseCase:249` | dispara `DispatchDeparture(preconfirmed=false)` | **cubierto por convergencia** — va al worker, que sí aplica el gate (`exitAtMs = now` ⇒ el primer sample es `Echo` y reintenta) |
| `SentryWakeTriage:89` | decide ESCALAR (encender GPS) | **exento** — nominación pura: no confirma nada, el coordinator mide después |
| `EvaluateBtParkUseCase:104,120` | **veta** (`DrivingAbort`) | **exento** — fallo asimétrico correcto: un espejismo aborta, nunca planta |
| `EvaluateShortHopDriveProofUseCase:94` | precondición del desplazamiento medido | **exento** — la prueba es el desplazamiento desde el pin, no el sample |

`grep -rn "isVerifiedDeparture"` — único consumidor: `CoordinatorParkingDetector:600` (la semilla).
`grep -rn "notifyDepartureConfirmed"` — único emisor: `RunDepartureCheckUseCase:147`.

## Fuera de alcance (deliberado)

- **DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001** — un fix suelto sigue abriendo la sesión por la vía del
  stream. Ticket propio, con su doc; descubierto por el replay de este ticket.
- La línea del safety-net imprime `la valla se re-registró hace 29790398min` (~56 años) — timestamp
  a 0 en el copy de diagnóstico. Cosmético; ticket aparte si molesta.
- El Oppo pierde ticks del heartbeat exacto (32 pérdidas, rachas de hasta 5) sin llegar a marcar
  `exactHeartbeatLaneDead`. Es la señal de `DET-HEARTBEAT-LANE-REPAIR-001`, que sigue bloqueado por
  medición.
