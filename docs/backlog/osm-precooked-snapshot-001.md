# OSM-PRECOOKED-SNAPSHOT-001 · la base de parkings se pre-cocina una vez, no se pregunta por usuario

**Estado:** ✅ Done — **publicado y verificado en vivo el 02-11-2026** · rama `feature/OSM-PRECOOKED-SNAPSHOT-001-cdn-base` ·
worktree `../Paparcar-osmsnapshot`
**Sale de:** [`data-providers-001.md`](data-providers-001.md) §B.1 (recomendación), §C.6 (coste
medido) y **§F.4** (es el **ticket 1**: el primero con código, y el que tiene que ir **antes** del
endpoint fusionado porque si no, no habría base que fusionar).

> Este ticket entrega **el artefacto y su licencia**, no el cambio de cliente. Quien conmuta la app
> a leerlo es `PARKINGS-ONE-CALL-MERGED-001` (ticket 2). Partido así a propósito: el artefacto se
> puede medir, publicar y verificar sin tocar una sola pantalla.

## Problema

El carril de parkings que el usuario mira **pregunta a Overpass en vivo, en cada zona nueva**, y lo
paga en la cara:

| Medición | Valor | Dónde se midió |
|---|---|---|
| Madrid, espejo caliente | 1,4 s | KDoc de `OverpassWalkPatience` |
| Madrid, en frío | **4,68 s** | ídem |
| Málaga, por fibra | **7,72 s** | ídem |
| Barcelona | **504 a 8,06 s** | ídem |
| Berlín | **429 a 9,48 s** | ídem |
| seis consultas seguidas | dos respuestas y **cuatro 429** de 7 a 8,5 s | KDoc de `OverpassQuota` |
| cuatro a la vez | un 429 y **tres 504 a 14,1 s** | ídem |
| techo del paseo de espejos | **30 s** (`WALK_CEILING_MS`) | ídem |
| contar los parkings de España | **1 m 52 s** | `data-providers-001.md` §C.6 |

Y no es sólo latencia: la cola propia es de **uno** (`CONCURRENT_ASKS = 1`) porque el límite por IP
es 2 y la IP se comparte, y la documentación de Overpass **pide montar instancia propia** a quien
tenga demanda sostenida. O sea que el camino de hoy no escala con los usuarios: empeora con ellos.

⛔ **Y el dato no lo justifica.** Un `amenity=parking` cambia en **semanas o meses**. Estamos pagando
un coste de frescura por minuto para un dato que no se mueve: el problema real es de
**disponibilidad y cupo**, y eso lo resuelve un snapshot, no un proxy ([`data-providers-001.md`](data-providers-001.md) §B.1).

## Doctrina violada

Ninguna regla escrita del proyecto — y conviene decirlo en vez de inventar una. Lo que hay es una
**dependencia mal dimensionada**. Lo que sí manda sobre la solución son tres reglas que ya existen:

1. ⛔ **ODbL no es un pie de página.** Consultar Overpass en vivo es *uso*; **publicar un extracto
   filtrado es una base de datos derivada**, y obliga a ofrecerla bajo ODbL además de atribuir. La
   app ya acredita OpenStreetMap con ODbL 1.0 en `LicensesScreen`, así que lo que falta es
   **publicar el fichero y su licencia**, no renegociar nada.
2. ⛔ **Sistemas, no parches**: el filtro de qué es un parking usable **ya existe** y es doctrina
   medida (`OsmPublicParkingParser`). Este ticket **no lo reimplementa**; ver §Diseño.
3. ⛔ **Todo corte es aditivo** (`data-providers-001.md` §D.4): 1.5.0 (vc16) está publicada y lee
   Overpass. El carril de Overpass **no se retira** del cliente mientras haya versiones publicadas
   que lo usen.

## Señales / datos disponibles

- **120.906 `amenity=parking` en España** [MEDIDO 30-10 contra `overpass-api.de`]: 10.140 nodes,
  **108.930 ways**, 1.836 relations. ⬅ el 90 % son **polígonos**, así que el generador **tiene que
  calcular centroides**; no es un volcado de nodos.
- `fee=no` 30.216 (25 %) · `fee=yes` 4.682 (3,9 %) · **sin `fee` 85.894 (71 %)** [MEDIDO].
- Hosting: **10 GB** de almacén y **10 GB/mes** de transferencia gratis [VERIFICADO]. España entera
  en crudo ~18 MB, gzip ~5-6 MB [INFERENCIA de §C.6] ⇒ **0,06 % del almacén**.
- ⚠️ **Esas 18 MB están calculadas sobre los 120.906 SIN FILTRAR, y el filtro se come la mayoría**
  [MEDIDO en el KDoc de `OsmPublicParkingParser`]: `parking=street_side|lane` (el bordillo, no una
  instalación) eran **256 de 300** elementos en el centro de Málaga y 35 de 70 en Châtelet, y
  `access` cerrado otros **86 de 223** en Berlín. ⇒ El snapshot real será **una fracción** de esa
  cifra. Medirlo es la primera tarea.
- ⛔ El CI **está vivo** (verificado 02-11 con `gh run list`: verdes 27-10 y 29-10). Este doc nació
  diciendo lo contrario, copiado de una memoria desfasada — ver D.5.
- Herramienta local [MEDIDO hoy]: `python 3.12.10`, `node 24.16`, `java 21`, `gzip`, `curl`.
  ⛔ **No hay `osmium` ni `pyosmium`** — este ticket introduce esa dependencia de build.

## Diseño

### D.1 ⛔ UN solo filtro, y es el de Kotlin — el generador lo EJECUTA, no lo reescribe

`OsmPublicParkingParser` no es un parser: es **la definición de qué es un parking para Paparcar**, y
cada línea está medida — la exclusión del bordillo, los ocho valores de `access` cerrado, la
excepción de `access=customers` (los tres de Sol son `fee=yes`, y uno lo explota el Ayuntamiento), el
mapeo de `kind`, los dos únicos valores de `fee` que afirman algo, y el `maxheight` que **deja null
lo imperial a propósito**.

⛔ **Portarlo a Python es el error de este ticket**, y el fallo sería mudo: el snapshot enseñaría un
conjunto distinto del que la app enseña hoy, y nadie lo vería hasta que un usuario mirara un
bordillo dibujado como parking. Así que el orden es al revés de lo obvio:

```
 Geofabrik .osm.pbf
   │
   ├─ (1) pyosmium · SOLO geometría y plumbing ───────────────┐
   │       tag-filter amenity=parking                          │  nada de doctrina:
   │       centroide de way y relation                         │  ni access, ni fee,
   │       emite JSON con la FORMA de Overpass                 │  ni street_side
   │       {elements:[{type,id,lat,lon,center,tags}]}          │
   │                                                           │
   ├─ (2) OsmPublicParkingParser.parse()  ⬅ EL MISMO de la app ┘
   │       filtro + modelo + id `osm/<type>/<id>`
   │
   ├─ (3) reparto en celdas por ÍNDICE ENTERO (ParkingZoneGrid)
   ├─ (4) gzip
   └─ (5) firebase deploy --only hosting  +  LICENSE.txt (ODbL 1.0)
```

⚠️ **El paso (2) necesita correr Kotlin fuera de un device, y `:shared` no tiene target `jvm`**
[MEDIDO: `androidTarget()` + 2 targets iOS]. Dos caminos, y se decide **probando**, no razonando:

| Opción | Cómo | Riesgo |
|---|---|---|
| 🟢 **A · `JavaExec` sobre el classpath de `androidDebugUnitTest`** | el runtime de los tests de `:shared` ya es una JVM con `commonMain` dentro; una tarea Gradle lanza un `main()` que llama al parser | que arrastre dependencias de Android en tiempo de ejecución — el parser sólo usa `kotlinx.serialization`, así que debería salir limpio |
| 🟡 B · añadir target `jvm` a `:shared` | lo ortodoxo | hay que resolver Room/KSP, ktor y GitLive para `jvm`; es un cambio de build grande para una tarea de generación |
| 🔴 C · portar el filtro a Python con test de paridad por fixtures | lo barato hoy | **dos implementaciones de la doctrina**; el test de paridad sólo cubre lo que el fixture contenga |

**Se intenta A primero.** Si A no sale, C **no** es el fallback: lo es B, porque la regla del
proyecto es que el invariante vive en un sitio.

### D.2 ⛔ Las celdas PARTEN el territorio — no se solapan, y el alcance se cubre leyendo el vecindario

Trampa que §F.3 no nombró y que decide el tamaño del artefacto. Una zona **alcanza 5.121 m** desde su
centro (`ZONE_RADIUS_METERS = 3.000 + 3.000·√2/2`) para que alguien en una esquina siga viendo 3 km a
su alrededor. Si cada fichero de celda tuviera que contener **todo lo que su zona alcanza**, cada
parking caería en ~21 celdas:

| Forma | Almacén | Veredicto |
|---|---|---|
| celda = lo que la zona ALCANZA (5.121 m) | ~18 MB × ~21 ≈ **380 MB** | ❌ y además cada parking se publica 21 veces |
| **celda = lo que la celda CONTIENE** (3 × 3 km) | **~18 MB**, sin duplicar un byte | 🟢 |

Con celdas que parten, el alcance se cubre **leyendo el anillo de alrededor**, y eso lo hace el
backend —no el cliente—, así que **sigue habiendo UNA sola llamada** (§E.3 intacto).

⛔ **Y el bloque NO es un cuadrado de anillos. La primera versión lo era, y PERDÍA PARKINGS.**
[MEDIDO el 30-10, y falsificado antes de creérselo]

La forma obvia es `r = ceil(DEFAULT_RADIUS / CELL)` anillos alrededor de la columna del centro — hoy
1, o sea un 3 × 3. Se implementó así, y el test de cobertura la tumbó: **desde la esquina suroeste de
la celda de Madrid, un punto a 3 km rumbo 255° cae en una columna que el 3 × 3 no contiene.** Dos
causas, las dos estructurales:

| Causa | Por qué rompe el cuadrado |
|---|---|
| **El ancho de una columna se deriva de la latitud del centro de SU banda** | las rejillas de dos bandas vecinas están **desalineadas**: `Zone(banda+1, col)` no es la celda justo al norte de `Zone(banda, col)`, y el centro de la celda preguntada puede caer hasta **media celda** descentrado en la columna de una banda vecina. Los anillos contados desde ahí se quedan cortos por ese desfase |
| **Un grado de longitud vale menos metros cuanto más lejos del ecuador** | el alcance en **grados** es mayor en el borde del bloque que en su centro. Contar en celdas de la banda del centro lo subestima, y el error crece con la latitud: **12 de 5.760 puntos de muestra fuera en Svalbard** contra 4 en Madrid |

Forma correcta, y es la que está en el código: el bloque se deriva de la **caja geográfica** que la
respuesta tiene que cubrir —`BLOCK_REACH_METERS = CELL/2 + DEFAULT_RADIUS` = 4.500 m—, con el delta
de longitud tomado en la **peor latitud de la caja** y las columnas resueltas **banda a banda**.

| | Anillos (3 × 3) | Caja geográfica |
|---|---|---|
| Puntos de muestra fuera del bloque (9 ciudades × 4 esquinas × 1.440 rumbos) | **35** | **0** |
| Celdas que hay que leer | 9 | **13 a 21** |

⛔ Es deliberadamente **generoso** en los bordes (`floor` en los dos extremos, así que un límite que
se toca en una sola línea también se lleva su celda): leer un fichero de más es barato — la mediana
de una celda son **224 B** [MEDIDO] — y un bloque con un agujero se ve exactamente como *«aquí no hay
parkings»*.

### D.3 La celda es un PAR DE ENTEROS — y la rejilla **no ha tenido que salir de Kotlin**

Decidido en `data-providers-001.md` §F.3/C.1: `cell = <band>_<col>`, los índices que
`ParkingZoneGrid.zoneOf()` ya calculaba con `floor()` y **tiraba**. Motivo corto: un `Double` en el
nombre del fichero o en la URL **no se escribe igual en Kotlin/JVM y en Kotlin/Native**, y dos
ortografías de la misma zona son dos entradas de CDN — la mitad de aciertos, sin un solo error
visible. `Zone` pasa a estar identificada por los dos enteros y el centro se **deriva** de ellos.

