# SEC-A-REPLACED-INVITE-STILL-OPENS-THE-DOOR-001 · un código "reemplazado" seguía abriendo el grupo

**Estado:** ✅ Done — regla desplegada en `pap-26` y releída para confirmar el deploy real, código y tests en verde. ⏳ Único resto: borrar los 4 invites huérfanos ya detectados (hygiene — ya inertes con la regla nueva, no bloquean nada).

## Problema
Confirmado en vivo contra `pap-26` (17-09): la colección `sharedCarInvites` tenía **4 documentos
vivos** apuntando al mismo `carId` (Ford Focus), ninguno reclamado (`claimedByUid: null`), todos
dentro de su ventana de 24 h — `R837WA`, `U3X9NC`, `F8SZCW`, `NFQBZ5` — y el documento
`sharedCars/{carId}` que se supone que guarda "cuál es el código vivo" ya no existía.

El dueño pidió "código nuevo" varias veces (`createInvite`); cada vez debía morir el anterior. No
murió ninguno.

## Doctrina violada
`SharedCarInviteCode` (comentario de cabecera) y `issueInvite()` (`SharedCarRepositoryImpl.kt`) lo
dicen explícitamente: **"One live code per car: the previous one dies here, so a code read out loud
yesterday cannot be used by whoever overheard it tomorrow."** [VEH-SHARED-001]

Con el dato de campo por delante, la promesa es falsa: un código "reemplazado" seguía siendo
perfectamente válido para entrar al grupo mientras no expirase (24 h) ni se reclamase.

## Causa raíz — dos fallos, uno de higiene y uno de seguridad

1. **Borrado frágil y silencioso.** `deleteInvitesFor(carId)` (`RemoteSharedCarDataSourceImpl.kt`)
   solo sabe qué código borrar leyendo `sharedCars/{carId}.inviteCode` — el único rastro que queda,
   a propósito, porque `allow list: if false` en `sharedCarInvites` impide enumerar códigos ajenos.
   Esa lectura y el borrado van envueltos en `runCatching` que traga cualquier fallo sin log. Un
   hipo de red, una carrera entre dos taps, o el hueco entre disolver el grupo (`stopSharing` borra
   `sharedCars/{carId}`) y volver a compartir el mismo coche, y el código huérfano queda
   **inalcanzable para siempre** — no hay forma de volver a encontrarlo.
2. **Ninguna comprobación de vigencia real.** Ni `rejectionFor()` (dominio) ni la regla
   `holdsValidInvite()` (`firestore.rules`, bajo `sharedCars/{carId}/members/{uid}`) comprueban que
   el código sea el que el coche tiene ACTIVO ahora mismo. Solo miran: existe, no ha expirado, no
   está reclamado. Así que aunque el borrado fallara, la promesa de "un solo código vivo" dependía
   ÚNICAMENTE de que ese borrado tuviera éxito — no hay defensa en profundidad.

El fallo (1) es el que produjo la basura observable; el fallo (2) es el que convierte esa basura en
un agujero de seguridad real, no solo en higiene de datos.

## Señales / datos disponibles
- `firestore_list_documents` sobre `sharedCarInvites` (pap-26, 17-09): 4 docs, mismo `carId` y
  `ownerUid`, ninguno `claimedByUid`.
- `firestore_get_document` sobre `sharedCars/{ese carId}`: **not found** — el grupo ya estaba
  disuelto, pero sus códigos viejos seguían vivos y utilizables.
- Reglas desplegadas releídas con `firebase_get_security_rules` y comparadas con el fichero local:
  coinciden — no es un problema de despliegue no aplicado, es la regla tal y como está escrita.

## Diseño
El sistema, no el parche, en dos capas independientes — una ataca la CAUSA (que se acumule
basura), la otra ataca el SÍNTOMA (que la basura, si existe, sea explotable):

1. **`replaceInvite()` atómico** (`RemoteSharedCarDataSource`): las tres escrituras sueltas de
   antes (`deleteInvitesFor` + `putInvite` + `setActiveInvite`) se colapsan en una sola transacción
   de Firestore (`firestore.runTransaction { … }`) que lee el código anterior del coche, borra su
   documento si procede, escribe el nuevo invite y actualiza el puntero — o pasan las tres cosas o
   no pasa ninguna. Cierra también la carrera entre dos taps o dos dispositivos del mismo dueño,
   que las tres llamadas sueltas no podían cerrar. Propuesto por el user a mitad de la
   investigación ("deberíamos hacer update y sustituir") en vez de solo blindar la validación.
