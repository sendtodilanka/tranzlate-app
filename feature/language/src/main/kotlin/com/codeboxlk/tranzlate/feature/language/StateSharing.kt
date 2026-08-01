package com.codeboxlk.tranzlate.feature.language

/**
 * How long a `stateIn(WhileSubscribed(…))` upstream stays alive after its last
 * collector leaves — one value for this module's two state holders, which each
 * carried their own copy of it until the picker and the offline manager became
 * one module (#130 PR-6/PR-7) and the duplication became visible.
 *
 * 5 s is the value both already used and the one the rest of the app uses: long
 * enough that a rotation or a short trip to another app re-collects the state
 * that is still there, short enough that a backgrounded screen stops paying for
 * a subscription nobody is reading.
 */
internal const val SUBSCRIBE_TIMEOUT_MS = 5_000L
