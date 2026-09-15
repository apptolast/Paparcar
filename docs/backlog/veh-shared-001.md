# VEH-SHARED-001 · Coche compartido — etapa 1: el grupo y el espejo

**Estado:** ✅ **Done** (etapa 1 de tres) · rama `feature/VEH-SHARED-001-mirror-and-group`
rebasada sobre `56d63e1f`

> F8 de la ola 2 ([`plus-roadmap-001.md` §F8](plus-roadmap-001.md)), la feature estrella de Plus.
> El **modelo** se decidió el 08-09 (proyección/espejo). Este ticket es la **etapa 1** de tres,
> acordada con el user el 15-09.

## Alcance — qué entra y qué NO (decisiones del user, 15-09)

| | Entra en este ticket | Queda para su ticket |
|---|---|---|
| Modelo | `sharedCars/{carId}` + espejo de sesiones + Room v11 | — |
| Grupo | invitación por código, unirse, salir, expulsar, dejar de compartir | — |
| Lectura | dónde está el coche, quién lo aparcó y cuándo — para TODO el grupo, dueño incluido | — |
| Backend propio | ❌ nada: reglas Firestore puras | Cloud Functions (dedupe 60 s, FCM, webhook RC) — comparten runtime con F6b |
| Aviso a los miembros | ❌ | push FCM («Marta ha aparcado en Ponzano, 14») |
| Detección | ❌ **no se toca** | dedupe de dos móviles + geocerca/Coordinator degradados a sugerencia (`det-change`, gate de campo con dos móviles) |

Lo que este ticket promete y se puede probar entero sin campo: **dos cuentas ven el mismo coche, y
cuando una aparca, la otra ve dónde está.**

## Problema

Un coche lo conducen dos personas y la app solo sabe de una. El que no aparcó no tiene forma de
saber dónde quedó el coche: su móvil no detectó nada y su historial personal —correctamente— no
tiene nada que contar. Hoy la única salida es preguntar por WhatsApp, que es justo el trabajo que
Paparcar existe para quitar.

## Doctrina aplicada (no violada — esta es la parte delicada)

- **Las rutas personales NO se comparten.** Desde [PRIV-A-TRACE-IS-NOT-A-STAT-001] (`ddb448e4`) la
  geometría ya es local-first y solo sube con consentimiento Plus; el espejo va **sin ruta** por
  construcción, y eso no es una regla de este doc: es el default del sistema.
- **Free pregunta / Plus vigila y recuerda** ([plus-alerts-and-tiers-001]): compartir el coche es
  que la app te cuente lo que hizo otro ⇒ **crear el grupo es Plus del DUEÑO**; los miembros no
  necesitan Plus (decisión 06/07-09). Gating **soft**: el botón se ve siempre, la acción abre el
  paywall.
- **Un coche compartido no cuenta para el límite free del miembro** (F1): `canAddOwnVehicle` ya
  tiene el hueco escrito en su KDoc — «when shared cars land (VEH-SHARED-001) a linked car carries
  `ownerUid != uid` and must NOT count here».
- **PLAZA es de la comunidad, APARCAMIENTO es tuyo** [COPY-SPOT-IS-NOT-A-PARKING-001]: lo que se
  comparte es el **aparcamiento** del coche, nunca una «plaza». Verbo del grupo: *«unirme»*, nunca
  *«compartir»* del lado del que entra.
- **Un caso de uso por VEREDICTO** [DET-VERDICT-NOT-PREDICATE-001]: la política de *qué viaja al
  espejo* es una función pura en `domain/sharedcar/`, no un `if` dentro del datasource.

## Diseño

### 1 · El esquema (decidido 08-09, detalle fino aquí)

```
users/{uid}/vehicles/{id}         ← + sharedCarId? + ownerUid?  (la fila del miembro es una fila
                                    normal con la MISMA MAC ⇒ la detección BT no se entera)
users/{uid}/parkingHistory/{id}   ← intacto: fuente de verdad, CON ruta, outbox intacto
sharedCars/{carId}                ← nace al compartir: ownerUid, displayName, btMacHash,
                                    createdAtMs, sharingEnabled, state{…}
  ├─ members/{uid}                ← displayName, photoUrl?, joinedAtMs, role
  └─ sessions/{sessionId}         ← ESPEJO: núcleo compartido, SIN ruta, SIN armEvidence/detectionPath
sharedCarInvites/{code}           ← carId, ownerUid, createdAtMs, expiresAtMs, claimedByUid?
```

