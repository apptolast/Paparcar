# DET-A-DEPARTURE-RATE-MUST-BE-PHYSICALLY-REACHABLE-001 · un fix que se declara parado no puede estar a 207 m cinco segundos después

**Estado:** ✅ **Done** — mergeado a master el 01-09-2026 (squash) · **2082 tests, 0 fallos** ·
`mock` y `prod` compilan
**Pendiente de campo:** ⏳ requiere `/run` — los móviles llevan `1.0.0-beta02` y no tienen ni esto ni
`DET-A-JUST-DEPARTED-CAR-IS-NOT-NO-SESSION-001`.
**Abierto:** 2026-09-01 sobre master `9071e9b8`
**Origen:** field 31-08 noche, Oppo — punto 3 de los tickets candidatos de
`project_det_field_2026_08_31_oppo_goes_deaf`. Medido en el `parkdiag.log` extraído el 01-09
(`paparcar-fieldlogs/2026-09-01/oppo-LNRCMZ8H6HBITWNJ-parkdiag.log`, líneas 24810-24817).

## Problema

`sustainedDepartureFromAnchor` mide una tasa de suelo — distancia desde el ancla dividida por el
tiempo desde que empezó su parada — y la acepta como **conducción corroborada** si cae en la ventana
`[minimumTripSpeedMps, sustainedDepartureMaxRateMps]` = **[5, 55] m/s**. El techo son **198 km/h**.

Entre esas dos barras cabe cómodamente un teleporte de caché. Del log, sin interpretar:

```
21:21:59        GEOFENCE_EXIT ARMED — d=276m acc=172m
21:22:00.142    loc#1 lat=36.6331117 lon=-6.228445 speed=0.0m/s acc=5.1m  sessionAge=737ms
                state … stoppedSince=1788204120142     ← el reloj del ancla arranca AQUÍ
21:22:05.252    loc#2 lat=36.63303   lon=-6.230765 speed=3.189m/s acc=12.5m
21:22:05.254    ⇢ SUSTAINED DEPARTURE — position ran 207 m from the anchor at 40.5 m/s avg
                  — credible drive by displacement [DET-CREDIBLE-DRIVE-001]
21:22:05.257    ✓ MOTOR witnessed by displacement — sustained 40.5 m/s from the anchor, above 11.1 m/s
21:22:07…36     loc#3..loc#11 — TODOS en el sitio de loc#2 (‑6.2306/‑6.2308), a 1,6-3,2 m/s
```

**146 km/h en el quinto segundo de una sesión**, y los once fixes de esa sesión traen
`hasEverReachedDrivingSpeed=false`.

⛔ **Lo que hace de esto un caso cerrado no es la velocidad, es que había un testigo que ya lo
desmentía.** Un segundo antes de `loc#1`, la valla había declarado `d=276m` del pin. `loc#1` dice
"estoy en el pin" cuando el sistema acababa de medir que estábamos a 276 m de él — y es `loc#1`, no
`loc#2`, el outlier: los nueve fixes siguientes se quedan donde está `loc#2`. El teleporte se usó
como base del ancla sin que nadie confrontara los dos testigos.

Márgenes con los que pasó, por si sirven para calibrar: el suelo era
`acc_ancla + acc_fix + sustainedDepartureFloorMeters` = 5,1 + 12,5 + 150 = **167,6 m**, y midió
**207 m**. Pasó por 40 m. La tasa, 40,5 m/s, pasó el techo de 55 por 14,5.

### Por qué importa aunque la salida FUESE real

El ground truth del user de esa noche es que el coche salía de verdad, despacio, por camino de
tierra. La plaza se liberó bien. Pero **acertó por un motivo que no había medido**: el veredicto no
salió de la salida lenta real, salió de un artefacto de caché. Un guard que da el resultado correcto
por una medición falsa es un guard que no está protegiendo nada.

## Doctrina violada

**El evento NOMINA, solo el movimiento MEDIDO confirma.** Un teleporte de caché no es movimiento
medido: es la misma posición vieja re-entregada. Aquí ha hecho de "conducción corroborada por
desplazamiento", que es literalmente el carril que existe para creerle a la traza cuando no se puede
creer a los fixes de uno en uno.

**Sistemas, no parches.** El comentario de `HumanPoweredRide.kt:126` ya dice dónde vive el
invariante — *"The baseline and the rate ceiling live inside `sustainedDepartureFromAnchor`"*. El
sitio está bien elegido; lo que falla es que el techo que hay ahí no es una ley física, es un número
redondo. El arreglo va en esa función, **no** en sus cuatro consumidores.

## Señales / datos disponibles

Todo lo necesario ya está en los parámetros de la función, sin añadir estado:

| señal | de dónde | qué permite |
|---|---|---|
| `anchor.speed` | el fix que abrió la parada | saber si la base se declaraba **parada** (aquí `0.0`) |
| `nowMs - anchorStoppedSinceMs` | ya se calcula (`elapsedSeconds`) | la ventana; aquí **5,1 s** |
| `d` | ya se calcula | **207 m** |
| `anchor.accuracy`, `fix.accuracy` | ya se usan para el suelo | el margen de incertidumbre |

