# DET-PHYSICS-EFFECTIVE-DRIVING-001 · P1.9 — el `when` de precedencia sale intacto, y por fin tiene test

**Estado:** ✅ Done (2026-08-25) · rama `refactor/DET-PHYSICS-EFFECTIVE-DRIVING-001-p1-9` ·
worktree `../Paparcar-physics-9`

Paso **P1.9** de la Fase 1. Sigue a `cfe2e025` (P1.8).

## Qué mueve

El discriminante **persona/coche**: ocho ramas que deciden si este fix limpia el ancla del
aparcamiento. Equivocarse sale caro en las dos direcciones — decir COCHE cuando anduvo una persona
borra el ancla buena y el pin re-ancla donde el usuario se pare (Camelias 10-07: tres pasos en el
bordillo y el pin acabó dentro de la casa); decir PERSONA cuando el coche reptó congela el ancla en
un semáforo.

**El orden ES el contenido.** Las filas no son reglas independientes: cada una existe para ganarle a
las de abajo en un caso donde ambas aplican. Por eso sale **verbatim** y recibe nueve señales ya
calculadas en vez de recalcular nada — aplanarlo en configuración sería menos legible que el `when`
comentado, y fragmentarlo destruiría lo único que codifica (07 §3.2 lo dice expresamente).

## Lo que este paso arregla de verdad

Hasta ahora el `when` vivía dentro de un bloque `update {}` de 700 líneas y sus ocho ramas **solo
eran alcanzables a través del coordinator entero**. Su propio comentario dice que cada fila ganó una
discusión con un viaje real — **y sin embargo nada fallaba si se permutaban dos filas.**

Es el mismo agujero que `DET-PRECEDENCE-MUST-BE-TESTABLE-001` encontró un nivel más arriba, y se
tapa igual: fijar **cada par adyacente** con un caso donde las dos filas aplican y discrepan.

### Verificado discriminante, no supuesto

| Permutación | Resultado |
|---|---|
| filas **5↔6** (hop corroborado ↔ contador mudo) | 🔴 `should_let_a_corroborated_hop_beat_the_mute_counter_rule` |
| filas **3↔4** (salida sin pasos ↔ ancla fijada) | 🔴 `should_let_a_stepless_departure_beat_a_pinned_anchor` |

El par 5/6 es el que más importa: leídas por separado las dos filas parecen contradecirse —*«contador
mudo ⇒ COCHE»* contra *«contador mudo ⇒ PERSONA»*— y no se contradicen: **la 5 es la escotilla
MEDIDA de la 6**. Permutarlas reabre Galeote (16-07: 23,7 m en 5 s contra 9,9 m de ruido, el coche
rodando al bordillo leído como caminata). Permutar 3/4 reabre Bodegas Osborne (23-07: 160 m de reptar
a 6-16 km/h que nunca movían el ancla congelada).

Eso queda ahora escrito en el KDoc **y sostenido por un test**, que es la diferencia entre un
comentario y una garantía.

## Doctrina

Ninguna tocada. **Cero cambio de conducta**: el `when` es carácter por carácter el mismo, solo que en
otro fichero y con las señales por parámetro.

## Tests

`EffectiveDrivingTest` (11): cada fila alcanzable por su cuenta y **cada par adyacente en conflicto**,
con el incidente de campo que lo justifica en el KDoc de cada test.

**Criterio de aceptación de la Fase 1 cumplido: la suite pasa sin editar un solo assert.**

**1.522 tests** (1.511 + 11), 0 fallos. `assembleMockDebug` ✅.

## Red de P0.4

```
tests 1522 - desaparecidos: 5 (los renombrados de P1.8, ya justificados) - nuevos: 73
```

Queda **P1.10** (introducir el sealed de forma del guardado, sin que nadie lo adopte) para cerrar la
Fase 1.
