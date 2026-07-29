package com.codeboxlk.tranzlate.core.translate.engine

import com.codeboxlk.tranzlate.core.model.AttemptCause
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Golden payloads captured from the real endpoints' documented/observed shapes
 * (issue #61 E2). The GOT shape is unofficial — a drifted body must fail SOFT
 * (ENGINE_ERROR attempt), never crash the waterfall.
 */
class ResponseParserTest {
    @Test
    fun `GOT single segment with detected source`() {
        val body = """[[["Bonjour","Good morning",null,null,10]],null,"en",null,null,null,null,[]]"""

        val result = parseGotResponse(body) as EngineResult.Success

        assertThat(result.text).isEqualTo("Bonjour")
        assertThat(result.detectedSource).isEqualTo("en")
    }

    @Test
    fun `GOT multi-segment bodies join in order`() {
        val body = """[[["Bonjour, ","Good morning, ",null,null,10],["le monde","world",null,null,10]],null,"en"]"""

        val result = parseGotResponse(body) as EngineResult.Success

        assertThat(result.text).isEqualTo("Bonjour, le monde")
    }

    @Test
    fun `GOT null detected source maps to null - not the string null`() {
        val body = """[[["Hallo","Hello",null,null,1]],null,null]"""

        val result = parseGotResponse(body) as EngineResult.Success

        assertThat(result.detectedSource).isNull()
    }

    @Test
    fun `GOT drifted shape fails soft as ENGINE_ERROR`() {
        val result = parseGotResponse("""{"unexpected":"object"}""")

        assertThat(result).isEqualTo(EngineResult.Failure(AttemptCause.ENGINE_ERROR))
    }

    @Test
    fun `GCT translation with detected source`() {
        val body =
            """{"data":{"translations":[{"translatedText":"Bonjour","detectedSourceLanguage":"en"}]}}"""

        val result = parseGctResponse(body) as EngineResult.Success

        assertThat(result.text).isEqualTo("Bonjour")
        assertThat(result.detectedSource).isEqualTo("en")
    }

    @Test
    fun `GCT with explicit source has no detected field`() {
        val body = """{"data":{"translations":[{"translatedText":"Bonjour"}]}}"""

        val result = parseGctResponse(body) as EngineResult.Success

        assertThat(result.detectedSource).isNull()
    }

    @Test
    fun `GCT error body fails soft as ENGINE_ERROR`() {
        val result = parseGctResponse("""{"error":{"code":403,"message":"quota"}}""")

        assertThat(result).isEqualTo(EngineResult.Failure(AttemptCause.ENGINE_ERROR))
    }
}
