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
