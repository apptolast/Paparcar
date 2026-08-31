# DET-TWO-DISPATCHES-OF-ONE-DEPARTURE-READ-DIFFERENT-STATE-001

**Estado:** 🟡 **Backlog — pendiente de DEFINIR y de CONFIRMAR daño.** Sin rama de código, sin
implementación. Este doc solo fija el hecho medido y descarta la solución equivocada.

> ⛔ **Este ticket se llamaba `DET-A-DEPARTURE-DISPATCHES-ONCE-PER-FENCE-001` y ese nombre estaba
> mal.** Prejuzgaba la solución —deduplicar— y la deduplicación es precisamente lo que los datos
> desaconsejan. Renombrado para describir el HECHO, no el arreglo.

## El hecho, medido

Field 30-08 21:27 (Oppo). La MISMA salida del MISMO geofence (`785dabe3`) se despachó **dos veces**,
con **596 ms** de diferencia, por dos puertas distintas — una la disparó entrar en una geocerca, otra
el AR:

```
21:27:33.967  SafetyNet ▶ dispatching departure (…)                    ← puerta 1: [geofence-enter]
21:27:33.968            → chaining parking backfill … arrivalWalk=12 steps      ← PLANTA el pin
21:27:34.563  SafetyNet ▶ dispatching departure (…)                    ← puerta 2: [ar-enter]
21:27:34.566            ⊘ arrival NOT placed … arrivalWalk=0 steps     ← VETA
```

## ⛔ Por qué deduplicar NO es la respuesta (objeción del user, 31-08)

> *«No veo por qué omitir uno si se encola otro; el que omitimos podría ser el bueno.»*

**Y es exactamente lo que pasó.** El primer despacho leyó `arrivalWalk=12` y **plantó el pin
equivocado**; el segundo leyó `arrivalWalk=0` y **vetó, que era lo correcto**. Un dedupe *"quédate
con el primero"* habría conservado justo el malo. *"Quédate con el último"* sería igual de arbitrario:
elegiría por orden de llegada, que es el defecto, no el criterio.

## Lo que el dato revela de verdad

Las dos evaluaciones **no son independientes**: el estado que leen es **mutable y se consume**. El
primer despacho gastó el presupuesto de pasos, y por eso el segundo vio `0`. O sea, el segundo no
acertó por tener mejor información — acertó **como efecto colateral de que el primero ya había
gastado la suya**.

Así que el problema no es *"se despacha dos veces"*. Es:

> **Un mismo hecho se juzga dos veces contra un estado que la primera evaluación ya ha alterado, y
> el veredicto depende del orden de llegada.**

Cualquier arreglo tiene que responder antes a: **¿qué es «el hecho»?** ¿La salida de esa geocerca?
¿Cada entrega del OS? Y **¿cómo se evalúa contra un estado estable**, en lugar de contra uno que la
propia evaluación consume?

## Daño confirmado hoy: ninguno activo

- 🟢 **El pin fantasma ya no ocurre**: `DET-BACKFILL-MUST-NOT-PIN-A-MOVING-CAR-001` (`29a9b0a5`) cede
  la llegada a la detección viva, así que esa vía ya no planta.
- 🔎 **Buscado y NO encontrado**: en el trazado del 30-08 el worker de salida corrió dos veces para
  `785dabe3` (21:27:34.092 y .876) y **ningún efecto se duplicó** — ni dos publicaciones de plaza ni
  dos cierres. Ambas pasadas eran `preconfirmed`, así que solo procesaron.
- 🟡 **Riesgo plausible, sin observar**: la cadena se encola con `beginUniqueWork(…, REPLACE)`, así
  que un segundo despacho **reemplaza** la del primero. En un despacho NO preconfirmado —los que
  reintentan a 15/30/60 s midiendo velocidad— reiniciaría los intentos desde cero y retrasaría
  liberar la plaza. Mecanismo claro, caso no medido.

## Antes de implementar nada, CONFIRMAR

1. ¿Ocurre el doble despacho en despachos **no** preconfirmados? (los del trazado eran los dos
   preconfirmados, que es el caso benigno).
2. ¿Se llega a reiniciar de verdad la cadena de reintentos, y cuánto retrasa liberar la plaza?
3. ¿Hay algún efecto no idempotente aguas abajo que dos pasadas puedan duplicar?

Sin al menos una de las tres respondida con datos, esto es deuda estructural sin daño demostrado —
y hay trabajo con daño medido por delante (p. ej. la latencia de los EXIT de geocerca, que saltó
8 veces en un solo día).
