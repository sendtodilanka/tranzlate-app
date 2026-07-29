package com.codeboxlk.tranzlate.core.translate.engine

import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.codeboxlk.tranzlate.core.model.Engine
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier 1 — ML Kit on-device (spec 02 §1.2: free, private, no network).
 *
 * NEVER triggers a model download (D-E2: downloads belong to the offline
 * manager, Settings Screen B): a missing model is reported as
 * [AttemptCause.MODEL_NOT_DOWNLOADED] so the owner's dialog can offer the
 * download CTA instead of silently pulling ~30MB mid-translation.
 */
@Singleton
internal class MlKitEngine
    @Inject
    constructor() : TranslateEngine {
        override val engine: Engine = Engine.OFFLINE_MLKIT

        override suspend fun translate(
            text: String,
            srcLang: String,
            tgtLang: String,
        ): EngineResult {
            val src =
                TranslateLanguage.fromLanguageTag(srcLang)
                    ?: return EngineResult.Failure(AttemptCause.UNSUPPORTED_PAIR)
            val tgt =
                TranslateLanguage.fromLanguageTag(tgtLang)
                    ?: return EngineResult.Failure(AttemptCause.UNSUPPORTED_PAIR)
            val downloaded = downloadedLanguages()
            if (src !in downloaded || tgt !in downloaded) {
                return EngineResult.Failure(AttemptCause.MODEL_NOT_DOWNLOADED)
            }
            val client =
                Translation.getClient(
                    TranslatorOptions
                        .Builder()
                        .setSourceLanguage(src)
                        .setTargetLanguage(tgt)
                        .build(),
                )
            return try {
                EngineResult.Success(client.translate(text).await())
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
            ) {
                EngineResult.Failure(AttemptCause.ENGINE_ERROR)
            } finally {
                client.close()
            }
        }

        private suspend fun downloadedLanguages(): Set<String> =
            try {
                RemoteModelManager
                    .getInstance()
                    .getDownloadedModels(TranslateRemoteModel::class.java)
                    .await()
                    .map { it.language }
                    .toSet()
            } catch (rethrown: kotlin.coroutines.cancellation.CancellationException) {
                throw rethrown
            } catch (
                @Suppress("TooGenericExceptionCaught", "SwallowedException") ignored: Exception,
            ) {
                emptySet() // unknown state reads as not-downloaded — never guess
            }
    }
