# UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001 · El lugar y el tiempo dejan de compartir una frase: cada uno con su icono, en su línea

**Estado:** ✅ Done · el lugar y el tiempo son dos filas con su propio icono en Home y en la ficha
· viaja en el mismo commit que [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001](ui-a-parked-card-offers-the-way-across-001.md)
(rediseñan la MISMA superficie: la card del coche aparcado en Home y en la ficha).

## Problema
La card del coche aparcado mete **dónde** y **cuándo** en una sola frase con UN solo icono delante:

- Home, card de un coche (`HomeVehicleCard`): `📍 Aparcado en Calle X · hace 6 min · 180 m`,
  `maxLines = 2`. El icono de lugar encabeza una línea en la que la mitad del contenido ya no es
  lugar; y cuando envuelve, la segunda línea arranca **sin icono**, colgando del margen.
- Ficha del vehículo (`VehicleStateLine` en `VehiclePageContent.kt`): mismo patrón —
  `📍 aparcado en Calle X · hace 2 h` — y con un detalle peor: cuando **no** está aparcado el icono
  pasa a ser el reloj (`Schedule`), o sea que el mismo hueco significa a veces "lugar" y a veces
  "tiempo". El reloj ya estaba en la pantalla; lo que no tenía era línea propia.

El dato de tiempo es el que más se consulta de un coche aparcado (¿cuánto llevo?), y hoy no tiene
ancla visual: hay que leer la frase hasta el punto medio para encontrarlo.

## Doctrina violada
Ninguna regla escrita lo prohíbe, pero rompe la simetría que el resto de la app sí cumple: en el
peek (`ParkingPeek`) el lugar y el tiempo **ya** son dos filas `PeekMetaRow` con su propio icono
(`DistanceRow` + `ParkingDurationRow`). El peek y la card cuentan lo mismo con dos anatomías
distintas. La que se corrige es la de las cards, hacia la que ya existe.

## Diseño
Una anatomía de **dos filas meta**, la misma en las dos pantallas y la misma que ya usa el peek:

```
📍  Calle de Alcalá 42          ← lugar (LocationOn, o Adjust si la sesión es una ZONA)
🕐  hace 6 min · 180 m          ← tiempo (Schedule), con la distancia donde ya estaba
```

- **Icono por fila**, y cada icono significa siempre lo mismo: el pin es LUGAR, el reloj es TIEMPO.
  Se acaba el hueco polisémico de la ficha.
- El tinte de los dos iconos sigue siendo el color de identidad del coche (`vehicleIdentityColor`),
  y el texto sigue en `onSurface` / `onSurfaceVariant`: **el estado se escribe, no se tiñe**
  [UI-COLOR-DOCTRINE-001].
- Las voces no cambian: `caption` en Home, `meta` en la ficha. Una cifra alineada en columna no
  necesita cambiar de voz [PEEK-META-INTER-001].
- La **distancia** se queda pegada al tiempo (`hace 6 min · 180 m`): responde a la misma pregunta
  ("¿lo tengo lejos / lo dejé hace mucho?") y ya viajaban juntas en `home_vehicle_card_parked_meta`.
- Estados que NO son "aparcado" (conduciendo, sin marcar, nunca aparcado) siguen siendo **una** fila:
  no hay dos datos que separar, y darles dos líneas sería altura vacía.

## Alcance
- `HomeParkingRow.kt` → `HomeVehicleCard` (card de un solo coche) y `HomeVehicleChip` (2+ coches,
  el chip compacto). El chip ya gastaba hasta 2 líneas en esa frase, así que las dos filas no le
  cuestan altura y le dan el mismo esqueleto que a la card grande.
- `VehiclePageContent.kt` → `VehicleStateLine`.

## Criterio de éxito
- En Home y en la ficha, un coche aparcado muestra pin+lugar arriba y reloj+tiempo debajo, alineados
  en la misma columna de texto.
- Una dirección larga envuelve **sin** arrastrar el tiempo ni dejar una línea huérfana sin icono.
- La ficha deja de reusar el hueco del pin para el reloj.
- `assembleMockDebug` + `assembleProdDebug` verdes y suite `:shared:testDebugUnitTest` verde.

