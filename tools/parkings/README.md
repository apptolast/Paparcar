# `tools/parkings` — el snapshot pre-cocinado de la base de parkings

[OSM-PRECOOKED-SNAPSHOT-001] · doc del ticket:
[`../../docs/backlog/osm-precooked-snapshot-001.md`](../../docs/backlog/osm-precooked-snapshot-001.md)

La app consulta la base OSM pre-cocinada por Firebase Hosting/CDN y `mergeCell`; Firestore
aporta la ocupación municipal, no el catálogo OSM. Overpass queda como respaldo. El snapshot se
genera semanalmente porque un `amenity=parking` cambia en semanas, no en cada consulta.

Ampliación ES + Canarias + Francia metropolitana:
[`PARKINGS-FRANCE-SNAPSHOT-001`](../../docs/backlog/parkings-france-snapshot-001.md).
Publicada el 27-11-2026: **391.087 parkings / 48.008 celdas**, verificados en la API pública
en 14 zonas de España, Canarias y Francia, incluida Córcega.

El workflow **genera y publica la unión completa cada lunes a las 04:17 UTC**. La publicación
semanal ampliada fue autorizada el 27-11-2026. En ejecución manual sigue siendo opt-in
(`deploy: true`); el valor por defecto solo genera y valida.
La ejecución de producción del 23-11-2026 sí publicó España; los avisos históricos de CI/secreto
ausente más abajo describen el estado de noviembre temprano, no el actual.

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

El extractor usa un índice temporal de coordenadas en disco (`sparse_file_array`) y libera el de
la primera pasada antes de la segunda. Evita exigir RAM proporcional a todos los nodos franceses;
no cambia geometría ni filtro de producto. En la segunda pasada, filtros nativos por ID dejan
pasar a Python solo los miembros de relaciones pedidos, después de cachear las coordenadas de
todos los nodos para sus ways. Un fixture real protege ese orden. En Canarias, salida idéntica
byte a byte frente al índice en RAM y frente a la iteración completa, bajando de 11,7 a 1,7 s.
Node 24 ejecuta la validación de publicación.

Java 17 y el wrapper de Gradle ya los pide el repo. ⛔ Compilar **siempre** por la herramienta Bash:
en PowerShell `.\gradlew` sale exit 0 sin compilar nada.

## Generar a mano

⛔ **España son DOS extractos, no uno** — ver «Los extractos de un territorio» más abajo.

```bash
WEEK="$(date -u +%G-W%V)-$(date -u +%Y%m%dT%H%M%S)-$(openssl rand -hex 4)" # único por generación
SCRATCH=/tmp/parkings                   # fuera del repo: son cientos de MB
mkdir -p "$SCRATCH"

# 1 · los extractos
curl -fL --retry 3 -o "$SCRATCH/es.osm.pbf"  https://download.geofabrik.de/europe/spain-latest.osm.pbf
curl -fL --retry 3 -o "$SCRATCH/ic.osm.pbf" https://download.geofabrik.de/africa/canary-islands-latest.osm.pbf
curl -fL --retry 3 -o "$SCRATCH/fr.osm.pbf" https://download.geofabrik.de/europe/france-latest.osm.pbf
#   (Madrid sola, para iterar rápido: .../europe/spain/madrid-latest.osm.pbf — 85 MB)

# 2 · geometría, UNO POR EXTRACTO (así una región que no ha cambiado no se vuelve a extraer)
python tools/parkings/osm_extract.py "$SCRATCH/es.osm.pbf" "$SCRATCH/es-raw.json"
python tools/parkings/osm_extract.py "$SCRATCH/ic.osm.pbf" "$SCRATCH/ic-raw.json"
python tools/parkings/osm_extract.py "$SCRATCH/fr.osm.pbf" "$SCRATCH/fr-raw.json"

# 3 · el filtro de la app + las celdas, directamente en el directorio de Hosting
./gradlew :shared:parkingSnapshotTool \
  --args="--snapshot hosting/public $WEEK $SCRATCH/es-raw.json $SCRATCH/ic-raw.json $SCRATCH/fr-raw.json"
node tools/parkings/validate-snapshot.mjs --download-baseline "$SCRATCH/previous.ndjson.gz"
node tools/parkings/validate-snapshot.mjs hosting/public --require-es-fr --baseline="$SCRATCH/previous.ndjson.gz"

# 4 · publicar
firebase deploy --only hosting          # ⛔ sólo con permiso del user, como cualquier despliegue

# 5 · y COMPROBARLO en la URL viva, que es la única que cuenta
curl -sI -H 'Accept-Encoding: gzip' https://paparcar.com/parkings/v1/latest.json | \
  grep -iE 'cache-control|content-encoding|age'
```

