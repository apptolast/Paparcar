# DET-LONE-SAMPLE-IS-NOT-A-DRIVE-001 · Un solo fix de velocidad todavía abre la sesión entera

**Estado:** ✅ Done — en master. Suite: **3856 tests, 0 fallos** (`testAll`), más
`:app:assembleMockDebug` y `:app:compileProdDebugKotlin`. Sin resultado visual.
· descubierto por el replay de
[DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001](det-exit-fix-cannot-prove-its-own-exit-001.md)
· **03-09: las DOS barras candidatas implementadas y MEDIDAS contra el harness — las dos pierden un
viaje real. El diseño de abajo está refutado tal como está escrito** (ver «Lo medido»)

> ### Re-verificación en código, 15-09 contra `d7880ac7` — sigue vivo, y **la ruta del snippet ya no existe**
>
> ⚠️ El bloque de abajo cita `CoordinatorParkingDetector.kt` y `location.speed`. Desde
> `DET-PACKAGE-CLUSTERS-001` la reducción vive en
> **`domain/detection/state/FixReduction.kt:105-107`**, con `fix.speed`:
>
> ```kotlin
> val hasJustReachedSpeed = !session.driveAuthorized &&
>         fix.speed >= config.minimumTripSpeedMps &&
>         credibleSpeedFix
> ```
>
> Misma forma exacta: **velocidad declarada + precisión declarada, sin corroboración**. Lo que SÍ
> cambió alrededor es la red que lo envuelve, y conviene no re-litigarla:
> `DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001` mantiene el cruce como
> `provisionalCrossing` y lo **REVOCA** si el siguiente fix vuelve dentro del sobre de origen dentro
> de `driveProofWindowMaxMs` (`FixReduction.kt:98-104`), y `credibleSpeedFix` se apoya en
> `minGpsAccuracyForDriving` (**50 m**, `ParkingDetectionConfig.kt:846`) — el mismo suelo global que
> discuten [DET-BT-PIN-GRADE-IS-NOT-A-DRIVING-THRESHOLD-001] y [DET-COARSE-FIX-DRIVE-PROOF-001].
> Es decir: el espejismo de interior ya no sobrevive si la traza lo desmiente, pero **un solo fix
> sigue siendo suficiente mientras nada lo contradiga**. El ticket sigue abierto por eso.

## Problema

`ParkingDetectionState.hasEverReachedDrivingSpeed` — la bandera que desarma **todos** los guards
anti-caminata (false-ENTER abort, presupuesto de no-movimiento, la puerta de `steps+egress`) — la
sigue poniendo **UN solo fix**:

```kotlin
// CoordinatorParkingDetector.kt
val hasJustReachedSpeed = !s.hasEverReachedDrivingSpeed &&
        location.speed >= config.minimumTripSpeedMps &&
        credibleSpeedFix                       // acc ≤ minGpsAccuracyForDriving
```

No hay corroboración: ni ventana, ni traza, ni desplazamiento. `credibleSpeedFix` sólo mira la
precisión declarada — y un espejismo de interior declara precisiones excelentes.

Está admitido por escrito en el propio fichero (`DET-DRIVE-PROOF-001`):

> «Arm seeding and session lifecycle (hasEverReachedDrivingSpeed) are **deliberately untouched**:
> the event nominates, only corroborated movement CONFIRMS.»

DET-DRIVE-PROOF-001 endureció la *estadística* de sesión (`maxSpeedMps`, `driveProven`) y dejó la
*bandera de ciclo de vida* como estaba.

## Evidencia medida (no inferida)

Replay `TRACE_HOUSE_MIRAGE_001` (field 22-08-2026 20:50, Oppo, teléfono quieto dentro de casa),
armando **`Unverified`** — es decir, ya con el fix de
DET-EXIT-FIX-CANNOT-PROVE-ITS-OWN-EXIT-001 puesto:

```
movedAfterFirstFix = true      ← el primer fix (8,2 m/s, acc 5,6 m) abre la sesión
outcome            = ended     ← NO aborta por false-ENTER: ese guard ya estaba desarmado
saves              = 0
```

La plaza fantasma no se plantó — pero **no** por el guard que debía pararla. Lo que salvó ese
replay fue la etiqueta del arm: `self_observed` mantiene despierto el guard de plausibilidad de
re-aparcamiento en `ConfirmParkingUseCase`, del que las etiquetas verificadas se libran
(`ArmEvidence.isVerifiedLabel`). Es decir: **quedó un solo guard entre el espejismo y el pin.**

## Doctrina violada

*El evento NOMINA, solo el movimiento MEDIDO confirma.* Un sample Doppler aislado no es movimiento
medido, y aquí abre la sesión entera.

## Diseño (esbozo, sin decidir)

