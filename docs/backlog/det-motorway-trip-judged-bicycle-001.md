# DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 · Una etiqueta de AR no puede ganarle a 131 km/h medidos

**Estado:** 🟢 §A + §B + §C hechos, **sin commitear** · rama
`bugfix/DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001-cadence-ceiling` · worktree `../Paparcar-motorway-bike`
· **base `22c2c513`** (la pila handoff + early-close, que es lo que corre en los móviles — no master)
· ⏳ pendiente: APK a los dos móviles y el viaje

## Problema

Field 2026-08-20, Redmi (`diagnostics/WZB7.../sessions/1787242874932`). Tres viajes en el día. El
Oppo, en el mismo coche y los mismos trayectos, clavó los tres (`steps+egress`, 0.9). El Redmi
**no puso ni un pin**, y al final del día perdimos la plaza.

Una **sola sesión de 102,4 min** se comió los dos primeros viajes:

```
18:21:14  ARM:GEOFENCE_EXIT (d=403 m, acc 6 m, dep=verified_enter)   ← hubo AR IN_VEHICLE ENTER
18:21-18:29  autovía: 967 fixes, 109 en banda, pico 131,4 km/h @ acc 4,6 m
18:29:15  llega · 18:29:31  AR IN_VEHICLE EXIT
18:29 → 19:35  66 MINUTOS parado, fix cada ~6 s a 5-7 m de precisión
              → 0 candidatos, 0 prompts, 0 decisiones. Silencio absoluto.
19:36-19:48  segundo trayecto · 19:49:30 segundo AR IN_VEHICLE EXIT
19:48:32  CONFIRM_DEGRADED_PROMPT  ← primer veredicto de la sesión, y es una PREGUNTA
20:03:33  UNATTENDED_HUMAN_POWERED_NUDGE → aborted_unattended_human_powered · NADA guardado
21:35     tercer viaje: sin pin no hay geocerca, sin geocerca no hay nominador → ni armó
```

`batteryUnrestricted=true`, cero `BACKGROUND_KILL_SUSPECTED`, 1.476 eventos sin un hueco: **el OS no
tiene nada que ver**. Despertó con datos → bug nuestro.

## Qué demuestra el replay (hecho ya)

`TRACE_MOTORWAY_REDMI_001` — los 1.470 eventos de campo 1:1, incluido el carril de AR. Dos pasadas
sobre el detector real, idénticas salvo **un sello `ON_BICYCLE`** inyectado a mitad de autovía:

| | Campo | Replay CON sello | Replay SIN sello |
|---|---|---|---|
| `CANDIDATE` | 0 | **0** | — |
| `DECISION` | `CONFIRM_DEGRADED_PROMPT`, `UNATTENDED_HUMAN_POWERED_NUDGE` | **idénticas y en orden** | — |
| `outcome` | `aborted_unattended_human_powered` | **idéntico** | confirmado |
| pines | 0 | **0** | **1** |

Reproducción exacta. Y la conclusión es limpia: **el sello de bici es la única diferencia entre
perder el coche y guardarlo.**

Descartadas con datos las otras dos fuentes de `isHumanPoweredRide`:
- **Perfil**: el C5 Aircross está registrado `vehicleType = CAR` (verificado en Firestore).
- **Cadencia de pedaleo** (`DET-MOTOR-PROOF-001`): reconstruida sobre el trace crudo → **6 eventos
  en 1 fix**, contra los 12 en 2 que exige el umbral. **No se disparó.** (Los 6 son de 18:29:06-09,
  a 12,5 km/h: la deceleración hacia la plaza.)

## Doctrina violada

- **El evento NOMINA, solo el movimiento MEDIDO confirma.** Aquí un evento de AR *vetó* lo que el
  stream había medido. La doctrina está escrita para el confirm y se olvidó en el veto: una etiqueta
  puede contradecir la medición sin que la medición pueda contradecirla a ella.
- **Fallo asimétrico bien entendido.** "Mejor FN que FP" justifica preguntar ante la duda, no
  fabricar una duda que los datos ya resolvieron. Con 109 fixes en banda y 131 km/h no hay duda.
