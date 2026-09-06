package com.rndeveloper.paparcar.presentation.paywall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.revenuecat.purchases.kmp.ui.revenuecatui.Paywall
import com.revenuecat.purchases.kmp.ui.revenuecatui.PaywallOptions
import com.rndeveloper.paparcar.presentation.util.collectAsStateLifecycleAware
import com.rndeveloper.paparcar.ui.components.PapDivider
import com.rndeveloper.paparcar.ui.components.PapIconTile
import com.rndeveloper.paparcar.ui.components.PapOutlinedCard
import com.rndeveloper.paparcar.ui.components.PapPremiumSurface
import com.rndeveloper.paparcar.ui.theme.PapAlpha
import com.rndeveloper.paparcar.ui.theme.PaparcarType
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import paparcar.composeapp.generated.resources.Res
import paparcar.composeapp.generated.resources.paywall_close_cd
import paparcar.composeapp.generated.resources.paywall_compare_title
import paparcar.composeapp.generated.resources.paywall_feature_alerts_free
import paparcar.composeapp.generated.resources.paywall_feature_alerts_plus
import paparcar.composeapp.generated.resources.paywall_feature_alerts_title
import paparcar.composeapp.generated.resources.paywall_feature_history_free
import paparcar.composeapp.generated.resources.paywall_feature_history_plus
import paparcar.composeapp.generated.resources.paywall_feature_history_title
import paparcar.composeapp.generated.resources.paywall_feature_support_free
import paparcar.composeapp.generated.resources.paywall_feature_support_plus
import paparcar.composeapp.generated.resources.paywall_feature_support_title
import paparcar.composeapp.generated.resources.paywall_store_unavailable
import paparcar.composeapp.generated.resources.paywall_tagline
import paparcar.composeapp.generated.resources.paywall_title
import paparcar.composeapp.generated.resources.paywall_unlimited_note

/**
 * Paparcar Plus paywall. [MONEY-001]
 *
 * When the store can sell, the selling surface is the REMOTE template (purchases-kmp-ui):
 * layout, plan choice, prices and trial copy all come from the RevenueCat dashboard against the
 * `default` offering, so they can change without an app release.
 *
 * [PaywallOfferFrame] is what a build that CANNOT sell shows — the mock flavor and a release
 * with no API key yet. It is a real paywall, not an apology: the same Plus-vs-Free comparison,
 * on the same metallic surface as the Settings row and the history footer, with one honest line
 * saying the purchase itself is unavailable right now. Deliberately price-free: prices live in
 * the store, and a build with no store cannot state one it can back.
 */
@Composable
fun PaywallScreen(
    onClose: () -> Unit,
    viewModel: PaywallViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateLifecycleAware()

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                PaywallEffect.Dismiss -> onClose()
            }
        }
    }
    // However Plus arrived (this purchase, a restore, another device), a paywall shown to a
    // Plus user has nothing left to sell — it leaves on its own.
    LaunchedEffect(state.isPlus) {
        if (state.isPlus) onClose()
    }

    PaywallContent(
        state = state,
        onIntent = viewModel::handleIntent,
    )
}

@Composable
fun PaywallContent(
    state: PaywallState,
    onIntent: (PaywallIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.storeAvailable) {
        // The remote template owns the whole surface, its own close affordance included.
        val options = remember(onIntent) {
            PaywallOptions(dismissRequest = { onIntent(PaywallIntent.Close) }) {
                // No explicit offering: the SDK resolves the CURRENT offering, which the
                // dashboard points at `default` — one source of truth, no restated id.
                shouldDisplayDismissButton = true
            }
        }
        Paywall(options)
    } else {
        PaywallOfferFrame(onIntent = onIntent, modifier = modifier)
    }
}