**Desviación documentada respecto al §F8 del roadmap:** el roadmap escribía los miembros como
`memberIds[]/accessUids[]` dentro del doc del coche. Van como **subcolección `members/{uid}`**
porque sin Cloud Functions la pertenencia tiene que ser verificable *por reglas*: un array exige
que el cliente que entra reescriba un campo del doc del dueño (carrera entre dos que se unen a la
vez, y una regla que no puede comprobar «solo te añades a ti mismo» sin leer el array previo),
mientras que `exists(/sharedCars/$(carId)/members/$(uid))` es una condición de regla directa y cada
uno escribe **su** documento. El `carId` es el id del vehículo del dueño (no se re-keya nada).

### 2 · La puerta única del espejo

`RemoteUserProfileDataSourceImpl` ya es *la* puerta por la que un aparcamiento llega a Firestore
(worker de Android, drainer del outbox de iOS, push de pendientes y mutaciones en sitio convergen
ahí) — es donde [PRIV-A-TRACE-IS-NOT-A-STAT-001] puso el strip de geometría. El espejo se engancha
**en esa misma puerta**, con un colaborador opcional `SharedCarMirror` (puerto de dominio):

- `saveParkingSession` → escribe el espejo + `state` si el vehículo de la sesión tiene `sharedCarId`.
- `clearParkingSessionActiveFlag` → el espejo deja de estar aparcado (la posición **se mantiene**:
  doctrina de fallo asimétrico — mejor «hace 3 h» que un coche que desaparece del mapa).
- `updateParkingSessionAddressAndPlace` → refresca la dirección del espejo cuando el geocoder responde.

Qué viaja lo decide **una función pura**, `sharedCarMirrorPayload()`, en `domain/sharedcar/`:
núcleo sí (autor, ubicación, timestamps, isActive, dirección, tamaño); ruta, `armEvidence`,
`detectionPath`, matrícula y `searchTimeMs` **no**. Es la lista blanca la que se escribe, no la
negra: un campo nuevo en `UserParking` no se cuela en el espejo por olvido.

### 3 · Unirse sin backend

Código de **6 caracteres** de un alfabeto sin ambigüedades (`ABCDEFGHJKMNPQRSTUVWXYZ23456789`,
31 símbolos ⇒ 31⁶ ≈ 8.9·10⁸ — sin `O`/`0` ni `I`/`1`/`L`, que es lo que rompe un código dictado), **un solo uso** y **caducidad de 24 h**, siempre con entrada manual
(los deep links fallan). El canje es cliente-a-cliente y lo arbitran las reglas:

1. El miembro lee `sharedCarInvites/{code}` (el código ES el secreto).
2. Escribe `sharedCars/{carId}/members/{uid}` citando el código; la regla valida con `get()` que el
   invite existe, no ha caducado, apunta a ese coche y no está canjeado.
3. Marca el invite como canjeado (`claimedByUid = uid`) — un solo uso, verificable por regla.
4. Su móvil crea la **fila vinculada** local (`ownerUid` del dueño, `sharedCarId`, misma MAC), y a
   partir de ahí su detección BT funciona como con cualquier coche suyo.

### 4 · Quién lee qué

`state` del espejo lo lee **todo el grupo, el dueño incluido** — si aparcó un miembro, la sesión
personal del dueño no sabe nada porque su móvil no aparcó. La fila de `vehicles` con `sharedCarId`
marca el coche como compartido; el espejo es el punto de encuentro. Cache local en Room
(`shared_cars`) para que la pantalla no salga vacía sin red.

### 5 · Entitlement

- `canShareVehicle(isPlus)` — crear el grupo es Plus del dueño (`PaywallReason.SHARED_CAR`).
- `canAddMember(currentMembers)` — tope duro de **4 miembros** además del dueño.
- `canAddOwnVehicle` deja de contar los vinculados: «propio» pasa a ser `ownerUid == null`.
- Downgrade del dueño: el coche **sigue visible** y el espejo sigue actualizándose; solo se bloquea
  **añadir miembros nuevos** (conservar lo creado, gatear lo nuevo). Las notificaciones que se
  pausan son las de la etapa 2, que aún no existen.

## Criterio de éxito

1. Dos cuentas, un coche: la B se une con el código, ve el coche en su garaje y **no** consume su
   límite free de 1 coche propio.
2. La A aparca → en la B aparece dónde está el coche, quién lo dejó y a qué hora, sin push.
3. El espejo **nunca** lleva ruta, `armEvidence`, `detectionPath` ni matrícula — testigo en test
   sobre la función pura y sobre el DTO.
