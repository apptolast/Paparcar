# `tools/parkings` — el snapshot pre-cocinado de la base de parkings

[OSM-PRECOOKED-SNAPSHOT-001] · doc del ticket:
[`../../docs/backlog/osm-precooked-snapshot-001.md`](../../docs/backlog/osm-precooked-snapshot-001.md)

El carril de parkings de la app pregunta hoy a Overpass en vivo y lo paga en latencia y en cupo
(4,68 s en frío en Madrid, 7,72 s en Málaga, 504 en Barcelona). Un `amenity=parking` cambia en
semanas, así que la base se **pre-cocina una vez por semana** y se sirve por CDN.

## Las dos mitades, y por qué están partidas así

```
 Geofabrik .osm.pbf
   │
   ├─ osm_extract.py          pyosmium · SOLO geometría y plumbing
   │                          (tag amenity=parking · centro de la bounding box · todos los tags)
   │
   └─ :shared:parkingSnapshotTool --snapshot
                              EJECUTA `OsmPublicParkingParser`, el filtro DE LA APP
                              (bordillo, access, fee, maxheight, kind) + reparto en celdas
```

⛔ **El filtro no se reimplementa en Python, se ejecuta.** Lo que decide qué es un parking para
Paparcar está medido dentro de `OsmPublicParkingParser` y una segunda implementación derivaría en
silencio: el snapshot enseñaría un conjunto distinto del que la app enseña, y nadie lo vería hasta
que un usuario mirara un bordillo dibujado como parking.

## Dependencias

```bash
pip install osmium            # pyosmium — ⛔ no viene con el repo
```

Java 17 y el wrapper de Gradle ya los pide el repo. ⛔ Compilar **siempre** por la herramienta Bash:
en PowerShell `.\gradlew` sale exit 0 sin compilar nada.

## Generar a mano

⛔ **España son DOS extractos, no uno** — ver «Los extractos de un territorio» más abajo.

```bash
WEEK=$(date -u +%G-W%V)                 # p. ej. 2026-W44
SCRATCH=/tmp/parkings                   # fuera del repo: son cientos de MB
mkdir -p "$SCRATCH"

# 1 · los extractos
curl -L -o "$SCRATCH/es.osm.pbf"  https://download.geofabrik.de/europe/spain-latest.osm.pbf
curl -L -o "$SCRATCH/ic.osm.pbf"  https://download.geofabrik.de/africa/canary-islands-latest.osm.pbf
#   (Madrid sola, para iterar rápido: .../europe/spain/madrid-latest.osm.pbf — 85 MB)

# 2 · geometría, UNO POR EXTRACTO (así una región que no ha cambiado no se vuelve a extraer)
python tools/parkings/osm_extract.py "$SCRATCH/es.osm.pbf" "$SCRATCH/es-raw.json"
python tools/parkings/osm_extract.py "$SCRATCH/ic.osm.pbf" "$SCRATCH/ic-raw.json"

# 3 · el filtro de la app + las celdas, directamente en el directorio de Hosting
./gradlew :shared:parkingSnapshotTool \
  --args="--snapshot hosting/public $WEEK $SCRATCH/es-raw.json $SCRATCH/ic-raw.json"

# 4 · publicar
firebase deploy --only hosting          # ⛔ sólo con permiso del user, como cualquier despliegue

# 5 · y COMPROBARLO en la URL viva, que es la única que cuenta
curl -sI -H 'Accept-Encoding: gzip' https://paparcar.com/parkings/v1/latest.json | \
  grep -iE 'cache-control|content-encoding|age'
```

⚠️ **La generación semanal la hace el workflow** `.github/workflows/parkings-snapshot.yml` en cuanto
esté en master (un `schedule` sólo corre desde la rama por defecto). Su paso de **publicación**
espera al secreto `FIREBASE_SERVICE_ACCOUNT`, que a 02-11 **no existe** en el repo; hasta que se cree,
el paso 4 de aquí es a mano.

El paso 5 no es ceremonia: este repo ya se comió una regla de cabeceras que **se desplegó limpia y
no casó nada** [WEB-A-HOSTING-HEADER-RULE-MUST-ACTUALLY-MATCH-001], y sólo se vio leyendo
`Cache-Control` de la URL publicada.

## Los extractos de un territorio — ⛔ España no cabe en uno

| Extracto de Geofabrik | Tamaño | `amenity=parking` |
|---|---|---|
| `europe/spain-latest.osm.pbf` | 1,35 GB | 114.065 |
| `africa/canary-islands-latest.osm.pbf` | 60 MB | **7.245** |
| | | **121.310** |

⛔ **Las Canarias están en `africa/`, no en `europe/spain`**, porque Geofabrik recorta por geografía
y no por administración. Medido: la caja del extracto de España es `lon −9,30..4,31`, y las islas
están entre −13 y −18. **Con un solo extracto el snapshot deja las Canarias a CERO** — 2,2 millones
de personas con el mapa vacío — y **nada falla**: el fichero se genera, las celdas se publican y la
app contesta «aquí no hay parkings».

Baleares, Ceuta y Melilla **sí** van dentro del de España (4.331 · 89 · 90 elementos, medidos).

