# DET-BT-CAR-CANNOT-NOMINATE-A-COORDINATOR-SESSION-001 · el coche del otro carril no puede ser "tu coche"

**Estado:** ✅ Done · en master como `5d6a941f` — *"the car in the other lane cannot be 'your car'"*.
La rama `bugfix/…-veto-bt-nominator` y el worktree `../Paparcar-bt-nominator` ya no existen.

> Corregido el 2026-08-30 por [DOCS-LIVING-DOCS-MUST-MATCH-MASTER-001]: decía *"🟢 Implementado, sin
> commitear"*. Verificado por las tres vías — commit con su tag, marcador vivo en
> `VehicleFenceOwnershipPolicy` / `EvaluateArEnterArmUseCase` / `CoordinatorDetectionService`, y rama
> inexistente.

## Problema

Field test 25-08-2026, Oppo (uid `fiypNbElGlfFexLMpU9sNaMjRMD3`). Coche activo: **Ford Focus**
(`addbe660`, sin BT → Coordinator). En el garaje hay además un **Skoda Kamiq** (`abf6c516`,
`bluetoothDeviceId = 50:26:EF:16:1D:C0` → estrategia **Bluetooth**), inactivo.

A las 19:59:05, al final del viaje, el AR disparó `IN_VEHICLE ENTER` y el servicio resolvió *"tu
coche aparcado"* como:

```
→ AR ENTER at own fence — arming Coordinator, waiting for ride proof (geof=a786c135 lag=149ms dep=enter_at_car)
```

`a786c135-2500-42c4-8adc-dd7d695ae0d8` es un pin **manual del 21-08-2026 19:52**, en C/ Góndola 7,
`detectionPath = manual`, `armEvidence = null`, `isActive = true` desde entonces — y su
`vehicleId` es **`abf6c516`, el Kamiq**. Un coche BT, que el user no ha movido en cinco días, se
convirtió en el sujeto de una sesión del Coordinator.

Consecuencias encadenadas:

1. Como ese pin está **en casa** y el ancla del viaje vivo estaba en el gimnasio,
   `shouldSupersedeRunningSession` midió **6.214 m** y canceló la sesión que llevaba 23 min de
   conducción medida (→ `DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001`).
2. La sesión sucesora nació anclada al Kamiq y murió en `aborted_false_enter`.
3. Desde entonces **el Oppo vigila el pin del Kamiq del 21-08**: el `parkdiag` de la madrugada del 26
   está lleno de `[exact-alarm] geof=a786c135: sigues junto al coche (d=5m, radio 85m)` hasta la
   01:38. El Focus, el coche que sí se condujo, no tiene aparcamiento ninguno.

El código responsable son **dos líneas idénticas** en `CoordinatorDetectionService`:

```kotlin
// :355 (handleSentryWake) y :847 (handleArTransition)
val session = sessions.firstOrNull { it.vehicleId == activeVehicleId } ?: sessions.firstOrNull()
```

El `?:` convierte *"el coche activo no tiene sesión aparcada"* en *"entonces vale cualquier coche"*.

## Doctrina violada

**«Dos estrategias independientes que NUNCA se mezclan.»** El Kamiq pertenece en exclusiva al carril
Bluetooth; su identidad es la MAC. Que su pin sirviera de nominador a una sesión del Coordinator es
la mezcla que CLAUDE.md prohíbe, por la puerta de atrás.

**Y esto ya estaba decidido, escrito y probado — sólo que en un sitio y no en el otro.**
`VehicleFenceOwnershipPolicy.resolveSessionVehicleId` lleva el veto dentro, con
`[DET-BT-OWNERSHIP-001]` y **este mismo incidente de campo** en su KDoc:

> *A Bluetooth-paired nominator is VETOED […] (field 2026-08-11: a parked Kamiq's fence nominated it
> for 8 Focus trips, each confirm re-fencing the Kamiq and re-arming the chain).*

