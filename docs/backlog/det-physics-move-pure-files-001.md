# DET-PHYSICS-MOVE-PURE-FILES-001 · P1.8 — dos ficheros que ya eran puros cambian de carpeta

**Estado:** ✅ Done (2026-08-24) · rama `refactor/DET-PHYSICS-MOVE-PURE-FILES-001-p1-8` ·
worktree `../Paparcar-physics-8`

Paso **P1.8** de la Fase 1, el trivial. Sigue a `3377e78d` (P1.7).

## Qué mueve

`SpeedBandClock.kt` y `GapDoubt.kt` ya eran funciones puras de nivel superior en el patrón destino —
solo estaban en la carpeta de al lado. `git mv` a `physics/`, paquete actualizado, y los dos
consumidores que las llamaban **cualificadas a mano** pasan a import, que es lo que el proyecto pide.

Git las registra como renombrados (`R`), así que la historia de ambos ficheros se conserva.

## Lo que no era trivial: `GapDoubt` no tenía test

Y tiene **dos consumidores** que meten su resultado directo en el radio de una zona guardada. Dos
consumidores y cero tests es exactamente la forma que tiene una cota de ensancharse en uno y quedarse
olvidada en el otro — que es, literalmente, el bug #9 de la auditoría, el que provocó que esta
función se extrajera.

`GapDoubtTest` (5): el hueco real de 100,5 s del viaje de Góndola (→ ~201 m de duda), el hueco de
cero, **el reloj hacia atrás** (un `gapMs` negativo tiene que dar cero y no un radio negativo, que se
hundiría bajo el suelo de zona y reclamaría en silencio una precisión que nadie tiene), la escala con
el techo peatonal, y la monotonía.

## ⚠️ La red de P0.4 se disparó por primera vez — y era una alarma real

El diff contra la línea base reportó **5 tests desaparecidos**:

```
com.rndeveloper.paparcar.domain.detection.SpeedBandClockTest :: should_creditTheGap_when_...
   (y 4 más)
```

No habían desaparecido: la base guarda el nombre **cualificado con el paquete**, y el paquete cambió.
Falsa alarma en sustancia, verdadera en mecanismo — un nombre salió de la base.

**Se verificó uno a uno** que los cinco existen bajo `…detection.physics.SpeedBandClockTest` con el
mismo nombre de método, y el renombrado queda **anotado dentro de la propia
`P0.4-baseline-tests.txt`**, no en la memoria de nadie. Así la red sigue limpia en los pasos
siguientes en vez de gritar lo mismo cada vez, que es como una red deja de leerse.

El plan pedía exactamente esto: *«cualquier test que desaparezca durante F6 **sin justificación
escrita** es una regresión silenciosa»*. Ésta tiene la suya.

## Doctrina

Ninguna tocada. **Cero cambio de conducta**: cambio de carpeta y de paquete.

**1.511 tests** (1.506 + 5), 0 fallos. `assembleMockDebug` ✅.

## Red de P0.4

```
tests 1511 - desaparecidos: 5 (los 5 renombrados, justificados y anotados) - nuevos: 62
```

Quedan P1.9 (el `when` de precedencia, intacto) y P1.10 (el sealed de forma, sin adoptarlo) para
cerrar la Fase 1.
