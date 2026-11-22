package com.rndeveloper.paparcar.presentation.home.model

import androidx.compose.runtime.Composable
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.parkings.OccupancyStamp
import com.rndeveloper.paparcar.domain.parkings.ParkingHeadline
import com.rndeveloper.paparcar.domain.parkings.PublicParking
import com.rndeveloper.paparcar.domain.parkings.PublicParkingKind
import com.rndeveloper.paparcar.domain.parkings.headline
import com.rndeveloper.paparcar.domain.parkings.occupancyStamp
import com.rndeveloper.paparcar.presentation.util.clockTimeWithDate
import com.rndeveloper.paparcar.presentation.util.relativeTimeText
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
import paparcar.composeapp.generated.resources.parking_occupancy_updated_at
import paparcar.composeapp.generated.resources.parking_occupancy_updated_last
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

/**
 * **Cuándo se actualizó la ocupación que se está enseñando** — «Actualizado: hace 3 min» mientras la
 * cifra manda, y «Última actualización: 19 nov 2026 a las 11:35» cuando ya ha caducado.
 * [PARKINGS-SHOW-FEE-AND-OCCUPANCY-001]
 *
 * Vive aquí, al lado de [occupancyLabel], porque es el OTRO lado de la misma afirmación y los dos
 * tienen que decidirse una sola vez: la ficha pedía la cifra a una función compartida y el sello se
 * lo habría escrito por su cuenta.
 *
 * ⛔ **Las dos formas, y el umbral que las separa NO es nuevo: es el de la escalera.** Decisión del
 * user: *«si está actualizado relativamente poco podemos poner hace… en vez de la hora»*. Y
 * «relativamente poco» ya tiene una definición en este código — es exactamente **mientras la cifra
 * siga mandando** ([OccupancyStamp.Current]: 10 min sellado por la fuente, 4 si lo sellamos
 * nosotros). Así que no hay constante nueva que elegir ni que mantener en sync:
 *
 * ```
 * Current  →  «hace 3 min»   · un lapso corto se lee de un vistazo y no obliga a mirar el reloj
 * Expired  →  «11:35»        · con su fecha si no es de hoy
 * ```
 *
 * ⛔ **Y el salto entre las dos NO es un cambio de criterio: es el MISMO evento.** Al cruzar el
 * umbral, la cifra desaparece de la línea de arriba y el sello cambia de forma **a la vez**, porque
 * las dos cosas dicen lo mismo — este dato ha dejado de mandar. Partirlo en dos umbrales distintos
 * daría un estado intermedio que no significa nada.
 *
 * ⛔ **Caducado va absoluto y no es una preferencia**: *«the only form that is still true an hour
 * later»* [DET-THE-ASK-SHOWS-ITS-PLACE-AND-RETRACTS-001]. Un «hace 3 días» obliga a restar para
 * saber de cuándo es, y encima envejece mal si la pantalla se queda abierta. El lapso corto no
 * tiene ese problema: dura minutos y [relativeTimeText] se re-escribe sola cada 60 s.
 */
@Composable
internal fun PublicParking.occupancyUpdatedLabel(nowMs: Long): String? =
    when (val stamp = occupancyStamp(nowMs)) {
        // Sin medida no hay sello: la ficha no dice nada, que es distinto de decir que es vieja.
        OccupancyStamp.None -> null
        // ⚠️ [relativeTimeText] lleva su PROPIO reloj y su propio tick de 60 s — por eso la etiqueta
        // sigue contando sola mientras la ficha está abierta, en vez de congelarse en lo que leyó al
        // componerse. El veredicto de arriba sí usa `nowMs`, que es el mismo que decide la cifra.
        is OccupancyStamp.Current ->
            stringResource(Res.string.parking_occupancy_updated_at, relativeTimeText(stamp.updatedAtMs))
        // La cifra ya no está en pantalla, así que esta línea es lo único que distingue «aquí se
        // mide y la última medida es vieja» de «aquí no se mide».
        is OccupancyStamp.Expired ->
            stringResource(
                Res.string.parking_occupancy_updated_last,
                clockTimeWithDate(stamp.updatedAtMs, nowMs),
            )
    }
