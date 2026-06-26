# HANDOFF — Refactor definitivo del flujo de detección de aparcamiento

> **Para Claude Code.** Este documento es el resultado de una sesión de razonamiento de
> arquitectura sobre el núcleo de detección de Paparcar. Resume el diagnóstico, las decisiones
> ya tomadas y el plan de refactor. **No actúes todavía**: lee esto, cruza cada afirmación con
> el código real del repo (tienes acceso completo que la sesión anterior no tenía), valida o
> refuta cada punto, y propón el plan de ejecución definitivo antes de tocar una línea.
>
> **Para el humano (Gonzalo).** Esto es la memoria de la conversación. Acompáñalo del esquema
> visual (`esquema-refactor.html` / `.svg`) y de las instrucciones de traspaso al final.

---

## 0. Por qué esto importa

La detección de aparcamiento es **la pieza central** de Paparcar: si publica plazas fantasma,
la red comunitaria pierde la confianza, que es su único activo. El refactor que sigue no es
cosmético — corrige un falso positivo real reproducido en campo (viaje en Bolt en Praga que
generó una plaza inexistente) y reordena la arquitectura para que ese tipo de error sea
imposible por construcción, no por parche.

---

## 1. Principio rector (leer antes que nada)

**Fallo asimétrico.** No existe sensor ni combinación que confirme "he dejado de conducir" al
100%. El objetivo no es certeza, es asimetría de error:

- **Falso negativo** (no detecto una plaza real) → coste ≈ 0, invisible.
- **Falso positivo** (publico plaza fantasma) → coste alto, rompe la confianza de la red.

Toda regla de confirmación se decide con: *¿qué señal es físicamente imposible de producir en
una parada de tráfico?* → el **egreso** (la persona sale del coche y se aleja), no la quietud.

**Corolario / principio unificador:** la fuerza de una confirmación viene de la **conjunción de
señales independientes**, nunca de una señal sola. Pasos solos = débil; pasos AND desplazamiento
= fuerte. BT connect solo = débil; BT connect AND geocerca = fuerte. Armado manual solo = riesgo;
armado AND gate de corroboración = seguro.

---

## 2. Diagnóstico (validar contra el código)

### 2.1 El sistema tiene dos mitades de calidad desigual
- **DEPARTURE — bien diseñado.** `DetectParkingDepartureUseCase` corrobora 3+ señales (sesión
  activa + match de geofence ID + `IN_VEHICLE_ENTER` dentro de `vehicleEnterWindowMs` + velocidad)
  y devuelve `Confirmed / Rejected / Inconclusive`, con retry ante la duda. **Es el modelo a
  imitar.**
- **DETECCIÓN DE APARCAMIENTO — la mitad frágil.** `ParkingDetectionCoordinator` (~816 líneas)
  confirma con **una sola señal sin corroborar** y acumula 20+ IDs de bug
  (`BUG-COORD-105/112/115`, `BUG-OPPO-LATE-CONFIRM`, `BUG-FALSE-ENTER-WALKING`, `BUG-SCOOTER-001`,
  `BUG-GARAGE-COLA-001`, etc.). Aquí vive el FP de Praga.

> **Reframe del refactor:** no es "arreglar la detección", es **subir la mitad de detección al
> estándar de corroboración multi-señal que el departure ya cumple.**

### 2.2 Causa raíz del FP de Praga (confirmada en código)
1. AR emite `IN_VEHICLE_EXIT` espurio a mitad de trayecto → `vehicleExitConfirmed = true`.
2. En el atasco/semáforo `stoppedSince != null`; el `stepJob` cuenta **cada vibración** como
   paso (`shouldCount = !hasEverReachedDrivingSpeed || stoppedSince != null`).
3. `stepCount` alcanza `minStepsToConfirm` (= 8; el comentario de `falseEnterAbortSteps` ya
   anticipaba este caso patológico: *"phone in pocket bouncing in stop-and-go traffic"*).
