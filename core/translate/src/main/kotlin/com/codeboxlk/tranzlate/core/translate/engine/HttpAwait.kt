package com.codeboxlk.tranzlate.core.translate.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Coroutine bridge for OkHttp with REAL cancellation: cancelling the coroutine
 * cancels the in-flight call (a blocked `execute()` on a dispatcher thread
 * would not respond to job cancellation).
 */
internal suspend fun Call.await(): Response =
    suspendCancellableCoroutine { continuation ->
        enqueue(
            object : Callback {
                override fun onResponse(
                    call: Call,
                    response: Response,
                ) {
                    // Cancel racing the delivery: a resumed-but-never-consumed
                    // Response must still be closed or the connection leaks
                    // (PR-62 lens OPEN-1 — the classic bridge leak).
                    continuation.resume(response) { _, _, _ -> response.close() }
                }

                override fun onFailure(
                    call: Call,
                    e: IOException,
                ) {
                    continuation.resumeWithException(e)
                }
            },
        )
        continuation.invokeOnCancellation { cancel() }
    }
