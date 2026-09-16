# PRIV-SIGN-OUT-WAITS-ONCE-NOT-THREE-TIMES-001 · Salir podía costar nueve segundos

**Estado:** ✅ Done (2026-09-16) — el hash vive en `MEMORY.md`.
**Origen:** 4.ª vuelta — B3.

## Problema

El arreglo de `PRIV-A-SIGN-OUT-WITHOUT-NETWORK-NEVER-SIGNS-OUT-001` puso un plazo de 3 s a **cada**
paso remoto, en serie: sin red, hasta **nueve segundos** mirando una pantalla que ya habías dicho
que querías abandonar. El arreglo de la espera infinita dejó una espera larga.

## Arreglo

Las tres retiradas comparten **un** presupuesto de 3 s y corren **en paralelo** — son independientes:
nada de lo que hace una cambia lo que hace otra. `signOut()` sigue fuera del plazo.

## Testigo

`should_waitOnceAndNotThreeTimes_when_nothingAnswers`, con los tres colgados en `awaitCancellation()`
y midiendo el **reloj virtual** de `runTest`: menos de 3,5 s. Con la versión en serie eran nueve.
