# FUEL-PRICE-RESOLVER-001 · El gasto deja de ser null: tu precio, o la media de tu país

**Estado:** ✅ Done · rama `feature/FUEL-PRICE-RESOLVER-001-cost-stops-being-null`

Primera mitad de la **F5 de la ola 2** ([`plus-roadmap-001.md`](plus-roadmap-001.md)). La segunda
—el adaptador MINETUR y las gasolineras cercanas en Home— va aparte en `FUEL-STATIONS-001`.

## Por qué se parte en dos

F5 tal y como está escrita en el roadmap es un puerto con adaptadores por país, un adaptador español
con caché en Room por provincia, una sección colapsable en Home, una capa de marcadores en el mapa y
un peek. Pero **la promesa de la fase es una sola cosa**: que la cifra de gasto exista. Y esa cifra
NO necesita ninguna gasolinera.

El propio diseño del roadmap lo dice con tres escalones: (1) adaptador nacional por gasolinera,
(2) media nacional de una tabla estática versionada en la app, (3) precio puesto por el usuario, que
siempre gana. **Los escalones 2 y 3 no tocan la red, funcionan en los nueve países y encienden el
gasto en todas partes.** El escalón 1 añade precisión en España.

Así que este ticket hace 2 y 3 —y deja el PUERTO definido para que el adaptador entre sin tocar
nada más—, y `FUEL-STATIONS-001` hace el 1 con su UI. Mismo corte que F4/F4b, y por el mismo motivo:
cada mitad se puede publicar sola.

## Problema

Desde STATS-CORE-001 el agregador sabe calcular el gasto, y desde STATS-UI-001 la ficha sabe
pintarlo. Pero `costEstimate` devuelve **null siempre**, porque nadie le pasa un precio: la regla
es que sin precio para CADA energía consumida no hay coste (un total parcial miente). Resultado: el
tile de gasto no existe y el teaser de free arranca con CO₂. La pantalla está esperando un dato que
nadie produce.

## Doctrina

- **La ausencia de precio ya está bien resuelta y no se toca**: `costEstimate` sigue siendo null si
  falta el precio de alguna energía consumida o si hay dos monedas. Este ticket no relaja esa regla,
  le da de comer.
- **El override del usuario manda**, igual que en el consumo: `Vehicle.consumptionPer100 ?? FuelDefaults`
  ya establece la forma. El precio la repite: `Vehicle.fuelPricePerLiter ?? media nacional`.
- **Ningún escalón exige red en el momento de calcular** (roadmap). La tabla de medias viaja en el
  binario; el override vive en el vehículo.
- **El país se resuelve al vuelo y no se guarda** (privacidad, roadmap): sale de
  `AddressInfo.countryCode`, que el reverse geocoder ya estampa en cada aparcamiento.
