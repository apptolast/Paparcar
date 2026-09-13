package com.rndeveloper.paparcar.presentation.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rndeveloper.paparcar.ui.components.PapCard
import com.rndeveloper.paparcar.ui.components.PapPrimaryButton
import com.rndeveloper.paparcar.ui.icons.PaparcarIcons
import com.rndeveloper.paparcar.ui.illustrations.OnboardingHero
import com.rndeveloper.paparcar.ui.theme.PaparcarSpacing
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import com.rndeveloper.paparcar.ui.theme.PapColor
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.onboarding_cta_next
import paparcar.composeapp.generated.resources.onboarding_cta_start
import paparcar.composeapp.generated.resources.onboarding_page1_subtitle
import paparcar.composeapp.generated.resources.onboarding_page1_title
import paparcar.composeapp.generated.resources.onboarding_page2_title
import paparcar.composeapp.generated.resources.onboarding_step1_desc
import paparcar.composeapp.generated.resources.onboarding_step1_title
import paparcar.composeapp.generated.resources.onboarding_step2_desc
import paparcar.composeapp.generated.resources.onboarding_step2_title
import paparcar.composeapp.generated.resources.onboarding_step3_desc
import paparcar.composeapp.generated.resources.onboarding_step3_title
import paparcar.composeapp.generated.resources.onboarding_step4_desc
import paparcar.composeapp.generated.resources.onboarding_step4_title

