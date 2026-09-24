package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.ZoneIcon
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

/**
 * The zone chip had no preview at all, which is how it spent its life painting every zone in brand
 * green: nothing put a public and a private chip side by side, and on the real map the private
 * label sits far enough away that the contradiction reads as "the marker is weird" rather than
 * "the chip is lying". Public and private go in ONE row here, on purpose.
 * [UI-A-PRIVATE-ZONE-IS-PRIVATE-ON-EVERY-SURFACE-001]
 */
@Composable
private fun Gallery() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Label("Pública · privada — misma identidad que su etiqueta en el mapa")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneChip(label = "Casa", iconKey = ZoneIcon.HOME, isPrivate = false, onClick = {}, onEdit = {})
            ZoneChip(label = "Home garage", iconKey = ZoneIcon.WORK, isPrivate = true, onClick = {}, onEdit = {})
        }

        Label("Nombre largo — la píldora crece, el candado no se cae")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZoneChip(
                label = "Gimnasio del barrio",
                iconKey = ZoneIcon.GYM,
                isPrivate = true,
                onClick = {},
                onEdit = {},
            )
        }

        // El "+" y los chips, en la MISMA fila y a la MISMA altura: el defecto que abrió el ticket
        // (32 vs 34 dp) solo se ve poniéndolos a tocarse.
        // [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
        Label("Fila con zonas — el + colapsado mide lo que los chips")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ZoneChip(label = "Casa", iconKey = ZoneIcon.HOME, isPrivate = false, onClick = {}, onEdit = {})
            AddZoneChip(expanded = false, onAddZone = {}, onDismiss = {})
        }

        Label("Sin zonas — el MISMO chip, expandido en invitación")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddZoneChip(expanded = true, onAddZone = {}, onDismiss = {})
        }

        // Interactivo: el colapso es lo que el user reporto como "sin animacion ni nada".
        Label("Vivo — pulsa la × y mira el colapso")
        var expanded by remember { mutableStateOf(true) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AddZoneChip(
                expanded = expanded,
                onAddZone = { expanded = !expanded },
                onDismiss = { expanded = false },
            )
        }
    }
}

@Composable
private fun Label(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.labelSmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Preview(name = "Zone chip · Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ZoneChipDark() = PaparcarTheme(darkTheme = true) { Gallery() }

@Preview(name = "Zone chip · Light", showBackground = true)
@Composable
private fun ZoneChipLight() = PaparcarTheme(darkTheme = false) { Gallery() }
