package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import com.rndeveloper.paparcar.ui.components.ctaFitsInline
import com.rndeveloper.paparcar.ui.components.ctaPairFitsSideBySide
import com.rndeveloper.paparcar.ui.components.PapCtaPill
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.automirrored.rounded.NotListedLocation
import androidx.compose.material.icons.rounded.SensorsOff
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.GpsPoint
import com.rndeveloper.paparcar.presentation.home.model.DetectionStory
import com.rndeveloper.paparcar.presentation.home.model.ParkedWatchBadge
import com.rndeveloper.paparcar.presentation.util.formatClockTime
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.PapControlHeight
import com.rndeveloper.paparcar.ui.theme.PapGap
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_det_notice_silence_cd
import paparcar.composeapp.generated.resources.home_det_awaiting_cta_primary
import paparcar.composeapp.generated.resources.home_det_awaiting_cta_secondary
import paparcar.composeapp.generated.resources.home_det_awaiting_sub
import paparcar.composeapp.generated.resources.home_det_awaiting_title
import paparcar.composeapp.generated.resources.home_det_ask_cta_no
import paparcar.composeapp.generated.resources.home_det_ask_cta_yes
import paparcar.composeapp.generated.resources.home_det_ask_sub_at
import paparcar.composeapp.generated.resources.home_det_ask_sub_at_place
import paparcar.composeapp.generated.resources.home_det_ask_title
import paparcar.composeapp.generated.resources.home_det_ask_title_vehicle
import paparcar.composeapp.generated.resources.home_det_core_cta
import paparcar.composeapp.generated.resources.home_det_core_sub
import paparcar.composeapp.generated.resources.home_det_core_title
import paparcar.composeapp.generated.resources.home_det_novehicle_cta
import paparcar.composeapp.generated.resources.home_det_novehicle_sub
import paparcar.composeapp.generated.resources.home_det_novehicle_title
import paparcar.composeapp.generated.resources.home_det_producer_cta
import paparcar.composeapp.generated.resources.home_det_producer_sub
import paparcar.composeapp.generated.resources.home_det_producer_title
import paparcar.composeapp.generated.resources.home_det_watch_interrupted_cta
import paparcar.composeapp.generated.resources.home_det_watch_interrupted_sub
import paparcar.composeapp.generated.resources.home_det_watch_interrupted_title
import paparcar.composeapp.generated.resources.home_det_watching_bt_sub
import paparcar.composeapp.generated.resources.home_det_watching_fortify_cta
import paparcar.composeapp.generated.resources.home_det_watching_fragile_sub
import paparcar.composeapp.generated.resources.home_det_watching_parked_sub
import paparcar.composeapp.generated.resources.home_det_watching_title
import paparcar.composeapp.generated.resources.home_nudge_cta
import paparcar.composeapp.generated.resources.home_nudge_dismiss
import paparcar.composeapp.generated.resources.home_nudge_sub
import paparcar.composeapp.generated.resources.home_nudge_title

/**
 * The Home **detection story surface** — the single voice that answers "what is the app doing
 * for me right now". [DET-READY-001h] [UX-DETECTION-STORY-001]
 *
 * Renders the one [DetectionStory] resolved by `resolveDetectionStory`: the action stories keep the
 * loud accent-bar row (severity-adaptive: amber CORE warning, calm amber upsells, brand-green
 * questions and cold start); [DetectionStory.Watching] renders a discreet one-line status — the
 * happy path speaks instead of going mute. [DetectionStory.Hidden] renders nothing. A live trip is
 * told by the vehicle card, stop CTA included, never here.
 * [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]
 *
 * This composable renders; it does NOT arbitrate. Which story wins — including the two pending
 * questions, [DetectionStory.AwaitingAnswer] and [DetectionStory.PendingAsk] — is decided by the
 * pure projection, so the precedence lives in one testable place. [DET-ASK-STATE-001]
 */
