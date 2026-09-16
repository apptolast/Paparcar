# SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001 · el grupo se entera de que otro se lleva el coche

**Estado:** ✅ Done · rama `feature/SHARED-A-BT-CONNECT-PROVES-WHO-TOOK-THE-CAR-001` · worktree `../Paparcar-who-took-it`

## Problema

`SHARED-A-CAR-THAT-LEAVES-IS-NEWS-TOO-001` dejó la salida anunciada **solo cuando se lleva el coche
quien lo aparcó** — que es el único caso que el espejo registraba. Si Marta aparca y **Antonio** coge
el coche, el espejo no escribe nada hasta que Antonio para: el grupo ve el coche aparcado en un sitio
donde ya no está, y todos menos Marta mantienen la valla compartida plantada ahí. Una valla sin coche
debajo es un trigger esperando a que su dueño pase andando por esa calle.

## Doctrina — por qué se hace así y no de la otra manera

> **El evento NOMINA, solo el movimiento MEDIDO confirma.**

El reparto es exacto y son dos piezas que ninguna vale sola:

- **El connect de Bluetooth dice EN QUÉ COCHE estoy.** Es su MAC: no es un autobús, no es el coche
  de otro. Lo que NO dice es que el coche vaya a ningún sitio — sentarse a oír la radio es un
  connect sin viaje, el caso que `BtTripShutoff` ya conoce como *null trip*.
- **El grabador de viaje dice QUE ESE COCHE SE MUEVE.** `TripTracker` ya calculaba ese instante para
  su propio apagado; ahora lo REPORTA. Lo que no dice es qué coche es — el grabador solo ve puntos.

⛔ **Y por eso el Coordinator NO llama aquí.** Salir andando de la plaza y subirse a un autobús
produce conducción medida igual de buena, y sin el connect nada ata ese movimiento al coche del
grupo. Anunciarlo escribiría en el espejo que un coche aparcado se ha movido **y dejaría a todo el
grupo sin la valla del sitio donde sigue estando**. Fallo asimétrico: mejor no enterarse que contar
algo falso. El camino probabilístico sigue en la etapa 2, atado con la escalera de
`EvaluateArEnterArmUseCase` y con gate de campo.

⛔ **El grabador sigue sin influir en la detección.** `onDrivingProven` no arma, no nomina y no
confirma nada, y no toca `DetectionRuntimeState`: reporta una medida. **No hay `detectionPath`
nuevo** porque de aquí no nace ningún camino de confirmación.

## Diseño

| Pieza | Dónde | Qué hace |
|---|---|---|
| `TripTracker.onDrivingProven` | `domain/detection/` | callback que salta **una vez**, cuando el viaje deja de poder ser un null trip |
| `EvaluateSharedCarTakeoverUseCase` | `domain/usecase/sharedcar/` | veredicto: ¿procede contárselo al grupo? |
| `SharedCarTakeoverAnnouncer` | `data/sharedcar/` | I/O: lee la caché del espejo, pregunta y escribe |
| `state.movedByUid/movedByName` | DTO + deserializador manual | quién conduce, cuando no es quien aparcó |
| `departureVerdict` | `functions/src/sharedCarFanout.ts` | `movedBy` gana a `author` |

**La prueba del viaje son las MISMAS dos que absuelven del null trip**, no un umbral nuevo: velocidad
declarada ≥ 5 m/s, o desplazamiento medido ≥ 150 m del primer fix. La segunda importa más de lo que
parece — el campo `speed` **miente bajo**, y un viaje de arrastre que nunca declara velocidad es tan
viaje como el otro. Con una sola de las dos, el aviso se perdería justo en los móviles cuyo GPS calla.

**El veredicto tiene tres respuestas y la del medio es la que evita contar la salida dos veces**: si
el aparcamiento es MÍO y mi sesión sigue viva, me callo — esa salida ya la anuncia el cierre de mi
sesión, por una vía que además la ha medido.

