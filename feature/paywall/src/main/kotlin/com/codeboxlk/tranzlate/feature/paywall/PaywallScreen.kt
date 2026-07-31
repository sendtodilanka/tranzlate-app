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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.codeboxlk.tranzlate.core.designsystem.LocalSpacing
import com.codeboxlk.tranzlate.core.designsystem.TranzlateTheme
import com.codeboxlk.tranzlate.core.model.PlanPrice
import com.codeboxlk.tranzlate.core.ui.rememberWindowInfo

/**
 * EXPANDED-window bound for the pricing column (issue #88 lens): medium fills
 * per the owner + M3, but three plan cards across a 1200dp+ window break the
 * 40-60cpl readability rule -- expanded re-centres at the old readable width.
 */
private val EXPANDED_CONTENT_MAX = 560.dp

/** §4 visual anchoring: the Yearly card is deliberately wider than its siblings. */
private const val YEARLY_CARD_WEIGHT = 1.4f

/**
 * BUSINESS_MODEL §4 — the paywall, verbatim: dismissible ✕ (Play policy),
 * benefit-led bullets, three periods with Yearly pre-selected (trial + save
 * badge), per-day framing, "Cancel anytime", CTA follows the selection,
 * Restore · Terms · Privacy.
 *
 * Terms/Privacy open the remote-served URLs in the browser. The purchase CTA
 * reaches the real billing gateway; a failure is always reported honestly and
 * a user-cancelled sheet says nothing at all.
 *
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
    val legalLinks by viewModel.legalLinks.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val uriHandler = LocalUriHandler.current

    // Already-PRO (or a purchase that just resolved) never sees the pitch.
    LaunchedEffect(isPro) {
        if (isPro) onClose()
    }
    val prices by viewModel.prices.collectAsStateWithLifecycle()
    val purchaseFailed = stringResource(R.string.paywall_purchase_unavailable)
    val restoreFailed = stringResource(R.string.paywall_restore_failed)
    val restoredFree = stringResource(R.string.paywall_restore_nothing)
    val linkUnavailable = stringResource(R.string.paywall_link_unavailable)
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            snackbarHostState.showSnackbar(
                when (event) {
                    PaywallEvent.PURCHASE_FAILED -> purchaseFailed
                    PaywallEvent.RESTORE_FAILED -> restoreFailed
                    PaywallEvent.RESTORED_FREE -> restoredFree
                    PaywallEvent.LINK_UNAVAILABLE -> linkUnavailable
                },
            )
        }
    }

    /**
     * Play requires Terms and Privacy to be reachable from the purchase screen.
     * Two things can stop that — the URL has not been fetched yet, or the device
     * has no browser (`AndroidUriHandler` raises `IllegalArgumentException` when
     * nothing resolves `ACTION_VIEW`). Both must tell the user, not fail mutely.
     */
    val openLink: (String) -> Unit = { url ->
        val opened =
            url.isNotBlank() &&
                runCatching { uriHandler.openUri(url) }.isSuccess
        if (!opened) viewModel.onLegalLinkUnavailable()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        PaywallContent(
            onOpenTerms = { openLink(legalLinks.termsUrl) },
            onOpenPrivacy = { openLink(legalLinks.privacyUrl) },
            selected = selected,
            purchasing = purchasing,
            prices = prices,
            onRetryPrices = viewModel::refreshPrices,
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
    /** Store prices keyed by offering id; empty until Play answers. */
    prices: Map<String, PlanPrice>,
    onRetryPrices: () -> Unit,
    onSelect: (PaywallPlan) -> Unit,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenTerms: () -> Unit = {},
    onOpenPrivacy: () -> Unit = {},
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier =
                Modifier
                    // Issue #88 (owner + M3 breakpoints): compact + medium FILL
                    // the width — the lg24 side padding already equals the medium
                    // margin. EXPANDED re-bounds the column (lens catch: three
                    // plan cards across ~1232dp break the 40-60cpl rule; M3
                    // expanded prefers bounded or paned content).
                    .then(
                        if (rememberWindowInfo().isExpanded) {
                            Modifier.widthIn(max = EXPANDED_CONTENT_MAX)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    ).padding(horizontal = spacing.lg24),
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
                    price = prices[PaywallPlan.WEEKLY.offeringId],
                    selected = selected == PaywallPlan.WEEKLY,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
                PlanCard(
                    plan = PaywallPlan.MONTHLY,
                    titleRes = R.string.paywall_plan_monthly,
                    price = prices[PaywallPlan.MONTHLY.offeringId],
                    selected = selected == PaywallPlan.MONTHLY,
                    onSelect = onSelect,
                    modifier = Modifier.weight(1f),
                )
                // No "SAVE ~60%" badge and no per-day figure: both were computed
                // from the prices we invented, so with real ones they would have
                // to be derived from real amounts. That plumbing does not exist
                // yet, and an uncomputed discount claim is exactly the class of
                // statement this screen is being cleaned of.
                PlanCard(
                    plan = PaywallPlan.YEARLY,
                    titleRes = R.string.paywall_plan_yearly,
                    price = prices[PaywallPlan.YEARLY.offeringId],
                    selected = selected == PaywallPlan.YEARLY,
                    onSelect = onSelect,
                    modifier = Modifier.weight(YEARLY_CARD_WEIGHT),
                )
            }
            Spacer(Modifier.height(spacing.md16))
            // Keyed on the SELECTED plan, not on the map being empty: a partial
            // answer from the store would otherwise leave the button silently
            // disabled with nothing on screen explaining why.
            if (prices[selected.offeringId] == null) {
                TextButton(
                    onClick = onRetryPrices,
                    modifier = Modifier.testTag("tt_paywall_price_retry"),
                ) {
                    Text(text = stringResource(R.string.paywall_price_unavailable))
                }
            }
            Text(
                text = stringResource(R.string.paywall_cancel_anytime),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(spacing.md16))
            val selectedPrice = prices[selected.offeringId]
            Button(
                onClick = onPurchase,
                // A button that can charge must not be tappable while the amount
                // it will charge is unknown. This is the gate, not a nicety.
                enabled = !purchasing && selectedPrice != null,
                modifier = Modifier.fillMaxWidth().testTag("tt_paywall_cta"),
            ) {
                if (purchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(text = ctaLabel(selectedPrice))
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
                TextButton(
                    onClick = onOpenTerms,
                    modifier = Modifier.testTag("tt_paywall_terms"),
                ) { Text(stringResource(R.string.paywall_terms)) }
                TextButton(
                    onClick = onOpenPrivacy,
                    modifier = Modifier.testTag("tt_paywall_privacy"),
                ) { Text(stringResource(R.string.paywall_privacy)) }
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
    /** The store's own figure, or null while it has not answered. */
    price: PlanPrice?,
    selected: Boolean,
    onSelect: (PaywallPlan) -> Unit,
    modifier: Modifier = Modifier,
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
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = price?.formattedPrice ?: stringResource(R.string.paywall_price_pending),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            // Shown only when the STORE says this account still has a trial
            // coming — eligibility is per account, and a user who already spent
            // the intro offer must not be promised another one.
            trialLabel(price)?.let { label ->
                Text(
                    text = label,
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
                prices = previewPrices,
                onRetryPrices = {},
                onSelect = {},
                onPurchase = {},
                onRestore = {},
                onClose = {},
            )
        }
    }
}

/** THE ITEMS: benefit rows + the three plan cards (Yearly is the wide one). */
@PreviewLightDark
@Composable
private fun PaywallItemsPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.padding(LocalSpacing.current.md16)) {
                BenefitRow(R.string.paywall_benefit_no_ads)
                BenefitRow(R.string.paywall_benefit_unlimited_ai)
                Spacer(Modifier.height(LocalSpacing.current.md16))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm8),
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                ) {
                    PlanCard(
                        plan = PaywallPlan.WEEKLY,
                        titleRes = R.string.paywall_plan_weekly,
                        price = previewPrices[PaywallPlan.WEEKLY.offeringId],
                        selected = false,
                        onSelect = {},
                        modifier = Modifier.weight(1f),
                    )
                    PlanCard(
                        plan = PaywallPlan.YEARLY,
                        titleRes = R.string.paywall_plan_yearly,
                        price = previewPrices[PaywallPlan.YEARLY.offeringId],
                        selected = true,
                        onSelect = {},
                        modifier = Modifier.weight(YEARLY_CARD_WEIGHT),
                    )
                }
            }
        }
    }
}

