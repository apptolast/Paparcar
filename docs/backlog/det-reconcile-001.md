# DET-RECONCILE-001 — Reconciliación de estado del aparcamiento

**Estado**: en implementación (rama `feature/DET-RECONCILE-001-parked-state-reconcile`)
**Origen**: field-test 2026-07-06 — ambos móviles perdieron aparcamientos reales pese a que cada capa "funcionó según diseño".

## El fallo estructural que esto corrige

El sistema actual es **edge-triggered**: cada transición (salir, aparcar) depende de que UN evento
llegue a tiempo (geofence EXIT, AR, SIGMOTION). El campo ha medido que **ningún** productor de
eventos es puntual:

| Señal | Medida de campo (06-07-2026) |
|---|---|
| Geofence EXIT | Redmi: entrega a 139 m (segundos). Oppo: a 789 m y a 986 m (**minutos** tarde; un viaje de 2 min cabe entero en la latencia). Re-entregas stale a 4,9 km tras revivir el proceso. |
| Activity Recognition | Se saltó ENTERO un viaje de 2 min; lags 21 s–5 min; re-entregas stale en MIUI. |
| Step detector (streaming) | Oppo fiable (154 pasos); Redmi mudo con el listener registrado (0 pasos en 17 min andando). |
| SIGMOTION | **Cero rastro de haberse armado o disparado jamás** (era además invisible en logs — DET-FORENSICS-001). |
| WorkManager periódico 15 min | **Corrió todo el día en ambos móviles**, offline incluido. El único primitivo fiable. |
| Fix one-shot activo | Fiable siempre que corre algo que lo pida. |

Cuando el evento llega tarde o no llega, el fallo es **silencioso y permanente**: no hay ninguna
pieza cuyo trabajo sea mirar el estado y decir "esto ya no cuadra con la realidad".

## El sistema: reconciliación level-triggered

El estado persistido ("tu coche está aparcado en X") se **re-deriva periódicamente de observables
que no dependen de GmsCore**. Los eventos pasan de dueños de la lógica a meros aceleradores del
mismo reconcile. Un solo cerebro; todos los despertares (periódico 15 min, EXIT, SIGMOTION,
app-start, detection-end) desembocan en la misma evaluación.

### El discriminador nuevo: presupuesto de pasos (StepBudget)

`TYPE_STEP_COUNTER` es un contador **hardware, acumulativo desde boot, mantenido por el
sensor-hub aunque el proceso y la CPU duerman**. Leerlo cuesta un registro one-shot al despertar.

En cada reconcile de una sesión aparcada, con ancla de posición fresca (fix previo DENTRO de la
geocerca, ya persistida a disco — ANCHOR-PERSIST-001):

- `D` = distancia del fix actual al coche; `ΔS` = contador ahora − contador en el ancla.
- Andar `D` metros cuesta ≈ `D / 0.75` pasos. Si `ΔS` es una fracción pequeña de eso
  (`< D/0.75 × WALK_FRACTION`), el usuario **no llegó andando** → llegó en vehículo → y como el
  movimiento empezó EN su coche (ancla), es su coche → **salida confirmada**, aunque ya esté
  aparcado y quieto en el destino.
- Si `ΔS` es compatible con andar → estado normal "aparcado y me fui a pie" → **silencio**
  (se conserva el no-nag de SAFETYNET-STATIONARY-001, que era correcto; lo roto era no tener
  ningún otro camino de recuperación).
- Contador no disponible/mudo → fallback físico: `D/Δt` sostenido por encima de velocidad de
  andar rápido → vehículo; ambiguo → prompt.

Esto resuelve **determinísticamente y sin nag** el caso Oppo del 06-07 (EXIT entregado post-viaje):
al siguiente tick de 15 min o app-start, D=986 m, ΔS≈10 pasos → salida en coche → plaza liberada y
posición actual candidata a aparcamiento nuevo. Latencia acotada por el periódico, no por GmsCore.

Riesgo aceptado (mismo sobre que la geocerca EXIT): un taxi/acompañante que te recoge EXACTAMENTE
en tu coche dentro de la ventana del ancla libera en falso. Andar hasta una parada de bus deja
pasos → no dispara.

### Decisiones de política (asimetría de costes)

1. **Prompt de confirmación al timeout → GUARDAR, no abortar.** El Redmi perdió un aparcamiento
   real (19:15) porque nadie tocó una notificación en 15 min. Guardar tu propio coche con
   fiabilidad baja cuesta poco (se corrige con un tap); tirarlo mata la promesa del producto.
   La sesión guardada así NO publica nada comunitario.
2. **Publicación de plaza solo si la salida es fresca.** Una salida procesada horas tarde
   (Redmi sin datos: worker en cola 5 h) limpia la sesión pero NO publica una plaza fantasma.
   Telemetría explícita del porqué.
3. **Preconfirmado**: cuando el reconcile ya decidió con ancla+pasos, el DepartureWorker no
   re-decide por velocidad instantánea (estaría parado en el destino y daría Inconclusive) —
   va directo al procesado con el gate de frescura.

### Qué NO cambia

- El camino rápido (EXIT puntual → coordinator vivo → steps+egress) sigue siendo el principal;
  cuando funciona (Redmi 18:56, Oppo 19:01) es el mejor.
- El listón alto para publicar plaza comunitaria.
- La cura de geocerca + ancla en cada tick (es la que hace posible el step-budget).

## Piezas

