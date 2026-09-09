# FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001 · el escalón de la media nacional deja de escribirse a mano

**Estado:** ✅ Done · rama `feature/FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001-openvan-average-prices` ·
worktree `../Paparcar-fuel-openvan`

Toca **F5a** (`FUEL-PRICE-RESOLVER-001`), escalón **2 de 3** del precio. No toca la lista ni la capa
de gasolineras: F5b está entera en master (`22b50ec6` + `b2722ea0` + `829330d7`) y su escalón 1
—surtidor real por gasolinera, España/Ministerio— no cambia.

## Problema

`domain/fuel/StaticAverageFuelPrices.kt` es una tabla escrita a mano: 8 países + una fila `EU`
compartida por 14 miembros del euro, con `VALID_AS_OF = "2026-09"` y la promesa de refrescarla en
cada release. Alimenta el GASTO ESTIMADO de los viajes (stats F3/F4), no las filas de gasolineras.

**La promesa no se está cumpliendo, y se puede medir.** Contra el Ministerio de hoy
(09/09/2026 19:41, 11.496 estaciones reales, la MISMA fuente que la app ya consume en F5b):

| España | Ministerio (real, n) | tabla estática | error de la tabla |
|---|---|---|---|
| Gasolina 95 E5 | **1,8420** (n=10.934) | 1,55 | **−15,9 %** |
| Gasóleo A | **1,8146** (n=11.282) | 1,45 | **−20,1 %** |
| GLP | **1,0915** (n=998) | 0,99 | **−9,3 %** |

En el mercado principal de la app, el gasto de cada viaje se está mostrando **una quinta parte por
debajo de lo real**. Un número con fecha de vigencia que nadie refresca no es más honesto que uno
sin fecha: es el mismo número inventado, con un sello.

## Doctrina violada

Ninguna regla de código — la tabla está bien construida (`Money` con moneda, `pumpProduct()`
exhaustivo, null como respuesta real). Lo que falla es el **mantenimiento como invariante**: el
único mecanismo que mantenía cierta la tabla era una entrada en un checklist de release, y un
invariante que depende de que alguien se acuerde no es un invariante.

⛔ **El invariante de F5a que este ticket NO puede romper:** ningún escalón exige red en el momento
de calcular. OpenVan es una fuente de red, así que el diseño tiene que responder qué pasa sin red —
y la respuesta está abajo.

## Medición previa — las cuatro puertas, antes de escribir una línea

Endpoint real, medido con `curl` el 09-09-2026. Los cuatro pasan.

### 1 · Licencia y términos ✅

`GET https://openvan.camp/.well-known/openapi.json` → `info.license`:

```json
"license": { "name": "CC BY 4.0", "url": "https://creativecommons.org/licenses/by/4.0/" }
```

- **CC BY 4.0**, no CC BY-**NC** → uso comercial permitido. Esto es exactamente lo que tumbó a
  GlobalPetrolPrices, y por qué esta puerta se mira primero.
- **Sin autenticación**, sin API key que gestionar.
- Rate limit **120 req/min por IP** (`X-RateLimit-Limit`/`-Remaining` en la respuesta). Nuestro uso
  es de un puñado de peticiones por dispositivo y semana: tres órdenes de magnitud por debajo.
- Atribución: la respuesta trae un bloque `_attribution` con
  `"license": "CC BY 4.0"` y `attribution_url`. El spec la declara `required: false,
  recommended: true`, pero **la licencia sí la exige legalmente** → entra en Ajustes › Licencias.
- Parámetro `?source=<dominio>` para tracking de integraciones → usamos `?source=paparcar.com`.
- `termsOfService`: https://openvan.camp/en/terms

### 2 · Cobertura real ✅ — se multiplica por 7, no encoge

`GET /api/fuel/prices?source=paparcar.com` → 154 KB, `meta.total_countries = 168`.

| | tabla estática hoy | OpenVan |
|---|---|---|
| Países con precio propio | **8** (+1 fila `EU` compartida por 14) | **168** |
| gasolina | 8 | **167** / 168 |
| diesel | 8 | **166** / 168 |
| lpg | 8 | 55 |
| cng | — (fundido en `LPG_CNG`) | 15 |
| **electricidad (kWh)** | 9 filas | **0 — no existe en la API** |