El `schedule` solo corre desde la rama por defecto. Cada ejecución escribe una versión
`<semana>-<run_id>-<run_attempt>`; el generador rechaza directorios ya existentes y extractos
vacíos. No hay un input que permita publicar Francia sola accidentalmente. CI conserva el
artefacto 14 días para revisión. Antes de publicar, compara con la base servida: perder más del 2 %
de sus IDs aborta, además de validar celdas, recuentos, unicidad y equivalencia con el bulk.

El guard de Hosting ejecuta la misma validación estructural, contrasta los IDs con la base viva
(también en un despliegue manual) y exige zonas testigo en Madrid,
Barcelona, Cuenca, Las Palmas, Palma, Ceuta, Melilla, París, Lyon, Toulouse, Ajaccio, Bourges y
Perpiñán, además de Sancerre como zona rural. Es una alarma contra un país omitido, no una prueba de exhaustividad de OSM.

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

- ⛔ **`v1` es el ESQUEMA, `<ver>` identifica una generación única, no solo la semana.** Un snapshot publicado es inmutable y el puntero se
  mueve al final: si una regeneración sobrescribiera la celda, un CDN con dos entradas en vuelo
  serviría mitad de un snapshot y mitad del otro, y las dos mitades se verían válidas.
- ⛔ **`hosting/public/parkings/v1/` está en `.gitignore`**: es salida de build (463 ficheros sólo la
  región de Madrid). `LICENSE.txt` no, porque se escribe a mano.
- ⛔⛔ **Un `firebase deploy --only hosting` ESPEJA el directorio**, así que **borra la versión
  anterior** en el mismo despliegue que mueve el puntero. Dos consecuencias que no son opcionales:
  el puntero tiene que leerse fresco (`must-revalidate`, ya está en `firebase.json`), y **quien lea
  una celda y reciba un 404 tiene que volver a leer el puntero** en vez de concluir que no hay
  parkings. Eso último es obligación del consumidor — `PARKINGS-ONE-CALL-MERGED-001`.

## Francia, límites y transición

El extracto `europe/france-latest.osm.pbf` (~4,8 GB, noviembre 2026) cubre Francia
metropolitana, incluida Córcega. Geofabrik excluye ultramar de ese fichero:
https://download.geofabrik.de/europe/france.html. No prometer cobertura DOM/TOM con esta unión.
El filtro, coordenadas e IDs siguen siendo los de la app; no se añaden precios, ocupación
francesa ni plazas libres en tiempo real.

El backend verifica el índice antes de leer archivos: una celda enumerada que falta produce
error (y fallback cliente); una celda no enumerada no se descarga. Si Hosting acaba de retirar
la versión anterior, refresca el puntero y reintenta el bloque completo una vez. Las cachés
del lector están acotadas. Un bloque entero sin datos devuelve `serve: false` de forma
conservadora: puede ser tierra vacía o territorio sin importar. No se infiere un país por una caja.

Publicar la ampliación requiere desplegar el backend revisado y Hosting con la unión validada,
tras aprobación. No hace falta cambiar el contrato del APK. La API/CDN puede conservar una
respuesta estática una hora, el puntero en el backend un minuto y la app su caché 30 minutos:
comprobar con una consulta fresca y después con el cliente. No prometer efecto inmediato.

Si falla la validación **no desplegar**. Si falla la comprobación posterior, restaurar la release
completa anterior desde Firebase Hosting y, si procede, la revisión anterior de la función.
El switch `serve: false` deriva a Overpass, pero también está sujeto al TTL del CDN; no equivale
a una reversión instantánea. No desplegar nunca un worktree sin la base conjunta generada.

