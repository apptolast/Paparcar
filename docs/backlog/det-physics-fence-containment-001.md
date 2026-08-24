# DET-PHYSICS-FENCE-CONTAINMENT-001 · P1.7 — dos se funden, la tercera se explica

**Estado:** ✅ Done (2026-08-24) · rama `refactor/DET-PHYSICS-FENCE-CONTAINMENT-001-p1-7` ·
worktree `../Paparcar-physics-7`

Paso **P1.7** de la Fase 1. Sigue a `a2f2bff9` (P1.6).

## Qué mueve

*¿Podría el cuerpo estar plausiblemente EN este coche?*

```
d(fix, centro)  ≤  radio  +  acc(fix)
```

El acolchado por la precisión del propio fix es el sentido de la función: un fix que puede estar
desviado 40 m y cae 30 m fuera del anillo **no ha demostrado que el cuerpo esté lejos del coche** —
ha demostrado que el receptor no está seguro. Leerlo como «definitivamente fuera» sería actuar sobre
ruido de GPS.

La asimetría corre en un solo sentido: un «dentro» equivocado cuesta unos despertares de más o un arm
etiquetado en-el-coche cuando era a-mitad-de-viaje; un «fuera» equivocado cuesta una plaza. Por eso
la contención es generosa, y lo es a propósito.

Dos llamadores hacían exactamente esta pregunta con la fórmula escrita a mano: el amortiguador del
sentry (`isInsideAnyOwnedFence`) y el evaluador del embarque AR. Ahora comparten
`physics/FenceContainment.kt`.

## La tercera no se toca — y ahí está el trabajo

`EvaluateSafetyNetCheckUseCase` usa `d ≤ radio` **sin acolchar**. No es una discrepancia: es
estructural. No hace una pregunta binaria, corre **una escalera de tres zonas**:

```
d ≤ radio                        → cura esta valla
d − acc(fix) ≤ umbralLejano      → no hacer nada  ← el anillo ambiguo (territorio de ruido GPS)
si no                            → lejos, correr las pruebas de salida
```

**Su generosidad ya vive en la puerta LEJANA**, donde «lejos» tiene que aguantar aunque el fix se
equivoque por su propia precisión (un salto de caché de 100 m no puede despejarla). Acolchar también
la de dentro empujaría los dos acolchados el uno contra el otro y **se comería el anillo ambiguo**,
convirtiendo fixes de ruido en curas de valla. Con radio ~80-130 m y umbral lejano 300 m, ese anillo
es real y ancho, y existe justamente para que ninguna de las dos respuestas se fuerce cuando el fix
no puede sostenerla.

## Lo que este paso aporta de verdad

Que esa tercera *deba* acolcharse es una pregunta de producto con su propio compromiso (bug #9) y
lleva su ticket. **Lo que faltaba era el párrafo**: sin él, una asimetría razonada es
indistinguible de un descuido, y el siguiente que note la diferencia no tiene forma de saber cuál de
las dos está mirando.

Queda escrito en dos sitios: en el KDoc de la función nueva (donde alguien busca la familia) **y en
el propio sitio que no acolcha** (donde alguien la encuentra por accidente).

## Doctrina

Ninguna tocada. **Cero cambio de conducta**: las dos fundidas son idénticas, la tercera no se toca.

## Tests

`FenceContainmentTest` (6). El que lleva el peso: **un fix demasiado vago para decidir no cuenta como
«fuera»** — 30 m pasado el anillo con 40 m de incertidumbre es *inseguro*, no *lejos*. Y su
contrapeso: la generosidad está acotada, un fix vago pero mucho más lejos que su propia
incertidumbre sigue estando fuera, o el acolchado se tragaría salidas reales.

**Criterio de aceptación de la Fase 1 cumplido: la suite pasa sin editar un solo assert.**

**1.506 tests** (1.500 + 6), 0 fallos. `assembleMockDebug` ✅. Retirados dos imports de
`haversineMeters` que quedaron huérfanos.

## Red de P0.4

```
tests 1506 - desaparecidos vs base: 0 - nuevos: 52 (P1.1 a P1.7)
```

Quedan P1.8 (mover dos ficheros de carpeta), P1.9 (el `when` de precedencia, intacto) y P1.10
(introducir el sealed de forma, sin adoptarlo).