4. **Path 8** (`vehicleExitConfirmed && stepCount >= minStepsToConfirm`) confirma plaza,
   saltándose slow-path y STILL. **Ningún gate de desplazamiento.** → plaza fantasma.

`evaluateCandidatePhase` tiene el mismo agujero: `hasStepsProof` mira `stepCount`, no distancia.

### 2.3 Bug secundario: notificación FGS colgada
- **Sospechoso primario:** `onStartCommand` devuelve `START_STICKY`; tras OEM-kill Android
  resucita con `intent == null`, tratado como `ACTION_START_TRACKING` → promueve FGS sin sesión.
- **Sospechoso secundario:** relevo de jobs — el `finally` solo limpia FGS si
  `detectionJob === thisJob`; si el job entrante aborta temprano, el FGS queda huérfano.
- `ForegroundServiceController` en sí está bien (`stopForegroundAndSelf` correcto). El fallo está
  en quién lo invoca y cuándo.
- ⚠ **Pendiente de leer para cerrarlo:** `GeofenceBroadcastReceiver`, `ParkingConfirmationReceiver`,
  `ActivityTransitionReceiver`. Claude Code debe revisarlos.

### 2.4 Infraestructura que YA existe (no reinventar)
- `ParkingStrategyResolver` **completo y correcto**: invariante BT-supersedes
  (`ARCH-MONITORING-002`), opt-out `SCOOTER/BIKE` → `NONE`, `BluetoothScanner` ya inyectado.
  Devuelve `NONE | BLUETOOTH | COORDINATOR`.
- `DepartureEventBus`, `vehicleEnterWindowMs` (30 min), `minimumDepartureSpeedKmh` ya en config.
- La ruta determinista BT está cableada a nivel de selección; **falta el receiver de
  BT-disconnect**.

---

## 3. Decisiones de diseño ya tomadas (D-1 … D-5)

### D-1 · `ParkingDecision` + separar decisión de confianza
- Nuevo sealed `ParkingDecision { Confirmed / Rejected / Inconclusive }`, espejo de
  `DepartureDecision`. `Inconclusive` = mantener candidato abierto y seguir; expira → descarta.
- **Decisión** = gate de corroboración (2 señales fuertes que concuerdan), booleano. NO score
  aditivo de débiles.
- **Confianza** = `Float` metadato de salida en `UserParking`:
  `confidence = reliabilityOfPath × gpsQualityFactor`
  - manual = 1.0 · BT disconnect = 0.95 · pasos+desplazamiento = 0.90 · AR-exit+desplazamiento = 0.75
  - gpsQualityFactor = 1.0 si `accuracy ≤ minGpsAccuracyMeters`, degradando por encima.
- `CalculateParkingConfidenceUseCase` se **reconvierte** (no se borra): de sumar señales a mapear
  ruta+GPS → confianza.
- **Señales degradadas a NO-decisoras:** STILL crudo, dwell-solo, GPS-low-speed-solo. Solo abren
  candidato; jamás confirman.

### D-2 · El gate de desplazamiento (fix inmediato del FP)
- **No reusar `bestStopLocation` como ancla** (se refina durante `initialStopWindowMs` = 30 s, y
  los 8 pasos llegan en ~5-8 s → el ancla aún se mueve con el usuario).
- Nuevo campo inmutable `egressAnchor`, capturado UNA vez en la transición a parado
  (`stoppedSince` null→no-null), limpiado al reanudar marcha.
- Helper:
  ```kotlin
  private fun hasEgressDisplacement(state: ParkingDetectionState, current: GpsPoint): Boolean {
      val anchor = state.egressAnchor ?: return false
      val d = haversineMeters(anchor.latitude, anchor.longitude, current.latitude, current.longitude)
      return d >= config.minEgressDisplacementMeters
  }
  ```
