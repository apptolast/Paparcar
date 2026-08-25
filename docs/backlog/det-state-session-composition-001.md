# DET-STATE-SESSION-COMPOSITION-001 · P2.6 — el orden en que se reducen los cinco

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STATE-SESSION-COMPOSITION-001-p2-6` ·
worktree `../Paparcar-state-6`

Paso **P2.6**. **Cierra la Fase 2.** Sigue a `15e251fa` (P2.5-bis).

## Qué mueve

`ParkingDetectionState` sale del coordinator y pasa a
`domain/detection/state/DetectionSessionState.kt`. A estas alturas **no es más que la composición de
los cinco sub-estados**, porque los cinco pasos anteriores la vaciaron campo a campo:

```kotlin
data class DetectionSessionState(
    val anchorTrust: AnchorTrust,
    val confirmation: ConfirmationLifecycle,
    val egress: EgressEvidence,
    val session: SessionTelemetry,
    val drive: DriveProof,
)
```

Lo que gana aquí es lo único que **existe solo ENTRE dueños**: el orden de reducción.

## Por qué el orden no es un detalle

La regla de diseño era unidireccional: el ancla se posee a sí misma y los pasos se le PRESENTAN
[07 §2.4]. `DET-CADENCE-CANNOT-ACCUSE-AFTER-EGRESS-001` hizo que el clasificador de cadencia lea el
ancla de vuelta, así que **el grafo tiene un ciclo**.

Con un ciclo, un despiste en el orden cambia un veredicto mientras el compilador sigue contento y
todos los tests de cada sub-estado siguen pasando. Es exactamente la clase de defecto que no atrapa
nadie, así que tiene fichero propio.

## El orden, enunciado

**En un fix GPS:**

1. `DriveProof` reduce primero, contra el estado previo al fix.
2. `SessionTelemetry` reduce después y consume la prueba de conducción **producida por ESE MISMO
   fix**.
3. `AnchorTrust` y `EgressEvidence` reducen los dos contra el snapshot previo, así que su orden
   relativo es irrelevante por construcción: ninguno lee el valor nuevo del otro.

**En un evento de paso:** el ancla se lee tal como estaba ANTES del paso, y el paso nunca escribe el
ancla — `onStepEvent` devuelve solo un `EgressEvidence`, así que **el tipo rompe el ciclo** y esa
dirección no se puede equivocar.

### La regla 2 es la que tiene dientes

`authorizedOnArmTrustOnly` —el flag que decide si un dismissal puede retractar un seed que el arm
solo PRESTÓ— deja de ser retractable **en el fix que prueba la conducción, no en el siguiente**.
`DetectionSessionState.onFix` hace de drive→session **un solo paso indivisible**.

| Neutralización | Resultado |
|---|---|
| que la sesión lea la prueba del fix ANTERIOR | 🔴 **1 test, el nuevo** |

Una ventana de UN fix, invisible para los **1.618** tests anteriores. En ella un dismissal podría
retractar un seed que el viaje ya se había ganado — y el flag existe precisamente para separar lo que
el arm prestó de lo que el viaje probó.

## ⚠️ Solo la regla 2 está garantizada aquí

Las reglas 1 y 3 siguen siendo **convenciones** que sostiene el bloque de fix del coordinator. Se
vuelven estructurales cuando la lista de etapas SEA el orden (Fase 3, `StageOrderTest` de P3.0).

Decir cuál es cuál importa más que la lista: **un orden que se declara garantizado sin estarlo es
peor que uno que admite ser una convención.**

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

`ReductionOrderTest` (4): la regla 2 en sus dos mitades, la conmutatividad de ancla↔egress (que es
como se dice «por construcción» en voz alta) y la dirección del evento de paso.

**1.622 tests**, 0 fallos. **Ni un fichero de test tocado.** Coordinator **−65 líneas**.
`assembleMockDebug` ✅.

## Red de P0.4

```
tests 1622 - desaparecidos: 17 (ya justificados: 5 de P1.8 + 12 del fichero movido en P2.4)
           - nuevos: 185
```

---

## Fase 2 cerrada

Siete pasos, `c5f06bb5` → aquí. **Cuarenta campos planos** pasaron a **cinco sub-estados** con
transiciones con nombre, y en ninguno de los siete se editó un solo assert.

| Paso | Sub-estado | Lo que arregló |
|---|---|---|
| P2.1 | `SessionTelemetry` | el seed y su etiqueta ya no se desincronizan; la lista de preservación escrita a mano |
| P2.2 | `ConfirmationLifecycle` | tres formas de terminar un hold que parecían una |
| P2.3 | `EgressEvidence` | tres reglas de reset que se leían como una |
| P2.4 | `DriveProof` | tres tiempos de vida que parecían un acumulador; predicado absorbido |
| P2.5 | `AnchorTrust` | el sellado ×5 → 1 |
| P2.5-bis | (egress birth) | los dos sabores → uno, con el bug #6 bautizado |
| P2.6 | `DetectionSessionState` | el orden de reducción |

Siguiente: **Fase 3**, las etapas en orden INVERSO. Empieza por **P3.0**, el andamio
(`stages/SessionStage.kt`, `StageVerdict`, el sealed `DetectionEffect`) más `StageOrderTest`, que
debe **fallar si se permutan dos entradas de la lista** — sin mover ninguna etapa todavía.
