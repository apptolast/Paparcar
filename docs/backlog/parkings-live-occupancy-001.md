# PARKINGS-LIVE-OCCUPANCY-001 · Cuántas plazas quedan, ahora, dentro del parking

**Estado:** ✅ Done — **Madrid en producción y verificado en vivo el 03-11-2026** · rama `feature/PARKINGS-LIVE-OCCUPANCY-001-cities` · worktree
`../Paparcar-occupancy` — es la **F6b** de la ola 2 y el **ticket 3 de 6** del plan de
[`data-providers-001.md`](data-providers-001.md) §F.4. La segunda mitad de
[`plus-roadmap-001.md`](plus-roadmap-001.md) §F6. La base mundial (dónde están) la pone
[`parkings-on-the-map-001.md`](parkings-on-the-map-001.md).

## Qué añade, y qué NO puede venir de donde viene la base

⛔ **OpenStreetMap no tiene ocupación, ni la tendrá.** Es un mapa que dibuja gente, sin sensores
detrás. Lo único que ofrece es `capacity`, que es el **aforo TOTAL** y además escasea: medido el
09-09-2026 sobre los parkings que pasan el filtro de F6a, **2 de 22 en Madrid**, 6 de 32 en Málaga,
6 de 22 en París y **0 de 76 en Cuenca**.

Por eso el marcador de F6a no lleva cifra: la ranura donde iría el aforo total es la misma donde esta
fase escribirá **plazas libres**, y un hueco que dice dos cosas distintas en dos versiones enseña al
usuario a desconfiar de él.

## 🔴 Fuentes españolas — **MEDIDAS el 03-11-2026**, y la lista acordada se mueve

⛔ Lo que había aquí era del **09-09** y hoy no se sostiene. De las **cinco** ciudades de la
prioridad acordada (§E.2 del expediente: Madrid · Málaga · Zaragoza · Vigo · Pamplona), **dos
verificadas, una MUERTA y dos sin confirmar**:

| Ciudad | Estado | Medido hoy |
|---|---|---|
| **Madrid** | ✅ **VIVO, sin clave** | **SOAP** en `https://servayto.madrid.es/MTPAR_WSINFO/InfoParking`, op. `GetListParking`. **200 en 0,18 s**, 51 KB. 75 parkings, **24 con ocupación** |
| **Málaga** | ✅ **VIVO** | CSV `https://datosabiertos.malaga.eu/recursos/aparcamientos/ocupappublicosmun/ocupappublicosmun.csv` · **216 B en 0,16 s** · **12 filas** |
| **Pamplona** | ⛔ **MUERTO** | `http://www.pamplona.es/xml/parkings.xml` — la URL que **publica datos.gob.es** — devuelve **HTTP 200 con el cuerpo de un 404** |
| **Vigo** | ⚠️ **INALCANZABLE** | `datos-ckan.vigo.org` no contesta: `http=000` a los 20 s, timeout a los 60 |
| **Zaragoza** | ⚠️ **no localizado** | su API existe (devuelve un JSON de error estructurado) pero el recurso de ocupación **no está donde apuntaba este doc** |
| Valencia · Bilbao · Barcelona · Sevilla | ❌ | ubicación sí; la ocupación la llevan operadores privados (B:SM, Aussa) y no está en abierto |

⚠️ **Y las cifras que este doc daba estaban desfasadas**: decía ~22 en Madrid y ~32 en Málaga. Hoy
son **24 y 12**. Un adaptador dimensionado con las viejas habría esperado el triple de Málaga.

### Madrid, el detalle que decide su adaptador

- `GetListParking` es **público**: ni clave, ni registro, ni token. Contesta SOAP/XML.
- De 75 elementos, **24 traen `<lstOccupation>`** con `<free>` numérico **y su propio `<moment>`**.
- Los `moment` eran de **hace ~1 minuto** ⇒ el feed está de verdad vivo, no es un volcado diario.
- Libres **de 0 a 382**, 4.969 plazas libres en ese instante.
- 🟢 **`Jacinto Benavente: 0` — un CERO REAL**, que valida el escalón **«Completo»** de la
  escalera de §F.1 del expediente: un `0` no se pinta como «0 libres», que se lee como dato que
  falta.
