package com.rndeveloper.paparcar.presentation.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.domain.entitlement.PaywallReason
import com.rndeveloper.paparcar.domain.entitlement.PlusMoment
import com.rndeveloper.paparcar.presentation.paywall.PLUS_BENEFITS
import com.rndeveloper.paparcar.presentation.paywall.PlusBenefitRow
import com.rndeveloper.paparcar.presentation.paywall.PlusBenefitsMoreRow
import com.rndeveloper.paparcar.presentation.util.shortDateText
import com.rndeveloper.paparcar.ui.components.PapAlertDialog
import com.rndeveloper.paparcar.ui.components.PapDialogAccent
import com.rndeveloper.paparcar.ui.components.PapPremiumMark
import com.rndeveloper.paparcar.ui.illustrations.FoundersGiftIllustration
import com.rndeveloper.paparcar.ui.components.PapSectionHeader
import com.rndeveloper.paparcar.ui.theme.PapGap
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.plus_promo_ending_benefits
import paparcar.composeapp.generated.resources.plus_promo_ending_body
import paparcar.composeapp.generated.resources.plus_promo_ending_cta
import paparcar.composeapp.generated.resources.plus_promo_ending_see_plans
import paparcar.composeapp.generated.resources.plus_promo_ending_title
import paparcar.composeapp.generated.resources.plus_welcome_benefits
import paparcar.composeapp.generated.resources.plus_welcome_more
import paparcar.composeapp.generated.resources.plus_welcome_body
import paparcar.composeapp.generated.resources.plus_welcome_cta
import paparcar.composeapp.generated.resources.plus_welcome_title
import paparcar.composeapp.generated.resources.plus_welcome_until

/**
 * The two things the app says about a Plus nobody paid for. [MONEY-FOUNDERS-WELCOME-001]
 *
 * **A dialog, and not a bottom sheet.** On Home the sheet IS already a modal surface, and a second
 * modal on top of it is the one thing this project forbids outright — nobody can tell what closes
 * what, and the layer underneath goes inert. A dialog floats over whichever tab the user is on and
 * owns the screen alone.
 *
 * **Los dos abren distinto, y es la diferencia entre una NOTICIA y un AVISO**
 * [HOME-FIRST-ENTRY-FLOW-001, 22-11]:
 *  · la bienvenida abre con [FoundersGiftIllustration] — una escena, no un emblema. Es lo primero
 *    que ve una cuenta recién creada y lo que tiene que contar es la OCASIÓN: un regalo, negro de
 *    Black Friday, aparcado en una plaza. Un emblema dice «esto es Plus», que es justo lo que el
 *    título ya dice con palabras; la escena dice lo que no cabe en el título.
 *  · el aviso de fin conserva [PapPremiumMark]: ahí no hay ocasión que celebrar sino una decisión
 *    que tomar, y una ilustración alegre encima de «se te acaba» es un mensaje cruzado. Su metal
 *    ya lo ponen las filas de debajo.
 *
 * ⛔ **Las dos listas salen de `PLUS_BENEFITS`, pero no son la misma lista**, y ahí está el diseño:
 *  · el AVISO DE FIN las lleva las **seis**, con su nota del plan free. Su pregunta es «¿qué cambia
 *    el día 15?», y eso sólo se contesta entero.
 *  · la BIENVENIDA lleva **tres**, elegidas por el gate que venden ([WELCOME_HIGHLIGHTS]). No hay
 *    nada que decidir —ya se lo han regalado—, y con las seis ocupaba casi toda la pantalla siendo
 *    lo primero que ve una cuenta nueva, medido en el Pixel 8. Que la lista está recortada lo dice
 *    ella misma, al final: `PlusBenefitsMoreRow`.
 *
 * Ver `PlusBenefits.kt`: dos lecturas de UNA lista siguen siendo el mecanismo; ahora también en
 * cuántas filas.
 */
@Composable
fun PlusMomentDialog(
    moment: PlusMoment,
    onDismiss: () -> Unit,
    onSeePlans: () -> Unit,
) {
    when (moment) {
        is PlusMoment.FoundersWelcome -> FoundersWelcomeDialog(
            expiresAtMillis = moment.expiresAtMillis,
            onDismiss = onDismiss,
        )
        is PlusMoment.PromoEnding -> PromoEndingDialog(
            expiresAtMillis = moment.expiresAtMillis,
            onDismiss = onDismiss,
            onSeePlans = onSeePlans,
        )
    }
}

