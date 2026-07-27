# DET-NODRIVE-ZONE-001 — timeout sin conducción probada pero con egress vivo masivo → zona acotada, no nudge-only

**Estado:** ✅ IMPLEMENTADO 2026-07-27 en `bugfix/DET-NODRIVE-ZONE-001` (encima del stack
DET-DEPART-PROOF-001) — suite prod completa verde (966 tests: replay
`Trace_RedmiLateExitHome001` 1:1 de la sesión de campo → zona en el ancla; espejismo y paseo
sin señal vehicular siguen nudge-only). ⏳ commit (go-ahead), APK, field-test.
**Origen:** FN 2026-07-27 20:36 (Redmi, aparcamiento REAL en casa), sesión `1785177396935`.

## Forense (telemetría pap-26 + logcat en vivo durante el cierre)

Viaje real de 4,3 km desde la plaza anterior (`-6.2780`, confirmada 20:12) hasta casa. MIUI
entregó el `GEOFENCE_EXIT` de esa plaza a **4.110 m** de la geocerca (acc 104 m) — la sesión
nació a las 20:36:36, cuando el viaje ya había terminado.

| hora | evento |
|---|---|
| 20:36:42 | Único tramo de conducción visto: 3 fixes, vmax 25 km/h (la maniobra final) |
| 20:37:48 | Fixes se asientan en `36.6039,-6.2308` — el coche descansa; ancla ahí |
| 20:37:55→20:39 | **176 pasos con contador VIVO**, desplazamiento real ~50 m hasta casa |
| 20:38:23 | `steps+egress` degrada a prompt (`weakEvidenceOnly`: arm `verified_enter` sin `sessionSawDriving`) — notificación posteada |
| 20:38:25 | AR `IN_VEHICLE EXIT` (`vehicleExitConfirmed=true` el resto de la sesión) |
| 20:38→20:53 | El prompt SÍ se mostró (confirmado por el usuario 28-07); quedó sin respuesta los 15 min |
| 20:53:30 | Timeout → rama `unattended_no_drive` (`maxSpeedMps=0.0` proven) → descarta el prompt y posta el nudge "¿Dónde has aparcado?" (el 2º aviso que vio el usuario) → **sin pin ni zona** → `aborted_unattended_no_drive` |

`corroboratesDrive` nunca latcheó (correcto: 3 fixes / ~100 m < 150 m netos en ventana 20–60 s),
así que `maxSpeedMps` (proven) quedó a 0 aunque `pendingMaxSpeedMps=7.02` y
`hasEverReachedDrivingSpeed=true`. Comparar con la sesión de las 20:12 del MISMO móvil: EXIT a
188 m → 28 driving fixes → `confirmed_steps+egress` silencioso. La única diferencia fue la
latencia de entrega del EXIT.

## Agujero

La rama `unattended timeout WITHOUT measured driving` (`CoordinatorParkingDetector.kt` ~L911)
sale con nudge-only **antes** de llegar a la escalera de zonas acotadas de
DET-FROZEN-COUNTER-001 (que solo cubre `unpinned_anchor` / `egress_mismatch` /
`walk_entered`, todas tras pasar `measuredDriving`). Es la política correcta para el caso que la
creó (pin en el salón del 10-07: sesión post-viaje cuyo ancla sigue al peatón) — pero trata igual
a dos sesiones que la evidencia SÍ separa:

- **Espejismo 27-07 14:56** (el FP que motivó DET-DRIVE-PROOF-001): deriva indoor, **1 paso**,
  sin AR EXIT → nudge-only es lo honesto. ✅
- **Esta sesión**: ancla donde los fixes se asentaron ANTES del primer paso, **176 pasos vivos**
  con ~50 m de desplazamiento real, AR `IN_VEHICLE EXIT`, fix crudo de conducción a 7 m/s →
  el aparcamiento real existe y los pasos ACOTAN dónde. Nudge-only = parking perdido
  (contrato: parking perdido con datos = bug NUESTRO).