Los 14 miembros del euro que hoy comparten la fila `EU` genérica **pasan a tener precio propio**
(AT 1,8744 · BE 1,9921 · MT 1,3392 · FI 2,1571…). La dispersión real entre ellos es de casi 0,80 €/L:
la fila `EU` única los estaba aplanando a todos.

⚠️ **La única contracción: la electricidad.** OpenVan no publica precio de kWh en ninguna forma
(claves reales del payload: `cng, diesel, diesel_premium, diesel_regular, e85, gasoline,
gasoline_premium, gasoline_regular, gasoline_super, kerosene, lpg, premium`). Por tanto
`averageKwh()` **no se sustituye**: la tabla de kWh escrita a mano se queda tal cual. OpenVan
reemplaza el escalón del LITRO, no el de la energía eléctrica.

### 3 · Frescura ✅ — declarada por dato, no solo global

- `meta.updated_at: "2026-09-05 11:14:47"`, `meta.cache_ttl_hours: 6`.
- **Cada país trae su propio `fetched_at`** con offset horario, más `sources` (lista nominal) y
  `sources_count`. Un precio sin fecha no vale, y aquí cada fila lleva la suya.
- Reparto medido: **167 de 168 filas** con `fetched_at = 2026-09-05`. Una sola rancia: **FM
  (Micronesia), 2026-05-02** — 4 meses. Un país donde la app no se usa, pero el diseño no puede
  confiar en eso: **hay que aplicar un umbral de antigüedad por fila** y descartar la que lo supere.
- 6 países vienen marcados `is_excluded: true` (FJ, MM, MR, PG, SN, VU) — la API misma dice que no
  se fíe uno de ellos. Se respetan y se descartan.
- `price_changes` por combustible permitiría, más adelante, decir "sube/baja". Fuera de alcance.

### 4 · Forma del dato ⚠️ — pasa, pero con dos trampas medidas

```json
"ES": {
  "country_code":"ES","country_name":"Spain","region":"europe",
  "currency":"EUR","local_currency":"EUR","unit":"liter",
  "prices":{"gasoline":1.8445,"diesel":1.8544,"lpg":1.1172, "...":null},
  "fetched_at":"2026-09-05T11:14:47+03:00","sources":[...],"sources_count":2,"is_excluded":false
}
```

- **Números JSON nativos**, no strings → nada de parsear comas decimales (a diferencia del
  Ministerio, que sirve `"1,845"` como string).
- **4 decimales**. El formateo lo sigue haciendo `Money` como hoy; no se toca copy, así que las dos
  mordeduras conocidas de Compose Resources (`\'` y `%%`) no entran en juego en este ticket.
- ⚠️ **TRAMPA 1 — moneda LOCAL, 120 monedas distintas.** No viene todo en EUR: PL en PLN (7,0163),
  RO en RON (9,5896), TR en TRY (76,8351), MA en MAD (14,9137). Esto **encaja** con lo que ya
  hacemos (`Money(amount, currency)`, y la tabla actual ya mezcla EUR/PLN/RON) y con la regla ya
  escrita en `HistoryState.countryCode()`: un periodo se valora en el país donde acabó el conductor,
  y el agregado ya **rechaza** mezclar monedas. **No hace falta convertir divisas.** Existe
  `/api/currency/rates`, y no lo vamos a usar.
- ⚠️ **TRAMPA 2 — 14 países NO cotizan por litro.** `unit` vale `gallon` (10 países) o
  `imperial_gallon` (4): US, CO, EC, PA, PE, SV, GT, HN, DO, BZ, KY, GD, KN, VC. Meter US $4,1053/gal
  en un cálculo que se llama `averageLitre` inflaría el gasto **×3,785**. El campo `unit` viene
  declarado en cada fila, así que es tratable — pero **hay que tratarlo explícitamente**, y un
  `unit` desconocido futuro tiene que devolver null, nunca colarse como litros.

### Validación cruzada contra fuente autoritativa independiente

La prueba de que los números de OpenVan son buenos, no solo frescos. Ministerio 09-09-2026 vs
OpenVan (fetched 05-09), España:

| | Ministerio (11.496 estaciones) | OpenVan | error de OpenVan |
|---|---|---|---|
| gasolina | 1,8420 | 1,8445 | **+0,1 %** |
| diesel | 1,8146 | 1,8544 | **+2,2 %** |
| GLP | 1,0915 | 1,1172 | **+2,4 %** |

