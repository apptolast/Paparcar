package com.rndeveloper.paparcar.domain.detection.sentry

import com.rndeveloper.paparcar.domain.detection.ServicePresence


/**
 * [DET-RESIDENT-FGS-001] Pure decision for what the foreground detection service does when a
 * tracking job ends and the service falls idle: DIE (today's wake-and-kill) or stay resident in
 * [ServicePresence.Sentry] (GPS off, waiting to catch the next departure on a live process).
 *
 * Kept as a pure function in commonMain so the (Android-only, race-fix-laden) service reads its fate
 * from one tested place instead of inlining the branch. SENTRY residency requires three things:
 *  - `autoDetectEnabled` — the user's Settings auto-detect toggle. F3 decision (2026-08-06, user):
 *    the sentry has NO switch of its own — residency is HOW detection stays ready, so the existing
 *    detection toggle governs it. During F1/F2 this input was an internal experiment flag.
 *  - `parkedCarNeedsWatcher` — hay algo aparcado Y ese algo no se despierta solo.
 *
 * ⛔ **La segunda entrada era `strategy == COORDINATOR` y estaba muerta desde el día siguiente.**
 * [DET-A-PARKED-BT-CAR-NEEDS-NO-RESIDENT-WATCHER-001] La idea era la correcta —bajo BLUETOOTH el
 * broadcast ACL despierta el proceso solo (receptor de manifiesto, exento de la restricción de
 * FGS-desde-background en Android 12+), así que la residencia sólo costaría batería y una
 * notificación permanente— pero la pregunta estaba mal hecha, y
 * [DET-BT-CONNECTED-NOT-PAIRED-001] la remató: desde entonces `BLUETOOTH` significa «CONECTADO
 * ahora mismo al coche», y este epílogo se consulta **justo después de aparcar**, que es
 * exactamente cuando el coche acaba de DESCONECTARSE. El gate era inalcanzable en el único momento
 * en que se le preguntaba, así que el usuario de un solo coche BT se quedaba con el centinela y su
 * notificación permanente. Reportado el 19-09 (issue #57), y con razón: «creía que habíamos
 * acordado despertar el servicio por BT receiver».
 *
 * La pregunta honesta no es qué estrategia conduce ahora, sino **si el coche APARCADO tiene un
 * despertador determinista propio**: lo tiene si está emparejado por Bluetooth —emparejado, no
 * conectado—, porque su próximo ACL CONNECT es su propia salida
 * [DET-A-BT-CONNECT-IS-A-DEPARTURE-001]. Eso lo contesta [parkedCarNeedsWatcher].
 *
 * ⛔ Y se decide **por coche, no por flota**: con un BT aparcado y otro sin BT también aparcado, el
 * segundo necesita el centinela y la residencia se queda. Es literalmente lo que pidió el user:
 * *«en caso de que sí tuviera otro activo, el vehículo activo necesita FGS siempre que tengamos el
 * vehículo activo con sesión activa aparcado»*.
 *
 * Deliberately NOT tier-gated: the sentry costs a silent minimum-importance notification and near-zero
 * battery on every tier, and the ASSISTED tiers (no BT receiver to revive a dead process) are exactly
 * where residency saves departures. Who wants no permanent notification turns detection off — one
 * concept, one switch.
 */
enum class PostDetectionLifecycle {
    /** Tear the service down — `stopSelfResult` + onDestroy, exactly as today. */
    Stop,

    /** Keep the service alive and foreground with the GPS off. [ServicePresence.Sentry] */
    EnterSentry,
}

fun resolvePostDetectionLifecycle(
    autoDetectEnabled: Boolean,
    parkedCarNeedsWatcher: Boolean,
): PostDetectionLifecycle =
    if (autoDetectEnabled && parkedCarNeedsWatcher) {
        PostDetectionLifecycle.EnterSentry
    } else {
        PostDetectionLifecycle.Stop
    }