/**
 * «You are one of the first 2,000.»
 *
 * ⛔ **One action, and it is not the paywall.** The obvious second button — "see what Plus
 * includes" — would point a user we have just given Plus to at the screen that sells it, on their
 * first day. The list of what Plus includes is right there in the dialog, which is the honest way
 * to answer that wish; the door to the plans belongs to [PromoEndingDialog], where there is
 * actually a decision to make. A soft paywall means the gate opens it, not the gift.
 *
 * The date comes from the grant and is written only if the grant HAS one. ⛔ Deliberately no
 * "three months free" anywhere: the promo's length is whatever RevenueCat says it is, and a
 * hardcoded duration is a sentence that goes false the day the dashboard is edited.
 */
@Composable
private fun FoundersWelcomeDialog(expiresAtMillis: Long?, onDismiss: () -> Unit) {
    PapAlertDialog(
        onDismiss = onDismiss,
        // Semantic fallback only: `heroContent` is what gets drawn. Required by the molde.
        icon = Icons.Rounded.WorkspacePremium,
        title = stringResource(Res.string.plus_welcome_title),
        body = stringResource(Res.string.plus_welcome_body),
        primaryLabel = stringResource(Res.string.plus_welcome_cta),
        onPrimary = onDismiss,
        // El botón lleva el MISMO metal que la cresta (decisión del user, 22-11): el diálogo
        // entero dice «esto es Plus», y su única acción no puede ser el verde plano de cualquier
        // otro confirmar. Ver [PapDialogAccent.Premium].
        accent = PapDialogAccent.Premium,
        // ⛔ **Una ESCENA, no un emblema** [HOME-FIRST-ENTRY-FLOW-001, 22-11, petición del user].
        // Este héroe pasó por la marca suelta a 30 dp (el metal no llegaba a dibujarse a ese
        // tamaño) y por la cresta metálica (que sí se dibujaba, pero decía lo mismo que el título).
        // Lo que faltaba no era material: era la OCASIÓN. La ilustración la cuenta sin una sola
        // palabra, que es lo que la deja viajar a los nueve idiomas.
        //
        // Va en `heroIllustration` y no en `heroContent` a propósito: trae su propio suelo y su
        // propio aire, así que el plato circular del molde sería un segundo marco alrededor de
        // algo ya enmarcado.
        heroIllustration = { FoundersGiftIllustration() },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PapGap.block),
            ) {
                PapSectionHeader(stringResource(Res.string.plus_welcome_benefits))
                // ⛔ **TRES filas, no las seis del paywall** [HOME-FIRST-ENTRY-FLOW-001]. Con las
                // seis, medido en el Pixel 8, el diálogo ocupaba casi toda la pantalla siendo lo
                // primero que ve una cuenta nueva — encima de un mapa que aún no ha mirado.
                //
                // Y la lista completa es el ARGUMENTARIO del paywall: existe para que alguien
                // DECIDA si paga. Aquí no hay nada que decidir, ya se lo han regalado. Lo que sí
                // hace falta es que el regalo se entienda, y para eso bastan las tres que
                // significan algo el PRIMER día: la app vigilando por ti, la app recordando por ti,
                // y la única que se cuenta sola. Las otras tres —zonas, coches, rutas— son topes
                // que se levantan, y un tope no dice nada a quien todavía no lo ha tocado.
                //
                // ⛔ Y que la lista está RECORTADA se dice AQUÍ, al final de ella. Se razonaba que
                // ya lo decía el cuerpo —«Todo, sin pagar nada»—, y el user lo falsificó leyendo la
                // pantalla: ese cuerpo va arriba, antes de la cabecera, y al terminar las tres
                // filas ya no se recuerda. Las tres se leían como el regalo entero.
                //
                // ⛔ Se eligen por el GATE que venden, no por su posición en la lista: reordenar
                // `PLUS_BENEFITS` no puede cambiar en silencio qué se celebra aquí.
                Column(modifier = Modifier.fillMaxWidth()) {
                    PLUS_BENEFITS
                        .filter { benefit -> benefit.sells.any { it in WELCOME_HIGHLIGHTS } }
                        .forEach { PlusBenefitRow(it) }
                    // Y la lista dice que está RECORTADA, con su propia fila. Ver
                    // [PlusBenefitsMoreRow].
                    PlusBenefitsMoreRow(stringResource(Res.string.plus_welcome_more))
                }
                // La fecha, ABAJO DEL TODO y en neutro (decisión del user, 22-11). Estaba encima
                // de la lista y en verde de marca, y ahí competía con el titular por la mirada:
                // el verde es el color de la ACCIÓN en esta app, y aquí la acción es «¡Vamos!».
                // Cerrando el diálogo, la fecha es el último dato que queda — que es justo su
                // papel: no es la noticia, es la letra pequeña de la noticia.
                expiresAtMillis?.let { UntilLine(stringResource(Res.string.plus_welcome_until, shortDateText(it))) }
            }
        },
    )
}

