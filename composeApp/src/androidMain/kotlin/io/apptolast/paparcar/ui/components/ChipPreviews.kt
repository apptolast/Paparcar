package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.chips.PaparcarAddChip
import io.apptolast.paparcar.ui.components.chips.PaparcarFilterChip
import io.apptolast.paparcar.ui.theme.PaparcarTheme

@Preview(showBackground = true)
@Composable
private fun PaparcarFilterChipPreview() {
    PaparcarTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaparcarFilterChip(label = "Todos", selected = true, onClick = {})
                    PaparcarFilterChip(label = "Esta semana", selected = false, onClick = {})
                    PaparcarFilterChip(label = "Este mes", selected = false, onClick = {})
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaparcarFilterChip(
                        label = "Con icono",
                        selected = true,
                        onClick = {},
                        leadingIcon = Icons.Rounded.CalendarToday,
                    )
                    PaparcarFilterChip(
                        label = "Deshabilitado",
                        selected = false,
                        onClick = {},
                        enabled = false,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PaparcarAddChipPreview() {
    PaparcarTheme {
        Surface {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PaparcarAddChip(onClick = {}, contentDescription = "Añadir")
            }
        }
    }
}
