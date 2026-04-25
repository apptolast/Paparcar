package io.apptolast.paparcar.presentation.home.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.presentation.util.MapCircleFab
import io.apptolast.paparcar.ui.components.GlassSurface
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ═══════════════════════════════════════════════════════════════════════════════
//  SECCIÓN B — PROPUESTA: HomeGlassNavBar
//  Reemplaza HomeFloatingHeader (☰ hamburger en top-right).
//  Flota sobre el mapa en la parte inferior, estilo glass.
//  Visible solo en HomeScreen. En las demás pantallas el BottomNav estándar toma el control.
//
//  Coordinación con HomeNavBar (navigate bar):
//    • HomeNavBar visible  → HomeGlassNavBar oculto (AnimatedVisibility fadeOut)
//    • Sheet en half/full  → HomeGlassNavBar sigue visible por encima del sheet
//    • Mapa moviéndose     → se vuelve glass como el resto de overlays (LocalMapInteracting)
// ═══════════════════════════════════════════════════════════════════════════════

// ─── A) Componente aislado ─────────────────────────────────────────────────────

@Preview(name = "B — HomeGlassNavBar (oscuro)", showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeGlassNavBarDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .background(Color(0xFF1A2420))
                .padding(vertical = 16.dp),
        ) {
            HomeGlassNavBar(onMapClick = {}, onHistoryClick = {}, onMyCarClick = {}, onSettingsClick = {})
        }
    }
}

@Preview(name = "B — HomeGlassNavBar (claro)", showBackground = true)
@Composable
private fun HomeGlassNavBarLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Box(Modifier.padding(vertical = 16.dp)) {
            HomeGlassNavBar(onMapClick = {}, onHistoryClick = {}, onMyCarClick = {}, onSettingsClick = {})
        }
    }
}

// ─── B) Concept "money shot" — pantalla completa ──────────────────────────────

@Preview(
    name = "B — HomeScreen: Glass BottomNav (oscuro) ★",
    showBackground = false,
    widthDp = 390,
    heightDp = 844,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun HomeGlassNavFullScreenDarkPreview() {
    PaparcarTheme(darkTheme = true) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF152218)),
        ) {
            // ── Simulated map tiles ────────────────────────────────────────────
            // Subtle grid to evoke a map without Google Maps dependency
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1B2A20)),
            )
            // Roads simulation
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(68.dp),
            ) {
                repeat(14) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF243320)))
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(52.dp),
            ) {
                repeat(9) {
                    Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFF243320)))
                }
            }

            // ── Spot markers on the map ────────────────────────────────────────
            Box(
                modifier = Modifier
                    .offset(x = 120.dp, y = 260.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("P", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            }
            Box(
                modifier = Modifier
                    .offset(x = 220.dp, y = 320.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("P", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
            Box(
                modifier = Modifier
                    .offset(x = 60.dp, y = 360.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFB08000)),
                contentAlignment = Alignment.Center,
            ) {
                Text("P", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }

            // ── Top: Search bar (sin FloatingHeader, más limpio) ───────────────
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = 50.dp, start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Busca una calle o lugar...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Right FABs ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 196.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.End,
            ) {
                MapCircleFab(
                    icon = Icons.Outlined.MyLocation,
                    onClick = {},
                )
                MapCircleFab(
                    icon = Icons.Outlined.Layers,
                    onClick = {},
                )
            }

            // ── Bottom sheet peek ──────────────────────────────────────────────
            // Sit justo encima del nav bar (padding-bottom = nav bar ~74dp)
            GlassSurface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = 76.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Drag handle
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)),
                        )
                    }
                    // Peek info row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Gran Vía, Madrid",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "3 plazas disponibles cerca",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "3",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                    }
                }
            }

            // ── Glass BottomNav — el protagonista ──────────────────────────────
            HomeGlassNavBar(
                onMapClick = {},
                onHistoryClick = {},
                onMyCarClick = {},
                onSettingsClick = {},
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
            )
        }
    }
}

@Preview(
    name = "B — HomeScreen: Glass BottomNav (claro) ★",
    showBackground = false,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun HomeGlassNavFullScreenLightPreview() {
    PaparcarTheme(darkTheme = false) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFD8E8D4)),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(68.dp),
            ) {
                repeat(14) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFC5D9C0)))
                }
            }
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(52.dp),
            ) {
                repeat(9) {
                    Box(Modifier.width(1.dp).fillMaxSize().background(Color(0xFFC5D9C0)))
                }
            }

            Box(
                modifier = Modifier
                    .offset(x = 170.dp, y = 290.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("P", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(top = 50.dp, start = 14.dp, end = 14.dp),
            ) {
                GlassSurface(
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f).height(52.dp),
                ) {
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("Busca una calle o lugar...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 14.dp, bottom = 196.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MapCircleFab(icon = Icons.Outlined.MyLocation, onClick = {})
                MapCircleFab(icon = Icons.Outlined.Layers, onClick = {})
            }

            GlassSurface(
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(bottom = 76.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.width(36.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Place, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Gran Vía, Madrid", style = MaterialTheme.typography.bodyMedium)
                            Text("3 plazas disponibles", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("3", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                }
            }

            HomeGlassNavBar(
                onMapClick = {},
                onHistoryClick = {},
                onMyCarClick = {},
                onSettingsClick = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            )
        }
    }
}
