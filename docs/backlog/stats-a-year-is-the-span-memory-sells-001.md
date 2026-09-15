# STATS-A-YEAR-IS-THE-SPAN-MEMORY-SELLS-001 · «Todo» enseña un año, no medio

**Estado:** ✅ Done (2026-09-16) — mergeado a master con squash; el hash vive en `MEMORY.md`.
**Abierto:** 2026-09-16 · desde master `b6313a7d` · fleco de
[`stats-intelligence-roadmap-001.md`](stats-intelligence-roadmap-001.md)

## Problema

El gráfico de «Todo» dibuja **seis meses** (`ALL_MONTHS_CAP`), y seis meses **no pueden enseñar una
estación**: quien aparca distinto en agosto que en febrero ve dos mitades de esa diferencia y nunca
la forma. Justo la pantalla que se vende como memoria es la que no llega a un año.

## Doctrina violada

Ninguna: el tope estaba declarado y documentado desde su día. Lo que hay es una decisión de producto
que se quedó corta cuando la ola de estadísticas convirtió el gráfico en una serie de VARIAS
métricas — la frase «gasté más en verano» sólo existe si el eje llega a verano.

## Diseño

1. `ALL_MONTHS_CAP` **6 → 12**. No cuesta nada: los buckets siempre se derivaron, nunca se guardaron.
2. ⛔ **Las etiquetas se alternan a partir de siete barras.** Medido a 360 dp: doce buckets dejan
   ~15 dp por barra y un «sep» ocupa el doble, así que el eje se convertía en un borrón. Se pinta
   una de cada dos **y siempre la última**, que es el mes en curso y la referencia de todo el
   gráfico.
3. El tope sigue siendo del DIBUJO, no de la cifra: el total y los km siguen contando todo el
   historial, como estaba documentado.

## Por qué esto es el lado Plus

Free no llega nunca: su ventana son siete días. El año es exactamente lo que «la app recuerda por
ti» significa, y no hace falta ningún gate para que lo sea.

## Criterio de éxito

- **Test**: «Todo» da doce barras y la última es el mes en curso.
- `testAll` + guardarraíles + compila mock/prod/iOS.

## Consumidores auditados

| Punto | Clasificación |
|---|---|
| `VehicleHistoryCalculator.ALL_MONTHS_CAP` | 🔧 6 → 12, y pasa a `internal` para que su test pueda citarlo |
| `ActivityBarCanvas.drawBarChart` | 🔧 etiquetas alternas por encima de siete barras |
| `HistoryChartWindowTest` | 🔧 un test nuevo; el comentario de cabecera decía «6 meses» |
| Todo lo demás del gráfico | ✅ exento: la serie, la métrica y el reductor no cambian |

## Verificación (16-09-2026)

- `testAll` verde · `HistoryChartWindowTest` tests="6" failures="0" (+1: doce barras y la última es
  el mes en curso) · guardarraíles a cero · mock/prod/iOS compilan.
- **En device (Redmi, mock)**: doce barras con el eje legible —«nov · ene · mar · may · jul · sep»—
  y septiembre, el mes en curso, rotulado y en negrita.
