# DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001 · un coche a 12 km/h por el centro no es una bicicleta

> ⚠️ **Cruzado con el rediseño (30-08): INTACTO, y SUBE de prioridad.** No lo toca ninguna pieza
> (§6.2 #2 y la Pieza 2 tratan el `humanPowered` por OMISIÓN; esto condena a un coche REAL por su
> velocidad urbana). La Pieza 4 lo **encarece**: hoy un veto falso cuesta un pin impreciso, y con
> «prompt sin contestar → cerrar sin pin» pasará a costar la plaza entera.
> Dato nuevo 29-08: el veto de cadencia heredó la velocidad ESPEJISMO (7,71 m/s) dentro del FP de la
> parafarmacia — si la Pieza 1 arregla `Measured`, este veto mejora de rebote.
> Ver `docs/detection/REDESIGN-DETECTION-SYSTEM.md` §9.3.

**Estado:** 🟡 Abierto, **bloqueado por medición** · sin rama · sin worktree
> **25-09: el bloqueo se ESTRECHA.** El hueco de telemetría que iba primero está cerrado
> (`c692d61c4`), así que el numerador ya se lee en remoto, y el field de esa mañana aporta la primera
> sesión medida del punto 1 — con un coste consumado y velocidades condenatorias del **doble** que las
> del 26-08. Lo que sigue bloqueando es sólo el punto 2: **la distribución de la bici**.

**Origen:** follow-up deliberado de `DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001` (field 26-08).
Aquel arregla el **desenlace** (que el veto se pueda levantar); éste ataca el **origen** (que el veto
no debería haberse puesto).

## Problema

El detector de pedaleo se dispara con coches. Field 2026-08-26, Redmi (`WZB7oftWLDY1toGJrDwoRHnnYHx2`):

```
19:11:27  ♲ pedal cadence — 12 steps concurrent with 3 above-ceiling fixes   ← viaje en COCHE 1
20:22:11  ♲ pedal cadence — 12 steps concurrent with 3 above-ceiling fixes   ← viaje en COCHE 2
```

**2 de 2 trayectos en coche de esa noche.** En todo el `parkdiag` del Redmi la línea aparece **8
veces**; en el del Oppo, **1 vez**. Cero trayectos en bici en ninguno de los dos.

El primero fue inocuo por 24 segundos de suerte: `MOTOR witnessed` se había escrito a las 19:11:03,
**antes** que la cadencia, y con la prueba de motor delante el veto no muerde. El segundo costó la
plaza de Góndola 1 (detalle completo en el ticket padre).

## La regla, y por qué falla

`EgressEvidence::cadenceQualifies` acredita un paso como pedalada cuando el fix fresco y creíble más
reciente va **por encima de `egressStepMaxSpeedMps` (3,0 m/s = 10,8 km/h)** y por debajo de
`motorProofSpeedMps` (11,1 m/s). Con 12 eventos así (`pedalCadenceMinStepEvents`) repartidos en 2
fixes distintos (`pedalCadenceMinFixes`), la sesión queda juzgada humana.

El razonamiento original: *nadie ANDA a 10,8 km/h, y dentro de un coche el podómetro se calla*.

Las dos mitades fallan a la vez en ciudad:

- **10,8 km/h no separa nada.** Reporte del user (26-08): *"he ido en coche por el centro de mi
  ciudad, lo cual lo ha podido detectar como bici; aquí hay varias zonas donde podemos ir más rápido
  pero en coche los 30 km/h se cogen fácil"*. Las velocidades reales que alimentaron el falso
  positivo del 26-08 fueron **3,52 · 4,02 · 3,13 · 4,65 · 3,29 · 3,67 m/s (11-17 km/h)**: coche por
  calle estrecha, sin nada anómalo.
- **El podómetro del Redmi no se calla.** 8 disparos en su log contra 1 en el del Oppo con el mismo
  coche y las mismas calles. La premisa "en un coche el contador está en silencio" es **dependiente
  del hardware**, y en este dispositivo es falsa.

Encima, la banda que la regla vigila —entre 10,8 y 40 km/h— **es exactamente la banda de la ciudad**.
Cuanto más urbano el trayecto, más tiempo pasa dentro y más probable es la condena.

