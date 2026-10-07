# FUEL-THE-CHEAPEST-AND-THE-NEAREST-WEAR-A-BADGE-001 · La gasolinera más barata y la más cercana se señalan

**Estado:** ✅ Done · master `80fd164c2` · cierra la **issue #33**

## Problema

Pedido por el product owner (#33): como no hay precios en todos los países, marcar con un badge la
**más barata de la zona** y otro para la **más cercana**, y decidir qué pasa cuando coinciden.

## Por qué «más cercana» NO es redundante con el orden

La lista se ordena con `compareBy { price == null }.thenBy { distanceMeters }` (`NearbyOrdering`):
primero las que tienen precio, cada bloque por distancia. Así que **la primera fila es la más cercana
CON PRECIO**, y en un país cuyo feed no trae precios —que es la mayoría del mundo, y justo el motivo
de la issue— la gasolinera realmente más cercana puede quedar abajo del todo y sin precio. El badge
dice lo que el orden no puede decir.

## Diseño

`domain/fuel/FuelStandouts.kt`: función pura sobre la lista, **no** un caso de uso — su resultado no
aparece en ningún diagnóstico ni mueve un pin. [DET-VERDICT-NOT-PREDICATE-001]

Lo que se NIEGA a afirmar, que es el grueso del diseño:

| Caso | Decisión | Por qué |
|---|---|---|
| Una sola gasolinera | sin badge | con una no hay «la más barata», hay una gasolinera |
| Empate de precio | sin badge de barata | un empate no tiene ganador, y el usuario lo comprueba en el surtidor |
| Divisas mezcladas | sin badge de barata | 1,42 £/l no es más barato que 1,65 €/l porque el número sea menor |
| Sin fix de ubicación | sin badge de cercana | «la más cercana» sin posición es una afirmación sobre nada |
| Coinciden las dos | **UN badge combinado** | dos badges en una fila estrecha es ruido; quitar uno pierde información que la issue pide |

UI: el badge va en la línea del subtítulo, no en el trailing —ahí vive el precio, y la afirmación es
sobre la gasolinera, no sobre la cifra—. Contenedor de marca cuando la afirmación toca al precio,
neutro cuando es solo distancia (que la fila ya dice en metros dos palabras después).

## Consumidores auditados

| Sitio | Decisión |
|---|---|
| `HomeFuelStationRows` | ✅ calcula los standouts UNA vez por respuesta y los reparte |
| `HomeFuelStationRow` | ✅ parámetro `badge`, opcional |
| Mapa (cápsula de gasolinera) | ❌ fuera de alcance de la issue — ver Preguntas |
| Peek de gasolinera | ❌ ídem |
| Dev Catalog | ❌ la galería no tenía filas de gasolinera; nada que sincronizar |

## Criterio de éxito

- En una lista con precios, la más barata lleva badge y la más cercana también.
- Cuando son la misma, sale un solo badge que lo dice.
- Ningún badge miente: ni con empate, ni con divisas mezcladas, ni sin ubicación.

## Verificación

`FuelStandoutsTest` — 6 tests en verde, cinco de ellos sobre afirmaciones que NO se deben hacer.

## Decisiones de la revisión (#37, rndevelo, 18-09)

> «El badge apenas destaca y no se nota debido a sus tonos, ¿podrías hacerlo más pequeño y
> compacto?»

Las dos cosas que pide son la misma: un badge tonal (`primaryContainer` sobre la superficie de la
hoja) se separa del fondo en unos pocos puntos de luminancia, y la salida NO es agrandarlo —eso lo
convierte en una segunda fila dentro de la fila— sino hacerlo **más pequeño y macizo**, que es como
se lee un token dentro de una línea.

- **Relleno macizo**: `primary` + `onPrimary` para lo que habla del precio, `inverseSurface` +
  `inverseOnSurface` para la distancia sola. Los dos al máximo contraste; lo que los separa es el
  tono, no el peso.
- **Caja densa**: `PapBadgeDensePadding` (6×1 dp) + píldora, frente a los 8×4 dp y el corte
  `extraSmall` del badge de pie. `PapBadge` gana `contentPadding` y `shape` **opcionales**, con los
  valores de hoy por defecto: ningún otro badge de la app cambia.
- El rol tipográfico sigue siendo `badge` — el tamaño es propiedad del rol, y lo que se ha encogido
  es la CAJA, no la letra. [UI-TYPE-TWO-VOICES-ONE-ROW-001]

## Decisiones de la review (#37, rndevelo, 20-09)

> «Solo veo el badge de MÁS CERCANA, no veo el de MÁS BARATA cuando debería de haber también para
> barato y ordenado arriba. El chip lo veo un poco grande.»

**Era un bug de diseño mío, y la captura lo prueba.** La regla decía que en un empate nadie es *la*
más barata, así que el badge se caía entero. En un mapa real los surtidores se copian el precio a la
milésima: en su pantalla había **dos a 1,809 €**, de modo que la gasolinera que era a la vez la más
barata y la más cercana sólo llevaba «MÁS CERCANA».

- **Un empate se COMPARTE**: el badge va a TODAS las que cotizan el mínimo. «Las dos son las más
  baratas» es verdad y se comprueba en el surtidor; la mentira habría sido inventar un ganador
  único. `cheapestKey: String?` pasa a `cheapestKeys: Set<String>`.
- **Lo que sí se calla es el empate UNIVERSAL**: si todas las que tienen precio cobran lo mismo, «la
  más barata» no distingue nada — sería ruido en todas las filas.
- **La más barata sube al principio de la lista** (`FuelStandouts.rankOf` + `NearbyOrdering`). Un
  badge que hay que buscar con scroll no es un badge.
- ⛔ **La más cercana NO sube**, y es deliberado: la lista pone las gasolineras con precio primero, y
  la más cercana suele ser una SIN precio — subirla deja la lista encabezada por una fila sin cifra,
  que es justo lo que ese orden existe para evitar. Un test lo fija.
- **Caja del badge**: 6×1 → 5×0 dp. La letra no se toca: 12 sp es propiedad del rol `badge`, no del
  call site. Si sigue pareciendo grande, lo que falta es un ROL nuevo (un `badgeInline` de 10 sp),
  no un `fontSize` suelto — ver Preguntas.

## Tercera vuelta (review del user, 20-09): la forma del badge de plaza, y al final de la fila

> «Evaluar cómo mete el badge de spot de tiempo para simetría en FORMA, no colores — cada uno tiene
> su identidad de colores. Y lo pondremos al final del row, no al principio, alineándonos con el
> badge de FREE SPOTS (3 min ago).»

### Lo que era cada badge antes de tocar

| | Badge de plaza (`SpotAgeIndicator`) | Badge de gasolinera (2.ª vuelta) |
|---|---|---|
| Caja | `contentPadding` por defecto | `PapBadgeDensePadding` (5×0) |
| Esquina | `shapes.extraSmall` | `CircleShape` (píldora) |
| Voz | rol `badge` | rol `badge` ✅ |
| Icono | `Icons.Rounded.Schedule` | ninguno |
| Color | par **tonal** de la rampa de frescura | **macizo** `primary` / `inverseSurface` |
| Sitio | **trailing** | línea del subtítulo, al principio |

### Qué se ha alineado

**Caja, esquina y posición.** `PapBadge` con sus valores por defecto, y el badge se muda al
**trailing**, apilado **bajo el precio**: el precio es la cifra que decide y manda en la columna; el
badge es lo que la cualifica, y una afirmación sobre un número se lee debajo de él.

⛔ La caja densa y la píldora **no se pierden por capricho**: existían porque el badge vivía dentro de
la línea del subtítulo, donde un token tiene que encogerse para no leerse como una segunda fila. Al
final de la fila esa razón desaparece — ahí tiene su propia columna. Al irse, el subtítulo recupera
su texto normal (antes se lo comía el badge).

### Lo que NO se ha alineado, y por qué

- **El color**, por indicación expresa: es la identidad del carril.
- **El icono.** El reloj del badge de plaza **informa**: hace que «3 min» se lea como una antigüedad
  sin la palabra. Un icono de «más barata» sería una metáfora (etiqueta, flecha, hucha), y este
  proyecto prohíbe la jerga inventada — incluso la dibujada. El pedido era *«al menos la forma»*, y
  la forma no necesita el glifo.
- **El PESO.** El de plaza es tonal y éste sigue macizo, porque la 2.ª vuelta lo pidió así («apenas
  destaca»). ⚠️ Pero ese argumento nació con el badge DENTRO del subtítulo. Al final de la fila la
  posición ya le da prominencia, así que el macizo puede haberse vuelto innecesario — y dos badges
  con la misma forma y distinto peso se leen como dos niveles de afirmación distintos. **Se decide
  viéndolo**, y es una línea.

### Verificación

`./gradlew testAll :app:compileMockDebugKotlin` → BUILD SUCCESSFUL.

⏳ **Sin captura del estado final**: no hay device conectado (`PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`).

## Cuarta vuelta: aplicadas las tres recomendaciones

> «Aplica todo lo recomendado, incluido lo de más barato aunque sea cercano.»

### 1. El peso baja a TONAL

`primaryContainer`/`onPrimaryContainer` para la afirmación de precio y `surfaceVariant`/
`onSurfaceVariant` para la distancia sola. Es el mismo peso que el badge de antigüedad de una plaza,
que lee su color de la rampa de frescura en `container`/`onContainer`.

El macizo entró cuando el badge vivía **dentro** de la línea del subtítulo: ahí competía con texto y
necesitaba contraste al máximo para no desaparecer («apenas destaca», 2.ª vuelta). Al final de la
fila tiene su propia columna, así que la prominencia se la da la **posición**, y el macizo pasaba de
resolver un problema a crear otro: dos badges con la misma forma y distinto peso se leen como dos
**niveles** de afirmación distintos al cambiar de pestaña, cuando lo que dicen es lo mismo —«ésta es
la que te interesa»— sobre carriles distintos.

### 2. La combinada dice sólo «MÁS BARATA»

Y no es un recorte por espacio. **«Y CERCANA» no añade nada justo en la fila donde más estorba**: una
gasolinera que es a la vez la más barata y la más cercana es, por construcción, la **primera** de la
lista —la más barata se adelanta desde la 3.ª vuelta— y sus metros están escritos dos palabras más
allá, en el subtítulo. La afirmación fuerte, la que no se puede deducir mirando, es el precio.

De paso resuelve el riesgo de ancho: 20 caracteres al borde de una fila que también lleva el precio
le comían el nombre a la gasolinera; el badge de una plaza no pasa de 11.

⛔ `FuelStandoutBadge.CHEAPEST_AND_NEAREST` **sobrevive como estado** —el orden lo usa para adelantar
la fila— y lo único que colapsa es su ETIQUETA. La key `home_fuel_badge_cheapest_nearest` se queda
sin lector, así que sale de **los nueve locales**: una string muerta en nueve ficheros es deuda que
nadie vuelve a mirar.

### 3. Sin icono, y es deliberado

El reloj del badge de plaza **informa**: hace que «3 min» se lea como una antigüedad sin la palabra.
Un icono de «más barata» no informaría, **metaforizaría** —etiqueta, flecha, hucha—, y eso es jerga
inventada, sólo que dibujada. Además el carril ya lleva su glifo de surtidor en el tile de la
izquierda: un segundo icono en la misma fila hablando del mismo tema es ruido. Lo pedido fue «al
menos la forma», y la forma no necesita el glifo.

### Verificación

`./gradlew testAll :app:compileMockDebugKotlin` → BUILD SUCCESSFUL (incluido
`LocaleParityGuardrailTest`, que es quien comprueba que la key salió de los nueve y no de ocho).

⏳ **Sin captura del estado final**: no hay device conectado (`PR-A-VISUAL-CHANGE-SHOWS-ITSELF-001`).