- **Toda sesión termina.** Una sesión con conducción probada se quedó sin ninguna salida.

## Las tres roturas

**§A · El veto no es refutable por medición.** `isHumanPoweredRide` arbitra entre dos etiquetas de
AR por **orden de reloj** (`vehicleRideAtMs >= bicycle`). Esta sesión tenía ENTER (armó
`dep=verified_enter`), pero el sello de bici llegó *después*, a mitad de autovía, y ganó por ser el
último. La velocidad medida no entra en la función. Además:
- **El `IN_VEHICLE EXIT` no cuenta.** Bajarse de un coche demuestra haberse subido. Hubo dos,
  ninguno pesó.
- **La cadencia tampoco tiene techo**: `lastSpeedMps >= egressStepMaxSpeedMps` (3,0 m/s) sin cota
  superior. Un paso fantasma junto a un fix de 36 m/s cuenta como pedalada. No mordió esta vez
  — pero en autovía cada bache es una "pedalada" a 130 km/h.

**§B · La sesión de marcha humana no tiene salida.** `DET-HUMAN-POWERED-EARLY-CLOSE-001` suprime el
prompt y confía el cierre a que llegue **High**. Pero el scorer tiene dos carriles y el rápido
cortocircuita al lento: con `activityExit` + 30 s parado devuelve **Medium (0,65) y retorna** —
`slowPath5MinMs`, único camino a High, queda inalcanzable. Es decir: **justo la señal más fuerte de
"me he bajado del coche" es la que impide cerrar**. Sin prompt no hay reloj de respuesta; sin High
no hay cierre. La sesión es inmortal. Muerde a cualquier bici real que produzca un AR EXIT, no solo
a este falso positivo.

**§C · El veto no deja rastro.** `ON_BICYCLE` solo se escribe en logcat. La señal que decidió la
sesión entera es invisible en forensics: 66 minutos indescifrables desde el trace.

## Diseño

**§A · La medición tiene la última palabra — hecho ✅**

El invariante vive en UN sitio, `isHumanPoweredRide`, y dice: *una etiqueta puede nominar "esto fue
a músculo"; solo la medición decide*. Tres piezas:

1. **Banda motora** (`motorProofSpeedMps = 11,1 m/s`, 40 km/h). Sostenerla `sustainedDriveProofMs`
   (30 s) **refuta cualquier reclamación de marcha humana**, venga del sello de AR o del pestillo de
   cadencia. Es una REFUTACIÓN, nunca una prueba de aparcamiento: no habilita ningún pin por sí sola
   (todo confirm sigue exigiendo egreso), solo impide afirmar músculo sobre una sesión que midió un
   motor. Deliberadamente **no** pasa por la promoción drive-proof de `provenDrivingBandMs`: su
   trabajo es tumbar un veto, y la asimetría corre del lado seguro (dudar del veto cuesta un prompt;
   creerlo costó un coche).
   - Umbral validado contra las tres trazas de campo: autovía **361,0 s** en banda · bici Redmi
     **0,0 s** · bici Oppo **0,0 s**. No es un margen ajustado, es un abismo.
2. **El `IN_VEHICLE EXIT` sella el embarque.** Nadie se baja de un coche al que no se subió, y en
   una sesión armada por geocerca el EXIT suele ser la ÚNICA transición `IN_VEHICLE` que AR entrega
   (el ENTER existió — armó `dep=verified_enter` — pero llegó antes de que la sesión existiera). Va
   al mismo `vehicleRideAtMs` que el ENTER, con su tiempo de transición REAL, y solo hacia delante
   (`maxOf`): AR entrega desordenado y un EXIT viejo no puede envejecer la evidencia.
3. **Techo en la cadencia.** `cadenceStep` tenía suelo (3,0 m/s) y ningún techo, así que un paso
   fantasma junto a un fix de 36 m/s contaba como pedalada a 131 km/h. Ahora la concurrencia solo
   cuenta **dentro** de la banda que una bici puede ocupar: `>= egressStepMaxSpeedMps` y
   `< motorProofSpeedMps`. Por encima, la concurrencia prueba lo contrario del pedaleo.