Tres de tres dentro del 2,5 % contra 11.496 surtidores reales, con 4 días de desfase. La tabla
estática falla entre el 9 % y el 20 % **hoy**.

**Veredicto: las cuatro puertas pasan. El ticket se ejecuta.**

## Señales / datos disponibles

- `AddressInfo.countryCode`, ya estampado por el geocoder inverso en cada parking, resuelto al vuelo
  y nunca persistido. No cambia.
- `Vehicle.fuelPricePerLiter` / `energyPricePerKwh` — el override del usuario, escalón 3.
- El precio de surtidor (escalón 1) sigue entrando como **parámetro** `stationPrice`, tal y como
  documenta `ResolveFuelPriceUseCase`.

## Diseño — construido

**La tabla estática no muere: deja de escribirse a mano y pasa a ser el fondo de armario.**

El invariante «ningún escalón exige red al calcular» se conserva porque el cálculo **nunca** habla
con la red: lee siempre una tabla que ya está en memoria. Lo único que cambia es de dónde salió.

Tres capas, todas locales en el momento de calcular:

1. **Tabla refrescada** — lo último bajado de OpenVan, si llegó.
2. **Snapshot embebido** — `domain/fuel/EmbeddedAverageFuelPrices.kt`, **161 países**, generado.
3. **null** — fuera de esos 161. Sigue significando «el tile de coste no aparece»: nunca un cero.

### Dos decisiones que cambiaron al chocar con el código

**(a) El snapshot es Kotlin generado, NO un asset JSON.** Era la idea inicial (8,1 KB en
`composeResources/files/`) y es inviable: `Res.readBytes` es **suspend**, y el gasto se calcula
desde **13 call sites** a través de un `HistoryTimeline.resolve` estático. Un asset habría obligado
a volver asíncrono todo el cálculo para no ganar nada. Un `.kt` generado cuesta un fichero y deja el
invariante absoluto: **ni red ni I/O en el momento de calcular**. Lo genera
`tools/fuelaverages/fuelaverages.py` (convención `tools/<name>/<name>.py`, como `trace2fixture`).

**(b) El caché refrescado vive en memoria, no en disco.** Es la MISMA pregunta que ya resolvió
`CachingFuelStationsSource`, y con la misma respuesta: persistir significa o bump de esquema de Room
con su `Migration`, o un blob serializado en DataStore que se trae su propio problema de versionado
— y las dos cosas solo compran los minutos entre un arranque en frío y el primer refresco, que es
exactamente el hueco que cubre el snapshot embebido. Documentado en la propia clase.

### Piezas

| Fichero | Qué hace |
|---|---|
| `tools/fuelaverages/fuelaverages.py` | Genera el snapshot desde el endpoint real |
| `domain/fuel/EmbeddedAverageFuelPrices.kt` | ⛔ GENERADO — 161 países, `AS_OF = 2026-09-05` |
| `domain/fuel/AverageFuelPriceTable.kt` | La tabla + `averageLitre()` + `pumpProduct()` + `.Embedded` |
| `domain/fuel/AverageFuelPricesSource.kt` | Puerto: de dónde sale una tabla más fresca |
| `domain/repository/AverageFuelPricesRepository.kt` | ⛔ `current()` síncrono · `refresh()` suspend — **el invariante vive en el TIPO** |
| `data/fuel/OpenVanFuelPricesParser.kt` | Las 4 trampas del formato, con sus tests |
| `data/fuel/OpenVanAverageFuelPricesSource.kt` | Ktor, una petición para el mundo entero |
| `data/fuel/AverageFuelPricesRepositoryImpl.kt` | Suelo embebido + TTL de 24 h |

**Lo que NO cambió — y es la mitad del ticket:** el orden `UserOverride > Station > NationalAverage`
de `ResolveFuelPriceUseCase` está intacto, y sus tests de orden no se tocaron. La sustitución ocurre
**debajo** de `NationalAverage`: misma etiqueta en pantalla, mismo `FuelPriceOrigin`. Nadie aguas
arriba se entera.

### Detalles que costaron una decisión

