# PARKINGS-ONE-CALL-MERGED-001 · una sola llamada, fusionada en el backend

**Estado:** ✅ Done — **desplegado y verificado en vivo el 02-11-2026** · ⏳ **sin ver en un móvil** (ver §Lo que queda) · rama `feature/PARKINGS-ONE-CALL-MERGED-001-api` ·
worktree `../Paparcar-onecall`
**Sale de:** [`data-providers-001.md`](data-providers-001.md) §E.3 (la arquitectura que eligió el
user) y **§F.4** (es el **ticket 2**). El almacén lo puso
[`osm-precooked-snapshot-001.md`](osm-precooked-snapshot-001.md) (ticket 1, ✅).

> **Este es el ticket que el conductor nota.** El 1 construyó el almacén y lo publicó; mientras no
> exista este, la app sigue preguntando a Overpass exactamente como la 1.5.0 que está en Play.

## Problema

Dos mitades del mismo hueco:

**1 · La app sigue pagando Overpass en cada zona nueva.** Medido el 02-11 contra `overpass-api.de`
con la consulta que compone la propia app:

| Punto | Respuesta | Tiempo |
|---|---|---|
| Madrid (Sol) | 227 KB | **44,8 s** |
| Barcelona | 218 KB | 33,9 s |
| Málaga | 277 KB | 29,3 s |
| Las Palmas | 152 KB | 39,5 s |
| Cuenca | 33 KB | **45,9 s** |
| Málaga **pedida justo detrás** de otra | 534 B | **502 en 0,22 s** |

⛔ Y el paseo de espejos corta a **`WALK_CEILING_MS = 30 s`**, así que **cuatro de esas cinco
habrían llegado tarde: la app ya habría abandonado la pregunta.** Lo que ve el conductor no es una
lista lenta — es la lista **recordada** de hasta 30 días, o **nada** en una zona nueva, que en
pantalla se lee como *«aquí no hay parkings»*.

**2 · El artefacto del ticket 1 no tiene consumidor.** Están publicadas 12.566 celdas que contestan
en **0,05-0,20 s** y pesan ~1,6 KB, y **nadie las lee**. Un almacén sin tubería no es una mejora,
es un coste de mantenimiento.

## Doctrina violada

Ninguna regla rota: lo que hay es una decisión tomada y sin implementar. Las que **mandan sobre la
solución**, y ninguna se rediscute aquí:

1. ⛔ **Una sola llamada, y la fusión en el BACKEND** (§E.3, decisión del user). Tres razones suyas
   que siguen en pie: la trampa de la fusión <40 m se implementa **una** vez y no una por plataforma
   —decisivo para un KMP que va a iOS—, la lógica de **licencias** vive en un sitio, y el cliente
   **conserva el puerto que ya tiene** (`PublicParkingsSource`).
2. ⛔ **Todo corte es ADITIVO** (§D.4). La 1.5.0 está publicada y lee Overpass: el carril de Overpass
   **no se retira** del cliente mientras haya versiones publicadas que lo usen.
3. ⛔ **Una función hace I/O y NADA más.** Convención ya establecida en `functions/`: toda la
   decisión vive en un módulo puro (`fanout.ts`, `sharedCarFanout.ts`) con su `.test.ts` que corre
   con `node --test`, *«donde se puede probar sin publicar una plaza de verdad y mirar un móvil de
   verdad»*.
4. ⛔ **Quien reciba un 404 en una celda tiene que RELEER el puntero**, no concluir que no hay
   parkings: un `firebase deploy --only hosting` **espeja** el directorio y borra la versión anterior
   en el mismo despliegue que mueve `latest.json`.

## Señales / datos disponibles

Todo [MEDIDO] en este worktree hoy:

- **La cadena del cliente es un decorador**, lo que hace la inserción limpia
  (`DataModule.kt:363`):
  ```
  CachingPublicParkingsSource(          memoria, TTL 30 min
    DiskBackedPublicParkingsSource(     Room, 30 días
      delegate = OsmPublicParkingsSource(fuelHttpClient())   la red
  ```
- **Hay cliente HTTP en `commonMain`**: `fuelHttpClient()`, una **fábrica** (no un `HttpClient()`
  suelto en DI) *«para que un test lo pueda conducir con `MockEngine`»*. Se reutiliza.
- **`functions/` es TypeScript con tests** (`npm test` → `tsc && node --test lib/*.test.js`),
  nodejs22, `europe-west1`, `maxInstances: 10`. Dos funciones vivas, las dos de Firestore.
