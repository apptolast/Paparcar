package com.rndeveloper.paparcar.push

import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.domain.push.SharedCarFenceRefresher
import com.rndeveloper.paparcar.fakes.FakeAppNotificationManager
import com.rndeveloper.paparcar.fakes.FakeUserParkingRepository
import com.rndeveloper.paparcar.fakes.FakeZoneWatchRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * El reparto que las dos plataformas comparten. [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]
 *
 * Lo que de verdad se comprueba aquí es un ORDEN, y por eso el refrescador de vallas y la
 * notificación escriben en el MISMO diario: una aserción sobre dos contadores separados no puede
 * distinguir «la valla primero» de «la valla también».
 */
class PushMessageHandlerTest {

    private val now = 1_000_000L

    /** Un solo diario para los dos lados, que es lo que hace comprobable el orden. */
    private val log = mutableListOf<String>()

    private val notifications = object : FakeAppNotificationManager() {
        override fun showZoneSpotAlert(
            zoneName: String,
            latitude: Double,
            longitude: Double,
            isProvisional: Boolean,
        ) {
            log.add("card:zone")
            super.showZoneSpotAlert(zoneName, latitude, longitude, isProvisional)
        }

        override fun showSharedCarParked(
            authorName: String?,
            addressLine: String?,
            latitude: Double,
            longitude: Double,
        ) {
            log.add("card:parked")
            super.showSharedCarParked(authorName, addressLine, latitude, longitude)
        }

        override fun showSharedCarLeft(authorName: String?) {
            log.add("card:left")
            super.showSharedCarLeft(authorName)
        }
    }

    private val fences = SharedCarFenceRefresher { log.add("fence") }
    private val zoneWatch = FakeZoneWatchRepository()

    private fun handler(parkings: FakeUserParkingRepository = FakeUserParkingRepository()) =
        PushMessageHandler(
            notificationPort = notifications,
            zoneWatch = zoneWatch,
            parkingRepository = parkings,
            sharedCarFences = fences,
            clock = { now },
        )

    private fun parkedPush(
        carId: String = "car-1",
        sessionId: String = "sess-theirs",
        parkedAtMs: Long = now,
        latitude: String? = "40.4000",
        longitude: String? = "-3.7000",
    ) = buildMap {
        put("type", "shared_car_parked")
        put("carId", carId)
        put("sessionId", sessionId)
        put("parkedAtMs", "$parkedAtMs")
        put("authorName", "Ana")
        put("addressLine", "Calle Mayor")
        latitude?.let { put("latitude", it) }
        longitude?.let { put("longitude", it) }
    }

    private fun mySession(id: String, atMs: Long, latitude: Double, longitude: Double) = UserParking(
        id = id,
        vehicleId = "car-1",
        location = GpsPoint(latitude, longitude, 8f, atMs, 0f),
        geofenceId = "fence-$id",
        isActive = true,
    )

    // ── El orden, que es la regla entera ────────────────────────────────────────────────────────

    @Test
    fun should_moveTheFenceBeforeTheCard_when_theGroupParkedTheCar() = runTest {
        handler().handle(parkedPush())

        assertEquals(listOf("fence", "card:parked"), log)
    }

    @Test
    fun should_moveTheFenceBeforeTheCard_when_theCarLeft() = runTest {
        handler().handle(mapOf("type" to "shared_car_left", "carId" to "car-1", "authorName" to "Ana"))

        assertEquals(listOf("fence", "card:left"), log)
        assertEquals("Ana", notifications.sharedCarLeftCards.single())
    }

    /**
     * ⛔ El caso que justifica que la valla vaya primero y SIN condiciones: un campo que falte cuesta
     * la tarjeta, nunca la vigilancia. Una valla plantada donde ya no hay coche es un trigger de
     * detección esperando a que su dueño pase andando por esa calle.
     */
    @Test
    fun should_stillMoveTheFence_when_theParkedPushHasNoPosition() = runTest {
        handler().handle(parkedPush(latitude = null, longitude = null))

        assertEquals(listOf("fence"), log)
        assertTrue(notifications.sharedCarParkedCards.isEmpty())
    }

