package com.codeboxlk.tranzlate.core.common

/**
 * Minimal success/failure seam shared across layers (plan §2 Ring 2).
 *
 * Feature-facing outcomes with richer semantics (e.g. translation) use their own
 * sealed types (`TranslationOutcome`); [AppResult] is for plumbing-level operations
 * (persistence writes, purchases, restores) where only success/failure matters.
 */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>

    data class Failure(val error: Throwable) : AppResult<Nothing>
}

inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (Throwable) -> R,
): R = when (this) {
    is AppResult.Success -> onSuccess(value)
    is AppResult.Failure -> onFailure(error)
}

fun <T> AppResult<T>.getOrNull(): T? = (this as? AppResult.Success)?.value
