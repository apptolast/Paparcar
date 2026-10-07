# UI-A-SCROLLING-CHIP-ROW-SAYS-SO-001 · Una fila de chips que se desplaza tiene que decirlo

**Estado:** ✅ Done · master `4fab17605` · cierra la **issue #50**

## Problema

Pedido por el product owner (#50): en **Mis vehículos**, el tercer filtro de actividad dice
«buscando |» — cortado a hueso contra el canto de la pantalla. Propone renombrarlo a «Tiempo
búsqueda» con el scroll indicado, o texto autoajustable.

La fila YA se desplaza (`LazyRow`, desde la medida del Oppo del 15-09: tres chips no caben en
360 dp). Lo que falta no es el scroll: es que **se note**. Un chip cortado a hueso se lee como un
fallo de maquetación; el mismo chip desvaneciéndose se lee como «hay más, arrastra».

## Decisión sobre el renombrado

**No renombro, y la razón es medible**: «tiempo de búsqueda» (17 caracteres) es MÁS largo que
«buscando plaza» (14), así que no habría arreglado el corte — sólo lo habría movido una letra. Y
cuesta vocabulario: el chip comparte deliberadamente las palabras del tile (`stats_tile_search_time`),
una idea una redacción. Si aun así lo prefieres, es cambiar una key y sus nueve locales.

## Diseño

`Modifier.papHorizontalFadingEdges(state)` en `ui/components/PapFadingEdges.kt`:

- Difumina **sólo el borde alcanzable** (`canScrollBackward` / `canScrollForward`), animado, para
  que al llegar al extremo el borde se apague en vez de desaparecer de golpe.
- Difumina el **CONTENIDO** con `BlendMode.DstIn`, no pinta una cortina del color del fondo: así
  vale sobre card, hoja o mapa sin que el call site tenga que acertar el color de su fondo — y el
  día que la card cambie de tono no quedan dos bordes distintos.

## Consumidores auditados

| Fila | Decisión |
|---|---|
| `HistoryWeeklyChart.ChartMetricRow` (la de la issue) | ✅ |
| `ReportPeek` — los cinco chips de tamaño de plaza | ✅ también se sale de 360 dp |
| `StatsScreen` — píldoras de vehículo | ✅ un garaje de tres coches ya se sale |
| `HomeSheetContent` — carrusel de tarjetas de vehículo | ❌ no son chips: las tarjetas son anchas y el corte a media tarjeta ya se lee como carrusel |

## Criterio de éxito

- En Mis vehículos, el tercer chip se desvanece en el canto en vez de cortarse; al arrastrar hasta
  el final, el desvanecido se apaga.
- Ninguna etiqueta cambia de texto.
