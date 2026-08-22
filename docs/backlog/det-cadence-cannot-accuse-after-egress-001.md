# DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001 · la caminata de salida no es pedaleo

**Estado:** ✅ Done · rama `bugfix/DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001-cadence` ·
worktree `../Paparcar-cadence-egress`

## Problema

Field 2026-08-22, viaje 1 (Góndola → Camelias), Redmi `2201117TY`. Un viaje en **coche de 75 km/h
con 57 fixes de conducción** fue dictaminado *"propulsado por humanos"*, degradó a pregunta, y el
"sí" del usuario acabó plantando el pin dentro de la casa.

Del `parkdiag` del propio móvil:

```
14:46:43  ⚓ anchor FROZEN — drive-entered stop matured     ← el coche YA está aparcado
14:47:12  ✦ step #35 (egress walk, anchor set)
14:47:18  🔒 anchor LOCKED (steps=37) — ignoring walking-range speed 4.27 m/s (< 5.0)
14:47:19  ♲ pedal cadence — 12 steps concurrent with 4 above-ceiling fixes → human-powered ride
14:47:32  ？ confirm degraded to user prompt (steps+egress, reason=human_powered)
```

El veto disparó **36 segundos después de congelar el ancla**, y los pasos que contó los etiqueta el
propio log como `egress walk, anchor set`. Es la caminata de salida del coche, con el GPS malo del
Redmi (10–55 m) produciendo fixes de 4,27 m/s.

## Doctrina violada

`isHumanPoweredRide` ya lleva escrito el principio correcto —
*"MEASURED MOTOR REFUTES EVERYTHING BELOW … the measurement gets the last word, wherever the claim
came from — the AR stamp below AND the cadence latch above it"*
(`DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001`). Pero esa refutación exige
`sustainedMotorBandMs >= sustainedDriveProofMs` (30 s **sostenidos** por encima de
`motorProofSpeedMps` = 11,1 m/s = 40 km/h) y el stream hambriento del Redmi no lo acumuló pese a
picar 75 km/h. La refutación existe y no llegó; el latido de cadencia sí.

La doctrina que se rompe es más simple y no necesita umbrales: **después de aparcar, "pasos a la vez
que fixes rápidos" es la firma ESPERADA de alejarse andando con GPS ruidoso — es exactamente lo
contrario de una prueba de pedaleo.** El mismo patrón significa cosas opuestas antes y después del
ancla, y el contador no miraba cuál de las dos estaba viviendo.

## Señales / datos disponibles

`cadenceStep` (`CoordinatorParkingDetector.kt:746`) exige: fix creíble ·
`egressStepMaxSpeedMps` (3,0) ≤ velocidad < `motorProofSpeedMps` (11,1) · paso fresco respecto al
fix. Nada sobre en qué punto del viaje estamos.

La banda 3,0–11,1 m/s son 10,8–40 km/h. Una persona andando NO la ocupa… salvo que su GPS esté
mintiendo, que es justo lo que hace un móvil en una calle estrecha después de aparcar. Por eso el
techo `motorProofSpeedMps` (que `DET-MOTORWAY-TRIP-JUDGED-BICYCLE-001 §A` añadió contra la vibración
de carretera a 131 km/h) no cubre este caso: 4,27 m/s cae limpiamente dentro de la banda.

El estado ya sabe si el coche ha llegado: `isAnchorPinned(s)` (`CPD:2191`) =
`isAnchorLocked(s) || (bestStopLocation != null && anchorFrozen)`.

## Diseño

Una condición más en `cadenceStep`, con el predicado que ya existe:

```kotlin
val cadenceStep = !isAnchorPinned(s) && s.lastFixCredible && …
```

**Un paso no puede ser una pedalada mientras el ancla está clavada.** Clavada significa que la
sesión ya atestiguó dónde descansa el coche — por parada madurada o por pasos de egress. A partir de
ahí los pies en marcha son la salida, no la propulsión.

Es el mismo movimiento que hizo §A con el techo de velocidad, un piso en vez de un techo: la
concurrencia paso+fix solo prueba pedaleo **mientras el viaje sigue siendo un viaje**.

### Alternativas descartadas

- **Bajar `sustainedDriveProofMs` o `motorProofSpeedMps`** para que la refutación por motor llegue en
  streams pobres: parchea el síntoma en un número calibrado contra dos trazas reales de bici, y
  dejaría el veto disparando igual en cualquier egress con GPS peor.
- **Exigir más pasos/fixes** (`pedalCadenceMinStepEvents`): hoy latió con 12 y 4; una caminata larga
  da 100. Subir el listón solo retrasa el mismo error.

## Criterio de éxito

- Test: pasos concurrentes con fixes de 4,3 m/s **después** de que el ancla se clave → no laten
  cadencia, la confirmación no se degrada.
- Test de regresión: los mismos pasos y fixes **antes** del ancla → siguen latiendo (la bici real de
  campo 2026-08-18 se detecta igual).
- Campo: un viaje normal en coche con el Redmi no debe volver a preguntar "¿has aparcado?" por
  `reason=human_powered`.

## Consumidores auditados

`grep -rn "fastMotionStepEvents\|fastMotionStepFixes\|isHumanPoweredRide" composeApp/src --include=*.kt`

| Consumidor | Clasificación |
|---|---|
| `isHumanPoweredRide` desde el coordinator (confirmación en fase CANDIDATE) | **cerrado** — los contadores ya no crecen tras el ancla |
| `isHumanPoweredRide` desde `EvaluateUnattendedParkingSaveUseCase` (timeout) | **cerrado por convergencia** — lee los mismos dos contadores; se cura en el origen, no en cada veredicto |
| Latido de traza `PEDAL_CADENCE_LATCHED` (`CPD:773`) | **cerrado** — cuelga de los mismos contadores, así que deja de emitirse en falso |
| Refutación por motor sostenido (`sustainedMotorBandMs`) | **exento con razón** — sigue por encima del latido y sin tocar; es la que protege el caso autovía |
| Vía AR (`bicycleRideAtMs` / `vehicleRideAtMs`) | **exento** — otra fuente, con sus propias reglas de caducidad y supersesión |
| Perfil del vehículo (`VehicleType.BIKE/SCOOTER`) | **exento** — respuesta del garaje, ninguna medición la discute |

Que los dos veredictos se curen desde un solo sitio es justo lo que pedía la regla: el invariante
vive donde se CUENTA la evidencia, no en cada quien la lee.

## Verificación de que el test discrimina

El test pasó a la primera, así que se comprobó que no fuera un test complaciente: neutralizando el
guard (`val cadenceStep = true && …`) **falla**, y con el guard pasa. El refactor profundo tiene
anotado como bug #8 *"tres tests «fast path» pasan con comentarios FALSOS"*; este no se suma.

## Estado final

- ✅ **1396 tests verdes** (`testProdDebugUnitTest`), incluido el nuevo.
- ✅ `compileProdDebugKotlinAndroid` + `compileMockDebugKotlinAndroid`.
- Sin strings nuevos ni pantallas nuevas → no toca i18n ni Dev Catalog. El efecto en la UI es
  **una pregunta que deja de aparecer**, no una pantalla nueva.
- `detectionPath` / `armEvidence` sin caminos nuevos.
- ⏳ Campo: un viaje normal en coche con el Redmi sin pregunta por `reason=human_powered`, y una
  bici de verdad que siga degradando a pregunta.
