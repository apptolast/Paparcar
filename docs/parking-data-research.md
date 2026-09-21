# Datos de parking — auditoría del sistema actual e investigación de fuentes

**Fecha:** 21-09-2026 · **Alcance:** por qué los parkings tardan en aparecer, y de dónde podría
salir información mucho más completa (precios, disponibilidad, tiempo real) en España.

> **Cómo leer este documento.** Cada afirmación va marcada:
> **[MEDIDO]** leído del código o de una medición registrada en este repo ·
> **[VERIFICADO]** comprobado hoy contra documentación oficial, con enlace ·
> **[NO VERIFICADO]** pista razonable que **no** se ha podido confirmar ·
> **[INFERENCIA]** deducción nuestra, explícitamente no un hecho.
>
> ⛔ La distinción que este documento no confunde nunca: *«esta empresa ENSEÑA precios»* no es
> *«esta empresa ofrece una API con esos precios»*, y *«hay cobertura»* no es *«hay autoservicio»*.

---

## 1 · Estado actual — el flujo de extremo a extremo

```
Cámara/GPS
  └─ HomeViewModel.scheduleNearbyRefresh()        debounce de cámara
     └─ refreshNearby(PARKINGS, …)                ¿misma zona? → no pregunta
        └─ fetchPublicParkings(lat, lon)          viewModelScope (Dispatchers.Main)
           └─ GetNearbyPublicParkingsUseCase
              ├─ ParkingZoneGrid.zoneOf()         centro pegado a rejilla de 3 km
              ├─ GeocoderDataSource.getAddress()  ⚠️ BLOQUEA, y nadie usa su respuesta
              └─ PublicParkingsSource
                 └─ CachingPublicParkingsSource   L1 · memoria · TTL 30 min
                    └─ DiskBackedPublicParkingsSource  L2 · Room · 30 días
                       │                               ⚠️ SOLO se lee si la red FALLA
                       └─ OsmPublicParkingsSource      red
                          └─ OverpassClient
                             ├─ OverpassQuota       1 pregunta a la vez · espera ≤10 s
                             ├─ OverpassWalkPatience caminata ≤30 s
                             └─ 2 espejos Overpass
                          └─ OsmPublicParkingParser.parse()   JSON en el hilo PRINCIPAL
              └─ filtro isIdentifiable + haversine + sort     también en el principal
  └─ HomeState.parkings → HomeParkingsLane / capa del mapa
```

**Ficheros:** `data/parkings/OsmPublicParkingsSource.kt` · `OsmPublicParkingParser.kt` ·
`CachingPublicParkingsSource.kt` · `DiskBackedPublicParkingsSource.kt` · `RoomPublicParkingsCache.kt` ·
`domain/parkings/ParkingZoneGrid.kt` · `PublicParkingsSource.kt` ·
`domain/usecase/parkings/GetNearbyPublicParkingsUseCase.kt` · `data/overpass/*` ·
`data/cache/{LocationKeyedCache,OsmCoverage}.kt` · `presentation/home/HomeViewModel.kt` ·
`data/datasource/local/room/{PublicParkingCacheEntity,PublicParkingCacheDao,OsmCoverageEntity}.kt`.

### De dónde salen los parkings hoy — **[MEDIDO]**

**OpenStreetMap, y solo OpenStreetMap**, vía Overpass. No hay registro por país (a propósito:
`OsmPublicParkingsSource.covers()` devuelve `true` siempre).

Dos espejos, en este orden (`OverpassEndpoints.ALL`):
1. `https://overpass-api.de/api/interpreter`
2. `https://overpass.openstreetmap.fr/api/interpreter`

Otros cinco están excluidos **por medición** (`EXCLUDED_BY_MEASUREMENT`), cada uno con su motivo.

### La consulta exacta — **[MEDIDO]**

Con el centro pegado a la rejilla y `ZONE_RADIUS_METERS = 3.000 + 3.000·(√2/2) = 5.121 m`:

```
[out:json][timeout:20];
(
  nwr(around:5121,<latZona>,<lonZona>)[amenity=parking][!parking];
  nwr(around:5121,<latZona>,<lonZona>)[amenity=parking]["parking"!~"^(street_side|lane|layby|on_kerb|half_on_kerb|verge)$"];
);
out center 1000;
```

El filtro de `access` (private/no/permit/…) **no** va en la consulta: se aplica en el móvil.

### Cuánto tarda cada parte — **[MEDIDO]**, mediciones registradas en este repo (14-09-2026)

| Tramo | Coste |
|---|---|
| Espera de turno (`OverpassQuota`) | 0 s si libre · hasta **10 s** si otro carril está preguntando |
| Overpass, espejo sano, consulta **caliente** | **1,4 s** |
| Overpass, espejo sano, consulta **fría** (centro nuevo) | 4,68 s (Madrid) · **7,72 s** (Málaga, por fibra) |
| Caminata completa si el primer espejo falla | hasta **30 s** (`WALK_CEILING_MS`) |
| Geocode de plataforma previo | variable, **sin timeout propio** |
| Parseo JSON de 238 KB / 681 elementos + haversine + orden | en el **hilo principal** |
| **Techo teórico antes de que la UI vea nada** | **10 s + 30 s = 40 s** |

Payload medido a 5.121 m: Madrid **681 elementos / 238 KB**, Málaga 571 / 269 KB, Cuenca 108 / 31 KB.

### Respuestas directas a las preguntas de la FASE 1