🟢 **Y esto cuadra la cuenta que no cuadraba**: el expediente contó 120.906 con el área
`ISO3166-1=ES` de Overpass y el extracto de España daba 114.065 (−5,7 %). Con las islas son
**121.310**, un 0,3 % POR ENCIMA del área exacta — y ese exceso también está medido: los recortes de
Geofabrik llevan **margen de frontera** (1.059 elementos al oeste de −7,0, zona de Portugal). Las dos
cifras quedan explicadas sin elegir la que más guste.

⛔ Por eso el tool acepta **varios** JSON y los **deduplica por id**: dos extractos vecinos traen el
mismo parking dos veces, y el id (`osm/<tipo>/<id>`) es estable.

## Lo que se publica

```
hosting/public/parkings/LICENSE.txt               ODbL 1.0 + qué se filtró · A MANO, viaja en git
hosting/public/parkings/v1/latest.json            el puntero · must-revalidate
hosting/public/parkings/v1/<ver>/index.json       el manifiesto de la versión
hosting/public/parkings/v1/<ver>/<band>_<col>.json  una celda · immutable
hosting/public/parkings/v1/<ver>/parkings.ndjson.gz  la base ENTERA · lo que ODbL pide ofrecer
```

⛔ **El árbol de celdas es la forma de SERVIR; la base a granel es la de OFRECER.** Un tercero no
puede adivinar 12.566 nombres de celda: el `.ndjson.gz` es la misma base en un fichero de 1,2 MB, una
línea por parking para poder leerla en streaming. Medido: la suma de las celdas son 3,0 MB y la base
entera 1,2 MB — **2,4×**, que es lo que cuesta poder pedir una celda sola.

- ⛔ **`v1` es el ESQUEMA, `<ver>` es la semana.** Un snapshot publicado es inmutable y el puntero se
  mueve al final: si una regeneración sobrescribiera la celda, un CDN con dos entradas en vuelo
  serviría mitad de un snapshot y mitad del otro, y las dos mitades se verían válidas.
- ⛔ **`hosting/public/parkings/v1/` está en `.gitignore`**: es salida de build (463 ficheros sólo la
  región de Madrid). `LICENSE.txt` no, porque se escribe a mano.
- ⛔⛔ **Un `firebase deploy --only hosting` ESPEJA el directorio**, así que **borra la versión
  anterior** en el mismo despliegue que mueve el puntero. Dos consecuencias que no son opcionales:
  el puntero tiene que leerse fresco (`must-revalidate`, ya está en `firebase.json`), y **quien lea
  una celda y reciba un 404 tiene que volver a leer el puntero** en vez de concluir que no hay
  parkings. Eso último es obligación del consumidor — `PARKINGS-ONE-CALL-MERGED-001`.

## Los otros modos del tool

```bash
# ¿corre el filtro de la app en esta JVM? (3 elementos -> 1)
./gradlew :shared:parkingSnapshotTool --args="--probe"

# cuántos sobreviven al filtro, y repartidos por kind/fee/campos
./gradlew :shared:parkingSnapshotTool --args="--count /tmp/parkings/es-raw.json"

# regenerar la tabla de fixtures de la rejilla (la vigila ParkingGridFixtureGuardrailTest)
./gradlew :shared:parkingSnapshotTool --args="--grid-fixtures tools/parkings/grid-fixtures.json"
```

## Medido

**España entera (02-11-2026)** — `europe/spain` + `africa/canary-islands`:

| | |
|---|---|
| Extractos | 1,35 GB + 60 MB |
| `osm_extract.py` | 114.065 + 7.245 elementos, en **331 s** y 14 s |
| Rendimiento del filtro de la app | **70.062 de 121.310 (57,8 %)** |
| Celdas | **12.566** · almacén **7,4 MB** · cable **3,0 MB** · escritas en **5,9 s** |
| Base entera (`parkings.ndjson.gz`) | **1,2 MB** · 70.062 líneas, 70.062 ids únicos |
| Coincidencia con el Overpass vivo | **916 / 916 · 100 %** en 5 puntos (Madrid, Barcelona, Málaga, Las Palmas, Cuenca) |
| ⚠️ En disco (`du`) | **35 MB**: 12.566 ficheros de mediana 244 B contra bloques de 4 KB. Hosting cobra por bytes |
| Celda mediana | **244 B** en el cable · p90 1,3 KB · la peor 71 KB |
| Mediana de parkings por celda | 2 |
| Hosting: % del almacén gratuito (10 GB) | **0,07 %** |

**Región de Madrid (30-10-2026)**, que es con la que se itera:

| | |
|---|---|
| Extracto `madrid-latest.osm.pbf` | 85,5 MB, descargado en 3,1 s |
| `osm_extract.py` | 14.646 elementos en 17,5 s · 2,63 MB de JSON |
| Rendimiento del filtro de la app | **6.656 de 14.646 (45,4 %)** |
| Celdas | 463 · almacén **655 KB** · cable **177 KB** |
| Celda mediana | **224 B** en el cable |
| Bloque de Sol (17 celdas, 926 parkings) | **21 KB** — contra **227 KB y 44,8 s** en Overpass |
| Coincidencia con el Overpass vivo | **194 / 194 · 100 %** |
