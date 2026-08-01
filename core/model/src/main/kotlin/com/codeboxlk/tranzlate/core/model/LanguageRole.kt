package com.codeboxlk.tranzlate.core.model

/**
 * Which side of the translate pair a language id is playing (issue #130 rev.3,
 * ruled: ONE type shared by the picker's target param, the usage store and the
 * per-role recents — three private enums would drift apart the first time one
 * gains a member).
 *
 * A [SOURCE] id is always RESOLVED — the "auto" sentinel is a picker affordance,
 * never a language, and must not be recorded under either role.
 */
enum class LanguageRole {
    SOURCE,
    TARGET,
}
