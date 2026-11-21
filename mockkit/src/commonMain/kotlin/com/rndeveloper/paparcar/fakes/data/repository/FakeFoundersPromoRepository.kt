package com.rndeveloper.paparcar.fakes.data.repository

import com.rndeveloper.paparcar.domain.repository.FoundersClaimOutcome
import com.rndeveloper.paparcar.domain.repository.FoundersPromoRepository
import com.rndeveloper.paparcar.fakes.MockScenario
import kotlin.time.Clock

/**
 * El timbre de la promo, sin red. [MONEY-FOUNDERS-PLUS-PROMO-001]
 *
 * **Scenario-aware, y eso es lo que hace que la palanca del Dev Catalog siga funcionando.** Desde
 * que la bienvenida exige prueba de ser fundador (`foundersPromoGrantedUids`), un fake que
 * contestara siempre `NotGranted` dejaría «Plus de REGALO (promo Fundadores)» encendido y **sin
 * efecto visible** — la trampa exacta que el Dev Catalog existe para no tener: una palanca puesta
 * que no enseña nada se lee como que la feature está rota.
 *
 * Así que el recibo lo decide la palanca, y el resto del camino es el de producción: el caso de uso
 * real apunta el uid, refresca y felicita. Sin escenario (iOS, tests) contesta `NotGranted`, que es
 * la respuesta honesta cuando no hay campaña que simular.
 */
class FakeFoundersPromoRepository(
    private val scenario: MockScenario? = null,
) : FoundersPromoRepository {

    /** Para quien quiera comprobar que se llamó una sola vez. */
    val claimedUids: MutableList<String> = mutableListOf()

    /** Qué contesta, si se quiere forzar. Null = lo decide el escenario. */
    var result: Result<FoundersClaimOutcome>? = null

    override suspend fun claim(uid: String): Result<FoundersClaimOutcome> {
        claimedUids += uid
        result?.let { return it }
        val sc = scenario ?: return Result.success(FoundersClaimOutcome.NotGranted)
        if (!sc.plusIsPromotional.value) return Result.success(FoundersClaimOutcome.NotGranted)
        // La fecha sale de la misma palanca que gobierna el grant, para que el diálogo y Ajustes
        // digan el mismo día. Null = regalo sin fecha de fin, que es una posición de la palanca.
        val expiresAtMillis = sc.plusDaysLeft.value?.let { days ->
            Clock.System.now().toEpochMilliseconds() + days * MILLIS_PER_DAY
        }
        return Result.success(FoundersClaimOutcome.Granted(expiresAtMillis))
    }

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
