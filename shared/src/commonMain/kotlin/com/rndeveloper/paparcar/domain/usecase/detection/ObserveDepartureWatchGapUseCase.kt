package com.rndeveloper.paparcar.domain.usecase.detection

import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.detection.sentry.PostDetectionLifecycle
import com.rndeveloper.paparcar.domain.detection.ServicePresence
import com.rndeveloper.paparcar.domain.detection.sentry.parkedCarNeedsWatcher
import com.rndeveloper.paparcar.domain.detection.sentry.vehiclesThatNeedNoWatcher
import com.rndeveloper.paparcar.domain.detection.sentry.watchFacts
import com.rndeveloper.paparcar.domain.detection.sentry.resolvePostDetectionLifecycle
import com.rndeveloper.paparcar.domain.permissions.PermissionManager
import com.rndeveloper.paparcar.domain.preferences.AppPreferences
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Emits `true` while there is a WATCH GAP: the departure watcher SHOULD be live (a Coordinator car is
 * parked and auto-detection is on) but the service is [ServicePresence.Dead]. [DET-WATCH-REACTIVATE-001]
 *
 * The "should be live" half is not re-derived here — it is [resolvePostDetectionLifecycle], the same
 * pure rule the service applies when deciding whether to stay resident after a trip. One rule, two
 * readers: the service closing a session and the app noticing the watcher is missing.
 *
 * Being a STREAM is the point. The predecessor self-heal read `observeActiveSessions().first()` once
 * at process start, so a clean install (Room empty until the Firestore sync lands) saw "nothing
 * parked", skipped the start, and left the watch dead until the next process launch — the exact
 * 2026-08-14 report. Here the gap simply closes when the session arrives, whenever that is.
 */
class ObserveDepartureWatchGapUseCase(
    private val userParkingRepository: UserParkingRepository,
    private val vehicleRepository: VehicleRepository,
    private val appPreferences: AppPreferences,
    private val detectionRuntime: DetectionRuntimeState,
    private val permissionManager: PermissionManager,
) {
    /**
     * **¿La residencia sigue haciendo falta?** — la regla de [resolvePostDetectionLifecycle] servida
     * como STREAM, sin mirar si el servicio está vivo.
     * [DET-A-PARKED-BT-CAR-NEEDS-NO-RESIDENT-WATCHER-001]
     *
     * ⛔ Existe porque la residencia tenía **una puerta de entrada y ninguna de salida honesta**. El
     * epílogo del servicio decide quedarse residente en el instante en que termina un viaje y no se
     * vuelve a preguntar nunca; el vigilante que sí podía derribarlo
     * (`watchSentryPreconditions`) se había escrito **su propia** versión de la regla —«detección
     * encendida y algo aparcado»—, que es la de antes de que existiera `parkedCarNeedsWatcher`.
     *
     * Resultado medido por el user el 20-09: emparejas por Bluetooth el coche que YA está aparcado,
     * ningún viaje termina, y el único que podía enterarse estaba mirando una regla vieja. La
     * notificación permanente se quedaba puesta — el bug de la issue #57 entrando por la otra
     * puerta.
     *
     * Ahora hay una sola definición y dos lectores, que es lo que el KDoc de esta clase ya prometía:
     * [invoke] pregunta «¿falta el vigilante?» y ésta «¿sobra?».
     *
     * ⛔ La flota entra en el `combine` **porque emparejar un BT es un cambio de la flota**: sin
     * ella el flujo ni siquiera re-emitiría, que es la mitad de por qué el bug sobrevivió.
     */
    fun residencyStillWanted(): Flow<Boolean> = combine(
        userParkingRepository.observeActiveSessions(),
        vehicleRepository.observeVehicles(),
        appPreferences.observeAutoDetectParking(),
    ) { sessions, vehicles, autoDetectEnabled ->
        resolvePostDetectionLifecycle(
            autoDetectEnabled = autoDetectEnabled,
            // Por coche aparcado: el emparejado por BT se despierta solo (su ACL CONNECT es su
            // salida, receptor de manifiesto), así que su ausencia de centinela NO es un hueco.
            // [DET-A-BT-CONNECT-IS-A-DEPARTURE-001] Esa frase era media verdad cuando se escribió:
            // el receptor cubría el APARCAR (disconnect) y nada cubría la salida. El CONNECT arma ya
            // su propia vigilancia de alejamiento, así que ahora se sostiene.
            parkedCarNeedsWatcher = parkedCarNeedsWatcher(
                parkedVehicleIds = sessions.map { it.vehicleId },
                vehiclesThatNeedNoWatcher = vehiclesThatNeedNoWatcher(
                    vehicles.map { it.watchFacts() },
                ),
            ),
        ) == PostDetectionLifecycle.EnterSentry
    }.distinctUntilChanged()

    operator fun invoke(): Flow<Boolean> = combine(
        residencyStillWanted(),
        detectionRuntime.presence,
    ) { wanted, presence ->
        // ⛔ **Un hueco es la AUSENCIA de vigilancia demostrada**, así que [ServicePresence.Unknown]
        // cuenta igual que `Dead` — y eso **preserva el comportamiento** que tenía antes de que
        // `Unknown` existiera, que es lo que hay que preservar aquí.
        //
        // Es la mitad OPUESTA a la del badge de Home, y a propósito: esta pregunta no produce una
        // frase, produce una ACCIÓN — arrancar el vigilante. Arrancar uno que ya corre no cuesta
        // nada; no arrancar uno muerto cuesta una plaza. Fallo asimétrico.
        // [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
        !presence.isWatchingLive && wanted
    }.distinctUntilChanged()

    /**
     * The gap, but only while a foreground moment can actually CLOSE it: the FGS promote needs the
     * runtime location permission, so an attempt without it is a guaranteed OS refusal
     * (`SecurityException` from `validateForegroundServiceType`, measured 2026-09-09 on a clean
     * install). [DET-A-GRANTED-PERMISSION-MUST-REOPEN-THE-WATCH-001]
     *
     * This is the stream the FOREGROUND lane collects, and the permission is part of it for two
     * reasons:
     *  - A clean install grants permissions WITHOUT ever leaving the app (the map asks for core,
     *    the detection checklist asks for producer), so the grant itself must be an emission — the
     *    raw gap never re-emits on it, and the one attempt the lane used to fire was spent on a
     *    refusal before the user could possibly have granted anything.
     *  - Filtering here also means the doomed pre-grant dispatch never happens, so the resumer's
     *    automatic cooldown is never stamped by an attempt that could not have worked.
     *
     * The RAW gap ([invoke]/[current]) deliberately stays permission-blind: a gap without
     * permissions is still a real gap (Readiness reports it as Blocked), and the `home-cta` force
     * path must keep dispatching so the service can answer with its honest refusal + the
     * permission-revoked notification.
     */
    fun closeableFromForeground(): Flow<Boolean> = combine(
        invoke(),
        permissionManager.permissionState.map { it.hasCorePermissions }.distinctUntilChanged(),
    ) { gap, canPromote -> gap && canPromote }.distinctUntilChanged()

    /**
     * The gap RIGHT NOW, for callers that act once instead of subscribing (the resumer's own guard).
     *
     * Suspends until every input has produced a real value — that is what [combine] does — so it can
     * never answer "nothing is parked" just because Room had not been read yet. A caller that reads
     * the repositories on its own instead would get exactly that stale answer, and would then
     * disagree with the stream that woke it up. [DET-WATCH-RESUME-RACE-001]
     */
    suspend fun current(): Boolean = invoke().first()
}
