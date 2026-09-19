package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.GallonKind
import com.rndeveloper.paparcar.domain.model.Money
import com.rndeveloper.paparcar.pumpGallonForCurrency
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * El precio se cotiza donde SE VENDE, no donde está el teléfono.
 * [FUEL-A-PRICE-IS-QUOTED-WHERE-IT-IS-SOLD-001]
 *
 * El caso que abre este fichero es real (issue #40): un usuario en El Puerto de Santa María con el
 * móvil en región US veía «7.552 EUR» en las gasolineras de su calle — el número de un galón, la
 * moneda de un litro, y ninguna de las dos unidades escrita en la etiqueta. La app multiplicaba por
 * 3,785 porque le preguntaba al locale del móvil en qué unidad reposta la gente.
 */
class PumpPriceMarketTest {

    @Test
    fun should_quotePerLitre_when_thePriceIsInEuros() {
        assertNull(pumpGallonForCurrency("EUR"), "ningún país que cotiza en euros vende por galón")
        assertEquals("1.995 EUR/l", Money(1.995, "EUR").pumpPriceLabel())
    }

    @Test
    fun should_quotePerGallon_when_thePriceIsInDollars() {
        assertEquals(GallonKind.US, pumpGallonForCurrency("USD"))
        // 0.9219 $/l son 3.490 $/gal — la cifra que se lee en la señal de una gasolinera americana.
        assertEquals("3.490 USD/gal", Money(0.9219, "USD").pumpPriceLabel())
    }

    @Test
    fun should_notMultiply_when_theCurrencyIsUnknownOrAbsent() {
        // Un precio sin moneda no autoriza a multiplicar por 3,785: ante la duda, el litro, que es
        // lo que hacen 193 países.
        assertNull(pumpGallonForCurrency(null))
        assertNull(pumpGallonForCurrency(""))
        assertNull(pumpGallonForCurrency("   "))
    }

    @Test
    fun should_ignoreTheCase_when_theFeedShoutsItsCurrency() {
        assertEquals(GallonKind.US, pumpGallonForCurrency("usd"))
        assertNull(pumpGallonForCurrency("eur"))
    }

    /**
     * La etiqueta dice SIEMPRE su unidad. Sin ella, el mismo texto puede ser un precio por litro o
     * uno por galón, y esa ambigüedad es lo que impidió ver el bug: «7.552 EUR» no dice si miente el
     * número o la unidad.
     */
    @Test
    fun should_nameItsUnit_when_anyPriceIsWritten() {
        assertTrue(Money(1.879, "EUR").pumpPriceLabel().endsWith("/l"))
        assertTrue(Money(1.879, "USD").pumpPriceLabel().endsWith("/gal"))
    }
}
