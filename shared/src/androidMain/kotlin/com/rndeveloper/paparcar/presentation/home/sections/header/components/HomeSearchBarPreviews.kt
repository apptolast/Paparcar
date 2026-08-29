package com.rndeveloper.paparcar.presentation.home.sections.header.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.ui.theme.PaparcarTheme

private val sampleResults = listOf(
    SearchResult("Gran Vía, Madrid", 40.4203, -3.7058),
    SearchResult("Gran Vía de les Corts Catalanes, Barcelona", 41.3809, 2.1677),
)

@Composable
private fun Bar(
    results: List<SearchResult> = emptyList(),
    isSearching: Boolean = false,
    showNoResults: Boolean = false,
) {
    HomeSearchBar(
        query = "Gran Vía",
        results = results,
        isActive = true,
        isSearching = isSearching,
        showNoResults = showNoResults,
        onQueryChange = {},
        onResultClick = {},
        onClear = {},
    )
}

@Composable
private fun Gallery() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Resultados", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Bar(results = sampleResults)
        // [UX-PARK-FLOW-001 H3] Zero-result success shows an explicit row — never the same
        // silence as a geocoder failure (that one raises a snackbar).
        Text("Sin resultados", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Bar(showNoResults = true)
        Text("Buscando", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Bar(isSearching = true)
    }
}

@Preview(name = "Search bar · Dark", showBackground = true, heightDp = 420, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SearchBarDark() = PaparcarTheme(darkTheme = true) { Gallery() }

@Preview(name = "Search bar · Light", showBackground = true, heightDp = 420)
@Composable
private fun SearchBarLight() = PaparcarTheme(darkTheme = false) { Gallery() }