- Otras operaciones del WSDL, por si sirven: `GetDetailParking`, `GetListFeatures`,
  `GetInfoParkingPoisForCoordinate`, `GetListStreetPoisParking`.

### 🔴 MÁLAGA SE QUEDA FUERA de este ticket — dos bloqueos medidos, no una sospecha

**1 · No existe un mapeo publicado de sus ids a una ubicación.** El CSV de ocupación trae 12 ids de
dos letras — `AL MA CA PA AN TE CE CY SJ PB SA CR` — y **ninguno aparece** en el único catálogo con
coordenadas que publica el portal (`da_aparcamientosRotacion-4326.csv`, 23 filas), que identifica por
un `ID_EQUIPAMIENTO` **numérico**. Y el dataset de ocupación tiene **un solo recurso**: el CSV.
[MEDIDO 03-11]

⛔ **Adivinar por iniciales** (`MA` → «Plaza de la Marina», `TE` → «Tejón y Rodríguez»…) **es
inventarse la fusión**, que es justo lo que §C.3 prohíbe: atribuiría plazas libres al parking
equivocado, y el error **parecería un dato**. No se hace.

⚠️ Y el catálogo de rotación tampoco sirve como fuente viva: lleva *«0 Plazas Libres»* **horneado
dentro de un tooltip HTML**, o sea un valor estático.

**2 · El portal declara su licencia de DOS formas que se contradicen.** En la misma ficha:
el título humano dice *«Creative Commons Atribución/Reconocimiento 4.0 … CC BY 4.0»* y el
`license_id` legible por máquina dice **`by-sa-40`** — con **share-alike**. [MEDIDO 03-11]

⇒ La pregunta de §C.4 del expediente («si meter el número en el mismo objeto que un registro
derivado de OSM cuenta como *adapted material*») **no se puede cerrar leyendo el portal**: el portal
no sabe qué licencia tiene. Entra en el correo que §C.4 ya dejaba pendiente.

👉 **Consecuencia de alcance, dicha y no escondida: este ticket entrega MADRID.** Son 24 parkings
con coordenadas propias y sello por parking — una feature completa y verificable. Málaga queda con
sus dos bloqueos escritos y vuelve cuando haya mapeo y licencia. ⛔ Y su adaptador **no se escribe a
medias**: un parser sin ubicación no se puede ni probar.

### ⛔ Dos consecuencias de diseño, y la primera CORRIGE una regla ya escrita

**1 · Málaga no puede decir CUÁNDO se midió su cifra.** Su CSV es `dato,id,libres` y **no trae
marca de tiempo**. La regla de §F.1 del expediente dice: *«la frescura se mide contra el `updatedAt`
de la FUENTE, no contra cuándo lo pedimos nosotros»*. Málaga **no la puede cumplir**.

La regla no se relaja: **gana una segunda cláusula.** Cuando la fuente no sella su dato, el sello es
**nuestra hora de lectura, y la respuesta tiene que decir cuál de las dos es** (`updatedAtIsFetch`).
Porque *«hace 3 minutos»* significa dos cosas distintas — «la ciudad lo midió hace 3 min» y
«lo preguntamos hace 3 min, y no sabemos de cuándo es» — y sin distinguirlas la cifra de Málaga se
presenta con una precisión que no tiene.

**2 · Málaga no trae coordenadas**, sólo un `id` de dos letras (`AL`, `MA`, `CA`, `PA`, `AN`…). Su
adaptador necesita **un segundo fichero** — el catálogo de ids — para situar los 12 parkings, y la
**fusión <40 m depende de ese segundo fichero**. ⇒ «un adaptador por ciudad» es en realidad **uno o
dos ficheros por ciudad**, y el que falta es justo el que coloca el dato en el mapa.

### 🔎 La pista que puede ahorrar N adaptadores — SIN verificar

La DGT publica en su **Punto de Acceso Nacional** un conjunto llamado *«Estado de ocupación de
parkings en Ayuntamientos»*: **DATEX II v3 por API REST**, licencia **CC BY 4.0**, registro opcional
(solo para avisos de cambios). Si de verdad agrega a varios ayuntamientos, sería **un adaptador para
todos** en lugar de uno por ciudad, y con un modelo de datos ya normalizado.