4. Un código caducado o ya canjeado no deja entrar, y lo dice con causa + remedio.
5. Sin red, la pantalla del coche compartido enseña el último estado conocido con su hora.
6. Suite en verde con `--rerun-tasks`, `assembleProdDebug` + `assembleMockDebug` + klib iOS.

## Consumidores auditados

| Sitio que asume «todo vehículo es mío» | Estado |
|---|---|
| `canAddOwnVehicle` / `VehiclesViewModel.AddVehicle` | ✅ cerrado: nace `countOwnVehicles(vehicles)` y el VM contaba `vehicles.size` — un coche compartido habría gastado el único hueco free |
| `VehicleRepositoryImpl.syncFromRemote` (LWW por usuario) | ✅ cubierto: `sharedCarId`/`ownerUid` viajan en el DTO y en el mapper, así que una reinstalación reconstruye el vínculo en vez de dejar un coche suelto con la misma MAC |
| `deleteVehicle` + cascada de sesiones | ✅ cubierto **por diseño**: salir del grupo NO borra la fila (`Vehicle.unlinked()`), porque borrarla se llevaría los aparcamientos propios del miembro [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001]. Testigo: `should_keepTheCarAndItsHistory_when_aMemberLeaves` |
| Garaje (pager) y ficha de vehículo | ✅ fila «Coche compartido» en la cabecera de cada coche, con las tres lecturas (sin grupo / lo compartes / te has unido) |
| Estado vacío del garaje | ✅ «Unirme a un coche compartido»: quien recibe una invitación aterriza justo ahí y, sin esto, se le pediría registrar un coche que no es suyo |
| `deleteUserData` / borrado de cuenta | ⚠️ **fuera de alcance, y declarado**: la cuenta borra lo suyo (`users/{uid}`) pero no disuelve un grupo del que era dueña. Entra con las Cloud Functions (etapa 2), que es donde puede hacerse en servidor; hoy el dueño lo hace desde la pantalla del grupo |
| Reglas Firestore (`match /{document=**} → false`) | ✅ `sharedCars/**` y `sharedCarInvites/{code}` añadidos y **validados** (`firebase_validate_security_rules` → OK) |
| `FirestoreDeserializerParityTest` | ✅ los 5 deserializadores nuevos registrados; sin eso, un campo nuevo del espejo caería a su default en silencio |
| Dev Catalog | ✅ grupo «Coche compartido» (7 variantes) + `MockScenario.sharedCarJoined` + `FakeSharedCarRepository` en `mockModule` (el grafo mock NO carga `dataModule`: sin el binding, la pantalla reventaría en el catálogo) |

## Desviaciones respecto al §F8 del roadmap (todas deliberadas)

1. **Miembros en subcolección**, no `memberIds[]` — para que la pertenencia sea comprobable por
   reglas sin Cloud Functions (ver §1).
2. **`state` es un documento propio** (`sharedCars/{carId}/state/current`), no un campo anidado: lo
   escribe quien aparca, y así un miembro no necesita permiso sobre el doc del dueño para decir
   dónde está el coche.
3. **El código vivo se guarda en el doc del coche** (`inviteCode`): un invite se lee de uno en uno
   por quien tiene el código —las reglas prohíben listarlos—, así que sin ese campo el dueño no
   podría ni releer su código ni revocarlo al disolver el grupo.
4. **Salir NO borra la fila del coche**, la desvincula (ver tabla de arriba).
5. **El tope de 4 se impone al CREAR el código**, no al entrar: es el único momento en que alguien
   con permiso de lectura sobre `members` puede contarlos. Endurecerlo es de la etapa 2.

## Estado de la verificación (15-09, tras el rediseño)