| Pregunta | Respuesta |
|---|---|
| ¿Espera a la red antes de enseñar? | **Sí, siempre**, salvo acierto de L1 en memoria. |
| ¿Room tiene caché? | Sí: `osm_public_parkings` + `osm_coverage` (Room v7+). |
| **¿Duración exacta de la caché?** | **L1 memoria: 30 min** — `CachingPublicParkingsSource.DEFAULT_TTL_MS = 30L*60*1_000`. **L2 Room: 30 días** — `RoomPublicParkingsCache.RETENTION_MS = 30L*24*60*60*1_000`, + tope de **60 discos** (`MAX_DISCS`). |
| ¿Qué provoca la expiración? | L1: el reloj, y **también un cambio de clave** (país/provincia del geocoder). L2: `evict()` en cada `put` — poda discos caducados, recorta a 60 y borra filas huérfanas. |
| ¿Qué pasa al expirar? | Se vuelve a preguntar a Overpass. El disco **no** se enseña mientras tanto. |
| ¿Se bloquea la UI? | El hilo no se bloquea, pero **el parseo y el ordenado corren en `Dispatchers.Main`** (no hay un solo `withContext` en `data/`) → jank con 681 elementos. La lista enseña spinner hasta que llega la red. |
| ¿Se puede enseñar caché y actualizar después? | **Sí, y el dato ya está en disco.** Hoy se prohíbe explícitamente (KDoc de `DiskBackedPublicParkingsSource`). Es el cambio de mayor impacto. |
| ¿Consulta demasiado grande? | Grande a propósito y **bien razonado**: una zona se pregunta una vez y panear dentro sale gratis 30 días. 238 KB en una sola respuesta es el precio. |
| ¿Demasiados elementos? | `out center 1000`. En Madrid vuelven 681 → no se trunca. El techo es correcto. |
| ¿Se podría dividir por zonas/radios? | **Ya está dividido por zonas** (`ParkingZoneGrid`, celda de 3 km). No hay reparto por zoom. |
| ¿Llamadas repetidas a la misma zona? | Sí, en un caso concreto: **el geocoder cambia la clave de L1**. Primera pregunta con `countryCode=""`, segunda con `"es"` → misma zona, dos peticiones. |
| ¿Consultas duplicadas? | No entre carriles (el cupo las serializa), sí el caso de arriba. |
| ¿Cuellos de botella de parsing/DB/mapeo? | Sí: parseo en el hilo principal, y `RoomPublicParkingsCache.evict()` hace `parkingDao.getAll()` en **cada escritura** y cruza cada fila contra cada disco vivo — O(filas × discos), hasta ~60.000 × 60 en el peor caso, **antes** de devolver la respuesta a la UI. |
| ¿Se puede mejorar la percepción aunque la red tarde igual? | **Sí, radicalmente**: el disco ya tiene la respuesta y no se usa. |

### Los cinco cuellos de botella, ordenados por impacto

1. 🔴 **El disco no se lee antes que la red.** Hay hasta 30 días de parkings de esa misma zona en
   Room y la UI enseña un spinner esperando 1,4–8 s (40 s en el peor caso). **[MEDIDO]**
2. 🔴 **El geocode bloquea la petición y nadie usa su respuesta.** `covers()` es `true` siempre;
   `countryCode`/`regionName` solo entran en la clave de L1 — donde además **causan** una petición
   duplicada cuando el geocoder despierta tarde. Ya estaba anotado como deuda en
   `overpass-the-quotas-wait-is-part-of-the-walk-001.md` § *Fuera de alcance*. **[MEDIDO]**
3. 🟠 **El calentamiento pregunta gasolineras ANTES que parkings**, y espera (`join()`) porque el
   cupo de Overpass es de una pregunta a la vez. Los parkings —la queja— van los segundos.
   `HomeNearbyLens.entries` = `SPOTS, FUEL, PARKINGS`. **[MEDIDO]**
4. 🟠 **Parseo y ordenado en el hilo principal.** 238 KB de JSON + 681 haversines + `sortedBy`.
   **[MEDIDO]**
5. 🟡 **`evict()` en el camino crítico**, con un `SELECT *` de la tabla entera por escritura.
   **[MEDIDO]**

---

## 2 · Cómo hacer OSM mucho más rápido — opciones y veredicto

| Enfoque | Ventaja | Inconveniente en NUESTRO caso | Veredicto |
|---|---|---|---|
| **Cache-first / stale-while-revalidate** | La lista aparece **instantánea** con lo de la última visita; la red refresca detrás | Hay que decir que se está recordando (el dato `rememberedAtMs` ya viaja) | ✅ **Hacer ya** |
| **Room como caché persistente** | Ya está hecho, con cobertura por discos | Solo se lee cuando la red falla | ✅ Ya está — falta **usarlo** |
| **Caché por geohash / tiles** | Claves estables | `ParkingZoneGrid` ya es exactamente esto (celda de 3 km) | ✅ Ya está |
| **Precarga de zonas vecinas** | La siguiente zona ya está | ×4–8 peticiones por sesión contra infraestructura donada; el cupo es 1 a la vez | ❌ Desproporcionado |
| **Consultas más pequeñas** | Primer byte antes | Medido: más peticiones **y** más bytes en total (5,8 MB vs 2,6 MB por ciudad/mes) | ❌ Ya descartado por medición |
| **Radio según zoom** | Menos payload al alejar | Rompe la rejilla de zonas, que es lo que hace que panear salga gratis | ❌ |
| **Primero lo cercano** | Percepción | Overpass no ordena por distancia (`out` corta por id) | ❌ No lo permite el servidor |
| **Refresco en background / WorkManager** | Disco siempre fresco | Gasta capacidad donada en mapa que nadie mira; ya decidido que no en `cache-a-map-…` §2 ter | ❌ Salvo medición que lo pida |
| **Debounce de cámara** | Menos peticiones | Ya existe (`NEARBY_CAMERA_SETTLE_MS`) + rejilla de zonas | ✅ Ya está |
| **Evitar repetir consultas** | Menos peticiones | El geocode rompe la clave hoy | ✅ **Arreglar ya** |
| **TTL distinto por tipo de dato** | Honestidad | Ya está: 30 min «¿vuelvo a preguntar?» vs 30 días «¿qué enseño si no puedo?» | ✅ Ya está |
| **Proxy/CDN propio delante de Overpass** | Latencia y cupo bajo control | Backend nuevo, coste, y Overpass sigue siendo el origen lento | 🟡 Nivel 3 |
| **Dataset PRE-COCINADO propio** (extracto OSM → JSON por celda en Firebase Hosting) | Primer byte de CDN, **sin cupo, sin espejos caídos**; ~1,4 GB de extracto de España → unos pocos MB filtrados | Es un **Derivative Database** ODbL: hay que ofrecerlo bajo ODbL y atribuir; hace falta un job de regeneración | 🟢 **La mejor apuesta a medio plazo** |
| **Overpass comercial (Geofabrik)** | Sin caídas, sin cupo compartido | 40–550 €/mes; sigue siendo una petición por zona | 🟡 Solo si el pre-cocinado no sale |
| **Otras fuentes de POI** (Overture, Geoapify, Foursquare) | Sin Overpass | Ninguna trae precio ni ocupación; Overture/FSQ son **descargas**, no APIs de proximidad | 🟡 Ver §9 |

