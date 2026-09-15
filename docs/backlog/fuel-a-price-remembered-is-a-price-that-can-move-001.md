# FUEL-A-PRICE-REMEMBERED-IS-A-PRICE-THAT-CAN-MOVE-001 · Un precio que nadie recuerda no puede subir ni bajar

**Estado:** ✅ Done — en master (15-09-2026).

Primera entrada de vigilancia de [`plus-alerts-and-tiers-001.md`](plus-alerts-and-tiers-001.md)
después de la de zona. Lo que este ticket NO puede hacer sin servidor está aparcado en
[`infra-an-alert-that-travels-needs-a-backend-001.md`](infra-an-alert-that-travels-needs-a-backend-001.md).

## Problema

El user pide tres alertas de gasolinera: **una más barata cerca**, **el movimiento diario de
precio**, y **seguir una estación**. Ninguna de las tres es posible hoy, y las tres fallan por la
misma razón, que no es la que parece.

No es que falten precios: los tenemos de toda España, Francia y Alemania, y ya viven en disco con
la hora que el propio feed les puso (`FuelStationFuelEntity.reportedAtMs`, deliberadamente **sin
TTL** — la fila guarda una medición y el lector decide).

Es que **el precio de ayer no existe**. `FuelStationCacheDao` borra y reescribe el juego entero de
combustibles en cada escritura:

```kotlin
/** ⛔ Paired with [upsertFuels] on every write, never skipped: a station whose diesel row
 *  disappeared upstream would otherwise keep quoting yesterday's diesel forever. */
@Query("DELETE FROM fuel_station_fuels WHERE stationKey IN (:stationKeys)")
```

Eso es **correcto** para una caché — su contrato es *"una respuesta fresca es la verdad sobre esa
estación"* — y es exactamente lo que imposibilita un delta. Una caché es una foto del ahora; un
movimiento necesita dos fotos. Pedirle las dos a la misma tabla es pedirle que deje de ser caché.

Y falta un segundo hueco, más callado: **no hay ninguna estación que sea tuya**.
`GetNearbyFuelStationsUseCase` contesta *"dónde reposto aquí"*, y la capa entera sigue a la cámara
[FUEL-LAYER-FOLLOWS-THE-CAMERA-001]. No hay sujeto que persista, así que la frase que vende la
alerta — *"8 c/l menos que donde sueles repostar"* — no tiene un **donde sueles repostar** que
citar. Inferirlo de las paradas es adivinar; una estrella es que te lo digan.

## Doctrina violada

Ninguna, todavía — este ticket **crea** superficie, no arregla un invariante roto. Las reglas que lo
gobiernan y que es fácil romper al escribirlo:

- **«Free pregunta, Plus vigila y recuerda»** (`plus-alerts-and-tiers-001.md`). Las dos
  notificaciones son vigilancia → **Plus, gating soft**. Estrellar una estación y ver su precio es
  responder a lo que acabas de hacer → **free**. ⛔ Ver precios **no se gatea jamás**.
- **«La base de datos guarda una medición, nunca una suposición»** (`FuelStationFuelEntity`). El
  histórico guarda lo que el feed dijo y cuándo lo dijo. Sin interpolar el día que no contestó, sin
  rellenar huecos, sin TTL: retención sí, TTL no.
- **Un caso de uso por VEREDICTO** [DET-VERDICT-NOT-PREDICATE-001]. Aquí hay **dos** veredictos, y
  son los que se citan en la notificación: *«esta de aquí está más barata que tu referencia»* y
  *«el precio que sigues se ha movido»*. Umbral, distancia y desvío son **predicados** y viven
  dentro de su veredicto.
- **Room desde la primera release pública**: tabla nueva = **bump de versión + `Migration`**, las
  dos cosas. Saltarse el bump es la única forma que `fallbackToDestructiveMigration` no cubre.
  Vamos por **v10 → v11**.
- **Magic numbers** en `companion object` privado; **strings en los 9 locales** en esta misma tarea;
  **peek nuevo → galería mock** en esta misma tarea.

## Señales / datos disponibles