- Aplicar en Path 8 y en `hasStepsProof` (AND con el `stepCount`).
- Config nueva: `minEgressDisplacementMeters = 18f` (por encima de `minGpsAccuracyMeters = 15`),
  con su `require(minEgressDisplacementMeters > minGpsAccuracyMeters)` en el `init`.

### D-3 · Refactor de clase: extraer la decisión a función PURA
```kotlin
class EvaluateParkingDecisionUseCase(private val config: ParkingDetectionConfig) {
    operator fun invoke(state: ParkingDetectionState, location: GpsPoint): ParkingDecision
}
```
- El `collect` del Coordinator queda en: actualizar estado → `when (evaluate(...))` →
  Confirmed dispara `runConfirm`, Rejected cierra, Inconclusive sigue. Los 9 paths y los 20+
  `BUG-COORD-*` colapsan en el orden interno de UNA función testeable.
- **Beneficio crítico:** una función pura se testea replayando una traza. El log remoto (§4) se
  vuelve *fixture de test* — graba la secuencia de Praga, pásala por la función, afirma
  `Rejected`. Reproduces el bug sin dispositivo ni Android Studio.

### D-4 · Ruta BT: geocerca como corroborador, NO como gate duro
- BT *connect* = trigger de departure; BT *disconnect* = trigger de aparcamiento.
- BT connect + match de última geocerca = departure de alta confianza (filtra connects espurios).
- **⚠ El match de geocerca NO puede ser obligatorio:** en garaje subterráneo el GPS está frío →
  el match falla justo donde el BT es insustituible. Jerárquico, no conjuntivo: BT disconnect
  confirma por sí mismo (hecho físico); la geocerca sube confianza cuando está disponible.
- No liberar plaza en el connect: liberar cuando el viaje arranca de verdad (velocidad
  sostenida). "Esperar a la salida real."
- **Falta implementar:** el receiver de BT-disconnect.

### D-5 · Armado por departure + cold-start (armar ≠ confirmar)
- **Armado por departure, no por AR `IN_VEHICLE_ENTER`.** Flujo objetivo: existe `UserParking`
  activa → `GeofenceBroadcastReceiver` recibe `GEOFENCE_EXIT` → `DetectParkingDepartureUseCase`
  decide → si `Confirmed`, libera plaza Y arma la detección del próximo aparcamiento. Sin plaza
  previa → sin geofence → sin departure → sin armado. **Esto mata el caso Praga por construcción.**
- **Cold-start:** cuando la máquina cae fuera de PARKED sin ubicación conocida (primer uso, o
  tras liberar sin nuevo aparcamiento), modal/dialog **no bloqueante** con 2 opciones: aparcar
  ahora / estoy conduciendo (armar detección).
- **Regla de oro:** armar manualmente NO implica confirmar manualmente. "Estoy conduciendo" solo
  pone la máquina en DRIVING; la confirmación sigue pasando por el gate de corroboración. Si no
  conducía → nunca hay velocidad+egreso → expira en Inconclusive → descarta. FP acotado.

---

## 4. Habilitante transversal: log de eventos remoto
Bloqueo actual: no se puede depurar con Android Studio en viaje. Crear una colección de debug en
Firestore (tras flag) que registre con timestamp+coords: transiciones AR, eventos de geofence,
BT connect/disconnect, eventos de paso, apertura/descarte/confirmación de candidato, y la ruta
que confirmó. **Doble función:** diagnóstico en campo + fixture para tests del
`EvaluateParkingDecisionUseCase`.

---

## 5. Máquina de estados objetivo

```
            departure CONFIRMADO (GEOFENCE_EXIT validado | BT connect+movimiento)
   PARKED ───────────────────────────────────────────────────────────▶ DRIVING
     ▲                                                                     │
     │                                                                     │ stop (speed < umbral)
     │  ParkingDecision.Confirmed:                                         │ → captura egressAnchor
     │   • BT disconnect, ó                                                ▼
     │   • pasos AND desplazamiento ≥ minEgressDisplacementMeters     CANDIDATE
     │                                                                     │
     └─────────────────────────────────────────────────────────────  Inconclusive: seguir
                                  ▲                                        │
                        re-seed manual                          vuelve IN_VEHICLE / speed↑
                     (cold-start, armar≠confirmar)                        │  → Rejected: descartar
                                                                          ▼
                                                                     (candidato muere)
```

