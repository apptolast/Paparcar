package com.rndeveloper.paparcar.presentation.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.ui.components.PapIconTextRow
import com.rndeveloper.paparcar.ui.components.PapPremiumSurface
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.paywall_benefit_alerts
import paparcar.composeapp.generated.resources.paywall_benefit_alerts_note
import paparcar.composeapp.generated.resources.paywall_benefit_history
import paparcar.composeapp.generated.resources.paywall_benefit_history_note
import paparcar.composeapp.generated.resources.paywall_benefit_routes
import paparcar.composeapp.generated.resources.paywall_benefit_routes_note
import paparcar.composeapp.generated.resources.paywall_benefit_shared_car
import paparcar.composeapp.generated.resources.paywall_benefit_shared_car_note
import paparcar.composeapp.generated.resources.paywall_benefit_vehicles
import paparcar.composeapp.generated.resources.paywall_benefit_vehicles_note
import paparcar.composeapp.generated.resources.paywall_benefit_zones
import paparcar.composeapp.generated.resources.paywall_benefit_zones_note

// ─────────────────────────────────────────────────────────────────────────────
// What Plus adds — the one list, and the one row that draws it.
// [PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001] [MONEY-FOUNDERS-WELCOME-001]
//
// It lived inside `PaywallScreen.kt` while the paywall was its only reader. It has two more now:
// the welcome that celebrates a Plus nobody paid for, and the reminder that says when that Plus
// ends — and both of them have to name exactly what the paywall names.
//
// ⛔ Moved, not copied, and that IS the point of this file. A second hand-written list is the
// defect `PaywallBenefitsCoverageTest` exists to catch, read in the other direction: there, a gate
// charged for eleven days with nothing selling it; here, a promise the app had stopped keeping
// would have stayed alive in a congratulation nobody reviews. One list, three surfaces, no memory
// required.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * One line of the pitch: what Plus adds, and the reference it is measured against.
 *
 * The [note] is what the free plan gives you where free gives you a smaller version of the same
 * thing ("Free: the last 7 days"), and what the feature IS where free does not have it at all.
 * ⛔ It is never "Not included": a benefit listed under *what Plus adds* already says that, and a
 * line whose content is the absence of content was the emptiest half of the old table.
 *
 * [sells] are the paywall entrances this row answers — every [PaywallReason] whose gate this line
 * is the pitch for. **Declared and not inferred**, and that is the whole point of the field: the
 * correspondence between *what we charge for* and *what we sell* used to live nowhere, so a gate
 * could be born with no line naming it and nothing would say a word. One was —
 * [PaywallReason.ROUTE_CLOUD], shipped by a ticket that never touched this file, sold on the
 * website and nowhere here for eleven days. `PaywallBenefitsCoverageTest` reads this field and
 * fails the build now. [PAYWALL-A-GATE-THAT-CHARGES-MUST-BE-SOLD-001]
 *
 * A row may sell several reasons when they are ONE promise to the reader; what it may not do is
 * leave one unsold.
 */
internal class PlusBenefit(
    val icon: ImageVector,
    val title: StringResource,
    val note: StringResource,
    val sells: Set<PaywallReason>,
)

