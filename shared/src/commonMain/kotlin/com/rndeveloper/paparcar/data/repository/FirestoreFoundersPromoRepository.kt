package com.rndeveloper.paparcar.data.repository

import com.rndeveloper.paparcar.domain.repository.FoundersClaimOutcome
import com.rndeveloper.paparcar.domain.repository.FoundersPromoRepository
import dev.gitlive.firebase.firestore.BaseTimestamp
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.code
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable

/** Creates the account's claim and waits for the server's receipt. */
class FirestoreFoundersPromoRepository(
    private val firestore: FirebaseFirestore,
) : FoundersPromoRepository {

    @Serializable
    private data class FoundersClaimDto(val claimedAt: BaseTimestamp)

    override suspend fun claim(uid: String): Result<FoundersClaimOutcome> = try {
        val claim = firestore.collection(COLLECTION).document(uid)
        firestore.runTransaction {
            if (!get(claim).exists) {
                set(claim, FoundersClaimDto.serializer(), FoundersClaimDto(Timestamp.ServerTimestamp))
            }
        }
        // ⛔ **Con tope.** Sin el, este `first()` espera un `outcome` que puede no llegar NUNCA —
        // la funcion sin desplegar, caida o con la plaza en `uncertain`— y deja la corrutina
        // suspendida con un listener de Firestore abierto lo que dure la sesion de UI, sin un solo
        // error. Agotarlo es un fallo TRANSITORIO a proposito: no marca el uid, asi que la proxima
        // entrada a Home vuelve a preguntar y el servidor contesta `already` si ya concedio.
        //
        // `withTimeoutOrNull` y no `withTimeout`: este `try` reenvia las `CancellationException`
        // para no tragarse la cancelacion del scope, y la de un timeout es una de ellas — se habria
        // propagado como si el ViewModel hubiera muerto.
        val outcome = withTimeoutOrNull(RECEIPT_TIMEOUT_MS) {
            claim.snapshots.mapNotNull { snapshot ->
                if (!snapshot.contains(OUTCOME_FIELD)) return@mapNotNull null
                when (snapshot.get<String>(OUTCOME_FIELD)) {
                    "granted" -> FoundersClaimOutcome.Granted(
                        expiresAtMillis = snapshot.takeIf { it.contains(EXPIRY_FIELD) }?.get<Long>(EXPIRY_FIELD),
                    )
                    "closed", "full", "inactive", "failed" -> FoundersClaimOutcome.NotGranted
                    else -> error("Unrecognized founders claim outcome")
                }
            }.first()
        } ?: return Result.failure(IllegalStateException("Founders receipt did not arrive in time"))
        Result.success(outcome)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (denied: FirebaseFirestoreException) {
        // ⛔ Un PERMISSION_DENIED aqui es la respuesta DEFINITIVA de la campaña, no una averia: la
        // regla `isPromoOpen()` deniega el `create` cuando la promo esta cerrada, agotada o
        // apagada. Tratarlo como fallo transitorio es lo que hacia que, a partir del 1 de
        // diciembre, TODA cuenta nueva intentase una escritura denegada en cada entrada a Home,
        // para siempre y en silencio — el uid no se marcaba nunca.
        if (denied.code == FirestoreExceptionCode.PERMISSION_DENIED) {
            Result.success(FoundersClaimOutcome.NotGranted)
        } else {
            Result.failure(denied)
        }
    } catch (error: Exception) {
        Result.failure(error)
    }

    private companion object {
        // Shared with firestore.rules and functions/src/foundersPromo.ts.
        const val COLLECTION = "foundersClaims"
        const val OUTCOME_FIELD = "outcome"
        const val EXPIRY_FIELD = "expiresAtMillis"

        /** Holgado para un arranque en frio de la funcion, corto para no vivir en la sesion. */
        const val RECEIPT_TIMEOUT_MS = 20_000L
    }
}
