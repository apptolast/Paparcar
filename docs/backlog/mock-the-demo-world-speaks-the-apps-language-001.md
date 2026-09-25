# MOCK-THE-DEMO-WORLD-SPEAKS-THE-APPS-LANGUAGE-001 · El mundo del mock habla el idioma de la app

**Estado:** ✅ Done · mergeado a master en squash

## Problema

El mundo del mock está en El Puerto de Santa María, y todo lo que se lee en pantalla está en
español: `Calle Larga 14`, `Área de Servicio La Bahía`, `Parking Plaza Mayor`,
`Supermercado La Bahía`, `Aparcamiento Estación`, `Cádiz`, `Andalucía`, `España`.

Eso sale en el vídeo de demo —que va en inglés porque las bases lo exigen— y saldrá en cualquier
captura que se haga para un mercado que no sea el español. La app **se publica en todos los países
que admite Google Play**, así que el mundo de demostración no puede estar atado a uno.

Lo que peor lee no son los nombres propios: un jurado entiende que una calle se llame *Calle Larga*.
Son las **palabras de categoría** —«Área de Servicio», «Aparcamiento», «Supermercado», «Parking»—,
que hacen pensar que la app no está traducida aunque lo esté.

## Doctrina violada

Ninguna, y esto importa decirlo bien. [MOCK-A-FAKE-NAME-ENDS-UP-ON-CAMERA-001] escribió el
invariante que separa los dos textos de un fake:

> Un **topónimo** describe algo real y se queda en su idioma; una **etiqueta que escribe el usuario**
> sigue al idioma base del proyecto.

Ese invariante **sigue intacto y no se toca**. Lo que cambia no es la regla sino el **escenario**: el
mundo de demostración se muda a una ciudad de habla inglesa, y entonces sus topónimos son ingleses
*porque es donde está*, no porque los hayamos traducido. Traducir «Calle Larga» a «Long Street»
habría roto la regla; mudar la demo a Dublín la respeta.

## Señales / datos disponibles

- El mundo son **41 coordenadas** y ~29 cadenas, repartidas en cinco fakes:
  `FakeUserParkingRepository`, `FakeSpotRepository`, `FakeFuelStationsSource`,
  `FakeOtherDataSources` y `FakeSharedCarRepository`.
- Las capturas y el vídeo se graban con la app forzada a **`en-IE`**, que es lo que da métrico y
  euro (`6.5 l/100 km`, `1.759 EUR/l`, `56.0 km`). Medido el 25-09 al descubrir que `en-US` pone
  millas y mpg.
- Las coordenadas actuales están en **36.58–36.60 N, −6.22 a −6.23 W**.

## Diseño

**La ciudad es Dublín**, y la razón es que es la única que no obliga a tocar nada más:

| candidata | inglés | moneda | distancia | veredicto |
|---|---|---|---|---|
| **Dublín** | ✅ | **euro** | **km / l** | ✅ encaja con `en-IE`, con los precios y con las cifras que ya hay |
| Londres | ✅ | libra | millas | ✗ rompe los precios en EUR y las distancias en km |
| Sídney / Toronto | ✅ | otra | km | ✗ moneda distinta, y husos que no casan con las horas de las sesiones |

Con Dublín, **los números no cambian**: 1,759 EUR/l es un precio irlandés creíble, los l/100 km
siguen siendo la unidad, y `en-IE` deja de ser un truco de grabación para ser el idioma del sitio.

**Las coordenadas se mudan con los nombres.** Un rótulo de calle de Dublín sobre una coordenada
gaditana pintaría etiquetas inglesas encima de un mapa de Andalucía, que es peor que cualquiera de
los dos estados por separado. La mudanza es **un solo desplazamiento aplicado a las 41**
(`lat += 16.759`, `lon −= 0.030`), no 41 coordenadas reescritas a mano: así la **topología se
conserva** —qué plaza está cerca de cuál, qué gasolinera es la más próxima, la forma de las rutas—
y no hay que volver a calibrar ninguna de las cifras que dependen de la geometría.

