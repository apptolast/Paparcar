# I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001 · El precio se cotiza en la unidad en la que lo compras

**Estado:** ✅ Done · en master · ⏳ **visto en emulador (Pixel 8 Pro, locale en-US de fábrica)**,
sin ver aún en un móvil físico

## Problema

La app cotiza el combustible SIEMPRE por litro. En EE. UU. esa no es una unidad incómoda: es la
unidad equivocada, porque allí nadie compra litros. Muerde en dos sitios opuestos y **uno de los dos
es mucho peor que el otro**:

- **(a) SALIDA — se lee mal, pero se lee.** El precio de estación (`pumpPriceLabel`, fila de la hoja,
  cápsula del mapa, peek), el texto de las dos alertas de precio («%1$s cheaper per litre than
  yesterday») y la copia de onboarding («what the litre cost»). Un americano ve un número que no
  reconoce y una palabra que no usa; sabe que algo va raro.
- **(b) ENTRADA — miente sin síntoma.** `vehicle_registration_fuel_price_label` dice «Price per litre
  (USD)». Un estadounidense teclea **3,49** pensando en su galón y
  `VehicleRegistrationViewModel:383` lo guarda tal cual en `Vehicle.fuelPricePerLiter`. A partir de
  ahí **todos** sus gastos salen inflados **×3,785** — 87 € en vez de 23 € al mes — con un número que
  parece perfectamente correcto en todas las pantallas. No hay pantalla que chille, no hay crash, no
  hay dato absurdo: sólo una factura mensual falsa.

Misma familia que [I18N-A-RADIUS-WRITTEN-BY-HAND-IGNORES-MILES-001]: **una unidad tecleada a mano que
miente donde el país no la usa.** Aquel ticket cerró el radio, la distancia y el consumo; dejó el
precio abierto y el follow-up quedó redactado al revés (ver §*Corrección del follow-up*).

## Doctrina violada

1. **La unidad se traduce en el BORDE, nunca en el dominio.** Es el invariante que el KDoc de
   `AverageFuelPriceTable` ya escribió con su medida: *«A gallon reaching this class would inflate a
   US drive by 3.79× in silence»*. `OpenVanFuelPricesParser` lo cumple al ENTRAR (normaliza los
   catorce países que cotizan por galón), y `ConsumptionFormat.kt` lo cumple al SALIR. El precio es
   el único que tiene el borde de entrada puesto y el de salida no.
2. **El formulario pide el número en la unidad en la que luego lo enseña** — la frase literal del
   KDoc de `consumptionFieldLabel`. Hoy el campo de precio pide litros a quien sólo ve galones.
3. **Sistemas, no parches.** No se arregla la etiqueta: se pone la frontera, con sus dos sentidos, en
   un sitio; cambiar sólo la palabra «litre» por «gallon» convertiría el bug silencioso (b) en un bug
   silencioso peor.

## Corrección del follow-up de I18N-A-RADIUS-…-001 (entra en este ticket)

`docs/backlog/i18n-a-radius-written-by-hand-ignores-miles-001.md:181-185` dice que esto *«obliga a
bajar el galón a toda la capa de precios (`StationPrice`, `AverageFuelPriceTable`, las alertas de
precio y los feeds oficiales): es un ticket de datos, no de copy»*.

**Eso es exactamente lo que no hay que hacer, y el KDoc de `AverageFuelPriceTable` ya lo prohíbe con
su razón medida.** Bajar el galón al dominio ES el bug de ×3,79 que ese KDoc documenta. La frase se
corrige en aquel doc como parte de esta tarea: el galón es un problema de **frontera**, simétrico a
cómo el generador ya normaliza al entrar; el ticket es de **presentación**, no de datos.

## ⛔ Invariante que NO se toca

**El dominio se queda en litros.** No cambian de unidad:

| Pieza | Sigue siendo |
|---|---|
| `Vehicle.fuelPricePerLiter` (+ columna Room, campo Firestore `fuelPricePerLiter`) | €/litro |
| `StationPrice.perUnit` · `FuelPrice.perUnit` | €/litro |
| `AverageFuelPriceTable` / `CountryFuelPrices` | €/litro |
| `EvaluateCheaperStationNearbyUseCase` (umbral 0,03) | €/litro |
| `EvaluateDailyPriceMoveUseCase` (umbral 0,005) | €/litro |
| Los cuatro feeds nacionales + `OpenVanFuelPricesParser` + `tools/fuelaverages` | €/litro |

Sin migración de datos y sin cambio de esquema: **lo guardado nunca cambia de unidad**, sólo lo que se
enseña y lo que se lee del teclado. Un precio ya guardado por un usuario europeo sigue significando lo
mismo mañana.

Consecuencia deliberada de los umbrales: en EE. UU. el aviso de «media noticia» salta con 0,005 €/l =
**1,9 ¢/gal**, no con medio centavo de galón. Es el mismo dinero por depósito que en Europa, que es lo
que el umbral mide; convertir el umbral sería subir el listón ×3,8 sin decidirlo.

## Señales / datos disponibles

El resolver ya existe y **se reutiliza, no se escribe otro** — `LocaleUnits.kt`:

- `GallonKind { US, IMPERIAL }`
- `countryUsesUsGallon(code)` · `localeUsesUsGallon()` · `localeGallonKind()`
- `US_GALLON_COUNTRIES = {US, LR}` ≠ `IMPERIAL_COUNTRIES = {US, GB, LR, MM}`

⛔ **Y aquí está la trampa que hay que escribir antes de empezar: `localeGallonKind()` NO sirve para
esta pregunta.** Devuelve `IMPERIAL` para España, para Francia y para todo el mundo que no sea US/LR,
porque contesta *«cuando este país dice mpg, ¿qué galón quiere decir?»*. Usarlo como selector de
unidad de surtidor pondría precios en galones imperiales en toda Europa. La pregunta de este ticket
es otra: *¿en qué unidad se COBRA en el surtidor?* → `localeUsesUsGallon()`, y sólo eso.

Dos consecuencias ya decididas y medidas en el ticket anterior, que este respeta:

- **El galón sale del LOCALE, no del GPS** (razonado en aquel doc: en el alta no hay permiso de
  ubicación todavía, y abrir el formulario en un país y guardarlo en otro corrompería el valor).
- **UK no se toca.** `GB` está en `IMPERIAL_COUNTRIES` pero **no** en `US_GALLON_COUNTRIES`: en el
  Reino Unido el surtidor cobra por litro aunque el coche se hable en mpg imperiales. La combinación
  actual en UK ya es la correcta.

⛔ **No se añade un ajuste explícito de unidad de volumen**: se descartó al decidir que el galón sale
del locale (una fila más que mantener en nueve locales a cambio de quitar una adivinación que falla
sólo en el «británico con el móvil en inglés-US»).

## Diseño

### 1 · La frontera: una función pura, dos sentidos, un sitio

Fichero nuevo `presentation/util/PumpPriceFormat.kt`, **hermano** de `ConsumptionFormat.kt` y no dentro
de él: el consumo es una magnitud *por distancia* que se INVIERTE (l/100 km ⇄ mpg); esto es dinero
*por volumen*, que es un factor limpio. Compartirían fichero sólo por sonar parecido.

```kotlin
/** null ⇒ este surtidor cobra por litro y no hay nada que convertir. */
fun pumpPriceToDisplay(perLitre: Double, gallon: GallonKind?): Double
fun pumpPriceFromDisplay(shown: Double, gallon: GallonKind?): Double
```

Mismo contrato que `consumptionToDisplay`/`consumptionFromDisplay`: **inversas exactas**, y la métrica
(`null`) es la identidad. `commonMain` → su test corre también en iOS.