⚠️ Lo que **no** tenemos: `GpsPoint.timestamp` es `Location.time` (ver
`AndroidLocationDataSourceImpl.toGpsPoint`), pero el reloj del ancla (`capturedAtStop`) se estampa
con el reloj de pared del procesado, no con el `timestamp` del fix. Es decir: **la edad real del fix
cacheado no llega hasta aquí**. Vía alternativa considerada y descartada de entrada por eso — habría
que cambiar cómo se sella la parada, y eso toca `AnchorTrust` entero.

## Diseño (a decidir antes de tocar código)

La forma que propone el dato: **no un techo de tasa, sino alcanzabilidad por aceleración desde el
estado que el ancla declaraba.**

```
d_max = v0 · t + ½ · a_max · t²        v0 = anchor.speed   ·   t = elapsedSeconds
```

Con `v0 = 0` y un `a_max` deliberadamente generoso (4 m/s², más que un utilitario cargado saliendo
de una plaza), en 5,1 s no se cubren ni **52 m**. Se midieron 207. Refutado sin ambigüedad.

Lo que hace atractiva esta forma es que **solo muerde donde debe**: la restricción se afloja con `t²`,
así que los dos casos de campo por los que esta función existe siguen pasando holgados —

| caso | tasa | ventana | `d_max` con a=4 m/s² | ¿sobrevive? |
|---|---|---|---|---|
| Enamorados 15-07 (OEM mata la accuracy) | 10,12 m/s | decenas de s | cientos de m | ✅ |
| Valdés→Góndola 26-08 (batching 163-200 s) | 26,2 m/s | 163 s | ~53 km | ✅ |
| **teleporte Oppo 31-08** | **40,5 m/s** | **5,1 s** | **52 m** vs 207 medidos | ❌ **refutado** |

— es decir, la ventana corta es exactamente la firma del teleporte, y la larga la del batching que
esta función existe para tolerar.

Alternativas descartadas y por qué:
- **Bajar `sustainedDepartureMaxRateMps`** de 55 a, digamos, 15 m/s: mataría el caso 26-08 (26,2 m/s
  reales, medidos, correctos). Un techo plano no distingue 26 m/s en 163 s de 40 m/s en 5 s, y esa
  distinción es todo el problema.
- **Subir `sustainedDepartureFloorMeters`**: aquí midió 207 m, así que el suelo tendría que subir por
  encima de un desplazamiento perfectamente real. Estrangula los viajes cortos.

⚠️ **Fuera de alcance a propósito**: el otro arreglo posible es que `loc#1` nunca hubiera sido base
del ancla, porque la valla lo desmentía un segundo antes. Eso es *confrontar dos testigos*, vive en
`AnchorTrust`/el intake, y es un ticket distinto — anotarlo como follow-up al cerrar éste, no
colarlo aquí.

## Criterio de éxito

- Un test que reproduzca los números del campo: ancla `speed=0`, `t=5,1 s`, `d=207 m` → la función
  devuelve `null`. Falsificado en rojo neutralizando el guard nuevo.
- Los tres casos de la tabla, como tests, con los dos legítimos en verde.
- `DriveCorroborationTest` sigue verde (4 casos existentes de `sustainedDepartureFromAnchor`).
- Suite completa verde, nº reportado.
- En campo: buscar `⇢ SUSTAINED DEPARTURE` con ventanas < 10 s en el siguiente `parkdiag` — no
  debería quedar ninguno.

## Consumidores auditados

Barrido hecho el 01-09 sobre `9071e9b8`. Una sola medición alimenta **cuatro** consecuencias, y por
eso el arreglo va en la medición:

| # | consumidor | qué hace con ella | estado |
|---|---|---|---|
| 1 | `AnchorPredicates.sustainedDepartureFrom:192` → `StopTracking.sustainedDeparture:53` | descongela el ancla; imprime la línea `⇢ SUSTAINED DEPARTURE` | **cubierto por convergencia** (misma función) |
| 2 | `EffectiveDriving.kt:90` — `sustainedDeparture -> true` | cuenta como conducción efectiva | **cubierto por convergencia** |
| 3 | `StopTracking.kt:384` — `steplessDeparture && !isRealDrive && !sustainedDeparture` | puerta de la salida sin pasos | **cubierto por convergencia** |
| 4 | `DriveProof.motorDisplacementRateMps:209` (via `CoordinatorParkingDetector:1009`) → `HumanPoweredRide.kt:128` y `CoordinatorParkingDetector:1100` | **revoca el veto human-powered** | **cubierto por convergencia** |
| 5 | `DriveProofBounds.maxRateMps` (`CoordinatorParkingDetector:1354`) → `corroboratesDrive:139` | comparte la CONSTANTE `sustainedDepartureMaxRateMps`, pero es otra función y otra geometría (fix contra fix de la ventana, no contra el ancla) | **exento** — no se toca en este ticket; si el techo nuevo lo necesita, se decide aparte |