🟢 **Y aquí se cae una preocupación que este doc traía escrita:** se dio por hecho que la rejilla
tendría que vivir «en Kotlin y en Python», con tabla de fixtures en los dos lados. **No hace falta:**
el reparto en celdas lo hace el **tool de Kotlin** (`--snapshot` agrupa por `zoneOf`), y el script de
pyosmium **no toca la rejilla** — hace geometría y se va. Un invariante en un solo idioma.

⚠️ **Donde sí volverá a aparecer es en el ticket 2**, porque el backend en TypeScript necesita
`blockAround` para leer el vecindario de la celda que le piden. Así que este ticket publica la
munición para entonces: **`tools/parkings/grid-fixtures.json`**, generado desde la rejilla viva y
vigilado por un test que se pone rojo si alguien cambia una constante y no lo regenera. El día que el
TS lo necesite, tiene contra qué comprobarse sin inventarse nada.

### D.4 Qué se publica, y bajo qué licencia

```
hosting/public/parkings/v1/2026-W44/<band>_<col>.json.gz     ← las celdas
hosting/public/parkings/v1/2026-W44/index.json               ← qué celdas existen y cuántas filas
hosting/public/parkings/v1/latest.json                       ← puntero a la semana vigente
hosting/public/parkings/LICENSE.txt                          ← ODbL 1.0 + atribución
```

- **Versionado por semana ISO y un puntero**: un snapshot publicado es **inmutable**, así que el CDN
  lo puede cachear para siempre y una regeneración no invalida nada — sólo mueve el puntero. ⛔ Lo
  contrario (sobrescribir la celda) es cómo un CDN sirve mitad de un snapshot y mitad del otro.
- ⛔ **La publicación ODbL es parte de la definición de hecho de este ticket**, no un seguimiento:
  si el fichero está servido y la licencia no, hemos redistribuido una base derivada sin su licencia.
  `LICENSE.txt` va **al lado** del artefacto y dice las tres cosas que ODbL pide: que es una base
  derivada de OpenStreetMap, que se ofrece bajo ODbL 1.0, y **qué se cambió respecto al origen** —
  el filtro entero, escrito en prosa, porque un receptor tiene derecho a saber qué se seleccionó.
- 🟢 **Y `LicensesScreen` NO se toca en este ticket, corrigiendo lo que este doc decía.** Se
  comprobó la fila: la app **ya** acredita *OpenStreetMap · ODbL 1.0* con su propósito, y el fichero
  lleva escrita su propia regla (*«un dataset que entra en la app entra AQUÍ en la misma tarea»*).
  Pero la obligación de ODbL de dar aviso nace al **usar públicamente** la base derivada, y aquí
  sólo se **publica** — con su licencia pegada al lado, que es como se licencia un dataset
  publicado. Quien empieza a usarla es el ticket 2, y es ahí donde la frase que cambia es *de dónde
  vienen los parkings*. Queda en su tabla de consumidores, no en un hueco.

### D.5 Cómo se regenera

El job es **un script**, no una función (⛔ 1,4 GB y minutos de CPU contra los límites de una Cloud
Function es pelearse con la herramienta — §C.3 del expediente). Así que:

1. **A mano la primera vez**, desde este worktree, con el comando escrito en el README del tool.
2. **GitHub Actions semanal**, que entra en este ticket escrito y commiteado.

⛔ **Y aquí este doc decía una cosa falsa: «el CI está muerto por facturación».** Comprobado el
02-11 con `gh run list`: **el CI arranca y pasa** — runs verdes el **27-10** (20 m 46 s) y el
**29-10** (7 m 57 s). La memoria del proyecto lo daba por muerto desde el 13-09 y se arrastró aquí
sin verificarlo. Memoria corregida.

⚠️ **Lo que SÍ falta para que el workflow publique solo, y es otra cosa:** `gh secret list` da siete
secretos y **ninguno es `FIREBASE_SERVICE_ACCOUNT`** [MEDIDO 02-11]. Sin él el paso de deploy no
puede autenticarse — y falla **ruidosamente**, porque el propio workflow lo comprueba antes
(`::error::falta el secreto FIREBASE_SERVICE_ACCOUNT`). Crearlo es del user. Hasta entonces: la
**generación** semanal sí correría sola; la **publicación** es a mano.

⚠️ Y arranca **por una provincia, no por España**: Geofabrik publica extractos por comunidad, así
que la primera medición se hace sobre Madrid (decenas de MB) y sólo cuando el pipeline esté probado
se baja el extracto del país.

## Progreso medido (30-10-2026)

### 🟢 1 · La bifurcación de D.1 está RESUELTA: **opción A funciona**

`./gradlew :shared:parkingSnapshotTool --args="--probe"` ejecuta `OsmPublicParkingParser` —código de
`commonMain`— en una JVM de escritorio, y con la doctrina viva: a una carga de **tres** elementos
(un subterráneo de pago, un bordillo `parking=street_side` y uno `access=private`) le deja **uno**.
⇒ ⛔ **No hay que portar el filtro a Python.** Opciones B y C descartadas sin gastarlas.

**Dos trampas de cableado, las dos medidas, las dos con su comentario en el build:**

| Forma | Qué pasa |
|---|---|
| `unitTest.runtimeDependencyFiles` a pelo | ❌ **falla por ambigüedad**: Gradle elige entre las variantes de `debugRuntimeElements` (`jar`, `android-symbol-with-package-name`, `supported-locale-list`) sin un `artifactType` que desempate |
| `tasks.named<Test>("testDebugUnitTest").map { … }` | 🟡 resuelve, pero un provider derivado de un task **arrastra su dependencia** ⇒ generar el snapshot **corría la suite entera** (y en Windows la suite tiene un fallo intermitente de DLL que no tiene nada que ver con esto) |
| 🟢 `artifactView` con `artifactType = "jar"` | ✅ lo que hace AGP por dentro. El tool corre en **748 ms** sin tocar un test |

### 🟢 2 · El lado de pyosmium funciona, y es rápido

`tools/parkings/osm_extract.py`, probado sobre el extracto de Geofabrik de **Madrid** (no España
todavía, por D.5):

