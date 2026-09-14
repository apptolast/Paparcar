# PARKINGS-A-ZONE-IS-ASKED-ONCE-AND-PANNED-FOR-FREE-001 · El carril pregunta por ZONA, no por donde esté la cámara

**Estado:** ✅ Done · en master (14-09-2026)
**Apilada sobre** `PARKINGS-A-CAPPED-ANSWER-MUST-NOT-DROP-THE-NEAREST-001` (comparten las dos constantes).

## Problema

El carril preguntaba por un disco centrado en la cámara. Cada paneo de más de medio radio producía
unas coordenadas que **el servidor no había visto nunca**, así que cada una se calculaba desde cero.
Medido el 14-09-2026 contra un espejo sano, tres paneos cortos por el centro de Madrid:

| Forma de preguntar | Primer byte | Área | Elementos |
|---|---|---|---|
| **Zona fija**, la MISMA consulta 3 veces | **1,33 · 1,59 · 1,32 s** | 34 km² | 416 |
| Disco móvil, 3 centros distintos | 1,67 · 2,65 · **5,73 s** | 28 km² | 259 · 293 · 281 |

La dispersión no es la red: es la caché del propio Overpass. Una consulta ya respondida vuelve de
memoria; un centro nuevo se calcula entero.

Y lo que ni siquiera sale en esa tabla pesa más: **un paneo dentro de la zona no cuesta ninguna
petición**, porque la respuesta ya está en disco bajo la misma clave, contra un servicio que
concede 2 peticiones por IP. [OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]

## Diseño

Una idea: **se pregunta por la ZONA, se contesta sobre TI.**

- `ParkingZoneGrid` (dominio, puro): cuadrícula fija de **3 km**. `zoneOf(lat, lon)` da el centro de
  celda; `sameZone(...)` dice si panear debe algo al servidor.
- `GetNearbyPublicParkingsUseCase` pide el **centro de zona** con el **alcance de zona**, y luego
  filtra y ordena por la distancia a **donde está el usuario de verdad**, descartando lo que cae
  fuera del radio de pantalla.
- `HomeViewModel` deja de re-preguntar por distancia para este carril: la pregunta pasa a ser *¿sigo
  en la misma zona?*. Gasolineras se queda con la regla de distancia — su fuente no es Overpass en
  la mayoría de los casos y su radio es otro.

### Los números, y de dónde salen

- **Celda de 3 km.** Celdas mayores son *contraintuitivamente más baratas en total*, porque se piden
  mucho menos: cubrir una ciudad de 10 × 10 km cuesta ~11 zonas a 238 KB (≈ 2,6 MB, una vez al mes)
  frente a ~35 zonas a 166 KB (≈ 5,8 MB) con celda de 1,7 km. Menos peticiones **y** menos bytes. El
  techo lo pone el peso de una sola petición.
- **Alcance de zona = 5.200 m**, derivado, no elegido: `radio de pantalla (3.000) + media diagonal de
  celda (2.121)`. Es lo que hace falta para que alguien plantado en una **esquina** de la celda siga
  viendo 3 km a su alrededor.
- **Tope a 1.000.** Medido a 5.200 m: Madrid **681** elementos / 238 KB, Málaga 571 / 269 KB,
  Cuenca 108 / 31 KB. Misma lección que el ticket anterior: el alcance y el tope son una sola
  decisión.

### ⛔ Por ciudad NO, y se midió

Preguntar por área administrativa (`admin_level=8`) falló en **los dos espejos**, cada uno a su
manera y las dos malas:

- `overpass.openstreetmap.fr` —nuestro único fallback— **no tiene índice de áreas**: devuelve `200`
  con lista vacía y un `remark` interno. `OverpassResponseHealth` lo daría por bueno y la app diría
  «no hay parkings en Madrid». Es otra vez el 200 que miente.
- `overpass-api.de` devuelve `504` a los 6,6 s: resolver un área y barrerla cuesta mucho más que un
  disco.

Además, un municipio no tiene tamaño acotado (Madrid 600 km², un pueblo 5), hace falta geocodificar
antes solo para saber qué nombre pedir, y **entre dos pueblos no hay ciudad que pedir** — que es la
mayor parte de un viaje.

### ⛔ El centro se redondea; la CONSULTA sigue siendo un disco

Mantener `around:` en vez de pasar a bounding box es lo que permite que toda la caché de cobertura
siga como está: `CoverageDisc` ya habla de discos, y dos discos redondeados o coinciden exactamente
o no se solapan. Un cambio a bbox habría arrastrado `OsmCoverage`, `RoomPublicParkingsCache` y sus
tests sin comprar nada.

## Criterio de éxito

- Un testigo que barra la celda entera y falle si **alguien en cualquier punto de la zona** queda
  servido por debajo del radio de pantalla. Es el fallo más difícil de ver: se leería como «aquí no
  hay parkings».
- Un testigo de que la cuadrícula sigue midiendo ~3 km a distintas latitudes (un grado de longitud
  son 85 km en Málaga y 62 en Ámsterdam).
- Un testigo de que dos puntos de la misma banda coinciden en la cuadrícula — si el paso de longitud
  variase punto a punto, cada caché fallaría y **nada parecería roto**.
- En campo: panear por la ciudad sin que el carril vuelva a cargar.

## Consumidores auditados

| Sitio | Qué asumía | Estado |
|---|---|---|
| `GetNearbyPublicParkingsUseCase` | radio de pantalla = radio de consulta | ✅ separados, con KDoc de por qué |
| `HomeViewModel.refreshNearby` | re-fetch por distancia para todos los carriles | ✅ parkings por zona; gasolineras intacta |
| `ViewportRefetch` | compartido con gasolineras | ✅ **no se toca** — sigue siendo la regla de su carril |
| `OsmPublicParkingParser.MAX_RESULTS` | densidad a 3 km | ✅ subido a 1.000 y su test con él |
| `RoomPublicParkingsCache` / `CoverageDisc` | discos | ✅ intacto: redondear el centro alinea los discos en vez de romperlos |
| `HomeViewModelTest` (4 tests) | el fixture aparecía aunque estuviera a 7,8 km | ✅ ver abajo |

### El filtro de radio cazó un fallo que los tests tapaban

Cuatro tests de `HomeViewModelTest` fallaron al añadir el filtro de pantalla, y **tenían razón en
fallar**: el fixture plantaba el parking en Sol mientras la cámara viajaba 7,8 km, y la lista lo
seguía enseñando. Lo tapaba que el fake ignora el radio que se le pide. Ahora el caso de uso descarta
por su cuenta lo que no se puede ver desde donde miras — el mismo *belt and braces* que el parser ya
aplica con la acera.

## Fuera de alcance

- Precargar las tres listas al arrancar: sigue esperando a ver esto en campo.
- El geocode que bloquea la petición sin que la fuente lo use (`GetNearbyPublicParkingsUseCase:63`);
  anotado en el ticket del cupo.

## Rebase

14-09-2026, sobre master `84a1577b`. **Sin conflictos**. Misma advertencia que la rama base sobre
`--update-refs` y los worktrees: se colocó con `reset --hard 26cfa853`.
