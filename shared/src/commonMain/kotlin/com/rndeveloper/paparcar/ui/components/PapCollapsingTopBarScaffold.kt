package com.rndeveloper.paparcar.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Velocity
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import kotlin.math.roundToInt

/**
 * Scaffold canónico para pantallas con **cabecera que se retira al scrollear** (Ajustes, Vehículos,
 * formulario de vehículo, config Bluetooth). Antes cada pantalla montaba su propio `Scaffold +
 * TopAppBar` y solo Ajustes tenía `scrollBehavior`, así que la app tenía dos cabeceras distintas y
 * dos tratos distintos de la status bar. Aquí vive el único. [UI-TOPBAR-COLLAPSE-001]
 *
 * Doctrina:
 * - El **cuerpo se dibuja a sangre** desde y=0. El [contentPadding] que recibe el `content` reserva
 *   la altura de la cabecera EN REPOSO, así que la primera fila arranca bajo el título y el resto
 *   pasa por debajo al scrollear (nunca se recorta contra la status bar).
 * - La cabecera se retira **entera** —franja de status bar incluida— y con ella todo lo que lleve
 *   ([subHeader]): al final del recorrido no queda banda, el contenido pasa bajo el reloj. Nada de
 *   chrome que sobreviva al colapso: si algo debe estar siempre a mano, no es cabecera.
 * - Mientras se retira **es opaca**: el contenido pasa por DEBAJO, no por encima del título.
 * - Se mueve a la misma velocidad que el contenido (con lo que la lista **consume**, no con el
 *   gesto), así una lista que no scrollea no la retira y el contenido no viaja al doble que el dedo.
 *
 * @param subHeader chrome propio de la pantalla bajo el título (p. ej. las pestañas de vehículo).
 *        Se retira junto con el título.
 * @param expandKey al cambiar de valor la cabecera vuelve a desplegarse. Para contenidos que se
 *        sustituyen por otro que empieza arriba (cambiar de página del pager), donde dejarla
 *        retirada abriría un hueco donde estaba el título.
 * @param content recibe el padding a aplicar como `contentPadding` del scrollable (o como padding
 *        DENTRO del `verticalScroll`), nunca como padding externo: el contenido debe poder pasar
 *        por debajo de la cabecera.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PapCollapsingTopBarScaffold(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    subHeader: (@Composable () -> Unit)? = null,
    expandKey: Any? = null,
    snackbarHost: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // Las tres piezas de la cabecera se miden en vez de asumirse: la franja de la status bar puede
    // venir consumida (banner de conectividad), el título crece con el tamaño de fuente del sistema
    // y el sub-header lo pone cada pantalla.
    var statusBarPx by remember { mutableIntStateOf(0) }
    var titleRowPx by remember { mutableIntStateOf(0) }
    var subHeaderPx by remember { mutableIntStateOf(0) }
    var offsetPx by remember { mutableFloatStateOf(0f) }

    val collapseLimitPx = (statusBarPx + titleRowPx + subHeaderPx).toFloat()

    // Si la cabecera encoge (rota la pantalla, aparece el banner, cambia el sub-header) el
    // desplazamiento acumulado podría dejarla fuera de rango: se re-encaja.
    LaunchedEffect(collapseLimitPx) {
        offsetPx = offsetPx.coerceIn(-collapseLimitPx, 0f)
    }

    // Contenido nuevo que empieza arriba → cabecera desplegada, o quedaría un hueco vacío.
    LaunchedEffect(expandKey) {
        if (expandKey != null) offsetPx = 0f
    }

    val connection = remember {
        object : NestedScrollConnection {
            // Se conduce con lo CONSUMIDO, no con lo disponible: una lista que no puede scrollear
            // consume 0 y la cabecera se queda quieta (nada de títulos que se van en una pantalla
            // que no scrollea), y cuando sí scrollea la cabecera acompaña al contenido 1:1.
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val limit = (statusBarPx + titleRowPx + subHeaderPx).toFloat()
                offsetPx = (offsetPx + consumed.y).coerceIn(-limit, 0f)
                return Offset.Zero
            }

            // Al soltar, la cabecera no se queda a medias: cae al borde más cercano.
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                val limit = (statusBarPx + titleRowPx + subHeaderPx).toFloat()
                if (limit > 0f && offsetPx != 0f && offsetPx != -limit) {
                    val target = if (offsetPx < -limit * SETTLE_THRESHOLD) -limit else 0f
                    animate(initialValue = offsetPx, targetValue = target) { value, _ ->
                        offsetPx = value
                    }
                }
                return Velocity.Zero
            }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(connection),
        containerColor = containerColor,
        // Solo insets horizontales: el cuerpo pinta bajo la status bar (ese es justo el efecto),
        // pero nunca bajo el recorte de cámara en horizontal.
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal),
        snackbarHost = snackbarHost,
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            content(
                PaddingValues(
                    top = with(density) { (statusBarPx + titleRowPx + subHeaderPx).toDp() },
                    bottom = innerPadding.calculateBottomPadding(),
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                ),
            )

            // La cabecera no se "desplaza": ENCOGE y se recorta por su borde superior, de modo que
            // el título se mete bajo el borde de pantalla en vez de dibujarse sobre el reloj.
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .clipToBounds()
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val height = (placeable.height + offsetPx).roundToInt().coerceAtLeast(0)
                        layout(placeable.width, height) {
                            placeable.place(x = 0, y = offsetPx.roundToInt())
                        }
                    }
                    // Opaca mientras existe: el contenido pasa por DEBAJO. Como se retira entera,
                    // al final no queda banda que recorte nada.
                    .background(containerColor)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                // La franja de la status bar es un hueco medido aparte (y no el inset del
                // TopAppBar) porque forma parte de lo que se retira; medida con
                // `windowInsetsTopHeight` respeta el inset ya consumido por el banner. [CONN-BANNER-001]
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsTopHeight(WindowInsets.statusBars)
                        .onSizeChanged { statusBarPx = it.height },
                )
                Box(modifier = Modifier.onSizeChanged { titleRowPx = it.height }) {
                    TopAppBar(
                        title = { Text(text = title, style = PaparcarType.current.screenTitle) },
                        navigationIcon = navigationIcon,
                        actions = actions,
                        // Transparente: el color lo pone la cabecera entera; un
                        // `scrolledContainerColor` volvería a plantar la banda opaca que
                        // sobrevivía al colapso y recortaba el contenido.
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                        ),
                        windowInsets = WindowInsets(0),
                    )
                }
                subHeader?.let { header ->
                    Box(modifier = Modifier.onSizeChanged { subHeaderPx = it.height }) { header() }
                }
            }
        }
    }
}

/** Fracción de cabecera oculta a partir de la cual el asentamiento la termina de esconder. */
private const val SETTLE_THRESHOLD = 0.5f
