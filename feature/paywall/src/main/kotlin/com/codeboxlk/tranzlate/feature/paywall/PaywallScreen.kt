package com.codeboxlk.tranzlate.feature.paywall

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import kotlinx.coroutines.launch

/** §4 visual anchoring: the Yearly card is deliberately wider than its siblings. */
private const val YEARLY_CARD_WEIGHT = 1.4f

/**
 * BUSINESS_MODEL §4 — the paywall, verbatim: dismissible ✕ (Play policy),
 * benefit-led bullets, three periods with Yearly pre-selected (trial + save
 * badge), per-day framing, "Cancel anytime", CTA follows the selection,
 * Restore · Terms · Privacy. Display prices are placeholder resources until
 * the store offerings land (gateway is NoOp — purchases surface honest
 * failures, never fake success).
 */
@Composable
fun PaywallScreen(
    viewModel: PaywallViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val purchasing by viewModel.purchasing.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Already-PRO (or a purchase that just resolved) never sees the pitch.
    LaunchedEffect(isPro) {
        if (isPro) onClose()
    }
    val purchaseFailed = stringResource(R.string.paywall_purchase_unavailable)
    val restoreFailed = stringResource(R.string.paywall_restore_failed)
    val restoredFree = stringResource(R.string.paywall_restore_nothing)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    PaywallEvent.PURCHASE_FAILED -> purchaseFailed
                    PaywallEvent.RESTORE_FAILED -> restoreFailed
                    PaywallEvent.RESTORED_FREE -> restoredFree
                },
            )
        }
    }

    val linksComing = stringResource(R.string.paywall_links_coming)
    val scope = rememberCoroutineScope()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        PaywallContent(
            onLinkNotice = { scope.launch { snackbarHostState.showSnackbar(linksComing) } },
            selected = selected,
            purchasing = purchasing,
            onSelect = viewModel::select,
            onPurchase = viewModel::purchase,
            onRestore = viewModel::restore,
            onClose = onClose,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

@Composable
internal fun PaywallContent(
    selected: PaywallPlan,
    purchasing: Boolean,
    onSelect: (PaywallPlan) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onLinkNotice: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    // Issue #88 (owner + M3 breakpoints): fill the width — the
                    // lg24 side padding already equals the medium margin.
                    .fillMaxWidth()
                    .padding(horizontal = spacing.lg24),
        ) {
            // Play policy: always dismissible, and the ✕ comes first in traversal.
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("tt_paywall_close"),
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.paywall_cd_close),
                )
            }
            Text(
                text = stringResource(R.string.paywall_hero),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(spacing.md16))
            BenefitRow(R.string.paywall_benefit_no_ads)
            BenefitRow(R.string.paywall_benefit_unlimited_ai)
            BenefitRow(R.string.paywall_benefit_characters)
            BenefitRow(R.string.paywall_benefit_phrasing)
            Spacer(Modifier.height(spacing.lg24))
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.sm8),
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
            ) {
                PlanCard(
                    plan = PaywallPlan.WEEKLY,
                    titleRes = R.string.paywall_plan_weekly,
                    priceRes = R.string.paywall_price_weekly,
                    selected = selected == PaywallPlan.WEEKLY,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
                PlanCard(
                    plan = PaywallPlan.MONTHLY,
                    titleRes = R.string.paywall_plan_monthly,
                    priceRes = R.string.paywall_price_monthly,
                    selected = selected == PaywallPlan.MONTHLY,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
                PlanCard(
                    plan = PaywallPlan.YEARLY,
                    titleRes = R.string.paywall_plan_yearly,
                    priceRes = R.string.paywall_price_yearly,
                    selected = selected == PaywallPlan.YEARLY,
                    onSelect = onSelect,
                    badgeRes = R.string.paywall_badge_save,
                    subRes = R.string.paywall_trial_line,
                    modifier = Modifier.weight(YEARLY_CARD_WEIGHT),
                )
            }
            Spacer(Modifier.height(spacing.md16))
            // §4 anchoring: per-day framing on the pre-selected Yearly.
            if (selected == PaywallPlan.YEARLY) {
                Text(
                    text = stringResource(R.string.paywall_per_day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.paywall_cancel_anytime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md16))
            Button(
                onClick = onPurchase,
                enabled = !purchasing,
                modifier = Modifier.fillMaxWidth().testTag("tt_paywall_cta"),
            ) {
                if (purchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        stringResource(
                            if (selected == PaywallPlan.YEARLY) {
                                R.string.paywall_cta_trial
                            } else {
                                R.string.paywall_cta_continue
                            },
                        ),
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TextButton(
                    onClick = onRestore,
                    modifier = Modifier.testTag("tt_paywall_restore"),
                ) { Text(stringResource(R.string.paywall_restore)) }
                TextButton(onClick = onLinkNotice) { Text(stringResource(R.string.paywall_terms)) }
                TextButton(onClick = onLinkNotice) { Text(stringResource(R.string.paywall_privacy)) }
            }
            Spacer(Modifier.height(spacing.lg24))
        }
    }
}

@Composable
private fun BenefitRow(textRes: Int) {
    val spacing = LocalSpacing.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = spacing.xs4),
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = null, // decorative — the text carries the meaning
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(spacing.sm8))
        Text(stringResource(textRes), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun PlanCard(
    plan: PaywallPlan,
    titleRes: Int,
    priceRes: Int,
    selected: Boolean,
    onSelect: (PaywallPlan) -> Unit,
    modifier: Modifier = Modifier,
    badgeRes: Int? = null,
    subRes: Int? = null,
) {
    val spacing = LocalSpacing.current
    val borderColor =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = { onSelect(plan) },
        selected = selected,
        shape = MaterialTheme.shapes.large,
        color =
            if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = MaterialTheme.shapes.large,
                ).testTag("tt_paywall_plan_${plan.name.lowercase()}"),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(spacing.sm8).fillMaxWidth(),
        ) {
            if (badgeRes != null) {
                Text(
                    text = stringResource(badgeRes),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(priceRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (subRes != null) {
                Text(
                    text = stringResource(subRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun PaywallPreview() {
    TranzlateTheme {
        Surface {
            PaywallContent(
                selected = PaywallPlan.YEARLY,
                purchasing = false,
                onSelect = {},
                onPurchase = {},
                onRestore = {},
                onClose = {},
            )
        }
    }
}
