package io.apptolast.paparcar.presentation.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.apptolast.paparcar.ui.components.PapCard
import io.apptolast.paparcar.ui.components.PapPrimaryButton
import io.apptolast.paparcar.ui.icons.PaparcarIcons
import io.apptolast.paparcar.ui.illustrations.OnboardingHero
import io.apptolast.paparcar.ui.theme.PaparcarSpacing
import io.apptolast.paparcar.ui.theme.PaparcarType
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.onboarding_cta_next
import paparcar.composeapp.generated.resources.onboarding_cta_setup
import paparcar.composeapp.generated.resources.onboarding_page1_subtitle
import paparcar.composeapp.generated.resources.onboarding_page1_title
import paparcar.composeapp.generated.resources.onboarding_page2_title
import paparcar.composeapp.generated.resources.onboarding_page3_subtitle
import paparcar.composeapp.generated.resources.onboarding_page3_title
import paparcar.composeapp.generated.resources.onboarding_step1_desc
import paparcar.composeapp.generated.resources.onboarding_step1_title
import paparcar.composeapp.generated.resources.onboarding_step2_desc
import paparcar.composeapp.generated.resources.onboarding_step2_title
import paparcar.composeapp.generated.resources.onboarding_step3_desc
import paparcar.composeapp.generated.resources.onboarding_step3_title

private const val PAGE_COUNT                 = 3
private const val DOT_ANIM_MS                = 300
private val       PAGE_CONTENT_BOTTOM_CLEARANCE = 140.dp
private val       HERO_ILLUSTRATION_W        = 140.dp
private val       HERO_ILLUSTRATION_H        = 120.dp
private val       STEP_ICON_SIZE             = 36.dp
private val       DOT_ACTIVE_WIDTH           = 24.dp
private val       DOT_INACTIVE_SIZE          = 8.dp

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
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
                1 -> OnboardingPage2()
                else -> OnboardingPage3()
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
                    stringResource(Res.string.onboarding_cta_setup)
                },
                // "Next" is wayfinding, not a named action — the screen context carries it,
                // so no arrow glyph. The final page's "Set up" keeps its Security icon because
                // it names a concrete action (grant permissions). [UI-SHEET-002]
                icon = if (pagerState.currentPage < PAGE_COUNT - 1) {
                    null
                } else {
                    Icons.Rounded.Security
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
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
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
private fun OnboardingPage2() {
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
            hero = OnboardingHero.HOW,
            modifier = Modifier.size(HERO_ILLUSTRATION_W, HERO_ILLUSTRATION_H),
        )
        Spacer(Modifier.height(PaparcarSpacing.sm))
        Text(
            text = stringResource(Res.string.onboarding_page2_title),
            style = PaparcarType.current.heroTitle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PaparcarSpacing.xxl + PaparcarSpacing.md))
        OnboardingStep(
            icon = Icons.Rounded.DirectionsCar,
            title = stringResource(Res.string.onboarding_step1_title),
            desc = stringResource(Res.string.onboarding_step1_desc),
        )
        Spacer(Modifier.height(PaparcarSpacing.lg))
        OnboardingStep(
            icon = PaparcarIcons.ParkingPlace,
            title = stringResource(Res.string.onboarding_step2_title),
            desc = stringResource(Res.string.onboarding_step2_desc),
        )
        Spacer(Modifier.height(PaparcarSpacing.lg))
        OnboardingStep(
            icon = Icons.Rounded.LocationOn,
            title = stringResource(Res.string.onboarding_step3_title),
            desc = stringResource(Res.string.onboarding_step3_desc),
        )
    }
}

@Composable
private fun OnboardingPage3() {
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
            hero = OnboardingHero.PRIVACY,
            modifier = Modifier.size(HERO_ILLUSTRATION_W, HERO_ILLUSTRATION_H),
        )
        Spacer(Modifier.height(PaparcarSpacing.sm))
        Text(
            text = stringResource(Res.string.onboarding_page3_title),
            style = PaparcarType.current.heroTitle,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(PaparcarSpacing.lg))
        Text(
            text = stringResource(Res.string.onboarding_page3_subtitle),
            style = PaparcarType.current.subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
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
                    fontWeight = FontWeight.SemiBold,
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
