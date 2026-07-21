package com.codeboxlk.tranzlate.core.model

/**
 * EDGE_CASES §1 — one resolver per blockable action answers
 * "can I do this right now? if not, why, and what should the user do?".
 * The UI only reflects this answer: enable on [Ready], otherwise disable AND
 * show the message + action. Never a silent dead-end (§7 NO-DEAD-END rule).
 */
sealed interface Availability {
    data object Ready : Availability

    /**
     * @property reason stable enum — not a string (EDGE_CASES §1).
     * @property message strings.xml resource id (localized via C-3 keys).
     * @property primaryAction what the user can do (a CTA), nullable.
     */
    data class Blocked(
        val reason: BlockedReason,
        val message: Int,
        val primaryAction: AvailabilityAction?,
    ) : Availability

    /**
     * Transient "wait" (EDGE_CASES §1 bullet 3): e.g. entitlement still
     * [Entitlement.Loading] — resolve first, never block/allow on stale data.
     */
    data object Pending : Availability
}

/** EDGE_CASES §4 reason catalog (stable enum, message/action mapping lives with STRINGS). */
enum class BlockedReason {
    NO_INTERNET_ONLINE_ENGINE,
    OFFLINE_LANG_NOT_DOWNLOADED_OFFLINE,
    ONLINE_ONLY_LANG_OFFLINE,
    DAILY_LIMIT_REACHED,
    EMPTY_INPUT,
    OVER_CHAR_LIMIT,
}

/** EDGE_CASES §4 action catalog — the CTA a Blocked state offers. */
enum class AvailabilityAction {
    GO_ONLINE,
    CONNECT,
    PICK_LANGUAGE,
    OFFLINE_LANGUAGES,
    UPGRADE,
    SWITCH_MODE,
}
