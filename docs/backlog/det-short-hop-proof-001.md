# DET-SHORT-HOP-PROOF-001 · Un salto corto prueba su conducción por DESPLAZAMIENTO desde el pin

**Estado:** 🔵 En progreso · rama `bugfix/DET-SHORT-HOP-PROOF-001-displacement-drive-proof` · worktree `../Paparcar-short-hop`

## Problema
Field 14-08 ~23:00 (Oppo, Ford Focus): "volví a donde estaba y no se puso". Sesión
`1786740987649`: armado PUNTUAL por `GEOFENCE_EXIT` verificado a 159 m del coche, 16,4 min,
**303 fixes**, vmax **30 km/h**, **104 pasos** de egress al llegar → `drive 3/303` →
`aborted_unattended_no_drive` + nudge no atendido. El Redmi confirmó el MISMO aparcamiento a las
23:05 (su sesión venía de la ida, con 73 km/h ya corroborados).

## Doctrina violada
Contrato de detección: *hubo despertar CON datos y el parking se perdió → bug NUESTRO*. Un guard
pensado contra el mirage (DET-DRIVE-PROOF-001) se había convertido en guard contra una conducción
REAL: su forma (fix a velocidad + ventana 20–60 s cubriendo 150 m) es inalcanzable en un salto
nocturno stop-and-go con stream escaso, así que `maxSpeedMps` se quedó en 0 — y esa estadística es
la que TODAS las vías de confirmación leen como "¿esta sesión vio conducción?".

## Diseño (sistema, no parche)
Segunda vía de prueba, INDEPENDIENTE y pura: `EvaluateShortHopDriveProofUseCase` (commonMain).
Desbloquea la misma estadística cuando la posición está, durante `shortHopProofFixes` (3) fixes
creíbles consecutivos, a más de `shortHopProofFloorMeters` (400 m) **del pin que el coche dejó**,
más allá del radio de la valla y de ambas envolventes de accuracy, y fuera del alcance peatonal
(`isBeyondPedestrianReach`, la misma física de DET-RIDE-PROOF-001) para el tiempo transcurrido.

**Por qué el ancla es el PIN y no el primer fix de la sesión:** el pin es una posición que el coche
ocupó de verdad, y ancla ahí hace imposible por construcción la clase mirage — un móvil derivando
en interiores junto a su pin mide ~0 m de desplazamiento respecto a él. Con el primer fix como
origen, el mirage del 27-07 habría usado su propio burst como origen y habría llamado "conducción"
a la vuelta a casa.

Requisito de arm verificado: el evento NOMINA (necesario, nunca suficiente). Sin evidencia externa
de salida, la misma geometría la produce un paseo largo o un autobús → no prueba nada.

## Criterio de éxito
- ✅ 9 tests del use case puro (incluidos: sin pin, arm no verificado, distancia caminable en el
  tiempo transcurrido, un solo fix, fix degradado, deriva junto al pin, justo bajo el suelo).
- ✅ Test de regresión end-to-end que replica la forma de campo (stream de 90 s sin ventana válida,
  llegada continua): **verificado ROJO sin el fix** (`aborted_unattended_no_drive` + nudge) y verde
  con él (`confirmed_unattended_timeout`).
- ✅ Test anti-resurrección: misma geometría con arm `self_observed` → no guarda nada.
- ✅ 1172 tests · `compileProdDebug` + `compileMockDebug`.
- ⏳ Campo: repetir el salto corto casa→destino→casa de noche.

## Consumidores auditados (grep `maxSpeedMps` / `driveProven`, 15-08)
La estadística desbloqueada es la misma que ya existía; se auditó cada lector:
- `measuredDriving` en el unattended timeout (L1078) → **la vía donde mordió el FN**. Cerrado.
- `enter-arm step veto` (L568): exige `maxSpeedMps < minimumTripSpeedMps` con `VERIFIED_ENTER` y 0
  pasos en los primeros ms. Un salto corto no llega a 400 m del pin en esa ventana → intacto. Exento.
- `lastFinishedMaxSpeedMps` (L1431) → escalera del honest-close: recibe la misma verdad mejorada
  (la sesión SÍ condujo). Cubierto por convergencia.
- `tripMaxSpeedMps` en `runConfirm` (L1625) → provenance persistida; ahora refleja el vmax real
  (30 km/h) en vez de 0. Cubierto.
- `sessionSawDriving` en `EvaluateParkingDecisionUseCase` → lee `maxSpeedKmh` derivado. Cubierto.
- Guards de calidad del ANCLA (gap-entered / walk-entered / unpinned / egress-born-away): NO se
  tocan. Probar la conducción dice que el coche fue a algún sitio, no que sepamos dónde paró —
  verificado en test (con hueco antes de la parada sigue `aborted_unattended_gap_anchor`). Exentos
  con razón.

## Mock / galería
Sin pantalla, estado MVI ni condición de routing nuevos → sin cambios en Dev Catalog. Sin strings
nuevos (no hay copy al usuario: la vía desbloquea confirmaciones existentes).