**El reloj de banda pasa a ser una función pura propia** (`domain/detection/SpeedBandClock.kt`):
tenía un consumidor y ahora tiene dos, y dos copias de un acumulador de cinco líneas es exactamente
cómo se arregla una señal en una banda y se olvida en la otra [DET-VERDICT-NOT-PREDICATE-001].

**Fixtures honestas.** `emitCorroboratedDrive` pedaleaba a 11 m/s (39,6 km/h) sostenidos: no es una
bici, es un coche urbano — pasaba por 0,36 km/h de margen. Ahora toma la velocidad como parámetro y
las pruebas de bici usan `BICYCLE_SPEED_MPS` (6,5 m/s ≈ 23 km/h), donde midieron las de campo.
Y la prueba `should_not_save_anything_when_the_ride_was_human_powered` inyectaba un `onVehicleExit()`
sintético cuando su propia sesión de origen (1786878499475) registró **cero** transiciones de AR:
afirmaba "bici" mientras le daba de comer una salida de vehículo. Fuera.

**§B · La parada sostenida se MIDE, no se deduce de una puntuación — hecho ✅**

`DET-HUMAN-POWERED-EARLY-CLOSE-001` preguntaba el veredicto de cierre dentro de `advanceHigh`,
razonando que *"High ES la parada sostenida certificada, su único camino es el escalón de 5 min"*.
Eso es cierto del carril **lento** del scorer — y el **rápido** lo cortocircuita: con `activityExit`
y 30 s parado, `CalculateParkingConfidenceUseCase` devuelve **Medium (0,65) y retorna** sin mirar
los escalones. High queda inalcanzable para el resto de la sesión.

Combinado con la supresión del prompt que el mismo ticket introdujo, la sesión se queda sin
puertas: sin prompt no hay reloj de respuesta, sin High no hay cierre, sin candidato no hay
veredicto. La señal más fuerte de "me he bajado del coche" era justo la que impedía terminar.

**El arreglo:** la certificación de reposo se lee del **reloj de parada** (`stoppedDuration >=
slowPath5MinMs` — el mismo número que usaba el escalón, sin knob nuevo) y la pregunta de cierre se
hace en `evaluateConfidence` **antes** del reparto por escalón, así que todos los caminos a una
parada madura pasan por ella exactamente una vez. La copia de `advanceHigh` desaparece: un High del
carril lento lleva `slowPath5MinMs` parado por construcción, así que no se pierde nada — y el
veredicto vuelve a vivir en un solo sitio. `advanceHigh` se queda sin sus parámetros muertos y sin
su `Boolean` de retorno.

**Regresión verificada ROJA sin el fix**: `should_close_the_session_even_when_an_ar_vehicle_exit_
caps_the_scorer_at_medium` (perfil BIKE + `AR EXIT` + parada madura — la forma exacta del campo, con
el perfil como fuente de marcha humana a propósito, porque es la única que §A no puede refutar).

**§C · El carril de evidencia de AR entra en el trace — hecho ✅**

La señal que decidió la sesión entera era **invisible**: 1.476 eventos y ninguno nombra el sello
`ON_BICYCLE`. Solo se llegó a él por eliminación (descartando perfil y cadencia con los datos
crudos), y días después. Sin tipo de evento nuevo — el vocabulario ya existía:

- `ON_BICYCLE / ENTER` e `IN_VEHICLE / ENTER` se registran como `ACTIVITY_TRANSITION`, con el mismo
  patrón de **flanco** que ya usaba el `EXIT` (desde el colector de fixes, no desde los métodos de
  señal: son entradas no-suspend llamadas desde un receiver, y el flanco pertenece al orden del
  stream).
- **El ENTER también**, no solo la bici: sin él el trace muestra un veto y ningún rastro del
  embarque que debería haberlo desmentido — que es justo la comparación que hace el veredicto.
- Cada uno lleva **cuánto de rancia venía la respuesta de AR** (`trueTimeAgeMs`, hasta ~2 min de
  latencia), porque el veredicto arbitra por tiempo de transición REAL: un trace con solo tiempos de
  entrega no permite auditar la decisión a posteriori. Viaja en la columna `enterAgeMs` que ya
  existía y que ya significa exactamente eso en el carril de salidas — **sin cambio de superficie
  del serializador**.