### Recomendación concreta para Paparcar

**Nivel 1 (hoy, sin infraestructura):** disco primero + quitar el geocode del camino + calentar
parkings antes que gasolineras + sacar el trabajo pesado del hilo principal. Esto convierte
«1,4–8 s de spinner» en **«la lista está ahí al abrir»** para cualquier zona ya visitada, y quita
segundos enteros del primer arranque.

**Nivel 3 (cuando haya tráfico):** un dataset pre-cocinado desde el extracto de Geofabrik, servido
como ficheros estáticos por celda desde Firebase Hosting. Es el único cambio que quita a Overpass
del camino del usuario del todo, y además resuelve el riesgo de que OSM nos retire el acceso.

---

## 3 · DOOBL — qué sabemos, y qué NO

**Qué es.** App española de aparcamiento operada por **Niutron Systems S.L.**, equipo de Málaga.
Compite frontalmente con Paparcar: detecta que aparcas e invita a publicar la plaza que dejas.
**[VERIFICADO]**

**Qué enseña**, según su propia web: «How likely you are to park on each street, with the zone types:
blue, resident, free», «Car parks: **Spaces, real-time availability and price**», cargadores
eléctricos y plazas PMR. Ciudades listadas: Málaga, Palma de Mallorca, Madrid, Barcelona, Valencia.
**[VERIFICADO]** — <https://www.doobl.app/en/>

**De dónde saca los datos: NO LO PUBLICAN.** Revisados hoy: la web, los *Terms and Conditions* y la
*Privacy Policy*. **Cero** menciones a proveedores, APIs, licencias o atribuciones — ni OpenStreetMap,
ni ODbL, ni Google, ni HERE, ni TomTom, ni open data municipal. La única referencia a terceros es
genérica: *«Doobl may display advertising, offers, or content from third parties»*. **[VERIFICADO]**

**No tienen API pública.** No existe portal de desarrolladores, documentación ni repositorio.
**[VERIFICADO — por ausencia]**

**[INFERENCIA]**, marcada como tal: las cinco ciudades que listan coinciden casi exactamente con
las que publican ocupación de parkings en open data (Málaga cada minuto, Madrid en tiempo real con
**tarifas**, y Palma/Barcelona/Valencia con catálogo municipal). Su «probabilidad por calle» encaja
con un modelo propio entrenado con observaciones de sus propios usuarios — que es lo que su web
describe: *«it learns from real observations, street by street»*. **Nada de esto está confirmado por
ellos**, y no debe citarse como hecho.

**Lo que sí se puede concluir con seguridad:** su ventaja en precios y ocupación **no requiere un
proveedor de pago**. Está al alcance de cualquiera que haga los adaptadores de open data municipal.

---

## 4 · Qué hacen otras apps

| App / servicio | Qué enseña | Infraestructura conocida | Verificación |
|---|---|---|---|
| **Google Maps** | Parkings, «parking difficulty», a veces precio | Datos propios + partners. La **API** (Places New) expone solo booleanos `freeParkingLot`/`paidParkingLot`/… y un nivel `PLENTY`/`SOMEWHAT_DIFFICULT`/`DIFFICULT`. **No hay precio ni plazas libres en la API.** | [VERIFICADO] |
| **Waze** | Parkings cercanos | Mismo grupo; sin API de datos de parking | [NO VERIFICADO] |
| **Parkopedia** | 90 M plazas en 90 países, precios y disponibilidad | **Ellos son el proveedor**: encuestadores presenciales + feeds de operadores + modelos predictivos para lo dinámico. API REST o feed, **solo por contrato** | [VERIFICADO] |
| **EasyPark / RingGo / ParkMobile / Flowbird** | Pago de zona regulada, tarifas | **Partner API** documentada pero *«only available with commercial agreement»* | [VERIFICADO] |
| **Parclick / Parkimeter / ParkVia** | Reserva de parking con precio | Marketplace: el precio es **suyo y de sus parkings contratados** (>900 en España). Programa de afiliados; **no hay API pública documentada** | [VERIFICADO el programa; API NO VERIFICADA] |
| **Telpark (Empark), Saba, Interparking** | Sus propios parkings | Datos de operador, integración caso a caso | [NO VERIFICADO] |
| **Apps de ciudad** (Parking Madrid, Zaragoza Parking, Vigo) | Ocupación real de los parkings municipales | Consumen el **open data de su propio ayuntamiento** | [VERIFICADO] |

⛔ Ninguna de las que enseñan precio lo hace con una API abierta. Las dos vías reales son
**contrato** (Parkopedia/TomTom/HERE) o **open data municipal** (Madrid, Málaga, …).