## Field 2026-09-25 (Oppo) — un coche a 38 km/h condenado, y la plaza perdida de verdad

Oppo CPH2371, uid `0l7X59ptXeQ4WUMlpFOZHLPIJAI3`, app 1.3.0, base `(default)`, sesión
`1790330246055` (11:57:26 → 12:07:55 hora local). **El mismo defecto, ahora en el Oppo y con el
numerador visible.** Viaje real: salida de la frutería, ~80 s y ~600 m de calle, pico **39,77 km/h**.

```
11:57:42-11:58:36  8 × CADENCE              fix a 24,6 · 27,3 · 29,2 · 30,9 · 31,6 · 37,2 · 37,7 · 37,9 km/h
11:58:06           DECISION PEDAL_CADENCE_LATCHED   steps=12 fixes=3 band=3.0-11.1mps
12:02:27           DECISION CONFIRM_DEGRADED_PROMPT pathLabel=steps+egress reason=human_powered
12:07:53           DECISION UNATTENDED_HUMAN_POWERED_NUDGE  (unattended_timeout) → sin pin
12:17:11           el user pone el pin A MANO (detectionPath=user_moved, fiabilidad 1.0)
```

Cuatro cosas que este field añade y las noches anteriores no tenían:

1. **Las velocidades condenatorias se DUPLICAN.** El 26-08 fueron 11-17 km/h y se pudieron leer como
   "calle estrecha"; aquí son **24,6-37,9 km/h**. Eso ya no es un coche atascado, es un coche
   circulando, y ninguna bici sostiene 37,9 km/h por el centro de Cádiz. La banda no falla en su
   borde inferior: falla en toda su anchura.
2. **La refutación por motor falló por 0,19 km/h.** `motorProofSpeedMps` = 11,1 m/s = **39,96 km/h**
   sostenidos 30 s; la sesión picó a **39,77 km/h** y no los sostuvo → `sustainedMotorBandMs = 0`.
   Aquí se ve que el techo de la banda de cadencia y la barra de refutación **son el mismo número**:
   un trayecto es condenable justo hasta la velocidad a la que podría refutarse, y por encima de eso
   además hay que sostenerla. Entre 10,8 y 40 km/h el coche urbano es condenable e irrefutable.
3. **La revocación no tenía de qué agarrarse.** Cero eventos `ACTIVITY_TRANSITION` en toda la sesión
   — ni un `IN_VEHICLE ENTER`, ni un `ON_BICYCLE`. Armó `GEOFENCE_EXIT` (geof `b15ad01b`, d=175 m).
   O sea: el caso que `DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001` dejó abierto a propósito
   —*"la cadencia habla por los trayectos cortos que AR nunca clasifica"*— es **exactamente** este,
   y este era un coche. El alcance que aquel ticket le reservó es el alcance donde muerde.
4. **El coste caro ya está consumado, en el Oppo.** No fue "un toque": el confirm bueno SÍ disparó
   (`steps+egress`) y el veto lo degradó a pregunta; nadie contestó; el cierre temprano
   (`DET-HUMAN-POWERED-EARLY-CLOSE-001`) lo cerró a los 5,4 min con un nudge y **sin pin**. El user
   lo marcó a mano 10 min después, a **~20 m** del ancla que la detección ya tenía
   (36.59367,-6.24536 vs 36.59352,-6.24551). No falló la medida: falló el permiso para guardarla.
   *(La plaza liberada de la frutería sí se publicó — spot `b15ad01b` a las 11:58:12. Lo perdido es
   el aparcamiento NUEVO.)*

### El corpus, ya ejecutable

La sesión está en el corpus como **`TRACE_FRUTERIA_2509_CADENCE_VETO`** con dos casos en
`DetectionTraceReplayTest` que difieren en UNA línea — por eso el par señala la causa en vez de
suponerla:

- `fruteria_2509_a_speed_verified_arm_gets_no_refutation_and_still_loses_it` — ⚠️ **el caso que
  mantiene ESTE ticket pinchado.** Afirma la pérdida (0 guardados,
  `aborted_unattended_human_powered`) con un arm que prueba la salida por VELOCIDAD, o sea sin
  embarque atestiguado: exactamente el coche urbano que la cadencia no debería condenar. **Cuando
  llegue la calibración, este es el test que se INVIERTE**, y su nueva expectativa es el criterio de
  éxito de más abajo.
