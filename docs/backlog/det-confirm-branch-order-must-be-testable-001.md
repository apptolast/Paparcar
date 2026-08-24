# DET-CONFIRM-BRANCH-ORDER-MUST-BE-TESTABLE-001 · los tres pares que faltaban de P0.1 no se pueden escribir

**Estado:** ✅ Done (2026-08-23) · **resultado NEGATIVO medido, sin código** · corrige el plan F5

Cierra **P0.1** del plan `docs/detection/10-plan-refactor.md` — no añadiendo los tres tests que el
plan pedía, sino **demostrando que ninguno de los tres se puede escribir** y por qué.

## Problema de partida

`DET-PRECEDENCE-MUST-BE-TESTABLE-001` fijó cuatro pares, todos contra la rama del usuario, y dejó
fuera los pares entre las ramas que **plantan el pin** con una justificación floja (*«cubiertas por
los replays»*). El plan F5 los listaba como mínimo obligatorio:

- `hold ↔ candidate`
- `no-movement-budget ↔ candidate`
- `response-timeout ↔ fast-confirm`

## Resultado: los tres caen, cada uno por un motivo distinto

### 1 · `no-movement-budget ↔ candidate` — ⛔ INALCANZABLE

El presupuesto exige `!hasEverReachedDrivingSpeed`; abrir un candidato exige conducción probada.
**Las condiciones son mutuamente excluyentes: no existe un fix donde compitan.** El plan pedía un par
imposible.

### 2 · `hold ↔ candidate` — redundante

La rama del candidato retorna en cuanto la fase es `Candidate`, así que el hold la tapa por el mismo
`return@collect` que tapa a la vía rápida. El par con contenido propio sería hold ↔ vía rápida — y
ese es el punto 3.

### 3 · `hold ↔ confirm rápido` — ⛔ NO OBSERVABLE, y esto es el hallazgo

Sobre el papel es el par que sostiene que el hold funcione: ese `return@collect` es lo único que
impide que el mismo fix vuelva a cruzar la vía rápida y abra un segundo confirm, y cada
`beginConfirm` reemplaza el `pendingConfirm` **reiniciando su reloj**.

Se construyó el escenario: aparcar → la vía rápida confirma y abre el hold → el usuario sigue
caminando con pasos frescos y desplazamiento creciente (las condiciones que **volverían** a
satisfacer la vía rápida) → vencer la ventana.

**Medición, neutralizando el `return@collect` del hold:**

| | `saves` | eventos |
|---|---|---|
| master | 0 | `SessionStarted=1, LocationFix=9, Step=40, Decision=1, SessionEnded=1` |
| sin el `return@collect` | 0 | **idénticos** |

**Salida byte a byte igual.** La causa no es el montaje: es que las dos condiciones están
**entrelazadas por diseño**. Lo que haría re-disparar a la vía rápida —alejarse del pin— es
exactamente lo que hace que el hold, al vencer, se **descarte por rancio**
[DET-CONFIRM-FRESHNESS-001] en vez de asentarse. Un peatón que se queda cerca no re-dispara la vía
rápida (sin desplazamiento de egress no hay `Confirmed`); uno que se aleja mata el hold por otra
puerta. **No existe un escenario donde las dos ramas compitan de verdad y el resultado se note desde
fuera.**

## Doctrina

Ninguna violada. Lo que sí se aplica es la regla de admisión del fichero de tests: *un test de orden
que pasaría con cualquier orden no vale nada*. Escribir este habría sido añadir un bug #8 nuevo — un
test verde con un comentario que afirma lo que no prueba. **Se decidió no escribirlo.**

## Consumidores auditados

Se comprobó que el desenlace real del escenario ya tiene dueño: el descarte por rancio al asentar
está cubierto por `should_discard_held_confirm_when_position_outran_the_steps_at_settle`
(`CoordinatorParkingDetectorTest`). No hay hueco de cobertura que tapar.

## Consecuencias

1. **P0.1 queda CERRADO** con cuatro pares discriminantes y tres descartados con demostración. El
   plan `10-plan-refactor.md` se corrige en consecuencia.
2. **Tercera instancia del mismo patrón.** Van tres veces que una rama del hold resulta indistinguible
   desde fuera:
   - el descarte por conducción reanudada vs. el descarte por rancio (ticket anterior),
   - el fall-through de cualquiera de los dos (ticket anterior),
   - y ahora el hold que aguanta vs. la vía rápida.

   Las tres tienen la misma raíz: **las sub-ramas del hold no emiten eventos propios**. Son ramas
   mudas del catálogo de `04-diagnostico.md`. La propuesta 3 de la arquitectura objetivo (cada rama
   muda pasa a ser una nota con nombre que emite el tap) no es sólo observabilidad de campo:
   **es lo que haría testeable esta zona**.
3. **`response-timeout ↔ fast-confirm` sigue vivo y diferido**, con receta escrita: exige fabricar
   una sesión en fase `Notified` con `promptShownAt` (**no** `Candidate`, que retorna antes), dejar
   vencer `confirmationResponseTimeoutMs`, y que en ese mismo fix `freshStepCount ≥ minStepsToConfirm`.
   Es el límite [DET-D-03] ↔ [DET-RECONCILE-001]. No entra aquí porque el montaje es un ticket propio,
   no porque sea imposible.

## Estado final

- **Cero código.** El único fichero de este ticket es este documento.
- Suite intacta en verde; `StagePrecedenceCharacterizationTest` se queda como estaba.