- **El resolver recibe un PROVEEDOR `() -> AverageFuelPriceTable`, no una tabla.** Si capturase la
  tabla al construirse, la instancia creada antes de que aterrizara el refresco seguiría citando el
  snapshot para siempre — y en una pantalla sí y en otra no. Se lee en el momento de la llamada.
- **`HistoryTimeline.resolve` recibe el resolver como parámetro**, con el resolver offline por
  defecto. Antes lo construía dentro, que es justo lo que impedía darle otro. Tests, previews y
  galería siguen llamando sin nada extra y obtienen cifra real, no un hueco.
- **El sello de frescura se mueve solo en ÉXITO.** La versión obvia sella cada intento, y entonces
  un fallo —la radio apagada un segundo— compra un día entero sin reintentar. Tiene test propio.
- **Un refresco vacío es un FALLO, no una tabla.** Instalar cero países encima de un snapshot bueno
  dejaría todos los tiles de coste en blanco a la vez: el síntoma más ruidoso desde el bug más mudo.
- **El refresco se lanza desde DI, no desde el constructor del repositorio**, que se queda puro y
  construible en un test sin salir a la red. Un `single` de Koin es perezoso, así que arranca justo
  cuando alguien pide precios por primera vez.

## Criterio de éxito — verificado

- ✅ **Los tres órdenes del resolver, intactos y verdes**, sin tocar un solo assert.
- ✅ **Fallback sin red**: fuente que falla → se sigue cotizando el snapshot embebido. Test propio.
- ✅ **Galones**: US 4,1053 $/gal → **1,0845 $/L** medido en test; unidad desconocida → null.
- ✅ **Frescura y exclusión**: la fila de 4 meses y las `is_excluded` se caen, con test cada una.
- ✅ **Paridad de cobertura**: test que exige que los 8 países + los 14 del euro que respondían
  antes **sigan respondiendo**. Y que Malta y Finlandia ya NO coticen la misma cifra.
- ✅ **Suite completa verde** con `--rerun-tasks` (incluidos los guardarraíles de locales, que no
  corren si solo cambia XML). `assembleMockDebug` y `prodDebug` compilan.
- ✅ **Verificación end-to-end contra el payload REAL de 154 KB**: el parser de Kotlin devuelve
  **161 países**, descarta exactamente las mismas 7 filas y convierte los galones igual que el
  generador de Python. Dos implementaciones independientes de las mismas reglas, de acuerdo.
- ✅ **`/run` en el Oppo (prodRelease, R8)** — `sha256` device↔local idéntico, sin `FATAL`:
  - La sección «Fuentes de datos» se ve con las tres fuentes y los acentos correctos. **R8 no se
    llevó nada por delante.**
  - Log temporal (revertido) con la tabla ya en el dispositivo:
    `embedded asOf=2026-09-05 countries=161 ES=(EUR, 1.8445, 1.8544, 1.1172) US=(USD, 1.0845, …)`
    — el snapshot generado **sobrevive a R8** y los galones de US llegan ya convertidos a litro.
  - Y la red, preguntada a la cara para que un fallo no se disfrazara de éxito (un refresco fallido
    devuelve la tabla retenida, así que `asOf` igual no probaba nada):
    `network ok=true err=null asOf=2026-09-05 countries=161 ES=(EUR, 1.8445, …)` — round-trip ~250 ms.
- ⛔ **NO se pudo ver el tile de gasto, y no es culpa de este ticket.** Se forzó Plus en el Oppo
  para descartar el gate y sigue sin salir. Medido: los 20 aparcamientos tienen `address` y
  **ninguno tiene `countryCode`**, así que no hay país que consultar y `costEstimate` es null desde
  antes de esta tarea. Ficha propia: `docs/backlog/fuel-a-parking-without-a-country-has-no-price-001.md`.
  Mientras eso siga abierto, **todo F5a es invisible** — este ticket incluido.

## Dos tests que habían dejado de vigilar

Al ampliar la cobertura, dos asserts que pasaban dejaron de significar lo que decían. No es
mantenimiento cosmético: un testigo que pasa por el motivo equivocado es peor que no tenerlo.

- `should_answerNothing_where_theTableHasNoIdea` usaba **Japón** como país desconocido. La tabla
  generada lo cotiza. Ahora usa `"ZZ"`, reservado por ISO 3166 y por tanto ausente **por
  construcción**, no por suerte.