| Ya existe | Dónde | Sirve para |
|---|---|---|
| Precio por (estación, combustible) + hora del feed | `fuel_station_fuels` | la muestra de hoy |
| Estaciones con geografía de retención larga | `fuel_stations` | seguir una que no está a la vista |
| Feeds por país, ruteados por provincia/municipio | `FuelStationsRegistry`, `MineturStationsSource` | refrescar una seguida lejana |
| Combustible del coche | `Vehicle.fuelType` | qué precio comparar |
| Gate Plus vivo + paywall soft | `ObserveIsPlusUseCase`, `MONEY-001` | quién recibe el aviso |
| Canal UPLOAD + IDs de notificación | `AppNotificationManager` | cómo se entrega |
| WorkManager con 13 workers y su patrón | `detection/worker/` | cuándo se mira |

**Lo que NO existe y este ticket crea:** la segunda foto, la lista de seguidas, y los dos veredictos.

⚠️ **Una trampa medida, no supuesta:** un `reportedAtMs` puede venir viejo — en Italia el 69 % de
los precios llega con 2 a 7 días y cola hasta 2013 [CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001].
Comparar «lo de hoy» contra «lo de ayer» sin mirar esa hora inventa movimientos: si el feed no ha
actualizado, el precio no ha subido ni bajado, **no se sabe**. El silencio no es un cero, igual que
un combustible ausente no es 0,000 €.

## Diseño

El sistema es **una segunda tabla y un día**, y de ahí salen las tres alertas.

### 1 · `fuel_price_history` — la foto que la caché no puede guardar

Tabla nueva, **separada de la caché a propósito**, porque la caché tiene prohibido acumular:

```
PK (stationKey, fuelType, observedDay)
  stationKey   source|id, la misma clave compuesta y por la misma razón
  fuelType     nombre del FuelType
  priceAmount  lo que el feed dijo. Null nunca se guarda: sin precio no hay muestra
  currency
  reportedAtMs la hora que puso el FEED  — lo que decide si hubo movimiento
  observedAtMs la hora en que MIRAMOS    — lo que decide qué día es la fila
  observedDay  día local de observedAtMs — la clave, para que dos miradas el mismo día
               no creen dos filas: la última del día manda
```

Es una **serie diaria, no un log**: un usuario que abra la app ocho veces no genera ocho filas.
Retención 30 días, podada por el mismo worker que escribe (es una poda, no un TTL: la fila vieja se
tira porque ya no cabe, no porque haya dejado de ser verdad).

### 2 · `followed_fuel_stations` — el sujeto

Tabla de usuario (no caché): `stationKey`, `source`, `id`, `name`, `lat`, `lon`, `followedAtMs`. Es
la que hace **acotado** el conjunto del snapshot diario: no se fotografía una provincia entera, se
fotografían las seguidas y lo que rodea a tus anclas.

Es también lo que resuelve la asimetría de la capa: una seguida a 300 km **tiene que seguir
contestando** aunque nunca mires allí. En España eso es *una* petición por provincia seguida — el
feed ya está indexado así [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001].

### 3 · Dos veredictos, citables los dos

- **`EvaluateCheaperStationNearbyUseCase`** → *«a 1,2 km, 8 c/l menos que tu referencia»*.
  Referencia = la seguida más cercana con precio; sin ninguna seguida, la **mediana** de las de la
  zona (nunca la más cara, que haría barata a cualquiera). Predicados dentro: ahorro mínimo,
  distancia máxima, y que la muestra de la candidata no sea más vieja que la de la referencia.
- **`EvaluateDailyPriceMoveUseCase`** → *«tu gasolinera ha bajado 3 c/l»*. Compara las dos últimas
  filas de `fuel_price_history` de cada seguida **exigiendo que `reportedAtMs` haya cambiado**. Si
  el feed no se ha movido, el veredicto es «sin noticia», que no es «sin cambio».

### 4 · `FuelPriceWatchWorker` — una mirada al día

Periódico diario. Refresca las seguidas, escribe el snapshot del día, poda, corre los dos
evaluadores y entrega. Un worker y no un stream: el precio de una gasolinera no cambia mientras
miras el mapa, y un poll de 15 min aquí sería gastar batería por un dato que se mueve una vez al día.

### 5 · Entrega y gating