Y el selector, en `LocaleUnits.kt` junto a lo que ya decide: `localePumpGallon(): GallonKind?` —
`GallonKind.US` si `localeUsesUsGallon()`, `null` en cualquier otro caso. **Nunca `IMPERIAL`**: no hay
país que cobre el combustible en galones imperiales, y devolverlo aquí sería reabrir la trampa de
arriba por la puerta de atrás.

Los litros por galón viven en **un** sitio (`LITRES_PER_US_GALLON = 3.785411784`, junto a `GallonKind`).
`OpenVanFuelPricesParser.LITRES_PER_UNIT` tiene hoy su propia copia del mismo número: se apunta a la
constante compartida (sin cambio de comportamiento) para que el número exista una vez.

### 2 · SALIDA

**Un solo punto de conversión: `Money.pumpPriceLabel()`** en `FuelPriceFormat.kt`. Lo usan la fila de
la hoja, el peek de estación, las dos cápsulas del mapa (vía `formatPumpPrice`) **y el worker de
alertas** (`FuelPriceWatchWorker:69,81`), así que la conversión entra una vez y cubre las cuatro
superficies y las dos notificaciones.

- ⚠️ **Los tres decimales se quedan.** EE. UU. también cotiza `$3.499/gal`; el tercer decimal es la
  comparación entera, exactamente igual que en Europa.
- La media nacional **no se cotiza por unidad en ninguna pantalla**: sólo multiplica km → dinero, y el
  dinero no tiene denominador de volumen. El gasto del periodo, el recap del mes y la nota
  «`stats_cost_from_average`» no cambian ni un carácter. (Auditado abajo.)
- ⚠️ Hoy **no hay fuente de estaciones en EE. UU.** ([FUEL-US-A-STATE-AVERAGE-IS-NOT-A-PUMP-001]), así
  que el carril de estaciones es un no-op en la práctica. Se convierte igual: una frontera que sólo
  funciona cuando alguien la mira no es una frontera. **Este ticket no desbloquea EE. UU.** ni amplía
  la capa de gasolineras.

### 3 · ENTRADA (el bug que no tiene síntoma)

`VehicleRegistrationViewModel`, pareja simétrica a `consumptionToField`/`consumptionFromField` que ya
existe justo debajo:

- Cargar (`:291`): `fuelPricePerLiter` → `pumpPriceToDisplay` → campo.
- Guardar (`:383`): campo → `pumpPriceFromDisplay` → `Money` en **litros**.

⛔ **Las dos direcciones van siempre en pareja.** El formulario enseña lo guardado y vuelve a guardar
lo que quede en el campo: convertir sólo al guardar multiplicaría el precio por 3,785 cada vez que
alguien abre la ficha a cambiar el nombre del coche.

El comentario de ese bloque (`:449-452`) dice hoy *«los precios no pasan por aquí, que son dinero, no
consumo»*. Esa frase es justo la premisa falsa del ticket —el dinero **sí** lleva denominador de
volumen— y se corrige ahí mismo.

`energyPricePerKwh` **no se toca**: un kWh es un kWh en los 195 países.

Redondeo de ida y vuelta: el campo de precio se pinta hoy con `formatConsumption` a precisión de Float
completa. En galones se escribe con **3 decimales** (precisión de surtidor), lo que hace la ida y
vuelta un punto fijo: `1,879 €/l → 7,113 $/gal → 1,878991 €/l → 7,113 $/gal` — estable a partir del
primer guardado, con una deriva única de 1·10⁻⁵ €/l (una milésima de céntimo). Medido y aceptado; lo
alternativo (guardar el texto tecleado) sería meter la unidad de presentación en el modelo.

### 4 · Copy

Variantes de galón, **no** frases compuestas — «per litre» no es un sufijo pegable en nueve idiomas, y
las notificaciones ya viven por esa regla («cuatro frases enteras, no una palabra de dirección pegada a
un tronco»):

