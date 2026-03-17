@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.apptolast.paparcar.presentation.history

import android.content.res.Configuration
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.preview.FakeData
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ─── Full-screen HistoryContent previews ──────────────────────────────────────

@Preview(name = "History — con datos (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryDataDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HistoryContent(
            state = HistoryState(
                sessions = FakeData.allSessions,
            ),
            contentPadding = PaddingValues(0.dp),
            onViewOnMap = { _, _ -> },
            onRefresh = {},
        )
    }
}

@Preview(name = "History — con datos (claro)", showBackground = true)
@Composable
private fun HistoryDataLightPreview() {
    PaparcarTheme(darkTheme = false) {
        HistoryContent(
            state = HistoryState(
                sessions = FakeData.allSessions,
            ),
            contentPadding = PaddingValues(0.dp),
            onViewOnMap = { _, _ -> },
            onRefresh = {},
        )
    }
}

@Preview(name = "History — solo finalizadas (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryEndedOnlyDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HistoryContent(
            state = HistoryState(
                sessions = FakeData.endedSessions,
            ),
            contentPadding = PaddingValues(0.dp),
            onViewOnMap = { _, _ -> },
            onRefresh = {},
        )
    }
}

@Preview(name = "History — cargando (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryLoadingDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HistoryContent(
            state = HistoryState(isLoading = true),
            contentPadding = PaddingValues(0.dp),
            onViewOnMap = { _, _ -> },
            onRefresh = {},
        )
    }
}

@Preview(name = "History — vacío (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryEmptyDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        HistoryContent(
            state = HistoryState(),
            contentPadding = PaddingValues(0.dp),
            onViewOnMap = { _, _ -> },
            onRefresh = {},
        )
    }
}

@Preview(name = "History — vacío (claro)", showBackground = true)
@Composable
private fun HistoryEmptyLightPreview() {
    PaparcarTheme(darkTheme = false) {
        HistoryContent(
            state = HistoryState(),
            contentPadding = PaddingValues(0.dp),
            onViewOnMap = { _, _ -> },
            onRefresh = {},
        )
    }
}
