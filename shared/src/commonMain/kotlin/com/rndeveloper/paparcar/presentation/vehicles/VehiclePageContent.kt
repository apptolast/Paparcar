@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.rndeveloper.paparcar.presentation.vehicles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Adjust
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.AccessTimeFilled
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.isAudioRouteLinkRelevant
import com.rndeveloper.paparcar.ui.components.PapIconTextRow
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapNavChevron
import com.rndeveloper.paparcar.ui.components.PapNavChevronSize
import com.rndeveloper.paparcar.domain.detection.DetectionPause
import com.rndeveloper.paparcar.domain.model.UserParking
import com.rndeveloper.paparcar.presentation.util.locationDisplayText
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
import com.rndeveloper.paparcar.domain.model.VehicleMonitoringStatus
import com.rndeveloper.paparcar.domain.model.Vehicle
import com.rndeveloper.paparcar.domain.model.SharedCar
import com.rndeveloper.paparcar.domain.model.SharedCarMember
import com.rndeveloper.paparcar.domain.model.VehicleWithStats
import com.rndeveloper.paparcar.domain.model.monitoringStatus
import com.rndeveloper.paparcar.presentation.util.vehicleFuelMetaLine
import com.rndeveloper.paparcar.ui.components.VehicleIdentityHeader
import androidx.compose.ui.text.style.TextOverflow
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.VehicleWatch
import com.rndeveloper.paparcar.ui.theme.PapPageRhythm
import com.rndeveloper.paparcar.ui.theme.vehicleIdentityColor
import com.rndeveloper.paparcar.ui.theme.watch
import com.rndeveloper.paparcar.ui.theme.PapBorders
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.domain.sharedcar.CarWhereabouts
import com.rndeveloper.paparcar.domain.sharedcar.carWhereabouts
import com.rndeveloper.paparcar.presentation.vehicles.components.SharedCarSection
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.my_car_edit_vehicle
import paparcar.composeapp.generated.resources.shared_car_title
import paparcar.composeapp.generated.resources.shared_car_where_parked_by
import paparcar.composeapp.generated.resources.vehicle_link_car_action
import paparcar.composeapp.generated.resources.vehicle_link_car_linked
import paparcar.composeapp.generated.resources.vehicle_set_active_action
import paparcar.composeapp.generated.resources.vehicle_state_last_parking
import paparcar.composeapp.generated.resources.vehicle_state_never_parked
import paparcar.composeapp.generated.resources.vehicle_state_parked
import paparcar.composeapp.generated.resources.vehicle_view_on_map