    /** Un mensaje que no es nuestro no toca nada — ni la valla. */
    @Test
    fun should_touchNothing_when_theTypeIsUnknown() = runTest {
        handler().handle(mapOf("type" to "something_else"))

        assertTrue(log.isEmpty())
    }

    // ── El propio viaje, visto desde el asiento de al lado ───────────────────────────────────────

    /**
     * Dos personas del grupo en el mismo coche: el espejo se queda con el primero que llega y el
     * segundo recibiría un aviso de un viaje en el que iba. La exclusión del autor la hace el
     * servidor y ahí no alcanza — para el servidor son dos personas distintas, y lo son.
     */
    @Test
    fun should_silenceTheCardButNotTheFence_when_theNewsIsMyOwnTrip() = runTest {
        val parkings = FakeUserParkingRepository(
            initialSession = mySession("sess-mine", atMs = now, latitude = 40.4, longitude = -3.7),
        )

        handler(parkings).handle(parkedPush(parkedAtMs = now + 30_000))

        assertEquals(listOf("fence"), log)
        assertTrue(notifications.sharedCarParkedCards.isEmpty())
    }

    /** Mismo coche, otro día: el ancla queda fuera de la ventana y la noticia SÍ es de otro. */
    @Test
    fun should_showTheCard_when_myOwnParkingIsAnOlderOne() = runTest {
        val parkings = FakeUserParkingRepository(
            initialSession = mySession("sess-mine", atMs = now - 3_600_000, latitude = 40.4, longitude = -3.7),
        )

        handler(parkings).handle(parkedPush())

        assertEquals(listOf("fence", "card:parked"), log)
        assertEquals("Ana" to "Calle Mayor", notifications.sharedCarParkedCards.single())
    }

    /** Sin los campos que identifican el viaje no se puede comparar, y se falla del lado de contarlo. */
    @Test
    fun should_showTheCard_when_theNewsCarriesNoSessionToCompare() = runTest {
        val parkings = FakeUserParkingRepository(
            initialSession = mySession("sess-mine", atMs = now, latitude = 40.4, longitude = -3.7),
        )
        val push = parkedPush().toMutableMap().apply { remove("parkedAtMs") }

        handler(parkings).handle(push)

        assertEquals(listOf("fence", "card:parked"), log)
    }

    // ── La puerta única de la zona ──────────────────────────────────────────────────────────────

    @Test
    fun should_showTheSpotAndRememberIt_when_itIsTheFirstTimeItIsAnnounced() = runTest {
        handler().handle(zonePush())

        assertEquals(listOf("card:zone"), log)
        assertEquals(listOf("Centro"), notifications.zoneSpotAlerts)
        assertTrue(zoneWatch.hasAnnounced("zone-1", "spot-1"))
    }

    /**
     * El stream puede haberlo dicho ya con la app abierta. Lo que se ha dicho vive en un solo sitio,
     * y esta es la puerta que lo consulta.
     */
    @Test
    fun should_stayQuiet_when_theSameSpotWasAlreadyAnnounced() = runTest {
        zoneWatch.recordAnnounced("zone-1", "spot-1", now + 60_000, now)

        handler().handle(zonePush())

        assertTrue(log.isEmpty())
        assertTrue(notifications.zoneSpotAlerts.isEmpty())
    }

    @Test
    fun should_stayQuiet_when_theSpotExpiredBeforeDelivery() = runTest {
        handler().handle(zonePush(expiresAt = now - 1))

        assertTrue(log.isEmpty())
    }

    private fun zonePush(expiresAt: Long = now + 60_000) = mapOf(
        "type" to "zone_spot",
        "spotId" to "spot-1",
        "zoneId" to "zone-1",
        "zoneName" to "Centro",
        "latitude" to "40.4",
        "longitude" to "-3.7",
        "expiresAt" to "$expiresAt",
    )
}
