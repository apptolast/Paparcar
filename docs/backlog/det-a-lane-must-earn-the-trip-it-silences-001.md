# DET-A-LANE-MUST-EARN-THE-TRIP-IT-SILENCES-001 · una reconexión no es un embarque, y no puede vetar lo que ya se midió

**Estado:** ✅ Done · en master · issue [#131](https://github.com/rndevelo/Paparcar/issues/131)

> ⏳ **Falta la prueba de campo**: el siguiente viaje de este usuario. Las sesiones que lleguen a
> `Candidate` deben acabar en `confirmed_*`; y si alguna acaba por arbitraje, ahora lo dirá con su
> nombre (`arbitrated_by_bt`) en vez de desaparecer bajo `"ended"`.

## Problema

Usuario `8fhNJGctp…` (Nothing A001, Android 16, app 1.3.0, base `(default)`), con un **BT aftermarket**
—comprado aparte, no la radio del coche— emparejado al Skoda Kamiq (`BT-HANDSFREE-AFTERMARKET`).
Reporta aparcamientos perdidos el 22-09 y pregunta si podemos fiarnos menos del BT.

Dos falsos negativos medidos, ambos con la MISMA forma:

| Sesión | Arm | Conducción medida | Prompt | Candidato | Fin | Pin |
|---|---|---|---|---|---|---|
| 12:20:19 → 12:32:56 | `ARRIVAL_HANDOFF` | 12 fixes 3,0–4,5 m/s | 12:23:59 `PROMPT_SHOWN` `low_medium` | OPENED 12:31:01 | **`ended`** | **ninguno** |
| 19:43:03 → 19:55:56 | `GEOFENCE_EXIT` (departure-follower 34 km/h) | 92 fixes 3,0–10,3 m/s | 19:49:47 `PROMPT_SHOWN` `low_medium` | OPENED 19:51:45 | **`ended`** | **ninguno** |

En la de las 12:20 los pasos van **1→11 en 8 segundos** —caminata pedestre real, ~80 pasos/min— y
**3 segundos después** llega `SESSION_ENDED`. La sesión muere en el instante exacto en que la prueba
de egreso iba a llegar.

`outcome = "ended"` no es un veredicto: es el valor de nacimiento
(`SessionTelemetry.kt:149`), y `endSession()` llama a `sessionCompleted()`, **no** a `endedWith()`
(`CoordinatorParkingDetector.kt:249-251`). Traducido: **el `finally` corrió y nadie estampó nada** →
cancelación externa.

La cancelación externa es `CoordinatorDetectionService.kt:1541`, dentro de `arbitrateBtEdge()`:

```kotlin
if (verdict != BtArbitrationVerdict.NoOp) {
    PaparcarLogger.d(DIAG, "  ⚡ BT arbitrates over the coordinator: … — aborting session")
    cancelDetectionJob()          // ⛔ sin outcome, sin pin, sin nudge
}
```

Y el veredicto sale de `EvaluateBtArbitrationUseCase.kt:127-130`:

```kotlin
BtArbitrationEvent.CONNECT -> when (coordinatorPhase) {
    DetectionPhase.Candidate -> BtArbitrationVerdict.VetoReturnToVehicle   // ⛔ no mira nada más
    DetectionPhase.Driving   -> BtArbitrationVerdict.NoOp
}
```

El dongle parpadea todo el día: **~20 ciclos connect/disconnect** medidos en el ledger de triggers
(`triggers_20718`, ~40 muestras `BATTERY source=bt_recorder`, dos por ciclo) y **12
`DEPARTURE_VERDICT`** entre las 20:26 y las 22:25, todos `bt_no_ride_ask` (enganche < 90 s) o
`bt_short_no_move_abort` (17–67 m). **Ninguno coloca un pin.** Cada **re**conexión que cae mientras
el Coordinator está en `Candidate` mata la sesión que estaba a punto de confirmar.

### El efecto cascada

El pin perdido de las 19:55 no costó un aparcamiento: costó **dos**. Sin pin no hay geocerca; sin
geocerca la salida siguiente no tiene `GEOFENCE_EXIT` que disparar; y esa sesión tampoco armó. El
usuario acabó marcando a mano (`587ce9e1` · `nudge_bt_no_ride`, 21:50). Por eso el issue dice
«hemos tenido que poner el aparcamiento manualmente **porque hemos perdido el último**».

### Por qué AHORA y no antes

| Pieza | Commit | Fecha |
|---|---|---|
| El veto `CONNECT→Candidate` | `DET-TIERS-001` (anterior a `b949efa13`) | **< 29-08** |
| `coordinatorDriveProven` en la rama **DISCONNECT** | `bb271e375` | 16-09 |
| Grabador de ruta en el connect BT (`btTrackingJob`) | `213b45966` | 14-09 |
| **El árbitro se mueve al intake serializado** | `d7880ac78` | **15-09** |

El veto es viejo. Lo que cambió el 15-09 es *dónde* se evalúa: su propio KDoc dice que antes se
calculaba en el receiver, «a snapshot racing the queue». **Antes el veto competía con la cola y a
veces se perdía; desde el 15-09 se ejecuta en todos los flancos.** DET-ONE-SERVICE-TWO-LANES-001 no
introdujo el defecto — dejó de hacerlo intermitente. El grabador de ruta del 14-09 **no** es la causa.

Y el 16-09 (`bb271e375`) arregló la rama hermana por este mismísimo aparato — su propio comentario lo
dice: *«Campo 16-09 (Nothing A001): 36 sesiones abortadas en 11 días por un MAC que se cae cada 17 s…
el usuario acabó marcando sus aparcamientos a mano.»* Metió `coordinatorDriveProven` en **DISCONNECT**
y dejó **CONNECT** a pelo. El mismo MAC entra hoy por la puerta de al lado.

## Doctrina violada

1. **«Todo trigger dispara SIEMPRE… nunca se descarta.»** El veto no degrada la sesión al evaluador:
   la cancela. No queda pin, ni nudge, ni outcome — ni siquiera un evento de diagnóstico que diga que
   hubo un veto.
2. **«Fallo asimétrico: ante la duda se PREGUNTA.»** Aquí ni se planta ni se pregunta: se calla. El
   prompt que YA estaba en pantalla muere con la sesión.
3. **«Sistemas, no parches.»** `bb271e375` cerró una de las dos ramas del mismo `when`. Cerrar la vía
   donde mordió no basta — es exactamente el corolario de DET-DRIVE-PROOF-001 → DET-DEPART-PROOF-001.

## Señales / datos disponibles

- `coordinatorDriveProven` — ya publicado en el runtime compartido y ya pasado al árbitro
  (`CoordinatorDetectionService.kt:1537`). No hay que añadir fontanería.
- **El servicio ya sabe distinguir una oscilación de un embarque** — y pregunta tarde:

  ```kotlin
  1557  arbitrateBtEdge(CONNECT, vehicleId)        // ← mata la sesión
  1558  // [BT-005] A reconnect inside the debounce means the disconnect was an oscillation…
  1560  if (btHuntJob?.isActive == true) { … }     // ← lo reconoce AQUÍ
  1563  btHuntJob?.cancel()
  ```

  `btHuntJob` activo = hay una caza abierta de un disconnect reciente = **este CONNECT es el rebote
  de esa misma desconexión**. El dato está en la mano tres líneas por debajo de donde se necesita.
  Mismo patrón ya corregido en `BluetoothParkingDetector.kt:102`: *«Se lee ANTES de la puerta de
  duración, no después. Ese orden ERA el bug.»*
- `BT_DISCONNECT_DEBOUNCE_MS = 30_000` (`BluetoothParkingDetector.kt:478`) — la ventana que ya define
  qué es oscilación en este proyecto. No inventamos un umbral nuevo.

## Diseño

**El invariante, en una frase:** *un carril solo puede silenciar un viaje con evidencia que él mismo
aceptaría como viaje.* Vive en el árbitro puro, que es el único sitio donde se decide quién manda
sobre quién.

### Fix A — la simetría que faltó el 16-09 (lo que perdió los dos pines)

El árbitro recibe **un dato más**: si este CONNECT llega dentro de la ventana de oscilación de la
desconexión que abrió la parada. Con eso, la rama CONNECT queda:

```kotlin
BtArbitrationEvent.CONNECT -> when (coordinatorPhase) {
    DetectionPhase.Candidate ->
        if (btReconnectIsOscillation) NoOp          // rebote del manos libres: no es un embarque
        else VetoReturnToVehicle                    // volvió al coche de verdad: el pin es falso
    DetectionPhase.Driving -> NoOp
}
```

⛔ **`coordinatorDriveProven` NO sirve como discriminador aquí**, y por eso no se usa: con conducción
probada son verdad *las dos* lecturas (volviste al coche después de aparcar **y** el dongle rebotó).
Lo que las separa es si hay una caza BT abierta de una desconexión de hace segundos.

Y el veto, cuando sea legítimo, **deja de ser mudo**: estampa outcome (`vetoed_by_bt_return`) y su
evento de diagnóstico, para que el próximo field-test no haya que reconstruirlo por resta.

### Fix B — reencuadrado (⚠️ ver «Decisión pendiente»)

La propuesta inicial —exigir `btMinRideDurationMs` (90 s) de enlace vivo antes de conceder
`BLUETOOTH` en `ParkingStrategyResolver.strategyFor`— **está mal y no se implementa**. Con la radio de
un coche sano el enlace se establece AL EMBARCAR, así que a la hora del `AR_VEHICLE_ENTER` es siempre
joven: el listón haría armar al Coordinator en cada viaje de todo usuario con BT, que es exactamente
el fallo de campo del 01-08-2026 (los viajes del Kamiq clavados en el Focus) que la puerta existe
para evitar.

Además, el barrido del ledger **desmiente** que el rechazo costara pines hoy:

| Rechazo | Arm posterior | Resultado |
|---|---|---|
| 09:10:45 `AR_VEHICLE_ENTER` | 09:11:15 `GEOFENCE_EXIT` | ✅ `confirmed_kinematic+egress` |
| 11:16:00 | 11:17:14 `GEOFENCE_EXIT` | ✅ `confirmed_steps+egress` |
| 12:16:34 | 12:20:19 `ARRIVAL_HANDOFF` | 🔴 FN #1 (muerto por el veto, no por el rechazo) |
| 19:39:12 + 19:39:28 | 19:43:03 `GEOFENCE_EXIT` | 🔴 FN #2 (ídem) |
| 22:17:03 | 22:18:43 `GEOFENCE_EXIT` | sesión abierta |

**Cinco de cinco:** el Coordinator armó igual, entre 30 s y 4 min después, por la puerta de la
geocerca. El `REFUSED_STRATEGY` es una violación de doctrina real (el trigger se **descarta** en vez
de degradarse al evaluador) pero **hoy no cuesta ni un pin**. Se documenta y se separa, no se arregla
a ciegas dentro de este ticket.

## Decisión pendiente (usuario)

El usuario propone lo que ya decidimos para iOS en [IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]: **BT como
SENSOR, no como dueño del viaje** — el FGS residente del Coordinator sigue midiendo siempre, y el BT
aporta su flanco determinista al confirmar. Es la respuesta de fondo a la pregunta del issue
(«¿podríamos confiar más en otras señales y menos el BT?»), pero arrastra la atribución: si el
Coordinator mide durante la propiedad BT, la sesión tiene que atribuirse al coche **conectado**, no
al primario. Eso es el fallo del 01-08-2026 y es un ticket propio, no un apéndice de este.

## Criterio de éxito

- [x] Un CONNECT que llega como rebote de una desconexión reciente **no** cancela una sesión en
      `Candidate` → el pin se coloca por `steps+egress` / `kinematic+egress` como en cualquier viaje.
- [x] Un CONNECT que es un regreso real al coche **sigue** vetando, y ahora deja outcome y evento.
- [x] Test de evaluador puro: 7 casos nuevos (rebote con y sin conducción probada, regreso tardío,
      borde exacto de la ventana, sin sello en disco, rebote en `Driving`, otro coche propio dentro
      de la ventana). Los 15 existentes se conservan intactos — el defecto `null` del helper (sin
      sello ⇒ sin oscilación probada) mantiene su intención.
- [x] **Suite: 3 692 tests verdes en 359 suites, 0 fallos** (`./gradlew testAll --rerun-tasks`, así
      que los guards de Konsist se EJECUTARON y no salieron UP-TO-DATE).
- [ ] Campo: en el siguiente viaje de este usuario, las sesiones que lleguen a `Candidate` acaban en
      `confirmed_*`. Y si alguna acaba por arbitraje, ahora lo dice: `arbitrated_by_bt`.

## Consumidores auditados

| Señal / símbolo | Sitios | Clasificación |
|---|---|---|
| `BtArbitrationVerdict` | `CoordinatorDetectionService.kt:1549` (único consumidor) | **cerrado** — el gate `!= NoOp` ahora estampa antes de cancelar |
| `markDisconnected` | `BluetoothConnectionReceiver.kt:127` (único call site) | **cerrado** — firma ampliada con `atMs`; el compilador cazaría cualquier otro |
| `lastDisconnectedAt` | `CoordinatorDetectionService.kt:1546` | **cerrado** — nace con este ticket |
| `btDisconnectDebounceMs` | `BluetoothParkingDetector` (caza) + `EvaluateBtArbitrationUseCase` (veto) | **cerrado** — una sola fuente; `grep BT_DISCONNECT_DEBOUNCE_MS` vuelve vacío |
| `SessionOutcome` (`when` exhaustivo) | `SessionOutcomeTest.kt:40` | **cerrado** — el guard OBLIGÓ a declarar las tres pertenencias de `ArbitratedByBt`; falló la compilación hasta hacerlo |
| `triggersHonestClose` | `CoordinatorDetectionService.maybeRunHonestClose()` | **cubierto por convergencia** — pregunta al TIPO (`lastOutcome.triggersHonestClose`), no al string; `ArbitratedByBt` responde `false` y la escalera no corre, que es lo correcto: el carril BT se queda el viaje |
| `lastSessionOutcome` (string) | `CoordinatorDetectionService.kt:1339`, `IosDetectionController.kt:934` | **exento** — sólo se imprime en la traza; ninguna decisión cuelga del literal |
| `sentryStreakEffect` | racha del centinela | **cerrado** — `RESETS`: un flanco físico del coche no es un armado refutado por caminata |

### Lado iOS

**Estructuralmente no aplica, y se deja escrito** ([IOS-A-BT-LANE-NEEDS-A-REAL-SIGNAL-001]): en iOS no
hay eventos de BT clásico para terceros, el resolver cae SIEMPRE a Coordinator y **no existe fuente
de flancos BT**. `grep "BtArbitration\|arbitrate" shared/src/iosMain` vuelve **vacío**: el árbitro se
invoca sólo desde `CoordinatorDetectionService` (androidMain). Lo que sí es común y viaja a iOS es el
outcome nuevo — `SessionOutcome` vive en `commonMain` y `IosDetectionController` ya serializa
`lastSessionOutcome`.

### No aplica en esta tarea

- **Strings**: cero texto de usuario nuevo. El arreglo quita una pregunta que sobraba, no añade copy.
- **Dev Catalog / `MockScenario`**: ni pantalla, ni estado MVI, ni variante, ni condición de routing.
- **`detectionPath` / `armEvidence`**: no hay camino de confirmación nuevo. Lo que se amplía es el
  vocabulario de `sessionOutcome`, y `ArbitratedByBt` llega a Firestore por el mismo
  `SessionEnded(sid, …, epilogue.outcome.serialized)` de siempre.
- **Web**: no toca límites del plan free, ni feature que `paparcar.com` nombre, ni hechos del build.

## Fuera de alcance (ticket aparte)

- El `REFUSED_STRATEGY` que **descarta** un trigger en vez de degradarlo al evaluador.
- BT como sensor + FGS residente (la «Decisión pendiente» de arriba).
- El spam de nudges: cada desconexión con enganche corto lanza un `showMarkParkingNudge`; con un
  dongle que parpadea son varios por trayecto (decisión del usuario: fuera de este ticket).