Su único consumidor es `DetectionEffectDispatcher:265` — la **atribución**, al confirmar. El veto
nunca bajó al momento de **nominar**. Es el corolario de `feedback_systems_not_patches`: se cerró la
vía donde mordió en agosto y quedaron abiertas las otras dos.

## Señales / datos disponibles

- `Vehicle.bluetoothDeviceId != null` es el discriminante, ya usado por
  `VehicleFenceOwnershipPolicy` en sus tres funciones.
- `HomeTripController.parkedOriginFor` **ya lo hace bien** y dice por qué en su firma: `byVehicle ?:
  sessions.singleOrNull()?.takeIf { vehicleId == null }` — *"vehicle-scoped only (never guesses among
  cars)"*. Existe el precedente correcto dentro del propio repo.
- La lista llega ordenada `timestamp DESC` (`UserParkingDao.observeActive`), así que el fallback no
  es "el más viejo": es **el más reciente de otro coche**, que es aún peor de razonar.

## Diseño

Un solo invariante, en un solo sitio:

> **La sesión que nomina una detección es la del coche que el usuario declaró activo. Si ese coche no
> tiene sesión aparcada, la respuesta es «ninguna» — nunca la de otro coche, y jamás la de un coche
> BT.**

- Extender `VehicleFenceOwnershipPolicy` con el veredicto de **nominación** (predicado puro, junto a
  sus hermanos `shouldOwnFence` / `resolveSessionVehicleId`; no es un caso de uso —
  `DET-VERDICT-NOT-PREDICATE-001`: su resultado no aparece en el vocabulario de diagnóstico).
- `:355` y `:847` consultan ese veredicto en vez de encadenar un `?:`.
- Decidir explícitamente y escribirlo: cuando el coche activo **sí** es el BT-emparejado, ese carril
  es el suyo y el Coordinator no debería estar nominando nada — `resolveSessionVehicleId` ya trata
  ese caso ("a possibly-redundant pin beats a lost parking"); aquí hay que decidirlo aparte, no
  heredarlo por parecido.

Cuestión abierta, **fuera del alcance de este ticket salvo que el barrido diga lo contrario**: por
qué un pin manual sobrevive cinco días con `isActive = true`. La honest-close ladder lo mantuvo vivo
con razón (el user estaba a 5 m), pero un pin de un coche que no se mueve desde el 21 es sedimento.
Si al implementar resulta que el veto lo deja huérfano sin cerrarlo, se abre ticket propio.

> **Resuelto al implementar: NO lo deja huérfano, así que no se abre ticket.** El safety-net lee la
> lista entera y decide por sesión, de modo que el pin del Kamiq conserva su vigilancia; lo único que
> pierde es el poder de nominar una sesión del Coordinator. El sedimento sigue abierto tal cual
> estaba, sin deuda nueva.

## Implementación

**El veredicto** — `VehicleFenceOwnershipPolicy.mayNominateDetection`, tercer hermano de
`shouldOwnFence` / `resolveSessionVehicleId`, predicado puro sobre primitivos:

```kotlin
if (activeVehicleId != null) sessionVehicleId == activeVehicleId
else isOnlyActiveSession && !sessionVehicleIsBtPaired
```

**Las dos decisiones que el diseño pedía tomar explícitamente:**

1. **Coche activo declarado que ES el BT-emparejado → SÍ nomina.** No se hereda de
   `resolveSessionVehicleId` por parecido: el emparejamiento no borra la declaración del user, y con
   el Bluetooth APAGADO en el móvil `resolveStrategy` manda ese mismo coche al Coordinator. Vetarlo
   sería un falso negativo mudo en el coche que su dueño declaró.
2. **Sesión sin atribuir (`vehicleId == null`) con coche declarado → NO nomina.** Es un cambio de
   conducta respecto al `?:` viejo, y es deliberado: "no sabemos de quién es este pin" es exactamente
   la adivinanza que se cierra.

**El I/O** — `CoordinatorDetectionService.nominatingSession(sessions, activeVehicleId)`. La consulta
de emparejamiento se paga **solo** en la rama sin declaración, la única que lo mira; un fallo de
repositorio ahí degrada a "no emparejado", que como mucho permite la única sesión que un usuario de
un coche tiene igualmente — nunca la nominación por OTRO coche, que es el fallo que se cierra.

