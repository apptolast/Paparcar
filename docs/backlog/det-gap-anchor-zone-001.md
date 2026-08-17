# DET-GAP-ANCHOR-ZONE-001 · un agujero GPS tiene DURACIÓN, así que la duda que crea está acotada

**Estado:** 🟡 Implementado, **sin commitear** · 1217 tests verdes · regresión verificada ROJA sin el
fix · rama `bugfix/DET-GAP-ANCHOR-ZONE-001-gap-hole-bounded-zone` · worktree `../Paparcar-gap-anchor-zone`

## Problema

Field 17-08-2026, salida de Góndola sobre las 14:00 (hora España). Los dos móviles en el mismo coche
y el mismo viaje, destino **Calle Bahía de Alcudia 4** (El Puerto de Santa María):

| Móvil | sesión | arm | outcome | Pin |
|---|---|---|---|---|
| **Oppo** (control) | `1786969793141` | `AR_VEHICLE_ENTER` lag 137 ms, `enter_at_car` | `confirmed_kinematic+egress` · 25,1 min · 58 km/h · 91/368 fix | ✅ 14:54 |
| **Redmi** | `1786970028118` | `GEOFENCE_EXIT` **entregado 2.672 m tarde** | `aborted_unattended_gap_anchor` · 36,0 min · 50 km/h · 34/429 fix · 353 pasos | ❌ |

Los dos puntos finales distan **40 m**. El Redmi tenía conducción medida, ancla fijada, pasos y
despertares con datos durante todo el trayecto — y acabó sin plaza.

### La traza, con el agujero que la mató

El stream del Redmi venía roto: **9 huecos de 23 a 127 s en 15 minutos** (MIUI). El decisivo:

```
12:51:19Z  fix v=11,6 m/s (42 km/h)  acc=7m      ← el coche, visto conduciendo
              ↕  126 s SIN FIXES  ·  301 m de desplazamiento
12:53:25Z  fix v=0                  acc=116m     ← abre parada ⇒ GAP-ENTERED
12:53:48Z  fix v=0,4                acc=11m      ← refina el ancla (misma parada)
12:53:49Z  primer paso (stepCount=1) — el usuario baja y anda
12:54:42Z  DECISION CONFIRM_DEGRADED_PROMPT      ← correcto: el tinte pregunta, no pincha
              …nadie contesta durante 15 min…
13:09:44Z  DECISION UNATTENDED_GAP_ANCHOR_NUDGE  stopMs=880.000 (14,7 min quieto, acc 12-20 m)
13:09:50Z  aborted_unattended_gap_anchor         → sin pin
```

La parada de las 12:53:25 se abrió al otro lado del hueco, así que el ancla nació con el tinte
`gap_entered`, y esa es **la única rama del evaluador desatendido que devuelve `Ask` sin condiciones**
(`EvaluateUnattendedParkingSaveUseCase.kt:226`).

## Doctrina violada

*"Plaza perdida con datos = bug NUESTRO"* (contrato de detección). Hubo despertares con datos durante
36 minutos y un viaje real de 25 min a 50 km/h. No es el OS.

La justificación escrita de la rama es *"el error hacia delante no es acotable — el coche pudo
conducir arbitrariamente lejos dentro del hueco"*, y **eso es falso**:

1. **El hueco duró 126 s.** El móvil sólo pudo ir del coche al ancla **andando** dentro de ese hueco,
   así que el desplazamiento coche→ancla está acotado por `duración × velocidad de peatón`. Es una
   cota, no un infinito. Un booleano no distingue un hueco de 45 s de uno de una hora; una duración sí.
2. **La hipótesis de "fix de paso" está muerta en esta sesión.** El FP que creó la rama (Av. Sanlúcar,
   29-07: un hueco de 100 s acabó en un fix a velocidad 0 a mitad de ruta y se pinchó 315 m antes del
   parking real) tiene una firma distinta: **allí el coche siguió conduciendo**. Aquí el móvil se quedó
   14,7 minutos clavado en el mismo punto con accuracy de 12 m. Un coche que pasa por un sitio no
   descansa 15 minutos en él.