## Fix propuesto (extender la escalera FROZEN-COUNTER a la rama no-drive, con candados)

En la rama `unattended_no_drive`, antes del nudge-only, intentar `saveUnattendedZone`
(`source = "no_drive_egress"`) SOLO si se cumple la conjunción completa:

1. **Ancla puesta** (`bestStopLocation != null`) — centro de la zona.
2. **Contador provablemente vivo + egress a escala humana**: `sessionSawSteps` y
   `stepCount ≥ anchorLockEgressSteps` **y** suelo de desplazamiento real de
   DET-WALK-FLOOR-001 (los pasos solos no; el espejismo tuvo 1 paso, pero un contador
   fantasioso podría dar más).
3. **Señal vehicular independiente del arm**: `vehicleExitConfirmed` (AR EXIT en sesión)
   **o** `pendingMaxSpeedMps ≥ minimumTripSpeedMps` con accuracy credible. (El arm no vale:
   el evento nomina.)
4. Radio = `max(anclaAposiciónActual, stepCount × anchorStrideMeters)` clampeado al mínimo/máximo
   de zona de DET-HONEST-CLOSE-001 — mismo contrato: la verdad queda dentro.

Reliability baja (`reliabilityUnattendedSave` o inferior), `detectionPath` propio
(`unattended_no_drive_zone` — provenance DET-PIN-PROVENANCE-001), telemetría `Decision`
con outcome propio. El nudge se mantiene como fallback si falla cualquier candado o el save.

### Anti-resurrección (los dos FP que esta rama NO puede reabrir)
- **Espejismo en casa (27-07 14:56)**: muere por candado 2 (1 paso, sin desplazamiento andado).
- **Bus/taxi a casa** (EXIT tardío de la plaza vieja + bajada del bus + caminata): pasa 2 y 3
  (AR EXIT dispara igual al bajar de un bus) — la zona plantaría el coche en casa estando en la
  plaza vieja. Mitigación: la zona NO libera la plaza anterior por sí sola (supersede solo con
  confirmación de usuario o conducción probada — revisar interacción con supersede-distancia), y
  el artefacto es pregunta-con-mapa (DET-ASK-STATE-001), no hecho. **Decidido en
  implementación**: reliability baja + card-como-ask basta — un guard "no supersede la sesión
  activa anterior" mataría exactamente el caso real (el EXIT tardío de la plaza vieja implica
  casi siempre una sesión anterior activa que SÍ hay que superseder). Residual del bus aceptado:
  zona corregible con un tap vs. parking real perdido con datos (contrato: eso es bug nuestro).

### Replay
Bajar los 279 eventos de la sesión `1785177396935` a un fixture del harness
(`Trace_RedmiLateExitHome001`) — es la traza canónica de "EXIT tardío + egress vivo masivo".
Verificar que el espejismo (`Trace` del 14:56) sigue muriendo en nudge-only.

## Ficheros previstos
- `CoordinatorParkingDetector.kt` (rama no-drive: intento de zona antes del nudge)
- `ParkingDetectionConfig.kt` (si hace falta constante nueva para el candado 3; reutilizar
  `anchorLockEgressSteps`, `anchorStrideMeters`, walk-floor)
- `CoordinatorParkingDetectorTest.kt` (+ fixture replay nuevo)
- `docs/detection/PARKING-DETECTION.md` (changelog, misma tarea)

## Relacionados
- Hermano de: DET-FROZEN-COUNTER-001 (patrón zona-en-vez-de-perder), DET-DRIVE-PROOF-001 (el
  gate que esta rama respeta), DET-HONEST-CLOSE-001 (semántica de zona), DET-ASK-STATE-001 (UX
  del ask), DET-NUDGE-PERSIST-001 (el prompt desaparecido de la bandeja es el otro 50 % de este FN).
- Raíz OEM: entrega tardía de geocercas MIUI (ver reference market research — geocercas se
  borran/retrasan; no arreglable desde app).
