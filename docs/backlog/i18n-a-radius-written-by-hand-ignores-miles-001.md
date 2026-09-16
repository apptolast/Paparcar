# I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001 · Una distancia escrita a mano dice «km» aunque el usuario haya elegido millas — y se queda atrás cuando la constante cambia

**Estado:** ✅ Done · rama `feature/I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001-radius-copy`
· worktree `../Paparcar-i18n-radius` · anotado el 2026-09-16 desde master `a74b2b99`
**Origen:** pregunta del user — *«¿aplicamos lo de millas cuando no es con km?»*, y al día siguiente
*«el empty state de parkings pone 1.5 km pero ahora lo tenemos a 3, ¿no?»* — que es el MISMO bug
enseñando su segunda cara.

## Problema

Toda distancia que la app **formatea** pasa por `distanceString` → `LocalDistanceUnit` (`App.kt:180`,
alimentado por el ajuste `settings_distance_unit`) y sale en millas si el usuario las eligió: las
cifras del panel, el eje del gráfico, la card del resumen, las filas del sheet. Ahí no hay fuga.

Las que no pasaban por ahí tenían la cifra —o su unidad— **tecleada dentro del copy**, y por eso
fallaban de dos maneras a la vez. La segunda es la que hace que esto no sea cosmético:

- **Miente en millas.** «No fuel stations within 5 km» en una app que da todo lo demás en millas.
- **Se desfasa en silencio.** `home_parkings_empty` decía «1,5 km» en los NUEVE locales mientras
  `PublicParkingsQuery.DEFAULT_RADIUS_METERS` ya valía 3.000: el radio subió en `e8c281ba` («the lane
  reaches 3 km») y la frase se quedó donde estaba. Nada falla, nada avisa. Hay que leerlo para
  enterarse, y lo leyó el user.

El doc original listaba cuatro. El barrido obligatorio de consumidores encontró **ocho**:

| Key | Antes | Qué era |
|---|---|---|
| `home_fuel_empty` | «No fuel stations within 5 km.» | radio en el texto |
| `home_fuel_too_far` | «…within %1$d km of you.» | cifra por parámetro, **unidad en el texto** |
| `home_parkings_empty` | «…within 1.5 km.» | radio en el texto · **y desfasado** |
| `home_parkings_too_far` | «…within %1$d km of you.» | igual que el otro `too_far` |
| `home_zone_radius_meters` | «%1$d m» | el valor del slider de radio de zona, siempre en metros |
| `home_peek_parking_approximate` | «Somewhere within %1$d m — …» | el radio de una sesión aproximada |
| `home_peek_spot_approximate` | «Somewhere within %1$d m — …» | ídem, en la plaza publicada |
| `home_gps_accuracy_banner` | «GPS ±%1$dm» | la precisión del fix, que es un radio |

Los cuatro `%1$d` son los peores: el hueco para pasar la distancia ya formateada **existía** y nadie
lo usó — se pasaba el número y se dejaba la unidad pegada al texto.

## Doctrina violada

*Sistemas, no parches.* Había un formateador de distancias y ocho sitios decidiendo por su cuenta.
Y el i18n: una cifra dentro de la frase obliga a tocar nueve traducciones cada vez que se mueve una
constante, que es exactamente lo que no se hizo en `e8c281ba`.

## Diseño

**El invariante, en un sitio:** una distancia que el usuario lee sale de `formatDistance`; un RADIO
sale de `formatRadius`. Ninguna cifra de distancia se escribe en `strings.xml`: las frases la reciben
como `%1$s` ya formateada, derivada de la constante que de verdad acota la consulta.

- `formatRadius` (`presentation/util/SpotUiUtils.kt`) es una capa **fina sobre `formatDistance`**, no
  un segundo formateador: la conversión, el umbral ft/mi y los nombres de unidad siguen viviendo en
  un solo sitio. Lo único que no hereda un radio es el decimal forzado — «3 km», no «3.0 km», porque
  la cifra la elegimos nosotros y no se midió. Un radio convertido sí lo conserva (1.9 mi), que ahí
  el decimal es la parte honesta. `FormatRadiusTest` fija esa relación con `formatDistance`, para que
  no puedan divergir sin que algo se ponga rojo.
- `radiusString` (`TimeStringComposables.kt`) es su envoltorio composable, junto a `distanceString`.
- Se retira `ViewportRefetch.maxFromUserKm`: hacía media pregunta —derivaba la cifra y dejaba el «km»
  escrito en los nueve locales—, y sin consumidores no se queda como código muerto.
- Se retira la key `home_zone_radius_meters`: era un formateador escrito a mano disfrazado de string.

## Criterio de éxito

- ✅ Con el ajuste en millas, ninguna frase de Home dice «km»: las ocho pasan por el formateador.
- ✅ El desfase 1,5 → 3 km no vuelve a ser posible: la frase no tiene cifra que desfasar.
- ✅ Nueve locales al día · `LocaleParityGuardrailTest` verde · `./gradlew testAll` verde.
- ✅ Guardarraíl nuevo con testigo (abajo).

