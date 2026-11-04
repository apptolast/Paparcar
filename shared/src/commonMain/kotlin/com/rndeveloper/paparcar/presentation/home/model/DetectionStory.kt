package com.rndeveloper.paparcar.presentation.home.model

import com.rndeveloper.paparcar.domain.detection.PendingPromptWindow
import com.rndeveloper.paparcar.domain.model.displayName
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsOwnership
import com.rndeveloper.paparcar.presentation.home.VehicleCard
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.watch

/**
 * The SINGLE detection story Home tells in the expanded sheet — one voice for "what is the app
 * doing for me right now", instead of action pills that go mute in every happy state.
 * [UX-DETECTION-STORY-001, cures C6/H5 of UX-PARK-FLOW-001]
 *
 * Exactly one story at a time, resolved by [resolveDetectionStory] with a fixed precedence
 * (urgent → quiet): [BlockedCore] → [AwaitingAnswer] → [PendingAsk] → [NoVehicle] → [Inactive] →
 * [AwaitingFirstPark] → [Watching] → [Hidden]. The action stories keep the loud accent-bar row;
 * [Watching] renders a discreet one-line status so the happy path has a voice.
 *
 * A LIVE TRIP has no story here any more: the vehicle card already tells it whole — radar halo,
 * identity border, route glyph, "En ruta · place" — and carries the stop CTA, and a trip can only
 * be narrated when that card is on screen (`drivingStory` needed a card to name). Two surfaces
 * telling the same news is the duplication this projection exists to prevent, the same reason the
 * calm watch line stands down. [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]
 * [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
 *
 * [DET-ASK-STATE-001] The two stories where the app is WAITING ON THE USER — [AwaitingAnswer] and
 * [PendingAsk] — sit at the top together, and both are resolved HERE. Until this ticket the nudge
 * was arbitrated by an `if` inside the composable, i.e. outside the very precedence this doc
 * declares: the chain said one thing and the surface did another, and only one of the two was
 * testable. One question, one place.
 *
 * The collapsed peek keeps its own phase eyebrow ("EN RUTA"/"APARCANDO…"/"¿HAS APARCADO?") — that
 * is the voice of the CLOSED sheet; this story is the voice of the OPEN sheet. Same translated
 * words, no drift.
 */
sealed interface DetectionStory {
    /** CORE permission missing — the app barely works. Loud, error-toned action row. */
    data object BlockedCore : DetectionStory

    /**
     * [DET-ASK-STATE-001] A "did you park?" question is posted and still answerable. The row asks it
     * in-app with the same two answers as the tray notification, so a user who opens Paparcar during
     * the window can resolve it without ever going to the shade — the case that silently timed out
     * on 2026-07-25.
     *
     * The whole [PendingPromptWindow] rides along rather than a copy of its fields: the vehicle
     * name is carried verbatim from the notification that posted it (null = the generic wording),
     * and since [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] so are the instant it was asked and
     * the place it is about. Splitting them into three parameters here would be three chances for
     * the row to describe a different question than the tray does.
     */
    data class AwaitingAnswer(val window: PendingPromptWindow) : DetectionStory

    /**
     * [DET-NUDGE-PERSIST-001] An unanswered "where did you leave your car?" nudge. A lost parking
     * record outranks every upsell and every happy line; only a CORE block (where the app barely
     * works) and a LIVE question with a deadline still beat it.
     */
    data object PendingAsk : DetectionStory

    /** No vehicle registered — nothing to detect. Action row. */
    data object NoVehicle : DetectionStory

    /** Detection off (Settings flag or producer permissions) — one-tap activate. Action row. */
    data object Inactive : DetectionStory

    /**
     * Coordinator cold-start — mark your parking or declare "I'm driving". Action row.
     *
     * [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] The ONLY story a surface above can take over
     * from: while the guided checklist is asking for the first parking it says the same thing with
     * more context, and two rows asking for one action is precisely the drift this projection
     * exists to prevent. See `firstStepsOwnsColdStart`.
     */
    data object AwaitingFirstPark : DetectionStory

