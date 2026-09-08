# UI-A-SPINNER-LIVES-IN-THE-BUTTON-THAT-WAS-PRESSED-001 · el estado de carga lleva el discriminador de la acción, no un booleano

**Estado:** ✅ Done (08-09-2026) · mergeado a master por squash

## Problema

Reportado por el user: *«a veces los loadings en botones no aparecen en su botón correspondiente»*.

Pasa en dos sitios visibles hoy, los dos con la misma forma: una superficie con **dos acciones
asíncronas** y **una sola bandera** de carga en el estado.

1. **Diálogo de liberar plaza** (`HomeReleaseDialog`). Dos botones — «Avisar de la plaza»
   (primario) y «Solo me voy» (secundario) — y los dos disparan `HomeIntent.ReleaseParking`, que
   levanta `HomeState.isReleasingParking`. Pulsas el de abajo → **el spinner aparece en el de
   arriba**, y el que pulsaste se queda apagado sin explicar nada.
2. **Peek de aparcamiento en edición** (`AddingParkingPeek`). «Confirmar» (Filled) y «Volver a
   aparcar aquí» (Outlined) disparan los dos `ConfirmAddParking`, que levanta `isSavingParking`.
   Solo el Filled declara `isLoading`, así que pulsar repark pinta el spinner en el botón de encima.

## Doctrina violada

**Un botón dice lo que le está pasando a ÉL.** Un spinner en un botón que no se pulsó es una
mentira sobre qué acción está corriendo — y en el caso del diálogo de liberar, las dos acciones
tienen consecuencias distintas (publicar la plaza para la comunidad vs. no publicarla), así que el
spinner está señalando la opción equivocada de una decisión que el usuario acaba de tomar.

También «sistemas, no parches» (CLAUDE.md): el fallo no está en los dos call sites, está en que el
estado modela *«algo está en vuelo»* en vez de *«ESTA acción está en vuelo»*, y en que
`PapAlertDialog` solo sabe pintar spinner en su acción primaria.

## Señales / datos disponibles

El discriminador **ya existe** en el intent y se tira al escribir el estado:

- `HomeIntent.ReleaseParking(sessionId, reason: ParkingReleaseReason)` → `isReleasingParking: Boolean`
- `HomeIntent.ConfirmAddParking(asNewSession: Boolean)` → `isSavingParking: Boolean`

Y el patrón correcto ya está establecido en tres sitios del repo, que sirven de referencia:

- `PaparcarSocialProviders` → `isLoading = loadingProvider == provider`
- `VehiclePageContent.SetActiveRow` → `settingActiveVehicleId == vehicle.id`
- `HomeViewModel.deleteZone` → `deletingZoneIds: Set<String>`

## Diseño

El invariante vive en dos capas, una vez en cada una:

**1 · El estado guarda QUÉ acción está en vuelo, no que haya uno.**
El booleano se sustituye por el discriminador que el intent ya traía, con el booleano derivado para
que los `enabled = !isSaving` existentes no se toquen:

```kotlin
val releasingReason: ParkingReleaseReason? = null
val isReleasingParking: Boolean get() = releasingReason != null
```

**2 · `PapAlertDialog` nombra la acción que carga.**
`isLoading: Boolean` → `loadingAction: PapDialogAction?` (`Primary` / `Secondary`), y
`SecondaryAction` gana el mismo camino de spinner que ya tenía `PrimaryAction` — hoy no tiene
ninguno, que es la razón de raíz de que el diálogo de liberar no pueda pintar el spinner donde toca.

## Criterio de éxito

- Pulsar «Solo me voy» pinta el spinner en «Solo me voy». ✅
- Pulsar «Volver a aparcar aquí» pinta el spinner en «Volver a aparcar aquí». ✅
- Ninguna superficie declara un `isLoading` que su `onClick` no pueda producir. ✅
- Tests de ViewModel sobre el estado: `releasingReason` / `savingParkingAsNewSession` valen lo que
  se pidió mientras la operación está en vuelo, y vuelven a `null` al resolver. ✅

## Tests

Tres nuevos, los tres sobre el discriminador (no sobre el pixel). Los tres corren con
`StandardTestDispatcher` a propósito: el `UnconfinedTestDispatcher` de la suite termina la operación
antes de que exista ventana en vuelo que mirar, y un test que asserta sobre una ventana inexistente
pasaría sin haber comprobado nada.