@Composable
fun HomeDetectionSurface(
    story: DetectionStory,
    onAddVehicle: () -> Unit,
    onOpenPermissions: () -> Unit,
    onMarkSpot: () -> Unit,
    onStartDrivingDetection: () -> Unit,
    onActivateDetection: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Whether the cold-start row offers the secondary "I'm driving" action. Off until the manual
     * Coordinator arming (DET-G-01b) exists — there is no infra to honour it yet. [DET-READY-001h]
     */
    allowDrivingDetection: Boolean = false,
    /** [DET-NUDGE-PERSIST-001] Resolve the pending "where did you leave your car?" nudge. Whether
     *  the nudge row shows at all is decided by `resolveDetectionStory`, not here. */
    onMarkNudgeSpot: () -> Unit = {},
    onDismissNudge: () -> Unit = {},
    /** [DET-ASK-STATE-001] The two answers to the "did you park?" row — the same pair of commands
     *  the tray notification's buttons send. Default no-ops so previews/callers that don't wire them
     *  don't break. */
    onAnswerParked: () -> Unit = {},
    onAnswerStillDriving: () -> Unit = {},
    /** [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Frame the place the question is about. */
    onFocusAsk: (GpsPoint) -> Unit = {},
    /** Fire the battery-optimization exemption request from the FRAGILE watch row — the setup whose
     *  problem the exemption actually solves. [DET-WATCH-HONEST-001] [DET-BATTERY-EXEMPTION-NUDGE-001] */
    onRequestBatteryExemption: () -> Unit = {},
    /** Rebuild the departure watcher from the INTERRUPTED watch row. Deliberately NOT the exemption
     *  request: this row means the watcher is dead, and a permission grant never restarts it.
     *  [DET-WATCH-REACTIVATE-001] */
    onResumeWatch: () -> Unit = {},
    /**
     * Se pinta DENTRO de la card del coche en vez de como tarjeta propia.
     * [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
     *
     * Regla del user (22-09): **la card de un coche gestiona TODOS los estados de ese coche**, y el
     * badge suelto sólo queda para cuando no hay ningún coche registrado — porque entonces no hay
     * card que pueda gestionar nada. Quien decide cuál de los dos pinta es `resolveDetectionStory`,
     * no este parámetro: aquí sólo se dice cómo vestirse.
     */
    embedded: Boolean = false,
    /** El glifo del coche del que habla la noticia, sólo en modo [embedded]: la card ya no pone su
     *  fila de identidad encima. [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] */
    embeddedLeading: (@Composable () -> Unit)? = null,
    /**
     * «Ahora no» para los avisos de SALUD DEL SISTEMA — permiso, detección apagada, vigilancia
     * frágil o parada, sin coche. Null = esta superficie no ofrece callar nada.
     * [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]
     *
     * ⛔ Las tres PREGUNTAS no lo reciben, y no hace falta que el compilador lo impida: lo impide
     * `DetectionStory.notice()`, que devuelve null para ellas, así que el llamante no tiene qué
     * pasar. Una pregunta callada sin contestar es una respuesta perdida.
     */
    onSilenceNotice: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val amber = Tone(cs.secondary, cs.onSecondary, cs.secondaryContainer, cs.onSecondaryContainer, isError = false)
    val error = Tone(cs.error, cs.onError, cs.errorContainer, cs.onErrorContainer, isError = true)
    // Colour = WHO is watching. Set-up rows speak as the app → brand green. Rows about a specific
    // vehicle wear that vehicle's identity colour — read from THE single resolver
    // (`vehicleIdentityColor`), the same call the chip, garage and peek make, so this surface can
    // never drift from them again. This file used to keep its own copy of the method→colour switch
    // (raw `papCarBlue`/`papWatchGreen` + a Boolean that could not say "unwatched") and that copy is
    // exactly what went silently wrong when the greens split. Tonal container = accent at low
    // alpha, same language as the watch badge. [UI-COLOR-DOCTRINE-001]
    val brand = Tone(cs.primary, cs.onPrimary, cs.primary.copy(alpha = INFO_CONTAINER_ALPHA), cs.primary, isError = false)
    @Composable
    fun identityTone(watch: VehicleWatch): Tone {
        val accent = vehicleIdentityColor(watch)
        return Tone(accent, cs.surface, accent.copy(alpha = INFO_CONTAINER_ALPHA), accent, isError = false)
    }

    when (story) {
        // [DET-ASK-STATE-001] The question the app is waiting on, asked in-app with the SAME two
        // answers as the tray notification. Green, not amber: nothing has failed — detection is
        // doing its job and simply cannot tell on its own, which is the doctrine's asymmetric
        // failure working as designed. The wording comes from the notification that posted it, so
        // the two surfaces cannot drift.
        //
        // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The WATCHED green, not the brand's. This
        // row was left on `brand` by the sweep that fixed `methodTone` a few lines below — the same
        // miss, one branch of the `when` further down, and the rule that sweep wrote applies here
        // more plainly than anywhere: this row names a specific car in its own title and wears its
        // glyph. A fixed `VehicleWatch.Assisted` rather than the story's own watch because the
        // question is Coordinator-only — `NotifyParkingConfirmation` and `degradeToPrompt` are
        // wired nowhere else, and a Bluetooth-watched car confirms deterministically without ever
        // being asked. Painting it blue would be the one screen that proves the car is NOT on the
        // Bluetooth lane.
        is DetectionStory.AwaitingAnswer -> ActionRow(
            tone = identityTone(VehicleWatch.Assisted),
            // The car, not a parking "P": this row is about the vehicle Paparcar is following, and
            // the app's whole visual vocabulary for "your car" is the car glyph. A P would be the
            // first place in the app where parking is drawn as signage.
            icon = Icons.Rounded.DirectionsCar,
            title = story.window.vehicleName
                ?.let { stringResource(Res.string.home_det_ask_title_vehicle, it) }
                ?: stringResource(Res.string.home_det_ask_title),
            // [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] The subtitle answers WHEN. Absolute
            // ("a las 12:41"), never "hace N min": a relative age needs a ticker, and without one it
            // freezes at whatever it read when the row was composed — the trap already met with the
            // map markers. An absolute time is also literally what was asked for, and it never lies
            // as it ages. It says nothing about what happens on silence: of the three verdicts the
            // timeout can reach, only one plants a pin here, so any countdown would be a promise
            // the app cannot keep.
            subtitle = story.window.street?.let { street ->
                // The place first: it is what decides the answer. The hour qualifies it.
                stringResource(
                    Res.string.home_det_ask_sub_at_place,
                    street,
                    formatClockTime(story.window.shownAtMs),
                )
            } ?: stringResource(
                Res.string.home_det_ask_sub_at,
                formatClockTime(story.window.shownAtMs),
            ),
            primaryLabel = stringResource(Res.string.home_det_ask_cta_yes),
            onPrimary = onAnswerParked,
            secondaryLabel = stringResource(Res.string.home_det_ask_cta_no),
            onSecondary = onAnswerStillDriving,
            secondaryIsDecline = true,
            // Tapping the CARD frames the place on the map. Not a button: a third control in a
            // yes/no row would rank "ver" alongside two answers it is not equal to, and the badge
            // glyph is a ~24 dp target nobody finds. Looking is NOT answering — this must never
            // touch the window, exactly like the deliberately absent `setDeleteIntent`.
            onClick = story.window.candidate?.let { at -> { onFocusAsk(at) } },
            modifier = modifier,
            embedded = embedded,
            embeddedLeading = embeddedLeading,
            )

        // [DET-NUDGE-PERSIST-001] The older, deadline-less ask: a parking record we could not place.
        DetectionStory.PendingAsk -> ActionRow(
            tone = amber,
            icon = Icons.AutoMirrored.Rounded.NotListedLocation,
            title = stringResource(Res.string.home_nudge_title),
            subtitle = stringResource(Res.string.home_nudge_sub),
            primaryLabel = stringResource(Res.string.home_nudge_cta),
            onPrimary = onMarkNudgeSpot,
            secondaryLabel = stringResource(Res.string.home_nudge_dismiss),
            onSecondary = onDismissNudge,
            modifier = modifier,
            embedded = embedded,
            embeddedLeading = embeddedLeading,
            )

        DetectionStory.NoVehicle -> ActionRow(
            tone = amber,
            icon = Icons.Rounded.DirectionsCar,
            title = stringResource(Res.string.home_det_novehicle_title),
            subtitle = stringResource(Res.string.home_det_novehicle_sub),
            primaryLabel = stringResource(Res.string.home_det_novehicle_cta),
            onPrimary = onAddVehicle,
            modifier = modifier,
            embedded = embedded,
            embeddedLeading = embeddedLeading,
            onSilence = onSilenceNotice,
            )

        // One "activate detection" surface for both causes — Settings flag off OR producer
        // permissions missing. The single button asks for whatever is missing. [DET-TOGGLE-001]
        DetectionStory.Inactive -> ActionRow(
            tone = brand,
            icon = Icons.Rounded.SensorsOff,
            title = stringResource(Res.string.home_det_producer_title),
            subtitle = stringResource(Res.string.home_det_producer_sub),
            primaryLabel = stringResource(Res.string.home_det_producer_cta),
            onPrimary = onActivateDetection,
            modifier = modifier,
            embedded = embedded,
            embeddedLeading = embeddedLeading,
            onSilence = onSilenceNotice,
            )

        // Amber, not red: missing location is PENDING and fixable, not broken — and the peek above
        // already wears the matching warning badge. The red full-sheet blocker this row used to sit
        // under is gone. [HOME-LOCATION-WARNING-IS-A-BADGE-NOT-A-MODAL-001]
        DetectionStory.BlockedCore -> ActionRow(
            tone = amber,
            icon = Icons.Rounded.LocationOff,
            title = stringResource(Res.string.home_det_core_title),
            subtitle = stringResource(Res.string.home_det_core_sub),
            primaryLabel = stringResource(Res.string.home_det_core_cta),
            onPrimary = onOpenPermissions,
            modifier = modifier,
            embedded = embedded,
            embeddedLeading = embeddedLeading,
            onSilence = onSilenceNotice,
            )

        DetectionStory.AwaitingFirstPark -> ActionRow(
            tone = brand,
            icon = Icons.Rounded.AddLocationAlt,
            title = stringResource(Res.string.home_det_awaiting_title),
            subtitle = stringResource(Res.string.home_det_awaiting_sub),
            primaryLabel = stringResource(Res.string.home_det_awaiting_cta_primary),
            onPrimary = onMarkSpot,
            secondaryLabel = if (allowDrivingDetection) stringResource(Res.string.home_det_awaiting_cta_secondary) else null,
            onSecondary = onStartDrivingDetection,
            modifier = modifier,
            embedded = embedded,
            embeddedLeading = embeddedLeading,
            )

        // A LIVE TRIP renders nothing here: the vehicle card tells it whole and carries the stop
        // CTA, so the projection never emits a driving story any more.
        // [HOME-A-DRIVE-IS-ONE-NEWS-AND-THE-CARD-CARRIES-THE-STOP-001]

        // Honest watch line: the vehicle's identity colour + no CTA only when the watch is genuinely
        // live; amber warning with an "activate" CTA when fragile; error "reactivate" when the OS
        // killed it. Each CTA pulls its OWN lever — the exemption fortifies a live-but-fragile watch,
        // the resume rebuilds a dead one; they are not interchangeable.
        // [DET-WATCH-HONEST-001] [DET-WATCH-REACTIVATE-001] [UI-COLOR-DOCTRINE-001]
        is DetectionStory.Watching -> when (story.watchBadge) {
            ParkedWatchBadge.WATCHING, ParkedWatchBadge.PARK_MY_VEHICLE -> ActionRow(
                tone = identityTone(story.watch),
                icon = Icons.Rounded.Visibility,
                title = stringResource(Res.string.home_det_watching_title, story.vehicleName),
                subtitle = stringResource(
                    if (story.isParked) Res.string.home_det_watching_parked_sub else Res.string.home_det_watching_bt_sub,
                ),
                primaryLabel = null,
                onPrimary = {},
                modifier = modifier,
                embedded = embedded,
                embeddedLeading = embeddedLeading,
                )

            ParkedWatchBadge.WATCHING_FRAGILE -> ActionRow(
                tone = amber,
                icon = Icons.Rounded.Visibility,
                title = stringResource(Res.string.home_det_watching_title, story.vehicleName),
                subtitle = stringResource(Res.string.home_det_watching_fragile_sub),
                primaryLabel = stringResource(Res.string.home_det_watching_fortify_cta),
                onPrimary = onRequestBatteryExemption,
                onSilence = onSilenceNotice,
                primaryStacksBelow = true,
                modifier = modifier,
                embedded = embedded,
                embeddedLeading = embeddedLeading,
                )

            ParkedWatchBadge.WATCH_INTERRUPTED -> ActionRow(
                tone = error,
                icon = Icons.Rounded.VisibilityOff,
                title = stringResource(Res.string.home_det_watch_interrupted_title, story.vehicleName),
                subtitle = stringResource(Res.string.home_det_watch_interrupted_sub),
                primaryLabel = stringResource(Res.string.home_det_watch_interrupted_cta),
                onPrimary = onResumeWatch,
                onSilence = onSilenceNotice,
                primaryStacksBelow = true,
                modifier = modifier,
                embedded = embedded,
                embeddedLeading = embeddedLeading,
                )

            // ⛔ Inalcanzable, y la rama existe para que lo siga siendo: `resolveDetectionStory`
            // devuelve `Hidden` sin noticias del servicio, así que esta superficie nunca recibe la
            // duda. Si alguien quitara ese filtro, el compilador no se quejaría — pero esta rama
            // dice qué haría: **nada**, nunca acusar. [DET-A-SILENT-SERVICE-IS-NOT-A-DEAD-ONE-001]
            ParkedWatchBadge.WATCH_UNKNOWN -> Unit
        }

        DetectionStory.Hidden -> Unit
    }
}