---

## 6. Plan de implementación por fases

### Fase A — Parar el sangrado (días)
- **A1.** `egressAnchor` + gate de desplazamiento en Path 8 y `hasStepsProof` (D-2).
- **A2.** `minEgressDisplacementMeters = 18f` + `require` en `ParkingDetectionConfig`.
- **A3.** Log de eventos remoto a Firestore tras flag de debug (§4).
- **A4.** Mitigación `START_STICKY`/intent-nulo: no promover FGS sin sesión válida (§2.3).

### Fase B — Endurecer (semana 1-2)
- **B1.** Revert automático post-confirm si reaparece velocidad de vehículo sostenida.
- **B2.** Degradar slow-path 5 min y STILL a "abrir candidato", nunca confirmar (D-1).
- **B3.** Watchdog de FGS huérfano (§2.3 secundario).

### Fase C — Reestructurar (semana 3-4)
- **C1.** Extraer `EvaluateParkingDecisionUseCase` puro (D-3). Coordinator → orquestador fino.
- **C2.** `ParkingDecision` sealed + `confidence` como metadato de salida (D-1).
- **C3.** Reconvertir `CalculateParkingConfidenceUseCase` a mapeo ruta+GPS → confianza.
- **C4.** Tests de la función pura replayando trazas (incluida la de Praga).

### Fase D — Trigger por departure + determinismo BT (alineado con roadmap BT)
- **D1.** Armar detección por departure confirmado, no por AR ENTER (D-5).
- **D2.** Cold-start modal armar≠confirmar (D-5).
- **D3.** Receiver de BT-disconnect; ruta BLUETOOTH determinista completa (D-4).
- **D4.** Geocerca como corroborador de confianza en la ruta BT, no gate duro.

---

## 7. Lo que Claude Code debe validar/refutar con el repo entero

La sesión anterior solo leyó: `ParkingDetectionCoordinator`, `ParkingDetectionService`,
`ForegroundServiceController`, `HandleVehicleTransitionUseCase`, `ParkingDetectionConfig`,
`DetectParkingDepartureUseCase`, `ParkingStrategyResolver`. **Hipótesis a confirmar leyendo el
resto:**

1. ¿`Path 8` confirma de verdad con `stepCount` solo? (leer el cuerpo completo del `collect`,
   que quedó truncado en la sesión anterior).
2. ¿`GeofenceBroadcastReceiver` y `ParkingConfirmationReceiver` reinyectan intents que disparan
   el FGS fantasma? (bug 2).
3. ¿`ConfirmationPhase` (sealed) encaja con el `ParkingDecision` propuesto o hay solapamiento?
4. ¿`ObserveAdaptiveLocationUseCase` da cadencia GPS suficiente para que el gate de
   desplazamiento tenga fixes a tiempo? (riesgo: si el GPS muestrea lento, el egreso tarda en
   verse).
5. ¿`CalculateParkingConfidenceUseCase` tiene consumidores que rompan al reconvertirlo?
6. ¿El armado por departure rompe algún flujo existente que dependa del AR ENTER directo?

**Orden sugerido de trabajo para Claude Code:** validar §7 → confirmar/ajustar Fase A → implementar
A1+A2 con tests → A3 → resto por fases. **No saltar a Fase C/D sin la A cerrada y probada.**

---

## 8. Notas de estilo del proyecto (respetar)
- Constantes mágicas en `companion object` a nivel de clase.
- Toda invariante entre umbrales va como `require(...)` en el `init` de la config.
- KMP: lógica de decisión en `commonMain`; sensores/receivers en `androidMain`.
- Multiidioma: strings nuevos en EN base + ES (P0).