@Composable
internal fun VehiclePageContent(
    vehicleWithStats: VehicleWithStats,
    historyState: HistoryState,
    isSettingActive: Boolean,
    isPlus: Boolean,
    /** Why the next trip will not be measured, or null when it will.
     *  [STATS-A-CHIP-YOU-CANNOT-MEASURE-MUST-NOT-REDRAW-001] */
    detectionPause: DetectionPause?,
    onRequestSetActive: () -> Unit,
    contentPadding: PaddingValues,
    onScrolledToTop: () -> Unit,
    onIntent: (VehiclesIntent) -> Unit,
    /** El grupo de ESTE coche, o null si no está compartido. [VEH-SHARED-001] */
    sharedCar: SharedCar? = null,
    members: List<SharedCarMember> = emptyList(),
    currentUid: String? = null,
    isOwnerOfShared: Boolean = false,
    sharedHistoryMineOnly: Boolean = false,
    sharedWorking: Boolean = false,
    /** Cómo se firma cada fila del historial cuando la lista es de varios conductores. */
    sharedNote: ((UserParking) -> SharedRowNote?)? = null,
) {
    val vehicleId = vehicleWithStats.vehicle.id

    HistoryContent(
        state = historyState,
        contentPadding = contentPadding,
        onViewOnMap = { lat, lon, sessionId -> onIntent(VehiclesIntent.ViewOnMap(lat, lon, sessionId)) },
        onFilterSelected = { filter -> onIntent(VehiclesIntent.SetStatsPeriod(filter)) },
        onMetricSelected = { metric -> onIntent(VehiclesIntent.SetChartMetric(metric)) },
        onOpenPlusPaywall = { onIntent(VehiclesIntent.OpenPlusPaywall) },
        isPlus = isPlus,
        detectionPause = detectionPause,
        onEnableDetection = { onIntent(VehiclesIntent.EnableAutoDetection) },
        onOpenStatsPaywall = { onIntent(VehiclesIntent.OpenStatsPaywall) },
        onOpenStatsPanel = { onIntent(VehiclesIntent.OpenStatsPanel(vehicleId)) },
        onScrolledToTop = onScrolledToTop,
        sharedNote = sharedNote,
        // [DET-A-STOP-AT-A-PUMP-IS-NOT-A-PARKING-001] La MISMA pregunta que ofrece Home, resuelta
        // sobre el MISMO registro: el intent acaba en el único caso de uso que la contesta.
        onAnswerRefuelStop = { sessionId, refuelled ->
            onIntent(VehiclesIntent.AnswerRefuelStop(sessionId, refuelled))
        },
        modifier = Modifier.fillMaxSize(),
        header = {
          // El mismo aire que Ajustes pone entre dos de sus cards: la hero y la sección del grupo
          // son dos bloques hermanos de esta página, no dos superficies sueltas. Eran 12 dp
          // elegidos aquí, que es lo que el user vio como «en vehicles el espaciado vertical es más
          // alto». [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001]
          androidx.compose.foundation.layout.Column(
              modifier = Modifier.padding(horizontal = PapPageRhythm.sideInset),
              verticalArrangement = Arrangement.spacedBy(PapPageRhythm.block),
          ) {
            VehicleHeroCard(
                vehicleWithStats = vehicleWithStats,
                isShared = sharedCar != null,
                // UNA sola autoridad sobre donde esta el coche, y de paso lo unico que un miembro
                // recien llegado puede contar: no tiene sesiones propias de este coche.
                // [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001]
                whereabouts = carWhereabouts(
                    ownLastSession = vehicleWithStats.lastSession,
                    sharedCar = sharedCar,
                    currentUid = currentUid,
                    vehicleId = vehicleId,
                ),
                isSettingActive = isSettingActive,
                // Making a vehicle active declares "I drive this" — confirm first (the screen owns
                // the consequence dialog), never switch silently. [VEH-ACTIVE-FENCE-001]
                onSetActive = onRequestSetActive,
                onEdit = { onIntent(VehiclesIntent.EditVehicle(vehicleId)) },
                // [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] Vía A: explicit "link this car" — the
                // row itself only paints where the platform links by audio route (iOS).
                onLinkCarAudio = { onIntent(VehiclesIntent.LinkCarAudio(vehicleId)) },
                // The way back to the map, on the SAME lambda the history rows use — one gesture,
                // one destination, so the live session and a past one can never drift apart.
                // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
                // ⛔ DOS destinos, porque son dos cosas. Un aparcamiento VIVO se mira en Home
                // —donde se puede uno ir, navegar o corregir el pin—, que es la regla editorial del
                // proyecto: si pasa AHORA, Home. Una sesión CERRADA no tiene pin en Home, así que va
                // al detalle del historial, en la misma lambda que sus propias filas.
                // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
                onViewOnMap = { session ->
                    onIntent(
                        if (session.isActive) {
                            VehiclesIntent.ViewLiveParkingOnMap(session.id)
                        } else {
                            VehiclesIntent.ViewOnMap(
                                lat = session.location.latitude,
                                lon = session.location.longitude,
                                sessionId = session.id,
                            )
                        },
                    )
                },
            )
            SharedCarSection(
                sharedCar = sharedCar,
                members = members,
                currentUid = currentUid,
                isOwner = isOwnerOfShared,
                mineOnly = sharedHistoryMineOnly,
                working = sharedWorking,
                onOfferShare = { onIntent(VehiclesIntent.ConfirmShareCar(vehicleId)) },
                onOpenMembers = { onIntent(VehiclesIntent.ShowMembers(vehicleId)) },
                onSetScope = { mineOnly -> onIntent(VehiclesIntent.SetSharedHistoryScope(mineOnly)) },
            )
          }
        },
    )
}


