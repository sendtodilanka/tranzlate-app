package com.codeboxlk.tranzlate.core.translate.engine

import com.codeboxlk.tranzlate.core.config.AppConfig
import com.codeboxlk.tranzlate.core.config.RemoteConfigSource
import com.codeboxlk.tranzlate.core.config.effectiveGctApiKey
import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 3 — official Cloud Translation **v2 REST** (API-key auth; spec 02 §1.1).
 * The accurate paid tier; the waterfall quota-gates it (BUSINESS_MODEL) and
 * only builds it into the chain when the white-label [AppConfig.gctApiKey]
 * is present. Source omitted when asking with "auto" — the API detects and
 * reports `detectedSourceLanguage` (the owner's no-source-given method).
 */
@Singleton
internal class GctEngine
    @Inject
    constructor(
        private val httpClient: OkHttpClient,
        private val config: RemoteConfigSource,
        private val appConfig: AppConfig,
    ) : TranslateEngine {
        override val engine: Engine = Engine.ONLINE_CLOUD_NLP

        override suspend fun translate(
            text: String,
            srcLang: String,
            tgtLang: String,
        ): EngineResult {
            val url =
                HttpUrl
                    .Builder()
                    .scheme("https")
                    .host("translation.googleapis.com")
                    .addPathSegments("language/translate/v2")
                    .addQueryParameter("key", config.effectiveGctApiKey(appConfig))
                    .build()
            val body =
                FormBody
                    .Builder()
                    .add("q", text)
                    .add("target", tgtLang)
                    .add("format", "text")
                    .apply { if (srcLang != "auto") add("source", srcLang) }
                    .build()
            val call =
                httpClient
                    .newBuilder()
                    .callTimeout(config.gctTimeoutMs(), TimeUnit.MILLISECONDS)
                    .build()
                    .newCall(
                        Request
                            .Builder()
                            .url(url)
                            .post(body)
                            .build(),
                    )
            return try {
                call.await().use { response ->
                    if (!response.isSuccessful) {
                        EngineResult.Failure(AttemptCause.ENGINE_ERROR)
                    } else {
                        parseGctResponse(response.body?.string().orEmpty())
                    }
                }
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown
            } catch (ignored: InterruptedIOException) {
                EngineResult.Failure(AttemptCause.TIMEOUT)
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
            ) {
                EngineResult.Failure(AttemptCause.OFFLINE)
            }
        }
    }

/** `{"data":{"translations":[{"translatedText":"…","detectedSourceLanguage":"en"}]}}` */
internal fun parseGctResponse(body: String): EngineResult =
    try {
        val first =
            Json
                .parseToJsonElement(body)
                .jsonObject
                .getValue("data")
                .jsonObject
                .getValue("translations")
                .jsonArray[0]
                .jsonObject
        EngineResult.Success(
            text = first.getValue("translatedText").jsonPrimitive.content,
            detectedSource = first["detectedSourceLanguage"]?.jsonPrimitive?.content,
        )
    } catch (
        @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
    ) {
        EngineResult.Failure(AttemptCause.ENGINE_ERROR)
    }
