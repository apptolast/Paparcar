package com.rndeveloper.paparcar.presentation.home.sections.header

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swmansion.kmpmaps.core.MapType
import com.rndeveloper.paparcar.domain.model.SearchResult
import com.rndeveloper.paparcar.domain.model.Zone
import com.rndeveloper.paparcar.presentation.home.HomeHeaderSlice
import com.rndeveloper.paparcar.presentation.home.sections.header.components.HomeSearchBar
import com.rndeveloper.paparcar.presentation.util.MAP_FLOATING_SIDE_INSET_DP
import com.rndeveloper.paparcar.presentation.home.sections.header.components.MapTypeToggle
import com.rndeveloper.paparcar.ui.theme.PapControlHeight
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PapMotion
import com.rndeveloper.paparcar.ui.theme.PapPageRhythm
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.AddZoneChip
import com.rndeveloper.paparcar.presentation.home.sections.sheet.components.ZoneChip

@Composable
internal fun HomeHeaderSection(
    slice: HomeHeaderSlice,
    onSearchQueryChanged: (String) -> Unit,
    onSearchResultClick: (SearchResult) -> Unit,
    onSearchClear: () -> Unit,
    onMapTypeSelected: (MapType) -> Unit,
    onSelectZone: (String) -> Unit,
    onAddZone: () -> Unit,
    onEditZone: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** A checklist step is pointing at the add-zone chip. [FIRST-STEPS-SHOW-WHERE-NOT-OPEN-001] */
    highlightAddZone: Boolean = false,
) {
    // Status-bar inset is applied by the parent column in HomeScreen (which also hosts the
    // persistent detection banner above this header). [DET-READY-001g]
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MAP_FLOATING_SIDE_INSET_DP.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Two controls, and only two: the search bar and the map-type toggle. A third 56dp
            // circle here squeezed the search bar below what its placeholder needs and wrapped it
            // to two lines — measured, and the reason nothing else is allowed into this row.
            // [UI-HOME-SEARCH-BAR-MUST-STAY-ONE-LINE-001]
            HomeSearchBar(
                query = slice.searchQuery,
                results = slice.searchResults,
                isActive = slice.isSearchActive,
                isSearching = slice.isSearching,
                showNoResults = slice.searchNoResults,
                onQueryChange = onSearchQueryChanged,
                onResultClick = onSearchResultClick,
                onClear = onSearchClear,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            MapTypeToggle(
                currentType = slice.mapType,
                onTypeSelected = onMapTypeSelected,
            )
        }
        // ⛔ Las zonas NO dependen del permiso de ubicación: una zona es un sitio TUYO que ya has
        // declarado — «donde vivo», «el trabajo» —, no tu posición de ahora. Y sin ubicación es
        // justo cuando más falta hace poder saltar a una: es la única forma que le queda a la app de
        // llevarte a un sitio que conoces. [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001]
        //
        // Se conserva el AnimatedVisibility —y no se sustituye por un `if`— porque la fila sigue
        // teniendo dos caras (con zonas / invitación) y entra y sale con la cabecera.
        AnimatedVisibility(
            visible = true,
            // Chips hang under the search bar → grow/collapse from the top edge.
            enter = fadeIn(PapMotion.medium()) + expandVertically(PapMotion.medium(), expandFrom = Alignment.Top),
            exit = fadeOut(PapMotion.medium()) + shrinkVertically(PapMotion.medium(), shrinkTowards = Alignment.Top),
        ) {
            // ONE row in every state. The empty state used to be a separate composable with its
            // own padding, so the add control sat at a different y depending on whether you had
            // zones — and jumped the moment you saved your first one. The invitation is now just
            // the same trailing chip, expanded. [UI-THE-ADD-ZONE-CHIP-IS-ONE-CHIP-IN-EVERY-STATE-001]
            //
            // Session-scoped dismissal (no preference plumbing): an invitation may come back on a
            // fresh launch. [UI-SHEET-003]
            var invitationDismissed by rememberSaveable { mutableStateOf(false) }
            HeaderZoneChips(
                zones = slice.zones,
                // Pointed at by the tutorial, the chip opens into its invitation so it says what it is.
                inviteToAddZone = (slice.zones.isEmpty() && !invitationDismissed) || highlightAddZone,
                highlightAddZone = highlightAddZone,
                onSelectZone = onSelectZone,
                onAddZone = onAddZone,
                onEditZone = onEditZone,
                onDismissInvitation = { invitationDismissed = true },
            )
        }
        // Two lanes, and only two. The GPS pill used to hold a third one — 34dp reserved full time
        // so that a degraded fix arriving could not push the header down. Reserving was the right
        // fix for a lane INSIDE this measured column; the mistake was the column. The pill now
        // floats in the map's FAB band, where appearing pushes nobody, there is nothing to reserve,
        // and it survives the pin modes this header hides itself for.
        // [UI-A-WARNING-COSTS-NOTHING-WHEN-SILENT-001] [UI-HEADER-PILLS-SHARE-ONE-GEOMETRY-001]
    }
}