Es **el mismo defecto que DET-WALK-ENTERED-ANCHOR-ZONE-001**, un tinte más allá: `walk_entered` ya
recibió una cota medida (`anchorWalkInSpanMeters`); `gap_entered` se quedó siendo un booleano sin cota.
La tesis de `AnchorTrust` (DET-VERDICT-NOT-PREDICATE-001) — *la duda y su cota nacen juntas* —
apareciendo como bug de campo antes del refactor.

## Señales / datos disponibles

Todo estaba ya en el estado, salvo la magnitud del hueco:

| Señal | Estado hoy |
|---|---|
| `stopEnteredAfterGap: Boolean` | ✅ existe — pero **tira la duración**, que es justo la cota |
| `anchorGapEnteredAtCapture: Boolean` | ✅ el tinte sellado con el ancla |
| `stoppedDurationMs` | ✅ 880.000 ms en el veredicto (lo usa ya T1) |
| `config.maxPedestrianSpeedMps` | ✅ 2,5 m/s — sin constante nueva |
| `config.sustainedStopForSaveMs` | ✅ 5 min — el mismo umbral que T1 |
| `config.unattendedZoneMaxRadiusMeters` | ✅ 250 m, con doctrina propia: *"se guarda al cap y la tarjeta pide refinar"* |

## Diseño

**El invariante:** *un tinte de ancla no es un booleano, es una duda CON su cota. Donde la cota existe,
la plaza se guarda degradando la precisión; sólo se pierde cuando la cota no existe.*

1. **El estado guarda la MAGNITUD, no el hecho.** `stopEnteredAfterGap: Boolean` →
   `stopEnteredAfterGapMs: Long` y `anchorGapEnteredAtCapture: Boolean` → `anchorGapMsAtCapture: Long`
   (0 = sin tinte). El booleano se conserva como **propiedad derivada** (`anchorGapMsAtCapture > 0L`)
   para que los 5 consumidores existentes sigan leyendo lo mismo desde una sola fuente de verdad.
2. **El veredicto acota.** La rama `GAP_ANCHOR` pasa de `Ask` incondicional a `zoneOrAsk`:
   - `doubtMeters = (anchorGapMs / 1000) × maxPedestrianSpeedMps` — lo que el peatón pudo andar dentro
     del hueco. Conservador a propósito: asume que TODO el hueco fue caminando.
   - `bounded = stoppedDurationMs ≥ sustainedStopForSaveMs` — el reposo sostenido mata la hipótesis
     del fix de paso, que es lo único que la rama protegía.
   - El radio final ya lo compone `saveUnattendedZone` (`max(min 60 m, accuracy del centro, doubt)`
     con cap de 250 m). Para esta sesión: 315 m de duda → zona de 250 m centrada en el ancla real.
3. **`egressExceedsWalkReach` sube por encima de los dos tintes de precisión.** No es una duda sobre
   la precisión: es **evidencia de ausencia** (la posición desbordó los pasos ⇒ un vehículo cubrió ese
   terreno). Ninguna zona es honesta sobre un sitio que el coche demostrablemente abandonó, así que la
   comprobación pertenece ANTES de cualquier rama que dibuje zona, no después. Hoy está al final y ya
   quedaba tapada por la rama `walk_entered` de T1. Mover la regla a su sitio en vez de duplicarla en
   la rama nueva; el cambio sólo puede volver veredictos MÁS conservadores (zona → pregunta).