| Key | Dónde | Locales |
|---|---|---|
| `vehicle_registration_fuel_price_gallon_label` | `composeResources` | 9 |
| `first_steps_explain_explore_fuel_gallon_body` | `composeResources` | 9 |
| `notif_fuel_cheaper_text_gallon` | `app/src/main/res` | 9 |
| `notif_fuel_move_text_{down,up,down_more,up_more}_gallon` | `app/src/main/res` | 9 |

⛔ Recordatorios que muerden en este reparto: en `composeResources` el apóstrofo va **crudo** (`'`) y
el `%` literal **sin doblar**; en `app/src/main/res` el apóstrofo va **escapado** (`\'`) — son las dos
reglas opuestas de [COPY-APOSTROPHE-IS-NOT-ESCAPED-001] / [COPY-AN-APOSTROPHE-GUARD-THAT-NEVER-EXISTED-001].
La elección de key se centraliza en `FuelPresentation.kt`, junto a `consumptionFieldLabel`, que ya
resuelve exactamente este tipo de pregunta.

### 5 · Dev Catalog

En la MISMA tarea: variante en galones en `StateGalleryScreen.kt` para la ficha de vehículo
(campo + etiqueta) y para la lista de gasolineras. Sin escenario de routing nuevo — la unidad no
cambia a dónde va nadie.

### 6 · iOS

`LocaleUnits.kt` es `commonMain` y se apoya en `androidx.compose.ui.text.intl.Locale.current`, que ya
viaja en iOS: es el mismo camino por el que `localePrefersImperialUnits()` da el default de
`useImperialUnits` en las dos plataformas ([I18N-A-RADIUS-…-001] §iOS: *«el lado iOS viaja con el
ticket»*). Todo lo nuevo de este ticket es `commonMain` y su test es `commonTest`, así que **corre en
iOS sin hueco que documentar**. Queda por verificar en la propia suite que `Locale.current.region`
contesta no-vacío en Native; si no lo hiciera, el hueco se escribe con su causa y el fallback es
métrico (que es el comportamiento actual).

## Criterio de éxito

- Un precio tecleado en galones se **persiste en litros** — con un test que lo mira. El bug (b) no
  tiene síntoma visible, así que sin testigo vuelve. [TEST: *a green suite must prove it looked*]
- En un locale `US`, la ficha de coche pide «Price per gallon (USD)» y el precio de estación se
  escribe por galón, con tres decimales.
- En `ES`, `FR`, **y en `GB`**, no cambia absolutamente nada (byte a byte en el copy).
- `./gradlew testAll` en verde.

### Tests

1. `PumpPriceFormatTest` (commonTest): litro→galón US, la inversa, ida y vuelta sin deriva apreciable,
   y `gallon = null` que no convierte nada.
2. `LocaleUnitsTest`: `localePumpGallon()` para US/LR → `US`; para GB/ES → `null`. **El caso GB es el
   guard del ticket**: es el que distingue esta pregunta de `localeGallonKind()`.
3. **El testigo que falta**: `VehicleRegistrationViewModelTest` — con el locale en galones, teclear
   `3.49` y comprobar que el `Vehicle` guardado lleva `fuelPricePerLiter ≈ 0.922`, y que recargar la
   ficha devuelve `3.49` al campo.
4. Paridad de locales: la cubre `LocaleParityGuardrailTest` (9 locales) + `NotificationCopyParityTest`.

## Consumidores auditados

`grep` de todo lo que asume «un precio es por litro»:

| Sitio | Veredicto |
|---|---|
| `Vehicle.fuelPricePerLiter`, `VehicleEntity`, `VehicleDto`, `VehicleMapper`, `VehicleReconcile`, Room v5 | **Exento — es el dominio.** Litros, sin migración |
| `ComputePeriodStatsUseCase`, `RunMonthRecapUseCase`, `HistoryState:164` | **Exento.** Consumen litros y producen DINERO; el dinero no tiene unidad de volumen |
| `ResolveFuelPriceUseCase`, `SessionFuelPrices` | **Exento.** Resuelven qué precio, no en qué unidad |
| `OpenVanFuelPricesParser` (`LITRES_PER_UNIT`) | **Cubierto.** Ya normaliza al entrar; sólo se le pasa la constante compartida |
| `EmbeddedAverageFuelPrices` + `tools/fuelaverages` | **Exento por encargo.** No se tocan |
| `Money.pumpPriceLabel()` / `formatPumpPrice` | **Cerrado.** Es LA frontera de salida |
| `HomeFuelStationRows:107,125` · `FuelStationPeek:159` · `PaparcarMapView:1049,1425` | **Cubiertos** vía la frontera; sus KDoc dicen «per litre» y se reescriben |
| `FuelPriceWatchWorker:69,81` | **Cubierto** vía la misma frontera (usa `pumpPriceLabel`) |
| `AppNotificationManagerImpl:241,279` + 5 keys | **Cerrado** con variantes de galón |
| `VehicleRegistrationViewModel:291,383` + `Screen:822-824` | **Cerrado.** Único call site del campo; alta y edición comparten pantalla |
| `first_steps_explain_explore_fuel_body` | **Cerrado** con variante |
| `SharedCarDto` / `SharedCarRepositoryImpl` | **Exento, y a propósito**: el coche compartido **no viaja con su precio** («what a litre costs is each reader's own»), así que cada miembro lo lee en SU unidad. El grupo mixto US/ES ya sale bien |
| `settings_notif_fuel_alerts_desc` | **Exento.** Habla de «cambia de precio», sin unidad |
| `consumptionUnitLabel` / `consumptionFieldLabel` (mpg) | **Exento.** Otro eje: ése lo manda el ajuste imperial, éste el locale |

## Lo que cambió al ejecutarlo

Tres decisiones que el diseño de arriba no tenía previstas, y una que sí y se confirmó:

### 1 · La unidad entra por `LocalPumpGallon`, no se lee del locale dentro del formateador

El diseño decía «`pumpPriceLabel` lee `localePumpGallon()`». **No sirve**, y lo demostró el punto 5
del encargo: la galería de estados corre en un móvil español, así que una variante «en galones» era
imposible de pintar — el formateador se habría leído el locale del device y habría enseñado litros
bajo un rótulo que promete galones.

Así que la unidad **entra como parámetro** (`pumpPriceLabel(gallon)`, `pumpPriceFigure(gallon)`) y la
UI la saca de un `CompositionLocal` hermano de `LocalDistanceUnit`, con `localePumpGallon()` de valor
por defecto y provisto en `App.kt` junto al sistema de unidades. Los dos no-composables la piden
explícitamente: `FuelPriceWatchWorker` y `AppNotificationManagerImpl.quotesPerGallon()`.

Efecto lateral necesario en `PaparcarMapView`: la cápsula lleva la cifra dentro de su `contentId`, así
que `pumpGallon` entra como CLAVE de los dos `remember` de marcadores — igual que `themeKey`.

### 2 · El ViewModel recibe la unidad por constructor

Por la misma razón, elevada: **el testigo del ticket no se puede escribir si la unidad se lee de un
singleton**. `VehicleRegistrationViewModel(..., pumpGallon: () -> GallonKind? = ::localePumpGallon)`,
y su registro en Koin pasa de `viewModelOf(::…)` a constructor explícito, porque el DSL `…Of`
intentaría resolver del grafo el parámetro que tiene valor por defecto.

### 3 · El precio se parsea como `Double`, no como `Float` (defecto preexistente)

Lo cazó el test del carril de litros: teclear «1,879» guardaba **1.878999948501587**. `Money.amount`
es `Double` y el formulario lo estaba pasando por `parseConsumption`, que devuelve `Float`. Existía
desde antes de este ticket y era invisible; con una multiplicación de unidad delante ya no lo es.
Nace `parsePrice`, con el mismo criterio (coma o punto, positivo) y sin pasar por `Float`.

### 4 · Confirmado: la media nacional no se cotiza por unidad en ninguna pantalla

Auditado y cierto — sólo multiplica km → dinero. El punto 2 del encargo («la media nacional en las
superficies de stats») **no tenía nada que cambiar**, y eso es un hallazgo, no un descuido: hoy, en
EE. UU., el ÚNICO sitio donde un precio por volumen es visible es el campo del formulario. O sea, el
bug (b) era el bug entero y el (a) es casi todo latente.

## Verificación

- `./gradlew testAll` ✅ · guards de Konsist y paridad con `--rerun-tasks` ✅ ·
  `:app:compileMockDebugKotlin` + `:app:compileProdDebugKotlin` ✅
- ✅ **Visto en el emulador**, que arranca con `ro.product.locale=en-US` — no hubo nada que falsear.
  Por el **grafo real** (`Registro de vehículo` del Dev Catalog, ViewModel vivo, no la galería):
  el campo dice **«Price per gallon (USD)»** y sostiene `3.499`; el paso 5 del onboarding dice
  **«Where to refuel and what the gallon cost…»**. Apóstrofos crudos, limpios.
- ⏳ Falta un móvil físico, y el guardado por UI no se llegó a completar: las dos burbujas flotantes
  del Dev Catalog (`DEV` y el sol) tapan la fila «BODY AND SIZE» y se comen los taps. La conversión
  de ENTRADA la sostiene su test, que es el testigo duradero.

### Lo que el emulador enseñó de paso (ninguno es de este ticket)

1. **La media de EE. UU. parece alta**: la fila del binario es `USD 1.0845` gasolina / `1.4743`
   diésel por litro = **$4,10 y $5,58 por galón**, donde lo normal ronda $3,1–3,5. Antes de este
   ticket ese número era **invisible** —nadie lee $/L— y ahora un americano lo va a leer en la unidad
   que sí reconoce. El ticket no arregla la media: convierte un dato malo en un dato VISIBLE.
2. **El mock no puede enseñar el carril imperial**: en el emulador US el consumo sale en `l/100 km`.
   No es un fallo de producción —`AndroidDataStoreAppPreferences:345` e `IosAppPreferences:371` sacan
   el default de `localePrefersImperialUnits()`— sino del fake: `FakeOtherDataSources:290` arranca en
   `MutableStateFlow(false)` e **ignora el locale**. Una línea, pero es del ticket hermano.
- **iOS**: todo lo nuevo es `commonMain` + `commonTest`, sin un solo `expect/actual`, y la señal de
  locale es la MISMA que `localePrefersImperialUnits()` ya usa en producción en iOS. No hay hueco que
  documentar. No se ha compilado el target iOS porque esta máquina es Windows: Kotlin/Native para iOS
  exige macOS, no es una omisión del ticket.

## Fuera de alcance (deliberado)

- Ampliar la capa de gasolineras a EE. UU. → [FUEL-US-A-STATE-AVERAGE-IS-NOT-A-PUMP-001].
- Ajuste explícito de unidad de volumen → descartado y razonado arriba.
- Convertir los umbrales de las dos alertas → razonado arriba: miden dinero por depósito.
- **Follow-up abierto con su doc**: `stats_value_liters` («142 L») es la única magnitud de volumen que
  no pasa por ningún borde — ni con el ajuste imperial ni con locale de EE. UU. →
  [STATS-A-VOLUME-BURNED-IS-QUOTED-IN-LITRES-EVERYWHERE-001]. No entra aquí porque un volumen a secas
  no es un precio, y decidir qué galón le toca es una pregunta abierta (la del coche, no la del
  surtidor).