⚠️ **No está verificado y no debe darse por bueno:** ni el dataset ni su wiki técnica listan un solo
ayuntamiento participante, y la única URL expuesta es un entorno de **preproducción**
(`pre.cmobility30.es/use-case-11`). Antes de diseñar nada sobre esto: pedir el endpoint de producción
a `soporte@cmobility30.es` y **medir qué ciudades contestan de verdad**, exactamente como se midió
Overpass antes de escribir el parser de F6a.
- `nap.dgt.es/dataset/estado-de-ocupacion-de-parkings-en-ayuntamientos`
- `github.com/dgt30-esp/Caso-de-uso-11/wiki`

Fuera de España, el equivalente open source es **ParkAPI v3 / `parkapi-sources`** (decenas de
ciudades DE/CH/AT/DK ya normalizadas a DATEX II Light). Comprar el dato a TomTom o HERE es otra
historia y tiene su propia ficha: [`parking-availability-vendor-001.md`](parking-availability-vendor-001.md).

## Lo que el roadmap ya fija (no se rediscute aquí)

- ⛔ **«Es el primer backend propio» era FALSO y lleva meses escrito aquí.** `functions/` existe con
  **tres** funciones v2 desplegadas en `europe-west1` — las dos de fan-out y, desde el ticket 2,
  `mergeCell` —, Blaze está activo y el deploy está estrenado. Esta fase **hereda** infraestructura:
  lo único nuevo que trae es **Cloud Scheduler**.
  ⚠️ El MCP `firebase_deploy` sigue siendo un **no-op silencioso**: desplegar SIEMPRE por CLI.
- `scheduler` → adaptadores → Firestore `publicParkings/{source}_{id}` (merge). Lectura pública,
  escritura sólo la función.
  ⚠️ **La cadencia baja de 3 a 5 min** (§C.2 del expediente): a 3 min son 17.280 escrituras/día,
  el **86 %** de la capa gratuita de Firestore, que es **diaria** — cualquier parking nuevo que se
  adhiera la cruza. Y **se escribe sólo cuando el valor cambia**: un parking cerrado de noche no
  cambia.
  ⚠️ Y el cliente **ya no lee Firestore**: desde el ticket 2 la ocupación la fusiona `mergeCell` y
  llega en los campos `availability` que la app **ya sabe leer** — el cliente no se toca.
- El campo `source` es **procedencia, no confianza**: ⛔ ningún enum con `CONFIRMED` (colisiona con
  `Spot.confidence` y `SpotStatus.CONFIRMED`). Si hace falta el tipo se llama `AvailabilitySource` y
  el valor oficial es `OFFICIAL`.
- ⛔ **La rampa 🟢🟡🔴 es EXCLUSIVA de la caducidad de plazas** [UI-COLOR-DOCTRINE-001]: la ocupación
  necesita su propia expresión — cifra de libres escrita en el glifo, gris cuando solo hay estático,
  y «hace X min» en el peek.

## Cómo engancha con F6a — la junta, que es lo delicado

⛔ F6a **no montó registro por país a propósito**, porque esta fase no *sustituye* la base: la
**enriquece**. El feed de una ciudad trae la ocupación de los cuatro parkings que gestiona y nada
más, así que hay que fusionar por proximidad (<40 m) contra los de OSM sin duplicar el mismo parking
— y sin que un fallo de emparejamiento acabe pareciendo un dato en vez de un bug, que es la trampa
que ya mordió al cruzar catálogos de gasolineras.

---

## Diseño

### D.1 Dónde va cada cosa — y el cliente NO se toca

```
Cloud Scheduler (lo ÚNICO nuevo)  cada 5 min
   └─► ingestOccupancy()          solo I/O
         ├─ adapters/madrid.ts    SOAP GetListParking   → 24 medidas
         ├─ adapters/malaga.ts    CSV + catálogo de ids → 12 medidas
         └─► Firestore publicParkings/{source}_{id}     (merge, y SÓLO si cambia)

mergeCell()  ← ya existe (ticket 2)
   ├─ base OSM         ← fichero semanal del CDN
   └─ ocupación        ← Firestore, colección pequeña          ⬅ lo que añade esta fase
   └─ fusiona <40 m → `availability` en la respuesta que la app YA sabe leer
```