---

## 9. Validación contra el repo (Claude Code, 2026-06-25)

Resultado de cruzar cada hipótesis de §7 con el código real. El plan de ejecución vive en
`docs/detection/REFACTOR-PLAN.md`.

### 9.1 Veredicto de §7
1. **Path 8 confirma con `stepCount` solo → CONFIRMADO (literal).** `ParkingDetectionCoordinator.kt:411`.
   Cadena de causa raíz confirmada: AR EXIT → `onVehicleExit()` (`HandleVehicleTransitionUseCase:74`);
   `shouldCount = !hasEverReachedDrivingSpeed || stoppedSince != null` cuenta pasos en atasco.
   `evaluateCandidatePhase.hasStepsProof` tenía el mismo agujero.
2. **Receivers reinyectan FGS → el doc apunta mal.** `GeofenceBroadcastReceiver` NO toca el FGS de
   detección (solo `GeofenceEventBus` + `DepartureDetectionWorker` por WorkManager). El sospechoso
   primario de §2.3 (`START_STICKY` + intent nulo) SÍ está confirmado. **Camino exacto del huérfano
   hallado:** `IN_VEHICLE_ENTER` duplicado con `isVehicleIn=true` → `onStartCommand` promueve FGS
   (línea 67) → `HandleVehicleTransitionUseCase` devuelve `Ignore` → nadie llama `stopIfIdle` → FGS
   colgado. Causa de diseño: se promueve el FGS antes de saber si hay trabajo.
3. **`ConfirmationPhase` vs `ParkingDecision` → no chocan, pero el doc subestima.** Son ortogonales;
   `ParkingDecision` envuelve a la fase, no la reemplaza. La fase sigue siendo estado de entrada de
   la función pura.
4. **Cadencia GPS suficiente → SÍ.** `AndroidLocationDataSourceImpl`: HIGH_ACCURACY pide 5 s, mínimo
   2 s → cadencia real ~2–5 s. Da 5–8 fixes durante el egreso de 18 m. El comentario "2 s" de
   `ParkingDetectionConfig:88` estaba desactualizado (corregido en DET-A).
5. **Consumidores de `CalculateParkingConfidenceUseCase` → solo Coordinator + DI + tests.** Reconvertir es bajo riesgo.
6. **Armar por departure rompe el flujo AR-ENTER → CONFIRMADO, es el cambio más invasivo.** Hay que
   conservar `DepartureEventBus.onVehicleEntered` y cubrir el arranque en frío con UI.

### 9.2 Correcciones de fondo al documento
- **La ruta Bluetooth determinista YA está implementada.** El doc dice tres veces que "falta el
  receiver de BT-disconnect" — es **falso**. Existen y están completos `BluetoothConnectionReceiver`
  (connect+disconnect), `BluetoothDetectionService` (FGS + race-guards) y `BluetoothParkingDetector`
  (debounce → GPS fix → walk ≥30 m → confirm 0.95). D-4 baja de "implementar receiver" a "añadir
  corroboración por geocerca + política de liberación en el connect".
- **El comentario de cadencia GPS "2 s interval"** en la config estaba mal (real ~2–5 s).

### 9.3 Aclaraciones de plan acordadas con el usuario
- Trigger de salida = **GEOFENCE_EXIT** (cerrado). El **modal/affordance de UI** para registrar un
  aparcamiento cuando no hay plaza ni detección activa es **tarea independiente del trigger**
  (Fase G-02): su función es crear el `UserParking` + geocerca que habilita la salida por geocerca.
- D-5 se parte en `DET-G-01` (motor, trigger) y `DET-G-02` (UI), desacoplados.
- Log remoto gateado por **Remote Config**; renombrado **tras Fase D**; FGS restart **no promueve sin sesión**.