- `fruteria_2509_the_arm_that_witnessed_the_boarding_carries_the_park` — el mismo stream cuando el arm
  SÍ vio el embarque: **guarda**, desde que entró
  `DET-A-VERIFIED-BOARDING-MUST-REACH-THE-VETO-001`. No cierra este ticket — sólo dice que cuando hay
  embarque la revocación ya funciona.

> ⚠️ **25-09, después:** el primer par de estos tests se llamaba
> `..._a_city_car_under_40_kmh_is_convicted_and_the_park_is_lost` y
> `..._the_boarding_the_arm_already_had_would_have_carried_it`. El segundo stampeaba el embarque a
> mano para demostrar que bastaba; al arreglarse eso de verdad, el stamp sobraba y el par se reordenó
> a los dos de arriba. La pareja sigue difiriendo en **una** línea: el arm.

⚠️ Montarlo destapó que **`trace2fixture` no leía los `CADENCE`**, o sea que generaba este trayecto
con 574 filas impecables y el replay **guardaba** — un fixture mudo sobre su propio bug. Arreglado en
el generador: `docs/backlog/det-the-cadence-corpus-must-be-executable-001.md`. Y de ahí salió también
`DET-A-VERIFIED-BOARDING-MUST-REACH-THE-VETO-001`, que **no sustituye a este ticket** (sigue
bloqueado: un coche sin embarque AR ninguno, como el del 18-08, seguiría condenado) pero repara la
revocación cuando el embarque existe, sin tocar un solo umbral.

### La fracción, medida por primera vez

Con `DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001` cerrado, el evento `CADENCE` trae
`creditedFixes`, `sessionStepEvents` y **la velocidad del fix contra el que se juzgó**. De esta
sesión:

| magnitud | valor |
|---|---|
| pedaladas acreditadas (`sessionStepEvents` final) | 25 |
| fixes distintos acreditados (`creditedFixes`) | **8** |
| fixes ≥ 18 km/h del rollup (`drivingFixes`) | 35 |
| fracción, cota SUPERIOR | **≤ 23 %** |

Es cota superior porque el denominador honesto es *los fixes de la banda* (>10,8 km/h), y 35 cuenta
sólo los ≥18 km/h: la fracción real es menor. **Apuntala la hipótesis del diseño candidato** — en
bici se esperaría una cadencia que acompañe a casi todos los fixes en movimiento; aquí acompaña a
menos de uno de cada cuatro, y aun así el umbral absoluto de 12 eventos se alcanzó en **40 segundos**.

## Doctrina violada

Ninguna directamente: el veto respeta *fallo asimétrico* (degrada a pregunta, no planta un pin
fantasma). Lo que rompe es el **presupuesto de coste**: `HumanPoweredRide.kt:42-43` justifica el
latch diciendo *"one tap, the direction asymmetric failure allows"*. El 26-08 demuestra que el coste
real no es un toque, es **la plaza entera** cuando nadie contesta el prompt en 15 minutos. Un veto
que se dispara en 2 de 2 viajes en coche no está comprando seguridad, está fabricando falsos
negativos.

## ✅ El hueco de telemetría, LEVANTADO (25-09)

`DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001` está en master (`c692d61c4`) y el field del 25-09
lo demuestra en caliente: los ocho `CADENCE` de la sesión llegaron a Firestore con su velocidad, y la
fracción del punto 1 se pudo calcular **sin cable** — que es justo lo que aquí no se podía hacer.
Queda como registro lo que costaba antes:

> El corpus del apartado siguiente **no se podía recoger con el build de agosto**. Los pasos que
> activan la cadencia son justo los que ningún diagnóstico graba: se dan conduciendo, con el ancla
> limpiada, y esa combinación no cae en ninguna de las tres ramas que emiten `✦ step`. Se descubrió
> al construir el replay del ticket padre — la traza salía verde con y sin el fix hasta que los doce
> eventos se reconstruyeron a mano.
>
> Medir la *fracción de fixes en movimiento con pasos concurrentes* exige ver el numerador. Así que
> **`DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001` va primero**, y es barato.

