package lk.salli.data.ai

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-scoped bus for download progress, populated by the download worker and observed by
 * the settings UI. WorkManager gives us its own progress via `getWorkInfoByIdFlow`, but (a) the
 * UI doesn't know the work id and (b) it loses state between worker runs and process restarts.
 * This singleton always reflects the latest snapshot, which is all the UI cares about.
 */
class ModelProgressStore {

    sealed interface Snapshot {
        data object Idle : Snapshot
        data class Running(val bytesDownloaded: Long, val totalBytes: Long) : Snapshot
        data class Installed(val sizeBytes: Long) : Snapshot
        data class Failed(val reason: String) : Snapshot
    }

    private val _state = MutableStateFlow<Snapshot>(Snapshot.Idle)
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun update(snapshot: Snapshot) {
        _state.value = snapshot
    }
}