/**
 * «It ends on the 15th.» The quiet knock, a week out.
 *
 * Three rules it is held to, all of them the monetisation doctrine's:
 *  · **"See plans" is the PRIMARY action and "Not now" the outlined one below it** (user's call,
 *    20-11; it shipped the other way round for one build). The reminder's whole point is that
 *    there is still something you can DO about the date, and burying the only action that does it
 *    under the dismissal makes the dialog a dead end — the same defect as a paywall that closes
 *    itself, one screen earlier. Soft gating is intact: "Not now" is a full-width button of the
 *    same size, not a link, not greyed out, not a second layer.
 *  · **Its header is "What changes"**, and that wording is a correction made from a screenshot.
 *    It said *"What the free plan gives you"* — over rows whose BOLD line is the Plus promise
 *    ("Unlimited zones") and whose quiet second line is the free limit ("Free: 2 zones"). Anyone
 *    scanning the bold text read a header that said the opposite of its own list. "What changes"
 *    is true of both halves of every row, and it still is not "what you lose".
 *  · **Nothing is deleted and the copy says so**, because that is the project's actual downgrade
 *    rule, stated by the gates themselves: history narrows its READ window, zones and cars above
 *    the free limit are conserved, and only what is NEW gets gated.
 */
@Composable
private fun PromoEndingDialog(
    expiresAtMillis: Long,
    onDismiss: () -> Unit,
    onSeePlans: () -> Unit,
) {
    PapAlertDialog(
        onDismiss = onDismiss,
        icon = Icons.Rounded.WorkspacePremium,
        title = stringResource(Res.string.plus_promo_ending_title),
        body = stringResource(Res.string.plus_promo_ending_body, shortDateText(expiresAtMillis)),
        primaryLabel = stringResource(Res.string.plus_promo_ending_see_plans),
        onPrimary = onSeePlans,
        secondaryLabel = stringResource(Res.string.plus_promo_ending_cta),
        onSecondary = onDismiss,
        heroContent = { PapPremiumMark(size = HERO_MARK_DP) },
        content = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(PapGap.block),
            ) {
                PapSectionHeader(stringResource(Res.string.plus_promo_ending_benefits))
                Column(modifier = Modifier.fillMaxWidth()) {
                    PLUS_BENEFITS.forEach { PlusBenefitRow(it, showNote = true) }
                }
            }
        },
    )
}

/**
 * The crest inside the dialog's 56 dp hero circle.
 *
 * Smaller than the molde's own 28 dp glyph would suggest at first glance, and the reason is that
 * the mark is FULL BLEED — it touches all four edges of its box, where a Material glyph keeps
 * about 8 % of padding inside its own. At 30 dp the two read as the same optical size.
 *
 * ⚠️ And at this size the premium FINISH does not read — see the class KDoc. The size is chosen for
 * the optical match with every other dialog in the app, not for the metal.
 */
/**
 * Las tres que significan algo el PRIMER día. [HOME-FIRST-ENTRY-FLOW-001]
 *
 * Se nombran por el GATE que venden y no por su posición en `PLUS_BENEFITS`, para que reordenar esa
 * lista no cambie en silencio qué se celebra aquí.
 *
 *  · `ZONE_ALERTS` — la app VIGILANDO por ti: plazas, precio de gasolineras y el resumen del mes.
 *    Es la mitad «quién trabaja» del eje de Plus, y la única que se nota sin haber usado la app.
 *  · `HISTORY` — la app RECORDANDO por ti. La otra mitad del eje.
 *  · `SHARED_CAR` — la única que se cuenta sola: un coche, hasta cinco conductores.
 *
 * Fuera quedan zonas, coches y rutas: son TOPES que se levantan, y un tope no le dice nada a quien
 * todavía no lo ha tocado.
 */
private val WELCOME_HIGHLIGHTS = setOf(
    PaywallReason.ZONE_ALERTS,
    PaywallReason.HISTORY,
    PaywallReason.SHARED_CAR,
)

/** Llena el círculo de héroe del molde (56 dp), así que el disco ES el héroe. */

private val HERO_MARK_DP = 30.dp

/**
 * La fecha de fin, una línea centrada al PIE del diálogo. [HOME-FIRST-ENTRY-FLOW-001, 22-11]
 *
 * ⛔ **Neutra, no verde de marca.** El verde es el color de la ACCIÓN en esta app
 * [UI-COLOR-DOCTRINE-001], y en este diálogo la acción es «¡Vamos!». Una fecha en verde le disputa
 * la mirada al botón y al titular sin ser pulsable — y además no es la noticia: es la letra
 * pequeña de la noticia, y por eso cierra en vez de abrir.
 */
@Composable
private fun UntilLine(text: String) {
    Text(
        text = text,
        style = PaparcarType.current.label,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}