- Los marcadores de flanco arrancan a 0 y no al estado actual **a propósito**: un sello **heredado**
  de antes de la sesión (el estado singleton solo se resetea al TERMINAR una sesión, así que un
  sello entregado entre sesiones se cuela en la siguiente) queda registrado en el primer fix con su
  edad real. Esa herencia es hoy invisible y es el tipo de veto más difícil de explicar después.
- Y `MOTOR_WITNESSED` cuando la banda motora cruza: si la refutación de §A alguna vez tumba un viaje
  que sí era a músculo, esa línea lo dirá.

`tools/trace2fixture` aprende los dos, y replaya el sello de bici en su tiempo de transición real.

**Y el pestillo de cadencia, que era la OTRA fuente del veto — plegado aquí el 21-08.** §C dejaba el
carril de AR en el trace y la cadencia solo en logcat, así que una sesión vetada por cadencia seguía
sin decir nada: el lector volvía a deducir por eliminación. Es exactamente lo que pasó con el
**segundo caso de campo** (abajo). Ahora emite `PEDAL_CADENCE_LATCHED` (`DetectionEvent.Decision`,
sin tipo nuevo — como `MOTOR_WITNESSED`), con `steps=`, `fixes=` y la **banda** que los admitió,
porque §A cambió qué cuenta y un trace posterior tiene que decir qué regla produjo el pestillo.

El marcador pasa de **igualdad a pestillo**. El edge anterior disparaba en el paso que igualaba el
umbral de eventos, y su propio comentario concedía el fallo: *"a session whose second distinct fix
arrives later satisfies the verdict without this line"*. Un veto que puede decidir una sesión en
silencio ES el defecto, así que se registra la primera vez que **ambas mitades** se sostienen.
Regresión **verificada ROJA** con el `==` restaurado:
`should_record_the_pedal_cadence_latch_in_the_trace_even_when_its_second_fix_arrives_late`.

⚠️ Nada de esto vale en logcat: **nadie conduce con el móvil enchufado al PC**, y el anillo de
logcat ya había rotado sobre esta decisión cuando se investigó por primera vez. El destino es el
trace remoto y `files/parkdiag.log`.

## Segundo caso de campo — Oppo, 2026-08-20 23:56 (mismo bug, otro móvil)

Sesión `diagnostics/fiypNbElGlfFexLMpU9sNaMjRMD3/sessions/1787263007358`. `ARM:GEOFENCE_EXIT`
(d=260 m, acc 5 m, `dep=verified_speed`), 36,6 min, 684 fixes, 125 de conducción, **vmax 63,3 km/h**,
parada total en el min 20 y **175 pasos de egress**. Los **tres** veredictos cayeron por el mismo
`isHumanPoweredRide`:

```
t=  585 s  CONFIRM_DEGRADED_PROMPT        kinematic+egress
t= 1291 s  CONFIRM_DEGRADED_PROMPT        steps+egress
t= 2191 s  UNATTENDED_HUMAN_POWERED_NUDGE unattended_timeout → aborted_unattended_human_powered
```

Cero pines. Perfil descartado con datos (los 3 vehículos son `vehicleType: CAR`), y **no se pudo
saber si fue el sello de AR o la cadencia**, porque ninguno de los dos dejaba rastro — que es
justo lo que este §C cierra.

**§A lo resuelve, y por triplicado.** Reloj de banda motora corrido sobre los 684 fixes reales
(`creditSpeedBand`, ≥11,1 m/s, acc ≤50 m, gap ≤60 s): **628,7 s acumulados**, cruzando el umbral de
30 s en **t = 33,7 s de sesión** — nueve minutos antes del primer veredicto degradado (292,7 s
acumulados) y 628,7 s en el segundo. La refutación de §A entra antes de mirar cadencia y antes de
mirar el sello, así que **da igual cuál de las dos fuentes fuese**. Además la pieza 2 (el
`IN_VEHICLE EXIT` de t=1708 s sella el embarque) cubriría la variante AR, y la pieza 3 (techo de
cadencia) mata los pasos fantasma del tramo de 45-63 km/h.