⛔ **El cliente no cambia ni una línea.** Es lo que el ticket 2 compró: el esquema de respuesta ya
declara `availability`, así que esta fase **añade una fuente, no rediseña un contrato**. Lo único
que cambiará en la app es la UI que pinta la cifra, y eso es el **ticket 4**.

⛔ **Y la decisión sigue fuera de la función**, como en `fanout.ts`: los parsers y la fusión viven en
módulos puros con sus tests y fixtures reales, porque *«ahí se pueden probar sin desplegar nada»*.

### D.2 ⛔ La fusión <40 m, y la regla que la hace honesta

Lo que fija el expediente (§C.3) y no se rediscute:

1. **Uno a uno por par más cercano**: un registro municipal reclama **como máximo un** parking de la
   base, y viceversa.
2. ⛔ **Ante la ambigüedad NO se fusiona.** Si hay un segundo candidato dentro del mismo radio, se
   dejan los dos y se marca. Un duplicado es **visible y evidente** para el usuario; una fusión
   equivocada **atribuye plazas libres a otro parking**, que es un hecho inventado.
3. **`mergedFrom` aparece sólo cuando ha habido fusión** — su presencia ES el testigo.

Y el testigo, **falsificado en las dos direcciones**: rojo cuando un par conocido se queda sin
fusionar, y rojo cuando dos instalaciones distintas se fusionan.

### D.3 Lo que cada fuente puede y no puede afirmar

| | Madrid | Málaga |
|---|---|---|
| Transporte | SOAP | CSV |
| Medidas | **24** | **12** |
| Coordenadas en el propio feed | ✅ | ⛔ **no** — hace falta el catálogo de ids |
| Sella su dato (`moment`) | ✅ por parking | ⛔ **no** — el sello es nuestra lectura |
| Aforo total | ✅ (`occupation`) | ⛔ no |
| Licencia | CC BY 4.0 | ⚠️ **CC BY-SA** — share-alike, §C.4 del expediente |

⛔ **El `updatedAtIsFetch` no es un detalle de implementación**: es lo que impide presentar la cifra
de Málaga con una precisión que no tiene. Ver la segunda cláusula de arriba.

⛔ **Y el share-alike de Málaga se aísla por ARTEFACTO**, no por buena intención: el snapshot
semanal publicado bajo ODbL **no contiene ni un valor de ocupación**, y la ocupación viaja en su
propio subobjeto con su propia `licence`.

## Criterio de éxito

1. **Los dos parsers, contra fixtures REALES** (la respuesta SOAP de Madrid y el CSV de Málaga
   capturados hoy), no contra un ejemplo inventado.
2. **El testigo de la fusión en rojo en las dos direcciones** (par perdido · par inventado).
3. **Se escribe sólo cuando el valor cambia**, demostrado por un test: dos ciclos con el mismo dato
   = una escritura.
4. **La regla de Firestore**: lectura abierta de `publicParkings`, escritura **sólo la función**,
   dentro de la suite de reglas ejecutadas que ya existe.
5. **Una celda de Madrid con `availability` en la respuesta de `/api/parkings.json`**, leída de la
   URL viva — y su `Cache-Control` bajando a **120 s** por llevar algo perecedero (§D.5 del ticket 2).
6. `./gradlew testAll` + `npm --prefix functions test` verdes.

⛔ **Lo que NO entra:** la UI que pinta la cifra (ticket 4), Zaragoza/Vigo/Pamplona hasta que sus
feeds contesten, y el NAP nacional (ticket 0, un correo sin enviar).

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `mergeCell` / `parkingsCell.ts` | ⏳ `responseFor` empieza a recibir ocupación; `hasPerishable` ya existe y baja el TTL a 120 s **sin tocarse** |
| El cliente (`ApiPublicParkingsSource`, DTO) | ✅ **sin cambios**: `availability` ya está declarado (ticket 2) |
| `firestore.rules` + suite de 30 tests | ⏳ regla nueva de `publicParkings` |
| `firebase.json` | ⏳ nada: el `rewrite` ya está |
| Cloud Scheduler | ⏳ **lo único nuevo** de infraestructura en toda la ola |
| `LicensesScreen` | ⏳ una fila por fuente municipal con su licencia (Madrid CC BY 4.0 · Málaga CC BY-SA) |
| UI (fila, marcador, peek) | ⛔ **ticket 4**, no aquí. La escalera de §F.1 del expediente ya está decidida |
| `docs/backlog/README.md` | ⏳ recuento al cerrar |

