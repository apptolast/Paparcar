
package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import com.rndeveloper.paparcar.presentation.home.model.FirstStepGuide
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Stable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.ScrollState
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.chips.PaparcarFilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.model.Spot
import com.rndeveloper.paparcar.domain.model.VehicleSize
import com.rndeveloper.paparcar.domain.onboarding.FindSpotAsk
import com.rndeveloper.paparcar.domain.onboarding.FirstStep
import com.rndeveloper.paparcar.domain.onboarding.NearbyComplement
import com.rndeveloper.paparcar.domain.onboarding.FirstStepsOwnership
import com.rndeveloper.paparcar.domain.onboarding.WatchAsk
import com.rndeveloper.paparcar.domain.onboarding.WatchReinforcement
import com.rndeveloper.paparcar.presentation.onboarding.FirstStepsCard
import com.rndeveloper.paparcar.presentation.home.HomeBrowseListSlice
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.home.HomeSelection
import com.rndeveloper.paparcar.presentation.home.VehicleCard
import com.rndeveloper.paparcar.presentation.home.isDriving
import com.rndeveloper.paparcar.presentation.home.vehiclesRowOrder
import com.rndeveloper.paparcar.presentation.home.model.DetectionStory
import com.rndeveloper.paparcar.presentation.home.model.isTheCarsQuestion
import com.rndeveloper.paparcar.presentation.home.model.subjectVehicleId
import com.rndeveloper.paparcar.presentation.home.model.silencedBy
import com.rndeveloper.paparcar.presentation.home.model.notice
import com.rndeveloper.paparcar.presentation.home.model.HomeNearbyLens
import com.rndeveloper.paparcar.presentation.home.model.resolveDetectionStory
import com.rndeveloper.paparcar.presentation.home.sections.sheet.HomeSheetAction
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.components.VehicleGlyphTile
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_vehicles_section_header
import paparcar.composeapp.generated.resources.home_nearby_section_header
import paparcar.composeapp.generated.resources.home_size_filter_all
import paparcar.composeapp.generated.resources.vehicle_size_large
import paparcar.composeapp.generated.resources.vehicle_size_medium
import paparcar.composeapp.generated.resources.vehicle_size_moto
import paparcar.composeapp.generated.resources.vehicle_size_small
import paparcar.composeapp.generated.resources.vehicle_size_van
import com.rndeveloper.paparcar.ui.theme.PapGap

/**
 * Emits the sheet content items into a [LazyListScope].
 *
 * **Two blocks, two different rules** — the split that
 * [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001] introduced:
 *
 *  1. **The FIXED block — lo MÍO ahora.** Guided first steps, the detection story, then "TUS
 *     VEHÍCULOS" and one row per registered vehicle (vehicles with an active session show their park
 *     status; the others show a "Park" pill that enters AddingParking for that specific vehicle). It
 *     never folds and never changes height with anybody else's data. Hidden only when there is no
 *     vehicle at all (onboarding edge). [MULTI-PARKING-001]
 *  2. **The LANE — qué hay cerca.** One sticky chip row picks the subject (free spots · car parks ·
 *     fuel) and exactly ONE of the three lists follows. Before this, the three were stacked and
 *     foldable, so each subject's distance from the top depended on how many rows the subjects above
 *     it held — and the spots feed has no cap.
 *
 * Zone chips have moved to [HomeHeaderSection] (below the search bar).
 */