    /**
     * Discreet happy line — detection is armed and covering the ACTIVE vehicle. Only the active
     * vehicle earns this story: it is the one the Coordinator works for (strategy gate
     * [DET-STRATEGY-GATE-001]); other cars never claim to be "watched". [isParked] = a fence is
     * watching its session; [watch] = the vehicle's watch method (Bluetooth-armed vs assisted).
     *
     * [watchBadge] makes the line HONEST: it only reads "Vigilando tu sitio" when the watch is
     * genuinely live ([ParkedWatchBadge.WATCHING]); a fragile setup warns, and a killed foreground
     * service degrades to a "reactivate" ask instead of a green lie. [DET-WATCH-HONEST-001]
     */
    data class Watching(
        val vehicleName: String,
        val isParked: Boolean,
        val watch: VehicleWatch,
        val watchBadge: ParkedWatchBadge = ParkedWatchBadge.WATCHING,
    ) : DetectionStory

    /** Nothing to say (non-parking vehicle, or no resolvable vehicle to talk about). */
    data object Hidden : DetectionStory
}

/**
 * Pure projection (state × vehicles) → the one [DetectionStory] to render.
 * Lives here — not in the composable — so the precedence is unit-testable. [UX-DETECTION-STORY-001]
 */
fun resolveDetectionStory(
    uiState: DetectionUiState,
    vehicleCards: List<VehicleCard>,
    // Honest watch health for the parked case — WATCHING when genuinely live, else fragile/interrupted.
    // Null (default) keeps the healthy "Vigilando" line, so callers that don't wire it don't regress.
    // [DET-WATCH-HONEST-001]
    parkedWatchBadge: ParkedWatchBadge? = null,
    /** [DET-ASK-STATE-001] The open "did you park?" question, or null when there is none to answer.
     *  Already filtered for expiry by `isPromptWindowOpen` upstream — this function decides
     *  precedence, not whether a clock has run out. */
    promptWindow: PendingPromptWindow? = null,
    /** [DET-NUDGE-PERSIST-001] An unanswered "where did you leave your car?" nudge is pending. */
    showParkNudge: Boolean = false,
    /**
     * La tira de coches está en pantalla y por tanto ya dice, en la card del coche, lo mismo que
     * diría la fila CALMADA de vigilancia. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
     *
     * Es el mismo mecanismo que [firstStepsOwns] y por el mismo motivo: dos superficies que dicen la
     * misma frase no compiten por el ojo, se duplican — «Watching your Alfa Romeo 156 · Parked»
     * justo encima de «Alfa Romeo 156 · Parked at Calle X» son dos veces la misma noticia, y el
     * usuario lo vio como un choque VISUAL cuando el problema era editorial.
     *
     * ⛔ Solo se aparta la fila calmada. Un aviso frágil, una vigilancia muerta o un coche sin
     * marcar dicen algo que la card NO dice, y esos se quedan enteros —
     * incluida su barra de acento. Apartar por «parecerse» en vez de por «decir lo mismo» es como
     * se pierde un aviso.
     */
    carCardSaysTheWatch: Boolean = false,
    /**
     * [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001] Which of this surface's asks the guided
     * checklist is making right now — the matching row STANDS DOWN so the surface keeps its one
     * voice. The checklist says the same thing with the context of the steps around it.
     *
     * Two rows can be taken over, each by the step that duplicates it
     * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]:
     * - [FirstStepsOwnership.COLD_START] → [AwaitingFirstPark] ("mark your parking")
     * - [FirstStepsOwnership.DETECTION_OFF] → [Inactive] ("turn on detection"), which the second step
     *   wears only when detection is actually stopped — precisely when its own copy would otherwise
     *   promise a watch that is not running.
     *
     * The suppression lives HERE, in the projection, and not as an `if` around the composable:
     * arbitrating precedence inside the surface is the mistake [DET-ASK-STATE-001] came back to
     * correct, and it would be the same mistake for the same reason — the chain would declare one
     * order and the screen would render another, with only one of the two testable.
     *
     * It suppresses the ONE matching story and nothing else. A CORE block, a live question, a pending
     * nudge and every watch alert still outrank the checklist: those are things the app cannot do its
     * job without, and a tutorial is never a reason to go quiet about them. A FRAGILE or interrupted
     * watch is likewise never taken over — the row names the specific cause (battery, a killed
     * service) and the step could only say something vaguer. [DET-WATCH-HONEST-001]
     */
    firstStepsOwns: FirstStepsOwnership = FirstStepsOwnership.NOTHING,
    /**
     * The first-entry location ask is imminent or on screen with its answer unread — so the
     * [BlockedCore] row STANDS DOWN. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
     *
     * Same mechanism as [firstStepsOwns] and [carCardSaysTheWatch], and for the same reason: a row
     * steps aside when another surface is already saying its sentence. Here that surface is the
     * system dialog itself, which asks for the very permission this row would complain about — and
     * it is louder, modal, and carries the actual remedy. The guided checklist SNAPS the sheet open
     * on the first frame it exists (`HomeSheetPositioning`), so this row really is on screen behind
     * that dialog; it is not a theoretical case.
     *
     * It suppresses this ONE row and nothing else. Once the answer is read the row comes back on its
     * own, and a refusal additionally opens the sheet onto it
     * [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001].
     */
    locationAskPending: Boolean = false,
): DetectionStory {
    val activeCard = vehicleCards.firstOrNull { it.vehicle.isActive }

    fun watchingStory(isParked: Boolean, badge: ParkedWatchBadge): DetectionStory {
        // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] The fortify step says what the
        // FRAGILE line says, with the context of the checklist around it — so while the step is the
        // current ask, this line stands down and the surface keeps one voice.
        //
        // Only the fragile one. An INTERRUPTED watch is a different, louder fact (the OS killed the
        // service) whose row names its own cause and offers its own repair; a tutorial step never
        // takes THAT over. [DET-WATCH-HONEST-001]
        if (badge == ParkedWatchBadge.WATCHING_FRAGILE &&
            firstStepsOwns == FirstStepsOwnership.WATCH_FRAGILE
        ) {
            return DetectionStory.Hidden
        }
        // ⛔ **Sin noticias del servicio no hay historia que contar.** Va ANTES de todo lo demás:
        // no es que la fila ceda ante otra superficie, es que todavía no hay veredicto, y un beat
        // después lo habrá. La card del coche sigue diciendo la verdad —aparcado, y dónde— sin
        // afirmar nada sobre el vigilante. [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
        if (badge == ParkedWatchBadge.WATCH_UNKNOWN) return DetectionStory.Hidden
        // ⛔ La fila calmada se calla si la card del coche ya la cuenta: coche aparcado y vigilancia
        // SANA es exactamente lo que esa card enseña, con su nombre y su calle.
        // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
        if (carCardSaysTheWatch && isParked && badge == ParkedWatchBadge.WATCHING) {
            return DetectionStory.Hidden
        }
        // Watching names the ACTIVE vehicle ONLY — never a ranked or first-of-list fallback.
        val card = activeCard ?: return DetectionStory.Hidden
        val name = card.vehicle.displayName().takeIf { it.isNotBlank() }
            ?: return DetectionStory.Hidden
        // The method is read off the vehicle itself so the row's identity colour can never disagree
        // with the garage. [UI-COLOR-DOCTRINE-001]
        return DetectionStory.Watching(
            name,
            isParked = isParked,
            watch = card.vehicle.monitoringStatus().watch(),
            watchBadge = badge,
        )
    }

    // A CORE block comes first even over the questions: with location off, neither answer can be
    // acted on and the app barely works. Below it, the two things the app is WAITING ON THE USER for
    // outrank everything the app wants to TELL the user — and a live question with a deadline
    // outranks a stale one. [DET-ASK-STATE-001]
    // ⛔ El arranque en frio tambien se calla si la card del coche ya lo pregunta: la card sin
    // marcar dice «¿Donde esta tu coche?» y trae su propio boton, para CADA coche y no solo para el
    // que esta funcion elige. Se aparta por decir lo MISMO, igual que la fila calmada de vigilancia.
    // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
    // ⛔ …y aun por delante de todo eso, la pregunta que el usuario tiene ABIERTA en pantalla: con el
    // diálogo del sistema pidiendo la ubicación, esta fila estaría acusando de una falta que nadie
    // ha cometido todavía. No es que se esconda, es que aún no le toca.
    // [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
    if (uiState == DetectionUiState.BlockedCore) {
        return if (locationAskPending) DetectionStory.Hidden else DetectionStory.BlockedCore
    }
    if (promptWindow != null) return DetectionStory.AwaitingAnswer(promptWindow)
    if (showParkNudge) return DetectionStory.PendingAsk

    return when (uiState) {
        DetectionUiState.BlockedCore -> DetectionStory.BlockedCore
        // ⛔⛔ «No tienes coche» solo se dice si NO HAY COCHE, y esta función tiene la lista delante.
        // Visto en el emulador (23-09): media galería enseñaba «Añade tu coche» con dos coches
        // debajo, porque la disponibilidad por defecto es `Disabled(NO_VEHICLE)` y basta con no
        // pasarla para que la proyección contradiga a la tira que ella misma recibe. En producción
        // las dos salen de la misma lista (`ObserveDetectionReadinessUseCase` devuelve NO_VEHICLE
        // sólo `if (vehicles.isEmpty())`), así que llegar aquí con cards significa que alguien las
        // ha desincronizado — un estado a medio armar, o un borrado en vuelo. Con la lista a la
        // vista, decirlo igual sería mentir a sabiendas; callar es lo único honesto.
        DetectionUiState.NoVehicle ->
            if (vehicleCards.isEmpty()) DetectionStory.NoVehicle else DetectionStory.Hidden
        DetectionUiState.Inactive ->
            if (firstStepsOwns == FirstStepsOwnership.DETECTION_OFF) DetectionStory.Hidden
            else DetectionStory.Inactive
        DetectionUiState.AwaitingFirstPark ->
            // El paso del checklist SÍ lo dice entero, con el contexto de los pasos alrededor, y con
            // él delante esta fila sobra.
            //
            // ⛔⛔ Pero `carCardSaysTheWatch` ya NO la suprime, y quitarlo arregla un agujero que se
            // vio en el emulador: con un coche registrado esta historia se escondía «porque la card
            // ya lo dice», y la card sólo decía la FRASE —«Marca tu aparcamiento y vigilo la
            // salida»— sin los dos botones. O sea que «Marcar mi aparcamiento» y «Voy conduciendo»
            // no existían en ninguna superficie: ni aquí, ni ahí. Ahora la card recibe la historia
            // ENTERA y la pinta con sus CTAs, que es lo que
            // [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001] vino a hacer.
            if (firstStepsOwns == FirstStepsOwnership.COLD_START) {
                DetectionStory.Hidden
            } else {
                DetectionStory.AwaitingFirstPark
            }
        // ⛔ Un viaje vivo NO tiene fila aquí. La card del coche lo cuenta entero (halo, borde de
        // identidad, glifo de ruta, «En ruta · lugar») y lleva el «Parar» — y la historia solo
        // podía pintarse cuando esa card estaba en pantalla, porque necesitaba una card a la que
        // ponerle nombre. Dos superficies con la misma noticia es el duplicado que esta proyección
        // existe para impedir. Las preguntas (AwaitingAnswer / PendingAsk) siguen ganando arriba:
        // apartarse de narrar no es apartarse de preguntar.
        // [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]
        DetectionUiState.Monitoring -> DetectionStory.Hidden
        // Honest: the parked line reflects whether the watch is really live. [DET-WATCH-HONEST-001]
        DetectionUiState.Parked ->
            watchingStory(isParked = true, badge = parkedWatchBadge ?: ParkedWatchBadge.WATCHING)
        // Bluetooth-armed is always covered by the receiver — honestly healthy.
        DetectionUiState.ArmedBluetooth ->
            watchingStory(isParked = false, badge = ParkedWatchBadge.WATCHING)
        DetectionUiState.Silent -> DetectionStory.Hidden
    }
}

