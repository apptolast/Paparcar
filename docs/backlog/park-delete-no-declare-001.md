# PARK-DELETE-NO-DECLARE-001 · Eliminar un registro de aparcamiento no debe declarar ese coche como activo

**Estado:** 🟡 Implementado, SIN commitear · 1122 tests verdes (+9) · pendiente validar en device ·
rama `bugfix/PARK-DELETE-NO-DECLARE-001-delete-record-keeps-active-vehicle` ·
worktree `../Paparcar-park-delete-no-declare`

## Problema
14-08-2026, reporte del user: **borra el aparcamiento activo del Škoda Kamiq y el Ford Focus deja de
ser el vehículo activo**. El Kamiq pasa a activo sin que el user haya declarado nada sobre él, y con
él se llevan las geocercas del Focus (`SwapActiveVehicleFencesUseCase`), que se queda sin vigilancia.

Camino real: sheet de edición del aparcamiento → botón rojo **Eliminar** →
`HomeIntent.ReleaseParking(sessionId, publishSpot = false)` → `HomeViewModel.releaseParking()`.

## Doctrina violada
*El evento NOMINA, solo el movimiento MEDIDO confirma* — trasladada al plano de identidad: **solo el
usuario declara qué coche conduce**. Borrar un registro erróneo no es una declaración de conducción;
es decir "esto nunca pasó". Inferir identidad de un borrado es exactamente el tipo de confirmación
sin evidencia que la doctrina prohíbe, y aquí además tiene coste real (el coche vigilado pierde sus
vallas).

Secundario: *sistemas, no parches* — la causa raíz es un flag sobrecargado, no un `if` que falte.

## Causa raíz
`HomeViewModel.releaseParking()` declaraba activo el coche de la sesión **incondicionalmente**:

```kotlin
target.vehicleId?.let { vehicleId -> declareActiveVehicle(vehicleId) }   // sin mirar el motivo
```

Eso es correcto para *"me voy en este coche"* (diálogo de liberar plaza), pero el intent
`ReleaseParking` lo comparten dos acciones con significados distintos, y su único parámetro
(`publishSpot`) describe **si se publica**, no **por qué se cierra la sesión**:

| Call site | Significado real | ¿Publica? | ¿Declara coche activo? |
|---|---|---|---|
| `HomeScreen` · diálogo "Liberar plaza" | me voy en este coche | sí | sí |
| `HomeScreen` · diálogo "Solo eliminar" | me voy, pero no comparto la plaza | no | sí |
| `AddingParkingPeek` · "Eliminar registro" | el registro estaba mal | no | **no** |

Con solo `publishSpot`, las filas 2 y 3 son indistinguibles → el borrado heredaba la semántica de la
salida.

## Segunda mitad del bug — el coche pesa tanto como el motivo
Revisión del user (14-08): *"si me voy con el Kamiq, que es por BT, no tendría nada que ver con
activo; activo solo es para cuando no tenemos BT"*. Correcto, y el repo ya lo tenía escrito en dos
sitios:

- `VehicleFenceOwnershipPolicy.shouldOwnFence(activo, btPaired) = activo || btPaired` — un coche BT
  posee valla **sin** el flag activo.
- `resolveSessionVehicleId(...)` [DET-BT-OWNERSHIP-001] — un nominador BT está vetado y la atribución
  cae al activo, *porque el coordinator ES la estrategia del vehículo activo*.

Es decir: **el flag activo es la declaración de identidad del coche que no tiene otra**. Un coche BT
se identifica por su MAC; declararlo activo no le aporta nada y le quita al coche del coordinator su
única señal (y sus vallas, vía el swap). Así que ni siquiera una salida REAL en el Kamiq debía tocar
el flag — el bug tenía dos mitades y la primera versión solo cerró una.

Matiz que se preserva: se veta la declaración **inferida** (soltar plaza), no la **explícita**
("hacer activo" en Vehículos, "conduzco este coche"), que sigue valiendo para cualquier coche, BT
incluido — es justo el caso que DET-BT-OWNERSHIP-001 contempla cuando el activo es el propio
nominador BT. Si el user quiere que el coordinator vigile el Kamiq con el BT apagado, lo declara él.

