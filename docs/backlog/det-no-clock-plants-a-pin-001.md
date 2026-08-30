# DET-NO-CLOCK-PLANTS-A-PIN-001 · que un reloj venza no es evidencia

**Estado:** 🔵 En progreso · rama `feature/DET-NO-CLOCK-PLANTS-A-PIN-001-no-clock-plants-a-pin` ·
worktree `../Paparcar-no-clock` · **apilada sobre `DET-DRIVING-EVIDENCE-VALUE-OBJECT-001`**

Pieza 4 del rediseño (`docs/detection/REDESIGN-DETECTION-SYSTEM.md` §8) + la **obligación 5** del
cruce §9.4. Cierra el fallo 1.3 (el pin a 142 m) y absorbe entero
`DET-STARVED-HOLD-HAS-NO-WITNESS-001`.

## Problema

Detección tiene siete relojes y **cuatro plantan un pin al vencer** (§6.3). `HoldLifecycle.kt:16` lo
admite por escrito: *«Two of these exits plant a pin with no fix to justify it»*.

Y el fallo de campo del 30-08 01:49 es el quinto: un viaje real acabó en un **agujero de GPS de
2 min 16 s**, el ancla quedó en el primer fix del otro lado —un punto que el coche estaba
**pasando**—, la app lo detectó (`anchor_gap_entered`), se negó a confirmar y **preguntó**, que es
correcto. Nadie contestó a la 1:34 de la mañana. Y 15 minutos después el guardado desatendido plantó
la zona **sobre esa misma ancla que ya había declarado inválida**, a 157 m del coche.

## Doctrina violada

*Que un reloj venza significa «no llegó más evidencia», y eso no es evidencia.* Más el fallo
asimétrico: ante la duda se pregunta, no se planta.

## Señales / datos disponibles — medidas

Sesión `825dcb60`, 216 fixes tras el agujero:

| medida | valor |
|---|---|
| fixes posteriores al ancla que vuelven a <100 m de ella | **0 de 215** |
| distancia mínima posterior · media | 116 m · 157 m |
| fixes con acc ≤12 m en la misma ventana de parada | **18**, dispersión media **4 m** |
| distancia de ese cúmulo al centro guardado | **159 m** |
| ventana del cúmulo | 01:34:50 → 01:39:22, **10 min antes** de plantar |

El dato bueno estaba en memoria, en la misma sesión viva, cuando el reloj plantó el malo.

## Diseño

### A · Ningún reloj planta sin conducción MEDIDA

Los dos salidas del hold que plantan sin fix detrás (`STARVED`, `SESSION_ENDED`) y el camino de
confirm más débil (ventana de `vehicleExit`) exigen ahora `DrivingEvidence.Measured`:

| reloj | antes | ahora |
|---|---|---|
| watchdog `STARVED` (2 min 30 s) | confirma sin fix | planta **sólo si `Measured`**; si no, cierra sin pin y lo dice (`STARVED_UNWITNESSED`) |
| `SESSION_ENDED` con hold vivo | confirma sin fix | igual (`SESSION_ENDED_UNWITNESSED`) |
| ventana `vehicleExit` (2 min) | confirma con AR EXIT + 18 m | exige `Measured` |

Los dos desenlaces nuevos son **entradas propias del enum**, no un flag: en forensics la pregunta
siempre es *por qué no hay pin*, y una salida muda no se distingue de un crash.

### B · Obligación 5 — la zona se centra en el reposo PRESENCIADO

`AnchorTrust.witnessedRestFix` = el fix más preciso que la ventana de parada llegó a ver. La zona del
timeout desatendido se centra ahí en vez de en el ancla, **y sólo si es estrictamente más preciso**
(igualdad no es mejora; mover el centro sin ganancia medida haría que dependiera del orden de la
lista, no de la evidencia).

⚠️ **El radio NO se encoge.** La duda del agujero es *dónde paró el COCHE*; un fix mejor de *dónde
descansa el TELÉFONO* no responde a esa pregunta. Sólo se mueve el centro. En la sesión de campo:
zona de 250 m centrada a ~10 m del coche, en vez de a 157 m.

## Dos desviaciones DELIBERADAS respecto a la tabla de la Pieza 4

