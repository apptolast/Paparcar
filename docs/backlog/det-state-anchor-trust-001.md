# DET-STATE-ANCHOR-TRUST-001 · P2.5 — un solo rebind donde la misma condición estaba escrita cinco veces

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-STATE-ANCHOR-TRUST-001-p2-5` ·
worktree `../Paparcar-state-5`

Paso **P2.5**, el más grande de la Fase 2. Sigue a `c43526c1` (P2.4).

## Qué mueve

Diecisiete campos — el ancla, la parada en que se capturó, sus cinco testigos sellados, los fixes de
la ventana de parada, el odómetro de walk-in, el nacimiento del egress y la racha de
re-aparcamiento — pasan a `domain/detection/state/AnchorTrust.kt`, con `AnchorCapture`, `WalkIn` y
`EgressBirth` dentro.

189 referencias re-enrutadas, guiadas por el compilador.

## El corazón del paso: el sellado ×5 → 1

Sellar la captura era `if (anchorStopOfRecord != s.anchorCapturedAtStop)` **escrito cinco veces**,
una por testigo, dentro de un `copy` de cuarenta campos:

```
anchorWalkFixesAtCapture  = if (stopOfRecord != capturedAtStop) … else …
anchorStepEventsAtCapture = if (stopOfRecord != capturedAtStop) … else …
anchorSawStepsAtCapture   = if (stopOfRecord != capturedAtStop) … else …
anchorWalkInSpanMeters    = if (stopOfRecord != capturedAtStop) { … } else …
anchorGapMsAtCapture      = if (stopOfRecord != capturedAtStop) … else …
```

Un sexto testigo tenía que **acordarse** de repetirla, y nada fallaba si no lo hacía; un testigo
retirado dejaba su condición atrás. `rebind()` hace que la captura se selle **entera o nada**.

Es el MISMO test de identidad que antes, así que un refinamiento de precisión **en la misma parada**
sigue conservando los taints originales: los taints pertenecen a la PARADA, no a la nitidez del fix.

### Verificado discriminante

| Neutralización | Resultado |
|---|---|
| que un testigo se olvide de re-sellarse en el rebind | 🔴 **4 tests** (3 nuevos + `should_still_taint_walk_entered_anchor_when_step_events_corroborate_the_walk_in`) |

⚠️ Probé antes a cambiar el test de **identidad** por **igualdad** y **no discrimina**: `GpsPoint` es
data class, así que sólo difieren con dos instancias distintas de contenido idéntico, y ningún
fixture produce eso. Lo apunto porque es información sobre el TEST, no sobre el código: la identidad
es load-bearing en un caso que nada reproduce hoy.

## ⚠️ Una asimetría PRESERVADA, no arreglada

Al limpiar el ancla, `walkInSpanMeters` y `gapMs` se resetean y `walkFixes`, `stepEvents` y
`sawSteps` **no**.

Parece un descuido, y **no es inobservable**: `isAnchorWalkEntered` lee los tres supervivientes **sin
exigir ancla**, y sus dos llamadores son alcanzables sin ella —el timeout desatendido, sobre todo—.
Ponerlos a cero voltearía un veredicto walk-entered a «limpio» justo donde falta el ancla, que es el
caso que la doctrina de fallo asimétrico manda tratar con más sospecha.

Un movimiento no tiene derecho a decidir eso. `AnchorCapture.clearedWithAnchor()` reproduce la
conducta de hoy **y le pone nombre**, con su test, para que la pregunta se haga sola y con un replay
dirigido detrás.

**→ Ticket futuro**: decidir si los tres testigos deben morir con el ancla. Necesita un replay que
alcance `isAnchorWalkEntered` con ancla nula y capture de una parada anterior.

## El cabo de `refinedParkLocation` se cierra solo

07 §2.1 lo dejó abierto: su fallback cae en `bestFix`, que leía la lista de fixes parados de **otra
máquina**. Esa lista es ahora el `stopWindowFixes` del propio ancla, así que el fallback y el ancla
son la misma máquina — el cabo se cierra por construcción, sin decisión de producto.

## Doctrina

Ninguna tocada. **Cero cambio de conducta.**

## Tests

`AnchorTrustTest` (11): el sellado atómico, el refinamiento en la misma parada, el re-sellado en una
parada posterior, el latch del freeze, la asimetría de limpieza (con su ⚠️ explicado), el odómetro de
walk-in y su reseteo por maniobra, el nacimiento del egress y el reloj de reposo del coche.

**1.614 tests**, 0 fallos. **Ni un fichero de test tocado.** Coordinator **−133 líneas netas**
(135 añadidas, 268 borradas). `assembleMockDebug` ✅.

## Red de P0.4

```
tests 1614 - desaparecidos: 17 (5 de P1.8 + 12 del fichero movido en P2.4, ya justificados)
           - nuevos: 177
```

Siguiente: **P2.5-bis**, la unificación de los dos sabores del egress-birth. Es paso **PROPIO** y
marcado `C` porque **puede cambiar conducta**: su criterio de aceptación es literal —
`Trace_Enamorados001` y `Trace_CameliasOppo001` **sin cambio de desenlace**, y **cualquier delta
observable detiene el paso y vuelve para aprobación**. La asimetría del bug #6
(`acceptsKinematicWitness`) se **preserva y se hace visible**, no se arregla.
