# UI-STATS-HEADER-WRAPS-THE-WRONG-SIDE-001 · La flexibilidad del título de Actividad estaba en el lado equivocado

**Estado:** ✅ Done (2026-09-09) · suite `:shared:testDebugUnitTest` verde · pendiente solo verlo en mano

## Problema
Capturas del user en device (09-09-2026, Redmi, ES, filtro «Este mes»):
1. En la tarjeta de Actividad, «20 aparcamientos» se parte en DOS líneas («20» arriba,
   «aparcamientos» debajo) mientras «118.3 km en 18 de ellos» se queda entera a la derecha.
   Es exactamente lo contrario de la jerarquía declarada por el propio código: el contador es el
   SUJETO de la tarjeta y la distancia un cualificador [ACTIVITY-CARD-TITLE-001].
2. En la tarjeta hero del vehículo, la línea de estado «Lugar la Cañada 205 · hac…» se come la
   dirección con ellipsis a 1 línea cuando cabría perfectamente en 2.

## Causa
`ActivityCardTitle` (HistoryWeeklyChart.kt) repartía la flexibilidad al revés:
- el contador llevaba `Modifier.weight(1f)` **sin `maxLines`** → recibía el espacio SOBRANTE y
  envolvía;
- la distancia iba sin weight y con `maxLines = 1` → se medía a su ancho intrínseco completo y
  empujaba al contador.

`VehicleStateLine` (VehiclePageContent.kt): `maxLines = 1` + `Ellipsis` sobre la única línea cuyo
payload es el DÓNDE.

## Doctrina violada
La jerarquía «el contador es el sujeto, la distancia el cualificador» estaba escrita en el
comentario del composable pero el layout la contradecía. No rompe guardarraíl automático.

## Diseño
El invariante vive en `ActivityCardTitle`, el ÚNICO sitio que pinta ese título (lo consumen la
ficha de vehículo y el panel de Stats vía el mismo `ActivityCard` — arreglo único, dos pantallas):
- Contador: ancho intrínseco, `maxLines = 1` — nunca envuelve.
- Distancia: `weight(1f)` + `maxLines = 2` + `TextAlign.End` + `Ellipsis` — toma lo que sobra y
  dobla a segunda línea antes de truncar.

`VehicleStateLine`: `maxLines = 2` (el ellipsis queda como último recurso).

## Criterio de éxito
En device: «20 aparcamientos» en una línea; la frase de km dobla a la derecha en dos líneas si no
cabe; la dirección del hero muestra calle + tiempo relativo en dos líneas sin comerse el lugar.

## Consumidores auditados
`grep ActivityCard` → `HistoryContent.kt` (ficha) y `StatsScreen.kt` (panel): ambos pasan por
`ActivityCardTitle`, cubiertos por el mismo cambio. `history_activity_distance_partial` no se pinta
en ningún otro sitio. Sin estados nuevos → sin cambios en galería/Dev Catalog.
