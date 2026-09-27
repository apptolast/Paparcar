# Cada escritura a Firestore, y el test de reglas que la cubre

> ⛔ Esta tabla existe porque tres cambios de reglas en septiembre se verificaron **leyendo** el
> código contra las reglas, y dos de ellos trajeron regresiones que solo aparecieron en la revisión
> siguiente — una de ellas dejaba la última posición de un coche compartido huérfana al disolver el
> grupo, y otra devolvía el nombre del expulsado a la tarjeta que acababa de quitarlo.
>
> La mantiene honesta `RulesWriterInventoryGuardrailTest`: escanea los tres ficheros que escriben en
> Firestore y **falla si alguna escritura no aparece aquí**. Añadir un `.set(`/`.update(`/`.delete(`
> sin tocar esta tabla pone la suite en rojo, que es lo contrario de lo que pasó este mes.

## `RemoteSharedCarDataSourceImpl.kt` — el espejo del grupo

| Línea | Escritura | Test |
|---|---|---|
| 66 · 67 | `createCar` (coche + ficha del dueño) | `sharedCars.test.ts` · *solo los miembros leen el coche* (la creación la ejerce la semilla) |
| 73 · 74 · 75 · 77 | `deleteCar` (sesiones → miembros → estado → coche) | `state.test.ts` · **A1 · el dueño puede borrar state/current al disolver** |
| 84 | `putState` (aparcar) | `state.test.ts` · *putState: quien aparca firma su propia cabecera* |
| 89 | `setStateParked` (cerrar sesión) | `state.test.ts` · *setStateParked: el autor cierra su propia sesión* |
| 107 | `setStateTakenBy` (se lo lleva otro) | `state.test.ts` · *setStateTakenBy: otro del grupo se lo lleva* |
| 121 | `putSession` | `sharedCars.test.ts` · *una sesión la borra su autor o el dueño* (la semilla la crea) |
| 126 · 148 | `closeSession` · `updateSessionAddress` | ⚠️ **sin test propio**: mismo `create, update` que `putSession`, cubierto por la regla que exige `authorUid == uid` |
| 161 | `putMember` (entrar) | `sharedCars.test.ts` · *se entra con una invitación viva y no con una caducada* |
| 167 · 174 | `setMemberTokens` · `setMemberSharesRoutes` | `sharedCars.test.ts` · *cada uno mantiene SU ficha y no la de otro* |
| 192 | `clearMemberFromState` | `state.test.ts` · *el autor borra su propio nombre* **y A2 · el dueño borra el del expulsado* |
| 204 | `deleteSessionsOf` | `sharedCars.test.ts` · *una sesión la borra su autor o el dueño* |
| 209 | `deleteMember` (salir / expulsar) | `sharedCars.test.ts` · *salir es cosa de uno; enseñar la puerta, del dueño* |
| 213 · 217 | `putInvite` · `setActiveInvite` | `sharedCars.test.ts` · *se lee de uno en uno y no se puede listar* |
| 225 | `claimInvite` | `sharedCars.test.ts` · *un código se reclama UNA vez* |
| 234 · 235 | `deleteInvitesFor` | ⚠️ **sin test propio**: mismo `delete` del dueño que `putInvite` |

## `FirebaseDataSourceImpl.kt` — las plazas de la comunidad

| Escritura | Test |
|---|---|
| `publishSpot` (`set`, que sobre un doc existente evalúa **update**) | `spots.test.ts` · *publicar* · *sin caducidad* · *a un mes vista* · *estado que el cliente no escribe* · *firmada con el uid de otro* · **el dueño NO puede estirar la caducidad** |
| `refreshSpot` / republicación | `spots.test.ts` · *el dueño republica dentro del techo* |
| `publishSpot` con `claim = RELEASING` (la plaza del embarque) | `spots.test.ts` · *la plaza de un embarque SÍ se publica* · *la salida real promociona el MISMO documento* · *el reintento del worker* — ⛔ la lista de estados de la regla es una copia de `SpotStatus` y la ata `SpotStatusRulesParityGuardrailTest` |
| `retractSpot` | `spots.test.ts` · *retractar es un update legítimo* |
| contadores comunitarios | `spots.test.ts` · *un tercero solo toca los contadores* |
| `deleteSpot` (barrido pasivo) | `spots.test.ts` · *cualquiera borra una plaza YA caducada, nadie una viva ajena* |

## `FirestoreZoneAlertSubscriptionRepository.kt` — el índice de alertas

| Escritura | Test |
|---|---|
| `replaceAll` (crear, actualizar y borrar las filas del propio uid) | `zoneAlertSubs.test.ts` · *cada uno escribe y borra la suya* |
| — | `zoneAlertSubs.test.ts` · **nadie puede reescribir encima la fila de otro** · *nadie puede leer la de otro* |

## Lo que estos tests NO cubren, y conviene saberlo

- **Los índices compuestos**: el emulador **no los exige**, así que una consulta que en producción
  falla por falta de índice pasa aquí en verde. Eso lo cubre
  `functions/scripts/verify-prod-queries.ts` contra la base real.
- **`diagnostics`, `diagnostics_reports` y `users/**`**: reglas simples de «solo el propio uid», sin
  cambios este mes. Entran cuando alguien las toque.
