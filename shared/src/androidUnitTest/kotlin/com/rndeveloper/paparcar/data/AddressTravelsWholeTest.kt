package com.rndeveloper.paparcar.data

import com.rndeveloper.paparcar.data.mapper.toDomain
import com.rndeveloper.paparcar.data.mapper.toDto
import com.rndeveloper.paparcar.data.mapper.toEntity
import com.rndeveloper.paparcar.detection.worker.ReportSpotWorker
import com.rndeveloper.paparcar.detection.worker.ReportSpotWorker.Companion.toAddressInfo
import com.rndeveloper.paparcar.domain.model.AddressInfo
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.Spot
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Una dirección viaja entera**, por las TRES vías que la escriben.
 * [FUEL-A-PARKING-WITHOUT-A-COUNTRY-HAS-NO-PRICE-001] [FUEL-A-POSTCODE-IS-THE-PROVINCE-ID-001]
 *
 * ### Por qué existe
 * Room ya tenía su guardarraíl (`ParkingAddressSurvivesEnrichmentTest`) desde que se descubrió que
 * `countryCode` se había caído de cuatro vías de escritura a la vez. Las otras dos no tenían
 * ninguno, y son exactamente las mismas por las que se cayó: el DTO de Firestore y el `workData`
 * del worker. Un campo nuevo se añade al modelo, se rellena donde el autor estaba mirando, y las
 * demás lo pierden **en silencio** — sin error, sin crash, sin nada que lo delate hasta que una
 * cifra no se renderiza meses después.
 *
 * ### Cómo lo impide
 * El fixture lo vigila [assertFixtureSetsEveryField]: **cualquier propiedad nueva de [AddressInfo]
 * pone este test en rojo** hasta que alguien la rellene, y entonces los viajes de ida y vuelta de
 * abajo comprueban si de verdad sobrevive. No hay lista de exenciones que mantener ni criterio que
 * recordar: la enumeración la hace la reflexión, no una persona.
 *
 * Reflexión de Java y no `kotlin-reflect`, igual que su gemelo de Room: sin dependencia nueva, y
 * `declaredFields` es justo la lista que crece cuando alguien añade una propiedad.
 */
class AddressTravelsWholeTest {

    @Test
    fun `should_keepEveryAddressField_through_theFirestoreDto`() {
        assertFixtureSetsEveryField(FULL_ADDRESS)

        val roundTripped = spot().toDto().toDomain().address

        assertEquals(FULL_ADDRESS, roundTripped)
    }

    @Test
    fun `should_keepEveryAddressField_through_theSpotsTable`() {
        assertFixtureSetsEveryField(FULL_ADDRESS)

        // La fila de Room se construye DESDE el DTO, que es como llega en producción.
        val roundTripped = spot().toDto().toEntity().toDomain().address

        assertEquals(FULL_ADDRESS, roundTripped)
    }

    @Test
    fun `should_keepEveryAddressField_through_theWorkerInputData`() {
        assertFixtureSetsEveryField(FULL_ADDRESS)

        // El escritor y el lector REALES, no una copia de ellos: un `workDataOf` que se olvide de
        // un campo tiene que caerse aquí. ⚠️ `set()` sin merge ya mandó campos a null una vez.
        val input = ReportSpotWorker.buildRequest(
            spotId = "spot-1",
            lat = 40.4168,
            lon = -3.7038,
            address = FULL_ADDRESS,
            placeInfo = null,
        ).workSpec.input

        assertEquals(FULL_ADDRESS, input.toAddressInfo())
    }

    @Test
    fun `should_readBackAnAddress_that_carriesOnlyOneField`() {
        // ⛔ La otra mitad del ticket: la pregunta «¿hay dirección aquí?» vive en
        // `AddressInfo.isBlank` y lee TODOS los campos. Estaba escrita tres veces a mano y las tres
        // discrepaban — la de las sesiones aparcadas se dejaba fuera `province`, así que una
        // dirección que solo tuviera provincia volvía como null y se tiraba.
        //
        // Se prueba campo a campo: cada uno, él solo, tiene que bastar para que haya dirección.
        listOf(
            AddressInfo(street = "Calle Mayor 1", city = null, region = null, country = null),
            AddressInfo(street = null, city = "Madrid", region = null, country = null),
            AddressInfo(street = null, city = null, region = "Comunidad de Madrid", country = null),
            AddressInfo(street = null, city = null, region = null, country = "España"),
            AddressInfo(street = null, city = null, region = null, country = null, countryCode = "es"),
            AddressInfo(street = null, city = null, region = null, country = null, province = "Madrid"),
            AddressInfo(street = null, city = null, region = null, country = null, postalCode = "28013"),
        ).forEach { single ->
            assertEquals(
                single,
                spot(single).toDto().toDomain().address,
                "una dirección con un solo campo no es una dirección vacía: $single",
            )
        }
    }

    @Test
    fun `should_readBackNothing_when_theAddressNamesNothing`() {
        // Y el contrapunto, adosado: si de verdad no hay nada, no se inventa una dirección vacía.
        val nothing = AddressInfo(street = null, city = null, region = null, country = null)

        assertTrue(nothing.isBlank)
        assertNull(spot(nothing).toDto().toDomain().address)
    }

    private fun spot(address: AddressInfo? = FULL_ADDRESS) = Spot(
        id = "spot-1",
        location = GpsPoint(
            latitude = 40.4168,
            longitude = -3.7038,
            accuracy = 8f,
            timestamp = 1L,
            speed = 0f,
        ),
        reportedBy = "user-1",
        address = address,
    )

    /** Refuses a fixture that does not exercise every field of [AddressInfo]. */
    private fun assertFixtureSetsEveryField(fixture: AddressInfo) {
        val unset = AddressInfo::class.java.declaredFields
            .filterNot { it.isSynthetic }
            .mapNotNull { field ->
                field.isAccessible = true
                field.name.takeIf { field.get(fixture) == null }
            }
        assertTrue(
            unset.isEmpty(),
            "El fixture deja $unset a null, así que este test no puede probar que esos campos " +
                "sobreviven. Rellénalos — y luego asegúrate de que las tres vías los escriben.",
        )
    }

    private companion object {
        /** Todos los campos poblados a propósito: un fixture con huecos no prueba nada de los huecos. */
        val FULL_ADDRESS = AddressInfo(
            street = "Calle Mayor 1",
            city = "Madrid",
            region = "Comunidad de Madrid",
            country = "España",
            countryCode = "es",
            province = "Madrid",
            postalCode = "28013",
        )
    }
}
