package io.apptolast.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.apptolast.paparcar.ui.theme.PaparcarSpacing

/**
 * Scaffold canónico para pantallas con **contenido + acción anclada abajo** (onboarding/permisos,
 * disclaimers, formularios first-run). Centraliza el patrón que antes cada pantalla recreaba a mano
 * con `Box + Column(BottomCenter) + onSizeChanged`, fuente de la divergencia de padding entre
 * pantallas. Aquí el `bottomBar` nativo mide el footer y nos da el `innerPadding` real: el contenido
 * reserva exactamente la altura del footer + [CONTENT_FOOTER_GAP], sin medir nada a mano. [ONB-SCAFFOLD-001]
 *
 * - El footer queda anclado (el contenido scrollea por encima, nunca por debajo).
 * - Insets de status/navigation bar gestionados aquí una sola vez.
 * - Un único token de separación contenido↔footer → imposible que vuelva a divergir entre pantallas.
 *
 * @param footer acción(es) ancladas abajo (botón principal + secundario opcional). `null` = sin barra.
 * @param scrollable envuelve el contenido en `verticalScroll`. Desactívalo para layouts que ya
 *        gestionan su propio scroll/medida (p. ej. un pager).
 */
@Composable
fun PaparcarBottomActionScaffold(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = PaparcarSpacing.xxl,
    scrollable: Boolean = true,
    contentArrangement: Arrangement.Vertical = Arrangement.Top,
    contentAlignment: Alignment.Horizontal = Alignment.Start,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        // Insets se aplican manualmente abajo (status arriba, navigation en el footer) para que el
        // contenido pueda pintar a sangre y el footer ancle al borde con su propio padding.
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            if (footer != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding)
                        .navigationBarsPadding()
                        .padding(bottom = FOOTER_BOTTOM_PADDING),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
                    content = footer,
                )
            }
        },
    ) { innerPadding ->
        val scrollModifier =
            if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(
            modifier = Modifier
                .fillMaxSize()
                // innerPadding.bottom = altura medida del footer (incluye su navigationBarsPadding);
                // le sumamos el único gap de holgura contenido↔footer.
                .padding(bottom = innerPadding.calculateBottomPadding() + CONTENT_FOOTER_GAP)
                .statusBarsPadding()
                .padding(horizontal = horizontalPadding)
                .then(scrollModifier),
            verticalArrangement = contentArrangement,
            horizontalAlignment = contentAlignment,
            content = content,
        )
    }
}

/** Holgura única entre la última fila de contenido y el footer anclado. */
private val CONTENT_FOOTER_GAP = PaparcarSpacing.xl

/** Padding bajo el botón (sobre la barra de navegación del sistema). */
private val FOOTER_BOTTOM_PADDING = PaparcarSpacing.lg