## Implementado
- `HomeParkingRow.kt`: nace `ParkedMetaLines` (el esqueleto de dos filas) sobre `VehicleMetaLine`
  (una fila = un icono + un hecho), y lo usan **card y chip**. La rama `session != null` sale de los
  dos `when` de estado: aparcado ya no es un caso del conmutador de iconos, es su propio bloque.
  Muere `parkedAddressLine` (la frase fusionada del chip) y con ella `META_SEPARATOR`.
- La alineación la decide el propio dato, igual que en `PeekMetaRow`: una línea → centrado con su
  icono; dos → el icono cuelga de la primera. El `6` inline del pie de la card grande pasa a
  `CARD_FOOT_GAP_DP` (estaba escrito dos veces).
- `VehiclePageContent.kt`: `VehicleStateLine` pasa a `Column` de hasta dos `VehicleStateRow`. El pin
  solo sale **si hay lugar que nombrar**; el reloj sale siempre y conserva el estado ESCRITO
  («Aparcado · hace 2 h»), así que un coche sin dirección resuelta sigue diciendo qué hace.
- Cae `vehicle_state_parked_at` (`%1$s · %2$s`) de los 9 locales: era el pegamento de la frase que
  ya no existe. Ningún otro consumidor la usaba.

## Tamaño y peso de los iconos (medido en device, 15-09)
- El reloj era el ÚNICO outlined de una fila donde el pin y el peatón son sólidos → `Schedule` pasa a
  `AccessTimeFilled` en las **6** filas meta que comparten esa anatomía (card y chip de Home, ficha,
  detalle de historial, peek de gasolinera y de parking público). El chip de filtro del historial y
  el tile de estadísticas conservan `Schedule`: son iconos de OTRO componente, no de esta fila.
- El glifo de vigilancia empataba a tamaño (16 dp) con dos iconos de dato que además son sólidos, o
  sea que la identidad del coche perdía su propia fila. Sube ÉL a 18 dp: bajar los de dato se probó
  (13 dp) y los dejaba ilegibles — pin y reloj se volvían la misma mancha.

## Lo que salió al mirarlo en device (15-09)
- ⛔ **Un `clip` de píldora RECORTA el contenido de las esquinas.** La fila tocable de la ficha
  llevaba `PapShapes.chip` = `RoundedCornerShape(999.dp)`; sobre una fila de DOS líneas el arco entra
  decenas de dp por cada extremo y se comía el pin y el reloj, que arrancan pegados al margen. Se
  veían mordidos. Sin `clip`: el ripple rectangular es lo que hace cualquier fila de lista.
- **El contador del modal no escalaba a días**: decía «Aparcado hace 50h 45m», que nadie lee como
  "anteayer" (la ficha, que usa `relativeTimeText`, decía «hace 2 días» a la vez y en la misma
  pantalla). `ParkingDurationRow` sube a días con el corte singular/plural del resto de la app →
  `home_peek_parking_duration_day` / `_days`, en los 9 locales.
- **El lápiz de la ficha flotaba a media card**: `VehicleIdentityHeader` centraba el trailing
  respecto a la FILA, y cuando el texto manda esa fila mide cuatro líneas. Ahora, y solo en ese caso,
  se centra sobre la **altura del tile del coche** — el ancla visual de la card. La card de Home no
  cambia: su fila es corta y ya estaba centrada.
- **`Me voy` lleva la flecha suelta (`ArrowOutward`), no `Outbound`**: aquel dibuja la flecha dentro
  de un disco sólido y sobre el relleno verde se leía como un botón dentro del botón.

## Verificación
- `:shared:testDebugUnitTest --rerun-tasks` → **2957 tests, 0 fallos** (incluye los guardrails
  Konsist de tipografía, color, divisores e imports, y la paridad de locales).
- `:app:assembleMockDebug` + `:app:assembleProdDebug` → OK.
- ✅ Visto en device (Oppo): dos líneas en Home y en la ficha, cada icono con su significado.

## Consumidores auditados
| Sitio | Qué hacía | Cierre |
|---|---|---|
| `HomeVehicleCard` (Home, 1 coche) | frase única `lugar · tiempo · distancia` | ✅ dos filas |
| `HomeVehicleChip` (Home, 2+ coches) | frase única `lugar · tiempo` | ✅ dos filas |
| `VehicleStateLine` (ficha) | frase única + icono polisémico | ✅ dos filas |
| `ParkingPeek` (modal) | YA eran dos filas con icono | exento — es el modelo que se copia |
| `HistoryTimeline` / detalle de historial | filas de sesiones pasadas, otra anatomía | exento — no es la card del coche |
