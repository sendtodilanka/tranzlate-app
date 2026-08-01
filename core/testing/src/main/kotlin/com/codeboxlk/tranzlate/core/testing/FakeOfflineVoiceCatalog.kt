package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.domain.speech.OfflineVoiceCatalog

/**
 * Settable offline-voice fake — tests drive [ids] to say which languages this
 * "device" can speak without a connection.
 *
 * The default is EMPTY on purpose. That is the state of a device with no TTS
 * engine, and it is the state every feature must survive without losing a row:
 * the voice set decorates the language list, it never gates it. A fake that
 * defaulted to a populated set would let a gating bug ship green.
 *
 * [calls] is the spy for the one-shot contract — a caller that asks per row
 * instead of per list shows up here as 194 instead of 1.
 */
class FakeOfflineVoiceCatalog(
    var ids: Set<String> = emptySet(),
) : OfflineVoiceCatalog {
    var calls: Int = 0
        private set

    override suspend fun offlineVoiceLanguageIds(): Set<String> {
        calls++
        return ids
    }
}