| Paso | Medición |
|---|---|
| descarga de `madrid-latest.osm.pbf` | **85,5 MB en 3,1 s** |
| barrido del PBF buscando `amenity=parking` | **0,2 s** |
| extracción completa con geometría (2 pasadas) | **14,6 s** |
| elementos extraídos | **14.646** — node 1.072 · way 13.506 · relation 68 |
| JSON intermedio (todos los tags) | **2,63 MB** |

- ⛔ **El 92 % son `way`**: confirma que esto no es un volcado de nodos y que el centroide es el
  trabajo de verdad. Se usa el **centro de la bounding box**, que es lo que devuelve `out center` de
  Overpass — un centroide de polígono movería los pines respecto de lo que ya está publicado.
- ⚠️ **2 relations de 70 se caen** por no tener geometría recuperable en el extracto. Documentado
  antes que silenciado; a escala de país hay que volver a mirarlo.
- Dos trampas de la herramienta, con su comentario en el script: restringir la 2.ª pasada a `WAY`
  **impide** `with_locations()` (*"Nodes not read from file"*), y una flecha Unicode en `stdout`
  **mata el script** en la consola cp1252 de Windows **después** de haber escrito el fichero.

### 🟢 3 · ⛔ El filtro se come **más de la mitad**, y eso corrige la estimación de §C.6

`--count` aplica el filtro real al extracto de Madrid:

```
entran=14.646  salen=6.654  (45,4 %)
  por kind: SURFACE 3.766 · UNKNOWN 2.544 · UNDERGROUND 285 · MULTI_STOREY 53 · GARAGE_BOXES 6
  por fee:  UNKNOWN 4.896 · FREE 1.438 · PAID 320
  con name=370  capacity=215  maxheight=13  opening_hours=76
```

| | Cifra | Marca |
|---|---|---|
| Rendimiento del filtro | **45,4 %** | [MEDIDO] |
| España, estimación anterior (sobre 120.906 **sin filtrar**) | ~18 MB crudo · ~5-6 MB gzip | [INFERENCIA de §C.6] |
| España, **corregida** (120.906 × 45,4 % ≈ **54.900** registros) | **~8 MB crudo · ~2-2,5 MB gzip** | [INFERENCIA sobre cifra medida] |

⇒ El artefacto es **la mitad de lo que el expediente presupuestó**, y el 0,02 % de los 10 GB
gratuitos de Hosting. El veredicto de §C.6 (0 €/mes) se refuerza.

⛔ **Y la cobertura de campos, medida por primera vez sobre una REGIÓN entera** (lo anterior eran
muestras de 1 km) confirma la doctrina en vez de matizarla — y la endurece:

| Campo | En el centro de Madrid (muestra de 1 km, 09-09) | En la región de Madrid (6.654 registros, hoy) |
|---|---|---|
| `name` | 72 % | **5,6 %** (370) |
| `capacity` | 9 % (2 de 22) | **3,2 %** (215) |
| `maxheight` | 0-7 % | **0,2 %** (13) |
| `opening_hours` | 0-3 % | **1,1 %** (76) |

👉 La fila titulada por su `kind` no es un caso de borde para pueblos: **es el 94 % de la región de
la capital**. Y el `trailing` vacío de `FeeInfo.UNKNOWN` es el **73,6 %** aquí, no el 71 %.

### 🟢 4 · La rejilla ya nombra sus celdas, y el bloque tiene su testigo

- `ParkingZoneGrid.Zone` pasa a ser **dos enteros con el centro derivado**, y gana `id`
  (`<band>_<col>`) y `zoneOf(id)` para volver. El parse es **estricto a propósito** — lee un nombre
  de fichero y una clave de CDN, así que lo que no se puede justificar es un fallo y no una
  suposición: un parse indulgente sirve los parkings de una celda bajo el nombre de otra y nada
  aguas abajo podría notarlo.
- `BLOCK_REACH_METERS` + `blockAround()` con la caja geográfica de D.2.
- **Testigo de cobertura**: 9 puntos del mundo × 4 esquinas × 1.440 rumbos = **51.840
  comprobaciones**, cero fuera. Falsificado en las dos direcciones (sin la media celda, y contando
  columnas en la banda del centro: las dos se ponen rojas).
- ⚠️ Y un fallo del propio test, de los que enseñan algo: los helpers `probeLonStep`/`probeLatStep`
  **caminan a saltos de 8,5 m** y sobreestiman el paso, así que `0,49999 × paso` caía FUERA de la
  celda y el test se acusaba a sí mismo. Con los índices expuestos, el paso es **la resta de dos
  centros contiguos** y no hay nada que estimar.

### 🟢 5 · El snapshot de la región de Madrid, escrito y medido

`--snapshot` agrupa por celda, serializa, comprime y escribe el manifiesto y el puntero:

| | Medición |
|---|---|
| Celdas con datos | **463** |
| Parkings publicados | **6.654** |
| **Toda la región de Madrid, gzip** | **177 KB** |
| Tiempo | **0,4 s** |
| Celda **mediana** | **224 B** gzip |
| Celda p90 | 820 B |
| Celda más cargada (`1500_-101`, 334 parkings) | **4,6 KB** gzip · 30,6 KB en crudo (**6,7×**) |
| Mediana de parkings por celda | 4 |

**Y el coste de responder a un usuario, que es la cifra que decide el ticket 2:**

| Dónde | Bloque | Con datos | gzip | Parkings |
|---|---|---|---|---|
| Sol (centro) | 17 celdas | 17 | **21 KB** | 926 |
| Móstoles | 13 celdas | 11 | **7 KB** | 256 |
| Buitrago (rural) | 17 celdas | 5 | **1 KB** | 12 |

👉 **Contra los 238 KB y 4,68 s en frío que hoy cuesta la misma pregunta en Overpass** (681 elementos
alrededor de Puerta del Sol, [MEDIDO en los tickets de Overpass]), el bloque de Sol son **21 KB
servidos por CDN**. ⚠️ La comparación no es de áreas idénticas —el bloque cubre ~12 × 9 km y el disco
de Overpass 5,1 km de radio— y la nuestra ya viene filtrada y comprimida; lo comparable es **lo que
cuesta contestarle a un conductor**, y ahí son 21 KB contra 238.