- ⛔ **Una media es una media y la UI lo dice.** La cifra se etiqueta con su procedencia ("precio
  medio en España", "tu precio"), porque un gasto calculado con la media nacional y otro con el
  precio real del usuario no merecen la misma confianza — misma familia que
  [UI-HISTORY-A-PARTIAL-SUM-IS-NOT-A-TOTAL-001].

## Diseño

- `domain/fuel/FuelPrice.kt` — `FuelPrice(perUnit: Money, source: FuelPriceSourceKind)` con
  `USER_OVERRIDE | NATIONAL_AVERAGE | STATION` (el tercero lo estrena FUEL-STATIONS-001, pero el
  enum nace completo: un `when` exhaustivo obliga a contestar por el escalón que falta).
- `domain/fuel/StaticAverageFuelPrices.kt` — tabla `countryCode × FuelType` con **fecha de vigencia
  declarada**, para los 9 países que la app habla + fallback de zona euro. Constantes nombradas y
  documentadas: una media que nadie sabe de cuándo es, es un número inventado.
- ⛔ **El puerto `FuelPriceSource` NO se escribe aquí, y es una desviación consciente del plan de
  arriba.** Definirlo hoy habría significado diseñar `priceNear(...)` sin su adaptador: ¿qué
  coordenada pone precio a un PERIODO entero?, ¿se descarga por provincia?, ¿qué se cachea y cuánto
  dura? Esas respuestas son de `FUEL-STATIONS-001`, y una interfaz moldeada a base de suposiciones
  no es ventaja, es algo contra lo que el adaptador tendrá que pelear. El escalón entra como
  PARÁMETRO (`stationPrice`) que hoy nadie pasa; **el ORDEN vive igualmente en el resolver y es lo
  que fijan los tests**, así que el adaptador solo tendrá que rellenar ese hueco.
- `domain/fuel/ResolveFuelPriceUseCase` — **veredicto** (se cita en la UI: la etiqueta de procedencia
  sale de aquí), así que es caso de uso y no predicado [DET-VERDICT-NOT-PREDICATE-001]. Orden:
  override del vehículo → adaptador (cuando exista) → media nacional → null.
- `Vehicle` gana `fuelPricePerLiter` y `energyPricePerKwh` (Money nullable), espejando la pareja de
  consumo. ⛔ Cambio de esquema → **bump de Room + `Migration` en `ALL_MIGRATIONS`** + paridad
  DTO/entity/serializers end-to-end.
- Alta/edición de vehículo: dos campos opcionales con su ayuda ("si lo dejas vacío usamos la media
  de tu país").
- Cableado: quien resuelve las stats pasa el precio a `ComputePeriodStatsUseCase`. Un solo sitio.

## Lo que apareció al construirlo

- ⛔ **Un híbrido reposta gasolina, y la tabla no le daba fila.** `LITRE` precia PRODUCTOS —los tres
  que vende un surtidor— y `HYBRID`/`PLUG_IN_HYBRID` no son ninguno, así que el gasto de todo
  híbrido volvía null y la pantalla lo habría enseñado como "no hay cifra" en vez de como el bug que
  era. Lo cazó su propio test. Se arregla con `FuelType.pumpProduct()`, un `when` exhaustivo para
  que un combustible nuevo tenga que contestar a qué surtidor va.
- **¿En qué moneda escribe el usuario?** El formulario no conoce el país (un coche no tiene país) y
  obligar a elegir divisa para meter un número es un peaje sobre el camino honesto. Se resuelve con
  un puerto `platformCurrencyCode()` expect/actual —la moneda en la que está configurado ESTE
  teléfono—, y **si la plataforma no sabe decirla, los campos no se ofrecen**: un importe cuya
  moneda nadie conoce significa tres cosas distintas en tres móviles.
- **Room a v5** con tres columnas (`fuelPricePerLiter`, `energyPricePerKwh`, `priceCurrency`). Una
  sola columna de moneda para los dos importes: quien reposta y carga en el mismo país paga en la
  misma moneda.
- **El país del periodo** sale del aparcamiento MÁS RECIENTE que sepa el suyo. Un periodo a caballo
  de una frontera se precia donde acabó el conductor, que es donde probablemente repostó; la
  alternativa —preciar cada sesión en su país y sumar— daría un total en monedas mezcladas, y el
  agregador ya rechaza esos.

## Criterio de éxito

- Tests del resolver: override gana a media; sin override cae a media; país sin tabla → null;
  EV usa `energyPricePerKwh`; PHEV exige las dos y con una sola sigue dando `costEstimate` null.
- Test de que la tabla de medias cubre los 9 locales que la app habla.
- Test de migración Room ida y vuelta con los campos nuevos.
- El tile de gasto APARECE en la ficha con datos de mock, etiquetado con su procedencia.
- Strings ×9 · galería · Konsist · suite · assembles · klib iOS.

## Verificación (08-09-2026)

- **En device (emulador, mock)**: la ficha abre con **«5,64 EUR · APPROX. SPEND»** como primera
  cifra —sin velo para el usuario free, porque el gasto es la cifra sobre la que se puede actuar— y
  al pie de la tarjeta, *«Spend estimated with your country's average price»*.
- Testigos: `ResolveFuelPriceUseCaseTest` tests="11" failures="0" · `ComputePeriodStatsUseCaseTest`
  13/0 (el agregador sigue verde con precios de verdad) · `AppDatabaseV1BaselineTest` 4/0, que
  afirma que un fichero v1 acaba en `PAPARCAR_DB_VERSION` — ahora 5 · guardarraíles de tipografía,
  color y paridad de locales a cero fallos.
- Suite, `assembleProdDebug`, `assembleMockDebug` y `compileKotlinIosSimulatorArm64` en verde.
- ⚠️ **El dinero se formatea con DOS decimales.** Salió en device: la primera versión imprimía
  «5.6 EUR» porque reutilicé el formateador de consumos, que recorta el decimal sobrante. Los
  céntimos no son ruido sobrante: son parte de lo que un precio ES.
- ⚠️ **El panel se quedaba sin la advertencia.** Enseña la MISMA cifra que la ficha y no llevaba la
  línea de procedencia — el mismo número con aviso en una pantalla y sin él en la otra. Cableado.

## Consumidores auditados

- `ComputePeriodStatsUseCase(fuelPricePerLiter, energyPricePerKwh)` — ya aceptaba los dos; solo
  faltaba quien se los diera ✔
- `HistoryTimeline.resolve` — ÚNICO sitio donde se resuelve el precio, y por eso el panel se arregló
  solo: `StatsViewModel` lee de la misma resolución ✔
- `Vehicle` → entity, DTO, 4 mappers, deserializador remoto + 3 constantes de campo, reconcile,
  formulario (estado, intents, VM, pantalla) ✔
- `ALL_MIGRATIONS` — `MIGRATION_4_5` con las tres columnas; testigo v1→v5 verde ✔
- `platformCurrencyCode()` — expect + actual Android (JVM `Currency`) + actual iOS (`NSLocale`) ✔
