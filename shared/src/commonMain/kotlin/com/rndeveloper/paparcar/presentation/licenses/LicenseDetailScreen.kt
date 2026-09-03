package com.rndeveloper.paparcar.presentation.licenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.presentation.util.collectAsStateLifecycleAware
import com.rndeveloper.paparcar.ui.components.PapCollapsingTopBarScaffold
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPrimaryButton
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.licenses_cd_back
import paparcar.composeapp.generated.resources.licenses_detail_open_terms
import paparcar.composeapp.generated.resources.licenses_detail_no_text
import paparcar.composeapp.generated.resources.licenses_error
import paparcar.composeapp.generated.resources.settings_licenses

/**
 * One licence: its full text when we may ship it, or a link when we may not.
 * [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
 */
@Composable
fun LicenseDetailScreen(
    licenseId: String,
    onNavigateBack: () -> Unit,
    viewModel: LicensesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateLifecycleAware()
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LicensesEffect.NavigateBack -> onNavigateBack()
                is LicensesEffect.OpenUrl -> uriHandler.openUri(effect.url)
                // Only the list navigates into a licence; from here there is nowhere deeper to go.
                is LicensesEffect.NavigateToLicense -> Unit
            }
        }
    }

    LicenseDetailContent(
        state = state,
        licenseId = licenseId,
        onIntent = viewModel::handleIntent,
    )
}

@Composable
fun LicenseDetailContent(
    state: LicensesState,
    licenseId: String,
    onIntent: (LicensesIntent) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    val license = state.license(licenseId)

    PapCollapsingTopBarScaffold(
        title = license?.name ?: stringResource(Res.string.settings_licenses),
        containerColor = cs.surfaceContainer,
        navigationIcon = {
            IconButton(onClick = { onIntent(LicensesIntent.NavigateBack) }) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(Res.string.licenses_cd_back),
                )
            }
        },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            license == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(SCREEN_H_PADDING),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.licenses_error),
                    style = PaparcarType.current.body,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // ⛔ La ÚNICA pantalla que se recorta contra la status bar en vez de pasar por debajo,
            // y la excepción está medida, no supuesta. La doctrina del scaffold
            // [UI-TOPBAR-COLLAPSE-001] es que la cabecera se retira entera y el cuerpo sigue subiendo
            // bajo el reloj: con filas-tarjeta eso se lee como profundidad —ves un objeto acotado
            // metiéndose debajo—. Con 10 000 caracteres de prosa corrida no hay objeto que se meta
            // debajo: hay glifos partidos por el reloj y la batería. Probado en el Oppo el 03-09,
            // primero metiendo el texto en una tarjeta: no basta, porque la tarjeta es más alta que
            // la pantalla y su primera línea acaba igual bajo el reloj.
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + LIST_V_PADDING,
                    bottom = padding.calculateBottomPadding() + LIST_V_PADDING,
                ),
            ) {
                item {
                    // La tarjeta le da al texto la misma superficie que tiene cualquier otro
                    // contenido de la app, en vez de dejarlo a sangre sobre el fondo.
                    PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        // Términos propietarios (Android SDK, Play): no son redistribuibles, así que
                        // la app lo dice y los enlaza en vez de fingir que los lleva.
                        Text(
                            text = license.text
                                ?: stringResource(Res.string.licenses_detail_no_text),
                            style = PaparcarType.current.body,
                            color = if (license.text != null) cs.onSurface else cs.onSurfaceVariant,
                            modifier = Modifier.padding(CARD_PADDING),
                        )
                    }
                }

                license.url?.let { url ->
                    item {
                        PapPrimaryButton(
                            label = stringResource(Res.string.licenses_detail_open_terms),
                            icon = Icons.AutoMirrored.Rounded.OpenInNew,
                            onClick = { onIntent(LicensesIntent.OpenUrl(url)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ── Layout tokens ─────────────────────────────────────────────────────────────

private val SCREEN_H_PADDING = 16.dp
private val LIST_V_PADDING = 12.dp
private val ROW_GAP = 8.dp
private val CARD_PADDING = 16.dp