Confirma que el bug no era del Redmi ni de la autovía: es del predicado.

## Hallazgo colateral (fuera de alcance, ticket propio)

Añadir evidencia **baja** la puntuación. La misma parada, madurada 5 minutos, puntúa **0,90 (High)**
sin `AR EXIT` y **0,65 (Medium)** con él, porque el carril rápido retorna antes de mirar los
escalones. Consecuencia medible en telemetría: **la fase CANDIDATO es inalcanzable en cuanto AR
entrega un `IN_VEHICLE EXIT`**, y con ella la vía de confirmación `windowElapsed && hadVehicleExit`
(`vehicleExitObservationWindowMs`), que en la práctica está muerta — `hadVehicleExit` se fotografía
al ENTRAR en candidato, y para entrar hace falta un High que el propio EXIT bloquea.

Contrastado con las sesiones del 20-08: la del Redmi tuvo 2 `ACTIVITY_TRANSITION` y **0 candidatos**;
las dos confirmadas del Oppo, **0 candidatos** (confirmaron por el carril rápido `steps+egress`); las
dos bicis del 19-08, **0 transiciones de AR y 5 candidatos cada una**.

No es un problema de seguridad (esas sesiones sí preguntan por el carril Low/Medium y conservan su
timeout de 15 min), así que no entra aquí → **`DET-EVIDENCE-MUST-NOT-LOWER-CONFIDENCE-001`**
(`docs/backlog/det-evidence-must-not-lower-confidence-001.md`).

**Medido antes de descartarlo** (spike aplicado sobre esta rama y revertido): el arreglo obvio —el
carril rápido como SUELO, `max(rápido, lento)`— **cuesta una plaza real**. En
`redmi_late_exit_home_001` (DET-NODRIVE-ZONE-001, campo 27-07) la sesión pasa de guardar la zona a
perderla: levantar el techo la mete en el bucle de candidatos
(`OPENED, DISCARDED, OPENED, DISCARDED, OPENED`) y **cada descarte pone `stepCount = 0`**, borrando
los pasos de egreso que el veredicto desatendido leía después para justificar la zona
(`confirmed_unattended_zone_no_drive_egress` → `aborted_unattended_no_drive` + nudge). Las otras 11
fixtures no se movieron. Conclusión: el techo está **tapando** que el descarte de candidato es
destructivo, así que ese es el primer arreglo, no el segundo.

## Criterio de éxito

- El replay `motorway_redmi_001_a_cycling_stamp_must_not_outrank_131_kmh_of_measured_driving` pasa:
  la sesión guarda su plaza pese al sello de bici.
- El control sin sello sigue guardando (no se rompe nada).
- Una bici de verdad (`Trace_*` de 19-08, vmax 18-40 km/h) sigue vetada y ahora **cierra** en su
  primera parada madura aunque AR haya emitido EXIT.
- Ninguna sesión con conducción probada puede quedarse sin salida.
- Campo: autovía → pin; bici → una sola notificación honesta y cierre.

## Consumidores auditados (§A)

`isHumanPoweredRide` tiene **un solo** llamador de producción: el wrapper
`CoordinatorParkingDetector.humanPoweredRide(state, type, now)`. Todo veredicto que pregunta "¿fue a
músculo?" pasa por ahí, así que el arreglo entra por un único punto — pero hay que demostrarlo, no
suponerlo:

| Sitio | Qué asume | Clasificación |
|---|---|---|
| Carril rápido `steps+egress` (`:1308`) | `humanPoweredRide` degrada el confirm a Prompt | **cerrado** — lee el wrapper |
| `parkingDecisionInput(...)` (`:2017`, unificado por early-close) | ídem, para carril candidato + `advanceHigh` | **cerrado** — lee el wrapper |
| `advanceLowMedium` (`:2703`) | suprime el prompt de una marcha humana | **cerrado** — lee el wrapper |
| `EvaluateUnattendedParkingSaveUseCase` | `humanPoweredRide` en su primera línea | **cerrado por convergencia** — recibe el input ya refutado |
| `onVehicleExit()` — receiver Android, `IosActivityRecognitionManagerImpl`, 17 tests | firma sin argumento | **cerrado** — `atMs` por defecto = reloj del coordinador; solo el receiver Android pasa el tiempo de transición real, que es el único sitio que lo tiene |
| `drivingBandMs` / `provenDrivingBandMs` | reloj de banda inline | **cerrado** — migrado a `creditSpeedBand`, comportamiento idéntico (1295 tests verdes) |
| Perfil `VehicleType.BIKE`/`SCOOTER` | veta siempre, mire AR lo que mire | **exento a propósito** — un vehículo registrado como bici no auto-confirma aunque mida 40 km/h sostenidos; es la dirección conservadora y es doctrina previa [DET-SOLID-001 C2] |

## Consumidores auditados (§B)

| Sitio | Qué asume | Clasificación |
|---|---|---|
| `advanceHigh` | era el único que podía cerrar una marcha humana | **cerrado** — pregunta retirada; se queda sin parámetros muertos (`location`, `activeVehicleId`, `activeVehicleType`) y sin retorno `Boolean` |
| `evaluateConfidence` | devolvía "la sesión termina aquí" solo vía `advanceHigh` | **cerrado** — ahora es ella quien pregunta, antes del reparto por escalón |
| `advanceLowMedium` | suprime el prompt de una marcha humana y confía el cierre a otro | **cerrado por convergencia** — el cierre ya ocurre antes de que la supresión importe |
| Carril rápido `steps+egress` | pasa `restCertified = false` | **exento** — corre sin parada detrás; cerrar ahí dormiría a un ciclista en un semáforo con cuatro pasos fantasma |
| `ParkingDecision.CloseHumanPowered` (`when` exhaustivo) | 5 valores | **cerrado** — sin ramas nuevas; solo cambia quién pregunta |
| `ParkingSafetyNetWorker` (red de 15 min) | reconcilia sesiones que el OS no entregó | **exento** — red del sistema operativo, ortogonal a que la sesión tenga salida propia |

## Consumidores auditados (§C)

| Sitio | Qué asume | Clasificación |
|---|---|---|
| `DetectionEventDto.toDto()` (`when` exhaustivo) | mapea cada variante | **cerrado** — campo nuevo mapeado a `enterAgeMs`, con test de paridad propio (`DetectionEventDtoTest`); el `when` no protege campos, solo variantes |
| `tools/trace2fixture` | solo consumía FIX/STEP | **cerrado** — aprende `ON_BICYCLE/ENTER` e `IN_VEHICLE/EXIT`, y replaya el sello en su tiempo real |
| `DetectionTraceReplayer` | 2 tipos de evento | **cerrado** — 4 tipos, con emisores por defecto para que las fixtures viejas sigan compilando y comportándose igual |
| Fixtures existentes (`Trace_*.kt`) | no llevan carril AR | **exento** — se grabaron antes de que existiera; `Trace_Enamorados001` documenta que su EXIT se inyecta a mano desde el test |
| Rollup del logger (`fixCount`, `maxStepCount`…) | cuenta por tipo | **exento** — no cuenta `ACTIVITY_TRANSITION`; el volumen añadido son 2-3 documentos por sesión |
| `IosActivityRecognitionManagerImpl.onVehicleExit()` | firma sin argumento | **cerrado** — argumento por defecto; iOS no tiene tiempo de transición que pasar |

## Resultado

**1300 tests verdes** (1295 en la base + 5 nuevos: §B, §C, paridad DTO ×3 y el reloj de banda ×5,
menos los ya contados), `compileProdDebugKotlinAndroid` y `compileMockDebugKotlinAndroid` OK. Sin
strings nuevos, sin pantalla ni estado nuevo → los 9 locales y el Dev Catalog no se tocan.

- §A: el replay de campo pasa de reproducir la pérdida a guardar la plaza; el control sin sello
  sigue guardando; las dos trazas de bici reales siguen vetadas.
- §B: regresión **verificada roja** sin el fix (y con ella cae también la del early-close, que era
  la otra cara del mismo veredicto).
