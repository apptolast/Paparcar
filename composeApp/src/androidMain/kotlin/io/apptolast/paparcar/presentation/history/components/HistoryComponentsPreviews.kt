@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.history.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.history.WeekDayStats
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ─── WeeklyActivityCard ───────────────────────────────────────────────────────

@Preview(name = "WeeklyActivityCard (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WeeklyActivityCardDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            WeeklyActivityCard(data = FakeData.weeklyStats)
        }
    }
}

@Preview(name = "WeeklyActivityCard (claro)", showBackground = true)
@Composable
private fun WeeklyActivityCardLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            WeeklyActivityCard(data = FakeData.weeklyStats)
        }
    }
}

@Preview(name = "WeeklyActivityCard — vacío (claro)", showBackground = true)
@Composable
private fun WeeklyActivityCardEmptyPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            WeeklyActivityCard(data = FakeData.weeklyStatsEmpty)
        }
    }
}

// ─── StatsRow ─────────────────────────────────────────────────────────────────

@Preview(name = "StatsRow (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun StatsRowDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            StatsRow(sessions = FakeData.allSessions)
        }
    }
}

@Preview(name = "StatsRow (claro)", showBackground = true)
@Composable
private fun StatsRowLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            StatsRow(sessions = FakeData.allSessions)
        }
    }
}

// ─── Opción 1 — Solo color (hero card, solo primaryContainer) ─────────────────

@Preview(name = "Opción 1 — Solo color (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Option1DarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            ActiveSectionHeader("Aparcado actualmente")
            Spacer(Modifier.height(2.dp))
            ActiveSessionHeroCard(session = FakeData.activeSession, onViewOnMap = { _, _ -> })
        }
    }
}

@Preview(name = "Opción 1 — Solo color (claro)", showBackground = true)
@Composable
private fun Option1LightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            ActiveSectionHeader("Aparcado actualmente")
            Spacer(Modifier.height(2.dp))
            ActiveSessionHeroCard(session = FakeData.activeSessionSupermarket, onViewOnMap = { _, _ -> })
        }
    }
}

// ─── Opción 2 — Strip lateral ─────────────────────────────────────────────────

@Preview(name = "Opción 2 — Strip lateral (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Option2DarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            ActiveSectionHeader("Aparcado actualmente")
            Spacer(Modifier.height(2.dp))
            ActiveSessionStripCard(session = FakeData.activeSession, onViewOnMap = { _, _ -> })
        }
    }
}

@Preview(name = "Opción 2 — Strip lateral (claro)", showBackground = true)
@Composable
private fun Option2LightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            ActiveSectionHeader("Aparcado actualmente")
            Spacer(Modifier.height(2.dp))
            ActiveSessionStripCard(session = FakeData.activeSessionSupermarket, onViewOnMap = { _, _ -> })
        }
    }
}

// ─── Opción 3 — Strip lateral en timeline (diseño actual) ────────────────────

@Preview(name = "Opción 3 — Strip en timeline (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Option3DarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            ActiveSectionHeader("Aparcado actualmente")
            EndedSessionTimelineNode(
                session = FakeData.activeSession,
                isLast = false,
                isActive = true,
                onViewOnMap = { _, _ -> },
            )
            EndedSessionTimelineNode(
                session = FakeData.endedSessions[0],
                isLast = false,
                onViewOnMap = { _, _ -> },
            )
            EndedSessionTimelineNode(
                session = FakeData.endedSessions[1],
                isLast = true,
                onViewOnMap = { _, _ -> },
            )
        }
    }
}

@Preview(name = "Opción 3 — Strip en timeline (claro)", showBackground = true)
@Composable
private fun Option3LightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            ActiveSectionHeader("Aparcado actualmente")
            EndedSessionTimelineNode(
                session = FakeData.activeSessionSupermarket,
                isLast = false,
                isActive = true,
                onViewOnMap = { _, _ -> },
            )
            EndedSessionTimelineNode(
                session = FakeData.endedSessions[2],
                isLast = false,
                onViewOnMap = { _, _ -> },
            )
            EndedSessionTimelineNode(
                session = FakeData.endedSessions[3],
                isLast = true,
                onViewOnMap = { _, _ -> },
            )
        }
    }
}

// ─── EndedSessionTimelineNode ─────────────────────────────────────────────────

@Preview(name = "EndedSessionTimelineNode — con POI (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EndedTimelineNodeDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            EndedSessionTimelineNode(
                session = FakeData.endedSessions[0],
                isLast = false,
                onViewOnMap = { _, _ -> },
            )
            EndedSessionTimelineNode(
                session = FakeData.endedSessions[1],
                isLast = true,
                onViewOnMap = { _, _ -> },
            )
        }
    }
}

@Preview(name = "EndedSessionTimelineNode — con POI (claro)", showBackground = true)
@Composable
private fun EndedTimelineNodeLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            EndedSessionTimelineNode(
                session = FakeData.endedSessions[2],
                isLast = false,
                onViewOnMap = { _, _ -> },
            )
            EndedSessionTimelineNode(
                session = FakeData.endedSessions[3],
                isLast = true,
                onViewOnMap = { _, _ -> },
            )
        }
    }
}

// ─── DayHeaderRow ─────────────────────────────────────────────────────────────

@Preview(name = "DayHeaderRow (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DayHeaderDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Column(Modifier.padding(16.dp)) {
            DayHeaderRow("Hoy")
            DayHeaderRow("Ayer")
            DayHeaderRow("12 mar 2025")
        }
    }
}

// ─── EmptyHistoryState ────────────────────────────────────────────────────────

@Preview(name = "EmptyHistoryState (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun EmptyHistoryDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        EmptyHistoryState()
    }
}

@Preview(name = "EmptyHistoryState (claro)", showBackground = true)
@Composable
private fun EmptyHistoryLightPreview() {
    PaparcarTheme(darkTheme = false) {
        EmptyHistoryState()
    }
}