---

## 5 · Fuentes de datos disponibles — tabla comparativa (FASE 5)

| Proveedor | Cobertura España | Parkings | Precios | Disponib. | Realtime | API | Coste | Uso comercial | Observaciones |
|---|---|---|---|---|---|---|---|---|---|
| **OpenStreetMap / Overpass** | Mundial | ✅ ubicación, tipo, aforo, gálibo, horario | ⚠️ solo `fee=yes/no` | ❌ | ❌ | ✅ pública | Gratis | ✅ con atribución ODbL | Cupo de facto **~10.000 req/día y <1 GB/día**; a los consumidores intensivos se les pide montar su instancia |
| **Extracto Geofabrik + pre-cocinado propio** | Mundial (España 1,4 GB, diario) | ✅ igual que OSM | ⚠️ igual | ❌ | ❌ | ✅ la que hagamos | Gratis (+ hosting) | ✅ ODbL: **el derivado se publica bajo ODbL** | La vía para quitar Overpass del camino |
| **Overpass comercial (Geofabrik)** | Mundial | ✅ | ⚠️ | ❌ | ❌ | ✅ | **40 €**/10k · **80 €**/100k · **160 €**/1M · XXL desde **550 €**/mes, pago anual | ✅ | Quita caídas y cupo, no la latencia |
| **Overture Maps — Places** | Mundial, 53 M POI | ✅ POI de parking | ❌ | ❌ | ❌ | ❌ (descarga GeoParquet en S3) | Gratis | ✅ CDLA-Permissive 2.0 + Apache 2.0 | Licencia **más permisiva que ODbL**; no hay share-alike |
| **Geoapify Places** | Mundial (OSM) | ✅ | ❌ | ❌ | ❌ | ✅ | 3.000 req/día gratis · desde **49 €/mes** por 300k créditos | ✅ permite **cachear y redistribuir** con atribución | Un Overpass gestionado; no añade precio |
| **Google Places (New)** | Mundial | ✅ POI | ❌ (solo free/paid booleano) | ⚠️ solo «dificultad» estimada | ❌ | ✅ | De pago por petición | ✅ con **fuertes restricciones de caché** | ⛔ No sirve para lo que buscamos |
| **Foursquare OS Places** | Mundial | ✅ POI | ❌ | ❌ | ❌ | Descarga | Gratis (Apache 2.0) | ✅ | Igual que Overture: catálogo, no precios |
| **Mapbox Search Box** | Mundial | ✅ POI | ❌ | ❌ | ❌ | ✅ | Por petición | ✅ | Idem |
| **TomTom Parking Availability** | **España ✔ «detailed data»** | ✅ off-street | — | ✅ | ✅ refresco **~10 min** | ✅ REST | ⛔ **NO está en free ni PAYG** — solo ventas | Por contrato | No se puede medir la cobertura real sin hablar con comercial |
| **TomTom Parking Prices** | **España ✔ «detailed data»** | ✅ off-street | ✅ estructura tarifaria + coste estimado | — | — | ✅ REST | ⛔ **NO está en free ni PAYG** | Por contrato | Es la API que más se acerca a `parking+coords+nombre+precio+horario` |
| **HERE Off-Street Parking** | ⛔ **no publica cobertura**: «contact your HERE representative» | ✅ | ✅ precios | ✅ | ✅ | ✅ REST | Contrato; HERE subió tarifas ~6 % desde 01-04-2026 | Por contrato | Opaca antes de firmar |
| **HERE On-Street Parking** | ⛔ idem | ✅ zonas | ✅ tarifas | ✅ + probabilidad | ✅ | ✅ | Contrato | Por contrato | 🔑 **Compite con nuestro producto** (ver §12) |
| **Parkopedia** | Alta (90 países) | ✅ | ✅ precios encuestados | ✅ real + predictivo | ✅ | ✅ REST o feed | Contrato | Por contrato | El estándar de facto del sector automoción |
| **DGT — NAP «Estado de ocupación de parkings en Ayuntamientos»** | España (municipios que se adhieran) | ✅ geometría y atributos | ❓ | ✅ ocupación | ✅ «lo más cercano al tiempo real» | ✅ DATEX II v3 REST | **Gratis** | ✅ **CC BY 4.0** | ⚠️ ver §6 — sin lista de municipios y docs en preproducción |
| **Open data municipal (Madrid, Málaga, Zaragoza, Vigo, Pamplona…)** | Por ciudad | ✅ | ✅ **Madrid publica tarifas** | ✅ | ✅ 1–3 min | ✅ | **Gratis** | ✅ CC BY 4.0 (⚠️ Málaga es **CC BY-SA**) | La vía realista para España |
| **EasyPark / Flowbird Partner API** | Alta en zona regulada | ⚠️ zonas | ✅ tarifas on-street | ⚠️ | — | ✅ | Solo con acuerdo comercial | Por contrato | Su negocio es el pago, no el dato |
| **Parclick / ParkVia (afiliación)** | >900 parkings en España | ✅ los suyos | ✅ **precio de reserva** | ✅ los suyos | ✅ | ❓ no pública | **Nos pagan a nosotros** (comisión) | Por acuerdo | El dinero va en la otra dirección |

### Qué tipo de dato es cada cosa

- **Estático:** OSM, Overture, Foursquare, catálogos municipales, ficha de Parkopedia. *Dónde está,
  cómo se llama, cuántas plazas tiene, qué gálibo.*
- **Dinámico real (del operador):** ocupación de Madrid/Málaga/Zaragoza/Vigo, TomTom Availability,
  HERE Off-Street. *Cuántas plazas quedan ahora.*
- **Estimación / predicción:** «parking difficulty» de Google, probabilidad de HERE On-Street,
  modelos predictivos de Parkopedia, la probabilidad por calle de Doobl. **No es un dato medido.**
