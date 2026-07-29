package com.codeboxlk.tranzlate.core.translate.engine

import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine

/** One engine adapter's answer — the waterfall folds these into the A3 trace. */
internal sealed interface EngineResult {
    data class Success(
        val text: String,
        /** BCP-47 the ENGINE detected when asked with "auto"; null when src was given. */
        val detectedSource: String? = null,
    ) : EngineResult

    data class Failure(
        val cause: AttemptCause,
    ) : EngineResult
}

/**
 * The engine adapter seam (issue #61 E2). Adapters do ONE engine each and
 * never decide order, quota or fallback — that's the waterfall's job
 * (spec 02 §5.1: the brain owns engine choice).
 */
internal interface TranslateEngine {
    val engine: Engine

    /** [srcLang] may be the "auto" sentinel only for engines that support server-side detect. */
    suspend fun translate(
        text: String,
        srcLang: String,
        tgtLang: String,
    ): EngineResult
}