**Diagnóstico**: un sentry-wake con sesiones pero ninguna nominable ya no reutiliza *"no parked
session"* — dice cuántas hay y de qué coche activo no son. Plantarse es el objetivo, no un fallo.

## Tests — 8 nuevos en `VehicleFenceOwnershipPolicyTest`, **neutralización verificada en los dos sentidos**

| Neutralización | Rojos |
|---|---|
| `mayNominateDetection = true` (la permisividad vieja) | **5** — los cinco negativos |
| `mayNominateDetection = false` (sobre-veto) | **3** — los tres positivos |

Los 8 discriminan; ninguno pasa por construcción. Cubren los dos criterios de éxito unitarios: coche
activo sin sesión + sesión de un coche BT → no arma; + sesión de otro coche **no** BT → tampoco.

## Fuera de la suite

`docs/detection/PARKING-DETECTION.md` §2 lleva la entrada completa. Sin strings, sin pantalla, sin
estado MVI y sin `detectionPath` nuevo → Dev Catalog e i18n no aplican; `assembleMockDebug`
verificado igualmente.

## Criterio de éxito

- Test: con coche activo sin sesión + una sesión activa de un coche BT, el sentry-wake y el
  AR ENTER **no arman** contra ella (y el sentry-wake responde *"no parked session; standing down"*).
- Test: con coche activo sin sesión + una sesión activa de otro coche **no BT**, tampoco nomina.
- Campo: con el pin del Kamiq presente, un viaje del Focus deja su pin del Focus y el `parkdiag`
  nunca cita `geof=a786c135` durante ese viaje.

## Consumidores auditados

Invariante: *quién puede ser "la sesión aparcada de tu coche"*.

| Sitio | Forma | Clasificación |
|---|---|---|
| `CoordinatorDetectionService:355` (SENTRY_WAKE) | `?: sessions.firstOrNull()` | ✅ **cerrado** → `nominatingSession(...)` |
| `CoordinatorDetectionService:847` (AR_TRANSITION) | `?: sessions.firstOrNull()` | ✅ **cerrado — donde mordió** → `nominatingSession(...)` |
| `ParkingSafetyNetWorker:122` | lee la lista **entera**, decide por sesión | ✅ exento, **verificado**: nunca elige una "tu coche" |
| `HomeTripController:314` | `byVehicle ?: singleOrNull().takeIf { vehicleId == null }` | ✅ ya correcto — es el patrón que se copió |
| `EvaluateGeofenceExitUseCase:85` | `.filter { it.session.vehicleId == activeVehicleId }` | ✅ **segundo precedente correcto**, sin elvis (hallado en el barrido) |
| `DetectionEffectDispatcher:265` | `resolveSessionVehicleId` | ✅ ya lleva el veto BT |
| `ConfirmParkingUseCase:350`, `GeofenceJanitorWorker:90` | `shouldOwnFence` | ✅ ya consultan la política |
| `ObserveDetectionReadinessUseCase`, `ObserveParkedVehiclesUseCase`, `ParkingLocationViewModel` | lista completa (`mapNotNull` por sesión) | ✅ exentos, **verificados** |

`grep` de la forma culpable (`?: sessions.firstOrNull()`) en todo `composeApp/src`: **cero hits**
tras el fix. No quedaba una tercera copia.

## Relacionado

- `DET-SUPERSEDE-CANNOT-DISCARD-A-MEASURED-DRIVE-001` — el otro ticket del mismo FN. Independientes:
  con el coche correcto el supersede habría disparado igual ante un re-embarque legítimo lejos del
  origen; y con el traspaso de prueba arreglado, este bug seguiría plantando el pin en el coche
  equivocado.
- `DET-BT-OWNERSHIP-001` (el veto original, aplicado sólo a la atribución), `VEH-ACTIVE-FENCE-001`.