## La credencial del despliegue — y ⛔ por qué NO correr `firebase init hosting:github` a pelo

El workflow semanal **genera** solo pero no **publica** hasta que exista una credencial de cuenta de
servicio en los secretos del repo. Acepta **dos** nombres y gana el que exista:
`FIREBASE_SERVICE_ACCOUNT` o `FIREBASE_SERVICE_ACCOUNT_PAP_26`.

⛔ **Lo recomendado por ahi fuera es `firebase init hosting:github`, y en ESTE repo tiene dos
efectos que hay que conocer antes** (verificados leyendo `lib/init/features/hosting/github.js` de
firebase-tools 15.18, no supuestos):

| Lo que hace | Por qué importa aquí |
|---|---|
| Nombra el secreto **`FIREBASE_SERVICE_ACCOUNT_<PROJECT_ID>`**, no el nombre pelado | por eso este workflow acepta los dos; con uno solo, quien lo creara por la vía recomendada dejaría el paso avisando «falta el secreto» **con el secreto puesto** |
| Genera **`firebase-hosting-merge.yml`** y **`firebase-hosting-pull-request.yml`** | ⛔⛔ el primero despliega **hosting en cada push a master** — desde un checkout limpio de CI, que **no tiene las celdas** (están en `.gitignore`: son salida de build). Es decir: intentaría **borrar las 12.566 celdas publicadas en cada merge**. Es el incidente del 03-11, pero automatizado |

🟢 El guard `predeploy` lo **para** (aborta el deploy en vez de borrar), así que el daño se
convierte en un **rojo en cada merge a master** — que es mejor que un borrado, y aún así es ruido que
enseña a ignorar los rojos.

**Lo que sí hacer, en orden:**

1. Crear la cuenta de servicio y su clave. Si se usa `firebase init hosting:github`, **borrar
   inmediatamente después los dos workflows que genera**: aquí el único camino de despliegue es
   `parkings-snapshot.yml`, que regenera las celdas **antes** de publicar.
2. Guardar la clave como secreto. Con el JSON delante:
   ```bash
   gh secret set FIREBASE_SERVICE_ACCOUNT < ruta/a/la/clave.json
   ```
3. Comprobar que el próximo lunes publica de verdad — o lanzarlo a mano con
   `workflow_dispatch` y `deploy: true`.

⚠️ Una clave JSON es una credencial **de larga vida** en los secretos de GitHub. La alternativa sin
clave es **Workload Identity Federation** (OIDC), que no deja nada que rotar; cuesta más de montar y
necesita `gcloud` o la consola. Queda dicho para quien lo decida.

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

**Unión ES + Canarias + Francia metropolitana publicada (27-11-2026)**:

- 65.509 + 4.667 + 321.064 parkings filtrados; 153 solapamientos deduplicados.
- **391.087 registros / 48.008 celdas**; ~40 MB de JSON, bulk ~6,6 MB.
- Baseline publicado: 70.153 de 70.177 IDs conservados (99,9658 %). Los 24 ausentes se
  investigaron: 10 ya no aparecen en extractos nuevos y 14 ahora excluidos por tags con el filtro
  existente (roadside, acceso de clientes sin pago o privado).
- París: 114/114 IDs contra Overpass vivo en radio de 3 km, cero exclusivos o movidos >10 m.
- Francia extraída en 308,7 s con índice de disco y filtros nativos. España 123,7 s frente a
  335,4 s; JSON idéntico por SHA-256. Son ejecuciones locales, no un benchmark aislado.
- El extractor conserva su limitación sobre miembros de relaciones anidadas: en Francia contó
  2 relaciones con miembros relación no resueltos y descartó 2 sin geometría recuperable.
- API pública: 14/14 zonas con nueva versión y datos; bloques de París 596, Madrid 938 y
  Las Palmas 307. Puntero must-revalidate; celdas gzip e immutable. Detalle en el ticket.
- Comprobación realizada por HTTP; sin prueba en APK/dispositivo ni un nuevo run completo de CI.


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
