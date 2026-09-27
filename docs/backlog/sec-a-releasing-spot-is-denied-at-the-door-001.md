# SEC-A-RELEASING-SPOT-IS-DENIED-AT-THE-DOOR-001 · la plaza del embarque nunca llegó a publicarse: las reglas no conocen su estado

**Estado:** ✅ Done · desplegado en `pap-26` el 27-09-2026 (functions primero, reglas después)

## Problema

`SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001` (`627df0386`, 23-09) publica la plaza en el EMBARQUE como
`SpotStatus.RELEASING`. **Nunca ha salido ni una.** No es que no dispare: la escritura la rechaza
Firestore.

`firestore.rules:32`, y las **desplegadas** son idénticas (leídas con
`firebase_get_security_rules` el 26-09):

```
allow create: if ... && request.resource.data.status in ['CONFIRMED', 'PROVISIONAL'];
```

La nominación es **siempre la PRIMERA escritura de ese documento** — lo dice el propio
`NominateSpotReleaseUseCase`: *«NOT a promotion: this is the FIRST publication of this document»* —
así que entra por `create` → `PERMISSION_DENIED`. El `allow update` (línea 45) tampoco la lista.

El fallo es **mudo**: la publicación va por `ReportSpotWorker`, el denied reintenta con backoff y a
los 4 min el propio worker la descarta por TTL vencido en cola
(`"expired in queue, not publishing"`). Ni crash, ni snackbar, ni rastro en el pin.

### El dato de campo que lo destapó

Field-test 26-09, tres móviles (los tres `1.3.0`, los tres COORDINATOR):

| Hora UTC | Móvil / uid | arm | outcome | ¿nominaba? |
|---|---|---|---|---|
| 18:30 | Oppo CPH2371 `0l7X59pt…` | `AR_VEHICLE_ENTER … dep=enter_at_car` | `confirmed_steps+egress` | No — park aproximado |
| 18:00 | motorola edge 60 `4k2G5cfX…` | `AR_VEHICLE_ENTER … dep=enter_at_car` (lag 94 ms) | `confirmed_steps+egress` | Sí → denegado |
| 18:24 | motorola edge 60 `4k2G5cfX…` | `AR_VEHICLE_ENTER … dep=enter_at_car` | `aborted_no_movement` | Sí → denegado |

El embarque del Oppo fue sobre `56e266dc` (Super Carmela): `zoneRadiusMeters = 240,75` →
`EvaluateSpotReleaseNomination` corta con `approximate_park`, y la salida real se retuvo por
`AREA_TOO_WIDE` (>150 m) → `publishedSpot: false`. Ese viaje no publicó nada, ni morado ni verde:
correcto por doctrina, pero es la razón por la que el denied no se vio ni siquiera ahí.

## Doctrina violada

Ninguna de detección — la decisión estaba bien tomada. Lo que se rompe es **sistemas, no parches**:
un `enum` de dominio quedó enumerado a mano, en literales de cadena, en otro lenguaje, y sin nada
que atara las dos copias. `627df0386` añadió el cuarto `SpotStatus` y no tocó `firestore.rules`.

Y no fue descuido de enum: `firestore-rules-tests/test/spots.test.ts:34` afirma que un `create` con
`RETRACTED` falla — *«un estado que el cliente no escribe no se publica»*. La lista blanca era
deliberada. Lo que faltó fue volver a preguntársela cuando apareció un estado que el cliente **sí**
escribe.

⛔ Ya existe un guard que lee `firestore.rules` (`RulesWriterInventoryGuardrailTest`): comprueba que
cada FICHERO que escribe esté inventariado, nunca que los VALORES que manda pasen la regla. El
agujero estaba justo al lado del guard.

## Señales / datos disponibles

- Telemetría 26-09 en `(default)` (builds de release): tres `ARM:AR_VEHICLE_ENTER … dep=enter_at_car`.
- `spots` de producción vacía entera a las 19:03 UTC. Sin relación con este bug (los `CONFIRMED` sí
  pasan la regla); pendiente de mirar aparte, probablemente cancelaciones de prueba.