1. **`confirmHoldMs` (SETTLED) no se toca.** La tabla pedía «confirma sólo si sigue habiendo
   `Measured`». Pero el settle ocurre **sobre un fix** y detrás lleva la decisión del evaluador, que
   desde `DET-DRIVING-EVIDENCE-VALUE-OBJECT-001` ya exige `Measured` salvo que el armado traiga su
   propia medición. Lo único que bloquearía de más son los armados `manual` / `inherited_drive` /
   `verified_speed` — y bloquear a `manual` rompe `DET-ASSERTION-OUTRANKS-INFERENCE-001`: la palabra
   del usuario no es una inferencia. Además `credibleFixCount` y la banda sólo CRECEN, así que
   `Measured` no puede degradarse durante un hold.
2. **El prompt sin responder no «cierra sin pin».** La tabla lo pedía; el user aprobó la obligación 5
   en su lugar. Cerrar sin pin tira un cúmulo de 18 fixes con 4 m de dispersión y pierde el
   aparcamiento entero — y §9.3 ya avisaba de que P3 encarece cada veto falso de «pin impreciso» a
   «plaza perdida». La regla implementada es la de precedencia: *un cierre honesto se centra en el
   mejor reposo que la sesión presenció; sólo se va sin pin cuando no hay ninguno*.

## Los tres caveats heredados de `DET-STARVED-HOLD-HAS-NO-WITNESS-001` (§9.1)

Los tres, escritos dentro del test nuevo para que no se pierdan:

1. ⛔ `confirmHoldMs > 0` es **costura de test, no opción de runtime** — `CoordinatorParkingDetectorTest`,
   `DetectionTraceReplayTest` y `StagePrecedenceCharacterizationTest` lo ponen a 0 para apagar el
   watchdog. Quien «limpie» esa guarda rompe tres ficheros.
2. El test **no estaba bloqueado por esperar 2 min 30 s**: `runTest` usa tiempo virtual.
3. ⛔ `pendingConfirm === pending` se compara **por identidad**, a propósito.

⚠️ **Corrección al ticket viejo:** decía que la rama `STARVED` *«no tiene un solo test»*. Lo que no
tiene es una aserción sobre el **valor del enum**; su conducta sí estaba cubierta por
`should_finalize_starved_hold_by_clock_when_gps_dies_after_parking`. Ese test es justo el que se puso
rojo al implementar esto.

### Y por qué ese test se modificó, en vez de adaptarse el código a él

Su escenario declarado siempre fue *«el egress COMÚN: aparcas, entras al edificio, el GPS muere»* —
que ocurre **después de conducir a algún sitio**. Su stream era **un solo fix a 6 m/s cubriendo
111 m, sin timestamps**: no es un viaje bajo ninguna medida que la app aplique. Se le ha puesto el
viaje que su propio comentario describe (5 fixes creíbles, 10 s de separación, 222 m) y su mitad
contraria vive ahora en un test nuevo con el stream mínimo original.

## Consumidores auditados

| sitio | clasificación |
|---|---|
| watchdog `STARVED` (`CoordinatorParkingDetector`) | **cerrado** |
| epílogo `SESSION_ENDED` | **cerrado** |
| `confirmNow`, rama `windowElapsed && hadVehicleExit` | **cerrado** |
| `HoldResolutionStage.settle` | **exento con razón** (desviación 1) |
| rama `WALK_ENTERED_ANCHOR` del guardado desatendido | **exento con razón**: un ancla walk-entered ES el sitio donde está el peatón, y el mejor fix de esa ventana también. Recentrar no acerca el círculo al coche; sólo lo movería sin ganancia |
| ramas de zona por precisión (`bounded`) | **cubierto por convergencia**: comparten `zoneOrAsk`, y el centro se decide en `bestWitnessedCenter` |
| `maxNoMovementMs` y hermanos | **exento**: ABORTAN, no plantan — son los relojes sanos |

## Criterio de éxito

- ✅ Test nuevo: hold famélico sin conducción medida → **0 guardados**.
- ✅ Test existente (con su viaje ya explícito): hold famélico CON conducción medida → sigue plantando.
- ✅ Test nuevo: zona `GAP_ANCHOR` con reposo presenciado mejor → centro en el reposo, **radio intacto**.
- ✅ Test nuevo: reposo presenciado no más preciso → el ancla conserva el centro.
- ✅ **1.815 tests en verde.**
