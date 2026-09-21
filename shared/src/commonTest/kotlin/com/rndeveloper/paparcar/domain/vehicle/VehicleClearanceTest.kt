package com.rndeveloper.paparcar.domain.vehicle

import com.rndeveloper.paparcar.domain.model.CarbodyType
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [VEH-A-HEIGHT-UNKNOWN-IS-NOT-A-FIT-001]
 *
 * ⛔ El invariante de todo el fichero cabe en una frase: **sólo `FITS` autoriza a prometer**, y
 * llegar a `FITS` cuesta más que llegar a `DOES_NOT_FIT`. Equivocarse escondiendo un parking cuesta
 * un rodeo; equivocarse prometiendo que cabes cuesta el techo del coche.
 */
class VehicleClearanceTest {

    private fun car(
        body: CarbodyType? = null,
        declared: Float? = null,
        size: VehicleSize = VehicleSize.MEDIUM_SUV,
    ) = Vehicle(
        id = "v1",
        userId = "u1",
        sizeCategory = size,
        carbodyType = body,
        heightMeters = declared,
    )

    @Test
    fun should_notPromiseAnything_when_theCarParkDoesNotDeclareItsClearance() {
        // El 93-100 % de los parkings no lo declara. Ni con el coche mejor conocido del mundo se
        // puede contestar una pregunta que nadie ha hecho.
        assertEquals(
            ClearanceVerdict.UNKNOWN,
            car(body = CarbodyType.HATCHBACK_SMALL, declared = 1.45f).clearanceUnder(null),
        )
    }

    @Test
    fun should_notPromiseAnything_when_weKnowNothingAboutTheCar() {
        // Sin carrocería y sin altura declarada no hay banda: un coche legacy no autoriza veredictos.
        assertEquals(ClearanceVerdict.UNKNOWN, car().clearanceUnder(2.0))
    }

    @Test
    fun should_sayItFits_when_theWholeBandClearsTheBarrierWithMargin() {
        // Un utilitario (1,40-1,60) bajo 2,00: la banda entera pasa, y sobran los 5 cm de margen.
        assertEquals(
            ClearanceVerdict.FITS,
            car(body = CarbodyType.HATCHBACK_SMALL).clearanceUnder(2.0),
        )
    }

    @Test
    fun should_sayItDoesNotFit_when_evenTheLowestOfTheBandIsTooTall() {
        // Una furgoneta comercial (1,90-2,80) bajo 1,80: no pasa ni en el mejor de los casos.
        assertEquals(
            ClearanceVerdict.DOES_NOT_FIT,
            car(body = CarbodyType.VAN_COMMERCIAL).clearanceUnder(1.80),
        )
    }

    @Test
    fun should_keepQuiet_when_theBandStraddlesTheBarrier() {
        // ⛔ EL testigo del ticket. Un SUV grande mide entre 1,65 y 1,85: bajo una barrera de 1,80
        // hay coches de ésos que pasan y coches que no, y elegir sería tirar una moneda con el
        // techo de alguien. Aquí es donde un «cabes» inventado se convierte en un golpe.
        assertEquals(
            ClearanceVerdict.UNKNOWN,
            car(body = CarbodyType.SUV_LARGE).clearanceUnder(1.80),
        )
    }

    @Test
    fun should_letTheDeclaredHeightDecide_when_theBodyAloneCouldNot() {
        // …y declarar el alto es justo lo que rompe ese empate, en los dos sentidos.
        val suv = CarbodyType.SUV_LARGE
        assertEquals(
            ClearanceVerdict.FITS,
            car(body = suv, declared = 1.70f).clearanceUnder(1.80),
        )
        assertEquals(
            ClearanceVerdict.DOES_NOT_FIT,
            car(body = suv, declared = 1.95f).clearanceUnder(1.80),
        )
    }

    @Test
    fun should_obeyTheDeclaredHeight_over_theCatalog() {
        // El cofre en el techo: la carrocería dice utilitario y el usuario dice 2,05. Manda él —
        // ningún catálogo del mundo sabe lo que alguien atornilló esta mañana.
        assertEquals(
            ClearanceVerdict.DOES_NOT_FIT,
            car(body = CarbodyType.HATCHBACK_SMALL, declared = 2.05f).clearanceUnder(1.90),
        )
    }

    @Test
    fun should_notPromise_when_itFitsByLessThanTheMargin() {
        // ⚠️ El margen asimétrico, medido: 1,60 de alto bajo 1,62 de barrera CABE en el papel y no
        // se promete, porque un neumático nuevo o el coche cargado se comen esos 2 cm.
        assertEquals(
            ClearanceVerdict.UNKNOWN,
            car(body = CarbodyType.HATCHBACK_SMALL, declared = 1.60f).clearanceUnder(1.62),
        )
        assertEquals(
            ClearanceVerdict.FITS,
            car(body = CarbodyType.HATCHBACK_SMALL, declared = 1.60f).clearanceUnder(1.65),
        )
    }

    @Test
    fun should_measureAMotorcycle_withoutACarbody() {
        // Una moto no tiene carrocería que consultar y pasa por cualquier barrera de coches.
        assertEquals(
            ClearanceVerdict.FITS,
            car(body = null, size = VehicleSize.MOTORCYCLE).clearanceUnder(2.0),
        )
    }

    @Test
    fun should_giveEveryBodyABand_that_isOrderedAndPlausible() {
        // Un guardarraíl sobre la tabla: si alguien añade una carrocería o toca una cifra, esto
        // exige que siga siendo un rango de un vehículo de calle y no un typo.
        CarbodyType.entries.forEach { body ->
            val band = body.heightBand()
            assertTrue(band.lowMeters < band.highMeters, "$body: la banda tiene que ser un rango")
            assertTrue(band.lowMeters >= 1.0f, "$body: ningún coche mide menos de 1 m")
            assertTrue(band.highMeters <= 3.0f, "$body: por encima de 3 m ya no aparca en ciudad")
        }
    }

    @Test
    fun should_haveNoBand_when_thereIsNeitherDeclaredHeightNorBody() {
        assertNull(car().heightBand())
    }
}
