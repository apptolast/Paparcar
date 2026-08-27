# DET-CADENCE-STEPS-ARE-INVISIBLE-TO-TELEMETRY-001 · el veto de bici decide con pasos que ningún diagnóstico registra

**Estado:** 🟡 Abierto, sin rama · **coste bajo, valor alto** — es un cambio de logging, no de decisión
**Origen:** descubierto el 2026-08-27 al construir el replay de
`DET-HUMAN-POWERED-VETO-MUST-BE-REVOCABLE-001`. La primera versión de la traza **pasaba también sin
el fix**, porque los eventos que causaron el bug no estaban en el log.

## Problema

`CoordinatorParkingDetector` emite `✦ step #N` (y su gemelo remoto `DetectionEvent.Step`) en
exactamente tres ramas:

```kotlin
if (!updated.session.driveAuthorized)           → "pre-drive, false-ENTER candidate"
else if (updated.anchorTrust.stopStartedAt != null) → "stopped"
else if (updated.anchorTrust.anchor != null)    → "egress walk, anchor set"
```

Un paso dado **conduciendo, con el ancla ya limpiada y la conducción autorizada** no cae en ninguna
de las tres: se procesa, incrementa `fastMotionStepEvents` y **no deja rastro en ningún sitio**.

Y esa es, exactamente, la forma de un paso de cadencia. `cadenceQualifies` exige
`!anchorPinned && lastFixSpeedMps >= egressStepMaxSpeedMps` — es decir, moviéndose y sin ancla
clavada. **Los únicos eventos que pueden activar el veto de tracción humana son justo los que el
diagnóstico no graba.**

## Cómo se descubrió

La sesión del 26-08 (Redmi, Valdés→Góndola) escribe esto:

```
20:22:11.527  ♲ pedal cadence — 12 steps concurrent with 3 above-ceiling fixes
```

…y entre el seed de conducción de las 20:21:12 y esa línea, el log **no contiene ni una sola** línea
`✦ step`. Las dos afirmaciones sólo son simultáneamente ciertas si los doce pasos tomaron la rama
muda. Al reconstruir la traza a partir del log, el replay salía verde con y sin el fix: sin esos doce
eventos la cadencia nunca se dispara y no hay bug que reproducir.

La reconstrucción por aritmética (4 pasos contra cada uno de los 3 últimos fixes en banda, dentro de
la ventana de frescura de 10 s) sí reprodujo el falso negativo. Pero es reconstrucción, y va marcada
como tal dentro de `Trace_Gondola2608CadenceVeto.kt`.

## Doctrina violada

`DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C` ya cerró este mismo defecto un nivel más arriba, y lo dejó
escrito en el propio código:

> *"A veto that can decide a session silently is the defect"* — y por eso el latch `PEDAL_CADENCE_LATCHED`
> se emite al trace, no sólo a logcat.

Se registró el **veredicto** y se dejaron mudas sus **entradas**. El resultado es que el latch se
puede leer pero no auditar: sabemos que hubo 12 pasos, no cuándo, no contra qué fixes, no si la
concurrencia era rítmica o casual. Justo lo que hace falta para calibrar
`DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001`.

## Consecuencia práctica, y por qué esto va antes que la calibración

`DET-PEDAL-CADENCE-CANNOT-CONVICT-A-CAR-IN-TRAFFIC-001` está bloqueado por medición: hay que medir
*fracción de fixes en movimiento con pasos concurrentes*, en coche y en bici. **Ese corpus no se puede
recoger hoy**, porque el numerador de esa fracción es precisamente lo que no se graba.

Así que este ticket es la precondición del otro. Es también el más barato de los tres: no cambia
ninguna decisión, sólo hace visible una entrada.

## Diseño

Una cuarta rama de logging para el paso que hoy cae al vacío, y su gemelo en `DetectionEvent.Step`
(o un campo `cadence: Boolean` en el evento existente, que evita duplicar tipos y deja el conteo
remoto intacto). Debe llevar los datos que hacen falta para juzgarlo después: velocidad del fix
contra el que se acredita, su precisión, y si contó como cadencia o no.

⚠️ **Volumen**: en bici serían decenas de eventos por minuto. El `parkdiag` aguanta (5 rotaciones,
~150 h desde `DET-PARKDIAG-KEEP-MORE-HISTORY-001`), pero el lane remoto no debería recibirlos uno a
uno — probablemente un rollup por fix, no un evento por paso.

## Criterio de éxito

- Replayar un viaje nuevo reconstruye la cadencia **sin inventar un solo evento**.
- El bloque marcado `⚠ RECONSTRUCTED` de `Trace_Gondola2608CadenceVeto.kt` deja de ser necesario para
  trazas futuras (la del 26-08 se queda como está: sus datos ya se perdieron).

## Relacionado

- Padre: `docs/backlog/det-human-powered-veto-must-be-revocable-001.md`
- **Bloquea a:** `docs/backlog/det-pedal-cadence-cannot-convict-a-car-in-traffic-001.md`
- Mismo defecto un nivel arriba, ya cerrado: `DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §C`