// Two pages of VALUE — what Paparcar is + what it does for you. The old page 3 ("Why we need your permissions",
// padlock hero) made the last thing a new user saw before the app a security lecture; its
// background-location disclosure now lives where background is actually requested (the "activate
// detection" visit). [ONB-PERMISSIONS-ASK-AT-THE-MOMENT-OF-VALUE-001]
private const val PAGE_COUNT                 = 2
private const val DOT_ANIM_MS                = 300
private val       PAGE_CONTENT_BOTTOM_CLEARANCE = 140.dp
private val       HERO_ILLUSTRATION_W        = 140.dp
private val       HERO_ILLUSTRATION_H        = 120.dp
private val       STEP_ICON_SIZE             = 36.dp
private val       DOT_ACTIVE_WIDTH           = 24.dp
private val       DOT_INACTIVE_SIZE          = 8.dp

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    /** Where the pager opens — only the gallery and previews pass anything but the first page. */
    initialPage: Int = 0,
) {
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { PAGE_COUNT })
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            when (page) {
                0 -> OnboardingPage1()
                else -> OnboardingPage2()
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = PaparcarSpacing.xxl)
                .navigationBarsPadding()
                .padding(bottom = PaparcarSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PagerDotIndicator(
                pageCount = PAGE_COUNT,
                currentPage = pagerState.currentPage,
            )
            Spacer(Modifier.height(PaparcarSpacing.xxl))
            PapPrimaryButton(
                label = if (pagerState.currentPage < PAGE_COUNT - 1) {
                    stringResource(Res.string.onboarding_cta_next)
                } else {
                    stringResource(Res.string.onboarding_cta_start)
                },
                onClick = {
                    if (pagerState.currentPage < PAGE_COUNT - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onComplete()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OnboardingPage1() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = PaparcarSpacing.xxxl)
            .padding(top = PaparcarSpacing.huge, bottom = PAGE_CONTENT_BOTTOM_CLEARANCE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingHero(
            hero = OnboardingHero.WELCOME,
            modifier = Modifier.size(HERO_ILLUSTRATION_W, HERO_ILLUSTRATION_H),
        )
        Spacer(Modifier.height(PaparcarSpacing.sm))
        Text(
            text = stringResource(Res.string.onboarding_page1_title),
            style = PaparcarType.current.heroTitle,
            color = PapColor.actionText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PaparcarSpacing.lg))
        Text(
            text = stringResource(Res.string.onboarding_page1_subtitle),
            style = PaparcarType.current.subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OnboardingPage2() = BoxWithConstraints(Modifier.fillMaxSize()) {
    // Four cards and a hero no longer fit a small phone above the CTA, so the page scrolls — and
    // still centres when it fits: the column is at least one viewport tall.
    // [ONBOARDING-SELLS-THE-NEW-PRODUCT-001]
    val viewportHeight = maxHeight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .heightIn(min = viewportHeight)
            .statusBarsPadding()
            .padding(horizontal = PaparcarSpacing.xxxl)
            .padding(top = PaparcarSpacing.huge, bottom = PAGE_CONTENT_BOTTOM_CLEARANCE),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OnboardingHero(
            hero = OnboardingHero.HOW,
            modifier = Modifier.size(HERO_ILLUSTRATION_W, HERO_ILLUSTRATION_H),
        )
        Spacer(Modifier.height(PaparcarSpacing.sm))
        Text(
            text = stringResource(Res.string.onboarding_page2_title),
            style = PaparcarType.current.heroTitle,
            color = PapColor.actionText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PaparcarSpacing.xxl + PaparcarSpacing.md))
        // [ONBOARDING-SELLS-THE-NEW-PRODUCT-001] Cuatro tarjetas de VALOR, no de mecánica: la
        // detección como servicio (tus trayectos y aparcamientos convertidos en cifras), tu
        // aparcamiento guardado, la plaza que liberas y — como complemento, nunca como producto —
        // parkings y gasolineras. Los glifos 1-3 conservan su significado; su frase cambia de «cómo
        // funciona por dentro» a «qué ganas».
        //
        // [UI-ONBOARDING-STEP-ICONS-SAY-WHAT-THE-APP-SAYS-001] Los tres glifos NO se eligen por
        // parecido con el título, sino por el significado que ya tienen dentro de la app: esta
        // pantalla es la primera vez que el usuario los ve, y los volverá a encontrar en Home
        // queriendo decir lo mismo. La flecha se mueve, el coche descansa, el megáfono avisa.
        OnboardingStep(
            // El glifo de `DetectionStory.Driving` en HomeDetectionSurface: "este coche circula
            // ahora". El coche quieto no dice conducir — y aquí ya está adjudicado al paso 2.
            icon = Icons.Rounded.Navigation,
            title = stringResource(Res.string.onboarding_step1_title),
            desc = stringResource(Res.string.onboarding_step1_desc),
        )
        Spacer(Modifier.height(PaparcarSpacing.lg))
        OnboardingStep(
            // Tu aparcamiento se dibuja con TU COCHE en toda la app (FAB de Home, FAB del
            // historial, fila de pregunta). Aquí había una "P", que es `PaparcarIcons.ParkingPlace`
            // = la categoría de POI "parking público": el único sitio de la app donde el
            // aparcamiento se dibujaba como señalética, justo sobre el paso que habla de lo TUYO.
            icon = Icons.Rounded.DirectionsCar,
            title = stringResource(Res.string.onboarding_step2_title),
            desc = stringResource(Res.string.onboarding_step2_desc),
        )
        Spacer(Modifier.height(PaparcarSpacing.lg))
        OnboardingStep(
            // El megáfono de "avisar de una plaza" (HomeSpotRows) y del diálogo que libera la tuya
            // al irte (HomeReleaseDialog): literalmente la pantalla que este paso promete. Un
            // `LocationOn` decía "el pin de TU aparcamiento" (HomeParkingRow) sobre la frase que
            // habla de la COMUNIDAD — el cruce que COPY-SPOT-IS-NOT-A-PARKING-001 quitó del copy.
            icon = Icons.Rounded.Campaign,
            title = stringResource(Res.string.onboarding_step3_title),
            desc = stringResource(Res.string.onboarding_step3_desc),
        )
        Spacer(Modifier.height(PaparcarSpacing.lg))
        OnboardingStep(
            // El edificio con su P de `PaparcarIcons.CarParkMark`: el MISMO glifo que lleva el carril
            // de parkings en el sheet de Home, el único dueño legítimo de la P. Una tarjeta, no una
            // página: los complementos se presentan de pasada y su carril se explica la primera vez
            // que se abre [ONB-THE-MAP-ANSWERS-MORE-THAN-SPOTS-001].
            icon = PaparcarIcons.CarParkMark,
            title = stringResource(Res.string.onboarding_step4_title),
            desc = stringResource(Res.string.onboarding_step4_desc),
        )
    }
}

@Composable
private fun OnboardingStep(icon: ImageVector, title: String, desc: String) {
    PapCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(STEP_ICON_SIZE),
            )
            Spacer(Modifier.width(PaparcarSpacing.lg))
            Column {
                Text(
                    text = title,
                    style = PaparcarType.current.rowTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = desc,
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PagerDotIndicator(pageCount: Int, currentPage: Int) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val width by animateDpAsState(
                targetValue = if (index == currentPage) DOT_ACTIVE_WIDTH else DOT_INACTIVE_SIZE,
                animationSpec = tween(DOT_ANIM_MS),
                label = "dot_width",
            )
            Box(
                modifier = Modifier
                    .padding(horizontal = PaparcarSpacing.xs)
                    .height(DOT_INACTIVE_SIZE)
                    .width(width)
                    .background(
                        color = if (index == currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                        shape = CircleShape,
                    ),
            )
        }
    }
}