- **La rejilla ya publica su tabla**: `tools/parkings/grid-fixtures.json`, 10 puntos del mundo con
  celda, centro y **bloque con su orden**, vigilada por `ParkingGridFixtureGuardrailTest`. Es contra
  lo que se mide el puerto de TypeScript.
- ⛔ **NO existe Remote Config en la app** (`grep` vacío en `shared/src` y `app/src`). Ver §D.3.
- ⛔ **`firebase.json` no tiene ni un `rewrite`** (`grep -c rewrites` → 0). Hay `redirects` y
  `headers`, pero el `rewrite` a función es nuevo.

## Diseño

### D.1 La forma, de punta a punta

```
App (KMP, commonMain)
  │  UNA llamada
  └─► GET https://paparcar.com/api/parkings?cell=<band>_<col>&v=1
            │
            │ Hosting rewrite  (firebase.json)
            ▼
         mergeCell()  ·  europe-west1  ·  ANÓNIMA  ·  solo I/O
            │
            ├─ base      → las celdas del bloque, de /parkings/v1/<ver>/ (CDN)   ✅ ticket 1
            ├─ ocupación → Firestore publicParkings                             ⏳ ticket 3
            └─ oferta    → Parclick                                             🔴 ticket 5
            │
            └─ fusiona (<40 m, uno a uno) → emite su PROPIO Cache-Control
```

- **El cliente conserva su puerto.** Nada en `domain/` cambia: entra un decorador nuevo
  `ApiPublicParkingsSource` **delante** de Overpass, no en su lugar:
  ```
  Caching( DiskBacked( delegate = Api( fallback = Osm(...) ) , disk) )
  ```
  Overpass queda **intacto** y pasa a ser el fallback. Eso es el §D.4 aditivo, en una línea de DI.
- **Hoy el `mergeCell` sirve la base SOLA** —la ocupación es el ticket 3— y **ya entrega valor**: el
  carril deja de pagar Overpass. Pero el **esquema de respuesta va completo desde el primer día**
  (`availability?`, `offer?`, `mergedFrom`, `licences`), así que el ticket 3 **añade una fuente, no
  rediseña un contrato** y **el cliente no se vuelve a tocar**: los campos aparecen en una respuesta
  que ya sabe leer.

### D.2 ⛔ ¿Por qué una función, si el CDN ya sirve las celdas?

Pregunta legítima: el cliente podría leer las celdas **directamente** y hoy funcionaría, sin función
y sin cold start. Se descarta, y la razón no es el rendimiento:

- Leer celdas directamente obliga a **fusionar en el cliente** en cuanto llegue el ticket 3 — que es
  exactamente lo que §E.3 existe para evitar, y se escribiría **dos veces** (Android e iOS).
- Y obligaría a **cambiar el contrato del cliente** al llegar la ocupación: dos releases en vez de
  una, con la 1.5.0 publicada por medio.

⚠️ **El coste que sí tiene, dicho antes de medirlo:** un **cold start** de la función. Se paga **una
vez por celda y por TTL, globalmente**, porque delante va el CDN (§D.5). Hay que **medirlo en vivo**
tras desplegar, no suponerlo.

### D.3 ⛔ El interruptor NO va en Remote Config, y es una desviación consciente de §D.4

§D.4 fijó: *«el interruptor va en Remote Config, `parkings_base_source` con valores
`overpass`|`snapshot`, por país»*. Al abrir el código: **Remote Config no existe en la app**
[MEDIDO]. Meterlo es un SDK nuevo, su inicialización, su ciclo de fetch/activate, su registro en
Koin, su fake para el flavor mock y sus tests — un montón de maquinaria para un `if`.

**Lo que se hace en su lugar, que conserva la INTENCIÓN (poder revertir sin release):**

| | |
|---|---|
| **El interruptor viaja en la propia respuesta** | el payload lleva `serve: true\|false`. En `false`, el cliente cae a Overpass para esa pregunta |
| **Y el fallback es automático sin flag** | error de red, 5xx, timeout o cuerpo ilegible ⇒ Overpass. No hace falta decirle nada al cliente para que sobreviva |
| **Revertir** | se cambia el flag en el servidor: efecto inmediato, sin release y sin esperar un `fetch` de Remote Config |
| ⚠️ **Lo que se PIERDE** | el despliegue **por porcentaje**. No hay forma de dárselo al 10 % |

