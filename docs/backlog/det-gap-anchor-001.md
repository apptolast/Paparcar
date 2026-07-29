# DET-GAP-ANCHOR-001 — un ancla nacida ciega (tras hueco GPS) no merece un pin exacto: el ancla exige presenciar la llegada al reposo

**Estado:** ✅ EN MASTER 2026-07-29 (`90693697`, merge FF + push; rama y worktree borrados;
`feature/DET-RESIDENT-FGS-001` rebasada encima) — suite prod completa verde (971 tests; 5
nuevos: replay Redmi Av. Sanlúcar → prompt, control sin hueco → confirm normal, timeout
desatendido → nudge, y 2 unitarios del evaluador). ⏳ APK + field-test (trayecto nocturno Redmi).
**Origen:** ticket 2026-07-27 (field-test 26-07, sesión `1785092508564`, pin 72 m off en casa de
la hermana). Reactivado por el **FP 2026-07-29 05:24** (Redmi, uid `WZB7of…`, sesión
`1785294055249`): pin en `36.6049,-6.2282` — un punto por el que se PASÓ conduciendo; el
aparcamiento real fue Calle la Angelita 3B, **315 m** más allá.

## Forense 29-07 (telemetría pap-26, eventos fix a fix)

Vuelta nocturna casa-del-padre → casa, sesión armada por sentry-wake a las 05:00:55 local:

| hora local | evento |
|---|---|
| 05:18:13 | Último fix EN MOVIMIENTO: `36.6065,-6.2348`, 17 m/s (61 km/h), acc 44 m — a ~600 m del destino |
| 05:18:13→05:19:53 | **Hueco de 100 s sin ningún fix** (throttling MIUI de madrugada) |
| 05:19:53 | UN único fix: `36.6049,-6.2282`, acc 31 m, **speed 0** + ACTIVITY_TRANSITION en el mismo ms. Ningún fix más en toda la sesión |
| ~05:19–05:24 | Aparca en Angelita (invisible), camina a casa → llegan pasos con el ancla ya clavada en el punto de paso |
| 05:24:45 | `steps+egress` confirma con el ancla congelada en el fix huérfano → **pin FP rel 0.9 a ~315 m del coche** |

El gate de frescura funcionó antes (DECISION `HOLD_STALE_DISCARDED` a las 05:16); el Oppo, con
stream sano en el MISMO trayecto, clavó Angelita con acc 1,7 m (`confirmed_steps+egress`, 64/210
driving fixes). La única diferencia: el hueco. Mismo mecanismo que el caso 26-07 (hueco de 90 s
exactamente en la ventana de aparcar → ancla en un fix rancio, error 72 m).

## Invariante implementado

Simétrico de DET-DRIVE-PROOF-001 (la conducción exige track corroborado, no un fix Doppler
suelto): **si el fix que ABRE la parada llega más de `anchorGapMaxFixGapMs` (45 s) después de un
`previousFix` aún a velocidad real de conducción (≥ `minimumTripSpeedMps`), la deceleración
ocurrió entera dentro del hueco** — ese fix puede ser un semáforo o un fix rancio del OEM en
mitad de la ruta. Misma clase que el taint walk-entered ([DET-CREDIBLE-DRIVE-001]): *las pruebas
aguantan, el ANCLA no — preguntar, nunca pinchar.* Solo velocidad en el fix pre-hueco a propósito
(sin barra de precisión): el Doppler es creíble a precisiones que suspenderían
`minGpsAccuracyForDriving`, y exigir precisión eximiría justo a los streams degradados que
producen el hueco (el fix del campo: 17 m/s a 44 m).

## Piezas

1. `ParkingDetectionConfig.anchorGapMaxFixGapMs = 45_000L` (+ require). Cadencia normal 2–6 s,
   túneles urbanos muy por debajo; los huecos MIUI observados (60–100 s) por encima.
2. Estado del detector: `stopEnteredAfterGap` (flag de la parada, calculado al ABRIRSE) +
   `anchorGapEnteredAtCapture` (sellado al (re)ligar el ancla a esa parada, como los sellos
   walk-entered; los refinamientos de precisión de la misma parada lo conservan; se limpia con el
   ancla al reanudar conducción real).
3. `ParkingDecisionInput.anchorGapEntered` → en `EvaluateParkingDecisionUseCase` se suma a la
   cláusula Prompt junto a `!egressBornAtAnchor || anchorWalkEntered`.
4. Timeout desatendido: rama nueva ANTES de walk-entered → **nudge-only**
   (`aborted_unattended_gap_anchor`, DECISION `UNATTENDED_GAP_ANCHOR_NUDGE`). A diferencia de
   walk-entered el error hacia delante NO es acotable (el coche pudo seguir arbitrariamente lejos
   dentro del hueco), así que ninguna zona es honesta.
5. "Sí" del usuario: con ancla gap-entered ancla en la parada actual del usuario (`bestFix`),
   igual que el caso egress-born-away de [DET-ANCHOR-EGRESS-001].

## Alcance deliberadamente NO cubierto

- **Re-anclar al clúster de egress** (la idea 2 del ticket original): asertar una posición
  inferida contradice el fallo asimétrico — se degrada a prompt/nudge y el usuario marca. La
  señal "clúster contradice al ancla" ya la cubre en parte `egressBornAtAnchor`
  ([DET-ANCHOR-EGRESS-001]).
- Cadencia normal intacta (control test): parada abierta ≤45 s tras el último fix de conducción →
  confirm silencioso idéntico. Un hueco DENTRO de una parada ya abierta no taintéa (el reposo ya
  estaba presenciado). Tests legados (timestamps=0) fuera del taint por construcción (delta ≤ 0).

## Pendiente

- [ ] Commit + merge a master (go-ahead del usuario) y rebase de `feature/DET-RESIDENT-FGS-001`.
- [ ] APK y field-test: repetir trayecto nocturno con el Redmi; esperado: prompt/nudge en vez de
      pin fantasma, y cero cambio en el Oppo.
- [ ] Relacionado, NO cubierto aquí: acotar el GPS de la sesión desatendida sin conducción
      (batería, ~45 min GPS/día en despertares falsos del sentry) y cerrar la sesión de
      telemetría al colocar el pin (outcome NULL en la sesión del FP).
