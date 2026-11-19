# ROUTE-A-JUNCTION-MUST-NOT-BORROW-THE-NEXT-STREET-001 · el matcher salta de vía para una sola medición y vuelve

**Estado:** 🔵 Abierto · sin rama · follow-up **medido** de
[ROUTE-THE-LINE-MUST-FOLLOW-THE-STREETS-IT-DROVE-001](route-the-line-must-follow-the-streets-it-drove-001.md)

## El defecto, medido
En un cruce, el Viterbi elige para **una** medición una vía distinta de la de sus vecinas y vuelve
en la siguiente. La línea dibuja un codo: sale a la otra calle y regresa.

`RouteMatchHarness` sobre 6 sesiones reales (2.334 fixes crudos, vías reales de Overpass): **11
picos**, con el vértice a **0,0 m de una vía** y los vecinos coincidiendo. Tamaños: 1-4 m salvo tres
— **24 m, 12 m y 10 m**. El de 24 m cae en el segundo vértice de la línea, que es el sitio más
visible (junto al marcador de origen).

## ⛔ Por qué NO se arregla con post-proceso
Es la trampa en la que ya se cayó una vez: ese vértice **no es geometría inventada**, es la
proyección de una medición real sobre otra vía. Colapsarlo —como hace
`RouteExcursions.collapseExcursions` con las excursiones de nodos— borraría la posición de un fix
medido. Por eso el colapso geométrico dispara **cero** veces aquí, y está bien que lo haga.

## Dónde vive el arreglo
En el **coste de transición** del HMM (`TrailMapMatcher.decodeSegment`): penalizar el cambio de vía
que se revierte en la medición siguiente, que es el remedio estándar (*way-change cost*) para el
flip-flop de Newson-Krumm. Alternativa más quirúrgica: post-procesar la **secuencia de candidatos
elegidos** —no la geometría— y, cuando el candidato *s+1* está en una vía distinta de *s* y *s+2*
y ambos vuelven a la misma, preferir su segundo mejor candidato.

## Riesgo, que es la razón de que esté abierto y no hecho
Tocar los costes del Viterbi es tocar **cómo se matchea todo**, no solo los cruces: una penalización
mal calibrada convierte giros reales en líneas que siguen recto. Y el peor caso medido son **24 m en
una sesión de 1 km que no era conducción** (4 km/h de media, 2 fixes de conducción). A zoom de
trayecto no se ve.

## Criterio de éxito
`RouteMatchHarness` antes y después, en las 6 sesiones: los picos de 10-24 m desaparecen, **y** los
km de los tres trayectos de conducción real (8,30 / 18,10 / 7,12) no se mueven más de ±1 %. Sin esa
segunda mitad, el arreglo no está probado: está pendiente de que algo se rompa.