/**
 * ¿Queda algún coche APARCADO que no se despierte solo?
 * [DET-A-PARKED-BT-CAR-NEEDS-NO-RESIDENT-WATCHER-001]
 *
 * Un coche emparejado por Bluetooth trae su propio despertador: su siguiente ACL CONNECT es su
 * salida, y lo entrega un receptor de manifiesto al proceso muerto
 * [DET-A-BT-CONNECT-IS-A-DEPARTURE-001]. Uno sin emparejar sólo tiene la señal de movimiento
 * significativo, que **no se puede entregar a un proceso muerto**: ése es el que paga el centinela.
 *
 * ⛔ **Emparejado, no conectado.** Un coche aparcado está, por definición, desconectado: preguntar
 * por la conexión aquí es preguntar por algo que nunca es cierto en este momento. Ése fue el fallo.
 *
 * ⛔ **Una sesión sin coche conocido CUENTA como que necesita vigilante.** Ante la duda se vigila:
 * quedarse residente cuesta una notificación silenciosa; no quedarse cuesta una salida perdida, que
 * es la clase de fallo crónico por la que existe el centinela.
 *
 * Un coche queda fuera por **dos** motivos, y el segundo es una red de seguridad heredada:
 *  1. **Se despierta solo** — emparejado por BT.
 *  2. **Nunca aparca en una plaza** — patinete/bici. Ahí no debería existir sesión ninguna, así que
 *     es un estado imposible; pero estaba explícitamente cubierto por el gate de estrategia que esto
 *     sustituye (`ParkingStrategy.NONE`) y su test, y una red que ya existía no se retira de paso en
 *     el ticket de otra cosa. [VEH-A-NEW-VEHICLE-TYPE-MUST-NOT-BE-A-CAR-BY-OMISSION-001]
 *
 * @param parkedVehicleIds el `vehicleId` de cada sesión activa (null incluido: una sesión sin coche)
 * @param vehiclesThatNeedNoWatcher los coches de la flota que no lo necesitan, por 1 o por 2
 */
fun parkedCarNeedsWatcher(
    parkedVehicleIds: List<String?>,
    vehiclesThatNeedNoWatcher: Set<String>,
): Boolean = parkedVehicleIds.any { it == null || it !in vehiclesThatNeedNoWatcher }

/**
 * [DET-RESIDENT-FGS-001 · F2] Pure verdict for a sentry-residency stamp found on disk. The service
 * stamps "I am resident" on enterSentry and clears it on every DELIBERATE exit (SENTRY→ACTIVE wake,
 * idle teardown), so a stamp that outlives the process without one of those exits is evidence the OS
 * killed the resident watcher — unless a reboot explains it innocently (consistent with the
 * BackgroundKillSuspected heuristic, which also skips reboots).
 *
 * Shared by BOTH detection lanes so they cannot diverge: the safety-net worker's periodic tick
 * (which can also measure the heartbeat gap = the dark window) and the service's own arm path
 * (a trigger reviving a dead process with the stamp still set IS the kill being witnessed live).
 */
sealed interface SentryKillVerdict {
    /** No residency expected, or the sentry is alive and well — nothing to do. */
    data object None : SentryKillVerdict

    /** The residency ended innocently (reboot, or a live job missed the handoff) — clear the
     *  stamp silently, log nothing. */
    data object ClearStamp : SentryKillVerdict

    /** The resident watcher died without a deliberate exit — log `sentry killed` + clear the stamp. */
    data object Killed : SentryKillVerdict
}

fun resolveSentryKillVerdict(
    residencyExpected: Boolean,
    presence: ServicePresence,
    rebootedSince: Boolean,
): SentryKillVerdict = when {
    !residencyExpected -> SentryKillVerdict.None
    presence == ServicePresence.Sentry -> SentryKillVerdict.None
    rebootedSince -> SentryKillVerdict.ClearStamp
    presence == ServicePresence.Active -> SentryKillVerdict.ClearStamp
    else -> SentryKillVerdict.Killed
}
