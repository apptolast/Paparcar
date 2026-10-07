# DEMO-A-REVIEW-ACCOUNT-MUST-LOOK-LIVED-IN-001 · la cuenta de review se entrega vacia y con el coche aparcado hace 20 dias

**Estado:** 🟢 Sembrado y verificado en las DOS bases · ⏳ falta el entitlement de Plus (paso del user) · rama `chore/DEMO-A-REVIEW-ACCOUNT-MUST-LOOK-LIVED-IN-001-seed-review-account` · worktree `../Paparcar-demo-seed`

## Problema
Paparcar se presenta a Shipaton con una cuenta de prueba, `paparcar.review@gmail.com`
(uid `bIx3P5psSQUzVVcGSdL33CZVOry2`, provider `password`). Medido hoy en `(default)` de `pap-26`:

- **1 vehiculo** (Dacia Sandero `b3b853e5-…`) y **11 sesiones**, todas del 9 y 10 de septiembre.
- Una de esas 11 sigue con **`isActive: true` desde el 10-09**: quien abra la cuenta ve un coche
  aparcado hace veinte dias, que es justo el estado que la app existe para no tener.
- Sin zonas, sin rutas guardadas de valor, sin nada que ver en Historial ni en las cifras.

El juez que abra la cuenta no ve el producto: ve una cuenta abandonada.

## Doctrina violada
Ninguna del codigo — no hay bug de app aqui. Lo que se incumple es la promesa de la propia
demo: la app mide viajes, plazas liberadas y tiempo buscando aparcamiento, y la cuenta con la
que se ensena no tiene nada medido. Y una regla de producto si queda expuesta:
**`FREE_HISTORY_WINDOW_DAYS = 7`** + **`FREE_OWN_VEHICLE_LIMIT = 1`** significan que sembrar
sin Plus no ensena nada mas que una semana y un coche.

## Senales / datos disponibles
La cuenta real `rndeveloper11501@gmail.com` (uid `0l7X59ptXeQ4WUMlpFOZHLPIJAI3`) tiene en
`(default)`:

- **89 sesiones**, del 03-09 al 29-09 (**27 dias, no 3 meses**: el borrado de datos del 03-09
  se llevo todo lo anterior — ver `project_release_firestore_data_wipe_2026_09_03`).
- **1 solo vehiculo** (Ford Focus diesel).
- Calidad alta y real: `routePolyline` + `routeDistanceMeters` (viajes de varios km),
  `searchTimeMs`, `publishedSpot`, `detectionPath` (`steps+egress`), `armEvidence`
  (`enter_at_car`), `detectionReliability` 0,9 y `address` a nivel de calle
  (El Puerto de Santa Maria, Cadiz).

Decision del user (30-09): **3 meses clonando el corpus**, **Plus por entitlement promocional**,
**direcciones y coordenadas tal cual** (maximo realismo, asumiendo que se publica su historial).

## Diseno

### Lo que NO hay que sembrar: Room
`BootstrapUserDataUseCase` hace, tras cada login, `syncFromRemote(uid)` en paralelo de
**vehicles + parkingHistory + zones**, y `RemoteUserProfileDataSourceImpl.getParkingHistory()`
se trae la coleccion **entera**, sin ventana ni limite. Room es un espejo reconciliado
(`reconcilePending`), no una fuente de verdad. La ruta del viaje **viaja** en el propio doc de
sesion (`routePolyline`, `routeDistanceMeters`, `routeSnapped`, `routeInferredSpans`);
`trip_route_points` es solo el buffer del viaje en curso, transitorio por diseno.

> **Sembrar Firestore ES sembrar la app.** No hay tarea local. Lo unico que no viaja es
> `licensePlate`, on-device a proposito.

### Donde se siembra
`FirestoreDatabase.idFor(isDebugBuild)`: debug → base `debug`, release → `(default)`. Un juez
instala de Play, que es release → **se siembra `(default)`**.

