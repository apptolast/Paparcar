
package io.apptolast.paparcar.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Replace with your font name
// val Manrope = FontFamily(
//     Font(R.font.manrope_regular, FontWeight.Normal),
//     Font(R.font.manrope_medium, FontWeight.Medium),
//     Font(R.font.manrope_bold, FontWeight.Bold)
// )

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, // Replace with Manrope
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, // Replace with Manrope
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, // Replace with Manrope
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