- **Aportado por usuarios:** las plazas de la comunidad de Paparcar, y las de Doobl.

---

## 6 · Open Data útil en España — estado real hoy

| Fuente | Qué da | Frecuencia | Licencia | Estado |
|---|---|---|---|---|
| **Madrid** — `datos.madrid.es` dataset 50027 | Plazas totales, **ocupación**, accesos, horarios, **TARIFAS**, servicios, de los rotacionales adheridos | Tiempo real | **CC BY 4.0** | ✅ [VERIFICADO]. API + WSDL. Vía EMT MobilityLabs con registro gratuito |
| **Málaga** — `datosabiertos.malaga.eu/recursos/aparcamientos/ocupappublicosmun/ocupappublicosmun.csv` (+ `catalogo.csv`) | Ocupación de los municipales | **Cada minuto** | ⚠️ **CC BY-SA 4.0** (share-alike) | ✅ [VERIFICADO] |
| **Zaragoza** | Ocupación de los parkings VINCI + estimación por zona | **90 s** / 2 min | CC BY | ✅ [VERIFICADO] |
| **Vigo** | Libres/ocupadas, JSON | 3 min | CC BY | ✅ [VERIFICADO] |
| **Pamplona** | Ocupación en tiempo real, XML | Tiempo real | CC BY | ✅ [VERIFICADO] |
| **Barcelona** | Tarifas del estacionamiento en superficie (AREA), CSV | Periódica | CC BY | ✅ [VERIFICADO]. La ocupación de los cerrados la lleva **B:SM**, no está en abierto |
| Valencia · Bilbao · Sevilla | Ubicación sí; ocupación en manos de operadores privados | — | — | ❌ [VERIFICADO en el ticket F6b del 09-09-2026] |

### 🔎 La pista que puede ahorrar N adaptadores — **sigue SIN cerrar**

**DGT, Punto de Acceso Nacional**, dataset *«Estado de ocupación de parkings en Ayuntamientos»*:
DATEX II v3, REST pull, **CC BY 4.0**, registro opcional. Ya figura en el catálogo de **producción**
(`nap.dgt.es`). **[VERIFICADO]**

⚠️ **Y sigue sin poder darse por bueno**, comprobado hoy:
- La wiki técnica (`github.com/dgt30-esp/Caso-de-uso-11/wiki`) describe el **Caso de uso 11** como el
  canal por el que **los ayuntamientos PUBLICAN**, no por el que terceros consumen.
- La única URL REST que expone sigue siendo **preproducción**: `https://pre.cmobility30.es/use-case-11`.
  La de producción que aparece es **MQTT**: `ssl://production.cmobility30.es:8883`.
- Hace falta **certificado de acceso**, que se pide a `soporte@cmobility30.es`.
- **No hay lista de ayuntamientos participantes** en ningún sitio.

👉 **Acción concreta y barata:** escribir a `soporte@cmobility30.es` pidiendo el endpoint de
consumo en producción y qué municipios publican de verdad. Si agrega a varios, es **un adaptador
para todos** con un modelo ya normalizado. Hasta entonces, no diseñar nada encima.

### 🟢 El cambio regulatorio que lo cambia todo a medio plazo — **[VERIFICADO]**

El **Reglamento Delegado (UE) 2024/490** modifica el 2017/1926 para incluir, entre los datos que los
poseedores deben hacer accesibles por el Punto de Acceso Nacional, *«data on parking, such as
location and availability of parking places, where and how to pay for parking, and **information on
parking tariffs**»*.

Plazos del punto 2.2 del Anexo: **1 de diciembre de 2026** para la red global TEN-T y **1 de
diciembre de 2028** para el resto de la red de la Unión, incluyendo *«availability check and location
for car parking spaces available (on and off-street)»*.

⚠️ **Qué significa y qué no.** Significa que **precio y disponibilidad de parking en España están
legalmente encaminados a ser open data vía NAP**, gratis y con licencia reutilizable. **No** significa
que vayan a estar completos ni puntuales: el propio caso de uso 11 de la DGT lleva desde antes en
preproducción. Es una razón muy fuerte para **construir el adaptador de NAP y no firmar un contrato
caro ahora**, no una razón para esperar sentados.

---

## 7 · Opciones para PRECIOS de parking en España

Ordenadas por viabilidad real para nosotros:

1. 🟢 **OSM `fee=yes/no`** — ya lo tenemos. Es «cuesta / no cuesta», no un precio. Cobertura medida:
   el 71 % no lleva la etiqueta. **[MEDIDO]**
2. 🟢 **Open data municipal.** **Madrid publica tarifas** de sus rotacionales, en tiempo real y con
   CC BY 4.0. Barcelona publica las tarifas de superficie (AREA). Es lo que un competidor necesita
   para enseñar «precio» en la capital sin pagar a nadie. **[VERIFICADO]**
3. 🟡 **NAP / DGT (2024/490).** Tarifas obligatorias por regulación con horizonte 2026–2028.
   Gratis, CC BY. Requiere el adaptador DATEX II. **[VERIFICADO la norma; NO VERIFICADO el dato]**
4. 🟡 **Afiliación con un marketplace** (Parclick, ParkVia). Nos daría **precio de reserva real** de
   >900 parkings en España y **ingresos** en vez de costes. No es el precio de ventanilla, es el
   precio prepagado — hay que decirlo así. **[VERIFICADO el programa; la API, no]**
5. 🔴 **TomTom Parking Prices / HERE / Parkopedia.** Es el dato bueno y completo. **Ninguno tiene
   puerta de autoservicio**: no se puede sacar una key, medir la cobertura en Málaga y decidir, que
   es como este proyecto decide todo. **[VERIFICADO]**
