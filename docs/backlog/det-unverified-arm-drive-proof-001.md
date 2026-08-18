# DET-UNVERIFIED-ARM-DRIVE-PROOF-001 · La salida del pin se puede PROBAR midiendo, no solo heredarla del evento que armó

**Estado:** ✅ Done · master `e9186a52` (ff-only, 16-08; rama y worktree borrados, sin pushear)
· 1185 tests verdes · ⏳ APK a campo

## Problema

Field 15-08, Redmi (uid `WZB7oftWLDY1toGJrDwoRHnnYHx2`), **dos viajes perdidos seguidos**. El Oppo
hizo los mismos dos trayectos y puso sus dos pines (`a6f6693d` 21:32, `2eef76a6` 02:17).

**Ida — sesión `1786821963745` (21:26:05Z→21:45:56Z), `aborted_unattended_no_drive`, sin pin.**

MIUI no entregó el EXIT de geocerca: la sesión nació 74 s tarde por sentry-wake
(`ARM:SIGNIFICANT_MOTION geof=644635b4`), con evidencia `self_observed`, y el coche ya iba a 1,1 km
de casa.

| Hora (Z) | Dato |
|---|---|
| 21:26:24 | 1er fix — 28,8 km/h **acc 56,2 m** (>50 → no creíble) |
| 21:26:30 | 25,6 km/h **acc 8,9 m** · a **1 088 m** del pin de casa |
| 21:26:38 | 29,6 km/h acc 23,4 m |
| 21:26:40 | 30,1 km/h acc 24,2 m |
| 21:26:46 | 29,1 km/h **acc 87,7 m** (>50 → no creíble) |
| 21:30:16 | `ACTIVITY_TRANSITION IN_VEHICLE EXIT` |
| 21:30:51 | `DECISION PROMPT_SHOWN low_medium(exit=true)` — degradado a pregunta |
| 21:59:00 | `DECISION UNATTENDED_NO_DRIVE_NUDGE` → nudge sin contestar → **plaza real perdida** |

`corroboratesDrive` ([DET-DRIVE-PROOF-001]) falló por 4 segundos: exige un fix de retrospectiva de
**20–60 s**, y el más viejo estaba a 16 s. El único que caía en la ventana (21:26:46, 22 s) traía
87,7 m de precisión. `maxSpeedMps` se quedó en 0 → "esta sesión no vio conducir".

`EvaluateShortHopDriveProofUseCase` ([DET-SHORT-HOP-PROOF-001]) **habría acertado**: 3 fixes
consecutivos creíbles (21:26:30/38/40) a >1 km del pin, muy por encima de los 400 m, de la valla,
de ambos sobres de precisión y del alcance peatonal a los 25–41 s de armar. Pero abre con
`if (!verifiedDeparture) return false` y el arm era `self_observed`. **El desplazamiento medido
nunca se miró.**

**Vuelta — sin sesión ni evento (02:11Z).** Consecuencia directa: sin pin en el destino no hay
geocerca que romper, y el armado por AR abre con `session?.geofenceId ?: return NoSession`
(`EvaluateArEnterArmUseCase:76`). No hubo ningún disparador posible. **No es OEM-kill**: la app
estaba viva a las 21:59 ejecutando su propio timeout.

## Doctrina violada

> *El evento NOMINA, solo el movimiento MEDIDO confirma.*

Aquí está del revés: la calidad del **evento de armado** veta un movimiento que sí se midió (1,1 km
desde el pin del propio coche, a 30 km/h, imposible andando). El veredicto de salida depende de
**cuándo llegó el fix**, no de lo que midió: el mismo fix de 25,6 km/h con 8,9 m de precisión, si lo
hubiera muestreado el EXIT, habría dado `VerifiedBySpeed` (`config.isCredibleDrivingSpeed` lo cumple
de sobra). Llegó 25 s tarde por el stream y no contó como evidencia de nada.

## Señales / datos disponibles

- `ParkingDetectionState.recentFixes` + el fix en curso: velocidad y precisión de cada medición.
- `departureAnchor` (el pin que el coche dejó) y `departureFenceRadiusMeters` — ya cableados por
  [DET-SHORT-HOP-PROOF-001], y presentes también en un arm por sentry-wake (lleva su `geof=`).
- `config.isCredibleDrivingSpeed(...)` — el predicado exacto del verificador de arm.
- `config.isBeyondPedestrianReach(...)` — la física de [DET-RIDE-PROOF-001].

## Diseño

**El invariante mal ubicado:** "salida verificada" está modelado como propiedad *del evento que armó*
(un parámetro inmutable de `invoke`), cuando es un **estado de la sesión** que puede ganarse con
evidencia que llegue después — por el worker o por la propia medición.

