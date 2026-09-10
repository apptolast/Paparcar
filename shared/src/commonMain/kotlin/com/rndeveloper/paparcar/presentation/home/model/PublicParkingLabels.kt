package com.rndeveloper.paparcar.presentation.home.model

import androidx.compose.runtime.Composable
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.parking_kind_garage_boxes
import paparcar.composeapp.generated.resources.parking_kind_multi_storey
import paparcar.composeapp.generated.resources.parking_kind_surface
import paparcar.composeapp.generated.resources.parking_kind_underground
import paparcar.composeapp.generated.resources.parking_kind_unknown

/**
 * What a car park is CALLED when nobody named it — the row title, the peek eyebrow and the map's
 * accessibility text all say it the same way. [PARKINGS-ON-THE-MAP-001]
 *
 * It lives in presentation because it is a translated string, and `PublicParkingKind` is domain,
 * which is Kotlin with no resources in it. One function so the three surfaces cannot drift.
 */
@Composable
fun publicParkingKindLabel(kind: PublicParkingKind): String = stringResource(
    when (kind) {
        PublicParkingKind.UNDERGROUND -> Res.string.parking_kind_underground
        PublicParkingKind.MULTI_STOREY -> Res.string.parking_kind_multi_storey
        PublicParkingKind.SURFACE -> Res.string.parking_kind_surface
        PublicParkingKind.GARAGE_BOXES -> Res.string.parking_kind_garage_boxes
        PublicParkingKind.UNKNOWN -> Res.string.parking_kind_unknown
    },
)
