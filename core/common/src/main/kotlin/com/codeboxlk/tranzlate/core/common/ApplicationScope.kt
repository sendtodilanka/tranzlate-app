package com.codeboxlk.tranzlate.core.common

import javax.inject.Qualifier

/**
 * Application-lifetime `CoroutineScope` for fire-and-forget work that must
 * OUTLIVE the caller — the first user is the translate flow's usage stamper
 * (issue #122): a stamp launched here survives the screen's scope dying
 * mid-write, and its failure is the stamp's own, never the translation's.
 *
 * Qualified for the same reason as `BillingScope`: an unqualified
 * `CoroutineScope` binding would be a graph-wide singleton nobody owns. The
 * qualifier lives in Ring 2 (JVM-pure, `javax.inject` only) because the
 * consumer is `:core:domain`; the binding itself is data-layer wiring
 * (`DataModule`), shared by both engine flavors.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