@Composable
private fun HeaderZoneChips(
    zones: List<Zone>,
    /** Show the add chip expanded into its invitation — only while there is no zone to point at. */
    inviteToAddZone: Boolean,
    onSelectZone: (String) -> Unit,
    onAddZone: () -> Unit,
    onEditZone: (String) -> Unit,
    onDismissInvitation: () -> Unit,
    highlightAddZone: Boolean,
) {
    val rowState = rememberLazyListState()
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Inset lives in contentPadding (not an outer padding) so the FAB shadow of
        // the first/last chip has room and isn't clipped at the row edge; chips also
        // scroll edge-to-edge. [MAP-GLASS-001]
        contentPadding = PaddingValues(
            horizontal = MAP_FLOATING_SIDE_INSET_DP.dp,
            // ⚠️ La invitación expandida NO es un chip de 34: crece con su texto (44 dp medidos en
            // EN, más en los locales largos), así que su overhang es menor y desconocido. Ahí el
            // hueco se escribe entero y se ven ~10 dp en vez de 8 — el aire de más lo arrastra el
            // chip, no el hueco. Suponer 34 dejaba la invitación a 3 dp del buscador, medido.
            vertical = if (inviteToAddZone) PapPageRhythm.block else CHIP_TO_SEARCH_BAR_GAP,
        ),
    ) {
        items(zones, key = { it.id }) { zone ->
            ZoneChip(
                label = zone.name,
                iconKey = zone.iconKey,
                isPrivate = zone.isPrivate,
                onClick = remember(zone.id, onSelectZone) { { onSelectZone(zone.id) } },
                onEdit = remember(zone.id, onEditZone) { { onEditZone(zone.id) } },
            )
        }
        item {
            AddZoneChip(
                expanded = inviteToAddZone,
                onAddZone = onAddZone,
                onDismiss = onDismissInvitation,
                highlighted = highlightAddZone,
            )
        }
    }
}

/**
 * El hueco entre el buscador y la fila de zonas, pedido por lo que se VE (8 dp, el mismo que
 * separa dos cards en Ajustes) y no por lo que se escribe.
 * [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001]
 *
 * Eran 8 dp a pelo, con un nombre —*shadow room*— que describía el otro trabajo que hace este
 * `contentPadding`: dejar sitio a la sombra de FAB del primer chip para que no se recorte contra el
 * borde de la fila. Los dos trabajos caben en el mismo número, pero el que manda es el hueco, y
 * el hueco no eran 8: un chip de 34 dp pinta su altura CENTRADA dentro de los 48 del mínimo táctil
 * de Material, así que arrastraba 7 dp más por arriba y se veían **15** — el doble de lo que el
 * resto de la app llama «dos bloques seguidos», y lo que el user señaló mirando esta pantalla
 * (19-10). [UI-THE-SPACING-SWEEP-IS-SCREEN-BY-SCREEN-001]
 *
 * ⛔ El overhang NO se quita desactivando el mínimo táctil: son 48 dp de área de toque. Lo que se
 * corrige es la cuenta — y de paso esos 7 dp invisibles son sitio de sobra para la sombra.
 */
private val CHIP_TO_SEARCH_BAR_GAP = PapGap.betweenControls(
    visible = PapPageRhythm.block,
    controlHeight = PapControlHeight.chip,
    sides = 1,
)