| Pieza | Dónde |
|---|---|
| `StepCounterSource` (puerto) + `AndroidStepCounterSource` (TYPE_STEP_COUNTER one-shot) | domain / androidMain |
| Step-budget + recovery lejos+parado en `EvaluateSafetyNetCheckUseCase` | domain (puro, testeado) |
| Ancla guarda también el contador de pasos | `ParkingSafetyNetWorker` prefs |
| Flag `preconfirmed` en `DepartureDetectionWorker`/`RunDepartureCheckUseCase` | androidMain/domain |
| Gate de frescura de publicación en el procesado de salida | domain |
| Guardar-al-timeout del prompt (fiabilidad baja) | `CoordinatorParkingDetector` |

## Deuda que este sistema deja explícita

- SIGMOTION queda como acelerador opcional; con DET-FORENSICS-001 ahora es observable — si el
  próximo field-test muestra que arma y dispara, adelanta el reconcile de 15 min a segundos.
- Pasos mudos del Redmi con listener vivo (06-07): perseguir por separado; el step-budget usa el
  COUNTER (otro sensor), medir si también se congela.
- Latencia EXIT del Oppo: no es arreglable por nosotros; el sistema ya no depende de ella.

## Reparaciones tras el field-test 2026-07-07 [DET-RETURN-ANCHOR-001]

El primer field-test del sistema completo (ambos móviles) validó la lógica de decisión — dos
ciclos autónomos completos de libro, y el viaje corto de 576 m resuelto en el Redmi (fall-through
publicó la plaza vieja + guardar-al-timeout marcó la nueva en el sitio exacto). Todos los fallos
del día fueron **primitivos de entrada podridos**, no decisiones:

1. **Fix cacheado** — el fused provider sirvió la misma coordenada con speed=0 durante 4 min en
   pleno viaje (Redmi 13:56) y "dentro de la geocerca" con el coche ya ido (Oppo 12:14, que además
   envenenó el ancla). → `GetOneLocationUseCase(maxAgeMs)`: toda decisión (safety-net, departure
   check, pre-arm del service) exige `freshFixMaxAgeMs` (30 s); un fix viejo se descarta y se
   espera uno vivo o se devuelve null. Ningún dato gana a dato viejo.
2. **Par ancla desparejado** — la cura refrescaba la HORA siempre pero el punto-cero de pasos solo
   si la lectura no hacía TIMEOUT → delta con pasos pre-ancla (Oppo 12:17: 365 pasos fantasma
   vetaron un veredicto real que con el par coherente eran ~30). → par atómico: sin lectura, se
   BORRA el punto-cero (delta=null → fallback de física, que solo necesita la hora).
3. **Contador mudo / lento** — Redmi devuelve 0 SIEMPRE (un delta 0 andando = falso "conducido");
   Oppo hacía TIMEOUT a los 5 s. → 0 se trata como desconocido; presupuesto de lectura a 12 s.
4. **La vuelta al coche horas después** (el agujero del día: plaza de la playa nunca liberada en
   ninguno de los dos): ancla caducada (30 min) + fence EXIT envenenada OUTSIDE por la salida
   andando + re-entrada no observada en Doze. → **geocerca gemela ENTER** sobre la misma región
   (`enter_<id>`, PendingIntent a `GeofenceEnterReceiver` → check gated): volver andando al coche
   re-sella ancla+pasos y cura el estado de la fence; la salida real vuelve a tener EXIT, y si
   llega tarde, el siguiente despertar tiene ancla fresca para el step-budget. El ENTER jamás
   arma nada ni arranca FGS.

## DET-EXIT-TRUST-001 — el EXIT rancio pierde autoridad (incidente 2026-07-08 04:16)

Con la sesión zombi de la tarde anterior aún activa, ColorOS entregó su GEOFENCE_EXIT pendiente a
las 04:16 **a 4 km de la valla**, con el usuario durmiendo en casa. Un único fix degradado
(acc=100 m) reportó 21,6 km/h con el móvil inmóvil → el departure worker lo confirmó: plaza
fantasma publicada 7 h tarde, semilla `hasEverReachedDrivingSpeed` en el coordinator, prompt a las
04:22 (despertó al usuario) y guardar-al-timeout de un parking falso a las 04:41 en su casa (el
coche real está a ~200 m). Tres cierres de sistema (commits 07166519/df3ea475/f33f5226):

1. **Una sola definición de evidencia de velocidad** — `ParkingDetectionConfig.isCredibleDrivingSpeed`
   (velocidad Y precisión). El veredicto de salida tenía su copia SIN precisión; ahora los 3 sitios
   (pre-arm, veredicto, evaluador) llaman a la misma regla.
2. **El EXIT entregado lejos es arqueología, no evento** — en el borde de la valla conserva el
   camino rápido (probado en campo); más allá del umbral far (300 m) pierde la premisa de confianza
   ("cruzaste el borde de TU valla") y se convierte en un despertar más del evaluador, que exige
   ancla + prueba de viaje. Sin armado del coordinator: si hay salida real en curso, el dispatch
   vivo del evaluador escala el tracking él mismo.
3. **El embarque AR entra al cerebro** — tercer testigo de viaje para contadores mudos: un
   IN_VEHICLE ENTER sellado DESPUÉS del ancla y reciente prueba que el desplazamiento fue un viaje
   que empezó en el coche. Absorbe el fall-through AR del worker CON la exigencia de ancla que el
   fall-through no tenía (auditoría 04-07: "fall-through publica plaza fantasma"). El fall-through
   del worker se conserva SOLO para el camino rápido (EXIT en el borde = ancla por definición).
   `DispatchDeparture.tripStartedAtMs` fecha la salida con su evidencia (embarque AR o sello del
   ancla) → el gate de frescura de publicación mide la edad real de la plaza: el salto corto de
   2 min publica, el zombi recuperado no.
