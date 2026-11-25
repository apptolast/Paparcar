package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.paywall.PlusBenefits
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

@Preview(widthDp = 320, locale = "es")
@Preview(widthDp = 320, locale = "de", fontScale = 1.4f)
@Composable
private fun PlusBenefitAlignmentPreview() {
    PaparcarTheme {
        Surface {
            Column(modifier = Modifier.padding(20.dp)) {
                PlusBenefits()
            }
        }
    }
}
