package com.codeboxlk.tranzlate.core.translate.engine

import com.google.mlkit.nl.languageid.LanguageIdentification
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** ML Kit's below-threshold answer — not a language. */
private const val UNDETERMINED = "und"

/**
 * On-device Language ID (issue #61 — the owner's detect rule): short/ambiguous
 * text identifies as "und" below ML Kit's default 0.5 confidence, and the
 * waterfall must NOT feed a guess into MLKit — null here means "let the online
 * engines detect server-side".
 */
@Singleton
internal class MlKitLanguageIdentifier
    @Inject
    constructor() {
        suspend fun identify(text: String): String? {
            val client = LanguageIdentification.getClient()
            return try {
                val tag = client.identifyLanguage(text).await()
                if (tag == UNDETERMINED) null else tag
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
            ) {
                null // identification is best-effort — the waterfall has online detect
            } finally {
                client.close()
            }
        }
    }
