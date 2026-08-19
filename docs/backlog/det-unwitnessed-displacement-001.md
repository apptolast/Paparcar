# DET-UNWITNESSED-DISPLACEMENT-001 · un desplazamiento que ningún testigo presenció no prueba un viaje

**Estado:** 🟡 IMPLEMENTADO 19-08 (sin commit — pendiente permiso) · rama
`bugfix/DET-UNWITNESSED-DISPLACEMENT-001-honest-close-witnessed-displacement` · worktree
`../Paparcar-unwitnessed-displacement` · 1228 tests verdes (+5) · mock compila · ⏳ APK + campo
(noche indoor sin FP + hop corto real que siga plantando zona/pin)

## Problema

Field 19-08 madrugada, Oppo (uid `fiyp…`), usuario DORMIDO en casa desde el pin real de La Bermeja 2
(00:58, `1195cc2c`, steps+egress 0.9). Dos falsos positivos encadenados del honest-close:

1. **FP1 Cantarranas-2 4, 05:26** (pin `e34e70ad`, `closed_approximate_pin`, rel 0.5). El GPS
   indoor nocturno se teletransportó ~950 m N con accuracy reportada "buena" (7 m, multipath).
   Tres sentry-wakes en 80 s acabaron en: casa (03:25:19 → fix basura a 1 km NE), casa
   (03:25:41), Cantarranas (03:26:12). El tercero abortó `false_enter` en 12 s y la escalera
   (traza `HONEST_CLOSE`, sesión `1787109972361`) concluyó `trip_proven`: pinDistance 923,7 m ·
   walkDistance 956,4 m · stepsDelta 36 vs requiredSteps 511 · liveness OK (36 ≥ 13) · acc 7 ≤ 50
   → **pin en el fix basura**. Nadie midió movimiento en toda la noche: 0 fixes de conducción,
   speed ≈ 0, AR mudo.
2. **FP2 Calle Góndola 7, 05:59** (pin `70a0f146`) — **cascada determinista de FP1**: la valla
   nueva en Cantarranas ve el móvil (en casa) 949 m fuera desde el segundo cero → GEOFENCE_EXIT
   inmediato (03:57, exitLoc = casa, `dep=self_observed`) → `aborted_no_movement` (sesión
   `1787111857151`: 25 fixes clavados en casa, 0 pasos) → honest-close con el seal FRESCO pero
   ENVENENADO (FP1 selló en el punto basura, `sealPoint = abortFix`) → walkDistance 949 m,
   delta 0 → `trip_proven` → **pin en la casa del usuario**.

Por qué no salvó ningún gate existente: `stale_seal` (2 h) no pudo morder porque el
`CureGeofence` del safety-net **resella el baseline en cada wake junto al coche**
(`ParkingSafetyNetWorker.kt:260`) — durmiendo al lado del pin el seal siempre está fresco. El
resello es correcto (el presupuesto ES fresco y same-origin); lo que falta es otra cosa.

## Doctrina violada

*El evento nomina, solo el movimiento MEDIDO confirma.* `trip_proven` infiere un viaje de pura
posición — un cluster de fixes de UN único wake — sin que ninguna fuente midiera movimiento. Y el
FP2 añade el corolario: un pin plantado por inferencia se vuelve la referencia de la siguiente
inferencia (la valla del FP es un nominador fabricado).

Es el flanco documentado y DIFERIDO en DET-TRIP-WITNESS-001 ("alternativa más ambiciosa: exigir
tránsito presenciado"). Ojo: la versión literal de esa alternativa (exigir movimiento medido EN la
sesión abortante) **mataría el caso legítimo fundacional** — en el hop de Camelias (14-07) el EXIT
llegó con el viaje ya terminado y la sesión solo vio a un peatón. El discriminador correcto no es
"la sesión midió movimiento", es otro (ver Diseño).

## Señales / datos disponibles