/** Semantic colour bundle for one severity tone, sourced from the [MaterialTheme] colour scheme. */
private data class Tone(
    val accent: Color,
    val onAccent: Color,
    val container: Color,
    val onContainer: Color,
    val isError: Boolean,
)

@Composable
private fun ActionRow(
    tone: Tone,
    icon: ImageVector,
    title: String,
    subtitle: String,
    /** Null = quiet story row: same card skeleton, no CTA. [UX-PARKED-STATE-001] */
    primaryLabel: String?,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {},
    /** Single-CTA stories that stack the button FULL-WIDTH below (title + subtitle keep the full row
     *  width, never truncate) — for the alert watch rows whose copy + car name won't fit inline.
     *  [DET-WATCH-HONEST-001]
     *
     *  ⛔ **Ya no es la única razón para bajar, y nunca debió ser la única.** Esto lo pide la FILA
     *  por su subtítulo; que el LABEL quepa lo decide `ctaFitsInline` solo, sin que nadie se acuerde
     *  de pasar nada — que es lo que falló cuando un copy corto se volvió una frase.
     *  [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002] */
    primaryStacksBelow: Boolean = false,
    /**
     * The secondary CTA is the NEGATIVE ANSWER to the title's question, not a second action — so it
     * drops the accent tint and reads as the quiet way out. [DET-ASK-STATE-001]
     *
     * Off by default because the other two-CTA row (the cold start: "mark my spot" / "I'm driving")
     * offers two real actions, and there the shared tone is right — neither is a refusal of the
     * other. This flag names the SEMANTICS, not the colour: only a yes/no answer sets it.
     */
    secondaryIsDecline: Boolean = false,
    /**
     * [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001] Optional whole-card tap, for rows whose content
     * is ABOUT a place the map can show. Null (the default) leaves the card inert, so no existing
     * story grows an invisible affordance by accident.
     */
    onClick: (() -> Unit)? = null,
    /**
     * Esta fila se pinta DENTRO de la card del coche, no como tarjeta suya.
     * [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
     *
     * Cambia tres cosas y ninguna es el contenido: se va su contenedor (la card ya es una), se va
     * su barra de acento (la card ya lleva la suya, del mismo color) y se va el tile (la card ya
     * lleva el glifo del coche). El texto y los botones son LOS MISMOS objetos — no una copia —,
     * que es la única forma de que las dos superficies no se puedan desincronizar.
     */
    embedded: Boolean = false,
    /** El glifo del coche del que habla la noticia, sólo en modo [embedded]: la card ya no pone su
     *  fila de identidad encima. [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] */
    embeddedLeading: (@Composable () -> Unit)? = null,
    /**
     * «Ahora no»: la × del badge. Null = esta fila no se puede callar.
     * [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]
     *
     * ⛔ Se ignora estando [embedded], y no por pereza: dentro de la card la × quedaría junto a
     * los botones de una PREGUNTA, donde ya hay una forma de decir que no — y dos gestos parecidos
     * con mecánicas distintas es peor que no tener el segundo. Lo que se calla es un AVISO, y los
     * avisos viven fuera.
     */
    onSilence: (() -> Unit)? = null,
) {
    // El enfasis es UNA variable, no un contenedor.
    // Dos niveles y nada mas: la fila que PIDE algo ahora se tine (contenedor + borde del acento) y
    // la que solo cuenta lo que pasa va en la tarjeta neutra que usa el resto del sheet.
    //
    // Se fue la BARRA DE ACENTO de 4dp que recorria la tarjeta entera. No decia "importante": decia
    // "esta la escribimos en agosto" - era un septimo lenguaje de contenedor en una pantalla que ya
    // tenia seis, y el color que gastaba competia con el unico verde que debe llamar, el de la
    // accion. [UI-ONE-SKELETON-ONE-EMPHASIS-ONE-GREEN-001]
    val cardColor = if (tone.isError) tone.container else MaterialTheme.colorScheme.surfaceContainerHigh
    val border = if (tone.isError) {
        BorderStroke(BORDER_DP.dp, tone.accent.copy(alpha = ERROR_BORDER_ALPHA))
    } else {
        BorderStroke(PapBorders.thin, MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA))
    }

    val cardModifier = modifier
        .fillMaxWidth()
        // The card is one target that CONTAINS two buttons, so it announces itself as such instead
        // of leaving a screen reader three peers with no hierarchy.
        .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)

    val body: @Composable () -> Unit = {
        // ⛔ LA BARRA DE ACENTO VUELVE. Es el badge de estado que esta superficie llevaba desde que
        // existe, y `b967323b` la perdió por el camino al pasar la card a `PapOutlinedCard` —
        // aquel ticket iba a por los CINCO lenguajes de caja, y se llevó por delante el único que
        // sí significaba algo. [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
        //
        // Lo que dice, y por lo que no es "una caja más": el color es `tone.accent`, o sea la
        // identidad de vigilancia del coche (verde asistido, azul Bluetooth) o el rojo del bloqueo.
        // Sin ella la card entera queda en gris y hay que LEERLA para saber si algo va mal; con
        // ella el estado se ve de un vistazo sin teñir una sola palabra — que es exactamente lo que
        // pide la doctrina de color. [UI-COLOR-DOCTRINE-001]
        //
        // ⛔ Va FUERA del `PapListItem`, no dentro: la fila sigue siendo el esqueleto canónico
        // (tile · título · sublínea · trailing) y la barra es cromo de la CARD. [UI-LIST-ITEM-001]
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            if (!embedded) {
                Box(
                    modifier = Modifier
                        .width(ACCENT_BAR_DP.dp)
                        .fillMaxHeight()
                        .background(tone.accent),
                )
            }
        Column(
            // ⛔ El aire de abajo es de quien PONE el borde. Empotrada, este padding se sumaba al de
            // la card y dejaba 20 dp entre la fila de respuestas y «No voy en mi coche» contra los
            // 12 que separan el texto de sus botones: el hueco más grande de la card caía justo
            // donde menos significa. El hueco lo pone el huésped, que es quien sabe qué viene
            // después. [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (embedded) 0.dp else CONTENT_VERTICAL_DP.dp),
            verticalArrangement = Arrangement.spacedBy(CTA_ROW_GAP_DP.dp),
        ) {
            // ¿Al lado o debajo? UNA pregunta, respondida una vez y leída en los dos sitios que
            // antes la respondían por separado. [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002]
            //
            // Dos razones distintas para bajar, y las dos cuentan:
            //  · [primaryStacksBelow] — la FILA lo pide, por su SUBTÍTULO (78 y 126 chars en las
            //    filas de vigilancia frágil e interrumpida): no sobrevive al recorte de anchura.
            //  · [ctaFitsInline] — el LABEL no cabe, lo pida la fila o no. Esta es la que faltaba:
            //    `home_det_awaiting_cta_primary` mide 25 chars en francés («Marquer mon
            //    stationnement») y se pintaba inline siempre que no hubiera CTA secundario.
            val stacksPrimary = primaryLabel != null &&
                (primaryStacksBelow || !ctaFitsInline(primaryLabel))
            // La MISMA fila que las plazas, las gasolineras y los parkings: tile - titulo - sublinea
            // - trailing. Este bloque tenia su propia anatomia a mano, y era (con la card del coche)
            // uno de los dos unicos sitios del sheet que no usaba el esqueleto que el proyecto
            // declara obligatorio. [UI-LIST-ITEM-001]
            PapListItem(
                title = title,
                subtitle = subtitle,
                titleMaxLines = 2,
                // ⛔ Una línea MÁS cuando la fila lleva ×: la × se come ~48 dp de ancho y visto en el
                // emulador eso bastó para cortar la frase del vigilante muerto («…but your …»),
                // justo el aviso más grave. Si el gesto de callar un aviso se paga con la mitad de
                // la razón por la que existe, el gesto sale caro.
                subtitleMaxLines = if (onSilence != null && !embedded) 3 else 2,
                // ⛔ Empotrada ya NO va sin baldosa. Perdía el tile porque la card ponía encima su
                // fila de identidad con el glifo del coche; desde
                // [HOME-A-CARS-NEWS-RIDES-ITS-OWN-CARD-001] la noticia OCUPA esa fila, así que si
                // tampoco trajera glifo la card no diría de qué coche habla más que repitiendo su
                // nombre dentro de la pregunta. El huésped pasa el glifo de SU coche.
                leading = if (embedded) embeddedLeading else ({ IconTile(icon = icon, tone = tone) }),
                contentPadding = PaddingValues(
                    start = CONTENT_START_DP.dp,
                    end = CONTENT_END_DP.dp,
                    top = CONTENT_VERTICAL_DP.dp,
                    bottom = 0.dp,
                ),
                // Single-CTA states keep the button INLINE on the right (the subtitle wraps to two
                // lines instead of truncating). Filled on error, tonal otherwise. [DET-READY-001h]
                // El trailing puede llevar DOS cosas: el CTA cuando cabe en línea, y la × cuando
                // el aviso se puede callar. Van juntos en una fila — no superpuestos en la esquina —
                // porque una × flotando sobre una cápsula de 40 dp se solapa en cuanto el título
                // ocupa dos líneas. [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]
                trailing = trailingSlot(
                    cta = if (secondaryLabel == null && primaryLabel != null && !stacksPrimary) {
                        {
                            PapCtaPill(
                                label = primaryLabel,
                                container = if (tone.isError) tone.accent else tone.container,
                                content = if (tone.isError) tone.onAccent else tone.onContainer,
                                onClick = onPrimary,
                            )
                        }
                    } else {
                        null
                    },
                    onSilence = if (embedded) null else onSilence,
                ),
            )
            // ⛔ Esta columna SOLO existe si hay algo que apilar. Se emitia siempre, y con ella el
            // `spacedBy` del padre metia su hueco entre la fila y una columna VACIA: sumado al
            // padding inferior, la card de «vigilando» llevaba ~24 dp abajo contra 12 arriba y se
            // leia descentrada. Un contenedor vacio no ocupa nada, pero el ESPACIO entre hermanos si.
            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
            val stacksSomething = stacksPrimary || (secondaryLabel != null && primaryLabel != null)
            if (stacksSomething) {
            Column(
                modifier = Modifier.padding(horizontal = CONTENT_START_DP.dp),
                // Un solo control en el hueco —arriba hay texto, abajo una cápsula—, así que el
                // aire invisible lo pone un lado nada más. [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
                verticalArrangement = Arrangement.spacedBy(
                    PapGap.betweenControls(
                        visible = CTA_ROW_GAP_DP.dp,
                        controlHeight = PapControlHeight.pill,
                        sides = 1,
                    ),
                ),
            ) {
                // Single CTA stacked full-width below: title + subtitle got the full row width above.
                // For the alert watch rows (fragile / interrupted) [DET-WATCH-HONEST-001] — y para
                // cualquier fila cuyo label sea una frase. [UI-A-PILL-IS-FOR-A-LABEL-NOT-A-SENTENCE-002]
                // `primaryLabel != null` lo lleva dentro `stacksPrimary` — el compilador lo dice.
                if (secondaryLabel == null && stacksPrimary) {
                    PapCtaPill(
                        label = primaryLabel,
                        container = if (tone.isError) tone.accent else tone.container,
                        content = if (tone.isError) tone.onAccent else tone.onContainer,
                        onClick = onPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        fillWidth = true,
                    )
                }
                // Las dos acciones, debajo del texto. ¿HOMBRO CON HOMBRO o UNA ENCIMA DE OTRA?
                // Lo decide el presupuesto, no la fila. [UI-A-PILL-PAIR-SPLITS-THE-BUDGET-IN-TWO-001]
                //
                // ⛔ Esta rama era la última maqueta de la pantalla que no preguntaba dónde cabía su
                // label, y la que más falta le hacía: aquí cada cápsula se queda con la MITAD del
                // ancho, así que el presupuesto es el más estrecho de los dos que maneja el sheet.
                // `home_det_awaiting_cta_primary` —que el guard YA declaraba frase, 15 chars en
                // inglés y 25 en francés— entraba igual en la `Row` y salía «Marcar mi apa…» junto a
                // «Voy conducie…»: los dos botones cortados, en los nueve idiomas.
                if (secondaryLabel != null) {
                    // Las mismas dos cápsulas en las dos maquetas — el modifier es lo único que
                    // cambia. Duplicarlas era el camino corto a que la apilada y la de al lado se
                    // tiñan distinto dentro de un mes.
                    val secondaryPill: @Composable (Modifier) -> Unit = { pillModifier ->
                        PapCtaPill(
                            label = secondaryLabel,
                            // A refusal wears no brand colour: green is action, and declining is
                            // not the action we are offering. [UI-COLOR-DOCTRINE-001]
                            container = if (secondaryIsDecline) {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            } else {
                                tone.container
                            },
                            content = if (secondaryIsDecline) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                tone.onContainer
                            },
                            onClick = onSecondary,
                            modifier = pillModifier,
                            fillWidth = true,
                        )
                    }
                    val primaryPill: @Composable (Modifier) -> Unit = { pillModifier ->
                        PapCtaPill(
                            label = primaryLabel,
                            container = tone.accent,
                            content = tone.onAccent,
                            onClick = onPrimary,
                            modifier = pillModifier,
                            fillWidth = true,
                        )
                    }
                    if (ctaPairFitsSideBySide(primaryLabel, secondaryLabel)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP_SM_DP.dp),
                        ) {
                            secondaryPill(Modifier.weight(1f))
                            primaryPill(Modifier.weight(1f))
                        }
                    } else {
                        // Apiladas, la PRIMARIA arriba. En fila la primaria va a la derecha porque
                        // ahí es donde se confirma; en columna es la de arriba, que es donde se lee
                        // primero. El orden no se invierte por gusto: en las dos maquetas la acción
                        // que proponemos ocupa el sitio que se mira antes de decidir.
                        //
                        // ⛔ Y el hueco entre ellas YA NO lo pone el `spacedBy` de la columna de
                        // arriba. Lo ponía, y por eso se veían **20,2 dp** donde la constante decía
                        // 12: con un control pulsable a cada lado, el mínimo táctil de 48 dp añade
                        // `(48 − 40)` de aire invisible que la constante no sabe que existe.
                        // Aquí la pareja pide su hueco en lo que se VE.
                        // [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
                        Column(
                            verticalArrangement = Arrangement.spacedBy(
                                PapGap.betweenControls(
                                    visible = CTA_ROW_GAP_DP.dp,
                                    controlHeight = PapControlHeight.pill,
                                ),
                            ),
                        ) {
                            primaryPill(Modifier.fillMaxWidth())
                            secondaryPill(Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            }
        }
        }
    }

    // El MISMO contenido, dos huéspedes. [HOME-AN-OPEN-QUESTION-IS-THE-CARS-NEWS-001]
    //
    // ⛔ Y es el mismo de verdad, no una copia con el mismo aspecto: el `when (story)` de arriba —
    // con sus ocho ramas, sus textos y sus dos CTAs— se evalúa una sola vez y lo pinta quien
    // corresponda. Reescribir estas filas dentro de la card habría sido el camino corto a que la
    // hoja y la card digan cosas distintas del mismo coche, que es justo el defecto que este
    // ticket viene a quitar.
    if (embedded) {
        body()
    } else {
        PapOutlinedCard(
            modifier = cardModifier,
            containerColor = cardColor,
            border = border,
        ) {
            body()
        }
    }
}