## Diseño — el motivo es el dato, `publishSpot` se deriva
Un enum de dominio `ParkingReleaseReason` sustituye al booleano y **posee las dos consecuencias**, de
modo que no existe combinación ilegal ni decisión repetida en los call sites:

```kotlin
enum class ParkingReleaseReason(val publishesSpot: Boolean, val isDeparture: Boolean) {
    DEPARTURE_PUBLISHED(publishesSpot = true, isDeparture = true),     // "Liberar plaza"
    DEPARTURE_UNPUBLISHED(publishesSpot = false, isDeparture = true),  // "Solo eliminar" del diálogo
    RECORD_DELETED(publishesSpot = false, isDeparture = false),        // "Eliminar registro"
}
```

`isDeparture` es un HECHO (qué pasó), no el veredicto. El veredicto compone motivo + coche en la
policy que ya es dueña de esta asimetría, para no abrir una segunda casa del mismo invariante:

```kotlin
// VehicleFenceOwnershipPolicy — junto a shouldOwnFence y resolveSessionVehicleId
fun shouldDeclareActiveOnRelease(reason: ParkingReleaseReason, releasedVehicleIsBtPaired: Boolean) =
    reason.isDeparture && !releasedVehicleIsBtPaired
```

- El invariante *"el flag activo es la identidad del coche que no tiene otra"* vive en **un sitio**;
  `HomeViewModel.releaseParking()` lo consulta, no lo reimplementa.
- `ReleaseActiveParkingSessionUseCase` recibe el motivo en vez de `publishSpot` y lo deriva.
- El motivo se estampa en la telemetría (`DetectionEvent.Released.reason`), reutilizando la columna
  `reason` que ya existe en `DetectionEventDto` → **sin cambio de superficie del serializador**. Así
  un cierre de sesión dice siempre *por qué* se cerró (regla de provenance).

Un call site nuevo tiene que elegir motivo explícito: no hay default que decida por él.

## Criterio de éxito
- Borrar el registro de cualquier coche deja intacto el vehículo activo y sus geocercas.
- Soltar la plaza de un coche **BT** (publique o no) tampoco toca el activo — sí publica la plaza.
- Soltar la plaza de un coche **sin BT** sigue declarándolo activo (era su única identidad).
- Tests unitarios: 3 en `VehicleFenceOwnershipPolicyTest` (motivo × coche) + 3 en `HomeViewModelTest`
  (Kamiq borrado, Kamiq BT soltado, borrado limpia sesión sin publicar). 1122 verdes en total.
- Verificación en device: Focus activo + pin del Kamiq → eliminar registro → Focus sigue activo; y
  liberar plaza del Kamiq → Focus sigue activo con sus vallas.

## Consumidores auditados
`grep` de `declareActiveVehicle` (todos los sitios que declaran identidad de coche):

| Sitio | Veredicto |
|---|---|
| `HomeViewModel.releaseParking()` :469 | **el bug** → ahora vía `shouldDeclareActiveOnRelease(motivo, esBT)` |
| `HomeViewModel.startDrivingDetection()` :618 | correcto — "conduzco este coche" es declaración EXPLÍCITA (vale también para un coche BT) |
| `VehiclesViewModel` :112 | correcto — el user pulsa "hacer activo" (explícita) |

`grep` de `ReleaseParking` / `publishSpot` (todos los cierres de sesión de usuario):

| Sitio | Motivo asignado |
|---|---|
| `HomeScreen.HomeReleaseDialogHost` · `onPublishSpot` | `DEPARTURE_PUBLISHED` |
| `HomeScreen.HomeReleaseDialogHost` · `onDeleteOnly` | `DEPARTURE_UNPUBLISHED` |
| `AddingParkingPeek` · confirmación de borrado | `RECORD_DELETED` |
| `ProcessConfirmedDepartureUseCase` (salida automática) | fuera de alcance — no pasa por este intent y su `publishSpot` significa "demasiado viejo para anunciar", no motivo de usuario |

## Notas
- La copy del diálogo de liberar ya promete que ese coche pasa a vigilado; sigue siendo cierta para
  sus dos botones. El borrado del sheet no promete nada → no hay copy que cambiar.
- No se toca el borrado de geocerca: eliminar el registro sigue retirando la valla de esa sesión.
