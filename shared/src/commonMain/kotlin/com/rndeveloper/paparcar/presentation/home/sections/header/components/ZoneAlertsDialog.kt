package com.rndeveloper.paparcar.presentation.home.sections.header.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.presentation.home.HomeIntent
import com.rndeveloper.paparcar.presentation.util.zoneIconFor
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_zone_alerts_done
import paparcar.composeapp.generated.resources.home_zone_alerts_empty
import paparcar.composeapp.generated.resources.home_zone_alerts_title
import paparcar.composeapp.generated.resources.settings_notif_zone_alerts_desc

/**
 * The bell's alert manager (Plus only — the free tap already landed on the paywall):
 * one switch per saved zone, writing [Zone.notifyOnSpot] through the same persistence
 * path as the zone form. Public so the Dev Catalog gallery can render it. [MONEY-001]
 */
@Composable
fun ZoneAlertsDialog(
    zones: List<Zone>,
    onIntent: (HomeIntent) -> Unit,
) {
    PapAlertDialog(
        onDismiss = { onIntent(HomeIntent.CloseZoneAlerts) },
        icon = Icons.Rounded.Notifications,
        title = stringResource(Res.string.home_zone_alerts_title),
        body = stringResource(Res.string.settings_notif_zone_alerts_desc),
        primaryLabel = stringResource(Res.string.home_zone_alerts_done),
        onPrimary = { onIntent(HomeIntent.CloseZoneAlerts) },
        content = {
            if (zones.isEmpty()) {
                Text(
                    text = stringResource(Res.string.home_zone_alerts_empty),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                zones.forEach { zone ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            imageVector = zoneIconFor(zone.iconKey),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(ZONE_ROW_ICON_DP.dp),
                        )
                        Text(
                            text = zone.name,
                            style = PaparcarType.current.rowName,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(
                            checked = zone.notifyOnSpot,
                            onCheckedChange = { enabled ->
                                onIntent(HomeIntent.ToggleZoneAlert(zone.id, enabled))
                            },
                        )
                    }
                }
            }
        },
    )
}

private const val ZONE_ROW_ICON_DP = 18
