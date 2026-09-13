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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rndeveloper.paparcar.domain.model.OpenSourceLibrary
import com.rndeveloper.paparcar.ui.components.PapCollapsingTopBarScaffold
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.components.PapNavChevron
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.licenses_cd_back
import paparcar.composeapp.generated.resources.licenses_data_sources_header
import paparcar.composeapp.generated.resources.licenses_source_minetur_purpose
import paparcar.composeapp.generated.resources.licenses_source_openvan_purpose
import paparcar.composeapp.generated.resources.licenses_source_osm_purpose
import paparcar.composeapp.generated.resources.licenses_source_prix_carburants_purpose
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
    val state by viewModel.state.collectAsStateWithLifecycle()

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

            // ⛔ The data-source attribution survives this branch on purpose.
            // [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001] The OSS list is read from a file the build
            // generates, and it can fail to load. The four data sources are not read from
            // anything — they are compiled in — and three of their licences (CC BY 4.0, ODbL,
            // Licence Ouverte) REQUIRE the credit. Letting a missing library file take the attribution
            // down with it would turn a cosmetic failure into a licence breach.
            state.failedToLoad || state.libraries.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(SCREEN_H_PADDING),
                verticalArrangement = Arrangement.spacedBy(ROW_GAP),
            ) {
                Text(
                    text = stringResource(Res.string.licenses_error),
                    style = PaparcarType.current.body,
                    color = cs.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                DataSourcesSection()
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

                item { DataSourcesSection() }

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

/**
 * The data Paparcar shows that somebody else measured. [FUEL-NATIONAL-AVERAGE-FROM-OPENVAN-001]
 *
 * Separate from the library list above it because these are different things wearing the same word:
 * the libraries are SOFTWARE compiled into the app, read from a file the Gradle build generates;
 * these are DATASETS the app queries at runtime, and no dependency graph knows about them. Three of
 * the four licences oblige us to say so — CC BY 4.0 for OpenVan.camp, ODbL for OpenStreetMap,
 * Licence Ouverte for the French register — and an obligation that depends on a generated file
 * having loaded is not being met.
 *
 * The names, licences and URLs are DATA, not copy: they are proper nouns and they are identical in
 * every language, exactly like the library names in the list below. Only the "what we use it for"
 * line is translated.
 */
@Composable
private fun DataSourcesSection() {
    Column(verticalArrangement = Arrangement.spacedBy(ROW_GAP)) {
        PapSectionHeader(title = stringResource(Res.string.licenses_data_sources_header))
        DATA_SOURCES.forEach { source ->
            PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                PapListItem(
                    title = source.name,
                    // The name of a real thing → MARCA, same as a library or a vehicle.
                    titleStyle = PaparcarType.current.rowName,
                    titleMaxLines = 2,
                    subtitle = stringResource(source.purpose) + SEPARATOR + source.license,
                    subtitleStyle = PaparcarType.current.label,
                )
            }
        }
    }
}

/**
 * One dataset the app reads, and the credit its licence asks for.
 *
 * [license] is spelled as the licence NAMES it, not as we would summarise it: "CC BY 4.0" is the
 * string a reader can look up, "free to use" is one they cannot.
 */
private data class DataSourceCredit(
    val name: String,
    val purpose: StringResource,
    val license: String,
)

/**
 * ⛔ A dataset added to the app is added HERE in the same task.
 *
 * There is no build step that can discover these — that is the whole difference from the library
 * list — so the only thing keeping it honest is putting the row in when the source goes in.
 */
private val DATA_SOURCES = listOf(
    DataSourceCredit(
        name = "OpenVan.camp",
        purpose = Res.string.licenses_source_openvan_purpose,
        license = "CC BY 4.0",
    ),
    DataSourceCredit(
        name = "OpenStreetMap",
        purpose = Res.string.licenses_source_osm_purpose,
        license = "ODbL 1.0",
    ),
    DataSourceCredit(
        name = "Ministerio para la Transición Ecológica",
        purpose = Res.string.licenses_source_minetur_purpose,
        license = "Datos abiertos",
    ),
    // [LEGAL-THE-APP-GREW-THREE-DATA-SOURCES-001] France shipped in `37c4ef48` and this row did
    // not, so the app read the French register for weeks while crediting nobody. It is the ONE
    // omission in this file that is not merely imprecise: the Licence Ouverte asks for the source
    // to be named, and naming it is the whole price of using it.
    //
    // The PORTAL, not the ministry behind it — `data.economie.gouv.fr` is what a reader can look
    // up and what the customary "source :" line cites, the same reasoning that spells the licences
    // the way the licences spell themselves.
    DataSourceCredit(
        name = "data.economie.gouv.fr",
        purpose = Res.string.licenses_source_prix_carburants_purpose,
        license = "Licence Ouverte 2.0",
    ),
)

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