1. **`EvaluateMeasuredDepartureUseCase`** (puro, `commonMain/domain/usecase/detection/`). Un fix
   prueba la salida del pin cuando reúne los **mismos dos hechos** que el arm verificado:
   - `config.isCredibleDrivingSpeed(fix)` — lo que probaba el fix de una toma del EXIT;
   - `config.isBeyondPedestrianReach(distancia al pin, transcurrido desde el arm)` más la valla y
     ambos sobres de precisión — lo que probaba el EXIT mismo (el teléfono cruzó el radio).

   Anclado **al pin**, nunca al primer fix de la sesión: el espejismo Doppler de 2026-07-27 (45 m/s
   con precisión declarada de 5 m, teléfono quieto en casa) mide ~0 m desde su propio pin → rechazado
   por construcción. El autobús tomado a 200 m del coche también cae: para cuando alcanza velocidad
   de conducción, el alcance peatonal ya cubre esa distancia.

2. **`ParkingDetectionState.departureProven`** — booleano latch, un solo significado
   ("el coche probó que dejó el pin"), **tres** fuentes: evidencia de arm verificada,
   `notifyDepartureConfirmed()` (upgrade tardío [DET-G-05]) y el nuevo veredicto medido.

3. La prueba de hop corto lee `departureProven`, no el parámetro de arm. Esto cierra además un
   segundo agujero de la misma línea: hoy `notifyDepartureConfirmed()` sube la etiqueta a
   `verified_late` pero **la prueba por desplazamiento nunca se entera**, porque lee el parámetro
   inmutable (`CoordinatorParkingDetector:741`).

**Sin reetiquetar la provenance.** El pin sigue guardando `armEvidence = self_observed`: nada externo
verificó nada, lo medimos nosotros. Y no hace falta — la política de evidencia débil ya confirma en
silencio cuando la sesión vio conducir (`weakEvidenceOnly = … && !sessionSawDriving`,
`EvaluateParkingDecisionUseCase:192`). Desbloquear la estadística basta; mentir sobre el origen del
pin, no.

## Criterio de éxito

- ✅ `should_keep_the_park_when_the_stream_itself_measured_the_departure_from_the_pin` replica la
  forma de campo (arm `Unverified` + 3 fixes creíbles a >1,1 km del pin en 10 s, demasiado juntos
  para la ventana de 20–60 s) y muere en el timeout no atendido — **verificado ROJO sin el fix**
  (revertida la línea del gate: `aborted_unattended_no_drive`, 0 pines), verde con él.
- ✅ Anti-resurrección: `should_not_prove_a_drive_by_displacement_when_nothing_measured_a_departure`
  — MISMA geometría a velocidad de peatón → sigue sin plantar pin.
- ✅ El espejismo indoor de 2026-07-27 sigue rechazado por construcción (test unitario del
  evaluador: 45 m/s con precisión 5 m a 55 m del pin → falso).
- ⏳ En campo: un viaje con el EXIT comido por MIUI acaba en pin, no en nudge.

## Estado de ejecución

- `EvaluateMeasuredDepartureUseCase` + 8 tests unitarios · `ParkingDetectionState.departureProven`
  (latch, 3 fuentes) · parámetro de la prueba de hop renombrado `verifiedDeparture` →
  `departureProven` (el nombre viejo mentía sobre lo que preguntaba).
- **1185 tests verdes** (master venía de 1176), `compileMockDebugKotlinAndroid` +
  `compileProdDebugKotlinAndroid` OK.
- Sin strings nuevos, sin pantalla/estado/routing nuevo → Dev Catalog y los 9 locales no aplican.
- `detectionPath` / `armEvidence` sin tocar: no hay camino de confirmación nuevo.

## Consumidores auditados

`grep -rn "isVerifiedDeparture\|currentArmEvidence" composeApp/src --include=*.kt`

| Sitio | Qué asume | Clasificación |
|---|---|---|
| `CoordinatorParkingDetector:476` (seed `hasEverReachedDrivingSpeed` al entrar) | arm-time | **Exento** — es el seed para sesiones que NO pueden observar la conducción. Aquí el stream sí la observa y la vía normal (`hasJustReachedSpeed`) ya la marcó en campo. |
| `CoordinatorParkingDetector:741` (short-hop proof) | arm-time inmutable | **Cerrado** — pasa a `departureProven`. |
| `CoordinatorParkingDetector:1365,1743` (`evidenceLabel` → política de evidencia débil) | etiqueta honesta | **Cubierto por convergencia** — sigue `self_observed`; el gate que importa (`sessionSawDriving`) se abre solo al probar la conducción. |
| `staleExitDelivery` (presupuesto de no-movimiento) | "solo relevante para evidencia no verificada" | **Exento** — lee `hasEverReachedDrivingSpeed`, no la etiqueta. |
| `ArmEvidence.isVerifiedLabel` (repark-plausibility en `ConfirmParkingUseCase`) | etiquetas verificadas | **Exento** — no se reetiqueta nada. |
