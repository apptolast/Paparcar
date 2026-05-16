package io.apptolast.paparcar.presentation.home.sections.header.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import io.apptolast.paparcar.ui.components.GlassDefaults
import io.apptolast.paparcar.ui.components.GlassSurface
import io.apptolast.paparcar.ui.components.LocalMapInteracting
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.domain.model.SearchResult
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.home_search_clear_cd
import paparcar.composeapp.generated.resources.home_search_placeholder

// Matches MapCircleFab's default shadow so the search bar reads as a peer of
// the circular layer/GPS/parked-car FABs floating over the map. [HOME-DEPTH-001]
private val FLOATING_SHADOW_ELEVATION = 6.dp

// Match the map-type FAB icon size/tint so the search lupa reads as a peer of
// the layers/GPS/parked-car circular FABs on the opposite side.
private val SEARCH_LEADING_ICON_SIZE = 24.dp

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
    // The GlassSurface's own Surface colour fades to ALPHA_INTERACTING while the
    // user pans the map. Fading the TextField via Modifier.alpha (a graphicsLayer)
    // creates a faint composite rectangle that flickers around the text during the
    // transition — the layer's bounds become visible while its alpha animates.
    // Fade the inner colours instead so each piece (text, placeholder, icon)
    // mixes into the surface without any extra compositing layer.
    val isInteracting = LocalMapInteracting.current
    val contentAlpha by animateFloatAsState(
        targetValue = if (isInteracting) GlassDefaults.ALPHA_INTERACTING else GlassDefaults.ALPHA_IDLE,
        animationSpec = tween(
            durationMillis = if (isInteracting) GlassDefaults.FADE_IN_MS else GlassDefaults.FADE_OUT_MS,
            easing = FastOutSlowInEasing,
        ),
        label = "searchBarContentAlpha",
    )
    val onSurfaceFaded = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha)
    val onSurfaceVariantFaded = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha)

    Column(modifier = modifier) {
        GlassSurface(
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (results.isEmpty()) 16.dp else 0.dp,
                bottomEnd = if (results.isEmpty()) 16.dp else 0.dp,
            ),
            shadowElevation = FLOATING_SHADOW_ELEVATION,
            modifier = Modifier.fillMaxWidth(),
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(Res.string.home_search_placeholder), style = MaterialTheme.typography.bodyMedium)
                },
                leadingIcon = {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(SEARCH_LEADING_ICON_SIZE), strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = onSurfaceFaded,
                            modifier = Modifier.size(SEARCH_LEADING_ICON_SIZE),
                        )
                    }
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = onClear) {
                            Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.home_search_clear_cd))
                        }
                    }
                } else null,
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    errorContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = onSurfaceFaded,
                    unfocusedTextColor = onSurfaceFaded,
                    focusedPlaceholderColor = onSurfaceVariantFaded,
                    unfocusedPlaceholderColor = onSurfaceVariantFaded,
                    cursorColor = onSurfaceFaded,
                ),
            )
        }

        // ── Results card ──────────────────────────────────────────────
        if (results.isNotEmpty()) {
            GlassSurface(
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                colors = GlassDefaults.colors(
                    border = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                ),
                shadowElevation = FLOATING_SHADOW_ELEVATION,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    results.take(5).forEachIndexed { index, result ->
                        if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onResultClick(result) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Outlined.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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