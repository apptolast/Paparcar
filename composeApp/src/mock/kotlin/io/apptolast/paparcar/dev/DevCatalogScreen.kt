package io.apptolast.paparcar.dev

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import io.apptolast.paparcar.domain.detection.MutableDetectionRuntimeState
import io.apptolast.paparcar.fakes.MockScenario
import org.koin.compose.koinInject

/**
 * Mock-only launcher. Lets you pick a session/permission scenario and enter the **real** app
 * graph with it (the fakes read [MockScenario] so routing flows naturally), plus one-tap presets
 * for the common flows. Phase 2 will add a static state gallery below.
 */
@Composable
fun DevCatalogScreen(
    scenario: MockScenario,
    onEnter: () -> Unit,
    onOpenGallery: () -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val session by scenario.session.collectAsStateWithLifecycle()
    val onboarding by scenario.onboardingCompleted.collectAsStateWithLifecycle()
    val tier by scenario.permissionTier.collectAsStateWithLifecycle()
    val gps by scenario.gpsEnabled.collectAsStateWithLifecycle()
    val online by scenario.online.collectAsStateWithLifecycle()
    // Shared detection runtime — toggling it simulates an in-progress trip in the real Home (moving
    // driving puck + "Conduciendo" chip + camera follow), no real drive needed. [DRIVE-SIM-001]
    val runtime: MutableDetectionRuntimeState = koinInject()
    val driving by runtime.isRunning.collectAsStateWithLifecycle()
    val candidate by runtime.phase.collectAsStateWithLifecycle()

    Scaffold(containerColor = cs.surface) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "Paparcar · Dev Catalog (mock)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = cs.onSurface,
            )
            Text(
                "Variante mock — sin Firebase ni OAuth. Elige un escenario y entra al grafo real.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )

            SectionTitle("Sesión")
            EnumChips(
                values = MockScenario.Session.entries,
                selected = session,
                label = { it.label() },
                onSelect = { scenario.session.value = it },
            )

            SectionTitle("Permisos")
            EnumChips(
                values = MockScenario.PermissionTier.entries,
                selected = tier,
                label = { it.name },
                onSelect = { scenario.permissionTier.value = it },
            )

            SwitchRow("Onboarding completado", onboarding) { scenario.onboardingCompleted.value = it }
            SwitchRow("GPS activado", gps) { scenario.gpsEnabled.value = it }
            SwitchRow("Conexión online", online) { scenario.online.value = it }

            SectionTitle("Simulación")
            SwitchRow("Conduciendo (puck en movimiento en Home)", driving) { on ->
                // Mirror a geofence-exit: stamp the trip's origin (route start) + the DEPARTING vehicle
                // (mock_vehicle_002, deliberately NOT the active mock_vehicle_001) so Home's blue origin
                // dot and the puck bind to the car that left — exercising DEPART-CONSISTENCY-001 in the
                // mock, not just the fallback. setRunning(false) clears the trip. [DEPART-CONSISTENCY-001]
                if (on) {
                    runtime.setTrip(
                        io.apptolast.paparcar.domain.detection.TripContext(
                            // Origin at the driving-sim route start (El Puerto, Av. de Valencia). [ROUTE-SNAP-001]
                            departurePoint = io.apptolast.paparcar.domain.model.GpsPoint(36.6068, -6.2270, 8.5f, 0L, 0f),
                            departingVehicleId = "mock_vehicle_002",
                        ),
                    )
                }
                runtime.setRunning(on)
            }
            // Candidate phase: stopped + walking away. Flips the Home chip from "Conduciendo" (blue) to
            // "Aparcando…" (green). Only visible while the driving sim is on. [DET-PHASE-001]
            SwitchRow(
                "Candidato (parado, andando)",
                candidate == io.apptolast.paparcar.domain.detection.DetectionPhase.Candidate,
            ) { on ->
                runtime.setPhase(
                    if (on) io.apptolast.paparcar.domain.detection.DetectionPhase.Candidate
                    else io.apptolast.paparcar.domain.detection.DetectionPhase.Driving
                )
            }

            Spacer(Modifier.height(4.dp))
            Button(onClick = onEnter, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text("Entrar a la app con este escenario", fontWeight = FontWeight.SemiBold)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Presets rápidos (set + entrar)")
            PresetButton("Login (deslogueado)") {
                scenario.reset(); scenario.session.value = MockScenario.Session.LoggedOut; onEnter()
            }
            PresetButton("Onboarding") {
                scenario.reset(); scenario.onboardingCompleted.value = false; onEnter()
            }
            PresetButton("Permisos · rationale (sin permisos)") {
                scenario.reset(); scenario.permissionTier.value = MockScenario.PermissionTier.None; onEnter()
            }
            PresetButton("Pantalla de permisos (GPS off)") {
                scenario.reset()
                scenario.permissionTier.value = MockScenario.PermissionTier.Core
                scenario.gpsEnabled.value = false
                onEnter()
            }
            PresetButton("Registro de vehículo (sin coche)") {
                scenario.reset(); scenario.session.value = MockScenario.Session.LoggedInNoVehicle; onEnter()
            }
            PresetButton("Home (todo OK)") {
                scenario.reset(); onEnter()
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionTitle("Galería de estados por pantalla")
            OutlinedButton(onClick = onOpenGallery, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir galería de estados")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun <T> EnumChips(
    values: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label(value)) },
            )
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PresetButton(label: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(label)
    }
}

private fun MockScenario.Session.label(): String = when (this) {
    MockScenario.Session.LoggedOut -> "Deslogueado"
    MockScenario.Session.LoggedInNoVehicle -> "Sin coche"
    MockScenario.Session.LoggedInWithVehicles -> "Con coches"
}
