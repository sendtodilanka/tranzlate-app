package com.codeboxlk.tranzlate.core.translate

import com.codeboxlk.tranzlate.core.model.OfflineModelState

/**
 * The pure state merge (issue #72, unit-tested): truth is the downloaded set;
 * transient overrides (Downloading/Deleting/Failed) win while an operation is
 * in flight or its failure hasn't been retried/dismissed.
 */
internal fun mergeModelStates(
    capable: Set<String>,
    downloaded: Set<String>,
    transient: Map<String, OfflineModelState>,
): Map<String, OfflineModelState> =
    capable.associateWith { tag ->
        transient[tag]
            ?: if (tag in downloaded) OfflineModelState.Downloaded else OfflineModelState.NotDownloaded
    }
