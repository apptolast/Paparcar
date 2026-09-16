package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.runtime.Immutable
import com.rndeveloper.paparcar.domain.detection.DetectionPause
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.VehicleWithStats

@Immutable
data class VehiclesState(
    val vehicles: List<VehicleWithStats> = emptyList(),
    val isLoading: Boolean = true,
    /** Id of the vehicle whose isActive flag is being switched. Null when idle. */
    val settingActiveVehicleId: String? = null,
    val selectedVehicleIndex: Int = 0,
    /** A car asked for from outside the screen whose page could not be resolved yet — the list had
     *  not arrived. Resolved (and cleared) on the first emission that contains it.
     *  [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] */
    val pendingFocusVehicleId: String? = null,
    val bluetoothConnectedVehicleId: String? = null,
    val historyCache: Map<String, HistoryState> = emptyMap(),
    /** Live Plus entitlement — what SetStatsPeriod re-resolves the timeline with. [MONEY-001] */
    val isPlus: Boolean = false,
    /**
     * Why the next trip will NOT be measured, or null when it will — what the notice above the
     * figures says out loud. [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001]
     *
     * ⛔ It replaced a plain `isAutoDetectionOn`, which was the stored INTENT and therefore said
     * "detection is on" for an account whose background location had lapsed — an account measuring
     * exactly nothing, and the one that most needed telling.
     * [DET-TOGGLE-A-SWITCH-THAT-CANNOT-RUN-IS-NOT-ON-001]
     */
    val detectionPause: DetectionPause? = null,
    // ── Coche compartido [VEH-SHARED-001] ────────────────────────────────────
    // ⛔ No hay pantalla de grupo: un coche compartido es un coche más del pager, y lo que el
    // grupo añade vive en una sección DENTRO de su página (decisión del user, 15-09).
    /** El espejo de cada coche compartido de este garaje, por id. Vacío = ninguno lo está. */
    val sharedCars: Map<String, SharedCar> = emptyMap(),
    /** Los miembros del coche que se está mirando. Solo de ese: el pager enseña uno. */
    val sharedMembers: List<SharedCarMember> = emptyList(),
    /** Historial y cifras del coche a la vista: del grupo, o solo los tuyos. */
    val sharedHistoryMineOnly: Boolean = false,
    /** Quién escribió cada sesión compartida, para poder firmarla en la fila. */
    val sharedAuthorByUid: Map<String, String> = emptyMap(),
    /** Sesiones cuyo autor NO comparte la ruta — la fila lo dice en vez de dejar un hueco. */
    val sharedWithheldRouteIds: Set<String> = emptySet(),
    /**
     * QUE accion de grupo esta en vuelo, o null si ninguna.
     * [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
     *
     * ⛔ Era un `sharedWorking: Boolean`, y el booleano es el bug: decia «algo esta corriendo», asi
     * que el hueco del codigo se ponia a brillar porque alguien habia tocado el interruptor de
     * rutas. Es el mismo fallo que `PapDialogAction` arreglo en los dialogos — un estado de carga
     * tiene que nombrar su accion o acaba prestandosela a otra.
     */
    val sharedBusy: SharedCarBusy? = null,
    /** La hoja de miembros, abierta sobre este coche. */
    val membersSheetCarId: String? = null,
    /** El diálogo de unirse, o null si no está abierto. */
    val joinDialog: JoinSharedCarDialog? = null,
    /**
     * El «+» preguntando qué clase de coche se añade: uno mío, o uno que ya me han compartido.
     * [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
     *
     * Existe porque la puerta para teclear un código vivía SOLO en el garaje vacío, así que quien ya
     * tenía un coche no podía unirse a ninguno — que es el caso normal: ya tengo el mío y alguien me
     * invita al suyo.
     */
    val showAddChoice: Boolean = false,
    /** Salir del grupo o disolverlo, esperando confirmacion. Disolver deja sin coche a cuatro
     *  personas y hasta ahora iba a un solo toque.
     *  [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001] */
    val confirmGroupExit: GroupExitPrompt? = null,
    /** El diálogo que explica qué verá el grupo antes de abrirlo, sobre este coche. */
    val confirmShareCarId: String? = null,
    /** La respuesta a «¿comparto también mis rutas?» que se está componiendo en ese diálogo.
     *  [COPY-A-CARD-THAT-PROMISES-ROUTES-STAY-HOME-001] */
    val confirmShareRoutes: Boolean = false,
    /** El coche que ha dejado de estar compartido sin que el usuario lo pidiera, esperando su
     *  decisión. [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001] */
    val dissolvedCar: DissolvedSharedCarPrompt? = null,
    val currentUid: String? = null,
) {
    /** Hay ALGUNA accion de grupo en vuelo — lo que congela botones. Cual es, lo dice [sharedBusy]. */
    val sharedWorking: Boolean
        get() = sharedBusy != null

    val currentVehicleId: String?
        get() = vehicles.getOrNull(selectedVehicleIndex)?.vehicle?.id

    /**
     * No cache entry means this vehicle's history has NOT arrived yet — never that it has none.
     * [UI-HISTORY-A-LOADING-LIST-MUST-NOT-CLAIM-TO-BE-EMPTY-001]
     */
    val historyState: HistoryState
        get() = currentVehicleId?.let { historyCache[it] }
            ?: HistoryState(timeline = HistoryTimeline.Unresolved)

    /** El coche a la vista. Lo pide la hoja de miembros, que ahora gestiona cosas SUYAS —el
     *  interruptor de rutas, el aviso de emparejamiento— y no solo del grupo.
     *  [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001] */
    val currentVehicle: Vehicle?
        get() = vehicles.getOrNull(selectedVehicleIndex)?.vehicle

    /** El espejo del coche a la vista, o null si ese coche no está compartido. */
    val currentSharedCar: SharedCar?
        get() = currentVehicleId?.let { sharedCars[it] }

    /** Este usuario es el propietario del coche a la vista. */
    val isOwnerOfCurrent: Boolean
        get() = currentSharedCar?.ownerUid == currentUid && currentUid != null

    val activeVehicle: Vehicle?
        get() = vehicles
            .map { it.vehicle }
            .let { list ->
                list.firstOrNull { it.id == bluetoothConnectedVehicleId }
                    ?: list.firstOrNull { it.isActive }
            }
}

/**
 * «Este coche ha dejado de compartirse»: la pregunta que queda cuando el dueño cierra el grupo.
 * [PRIV-A-DELETED-CAR-MUST-NOT-ORPHAN-ITS-GROUP-001]
 *
 * No es un aviso, es una decisión: la fila pasa de PRESTADA —que no gastaba plaza de coche— a
 * propia, y con el plan gratuito eso ocupa la única que hay. Por eso viaja [takesTheFreeSlot]: el
 * mismo diálogo dice una cosa u otra según lo que le cueste al que lo lee.
 *
 * [endedParkings] es lo que se perdería al borrarlo, y se enseña por la misma razón que en el
 * borrado normal de un coche [VEH-A-DELETED-CAR-DOES-NOT-ERASE-ITS-HISTORY-001]: esos viajes son
 * SUYOS aunque el coche fuera de otro.
 */
data class DissolvedSharedCarPrompt(
    val vehicleId: String,
    val carName: String,
    val endedParkings: Int,
    val takesTheFreeSlot: Boolean,
)

/**
 * Las acciones de grupo que tardan, por su nombre. [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
 *
 * Existe para que una espera se dibuje SOLO donde ocurre: el shimmer del codigo es de [Invite] y de
 * nadie mas.
 */
enum class SharedCarBusy { Invite, Routes, Membership, Sharing }