- `should_readTheEuroAreaRow_forACountryTheTableDoesNotPriceOneByOne` afirmaba que Irlanda leía la
  fila `EU` compartida. Ya no existe esa fila para litros. Pasa a
  `should_priceEachEuroAreaCountryWithItsOwnFigure`, y exige que **Malta y Finlandia no coincidan** —
  el aplanamiento que había, medido, era de 0,80 €/L.

## Consumidores auditados

| Sitio | Estado |
|---|---|
| `domain/fuel/StaticAverageFuelPrices.kt` | ✅ se queda SOLO con el kWh (OpenVan no lo tiene), con el porqué escrito |
| `domain/fuel/EmbeddedAverageFuelPrices.kt` | ✅ nuevo, generado |
| `domain/usecase/fuel/ResolveFuelPriceUseCase.kt` | ✅ lee el proveedor; **el orden NO cambió** |
| `presentation/vehicles/HistoryState.kt` | ✅ el resolver pasa de construirse dentro a ser parámetro |
| `presentation/vehicles/VehiclesViewModel.kt` | ✅ inyectado (2 call sites) |
| `presentation/stats/StatsViewModel.kt` | ✅ inyectado (1 call site) + `PresentationModule` |
| `di/DataModule.kt` · `di/DomainModule.kt` | ✅ repositorio `single` + refresco lanzado desde DI |
| `domain/usecase/stats/ComputePeriodStatsUseCase` | ✅ exento — recibe `Money` ya resuelto |
| `data/fuel/MineturStationsSource.kt` (escalón 1) | ✅ exento — otro escalón, no se tocó |
| Ajustes › Licencias | ✅ sección «Fuentes de datos» con las TRES fuentes |
| Dev Catalog / galería | ✅ sin cambios: las 3 variantes llaman a `LicensesContent`, que ya trae la sección |
| Strings | ✅ 4 keys nuevas × 9 locales |

### Atribución — decisión del user

La sección lista **OpenVan.camp (CC BY 4.0)**, **OpenStreetMap (ODbL 1.0)** y el **Ministerio para
la Transición Ecológica**. Las dos últimas son un hueco legal que ya existía antes de este ticket —
no había ni un string de atribución para ellas — y el user decidió cerrarlo aquí en vez de dejarlo
apuntado.

⛔ **La sección sobrevive al branch de error de la pantalla.** La lista OSS se lee de un fichero que
genera el build y puede fallar; las tres fuentes están compiladas y dos de sus licencias EXIGEN el
crédito. Que un fichero ausente se llevase por delante la atribución convertiría un fallo cosmético
en un incumplimiento de licencia.

Los nombres, licencias y URLs son **datos, no copy**: son nombres propios idénticos en los nueve
idiomas, igual que los nombres de librería de la lista de abajo. Solo se traduce la línea de «para
qué lo usamos».

## Decisiones tomadas (no son preguntas abiertas)

1. **No convertimos divisas.** El agregado ya rechaza mezclar monedas y el periodo se valora en el
   país donde acabó el conductor. `/api/currency/rates` existe y no se usa.
2. **Los galones se convierten**, no se descartan: 14 países más por dos constantes.
3. **La electricidad se queda escrita a mano** porque la API no la tiene. No es un olvido, y el
   comentario de `StaticAverageFuelPrices` existe para que nadie «termine la migración» inventando
   precios de kWh desde una fuente que no los publica.
4. **El snapshot embebido se genera, no se teclea.** Refrescarlo sigue siendo un paso de release,
   pero ahora es un comando reproducible contra un endpoint, no criterio de nadie.


## Integracion al rebasar sobre master

Master trajo `FuelHttpClient` (`FUEL-LAYER-FOLLOWS-THE-CAMERA-001`) mientras esta rama estaba
abierta. El rebase entro **sin conflicto**, y por eso habia que mirarlo: git dejo la fuente de
OpenVan con un `HttpClient()` pelado mientras el resto del modulo ya usaba `fuelHttpClient()`.

⛔ No era cosmetico. El cliente pelado **no lleva timeout**, y `refresh()` sostiene su `Mutex`
mientras espera la red: una peticion colgada habria dejado el candado tomado para siempre y ningun
refresco posterior habria vuelto a entrar. Corregido a `fuelHttpClient()`, que ademas nos
identifica con User-Agent propio.