- La escalera ya recibe: seal (posición + edad), delta de pasos, kinemática de la sesión.
- Lo que NO recibe y el sistema SÍ tiene: **la última posición presenciada por un wake anterior
  independiente**. A las 03:25:52 el sistema midió el teléfono QUIETO en casa; 32 s después el
  abort fix afirmaba un punto a 950 m, también quieto. Velocidad puerta-a-puerta implícita:
  ~30 m/s (107 km/h) entre dos observaciones estacionarias en 32 s — físicamente imposible, y
  observable en el momento del cierre sin sensor nuevo.
- Trazas completas para replay: FP1 `1787109972361`, FP2 `1787111857151`, más los dos wakes
  previos (`1787109919392` casa→basura#1, `1787109941202` casa) — diagnostics `fiyp…`.
- Los replays legítimos ya fijados: Camelias D3 `1784056795594`, regreso D2 `1784081508556`,
  Glorieta 30-07 `1785426554477` (todos en `EvaluateHonestCloseUseCaseTest`).

## Diseño — gate de continuidad espacio-temporal (el SISTEMA, no el parche)

**Invariante, en un sitio:** *el fix del abort solo sostiene un `trip_proven` si es
espacio-temporalmente compatible con la última posición presenciada; dos testigos recientes que
se contradicen físicamente anulan el veredicto — ningún fix es pin-grade cuando los testigos
discrepan.*

En `EvaluateHonestCloseUseCase` (puro, replayable):

- Entradas nuevas: `lastWitnessedFix: GpsPoint?` + `lastWitnessedAtMs: Long?` — el último fix de
  un wake ANTERIOR (fin de la sesión previa, check del safety-net o seal, el más reciente).
- Gate nuevo (tras `session_measured_driving` — conducción medida en sesión ES tránsito
  presenciado y no pasa por aquí; antes de todo lo inferencial):

  ```
  distancia(lastWitnessedFix, abortFix) >
      lastWitnessedFix.accuracy + abortFix.accuracy +
      elapsedSeconds × honestCloseMaxImpliedTravelSpeedMps
  → KeepSilent, REASON_UNWITNESSED_DISPLACEMENT
  ```

- `honestCloseMaxImpliedTravelSpeedMps` (config, **15 m/s** = 54 km/h de media
  puerta-a-puerta entre dos momentos estacionarios, incluyendo arrancar y aparcar): FP1 exigía
  30 m/s → gate dispara; un hop real de 950 m tarda ≥ 90 s → ~10 m/s → pasa. La fórmula se
  auto-limita: con testigos viejos (Camelias: horas) el término temporal lo cubre todo y el gate
  no constriñe nada — no hace falta cap de frescura.
- El testigo lo persiste la capa Android (prefs, patrón ANCHOR-PERSIST-001 — debe sobrevivir a
  muerte de proceso): al cerrar cada sesión de detección y en cada check del safety-net, sitios
  que ya tienen el fix en la mano. Decisión en use case puro; I/O en el servicio [DET-INTAKE-001].
- Telemetría: `REASON_UNWITNESSED_DISPLACEMENT` viaja por `HonestCloseVerdict.reason` al evento
  `HONEST_CLOSE` (columna string existente, sin cambio de wire). Añadir al payload la distancia
  al testigo para auditar el umbral en campo.

**Por qué muere FP2 sin gate propio:** con FP1 silenciado no hay pin en Cantarranas → no hay
valla → no hay EXIT a las 03:57 → no hay abort → no hay cierre. La cascada requería un seal
plantado donde el cuerpo nunca estuvo, y eso solo lo produce el propio defecto de FP1 (el save
sella en `abortFix`): un pin manual remoto lo protege `user_asserted_pin` (1.0) y una zona/pin
aproximados legítimos sellan donde SÍ estaba el cuerpo (siguiente wake → `walk_too_short`).
Replay de FP2 en aislamiento documentado como cascada, no como gate.

**Alternativas descartadas:**
- *Exigir movimiento medido en la sesión abortante* — mata Camelias (legítimo): el viaje terminó
  antes de armar. El honest-close existe precisamente para ese caso.
- *Degradar a ZONA cuando el tránsito no fue presenciado* — seguiría plantando un artefacto falso
  a 950 m; un fix refutado por un testigo más fresco no es "impreciso", es falso.

## Criterio de éxito

Una noche de multipath indoor con el móvil quieto no puede plantar NINGÚN artefacto: la escalera
calla (`unwitnessed_displacement`) y el pin real (La Bermeja) sobrevive hasta el viaje de verdad
de la mañana. Los replays legítimos (Camelias → artefacto, D2 → silencio walk, Glorieta →
stale_seal) siguen verdes.

### Tests (✅ hechos, 1228 verdes)
- ✅ Replay FP1 (Evaluate + Run end-to-end): teleport con testigo (casa, 32 s) →
  `KeepSilent/unwitnessed_displacement`, cero saves, cero nudge, pin de La Bermeja intacto —
  el assert de "nada guardado" ES el assert de cascada: sin valla en el espejismo no existe el
  estímulo de FP2.
- ✅ Mismo teleport con testigo de 2 h → gate transparente → `trip_proven` (protege la forma
  Camelias/D2), con `witnessDistanceMeters`/`witnessAgeMs` estampados para auditar el umbral.
- ✅ Frontera: 523 m vs allowance 500 m → refuta; 479 m → pasa y el presupuesto testifica.
- ✅ Testigo null (instalación fresca / legacy) → transparente (todos los replays existentes).
- ✅ Regresión: Camelias, D2, Glorieta (stale_seal), frozen-counter, walk-floor, step-budget-origin,
  measured-driving — todas verdes sin cambios.

## Consumidores auditados

¿Quién más confía en el fix de UN solo wake como pin-grade?
- `EvaluateHonestCloseUseCase` → **cerrado por este ticket** (gate de coherencia).
- `EvaluateSafetyNetCheckUseCase` (far+evidence dispatch / `backfillBounded`) — **exento con
  razón**: el dispatch exige evidencia de VEHÍCULO (AR boarding / BT) además del fix, y el propio
  worker ahora refresca el testigo en cada check, acotando la ventana de un espejismo. Un teleport
  sin evidencia de vehículo clasifica `None` por diseño. Si el campo enseña lo contrario, el
  testigo ya está persistido y el evaluador puede recibirlo (follow-up, no ahora — sistemas, no
  parches preventivos).
- `BluetoothDetectionStrategy` (fix post-disconnect + ≥ 30 m) — **exento**: el disconnect es
  hardware determinista de la MAC del coche; el fix solo refina dónde. Carril separado por doctrina.
- Geofence EXIT `self_observed` (un teleport puede ARMAR, como el EXIT de las 03:57) — **cubierto
  por convergencia**: armar es nominar; toda confirmación de esa sesión exige conducción medida, y
  su abort converge en este gate.
- `EvaluateParkingDecisionUseCase` egress cinemático — **exento**: exige secuencia de fixes
  consecutivos en banda de calidad; un salto no es una banda (DET-KINEMATIC-EGRESS-001).
- Inferencia de ruta para pines aproximados (FP1 llevaba `routeInferredSpans` de un viaje
  inexistente) — **muere con el gate**; sin cambio propio.

## Ficheros tocados
- `EvaluateHonestCloseUseCase.kt` — gate + `REASON_UNWITNESSED_DISPLACEMENT` + campos de testigo
  en `HonestCloseVerdict` + KDoc.
- `ParkingDetectionConfig.kt` — `honestCloseMaxImpliedTravelSpeedMps = 15f` + require.
- `RunHonestCloseUseCase.kt` — pass-through de testigo.
- `CoordinatorDetectionService.kt` — `readLastWitnessedFix()` (edad a now, negativo→null) +
  `stampLastWitnessedFix()` en el epílogo del intake DESPUÉS del honest close (una sesión nunca
  atestigua su propio abort) + campos en el evento.
- `ParkingSafetyNetWorker.kt` — keys `last_witnessed_*` + refresco del testigo en cada check.
- `DetectionEvent.kt` / `DetectionEventDto.kt` — `witnessDistanceMeters` (columna nueva) +
  `witnessAgeMs` viajando en `sessionAgeMs` (reuso deliberado, patrón Sentry).
- Tests: `EvaluateHonestCloseUseCaseTest` (+4) · `RunHonestCloseUseCaseTest` (+1).
- `docs/detection/PARKING-DETECTION.md` — entrada en Sección 2.