## Guardarraíl — `DistanceUnitCopyGuardrailTest`

El doc pedía «un barrido para que el que venga detrás no reabra esto». Un barrido no lo impide: lo
impide un test. Prohíbe en los nueve locales una cifra —literal o `%1$d`— con una unidad abreviada
pegada detrás, con **tres** testigos, porque una prohibición sin testigo no es un chequeo:

1. **Población** — que lee los 9 ficheros y que ninguno está vacío.
2. **Patrón** — que el regex sigue cazando las cuatro frases que este ticket barrió, y que NO caza la
   forma correcta (`%1$s`). Sin esto, un regex que dejara de casar sería un test que siempre pasa.
3. **Falso positivo fijado** — que no caza la prosa que deletrea la unidad («30 mètres»).

Dos trampas medidas escribiéndolo, las dos ya documentadas en el propio test:
- Dentro de un raw string Kotlin no vale `\$`, y el `$` sin escapar es el metacarácter de fin de
  línea: `%1$d` como regex significa «%1, fin de línea, d» y **no casa nunca**. El guard compilaba,
  corría y no veía nada.
- `\b` en Java usa `\w` ASCII, así que «30 mètres» tenía frontera entre la `m` y la `è` y entraba
  como si fuera «30 m». Cierre correcto: `(?![\p{L}\d])`.

**Allowlist, cada entrada con su motivo** (una allowlist sin motivos es cómo un guard se muere en
silencio): quedan **dos**, las de tiempo relativo — `%1$dm` ahí son MINUTOS, y coinciden con los
metros en la letra y en nada más. Ver abajo por qué el consumo salió de la lista.

## Consumidores auditados

`grep` de cifra+unidad sobre los 9 locales → **13 keys arregladas**, 2 exentas:
- **Cerrados (13)** — las 8 de la tabla, más el gálibo y las 4 del consumo (ampliación de abajo).
- **Exento permanente (2)** — `relative_time_compact_min` / `_now`: son minutos, no metros.
- **Abierto (1)** — el precio por litro, al final.
- **iOS** — nada que abrir: todo lo nuevo (`formatRadius`, `radiusString`, `formatHeight`,
  `ConsumptionFormat`, `GallonKind`) vive en `commonMain`, y sus tests en `commonTest`, que corre
  también en iOS. El lado iOS viaja con el ticket.
- **Dev Catalog** — sin pantalla ni estado nuevos; sólo cambia el texto que ya pintaban. Verificado
  con `:app:assembleMockDebug` y `:app:compileProdDebugKotlin`.
- **El dominio** — intacto a propósito: ni el radio, ni la altura, ni el consumo cambian de unidad en
  el modelo. Lo único que se movió hacia dominio es la constante del gálibo, que ya se citaba ahí.

## Ampliación pedida por el user — el consumo y el gálibo ENTRAN (16-09)

Los dos follow-ups que este doc dejaba fuera se hicieron en el mismo ticket, a petición del user.

### Consumo → mpg · kWh/100 mi

**El consumo no es una distancia: es una magnitud POR distancia, y su forma imperial va al revés.**
l/100 km mide gasto (más es peor) y mpg mide rendimiento (más es mejor), así que no hay factor que
multiplicar — hay que INVERTIR. Por eso `presentation/util/ConsumptionFormat.kt` es fichero propio y
no una unidad más de `formatDistance`. La eléctrica sí es un factor (el kWh no cambia, la distancia
sí): kWh/100 km → kWh/100 mi.

- ⛔ **El dominio no se entera**: `consumptionPer100` sigue en l/100 km y `consumptionElectricPer100`
  en kWh/100 km, que es lo que leen `FuelDefaults`, el cálculo de litros y todo el pipeline de
  estadísticas. La conversión vive **sólo en el borde**: al pintar y al leer lo que el usuario
  escribió. Meter mpg en el modelo habría obligado a cada consumidor a preguntarse en qué unidad le
  llega el número — el mismo fallo que el ticket cierra, una capa más abajo.
- **El galón NO se deduce de «usa millas»**, y aquí estaba lo caro. US y UK dicen los dos «mpg» y no
  es la misma cifra: el mismo coche marca 42,8 mpg en Michigan y 51,4 en Kent. Elegir mal no descoloca
  la unidad, deja el número mal por un 20 % — peor, porque parece correcto. Reino Unido es el caso que
  obliga a separar las dos preguntas: señaliza en millas y reposta en litros, pero habla de su coche
  en mpg imperiales. Por eso `GB` está en `IMPERIAL_COUNTRIES` y **no** en `US_GALLON_COUNTRIES`,
  ambos en `LocaleUnits.kt`, por el camino que ya abrió [SETTINGS-UNITS-DEFAULT-FOLLOWS-COUNTRY-001].