@Composable
private fun PaywallOfferFrame(
    onIntent: (PaywallIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = SCREEN_H_PADDING_DP.dp),
            ) {
                Spacer(Modifier.height(CLOSE_ROW_HEIGHT_DP.dp))

                PapPremiumSurface(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(HERO_PADDING_DP.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(HERO_GAP_DP.dp),
                    ) {
                        // Inverted on the filled brand bed — see PapPremiumSurface.
                        PapIconTile(
                            icon = Icons.Rounded.WorkspacePremium,
                            size = HERO_TILE_DP.dp,
                            iconSize = HERO_TILE_ICON_DP.dp,
                            container = MaterialTheme.colorScheme.onPrimary,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = stringResource(Res.string.paywall_title),
                            style = PaparcarType.current.heroTitle,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(Res.string.paywall_tagline),
                            style = PaparcarType.current.subtitle,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = PapAlpha.body),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(SECTION_GAP_DP.dp))
                Text(
                    text = stringResource(Res.string.paywall_compare_title),
                    style = PaparcarType.current.sectionTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(COMPARE_TITLE_GAP_DP.dp))

                PapOutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        PAYWALL_FEATURES.forEachIndexed { index, feature ->
                            if (index > 0) PapDivider()
                            PaywallFeatureRow(feature)
                        }
                    }
                }

                Spacer(Modifier.height(SECTION_GAP_DP.dp))
                // Vehicles and zones stay unlimited on both tiers — saying so out loud stops the
                // comparison from reading as "everything else is capped too". [MONEY-001]
                Text(
                    text = stringResource(Res.string.paywall_unlimited_note),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(NOTE_GAP_DP.dp))
                Text(
                    text = stringResource(Res.string.paywall_store_unavailable),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(SECTION_GAP_DP.dp))
            }

            IconButton(
                onClick = { onIntent(PaywallIntent.Close) },
                modifier = Modifier.align(Alignment.TopEnd).padding(CLOSE_PADDING_DP.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.paywall_close_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PaywallFeatureRow(feature: PaywallFeature) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(ROW_PADDING_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(ROW_GAP_DP.dp),
    ) {
        PapIconTile(icon = feature.icon)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(feature.title),
                style = PaparcarType.current.rowTitle,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(ROW_TEXT_GAP_DP.dp))
            // Free first, then Plus: the order the reader lives in — what they have now, then
            // what changes. The Plus line carries the check, so the win is scannable.
            Text(
                text = stringResource(feature.free),
                style = PaparcarType.current.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CHECK_GAP_DP.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(CHECK_DP.dp),
                )
                Text(
                    text = stringResource(feature.plus),
                    style = PaparcarType.current.caption,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/** One row of the Plus-vs-Free comparison. */
private class PaywallFeature(
    val icon: ImageVector,
    val title: StringResource,
    val free: StringResource,
    val plus: StringResource,
)

// Only what Paparcar Plus ACTUALLY changes today — no aspirational rows. The third is not a
// feature and says so plainly; a comparison padded with invented perks is how a paywall stops
// being believed. [COPY-NO-INVENTED-JARGON]
private val PAYWALL_FEATURES = listOf(
    PaywallFeature(
        icon = Icons.Rounded.History,
        title = Res.string.paywall_feature_history_title,
        free = Res.string.paywall_feature_history_free,
        plus = Res.string.paywall_feature_history_plus,
    ),
    PaywallFeature(
        icon = Icons.Rounded.NotificationsActive,
        title = Res.string.paywall_feature_alerts_title,
        free = Res.string.paywall_feature_alerts_free,
        plus = Res.string.paywall_feature_alerts_plus,
    ),
    PaywallFeature(
        icon = Icons.Rounded.WorkspacePremium,
        title = Res.string.paywall_feature_support_title,
        free = Res.string.paywall_feature_support_free,
        plus = Res.string.paywall_feature_support_plus,
    ),
)

private const val SCREEN_H_PADDING_DP = 20
private const val CLOSE_ROW_HEIGHT_DP = 56
private const val CLOSE_PADDING_DP = 4
private const val HERO_PADDING_DP = 24
private const val HERO_GAP_DP = 10
private const val HERO_TILE_DP = 64
private const val HERO_TILE_ICON_DP = 32
private const val SECTION_GAP_DP = 24
private const val COMPARE_TITLE_GAP_DP = 10
private const val NOTE_GAP_DP = 8
private const val ROW_PADDING_DP = 16
private const val ROW_GAP_DP = 14
private const val ROW_TEXT_GAP_DP = 4
private const val CHECK_GAP_DP = 6
private const val CHECK_DP = 16
