# STATS-THE-DETECTION-SHOWS-ITS-HIT-RATE-001 · La detección enseña su acierto, en porcentaje y en anillo

**Estado:** ✅ Done · master `4ef6cd541` · cierra la **issue #51**

## Problema

Pedido por el product owner (#51): la baldosa «detectados solos» necesita feedback visual — una
barra de progreso circular o un porcentaje (p. ej. «92% efectividad») que refuerce la precisión del
algoritmo.

Hoy la baldosa dice **«12/13»**, que obliga a dividir mentalmente para contestar la única pregunta
que esa cifra responde: *¿va bien la detección?*.

## Diseño

- **El porcentaje pasa a ser la cifra** (`stats_value_percent`, nueve locales) y el recuento baja a
  la línea de nota, con **la misma frase que ya usa la cobertura** (`stats_tile_coverage`, «12 de 13
  viajes»): sin el recuento, un porcentaje suelto no dice sobre cuántos viajes se calculó.
- **Anillo** (`PapStatTile.progress`): pista tenue + arco de la fracción desde las 12 en punto,
  creciendo con el mismo `rememberCountUp` con el que trepan las cifras — dos velocidades en una
  tarjeta se leen como dos sucesos.
- ⛔ **El anillo sólo existe donde la figura ES una proporción.** Un anillo sobre un gasto o unos
  kilómetros tendría que inventarse un máximo, y un máximo inventado es una barra que miente.
- **El verde es deliberado y es el único de la rejilla**: el anillo dice «esto lo ha hecho la app»,
  lo más cerca de identidad de marca que hay en una pantalla de cifras. La cifra sigue en
  `onSurface`. [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
- ⛔ **Nunca 100% si falta uno** (`detectionSharePercent`): 249 de 250 es 99,6%, y «100%» con un
  aparcamiento marcado a mano es la app diciendo que no falla nunca justo donde el usuario puede
  comprobar que sí. 99% es la respuesta honesta. Dos tests lo fijan.

## Consumidores auditados

| Sitio | Decisión |
|---|---|
| `StatTileKey.AUTO_DETECTED` | ✅ porcentaje + nota + anillo |
| Las otras 9 baldosas | ❌ ninguna es una proporción — sin anillo, y el parámetro es opcional |
| `stats_value_share` («%1$d/%2$d») | ✅ **retirada de los 9 locales**: era su único lector |
| `StatsScreen` (panel Plus) | ❌ no dibuja esta baldosa |

## Criterio de éxito

- La baldosa contesta «¿va bien?» sin dividir.
- El anillo y el número llegan juntos al cambiar de periodo.
- Con 249 de 250, la app dice 99%.
