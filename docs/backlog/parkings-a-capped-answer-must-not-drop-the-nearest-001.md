# PARKINGS-A-CAPPED-ANSWER-MUST-NOT-DROP-THE-NEAREST-001 · El carril llega a 3 km, y el tope deja de recortar al azar

**Estado:** ✅ Done · en master (14-09-2026)

## Problema

El radio de parkings públicos era de **1.500 m** y se queda corto: en la práctica el carril enseña
un puñado de parkings alrededor del punto exacto y el usuario tiene que panear para ver lo que hay
a dos calles.

Subirlo parecía cambiar una constante. No lo era.

## Doctrina violada

**⛔ Un comentario que describe lo que hace el CONSUMIDOR, no lo que hace la FUENTE.** El KDoc del
tope decía:

> *A ceiling on the payload, not on the map: **the nearest ones** are what a driver acts on.*

Eso describe `OsmPublicParkingsSource`, que ordena por distancia **después** de parsear. No describe
a Overpass, que es quien recorta: **`out center N` corta por orden de id, no por distancia**. Un
resultado capado es un subconjunto **arbitrario** del disco — el parking de enfrente tiene la misma
probabilidad de caerse que el del borde, y ordenar después no recupera lo que nunca llegó.

A 1.500 m era casi inocuo. Al radio nuevo no: medido contra el endpoint real el **14-09-2026**,

| Zona | r=1,5 km | r=3 km | r=5 km |
|---|---|---|---|
| Madrid, Puerta del Sol | ~cerca del tope | **259** | 641 |
| Málaga centro | 300 | ~250 | 557 |

…con un tope de 80 se habrían tirado **dos de cada tres parkings de Madrid, al azar**. Ensanchar el
alcance sin tocar el tope habría empeorado las ciudades densas mientras parecía mejorar los pueblos.

## Diseño

Las dos constantes son **una sola decisión** y por eso se mueven juntas:

- `PublicParkingsQuery.DEFAULT_RADIUS_METERS`: 1.500 → **3.000**
- `OsmPublicParkingParser.MAX_RESULTS`: 80 → **300**

3 km es donde se cruzan dos cosas. Cubre un destino y su entorno por unos 100 KB de respuesta, y
—como `ViewportRefetch` re-pregunta al pasar de LA MITAD del radio— **dobla la distancia de paneo
que dispara una petición nueva**, de 750 m a 1.500 m. Contra un servicio público que permite **2
peticiones por IP**, eso no es un efecto colateral: es la mitad del motivo. Menos respuestas y más
grandes ganan a muchas pequeñas cuando el presupuesto son peticiones, no bytes.
[OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001]

⛔ **Sigue sin ser el radio de gasolineras (5 km)**, y el test lo fija: a un repostaje se va de
camino, pero se aparca en el DESTINO. Un parking a 5 km no es una opción, es otro viaje.

## Criterio de éxito

- Un testigo que falle si alguien vuelve a bajar el tope por debajo de la densidad medida al radio
  configurado — el invariante, no el número.
- Un testigo de que la consulta lleva el radio que declara el dominio y no una copia suya.
- En campo: abrir el carril en Madrid/Málaga y ver parkings a varias calles, no solo los de la manzana.

## Consumidores auditados

| Sitio | Qué asume del tope o del radio | Estado |
|---|---|---|
| `OsmPublicParkingParser.buildQuery` | mete `MAX_RESULTS` en `out center` | ✅ deriva, no copia |
| `RoomPublicParkingsCache:88` | `isTruncated = parkings.size >= MAX_RESULTS` | ✅ sigue correcto: la honestidad de la cobertura se recalcula sola con el tope nuevo |
| `RoomPublicParkingsCache:181` | acota las filas por disco vía `MAX_RESULTS` | ⚠️ el techo de filas en disco sube ×3,75 — ver abajo |
| `GetNearbyPublicParkingsUseCase:47` | `DEFAULT_RADIUS_METERS` por defecto | ✅ deriva |
| `HomeViewModel:727` | usa la constante para decidir el re-fetch | ✅ deriva — de ahí sale el paneo de 1.500 m |
| `OsmCoverageTest` | usa 1.500 como literal de geometría | ✅ no depende del default: prueba discos, no el carril |
| `FuelStationsQuery.DEFAULT_RADIUS_METERS` (5 km) | otro carril | ✅ intacto, y ahora hay test que impide que converjan |

### ⚠️ Efecto en disco, aceptado y anotado

`RoomPublicParkingsCache` acota las filas guardadas por número de discos × `MAX_RESULTS`, así que el
techo de filas sube de 80 a 300 por disco. Con el radio también mayor hacen falta MENOS discos para
cubrir la misma ciudad, así que el total no se multiplica por 3,75 — pero sube. Se deja así a
propósito: el caché de 30 días es lo que sostiene el mapa en frío
[CACHE-A-MAP-MUST-NOT-EMPTY-ON-COLD-START-001] y recortarlo aquí resolvería un problema que nadie ha
medido todavía. Si aparece, es su propio ticket.

## Fuera de alcance

La pregunta del user sobre **precargar las tres listas en el primer arranque** no entra aquí: es un
cambio de comportamiento del arranque, no del alcance de una consulta, y su respuesta depende de la
Fase 2 del plan de fuentes (servidor propio). Ver la conversación y
`docs/backlog/parkings-fuentes-de-datos.html`.

## Rebase

14-09-2026, sobre master `84a1577b`. **Sin conflictos**. Es la base de la cadena
radio → zonas → precarga, rebasada por la punta con `--update-refs`.

⚠️ `--update-refs` **no movió esta rama**: git no puede mover una rama que está checkouteada en otro
worktree, y esta lo estaba. Se colocó con un `reset --hard` al commit ya rebasado dentro de la punta
(`29740a70`), que es lo que evita duplicar el commit en dos copias divergentes.
