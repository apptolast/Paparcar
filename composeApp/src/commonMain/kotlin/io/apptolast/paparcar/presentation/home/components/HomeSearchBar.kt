package io.apptolast.paparcar.presentation.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.SearchResult
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_search_clear
import paparcar.composeapp.generated.resources.home_search_placeholder

// Pill shape shared between the search surface and the result card
private val PillRadius = 28.dp

@Composable
internal fun HomeSearchBar(
    query: String,
    results: List<SearchResult>,
    isActive: Boolean,
    isSearching: Boolean,
    onQueryChange: (String) -> Unit,
    onResultClick: (SearchResult) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasResults = results.isNotEmpty()

    Column(modifier = modifier) {
        // ── Search input — pill that opens at the bottom when results are shown ──
        Surface(
            shape = RoundedCornerShape(
                topStart = PillRadius, topEnd = PillRadius,
                bottomStart = if (hasResults) 0.dp else PillRadius,
                bottomEnd = if (hasResults) 0.dp else PillRadius,
            ),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        stringResource(Res.string.home_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                leadingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = onClear) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(Res.string.home_search_clear),
                            )
                        }
                    }
                } else null,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )
        }

        // ── Results dropdown — completes the pill at the bottom ───────────────
        if (hasResults) {
            Card(
                shape = RoundedCornerShape(bottomStart = PillRadius, bottomEnd = PillRadius),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    // Thin rule visually separating input from results
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                    )
                    results.take(5).forEachIndexed { index, result ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            )
                        }
                        Surface(
                            onClick = { onResultClick(result) },
                            color = Color.Transparent,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = result.displayName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