6. ⛔ **Scraping.** Descartado: los operadores publican tarifas en webs con condiciones de uso que lo
   prohíben, el dato cambia sin aviso, y una tarifa mal leída manda a un conductor a pagar el doble.
   Choca de frente con la doctrina de fallo asimétrico del proyecto. **Solo se consideraría** sobre
   una web de un ayuntamiento que publique la tarifa oficial **sin** dataset — y aun así, antes de
   scrapear se pide el dataset.

**¿Se puede conseguir `parking + coordenadas + nombre + precio + horario + disponibilidad`?**
**Sí, pero no en todas partes.** Hoy y gratis: **en Madrid, entero**; en Málaga/Zaragoza/Vigo/Pamplona,
todo menos el precio; en el resto de España, solo `parking + coords + nombre + horario` (OSM) y
«cuesta o no cuesta». Con contrato (TomTom/Parkopedia): casi en todas partes.

---

## 8 · Precio y tiempo real — qué podemos prometer sin mentir (FASE 7)

| Concepto | Qué es | ¿Podemos? |
|---|---|---|
| **Precio fijo** | Tarifa plana por estancia/día | ✅ donde el ayuntamiento lo publique |
| **Tarifa por hora** | €/h | ✅ Madrid; ⚠️ resto no |
| **Tarifa por tramo** | Primeros 30 min a X, luego Y | ✅ con el dato de Madrid; requiere modelo de tramos |
| **Tarifa nocturna / abonos** | Franja horaria distinta | 🟡 depende de la ficha |
| **Precio actualizado recientemente** | Un precio **con su hora** | ✅ y es **la doctrina que la app ya aplica** al combustible (`StationPrice.reportedAtMs`) |
| **Precio del operador** | El operador lo publica | 🔴 solo por contrato o afiliación |
| **Disponibilidad en tiempo real** | Plazas libres AHORA | ✅ en las ciudades de §6, con retraso de 1–3 min |
| **Predicción de disponibilidad** | Probabilidad estimada | ⛔ **No lo hacemos.** Es lo que vende HERE/Parkopedia/Doobl y es *estimación*. Nuestro producto mide |
| **Reserva en tiempo real** | Reservar y pagar | 🟡 solo vía marketplace afiliado |

⛔ **La regla que se deriva de la doctrina de la casa:** un número sin hora no se enseña, y una
estimación no se enseña con la misma tipografía que una medición. El combustible ya lo resolvió así
—cada precio lleva su `reportedAtMs` y la UI dice la edad— y el parking debe heredarlo tal cual,
sin inventar vocabulario nuevo.

---

## 9 · Arquitectura híbrida propuesta (FASES 6 y 16)

```
                            GetNearbyPublicParkingsUseCase
                                        │
                        PublicParkingsSource (el puerto que ya existe)
                                        │
        ┌───────────────────────────────┼───────────────────────────────┐
        │                               │                               │
   BASE GEOGRÁFICA              ENRIQUECIMIENTO                   LO NUESTRO
   (dónde está)                 (cuánto y cuántas)                (qué sabemos)
        │                               │                               │
  OSM / Overpass            Open data municipal (ES)            Plazas de la comunidad
  → pre-cocinado propio     DGT NAP (DATEX II, cuando exista)   Detección automática
  cobertura MUNDIAL         Afiliado (precio de reserva)        Historial propio
                            fusión por proximidad <40 m
```

**Sí, esto da cobertura nacional sin depender en exclusiva de Overpass**, y es exactamente la junta
que el ticket `PARKINGS-LIVE-OCCUPANCY-001` ya había dibujado: la base **no se sustituye, se
enriquece**. Un feed municipal trae la ocupación de los cuatro parkings que gestiona y nada más.

⛔ **La trampa conocida, ya pagada una vez al cruzar catálogos de gasolineras:** un emparejamiento
fallido entre el parking de OSM y el del feed municipal **parece un dato** (dos parkings donde hay
uno) en vez de parecer un bug. La fusión por proximidad necesita su propio testigo.

---

## 10 · Costes aproximados

| Vía | Coste | Comentario |
|---|---|---|
| Lo de hoy (Overpass público) | **0 €** | Con riesgo de que nos retiren el acceso |
| Nivel 1 + 2 (este ticket) | **0 €** | Solo código |
| Pre-cocinado desde Geofabrik + Firebase Hosting | **~0 €** | Hosting en el plan actual; el job cabe en GitHub Actions |
| Adaptadores de open data municipal | **0 €** de licencia | Pero estrena backend: `functions/` + plan Blaze (unos pocos €/mes en Cloud Scheduler + Functions) |
| Geofabrik Overpass | 40 € / 80 € / 160 € / desde 550 € al mes, **pago anual por adelantado** | |
| Geoapify | 0 € (3.000 req/día) · desde 49 €/mes | |
| TomTom / HERE / Parkopedia | **Sin precio público** | Solo por comercial |
| Afiliación Parclick/ParkVia | **Ingreso**, no coste | |

---

## 11 · Restricciones y licencias — lo que hay que respetar

- **OSM es ODbL 1.0.** Consultar Overpass en vivo es *uso*. **Publicar un extracto filtrado propio es
  una *Derivative Database*** y obliga a ofrecerla bajo ODbL, además de la atribución. Es asumible
  —publicamos el fichero y su licencia— pero **hay que hacerlo a propósito**, no por descuido.
  La app ya acredita OpenStreetMap con ODbL 1.0 en `LicensesScreen`. **[VERIFICADO]**
- **Overpass público:** ~10.000 peticiones/día y <1 GB/día por consumidor, y la propia documentación
  pide a los servicios con demanda sostenida que monten su instancia. Los servicios comerciales
  están avisados de que el acceso puede retirarse. **[VERIFICADO]**
- ⚠️ **Málaga es CC BY-SA 4.0, no CC BY.** Share-alike: mezclar su ocupación dentro de una base que
  redistribuyamos arrastra la licencia. Madrid y la DGT son CC BY 4.0, sin ese problema.
