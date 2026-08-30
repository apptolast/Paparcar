# DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001 · un coche a 12 km/h por el centro no es una bicicleta

> ⚠️ **Cruzado con el rediseño (30-08): INTACTO, y SUBE de prioridad.** No lo toca ninguna pieza
> (§6.2 #2 y la Pieza 2 tratan el `humanPowered` por OMISIÓN; esto condena a un coche REAL por su
> velocidad urbana). La Pieza 4 lo **encarece**: hoy un veto falso cuesta un pin impreciso, y con
> «prompt sin contestar → cerrar sin pin» pasará a costar la plaza entera.
> Dato nuevo 29-08: el veto de cadencia heredó la velocidad ESPEJISMO (7,71 m/s) dentro del FP de la
> parafarmacia — si la Pieza 1 arregla `Measured`, este veto mejora de rebote.
> Ver `docs/detection/REDESIGN-DETECTION-SYSTEM.md` §9.3.

**Estado:** 🟡 Abierto, **bloqueado por medición** · sin rama · sin worktree
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

## Doctrina violada

Ninguna directamente: el veto respeta *fallo asimétrico* (degrada a pregunta, no planta un pin
fantasma). Lo que rompe es el **presupuesto de coste**: `HumanPoweredRide.kt:42-43` justifica el
latch diciendo *"one tap, the direction asymmetric failure allows"*. El 26-08 demuestra que el coste
real no es un toque, es **la plaza entera** cuando nadie contesta el prompt en 15 minutos. Un veto
que se dispara en 2 de 2 viajes en coche no está comprando seguridad, está fabricando falsos
negativos.

## ⛔ Bloqueado ADEMÁS por un hueco de telemetría (descubierto 27-08)

El corpus del apartado siguiente **no se puede recoger con el build actual**. Los pasos que activan
la cadencia son justo los que ningún diagnóstico graba: se dan conduciendo, con el ancla limpiada, y
esa combinación no cae en ninguna de las tres ramas que emiten `✦ step`. Se descubrió al construir el
replay del ticket padre — la traza salía verde con y sin el fix hasta que los doce eventos se
reconstruyeron a mano.

Medir la *fracción de fixes en movimiento con pasos concurrentes* exige ver el numerador. Así que
**`DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001` va primero**, y es barato.

## Por qué está bloqueado

Cualquier número que se elija hoy se elige con **una noche de datos y cero trayectos en bici**.
Apretar de más devuelve el incidente que creó la regla: 2026-08-16, Samsung SM-A536B, 59 min de bici
hasta Los Toruños a 38 km/h de pico, geocerca rota a 352 m y un Mercedes re-pinchado a 4,8 km del
coche real. Ese modo de fallo **cuesta un coche**; éste cuesta una plaza. No se toca a ojo.

## Qué hay que medir antes de tocar nada

1. **Coches, varios dispositivos, varios recorridos urbanos.** Por sesión: nº de fixes en movimiento,
   cuántos llevan pasos concurrentes, y la fracción entre ambos. El Redmi y el Oppo por separado —
   la diferencia 8 vs 1 dice que el ruido del podómetro es del aparato, no del trayecto.
2. **Al menos una bici real** con el build actual, misma instrumentación. Sin la distribución de la
   bici no hay listón que poner.
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
- El trayecto de bici del 18-08 (Oppo, sesión `1787077943062`, 6 min, cero eventos AR) **sí** lo
  dispara — es el caso que la regla existe para cazar y no puede perderse.
- Los Toruños (16-08) sigue vetado.

## Relacionado

- Padre: `docs/backlog/det-human-powered-veto-must-be-revocable-001.md`
- Origen de la regla: `DET-MOTOR-PROOF-001` · `DET-BIKE-NOT-A-CAR-001`
- Guard hermano ya cerrado: `DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001` (la cadencia no puede
  acusar una vez el ancla está clavada) — mismo patrón, recortar el ALCANCE de la regla en vez de
  sus números.