**Y dos propiedades del artefacto que no son cosmética:**
- ⛔ **Inmutable bajo su versión**, con el puntero `latest.json` movido al final: si una regeneración
  sobrescribiera la celda, un CDN con dos entradas en vuelo serviría **mitad de un snapshot y mitad
  del otro**, y las dos mitades se verían perfectamente válidas.
- ⛔ **Salida determinista** (celdas ordenadas, parkings por id, manifiesto ordenado): dos
  generaciones del mismo extracto dan el mismo byte, así que «esta semana no ha cambiado nada» se
  **comprueba** con un hash en vez de suponerse.
- Los nulos no se escriben: con `name` en el 5,6 % y `maxheight` en el 0,2 %, escribir `null` diez
  mil veces es pagar bytes por decir que no se sabe nada. Y las coordenadas se redondean a **7
  decimales**, que es la precisión con la que OSM guarda las suyas (1,1 cm) — lo que sobra no es
  dato, es el ruido de promediar una caja envolvente en coma flotante.

### 🟢 6 · El artefacto se sirve en `.json` PLANO, y la razón es una trampa ya pagada dos veces

Estaba escrito «JSON gzip por celda» y al abrir `firebase.json` salieron dos lecciones del propio
repo que lo desaconsejan:

1. ⛔ Una regla de cabeceras puede **desplegarse limpia y no casar nada**
   [WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001], y sólo se vio leyendo `Cache-Control` de la
   URL viva. Un `.json.gz` **depende** de acertar con `Content-Encoding`.
2. ⛔ Un `.json` sin regla **hereda el `max-age=3600` por defecto de Hosting**, que es el bug que
   documenta la regla del favicon: esconde un redespliegue.

Así que las celdas van en `.json` plano —Hosting ya comprime según el `Accept-Encoding` de quien
pregunta, sin una sola regla nueva— y el tool **mide las dos cifras**: lo que ocupa y lo que pesará
en el cable.

| Región de Madrid | Almacén | Cable |
|---|---|---|
| 463 celdas | **654 KB** | **177 KB** |

Y las dos reglas de caché entran **con la forma que ya está desplegada y funcionando** (la general
antes, la específica después, como `/assets/**` seguido de `/assets/manifest.json`):

| Ruta | `Cache-Control` | Por qué |
|---|---|---|
| `/parkings/v1/**` | `max-age=31536000, immutable` | una celda publicada no cambia nunca: la regeneración escribe otra versión |
| `/parkings/v1/latest.json` | `max-age=0, must-revalidate` | es el **picaporte**; pinarlo escondería una regeneración durante una hora |

- ⛔ **Y `LICENSE.txt` dice qué se CAMBIÓ respecto al origen**, no sólo que es ODbL: el filtro
  entero en prosa (bordillo, los ocho `access`, la excepción de `access=customers`, los dos únicos
  valores de `fee`, el `maxheight` sólo métrico, los 7 decimales). Un receptor tiene derecho a saber
  qué se seleccionó, y es lo que convierte «ODbL» en una licencia utilizable.
- ⛔⛔ **Un `firebase deploy --only hosting` ESPEJA el directorio** [MEDIDO: la web son 79 ficheros
  commiteados, así que no se borra; el snapshot **sí**]. O sea que **borra la versión anterior en el
  mismo despliegue que mueve el puntero**. Dos consecuencias que no son opcionales: el puntero va con
  `must-revalidate`, y **quien reciba un 404 en una celda tiene que volver a leer el puntero** en vez
  de concluir que no hay parkings — obligación del **ticket 2**, anotada en su fila.

### 🟢 7 · Guardarraíl, workflow y manual

- **`ParkingGridFixtureGuardrailTest`** compara `tools/parkings/grid-fixtures.json` con la rejilla
  viva (celda, nombre reversible, centro y **bloque con su orden**) y falla con el comando de
  regeneración escrito en el mensaje. **Falsificado**: alterando un id del bloque se pone rojo.
- **`.github/workflows/parkings-snapshot.yml`**: semanal + manual, con el deploy **opt-in** en la
  ejecución a mano y una verificación final que lee el puntero y las cabeceras **de la URL viva**.
  ⚠️ Arrancará por `schedule` **cuando esté en master** (los cron sólo corren desde la rama por
  defecto), y el paso de deploy esperará al secreto `FIREBASE_SERVICE_ACCOUNT`, que no existe.
  Trampa cazada al escribirlo: la semana se calcula con **`%G-W%V`**, no
  `%Y-W%V` — en la semana que cruza el año, `%Y` da el año del DÍA y machacaría una versión que ya
  existe.
- **`tools/parkings/README.md`** con la secuencia manual completa, que es la que vale mientras el CI
  esté caído.
- **`hosting/public/parkings/v1/` en `.gitignore`**: es salida de build. `LICENSE.txt` no.

### 🟢 8 · La suite entera, y contada

`./gradlew testAll --rerun-tasks` ⇒ **BUILD SUCCESSFUL**, y verificado que de verdad corrió:
**3.924 tests en 392 clases de `:shared`** + 4 en `:app`, **cero fallos**
[PROJECT-TEST-A-GREEN-SUITE-MUST-PROVE-IT-LOOKED]. El cambio de `Zone` toca código que lee medio
proyecto, así que el verde importa más que de costumbre.

### 🔴🟢 9 · El criterio 1, y el BUG que encontró: un parking municipal de Madrid desaparecía

La comparación se hizo con una herramienta, no a ojo, y con **la consulta de la app** —`--query`
imprime la que compone `OsmPublicParkingParser`, para que la diferencia no sea la de dos preguntas
distintas—. Primero el coste del camino de hoy, medido **hoy mismo**:

| Overpass vivo, zona de Sol, 02-11-2026 | |
|---|---|
| Respuesta | 227 KB |
| **Tiempo** | **44,8 s** |

⚠️ Casi diez veces los 4,68 s «en frío» de septiembre. No es una cifra que haga falta discutir: es el
argumento del ticket, y cambia de un día para otro porque **no depende de nosotros**.

**Primera comparación: 193 de 194 (99,5 %)** — y las dos discrepancias eran `relation`:

| Discrepancia | Qué era |
|---|---|
| sólo en Overpass: `relation/14451679` | **«Aparcamiento Plaza de España», de la EMT de Madrid** |
| mismo id, movido 15 m: `relation/21382798` | «Aparcamiento Plaza Mayor» |

