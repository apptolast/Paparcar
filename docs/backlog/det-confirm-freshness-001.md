# DET-CONFIRM-FRESHNESS-001 — La evidencia de un confirm debe seguir siendo cierta al plantar el pin

**Estado:** EN CURSO (2026-07-24) · rama `bugfix/DET-CONFIRM-FRESHNESS-001` (apilada sobre `bugfix/DET-STEP-BUDGET-ORIGIN-001`)
**Origen:** field-test 23/24-jul-2026 (2 FP Redmi + 1 FN Redmi, telemetría completa en
`diagnostics/WZB7…/sessions/{1784829489071, 1784839287970, 1784846935112}`). Ver memoria
`project_det_field_2026_07_23`.

## Invariante

Dos verdades que hoy el coordinator viola, cada una en un sitio distinto:

1. **Un contador de pasos VIVO que calla mientras la posición se mueve es evidencia de COCHE.**
   Un contador MUERTO que calla no dice nada (protección Camelias-Oppo intacta). Hoy tratamos el
   silencio de pasos igual en ambos casos.
2. **Una decisión de confirm se ata a la evidencia del momento en que nace, y al plantar el pin se
   re-valida que siga siendo cierta.** Hoy el hold finaliza con el snapshot de hace 2 min sin mirar
   dónde está el coche, y el guardado desatendido no re-comprueba el desplazamiento.

## Los tres incidentes (23-jul, Redmi, build con TODOS los fixes previos)

