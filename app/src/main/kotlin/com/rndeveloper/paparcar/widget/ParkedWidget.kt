package com.rndeveloper.paparcar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.rndeveloper.paparcar.MainActivity
import com.rndeveloper.paparcar.R
import com.rndeveloper.paparcar.domain.detection.DetectionPhase
import com.rndeveloper.paparcar.domain.detection.DetectionRuntimeState
import com.rndeveloper.paparcar.domain.repository.UserParkingRepository
import com.rndeveloper.paparcar.domain.repository.VehicleRepository
import com.rndeveloper.paparcar.presentation.widget.ParkedWidgetState
import com.rndeveloper.paparcar.presentation.widget.resolveParkedWidgetState
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.koin.core.context.GlobalContext
import java.text.DateFormat
import java.util.Date

/**
 * Home-screen widget: which car is parked, where, since when — plus two QUIET actions on the
 * right edge [WIDGET-ACTIONS-002]: the SAME directions glyph the in-app parking peek uses (straight
 * to the maps app), and a three-dot "more" that opens the app with the vehicle's card selected —
 * every other verb (leave, edit) lives THERE with its own confirmations, so the launcher never
 * holds a one-tap irreversible action. Tapping the rest of the card opens the map on the pin.
 *
 * WHAT to show is decided by [resolveParkedWidgetState] in commonMain; this class only paints.
 *
 * Refresh model: content collects the Room flows while the composition lives;
 * [ParkedWidgetRefresher] pushes an update on every state change from our own process; and the
 * provider's `updatePeriodMillis` re-renders after process death. The parked time is shown as the
 * ABSOLUTE hour on purpose — a relative "2 h ago" frozen between refreshes lies, a clock time
 * never does.
 */
class ParkedWidget : GlanceAppWidget() {