⛔ **Ninguna marca de terceros.** Las bases del Shipaton prohíben marcas ajenas en el vídeo, así que
los negocios son inventados y plausibles (`Bayside Service Station`), nunca cadenas reales.

## Criterio de éxito

- Ni una palabra en español en lo que se lee del mapa, la hoja o el historial del mock.
- Las tres pestañas de «IN THIS AREA» siguen dando 6 plazas, 4 parkings y 5 gasolineras.
- Las distancias y los tiempos siguen siendo los mismos órdenes de magnitud: nada de una plaza
  «a 11 km» donde antes había 179 m.
- `./gradlew testAll` en verde.
- Verificado en device con `en-IE` y el GPS en Dublín: el mapa pinta Dublín y los rótulos casan.

## Verificado el 25-09-2026

- `./gradlew testAll` → **BUILD SUCCESSFUL**.
- En el Pixel 8 con `en-IE` y el GPS en Dublín: el peek dice **`Grafton Street · Dublin, County
  Dublin`**, y el mapa pinta calles reales de Dublín (Parnell Square, Granby Row, Trinity College).
- Las tres pestañas siguen dando **6 plazas · 4 parkings · 5 gasolineras**, y las distancias siguen
  en su orden de magnitud (73–457 m), no una plaza «a 11 km».
- Los precios siguen leyéndose `1.759 EUR/l` y el consumo `6.5 l/100 km`: Dublín no obligó a
  recalibrar ni una cifra, que era justo el motivo de elegirla.

⚠️ **El coche del grupo estaba en Madrid** (40,4 / −3,7), fuera del patrón de El Puerto, así que el
desplazamiento general no lo alcanzaba. Movido aparte: sin eso, el historial compartido habría
quedado a 1.500 km del resto del mundo.

## Consumidores auditados

| Sitio | Veredicto |
|---|---|
| `fakes/…/FakeUserParkingRepository.kt` | **cerrado** — calles del historial + ciudad/región/país |
| `fakes/…/FakeSpotRepository.kt` | **cerrado** — plazas de la comunidad y sus lugares |
| `fakes/…/FakeFuelStationsSource.kt` | **cerrado** — nombres y direcciones de gasolineras |
| `fakes/…/FakeOtherDataSources.kt` | **cerrado** — parkings públicos |
| `fakes/…/FakeSharedCarRepository.kt` | **cerrado** — dirección del coche del grupo |
| `presentation/preview/FakeData.kt` | **cerrado** — son los `@Preview` de los MISMOS componentes que salen en el vídeo; dejarlos en Madrid hacía que el preview y la app se contradijeran |
| `app/src/mock/…/StateGalleryScreen.kt` · direcciones de sesión y búsqueda | **cerrado** — 5 fixtures genéricas |
| `app/src/mock/…/StateGalleryScreen.kt` · `Repsol`, `Petroprix`, `Madrid`, `EMT Madrid`, variantes «(España)» | **EXENTO a propósito** — ver abajo |
| `commonTest/` | **exento** — fixtures; no se renderizan y su geografía no la ve nadie |

### ⛔ Lo que NO se muda, y por qué

Dos variantes de la galería se llaman **«Con precios (España)»** y **«Peek de gasolinera (España,
con precios)»**, y sus fixtures son `Repsol` y `Petroprix` en Madrid. Eso **no es el mundo de demo**:
es la prueba de una conducta real de producto — España publica el precio **por surtidor** a través de
su registro oficial, y casi ningún otro país lo hace. Mudar esas fixtures a Dublín dejaría una
variante que dice «España» enseñando Irlanda, y borraría el único sitio donde se revisa esa
diferencia. La regla, otra vez, es la misma: **el mundo de demo se muda; una fixture que existe para
probar un comportamiento de un país concreto, no.**

Esas marcas son reales, pero viven sólo en la galería y no llegan a ninguna captura ni al vídeo,
donde sí estarían prohibidas.
