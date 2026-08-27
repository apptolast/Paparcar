# DET-LONE-SAMPLE-CANNOT-UNFREEZE-AN-ANCHOR-001 · una muestra suelta no deshace un descanso presenciado

**Estado:** ✅ Done · mergeado a master por squash · worktree y rama retirados

> Rebasada dos veces el 27-08-2026: sobre `46c7bad4` sin conflictos, y luego sobre el master que
> ya llevaba DET-BACKFILL-CANNOT-PIN-A-MOVING-FIX-001, con un conflicto **puramente aditivo** en
> `docs/detection/PARKING-DETECTION.md` (cuatro entradas nuevas al final del mismo fichero, todas
> conservadas). Verde tras cada rebase.

## Problema

Field 2026-08-27, Oppo, llegada a La Parafarmacia (Calle del Vivero). El user:
*«el punto donde acaba la polilínea es por donde he aparcado, pero ha arrastrado el pin hasta el
fisio»*, y sobre la mecánica: *«cuando marca 0,0 m/s y después andamos un rato a unos metros, no fue
real que me desplazara en coche hasta ahí; quizás ande más rápido de la cuenta, pero no tiene sentido
guardar el ancla ahí»*.

El coche paró de verdad en `36.5999567,-6.2514667`: **cuatro fixes consecutivos a 0,0 m/s con
precisión 2,2 m**, y el ancla se congeló ahí.

```
12:31:02  loc#44 speed=0.0m/s acc=2.583m
12:31:04  loc#45 speed=0.0m/s acc=2.383m
12:31:06  loc#46 speed=0.0m/s acc=2.25m
12:31:06  ⚓ anchor FROZEN — drive-entered stop matured (stableFixes=3, walkFixes=2)
...
12:31:30  🔒 anchor FROZEN (end-of-drive stop) — ignoring walking-range speed 2.6549373 m/s (< 5.0)
12:31:44  🔒 anchor FROZEN (end-of-drive stop) — ignoring walking-range speed 4.2521205 m/s (< 5.0)
12:31:49  loc#63 speed=6.449984m/s acc=2.3m     ← UN fix. Descongela.
12:31:54  loc#64 speed=0.0m/s acc=2.5m          ← el siguiente ya lo desmiente
12:32:09  ⚓ anchor FROZEN — drive-entered stop matured   ← re-ancla 56 m calle abajo
```

El guard aguantó 2,65 y 4,25 m/s (los ignoró explícitamente). **Una sola muestra a 6,45 m/s lo
tumbó.** El pin final quedó a **35 m** de donde paró el coche; el re-ancla intermedio, a **56 m**.

El salto que la produjo: 37 m entre `loc#62` y `loc#63` en 5,05 s. Una persona andando deprisa con el
GPS en una calle estrecha, no un coche — y el fix siguiente, 5 s después, está parado a 12 m.

## Doctrina violada

`DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001`, el invariante que este proyecto ya tiene abierto: **una muestra
no es un viaje.** El propio catálogo lo dice de otra racha: `shortHopProofFixes` exige más de un fix
porque *«a single fix can be a cache teleport»*. La misma física, sin aplicar aquí.

Y es un caso de *fallo asimétrico* mal orientado: creer a una muestra suelta y mover el ancla planta
el pin en el sitio equivocado, que es un falso positivo posicional. Mantener el ancla ante la duda
sólo retrasa la salida un fix (~5 s) cuando el coche arranca de verdad.

## Señales / datos disponibles

`physics/EffectiveDriving.kt` es el ranking que decide si un fix limpia el ancla. Su fila 1:

```kotlin
isRealDrive -> true          // "Nothing outranks a credible fix at trip speed."
...
anchorPinned -> false        // fila 4 — el ANCHOR-LOCK que protege la caminata
```

`isRealDrive` es `isCredibleMovingFix(location, minimumTripSpeedMps=5.0, minGpsAccuracyForDriving)`:
**un solo fix**. La fila 1 gana a la 4, así que el `ANCHOR-LOCK` se salta entero con una muestra.

La fila 2 (`sustainedDeparture`) ya es la versión corroborada por desplazamiento, pero exige
`sustainedDepartureFloorMeters` = 150 m; el 27-08 el desplazamiento desde el ancla era de ~49 m, así
que no aplicaba. No hay hoy ninguna forma de corroborar un `isRealDrive`.

## Diseño

**El invariante:** *mientras el ancla esté CLAVADA, sólo una conducción corroborada la mueve.* Clavada
significa que la sesión ya presenció dónde descansa el coche (parada madurada o pasos de egress);
deshacer ese testimonio pide más que una muestra.