Dos IDs nuevos en el canal UPLOAD (2010 «más barata cerca», 2011 «movimiento de precio»), un master
switch nuevo en Ajustes (`notifyFuelPrices`, hermano del de zona) y el gate Plus **vivo**: perder
Plus **pausa** el worker sin borrar una sola seguida ni una fila de histórico, que es la regla de
degradación de `MONEY-001`.

| | Free | Plus |
|---|---|---|
| Ver precios, ranking, cómo llegar | ✅ completo | ✅ |
| Estrellar una estación y ver su precio | ✅ | ✅ |
| Histórico de la seguida | 7 días | 30 |
| Aviso de más barata cerca · movimiento diario | ❌ (paywall soft) | ✅ |

## Criterio de éxito — medido

| | |
|---|---|
| `EvaluateDailyPriceMoveUseCase`: sello de feed que no se mueve → **sin noticia** | ✅ 9 tests |
| `EvaluateCheaperStationNearbyUseCase`: umbral, desvío, muestra rancia, moneda, referencia mediana | ✅ 8 tests |
| Tienda v11 contra SQLite real por el constructor de producción: **8 lecturas en una tarde = 1 fila** | ✅ 4 tests (Robolectric) |
| Suite completa + `compileMockDebugKotlin` + `compileProdDebugKotlin` | ✅ |
| Guardarraíles Konsist + paridad de locales + paridad de copy de notificación, con `--rerun-tasks` | ✅ 20 clases, 0 fallos |
| iOS (`compileKotlinIosSimulatorArm64`) | ✅ |
| Galería mock: peek de gasolinera **sin seguir / seguida** | ✅ |

⚠️ **Lo que NO está medido, dicho aquí y no descubierto luego:**

1. **No hay test del salto v10 → v11 con un fichero v10 real.** La migración está escrita y
   registrada, y Room exportó `11.json`, pero sembrar una base v10 exige el DDL completo de las
   **once** tablas: tras migrar, Room valida el esquema entero contra las entidades, así que un
   sembrado parcial falla por las tablas que no toca este ticket. Lo que sí está probado es que la
   v11 abre, lee y escribe por el constructor de producción. Es el mismo hueco que ya tienen
   `MIGRATION_8_9` y `MIGRATION_9_10`; si se quiere cerrar, se cierra para las tres a la vez con un
   sembrador que genere el DDL desde el `.json` exportado — **ticket aparte, no un parche aquí.**
2. **Campo, pendiente**: estrellar una estación y, al día siguiente, recibir el aviso correcto — o
   no recibir ninguno si el feed no se movió, que es el caso que de verdad hay que ver.
3. **La primera alerta no puede llegar antes del segundo día.** No es un fallo: un movimiento
   necesita dos lecturas y ningún dispositivo ha guardado nunca la primera.

## Consumidores auditados

| Quién | Asumía | Estado |
|---|---|---|
| `RoomFuelStationsCache` | único escritor de `fuel_station_fuels`, y borra el juego entero en cada escritura | ✅ intacto — por eso el histórico es **otra tabla** |
| `RoomFuelStationsCache.stationKey` | la regla `source\|id` era privada suya | ✅ barrido: pasa a `domain/fuel/fuelStationKey`, la caché la llama |
| `GetNearbyFuelStationsUseCase` | `NearbyFuelStations` tiraba el `source` en la frontera | ✅ cerrado: cada fila viaja con `stationKey` + `source` |
| 4 sitios que construyen `NearbyFuelStation` (uso real, 2 tests, galería) | — | ✅ los 4 actualizados; **sin valor por defecto** en la identidad, que es lo que dejaría pasar un `stationKey` vacío |
| `AppPreferences` × 4 impls (Android, iOS, fake mock, fake test) | — | ✅ las cuatro |
| `AppNotificationManager` × 4 impls | los métodos nuevos son `default no-op` | ✅ solo Android los implementa, como el aviso de zona |
| `PaywallReason` (`when` exhaustivo en `PaywallScreen`) | 7 entradas | ✅ `FUEL_ALERTS` + su copy en 9 locales |
| `HomeViewModel` (constructor posicional en `PresentationModule`) | 36 `get()` por posición | ✅ los dos nuevos **al final**, como avisa el comentario de ese fichero |
| Doctrina «ver no se gatea» | — | ✅ nada de lo existente pasa a ser de pago: el gate nuevo vive **solo** en `RunFuelPriceWatchUseCase` |
