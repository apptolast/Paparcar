# DET-A-BT-CONNECT-IS-A-DEPARTURE-001 · el Bluetooth toma el mando al conectar y entre conectar y desconectar no detecta nada

**Estado:** ✅ Done · mergeado en master el 14-09-2026 · ⏳ pendiente de campo

## Problema

La estrategia Bluetooth es la determinista —la que el producto llama "automática"— y **no publica ni
una plaza**. Lo dice su propio detector (`BluetoothParkingDetector:227`): *"nothing community-facing
is published at confirm time (spots publish at departure, with their own guards)"*. Y la salida no la
recoge nadie en ese carril: `BluetoothConnectionReceiver` en `ACTION_ACL_CONNECTED` sella
`BtConnectionStore`, marca el estado y llama a `startService`; `BluetoothDetectionService.handleConnected`
cancela el job de desconexión pendiente y se apaga.

### El hallazgo, que es más incómodo que "falta una señal"

Al conectar el coche emparejado pasan **tres** cosas a la vez, y ninguna detecta nada:

1. `EvaluateBtArbitrationUseCase` decide `VetoReturnToVehicle` si había una sesión del Coordinator en
   fase `Candidate` → el servicio aborta esa sesión. *BT manda sobre el Coordinator.*
2. `ParkingStrategyResolver.strategyFor` pasa a devolver `BLUETOOTH` — exige estar **CONECTADO**, no
   solo emparejado ([DET-BT-CONNECTED-NOT-PAIRED-001]).
3. Como consecuencia, `resolvePostDetectionLifecycle(…, strategy = BLUETOOTH)` = **`Stop`**: el
   centinela residente del Coordinator —que con el coche aparcado SÍ estaba vigilando, porque
   desconectado la estrategia es COORDINATOR— **se retira justo en el instante en que el coche va a
   salir.**

O sea: **el Bluetooth reclama la propiedad de la detección al conectar, hace que el vigilante que
había se marche, y después no vigila nada hasta el DISCONNECT.** Entre esos dos momentos —que es
exactamente el viaje— el único testigo de la salida es la geocerca de Play Services, sin ayuda
residente y sin nominador de baja latencia.

Y la geocerca es justo el testigo que falla en un móvil sin exención de batería: su motor solo
actualiza el estado DENTRO/FUERA cuando un fix llega al fused provider, y con el móvil quieto se
muere de hambre durante decenas de minutos. Eso es lo que se acaba de arreglar por el lado del
RELOJ en [DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001] (`488d6982`), pero un EXIT que no
llega no tiene hora que corregir.

### Datos de campo

Fernando (usuario real, Skoda emparejado): **6 de 43** pins en 8 días son `nudge_bt_no_ride`, y
**ninguno** de sus 43 pins tiene `detectionPath = "bt"`. El carril determinista no está colocando
pins ni liberando plazas; está preguntando.

## Doctrina

No hay invariante roto — hay uno **no ejercido**. Las reglas que el diseño tiene que respetar:

- **El evento NOMINA, solo el movimiento MEDIDO confirma.** Un CONNECT prueba **embarque**, no que el
  coche se moviera: te metes, enciendes la radio y te bajas. Así que el CONNECT **arma**; lo que
  confirma es conducción medida. Publicar con el CONNECT a secas sería el falso positivo del ticket.
- ⛔ **Las dos estrategias NUNCA se mezclan.** Esto **no** mete señales BT en el scoring del
  Coordinator ni arma el Coordinator con un trigger BT (el gate `coordinatorMayArm` lo rechazaría,
  y con razón). Es el carril BT ejerciendo la propiedad que ya reclama, y convergiendo en la
  **puerta común** de salida — igual que hoy converge en `ConfirmParkingUseCase` para la entrada.
- **Fallo asimétrico**: si no se mide conducción, silencio. Ni plaza, ni sesión cerrada, ni pregunta.
- **La decisión en `commonMain` puro**, el servicio solo I/O.

## Señales disponibles (ya se guardan)

- **`BtConnectionStore.recordConnected(ctx, vehicleId, atMs, originFix)`** — instante del embarque y
  fix cacheado de origen. Hoy solo lo leen el gate de identidad del safety net
  ([DET-BT-IDENTITY-GATE-001]) y `parkedSessionIsContradictedByBtConnect`.
