# ROUTE-THE-LINE-MUST-FOLLOW-THE-STREETS-IT-DROVE-001 · atacar los huecos y la precisión del matcher

**Estado:** ✅ Done · los huecos y el descarte de fixes miden limpio, el banco queda versionado, y
lo único que sobrevive a la medida se abre aparte:
[ROUTE-A-JUNCTION-MUST-NOT-BORROW-THE-NEXT-STREET-001](route-a-junction-must-not-borrow-the-next-street-001.md)

## Encargo
Tras [ROUTE-A-SNAPPED-LINE-MUST-NOT-INVENT-GROUND-001] el user pidió atacar las dos cosas que
quedaban nombradas: **los huecos** (53 % de las trazas de sep-oct traían tramos inferidos) y la
**precisión del matcher en los cruces**.

## El método, que es la mitad del ticket

Hasta ahora la calidad del matcher no se podía medir, solo opinar. Así que lo primero fue poder
medirla: `RouteMatchHarness` (androidUnitTest) corre el matcher **real** sobre los fixes **crudos**
de 6 sesiones de campo con las **mismas vías de OSM** que pediría la app (misma lista `drivable`,
mismo margen de bbox de 0,004°, mismo `out tags geom`), y contesta por sesión: fixes sin vía a
≤120 m y terreno que se tiraría con ellos, huecos reconstruidos, desvío de la distancia dibujada
respecto a la medida, y los picos con su enlace a OSM.

⛔ Se salta solo si el corpus no está en disco (vive fuera del repo por decisión del user, 03-09),
así que puede estar versionado sin romper la suite de nadie.

## Lo medido (6 sesiones, 2.334 fixes crudos, vías reales de Overpass)

| sesión | admitidos | sin vía ≤120 m | huecos | cuts | km medidos | km dibujados | desvío | picos |
|---|---|---|---|---|---|---|---|---|
| …3459470 | 212 | **0** | **0** | **0** | 8,30 | 8,30 | +0,1 % | 1 (3 m) |
| …8017537 | 80 | **0** | **0** | **0** | 1,09 | 0,99 | −9,5 % | 1 (24 m) |
| …3856404 | 209 | **0** | **0** | **0** | 18,10 | 18,11 | 0,0 % | 2 (2, 12 m) |
| …8539901 | 13 | **0** | **0** | **0** | 0,38 | 0,36 | −7,2 % | 1 (10 m) |
| …0300224 | 138 | **0** | **0** | **0** | 2,76 | 2,85 | +3,4 % | 1 (4 m) |
| …0763943 | 189 | **0** | **0** | **0** | 7,12 | 7,17 | +0,7 % | 5 (1-2 m) |

**1 · Los huecos ya no existen.** Cero spans inferidos y cero cuts en las 6 sesiones, frente al
53 % de las trazas de sep-oct. Los huecos de entonces eran de **captura escasa**, no de diseño del
matcher: con la cadencia actual (p50 2-5 s) no hay agujero que reconstruir. ⚠️ No se pudo cerrar la
cronología del build que corría el móvil en sep-oct (los tags `v1.4.0`/`v1.5.0` no están en local),
así que la causa queda **nombrada pero no probada**.

**2 · El matcher no tira terreno.** Cero fixes sin vía a ≤120 m, así que el descarte de fixes
fuera de calzada ([ROUTE-LINE-ONROAD-001]) no está perdiendo nada hoy. ⚠️ Una primera medición dijo
16-39 fixes fuera de vía por sesión: era **mi métrica**, que medía al vértice más cercano con un
umbral de 40 m en vez de proyectar punto-segmento con el radio real de 120 m. Corregida.

**3 · La distancia dibujada es fiel en los viajes que son conducción**: +0,1 %, 0,0 % y +0,7 % en
los tres trayectos de verdad (8, 18 y 7 km). Los desvíos de −9,5 % y −7,2 % son sesiones de 1 km y
0,4 km a ~4 km/h de media con 2 y 5 fixes de conducción: maniobra lenta, no viajes. No hay sesgo
sistemático de kilometraje que justifique cambiar de dónde sale la distancia.

**4 · Quedan 11 picos, y son casi invisibles**: 1-4 m salvo tres (24, 12 y 10 m). El vértice del
pico está a 0,0 m de una vía y sus vecinos coinciden: es **way-hopping en un cruce** — el matcher
elige para UNA medición una vía distinta y vuelve.

## ⛔ Corrección sobre el ticket anterior: su arreglo no hace nada en datos reales

[ROUTE-A-SNAPPED-LINE-MUST-NOT-INVENT-GROUND-001] (master `c3819d502`) salió con la cifra «206
excursiones, 2,345 km de línea fantasma». **Esa cifra medía el DEFECTO en las trazas viejas,
simulado en Python — no el EFECTO del código Kotlin.** Medido ahora con el banco: la salida del
matcher es **idéntica con y sin aquel guard** (mismos puntos, mismos km, mismos picos) en las 6
sesiones. Dispara **cero** veces.

La razón: aquel guard comparaba solo los DOS EXTREMOS de un paso inter-medición, y la forma real
vive **dentro** de la cadena de nodos (…X → Y → X…) mientras el paso sí va a algún sitio.

Lo corregido en esta rama: `RouteExcursions.collapseExcursions` recorre el paso con una pila y
colapsa las excursiones **anidadas**, sin mover nunca la medición de la que sale el paso. Es la
generalización correcta de aquel guard… y **también es un no-op en los datos de hoy**, porque los
11 picos que quedan no son geometría inventada: su vértice es la proyección de una medición real
sobre otra vía. Se queda porque la forma vieja existió y ahora está bien expresada y testeada, no
porque arregle lo que se ve hoy.

## Lo que NO se ha hecho, y por qué — abierto en [ROUTE-A-JUNCTION-MUST-NOT-BORROW-THE-NEXT-STREET-001]
El way-hopping en cruces se arregla en el **coste de transición del Viterbi** (penalizar el cambio
de vía que se revierte de inmediato), no con post-proceso geométrico: borrar esos vértices sería
borrar la posición de una medición. Es un cambio real en el HMM, con riesgo de regresión real, sobre
un defecto cuyo peor caso medido son **24 m en una sesión de 1 km que no era conducción**. No se
toca sin decidirlo a la vista de esto.

## Criterio de éxito
El banco. Cualquier cambio futuro del matcher se mide con él antes y después, en vez de razonarse.

## Tests
`RouteExcursionsTest` ahora son 7 (añadidos: excursión interior, anidada, y que la medición de
salida nunca se mueva). Suite completa **4083 verdes, 0 fallos**.