- `HomeViewModelTest.should_remember_the_chosen_reason_while_the_release_is_in_flight`
- `HomeViewModelTest.should_remember_which_confirm_is_in_flight_on_ConfirmAddParking_from_edit`
- `SettingsViewModelTest.should_keep_the_confirmation_open_while_the_deletion_is_in_flight`
  — sustituye a `should_setIsDeletingAccount_true_and_hide_dialog_on_confirm`, que asertaba
  **exactamente el bug** (`assertFalse(showDeleteAccountConfirmation)` tras confirmar), y se le suma
  `should_keep_the_confirmation_open_for_retry_on_deleteAccount_failure`.

Del segundo barrido, cuatro más:

- `HomeViewModelTest.should_keep_the_zone_peek_open_while_its_delete_is_in_flight`
- `HomeViewModelTest.should_keep_the_parking_peek_open_while_the_record_delete_is_in_flight`
- `ParkingHistoryViewModelTest.should_hold_the_withdraw_confirm_until_the_retraction_lands`
- `ParkingHistoryViewModelTest.should_not_claim_the_focused_parking_is_being_withdrawn_when_another_one_is`
  — el caso que justifica que `withdrawingSessionId` sea un id: el stepper puede moverse mientras la
  escritura corre.

Suite completa: **2.409 tests / 214 clases, 0 fallos**. `assembleMockDebug` +
`compileProdDebugKotlin` OK.

## Dev Catalog

- **Ajustes → «Borrando cuenta»**: variante nueva. Estado que **antes no existía** — el diálogo se
  cerraba antes de poder pintarlo.
- **Historial → «Cerrada · quitando del historial»**: variante nueva. El confirm vive en un
  `remember` dentro de la ficha, así que se mira PULSANDO «Quitar del historial»: el diálogo sale ya
  con el spinner. Es la única forma de ver el estado sin una retirada real.
- **Plus → «Paywall · restaurando compra»**: la variante ya estaba y no enseñaba nada (el botón solo
  se apagaba). Ahora enseña el spinner sin ida y vuelta a la tienda.
- Diálogo de liberar, peek de aparcamiento y peek de zona son `internal` de `:shared`: sin preview
  ni entrada de galería, y la galería no los alcanza. No hay nada que sincronizar ahí.

## Segundo barrido: los 15 `PapAlertDialog` (a petición del user)

El primer barrido miró los botones que ya declaraban `isLoading`. Eso dejó fuera una familia
entera: los **diálogos de confirmación** que cuelgan de los peeks. Inventario de los 15:

- **5 con acción asíncrona y spinner correcto**: liberar plaza, guardar zona, enviar diagnóstico,
  borrar cuenta, borrar vehículo.
- **6 sin acción asíncrona** (navegan o cierran): los dos de bootstrap de `App.kt`,
  `ZoneAlertsDialog`, los dos de permisos, `BluetoothRecommendationDialog`.
- **1 coherente aunque cierre**: `SetActiveConfirmDialog` de Vehículos — el spinner va al
  `SetActiveRow`, que sigue en pantalla y es de donde salió el gesto (resuelto per-id).
- **3 con acción asíncrona y CERO spinner** → los de abajo.

### El segundo contrato: escritura destructiva que se descarta a sí misma

Tres confirms destructivos se cerraban en el mismo tap que lanzaba la escritura:

| Diálogo | Lanza | Qué hacía |
|---|---|---|
| Corregir aparcamiento → «Borrar registro» | `ReleaseParking(RECORD_DELETED)` | cerraba el diálogo **y** el peek entero |
| Zona → «Borrar zona» | `DeleteZone` | cerraba el diálogo; el VM cerraba el peek al instante |
| Historial → «Quitar del historial» | `WithdrawParking` | cerraba el diálogo; no había ningún estado en vuelo |

Con eso, un borrado irreversible corría sin nada en pantalla que lo dijera, y su fallo llegaba por
snackbar sobre una pantalla que el usuario ya había abandonado, con el registro aparentemente ido.
La app tenía **dos contratos para el mismo gesto**: borrar vehículo y borrar cuenta bloquean; estos
tres no.

**Decisión del user (08-09): un solo contrato.** Los tres se alinean con borrar vehículo — el
diálogo se queda con su spinner hasta que la escritura aterriza, y el fallo lo deja abierto para
reintentar. Plomería:

- `HomeViewModel.releaseParking` cierra el modo con `clearedModeFields()` **al resolver**, no al
  tocar. `RECORD_DELETED` tiene un único call site de producción, así que el cambio no alcanza a
  ninguna otra salida.
- `HomeViewModel.deleteZone` mueve su `clearedModeFields()` dentro del `.onSuccess`.
  `HomePeekSlice.isDeletingZone` proyecta `editingZoneId in deletingZoneIds`: la peek solo puede
  borrar la zona que edita, así que el `Set` entero no cruza la frontera.
- `ParkingHistoryState.withdrawingSessionId` es nuevo — y es un **id, no un flag**: la pantalla
  camina entre aparcamientos con ‹ / ›, y un booleano dejaría al spinner seguir al usuario hasta
  otro aparcamiento distinto. `isWithdrawingFocused` es lo que lee el diálogo.

## Consumidores auditados

Grep de todo lo que pinta un spinner dentro de un botón (`isLoading =` + `CircularProgressIndicator`
en `presentation/` y `ui/components/`):

| Sitio | Veredicto |
|---|---|
| `HomeReleaseDialog.kt:36` | 🔴 **bug visible** — 2 acciones, 1 bandera → ✅ `releasingReason` mapea a `PapDialogAction.Primary`/`Secondary` |
| `AddingParkingPeek.kt:154-176` | 🔴 **bug visible** — 2 acciones, 1 bandera → ✅ cada confirm lee `savingAsNewSession == false` / `== true` |
| `SettingsScreen.kt:363` (borrar cuenta) | 🟠 `isLoading` **muerto**: `SettingsViewModel:272` cerraba el diálogo en el mismo `updateState` que levanta la bandera → el spinner salía en el botón de la `DangerZoneCard` de detrás. Contrato opuesto al de `VehicleRegistrationScreen.kt:615` → ✅ el diálogo se queda abierto; la `DangerZoneCard` pierde su spinner (era UI inalcanzable: abre el modal, no borra) |
| `AddingZonePeek.kt:183` | 🟠 bandera en un botón que **no guarda**: su `onClick` solo abre `ZoneNameDialog`, que es quien lanza el guardado (y sí pinta su propio spinner, `:257`) → ✅ el botón que abre ya no dice que guarda |
| `PaywallScreen.kt:490` (restaurar compras) | 🟠 `state.restoring` solo **deshabilitaba**: no había spinner en ninguna parte → ✅ spinner en el propio botón |
| `SettingsScreen.kt:321` (enviar diagnóstico) | ✅ ya correcto; migrado a `loadingAction` |
| `VehicleRegistrationScreen.kt:620` | ✅ ya correcto (es el modelo de referencia); migrado a `loadingAction` |
| `AddingParkingPeek.kt:206` (borrar registro) | 🔴 se descartaba a sí mismo **y al peek** → ✅ bloquea con su spinner; el VM sale del modo al resolver |
| `AddingZonePeek.kt:217` (borrar zona) | 🔴 se descartaba a sí mismo → ✅ bloquea; `clearedModeFields()` pasa al `onSuccess` |
| `ParkingHistoryDetailScreen.kt:709` (quitar del historial) | 🔴 se descartaba a sí mismo, sin ningún estado en vuelo → ✅ bloquea con `withdrawingSessionId` (id, no flag) |
| `VehiclesScreen.kt:317` (declarar activo) | ✅ cierra, pero el spinner va al `SetActiveRow` que lo originó y sigue en pantalla (per-id) |
| `App.kt:296` · `App.kt:321` · `ZoneAlertsDialog:40` · `PermissionsContent:114` · `:129` · `VehicleRegistrationScreen:210` | ✅ sin acción asíncrona — navegan o cierran, no aplica |
| `PaparcarAuthSlots.kt:481` | ✅ per-id (`loadingProvider == provider`) |
| `VehiclePageContent.kt:234` | ✅ per-id (`settingActiveVehicleId == id`) |
| `HomeViewModel.deleteZone` | ✅ per-id (`deletingZoneIds`) |
| `VehicleRegistrationScreen.kt:620 / :730` | ✅ banderas separadas (`isDeleting` / `isSaving`), diálogo abierto durante el borrado |
| `BluetoothConfigScreen.kt:144` | ✅ una sola acción en la barra |
| `ReportPeek.kt:78` | ✅ una sola acción |
| `SpotPeek` / `ParkingHistoryDetailScreen` / `PermissionsContent` | ✅ sin estado de carga (fire-and-forget) |