---

## 🟢 VERIFICADO EN VIVO (03-11-2026) — y dos bugs que sólo se ven en producción

`paparcar.com/api/parkings.json?cell=1499_-105`, la celda de Sol:

```
Cache-Control: public, max-age=60, s-maxage=120, stale-while-revalidate=60   ← BAJÓ SOLO
Content-Encoding: gzip
licences: ['odbl-1.0', 'cc-by-4.0']                                          ← Madrid, sólo ahora
```

| Parking | Libres | Medido por la fuente | `total` | `updatedAtIsFetch` |
|---|---|---|---|---|
| Aparcamiento Plaza del Carmen | 153 | 15:11:15 | — | ausente |
| Nuestra Señora del Recuerdo | 160 | 15:08:49 | — | ausente |
| Aparcamiento Recoletos | 67 | 15:08:54 | — | ausente |
| Parking Barceló | 79 | 15:10:02 | — | ausente |
| **Parking Benavente** | **0** | 15:09:32 | — | ausente |
| … y 3 más | | | | |

**8 de las 24 medidas de Madrid caen en esta celda**, con su `mergedFrom` completo
(`['osm/node/1237726255', 'madrid_94']`): las dos mitades del emparejamiento, auditables.

🟢 **Y tres cosas que no hubo que tocar, porque estaban diseñadas para esto:**
- **El TTL bajó solo a 120 s** — `hasPerishable` es del ticket 2 y se activó al llegar un dato
  perecedero. El TTL es propiedad de la RESPUESTA, no del endpoint.
- **Madrid se acredita sólo ahora** que se sirve su dato: una licencia citada sin usar su dato es
  ruido y sugiere una cobertura que no hay.
- **El cliente no se tocó**: `availability` ya estaba declarado.
- `total` ausente y `updatedAtIsFetch` ausente son **las dos verdades** de Madrid: no publica aforo y
  sí sella su dato.

### 🔴 Los dos bugs, y los dos eran MUDOS

**1 · Firestore rechaza el documento ENTERO si una clave vale `undefined`.** `total: undefined`
tumbó **todos** los ciclos del scheduler desde las 11:09 hasta las 15:0x — horas — con *«Cannot use
"undefined" as a Firestore value»*. ⛔ Y los tests unitarios lo dejaban pasar **porque no tocan
Firestore**: el único síntoma visible era que la cifra **nunca aparecía**. La función registraba su
error y nadie lee logs.
⇒ Testigo nuevo: recorre lo que se guarda y exige que no viaje **ni un** `undefined`. Falsificado.

**2 · Estaba leyendo un campo que NO EXISTE.** Dentro de `<lstOccupation>` Madrid publica `code`,
`free`, `moment`, `name` y `renewalIndex`. `occupation` es el nombre del elemento **de fuera**, así
que `total` salía `undefined` **las 24 veces**.
⇒ **Madrid no publica el aforo** en `GetListParking`, y no se inventa. Si alguna vez hace falta
vendrá de `GetDetailParking` o del `capacity` de OSM (3,2 % de cobertura).
⚠️ **Consecuencia para el ticket 4**: «42 libres» **no podrá decir «de 587»**.

### 🔴🟢 Y un incidente de producción por el camino: el snapshot BORRADO

Durante este ticket, `paparcar.com/parkings/v1/latest.json` y las **12.566 celdas** estuvieron en
**404**, y `mergeCell` devolviendo **503** a cada petición. Causa: un `firebase deploy --only hosting`
desde un worktree **sin** las celdas generadas — Hosting **espeja** el directorio y las celdas están
en `.gitignore` porque son salida de build.

🟢 Lo tapó el diseño aditivo: la app cae a Overpass sola, así que ningún usuario habría visto un
mapa vacío. ⛔ Pero **estaba escrito en dos docs y una memoria y se repitió igual**, así que ahora
hay guard: `tools/parkings/guard_before_hosting_deploy.py` entra como **`predeploy` de hosting** y
**aborta** si producción sirve un puntero y en local no hay ninguno. Falsificado: el deploy se niega.
Tiene escape explícito (`PAPARCAR_ALLOW_SNAPSHOT_DELETE=1`) y un fallo de red **no** aborta nada —
el guard existe para evitar un borrado, no para ser un segundo punto de fallo.