internal val PLUS_BENEFITS = listOf(
    // Memory first, then watching, then capacity — the order of the doctrine's own axis: free is
    // asking and being answered, Plus is being watched over and remembered for.
    //
    // 🔑 **History and figures are ONE row, because they are one gate.** They were two, and the
    // two carried the SAME note a line apart — "Free: the last 7 days", twice — which is not a
    // coincidence to tidy away but the fact itself: there is one window
    // (`FREE_HISTORY_WINDOW_DAYS`) and the figures are computed over whatever it lets through.
    // [STATS-FREE-SEES-EVERY-FIGURE-FOR-7-DAYS-001] Two rows stating one limit read as a copy bug
    // and spent a row saying it.
    //
    // ⛔ **The title LISTS what you get, because every shorter phrasing was a brochure.** The cell
    // this replaces said "Any period", which describes a date picker and undersells eleven
    // tickets. The first rewrite said "and what it learns from it" — and someone two days into
    // the app does not know what that means. *Learns* is the kind of word that sounds like value
    // and names none: it survives review because nobody can prove it wrong.
    //
    // So the row spends its width naming the actual outputs of the stats screen — spend, time
    // looking, best places, best hours, forecasts. Still no mechanism words: no "insights", no
    // "heatmap", no "clusters". Five nouns a first-week user can check against what they see.
    //
    // ⚠️ "places" here means the LOCATIONS you park in, not free community spots — so the
    // translations must avoid each language's reserved word for a spot (posto, plek, miejsce,
    // loc) and reach for its word for a place. [COPY-SPOT-IS-NOT-A-PARKING-001]
    PlusBenefit(
        PaparcarIcons.History,
        Res.string.paywall_benefit_history,
        Res.string.paywall_benefit_history_note,
        // One row, two reasons, because there is ONE gate behind both — see above.
        sells = setOf(PaywallReason.HISTORY, PaywallReason.STATS),
    ),
    // Memory's other half, and the reason it is a row of its own and not a clause of the one
    // above: it is a DIFFERENT gate, with its own switch in Settings and its own entrance
    // ([PaywallReason.ROUTE_CLOUD]). The history row sells a window in TIME; this one sells the
    // trace surviving the phone that drew it. [PRIV-A-TRACE-IS-NOT-A-STAT-001]
    //
    // ⚠️ The note says where free keeps them, not that free lacks them: a free user's routes are
    // recorded and drawn exactly the same — they just live on one phone and a reinstall is the
    // end of them. Writing it as an absence would be the "Not included" cell this class forbids,
    // and it would also be false.
    PlusBenefit(
        Icons.Rounded.Route,
        Res.string.paywall_benefit_routes,
        Res.string.paywall_benefit_routes_note,
        sells = setOf(PaywallReason.ROUTE_CLOUD),
    ),
    // Both watches and the recap in one line — they are one promise to the reader ("the app tells
    // me"), and splitting them would spend three rows on it.
    //
    // ⛔ "prices" alone does not say prices OF WHAT. The title carried it for one build and it was
    // unreadable: parking prices? the subscription? It names the fuel station now. The recap is
    // here and not with the history because this is the WATCHING row, and the month recap is the
    // only thing in the whole app that knocks on the door by itself.
    // [PLUS-A-PLAN-MUST-NAME-WHAT-IT-JUST-GAINED-001]
    PlusBenefit(
        Icons.Rounded.NotificationsActive,
        Res.string.paywall_benefit_alerts,
        Res.string.paywall_benefit_alerts_note,
        sells = setOf(PaywallReason.ZONE_ALERTS, PaywallReason.FUEL_ALERTS, PaywallReason.MONTH_RECAP),
    ),
    PlusBenefit(
        Icons.Rounded.Map,
        Res.string.paywall_benefit_zones,
        Res.string.paywall_benefit_zones_note,
        sells = setOf(PaywallReason.ZONES),
    ),
    // The widget folded in here: it is FREE by doctrine — the present is never hidden
    // [WIDGET-ACTIONS-002] — and what Plus adds to it is the other cars, which is this row.
    // [WIDGET-A-TRIP-IS-NOT-AN-EMPTY-WIDGET-001]
    PlusBenefit(
        Icons.Rounded.DirectionsCar,
        Res.string.paywall_benefit_vehicles,
        Res.string.paywall_benefit_vehicles_note,
        sells = setOf(PaywallReason.VEHICLES),
    ),
    // La feature estrella de Plus: el coche que conducen varios. [VEH-SHARED-001]
    PlusBenefit(
        PaparcarIcons.SharedCar,
        Res.string.paywall_benefit_shared_car,
        Res.string.paywall_benefit_shared_car_note,
        sells = setOf(PaywallReason.SHARED_CAR),
    ),
)