@Composable
private fun IconTile(icon: ImageVector, tone: Tone) {
    // ⛔ Este comentario decia «con la barra de acento retirada...» y la barra VOLVIO
    // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]. El tono vive ahora en TRES
    // sitios que dicen lo mismo a distinta distancia: la barra (de un vistazo), este tile (al mirar
    // la fila) y el CTA (al ir a tocarlo). Error = relleno (fuerte); el resto, un tinte ligero del
    // mismo acento. Tamano y radio los pone el tile canonico. [UI-LIST-ITEM-001]
    PapIconTile(
        icon = icon,
        container = if (tone.isError) tone.accent else tone.accent.copy(alpha = TILE_TINT_ALPHA),
        tint = if (tone.isError) tone.onAccent else tone.accent,
    )
}

/**
 * Lo que va a la derecha del título: el CTA si cabe en línea, la × si el aviso se puede callar, o
 * las dos cosas. Devuelve null cuando no hay ninguna, que es lo que `PapListItem` espera para no
 * reservar hueco. [UI-A-STATUS-BADGE-CAN-BE-DISMISSED-A-QUESTION-CANNOT-001]
 */
private fun trailingSlot(
    cta: (@Composable () -> Unit)?,
    onSilence: (() -> Unit)?,
): (@Composable () -> Unit)? {
    if (cta == null && onSilence == null) return null
    return {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CONTENT_GAP_SM_DP.dp),
        ) {
            cta?.invoke()
            onSilence?.let { silence ->
                IconButton(onClick = silence, modifier = Modifier.size(SILENCE_TOUCH_DP.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        // Dice lo que hace —callar ESTE aviso—, no «cerrar»: lo que se cierra no
                        // existe, y esto sigue existiendo. [COPY-NO-INTERNALS-001]
                        contentDescription = stringResource(Res.string.home_det_notice_silence_cd),
                        // Del tono más callado de la fila: es una salida, no una acción que ofrecer.
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(SILENCE_ICON_DP.dp),
                    )
                }
            }
        }
    }
}

private const val ACCENT_BAR_DP = 4
/** La × : un objetivo táctil honesto (los 24 dp del glifo no lo son) con un glifo discreto dentro. */
private const val SILENCE_TOUCH_DP = 40
private const val SILENCE_ICON_DP = 18
private const val TILE_TINT_ALPHA = 0.16f
private const val CONTENT_START_DP = 14
private const val CONTENT_END_DP = 14
private const val CONTENT_VERTICAL_DP = 12
private const val CONTENT_GAP_SM_DP = 8
private const val CTA_ROW_GAP_DP = 12
private const val BORDER_DP = 1
private const val ERROR_BORDER_ALPHA = 0.6f
// Tonal container for the car-blue info tone — accent at low alpha, same language as the watch badge.
private const val INFO_CONTAINER_ALPHA = 0.16f