- El log del denied vive en `parkdiag.log` (`PARKDIAG/SpotRelease`), no en Firestore.

## Diseño

**Tres piezas, y la tercera es la que impide la repetición.**

### 1 · Las reglas conocen los cuatro estados, cada puerta el suyo

- `create`: `CONFIRMED`, `PROVISIONAL`, `RELEASING`. **`RETRACTED` no** — una retractación sólo
  aterriza sobre un documento que ya existe, y el test que lo afirma se queda como está.
- `update`: los cuatro. Un `set()` sobre un documento vivo evalúa UPDATE, así que el reintento del
  worker sobre una nominación ya escrita tiene que pasar; y la promoción `RELEASING → CONFIRMED` es
  el mecanismo entero del ticket original.

### 2 · El fan-out de zona: un embarque no se anuncia, y la promoción sí

Habilitar el `create` sin tocar la función es **peor que no arreglarlo**: `onSpotPublished` es
`onDocumentCreated` y `verdictFor` sólo descarta `RETRACTED`, así que cada embarque mandaría un push
diciendo «hay una plaza en tu zona» sobre un coche que sigue aparcado. El cliente
(`ZoneSpotAlertPolicy:72`) ya lo excluye; el servidor no, y son las dos mitades de la misma promesa.

Y hay un segundo efecto, menos visible: con el `create` gastado por la nominación, la salida real
pasa a ser un **UPDATE**, que `onDocumentCreated` no ve. Las alertas de zona —que son Plus— dejarían
de salir en silencio justo para las plazas mejor detectadas.

Así que el fan-out pasa a decidir sobre la **transición**, igual que `onSharedCarParked` ya hace:

```
verdictFor(before, after, nowMs) → announce sólo cuando `after` es anunciable y `before` NO lo era
```

- `RELEASING → CONFIRMED` (la salida real) **anuncia**: es la primera vez que hay plaza que ofrecer.
- `CONFIRMED → CONFIRMED` (un `enRouteCount`) no re-anuncia.
- `PROVISIONAL → CONFIRMED` no re-anuncia: ya avisó al nacer, marcada como no confirmada. Es el
  comportamiento de hoy, conservado.
- `RELEASING` y `RETRACTED` no se anuncian nunca.

### 3 · El guard que faltaba

`SpotStatusRulesParityGuardrailTest` (androidUnitTest, junto a los demás que leen ficheros del
repo): extrae las dos listas blancas de `firestore.rules` y las compara contra `SpotStatus.entries`.
Un quinto estado no compila el veredicto sin que su autor conteste por las dos puertas.

⛔ Deliberadamente NO comprueba que la regla sea correcta — eso lo ejecutan
`firestore-rules-tests/`. Comprueba lo único que se puede comprobar desde Kotlin: que las dos copias
del enum dicen lo mismo.

### Lado iOS

Nada que escribir en `iosMain`: las tres piezas son reglas, Cloud Function y un guard de JVM, o sea
las tres comunes a los dos clientes — iOS queda arreglado por el mismo despliegue. El hueco de iOS
sigue siendo el que ya dejó escrito `SPOT-A-BOARDING-IS-NOT-A-DEPARTURE-001`: **falta el disparador
del embarque**, así que allí no hay nada que denegar todavía.

### ⛔ El orden del despliegue no es indiferente

Primero **functions**, después **rules**. Al revés hay una ventana en la que los clientes ya en
campo (1.3.0 lleva la nominación desde el 23-09) publican `RELEASING` mientras la función vieja
sigue anunciando cualquier cosa que no sea `RETRACTED`: un push diciendo «hay una plaza en tu zona»
por cada coche en el que alguien acaba de sentarse. Al derecho no hay ventana: la función nueva
sabe callarse antes de que exista la primera plaza que callar.

`firebase.json` declara **las dos bases** (`(default)` y `debug`) contra el mismo `firestore.rules`.