- ✅ `:shared:testDebugUnitTest` — **3.064 tests, 0 fallos** (34 del coche compartido)
- ✅ `:app:compileProdDebugKotlin` + `:app:compileMockDebugKotlin` + `:app:assembleProdRelease` (R8)
- ✅ `:shared:compileKotlinIosSimulatorArm64` (iOS verificado en Windows)
- ✅ `firestore.rules` validado con el MCP de Firebase
- ✅ **EN MANO (Oppo, `316a509e…`)**: la migración **v12 → v14 corrió sobre datos reales** sin perder
  nada (13 aparcamientos y la sesión viva siguen ahí), la fila «Compartir este coche · Compártelo
  con hasta cuatro personas» se ve en la página del Ford Focus, y tocarla en una cuenta FREE abre el
  paywall con su fila nueva («No incluido» / «Hasta cuatro conductores más»). ⛔ El APK tuvo que ser
  `prodRelease`: el móvil llevaba un sideload firmado con la llave del repo y `prodDebug` rebota con
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- ✅ Dos guardarraíles cazaron cosas reales y se corrigieron: `ArchitectureTest` (el
  `SecureRandom` con `java.security` estaba en `domain/` → movido a `data/util/`) y
  `LocaleParityGuardrailTest` (tres keys declaradas y nunca leídas — dos borradas de los 9 locales
  y la tercera, el aviso de emparejar, **puesta a trabajar**: un miembro sin BT no detecta sus
  propios aparcamientos y ahora la pantalla se lo dice)
- ⏳ **Falta el ciclo de DOS cuentas**: el Redmi no estaba conectado, así que unirse con un código,
  aparcar en un móvil y mirarlo en el otro sigue sin probarse en campo. Es lo único que los tests no
  pueden sostener

## ✅ Rebase y renumeración (15-09)

La rama salió de `b72ba4e7` (Room v10) y escribió el coche compartido como **v11**; master ya iba
por **v12** (`1cff6f26` vigilancia de precio = v11 · `56d63e1f` zona en background = v12). Rebasada
sobre `56d63e1f` y renumerada: **`MIGRATION_12_13`**, `PAPARCAR_DB_VERSION = 13`, esquema `13.json`
(el `11.json` de la vigilancia de precio, intacto). Es la colisión que git NO ve: tres ramas
salieron del mismo v10 y dos ya habían elegido el mismo número — la del zone watch lo dejó escrito
en su propio KDoc, y esta lleva la misma nota.

## ✅ Revisión de diseño del user (15-09) — implementada

Dos correcciones que **derogan** supuestos con los que se escribió la primera versión de la etapa 1.
Esquema completo: artifact «El coche compartido es un coche, no una pantalla». Las cuatro decisiones
abiertas las cerró el user con un **sí a las cuatro**.

### (a) La traza y las cifras son del grupo

Se cae la regla del 06/07-09 («las rutas personales no se comparten»): un coche compartido se mide y
se evalúa *exactamente* como cualquier otro —mismo grabador, mismo carril BT, mismo Coordinator— y
sus estadísticas son del grupo. Lo que queda bajo control de cada persona es **si su recorrido se
VE**, no si se mide. El candado estaba en el sitio equivocado: lo que PRIV-A-TRACE protege es
*publicar una traza sin permiso*, no *medirla*.

| Qué | Viaja | Se VE |
|---|---|---|
| ubicación, horas, dirección, autor | siempre | siempre |
| `routeDistanceMeters`, `searchTimeMs`, `publishedSpot` | siempre — son CIFRAS, y sostienen las stats del coche | siempre |
| `routePolyline`, `routeSnapped`, `routeInferredSpans` | **siempre** | según el interruptor de su autor, resuelto AL LEER |
| `armEvidence`, `detectionPath`, `tripMaxSpeedMps`, matrícula | nunca | — (no existe el campo: sigue siendo un TIPO, no un `if`) |

⚠️ **El interruptor es de VISIBILIDAD, no de subida** (corrección del user, 15-09). La geometría se
guarda siempre por dos razones suyas: que las cifras del grupo se puedan recalcular, y sobre todo
que **volver a encenderlo devuelva los viajes viejos** en vez de dejar un agujero permanente. Por
eso la visibilidad **no se sella en cada sesión**: se lee viva de `members/{uid}.sharesRoutes`, que
es lo único que un sello congelado no podría deshacer. Se aplica en UN sitio —
`SharedCarSession.asHistoryEntry(showRoute)` —, que es por donde pasan la fila y el mapa.

⛔ **Consecuencia que hay que declarar**: con el interruptor apagado, el polyline **sigue en
`sharedCars/{carId}/sessions/{id}`** y las reglas dejan leer ese documento a cualquier miembro del
grupo. Apagarlo oculta la ruta en la app, no la retira del espejo. Es una decisión del user y entra
tal cual en el anexo de Data Safety / política: *el recorrido se comparte con el grupo; cada
conductor decide si se muestra*.

- Interruptor **«Mostrar mis rutas en este coche»**, por coche y por persona, **encendido por
  defecto** (decisión 1): apagado por defecto haría que la función pareciera rota el día que se
  estrena. Vive en `vehicles.shareRoutesInGroup` y se publica en `members/{uid}.sharesRoutes`, que es
  de donde lo lee el grupo.
