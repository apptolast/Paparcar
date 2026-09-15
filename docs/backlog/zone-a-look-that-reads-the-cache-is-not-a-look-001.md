# ZONE-A-LOOK-THAT-READS-THE-CACHE-IS-NOT-A-LOOK-001 · La mirada de fondo se miraba a sí misma

**Estado:** ✅ Done — en master (15-09-2026).

Bug de [`zone-an-alert-nobody-is-awake-to-receive-001.md`](zone-an-alert-nobody-is-awake-to-receive-001.md),
encontrado el mismo día de mergearlo al ir a estimar el coste en Firestore.

## Problema

`RunZoneSpotWatchUseCase` pregunta así:

```kotlin
spotRepository.observeNearbySpots(centre, zone.radiusMeters.toDouble()).first()
```

Y `SpotRepositoryImpl.observeNearbySpots` es un `channelFlow` con **dos ramas y un solo emisor**:

| rama | qué hace | ¿emite? |
|---|---|---|
| Room | lee la caché local | **sí** — el único `send(` de la función, línea 62 |
| Firestore | abre un listener y escribe en Room (`smartReplaceForBoundingBox`) | **no** |

O sea que lo remoto llega al consumidor **de rebote**: Firestore escribe en Room y Room vuelve a
emitir. Correcto para una pantalla viva. Letal para un `.first()`: la primera emisión es una lectura
local de milisegundos contra una ida y vuelta de red, así que **Room gana siempre**, y el worker se
lleva la caché y cancela el flujo — matando el listener justo cuando iba a contestar.

**Consecuencia: el worker mira la foto que quedó la última vez que la app estuvo abierta.** Con la
app cerrada —que es cuando la alerta sirve para algo— ahí no hay nada nuevo por definición. La
entrega en background que se mergeó en `56d63e1f` **no entrega**.

> **«Pero lo de Room llega de Firestore, ¿no?»** — la objeción es correcta a medias, y la mitad que
> falla es la que importa. Room se llena desde el listener, sí, pero **solo mientras alguien colecta**.
> Medido: el único escritor de `spotDao` es la línea 106 de `SpotRepositoryImpl`, dentro de esa rama,
> y los únicos colectores son `HomeSpotsController` (mapa abierto) y los dos casos de uso de zona.
> Proceso muerto, nadie colecta, caché congelada. Y la suscripción del propio worker no llega a ser
> ese alguien: `.first()` la cancela antes de que el snapshot aterrice. Lo peor de las dos: abre un
> listener —que puede costar— y usa el dato viejo igual.

### Por qué los tests no lo vieron

`FakeSpotRepository.observeNearbySpots` devuelve un `MutableStateFlow` directo: **el fake no tiene
partición caché/red**, así que no puede reproducir la carrera que ES el bug. Doce tests en verde
sobre una forma que en producción no existe. Misma familia que el fake que priceaba HYBRID y tapó
[FUEL-A-HYBRID-FILLS-UP-WITH-PETROL-001] en la galería.

## Segundo problema: el radio de la alerta no es el radio de la zona

Petición del user: *«independientemente del radio que haya puesto el usuario —puede ser pequeño,
solo para marcar dónde vive—, la alerta debería alcanzar más»*.

Es correcto, y el dato lo confirma: **una zona va de 50 a 500 m (250 por defecto), mientras que la
propia app llama «cerca» a 2 km** (`ObserveNearbySpotsUseCase.DEFAULT_SEARCH_RADIUS_METERS`). El
número de la zona está contestando otra pregunta:

- El radio de la zona responde **«dónde está mi sitio»** — un pin con tolerancia.
- La alerta necesita **«hasta dónde andaría desde una plaza hasta mi sitio»**.

Alguien que dibuja 50 m para marcar su portal no está diciendo «solo avísame de plazas en mi
portal»: está marcando un punto. Con el radio tal cual, esa alerta no salta nunca.

## Doctrina violada

- **«El evento nomina, el movimiento MEDIDO confirma»** tiene aquí su gemelo: *una mirada que lee lo
  que ya sabías no es una mirada.* El worker producía un veredicto sobre el mundo consultando su
  propia memoria.
- **Los fakes se parecen a lo real o no prueban nada.** Un fake sin la partición que causa el fallo
  es una suite que mide otra cosa.
- **«Un aviso que no llega no avisa de que no ha llegado»**: en campo esto se ve como «no había
  plazas», indistinguible de «no miré».

## Diseño

### 1 · Una lectura puntual, que además es contable

`SpotRepository.getNearbySpotsOnce(...)` con un **`get()`** de Firestore en vez de `.snapshots`,
sobre las MISMAS `geohashQueryBounds` que ya usa el listener. Escribe en Room de paso —la caché se
beneficia de la mirada— y devuelve el resultado.

⛔ No es `.first()` con más paciencia: esperar a la segunda emisión sería carrera, no contrato, y no
hay forma honesta de testear «espera a que la red gane».

### 2 · El radio de la alerta es suyo