fun LazyListScope.homeSheetItems(
    slice: HomeBrowseListSlice,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    /** Hoisted scroll of the horizontal rows, so they survive the list leaving composition under a
     *  detail sheet. Null (previews, gallery) = each row remembers its own, as before.
     *  [HOME-BROWSE-SURVIVES-THE-DETAIL-SHEET-001] */
    rowsScroll: HomeSheetRowsScroll? = null,
    /** The control a checklist step is pointing at — highlighted in place. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] */
    firstStepGuide: FirstStepGuide? = null,
) {
    val filteredSpots = slice.filteredSpots
    val vehicleCards = slice.vehicleCards
    // ⛔ Ni este bloque ni el carril preguntan ya por el permiso de ubicación, y esa es la decisión
    // entera de [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]: el permiso de primer plano responde a
    // «¿dónde estoy YO?», no a «¿qué hay AHÍ?». Tus coches no tocan la ubicación por ningún lado, y
    // el carril se pregunta desde la CÁMARA (`viewportCentreOrGps`), no desde el fix.
    //
    // Lo que había antes vaciaba medio Home —tira de coches, carril, filtros, tarjeta de avisar—
    // justo para el usuario que acaba de pulsar «No permitir», que es a quien hay que convencer de
    // lo contrario. Y sin coche registrado ni siquiera quedaba el aviso que lo explicara: se iba
    // el contenido y el motivo a la vez.
    val showPersonalBlocks = vehicleCards.isNotEmpty()
    val showFilterBar = slice.hasAnySpots

    // ── 0. Qué se renderiza aquí, y en qué orden ──────────────────────────────────────────────
    // ⛔ Este comentario decía «Detection story surface — under the address header, above vehicles»
    // y siguió diciéndolo después de que `b967323b` bajara la detección debajo de los coches: un
    // comentario que sobrevive al cambio que lo desmiente cuesta más que ninguno.
    // [HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001]
    //
    // El orden del bloque fijo es: primeros pasos · VIGILANCIA · tira de coches, y luego el carril.
    // This whole list only exists in Browse with nothing selected — a selected pin gives the peek
    // the entire surface and the stepper walks the pins from there, so the old "hide the story
    // behind a tapped spot" branches (and the live-question exception to them) are gone with the
    // state that made them reachable. [UI-PEEK-STEPS-BETWEEN-PINS-001] [DET-ASK-STATE-001]
    // Both pending questions (prompt and nudge) are resolved by the projection, not arbitrated here.
    // The car both cold-start CTAs are about: the active vehicle, or the first if none is flagged.
    // Hoisted out of the detection item because the checklist's first step targets the SAME car —
    // two resolutions of "which car is this about" would be two chances to disagree.
    // [VEH-ACTIVE-FENCE-001]
    val coldStartVehicleId = vehicleCards.firstOrNull { it.vehicle.isActive }?.vehicle?.id
        ?: vehicleCards.firstOrNull()?.vehicle?.id

    // ── -1. Guided first steps — above the detection story, and only for a new user.
    // [ONBOARDING-FIRST-STEPS-ARE-GUIDED-NOT-TOLD-001]
    val firstSteps = slice.firstSteps
    // The gate lives on the slice: the sheet also OPENS ITSELF on this same question, and a second
    // copy of the rule here is how the two would come to disagree.
    // [ONBOARDING-FIRST-STEPS-MUST-BE-READABLE-AND-FOUND-001]
    val showFirstSteps = slice.showsFirstSteps
    if (showFirstSteps) {
        item("first_steps") {
            FirstStepsCard(
                progress = firstSteps,
                // El paso anuncia que va a señalar arriba ANTES de que la hoja baje.
                // [ONB-A-GUIDE-ANSWERS-UNTIL-YOU-ACT-001]
                pointingAtStep = FirstStep.PRIVATE_ZONE
                    .takeIf { firstStepGuide == FirstStepGuide.ADD_ZONE_CHIP },
                onStartStep = { step ->
                    // Pressing the CTA counts as much as reading it: it drops the user INTO the flow
                    // the step teaches, which is what the step is about.
                    // [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001]
                    if (step.completesOnEngage) onIntent(HomeIntent.CompleteFirstStep(step))
                    when (step) {
                        // The REAL flow, with the same parameters the cold-start row passes — the
                        // step teaches the app's own control, not a rehearsal of it.
                        FirstStep.MARK_PARKING -> onIntent(
                            HomeIntent.EnterAddParkingMode(
                                initialGps = slice.userGpsPoint,
                                targetVehicleId = coldStartVehicleId,
                            ),
                        )
                        // [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] Points at the header's add-zone chip
                        // instead of opening the form: the chip is where the user will add zones
                        // after the tutorial. Tapped while this step is the ask, the zone starts
                        // private. [FIRST-STEPS-A-PRIVATE-GARAGE-IS-A-PRIVATE-ZONE-001]
                        FirstStep.PRIVATE_ZONE -> onAction(HomeSheetAction.GuideTo(FirstStepGuide.ADD_ZONE_CHIP))
                        // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001] Two faces here too.
                        // With detection healthy the step only DESCRIBES and renders no CTA, so this
                        // is unreachable and it completes by observing the honest watch line below.
                        // With detection stopped the step is the one asking for it, and it fires the
                        // very intent the row it took over fires — one action, one label, one voice.
                        // [DET-TOGGLE-001]
                        // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001] Each reinforcement
                        // fires the flow that grants it, and each is the SAME entry point the app
                        // already had: the exemption request behind the fragile-watch row, and the
                        // car-Bluetooth screen Settings deep-links to. NONE renders no CTA at all
                        // (the step is not even applicable), so it cannot be pressed.
                        FirstStep.FORTIFY_WATCH -> when (firstSteps.reinforcement) {
                            WatchReinforcement.BATTERY -> onIntent(HomeIntent.RequestBatteryExemption)
                            WatchReinforcement.BLUETOOTH -> coldStartVehicleId
                                ?.let { onAction(HomeSheetAction.LinkVehicleBluetooth(it)) }
                            WatchReinforcement.NONE -> Unit
                        }
                        FirstStep.UNDERSTAND_WATCH -> when (firstSteps.watchAsk) {
                            WatchAsk.TURN_IT_ON -> onIntent(HomeIntent.EnableAutoDetection)
                            WatchAsk.EXPLAIN_RELEASE -> Unit
                        }
                        // The community half, two faces decided by the projection (`findSpotAsk`).
                        // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
                        // Both now POINT instead of doing it for the user: the spots lane, or the
                        // "Report a free spot" card at its bottom — never the report flow itself.
                        // The step still closes on this tap (it is knowledge), so the tutorial can be
                        // finished without reporting anything. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001]
                        FirstStep.FIND_SPOT -> when (firstSteps.findSpotAsk) {
                            FindSpotAsk.SEE_NEARBY -> onAction(HomeSheetAction.GuideTo(FirstStepGuide.SPOTS_LANE))
                            FindSpotAsk.REPORT_ONE -> onAction(HomeSheetAction.GuideTo(FirstStepGuide.REPORT_SPOT_CARD))
                        }
                        // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] The other two questions the map
                        // answers. It used to SELECT the car parks lane for the user; now it scrolls to
                        // the tab row and highlights car parks and fuel, and the user picks one.
                        // [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001]
                        FirstStep.EXPLORE_NEARBY -> onAction(
                            HomeSheetAction.GuideTo(FirstStepGuide.NEARBY_COMPLEMENT_TABS),
                        )
                    }
                },
                onDismiss = { onIntent(HomeIntent.DismissFirstSteps) },
                // "Not yet" — the step stops being the ask without pretending it was done.
                // [ONBOARDING-A-CHECKLIST-THAT-GUIDES-NEVER-BLOCKS-001]
                onDeferStep = { step -> onIntent(HomeIntent.DeferFirstStep(step)) },
                // "I don't have a garage" answers the private-zone step: banked as done, because it
                // is the answer the step asks for. [FIRST-STEPS-A-PRIVATE-GARAGE-IS-A-PRIVATE-ZONE-001]
                onAnswerNoStep = { step -> onIntent(HomeIntent.CompleteFirstStep(step)) },
                onResumeStep = { step -> onIntent(HomeIntent.ResumeFirstStep(step)) },
                // [ONBOARDING-A-SPOT-IS-BORN-TWO-WAYS-001] Every row opens its explainer, done ones
                // included — that is what makes replaying the checklist from Settings worth
                // something once the live state already ticks all three.
                onOpenStep = { step ->
                    onAction(HomeSheetAction.OpenFirstStepExplainer(step))
                    // [ONBOARDING-THE-COMMUNITY-STEP-CANNOT-DEMAND-A-SPOT-001] For a step of
                    // KNOWLEDGE, engaging with it IS doing it. WHICH steps those are is the model's
                    // answer (`completesOnEngage`), not an `if` written here.
                    if (step.completesOnEngage) onIntent(HomeIntent.CompleteFirstStep(step))
                },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
            )
        }
    }

    val detectionStory = resolveDetectionStory(
        slice.detectionUiState, vehicleCards, slice.parkedWatchBadge,
        promptWindow = slice.promptWindow,
        showParkNudge = slice.showParkNudge,
        // One voice: whichever of this surface's asks the checklist is currently making, THAT row
        // stands down. Which one is derived by the projection (`owns`); all this line adds is that a
        // checklist nobody can see owns nothing.
        // [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
        firstStepsOwns = if (showFirstSteps) firstSteps.owns else FirstStepsOwnership.NOTHING,
        // Mismo trato que el checklist: si la tira de coches está en pantalla, ella cuenta la
        // vigilancia sana y esta superficie no la repite.
        // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
        carCardSaysTheWatch = showPersonalBlocks,
        // Y con el diálogo de permisos abierto encima, la fila ámbar se calla: la pregunta que
        // contestaría está literalmente en pantalla. [HOME-A-PENDING-ASK-IS-NOT-A-REFUSAL-001]
        locationAskPending = slice.locationAskPending,
    )
        // Y por último, lo que el usuario ya ha dicho que no quiere ver ahora. Va DESPUÉS de la
        // proyección y no dentro: la precedencia decide QUÉ se cuenta, y esto sólo tapa un aviso que
        // ya estaba decidido. Una pregunta nunca se calla aquí — no devuelve `notice()`.
        // [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]
        .silencedBy(slice.silencedNotices)
    // ⛔ QUIÉN la pinta se decide UNA vez, aquí, y se lee en los dos sitios. Dos lugares evaluando
    // «¿la lleva la card?» es exactamente cómo se consigue que no la cuente NADIE: basta con que las
    // dos condiciones se desincronicen un día. [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
    //
    // La regla es del user: la card de un coche hospeda las PREGUNTAS de ese coche (22-09), y la
    // salud del sistema —permisos, detección apagada, vigilancia frágil o parada— se queda FUERA
    // (23-09), porque repercute a todos los coches y porque dentro de la card sustituye la
    // información del coche por un botón que empuja a pulsar. Lo decide `isTheCarsQuestion()`.
    //
    // ⛔ Y hace falta SABER DE QUÉ COCHE habla: sin sujeto no hay card a la que mudarla.
    // [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001]
    //
    // Aquí ponía `vehicleCards.size == 1`, y la excepción daba dos razones: que con 2+ coches la
    // tira son chips estrechos donde no cabe un título con sublínea y dos botones, y que el coche
    // del que habla la noticia puede quedar fuera de pantalla. Las dos se han cerrado por abajo en
    // vez de esquivarlas: el sujeto se eleva al principio del carril (`vehiclesRowOrder`) y, si
    // lleva noticia, deja de ser un chip y se dibuja como la card a ancho completo que ya existía
    // para el garaje de un coche. Lo que NO ha cambiado es la regla que las motivaba — *un aviso
    // que hay que descubrir arrastrando es peor que el badge que sustituiría* —, y por eso el
    // carril se lleva a su inicio cuando aparece (ver `vehiclesSection`).
    val storySubjectVehicleId = detectionStory.subjectVehicleId(
        parkNudgeVehicleId = slice.parkNudgeVehicleId,
        activeVehicleId = coldStartVehicleId,
    )
    val cardHostsTheStory = detectionStory.isTheCarsQuestion() &&
        vehicleCards.any { it.vehicle.id == storySubjectVehicleId }

    // ── 1. La VIGILANCIA — lo que está pasando con tu coche ahora mismo ────────────────────────
    // The ONE voice for "what is detection doing right now": loud action rows + discreet happy
    // lines, resolved by a single testable projection. [UX-DETECTION-STORY-001]
    //
    // PRIMERA del bloque fijo, encima de la tira de coches, como dibuja la ficha de
    // `HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001`.
    // [HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001]
    // La MISMA fila, dos huéspedes: la card del coche cuando puede gestionarlo, y el badge
    // suelto cuando no hay card. Se construye UNA vez como lambda para que no haya dos
    // llamadas que puedan divergir. [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
    val detectionRow: @Composable (Boolean) -> Unit = { insideCard ->
            HomeDetectionSurface(
                story = detectionStory,
                onAddVehicle = { onAction(HomeSheetAction.AddVehicle) },
                onOpenPermissions = { onAction(HomeSheetAction.OpenCorePermissions) },
                onMarkSpot = {
                    // Cold-start "mark my spot" — enters AddingParking for that vehicle. [DET-TOGGLE-002]
                    onIntent(
                        HomeIntent.EnterAddParkingMode(
                            initialGps = slice.userGpsPoint,
                            targetVehicleId = coldStartVehicleId,
                        ),
                    )
                },
                // [DET-G-01b] "I'm driving" declares THIS car and arms detection for it. [VEH-ACTIVE-FENCE-001]
                onStartDrivingDetection = { onIntent(HomeIntent.StartDrivingDetection(vehicleId = coldStartVehicleId)) },
                onActivateDetection = { onIntent(HomeIntent.EnableAutoDetection) }, // [DET-TOGGLE-001]
                // Fragile watch → fortify it with the battery exemption. [DET-WATCH-HONEST-001]
                onRequestBatteryExemption = { onIntent(HomeIntent.RequestBatteryExemption) },
                // Interrupted watch → rebuild the watcher itself. [DET-WATCH-REACTIVATE-001]
                onResumeWatch = { onIntent(HomeIntent.ResumeWatch) },
                allowDrivingDetection = true, // show both cold-start CTAs (mark spot + I'm driving)
                // [DET-ASK-STATE-001] The same two commands the notification's buttons send.
                onAnswerParked = { onIntent(HomeIntent.AnswerParkingPrompt(parked = true)) },
                onAnswerStillDriving = { onIntent(HomeIntent.AnswerParkingPrompt(parked = false)) },
                // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Tapping the card frames the place
                // the question is about. It is a CAMERA move and nothing else — the same action a
                // row tap already uses — so consulting the map can never be mistaken for answering.
                onFocusAsk = { at -> onAction(HomeSheetAction.MoveCamera(at.latitude, at.longitude)) },
                onMarkNudgeSpot = {
                    // Same promise as the notification's "Marcar mi plaza": straight into
                    // AddingParking for the nudged vehicle. [DET-NUDGE-PERSIST-001] The row only
                    // exists because DETECTION nominated a park it could not place — the confirmed
                    // pin keeps detection provenance. [DET-NUDGE-PIN-PROVENANCE-001]
                    onIntent(
                        HomeIntent.EnterAddParkingMode(
                            initialGps = slice.userGpsPoint,
                            targetVehicleId = slice.parkNudgeVehicleId ?: coldStartVehicleId,
                            fromDetectionNudge = true,
                        ),
                    )
                },
                onDismissNudge = { onIntent(HomeIntent.DismissParkNudge) },
                modifier = if (insideCard) Modifier else Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                embedded = insideCard,
                // La × sólo existe si la historia es un AVISO: `notice()` devuelve null para las tres
                // preguntas, así que no hay nada que pasar y la card nunca la ve.
                // [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]
                onSilenceNotice = detectionStory.notice()?.let { notice ->
                    { onIntent(HomeIntent.SilenceDetectionNotice(notice)) }
                },
                // Dentro de la card, el glifo del coche del que habla la noticia: la card ya no pone
                // su fila de identidad encima, y sin esto la única cosa que diría de qué coche
                // hablamos sería el nombre repetido DENTRO de la pregunta.
                // [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001]
                embeddedLeading = if (insideCard) {
                    vehicleCards.firstOrNull { it.vehicle.id == storySubjectVehicleId }?.vehicle
                        ?.let { subject -> { VehicleGlyphTile(vehicle = subject) } }
                } else {
                    null
                },
            )
    }

    // El hueco que la card rellena — null cuando no le toca a ella.
    val cardStatus: (@Composable () -> Unit)? =
        if (cardHostsTheStory) ({ detectionRow(true) }) else null

    if (detectionStory != DetectionStory.Hidden && !cardHostsTheStory) {
        item("detection_surface") {
            detectionRow(false)
        }
    }

    // ── 2. TU COCHE — la tira que sirve para NAVEGAR entre coches ──────────────────────────────
    // Debajo de la vigilancia, no encima. Lo dibujó así la ficha de
    // `HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001` y el código se mergeó al revés:
    // la tarjeta de vigilancia responde «¿está pasando algo con mi coche AHORA?», que es la pregunta
    // con la que se abre Home, y un aviso sobre el coche vigilado no puede llegar después del índice
    // que lo lista. [HOME-THE-WATCH-COMES-FIRST-AND-THE-HEADER-FOLLOWS-THE-LANE-001]
    //
    // ⛔ CON cabecera de sección, y SIEMPRE — con uno o con cinco coches (user, 10-09, en device).
    //
    // Salía solo a partir de dos, se quitó del todo por repetir en mayúsculas lo que cada card ya
    // dice, y se vuelve a poner porque en mano hace otro trabajo del que se le atribuía: no nombra
    // los coches, SEPARA dos bloques que se estaban leyendo como uno. La superficie de vigilancia
    // que va encima es una noticia; esto es un índice — y sin nada que lo diga, el ojo bajaba de una
    // card a otra sin saber que había cambiado de tema.
    // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
    //
    // Singular y plural por `plurals`, así que con un coche dice «Tu vehículo» y no la fórmula en
    // plural que delata a una app traducida a medias.
    if (showPersonalBlocks) {
        item("vehicles_header") {
            PapSectionHeader(
                title = pluralStringResource(
                    Res.plurals.home_vehicles_section_header,
                    vehicleCards.size,
                ),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
            )
        }
        vehiclesSection(slice, vehicleCards, onIntent, onAction, rowsScroll, cardStatus, storySubjectVehicleId)
    }

    // ── 3. The nearby LANE: the chip row that picks a subject, and that subject's list ───────
    // ⛔ Ya NO se esconde sin CORE. La premisa era «"cerca" no significa nada sin ubicación», y el
    // propio código la desmiente: el carril se ancla a la CÁMARA (`viewportCentreOrGps`),
    // `isAPlace` no mira el fix y `NearbyOrdering` ya ordena sin `user`. Lo que la falta
    // de fix quita no es la respuesta —qué hay donde miras— sino UNA línea de cada fila: a cuánto
    // está DE TI. Eso es lo que se calla, y nada más.
    // [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
    //
    // ⛔ El corte entre LO MIO y LO DE AQUI. Sin el, la tira de coches y la fila de pestanas se
    // tocaban y el sheet se leia como una sola lista larga: el bloque fijo dejaba de parecer un
    // bloque. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
    item("mine_lane_split") {
        PapSectionHeader(
            title = stringResource(Res.string.home_nearby_section_header),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
        )
    }
    nearbyLane(
        slice = slice,
        onIntent = onIntent,
        onAction = onAction,
        filteredSpots = filteredSpots,
        showFilterBar = showFilterBar,
        rowsScroll = rowsScroll,
        firstStepGuide = firstStepGuide,
    )
}