/**
 * What this car is doing, in one line: parked at a place, last parked a while ago, or never parked.
 * [STATS-UI-001]
 *
 * The ACTIVE session outranks the last one — a car parked since Tuesday is "aparcado en X", not
 * "último aparcamiento hace 3 días", and that difference is the whole point of a state line. When
 * it IS parked the line says WHERE, with the same place glyph and the same resolved name Home puts
 * on its card, because a user who reads "Calle de Alcalá 42 · hace 2 h" in one screen should not
 * have to re-read a different sentence in the other. (User, 08-09-2026.)
 *
 * "Never parked" is itself a state and gets its own words: a brand-new vehicle showing nothing at
 * all reads as a card that failed to load.
 */
@Composable
private fun VehicleStateLine(
    lastSession: UserParking?,
    watch: VehicleWatch,
    onViewOnMap: (UserParking) -> Unit,
    /** Quien dejo el coche ahi, solo cuando no fuiste tu. Es el unico dato que justificaba una
     *  segunda card diciendo donde esta el coche, y ahora vive en la unica que lo dice.
     *  [SHARED-A-GROUP-YOU-CANNOT-JOIN-IS-NOT-A-GROUP-001] */
    parkedByName: String? = null,
) {
    val parkedNow = lastSession?.isActive == true
    // The glyph carries the vehicle's identity colour; the words stay onSurface. Same split the
    // whole app uses — the state is written, never tinted. [UI-COLOR-DOCTRINE-001]
    val tint = vehicleIdentityColor(watch)
    val place = lastSession?.let { locationDisplayText(placeInfo = it.placeInfo, address = it.address) }

    // ⛔ El enlace al mapa NO es un botón aparte. Lo fue un rato —un link verde bajo estas líneas— y
    // no encajaba: metía un cuarto elemento suelto en una card cuyo bloque de estado ya nombra el
    // sitio del que habla. Ahora el bloque ENTERO se toca y lleva su chevron, que es como la app
    // dice "esta fila abre algo" en el modal. La dirección ES el enlace, y de paso el área táctil
    // pasa a ser las dos líneas en vez de un texto de 20 dp.
    // [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001] [UI-LIST-ITEM-001]
    Row(
        modifier = Modifier
            .then(
                // ⛔ SIN `clip`. Llevó un rato `PapShapes.chip`, que es una píldora de 999 dp: sobre
                // una fila de dos líneas el arco entra decenas de dp por cada extremo y RECORTA lo
                // que viva en las esquinas — o sea el pin y el reloj, que arrancan pegados al
                // margen izquierdo. Se veían mordidos en device (user, 15-09). El ripple
                // rectangular de la fila es además lo que hace cualquier fila de lista.
                if (lastSession != null) {
                    Modifier.clickable { onViewOnMap(lastSession) }
                } else Modifier,
            )
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(STATE_LINE_GAP.dp),
        ) {
            // WHERE, on its own line with the place glyph — and only when there IS a place to name.
            // It used to share one sentence with the time behind a single icon, which made that icon
            // mean "place" here and "time" one state later; now the pin is always the place and the
            // clock is always the time. [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001]
            if (parkedNow && place != null) {
                VehicleStateRow(
                    // An approximate close saved an AREA, and the zone glyph is how the rest of the
                    // app says so. [UI-APPROXIMATE-ZONE-IN-HISTORY-001]
                    icon = if (lastSession.isApproximate) Icons.Rounded.Adjust else Icons.Rounded.LocationOn,
                    text = place,
                    tint = tint,
                    // Two lines before truncating: an address rarely fits one line on a narrow
                    // device, and eating the WHERE to keep the height is backwards — the place is
                    // the payload of this line. [UI-STATS-HEADER-WRAPS-THE-WRONG-SIDE-001]
                    maxLines = PLACE_MAX_LINES,
                )
            }
            // WHEN — always present, always the clock. The state stays WRITTEN in it ("Parked · 2 h
            // ago"), so a car with no resolved address still says what it is doing.
            VehicleStateRow(
                icon = Icons.Rounded.AccessTimeFilled,
                text = when {
                    lastSession == null -> stringResource(Res.string.vehicle_state_never_parked)
                    parkedNow -> stringResource(
                        Res.string.vehicle_state_parked,
                        relativeTimeText(lastSession.location.timestamp),
                    ) + parkedByName?.let {
                        " · " + stringResource(Res.string.shared_car_where_parked_by, it)
                    }.orEmpty()
                    else -> stringResource(
                        Res.string.vehicle_state_last_parking,
                        relativeTimeText(lastSession.location.timestamp),
                    )
                },
                tint = tint,
            )
        }
        // Chrome, no identidad: el chevron dice "se abre", no de quién es el coche.
        // [UI-COLOR-DOCTRINE-001]
        //
        // ⛔ El canónico, no uno propio. Era un `ChevronRight` de 20 dp en `onSurfaceVariant`
        // dibujado a mano, mientras las filas de Historial de ESTA MISMA pantalla usaban
        // `PapNavChevron`. Dos glifos distintos para «esto abre algo» a dos dedos de distancia.
        // [UI-A-CHEVRON-MUST-SIT-ON-THE-CARD-RAIL-001]
        if (lastSession != null) {
            PapNavChevron(contentDescription = stringResource(Res.string.vehicle_view_on_map))
        }
    }
}

