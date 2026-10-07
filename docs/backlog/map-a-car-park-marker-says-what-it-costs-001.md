# MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001 · El parking dice lo que cuesta, con la cápsula de la gasolinera

**Estado:** ✅ Done · master `8fcf038ff` · cierra la **issue #46**

## Problema

Pedido por el product owner (#46): el marcador de parking debe seguir «la misma estructura de
diseño y contenido que gasolineras»: icono + (precio si lo hay; si no, «de pago» o «gratuito»; y si
no se sabe, nada).

Hoy el parking es un disco: sólo puede decir «P». Y la tarifa es justo lo que decide entre dos
parkings a la misma distancia.

## Diseño

- **Una sola cápsula**: `NearbyCapsuleMarker` (glifo + acento + hasta dos líneas), y gasolinera y
  parking pasan a ser dos llamadas. Copiar la forma habría dejado dos definiciones de seis números
  (corner, dos grosores de borde, dos alphas, tamaño del glifo) — dos copias es el día que una se
  ajusta y la otra no.
- **El azul pasa a ser ACENTO** (glifo, borde y punto), no relleno. La P-sobre-azul de #35 sigue
  entera donde hay sitio para un tile: la fila y el peek. Dos cuerpos macizos de colores distintos
  uno al lado del otro compiten con el producto —las plazas—, y ninguno de los dos es el producto.
- **Sin cifra**: no hay precio que escribir. `FeeInfo` es sí/no; el `charge` de OSM es texto libre
  («1.50 EUR/hour», «first 30 min free») con cobertura residual. Y el hueco de la cifra está
  reservado a las plazas LIBRES de `PARKINGS-LIVE-OCCUPANCY-001`.
- **`UNKNOWN` no dice nada** — el 71 % de los parkings. Un conductor al que le decimos «gratuito» y
  se encuentra una barrera fue engañado por nosotros, no por OSM. Misma regla que ya cumple la fila.
- **La tarifa entra en el `contentId`**: está dibujada dentro del bitmap y kmpmaps cachea por clave.
  Y se resuelve UNA vez por parking, alimentando a la vez la clave y el bitmap — calcularla dos
  veces es exactamente como se registra un handler para un id que el marcador no pide (el fallo que
  cerró la review de #38).
- Las palabras las resuelve `HomeMapSection` con las **mismas keys que la fila**
  (`parking_fee_paid` / `parking_fee_free`): una idea, una redacción, cero strings nuevos.

## Consumidores auditados

| Sitio | Decisión |
|---|---|
| `PublicParkingMarker` | ✅ cápsula + tarifa |
| `FuelStationMarker` | ✅ pasa a llamar a la cápsula compartida; pixel-idéntica salvo el apoyo |
| `publicParkingContentId` | ✅ la tarifa entra en la clave |
| `HomeMapSection` | ✅ resuelve las dos palabras |
| Dev Catalog | ✅ las TRES tarifas + una variante parking-junto-a-gasolinera |
| Fila y peek del parking (#35) | ❌ sin cambios: ahí el tile azul con la P blanca tiene sitio |

## Relación con otras ramas

- **#35** (azul del parking): es la BASE de esta rama. Mergear #35 primero.
- **#45** (apoyo de la cápsula sobre la línea del puck): esta rama trae el mismo arreglo dentro de
  la cápsula compartida. Si #45 entra antes, el conflicto es la constante duplicada
  `MARKER_BODY_BOTTOM_Y` y se resuelve quedándose con la extracción.

## Criterio de éxito

- Un parking de pago y uno gratuito se distinguen sin abrirlos.
- Uno sin tarifa conocida enseña sólo la P.
- Parking y gasolinera, uno al lado del otro, son la misma forma con distinto acento.

## Decisiones de la review (#59, rndevelo, 20-09)

> «La P debería ir en un círculo azul con la P en medio del círculo en blanco dentro de la propia
> cápsula. Y "De pago" no cabe en el marker, valorar si aumentar el tamaño del marker o qué hacer.
> Valorar si cambiar también color de recuento del peek "17" y el antetítulo "PARKINGS".»

- **«De pago» no cabía, y el marcador no era el problema.** La cápsula pedía
  `Modifier.width(IntrinsicSize.Min)`, y el ancho mínimo intrínseco de un texto es el de su palabra
  **más larga**: con `maxLines = 1` quedaba en «De» y tiraba «pago» fuera. Se le estaba pidiendo el
  ancho de una palabra para pintar una frase. `IntrinsicSize.Max` es el ancho de la línea entera.
  La gasolinera nunca lo enseñó porque sus dos líneas son de una palabra («1.879», «Diésel»).
- **La P va en disco**: `NearbyCapsuleMarker` gana `glyphOnDisc`/`onAccent`, y el parking los pide.
  El mismo disco azul con la P blanca de la fila y el peek, ahora también en el mapa: las tres
  superficies dibujan el mismo objeto y lo que cambia es el tamaño. La gasolinera NO lo lleva — su
  glifo suelto ya es el surtidor.
- **El contador «17» y el antetítulo se quedan verdes de momento** — ver Preguntas.

## Tercera vuelta (review del user, 20-09): sin tarifa, no hay cápsula

> «El marker de parking que no tenga ese dato y solo tengamos la P será del mismo tamaño que el
> marker de gasolinera que tampoco tenga datos de precio, o sea, que sea un marker redondo también
> porque no tiene más datos que mostrar. Y en este caso, la P y su disco no irán dentro de un
> contenido surface con contorno: el propio marker será el azul de parking con su P blanca dentro.»

Tiene razón, y el argumento es el mismo que sostiene toda la cápsula al revés: **una cápsula es un
papel con contorno que existe para SOSTENER una frase.** Sin tarifa no hay frase, así que lo que
quedaba era un disco azul metido dentro de una caja blanca vacía — **dos cuerpos, dos bordes y dos
colores para decir una sola letra**.

Y no es el caso raro: `FeeInfo.UNKNOWN` es el **71 %** de los parkings. La forma que se estaba
puliendo era la de la minoría.

### Qué se ha hecho

Nace `NearbyDiscMarker`, la hermana callada de `NearbyCapsuleMarker` — para un sitio que no tiene
nada que decir además de qué es. `PublicParkingMarker` elige: con tarifa, cápsula; sin tarifa, disco.

⛔ **Comparte TODA la geometría con la cápsula**: misma escala `s`, mismo `markerH`, mismo apoyo en
`MARKER_BODY_BOTTOM_Y`, y el diámetro del disco es el **alto mínimo del cuerpo de la cápsula**. Ese
48 estaba inline y ahora es `CAPSULE_BODY_MIN_U`, usado por las dos. Así el disco mide **exactamente
lo que mide una cápsula que sólo lleva glifo** —la gasolinera sin precio—, que es literalmente lo que
se pidió, y no puede desincronizarse aunque alguien ajuste la cápsula.

El glifo entra por **proporción** (13 de 20), no por sus 13 dp literales: el disco suelto es mayor y
un glifo fijo se vería perdido en el centro. Lo que se comparte entre superficies es el objeto, no la
cifra.

### La única licencia que me he tomado

**En reposo no lleva borde** —el cuerpo es el color, como se pidió—, pero la **selección** sí dibuja
un anillo de papel. Sin ninguna diferencia, un parking tocado se vería idéntico a uno sin tocar, y
toda la familia marca la selección de alguna forma. El anillo va por fuera del disco, así que no le
come tamaño al glifo. Si sobra, es una línea.

### Verificación

`./gradlew testAll :app:compileMockDebugKotlin` → BUILD SUCCESSFUL.

⏳ **Sin captura del estado final**: no hay device conectado (`PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`).