- La fila escribe **«ruta oculta»** solo cuando ese viaje TENÍA ruta; un aparcamiento BT que nunca
  tuvo una calla, porque no hay decisión que comentar. Un hueco sin explicación se lee como un fallo
  nuestro; una frase falsa es peor.
- El espejo del coche gana su **ficha de consumo** (`fuelType`, consumos) para que las cifras salgan
  iguales a todo el grupo; el **precio NO viaja** (decisión 3): lo que paga cada uno por un litro es
  suyo, así que dos miembros pueden ver euros distintos sobre los mismos kilómetros.
- Provenance de detección, fuera (decisión 4): es vocabulario de diagnóstico, no información para
  quien conduce, y vive entera en la telemetría.

### (b) No hay pantalla de grupo

Todo dentro de la pestaña de Vehículos:

- **La card**: marca junto al nombre (icono de grupo, gris de sistema) — el color sigue diciendo
  *cómo se vigila* el coche, y meter «compartido» en esa señal haría pelear dos hechos por un canal.
- **Sección «Coche compartido»** en la página del coche: dónde está y quién lo dejó (solo si lo
  aparcó OTRO, o si aún no lo ha aparcado nadie), fila de miembros → **hoja**, código si eres dueño,
  interruptor de rutas, aviso de emparejar si este móvil no tiene la MAC, y los chips de alcance.
- **Historial y cifras del GRUPO** (decisión 2), con chip «solo yo». Sin segundo calculador: las
  sesiones del espejo se convierten a `UserParking` (`asHistoryEntry`) y pasan por el MISMO
  `HistoryTimeline.resolve` + `ComputePeriodStatsUseCase` [STATS-CORE-001]. Cada fila se firma con su
  autor; la tuya no, porque en una lista tuya y de otros el nombre que hace falta es el del otro.
- **Unirse = diálogo** desde el garaje vacío, no pantalla.
- Borrados: `SharedCarScreen`, `JoinSharedCarScreen`, sus dos ViewModels, `Routes.SHARED_CAR` y
  `Routes.JOIN_SHARED_CAR`. El estado del grupo vive en `VehiclesViewModel`, que es el que ya resuelve
  el historial de ese coche — dos ViewModels lo resolverían dos veces, que es justo lo que la regla
  de «una superficie, un alcance» existe para impedir.
- Suscripción **solo al coche a la vista**: el pager enseña uno y cinco listeners de Firestore para
  pintar una página no se justifican.

**Room v14**: `vehicles.shareRoutesInGroup` (default 1) + la ficha de consumo en la caché del espejo.

## Lo que queda de F8 (etapas 2 y 3, sin empezar)

- **Etapa 2 · el aviso**: Cloud Functions (primer backend propio, compartido con F6b), FCM
  «Marta ha aparcado en Ponzano, 14» con canal nuevo y strings ×9, dedupe de 60 s entre dos móviles
  del grupo, y el tope de miembros impuesto en servidor. Aquí entra también la degradación de
  geocerca/Coordinator a sugerencia en coche compartido (`det-change`, gate de campo con dos móviles).
- **Etapa 3 · el dinero y la ley**: webhook RevenueCat → `plusActive`/`sharingEnabled` como
  proyección de solo lectura para las reglas (exige su ADR, roadmap padre §3.7), pausa de avisos al
  bajar de Plus, fila de Play Data Safety («ubicación compartida con otros usuarios») y el párrafo
  de la política de privacidad. ⏳ del user, como el resto de la superficie legal.

## Bitácora

- **15-09** · Abierto. Alcance de la etapa 1 fijado con el user (3 decisiones: etapa 1, sin Cloud
  Functions, sin tocar detección). Recon del sistema: la puerta única de sesiones es
  `RemoteUserProfileDataSourceImpl`; Room iba por v10; `canAddOwnVehicle` ya tenía el hueco escrito.
- **15-09** · Etapa 1 escrita entera: dominio (modelos + 3 funciones puras + gate), datos (DTOs,
  datasource remoto con deserializadores manuales, repositorio cache-first, Room v11, espejo
  enganchado a la puerta única), UI (pantalla del grupo con sus tres lecturas + pantalla de unirse +
  fila en el garaje + entrada en el estado vacío), 37 strings ×9 locales, reglas Firestore, 30 tests
  y 7 variantes de galería. Sin commitear.
