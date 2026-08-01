package com.codeboxlk.tranzlate.core.testing

import com.codeboxlk.tranzlate.domain.repository.DownloadPrefsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The issue-#90 STANDING permission, faked. [state] is public so a test can
 * both arm it and — the case that matters — assert it was left alone: the
 * dialog's "Download once" is a one-off yes and must never write here.
 */
class FakeDownloadPrefsRepository(
    initiallyAllowed: Boolean = false,
) : DownloadPrefsRepository {
    val state: MutableStateFlow<Boolean> = MutableStateFlow(initiallyAllowed)

    override val allowMobileData: Flow<Boolean> get() = state

    override suspend fun setAllowMobileData(value: Boolean) {
        state.value = value
    }
}