## Por qué está bloqueado

Cualquier número que se elija hoy se elige con **dos días de datos y cero trayectos en bici** (26-08
Redmi, 25-09 Oppo).
Apretar de más devuelve el incidente que creó la regla: 2026-08-16, Samsung SM-A536B, 59 min de bici
hasta Los Toruños a 38 km/h de pico, geocerca rota a 352 m y un Mercedes re-pinchado a 4,8 km del
coche real. Ese modo de fallo **cuesta un coche**; éste cuesta una plaza. No se toca a ojo.

## Qué hay que medir antes de tocar nada

1. **Coches, varios dispositivos, varios recorridos urbanos.** Por sesión: nº de fixes en movimiento,
   cuántos llevan pasos concurrentes, y la fracción entre ambos. El Redmi y el Oppo por separado —
   la diferencia 8 vs 1 dice que el ruido del podómetro es del aparato, no del trayecto.
   🟡 **Primera muestra dentro (25-09, Oppo): 8/35 ≤ 23 %.** Y matiza la lectura de agosto: el
   "8 vs 1" era ruido de podómetro, pero el Oppo **también condena**, así que el defecto no es de un
   aparato concreto. Faltan más recorridos y el Redmi con esta instrumentación.
2. **Al menos una bici real** con el build actual, misma instrumentación. Sin la distribución de la
   bici no hay listón que poner. 🔴 **Sigue siendo EL bloqueo**: es lo único que impide elegir número.
3. **Tráfico**: el user avisa de que en su ciudad *"no solemos tener mucho tráfico"*, así que el
   corpus está sesgado a marcha fluida. Un atasco largo mete al coche en la banda 10-40 km/h durante
   mucho más rato y es el peor caso de esta regla — hace falta al menos una muestra.

## Diseño candidato (NO decidido — depende de lo anterior)

La hipótesis a contrastar es que el discriminador correcto **no es un contador, es una fracción
sostenida**: un ciclista pedalea de forma continua durante todo el trayecto (la cadencia acompaña a
casi todos los fixes en movimiento), mientras que un coche produce ráfagas esporádicas en baches y
frenadas. Bajo esa hipótesis:

- `fastMotionStepEvents >= 12` es un **umbral absoluto** que un trayecto urbano largo alcanza por
  acumulación aunque la señal sea rarísima — 12 pasos en 20 minutos de coche no es una cadencia, es
  ruido de fondo;
- la magnitud correcta sería *fracción de fixes en movimiento con pasos concurrentes* sobre una
  ventana, que en bici tiende al 100 % y en coche a un porcentaje bajo.

Ambas cifras salen del corpus del punto 1 — por eso el diseño va después de la medición, no antes.

Alternativa más barata a evaluar en paralelo: **subir `egressStepMaxSpeedMps` sólo para la lectura de
cadencia**, desacoplándolo del techo de egress (hoy el mismo valor sirve para dos preguntas
distintas, ver el comentario *"Deliberately independent of the counting gate"* en
`EgressEvidence::cadenceQualifies`).

## Criterio de éxito

- Los dos trayectos en coche del 26-08, pasados por replay, **no** disparan `pedal cadence`.
- El trayecto del **25-09** (Oppo, sesión `1790330246055`, cadencias a 24,6-37,9 km/h) tampoco lo
  dispara — y su confirm sale `confirmed_steps+egress`, no `CONFIRM_DEGRADED_PROMPT`.
- El trayecto de bici del 18-08 (Oppo, sesión `1787077943062`, 6 min, cero eventos AR) **sí** lo
  dispara — es el caso que la regla existe para cazar y no puede perderse.
- Los Toruños (16-08) sigue vetado.

## Relacionado

- Padre: `docs/backlog/det-human-powered-veto-must-be-revocable-001.md`
- Origen de la regla: `DET-MOTOR-PROOF-001` · `DET-BIKE-NOT-A-CAR-001`
- Guard hermano ya cerrado: `DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001` (la cadencia no puede
  acusar una vez el ancla está clavada) — mismo patrón, recortar el ALCANCE de la regla en vez de
  sus números.
