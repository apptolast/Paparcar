package com.rndeveloper.paparcar.presentation.home.sections.sheet.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapColor
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.fuel_peek_following

/**
 * «Ésta es una de las que sigues» — la campana de una gasolinera seguida, **en todas las superficies
 * que pintan una gasolinera**. [FUEL-A-FOLLOW-MUST-SHOW-ITSELF-001]
 *
 * ⛔ Un solo objeto, y existe porque no existía: `followedFuelStationKeys` llevaba meses en el
 * estado con **un** consumidor —el botón de pie del peek—, así que la lista no distinguía una
 * gasolinera seguida de una cualquiera y el único sitio que lo decía había que ir a buscarlo al
 * final de su propia hoja. Dos superficies dibujando cada una su propia marca es la forma de que
 * dentro de tres meses digan dos cosas distintas.
 *
 * ## Por qué una campana verde, y no otra cosa
 *
 * Verde de acción legible ([PapColor.actionText]), el mismo que lleva el botón que la enciende: la
 * campana es la **huella de un seguimiento elegido por el usuario**, no identidad de vehículo
 * (`papCarBlue` / gris) ni rampa de frescura (los cuatro de las plazas). Y es una marca, no un estado
 * del mundo, así que no la escribe ninguna palabra. [UI-COLOR-DOCTRINE-001]
 *
 * ## Y su etiqueta es la del botón, no una nueva
 *
 * `fuel_peek_following` es exactamente la palabra que esto significa y ya vive en los nueve
 * locales. Un string a11y nuevo sería una segunda traducción de la misma palabra, y la que se
 * quedara atrás sería la que nadie ve escrita.
 */
@Composable
internal fun FuelFollowMark(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Rounded.Notifications,
        // No es decorativa: es la única diferencia entre esta fila y la de al lado.
        contentDescription = stringResource(Res.string.fuel_peek_following),
        tint = PapColor.actionText,
        modifier = modifier.size(MARK_DP.dp),
    )
}

/**
 * Un solo tamaño para la fila y para la cabecera del peek, aunque sus títulos no midan lo mismo
 * (`rowName` 16sp · `cardTitle` 18sp): la marca es el MISMO objeto en las dos, y dos tamaños la
 * convertirían en dos marcas que casualmente se parecen. A 16dp casa con la altura de la x de
 * ambos roles sin empujar la línea.
 */
private const val MARK_DP = 16
