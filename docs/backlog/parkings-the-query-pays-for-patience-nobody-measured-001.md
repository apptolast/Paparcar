# PARKINGS-THE-QUERY-PAYS-FOR-PATIENCE-NOBODY-MEASURED-001 · dos números de la consulta de parkings que nadie ha vuelto a medir

**Estado:** 🔵 Abierto · sin empezar · sin rama · **son DOS experimentos de ~2 min cada uno contra el
endpoint**, no un refactor · sale de una pregunta del user durante
[UX-THE-HOME-MUST-EARN-ITS-FIRST-GLANCE-001](ux-the-home-must-earn-its-first-glance-001.md):
*«¿podríamos reducir el tiempo de petición de parkings? ¿por qué tarda tanto?»*

## Problema

Una consulta de parkings tarda **1,4 s en caliente, 4,7–7,7 s en frío y hasta 40 s** en el peor caso
(`HomeViewModel`, nota de `PARKINGS-A-REMEMBERED-MAP-ANSWERS-BEFORE-THE-NETWORK-001`). Al
investigarlo, **la mayor parte de ese tiempo no es la consulta**: es la cola de uno
([OVERPASS-A-QUEUE-OF-ONE-MUST-LET-THE-USER-GO-FIRST-001](overpass-a-queue-of-one-must-let-the-user-go-first-001.md))
y el paseo de espejos, los dos ya medidos y acotados.

Pero quedan **dos números de la consulta en sí** que no se han vuelto a medir desde que se pusieron,
y los dos son baratos de comprobar.

## Doctrina violada

Ninguna. ⚠️ Y conviene decirlo claro para que este ticket no se lea como una crítica al subsistema:
`OverpassWalkPatience`, `OverpassQuota` y `OverpassResponseHealth` **están medidos contra el endpoint
real y documentan sus cifras**. Lo que este ticket mira son dos números que quedaron **fuera** de
ese barrido porque viven en la consulta, no en el paseo.

## Señales / datos disponibles

La consulta, tal cual (`OsmPublicParkingParser.buildQuery`):

```
[out:json][timeout:20];
(
  nwr(around:R,lat,lon)[amenity=parking][!parking];
  nwr(around:R,lat,lon)[amenity=parking]["parking"!~"^(roadside|…)$"];
);
out center 1000;
```

✅ **Lo que ya está bien y no se toca:** `out center` (sólo el punto, no la geometría), el tope de
1.000, y el filtro de acceso hecho en el cliente **a propósito** — *«la consulta no puede expresarlo
sin doblarse otra vez»*.

## Qué pide medir — DOS experimentos

### 1 · El `[timeout:20]` que le concedemos al servidor

`OsmPublicParkingsSource.TIMEOUT_S = 20`, y **la respuesta buena más lenta medida fue 7,72 s**
(Málaga, por fibra, 14-09-2026). Ese número es la paciencia que el SERVIDOR se concede a sí mismo, y
`OverpassWalkPatience.declaredServerBudgetMs()` lo **lee de la consulta** para dimensionar la espera
del cliente — así que bajarlo acorta también lo que el paseo le aguanta a un servidor atascado antes
de pasar al siguiente.

**Hipótesis:** 12 s (la cifra que ya usa `SLOWEST_MEASURED_ANSWER_MS`) basta, y recorta la cola del
peor caso.

⚠️ **Y el riesgo que lo hace un experimento y no un cambio:** Overpass **planifica** con el timeout
declarado. Demasiado bajo y el servidor **rechaza o aborta** la consulta en vez de contestarla — con
lo que se cambiarían respuestas lentas por respuestas que no llegan. Hay que medirlo en una ciudad
**grande y densa**, no en una fácil.

### 2 · ¿Las dos sentencias de la unión podrían ser una?

La unión pide dos veces lo mismo con filtros distintos: `[!parking]` (sin la etiqueta) y
`["parking"!~"roadside…"]` (con la etiqueta pero no de calzada).

**Hipótesis:** si en Overpass QL `!~` **ya casa con la etiqueta ausente**, la primera sentencia es
redundante y la consulta hace la mitad del trabajo.

⛔ **No se da por cierto, y hay un motivo para sospechar lo contrario:** lo más probable es que
estén separadas **precisamente porque `!~` no casa con ausente**, y entonces unirlas **borraría del
mapa todos los parkings sin la etiqueta `parking`** — que son muchos. El fallo sería **silencioso**:
una lista más corta, sin error.

**Cómo se comprueba antes de tocar nada:** una consulta a mano contra el endpoint, con y sin la
primera sentencia, sobre la misma zona, **comparando el número de elementos**. Si sale el mismo, la
sentencia es redundante; si sale menos, se cierra este punto y se anota por qué.

## Criterio de éxito

Una medición escrita en este doc por cada experimento, con ciudad, hora y cifras — y el cambio
aplicado **sólo si la medición lo respalda**. Un «no» medido cierra el punto igual de bien que un
«sí»: lo que no vale es dejarlos sin mirar.

Si el punto 2 sale redundante, su arreglo lleva **un test de paridad** que compare el recuento de las
dos formas de la consulta sobre un payload fijo.

## Lo que este ticket NO hace

- ⛔ Subir `CONCURRENT_ASKS`. Medido: cuatro a la vez → un 429 y tres 504 a 14,1 s.
- ⛔ Quitar el paseo de espejos. Un espejo es un punto único de fallo y ya costó
  `OSM-ONE-MIRROR-IS-A-SINGLE-POINT-OF-FAILURE-001`; además hay espejos **que mienten** (200 en
  0,3 s con lista vacía).
- ⛔ Subir `WALK_CEILING_MS` (30 s). Pasado eso la pregunta ya no es «va lento» sino «hay alguien».
- ⛔ Tocar `out center`, el tope de 1.000 ni el filtro de acceso del cliente.
- ⛔ Prometer que esto se nota en la home: **no se nota**, porque la home ya no espera a la red
  (pinta lo recordado y refresca detrás). Esto es latencia del carril cuando el usuario lo abre.
