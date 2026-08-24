# DET-PHYSICS-CREDIBLE-MOVEMENT-001 · P1.3 — el gate LOC-002 deja de estar escrito cinco veces

**Estado:** ✅ Done (2026-08-24) · rama `refactor/DET-PHYSICS-CREDIBLE-MOVEMENT-001-p1-3` ·
worktree `../Paparcar-physics-3`

Paso **P1.3** de la Fase 1. Sigue a `c56242b9` (P1.2).

## Qué mueve

`accuracy <= minGpsAccuracyForDriving` — el gate que decide **si la velocidad que reporta un fix se
puede creer siquiera**. Un receptor GPS reporta su Doppler con la misma seguridad cuando no tiene ni
idea de dónde está; el sobre de precisión es lo único que separa una medida de un cuento.

Estaba escrito cinco veces (07 §2.2). El propio comentario del coordinator en el primero lo llama
*«the same 50 m gate»* — la duplicación describiéndose a sí misma.

## Pero no son cinco veces lo mismo, y ahí está el trabajo

| Sitio | Expresión | Veredicto |
|---|---|---|
| `credibleSpeedFix` | `acc ≤ gate` (la velocidad se compara aparte) | → `isCredibleFixAccuracy` |
| `isDriving` | `speed ≥ clearBestStopSpeedMps && acc ≤ gate` | → `isCredibleMovingFix` |
| `isRealDrive` | `speed ≥ minimumTripSpeedMps && acc ≤ gate` | → `isCredibleMovingFix` |
| `drivingResumed` | `speed **>** resumeBar && acc ≤ gate` | ⚠️ solo el gate |
| egress cinemático | `speed **<** minTrip && acc ≤ gate` | ⚠️ solo el gate |

Los dos últimos comparten el gate y **no el predicado**:

- **`drivingResumed` compara estrictamente mayor**, no `>=`. La diferencia solo aparece justo en la
  barra, pero esa decisión **descarta un pin que ya se había ganado su confirm**, así que la
  frontera no se mueve dentro de un paso que se anuncia como movimiento puro. Se queda estricta.
- **El egress cinemático quiere la banda PEATONAL** — velocidad *por debajo* de la barra, con el
  mismo gate de precisión. Comparte el gate, no la pregunta.

Van tres pasos y tres exclusiones (P1.1 el gate de identidad BT, P1.2 el envelope por tiempo, P1.3
estas dos). **Empieza a ser el patrón dominante de la Fase 1: lo que parece familia casi nunca lo es
del todo, y la parte que sí lo es se extrae sin arrastrar la que no.**

## Diseño

Dos funciones, no una: `isCredibleFixAccuracy(fix, maxAccuracyMeters)` y
`isCredibleMovingFix(fix, speedBarMps, maxAccuracyMeters)`, la segunda construida sobre la primera.
Así los dos casos que solo comparten la mitad la usan directamente en vez de quedarse fuera.

El umbral va por parámetro, no leído del config: `physics/` sigue sin conocer la configuración
(misma regla que P1.2), y así el llamador que usa **otro** sobre —el burst de reposición, con su
`repositionMaxAccuracyMeters`— no queda forzado a un gate que no es el suyo.

## Doctrina

Ninguna tocada. **Cero cambio de conducta**: los tres sitios reescritos son idénticos y los dos
exentos conservan su operador literal. Comprobado además que **no queda ni una comparación cruda**
de `location.accuracy <= config.minGpsAccuracyForDriving` en el coordinator.

## Tests

`CredibleMovementTest` (8):

- creer y no creer la velocidad, con el incidente del Redmi (75 km/h a 120 m de precisión);
- el gate **inclusivo** en el sobre exacto;
- **rápido y ciego no es conducir** — sin la mitad de precisión, un Doppler alucinado enciende
  `hasEverReachedDrivingSpeed` y desbloquea todos los caminos de confirm;
- la barra de velocidad es **inclusiva**, y eso importa: el `drivingResumed` del hold es
  deliberadamente exclusivo y por eso no pasa por aquí. **Si alguien los funde por parecido, ese
  test es donde está escrita la diferencia**;
- y las dos barras dando veredictos opuestos sobre el mismo fix.

**Criterio de aceptación de la Fase 1 cumplido: la suite pasa sin editar un solo assert.**

**1.473 tests** (1.465 + 8), 0 fallos. `assembleMockDebug` ✅.

## Red de P0.4

```
tests 1473 - desaparecidos vs base: 0 - nuevos: 19 (P1.1 + P1.2 + P1.3)
```