Descartado a propósito: **usar los pasos para acotar más fino.** Tentador (en esta sesión
`anchorStepEventsAtCapture = 0` con un contador que 24 s después demostró estar vivo con 353 pasos, lo
que daría una zona del tamaño de la accuracy en vez de 250 m), pero durante un hueco de MIUI el proceso
puede estar congelado y que el delta del contador aflore o no depende de la re-registración del sensor.
No es verificable desde la traza, y equivocarse pincha un punto preciso en un sitio posiblemente
erróneo — exactamente el FP que la rama existe para evitar. Si el campo demuestra que el testigo de
pasos es fiable a través de huecos, se aprieta en un follow-up.

## Criterio de éxito

- Regresión ROJA sin el fix: la sesión `1786970028118` (hueco 126 s, reposo 880 s) da `SaveZone`, no `Ask`.
- El FP de Av. Sanlúcar sigue muerto: hueco + ancla gap-entered **sin** reposo sostenido ⇒ `Ask`.
- Un ancla gap-entered con el coche demostrablemente ido (`egressExceedsWalkReach`) ⇒ `Ask`, nunca zona.
- En campo: el Redmi repite un viaje con el stream roto y **queda pin** (zona), o el diagnóstico dice
  en una línea por qué no.

## Consumidores auditados

`grep -rn "stopEnteredAfterGap\|anchorGapEnteredAtCapture\|anchorGapEntered"`:

| Sitio | Qué hace | Veredicto |
|---|---|---|
| `Coordinator:2059-2065` | calcula el tinte al abrir parada | **cerrado** — ahora guarda la duración medida |
| `Coordinator:2171-2173` | sella el tinte al (re)atar el ancla | **cerrado** — sella los ms |
| `Coordinator:2334-2335` | limpia el tinte con el ancla | **cerrado** — limpia a 0L |
| `Coordinator:1047` `1063-1064` | user-confirm: un ancla gap-entered no gana el pin | **exento con razón** — un "Sí" del usuario tiene mejor evidencia que una cota estadística; sigue re-anclando en la parada del usuario |
| `Coordinator:1083` | log | **exento** — sólo diagnóstico (se enriquece con los ms) |
| `Coordinator:1131` → `UnattendedSaveInput` | la vía que mordió | **cerrado** — pasa `anchorGapMs` |
| `Coordinator:1689` `1248` → `ParkingDecisionInput` | confirm silencioso: degrada a prompt | **exento con razón** — es el comportamiento CORRECTO y observado en campo (`CONFIRM_DEGRADED_PROMPT` a las 12:54:42). Preguntar cuando hay un humano despierto es mejor que una zona; la zona es el último recurso cuando nadie contestó |
| `EvaluateParkingDecisionUseCase:84,224` | el booleano en el input del confirm | **exento** — ese evaluador sólo necesita el hecho, no la magnitud |

## Registro

- 2026-08-17 — abierto tras el diagnóstico del field 17-08 (Redmi pierde Calle Bahía de Alcudia 4).
- 2026-08-17 — implementado. **1217 tests verdes** (master venía de 1212). Regresión verificada
  **ROJA** revirtiendo la rama a `Ask` incondicional: caen los 4 tests que fijan el invariante nuevo
  (`should_saveBoundedZone_when_gapEnteredAnchorCameToASustainedRest`,
  `should_scaleTheDoubtWithTheHole_when_holesDiffer`, `should_preferTheGapBound_when_bothTaintsHold`
  y el de integración del coordinator). `compileMockDebugKotlinAndroid` verde; sin pantallas ni
  estados nuevos, así que el Dev Catalog no cambia. Sin strings nuevos.
  ⚠️ Un test de integración existente **cambió de sentido a propósito**:
  `should_nudge_notPin_when_unattended_timeout_finds_gap_entered_anchor` →
  `should_saveBoundedZone_when_unattended_timeout_finds_gap_entered_anchor_at_rest`. Su fixture
  (hueco de 120 s + reposo largo) ES el caso de campo; era la conducta vieja lo que fijaba.
  El vocabulario de traza gana `confirmed_unattended_zone_gap_anchor` / `unattended_zone_gap_anchor`
  como `detectionPath` — instancia del patrón que ya introdujo T1, no una forma nueva.
