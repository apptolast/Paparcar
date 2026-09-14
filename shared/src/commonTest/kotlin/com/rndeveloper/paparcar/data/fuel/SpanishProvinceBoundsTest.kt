package com.rndeveloper.paparcar.data.fuel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * La tabla generada y su desempate. [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
 *
 * ⛔ Los puntos NO son inventados: son los mismos doce con los que se midió la sonda del geocoder,
 * y sus ids son los que el ministerio publica en `/Listados/Provincias/`. Un test que se inventara
 * las coordenadas estaría comprobando que la tabla es la tabla.
 */
class SpanishProvinceBoundsTest {

    @Test
    fun `should_resolveTheProvince_forPointsAcrossSpain`() {
        val points = listOf(
            Triple("El Puerto de Santa María", 36.6019 to -6.2598, "11"),
            Triple("Madrid · Sol", 40.4169 to -3.7035, "28"),
            Triple("Sevilla", 37.3886 to -5.9953, "41"),
            Triple("Girona", 41.9794 to 2.8214, "17"),
            Triple("León", 42.5987 to -5.5671, "24"),
            Triple("Trujillo", 39.4590 to -5.8820, "10"),
            Triple("Palma de Mallorca", 39.5696 to 2.6502, "07"),
        )

        points.forEach { (name, coords, expected) ->
            assertEquals(
                expected,
                SpanishProvinceBounds.idFor(coords.first, coords.second),
                "$name debería resolver a la provincia $expected",
            )
        }
    }

    @Test
    fun `should_resolveTheAutonomousCities_whereThereIsNoProvinceNameAtAll`() {
        // 🔑 El caso que ninguna otra clave cubre: medido en device, Ceuta y Melilla NO traen
        // `subAdminArea`, así que el camino por nombre se queda sin provincia que emparejar.
        assertEquals("51", SpanishProvinceBounds.idFor(35.8894, -5.3213), "Ceuta")
        assertEquals("52", SpanishProvinceBounds.idFor(35.2923, -2.9381), "Melilla")
    }

    @Test
    fun `should_answerNothing_outsideSpain`() {
        // Null no es un fallo: es la respuesta normal en el resto del mundo, y es lo que mantiene a
        // esta fuente fuera de consultas que no son suyas.
        assertNull(SpanishProvinceBounds.idFor(48.8566, 2.3522), "París")
        assertNull(SpanishProvinceBounds.idFor(38.7223, -9.1393), "Lisboa")
        assertNull(SpanishProvinceBounds.idFor(51.5074, -0.1278), "Londres")
    }

    @Test
    fun `should_breakTheTie_byNearestCentre_whenBoxesOverlap`() {
        // Un punto cae de media en TRES cajas, así que sin desempate esto sería una moneda al aire.
        // Sol está dentro de la caja de Madrid y también de las de sus vecinas, y gana Madrid.
        val madrid = 40.4169 to -3.7035
        val containing = listOf("28", "45", "05", "40", "19").filter { id ->
            SpanishProvinceBounds.idFor(madrid.first, madrid.second) == id
        }
        assertEquals(listOf("28"), containing, "el desempate tiene que dar Madrid, no una vecina")
    }
}