/** One state row of the ficha: its icon, its fact. */
@Composable
private fun VehicleStateRow(
    icon: ImageVector,
    text: String,
    tint: Color,
    maxLines: Int = 1,
) {
    PapIconTextRow(
        gap = STATE_ICON_GAP.dp,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(STATE_ICON_DP.dp),
            )
        },
    ) {
        Text(
            text = text,
            // `meta` (LECTURA) rather than `caption`: this is not more quiet metadata about the car,
            // it is what the car is DOING — and onSurface separates it from the two grey attribute
            // lines above without spending a colour on it.
            style = PaparcarType.current.meta,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hero card — WHO this car is and HOW it is being watched. Zone 01 of the ficha
// layout signed off on 08-09-2026: no figures of USE live here any more.
//
// It used to close with three counters (total sessions · last session · spots
// shared) and that row is what gave the screen two clocks: the card counted the
// car's whole life while the Activity card below counted the selected period,
// and neither said which. The counters moved to the figures card (period) and
// its footer line (lifetime, and it says so); "last session" was never a
// statistic at all — it is STATE, so it reads as one line under the car's data.
// [STATS-UI-001]
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun VehicleHeroCard(
    vehicleWithStats: VehicleWithStats,
    isShared: Boolean,
    whereabouts: CarWhereabouts?,
    isSettingActive: Boolean,
    onSetActive: () -> Unit,
    onEdit: () -> Unit,
    onLinkCarAudio: () -> Unit,
    onViewOnMap: (UserParking) -> Unit,
) {
    val vehicle = vehicleWithStats.vehicle
    val monitoring = vehicle.monitoringStatus()
    val isInactive = monitoring == VehicleMonitoringStatus.Inactive
    val cs = MaterialTheme.colorScheme

    // ⛔ Sin margen propio. Llevaba `padding(16, 16, top = 8, bottom = 8)` DENTRO del componente,
    // o sea su propio hueco con el mundo: sumado al `spacedBy` de la cabecera daba **16 dp** a la
    // vista entre esta card y la del grupo, el doble de la ley, y nadie podía verlo leyendo el
    // contenedor. Un bloque no se separa a sí mismo: el hueco es del que los ordena.
    // [UI-A-PAGE-KEEPS-ONE-VERTICAL-RHYTHM-001]
    Surface(
        modifier = Modifier
            .fillMaxWidth(),
        shape = PapShapes.card,
        color = cs.surfaceContainerHigh,
        // ⛔ Neutral border, not the watch colour. This card already says "vigilado por Bluetooth"
        // three other ways — the dot on its tab chip, the glyph before the name and the state
        // line's own icon — and of the four the frame is the only one nobody looks AT: it surrounds
        // everything, so instead of delivering a fact it tints the reading of the fuel line and the
        // state, which are not about the watch tier at all. The card keeps an edge; it stops
        // shouting a method. HOME KEEPS ITS COLOURED BORDER: there several cards coexist and the
        // frame does real work telling one from another. [UI-IDENTITY-IS-SAID-ONCE-001]
        border = BorderStroke(PapBorders.thin, cs.outlineVariant),
    ) {
        Column {
            // Shared identity anatomy (tile glyph + name + size chip + status pin) — single source
            // of truth with the Home single-vehicle card. Edit rides as the trailing action, in a
            // compact box so it doesn't push the meta row with a 48dp dead zone.
            VehicleIdentityHeader(
                vehicle = vehicle,
                modifier = Modifier.padding(
                    start = CARD_PADDING.dp, end = CARD_PADDING.dp,
                    top = IDENTITY_TOP_PAD.dp, bottom = IDENTITY_BOTTOM_PAD.dp,
                ),
                // The fuel data joins the vehicle-data column, right under carbody · size — an
                // attribute of the car, not a floating meta line. [VEH-FUEL-001]
                metaSecondLine = vehicleFuelMetaLine(vehicle),
                // La marca de «lo conduce más gente», junto al nombre y en gris de sistema: es
                // plumbing de UI, no identidad de vigilancia. [VEH-SHARED-001]
                nameSuffix = if (isShared) {
                    {
                        Icon(
                            imageVector = PaparcarIcons.SharedCar,
                            contentDescription = stringResource(Res.string.shared_car_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(SHARED_MARK_DP.dp),
                        )
                    }
                } else null,
                statusLine = {
                    // El bloque de estado es además la puerta al mapa: dice dónde está el coche, así
                    // que tocarlo lleva allí. [UI-A-PARKED-CARD-OFFERS-THE-WAY-ACROSS-001]
                    VehicleStateLine(
                        lastSession = whereabouts?.session,
                        watch = monitoring.watch(),
                        onViewOnMap = onViewOnMap,
                        parkedByName = whereabouts?.parkedByName,
                    )
                },
                trailing = {
                    // El raíl de acciones de esta card es el EJE DEL GLIFO —el mismo en el que caen
                    // el chevron del bloque de estado y el de «Compartir este coche»—, no el borde
                    // de la caja táctil. El lápiz vive en una caja de 40 dp que no se puede
                    // encoger sin bajar del mínimo táctil, así que la caja SOBRESALE lo justo para
                    // que su glifo aterrice en el eje: media caja menos medio chevron. Calculado,
                    // no escrito a mano — si el chevron cambia de tamaño, el lápiz lo sigue.
                    // [UI-A-CHEVRON-MUST-SIT-ON-THE-CARD-RAIL-001]
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(EDIT_BOX_DP.dp)
                            .offset(x = (EDIT_BOX_DP.dp - PapNavChevronSize) / 2),
                    ) {
                        Icon(
                            Icons.Rounded.Edit,
                            contentDescription = stringResource(Res.string.my_car_edit_vehicle),
                            tint = cs.onSurface.copy(alpha = EDIT_ICON_ALPHA),
                        )
                    }
                },
            )

            // "Set as active" is its own row — and only for an inactive vehicle. Active / Bluetooth
            // vehicles are already the tracked one, so the action never appears for them.
            if (isInactive) {
                PapDivider()
                SetActiveRow(isLoading = isSettingActive, onClick = onSetActive)
            }
            // [IOS-DET-AUDIO-ROUTE-PROVES-WHOSE-CAR-001] Vía A: link this car by reading the live
            // audio route. Only where the platform links that way (iOS) — on Android the ACL lane
            // owns pairing and this row never appears. Reads "linked" once a MAC is stored.
            if (isAudioRouteLinkRelevant) {
                PapDivider()
                LinkCarRow(isLinked = vehicle.bluetoothDeviceId != null, onClick = onLinkCarAudio)
            }
        }
    }
}

@Composable
private fun LinkCarRow(isLinked: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = cs.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SET_ACTIVE_PAD.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SET_ACTIVE_GAP.dp, Alignment.CenterHorizontally),
        ) {
            Icon(
                imageVector = if (isLinked) Icons.Rounded.BluetoothConnected else Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = if (isLinked) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.size(SET_ACTIVE_ICON_DP.dp),
            )
            Text(
                text = stringResource(
                    if (isLinked) Res.string.vehicle_link_car_linked else Res.string.vehicle_link_car_action,
                ),
                style = PaparcarType.current.cta,
                color = if (isLinked) cs.onSurfaceVariant else PapColor.actionText,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Set-active row — full-width action, inactive vehicles only
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SetActiveRow(isLoading: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = !isLoading,
        modifier = Modifier.fillMaxWidth(),
        color = cs.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(SET_ACTIVE_PAD.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SET_ACTIVE_GAP.dp, Alignment.CenterHorizontally),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(SET_ACTIVE_ICON_DP.dp),
                    strokeWidth = SET_ACTIVE_SPINNER_STROKE.dp,
                    color = cs.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(SET_ACTIVE_ICON_DP.dp),
                )
            }
            Text(
                text = stringResource(Res.string.vehicle_set_active_action),
                // An action row is a button → cta, the app's button convention (PapButton);
                // not rowName (MARCA), which is for identity titles. [CARD-ONE-BADGE-001]
                style = PaparcarType.current.cta,
                color = PapColor.actionText,
            )
        }
    }
}

