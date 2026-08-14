package io.apptolast.paparcar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.theme.PapMotion
import io.apptolast.paparcar.ui.theme.outlineSubtle
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.common_scroll_to_top_cd

/**
 * Botón "volver arriba" para listas largas (historial de vehículo, ajustes). Aparece solo cuando se
 * ha bajado lo suficiente como para que volver arrastrando sea trabajo, y devuelve la lista al
 * principio de una vez. [UI-SCROLL-TO-TOP-001]
 *
 * No usa `GlassSurface`: el cristal es para lo que flota **sobre el mapa**, donde hay tiles que
 * tienen que asomar. Aquí flota sobre una lista opaca, así que es una superficie normal.
 *
 * Se coloca solo (abajo a la derecha) sobre el `Box` que envuelve la lista; [bottomPadding] es el
 * hueco reservado por la barra inferior, que la pantalla ya recibe de su scaffold.
 */
@Composable
fun BoxScope.PapScrollToTopButton(
    listState: LazyListState,
    bottomPadding: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // El umbral es DISTANCIA recorrida, no número de items: las tarjetas de ajustes miden el triple
    // que una fila de historial, así que contar items haría aparecer el botón a destiempo en una de
    // las dos. Se estima con el tamaño medio de lo visible y se mide en pantallas, que es como se
    // percibe "he bajado mucho". `derivedStateOf` recompone solo cuando el booleano cambia.
    val visible by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val viewport = info.viewportEndOffset - info.viewportStartOffset
            val averageItem = info.visibleItemsInfo
                .takeIf { it.isNotEmpty() }
                ?.sumOf { it.size }
                ?.div(info.visibleItemsInfo.size)
                ?: 0
            val scrolled = listState.firstVisibleItemIndex.toLong() * averageItem +
                listState.firstVisibleItemScrollOffset
            viewport > 0 && scrolled > viewport * SHOW_AFTER_SCREENS
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(PapMotion.fast()) + scaleIn(PapMotion.fast(), initialScale = ENTER_SCALE),
        exit = fadeOut(PapMotion.fast()) + scaleOut(PapMotion.fast(), targetScale = ENTER_SCALE),
        modifier = modifier
            .align(Alignment.BottomEnd)
            .padding(end = EDGE_MARGIN, bottom = EDGE_MARGIN)
            .padding(bottom = bottomPadding),
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = outlineSubtle,
            shadowElevation = SHADOW,
            modifier = Modifier.size(BUTTON_SIZE),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.common_scroll_to_top_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ICON_SIZE),
                )
            }
        }
    }
}

/** Pantallas recorridas a partir de las cuales volver arrastrando ya es trabajo. */
private const val SHOW_AFTER_SCREENS = 1.5f

private const val ENTER_SCALE = 0.8f
private val BUTTON_SIZE = 44.dp
private val ICON_SIZE = 24.dp
private val EDGE_MARGIN = 16.dp
private val SHADOW = 4.dp
