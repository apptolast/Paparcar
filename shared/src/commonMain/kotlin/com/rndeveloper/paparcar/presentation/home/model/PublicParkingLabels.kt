package com.rndeveloper.paparcar.presentation.home.model

import androidx.compose.runtime.Composable
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.ParkingHeadline
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.headline
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.parking_fee_free
import paparcar.composeapp.generated.resources.parking_fee_paid
import paparcar.composeapp.generated.resources.parking_kind_garage_boxes
import paparcar.composeapp.generated.resources.parking_kind_multi_storey
import paparcar.composeapp.generated.resources.parking_kind_surface
import paparcar.composeapp.generated.resources.parking_kind_underground
import paparcar.composeapp.generated.resources.parking_kind_unknown
import paparcar.composeapp.generated.resources.parking_occupancy_free
import paparcar.composeapp.generated.resources.parking_occupancy_full
import paparcar.composeapp.generated.resources.parking_marker_spaces_left

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

@Composable
internal fun publicParkingFeeLabel(fee: FeeInfo): String? = when (fee) {
    FeeInfo.PAID -> stringResource(Res.string.parking_fee_paid)
    FeeInfo.FREE -> stringResource(Res.string.parking_fee_free)
    FeeInfo.UNKNOWN -> null
}

@Composable
internal fun PublicParking.occupancyLabel(nowMs: Long, compact: Boolean = false): String? =
    when (val headline = headline(nowMs)) {
        is ParkingHeadline.FreeSpaces -> if (compact) {
            pluralStringResource(Res.plurals.parking_marker_spaces_left, headline.free, headline.free)
        } else {
            stringResource(Res.string.parking_occupancy_free, headline.free)
        }
        is ParkingHeadline.Full -> stringResource(Res.string.parking_occupancy_full)
        else -> null
    }