Elevar `hasJustReachedSpeed` al mismo listón que ya usa `driveProven`: corroboración por traza
(`corroboratesDrive`) o por desplazamiento desde el pin (`EvaluateShortHopDriveProofUseCase`).

⚠ **No es un cambio pequeño.** `hasEverReachedDrivingSpeed` es la bandera de BUG-SHORT-TRIP («dio
la vuelta a la manzana»): subir el listón puede perder viajes cortos reales cuyo stream arranca
tarde. Antes de tocarlo hay que pasar **todas** las trazas del replay harness y mirar cuáles
dependen de que un fix suelto abra la sesión.

## Criterio de éxito

- `TRACE_HOUSE_MIRAGE_001` con arm `Unverified` acaba en `aborted_false_enter`, no en `ended`.
- Ninguna traza correcta del harness (Calle Gavia, Camelias, Enamorados, Galeote, Supermarket,
  Motorway) pierde su confirmación.

---

## Lo medido (03-09-2026) · el aviso del propio doc se cumplió, y con nombre y apellidos

El doc pedía *«pasar TODAS las trazas del replay harness y mirar cuáles dependen de que un fix suelto
abra la sesión»*. Hecho. **Depende una: `TRACE_CASA_GAP_ANCHOR_3008`** — y no es una traza cualquiera,
es un viaje REAL cuyo propio test dice *«the drive was real and the park was real, and losing it
would be its own failure»*.

Se implementaron y midieron las dos barras candidatas. Las dos cumplen el criterio del espejismo y
las dos matan la traza buena:

| Barra | `HOUSE_MIRAGE` (criterio) | `CASA_GAP_ANCHOR` | Suite |
|---|---|---|---|
| **A** · racha de 2 fixes creíbles en banda consecutivos (`driveAuthorizationMinFixes`, hermano de `pinnedAnchorRealDriveFixes`) | ✅ `aborted_false_enter` | ❌ secuencia de preguntas `[]` (esperaba 3) | 29 rojos |
| **B** · geométrica: TODO cruce nacido del stream queda provisional y lo refuta un fix de vuelta dentro del sobre del origen (extiende `DET-DISPLACEMENT-DRIVE-MUST-SURVIVE-ITS-NEXT-FIX-001`) | ✅ `aborted_false_enter` | ❌ igual, `[]` | 5 rojos |

### Por qué la traza buena se cae, medido sobre sus fixes

`TRACE_CASA_GAP_ANCHOR_3008` tiene **379 fixes y solo 7 creíbles en banda** (≥5 m/s, acc ≤50 m):
índices `[1, 23, 24, 147, 148, 155, 156]`, con racha máxima de **2**.

O sea: su primer fix en banda es el índice **1** y el siguiente no llega hasta el **23**. Con
cualquiera de las dos barras la autorización no se abre (o se revoca) en ese hueco de ~22 fixes — y
la sesión **no llega viva** hasta el 23. Por eso la secuencia de preguntas sale vacía: no es que
pregunte distinto, es que ya no existe.

### Lo que eso reencuadra

**El problema no es solo qué abre la bandera, es qué MATA la sesión mientras la evidencia está
pendiente.** Los guards anti-caminata (false-ENTER abort, presupuesto de no-movimiento) disparan
precisamente cuando la bandera está en `false`, así que **cualquier** barra que retrase la bandera
acorta también la vida de un viaje real con stream pobre. Endurecer la bandera sin tocar la vida de
la sesión cambia un FP de interior por un FN de coche real — y `DET-COARSE-FIX-DRIVE-PROOF-001`
documenta exactamente esa misma población (móviles con accuracy crónicamente mala).

Dirección que señala la evidencia, para quien lo retome: **separar «puede confirmar» de «puede
seguir viva»**. Que el cruce solitario siga abriendo la sesión (para no matarla), pero que lo que
desarma no sea todo a la vez — o que los guards que la matan tengan su propia tolerancia mientras
haya un cruce provisional pendiente.

⚠️ Observación sin explicar, por si alguien repite el experimento: con la variante **B** aparecieron
además 2 NPE en `ArmLabelTest` (`getLabel()`/`getDriveAuthorization()` sobre un `null`) que no se
investigaron — mirarlas antes que nada, porque no se ve la relación.

⛔ **No se dejó código**: la rama se revirtió entera y la suite quedó en 2.161/0. Lo que queda es
esta medición, que es lo que faltaba para poder decidir.


---

## El cierre (06-10-2026) · no era la bandera de ciclo de vida, era la OTRA barra

⛔ **Las dos barras que este doc proponía siguen refutadas, y no se volvieron a tocar.** El fix no
sube `hasEverReachedDrivingSpeed`, así que la objeción central del 03-09 —*endurecer la bandera
acorta la vida de un viaje real con stream pobre*— no aplica: `TRACE_CASA_GAP_ANCHOR_3008` sigue
viva y sigue confirmando.

### El campo que lo reencuadró