👉 **Por qué se asume esa pérdida:** lo que un rollout por porcentaje compra es descubrir que la
respuesta es **incorrecta** con poca gente delante. Eso aquí ya está medido por otra vía: el
comparador del ticket 1 dio **916 de 916** parkings idénticos al Overpass vivo en cinco puntos de
España. Y lo que un porcentaje **no** arregla —que el endpoint se caiga— lo cubre el fallback.
⛔ Si el user quiere el rollout por porcentaje, es **su propio ticket** y trae Remote Config entero
con él; no se cuela aquí a medias.

### D.4 ⛔ La rejilla pasa a vivir en DOS lenguajes, y por eso ya tiene su tabla

El backend recibe `cell=<band>_<col>` del cliente, así que **no** necesita `zoneOf(lat,lon)` — pero
sí necesita **`blockAround`**, porque las celdas **parten** el territorio y la respuesta tiene que
cubrir el alcance (§D.2 del ticket 1).

Y ⛔ **`blockAround` no es un cuadrado de anillos**: las columnas se derivan de la latitud de **su**
banda (rejillas vecinas desalineadas hasta media celda) y un grado de longitud vale menos metros
lejos del ecuador. La versión «obvia» de anillos **pierde parkings**, medido: 35 puntos de muestra
fuera en 9 ciudades, 12 de ellos en Svalbard.

**Por eso el ticket 1 publicó `tools/parkings/grid-fixtures.json`**: el puerto de TypeScript se
mide contra esa tabla —celda, centro y **bloque con su orden**— en su propio `.test.ts`. Un
invariante en dos idiomas **con un testigo compartido**, que es la trampa de `workData` resuelta
antes de que muerda.

### D.5 El TTL es propiedad de la RESPUESTA (§C.5 del expediente)

La función emite su propio `Cache-Control` según lo que acaba de fusionar:

| Celda | `Cache-Control` | Por qué |
|---|---|---|
| con fuente de ocupación (5 ciudades, ticket 3) | `s-maxage=120, stale-while-revalidate=60` | el dato más perecedero manda |
| **sin** nada perecedero (todo lo demás, y **todo** hoy) | `s-maxage=86400` | no hay nada que caduque en minutos |

⛔ **La respuesta tiene que ser ANÓNIMA**: un CDN no cachea lo que lleva `Set-Cookie` ni lo que
depende de quién pregunta. Encaja con la doctrina — **nunca se gatea VER** un parking, así que no hay
nada que personalizar.

### D.6 Qué módulos nuevos, y dónde

| Dónde | Qué | Por qué ahí |
|---|---|---|
| `functions/src/parkingsCell.ts` | **puro**: parsear el `cell`, `blockAround`, fusionar, decidir el `Cache-Control` | la convención de `functions/`: la decisión fuera de la función |
| `functions/src/parkingsCell.test.ts` | sus tests + la tabla de fixtures de la rejilla | `node --test`, sin desplegar nada |
| `functions/src/index.ts` | `mergeCell` = `onRequest` que lee, llama al módulo puro y escribe | I/O y nada más |
| `firebase.json` | el `rewrite` `/api/parkings` → `mergeCell` | — |
| `shared/.../data/parkings/ApiPublicParkingsSource.kt` | el decorador con su fallback | mismo patrón que el resto de la cadena |
| `shared/.../data/parkings/ParkingsApiDto.kt` | el contrato de respuesta, deserializado | al lado de `PublicParkingSnapshot.kt`, que es el del fichero |

## Progreso medido (02-11-2026)

### 🟢 1 · El puerto de la rejilla a TypeScript, con su testigo CRUZADO

`functions/src/parkingsGrid.ts` porta `zoneOf(id)`, el centro derivado y `blockAround`. Y
`parkingsGrid.test.ts` lo mide contra `tools/parkings/grid-fixtures.json` —generado por la rejilla
viva de Kotlin— celda a celda, centro a centro y **bloque a bloque, en orden**, sobre los 10 puntos
del mundo.

**Falsificado**: moviendo `CELL_METERS` de 3000 a **3001** se ponen **2 rojos de 36**. El invariante
vive en dos idiomas y ahora los dos se delatan: si alguien cambia la rejilla en Kotlin y no regenera
la tabla, salta `ParkingGridFixtureGuardrailTest`; si alguien toca este puerto, salta esto.

### 🟢 2 · El módulo puro de la respuesta, y un error de razonamiento MÍO que arreglé por el camino