private const val STATE_ICON_DP = 15
private const val STATE_ICON_GAP = 6
/** Aire entre el lugar y el tiempo: son dos mitades del mismo hecho, no dos bloques.
 *  [UI-A-PARKED-CARD-SAYS-WHERE-AND-WHEN-IN-TWO-LINES-001] */
private const val STATE_LINE_GAP = 4
private const val PLACE_MAX_LINES = 2
private const val CARD_PADDING = 16
// Identity block paddings per the design spec (15 top / 13 bottom).
private const val IDENTITY_TOP_PAD = 15
private const val IDENTITY_BOTTOM_PAD = 13
// Compact edit box — kills the invisible 48dp dead zone that misaligned the card's right edge.
private const val EDIT_BOX_DP = 40
private const val EDIT_ICON_ALPHA = 0.7f
private const val STAT_CELL_V_PAD = 13
private const val STAT_CELL_H_PAD = 8
private const val STAT_DIVIDER_V_PAD = 13
private const val STAT_ICON_DP = 17
private const val STAT_ICON_GAP = 5
// La altura de mayuscula de la voz CIFRA vivio aqui como constante (0.700, el cap de la condensada
// de entonces) hasta que cambiar de familia despego el icono de los digitos: era un dato de la
// FUENTE, no de esta pantalla. Ahora se lee de PapFontSet (hoy 0.745).
// [UI-STAT-ICON-CENTERS-ON-DIGITS-001]
private const val STAT_LABEL_GAP = 5
private const val SET_ACTIVE_PAD = 13
private const val SET_ACTIVE_GAP = 9
private const val SET_ACTIVE_ICON_DP = 18
private const val SET_ACTIVE_SPINNER_STROKE = 2
/** La marca de coche compartido junto al nombre: del tamaño de una mayúscula, no de un botón. */
private const val SHARED_MARK_DP = 18