/**
 * ¿Es esta historia una **pregunta sobre un coche concreto**, y por tanto la hospeda la card de ese
 * coche? [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
 *
 * El corte NO es «de quién habla» sino **qué se le pide al usuario**, y lo fijó el user mirando el
 * dibujo (23-09):
 *
 *  · **Card** — lo que ESE coche puede contestar AHORA: ¿has aparcado?, ¿dónde lo dejaste?, ¿dónde
 *    está tu coche? Son preguntas de un coche, con respuesta en un toque, y la card es justo la
 *    superficie que lo nombra.
 *  · **Badge** — la salud del SISTEMA: el permiso de ubicación, la detección apagada, la vigilancia
 *    frágil o parada por el móvil. Son avisos que **repercuten a todos los coches** (un permiso, un
 *    ajuste y UN servicio en primer plano para los dos carriles), así que colgarlos de la card de un
 *    coche nombra al culpable equivocado — y con dos coches obligaría a elegir a cuál acusar.
 *
 * Y hay una razón de trato, no sólo de taxonomía: metido en la card, un aviso de sistema **sustituye
 * a la información del coche por un botón rojo a ancho completo**, así que empuja a pulsar algo que
 * el usuario quizá no quiere ahora. Fuera, informa sin secuestrar la card.
 *
 * ⛔ Contesta QUÉ es la historia, no QUIÉN la pinta. Quién la pinta lo decide la hoja, que es la
 * única que sabe si hay UNA card a ancho completo o una tira de chips estrechos — y un chip no puede
 * alojar un título, una sublínea y dos botones, ni sirve de nada si está fuera de pantalla. Meter
 * esa condición aquí habría atado una proyección pura a la anchura de un contenedor.
 */
