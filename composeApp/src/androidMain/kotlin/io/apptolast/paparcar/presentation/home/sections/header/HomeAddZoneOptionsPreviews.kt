package io.apptolast.paparcar.presentation.home.sections.header

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.apptolast.paparcar.ui.components.GlassSurface
import io.apptolast.paparcar.ui.theme.PapBorders
import io.apptolast.paparcar.ui.theme.PapShapes
import io.apptolast.paparcar.ui.theme.PaparcarTheme

// ─────────────────────────────────────────────────────────────────────────────
// Shared mock search bar stub (visual context only)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MockSearchBar(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(SEARCH_BAR_HEIGHT.dp),
        shape = RoundedCornerShape(SEARCH_BAR_RADIUS.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            PapBorders.thin,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.18f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Rounded.EditCalendar,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
                modifier = Modifier.size(18.dp),
            )
            Text(
                "Buscar dirección…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.36f),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OPTION A — Enhanced header chip
// Label + subtitle hint, primary border, same GlassSurface position
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OptionAChip(onAddZone: () -> Unit = {}) {
    GlassSurface(
        shape = RoundedCornerShape(CHIP_RADIUS.dp),
        shadowElevation = CHIP_SHADOW.dp,
        onClick = onAddZone,
        modifier = Modifier.padding(start = 14.dp, top = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(CHIP_ICON_BOX.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(CHIP_ICON_SIZE.dp),
                    )
                }
            }
            Column {
                Text(
                    text = "Añadir lugar habitual",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Casa, trabajo, gimnasio…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Preview(name = "A — chip mejorado · claro", showBackground = true)
@Composable
private fun OptionAPreviewLight() {
    PaparcarTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(14.dp)) {
                MockSearchBar()
                OptionAChip()
            }
        }
    }
}

@Preview(
    name = "A — chip mejorado · oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OptionAPreviewDark() {
    PaparcarTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(14.dp)) {
                MockSearchBar()
                OptionAChip()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OPTION B — Empty-state card in the sheet
// Card with title + subtitle + CTA pill, below search bar, above spots
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OptionBEmptyZoneCard(onAddZone: () -> Unit = {}) {
    Surface(
        onClick = onAddZone,
        modifier = Modifier.fillMaxWidth(),
        shape = PapShapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            PapBorders.thin,
            MaterialTheme.colorScheme.outline.copy(alpha = PapBorders.DEFAULT_OUTLINE_ALPHA),
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(CARD_ICON_BOX.dp)
                    .clip(RoundedCornerShape(CARD_ICON_RADIUS.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Bookmark,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Guarda un lugar habitual",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Casa, trabajo, familia… navegación en un tap",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
            }
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "Añadir",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Preview(name = "B — card en sheet · claro", showBackground = true)
@Composable
private fun OptionBPreviewLight() {
    PaparcarTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MockSearchBar()
                // Header chip queda vacío (solo el + circular de siempre)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(PapBorders.thin, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        ) {
                            Box(Modifier.padding(8.dp)) {
                                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                // Sheet content
                Text(
                    "LUGARES HABITUALES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                OptionBEmptyZoneCard()
            }
        }
    }
}

@Preview(
    name = "B — card en sheet · oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OptionBPreviewDark() {
    PaparcarTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MockSearchBar()
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            border = BorderStroke(PapBorders.thin, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        ) {
                            Box(Modifier.padding(8.dp)) {
                                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
                Text(
                    "LUGARES HABITUALES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp),
                )
                OptionBEmptyZoneCard()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OPTION C — Coachmark tooltip (one-time hint)
// Arrow bubble anchored below the add-zone chip, first launch only
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CoachmarkBubble(
    text: String,
    modifier: Modifier = Modifier,
) {
    val bubbleColor = MaterialTheme.colorScheme.inverseSurface
    val textColor = MaterialTheme.colorScheme.inverseOnSurface

    Column(
        modifier = modifier.width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.Start,
    ) {
        // Arrow pointing up-left toward the chip
        Box(
            modifier = Modifier
                .padding(start = ARROW_OFFSET.dp)
                .size(width = ARROW_W.dp, height = ARROW_H.dp)
                .drawBehind {
                    val path = Path().apply {
                        moveTo(0f, size.height)
                        lineTo(size.width / 2f, 0f)
                        lineTo(size.width, size.height)
                        close()
                    }
                    drawPath(path, bubbleColor)
                },
        )
        Surface(
            shape = RoundedCornerShape(BUBBLE_RADIUS.dp),
            color = bubbleColor,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor,
                    fontWeight = FontWeight.Medium,
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.15f),
                ) {
                    Text(
                        "Entendido",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCScene(modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Column(modifier = Modifier.padding(14.dp)) {
            MockSearchBar()
            Spacer(Modifier.height(6.dp))
            // Existing minimal chip (unchanged)
            GlassSurface(
                shape = RoundedCornerShape(CHIP_RADIUS.dp),
                shadowElevation = CHIP_SHADOW.dp,
                onClick = {},
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Añadir lugar habitual",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        // Scrim behind coachmark
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(Color.Black.copy(alpha = 0.35f)),
        )
        // Coachmark anchored below-left of the chip
        CoachmarkBubble(
            text = "Guarda casa, trabajo o gym\npara navegar con un tap",
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 14.dp, y = COACHMARK_OFFSET_Y.dp),
        )
    }
}

@Preview(name = "C — coachmark · claro", showBackground = true)
@Composable
private fun OptionCPreviewLight() {
    PaparcarTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            OptionCScene()
        }
    }
}

@Preview(
    name = "C — coachmark · oscuro",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun OptionCPreviewDark() {
    PaparcarTheme {
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
            OptionCScene()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

private const val SEARCH_BAR_HEIGHT = 52
private const val SEARCH_BAR_RADIUS = 14
private const val CHIP_RADIUS = 18
private const val CHIP_SHADOW = 6
private const val CHIP_ICON_BOX = 28
private const val CHIP_ICON_SIZE = 16
private const val CARD_ICON_BOX = 44
private const val CARD_ICON_RADIUS = 14
private const val ARROW_OFFSET = 20
private const val ARROW_W = 16
private const val ARROW_H = 8
private const val BUBBLE_RADIUS = 14
private const val COACHMARK_OFFSET_Y = 112
