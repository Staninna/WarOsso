package io.github.staninna.warosso.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.staninna.warosso.data.ComplexRepository
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.Entrance
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** The entrance being edited, with the complex around it. */
data class EntranceEditorState(val data: ComplexData, val entrance: Entrance)

/** Edits go straight to the repository, one small change at a time, so there is no draft to save or lose. */
class EntranceEditorViewModel(private val repository: ComplexRepository, private val entranceId: Long) : ViewModel() {

    /** Null once the entrance is gone, which is the screen's cue to close. */
    val state: StateFlow<EntranceEditorState?> = repository.data
        .map(::stateOf)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), stateOf(repository.data.value))

    fun update(change: (Entrance) -> Entrance) {
        repository.updateEntrance(entranceId, change)
    }

    fun delete() {
        repository.deleteEntrance(entranceId)
    }

    private fun stateOf(data: ComplexData?): EntranceEditorState? =
        data?.entrance(entranceId)?.let { EntranceEditorState(data, it) }
}
