# UI-ACTIVITY-HEADER-RESERVES-ITS-HEIGHT-001 · La cabecera de Actividad reservaba su altura al texto: envolver el cualificador movía el gráfico

**Estado:** ✅ Done — en master. Verificado: `:app:compileProdDebugKotlin` ✅ ·
`:shared:testDebugUnitTest` ✅ (guardarraíles Konsist incluidos). ⏳ falta verlo en device.

## Problema

La cabecera de la tarjeta de Actividad (`ActivityCardTitle`, `HistoryWeeklyChart.kt`) salta: unas
veces el cualificador de la derecha ocupa una línea y otras dos, y el gráfico entero se desplaza
8dp bajo él. Lo reporta el user el 09-09-2026: *«lo de km en n de ellos le pasa lo que le pasaba a
los aparcamientos, cada vez que cambiamos el composable salta porque a veces es 1 línea o 2»*.

Es la MISMA patología que [UI-STATS-HEADER-WRAPS-THE-WRONG-SIDE-001] (`c5630b99`) resolvió en el
otro lado de la fila. Aquel ticket puso la flexibilidad donde tocaba — la cuenta es el sujeto y no
envuelve, el cualificador es el que se dobla — pero dejó viva la causa de fondo: **la altura de la
fila se DEDUCE de dónde rompa el texto**.

Medido sobre los roles (`PaparcarType.kt`): `cardTitle` tiene `lineHeight = 24.sp`, `caption`
`lineHeight = 16.sp`.

| Caso | Altura de la fila |
|---|---|
| Cualificador en 1 línea (`«84,2 km»`) | `max(24, 16)` = **24sp** |
| Cualificador en 2 líneas (`«118,3 km en 18 de ellos»`) | `max(24, 32)` = **32sp** |

**8dp de salto**, y hay tres disparadores distintos, no uno:

1. **La frase.** `«118,3 km en 18 de ellos»` (23 caracteres a 12sp) cae justo en el límite del hueco
   que queda tras `«20 aparcamientos»` a 18sp. La variante completa (`«84,2 km»`) entra en una
   línea; la parcial (`history_activity_distance_partial`) no. Cambiar de filtro cambia la altura.
2. **El count-up.** `rememberCountUp` anima la cuenta de `5` a `20`, y ese `Text` tiene ancho
   intrínseco: mientras sube, el hueco que le queda al cualificador se ESTRECHA, la frase envuelve
   a dos líneas y vuelve a una al terminar. La fila respira durante toda la animación aunque el
   contenido final sea el mismo.
3. **El `Crossfade`.** Durante el fundido mide los dos hijos y se queda con el mayor: si uno de los
   dos es de dos líneas, la fila crece durante la transición y encoge al acabar.

## Doctrina violada

Nada de lo que sostiene contenido debajo puede tener una altura deducida de una medición de texto,
y menos si esa medición depende de una ANIMACIÓN. Es el mismo invariante que ya está escrito para
el peek (`feedback_peek_height_hysteresis`: una altura que oscila se estabiliza, no se recalcula) y
el mismo que motivó `UI-STATS-HEADER-WRAPS-THE-WRONG-SIDE-001`.

Corolario del que salió este ticket: **arreglar quién envuelve no arregla que envolver mueva
cosas.** Son dos invariantes, y `c5630b99` solo cerró el primero.

## Señales / datos disponibles

- `PaparcarType.current.caption.lineHeight` = 16sp → el techo del cualificador (`maxLines = 2`) es
  exactamente 32sp. No hace falta medir nada en runtime ni congelar un dp: el rol ya lo dice.
- El cualificador ya estaba limitado a `maxLines = 2` desde `c5630b99`, así que el peor caso es
  conocido y acotado.

## Diseño

**La fila reserva la altura de su caso PEOR en vez de deducirla del contenido de hoy.**

El cualificador pasa a vivir dentro de un `Box` con `weight(1f)` y
`heightIn(min = caption.lineHeight * QUALIFIER_LINES)`, con `contentAlignment = CenterEnd`:

- La fila ya no puede crecer: 32sp siempre, envuelva o no, esté animando o no, esté fundiendo dos
  frases o no. Los tres disparadores mueren con la misma línea, porque los tres atacaban la misma
  vía (la altura como función de la medición).
- El caso corto se CENTRA en el hueco reservado en vez de quedarse pegado arriba — que es lo que
  pasaría con `minLines = 2` sobre el propio `Text`, y por eso no se usa esa vía.
- `heightIn(min = …)` y no `height(…)`: si alguna métrica de fuente hiciera medir 32,4sp a dos
  líneas, el mínimo cede en lugar de recortar la segunda línea.
- El número sale del ROL (`QUALIFIER_LINES` × `caption.lineHeight`), no de un dp literal: si el rol
  cambia de tamaño, o el usuario sube la escala de fuente del sistema, el hueco reservado le sigue.
  `QUALIFIER_LINES` es a la vez el `maxLines` del texto y el multiplicador de la altura, así que lo
  reservado no puede desalinearse de lo permitido.

Coste asumido: **+8dp fijos** en la cabecera cuando la frase cabe en una línea. Elegido por el user
sobre las alternativas (acortar la frase a `«118,3 km · 18/20»` para que quepa siempre en una línea
—cambio de copy en 9 locales y sigue siendo una apuesta sobre el ancho en DE/PL—, o bajar el
cualificador a su propia línea, que abandona el diseño de dos extremos aprobado el 08-09).

## Criterio de éxito

Cambiar de filtro / de periodo en la ficha de vehículo y en el panel Stats, con datos que produzcan
las dos variantes de frase (completa y parcial): el gráfico no se mueve ni un pixel, ni al final ni
durante el count-up ni durante el fundido.

## Consumidores auditados

`ActivityCardTitle` es privado y su único llamante es `ActivityCard`, que tiene dos consumidores —
ambos quedan cubiertos por el mismo arreglo:

| Consumidor | Estado |
|---|---|
| `HistoryContent.kt:310` — ficha de vehículo | ✅ cubierto (mismo componente) |
| `StatsScreen.kt:154` — panel de Stats | ✅ cubierto (mismo componente) |

`grep` de la otra vía por la que la misma frase podría montar una fila:
`history_activity_distance_partial` solo se formatea en esos dos call sites (`HistoryContent.kt:303`
y `StatsScreen.kt:165`) y en ambos se pasa como `distanceText` a `ActivityCard`. No hay tercera
copia de la frase.

## Fuera de alcance (apuntado, no hecho)

Con la altura reservada, el gráfico ya no se mueve — pero el TEXTO del cualificador sigue
reflotando dentro de su hueco mientras el count-up ensancha la cuenta (disparador 2). Matarlo
también exige congelar el ancho de la cuenta a su valor FINAL (una capa invisible con el texto
objetivo debajo de la animada). El user eligió deliberadamente no incluirlo: el salto que molestaba
era el de la tarjeta, y esto es un temblor contenido dentro de una caja que ya no se mueve.