**`movedBy` gana a `author` en el servidor, y lo que importa es a quién se EXCLUYE**: `authorUid`
responde a otra pregunta (quién dejó el coche ahí), así que la lectura ingenua nombraría a Marta y le
mandaría el aviso a Antonio, que es quien conduce. Cuando nadie escribió `movedBy`, el que conduce ES
el que aparcó — el espejo solo se pone en «se ha ido» sin ese campo desde el móvil cuya propia sesión
está mostrando el estado.

⛔ `setStateParked` **limpia** `movedBy` al volver a aparcar: un `movedBy` viejo colgando de un
aparcamiento nuevo haría que la siguiente salida nombrase a quien condujo hace dos viajes. Tiene test.

⛔ Los dos campos **no se cachean en Room**: el único que los lee es el reparto del servidor, y dos
columnas nuevas serían una migración por un dato que ninguna pantalla pide. Si algún día la ficha del
coche quiere decir «lo lleva Marta», ese será el ticket que pague la migración.

Sin strings nuevos ni cambios en `firestore.rules`: la tarjeta es la de
`notif_shared_car_left_*` (ya en los 9 locales) y `state/**` ya lo escribe cualquier miembro.

## Criterio de éxito

- ✅ 25 tests en `functions/` (2 nuevos: nombra al conductor y lo excluye del reparto; no arrastra un
  conductor viejo).
- ✅ `EvaluateSharedCarTakeoverUseCaseTest` (5 casos) + 2 tests nuevos de `TripTracker` — el que
  prueba que **un null trip no anuncia nada** es el que protege contra el falso positivo.
- ✅ `testAll` verde + guardarraíles con `--rerun-tasks`.
- ⏳ **Campo, dos cuentas y un coche emparejado con los dos móviles**: A aparca, B arranca → B ve
  desaparecer su valla compartida y A recibe «B se ha llevado el coche».

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `tripTracker.track(...)` | **1 llamante de producción** (`startBtTracking`, línea BT) + 7 en tests, todos cubiertos por el valor por defecto del parámetro |
| Trabajo del Coordinator | **exento con razón** — usa sus propias tomas, no el grabador; y no debe llamar aquí (ver Doctrina) |
| `setStateParked` | **cerrado** — único llamante `SharedCarMirrorWriter.onSessionClosed`, y ahora limpia `movedBy` |
| `toSharedCarStateDto` (deserializador manual) | **cerrado** — los dos campos nuevos, con su test de paridad |
| `EvaluateSharedCarWatchUseCase` | **cubierto por convergencia** — ya trata `!isParked` como «nada que vigilar»; con esto se entera al instante en vez de en 15 min |

## iOS — lo que este ticket NO le da, y por qué no es culpa del ticket

Un miembro en iOS **nunca** anuncia que se lleva el coche, y no por una configuración: en iOS la
línea Bluetooth es **imposible por construcción**. Apple no expone el Bluetooth clásico (HFP/A2DP),
que es el que usan los coches, a apps de terceros; `IosBluetoothScanner.getBondedDevices()` devuelve
lista vacía a propósito y lo documenta. Así que en iOS `resolveStrategy` siempre cae en el
Coordinator, y el Coordinator —deliberadamente— no llama al anunciador.

⚠️ Y hay un hueco MAYOR que este, anterior a este ticket: **en `iosMain` no hay FCM**. Un miembro en
iOS ni publica token (`SharedCarMemberSync` es de `androidMain`) ni recibe ningún push del grupo —
tampoco el de aparcar. Se entera por la pasada de reconciliación de `IosDetectionController`. O sea
que para iOS todo el canal de avisos del coche compartido está por montar, y el gate de Bluetooth de
este ticket es la menor de sus dos carencias.

Consecuencia práctica hoy, con iOS congelado (07-09): un grupo mixto funciona —el iPhone ve dónde
está el coche y sus viajes cuentan en las cifras compartidas— pero **con retraso en las dos
direcciones**. Montar FCM en iOS es su propio ticket.

## Lo que sigue fuera

- **El mismo aviso sin Bluetooth** (línea Coordinator): etapa 2 de `VEH-SHARED-001`, con la atadura
  al coche y su ciclo de campo.
- Tope de miembros en servidor y borrado de cuenta disolviendo grupos: etapa 2.
