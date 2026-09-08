package com.rndeveloper.paparcar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
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
 * Home-screen widget: which car is parked, where, since when — tap opens the map centred on the
 * pin (same [MainActivity.EXTRA_FOCUS_LAT]/[MainActivity.EXTRA_FOCUS_LON] contract the
 * notifications use). WHAT to show is decided by [resolveParkedWidgetState] in commonMain; this
 * class only paints. [WIDGET-PARKED-001]
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
                .padding(16.dp),
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
        Row(verticalAlignment = Alignment.CenterVertically) {
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
    }
}

class ParkedWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ParkedWidget()
}