- **`config.isBeyondPedestrianReach(distance, elapsed, fenceRadius, accuracy)`** — *"la única
  comprobación física detrás de [DET-RIDE-PROOF-001]: un evento del OS solo puede NOMINAR una
  salida; lo que la CONFIRMA es…"*. Literalmente el primitivo que hace falta.
- **La puerta común de salida**, ya con dos relojes separados desde `488d6982`:
  `DepartureDetectionWorker.buildRequest(geofenceId, triggerAtMs, departedAtMs, preconfirmed)`.

## Diseño

**El CONNECT arma una vigilancia de salida en el propio carril BT, simétrica de la que ya monta el
DISCONNECT.** Nada más entra en juego: la confirmación, la publicación y el cierre siguen siendo la
puerta común.

1. `BluetoothConnectionReceiver` pregunta si ese coche **retiene plaza** y pasa el `EXTRA_VEHICLE_ID`
   (que en un CONNECT no viajaba: nada aguas abajo necesitaba saber QUÉ coche, y buscar su
   aparcamiento sí).
   ⚠️ **La pregunta se hace AQUÍ, y no en el servicio como decía el primer borrador de esta ficha.**
   `startForegroundService` es una promesa de promocionar en 5 s o la app cae (la clase
   BUG-FGS-001), así que la forma de arrancar hay que elegirla ANTES de arrancar: con plaza →
   foreground; sin plaza → `startService`, y un FGS ahí sería un parpadeo de notificación comprado
   para nada. El receiver ya resuelve el vehículo con una lectura de Room; esta es la segunda.
   Una lectura fallida NO se lee como "no hay sesión" — erraría hacia renunciar a la vigilancia por
   un hipo de Room —, así que asume que sí y el servicio re-comprueba.
2. `BluetoothDetectionService.handleConnected` conserva **todo** lo que ya hacía (cancelar el job
   pendiente, acelerar el safety net) y, solo si hay plaza, promociona y lanza la vigilancia.
   Re-lee la sesión dentro del job: entre el receiver y aquí la geocerca puede haber cerrado la
   salida, y eso no es un error sino la carrera resolviéndose del otro lado.
3. **`BluetoothDepartureDetector`** observa la ubicación hasta que el veredicto puro dice que el
   coche se ha ido **medido**, y entonces despacha la puerta común con
   `departedAtMs = connectedAtMs` (el instante MEDIDO del embarque) y **`preconfirmed = false`**.
   Sin sello de CONNECT no arranca siquiera: una salida sin hora no publica nada aguas abajo
   ([DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001]), y sostener GPS para producir una
   respuesta inservible es gastar batería por nada.
4. Techo duro de tiempo. Si no se mide conducción, se retira en silencio: la sesión y la geocerca
   siguen donde estaban.

**Por qué `preconfirmed = false`** y no `true`, que sería lo intuitivo ("ya lo hemos medido"):
`preconfirmed` significa *"el viaje ya TERMINÓ, no vuelvas a medir velocidad porque vetarías una
salida real"*, y arrastra `DepartureProof.Deduced` → plaza **provisional** (12 min) y sesión
retenida. Aquí el viaje está EMPEZANDO: dejar que la escalera tome su propio fix fresco la hace
`Witnessed` → publicación con vida completa y coche liberado. La vigilancia BT no sustituye a la
escalera, le da **el momento bueno** para mirar.

**El veredicto, puro y en `domain/detection/`** con el resto de la política
([DET-VERDICT-NOT-PREDICATE-001]: se consulta en un sitio, no produce `detectionPath` propio y es
directamente testeable sin ceremonia de clase inyectada — igual que `freedSpotIsStillThere` y
`measuredDepartureAtMs`):

```kotlin
fun btDriveAwayIsMeasured(
    pin: GpsPoint, current: GpsPoint, connectedAtMs: Long,
    fenceRadiusMeters: Float, nowMs: Long, config: ParkingDetectionConfig,
): Boolean
```