### ⛔⛔ `firebase deploy --only firestore:rules` NO despliega nada desde el 13-09, y sale con éxito

Medido al desplegar este ticket. Con `firebase.json` en forma de **array de bases**
([FIRESTORE-DEBUG-BUILDS-USE-DEBUG-DATABASE-001], 13-09) el filtro `firestore:<algo>` se lee como
*«la instancia de Firestore llamada `<algo>`»*, y no existe ninguna llamada `rules`. La salida es:

```
=== Deploying to 'pap-26'...
i  deploying firestore
i  firestore: ensuring required API firestore.googleapis.com is enabled...
+  Deploy complete!          ← y NO ha liberado nada
```

Se distingue por lo que **falta**: un despliegue de reglas de verdad imprime
`+ firestore: released rules firestore.rules to cloud.firestore` (dos veces, una por base). El
comando correcto es **`firebase deploy --only firestore`** — ya estaba escrito en el doc de aquel
ticket, pero media docena de docs anteriores siguen citando el `:rules`, que entonces funcionaba.

⛔ **Y no basta con leer la salida: hay que releer la regla del servidor.** Aquí se creyó
desplegada y no lo estaba; lo cantó `firebase_get_security_rules`, que devolvió la versión vieja
palabra por palabra.

## Criterio de éxito

- [x] `npm --prefix firestore-rules-tests test` — **34 en verde**, y los tres nuevos **falsificados**:
      quitando `RELEASING` del `create`, los tres se ponen rojos y el resto sigue verde.
- [x] `npm --prefix functions test` — **31 en verde**, con los cinco casos de la transición.
- [x] `./gradlew testAll` en verde. `SpotStatusRulesParityGuardrailTest` **falsificado**: con la
      regla como estaba el 23-09 falla en `SpotStatusRulesParityGuardrailTest.kt:58`, o sea que
      habría cazado este bug el día que entró.
- [x] Desplegado y **releído del servidor**: la regla viva ya dice
      `['CONFIRMED', 'PROVISIONAL', 'RELEASING']`.
- [ ] ⏳ **Campo**: un embarque con el coche en un park EXACTO fuera de zona privada deja un pin
      morado visible en el otro móvil durante ≤ 4 min, y ese mismo pin se vuelve verde al salir.
      ⚠️ El park del 26-09 era de zona (240 m), así que el escenario no se ha dado todavía: hace
      falta aparcar donde la detección cierre con pin exacto.
- [ ] ⏳ Comprobar en los logs de la función que un embarque deja `spot not announced` con
      `reason: releasing`, y su salida un `spot fan-out`.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `firestore.rules` `spots/create` | 🔧 arreglado — `RELEASING` añadido |
| `firestore.rules` `spots/update` | 🔧 arreglado — los cuatro estados |
| `functions/src/fanout.ts` `verdictFor` | 🔧 arreglado — `RELEASING` se descarta; veredicto por transición |
| `functions/src/index.ts` `onSpotPublished` | 🔧 arreglado — `onDocumentWritten` |
| `ZoneSpotAlertPolicy` (cliente) | ✅ ya excluía `RELEASING`, con su razón escrita |
| `SpotTtlPolicy.ttlMs` | ✅ `when` exhaustivo sobre el estado; 4 min |
| `SpotTone` / `SpotStateColors` / `SpotIndicators` | ✅ cuarta variante con su dibujo, por construcción |
| `NearbyOrdering` | ✅ `RELEASING` último en el carril |
| `SpotDto` / `FirebaseDataSourceImpl.toSpotDto` | ✅ lee `status` con default `CONFIRMED` para docs viejos |
| `MAX_LIVE_SPOTS_PER_AUTHOR` (antiflood) | ✅ mismo documento en las dos fases: no cuenta doble |
| Reglas de `sharedCars` / `zoneAlertSubs` | ✅ no enumeran `SpotStatus` |
| `web/` | ✅ la web no nombra el estado de una plaza |
