# UI-IDENTITY-IS-SAID-ONCE-001 · El color dice ESTADO donde la identidad ya es constante

**Estado:** ✅ Done · rama `refactor/UI-IDENTITY-IS-SAID-ONCE-001-color-says-state`

Tres correcciones de la ficha del vehículo pedidas por el user tras vivir con
[`stats-ui-001.md`](stats-ui-001.md) en device. Las dos primeras son el mismo problema visto en dos
sitios; la tercera es higiene que sale al tirar del hilo.

## Problema

**La página de Vehículos es un pager por coche**, así que dentro de ella la identidad del vehículo
es una CONSTANTE: todo lo que hay debajo de la cabecera pertenece a ese coche por construcción. Y
sin embargo el color de identidad se gasta ahí dentro tres veces más:

1. **La card de sesión activa se rellena** con el contenedor de identidad (azul en un coche BT). Un
   relleno es el canal más ruidoso que tiene la app y lo estamos gastando en la constante, mientras
   que lo que de verdad varía en esa lista —cuál está viva— se queda sin canal propio. El user
   reporta que el azul "chirría"; el verde no tanto sólo porque se parece al de marca.
2. **La card de vehículo lleva borde de color.** Esa card dice ya "vigilado por Bluetooth" cuatro
   veces: el punto del chip de la pestaña, el glifo antes del nombre, el icono de la línea de estado
   (añadido en STATS-UI-001) y el borde. De los cuatro, el borde es el único que no se mira: es un
   marco, así que no entrega un dato — tiñe la lectura de todo lo que hay dentro, incluidos el
   combustible y el estado, que no van de eso.
3. Al retirar del titular del gráfico una flecha decorativa (ver STATS-UI-001) quedó a la vista que
   **`LowActivitySummary` lleva un `FontWeight.Bold` inline** y otra copia de la misma flecha.

## Doctrina violada

- **El estado se escribe, no se tiñe** [UI-COLOR-DOCTRINE-001]. Un relleno de color sobre la sesión
  activa es exactamente teñir un estado.
- **Precedente directo del propio user (28-08):** el gráfico de actividad se pintó con el color de
  identidad del coche y **lo revirtió en device**; desde entonces las barras son verde marca en la
  página de todos los coches, con el argumento de que *el gráfico es chrome de la app, no anatomía
  de identidad*. La card de sesión activa es el mismo tipo de objeto y merece el mismo trato.
- **Una exención de guardarraíl no puede cubrir más de lo que se ganó.** `HistoryWeeklyChart` está
  en `WEIGHT_ALLOWLIST` porque su CANVAS necesita `fontWeight` en `TextMeasurer` — y esa exención,
  al ser por fichero, está tapando un `FontWeight.Bold` de Compose corriente en `LowActivitySummary`.
  Es literalmente el patrón que `TypographyGuardrailTest` documenta con ConnectivityBanner:
  *«estar en la allowlist es lo que le permitió quedarse sin familia; la exención tapaba el fallo, no
  lo documentaba»*. [UI-TYPE-SYSTEM-HYGIENE-001]

## Diseño

**Regla que ordena las tres:** *donde la identidad ya es constante, el color habla de estado.*

1. **La línea de tiempo deja de contar identidad.** La card activa toma el `primaryContainer` de la
   app; el punto pulsante, el raíl y la cabecera «Activa» pasan a marca. Consecuencia buscada: el
   parámetro `watch` de `HistoryContent` se queda sin uso y **se retira**, en vez de quedarse como
   parámetro muerto que el próximo lector creerá que hace algo.
2. **El borde de la card de vehículo pierde el color, no el borde.** La card necesita canto contra
   el fondo, así que se queda la hairline neutra del resto de la app. En **Home NO se toca**: allí
   varias cards conviven y el borde sí hace trabajo de distinguir una de otra.
3. **`LowActivitySummary` se borra.** Existía por un problema de dibujo —«un gráfico con pocos
   buckets renderiza un pegote a todo lo ancho»— que era el síntoma de que la barra tomaba una
   FRACCIÓN del ancho disponible. Con el hueco fijo de STATS-UI-001 una semana con un aparcamiento
   dibuja una barra corta entre siete, legible. Al irse se lleva el Bold inline, la flecha decorativa
   y la segunda implementación de la frase «N aparcamientos».
   Y con el Bold fuera, **el canvas se separa a su propio fichero** para que la allowlist cubra sólo
   lo que se ganó la exención y el guardarraíl vuelva a proteger los composables.

## Criterio de éxito

- Ningún `vehicleIdentityColor`/`Container` en la línea de tiempo; `watch` no sobrevive como
  parámetro muerto en ninguna firma.
- `HistoryWeeklyChart` (composables) **sale de `WEIGHT_ALLOWLIST`**; entra sólo el fichero del canvas.
- Un periodo con 1-2 aparcamientos dibuja su gráfico y se ve bien en device.
- Suite + Konsist + assembles + klib iOS en verde. Galería al día.

## Verificación (08-09-2026)

- **En device (Pixel 8 Pro, mock):** en un coche **Bluetooth**, la card de sesión activa, el punto
  pulsante, el raíl y la cabecera «Currently parked» son ya **verde marca**; el azul ha desaparecido
  de la línea de tiempo entera. La card de vehículo tiene borde neutro y su identidad sigue leyéndose
  sin esfuerzo por tres vías (punto del chip, glifo BT junto al nombre, pin azul de la línea de
  estado). Home conserva su borde de color, sin tocar.
- El caso de «pocos datos» se ve bien con el gráfico normal: el Seat en «Todo» dibuja **una sola
  barra entre seis meses** y se lee perfectamente, que era justo el escenario por el que existía
  `LowActivitySummary`.
- **`HistoryWeeklyChart` sale de la allowlist de pesos** y el guardarraíl de tipografía pasa con el
  fichero ya cubierto: 5 tests, 0 fallos. Los cinco guardarraíles ejecutados de verdad —Typography 5,
  Color 8, Divider 1, Import 1, LocaleParity 5, todos en 0 fallos.
- Suite completa, `assembleProdDebug`, `assembleMockDebug` y `compileKotlinIosSimulatorArm64` en verde.
- Al borrar el bloque quedó huérfano `history_activity_low_hint`: **retirado de los 9 locales** (un
  string sin consumidor es deuda que el siguiente lector cree que significa algo).

## Consumidores auditados

- `HistoryContent(watch = …)` — el parámetro se RETIRA; barridos sus call sites (ficha y galería;
  el panel no lo pasaba) ✔
- `EndedSessionTimelineNode` — pierde `watch`; el dot, el raíl y el relleno pasan a `PapColor.brandData`
  y `primaryContainer` ✔
- `ActiveSectionHeader(accent = …)` — recibe marca en vez del color de identidad ✔
- `vehicleChassisBorder` — INTACTO y sigue vivo: sus dos usos de Home no se tocan; sólo la ficha
  deja de llamarlo ✔
- `TypographyGuardrailTest.WEIGHT_ALLOWLIST` — `HistoryWeeklyChart` fuera, `ActivityBarCanvas` dentro ✔
- `history_activity_low_hint` — string huérfano retirado de los 9 locales ✔