Una racha, con el mismo patrón que `repositionStreak` ya usa en el mismo fichero:

- `AnchorTrust.realDriveStreak` — fixes `isRealDrive` **consecutivos**. Cualquier fix que no lo sea la
  rompe a cero, y todo fix parado la resetea (igual que `repositionStreak`).
- `config.pinnedAnchorRealDriveFixes = 2` — con `require(> 1)`, porque el número entero del ticket es
  «más de uno».
- La fila 1 de `effectiveDriving` se parte en dos, preservando que **el orden es el contenido**:

```kotlin
isRealDrive && !anchorPinned -> true        // 1a. sin ancla que proteger, una muestra basta
isRealDrive && realDriveCorroborated -> true // 1b. clavada, pero la conducción está corroborada
sustainedDeparture -> true
steplessDeparture -> true
anchorPinned -> false                        // 4. ahora también atrapa la muestra suelta
```

Con el ancla clavada, el PRIMER fix de conducción real cae a la fila 4 (el ancla aguanta) y la racha
sube; el SEGUNDO la limpia. Coste cuando el coche arranca de verdad: **un fix, unos 5 s**. El 27-08 no
hubo segundo — el siguiente marcaba 0,0 m/s — así que el ancla habría aguantado en el sitio correcto.

### Alternativas descartadas

- **Subir `minimumTripSpeedMps`.** No discrimina: el problema no es el umbral sino que se cree a una
  muestra. Un salto de GPS puede dar 12 m/s igual de fácil que 6,45.
- **Poner `anchorPinned` por encima de `isRealDrive`.** Rompe la salida legítima: al arrancar de una
  plaza el ancla está clavada por construcción y nunca se limpiaría.
- **Exigir `sustainedDeparture` siempre.** Su suelo son 150 m; obligaría a recorrer una manzana y
  media antes de reconocer una salida, y hay `DET-SHORT-TRIP-FREEZE-001` viviendo por debajo de eso.

## Criterio de éxito

- Test: ancla clavada + un fix a 6,45 m/s / acc 2,3 m seguido de uno a 0,0 → el ancla **no** se mueve
  (los números del 27-08).
- Test de regresión: ancla clavada + **dos** fixes consecutivos a 6,45 m/s → el ancla se limpia (la
  salida legítima sigue funcionando, un fix más tarde).
- Test de regresión: sin ancla clavada, un fix real sigue bastando (fila 1a intacta).
- Verificar que cada test discrimina, neutralizando su guard y comprobando que se pone rojo.
- Campo: aparcar y alejarse andando deprisa por una calle estrecha sin que el pin se desplace.

## Consumidores auditados

`grep -rn "effectiveDriving\|isRealDrive\|realDriveStreak" composeApp/src --include=*.kt`

| Sitio | Clasificación |
|---|---|
| `physics/EffectiveDriving.kt` — el ranking | **cerrado** — el invariante vive aquí, en una sola tabla |
| `state/StopTracking.kt` — único llamante, calcula las señales | **cerrado** — computa y propaga la racha |
| `state/AnchorTrust.kt` `onMovingFix` / reset en fix parado | **cerrado** — la racha vive con las otras |
| Fila 2 `sustainedDeparture` (`DET-CREDIBLE-DRIVE-001`) | **exento con razón** — ya es corroborada por desplazamiento; es la que salva el stream con precisión hambrienta |
| Fila 3 `steplessDeparture` | **exento con razón** — ya exige `frozenAnchorSteplessDepartureFixes` = 4 fixes, o sea ya es una racha |
| `DriveProof.proven` / `hasEverReachedDrivingSpeed` | **exento** — autorización de ciclo de vida de la sesión, no posición del ancla. Su propio problema de muestra suelta va en el ticket hermano (ver abajo) |
| Estrategia Bluetooth | **exento por construcción** — no usa el ancla del Coordinator |

## Ticket hermano (mismo invariante, otra víctima)

`DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001`: el mismo «una muestra no es un viaje» aplicado
al latch de sesión. Madrugada del 27-08, con el móvil en el sofá, `DET-CREDIBLE-DRIVE-001` aceptó
*«SUSTAINED DEPARTURE — 230 m del ancla a 24,5 m/s»* y latcheó `hasEverReachedDrivingSpeed`; el fix
siguiente, 7 s después, estaba **de vuelta en el ancla a 0,0 m/s**. Se abre aparte porque toca otro
estado (`SessionTelemetry`, no `AnchorTrust`) y porque ahí el guard hermano
`DET-STOP-MUST-BE-STILL-IN-SPACE-001` sí existe del lado de la parada — la asimetría es el hallazgo.
