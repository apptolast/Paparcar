# PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001 · Filtros del carril de parkings

**Estado:** 🔵 En revisión · rama `feature/PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001` ·
cierra la **issue #43**

## Problema

Pedido por el product owner (#43): *«Deberíamos crear filtros para parkings valorando de qué forma
necesitamos filtrar: gratuitos, de pago, más cercanos, más barato… y más filtros que deberían entrar
en esta categoría.»*

## Lo que se puede filtrar, y lo que NO

| Filtro | ¿Se puede? | Por qué |
|---|---|---|
| **Gratuitos** / **De pago** | ✅ | `fee` es un sí/no de OSM |
| **Cubiertos** | ✅ | subterráneo y varias plantas son `kind` |
| ~~Más barato~~ | ❌ | **no hay precio.** OSM trae `charge` en texto libre («1.50 EUR/hour», «first 30 min free») con cobertura residual. Ordenar por un precio que no existe exige inventárselo |
| ~~Más cercanos~~ | ➖ | **ya es el orden**, y el único posible sin precio |
| ~~Cabe mi coche~~ | ❌ **por ahora** | el parking trae su gálibo (`maxHeightMeters`, 0-7% de cobertura) pero **el coche no trae su altura**: `CarbodyType` describe anchura e identidad visual. Compararlos exige inventar una altura por carrocería → ticket propio |

## Diseño

- `PublicParkingFilter` (FREE · PAID · COVERED) + `List<NearbyPublicParking>.filteredBy(...)`, puro
  y testeado.
- ⛔ **La tarifa DESCONOCIDA no cuenta ni como gratis ni como de pago** — es el 71 %, y decir
  «gratuito» de lo que nadie ha comprobado es el mismo engaño que el marcador ya se negó a hacer.
- ⛔ **Aquí los chips ESCONDEN filas**, al revés que los de gasolineras, que ordenan. Por eso el
  carril gana una frase propia: **«ningún parking cumple ese filtro»**, distinta de «aquí no hay
  parkings». La segunda es un veredicto sobre el mundo; la primera, sobre tu filtro — y sólo una se
  arregla tocando un chip.
- **Sin chip «Todos»**: el estado sin filtro es el normal. Pulsar el chip encendido lo apaga.
- No vuelve a preguntar a Overpass: la tienda es la misma respuesta.

## Criterio de éxito

- «Gratuitos» deja sólo los que la fuente declara gratis, nunca los desconocidos.
- Un filtro sin resultados dice que es el filtro, no que no haya parkings.
- El orden sigue siendo la distancia.

## Preguntas (para la revisión)

1. **¿Abro el ticket del gálibo?** Es el filtro que de verdad hace falta para una furgoneta, y
   necesita una decisión de producto: una altura por `CarbodyType` (inventada por nosotros) o
   preguntársela al usuario una vez en la ficha del coche.
2. **¿Los tres chips, o sólo tarifa?** «Cubiertos» es útil con lluvia, pero suma un tercer chip a
   una fila que ya se desplaza.

## Segunda vuelta (review del user, 20-09): «los filtros serán sticky como los tabs»

### Lo que se encontró al ir a hacerlo

**La barra de tallas de PLAZAS tampoco era sticky.** O sea que esto no era un rasgo que le faltara al
carril de parkings: era un rasgo que le faltaba a la **cabecera del carril**, y arreglarlo sólo en
parkings habría dejado dos carriles comportándose distinto en la misma hoja.

### ⛔ Por qué NO son dos `stickyHeader`

Lo obvio —hacer la barra de filtros su propio `stickyHeader`— **rompe los tabs**. En una
`LazyColumn` sólo hay un sticky a la vez: el siguiente empuja al anterior y lo echa fuera. La barra
de filtros habría expulsado la fila de pestañas, que es justo la fila cuya razón de ser es que
volver a Plazas desde el fondo de una lista no cueste un scroll.

**Tabs y filtros pasan a ser UN solo bloque fijado.** Además de no pelearse, dice la verdad de lo
que son: los filtros no son de la hoja, son **del carril**, y el carril lo eligen los tabs de
encima. Por eso el bloque cambia de contenido al cambiar de pestaña.

### Dónde vive ahora

Un composable nuevo, `LaneFilterBar`, dentro del `stickyHeader` de la cabecera. Un solo sitio decide
qué filtros se enseñan y lo decide la pestaña:

| Carril | Se enseña cuando | Por qué esa condición y no otra |
|---|---|---|
| Plazas | `hasAnySpots` | el feed es una suscripción viva: si hay plazas, hay algo que filtrar |
| Parkings | `hasAnyBeforeFilter` | encima de «no hemos podido preguntar», la barra haría creer que el filtro es la causa de que no haya nada |
| Gasolineras | — | entra por aquí en #42, no por dentro del carril |

⛔ **Las dos condiciones NO se unifican**: son preguntas distintas y colapsarlas inventaría un
veredicto para uno de los dos carriles.

### Consumidores auditados (segunda vuelta)

- `HomeParkingsLane` → deja de dibujar su barra y **pierde el parámetro `onFilterSelect`**.
- `spotsLane` → pierde su `item("filter_bar")`.
- `HomeParkingFilterBar` → pasa de privado a público, para que la cabecera pueda pedirlo.
- **Dev Catalog** (`StateGalleryScreen`) → ⚠️ **lo cazó el compilador del flavor mock**, no yo: las
  dos variantes del carril pasaban `onFilterSelect`. Ahora componen barra + carril igual que la
  hoja, que además es más fiel a lo que se ve en device. Es exactamente el fallo que
  `TEST-A-GUARD-MUST-NOT-READ-THE-FOLDER-NAME-001` dice que hay que correr con `:app`.

### Verificación

`./gradlew testAll :app:compileMockDebugKotlin :app:compileProdDebugKotlin` → BUILD SUCCESSFUL.

⏳ **Sin captura del estado final**: no hay device conectado (`PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`).
