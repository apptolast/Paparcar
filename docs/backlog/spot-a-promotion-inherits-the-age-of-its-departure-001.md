# SPOT-A-PROMOTION-INHERITS-THE-AGE-OF-ITS-DEPARTURE-001 · promover una salida deducida no le devuelve la juventud a la plaza

**Estado:** 📋 Abierto · sin rama · descubierto el 14-09-2026 durante
[DET-AN-EXIT-IS-DATED-BY-THE-FIX-THAT-CROSSED-IT-001](det-an-exit-is-dated-by-the-fix-that-crossed-it-001.md)

> ### Verificado en código el 15-09 contra `d7880ac7` — vivo, con DOS mitigaciones que ya existen
>
> El mecanismo está confirmado en dos sitios:
> - `ReportSpotWorker.kt:71` declara *"The TTL is anchored to the RELEASE time (enqueue)"* y
>   `:163` estampa `KEY_TIMESTAMP to Clock.System.now()…` **en el momento del encolado**. En la
>   promoción ese momento es la promoción, no la salida deducida.
> - `FinalizeDeducedDepartureUseCase.kt:95-96`: *"Same spot id → the worker's REPLACE policy rewrites
>   the SAME document with the full TTL"*, y la llamada a `reportSpotReleased` (`:131-148`) **no pasa
>   ningún instante**: pasa `deducedAtMs` a `clearActiveParkingSession` y no a la publicación.
>
> Lo que ya está tapado (no volver a escribirlo en el diseño): una promoción de **forma Zone**
> conserva radio, confianza capada y **TTL provisional corto**; y un pin **desmentido por un CONNECT
> BT en otro sitio** no promueve nada [DET-A-BT-CONNECT-KNOWS-WHERE-THE-CAR-IS-001]. El agujero real
> que queda es el caso normal (forma punto): plaza con vida completa de 2 h anclada a la promoción.
> **El arreglo es un parámetro, no una tubería**: hacer que la publicación acepte el instante de la
> salida y que el worker lo use como ancla del TTL en vez de `now`.

## Problema

`FinalizeDeducedDepartureUseCase` es la **segunda** puerta a la comunidad y es la única que no
pregunta la edad de lo que publica.

Una salida DEDUCIDA publica una plaza provisional (TTL 12 min, [DET-HANDOFF-NOT-MANUAL-001 §B]) y
marca la sesión con `provisionalDepartureAtMs`. Cuando después se MIDE una conducción de ese
vehículo, esta use case promueve: reescribe el mismo documento con `provisional = false`, o sea con
la vida **completa de 2 h**… anclada al instante de la promoción, porque `ReportSpotWorker` estampa
`KEY_TIMESTAMP = now` al encolar.

Nada entre la deducción y la promoción mira el reloj:

```kotlin
// FinalizeDeducedDepartureUseCase:88 — el dato existe, se usa para un log y para cerrar la sesión…
val now = Clock.System.now().toEpochMilliseconds()
PaparcarLogger.d(TAG, "drive measured ${now - deducedAtMs}ms after a deduced departure — promoting…")
// …y :132, donde se decide publicar, no lo consulta
val publishes = !pinIsContradicted && shape.publishes()
```

El mensaje de log ya calcula `now - deducedAtMs` — la edad exacta que haría falta— y la imprime en
vez de decidir con ella. Mismo patrón que el `fixAge` de `GeofenceExitWitnessReceiver` en el ticket
padre.

## Escenario

Sesión deducida a las 10:00 → plaza provisional publicada, muere sola a las 10:12. El usuario vuelve
al coche a las 16:00 y arranca → se mide conducción → la promoción **republica la plaza del sitio de
las 10:00** con 2 h de vida por delante. La plaza que anuncia lleva seis horas ocupada, o libre por
razones que nadie midió; en cualquier caso no es la que el coche acaba de liberar.

## Doctrina violada

- *Fallo asimétrico*: anunciar una plaza de hace seis horas le cuesta a un desconocido un viaje en
  balde. No anunciarla cuesta una plaza que nadie podía creerse.
- La misma que cerró el ticket padre: **el instante que data una plaza es el medido, no aquel en que
  la app llega a la conclusión**. Aquí el medido existe y tiene nombre (`deducedAtMs`).

## Diseño (esbozo)

`freedSpotIsStillThere(exitAtMs = deducedAtMs, nowMs = now, config)` antes de promover. Si no pasa:
no se republica —la provisional ya expiró sola, que es exactamente lo que su TTL corto existe para
garantizar— y la sesión se cierra igual con `endedAtMs = deducedAtMs`, como ya hace.

⚠️ Decidir qué pasa con el `clearActiveParkingSession` cuando la promoción no publica: hoy cerrar y
publicar van juntos en esta puerta, y son dos compromisos distintos (uno con el usuario, otro con
desconocidos) — el ticket padre ya separó ese par en `ProcessConfirmedDepartureUseCase` vía
`publishSpot`.

## Consumidores a barrer

- `RetractDeducedDepartureUseCase` — la tercera puerta; comprobar si retracta con criterio de edad.
- `ProcessConfirmedDepartureUseCase:260` (`gaveSpot`) — lee `session.provisionalDepartureAtMs` para
  decidir si esta sesión dio plaza a la comunidad; si la promoción puede no publicar, esa cuenta
  cambia.
