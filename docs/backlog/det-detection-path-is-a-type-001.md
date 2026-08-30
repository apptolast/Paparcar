# DET-DETECTION-PATH-IS-A-TYPE-001 · la procedencia de un pin deja de ser un string que tres sitios reinterpretan

**Estado:** ✅ Done · rama `feature/DET-DETECTION-PATH-IS-A-TYPE-001-path-is-a-type` ·
worktree `../Paparcar-path-type` · apilada sobre `DET-DOUBT-REACHES-REMOTE-001`

Pieza 2 del rediseño. Cierra los fallos **#4**, **#12** y el barrido de textos del safety-net (§9.3).

## Problema

`detectionPath` es la respuesta a *«qué disparador puso este aparcamiento»* — el campo sobre el que
descansa todo el método de diagnóstico de campo. Era un `String` pelado, y cada pregunta que se le
hacía se contestaba deletreándolo otra vez en otro sitio:

| pregunta | cómo se contestaba | qué fallaba |
|---|---|---|
| **quién lo detectó** | `detectionPath.startsWith("bt")` + `else -> Assisted` | un prefijo de **dos letras** decide estrategia; y lo desconocido se **atribuía al Coordinator** teniendo `Unknown` al lado |
| **cuánto vale** | `pathLabel == "kinematic+egress"`, si no el otro | todo lo no reconocido cae al **máximo (0.90)**: un camino nuevo nace con la fiabilidad más alta sin que su autor la elija |
| **cuál es la etiqueta** | literales sueltos por todo el código | **drift**: producción escribe `"vehicleExit+window+egress"`, y el KDoc de `UserParking`, el fake del repositorio y los datos de preview dicen `"vehicle-exit"` — una procedencia que la app **no ha escrito jamás** |

## Doctrina violada

*Permisos concedidos por omisión* (§2 del rediseño), tres veces. Y en la clasificación de fuente, el
fallo asimétrico aplicado a lo que se le CUENTA al usuario: `Assisted` es una afirmación; `Unknown`
no afirma nada.

## Diseño

`DetectionPath`, sealed, en `domain/detection/`. Cada camino declara **su etiqueta**, **su
estrategia** y **qué fiabilidad puede reclamar**. La etiqueta sigue siendo el formato de cable —
persiste en Room y en Firestore — así que el tipo es el sitio que la **posee**, no una migración.

- `ofLabel()` **falla cerrado**: lo no reconocido devuelve `null` → `Unknown`.
- La familia `UnattendedZone` es la única con etiqueta **compuesta**, y por eso esto es un
  `sealed interface` y no un `enum`. Es también el único prefijo que se empareja — pero sobre una
  regla que el propio tipo posee, que es lo contrario de adivinar.
- `confirmReliability()` devuelve `null` para los caminos que no confirman en vivo: su fiabilidad se
  decide en su propio sitio y contestar aquí sería una segunda opinión.

### El barrido de §9.3: el safety-net que nombraba la causa equivocada

`SafetyNetAction.PromptStillParked` tenía **cuatro productores** y el worker imprimía **una sola
causa para los cuatro**: «LEJOS del coche pero SIN pruebas de viaje». Es falsa en tres de los cuatro,
y en uno es **exactamente lo contrario**:

| causa real | qué decía el log |
|---|---|
| el coche se vigila por BT y ninguna conexión avala el trayecto | «sin pruebas de viaje» |
| **vas a velocidad de coche**, pero el movimiento no empezó junto al tuyo (bus/taxi) | «sin pruebas de viaje» ← se midió conducción |
| hubo una salida de valla que los pasos no explican | «sin pruebas de viaje» |
| tienes la app abierta y ninguna prueba explica cómo has llegado | «sin pruebas de viaje» |

Ahora la acción **lleva su `StillParkedReason`** y el log la lee. Misma forma que la pieza: la causa
la decide quien decide, no la adivina quien escribe la línea.

## Consumidores auditados

| sitio | clasificación |
|---|---|
| `parkingDetectionSourceOf` | **cerrado** — pregunta al tipo, falla cerrado |
| `EvaluateParkingDecisionUseCase` (fiabilidad + label) | **cerrado** |
| `DetectionEffectExecutor` / `DetectionEffectDispatcher` (productores de `unattended_*`) | **cerrado** — usan `.label` |
| `FakeUserParkingRepository`, `FakeData` (preview/Dev Catalog) | **cerrado** — usaban `"vehicle-exit"`, que no existe |
| `ParkingDetectionSource` KDoc | **corregida** — afirmaba que el coordinator persiste jerga de diagnóstico. **No lo hace**: esa jerga es una nota de traza de `FastConfirmStage`, y tres cuentas reales de histórico sólo tienen etiquetas declaradas |
| `UserParking` KDoc | **cerrada** — enumeraba los valores en prosa y había derivado; ahora remite al tipo |
| Productores BT (`bt`, `bt_timeout`) | **exentos**: los escribe la estrategia BT, fuera de este evaluador. Sus etiquetas ya están declaradas en el tipo |

## Cambio de conducta, y por qué

⚠️ Una etiqueta **no reconocida** pasa de mostrarse como `Assisted` a mostrarse como `Unknown`. Es
visible para el usuario en el detalle de Historial.

Se ha tomado con dato, no por gusto: el test que exigía `Assisted` para jerga de diagnóstico
guardaba un caso que **no ocurre**. Se comprobó contra el histórico real de tres cuentas leído el
30-08 — sólo hay etiquetas declaradas y nulos, ni un path con jerga. Y `"vehicle-exit"` entra en el
mismo saco: vivía sólo en docs y fakes.

## Criterio de éxito

- ✅ Round-trip `label → ofLabel → mismo objeto` para todos los caminos fijos, y unicidad de etiquetas.
- ✅ `ofLabel` falla cerrado con etiquetas inventadas, incluida `"bt_something_new"` (el prefijo).
- ✅ Cada camino de confirm en vivo tiene SU fiabilidad; los demás devuelven `null`.
- ✅ Las cuatro causas del safety-net llegan al log.
- ✅ **1.824 tests en verde.**

## Pendiente

- [ ] Las otras tres membresías de la Pieza 2 (`isVerifiedLabel`, `VehicleType.isHumanPowered`,
      `NON_PARKING_TYPES`) — mismo patrón, otro alcance.