⛔ **Mide DESPLAZAMIENTO desde el pin, no un sample de velocidad.** Un solo Doppler creíble es el
espejismo que ya plantó plazas fantasma ([DET-DRIVE-PROOF-001]: *"la corroboración es la track, no un
sample"*). Y exige **fix fresco**: la primera emisión del fused provider suele ser un last-known
cacheado, trampa documentada en el propio `ParkingDetectionConfig`.

**Lo que aporta de verdad**: un testigo del arranque que es **nuestro** y no depende de Play
Services. Si la geocerca hubiera ido a dispararse igual, no perdemos nada (la puerta es la misma y
`REPLACE` deduplica por `geofenceId`); si el motor de geocercas está famélico, esta es la única
vigilancia con GPS activo mirando el momento exacto en que el coche arranca.

## Criterio de éxito

- Embarcar y conducir → la plaza sale, fechada por el instante del CONNECT, aunque la geocerca no
  emita.
- Embarcar y NO conducir (radio, buscar algo, bajarse) → no sale nada, la sesión sobrevive intacta.
- Un CONNECT sin sesión aparcada de ese vehículo → comportamiento idéntico al de hoy, sin FGS.
- Si la geocerca sí dispara, no hay doble publicación.

## Consumidores auditados

| Sitio | Clasificación |
|---|---|
| `EvaluateBtArbitrationUseCase` (veredictos de CONNECT) | **exento** — corre antes y sin cambios; BT sigue mandando sobre el Coordinator |
| `ParkingStrategyResolver.strategyFor` | **exento** — la propiedad al conectar no cambia; lo que cambia es que ahora se ejerce |
| `resolvePostDetectionLifecycle` (→ `Stop` en BLUETOOTH) | **cubierto por convergencia** — el centinela sigue retirándose, pero ya no deja el hueco: esta vigilancia ocupa su sitio con mejor información |
| `ObserveDepartureWatchGapUseCase` | **cerrado** — su comentario afirmaba que un coche conectado *"está cubierto por el receptor ACL y no necesita vigilante residente"*. Era media verdad (cubría el aparcamiento, no la salida); ahora lo es entera, y el comentario lo dice |
| `ParkingSafetyNetWorker.SOURCE_BT_CONNECT` | **exento** — se mantiene; es preparación (resella ancla, cura valla), no observación |
| `DET-BT-IDENTITY-GATE-001` (`vehicleBtGated`) | **exento con razón** — esta vía no pasa por el safety net, y el falso positivo que ese gate cubre lo corta aquí el DISCONNECT (ver abajo) |
| `parkedSessionIsContradictedByBtConnect` | **exento** — lee el mismo sello para otra pregunta (si el pin está obsoleto) |
| `handleDisconnected` | **cerrado** — su `detectionJob?.cancel()` es ahora también el guard de esta vigilancia |

## Lo implementado (14-09-2026)

| Pieza | Dónde |
|---|---|
| `btDriveAwayIsMeasured(...)` — el veredicto, puro | `domain/detection/` |
| `BluetoothDepartureDetector` — la vigilancia, sin confirmar ni publicar nada | `androidMain/bluetooth/` |
| `EXTRA_VEHICLE_ID` + `EXTRA_WATCH_DEPARTURE` en el CONNECT, y `startForegroundService` **solo** si hay plaza | `BluetoothConnectionReceiver` |
| `handleConnected` conserva todo lo de hoy y añade la vigilancia | `BluetoothDetectionService` |
| `btDepartureWatchTimeoutMs = 5 min` (+ su `require`) | `ParkingDetectionConfig` |

**Sobre los tests.** La decisión está cubierta por `BtDriveAwayIsMeasuredTest` (6 tests: coche,
peatón rápido, embarcar sin conducir, fix cacheado lejano, fix anterior al embarque, valla ancha con
fix sucio). El detector en sí es orquestación y no lleva test propio — **misma división que su
gemelo**: `BluetoothParkingDetector` tampoco tiene uno porque su decisión vive en
`EvaluateBtParkUseCase`, que sí lo tiene. Se dice aquí en vez de dejarlo como hueco silencioso.

**Dev Catalog:** sin cambios (ni pantalla, ni estado, ni routing). **Strings:** ninguno.
**Provenance:** ningún `detectionPath` nuevo — la salida se publica por la puerta común, que estampa
lo que siempre estampa. Los diagnósticos ganan dos veredictos con `source = "bt"`
(`bt_departure_measured` / `bt_departure_never_drove`).

## Pendiente

- ⏳ **Campo.** Es la primera vez que este carril publica algo; el corpus de Fernando con los
  diagnósticos ya encendidos es el que debe decir si `btDepartureWatchTimeoutMs = 5 min` es el
  número correcto y cuánto tarda de media en medirse el arranque (`enterAgeMs` en los veredictos).
