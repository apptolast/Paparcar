# MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001 · El parking dice lo que cuesta, con la cápsula de la gasolinera

**Estado:** 🔵 En revisión · rama `feature/MAP-A-CAR-PARK-MARKER-SAYS-WHAT-IT-COSTS-001` ·
worktree `../PaparcarKMP-issue46` · cierra la **issue #46** ·
⛔ **sale de la rama de #35**, porque reescribe el mismo marcador

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