⛔ **Causa raíz, medida: `osm_extract.py` recogía sólo miembros `way`.** Las dos son relaciones
`type=site`, y una relación de parking suele llevar sus **accesos como NODOS**:

- Plaza de España: **5 nodos y CERO ways** ⇒ lista de miembros vacía ⇒ sin geometría ⇒ un `continue`
  **mudo** ⇒ **fuera del snapshot**. Un parking municipal en el centro de la capital, perdido sin que
  nada lo dijera.
- Plaza Mayor: 5 nodos + 2 ways ⇒ la caja salía de dos de sus siete miembros ⇒ centro **15 m**
  desplazado.

`out center` de Overpass es la caja de **todos** los miembros. Con los nodos dentro:

| | Antes | Después |
|---|---|---|
| Relaciones extraídas en Madrid | 68 | **70** |
| Coincidencia con el Overpass vivo | 193 / 194 · 99,5 % | **194 / 194 · 100 %** |
| Mismo id movido > 10 m | 1 | **0** |

**Y las tres consecuencias que deja, que valen más que el arreglo:**

1. ⛔ **Lo que se tira ahora se CUENTA.** El `continue` mudo es lo que convirtió un bug en un dato
   que falta: el script imprime un aviso con cuántos ways y cuántas relations se quedaron sin
   geometría recuperable. Y las relaciones **anidadas** (miembros `relation`, que no resolvemos) se
   cuentan aparte en vez de suponerse inexistentes — en Madrid son **cero**.
2. 🟢 **El bug lleva su regresión, en Python, porque ya había precedente** (`tools/release/test_*.py`):
   `tools/parkings/test_osm_extract.py`, **6 tests**, con los dos casos reales de Madrid escritos
   como lo que son (cinco nodos y cero ways · nodos y ways mezclados) y con el centro de la caja
   distinguido del centroide. **Falsificado**: devolviendo `RELATION_MEMBER_TYPES` a `("w",)` se
   ponen **3 rojos de 6**.
3. ⛔ **La selección de miembros es ahora una función pública y pura** (`member_refs`), no un
   `if` dentro del bucle: el bug era exactamente esa línea, y una línea que puede borrar un parking
   municipal tiene que poder probarse sola.

👉 **Y esto es lo que el criterio 1 existía para hacer.** Sin la comparación, el snapshot habría
salido con el 99,5 % de coincidencia y nadie lo habría notado — porque el 0,5 % que falta **no se ve
como un error**: se ve como un parking que no está.

### 🔴🟢 10 · España no cabe en un extracto: **las Canarias salían a CERO y nada fallaba**

El extracto de Geofabrik de España (1,35 GB) da **114.065** elementos `amenity=parking` en **331 s**.
Pero el expediente contó **120.906** con el área `ISO3166-1=ES` de Overpass (§C.6): **5,7 % menos**, y
tres días no explican eso. Así que se midió en vez de elegir una cifra, y la caja del extracto lo
dijo de golpe:

```
caja del extracto de España:  lat 35,269..43,784   lon −9,298..4,310
las Islas Canarias están en:                       lon −13..−18
```

⛔ **Las Canarias viven en `africa/canary-islands-latest.osm.pbf`**, porque Geofabrik recorta por
**geografía** y no por administración. Medido sobre el extracto: **Canarias 0**, Baleares 4.331,
Ceuta 89, Melilla 90.

👉 **Y el fallo era MUDO, otra vez:** el pipeline se ejecutaba entero, escribía sus celdas y
publicaba un snapshot perfectamente válido en el que **2,2 millones de personas no tienen un solo
parking**. La app habría contestado «aquí no hay parkings» sin un error en ningún sitio.

**Arreglado cambiando la forma del pipeline, no parcheando una ruta:**

| | |
|---|---|
| `osm_extract.py` | **un extracto por llamada** — así una región que no ha cambiado no se vuelve a extraer, y 331 s no se pagan por las islas |
| `--snapshot <dir> <ver> <raw...>` | acepta **varios** JSON y los **deduplica por id**: los recortes de Geofabrik **se solapan** en las fronteras (medido: 1.059 elementos al oeste de −7,0, zona de Portugal) y el mismo parking llega dos veces |
| El workflow y el README | llevan la **lista** `europe/spain` + `africa/canary-islands`, no una ruta |

🟢 **Y la cuenta queda cuadrada sin elegir cifra:** 114.065 + 7.245 = **121.310**, un **0,3 % por
encima** del área administrativa exacta — y ese exceso es justo el margen de frontera medido. Las dos
cifras quedan explicadas.

### 🟢 11 · España entera, generada y medida

| | |
|---|---|
| Parkings publicados | **70.062** (65.406 península+islas del extracto de España · 4.656 de Canarias) |
| Rendimiento del filtro | **70.062 de 121.310 = 57,8 %** (en la región de Madrid era el 45,4 %: allí hay mucho más `street_side`) |
| Celdas | **12.566** |
| Almacén · cable | **7,4 MB** · **3,0 MB** |
| Tiempo de escritura | **5,9 s** |
| Celda mediana · p90 · la peor | **244 B** · 1,3 KB · 71 KB (`1430_-4`, 760 parkings) |
| % del almacén gratuito de Hosting (10 GB) | **0,07 %** |

Y la comprobación que importaba, celda a celda sobre el fichero publicado:

| | Parkings en su propia celda |
|---|---|
| Las Palmas de Gran Canaria | **46** |
| Santa Cruz de Tenerife | **65** |
| Arrecife | **24** |
| Palma de Mallorca | 84 |
| Ceuta | 20 |

⚠️ La estimación de §3 (~54.900 registros, ~2-2,5 MB gzip) se queda **corta en un 28 %**: son 70.062
y 3,0 MB. El veredicto de coste no se mueve — sigue siendo **0 €/mes** y el 0,07 % del cupón — pero la
cifra que vale es la medida.

⛔ Y los 12.566 ficheros **no entran en git**: verificado que `hosting/public/parkings/v1/` sale
marcado como ignorado y que lo único que se añade ahí es `LICENSE.txt`.

### 🟢 12 · El criterio 1, ahora sobre ESPAÑA: cinco puntos, 916 parkings, cero discrepancias