/**
 * One line of the pitch, drawn the same way wherever the pitch appears.
 *
 * @param showNote whether to draw the free-plan reference under the title. The two readings of one
 *   list: a **celebration** shows titles alone — there is nothing to compare against when you have
 *   all of it — and the **end-of-promo reminder** shows the notes, because "Free: the last 7 days"
 *   is literally the answer to the only question that reminder raises.
 */
@Composable
internal fun PlusBenefitRow(benefit: PlusBenefit, showNote: Boolean = false) {
    PapIconTextRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = BENEFIT_V_PAD_DP.dp),
        gap = BENEFIT_GAP_DP.dp,
        icon = {
            // ⛔ Not `PapIconTile`: that one takes a flat container colour and the Plus finish is a
            // brush, not a tone. `PapPremiumSurface` at row shape is the same recipe the crest and the
            // buy button use — the metal has exactly one definition. It also supplies `premiumInk` as
            // its content colour, so the glyph cannot be paired with the wrong ink by accident.
            PapPremiumSurface(
                modifier = Modifier.size(BENEFIT_TILE_DP.dp),
                shape = PapShapes.row,
            ) {
                Icon(
                    imageVector = benefit.icon,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.Center).size(BENEFIT_ICON_DP.dp),
                )
            }
        },
    ) {
        Column {
            // `rowTitle` and not `rowName`: these are structural titles of a row, not the names of
            // real things. The distinction is the type doctrine's, and it has one answer per call
            // site. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
            Text(
                text = stringResource(benefit.title),
                style = PaparcarType.current.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (showNote) {
                Text(
                    text = stringResource(benefit.note),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * El CIERRE de una lista recortada de beneficios: la última fila, con los puntos suspensivos
 * dibujados en el mismo tile de metal que las demás.
 *
 * Es una FILA y no un pie de lista, y esa es toda la decisión. Como texto suelto sangrado hasta
 * la columna de los títulos se quedaba flotando entre la lista y la línea de la fecha, sin
 * pertenecer a ninguna de las dos. Con su tile ocupa el hueco que le faltaba: la lista termina
 * donde el ojo ya esperaba otra fila.
 *
 * Vive aquí, y no en el diálogo que la usa, porque su geometría es la de [PlusBenefitRow] — mismo
 * tile, mismo hueco, mismo rol tipográfico. Escrita en el call site, cambiar el tamaño del tile la
 * descolocaría sin que nadie se enterara.
 *
 * ⛔ Existe porque el argumento contrario se falsificó mirando la pantalla. La bienvenida enseña
 * tres de las seis filas y se razonaba que «son todas las demás» ya lo decía su cuerpo —«Todo, sin
 * pagar nada»—; pero ese cuerpo va ARRIBA, antes de la cabecera, y al llegar al final de la lista
 * ya no se recuerda. El user leyó las tres filas como el regalo entero. Un recorte tiene que
 * decirse donde termina. [UI-THE-FOUNDERS-GIFT-EARNS-ITS-VOLUME-001]
 */
@Composable
internal fun PlusBenefitsMoreRow(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = BENEFIT_V_PAD_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(BENEFIT_GAP_DP.dp),
        // La etiqueta corta de cierre queda centrada con su tile.
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PapPremiumSurface(
            modifier = Modifier.size(BENEFIT_TILE_DP.dp),
            shape = PapShapes.row,
        ) {
            // Los puntos suspensivos, que es literalmente lo que dice la fila. Nivel 1: plumbing
            // de UI, no un concepto de Paparcar, así que Material Symbols Rounded.
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = null,
                modifier = Modifier.align(Alignment.Center).size(BENEFIT_ICON_DP.dp),
            )
        }
        // Mismo rol y mismo color que el título de una fila: esta línea ES un elemento de la lista.
        // En `label` y `onSurfaceVariant` rimaba con la línea de la fecha de debajo —otra voz, otro
        // trabajo— y las dos se leían como un pie suelto.
        Text(
            text = text,
            style = PaparcarType.current.rowTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

private const val BENEFIT_TILE_DP = 36
private const val BENEFIT_ICON_DP = 18
private const val BENEFIT_GAP_DP = 14
private const val BENEFIT_V_PAD_DP = 7