`parkingsCell.ts` decide la fusión, el `serve` y el `Cache-Control`; `index.ts` sólo hace I/O. **47
tests** en `functions/`, con las negativas que importan: una celda que falta **no es un error** (es
la respuesta correcta para casi todo el territorio), un parking que llega por dos celdas se cuenta
**una**, un puntero sin bandera **no apaga** el carril, y el orden es estable por id.

⛔ **Y el fallo de razonamiento, que es el hallazgo del ticket:** había puesto `s-maxage` de un día
para las celdas sin nada perecedero, razonando sólo el coste. Pero **el `s-maxage` es también el
suelo de cuánto tardas en REVERTIR cualquier cosa**: mientras el CDN tenga la respuesta cacheada la
sigue sirviendo **aunque la función se borre**, aunque el puntero diga `serve: false` y aunque el
`rewrite` desaparezca. Con 86.400 s, el interruptor de §D.3 **no era un interruptor**: tardaba hasta
un día.

Bajado a **3.600 s**. Cuesta ~24× invocaciones (del orden de 150-500 k/mes, calderilla) y compra
poder retirar algo **el mismo día**. Lleva su test, que además exige que el `max-age` del cliente no
sea mayor que el del CDN — una copia en el móvil que sobreviva a la del CDN es una marcha atrás aún
más lenta y sin forma de medirla.

### 🟢 3 · La trampa de las cabeceras, resuelta **en el emulador** y no desplegando

⚠️ `firebase.json` tiene una regla `**/!(*.*)` con `max-age=0, must-revalidate` para las páginas sin
extensión — y **`/api/parkings` no tiene extensión**. Si esa regla pisaba la cabecera de la función,
el CDN no cachearía nada y la función correría **una vez por usuario**: el coste que todo este
diseño evita, y sin un solo error visible.

No se podía razonar, así que se midió con `firebase emulators:start --only hosting,functions`:

```
HTTP/1.1 200 OK
cache-control: public, max-age=300, s-maxage=3600     ← la de la FUNCIÓN. La regla no la pisa
content-type: application/json; charset=utf-8
```

Y de paso, el endpoint entero funcionando por el `rewrite`, contra el snapshot real publicado:

| Petición | Resultado |
|---|---|
| `?cell=1499_-105` (Sol) | `200` · **927 parkings** · 95.188 B de JSON · `baseVersion 2026-W45` · `serve true` |
| `?cell=1520_-102` (rural) | `200` · 12 parkings |
| `?cell=01_2` · `?cell=notacell` · sin `cell` · `&v=2` | **400** los cuatro |

⛔ El `400` de `01_2` no es cosmética: es el parse **estricto** del nombre de celda. Dos ortografías
de la misma celda son dos entradas de CDN, y una tercera que no es ninguna no puede servirse como si
lo fuera.

### 🟢 4 · El cliente: UNA línea de DI, y Overpass intacto

`ApiPublicParkingsSource` entra **delante** de `OsmPublicParkingsSource`, que no se toca:

```
Caching( DiskBacked( delegate = Api( fallback = Osm(…) ), disk ) )
```

**7 tests**, y el grueso son las formas de perder, porque es lo único que garantiza que la 1.5.0
siga funcionando: 503, 404, 500 y **cuerpo ilegible** van a Overpass; `serve: false` también, y
**sin registrarse como error** porque es una orden, no un fallo; un backend viejo sin bandera **sigue
sirviendo**; el orden se mide **desde quien pregunta**, no desde quien contesta; un `kind` que esta
app no conoce cae a `UNKNOWN` en vez de reventar (el backend puede desplegarse antes que la app); y
`remembered` **se delega**, que si no el mapa volvería a salir vacío en frío.

### 🟢 5 · Y la obligación de ODbL que nace con este ticket

⛔ Mientras sólo **consultábamos** Overpass, bastaba con **atribuir**. Desde que la app **usa**
nuestra base derivada, ODbL pide además **avisar de que esa base está disponible bajo la misma
licencia** — y lo único que lo cumple de verdad es decir **dónde**. La fila de OpenStreetMap de
`LicensesScreen` gana su aviso con la URL, en **los 9 locales**, en su propia línea (apelotonarlo en
el subtítulo dejaba tres frases que se cortan justo donde empieza a importar).

### 🔴🟢 6 · DESPLEGADO — y el emulador me había dicho que todo iba bien

`firebase deploy --only functions:mergeCell,hosting`: función **creada** en `europe-west1`, hosting
con 12.649 ficheros.