### La fuente se versiona, el resultado no
El seeder vive en `tools/demoseed/seed_review_account.mjs` y es **idempotente**: ids
deterministas derivados de una semilla fija, de modo que volver a correrlo reconstruye la misma
cuenta en vez de acumular duplicados. Se autentica con las credenciales del `firebase` CLI ya
logueado (refresh token de `~/.config/configstore/firebase-tools.json` + el cliente OAuth
publico de firebase-tools) y escribe por `documents:commit` en lotes de <= 500.

### Que se escribe
1. **3 vehiculos** — el Sandero que ya esta (diaria, diesel), una **moto** y un **electrico**,
   para que el pager del garaje y las cifras por coche tengan contenido y se vea la
   categorizacion bidimensional. ⛔ **Ninguno lleva `bluetoothDeviceId`**: el resolver exige
   emparejado **y conectado ahora** (`DET-BT-CONNECTED-NOT-PAIRED-001`), asi que un MAC sembrado
   solo pintaria el coche de azul prometiendo una vigilancia que el movil del juez no tiene.
2. **~200 sesiones en 90 dias** — las 89 reales tal cual (ultimas 4 semanas) mas dos clones
   adelgazados y con jitter desplazados -30 d y -60 d. Todas `isActive: false` salvo **una**
   activa de hace unas horas, para que Home ensene el coche aparcado con su peek.
3. **Zonas** (`users/{uid}/zones`) con `notifyOnSpot`, que es lo que Plus vende.

### Lo que no se puede sembrar, y por que
- **Plazas de la comunidad** (`spots`): la consulta es el vecindario geohash 3x3 **alrededor de
  quien mira** y las plazas caducan. Un juez lejos de Cadiz no vera nada se escriba lo que se
  escriba. Como mucho se dejan plazas vivas donde esta el corpus.
- **Alertas de zona** (`zoneAlertSubs`): necesitan el token FCM del dispositivo, que solo existe
  cuando el device enciende el interruptor.
- **Coche compartido**: el espejo `sharedCars/{carId}` necesita miembros que sean uids reales.
  Inventar un miembro deja una fila sin perfil. Fuera de alcance, ticket aparte si se quiere.
- **Plus**: no sale de Firestore. `logIn(uid)` ata el app user id de RevenueCat al uid de
  Firebase → hay que conceder el **entitlement promocional** a `bIx3P5psSQUzVVcGSdL33CZVOry2`
  desde el dashboard. **Paso del user.**
  ⚠️ Efecto medido: con Plus, `SettingsScreen.kt:438` pone `onClick = null` en la fila de Plus →
  el paywall deja de ser alcanzable desde Ajustes.

## Criterio de exito
- Entrar con `paparcar.review@gmail.com` en un build de release y ver: 3 coches en el garaje,
  Historial con 90 dias agrupados por dia, cifras (km, tiempo buscando, plazas liberadas) sobre
  los tres meses, rutas dibujadas en el detalle de viaje, y Home con UN coche aparcado hace
  horas — no hace veinte dias.
- Volver a correr el seeder no duplica nada.

## Consumidores auditados
| Sitio que podria asumir lo contrario | Estado |
|---|---|
| `BootstrapUserDataUseCase` (3 syncs) | ✅ cubre vehicles/parkingHistory/zones; es la via por la que Room se llena |
| `reconcileParkingSessions` / `reconcileVehicles` | ✅ remoto gana en instalacion limpia (no hay filas `pendingSync` locales) |
| `HistoryEntitlementGate` (7 dias free) | ⚠️ exige Plus para que los 3 meses se vean — paso del user |
| `VehicleEntitlementGate` (1 propio free) | ⚠️ idem para los 3 coches |
| `ParkingStrategyResolver` (BT requiere ACL viva) | ✅ por eso no se siembra `bluetoothDeviceId` |
| `spots` (geohash 3x3 + caducidad) | ⛔ no sembrable para un juez remoto — escrito arriba |
| `zoneAlertSubs` (token FCM) | ⛔ no sembrable desde servidor — escrito arriba |
| base `debug` vs `(default)` | ✅ se siembra `(default)`, que es lo que lee un build de Play |

