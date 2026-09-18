# FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001 · La gasolinera más barata y la más cercana se señalan

**Estado:** 🔵 En revisión · rama `feature/FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001` ·
worktree `../PaparcarKMP-issue33` · cierra la **issue #33**

## Problema

Pedido por el product owner (#33): como no hay precios en todos los países, marcar con un badge la
**más barata de la zona** y otro para la **más cercana**, y decidir qué pasa cuando coinciden.

## Por qué «más cercana» NO es redundante con el orden

La lista se ordena con `compareBy { price == null }.thenBy { distanceMeters }` (`NearbyOrdering`):
primero las que tienen precio, cada bloque por distancia. Así que **la primera fila es la más cercana
CON PRECIO**, y en un país cuyo feed no trae precios —que es la mayoría del mundo, y justo el motivo
de la issue— la gasolinera realmente más cercana puede quedar abajo del todo y sin precio. El badge
dice lo que el orden no puede decir.

## Diseño

`domain/fuel/FuelStandouts.kt`: función pura sobre la lista, **no** un caso de uso — su resultado no
aparece en ningún diagnóstico ni mueve un pin. [DET-VERDICT-NOT-PREDICATE-001]

Lo que se NIEGA a afirmar, que es el grueso del diseño:

| Caso | Decisión | Por qué |
|---|---|---|
| Una sola gasolinera | sin badge | con una no hay «la más barata», hay una gasolinera |
| Empate de precio | sin badge de barata | un empate no tiene ganador, y el usuario lo comprueba en el surtidor |
| Divisas mezcladas | sin badge de barata | 1,42 £/l no es más barato que 1,65 €/l porque el número sea menor |
| Sin fix de ubicación | sin badge de cercana | «la más cercana» sin posición es una afirmación sobre nada |
| Coinciden las dos | **UN badge combinado** | dos badges en una fila estrecha es ruido; quitar uno pierde información que la issue pide |

UI: el badge va en la línea del subtítulo, no en el trailing —ahí vive el precio, y la afirmación es
sobre la gasolinera, no sobre la cifra—. Contenedor de marca cuando la afirmación toca al precio,
neutro cuando es solo distancia (que la fila ya dice en metros dos palabras después).

## Consumidores auditados

| Sitio | Decisión |
|---|---|
| `HomeFuelStationRows` | ✅ calcula los standouts UNA vez por respuesta y los reparte |
| `HomeFuelStationRow` | ✅ parámetro `badge`, opcional |
| Mapa (cápsula de gasolinera) | ❌ fuera de alcance de la issue — ver Preguntas |
| Peek de gasolinera | ❌ ídem |
| Dev Catalog | ❌ la galería no tenía filas de gasolinera; nada que sincronizar |

## Criterio de éxito

- En una lista con precios, la más barata lleva badge y la más cercana también.
- Cuando son la misma, sale un solo badge que lo dice.
- Ningún badge miente: ni con empate, ni con divisas mezcladas, ni sin ubicación.

## Verificación

`FuelStandoutsTest` — 6 tests en verde, cinco de ellos sobre afirmaciones que NO se deben hacer.