Field 05-10-2026, Oppo, sesión `1791123836936` (uno de los tres FP del lote). Un sentry-wake armó la
sesión a **1,4 km** del pin con el usuario **andando** por Granada, y se plantó un pin EXACTO en una
calle peatonal. Al tirar del hilo, el `proven` que lo autorizó **no vino de la bandera ni del
track**: vino de `SHORT_HOP`.

```
16:23:56  FIX  spd 5.88 m/s = 21.2 km/h  acc  5.2    ┐ las tres primeras fijaciones
16:23:58  FIX  spd 3.78 m/s = 13.6 km/h  acc 13.5    │ de la sesión, en CUATRO segundos
16:24:00  FIX  spd 5.65 m/s = 20.3 km/h  acc 18.2    ┘ — un receptor convergiendo en frío
          posición recorrida en esos 4 s: 18 m
          summary de la sesión: drive 2/401fix · vmax 21 km/h · steps 1192
```

Las tres pasaban toda la geometría contra el pin (1,4 km está muy por encima del suelo de 400 m, y
en 4 s no se anda nada), así que el run llegó a 3, `SHORT_HOP` latchó, `provenMaxSpeedMps` se
promovió a 5,88 m/s **y desde ahí toda la escalera leyó «esta sesión midió conducción»**.

### La causa: dos barras para una pregunta, en el mismo fichero

`shortHopQualifies` preguntaba «¿esta fijación mide conducción?» con
`isCredibleDrivingSpeed`, cuyo listón es **`minimumDepartureSpeedKmh` = 10 km/h** — el de *«¿se está
yendo conduciendo?»*, que es otra pregunta. Once líneas más arriba, en el mismo fichero, `fixInBand`
responde la misma con **`minimumTripSpeedMps` = 18 km/h**. El espejismo vivía entre las dos.

[DET-SENTRY-ARM-PEDESTRIAN-CLOCK-001] ya había visto el agujero del sentry-wake y lo cerró
exigiendo que *«el run MIDA conducción fijación a fijación»*. La regla era correcta; el listón con
el que se midió, no.

### Los tres discriminantes probados, y por qué sólo uno vale

| Candidato | Espejismo (FP3) | Hop real 14-08 | Hop tardío real 15-08 |
|---|---|---|---|
| **C** · el desplazamiento que la sesión OBSERVÓ supera el alcance peatonal | ✅ 18 m en 4 s, no cualifica | ✅ 900 m | ❌ **muere**: sólo ve la COLA del viaje, 33 m en 10 s con acc 23 m — paso peatonal |
| **D** · barra de conducción (`minimumTripSpeedMps`) por fijación, run de 3 | ✅ el 13,6 km/h rompe el run | ❌ **muere**: su maniobra final va a 10,8 km/h y el run nunca llega a 3 | ✅ |
| **D′** · igual, **run de 2** | ✅ | ✅ | ✅ |

**C es la idea bonita y está medida como falsa**: en el hop tardío real el GPS está tan sucio
(acc 23-24 m) que la posición apenas se separa, y su Doppler es lo único que lo distingue del
espejismo. El desplazamiento observado no separa — 3,3 m/s el bueno contra 4,5 m/s el malo.

### Lo que entra

Una línea y una constante, **y se mueven juntas**: pedir más a cada fijación obliga a pedir menos
fijaciones, o el viaje real del 14-08 (que acaba maniobrando a 10,8 km/h) se pierde.

```kotlin
// shortHopQualifies, al final de la escalera
return fix.speed >= config.minimumTripSpeedMps     // era isCredibleDrivingSpeed → 10 km/h
```
```kotlin
val shortHopProofFixes: Int = 2                    // era 3
```

**Testigo falsificado** [TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED-001]: con `return true` en esa
línea, `should not prove the drive from a cold start burst below the driving bar` falla. Viene con
su discriminante, `should still prove the drive at real driving speed` (7,1 m/s, la cola del viaje
real del 15-08).

### Y la nota de los NPE, por fin explicada

El aviso del 03-09 —*«2 NPE en `ArmLabelTest` que no se investigaron, mirarlas antes que nada»*— se
cerró de camino: **son flaky por orden de carga de clases, y no tienen ninguna relación con este
ticket**. Medido: entre dos pasadas en las que sólo cambiaron COMENTARIOS, pasaron de verdes a rojos.
→ `docs/backlog/test-armlabel-is-flaky-by-class-init-race-001.md`

### Lo que sigue abierto

El diseño original de este doc — subir `hasEverReachedDrivingSpeed` al listón de `driveProven` —
**no se ha hecho y sigue refutado tal como está escrito**. Lo que se ha cerrado es la vía por la que
el espejismo entraba de verdad. Si vuelve a aparecer un FP de esta familia, el reencuadre del 03-09
(*separar «puede confirmar» de «puede seguir viva»*) sigue siendo la dirección.