## Resultado — leido del servidor, no del log del seeder
Identico en `(default)` y en `debug`:

| | |
|---|---|
| Vehiculos | **3** — Dacia Sandero (diesel, activo), Renault Zoe (electrico), Honda PCX 125 (moto) |
| Sesiones | **191**, del 05-07 al 30-09 = **86 dias**, **57 dias distintos** con actividad |
| Activas | **1**, aparcada hace ~5 h (antes: una colgada desde el 10-09, ya borrada) |
| Zonas | **3** — Home (privada), Work y Downtown con `notifyOnSpot` |
| Contenido medido | **63** sesiones con ruta dibujable · **70** plazas liberadas · **1.069 km** |
| Integridad | toda sesion apunta a un coche que existe · `geofenceId == id` · ningun timestamp futuro |

## Dos cosas que se aprendieron sembrando

### ⛔ El corpus se lee de `(default)`, nunca de la base destino
La primera version leia el corpus de la MISMA base a la que escribia. Al sembrar `debug` salieron
**71 sesiones en vez de 191**, porque en `debug` la cuenta real solo tiene el espejo puntual de 33
sesiones del 13-09. Dos bases con dos cuentas de demo distintas y ningun error: el script decia
"Hecho". Arreglado con `SRC_ROOT` fijo a `(default)` — **un corpus, una cuenta derivada; la base
solo decide DONDE se escribe**.

### ⛔ Una plaza de la comunidad no se puede sembrar HONESTAMENTE
Se llego a evaluar sembrar plazas alrededor del corpus, y tiene mejor pinta de lo que parecia:
`HomeSpotsController` documenta que el centro de consulta **no es la posicion del usuario sino el
punto del mapa que se esta mirando** (radio 5 km, `HOME-WITHOUT-LOCATION-STILL-ANSWERS-001`), asi
que un juez que panee a Cadiz —o que aterrice ahi por el coche aparcado— las veria.

Se descarta por producto, no por dificultad. Una plaza es **efimera por diseno** y su color dice su
frescura: con TTL corto caduca antes de que el juez mire, y con TTL largo la rampa verde-amarillo-rojo
miente diciendo "recien liberada" durante tres meses. Ademas `fanout.ts` empujaria un aviso real a
quien tenga alertas por ahi. Lo que el mapa si tiene sin inventar nada son **parkings publicos y
gasolineras con precio**, que son datos reales y free completo.

> Si hace falta ver plazas en una demo EN VIVO, la via honesta es avisar de dos desde una segunda
> cuenta minutos antes: es exactamente lo que hace un usuario.

## Fuera de alcance (deliberado)
- **Coche compartido**: el espejo `sharedCars/{carId}` necesita miembros con uid real; inventarlos
  deja filas sin perfil. Se hace emparejando dos cuentas de verdad, que ya esta pendiente en
  `project_shared_car_sharing_does_not_erase_the_past_001`.
- **`zoneAlertSubs`**: necesitan el token FCM del device. Las zonas estan; el interruptor lo
  enciende el movil.
- **Matricula**: on-device a proposito, no viaja y no se puede sembrar desde servidor.

## Verificacion
- `node tools/demoseed/seed_review_account.mjs --dry-run` → plan revisable en JSON antes de escribir.
- Re-lectura del servidor tras aplicar (tabla de arriba), en las dos bases.
- No se toca ni Kotlin ni recursos ni `web/`, asi que la suite no puede verse afectada; aun asi se
  ejecutaron con `--rerun-tasks` los dos unicos guards que leen rutas de `tools/`
  (`WebSiteParityGuardrailTest`, `PlayListingPasteParityGuardrailTest`): **BUILD SUCCESSFUL**.

## ⏳ Paso del user (sin esto, la siembra se ve a un septimo)
Conceder el **entitlement promocional de Plus** en RevenueCat al app user id
**`bIx3P5psSQUzVVcGSdL33CZVOry2`**. Sin el, `HistoryEntitlementGate` recorta el historial a 7 dias y
las cifras se calculan solo sobre esa semana: de las 191 sesiones se verian ~15.
