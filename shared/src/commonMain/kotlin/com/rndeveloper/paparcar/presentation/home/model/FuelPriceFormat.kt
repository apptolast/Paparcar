package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.GallonKind
import com.rndeveloper.paparcar.domain.fuel.StationPrice
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.presentation.util.pumpPriceToDisplay
import com.rndeveloper.paparcar.pumpGallonForCurrency
import kotlin.math.roundToInt

/**
 * The ONE way a pump price is written — list row, map capsule and station peek all quote it, and
 * three private copies is how one of them rounds differently some day.
 * [FUEL-STATIONS-MAP-AND-PEEK-001]
 *
 * ⛔ **Three decimals, not two.** Fuel is quoted to a tenth of a cent, and that tenth is frequently
 * the entire difference between two forecourts — rounding 1,879 and 1,875 both to "1,88" would erase
 * exactly the comparison this feature exists to make. **The gallon does not change this**: the US
 * quotes $3.499/gal on the very same signage, to the same tenth of a cent.
 */
internal fun formatPumpPrice(amount: Double): String {
    val thousandths = (amount * PRICE_SCALE).roundToInt()
    val whole = thousandths / PRICE_SCALE.toInt()
    val fraction = (thousandths % PRICE_SCALE.toInt()).toString().padStart(PRICE_DECIMALS, '0')
    return "$whole.$fraction"
}

/**
 * `1.879` — a stored per-LITRE amount, written in the unit this driver actually buys it in.
 * [I18N-A-PRICE-IS-QUOTED-IN-THE-UNIT-YOU-BUY-IT-001]
 *
 * ⛔ **This is THE outbound boundary for every fuel price**, and it is one function because the
 * readers are four surfaces plus a worker: the sheet row, the station peek, the two map capsules and
 * `FuelPriceWatchWorker`, which writes both alert bodies through here. A second place converting the
 * same number is how one of them would some day quote litres to an American.
 */
internal fun Money.pumpPriceFigure(): String =
    formatPumpPrice(pumpPriceToDisplay(amount, pumpGallon()))

/**
 * La unidad en la que está cotizado ESTE dinero. La decide la MONEDA, no el móvil.
 * [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001]
 *
 * ⛔ Antes entraba como parámetro y cada superficie lo resolvía por su cuenta desde
 * `LocalPumpGallon` — o sea, desde la región del teléfono. Cinco call sites haciendo la misma
 * pregunta equivocada es un bug con cinco copias: un usuario en España con el móvil en región US
 * veía «7.552 EUR» en las gasolineras de su calle. Ahora el precio trae su unidad consigo y no hay
 * parámetro que pasar mal.
 */
private fun Money.pumpGallon(): GallonKind? = pumpGallonForCurrency(currency)

/**
 * `1.879 EUR/l` — importe en la unidad en la que se vende, moneda tal y como la da el feed, **y la
 * unidad dicha en voz alta**. [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001]
 *
 * ⛔ **La unidad no es opcional.** Un precio de combustible sin unidad es ambiguo por un factor de
 * 3,785, y cuando el número se desvió fue precisamente lo que impidió verlo: «7.552 EUR» no dice si
 * miente el número o la unidad. El símbolo (`l`, `gal`) es el mismo en los nueve idiomas —es
 * notación, no prosa, igual que la `m`/`km` de `formatDistance`— así que no lleva recurso.
 */
internal fun Money.pumpPriceLabel(): String = "${pumpPriceFigure()} $currency/${pumpUnitSymbol()}"

/** `l` o `gal`: el símbolo de la unidad en la que está cotizado este precio. */
private fun Money.pumpUnitSymbol(): String = if (pumpGallon() == null) LITRE_SYMBOL else GALLON_SYMBOL

private const val LITRE_SYMBOL = "l"
private const val GALLON_SYMBOL = "gal"

/**
 * Whether this figure needs saying out loud that somebody else pumps it.
 * [FUEL-ITALY-A-PRICE-DEPENDS-ON-WHO-PUMPS-IT-001]
 *
 * ⛔ **Only the attended price is marked, never the self one.** Self-service is the default
 * everywhere the question is even asked, and in the four countries that do not ask it
 * [StationPrice.isSelf] is null — so marking "self" would put a word on every price in Europe to
 * qualify the exception. The exception is what gets the word.
 *
 * Asked as a question so no call site spells `isSelf == false`, which is the comparison that reads
 * as a typo and gets "simplified" into `!isSelf` — silently marking every French price attended.
 */
internal fun StationPrice.isAttendedService(): Boolean = isSelf == false

private const val PRICE_SCALE = 1_000.0
private const val PRICE_DECIMALS = 3
