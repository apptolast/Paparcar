# FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001 · El precio lo cotiza el mercado, no el móvil

**Estado:** ✅ Done · master `4463ba956` · cierra la **issue #40**

## Problema

Producción. Un usuario (Alex) ve en El Puerto de Santa María precios de **«7.552 EUR»**, cuando el
gasóleo de esa calle está a 1,995 €/l. Solo le pasa a él: borrar datos y reiniciar no lo arregla, y
en otro móvil los mismos surtidores salen bien.

**El número no es falso: es un galón.** 1,995 × 3,785411784 = 7,552. La misma captura lo firma dos
líneas más arriba: *«a 303 ft de ti»*. Ese móvil está en **región US**, y la app le da la vuelta a
todo lo que mide — distancia en pies y precio por galón —, incluido lo que se vende en la acera de
enfrente en litros y euros.

Y hay un segundo fallo que es el que impidió verlo en un vistazo: **la etiqueta no dice la unidad**.
«7.552 EUR» no permite distinguir si miente el número o si falta una palabra.

## Doctrina violada

`I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001` dejó escrito el principio correcto —*el precio
se enseña en la unidad en la que se paga*— pero lo implementó preguntándole al **locale del
teléfono**: `localePumpGallon()` → `Locale.current.region`. Eso contesta *«¿en qué unidad reposta la
gente del país de este móvil?»*, que es otra pregunta. El surtidor de tu calle no cambia con la
región de tu teléfono.

Además, cinco superficies resolvían la unidad por su cuenta (`LocalPumpGallon.current` en la fila, el
peek, las dos cápsulas del mapa; `localePumpGallon()` en el worker y en las notificaciones): **un
bug con cinco copias**.

## Diseño

**La unidad es una propiedad del MERCADO en el que se cotizó el precio, y quien nombra el mercado es
la MONEDA.** Ningún país que cotiza en euros vende por galón.

- `pumpGallonForCurrency(currency: String?)` en `LocaleUnits.kt` — `USD`/`LRD` → galón US, todo lo
  demás (y la ausencia de moneda) → litro.
- `Money.pumpPriceFigure()` / `Money.pumpPriceLabel()` **ya no reciben la unidad**: la resuelven
  desde su propia moneda. No hay parámetro que pasar mal en ninguna de las cinco superficies.
- **La etiqueta dice siempre su unidad**: `1.995 EUR/l`, `3.490 USD/gal`. El símbolo (`l`, `gal`) es
  notación y es el mismo en los nueve idiomas, igual que la `m`/`km` de `formatDistance`, así que no
  lleva recurso.
- La notificación recibe `quotesPerGallon` **pegado a la cifra que describe**, en vez de volver a
  preguntarle al locale: la frase y el número no pueden desafinar.
- El formulario de vehículo pasa a inyectar la **moneda** (`priceCurrency: () -> String?`) en vez del
  galón. Misma regla en toda la app.

## Consumidores auditados

| Sitio | Qué hacía | Decisión |
|---|---|---|
| `HomeFuelStationRows` (fila) | `LocalPumpGallon.current` | ✅ lo dice la moneda del precio |
| `FuelStationPeek` | `LocalPumpGallon.current` | ✅ ídem |
| `PaparcarMapView` (cápsula ×2) | `LocalPumpGallon.current` + clave del raster | ✅ ídem; la clave `pumpGallon` se retira (la moneda ya viaja en `fuelStations`) |
| `FuelPriceWatchWorker` | `localePumpGallon()` | ✅ moneda del precio, y la pasa a la notificación |
| `AppNotificationManagerImpl.quotesPerGallon()` | locale | ✅ borrado: entra como parámetro |
| `fuelPriceFieldLabel(currency)` | `LocalPumpGallon.current` | ✅ ya recibía la moneda; ahora la usa |
| `VehicleRegistrationViewModel` | `pumpGallon: () -> GallonKind?` | ✅ pasa a `priceCurrency: () -> String?` |
| `exploreFuelExplainerBody()` (onboarding) | locale | ⚠️ se queda: no hay precio ni país que mirar todavía — ver Preguntas |
| `StateGalleryScreen` (2 variantes EE. UU.) | proveía `LocalPumpGallon` a mano | ✅ ahora mienten como producción: precio en USD / `priceCurrency = "USD"` |
| `LocalPumpGallon` / `localePumpGallon()` | — | ✅ vivos para lo que sí es del dispositivo (el onboarding); ya no deciden ningún precio |

## Criterio de éxito

- Un móvil en región US, en España, lee «1.995 EUR/l» en las gasolineras de su calle.
- Un móvil en región ES, en EE. UU., leería «3.490 USD/gal».
- Toda etiqueta de precio nombra su unidad.
- `PumpPriceMarketTest` (5 casos) en verde, y `VehicleRegistrationViewModelTest` sigue cubriendo las
  dos direcciones de la conversión del formulario.

## Preguntas (para la revisión)

1. **Las DISTANCIAS siguen el locale** (por eso Alex ve «303 ft»). Eso sí tiene interruptor en
   Ajustes (`useImperialUnits`), así que es arreglable por el usuario y no he querido cambiar el
   valor por defecto en el mismo PR. Si lo quieres, el criterio honesto sería el mismo de aquí: que
   por defecto mande **dónde estás**, no cómo está el teléfono. Lo abro aparte si dices que sí.
2. **El paso de onboarding que promete precios** (`exploreFuelExplainerBody`) sigue nombrando la
   unidad según el locale, porque ahí todavía no hay ni precio ni país. En el móvil de Alex
   prometería galones y luego vería litros. La alternativa es una frase que no nombre unidad
   ninguna, y eso es copy nuevo en los nueve idiomas.
3. Alex debería ver el arreglo **sin tocar nada** al actualizar: no hay dato guardado que migrar —
   el dominio siempre guardó €/litro, lo que estaba mal era la salida.