2. **Invariante comprobado en el momento de USAR el código, no solo al crearlo** — porque un fallo
   futuro en (1) (o cualquier huérfano ya existente) no puede volver a abrir la puerta:
   - **Regla de Firestore** (`holdsValidInvite`, bajo `sharedCars/{carId}/members/{uid}`): nueva
     condición `code == carActiveInviteCode()`, que lee `sharedCars/{carId}.inviteCode`.
   - **Dominio** (`rejectionFor()`): mismo criterio vía el nuevo parámetro `activeCode`, con un
     motivo de rechazo propio (`SharedCarInviteRejection.SUPERSEDED` →
     `PaparcarError.SharedCar.InviteSuperseded`) en vez de un error genérico.
3. **`deleteInvitesFor` deja de tragar fallos en silencio** (sigue existiendo, solo para la
   disolución del grupo entero): ahora loguea con `PaparcarLogger.w` si el borrado o la limpieza
   del puntero fallan.
4. Los fakes de test (`FakeSharedCarRemote`) se corrigieron para ser tan fieles como el real: antes
   `deleteInvitesFor` borraba "todos los invites de ese carId" (algo que el real NO puede hacer sin
   `list`, prohibido por las reglas) y `deleteCar` limpiaba el puntero DESPUÉS de borrar el coche —
   dos formas en que el fake era "más majo" que la realidad y habría podido esconder esta misma
   familia de bugs en el futuro.

## Criterio de éxito
- ✅ Tests de dominio (`SharedCarInviteCodeTest`): un invite con `activeCode != invite.code` rechaza
  como `SUPERSEDED`, incluso sin expirar ni estar reclamado; `EXPIRED` sigue ganando si ambos
  aplican; sin `activeCode` conocido no se rechaza (defensa en profundidad, no requisito).
- ✅ Tests de repositorio (`SharedCarRepositoryImplTest`): pedir un código nuevo dos veces dejó
  UN solo invite vivo en el datasource y el viejo ya no resuelve (`getInvite` → null); y aunque el
  documento viejo sobreviviera (simulando el propio bug), unirse con él falla con
  `InviteSuperseded`.
- ✅ Tests de reglas (emulador, `firestore-rules-tests`, 31/31 en verde, en SERIE): un código
  reemplazado no puede crear la membership aunque su documento siga vivo; el nuevo sí puede.
- ✅ `./gradlew testAll` en verde (incluye el guard del grafo mock de `:app` y los 9 locales).
- ✅ `firestore.rules` desplegado a `pap-26` por CLI y releído — el ruleset en vivo coincide con el
  fichero local, `carActiveInviteCode()` incluida.
- ⏳ Sanear los 4 invites huérfanos ya detectados en `pap-26` (ya inertes tras el deploy de la
  regla, pero sin motivo para dejarlos ocupando la colección) — hygiene, fuera de este cierre.

## Consumidores auditados
- `SharedCarRepositoryImpl.findInvite()` / `joinByCode()` — ✅ pasa por `rejectionFor()` con el
  nuevo `activeCode`, único caller de producción de `rejectionFor`.
- `firestore.rules` → `sharedCars/{carId}/members/{uid}.holdsValidInvite()` — ✅ único sitio server-
  side que decide si una membership puede nacer de un código; único caller de `holdsValidInvite`.
- `issueInvite()` (`shareVehicle` + `createInvite`) — ✅ único emisor de invites de producción,
  ahora vía `replaceInvite()`. `putInvite`/`setActiveInvite` (las tres llamadas sueltas) se
  eliminaron de la interfaz — no quedó ningún caller de producción sin migrar.
- `deleteInvitesFor()` — ✅ único otro caller: `deleteCar()` (disolución del grupo). Sigue siendo
  best-effort a propósito (ya no es la única línea de defensa), pero deja de tragar el fallo.
- `FakeSharedCarRemote` (test) — ✅ auditado y corregido: `putInvite` (ahora raw, no-interfaz),
  `deleteInvitesFor` y el orden de `deleteCar` no eran fieles al comportamiento real; los 13 sitios
  de `SharedCarRepositoryImplTest` que sembraban un invite se migraron a `seedActiveInvite()`.
- No hay otro lector de `SharedCarInviteDto` / `SharedCarInvite` en el repo (grep confirmado).
