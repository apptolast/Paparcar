# DET-CONFIRM-ANCHOR-001 — el "Sí" tardío del usuario ancla en el coche, no en el usuario

**Estado:** 🔵 IMPLEMENTADO en rama `bugfix/DET-CONFIRM-ANCHOR-001-user-confirm-anchor`
(working tree, sin commit) — suite prod completa verde (1088 tests; 6 nuevos del detector).
⏳ Commit + merge (go-ahead del usuario) y field-test.
**Origen:** bug de campo 2026-08-11 16:08 (evidencia Firestore + telemetría).

## Forense 11-08 16:08

Sesión Coordinator con conducción MEDIDA (32 driving fixes, vmax 55 km/h) que llegó al reposo en
una parada presenciada (sin hueco GPS). Contador de pasos mudo a efectos prácticos (2 pasos) →
el confirm silencioso degradó a prompt. El usuario respondió "Sí" cuando **ya se había ido
ANDANDO a su destino** → el pin se plantó en el destino peatonal, no donde terminó la conducción.

Causa en `CoordinatorParkingDetector` (rama `state.userConfirmedParking`):

```kotlin
val locationToConfirm = if (isEgressBornAtAnchor(state) && !state.anchorGapEnteredAtCapture) {
    state.bestStopLocation ?: state.bestFix(location)
} else {
    state.bestFix(location)   // ← posición ACTUAL del usuario al responder
}
```

El comentario asumía "they answer near the car". Cuando el usuario responde tarde/lejos, el fix
actual ES el peatón — exactamente lo que la doctrina ANCHOR-LOCK/DET-ANCHOR-FREEZE prohíbe que
arrastre el pin.

## Remedio acotado (solo la rama `else`)

Guarda de distancia barata: en el "Sí" fuera del camino feliz born-at-anchor, la confirmación se
re-ancla en `bestStopLocation` (el final de conducción congelado) **solo** cuando se cumplen
TODAS:

1. Existe parada presenciada (`bestStopLocation != null`).
2. El ancla **NO** entró por hueco GPS (`!anchorGapEnteredAtCapture`) — un ancla gap-entered
   puede ser un punto de paso con error hacia delante inacotable [DET-GAP-ANCHOR-001], nunca
   gana este re-anclaje.
3. `d(fix actual, bestStopLocation) > USER_CONFIRM_NEAR_CAR_MAX_METERS` (100 m, companion
   privado; entre los radios near-car estándar `geofenceRadiusMeters` 80 m /
   `geofenceRadiusVanMeters` 120 m y por debajo de `egressBirthFloorMeters` 150 m).
4. `d(fix actual, egressOriginFix) > USER_CONFIRM_NEAR_CAR_MAX_METERS` — **ajuste mínimo** sobre
   el mandato original, ver invariantes abajo.

En cualquier otro caso (cerca de la parada, cerca del nacimiento del egress, gap-entered, sin
ancla) el comportamiento queda EXACTAMENTE igual que hoy. El log de la rama USER-CONFIRMED
estampa `stopDistance`/`birthDistance`/`gapEntered` y qué testigo ganó (proveniencia
obligatoria).

## Invariantes respetados (y por qué la cláusula 4)

- **[DET-GAP-ANCHOR-001] pieza 5**: con ancla gap-entered el "Sí" sigue anclando en la parada
  actual del usuario, incondicionalmente — la guarda excluye el gap por construcción.
- **[DET-ANCHOR-EGRESS-001]**: la rama `else` sin gap solo se alcanza con egress nacido LEJOS
  del ancla — y su doctrina dice que entonces el ancla puede ser una parada intermedia (Enamorados
  15-07: congelada en un semáforo 1,11 km antes del aparcamiento real) y el NACIMIENTO del egress
  es donde está el coche. La guarda tal cual la pedía el ticket (solo distancia al ancla)
  re-plantaría el pin del semáforo cuando el usuario responde "Sí" junto al coche (>100 m del
  ancla). La cláusula 4 es el ajuste mínimo: si el usuario responde CERCA del nacimiento, la
  hipótesis "el nacimiento es el coche" sigue viva → comportamiento actual (bestFix). El
  re-anclaje solo gana cuando el usuario está lejos de AMBOS testigos del coche — entonces su
  posición es demostrablemente el peatón y la parada presenciada es el único testigo honesto.
- **Fallo asimétrico**: la guarda nunca crea un pin donde antes no lo había — solo elige, entre
  dos posiciones ya candidatas, el testigo medido (fin de conducción presenciado) frente al
  peatón. Residual aceptado: si el contador mudo entrega sus pasos ya EN el destino, el
  nacimiento queda junto al destino y la guarda se abstiene (= comportamiento de hoy, fallo
  conservador).
- **Rama then intacta**: born-at-anchor sin gap sigue anclando en `bestStopLocation` como
  siempre; `reliabilityUserConfirmed` no cambia.

## Piezas

1. `CoordinatorParkingDetector` — guarda en la rama `else` del user-confirm + log de distancias;
   `USER_CONFIRM_NEAR_CAR_MAX_METERS = 100.0` en el companion privado.
2. Tests (`CoordinatorParkingDetectorTest`, 6 nuevos, fakes + clock inyectado):
   - `should_anchor_at_witnessed_stop_when_user_confirms_far_from_it` (replay 11-08: lejos de
     ambos testigos → pin en la parada presenciada; falla con el código antiguo),
   - `should_keep_current_fix_when_user_confirms_near_the_witnessed_stop`,
   - `should_keep_current_fix_when_user_confirms_near_the_egress_birth` (protección Enamorados),
   - `should_keep_current_fix_when_user_confirms_far_but_anchor_is_gap_entered`,
   - `should_anchor_at_current_fix_when_no_stop_was_witnessed`,
   - `should_anchor_at_stop_when_egress_born_at_anchor_and_user_confirms_far` (rama then
     intacta).
3. `docs/detection/PARKING-DETECTION.md` — sección DET-CONFIRM-ANCHOR-001 + cross-refs en
   GAP-ANCHOR.

## Pendiente

- [ ] Commit + merge a master (go-ahead del usuario).
- [ ] Field-test: repetir el patrón 11-08 (aparcar, andar al destino, responder "Sí" tarde) y
      verificar pin en el fin de conducción; control: responder junto al coche → sin cambio.