- **Google Places:** restricciones fuertes de caché y de uso fuera de un mapa de Google. Descartado.
- **TomTom/HERE/Parkopedia:** revisar **la cláusula de caché y almacenamiento ANTES que el precio** —
  es lo que decide si el dato puede vivir en nuestro backend o hay que consultarlo por usuario y por
  vista, que es un coste completamente distinto.

---

## 12 · Recomendación técnica final

1. **Arreglar la velocidad con lo que ya tenemos.** El disco lleva 30 días de parkings de esa misma
   zona y nadie lo mira hasta que la red falla. Es el cambio de mayor impacto y cuesta cero.
2. **No comprar nada todavía.** Ni TomTom, ni HERE, ni Parkopedia. Ninguno deja medir su cobertura
   antes de firmar, y **la regulación europea va a poner tarifas y disponibilidad en el NAP español**
   con horizonte 2026–2028, gratis y con licencia reutilizable.
3. **La siguiente inversión de producto es el open data municipal**, empezando por **Madrid** —la
   única fuente verificada que trae **ocupación Y tarifas** juntas, en tiempo real y CC BY 4.0— y
   **Málaga**, con ocupación cada minuto (⚠️ share-alike).
4. **Antes de escribir N adaptadores, gastar un correo**: `soporte@cmobility30.es`, preguntando por
   el endpoint de consumo en producción del caso de uso 11 y por qué municipios publican.
5. **El pre-cocinado de OSM es la apuesta técnica a medio plazo** para la base geográfica: quita
   Overpass del camino del usuario y el riesgo de que nos corten.
6. 🔑 **No comprar «probabilidad de encontrar sitio en la calle».** Es lo que vende HERE On-Street,
   y es exactamente la pregunta que Paparcar responde con comunidad y detección **medida** en vez de
   estimada. Pagar por una estimación de lo que la propia app mide es comprarse la competencia.

---

## 13 · Plan por fases

### Nivel 1 — mejora inmediata (este ticket)

| Cambio | Ficheros | Complejidad | Coste | Impacto | Riesgos |
|---|---|---|---|---|---|
| **Disco primero, red después** (SWR) | `DiskBackedPublicParkingsSource`, `PublicParkingsSource`, `GetNearbyPublicParkingsUseCase`, `HomeViewModel` | Media | 0 € | 🔴 **Alto**: la lista aparece al instante en zona visitada | Enseñar un dato viejo sin decirlo → lo cubre `rememberedAtMs`, que ya existe y ya se renderiza |
| **Quitar el geocode del camino** | `GetNearbyPublicParkingsUseCase` | Baja | 0 € | 🟠 Segundos en frío + elimina la petición duplicada | Cuando llegue el registro por país habrá que reintroducirlo **con su medición** |
| **Calentar parkings antes que gasolineras** | `HomeViewModel` | Trivial | 0 € | 🟠 Adelanta los parkings una respuesta entera | Retrasa gasolineras lo mismo |
| **Parseo y orden fuera del hilo principal** | `OsmPublicParkingsSource` | Baja | 0 € | 🟡 Quita jank con 681 elementos | — |
| **`evict()` fuera del camino crítico** | `RoomPublicParkingsCache` | Baja | 0 € | 🟡 | La poda pasa a ser perezosa |

### Nivel 2 — arquitectura de caché

La pila se mantiene, cambia **el orden de lectura**:

```
ANTES:    L1 memoria (30 min) → RED → [si falla] L2 Room (30 días)
DESPUÉS:  L1 memoria (30 min) → [L2 Room si cubre: SE ENSEÑA YA] → RED → reconcilia L2
```

⛔ Lo que **no** cambia: los dos TTL siguen contestando a dos preguntas distintas, el disco sigue
reconciliando discos enteros (las bajas se propagan), y un fallo sin cobertura sigue siendo el fallo
original, nunca «no hay parkings aquí».

### Nivel 3 — cobertura (no ahora)

Dataset pre-cocinado desde el extracto de Geofabrik → ficheros por celda en Firebase Hosting →
`StaticPublicParkingsSource` delante de `OsmPublicParkingsSource`. Publicar el derivado bajo ODbL.

### Nivel 4 — precios (no ahora)

Adaptador de Madrid (tarifas + ocupación) como implementación del mismo puerto, fusionando por
proximidad contra la base. Y el correo a la DGT antes de multiplicar adaptadores.

### Nivel 5 — tiempo real (no ahora)

Lo que es **realmente viable**: ocupación de las 5–6 ciudades españolas que la publican, con retraso
de 1–3 min y diciendo la edad. Lo que **no**: predicción de disponibilidad, precio de operador
nacional, y reserva — esta última solo vía afiliado.

---

## 14 · Qué haría ahora / Qué dejaría para después

**Ahora, sin dependencias de nadie:** los cinco cambios del Nivel 1 y el reordenamiento de caché del
Nivel 2. Cero coste, cero backend, cero proveedores.

**Después, cuando haya tráfico o una decisión de producto:**
- el dataset pre-cocinado (necesita job de regeneración y publicar el derivado ODbL);
- los adaptadores de open data municipal (**estrena backend**: `functions/`, Blaze, scheduler);
- el correo a `soporte@cmobility30.es` — barato, y puede ahorrar N adaptadores;
- la afiliación con un marketplace (acuerdo comercial, pero el dinero va hacia nosotros);
- TomTom/HERE/Parkopedia: **solo** si alguna vez conceden evaluación para medir antes de firmar.

---

## 15 · ANTES / DESPUÉS — lo que se implementó en esta misma tarea

Detalle, testigos y falsificaciones:
[`docs/backlog/parkings-a-remembered-map-answers-before-the-network-001.md`](backlog/parkings-a-remembered-map-answers-before-the-network-001.md).

