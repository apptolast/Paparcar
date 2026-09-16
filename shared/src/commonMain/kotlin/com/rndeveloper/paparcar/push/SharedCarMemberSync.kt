package com.rndeveloper.paparcar.push

import com.apptolast.baselogin.domain.AuthRepository
import com.rndeveloper.paparcar.data.datasource.remote.RemoteSharedCarDataSource
import com.rndeveloper.paparcar.domain.push.PushTokenProvider
import com.rndeveloper.paparcar.domain.repository.SharedCarRepository
import com.rndeveloper.paparcar.domain.util.PaparcarLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Deja el token de ESTE móvil en la ficha de miembro de cada coche del grupo.
 * [SHARED-A-PARKING-TELLS-THE-GROUP-001]
 *
 * ⛔ **Estar en la lista ES el permiso**, igual que en las alertas de zona: la Cloud Function no
 * pregunta si alguien tiene Plus —no puede, eso vive en RevenueCat— sino a quién mandarle el aviso,
 * y la respuesta son los tokens de `members/{uid}`. Salir del grupo borra la ficha y con ella el
 * aviso, sin tocar nada más.
 *
 * ⛔ Un flujo y no una escritura al arrancar, por la razón que [ZoneAlertSubscriptionSync] ya
 * documenta: una instalación limpia tiene Room vacío mientras la sincronización aterriza, así que
 * una sola lectura al inicio no suscribe nada y se queda mal hasta el siguiente arranque.
 *
 * ⛔ Un token NO es para siempre: se rota en reinstalación, restauración y borrado de datos. Por eso
 * [resyncNow] existe y el receptor de cada plataforma lo llama — una rotación que nadie apunta es un
 * móvil que conserva su sitio en el grupo y deja de recibir en silencio.
 *
 * Vive en `commonMain` desde [IOS-THE-GROUP-CANNOT-REACH-AN-IPHONE-001]: de todo lo de arriba, lo
 * único que era de Android era de dónde salía el token, y eso es ahora [PushTokenProvider].
 */
class SharedCarMemberSync(
    private val sharedCarRepository: SharedCarRepository,
    private val remote: RemoteSharedCarDataSource,
    private val authRepository: AuthRepository,
    private val pushTokens: PushTokenProvider,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nudge = MutableStateFlow(0)

    fun start() {
        scope.launch {
            combine(sharedCarRepository.observeSharedCars(), nudge) { cars, _ ->
                cars.map { it.carId }.sorted()
            }
                .distinctUntilChanged()
                .catch { e -> PaparcarLogger.w(TAG, "shared-car token sync stopped", e) }
                .collect { carIds -> publishToken(carIds) }
        }
    }

    /** Una rotación de token, o un cambio que estos flujos no ven. */
    fun resyncNow() {
        nudge.value = nudge.value + 1
    }

    /**
     * Un token por móvil, y la lista se REEMPLAZA en vez de crecer: la ficha de miembro es de una
     * persona, pero el reparto va a dispositivos, y un token viejo que nadie retira es el goteo que
     * convierte un envío barato en uno caro. Los muertos los poda además la function al fallar.
     */
    private suspend fun publishToken(carIds: List<String>) {
        if (carIds.isEmpty()) return
        val uid = authRepository.getCurrentSession()?.userId ?: return
        val token = runCatching { pushTokens.currentToken() }.getOrNull() ?: run {
            PaparcarLogger.w(TAG, "sin token de push — el grupo no podrá avisar a este móvil")
            return
        }
        carIds.forEach { carId ->
            runCatching { remote.setMemberTokens(carId, uid, listOf(token)) }
                .onFailure { PaparcarLogger.w(TAG, "no se pudo publicar el token en $carId", it) }
        }
        PaparcarLogger.d(TAG) { "✓ token publicado en ${carIds.size} coche(s) del grupo" }
    }

    private companion object {
        const val TAG = "SharedCarTokens"
    }
}