/**
 * The trial line, or null when there is nothing true to say.
 *
 * Three cases, deliberately distinct: an exact day count when the store's own
 * unit converts without rounding, a bare "free trial included" when it is a
 * month or a year (where "30-day" would be an invention), and silence when this
 * account is not eligible.
 */
@Composable
private fun trialLabel(price: PlanPrice?): String? {
    if (price == null || !price.hasTrial) return null
    val days = price.trialDays ?: return stringResource(R.string.paywall_trial_generic)
    return pluralStringResource(R.plurals.paywall_trial_days, days, days)
}

/** The call to action names the trial only when the buyer actually gets one. */
@Composable
private fun ctaLabel(price: PlanPrice?): String {
    if (price == null || !price.hasTrial) return stringResource(R.string.paywall_cta_continue)
    val days = price.trialDays ?: return stringResource(R.string.paywall_cta_trial_generic)
    return pluralStringResource(R.plurals.paywall_cta_trial_days, days, days)
}

/**
 * The state this change introduced: the store has not answered, so there is no
 * price to show and nothing may be charged. Rule 7 — it is a meaningful state,
 * so it is previewable.
 */
@PreviewLightDark
@Composable
private fun PaywallPricesUnknownPreview() {
    TranzlateTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            PaywallContent(
                selected = PaywallPlan.YEARLY,
                purchasing = false,
                prices = emptyMap(),
                onRetryPrices = {},
                onSelect = {},
                onPurchase = {},
                onRestore = {},
                onClose = {},
            )
        }
    }
}

/** Literal store answers, so the preview shows what a real Play response renders as. */
private val previewPrices =
    mapOf(
        PaywallPlan.WEEKLY.offeringId to PlanPrice("Rs 690.00"),
        PaywallPlan.MONTHLY.offeringId to PlanPrice("Rs 1,750.00"),
        PaywallPlan.YEARLY.offeringId to PlanPrice("Rs 10,500.00", trialDays = 7, hasTrial = true),
    )
