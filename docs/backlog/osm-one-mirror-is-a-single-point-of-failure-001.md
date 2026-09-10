# OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001 · Un servidor caído no puede dejar sin gasolineras a medio mundo

**Estado:** ✅ Done — en master (hash en `MEMORY.md`, por construcción del cierre)

⚠️ **Esto MITIGA, no arregla**: Overpass sigue siendo un servicio público sin SLA. Con los tres
servidores caídos a la vez seguirá sin haber gasolineras fuera de España — pero diciéndolo («no
pudimos preguntar»), que es lo único que estaba en nuestra mano.

## Problema

Toda la cobertura mundial de gasolineras —los 160+ países que no tienen adaptador nacional— cuelga
de **una sola instancia pública**: `overpass-api.de`. El 09-09 se cayó **dos veces en una tarde**
(504s, luego `ConnectTimeoutException`), y con ella desapareció la función entera fuera de España.
No es un incidente: es una instancia pública, gratuita y sin SLA, sosteniendo una función de
producto.

## Lo medido (09-09-2026, consulta de CONTROL en Madrid donde sabemos que hay 60 gasolineras)

| Servidor | HTTP | Tiempo | Elementos | `timestamp_osm_base` |
|---|---|---|---|---|
| `overpass-api.de` (el que usamos) | — | 21,7 s | caído | — |
| `overpass.kumi.systems` | — | 30,0 s | caído | — |
| `overpass.osm.ch` | **200** | **0,3 s** | **0** ⛔ | `116992` (¡no es una fecha!) |
| `overpass.private.coffee` | 200 | 1,8 s | **60** ✅ | `2026-07-15T15:22:01Z` |
| `overpass.openstreetmap.ru` | — | 21,1 s | caído | — |

## ⛔⛔ El hallazgo que decide el diseño: un 200 puede mentir

`overpass.osm.ch` responde **rápido, con 200, y con la lista VACÍA** — su base de datos está rota
(su `timestamp_osm_base` es un contador, no una fecha). Un failover ingenuo —«prueba el siguiente
solo si hay error HTTP»— lo elegiría por ser el más rápido y la app diría **«no hay gasolineras en
Madrid»**. Eso es peor que la caída: la caída se ve, la mentira no.

→ **El criterio de validez no puede ser el código HTTP.** Es el `timestamp_osm_base`: una base sana
lo publica como fecha ISO. Sin fecha parseable, la respuesta se descarta y se pasa al siguiente
servidor, aunque haya venido con un 200 impecable. Es una señal del propio servidor sobre SÍ MISMO,
y no depende de que la zona consultada tenga datos o no — que es lo que la hace usable: una lista
vacía en mitad del desierto es legítima y debe seguir siéndolo.

## Diseño

- `data/fuel/OverpassEndpoints` — la lista ORDENADA, con su razón de estar cada una: la de OSM
  primero (es la de referencia), los relevos detrás. Constante, no configuración remota: una lista
  que puede cambiar sola es una lista que no se puede depurar.
- `OsmFuelStationsSource` recorre la lista hasta la primera respuesta **válida** (HTTP ok **y**
  `timestamp_osm_base` con fecha). Un servidor que falla o que miente no aborta la consulta: pasa
  el turno. Solo cuando se acaban todos, el resultado es un fallo — y entonces la sección dice
  honestamente «no pudimos preguntar».
- Cada salto se **loguea** con el servidor y el motivo, para que un diagnóstico pueda citar por qué
  la respuesta vino de un relevo (misma doctrina que el testigo del viewport).
- ⚠️ Los datos de un relevo pueden ser más VIEJOS (private.coffee iba 2 meses por detrás). Para
  gasolineras es aceptable —una estación no se muda en dos meses— y NO se convierte en un precio:
  OSM no publica precios. Se anota aquí para que nadie lo descubra por sorpresa.

## Criterio de éxito

- Con el primer servidor caído, las gasolineras salen igual (por el relevo).
- Con un servidor que responde 200 y base rota, se DESCARTA y se sigue: nunca produce un «no hay».
- Con todos caídos: fallo honesto, sin inventar lista vacía.
- Tests con `MockEngine`: caída → relevo · 200-con-base-rota → relevo · todos mal → fallo · el
  primero sano no consulta a nadie más.
- Suite · assembles · klib iOS.

## Verificado en device (09-09-2026, prodDebug hash comprobado)

Con el servidor principal ya recuperado, contestó él y el relevo no llegaba a entrar — así que se
forzó con un **experimento controlado**: un host inexistente el primero de la lista. Resultado en
el log del emulador, con el GPS en Francia:

```
W OsmFuelStations: overpass overpass-que-no-existe.paparcar.invalid passed:
                   Unable to resolve host … No address associated with hostname
D HomeViewModel:   fuel viewport: 33 stations around 48.7886,2.2769
```

El servidor muerto **cede el turno diciendo por qué**, y las 33 gasolineras llegan igual por el
siguiente. El experimento se revirtió; no queda rastro en el código.

Antes de esto, con el principal caído de verdad, la app decía «no pudimos preguntar» — correcto
entonces, innecesario ahora.

## Consumidores auditados

- **`OsmFuelStationsSource`** — único consumidor de la lista. Su firma gana `endpoints` con
  default, así que el DI no cambia y los tests pueden inyectar los suyos.
- **`FuelStationsRegistry`** — sin tocar: sigue viendo una fuente que responde o falla; que por
  dentro pregunte a tres servidores no es asunto suyo.
- **`FuelHttpClientTest`** — su fixture no traía `timestamp_osm_base` y el guard nuevo lo rechazó:
  test actualizado. Buena señal, no daño colateral — el guard cazó una respuesta que no lo era.
- **España** — intacta: el Ministerio no pasa por aquí.
