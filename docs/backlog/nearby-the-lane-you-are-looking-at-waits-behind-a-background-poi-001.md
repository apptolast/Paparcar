# NEARBY-THE-LANE-YOU-ARE-LOOKING-AT-WAITS-BEHIND-A-BACKGROUND-POI-001 · la ranura de Overpass no sabe quién está mirando

**Estado:** 🔵 Abierto — hipótesis con mecanismo leído en el código y **sin medir todavía en campo**.
Sale de [HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001], que arregló lo que se VEÍA (el shimmer colgado) y
dejó esto: *«aunque haya internet, parkings sigue tardando un poco mucho»* (user, 26-09-2026).

## La hipótesis, con su mecanismo

`OverpassQuota` concede **una sola pregunta a la vez para toda la app** (`CONCURRENT_ASKS = 1`), con
10 s de techo de espera (`WAIT_CEILING_MS`) y 30 s de techo de caminata
(`OverpassWalkPatience.WALK_CEILING_MS`). La ranura es correcta y está bien argumentada: el servidor
da 2 por IP y una IP es un operador entero.

Lo que la ranura **no sabe** es quién está esperando al otro lado:

| carril | quién lo pidió | presupuesto declarado |
|---|---|---|
| POI de la cámara (fase 2 del geocodificador) | nadie: decora una cabecera | `[timeout:8]` |
| parkings públicos | el usuario, que está mirando la lista | `[timeout:20]` |
| red de calles del casador de rutas | el viaje en curso | `[timeout:20]` |

Al abrir Home se disparan la fase 2 del geocodificador **y** el calentamiento de carriles. Si el POI
coge la ranura primero, el carril de parkings espera detrás — hasta 10 s — y si no le llega el turno
**falla** («overpass slot never came free»), que en pantalla es la frase «no se pudo preguntar»
teniendo cobertura perfecta.

## Qué falta antes de tocar nada

⛔ **Medir, no deducir.** `HOME-A-SKELETON-SAYS-WE-ARE-ASKING-001` dejó ya la instrumentación que
faltaba: las dos líneas de log de los carriles llevan ahora el tiempo de la pregunta
(`parkings: N car parks around … in NNNN ms`, y `failed after NNNN ms`), y `OverpassClient` ya
registra qué mirror pasó, con cuánta paciencia, y cuándo la ranura no llegó a liberarse. Con un viaje
de campo se distingue:

- mirror lento (paciencia por mirror, `overpass … passed after N ms`),
- caminata agotada (`overpass budget spent after N ms`),
- **inversión de prioridad** (`overpass slot never came free; not asking`) ← la hipótesis.

## Diseño candidato, si se confirma

Una ranura que distinga **interactivo** (lo que el usuario está mirando) de **de fondo** (POI de la
cabecera, enriquecimiento): el de fondo cede el turno y espera; el interactivo no hace cola detrás de
una decoración. Ni dos ranuras ni más concurrencia — el servidor sigue dando lo que da; lo que cambia
es el ORDEN.

⛔ Y no se arregla subiendo `CONCURRENT_ASKS`: eso es exactamente lo que midió
`OVERPASS-A-MIRROR-WALK-MUST-OUTLIVE-THE-SERVER-IT-ASKS-001` (cuatro preguntas a la vez → un 429 y
tres 504 a los 14,1 s para una consulta que sola tarda 1,4 s).
