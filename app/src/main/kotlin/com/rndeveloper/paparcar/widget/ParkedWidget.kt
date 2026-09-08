package com.rndeveloper.paparcar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.components.CircleIconButton
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val koin = GlobalContext.get()
        val stateFlow = combine(
            koin.get<UserParkingRepository>().observeActiveSessions(),
            koin.get<VehicleRepository>().observeVehicles(),
        ) { sessions, vehicles -> resolveParkedWidgetState(sessions, vehicles) }
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
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(CORNER_RADIUS_DP.dp)
                .clickable(actionStartActivity(openIntent(context, state)))
                // Asymmetric on purpose: the text keeps its 16dp inset; the action icons hug the
                // right edge (their 40dp boxes already carry inner air). [WIDGET-ACTIONS-002]
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = END_PADDING_DP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                is ParkedWidgetState.Empty -> Empty()
                is ParkedWidgetState.Parked -> Parked(state)
            }
        }
    }

    @Composable
    private fun Empty() {
        val context = LocalContext.current
        Text(
            text = context.getString(R.string.widget_empty_title),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = TITLE_SP.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = context.getString(R.string.widget_empty_hint),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = BODY_SP.sp),
            maxLines = 1,
        )
    }

    @Composable
    private fun Parked(state: ParkedWidgetState.Parked) {
        val context = LocalContext.current
        // fillMaxWidth is what pins the action icons to the card's right edge — a wrap-width Row
        // would leave them floating mid-card however the weights fall.
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark()
                    Spacer(GlanceModifier.width(BRAND_GAP_DP.dp))
                    Text(
                        text = state.vehicleName,
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurface,
                            fontSize = TITLE_SP.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                        maxLines = 1,
                    )
                    if (state.othersCount > 0) {
                        Spacer(GlanceModifier.width(6.dp))
                        Text(
                            text = context.getString(R.string.widget_more_parked, state.othersCount),
                            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = BODY_SP.sp),
                            maxLines = 1,
                        )
                    }
                }
                Spacer(GlanceModifier.height(2.dp))
                val street = state.street ?: context.getString(R.string.widget_location_pending)
                val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.parkedAtMs))
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
                onClick = actionStartActivity(navigateIntent(state)),
                backgroundColor = null,
                contentColor = GlanceTheme.colors.onSurfaceVariant,
                modifier = GlanceModifier.size(ACTION_BUTTON_DP.dp),
            )
            CircleIconButton(
                imageProvider = ImageProvider(R.drawable.ic_widget_more),
                contentDescription = context.getString(R.string.widget_action_more),
                onClick = actionStartActivity(showParkingIntent(context, state)),
                backgroundColor = null,
                contentColor = GlanceTheme.colors.onSurfaceVariant,
                modifier = GlanceModifier.size(ACTION_BUTTON_DP.dp),
            )
        }
    }

    /** The brand's monochrome glyph, tinted like an icon — present, not shouting. */
    @Composable
    private fun BrandMark() {
        Image(
            provider = ImageProvider(R.drawable.ic_notification_logo),
            contentDescription = null,
            colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
            modifier = GlanceModifier.size(BRAND_MARK_DP.dp),
        )
    }

    /** Straight to the maps app — read-only, no need to pass through Paparcar. */
    private fun navigateIntent(state: ParkedWidgetState.Parked): Intent =
        Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("geo:${state.latitude},${state.longitude}?q=${state.latitude},${state.longitude}"),
        )

    /** "More" → the app WITH the vehicle's card selected: every verb lives there. */
    private fun showParkingIntent(context: Context, state: ParkedWidgetState.Parked): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SHOW_PARKING_SESSION_ID, state.sessionId)
            putExtra(MainActivity.EXTRA_FOCUS_LAT, state.latitude)
            putExtra(MainActivity.EXTRA_FOCUS_LON, state.longitude)
        }

    /** Parked → open the map centred on the pin; empty → just open the app. Same extras contract
     *  as the notifications' focus intent. */
    private fun openIntent(context: Context, state: ParkedWidgetState): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (state is ParkedWidgetState.Parked) {
                putExtra(MainActivity.EXTRA_FOCUS_LAT, state.latitude)
                putExtra(MainActivity.EXTRA_FOCUS_LON, state.longitude)
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
    }
}

class ParkedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ParkedWidget()
}
