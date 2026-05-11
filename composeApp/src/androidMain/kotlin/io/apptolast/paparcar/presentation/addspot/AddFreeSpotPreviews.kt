package io.apptolast.paparcar.presentation.addspot

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.apptolast.paparcar.ui.theme.PaparcarTheme

@Preview(name = "AddFreeSpot — idle · Claro", showBackground = true)
@Composable
private fun AddFreeSpotIdleLightPreview() {
    PaparcarTheme(darkTheme = false) {
        AddFreeSpotContent(
            state = AddFreeSpotState(),
        )
    }
}

@Preview(name = "AddFreeSpot — idle · Oscuro", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AddFreeSpotIdleDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        AddFreeSpotContent(
            state = AddFreeSpotState(),
        )
    }
}

@Preview(name = "AddFreeSpot — enviando", showBackground = true)
@Composable
private fun AddFreeSpotReportingPreview() {
    PaparcarTheme(darkTheme = false) {
        AddFreeSpotContent(
            state = AddFreeSpotState(isReporting = true),
        )
    }
}