🔴 **El consumidor 4 es el que convierte esto en un defecto con memoria**: `motorDisplacementRateMps`
es un **latch de sesión** (`maxOf`, con el KDoc *"ground the vehicle provably covered does not become
uncovered"*). Un teleporte en el segundo 5 deja el veto human-powered revocado **el resto de la
sesión**, sin forma de deshacerlo. No es un veredicto puntual que el siguiente fix corrige.

## Lo implementado

Tres ficheros de producción, un parámetro nuevo y seis líneas de lógica:

1. **`ParkingDetectionConfig.kt`** — `sustainedDepartureMaxAccelerationMps2: Float = 4f`, con su
   `require(> 0f)` en el bloque de validación.
2. **`DriveCorroboration.kt`** — el segundo techo dentro de `sustainedDepartureFromAnchor`, después
   de la ventana de tasa:
   ```kotlin
   val reachableMeters = anchor.speed * elapsedSeconds +
       0.5 * maxAccelerationMps2 * elapsedSeconds * elapsedSeconds
   if (d - jointAccuracyMeters > reachableMeters) return null
   ```
   `v0 = anchor.speed`, así que una parada que aún rodaba no se juzga como si hubiera estado quieta.
3. **`AnchorPredicates.kt`** — pasa el parámetro desde config. **Sin default**: un default aquí sería
   una respuesta silenciosa permanente [DET-A-DOUBT-FIELD-MUST-NOT-DEFAULT-TO-CERTAINTY-001].

**Decisiones que quedaban abiertas, resueltas:**
- `a_max = 4 m/s²`, con la cuenta escrita en el KDoc de la config. Es una barra de **imposibilidad
  física**, no de estilo de conducción: todo lo que un coche puede hacer de verdad la pasa.
- Va **dentro** de `sustainedDepartureFromAnchor`, no en predicado propio: tiene un solo consumidor y
  el propio código ya declaraba esa función como el sitio del techo (`HumanPoweredRide.kt:126`).

**Descubierto al implementar, y no estaba en el diseño:** el descuento por accuracy. El resto del
fichero suma las envolventes al suelo (`d <= anchor.accuracy + fix.accuracy + floorMeters`); comparar
la `d` cruda contra un límite físico habría sido incoherente con eso y, peor, habría hecho que el
ruido de GPS refutara salidas reales. ⛔ **Aquí rechazar NO es el lado seguro** — un ancla que no se
descongela es lo que plantó el pin a 1,11 km en Enamorados. Por eso se compara
`d - (anchor.accuracy + fix.accuracy)`.

## Verificación

**2082 tests, 0 fallos.** `:app:compileMockDebugKotlin` y `:app:compileProdDebugKotlin` verdes.
Cuatro tests nuevos en `DriveCorroborationTest` (14 → 18):

| test | qué fija |
|---|---|
| `should_stay_silent_when_the_distance_is_unreachable_from_a_stopped_anchor` | el caso de campo: 207 m en 5,1 s desde `speed=0` |
| `should_report_the_measurement_when_a_long_window_makes_the_rate_reachable` | el 26-08 real bajo batching (163 s) sigue pasando |
| `should_start_the_bound_from_the_speed_the_anchor_declared` | misma geometría: pasa desde 12 m/s, se rechaza desde 0 |
| `should_discount_both_accuracy_envelopes_before_judging_reachability` | 240 m crudos sobre un límite de 200, de los que 50 son envolvente → pasa |

**Falsación** (método del proyecto: neutralizar el guard y verlo en rojo). Sustituido
`if (d - jointAccuracyMeters > reachableMeters) return null` por una expresión inerte:

```
DriveCorroborationTest > should_stay_silent_when_the_distance_is_unreachable_from_a_stopped_anchor FAILED
DriveCorroborationTest > should_start_the_bound_from_the_speed_the_anchor_declared          FAILED
18 tests completed, 2 failed
```

Los otros dos siguen verdes con el guard neutralizado **y eso es lo correcto**: afirman que el guard
NO dispara, así que son la red de los casos legítimos, no testigos del guard. Guard restaurado y
suite re-ejecutada con `--rerun-tasks`.

## Pendiente

- [x] Mergear
- [ ] Campo: en el siguiente `parkdiag`, buscar `⇢ SUSTAINED DEPARTURE` con ventanas < 10 s — no
      debería quedar ninguno. ⚠️ requiere `/run`: los móviles llevan `1.0.0-beta02`.
- [x] `docs/detection/PARKING-DETECTION.md` §2
- [ ] Follow-up a abrir al cerrar éste: confrontar el fix de base del ancla con el testigo de la valla
      (`loc#1` decía estar en el pin un segundo después de que la valla midiera 276 m)