### FP-1 · "Bodegas Osborne" 20:03 (semáforo; real = Calle Aurora, que el Oppo clavó)
`confirmed_kinematic+egress` con ancla = parada de 27 s en un semáforo:
- Quick-freeze ([DET-SHORT-TRIP-FREEZE-001], 7 fixes parados) congeló el ancla en el semáforo.
- La búsqueda de plaza posterior (1.7–4.4 m/s, 6–16 km/h por calles estrechas, GPS infra-midiendo)
  nunca llegó a `minimumTripSpeedMps=5` con acc≤50 → el ancla congelada ("solo conducción real la
  mueve") ignoró 160 m de creep **con 0 pasos y contador vivo**.
- El creep alimentó `kinematicEgressFixes` (fixes <5 m/s acc≤50 cuentan como "paseo") y parió el
  `egressOriginFix` a 30 m del ancla → todos los guards pasaron → pin en el semáforo.

### FP-2 · Calle Abeto 22:52 (recogida del primo — repite 18-07 por un agujero NUEVO)
`confirmed_steps+egress` con ancla = parada de recogida (4.5 min):
- 19 pasos incidentales (todos `stopped=True`) + un fix de deriva acc=127 m a 36 m del ancla
  (speed 0) abren el confirm tentativo a las 22:50:01 → hold 2 min [DET-C-02].
- El arranque real produce UN solo fix rodando (10.68 m/s, 190 m en 9 s) con **acc=71 > 50** → el
  descarte del hold (exige speed>bar Y acc≤50) queda ciego. Después, 95 s de apagón GPS conduciendo.
- 22:52:02: expira el hold con el coche parado en OTRO semáforo a 570 m → finaliza con el snapshot.
  El techo peatonal del 18-07 nunca ve los 570 m: mató el re-confirm, no el primer confirm retenido.

### FN · Vista Hermosa 01:08 (sin pin; el Oppo confirmó a 30 m)
El sistema DETECTÓ bien: ancla en el coche (locked por 45 pasos de egress), `steps+egress` a la
01:09:14… degradado a **prompt** porque el ancla estaba marcada "entrada andando":
- `walkFixesSinceDriving` cuenta CUALQUIER fix en banda peatonal sin exigir pasos → los fixes de la
  **propia maniobra de aparcar** (deceleración 13.5→2.65→1.49→1.44→0, acc 37–56) taintaron
  `anchorWalkFixesAtCapture` > 3 **con 0 pasos contados y contador vivo**.
- Prompt a la 01:09 (madrugada, nadie lo mira) → timeout 15 min → el guard walk-entered (el mismo
  taint falso) rechazó el guardado desatendido → nudge y sin pin. El ancla era PERFECTA todo el rato.

## Piezas

### A · Hold re-valida al expirar (FP-2)
En el settle automático (`heldMs >= confirmHoldMs`, NO el camino user-yes):
`d(pending.location → fix actual) > stepCount×stride + acc(pending) + acc(fix) + egressBirthFloorMeters`
→ el coche recorrió una distancia que los pasos no explican DESDE el confirm tentativo → era una
parada de recogida/recado → **descartar el pending y seguir detectando** (mismo destino que el
descarte por arrancar; la sesión sigue hacia el park real). Telemetría: `HOLD_STALE_DISCARDED`.
- El mismo techo se añade al guardado desatendido por timeout (`egressExceedsWalkReach`) → nudge
  (`aborted_unattended_vehicular_egress`).
- El hold-watchdog (finalize por reloj, stream muerto de hambre) NO re-valida: no hay fix contra el
  que hacerlo; se documenta la asimetría.

### B · Descongelar el ancla ante partida sin pasos con contador vivo (FP-1)
Estado nuevo: `sessionSawSteps` (el sensor disparó ≥1 vez esta sesión) y
`pinnedSteplessMovingFixes`. Con ancla PINNED y contador vivo, un fix cuenta si
`speed ≥ clearBestStopSpeedMps` **y** `d(ancla→fix) > acc(ancla)+acc(fix)+minEgressDisplacementMeters`
(escapa las envolventes — un blip Doppler parado no cuenta). Cualquier evento STEP resetea el
contador. Al llegar a `frozenAnchorSteplessDepartureFixes` (default 4) → veredicto COCHE
(`effectiveDriving`): ancla+pasos+fase se limpian y la parada real re-captura limpio.
- Contador MUDO (`!sessionSawSteps`) → jamás dispara → laundering Camelias-Oppo sigue imposible.
- Replay semáforo: 4º fix cualificado a las 20:01:18 → unfreeze justo antes de la parada real
  (20:01:25) → re-captura en Aurora → confirm silencioso correcto (paridad con el Oppo).

### C · El taint walk-entered exige corroboración (FN) — exención de maniobra ACOTADA
Estampar en la captura del ancla, junto a `anchorWalkFixesAtCapture`: `anchorStepEventsAtCapture`
(EVENTOS de paso desde la última conducción resuelta — no `stepCount`, cuyo gate ignora pasos
andando sin ancla) y `anchorSawStepsAtCapture` (¿contador ya probado vivo?). Helper único
`isAnchorWalkEntered(s)`: el taint se EXIME solo si se cumplen las TRES —
`stepEvents == 0 && sawSteps && walkFixes <= maneuverEntryMaxWalkFixes(8)`.
- El tope de longitud es estructural, lo enseñó el replay de Camelias en rojo: su contador estuvo
  vivo ANTES (73 pasos) y se quedó MUDO justo durante el paseo de vuelta (~13 fixes) — "vivo en
  algún momento" no implica "vivo ahora", así que el silencio solo es fiable en tramos CORTOS.
  Una maniobra de aparcar son 4–6 fixes (~30 s); un paseo, una docena larga.
- Reemplaza las 3 lecturas: fast-confirm, scoring, guard del timeout desatendido.
- Coste aceptado y documentado: un paseo corto (≤8 fixes ≈ ≤40 m) con contador mudo-a-ratos puede
  pinear a esa distancia acotada del coche — mejor que el FN sistemático de cada maniobra lenta.

## Composición (por qué las 3 piezas juntas)
- Semáforo: B re-ancla en la plaza real; C evita que el creep (contado como walkFixes) degrade a
  prompt el confirm correcto posterior. Resultado = pin silencioso en Aurora.
- Abeto: A descarta el confirm retenido; la sesión sigue viva hacia el park real del Paseo Marítimo.
- Vista Hermosa: C elimina el falso taint → confirm silencioso a la 01:09 en el ancla buena
  (ni prompt ni timeout). A protege además el guardado desatendido si el coche se mueve esperando.

## No se toca
- BluetoothDetectionStrategy (estrategias nunca se mezclan).
- Camino user-yes del hold (el usuario manda, cero guards).
- Techo peatonal, egress-birth, safety-net: intactos; las piezas los reutilizan.

## Validación
- Tests unitarios por pieza con los números reales de las 3 trazas + regresiones (Camelias mute,
  Gavia 68 m/8 pasos, Gloria/Glorieta silencio 110≥45).
- Suite común completa verde. Field-test en ambos móviles antes de merge.

## FN Oppo 05:00 (mismo field-test, FUERA de este ticket)
Vuelta a casa sin arm: ColorOS congeló la app tras la 04:15; el EXIT se entregó a las 16:37 (11 h
tarde). No es bug del coordinator — es el patrón OEM-kill conocido con permisos al mínimo; el plan
sigue en `project_det_field_2026_07_18_night` (nudge BackgroundKillSuspected + setup guiado Oppo).