- **El alta de vehículo es la parte delicada**, porque ahí el usuario TECLEA. Con el ajuste en millas
  el campo pide mpg, así que `VehicleRegistrationViewModel` recibe `AppPreferences` (sin tocar DI:
  `viewModelOf` resuelve el constructor y `AppPreferences` ya estaba declarado en ambos módulos) y
  convierte en las **dos** direcciones. Sin la vuelta, «42.8» se guardaría tal cual: un coche que
  gasta 42,8 litros a los cien, y toda la aritmética de ahorro leyendo ese número. Etiqueta y marcador
  van juntos o el campo pediría un número en una unidad sugiriendo otro en la contraria.
- `consumptionToDisplay`/`consumptionFromDisplay` son **inversas exactas**, con test sobre las cuatro
  combinaciones: el formulario enseña lo guardado y vuelve a guardar lo que quede en el campo, así que
  si divergen, editar el nombre de un coche le cambia el consumo.

#### Decidido: el galón sale del LOCALE, no del GPS (preguntado por el user, 16-09)

*«Dependiendo del país en que estemos, ¿no podemos obtener esa info al registrar el vehículo?»* — la
respuesta corta es que para métrico/imperial **ya pasa** (`localePrefersImperialUnits()` es el default
de `useImperialUnits` en Android y iOS, [SETTINGS-UNITS-DEFAULT-FOLLOWS-COUNTRY-001]), y para el galón
se descartó el GPS a propósito. Queda escrito porque es una pregunta que se vuelve a hacer sola:

1. **En el alta inicial no hay GPS.** El permiso de ubicación se pide *en el mapa*, al entrar en Home
   — después del formulario (`SplashViewModel`: «the fine location ask happens ON the map on first
   entry»). La señal no existe justo donde más se usa la pantalla.
2. **El consumo de un coche no cambia al cruzar una frontera.** Un británico de viaje por Francia
   sigue teniendo un coche de 51 mpg imperiales; con el GPS pasaría a marcar 42,8 al cruzar. Y el caso
   feo no es cosmético: abrir el formulario en un país y guardarlo en otro convertiría con galones
   distintos a la ida y a la vuelta, y ahí sí se corrompe el valor guardado.

El locale es la señal correcta porque dice **de dónde eres**, no dónde estás. Falla sólo en el
«británico con el móvil en inglés-US», y aun así únicamente si tiene el ajuste en imperial. La
alternativa descartada, por si vuelve: un ajuste explícito de galón, que quita la adivinación a cambio
de una fila más que mantener en nueve locales.

### Gálibo

«2.10 m» vivía en dos sitios y en ninguno de verdad: como `~2.10 m` dentro de un comentario, y otra
vez tecleado en la frase, en nueve locales. Ahora es `STANDARD_GARAGE_CLEARANCE_METERS` en dominio
—al lado de `requiresHighCeiling`, que es quien lo usa— y la frase lo recibe formateado.

`formatHeight` no pasa por `formatDistance`, y tampoco es una excepción a la regla del formateador
único: **una altura no se dice como una distancia**. `formatDistance` daría «6.9 ft» y un gálibo se
dice en pies y PULGADAS; además una distancia corta pasa a millas a partir de los 1.000 pies, y una
altura no pasa a millas nunca.

### Y el guard se afinó en vez de crecer la allowlist

Al arreglar el consumo, el guard empezó a cazar «Consumption (l/100 km)». La distinción correcta no
era una excepción: **`l/100 km` es el NOMBRE de una unidad, no una medida** — no lleva cifra delante
que pueda desfasarse, y cuál de las cuatro se usa lo decide ya el ajuste. La barra es lo que las
separa, así que eso se le enseñó al PATRÓN (`(?<![/\d.,])`). Una allowlist de cinco keys habría dado
barra libre también a las medidas de verdad que se colaran en esas mismas frases. **La allowlist baja
de siete entradas a dos**, las dos de minutos.

## Follow-up que queda abierto

**El precio del combustible sigue siendo por LITRO** (`vehicle_registration_fuel_price_label`:
«Price per litre»). El alcance real es más pequeño de lo que parece: en UK la combinación actual es
la CORRECTA —litros en el surtidor, mpg en el coche—, así que esto sólo chirría en **US**.

> ⛔ **Corregido el 16-09 al abrir [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001].** Este
> párrafo decía que hacerlo *«obliga a bajar el galón a toda la capa de precios (`StationPrice`,
> `AverageFuelPriceTable`, las alertas de precio y los feeds oficiales): es un ticket de datos, no de
> copy»*. **Está al revés, y ejecutarlo literalmente es el bug.** El KDoc de `AverageFuelPriceTable`
> ya lo prohíbe con su razón medida: *un galón llegando a esa clase infla un viaje en EE. UU. ×3,79
> en silencio*. El dominio se queda en **litros**; el galón es un problema de **frontera**, simétrico
> a cómo `OpenVanFuelPricesParser` ya normaliza al ENTRAR los catorce países que cotizan por galón.
> Es un ticket de presentación —la salida y, sobre todo, la ENTRADA del formulario— y no toca ni un
> feed.