/**
 * DE QUÉ COCHE habla esta historia, o null si no habla de ninguno.
 * [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001]
 *
 * [isTheCarsQuestion] contesta si la historia **cabe** en una card de coche; esto contesta **en
 * cuál**. Son dos preguntas distintas y hasta ahora sólo existía la primera, porque con UN coche la
 * segunda era trivial: la card era la única que había. Con dos o más, sin esta respuesta no hay a
 * quién mudar la noticia — y ése, y no la anchura del chip, era el muro contra el que paró
 * [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001].
 *
 * ⛔ **Por ID, nunca por nombre.** La pregunta ya viajaba con `vehicleName` para poder decirse igual
 * en la bandeja y en Home, pero un nombre no identifica: dos coches pueden llamarse igual y el
 * nombre se edita. El sujeto llega en `PendingPromptWindow.vehicleId`, escrito por la misma llamada
 * que pone el nombre, así que no pueden contradecirse.
 *
 * ⛔ Y las historias de SALUD DEL SISTEMA devuelven null a propósito, no por falta de dato: un
 * permiso, la detección apagada o un centinela muerto **repercuten a todos los coches**, así que no
 * tienen sujeto que elegir. Es la misma frontera que [isTheCarsQuestion] y se mantiene por el mismo
 * motivo — colgarlas de un coche nombraría al culpable equivocado.
 *
 * @param parkNudgeVehicleId el coche del nudge pendiente, que es quien tiene la pregunta sin plazo.
 * @param activeVehicleId el coche activo — el sujeto del arranque en frío, que aún no ha aparcado.
 */
fun DetectionStory.subjectVehicleId(
    parkNudgeVehicleId: String?,
    activeVehicleId: String?,
): String? = when (this) {
    is DetectionStory.AwaitingAnswer -> window.vehicleId
    DetectionStory.PendingAsk -> parkNudgeVehicleId
    DetectionStory.AwaitingFirstPark -> activeVehicleId
    DetectionStory.BlockedCore,
    DetectionStory.Inactive,
    DetectionStory.NoVehicle,
    DetectionStory.Hidden,
    is DetectionStory.Watching,
    -> null
}

fun DetectionStory.isTheCarsQuestion(): Boolean = when (this) {
    // Las tres preguntas de un coche: con plazo, sin plazo, y la del arranque en frío.
    is DetectionStory.AwaitingAnswer -> true
    DetectionStory.PendingAsk, DetectionStory.AwaitingFirstPark -> true
    // Salud del sistema y nada-que-decir: del badge. `NoVehicle` es además el que justifica que el
    // badge exista — dice «no tienes coche», así que no hay card a la que mudarlo.
    DetectionStory.BlockedCore,
    DetectionStory.Inactive,
    DetectionStory.NoVehicle,
    DetectionStory.Hidden,
    is DetectionStory.Watching,
    -> false
}
