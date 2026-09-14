package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.parkings.FeeInfo
import com.rndeveloper.paparcar.domain.usecase.parkings.NearbyPublicParking
import com.rndeveloper.paparcar.presentation.home.model.publicParkingKindLabel
import com.rndeveloper.paparcar.presentation.util.formatDistance
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapListItem
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.theme.PapColor
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.parking_fee_free
import paparcar.composeapp.generated.resources.parking_fee_paid

/**
 * One car park: what it is, how far, and whether it charges. [PARKINGS-ON-THE-MAP-001]
 * [UI-LIST-ITEM-001]
 *
 * ⛔ **Titled by its KIND when nobody named it**, which is the common case and not an edge one:
 * `name` sits at 72% in central Madrid but at 2,6% in Cuenca. A fuel station can be titled by its
 * brand because OSM names all of them; here "Underground car park · 300 m" is the row that reads,
 * and an empty title is the row that does not.
 */
@Composable
fun HomePublicParkingRow(
    parking: NearbyPublicParking,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    /** Whether that distance is a distance TO THE USER. See [subtitleFor].
     *  [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] */
    hasFix: Boolean = true,
) {
    val kindLabel = publicParkingKindLabel(parking.parking.kind)
    val title = parking.parking.displayName ?: kindLabel

    PapListItem(
        title = title,
        // Clickable OUTSIDE the caller's padding, so the whole row is the touch target.
        modifier = (if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .then(modifier),
        contentPadding = PaddingValues(0.dp),
        titleStyle = PaparcarType.current.rowName,
        titleMaxLines = 1,
        subtitle = subtitleFor(parking, kindLabel, title, hasFix),
        subtitleMaxLines = 1,
        leading = {
            // ⛔ VERDE, no neutro (user, 10-09, viéndolo en el Oppo). El barrido de
            // `UI-A-NEUTRAL-TILE-MUST-NOT-MUTE-AN-ACTION-001` dejó neutro todo tile que ETIQUETA su
            // fila, y por regla esto lo es. En mano la conclusión fue otra: una lista entera de
            // glifos grises no se lee como «esto es una etiqueta», se lee como una lista apagada.
            // El verde aquí no compite con ninguna acción — en estas filas no hay ninguna.
            // [HOME-A-PROVINCE-IS-NOT-A-REGION-AND-A-LANE-MUST-BREATHE-001]
            PapIconTile(icon = PaparcarIcons.CarParkMark, tint = PapColor.actionText)
        },
        trailing = feeLabel(parking.parking.fee)?.let { label -> { FeeLabel(label) } },
    )
}

/**
 * Distance first — it is what decides between two car parks — then the kind, but only when the title
 * did not already spend it. Whatever the title said, the subtitle does not repeat.
 *
 * ⛔ **Sin fix no hay distancia que rotular.** `distanceMeters` se mide desde el punto por el que se
 * PREGUNTÓ: con ubicación eres tú, sin ella es el centro del mapa. Mientras eso duraba los primeros
 * segundos de un arranque en frío era un mal menor; desde que el carril responde también sin permiso
 * [HOME-WITHOUT-LOCATION-STILL-ANSWERS-001] sería permanente, y «a 300 m» de un punto donde no hay
 * nadie es una frase falsa sostenida en el tiempo. Se calla exactamente eso — nombre, tipo y tarifa
 * siguen siendo verdad. Es lo que la fila de plaza ya hacía con su `userLocation` nulo.
 */
@Composable
private fun subtitleFor(
    parking: NearbyPublicParking,
    kindLabel: String,
    title: String,
    hasFix: Boolean,
): String? {
    val distance = if (hasFix) formatDistance(parking.distanceMeters.toFloat()) else null
    val kind = kindLabel.takeIf { it != title }
    return when {
        distance != null && kind != null -> "$distance · $kind"
        else -> distance ?: kind
    }
}

/**
 * ⛔ Only a stated fee is shown. `FeeInfo.UNKNOWN` — the 71% of car parks nobody has priced — renders
 * as **nothing**, never as "free": a driver told it was free and met a barrier was told a lie by us,
 * not by OpenStreetMap.
 */
@Composable
private fun feeLabel(fee: FeeInfo): String? = when (fee) {
    FeeInfo.PAID -> stringResource(Res.string.parking_fee_paid)
    FeeInfo.FREE -> stringResource(Res.string.parking_fee_free)
    FeeInfo.UNKNOWN -> null
}

/** LECTURA: it shares a line box with a name in MARCA. [UI-TYPE-TWO-VOICES-ONE-ROW-001] */
@Composable
private fun FeeLabel(label: String) {
    Text(
        text = label,
        style = PaparcarType.current.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Divider inset to clear the glyph, so the rows read as one column of names. */
@Composable
fun HomePublicParkingRows(
    parkings: List<NearbyPublicParking>,
    modifier: Modifier = Modifier,
    onParkingClick: ((String) -> Unit)? = null,
    hasFix: Boolean = true,
) {
    Column(modifier = modifier) {
        parkings.forEachIndexed { index, parking ->
            HomePublicParkingRow(
                parking = parking,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                onClick = onParkingClick?.let { cb -> { cb(parking.parking.id) } },
                hasFix = hasFix,
            )
            if (index < parkings.lastIndex) {
                PapDivider(modifier = Modifier.padding(start = 70.dp, end = 16.dp))
            }
        }
    }
}
