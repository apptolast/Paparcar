package io.apptolast.paparcar.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ── Shared tabs (Vehicles + Settings never change) ───────────────────────────

private val tabVehicles = AppBottomNavItem(
    route = "vehicles",
    label = { "Vehicles" },
    iconFilled = Icons.Filled.DirectionsCar,
    iconOutline = Icons.Outlined.DirectionsCar,
)

private val tabSettings = AppBottomNavItem(
    route = "settings",
    label = { "Settings" },
    iconFilled = Icons.Filled.Settings,
    iconOutline = Icons.Outlined.Settings,
)

// ── Option A — Spots + NearMe ─────────────────────────────────────────────────

private val tabSpots = AppBottomNavItem(
    route = "home",
    label = { "Spots" },
    iconFilled = Icons.Filled.NearMe,
    iconOutline = Icons.Outlined.NearMe,
)

// ── Option B — Nearby + Explore ───────────────────────────────────────────────

private val tabNearby = AppBottomNavItem(
    route = "home",
    label = { "Nearby" },
    iconFilled = Icons.Filled.Explore,
    iconOutline = Icons.Outlined.Explore,
)

// ── Option C — Radar + Radar ──────────────────────────────────────────────────

private val tabRadar = AppBottomNavItem(
    route = "home",
    label = { "Radar" },
    iconFilled = Icons.Filled.Radar,
    iconOutline = Icons.Outlined.Radar,
)

// ── Preview helpers ───────────────────────────────────────────────────────────

@Composable
private fun AllOptionsPreview() {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        OptionRow(label = "A — Spots + NearMe", firstTab = tabSpots)
        Spacer(Modifier.height(4.dp))
        OptionRow(label = "B — Nearby + Explore", firstTab = tabNearby)
        Spacer(Modifier.height(4.dp))
        OptionRow(label = "C — Radar + Radar", firstTab = tabRadar)
    }
}

@Composable
private fun OptionRow(label: String, firstTab: AppBottomNavItem) {
    val items = listOf(firstTab, tabVehicles, tabSettings)
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 2.dp),
        )
        AppBottomNavigation(
            items = items,
            currentRoute = "home",
            onNavigate = {},
        )
    }
}

// ── Light ─────────────────────────────────────────────────────────────────────

@Preview(name = "Nav tab options — Light", showBackground = true, widthDp = 400)
@Composable
private fun NavTabOptionsLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Surface { AllOptionsPreview() }
    }
}

// ── Dark ──────────────────────────────────────────────────────────────────────

@Preview(
    name = "Nav tab options — Dark",
    showBackground = true,
    widthDp = 400,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun NavTabOptionsDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Surface { AllOptionsPreview() }
    }
}