`zoneAlertRadiusMeters(zone) = max(zone.radiusMeters, ZONE_ALERT_MIN_RADIUS_METERS)`, con el suelo
en **500 m** — el radio máximo que un usuario puede dibujar, así que la alerta nunca mira menos que
la zona más grande que existe, y sigue siendo distancia andando (~6 min).

⛔ Con `max` y no sumando: quien dibuja 500 m a propósito recibe 500 m. El suelo arregla el caso que
el user describe —el pin pequeño— sin pisar una elección explícita.

⚠️ Y hay que aplicarlo en **los dos sitios**: consultar más lejos y seguir filtrando por
`zone.radiusMeters` en `isAlertableInZone` no cambiaría absolutamente nada. El radio de alerta pasa
a ser el contrato del círculo, y los dos carriles (stream y worker) usan el mismo.

### 3 · Logs para poder medir el coste

Firestore **cobra por documento leído**, no por consulta, y ese es justo el número que no tenemos:
la consulta es por geohash y *sobre-trae* respecto al bounding box (por eso el círculo se aplica
después, en Kotlin). La factura es `ejecuciones × zonas × usuarios × documentos por consulta`, y de
los cuatro factores solo el último es desconocido — y es el que manda.

Así que cada mirada deja escrito: radio usado, **documentos que devolvió Firestore**, cuántos caen
dentro del círculo, cuántos son novedad y cuánto tardó. Con eso, un día de worker en un móvil real
da la cifra sin tener que estimar nada.

## Criterio de éxito — medido

| | |
|---|---|
| Una plaza que **solo tiene el servidor** (caché rancia) se ve y se avisa | ✅ |
| El worker **pregunta al servidor**: `onceCallCount > 0` | ✅ |
| Zona de 50 m → consulta 500 m y avisa de una plaza a ~333 m | ✅ |
| Zona dibujada a 500 m → sigue siendo 500 m (suelo, no multiplicador) | ✅ |
| Más allá del círculo de alerta sigue sin avisarse (~1,1 km) | ✅ |
| `documentsRead` reporta lo que Firestore **cobró**, no lo que guardamos | ✅ |
| Suite completa · ambos flavors · iOS · `--rerun-tasks` | ✅ 18 tests en el worker (eran 12) |

### 🔑 El testigo, comprobado de verdad

*Una prohibición sin testigo no es un chequeo.* Se revirtió el código al `.first()` original y se
corrió la suite: **6 tests caen**, incluido `should_seeASpotThatOnlyTheServerHas_when_theLocalCacheIsStale`.
Restaurado el arreglo, verde. Sin eso, los tests nuevos serían decoración.

### Lo que cambia para el usuario

- **Una zona pequeña ya alerta.** Antes, un pin de 50 m no saltaba nunca. Un test que afirmaba lo
  contrario (`should_ignoreSpotsOutsideTheZoneRadius`, zona 100 m ignora plaza a 400 m) se reescribió:
  ahora esa plaza **sí** se avisa, y el que clipa usa 900 m.
- **Y la alerta empieza a existir**: antes miraba su propia memoria.

### Lo que NO está medido

El coste. Ahora es **medible**, que no es lo mismo que medido: cada ejecución escribe
`▶ watch run: zones=N docs=N inZone=N new=N tookMs=N` y cada lectura
`one-shot read r=500m → N docs, N in bbox, N alive`. Un día de worker en un móvil real da la cifra.
⚠️ Y el suelo de 500 m **sube** el número de documentos frente a una zona de 50 m — la alerta útil
cuesta más que la que no saltaba, y por eso el log va en el mismo commit que el radio.

## Consumidores auditados

| Quién | Estado |
|---|---|
| `RunZoneSpotWatchUseCase` | ✅ lectura puntual + radio de alerta |
| `ObserveZoneSpotAlertsUseCase` | ✅ mismo radio que el worker — dos carriles no pueden vigilar círculos distintos |
| `isAlertableInZone` | ✅ clipa por `zoneAlertRadiusMeters`, no por `zone.radiusMeters`. **Es la mitad que se olvida**: ensanchar solo la consulta no cambia nada y es la mitad cara |
| `grep` de `observeNearbySpots(...).first()` en todo el repo | ✅ cero ocurrencias reales (solo comentarios que lo prohíben) |
| `grep` de `zone.radiusMeters` en zona | ✅ una sola lectura, dentro de `zoneAlertRadiusMeters` |
| `SpotRepository` × 4 impls (real, fake test, fake mock, `RecordingSpots`) | ✅ las cuatro |
| `FirebaseDataSource` × 3 impls | ✅ las tres |
| `HomeSpotsController` (mapa) | ✅ intacto: sigue con el listener, que es lo correcto con la app abierta |

## Seguimiento abierto

**El suelo de 500 m es un número elegido, no medido.** Si el campo dice que sobran o faltan, el
siguiente paso natural no es tocarlo a ojo: es darle al usuario un radio de alerta propio
(`Zone.alertRadiusMeters`), que es la versión honesta de «hasta dónde andarías» — con su migración y
su UI. Hoy sería una perilla sin dato detrás.