**Una trampa que se evita recordándola no está evitada.**

## 📋 Pendientes de la ola entera (al 03-11-2026)

> Apuntado aquí porque es el doc de la ola que llega a master antes. ⚠️ **El plan completo
> (`data-providers-001.md`, fases A-F con todas las decisiones) sigue SIN COMMITEAR** en el worktree
> `../Paparcar-dataproviders`: si ese worktree se pierde, se pierde el expediente.

### Los seis tickets

| # | Ticket | Estado | Qué falta, y de quién es |
|---|---|---|---|
| **0** | Los correos | 🔴 **sin enviar** | `soporte@cmobility30.es` (NAP nacional) — **es el multiplicador**: con 1 ciudad viva de 5, es la única vía por la que la ocupación escala · `tech-support@parclick.com` · y uno nuevo: **Málaga**, por su licencia contradictoria y el mapeo de ids. **USER** |
| **1** | `OSM-PRECOOKED-SNAPSHOT-001` | ✅ en master | ⏳ el snapshot está **congelado en `2026-W45`**: el workflow semanal **genera** solo pero **no publica** — falta el secreto `FIREBASE_SERVICE_ACCOUNT`. **USER** |
| **2** | `PARKINGS-ONE-CALL-MERGED-001` | ✅ en master | ⏳⏳ **nadie lo ha visto en un móvil**: los criterios *«la app contesta sin tocar Overpass»* y *«el fallback entra»* se ven en device y en ningún otro sitio · ⏳ el **cold start** de la función tal como lo vive la app, sin medir |
| **3** | `PARKINGS-LIVE-OCCUPANCY-001` | 🔵 **en curso** | entrega **Madrid** (24). Falta: ingesta `onSchedule`, regla de `publicParkings`, y la cifra saliendo por `/api/parkings.json` |
| **4** | La UI de la cifra (la escalera de §F.1) | 🔵 decidida, sin escribir | depende del 3 · y **encaja con el rediseño de la home**, que es la autoridad de diseño de esa pantalla |
| **5** | `PARCLICK-OFFER-BEHIND-A-FLAG-001` | 🔴 **bloqueado** | no se empieza sin respuesta por escrito. ⛔ Y **no sustituye a la ocupación**: no publica plazas libres ni aforo |

### Las ciudades, y por qué cada una no está

| Ciudad | Bloqueo | Quién lo desbloquea |
|---|---|---|
| **Madrid** | — | ✅ entra en este ticket |
| **Málaga** | sin mapeo id→ubicación publicado · licencia declarada de dos formas (`CC BY 4.0` vs `by-sa-40`) | un correo al portal |
| **Zaragoza** | su API responde, el recurso de ocupación no está donde decía el doc | buscarlo en su catálogo |
| **Vigo** | `datos-ckan.vigo.org` no contesta | reintentar otro día |
| **Pamplona** | la URL que publica datos.gob.es devuelve 200 con cuerpo de 404 | ver si el feed se mudó |

### Deuda de alrededor, tocada por esta ola

- ⚠️ **`data-providers-001.md` sin commitear** — todo el expediente (A-F) vive en un worktree suelto.
- ⚠️ **`MEMORY.md` en 25,4 KB** sobre un techo declarado de ~17 KB; ya se truncó una vez por esto.
  Necesita un barrido de archivado, que es tarea aparte.
- 🟢 **`OVERPASS-A-QUEUE-OF-ONE-...-001`**: desbloqueado y con su alcance reescrito, **sin abrir**.
  Es prerrequisito de una tarea del rediseño de la home.
- ⚠️ **Super-celdas** cuando entre el segundo país: un fichero por celda no escala a un continente
  (España sola son 12.566).
- ⚠️ El **Reglamento (UE) 2024/490** obliga a publicar ocupación y tarifas en el NAP con plazo
  **1-12-2026** para la red principal — dentro de un mes. Juega a favor de esperar al NAP antes de
  escribir N adaptadores municipales.
