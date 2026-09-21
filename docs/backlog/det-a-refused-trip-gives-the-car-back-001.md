# DET-A-REFUSED-TRIP-GIVES-THE-CAR-BACK-001 · Un viaje que el conductor niega devuelve el coche

**Estado:** 🔵 En revisión · rama `bugfix/DET-A-REFUSED-TRIP-GIVES-THE-CAR-BACK-001` · worktree
`../Paparcar-giveback`

## Problema

Pregunta del user (21-09-2026), sobre el verbo «No voy en mi coche» de
[WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001]:

> «Pero si revertimos la detección debería quedarse como antes, teniendo nuestra última sesión de
> aparcamiento, ¿es así?»

**En dos de los tres casos sí. En el tercero no, y es el que duele.**

| Hasta dónde llegó el viaje | Qué se entregó | ¿Lo devolvía el botón? |
|---|---|---|
| Armado, nada publicado | nada: `onUserStoppedDetection` sólo limpia el estado de **detección** | ✅ nada que devolver |
| Salida **deducida** | plaza provisional; sesión y geocerca **siguen en su sitio** | ✅ `retractDeducedDeparture` ya corría en el epílogo |
| Salida **presenciada** | plaza publicada **+ sesión CERRADA** | ❌ **no** |

## Causa raíz

El deshacer de la salida presenciada ya existía: `RestoreRefutedDeparture` — *«gives the car back»*—,
escrita en [DET-A-TRAIN-IS-NOT-A-CAR-001] para el AVE que confirmó salida desde el andén de Atocha.

Pero su disparador era **una sola condición**: `roadPlausibility == IMPOSSIBLE`. O sea que el coche
se devolvía cuando **la app** deducía que aquello era un tren, y **no** cuando **la persona** decía
que no era su coche. Y un viaje en el coche de un amigo es perfectamente `PLAUSIBLE` —porque es un
coche de verdad—, así que la condición **no se cumplía nunca en el caso para el que el botón
existe**.

## Doctrina

- *Fallo asimétrico, y aquí apunta a devolver*: devolver un coche que sí había salido deja una
  sesión de más, que el usuario cierra con un gesto. **No** devolverlo le deja sin su aparcamiento y
  manda a un desconocido hacia una plaza que nadie liberó.
- *La decisión vive en una función pura de `commonMain`*: el epílogo del detector la consulta, no la
  re-deriva.
- No se crea ningún camino de confirmación nuevo → **sin `detectionPath` ni `armEvidence` nuevos**.

## Diseño

`witnessedDepartureIsRefuted(roadPlausibility, outcome)` en `domain/detection/DepartureRefutation.kt`:
refuta la **carretera imposible** *o* **`SessionOutcome.StoppedByUser`**. Una línea del epílogo
(`finally`) pasa a preguntarle a ella en vez de comparar con `IMPOSSIBLE`.

Lo que lo hace un arreglo y no un parche es que **las dos entradas ya estaban y sobreviven al borrado
de estado**, sin añadir nada:

- `keepingIdentity()` conserva `outcome` **a propósito**, y su propio comentario lo dice: *«sin estas
  dos líneas el wipe borraría justo lo que la llamada venía a decir»*.
- `currentArmGeofenceId` no vive en el estado, así que la geocerca de armado sigue disponible cuando
  el epílogo la pide — y `reset()` corre **después**.

⛔ **`DOUBTFUL` no refuta**, y es deliberado: la duda ya tiene su remedio —se **pregunta**— y
devolver el coche ante una sospecha resucitaría sesiones por cada tramo raro de autovía.

## Consumidores auditados

`grep -rn "StoppedByUser|restoreRefutedDeparture(|retractDeducedDeparture(" shared/src app/src`

| Sitio | Estado |
|---|---|
| `CoordinatorParkingDetector` — el `finally` | **cerrado**: única llamada a los dos deshaceres |
| `onUserStoppedDetection` (línea 1366) | **único** sitio que estampa `StoppedByUser` |
| `ACTION_USER_STOP` → card · notificación · widget | **cubiertos por convergencia**: las tres superficies despachan el MISMO intent [WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001], así que ninguna se toca y las tres devuelven el coche |
| `onUserDeniedParking` («seguir conduciendo») | **exento**: niega un APARCAMIENTO, no el viaje |
| `ACTION_PARKING_REVERT` (post-guardado) | **exento**: otro momento y otro verbo — deshace un pin ya puesto |

## Criterio de éxito

Con una sesión aparcada, disparar detección en el coche de otro hasta que la salida se confirme, y
pulsar «No voy en mi coche»: la plaza se retira y **el coche vuelve a aparecer aparcado donde
estaba**.

## Tests

`DepartureRefutationTest` — 4:
- **el testigo**: `PLAUSIBLE` + `StoppedByUser` → devuelve. ⚠️ **Falsificado**: con la condición
  anterior (`IMPOSSIBLE` a secas) este test **falla**.
- el caso del tren, que es el que ya funcionaba;
- una salida real se queda donde está;
- `DOUBTFUL` no basta.

**3.503 tests verdes, 0 fallos** (`testAll` + `:app:compileMockDebugKotlin`).

## Pendiente

⏳ **Campo.** Es un cambio sin resultado visual propio —lo que se ve es que la tarjeta del coche
vuelve—, así que no hay captura que valga: hay que provocarlo.