⚠️ **Antes de lanzarlo, una comprobación que evitó un destrozo:** este worktree es un checkout
limpio, así que `hosting/public/parkings/v1/` estaba **vacío** (es salida de build, gitignorada) — y
un deploy de hosting **espeja** el directorio. Habría **borrado las 12.566 celdas publicadas** y
dejado la función leyendo 404. Regeneradas desde los extractos antes de desplegar. Es exactamente la
trampa que el ticket 1 dejó escrita, y casi la piso yo.

#### ⛔ Y entonces: la regla de cabeceras SÍ pisaba a la función, en producción

Lo que leí de `paparcar.com/api/parkings`:

```
Cache-Control: public, max-age=0, must-revalidate     ← la del HOSTING, no la de la función
X-Cache: MISS
```

⇒ **El CDN no cacheaba nada y la función habría corrido una vez POR USUARIO**, que es el coste que
toda esta arquitectura existe para evitar. Sin un solo error visible: respuesta 200, cuerpo correcto.

👉 **Y esto es una versión más afilada de la lección que el repo ya tenía**
[WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001]: no basta con no fiarse de la documentación,
**tampoco se puede fiar del emulador**. El emulador contestó con la cabecera de la función
(§3 de este mismo doc), así que me dio por resuelto justo lo que estaba roto. Lo único que cuenta es
la URL publicada.

**Causa, leída de la configuración:** la regla `**/!(*.*)` casa **cualquier** ruta cuyo último
segmento no lleve punto — está para que las páginas sin extensión de la web revaliden — y
`/api/parkings` no lleva punto. De las doce reglas, es la única que lo toca aparte de `**`.

**Arreglo: el endpoint pasa a `/api/parkings.json`.** Con extensión, la única regla que casa es `**`,
que no pone caché, y sobrevive el `Cache-Control` **por respuesta** — que es lo que sostiene el
modelo de coste de §D.5. Verificado en vivo:

| | |
|---|---|
| `Cache-Control` | **`public, max-age=300, s-maxage=3600`** ✅ la de la función |
| 1.ª petición | `X-Cache: MISS` · **0,36 s** |
| 2.ª y 3.ª | **`X-Cache: HIT`** · **0,061-0,075 s** |
| `/api/parkings` (la ruta vieja) | `404`, limpio |

⚠️ El `.json` en un endpoint de API queda raro y **no se quita**: su razón está medida y escrita en
`firebase.json` y en el `PATH` del cliente.

### 🟢 7 · Y Hosting NO comprime la respuesta de una función — 95 KB → 17,7 KB

Medido en la URL viva: la celda de Sol salía con **95.188 B**. Hosting **sí** comprime los ficheros
estáticos (una celda viaja a 1,6 KB) pero la salida de una función sale entera. En la pantalla que
el producto usa **conduciendo**, eso son ~5× los datos del usuario.

Así que comprime la función, con su `Vary: Accept-Encoding` (sin él el CDN guardaría UNA copia y se
la daría también a quien no sepa descomprimirla):

| | Bytes | Tiempo |
|---|---|---|
| con `Accept-Encoding: gzip` | **17.665 B** | 0,062 s en caliente |
| sin gzip | 95.188 B | — |

### 🟢 8 · El interruptor, que casi nació inservible

`serve` entró en el manifiesto de Kotlin como `Boolean = true`… y **kotlinx omite los valores por
defecto**, así que `latest.json` se publicaba **sin la bandera**. El lado que la lee interpreta
ausente como `true`, así que todo funcionaba — y el interruptor era inútil, porque **una bandera que
no está escrita es una bandera que nadie puede encontrar para cambiarla**. Con `encodeDefaults = true`
el puntero publicado ya dice `"serve":true`, y las celdas no cambian de tamaño (sus opcionales son
nulos, que se siguen omitiendo).

### 🟢 9 · El endpoint, en vivo, por toda España

| Celda | Dónde | Parkings |
|---|---|---|
| `1535_60` | Barcelona | **657** |
| `1499_-105` | Madrid (Sol) | 51 en su celda · **927** en el bloque |
| `1043_-505` | Las Palmas | **299** |
| `1486_-61` | Cuenca | 99 |
| `1520_-102` | rural | 12 |
| `01_2` · `v=2` · sin `cell` | — | **400** los tres |

Y el artefacto del ticket 1 sigue entero tras el espejo: puntero `2026-W45` con `serve:true`, celda
estática `200`, web `200`.