**ANTES**
- Fuente: OSM vía Overpass (2 espejos).
- Caché: L1 memoria 30 min · L2 Room 30 días, **leída solo cuando la red fallaba**.
- Tiempo hasta ver filas: lo que tardase Overpass — 1,4 s en caliente, 4,7–7,7 s en frío, **techo de
  40 s**; y detrás de la respuesta entera de gasolineras al entrar en Home.
- Problemas: esqueleto sobre un disco que ya tenía la respuesta · geocode bloqueante que nadie leía y
  que duplicaba la petición por zona · 238 KB de JSON parseados en el hilo principal · `SELECT *` de
  la tabla en cada escritura, dentro del camino crítico.

**DESPUÉS**
- Fuente: **la misma** — no se ha añadido ni un proveedor.
- Estrategia de caché: L1 memoria (30 min) → **lo recordado se PINTA** → red → reconcilia L2 (30
  días). Los tres TTL siguen contestando a tres preguntas distintas; se añade la tercera,
  `remembered()`: *«¿qué puedo enseñar MIENTRAS pregunto?»*.
- Flujo de actualización: la respuesta viva **gana siempre** y sustituye a lo recordado. Si falla, se
  mantiene lo recordado y aparece la nota de «sin conexión» — que sigue siendo verdad porque solo
  sale cuando la pregunta ha terminado.
- Mejoras: disco primero · sin geocode (clave de caché constante ⇒ **se acaba la petición duplicada
  por zona**) · parkings se calientan antes que gasolineras · parseo, haversine y orden en
  `Dispatchers.Default` · la barrida de huérfanos solo cuando ha desaparecido un disco.
- Costes adicionales: **ninguno**. Ni proveedor, ni backend, ni dependencia nueva.
- Verificación: suite completa **3.503 tests, 0 fallos** (`testAll --rerun-tasks`, que es lo que
  obliga a los guards de Konsist a ejecutarse), ambos flavours de Android compilando, y **cinco
  testigos falsificados** uno a uno.
  ⚠️ `:shared:compileKotlinIosSimulatorArm64` falla en `IosAppNotificationManagerImpl` por
  `notif_action_ack` — **comprobado en master con el árbol limpio: ya fallaba**, es ajeno a esto.

## Fuentes consultadas

Código y mediciones propias: `docs/backlog/parkings-on-the-map-001.md`,
`parkings-a-zone-is-asked-once-and-panned-for-free-001.md`,
`cache-a-map-must-not-empty-on-cold-start-001.md`,
`overpass-a-mirror-walk-must-outlive-the-server-it-asks-001.md`,
`overpass-the-quotas-wait-is-part-of-the-walk-001.md`,
`home-the-three-lanes-warm-up-once-on-entry-001.md`,
`parkings-live-occupancy-001.md`, `parking-availability-vendor-001.md`.

Externas (consultadas el 21-09-2026):
- Doobl — <https://www.doobl.app/en/> · <https://www.doobl.app/en/terms-and-conditions/> · <https://www.doobl.app/en/privacy-policy/> · <https://play.google.com/store/apps/details?id=com.niutronsystems.app>
- Overpass fair use — <https://dev.overpass-api.de/overpass-doc/en/preface/commons.html>
- Geofabrik Overpass comercial — <https://www.geofabrik.de/data/overpass-api.html>
- Geofabrik extractos España — <https://download.geofabrik.de/europe/spain.html>
- ODbL / OSMF FAQ — <https://osmfoundation.org/wiki/Licence/Licence_and_Legal_FAQ> · <https://opendatacommons.org/licenses/odbl/1-0/>
- Overture Places — <https://docs.overturemaps.org/guides/places/>
- Geoapify — <https://www.geoapify.com/pricing/> · <https://apidocs.geoapify.com/docs/places/>
- Google Places (New), campos — <https://developers.google.com/maps/documentation/places/web-service/data-fields> · <https://developers.google.com/maps/documentation/geocoding/parking-options>
- TomTom Parking Availability — <https://docs.tomtom.com/parking-availability-api/documentation/product-information/introduction> · <https://docs.tomtom.com/parking-availability-api/documentation/product-information/market-coverage>
- TomTom Parking Prices — <https://docs.tomtom.com/parking-prices-api/documentation/product-information/introduction> · <https://docs.tomtom.com/parking-prices-api/documentation/product-information/market-coverage>
- HERE Parking — <https://docs.here.com/parking/docs/introduction-off-street-parking> · <https://docs.here.com/parking/docs/on-street-parking-api-reference>
- Parkopedia — <https://business.parkopedia.com/parking-data>
- EasyPark Partner API — <https://developer.easyparkgroup.com/>
- Parclick afiliados — <https://parclick.es/partnership/affiliate>
- DGT NAP — <https://nap.dgt.es/dataset/estado-de-ocupacion-de-parkings-en-ayuntamientos> · <https://github.com/dgt30-esp/Caso-de-uso-11/wiki>
- Madrid — <https://datos.madrid.es/dataset/50027-0-aparcamientosocupacionyservicios>
- Málaga — <https://datos.gob.es/en/catalogo/l01290672-ocupacion-aparcamientos-publicos-municipales>
- Vigo — <https://datos-ckan.vigo.org/dataset/t-parking-real>
- Zaragoza — <https://www.zaragoza.es/sede/portal/movilidad/trafico/estacionamiento>
- Pamplona — <https://datos.gob.es/en/catalogo/l01312016-ayto-de-pamplona-ocupacion-de-parkings-en-tiempo-real>
- Reglamento Delegado (UE) 2024/490 — <https://eur-lex.europa.eu/eli/reg_del/2024/490/oj/eng>
- ParkAPI — <https://github.com/ParkenDD/parkapi-sources-v3>