La coincidencia del 100 % estaba medida sólo en Madrid. Repetida sobre el snapshot nacional, con la
consulta de la app y contra el Overpass **vivo** de hoy:

| Punto | Celda | Overpass vivo | Snapshot | En los dos | Movidos | Coincidencia |
|---|---|---|---|---|---|---|
| Madrid (Sol) | `1499_-105` | 194 | 194 | 194 | 0 | **100 %** |
| Barcelona | `1535_60` | 269 | 269 | 269 | 0 | **100 %** |
| Málaga | `1362_-132` | 186 | 186 | 186 | 0 | **100 %** |
| **Las Palmas de Gran Canaria** | `1043_-505` | **179** | **179** | **179** | 0 | **100 %** |
| Cuenca (rural) | `1486_-61` | 88 | 88 | 88 | 0 | **100 %** |
| | | **916** | **916** | **916** | **0** | **100 %** |

🟢 **Las Palmas es la fila que importa**: 179 parkings donde el pipeline de un solo extracto tenía
**cero**, y la comparación no los habría echado de menos — habría dicho «0 de 0, 100 %» si le
hubiéramos preguntado por una celda vacía contra un Overpass que sí los tiene. Lo que los encontró
fue **mirar la caja del extracto**, no el testigo.

**Y el coste del camino de hoy, medido otra vez y en cuatro sitios distintos:**

| | Respuesta | Tiempo |
|---|---|---|
| Madrid | 227 KB | **44,8 s** |
| Barcelona | 218 KB | 33,9 s |
| Málaga | 277 KB | 29,3 s |
| Las Palmas | 152 KB | 39,5 s |
| Cuenca | 33 KB | **45,9 s** |
| Málaga, **pedida justo detrás** de otra | 534 B | **502 en 0,22 s** |

⚠️ Esa última fila es el argumento entero en una línea: **la segunda pregunta seguida se la deniegan**,
exactamente como mide el KDoc de `OverpassQuota`. Y la de Cuenca —33 KB, el pueblo con menos datos de
los cinco— tardó **45,9 s**: el tiempo no lo manda nuestro tamaño, lo manda su cola.

⛔ **Y esos segundos NO son lo que espera un usuario — son peores que eso.** El paseo de espejos está
acotado a **`WALK_CEILING_MS = 30 s`** [MEDIDO en `OverpassWalkPatience`], así que **cuatro de las
cinco respuestas de arriba habrían llegado TARDE: la app ya habría abandonado la pregunta.** Lo que
ve el conductor no es una lista que tarda 45 s, es:

- una lista **recordada** de hasta 30 días, si ya había pasado por esa zona (`DiskBackedPublicParkingsSource`), o
- **nada**, en una zona nueva — que en pantalla se lee como *«aquí no hay parkings»*.

Es la misma familia de fallo que las Canarias (§10) y que el `continue` mudo (§9): **el sistema no se
cae, contesta de menos.** Y por eso la cifra que importa del snapshot no es «21 KB contra 227 KB»
sino que **el CDN contesta siempre**.

### 🟢 13 · La base entera en UN fichero, porque es lo que ODbL pide ofrecer

El árbol de celdas es la forma de **servir**. Es una mala forma de **ofrecer** una base de datos a un
tercero: tendría que adivinar 12.566 nombres de celda y descargarlos uno a uno. Así que al lado del
manifiesto se publica la base completa:

```
hosting/public/parkings/v1/<ver>/parkings.ndjson.gz    70.062 líneas · 1.242 KB
```

| | |
|---|---|
| Líneas · ids únicos · ilegibles | **70.062 · 70.062 · 0** [MEDIDO] |
| La base entera, un solo flujo gzip | **1.242 KB** |
| La suma de las 12.566 celdas | **3.001 KB** |
| Lo que cuesta poder pedir una celda | **2,4×** |

- **NDJSON y no un array**: una línea por parking se lee en streaming, sin cargarse el país en
  memoria.
- El id de celda **no** se repite por línea: se deriva del punto con la misma rejilla, y escribirlo
  70.062 veces es pagar bytes por un dato calculable.
- 🟢 Y esto deja el recuento de ficheros como **un asunto de ingeniería, no de licencia**.

### ⚠️ 14 · Dos cifras del artefacto que conviene no confundir, y una pregunta abierta de escala

- **35 MB en disco frente a 7,4 MB de bytes** [MEDIDO]: son 12.566 ficheros diminutos (mediana
  **244 B**) contra bloques de 4 KB. Hosting cobra por bytes, así que lo que cuenta es 7,4 MB — pero
  el número que ve un `du` es otro, y no es un error.
- ⚠️ **¿Cuántos ficheros admite una versión de Hosting?** **No está documentado** — comprobado hoy en
  la página de cuotas (sólo fija 2 GB por fichero y los 10 GB de almacén) y en la de despliegue, que
  no menciona ningún tope de ficheros [VERIFICADO que NO lo dice; ⛔ no se afirma que no exista].
- 👉 **Lo que sí se puede decir sin saberlo: un fichero por celda no escala a un continente.** España
  sola son 12.566; Francia y Alemania son mayores y están mejor etiquetadas. Cuando el snapshot pase
  de un país, la salida es **agrupar celdas en super-celdas** (una de 10 × 10 dejaría España en ~126
  ficheros) o servir el árbol desde Cloud Storage y publicar en Hosting sólo la base a granel, que ya
  existe. **No entra en este ticket** —España cabe de sobra— y queda escrito para quien añada el
  segundo país.

### 🟢 15 · PUBLICADO, y leído de la URL viva (02-11-2026)

`firebase deploy --only hosting` sobre `pap-26`: **12.570 ficheros subidos**, versión finalizada y
release completo. Antes de lanzarlo se comprobó que la web local es **idéntica** a la viva
(`site.d8b59b55.css` y el mismo `<title>` en los dos lados), porque el deploy **espeja** el
directorio y una web local desfasada la habría regresado.

Y después, lo único que cuenta — **las cabeceras leídas de `paparcar.com`, no de la documentación**
[WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001]:

| Comprobación | Resultado |
|---|---|
| `/parkings/v1/latest.json` | `200` · **`version: 2026-W45`** · `cells 12566` · `parkings 70062` |
| su `Cache-Control` | **`public, max-age=0, must-revalidate`** ✅ la regla del picaporte CASÓ |
| `/parkings/v1/2026-W45/1499_-105.json` (Sol) | `200` · **`public, max-age=31536000, immutable`** ✅ la regla general casó, y la específica no la pisó |
| ⛔ **¿comprime Hosting un `application/json`?** | ✅ **SÍ**: `Content-Encoding: gzip`, `Vary: accept-encoding`, **1.642 B** en el cable para la celda de Sol |
| `/parkings/LICENSE.txt` | `200` · `text/plain; charset=utf-8` · revalida |
| `/parkings/v1/2026-W45/parkings.ndjson.gz` | `200` · **1.272.338 B** · immutable |
| `/parkings/v1/2026-W45/1043_-505.json` (Las Palmas) | `200` · **46 parkings**, el primero un subterráneo de pago con nombre |
| La web: `/` · `/en` · `/privacy-policy` | `200` · `200` · `200` |

🟢 **Cerrado el único [SUPUESTO] que quedaba del diseño:** se decidió publicar `.json` plano
*suponiendo* que Hosting comprime `application/json`, con la nota de comprobarlo en la URL viva. **Lo
hace.** Así que el fichero plano da el mismo cable que un `.json.gz` sin una sola cabecera que
acertar, que era justo el motivo de elegirlo.

🟢 **Y la base derivada ODbL ya está OFRECIDA**, no sólo licenciada: la licencia y el `.ndjson.gz` de
1,2 MB tienen URL pública. Era parte de la definición de hecho de este ticket.

⚠️ **Lo que publicar NO hace: la app no lo usa.** Sigue preguntando a Overpass exactamente como la
1.5.0 publicada. Quien lo lee es `PARKINGS-ONE-CALL-MERGED-001`, detrás de Remote Config, y Overpass
se queda como fallback mientras haya versiones publicadas que lo usen (§D.4).

### 🟢 16 · Y qué pasa cada lunes, exactamente

Una vez en master, el `schedule` arranca **todos los lunes a las 04:17 UTC** (un cron de GitHub sólo
corre desde la rama por defecto, así que hasta el merge no se mueve) y hace: descargar los dos
extractos → geometría → el filtro de la app → celdas → el guardarraíl de la rejilla → publicar.

⛔ **Y el paso de publicar AVISA en vez de fallar cuando falta la credencial.** A 02-11 el repo tiene
siete secretos y **ninguno es `FIREBASE_SERVICE_ACCOUNT`** [MEDIDO con `gh secret list`], así que un
`exit 1` pondría el workflow **en rojo todos los lunes** por una causa conocida que sólo el user
puede resolver. Un rojo semanal que nadie puede arreglar es cómo se aprende a ignorar los rojos, y
este repo ya lo pagó — *«un PR en rojo no significa nada»* es lo que dejó master sin compilar para
iOS **con el guard puesto**. Así que: `::warning::`, una nota en el resumen del run con qué hacer, y
el job verde. La verificación de cabeceras en vivo se salta sola en ese caso
(`if: steps.publish.outputs.published == 'true'`), porque comprobar un puntero que no se ha movido
daría un rojo falso.

| | |
|---|---|
| Generación semanal | ✅ automática en cuanto esté en master |
| Publicación semanal | ⏳ **espera un secreto del user**; hasta entonces, a mano con el README |
| Si falta el secreto | aviso + resumen, **no** un rojo |

## Criterio de éxito

1. **Una celda servida por CDN con la misma forma que hoy da Overpass**: para un punto de Madrid, el
   conjunto del snapshot y el conjunto que devuelve el carril en vivo **coinciden** salvo lo que haya
   cambiado en OSM entre las dos fechas — comparado con una herramienta, no a ojo.
2. **El filtro no se ha duplicado**: el generador invoca `OsmPublicParkingParser`. Falsificación: si
   alguien añade un valor a `ROADSIDE` y **no** regenera, el testigo de paridad lo canta.
3. **Tabla de fixtures de la rejilla verde en los dos lenguajes**, y roja si `CELL_METERS` se mueve.
4. **La fórmula de anillos tiene su test**: con `DEFAULT_RADIUS_METERS` subido a 4.000, el test exige
   2 anillos. Hoy exige 1.
5. **`LICENSE.txt` servido** junto al artefacto, y las cabeceras de caché **leídas de la URL viva**
   — no de la documentación ni de este doc [WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001].
6. `./gradlew testAll` verde.

⛔ **Y lo que NO es criterio de éxito:** que la app lea el snapshot. Eso es el ticket 2.

## Consumidores auditados

| Consumidor | Estado |
|---|---|
| `OsmPublicParkingParser` | ⏳ **se reutiliza, no se toca** (salvo lo necesario para invocarlo desde el generador) |
| `ParkingZoneGrid` | ⏳ `Zone` expone `bandIndex`/`columnIndex`; cambio **aditivo**, el centro sigue derivado |
| `ParkingZoneGrid` ↔ el generador | ⏳ contrato en dos lenguajes ⇒ tabla de fixtures compartida (D.3) |
| `OsmPublicParkingsSource` / `OverpassQuota` | ✅ **sin cambios en este ticket**. El carril sigue siendo Overpass hasta el ticket 2, y **nunca se retira** mientras 1.5.0 esté publicada |
| `LicensesScreen` | 🟢 **sin cambios aquí**: ya acredita OpenStreetMap · ODbL 1.0 [MEDIDO]. Nombrar la base derivada es del **ticket 2**, que es quien la usa (ver D.4) |
| `firebase.json` (hosting) | ⏳ cabeceras de caché para `/parkings/**` (inmutable salvo `latest.json`) |
| `WebSiteParityGuardrailTest` | ⚠️ comprobar que publicar bajo `hosting/public/parkings/**` no le descuadra la salida construida de la web |
| `docs/backlog/README.md` | ⏳ recuento al cerrar (`BacklogStatusGuardrailTest`) |
| `data-providers-001.md` §F.4 | ⏳ marcar el ticket 1 como abierto |
| CI | 🟢 **vivo** (verificado 02-11: verdes 27-10 y 29-10). El workflow arrancará al estar en master · ⚠️ su deploy espera al secreto `FIREBASE_SERVICE_ACCOUNT`, que **no existe** |