### ⏳ Lo que queda — y se cierra DICIÉNDOLO, no dándolo por hecho

⛔ **Los criterios 1 y 2 no están comprobados: nadie ha visto la app leyendo esto.** El endpoint está
verificado en vivo y el cliente tiene sus 7 tests, pero *«la app contesta sin tocar Overpass»* y
*«el fallback entra de verdad»* se ven en un móvil y en ningún otro sitio. El ticket se cierra con
esa deuda **escrita** —aquí y en `MEMORY.md`— en vez de con un criterio marcado a la ligera:

1. ⏳ **Device**: que una zona nueva conteste por `/api/parkings.json`, y **provocar el fallback**
   (apagar el `serve` del puntero, o cortar la red) para verlo entrar. Es `/run` con el móvil
   delante.
2. ⏳ Y de paso, lo que §D.2 dejó pendiente de medir: el **cold start** de la función tal y como lo
   vive la app, no como lo vive un `curl` desde este PC.

## Criterio de éxito

1. **El carril de parkings de la app contesta sin tocar Overpass**, verificado en device con el log
   de red: una zona nueva responde desde `/api/parkings`.
2. **El fallback funciona de verdad**, y se prueba provocándolo: con el endpoint devolviendo 503 (o
   `serve: false`), la lista **sigue saliendo** por Overpass. Un fallback que nadie ha visto fallar
   no es un fallback.
3. **El puerto de TypeScript coincide con la rejilla de Kotlin** en los 10 puntos de
   `grid-fixtures.json`, incluido el **orden** del bloque. Falsificable moviendo una constante.
4. **El conjunto que devuelve la API == el que devuelve Overpass** para el mismo punto, con el
   comparador del ticket 1 (que hoy da 916/916 contra el fichero).
5. **Cabeceras leídas de la URL viva**: `/api/parkings?cell=…` responde con su `Cache-Control` y
   **se cachea** (no `private`, no `Set-Cookie`).
6. `./gradlew testAll` + `npm --prefix functions test` verdes.

⛔ **Y lo que NO es criterio de éxito:** la ocupación en tiempo real (ticket 3) ni Parclick
(ticket 5). Este ticket entrega la tubería; el esquema ya los contempla.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `DataModule.kt:363` (la cadena) | ⏳ entra `ApiPublicParkingsSource` **delante** de `OsmPublicParkingsSource`, que no se toca |
| `OsmPublicParkingsSource` · `OverpassQuota` | ✅ **sin cambios**: pasa a ser el fallback y sigue vivo mientras la 1.5.0 esté publicada |
| `GetNearbyPublicParkingsUseCase` · `PublicParkingsSource` | ✅ **sin cambios**: el puerto es el mismo (§E.3) |
| `PublicParkingSnapshot.kt` (el DTO del fichero) | ⏳ lo **lee la función**, no el cliente; el cliente lee el DTO de la API |
| `tools/parkings/grid-fixtures.json` | ⏳ pasa a tener su **segundo** lector (TypeScript) — era su razón de existir |
| `firebase.json` | ⏳ primer `rewrite` del proyecto |
| `OVERPASS-A-QUEUE-OF-ONE-...-001` | ⚠️ al salir parkings del carril de Overpass, su `Foreground` principal pasa a ser **POI del sitio aparcado**. Su alcance ya está reescrito en su doc |
| `mockkit` / Dev Catalog | 🟢 **nada que hacer, corrigiendo lo que esta tabla decía.** El grafo mock **no lleva `dataModule`**: `FakePublicParkingsSource` sustituye el puerto ENTERO, así que ni la API ni el fallback existen ahí. Y más de fondo: este ticket **no añade un estado de UI** — la lista se ve **igual** venga de la API o de Overpass, que es justo lo que significa que el corte sea aditivo. Un fallback visible sería otro ticket |
| `firestore.rules` | ✅ sin cambios en este ticket (la ocupación es el 3) |
| `LicensesScreen` | ✅ **hecho**: la fila de OpenStreetMap gana su **aviso** con la URL de nuestra copia, en los 9 locales. ODbL pide dos cosas distintas y es fácil confundirlas — **atribuir** (ya estaba) y, al **usar** públicamente una base derivada, avisar de dónde está y bajo qué licencia (faltaba, y nace con este ticket) |
| Ficha de Play · web | ⛔ no se tocan: el usuario no ve una feature nueva, ve la misma lista más rápido |
