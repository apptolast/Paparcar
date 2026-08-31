# VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001 · Borrar un coche deja su historial vivo pero inalcanzable

**Estado:** ✅ Done · rama `bugfix/VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001-cascade` ·
worktree `../Paparcar-veh-cascade`
**Abierto:** 2026-08-31 sobre master `748648fc` · **decidido: cascade + bloquear si está aparcado**
(user, 31-08)

## Problema

`VehicleRepositoryImpl.deleteVehicle` (`VehicleRepositoryImpl.kt:164-204`) borra la fila del
vehículo y **no toca `parking_sessions`**:

- Local: `dao.deleteById(id)` y nada más. No existe ningún `deleteByVehicleId` en `UserParkingDao`
  — comprobado por grep, el DAO no tiene forma de borrar sesiones por coche.
- Remoto: `userProfileDataSource.deleteVehicle(uid, id)` borra el documento del vehículo. El
  historial remoto (`ParkingHistoryDto`) sigue en Firestore con su `vehicleId`.

Todas las lecturas del historial filtran por coche —
`getEndedSessionsByVehiclePaged` (`UserParkingDao.kt:43`) y `observeByVehicle`
(`UserParkingDao.kt:60`), ambas `WHERE vehicleId = :vehicleId`. El pager de Vehículos tiene una
página por vehículo **registrado**. Borrado el vehículo, no queda ninguna página que pida esas
sesiones: siguen existiendo, siguen ocupando sitio, siguen sincronizando, y el usuario no puede
llegar a ellas por ninguna ruta de la pestaña Vehículos.

Que las sesiones sobreviven no es teoría: el detalle del mapa ya contempla el caso y lo dice en su
propio contrato — `ParkingHistoryState.kt:58`, *"The registered vehicle that owns the focused
session, or null if it was deleted / unresolved"*. O sea que el sistema ya sabe que hay sesiones
huérfanas; solo el historial se comporta como si no existieran.

**Lo que ve el usuario:** borra un coche viejo y su historial real desaparece sin un solo aviso. No
se le pregunta, no se le advierte y no se le ofrece conservarlo. Si el borrado fue un error, no hay
deshacer.

## Doctrina violada

No hay un invariante escrito que cubra esto — es un hueco, no una infracción. El más cercano es el
de honestidad de las superficies (`UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001`): una
pantalla no afirma en silencio algo que no ha comprobado. Aquí el historial afirma, por omisión, que
esas sesiones no existen.

Lo que sí rompe es la regla de **sistemas, no parches**: el ciclo de vida de un vehículo y el de sus
sesiones nunca se ató en ningún sitio. Cualquier arreglo que solo esconda el síntoma (p. ej. filtrar
huérfanas en una consulta) deja el mismo hueco abierto para el siguiente consumidor.

## Señales / datos disponibles

Todo está en local y es consultable ahora mismo:

- `parking_sessions.vehicleId` sobrevive intacto al borrado (no hay FK con cascade — el esquema
  Room v1 no la declara).
- Se puede contar cuántas sesiones quedarían huérfanas **antes** de borrar: basta un `COUNT` por
  `vehicleId`, consulta que hoy no existe y habría que añadir.
- La pantalla de Vehículos ya bloquea borrar el último vehículo, así que siempre queda al menos un
  coche vivo donde reubicar o desde donde avisar.

## Diseño

**Decidido por el user el 31-08: el borrado es borrado (cascade).** Borrar el coche borra sus
sesiones, local y remoto, dentro de la misma operación.

**El invariante: no existe sesión de aparcamiento sin vehículo al que pertenecer**, y vive en
`deleteVehicle` — el único sitio donde un vehículo deja de existir. No se implementa filtrando
huérfanas en las consultas de lectura: eso sería esconder el síntoma en cada consumidor en vez de
cerrar la puerta por donde entran.

Piezas:

1. `UserParkingDao` gana un borrado por `vehicleId` (hoy no existe ninguno).
2. `deleteVehicle` lo llama en la misma transacción local que `dao.deleteById(id)`.
3. El borrado remoto sigue al local — ⛔ **obligatorio, no opcional**: si las sesiones se van solo
   de Room, el reconcile las devuelve en la siguiente sincronización.
4. Aviso previo con la cifra real: *"se borrarán también sus N aparcamientos"*. Necesita un `COUNT`
   por `vehicleId` que tampoco existe hoy. Es irreversible y el usuario tiene que saberlo **antes**
   de confirmar, no después.
5. **Un coche aparcado AHORA no se borra en absoluto** (decisión del user, 31-08). No se cierra su
   aparcamiento en silencio ni se publica la plaza: cerrar un aparcamiento puede liberar una plaza
   para la comunidad, y eso jamás puede ser un efecto secundario de borrar un coche. El botón se
   deshabilita **con el motivo escrito** —una acción gris sin explicación se lee como un bug— y el
   repositorio lo rechaza además por su cuenta, para que la regla valga para todo llamador y no sólo
   para esa pantalla. El rechazo es un veredicto propio (`DeleteBlockedByActiveParking`), no un
   `DeleteFailed`: no se ha roto nada, y el usuario recibe qué hacer, no "algo ha fallado".

