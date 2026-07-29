package com.codeboxlk.tranzlate.core.translate.engine

import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The old app sent a desktop UA too — the endpoint serves browsers. */
private const val USER_AGENT = "Mozilla/5.0"

/**
 * Tier 2 — GOT, the unofficial `translate_a/single` endpoint.
 *
 * **D-E1: RISK ACCEPTED by the product owner** (ToS + reliability documented in
 * spec 02 §2 — no SLA, may break without notice, 429/IP-ban possible). Fenced
 * accordingly: remote kill-switch (`got_enabled`, read by the waterfall),
 * per-call timeout (`got_timeout_ms`), and the response's detected-`src` field
 * IS captured — the old app threw it away and lost auto-detect metadata.
 */
@Singleton
internal class GotEngine
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
        private val config: RemoteConfigSource,
    ) : TranslateEngine {
        override val engine: Engine = Engine.ONLINE_GOOGLE

        override suspend fun translate(
            text: String,
            srcLang: String,
            tgtLang: String,
        ): EngineResult {
            val url =
                HttpUrl
                    .Builder()
                    .scheme("https")
                    .host("translate.googleapis.com")
                    .addPathSegments("translate_a/single")
                    .addQueryParameter("client", "gtx")
                    .addQueryParameter("dt", "t")
                    .addQueryParameter("sl", srcLang) // "auto" → server-side detect
                    .addQueryParameter("tl", tgtLang)
                    .addQueryParameter("q", text)
                    .build()
            val request =
                Request
                    .Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .build()
            val call =
                httpClient
                    .newBuilder()
                    .callTimeout(config.gotTimeoutMs(), TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(request)
            return try {
                call.await().use { response ->
                    if (!response.isSuccessful) {
                        EngineResult.Failure(AttemptCause.ENGINE_ERROR)
                    } else {
                        parseGotResponse(response.body?.string().orEmpty())
                    }
                }
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown
            } catch (ignored: InterruptedIOException) {
                EngineResult.Failure(AttemptCause.TIMEOUT) // OkHttp callTimeout surfaces here
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
            ) {
                EngineResult.Failure(AttemptCause.OFFLINE) // I/O mid-flight = network trouble
            }
        }
    }

/**
 * The endpoint answers a bare JSON array-of-arrays:
 * `[[["Bonjour","Good morning",…],…], null, "en", …]` —
 * `[0][n][0]` are translated segments (joined), `[2]` is the detected source.
 */
internal fun parseGotResponse(body: String): EngineResult =
    try {
        val root = Json.parseToJsonElement(body).jsonArray
        val segments = root[0].jsonArray
        val text =
            buildString {
                for (segment in segments) append(segment.jsonArray[0].jsonPrimitive.content)
            }
        val detected =
            root
                .getOrNull(2)
                ?.takeIf { it !is JsonNull }
                ?.jsonPrimitive
                ?.content
        EngineResult.Success(text, detected)
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
    ) {
        EngineResult.Failure(AttemptCause.ENGINE_ERROR) // shape drifted — the unofficial risk
    }
