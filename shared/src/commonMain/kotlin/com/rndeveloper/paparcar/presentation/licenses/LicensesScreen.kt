package com.rndeveloper.paparcar.presentation.licenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.OpenSourceLibrary
import com.rndeveloper.paparcar.presentation.util.collectAsStateLifecycleAware
import com.rndeveloper.paparcar.ui.components.PapCollapsingTopBarScaffold
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapNavChevron
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.licenses_cd_back
import paparcar.composeapp.generated.resources.licenses_count
import paparcar.composeapp.generated.resources.licenses_error
import paparcar.composeapp.generated.resources.licenses_subtitle
import paparcar.composeapp.generated.resources.settings_licenses

/**
 * Every open source library the app ships with, read from the file the build generates out of the
 * Gradle dependency graph. [SET-LICENSES-ARE-SHOWN-IN-THE-APP-001]
 */
@Composable
fun LicensesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicense: (String) -> Unit,
    viewModel: LicensesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateLifecycleAware()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is LicensesEffect.NavigateBack -> onNavigateBack()
                is LicensesEffect.NavigateToLicense -> onNavigateToLicense(effect.licenseId)
                // The list has no outbound links; only the detail screen emits this.
                is LicensesEffect.OpenUrl -> Unit
            }
        }
    }

    LicensesContent(state = state, onIntent = viewModel::handleIntent)
}

@Composable
fun LicensesContent(
    state: LicensesState,
    onIntent: (LicensesIntent) -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme

    PapCollapsingTopBarScaffold(
        title = stringResource(Res.string.settings_licenses),
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

            state.failedToLoad || state.libraries.isEmpty() -> Box(
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

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + LIST_V_PADDING,
                    bottom = padding.calculateBottomPadding() + LIST_V_PADDING,
                ),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(Res.string.licenses_subtitle),
                            style = PaparcarType.current.body,
                            color = cs.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                Res.string.licenses_count,
                                state.libraries.size,
                                state.licenses.size,
                            ),
                            style = PaparcarType.current.meta,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                items(items = state.libraries, key = { it.id }) { library ->
                    LibraryRow(
                        library = library,
                        licenseNames = state.licenseNames(library),
                        onClick = {
                            library.licenseIds.firstOrNull()
                                ?.let { onIntent(LicensesIntent.OpenLicense(it)) }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryRow(
    library: OpenSourceLibrary,
    licenseNames: List<String>,
    onClick: () -> Unit,
) {
    PapOutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        PapListItem(
            title = library.name,
            // The library's name is the name of a real thing → MARCA, like a vehicle or a place.
            titleStyle = PaparcarType.current.rowName,
            titleMaxLines = 2,
            subtitle = listOfNotNull(library.version, licenseNames.joinToString(SEPARATOR))
                .filter { it.isNotBlank() }
                .joinToString(SEPARATOR),
            subtitleStyle = PaparcarType.current.label,
            trailing = { PapNavChevron() },
        )
    }
}

// ── Layout tokens ─────────────────────────────────────────────────────────────

private const val SEPARATOR = " · "
private val SCREEN_H_PADDING = 16.dp
private val LIST_V_PADDING = 12.dp
private val ROW_GAP = 8.dp
