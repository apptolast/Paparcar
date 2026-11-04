package com.rndeveloper.paparcar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.theme.PapShapes
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PaparcarType

/**
 * Una OPORTUNIDAD del primer vistazo: un item concreto, y el camino a su lista.
 * [HOME-THE-FIRST-GLANCE-SPEAKS-FOR-THE-CAR-001]
 *
 * ⛔ **Dos destinos, y por eso no es una fila.** La petición es del user, sobre la tarjeta de
 * gasolinera del prototipo: *«si pulsas la card te lleva a la modal y al punto en el mapa… esa misma
 * card quizás debería tener algún botón accesible para que te lleve a mostrar la lista de items de
 * ese tipo»*. Son dos intenciones distintas —**este** item, o **todos** los de su clase— y una fila
 * con un solo destino obliga a elegir cuál se pierde:
 *
 * | Zona | A dónde lleva |
 * |---|---|
 * | el CUERPO | a este item: lo abre y lo enfoca en el mapa, igual que pulsar su fila en la lista |
 * | el PIE | al carril de su sujeto, con la lista entera |
 *
 * ⛔ **Dos destinos son dos objetivos táctiles, no uno con un gesto escondido.** La tarjeta en sí no
 * es pulsable: si lo fuera habría tres zonas (cuerpo, pie y «el resto») y la tercera no tendría
 * destino propio. El divisor no es decoración — es la frontera que el dedo necesita ver.
 *
 * ⛔ **El pie NOMBRA su sujeto** («Ver las 3 gasolineras»), y eso corrige la nota de mi propio
 * prototipo, que pedía que el pie dijera sólo la cifra porque el sujeto ya lo lleva el eyebrow. Dos
 * razones lo tiran: en ES/IT/PT/FR/RO *«Ver las 3»* no inflexiona el artículo —un parking es
 * masculino y una plaza femenina— y un pie que sólo dice la cifra no es un nombre accesible por sí
 * solo, que es justo lo que el prototipo tapaba con un `aria-label`. La cifra sigue ahí: es lo que
 * decide si merece la pena pulsar.
 *
 * ⛔ **Sin sección cuando no hay oportunidades**, y eso lo decide quien la hospeda leyendo
 * `nearbyNotables()`: ni título, ni hueco, ni caja diciendo que no hay nada.
 *
 * Todo el contenido llega por parámetro o por slot: esta tarjeta no sabe de plazas, parkings ni
 * gasolineras. Los roles tipográficos son los del sistema y el **único** color que se decide en el
 * call site es el de la cifra, que es la identidad del sujeto. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
 * [UI-COLOR-DOCTRINE-001]
 *
 * @param eyebrow qué AFIRMACIÓN es ésta («GASOLINA 95 MÁS BARATA»), en caps por su rol.
 * @param name el nombre del item — voz MARCA.
 * @param footerLabel el pie, ya resuelto en plural por su sujeto y con la cifra dentro.
 * @param figure la cifra que protagoniza el bloque (precio, delta), o null. Voz CIFRA.
 * @param figureUnit la unidad bajo la cifra («€/l»), o null.
 * @param figureColor la identidad del sujeto; sólo pinta la CIFRA, nunca el nombre ni el estado.
 * @param caption dónde está y a cuánto — una línea, nunca dos.
 * @param stamp la procedencia o la edad del dato, cuando el sujeto la tiene.
 * @param leading el glifo del sujeto, junto al eyebrow.
 * @param chip la MISMA afirmación que lleva su fila en la lista, para que al abrirla se reconozca.
 */
@Composable
fun PapOpportunityCard(
    eyebrow: String,
    name: String,
    onOpen: () -> Unit,
    footerLabel: String,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    figure: String? = null,
    figureUnit: String? = null,
    figureColor: Color = MaterialTheme.colorScheme.onSurface,
    caption: String? = null,
    stamp: String? = null,
    chip: (@Composable () -> Unit)? = null,
) {
    PapOutlinedCard(modifier = modifier.fillMaxWidth(), shape = PapShapes.card) {
        Column {
            // ── El CUERPO: este item ──────────────────────────────────────────
            Surface(
                onClick = onOpen,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = PaparcarSpacing.lg,
                        vertical = PaparcarSpacing.md,
                    ),
                    verticalArrangement = Arrangement.spacedBy(PaparcarSpacing.xs),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (leading != null) {
                            leading()
                            Spacer(Modifier.width(PaparcarSpacing.sm))
                        }
                        Text(
                            text = eyebrow,
                            style = PaparcarType.current.eyebrow,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // La CIFRA es el sujeto de su propio bloque, así que va sola en su línea con la
                    // unidad asentada en su misma base. [UI-TYPE-TWO-VOICES-ONE-ROW-001]
                    if (figure != null) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = figure,
                                style = PaparcarType.current.counter,
                                color = figureColor,
                                maxLines = 1,
                            )
                            if (figureUnit != null) {
                                Spacer(Modifier.width(PaparcarSpacing.xs))
                                Text(
                                    text = figureUnit,
                                    style = PaparcarType.current.counterUnit,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }

                    Text(
                        text = name,
                        style = PaparcarType.current.rowName,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = OPPORTUNITY_NAME_MAX_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (caption != null) {
                        Text(
                            text = caption,
                            style = PaparcarType.current.caption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (stamp != null) {
                        Text(
                            text = stamp,
                            style = PaparcarType.current.meta,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (chip != null) chip()
                }
            }

            PapDivider()

            // ── El PIE: todos los de su clase ─────────────────────────────────
            Surface(
                onClick = onOpenList,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = PaparcarSpacing.lg,
                        vertical = PaparcarSpacing.md,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = footerLabel,
                        style = PaparcarType.current.cta,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(PaparcarSpacing.xs))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(OPPORTUNITY_FOOTER_CHEVRON_DP),
                    )
                }
            }
        }
    }
}

/** Dos líneas: un nombre de gasolinera con marca y calle no cabe en una, y elidirlo deja al usuario
 *  sin saber cuál es. ⛔ Tres ya es una lista dentro de una tarjeta. */
private const val OPPORTUNITY_NAME_MAX_LINES = 2

/** El chevron del pie, a la medida del rol `cta` al que acompaña. */
private val OPPORTUNITY_FOOTER_CHEVRON_DP = 18.dp