/**
 * The scroll of the sheet's HORIZONTAL rows — the vehicle strip and the size-filter chips.
 *
 * Both used to be remembered INSIDE their list item, and the whole list leaves composition while a
 * detail sheet is open, so coming back from one reset them to the start. Hoisted next to the list's
 * own `LazyListState` (which already outlived the detail) so Browse comes back exactly as it was.
 * [HOME-BROWSE-SURVIVES-THE-DETAIL-SHEET-001]
 */
@Stable
class HomeSheetRowsScroll(
    val vehicles: LazyListState,
    val sizeFilter: ScrollState,
)

@Composable
fun rememberHomeSheetRowsScroll(): HomeSheetRowsScroll {
    val vehicles = rememberLazyListState()
    val sizeFilter = rememberScrollState()
    return remember(vehicles, sizeFilter) { HomeSheetRowsScroll(vehicles, sizeFilter) }
}

/** El aire entre el bloque de lo MIO y el carril. Un solo valor, para que los dos lados respiren
 *  igual. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001] */

// ─────────────────────────────────────────────────────────────────────────────
// Sections
// ─────────────────────────────────────────────────────────────────────────────

private fun LazyListScope.vehiclesSection(
    slice: HomeBrowseListSlice,
    vehicleCards: List<VehicleCard>,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    rowsScroll: HomeSheetRowsScroll?,
    /** El estado de ESTE coche, ya construido por la hoja, o null si lo pinta el badge suelto.
     *  [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001] */
    cardStatus: (@Composable () -> Unit)? = null,
    /** De qué coche habla [cardStatus]. Null cuando no hay noticia de un coche concreto.
     *  [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] */
    storySubjectVehicleId: String? = null,
) {
    val userLocation = slice.userGpsPoint?.let { Pair(it.latitude, it.longitude) }
    // The vehicle whose trip is being detected RIGHT NOW (driving, not yet parked). [CHIP-DRIVING-001]
    val drivingVehicleId = slice.drivingMeta?.vehicleId
    fun VehicleCard.isDriving() = isDriving(drivingVehicleId)
    // The trip stopped and the user appears to be leaving the car — the chip flips to the candidate
    // ("Parking…") treatment. Only meaningful for the driving vehicle. [DET-PHASE-001]
    val isCandidatePhase = slice.drivingMeta?.phase == DetectionPhase.Candidate
    // Live state floats first: driving → parked (most recent park first, same preference as the
    // Browse peek subject) → monitoring config (BT, Active, Inactive). [UI-BROWSE-DRIVING-OVER-PARKED-001]
    val sorted = vehiclesRowOrder(vehicleCards, drivingVehicleId, storySubjectVehicleId)

    fun cardClick(card: VehicleCard): () -> Unit = {
        val session = card.session
        if (session != null) {
            // Row with an active session — select it and fly the camera there.
            onIntent(HomeIntent.SelectItem(HomeSelection.Parking(session.id)))
            onAction(HomeSheetAction.MoveCamera(session.location.latitude, session.location.longitude))
        } else {
            // "Aparcar" pill — enter AddingParking pre-centred on the user's GPS and
            // tagged with this vehicleId so ConfirmParkingUseCase persists the session
            // for that specific car instead of the default. [MULTI-PARKING-001]
            onIntent(
                HomeIntent.EnterAddParkingMode(
                    initialGps = slice.userGpsPoint,
                    targetVehicleId = card.vehicle.id,
                ),
            )
        }
    }

    fun chipRow(cards: List<VehicleCard>) {
        item("vehicles_row") {
            LazyRow(
                state = rowsScroll?.vehicles ?: rememberLazyListState(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(cards, key = { it.vehicle.id }) { card ->
                    val onCardClick = remember(card.session?.id, card.vehicle.id, onIntent, onAction) {
                        cardClick(card)
                    }
                    HomeVehicleChip(
                        card = card,
                        isDriving = card.isDriving(),
                        isCandidate = card.isDriving() && isCandidatePhase,
                        // One trip at a time, and the chip only reads it while it IS that trip.
                        // [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
                        drivingPlace = slice.drivingPlace,
                        // Same stop as the single-vehicle card's. [DET-STOP-BUTTON-001]
                        onStopTrip = { onIntent(HomeIntent.StopDetection) },
                        onClick = onCardClick,
                    )
                }
            }
        }
    }

    // ¿Hay un coche que se dibuje como CARD a ancho completo, en vez de como chip?
    // [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001]
    //
    // Dos motivos, y el segundo es el que nace aquí:
    //  · es el único coche del garaje — la maqueta de siempre [HOME-CARDS-001]; o
    //  · LIDERA el carril y trae noticia, así que la noticia va dentro de SU card en vez de en una
    //    segunda card encima. `vehiclesRowOrder` ya lo ha puesto primero, que es la otra mitad: sin
    //    el hoist, la card del sujeto podía quedar fuera de pantalla, y *un aviso que hay que
    //    descubrir arrastrando es peor que el badge que sustituiría*.
    val leader = sorted.firstOrNull()
    val leaderCarriesTheNews = cardStatus != null &&
        storySubjectVehicleId != null &&
        leader?.vehicle?.id == storySubjectVehicleId
    if (sorted.size == 1 || leaderCarriesTheNews) {
        val card = sorted.first()
        // Los demás siguen siendo chips detrás; con un solo coche no hay «detrás».
        val rest = sorted.drop(1)
        item("vehicle_single") {
            HomeVehicleCard(
                card = card,
                isDriving = card.isDriving(),
                isCandidate = card.isDriving() && isCandidatePhase,
                // Already null unless a trip is live, so the card reads it only when it applies.
                // [UI-A-LIVE-TRIP-NAMES-ITS-PLACE-001]
                drivingPlace = slice.drivingPlace,
                // "Parar" — ends THIS trip, leaves the feature on. Lives on the card because the
                // card is the one surface telling the trip. [DET-STOP-BUTTON-001]
                // [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]
                onStopTrip = { onIntent(HomeIntent.StopDetection) },
                // El estado de ESTE coche, dentro de su card: la MISMA fila que pintaría el badge,
                // en modo `embedded`. Mismos textos y mismos intents — cambia el huésped, no lo que
                // pasa al tocar. [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
                status = cardStatus,
                userLocation = userLocation,
                onClick = cardClick(card),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        // Los demás, detrás, como chips. UNA sola definición de la tira para las dos maquetas: con
        // la card líder delante y sin ella. Duplicarla era el camino corto a que se tiñan distinto
        // dentro de un mes — el mismo argumento que ya hizo una sola las dos cápsulas del par.
        // [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001]
        if (rest.isNotEmpty()) chipRow(rest)
    } else {
        chipRow(sorted)
    }
}

/**
 * The nearby lane: ONE sticky chip row that picks the subject, then that subject's list.
 * [HOME-A-SECTION-MUST-NOT-SIT-BEHIND-ANOTHERS-LIST-001]
 *
 * What this replaced: three foldable sections stacked in a fixed order (spots → car parks → fuel),
 * where the `y` of each one was a function of how many rows the ones above it held. The free-spots
 * feed has no cap, so "where to refuel" sat ~1.100 dp down with 10 spots nearby and ~2.100 dp with 25
 * — measured off this file's own paddings. A subject the user asks for cannot live behind another
 * subject's list, and folding never fixed it: to fold spots you first have to reach its chevron.
 */
@OptIn(ExperimentalFoundationApi::class)
private fun LazyListScope.nearbyLane(
    slice: HomeBrowseListSlice,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    filteredSpots: List<Spot>,
    showFilterBar: Boolean,
    rowsScroll: HomeSheetRowsScroll?,
    firstStepGuide: FirstStepGuide?,
) {
    // STICKY, and that is the half of this design that makes switching back cheap: from the bottom of
    // a car-park list the way back to the spots is the row already pinned above, not a scroll to the
    // top. Its key is public because the checklist's GuideTo walks the list to it. [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
    stickyHeader(NEARBY_LENS_KEY) {
        // ⛔ **Tabs y filtros son UN solo bloque fijado, y tenía que ser uno.**
        // [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
        //
        // Los filtros también se quedan arriba al desplazar (petición del user), pero hacerlos un
        // `stickyHeader` aparte los habría enfrentado con éste: en una `LazyColumn` sólo hay un
        // sticky a la vez y el siguiente EXPULSA al anterior, así que la barra de filtros habría
        // echado fuera los tabs — justo la fila cuya razón de ser es que volver a Plazas desde el
        // fondo de una lista no cueste un scroll.
        //
        // Juntos en un bloque no hay pelea, y además dicen la verdad de lo que son: los filtros no
        // son de la hoja, son DEL CARRIL, y el carril lo eligen los tabs de encima. Por eso el
        // bloque cambia de contenido cuando cambia la pestaña.
        //
        // ⛔ **Y el bloque PINTA SU SUELO** [HOME-A-PINNED-HEADER-NEEDS-ITS-OWN-FLOOR-001]. Un
        // `stickyHeader` no trae fondo: flota sobre la lista, y la lista se desplaza POR DEBAJO.
        // Mientras esto era una sola fila de pestañas no se notaba —los tabs son texto ancho y
        // opaco—, pero al entrar los filtros el hueco entre chips redondeados dejó ver las filas
        // pasando por detrás: en la captura del user, «Free / Paid / Covered» y «Car park · 2.0 km»
        // se leían a la vez.
        //
        // El color es el de la hoja (`surfaceContainer`), el mismo que pinta el `Surface` del
        // bottom sheet, así que la cabecera no se lee como una tarjeta encima sino como la parte de
        // la hoja que no se va.
        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainer)) {
            HomeNearbyLensRow(
                lens = slice.nearbyLens,
                // Spots always know their count — the feed is a live subscription. The complements
                // only know theirs once their lane has been asked, and a `· 0` before that would be
                // a verdict no source gave. [FUEL-A-SECTION-ANSWERS-ONLY-WHAT-IT-ASKED-001]
                spotCount = filteredSpots.size,
                fuelCount = slice.fuel.stations.size,
                parkingsCount = slice.parkings.parkings.size,
                onSelect = { lens -> onIntent(HomeIntent.SetNearbyLens(lens)) },
                highlighted = when (firstStepGuide) {
                    FirstStepGuide.SPOTS_LANE -> setOf(HomeNearbyLens.SPOTS)
                    FirstStepGuide.NEARBY_COMPLEMENT_TABS -> setOf(HomeNearbyLens.PARKINGS, HomeNearbyLens.FUEL)
                    else -> emptySet()
                },
            )
            LaneFilterBar(slice, onIntent, showFilterBar, rowsScroll)
        }
    }

    when (slice.nearbyLens) {
        HomeNearbyLens.SPOTS -> spotsLane(slice, onIntent, onAction, filteredSpots, showFilterBar, rowsScroll, firstStepGuide)

        // Car parks before fuel in the chip row, and that order is a PRODUCT decision, not an
        // accident: this app is about leaving the car somewhere, so "where do I park" sits closer to
        // what the user came for than "where do I refuel". Both are complements to the free spots;
        // neither is the product. [PARKINGS-ON-THE-MAP-001]
        HomeNearbyLens.PARKINGS -> item("parkings_lane") {
            HomeParkingsLane(
                slice = slice.parkings,
                // A row selects the car park THROUGH the same lambda its map capsule uses, so the
                // camera flies to it and the peek opens — a selection the map does not acknowledge
                // reads as a dead tap. [FUEL-STATIONS-MAP-AND-PEEK-001]
                onParkingClick = { parkingId ->
                    onAction(HomeSheetAction.SelectPublicParking(parkingId))
                },
                // [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001] Whether THIS lane is the one being
                // introduced — the slice carries the complement, so the comparison is the lane's.
                isIntroducing = slice.nearbyIntro == NearbyComplement.CAR_PARKS,
                // Sin fix la fila se calla la distancia: se midió desde la cámara, no desde ti.
                // [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
                hasFix = slice.userGpsPoint != null,
            )
        }

        HomeNearbyLens.FUEL -> item("fuel_lane") {
            HomeFuelLane(
                slice = slice.fuel,
                onStationClick = { stationId ->
                    onAction(HomeSheetAction.SelectFuelStation(stationId))
                },
                isIntroducing = slice.nearbyIntro == NearbyComplement.FUEL,
                hasFix = slice.userGpsPoint != null,
                // La estrella de cada fila. [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
                followedStationKeys = slice.followedFuelStationKeys,
            )
        }
    }
}

/**
 * **La barra de filtros DEL CARRIL ACTIVO**, dentro de la cabecera fijada y justo bajo los tabs.
 * [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]
 *
 * Un solo sitio decide qué filtros se enseñan, y lo decide la pestaña. Antes cada carril dibujaba
 * los suyos donde le tocaba —las tallas como un `item` de la lista de plazas, la tarifa dentro del
 * propio `HomeParkingsLane`—, así que se iban con el scroll y, peor, no había forma de que dos
 * carriles se pusieran de acuerdo sobre dónde vive su barra.
 *
 * ⛔ **Cada carril decide si TIENE filtros y cuándo enseñarlos, y la condición no es la misma**:
 *  · Plazas los enseña con que haya plazas (`hasAnySpots`) — el feed es una suscripción viva.
 *  · Parkings sólo con una respuesta que filtrar (`hasAnyBeforeFilter`): encima de «no hemos podido
 *    preguntar» la barra haría creer que el filtro es la causa de que no haya nada.
 * Unificar las dos en una sola condición sería inventarse un veredicto para uno de los dos.
 */
@Composable
private fun LaneFilterBar(
    slice: HomeBrowseListSlice,
    onIntent: (HomeIntent) -> Unit,
    showFilterBar: Boolean,
    rowsScroll: HomeSheetRowsScroll?,
) {
    // 16dp — same grid as headers/rows so the bar doesn't step out of the left edge when the sheet
    // expands. [HOME-VEH-REFINE-001]
    val barModifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    when (slice.nearbyLens) {
        HomeNearbyLens.SPOTS -> if (showFilterBar) {
            HomeSizeFilterBar(
                selectedSize = slice.sizeFilter,
                onFilterSelect = { size -> onIntent(HomeIntent.SetSizeFilter(size)) },
                scrollState = rowsScroll?.sizeFilter ?: rememberScrollState(),
                modifier = barModifier,
            )
        }

        HomeNearbyLens.PARKINGS -> if (slice.parkings.hasAnyBeforeFilter) {
            HomeParkingFilterBar(
                selected = slice.parkings.filter,
                onSelect = { filter -> onIntent(HomeIntent.SetParkingFilter(filter)) },
                modifier = barModifier,
            )
        }

        //  · Gasolineras, con respuesta que ordenar (`stations.isNotEmpty()`): encima de «no hemos
        //    podido preguntar» o de «tu coche no tiene combustible» sería ofrecer ordenar una lista
        //    que no existe. [FUEL-THE-LANE-IS-FILTERED-BY-WHAT-YOU-BURN-001]
        HomeNearbyLens.FUEL -> if (slice.fuel.stations.isNotEmpty()) {
            HomeFuelFilterBar(
                sort = slice.fuel.sort,
                product = slice.fuel.product,
                availableProducts = slice.fuel.availableProducts,
                onSortSelect = { sort -> onIntent(HomeIntent.SetFuelSort(sort)) },
                onProductSelect = { product -> onIntent(HomeIntent.SetFuelProduct(product)) },
                modifier = barModifier,
            )
        }
    }
}

private fun LazyListScope.spotsLane(
    slice: HomeBrowseListSlice,
    onIntent: (HomeIntent) -> Unit,
    onAction: (HomeSheetAction) -> Unit,
    filteredSpots: List<Spot>,
    showFilterBar: Boolean,
    rowsScroll: HomeSheetRowsScroll?,
    firstStepGuide: FirstStepGuide?,
) {
    // ⛔ La barra de tallas ya NO se dibuja aquí: subió a la cabecera fijada, con los tabs.
    // Ver [LaneFilterBar]. [PARKINGS-THE-LANE-FILTERS-BY-WHAT-WE-KNOW-001]

    when {
        slice.isLoading -> item("skeleton") { NearbyRowsSkeleton() }
        filteredSpots.isEmpty() && slice.sizeFilter != null && slice.hasAnySpots ->
            item("empty_filtered") {
                HomeEmptyFilteredSpots(
                    onClearFilter = { onIntent(HomeIntent.SetSizeFilter(null)) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        filteredSpots.isEmpty() -> item("empty") { HomeEmptySpots(unasked = slice.spotsUnasked) }
        else -> itemsIndexed(filteredSpots, key = { _, spot -> spot.id }) { index, spot ->
            Column {
                HomeSpotRow(
                    spot = spot,
                    userLocation = slice.userGpsPoint?.let { Pair(it.latitude, it.longitude) },
                    onSelect = {
                        onAction(HomeSheetAction.MoveCamera(spot.location.latitude, spot.location.longitude))
                        onIntent(HomeIntent.SelectItem(HomeSelection.Spot(spot.id)))
                    },
                )
                if (index < filteredSpots.lastIndex) {
                    PapDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
                }
            }
        }
    }

    // El estado vacio ya no lleva CTA propio (es una linea), asi que la tarjeta de avisar se
    // muestra SIEMPRE: antes se suprimia justo cuando mas falta hacia, para no duplicar el boton que
    // vivia dentro de la caja discontinua. [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
    item("report_spot_cta") {
        HomeReportSpotCard(
            onReport = { onAction(HomeSheetAction.RequestReportMode) },
            // ⛔ El ÚNICO gate por ubicación que sobrevive en este sheet, y por una razón que no es
            // la de los demás: **la única fiabilidad que tiene un aviso comunitario es que sepamos
            // que estabas cerca.** Un aparcamiento es tuyo y lo verifica quien lo pone; una plaza se
            // publica a desconocidos que van a conducir hasta ella. Sin fix no podemos afirmar que
            // la viste — solo que alguien arrastró un pin sobre un mapa.
            //
            // Es la doctrina de detección —el evento NOMINA, solo lo MEDIDO confirma; mejor falso
            // negativo que plaza fantasma— aplicada al reporte manual. Y por eso la tarjeta se
            // queda a la vista DICIENDO por qué no puede: esconderla dejaba al usuario sin la
            // acción y sin el motivo. [SPOT-A-REPORT-WITHOUT-A-PLACE-MUST-NOT-HAPPEN-001]
            // [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
            enabled = slice.userGpsPoint != null,
            highlighted = firstStepGuide == FirstStepGuide.REPORT_SPOT_CARD,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        )
    }
}

/**
 * Item key of the sticky lens row. Public because [HomeSheetAction.GuideTo] walks the list to
 * it — a second spelling of the string would be a scroll that silently stops working.
 * [ONBOARDING-STEPS-MUST-EXPLAIN-WHAT-REALLY-HAPPENS-001]
 */
internal const val NEARBY_LENS_KEY = "nearby_lens"

/**
 * Dónde está la cabecera del carril en la lista, o null si todavía no se ha compuesto.
 * [HOME-THE-CAMERA-FRAMES-WHAT-THE-LIST-SHOWS-001]
 *
 * Vive junto a su clave y devuelve `null` en vez de inventarse un destino, porque **cada uso quiere
 * un fallback distinto**: el checklist, que va a ENSEÑAR el control, baja al último item (todo lo
 * que hay bajo la fila de pestañas es el carril, así que ambos caminos acaban dentro); el cambio de
 * carril, en cambio, prefiere **no moverse** — forzar un scroll a alguien que está mirando sus
 * coches es peor que dejarlo donde está.
 */
internal fun LazyListState.nearbyLensIndexOrNull(): Int? =
    layoutInfo.visibleItemsInfo.firstOrNull { it.key == NEARBY_LENS_KEY }?.index

// Width of the right-edge scroll-hint fade on the size filter bar. [HOME-POLISH-001]
private const val FILTER_FADE_WIDTH_DP = 28

// ─────────────────────────────────────────────────────────────────────────────
// Size filter bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeSizeFilterBar(
    selectedSize: VehicleSize?,
    onFilterSelect: (VehicleSize?) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
) {
    val allLabel    = stringResource(Res.string.home_size_filter_all)
    val motoLabel   = stringResource(Res.string.vehicle_size_moto)
    val smallLabel  = stringResource(Res.string.vehicle_size_small)
    val mediumLabel = stringResource(Res.string.vehicle_size_medium)
    val largeLabel  = stringResource(Res.string.vehicle_size_large)
    val vanLabel    = stringResource(Res.string.vehicle_size_van)

    // Right-edge fade to the sheet background, signalling there are more filter chips off-screen.
    // Drawn BEFORE horizontalScroll so it stays fixed at the viewport edge (not scrolled with the
    // content) and, being a draw modifier, never intercepts chip taps. Only shown while there is
    // more to scroll. [HOME-POLISH-001]
    val fadeColor = MaterialTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier
            .drawWithContent {
                drawContent()
                if (scrollState.canScrollForward) {
                    val fadeW = FILTER_FADE_WIDTH_DP.dp.toPx()
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, fadeColor),
                            startX = size.width - fadeW,
                            endX = size.width,
                        ),
                        topLeft = Offset(size.width - fadeW, 0f),
                        size = Size(fadeW, size.height),
                    )
                }
            }
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(PapGap.chipRow),
    ) {
        PaparcarFilterChip(
            label = allLabel,
            selected = selectedSize == null,
            onClick = { onFilterSelect(null) },
        )
        listOf(
            VehicleSize.MOTORCYCLE   to motoLabel,
            VehicleSize.MICRO_SMALL  to smallLabel,
            VehicleSize.MEDIUM_SUV to mediumLabel,
            VehicleSize.LARGE_SEDAN  to largeLabel,
            VehicleSize.VAN_HIGH    to vanLabel,
        ).forEach { (size, label) ->
            PaparcarFilterChip(
                label = label,
                selected = selectedSize == size,
                onClick = { onFilterSelect(if (selectedSize == size) null else size) },
            )
        }
    }
}