⛔ **Lo que NO se hace**: dejar las sesiones vivas pero inalcanzables (lo de hoy), que destruye el
acceso sin destruir el dato — el peor de los tres mundos.

Descartada la alternativa "el coche se retira en vez de borrarse": es el sistema más completo pero
mete un eje de estado nuevo en `Vehicle` que habría que sincronizar y barrer por detección,
`resolveStrategy`, pager, `defaultVehicleId` y galería mock. No lo paga antes de la beta, y cascade
no lo impide más adelante — retirar es un superconjunto de borrar.

## Criterio de éxito — cumplido

`VehicleDeleteCascadeTest` (androidUnitTest, Robolectric + Room **real**, no fakes) mide las tres:

- Borrado un vehículo, **cero** filas suyas en `parking_sessions` — incluidas las retractadas, que
  el historial nunca enseñó y que un arreglo "barre lo que se ve" habría dejado. El historial del
  otro coche queda intacto.
- Un coche con aparcamiento ACTIVO se rechaza con `DeleteBlockedByActiveParking` y **no cambia
  nada**: ni sus sesiones ni su propia fila.
- El footprint cuenta sólo lo que el usuario vería desaparecer (12 sesiones ≠ 12 filas).

⛔ **Validado por falsación**, no por verde: quitando la línea `userParkingDao.deleteByVehicle(id)`
el primer test FALLA. Un test que no se ha visto fallar no prueba nada — la lección de
`UI-COLOR-THE-RAMP-HAS-ONE-RESOLVER-001`.

Por qué contra Room real y no contra el fake: el defecto es **invisible desde arriba**. Leyendo por
la UI o por las propias lecturas del repositorio, las filas huérfanas no aparecen — ese es
exactamente el bug. Sólo preguntándole a la TABLA se ve.

Resto, hecho: strings en los 9 locales; dos variantes nuevas en la galería mock (la zona de borrado
no salía en NINGUNA variante, porque sólo aparece con `existingVehicleCount > 1`).

## Consumidores auditados

- `UserParkingDao` — añadidos `deleteByVehicle` y `countEndedByVehicle`; no existía ninguno de los
  dos. El `COUNT` refleja exactamente los filtros de las lecturas del historial (`isActive = 0`,
  `retractedAtMs IS NULL`) para que la cifra del aviso sea la que el usuario ve.
- `RemoteUserProfileDataSource(+Impl)` — añadido `deleteParkingSessionsForVehicle`. Filtrado en
  cliente, no con `where`: es el historial de un solo usuario (ya se descarga entero en
  `getParkingHistory`) y un filtro servidor pediría índice para un borrado ocasional.
- `UserParkingReconcile` — ⛔ **la razón por la que el borrado remoto NO es opcional**:
  `reconcilePending` se queda con cualquier fila remota que no exista en local, así que un
  documento superviviente volvería a Room como historial de un coche que ya no existe. Por eso el
  remoto borra **sesiones primero** y luego el vehículo.
  ⚠️ **Riesgo residual asumido**: los borrados remotos van en `syncScope` (background) como ya hacía
  el borrado del vehículo, para no colgar el botón esperando el ack del servidor. Se apoya en la
  cola offline del SDK de Firestore. Si el proceso muere antes de drenarla, las sesiones remotas
  sobreviven y un sync posterior las resucita. Es la misma exposición que el borrado de vehículo ya
  tenía; hacerlo `await` colgaría el borrado sin red. No se cambia aquí.
- `VehicleRepositoryImpl` — el guard vive AQUÍ, no en la pantalla: la pantalla deshabilita el botón,
  pero el invariante tiene que ser cierto para todo llamador.
- `ParkingHistoryState.focusedVehicle` (`ParkingHistoryState.kt:58`) — su doc dice *"or null if it
  was deleted / unresolved"*. Con cascade el null por borrado deja de poder ocurrir; se deja como
  está porque "unresolved" sigue siendo alcanzable (sesión sin `vehicleId`, o lista aún sin cargar).
- `VehicleActiveStatePolicy.promotionTarget` — sin cambios; la promoción del activo sigue igual.
- ⛔ **Sesión activa**: resuelto bloqueando, no limpiando. Al no borrarse nunca un coche aparcado, no
  hay geocerca, notificación ni worker que reconciliar — el caso deja de existir en vez de
  gestionarse.

## Relacionados

- `UI-HISTORY-THE-CHART-SPANS-WHAT-THE-FILTER-SPANS-001` y
  `UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001` — hermanos de la misma revisión del historial
  (2026-08-31), pero independientes: ninguno bloquea a este.
