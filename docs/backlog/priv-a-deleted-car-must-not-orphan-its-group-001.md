# PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001 · Borrar el coche dejaba el grupo vivo, y al miembro un coche gratis que nadie pagó

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Verificado:** `testAll --rerun-tasks` → **3415 tests, 0 fallos** · `assembleMockDebug` ·
`:shared:compileKotlinIosSimulatorArm64` OK.
**Origen:** pregunta del user (16-09): *«¿qué pasa si el owner elimina su cuenta o el coche?»*.

## Problema

Borrar la **cuenta** del dueño ya disolvía el grupo. Borrar el **coche**, no:
`VehicleRepositoryImpl.deleteVehicle()` no tocaba `sharedCars` en ningún punto, y `stopSharing` solo
se llamaba desde el botón de la ficha del coche — la ficha que acababa de desaparecer. Quedaba un
grupo huérfano: los miembros lo seguían viendo, con sus tokens dentro, el push seguía repartiendo, y
el dueño ya no tenía pantalla desde la que cerrarlo. ✅ Y **la UI no lo impedía**: el único freno del
borrado es `DeleteBlockedByActiveParking`; ni `isShared` ni `isLinked` se miraban.

## Lo que se midió antes de decidir

El user planteó que, al disolverse, el coche debía borrarse también a los demás — si no, *«un
usuario sin plus podría tener coches ilimitados sin pagar»*. Medido:

- `canAddOwnVehicle` **solo gatea el ALTA**: *«losing Plus never deletes or disables anything»*.
- `countOwnVehicles` = `count { !isLinked }` → **un coche prestado no cuenta**, y es el trato:
  *«the OWNER's Plus pays for the group»*. Compartir exige Plus; entrar es gratis.

Así que el agujero **no estaba donde parecía**: entrar en N grupos da N coches que no cuentan, pero
cada uno exige un amigo con Plus. Lo que sí convertía un coche no contado en uno propio sin pagar
era la **disolución**, porque `unlinked()` lo hace contar y el indulto del alta lo dejaba pasar.

## La decisión (user, 16-09): opción B

Tres salidas posibles. **Borrar la fila** cerraba el agujero pero se llevaba **el historial del
miembro** —sus km, su gasto, sus viajes— por una acción de un tercero, y contra el invariante que el
propio repo ya tenía escrito: *«sharing lends the car, not the memory of using it»*.

**B, la elegida**: la fila se queda, **cuenta**, y la app le hace elegir. Cierra exactamente lo
mismo, y la única diferencia es quién aprieta el botón sobre datos que son suyos.

## Diseño

| Pieza | Qué hace |
|---|---|
| `DeleteVehicleUseCase` | Coche **tuyo** compartido → `stopSharing` (se disuelve para todos). **Prestado** → `leave`. Normal → nada. Vive en un caso de uso porque `SharedCarRepositoryImpl` **ya depende** de `VehicleRepository` y la inversa sería un ciclo en Koin |
| `syncFromRemote` | Cuando el espejo ya no se lee, además de desvincular **anota** el coche: la disolución no la pidió quien la sufre |
| `AppPreferences.observeDissolvedSharedCars()` | Un CONJUNTO, no un slot: se puede estar en varios grupos. Entra **toda** fila que deja de ser prestada (ver abajo) |
| `VehiclesViewModel` | Resuelve la pregunta contra la lista de coches: un id anotado cuyo coche ya no existe es un resto, y se limpia en silencio |
| El diálogo | Conservar es el PRIMARIO y descartar hace lo mismo: la salida barata tiene que ser la que no borra nada. Borrar es secundario y destructivo, y **dice cuántos viajes se lleva** |

⛔ **El aparcamiento activo se comprueba ANTES de tocar el grupo.** Al revés, disolvería un grupo de
cuatro personas para después negarse a borrar el coche y dejarlo todo donde estaba.

⛔ **`takesTheFreeSlot` se calcula con `ownedCount - 1`**: la pregunta es si conservarlo le pone por
encima del tope, no si ya lo está. Con Plus no hay tope, y un free sin coche propio se lo queda sin
pagar nada — que es justo lo que el trato dice.

## Testigos

`DeleteVehicleUseCaseTest` (5): disuelve siendo dueño · sale siendo miembro · no toca nada si no
había grupo · **se niega SIN tocar el grupo** con el coche aparcado · olvida la pregunta pendiente.
`SharedCarRepositoryImplTest` (+2): la disolución ajena deja pregunta, y **salirse uno mismo también**.

## Corrección sobre la marcha: salir del grupo también pregunta

La primera versión solo preguntaba cuando la disolución era **ajena**, con el argumento de que
salirse uno mismo ya es una decisión tomada. El user lo cuestionó y tenía razón: **decides sobre el
GRUPO, no sobre el coche**. La consecuencia es idéntica —la fila se queda y pasa a ocupar plaza— y
no la cuenta nadie: verificado que «Salir de este coche» es una acción directa, **sin diálogo** que
explique qué pasa con la fila.

Así que el invariante es más simple de lo que era: *toda fila que deja de ser prestada plantea la
pregunta, la haya soltado quien la haya soltado*. Lo único que no entra es `stopSharing`, donde la
fila es del propio dueño y ya contaba antes.

## Dev Catalog

`MockScenario.sharedCarDissolved` + su interruptor en el catálogo: es el **único camino** por el que
ese diálogo aparece, y desde la cuenta del dueño no se puede provocar.

## El tope, decidido a media tarea (user, 16-09): **2 coches por dueño**

Entra en este ticket porque es la otra mitad de la misma pregunta —qué acota un coche compartido— y
vive en el mismo sitio, `SharedCarEntitlementGate.kt`, junto al tope de miembros que ya estaba.

- `SHARED_CAR_MAX_OWNED = 2` · `canShareAnotherCar(alreadyShared)`.
- Techo real: **2 coches × 4 invitados = 8 personas por suscripción**. Sin él, una sola suscripción
  habilitaba grupos ilimitados — diez coches serían cuarenta personas por un pago.
- ⛔ **Sin tope en el lado del invitado**, a propósito: compartir ya exige Plus y entrar es gratis,
  así que cobrarle al que entra sería cobrar dos veces el mismo sitio. Lo contradice el trato que el
  propio código documenta — *the OWNER's Plus pays for the group*.
- ⛔ **No es un paywall**: quien choca con el tope YA es Plus. Se responde con
  `PaparcarError.SharedCar.TooManySharedCars` y un mensaje que no ofrece nada que comprar.
- ⛔ Y se cuentan solo los **propios**: `isShared` es `sharedCarId != null`, que una fila PRESTADA
  también cumple. Contar eso gastaría el tope del dueño con coches ajenos.

`SharedCarEntitlementGateTest` fija los dos topes y, sobre todo, deja escrito que **no existe** una
función que le pregunte lo mismo al que entra.

## Lado iOS

Todo `commonMain` salvo la preferencia, que tiene su implementación en `IosAppPreferences`.
Verificado con `:shared:compileKotlinIosSimulatorArm64`.