    /**
     * [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001 D5] The provider has always declared
     * `resizeMode="horizontal|vertical"`, so a user could already stretch this widget — and got
     * blank space for it, because the content painted one row whatever the height. Two buckets: the
     * 3x1 it ships as, and a taller one that lists a row per parked car instead of collapsing them
     * into "+N". Only reachable with more than one vehicle, which is a Plus situation
     * [FREE-LIMITS-001], so the short layout stays the one everybody sees.
     */
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT_SIZE, TALL_SIZE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = GlobalContext.get()
        // The live trip is NOT in Room — it lives in the detection runtime, in this same process.
        // Without it a trip reads as Empty and the widget asks you to mark the car you are driving.
        // [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001]
        val runtime = koin.get<DetectionRuntimeState>()
        val stateFlow = combine(
            koin.get<UserParkingRepository>().observeActiveSessions(),
            koin.get<VehicleRepository>().observeVehicles(),
            runtime.isRunning,
            runtime.trip,
            runtime.phase,
        ) { sessions, vehicles, isRunning, trip, phase ->
            resolveParkedWidgetState(
                sessions = sessions,
                vehicles = vehicles,
                isDriving = isRunning,
                drivingVehicleId = trip?.departingVehicleId,
                isCandidate = phase == DetectionPhase.Candidate,
            )
        }
        // First value resolved before composing so a re-render never flashes the empty state.
        val initial = stateFlow.first()
        provideContent {
            val state by stateFlow.collectAsState(initial)
            GlanceTheme {
                WidgetContent(state)
            }
        }
    }

    @Composable
    private fun WidgetContent(state: ParkedWidgetState) {
        val context = LocalContext.current
        // A tall widget lists the cars; the short one keeps its single row. Read from the size the
        // launcher actually gave us, not from the bucket we asked for — and read HERE, above the
        // Column, because the alignment depends on it too.
        val height = LocalSize.current.height
        val tall = height >= TALL_SIZE.height
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(CORNER_RADIUS_DP.dp)
                .clickable(actionStartActivity(openIntent(context, state)))
                // Asymmetric on purpose: the text keeps its 16dp inset; the action icons hug the
                // right edge (their 40dp boxes already carry inner air). [WIDGET-ACTIONS-002]
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = END_PADDING_DP.dp),
            // One row centres in its box; a LIST hangs from the top. Centring the list left two
            // bands of dead air as tall as the rows themselves — caught on device, not by a test:
            // the rows, their order and their per-car actions were all green while it looked wrong.
            // [WIDGET-A-TALL-WIDGET-HANGS-FROM-THE-TOP-001]
            verticalAlignment = if (tall) Alignment.Top else Alignment.CenterVertically,
        ) {
            when (state) {
                is ParkedWidgetState.Empty -> Empty()
                is ParkedWidgetState.Driving -> Driving(state, tall, height)
                is ParkedWidgetState.Parked -> Parked(state, tall, height)
            }
        }
    }

    /**
     * How many car rows fit in [height]. A `Column` does not scroll, so without this the rows past
     * the bottom edge would be silently cut — and a widget that hides a car is worse than one that
     * says "+1 more", which is exactly what the leftovers fall back to.
     * [WIDGET-A-TALL-WIDGET-HANGS-FROM-THE-TOP-001]
     */
    private fun rowsThatFit(height: androidx.compose.ui.unit.Dp): Int =
        (((height.value - VERTICAL_PADDING_DP) / ROW_BLOCK_DP).toInt()).coerceAtLeast(1)

    @Composable
    private fun Empty() {
        val context = LocalContext.current
        TitleAndHint(
            title = context.getString(R.string.widget_empty_title),
            hint = context.getString(R.string.widget_empty_hint),
        )
    }

    /**
     * A trip in progress. [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001]
     *
     * Same anatomy as [Empty] — brand mark, one line of fact, one line of hint. No route glyph:
     * `RemoteViews` cannot animate, so a second glyph sólo competiría con la marca para decir lo
     * que la línea ya dice con palabras.
     *
     * ⛔ **Aquí decía «no action buttons», y era verdad a medias.** El argumento —«este estado no
     * tiene sesión, así que los dos verbos de [Parked] no apuntan a nada»— sigue siendo cierto
     * PARA ESOS DOS: no hay pin al que navegar ni ficha que abrir. Lo que no se siguió de ahí es
     * que no hubiera NINGÚN verbo: el que falta no mira al pasado, mira al presente — **«ya he
     * aparcado»**, que es exactamente lo que el conductor sabe y la app todavía no.
     * [WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001]
     *
     * Y es el MISMO destino que ya ofrece la notificación de «¿sigues aparcado?»
     * (`EXTRA_START_ADD_PARKING`), no un camino nuevo: el widget ofrece lo que la app ya sabe hacer.
     */
    @Composable
    private fun Driving(state: ParkedWidgetState.Driving, tall: Boolean, height: androidx.compose.ui.unit.Dp) {
        val context = LocalContext.current
        val phase = if (state.isCandidate) {
            context.getString(R.string.widget_driving_parking)
        } else {
            state.vehicleName
                ?.let { context.getString(R.string.widget_driving_title, it) }
                ?: context.getString(R.string.widget_driving_title_unknown)
        }
        // With a second car parked, a tall widget keeps showing it: driving one vehicle is no
        // reason to hide where the other one is. The short layout has no room and the trip wins.
        // [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001 D5]
        // The trip line already took one row's worth of the height.
        val alsoShown = if (tall) state.alsoParked.take(rowsThatFit(height) - 1) else emptyList()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                TitleAndHint(
                    title = phase,
                    hint = context.getString(R.string.widget_driving_hint),
                    showBrand = alsoShown.isEmpty(),
                )
            }
            // Los DOS verbos de un viaje, y son los dos que el conductor puede saber antes que la
            // app: **ya he aparcado** (se adelanta a la detección) y **no voy en mi coche** (la
            // desmiente). [WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001]
            //
            // El orden no es casual: primero el que CONSTRUYE —confirmar un aparcamiento es lo que
            // el usuario hace nueve de cada diez veces— y después el que DESHACE. Los dos callados,
            // con el mismo lenguaje que los de [Parked]: icono sin relleno, a la derecha.
            Spacer(GlanceModifier.width(ACTION_GAP_DP.dp))
            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.ic_widget_mark_parking),
                contentDescription = context.getString(R.string.widget_action_mark_parking),
                onClick = actionStartActivity(addParkingIntent(context)),
                backgroundColor = null,
                contentColor = GlanceTheme.colors.onSurfaceVariant,
                modifier = GlanceModifier.size(ACTION_BUTTON_DP.dp),
            )
            // ⛔ Éste NO abre la app: es un COMANDO. Ver [StopTripFromWidgetAction] para por qué, y
            // para por qué despacha el mismo `ACTION_USER_STOP` que la tarjeta y la notificación en
            // vez de inventarse un camino propio.
            Spacer(GlanceModifier.width(ACTION_GAP_DP.dp))
            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.ic_widget_not_my_car),
                contentDescription = context.getString(R.string.widget_action_not_my_car),
                onClick = actionRunCallback<StopTripFromWidgetAction>(),
                backgroundColor = null,
                contentColor = GlanceTheme.colors.onSurfaceVariant,
                modifier = GlanceModifier.size(ACTION_BUTTON_DP.dp),
            )
        }
        alsoShown.forEach { car ->
            Spacer(GlanceModifier.height(ROW_GAP_DP.dp))
            CarRow(car = car, othersCount = 0, showBrand = false)
        }
    }

    /**
     * The shape both action-less states share: the brand mark, the fact, and one quiet line under
     * it. The mark is here — and not only in [Parked] — because a freshly pinned widget with no
     * parking yet used to be an anonymous grey rectangle on the launcher, at the exact moment the
     * user has to recognise what they just placed there.
     */
    @Composable
    private fun TitleAndHint(title: String, hint: String, showBrand: Boolean = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showBrand) {
                BrandMark()
                Spacer(GlanceModifier.width(BRAND_GAP_DP.dp))
            }
            Text(
                text = title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = TITLE_SP.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = hint,
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = BODY_SP.sp),
            maxLines = 1,
        )
    }

    /**
     * Parked cars. Short → the subject only, with the rest as "+N more"; tall → a row per car, in
     * the order the projection already put them (the app's own preference, so the widget's first
     * row and Home's first chip are the same car). [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001 D5]
     */
    @Composable
    private fun Parked(state: ParkedWidgetState.Parked, tall: Boolean, height: androidx.compose.ui.unit.Dp) {
        if (!tall) {
            CarRow(car = state.subject, othersCount = state.othersCount, showBrand = true)
            return
        }
        // Only the rows that actually fit; whatever is left keeps being counted, never dropped.
        val visible = state.cars.take(rowsThatFit(height))
        val hidden = state.cars.size - visible.size
        visible.forEachIndexed { index, car ->
            if (index > 0) Spacer(GlanceModifier.height(ROW_GAP_DP.dp))
            CarRow(
                car = car,
                othersCount = if (index == 0) hidden else 0,
                showBrand = index == 0 && visible.size == 1,
            )
        }
    }

    /**
     * One parked car: name (+ "+N more" when a short widget is collapsing the rest), street and
     * time, and its OWN actions on the right edge.
     *
     * Both verbs are per-car by construction — directions needs this car's coordinates and "more"
     * needs this car's session — so a tall widget cannot offer an action that belongs to a
     * different vehicle. Neither is irreversible: the launcher never holds a one-tap "I'm leaving".
     * [WIDGET-ACTIONS-002]
     */
    @Composable
    private fun CarRow(car: ParkedWidgetState.ParkedCar, othersCount: Int, showBrand: Boolean) {
        val context = LocalContext.current
        // fillMaxWidth is what pins the action icons to the card's right edge — a wrap-width Row
        // would leave them floating mid-card however the weights fall.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showBrand) {
                        BrandMark()
                        Spacer(GlanceModifier.width(BRAND_GAP_DP.dp))
                    }
                    Text(
                        text = car.vehicleName,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = TITLE_SP.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                    if (othersCount > 0) {
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = context.getString(R.string.widget_more_parked, othersCount),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = BODY_SP.sp),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
                val street = car.street ?: context.getString(R.string.widget_location_pending)
                val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(car.parkedAtMs))
                Text(
                    text = "$street · $time",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = BODY_SP.sp),
                    maxLines = 1,
                )
            }
            // Right-edge quiet actions: borderless icon buttons, never a filled pill.
            Spacer(GlanceModifier.width(ACTION_GAP_DP.dp))
            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.ic_widget_directions),
                contentDescription = context.getString(R.string.widget_action_navigate),
                onClick = actionStartActivity(navigateIntent(car)),
                backgroundColor = null,
                contentColor = GlanceTheme.colors.onSurfaceVariant,
                modifier = GlanceModifier.size(ACTION_BUTTON_DP.dp),
            )
            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.ic_widget_more),
                contentDescription = context.getString(R.string.widget_action_more),
                onClick = actionStartActivity(showParkingIntent(context, car)),
                backgroundColor = null,
                contentColor = GlanceTheme.colors.onSurfaceVariant,
                modifier = GlanceModifier.size(ACTION_BUTTON_DP.dp),
            )
        }
    }

    /**
     * The brand's monochrome glyph, tinted like an icon — present, not shouting.
     *
     * **Se pinta solo cuando el widget muestra UN bloque.** Con una lista desaparece, y no por
     * ahorro: la marca va delante del primer nombre, así que con varias filas sangraba la primera y
     * dejaba las demás pegadas al borde — una lista con un escalón. Repetirla en cada coche la
     * convertiría en un badge del vehículo, que es lo contrario de lo que es; y reservarle su hueco
     * en todas las filas le quitaría ancho a la calle, que **ya se trunca hoy** con dos coches.
     *
     * En la práctica sigue viéndose casi siempre: el plan gratis es un coche [FREE-LIMITS-001], así
     * que solo falta en el caso Plus con el widget estirado a propósito — donde el contenido ya dice
     * de sobra de quién es. [WIDGET-THE-MARK-ACCOMPANIES-ONE-FACT-001]
     */
    @Composable
    private fun BrandMark() {
        Image(
            provider = ImageProvider(R.drawable.ic_notification_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(BRAND_MARK_DP.dp),
        )
    }

    /** Straight to the maps app — read-only, no need to pass through Paparcar. Per CAR, so a tall
     *  widget's second row navigates to the second car and not to the subject. */
    private fun navigateIntent(car: ParkedWidgetState.ParkedCar): Intent =
        Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("geo:${car.latitude},${car.longitude}?q=${car.latitude},${car.longitude}"),
        )

    /** "More" → the app WITH that car's session selected: every verb lives there. */
    private fun showParkingIntent(context: Context, car: ParkedWidgetState.ParkedCar): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SHOW_PARKING_SESSION_ID, car.sessionId)
            putExtra(MainActivity.EXTRA_FOCUS_LAT, car.latitude)
            putExtra(MainActivity.EXTRA_FOCUS_LON, car.longitude)
        }

    /**
     * «Ya he aparcado» desde el widget: abre la app directamente en el marcado manual.
     * [WIDGET-A-TRIP-CAN-BE-ANSWERED-FROM-THE-LAUNCHER-001]
     *
     * ⛔ **Los mismos extras que la notificación**, incluido `fromDetection = false`: este pin lo
     * pone una persona, no lo nomina una detección, y su procedencia tiene que decirlo — es lo que
     * distingue un pin manual de uno detectado en los diagnósticos.
     * [DET-NUDGE-PIN-PROVENANCE-001]
     */
    private fun addParkingIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_START_ADD_PARKING, true)
            putExtra(MainActivity.EXTRA_ADD_PARKING_FROM_DETECTION, false)
        }

    /** Parked → open the map centred on the pin; empty → just open the app. Same extras contract
     *  as the notifications' focus intent. */
    private fun openIntent(context: Context, state: ParkedWidgetState): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (state is ParkedWidgetState.Parked) {
                putExtra(MainActivity.EXTRA_FOCUS_LAT, state.subject.latitude)
                putExtra(MainActivity.EXTRA_FOCUS_LON, state.subject.longitude)
            }
        }

    private companion object {
        const val CORNER_RADIUS_DP = 16
        const val TITLE_SP = 15
        const val BODY_SP = 13
        const val BRAND_MARK_DP = 16
        const val BRAND_GAP_DP = 6
        const val ACTION_GAP_DP = 4
        const val ACTION_BUTTON_DP = 40
        const val END_PADDING_DP = 4
        const val ROW_GAP_DP = 10
        // One car row + its gap: title (15sp) over body (13sp) plus the 40dp action buttons that
        // set the real floor. Used to decide how many rows FIT, so none is silently cut.
        const val ROW_BLOCK_DP = 50f
        const val VERTICAL_PADDING_DP = 24f // 12 top + 12 bottom
        // The two size buckets. COMPACT mirrors the provider's 3x1 target.
        //
        // TALL was 110dp and that was measured wrong: on the emulator (density 480) the 3x1 widget
        // draws 108dp tall, so the threshold sat TWO dp above the layout it must never trigger --
        // any launcher with a slightly taller cell would have served the list to a one-cell widget.
        // 150dp sits clear of a single cell (~100-140dp across devices) and below any two-cell one.
        // [WIDGET-A-TALL-WIDGET-HANGS-FROM-THE-TOP-001]
        val COMPACT_SIZE = DpSize(180.dp, 40.dp)
        val TALL_SIZE = DpSize(180.dp, 150.dp)
    }
}

class ParkedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ParkedWidget()
}
