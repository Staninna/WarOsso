package io.github.staninna.warosso.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.staninna.warosso.data.ComplexRepository
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.edgeMidpoints
import io.github.staninna.warosso.domain.inserted
import io.github.staninna.warosso.domain.withoutVertex
import io.github.staninna.warosso.ui.map.MapHandle
import io.github.staninna.warosso.ui.map.MarkerKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    /** Null while the database is still loading. Shows a dragged entrance where the finger is. */
    val data: ComplexData? = null,
    /** The spot tapped for a new entrance, waiting for a yes or no. */
    val pending: LatLng? = null,
    /** The polygon being redrawn, or null outside area editing. Saved on "Done". */
    val areaDraft: List<LatLng>? = null,
)

class SetupViewModel(private val repository: ComplexRepository) : ViewModel() {
    private val pending = MutableStateFlow<LatLng?>(null)
    private val areaDraft = MutableStateFlow<List<LatLng>?>(null)

    /** An entrance on the move: its id and where the finger has it. */
    private val dragged = MutableStateFlow<Pair<Long, LatLng>?>(null)

    /** While a midpoint is dragged it has already become this corner of the draft. */
    private var draggedCorner: Int? = null

    val state: StateFlow<SetupUiState> = combine(repository.data, dragged, pending, areaDraft) { data, dragged, pending, draft ->
        val shown = if (data == null || dragged == null) {
            data
        } else {
            data.copy(entrances = data.entrances.map { if (it.id == dragged.first) it.copy(latLng = dragged.second) else it })
        }
        SetupUiState(shown, pending, draft)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SetupUiState(repository.data.value))

    fun onMapClick(point: LatLng) {
        if (areaDraft.value == null) pending.value = point
    }

    fun cancelPending() {
        pending.value = null
    }

    /** Stores the pending entrance and reports its id, or null when OSM knows no building to attach it to. */
    fun confirmPending(onAdded: (Long?) -> Unit) {
        val point = pending.value ?: return
        pending.value = null
        viewModelScope.launch { onAdded(repository.addEntrance(point)) }
    }

    fun onMarkerClick(handle: MapHandle) {
        when (handle.kind) {
            MarkerKind.VERTEX -> areaDraft.update { it?.withoutVertex(handle.id.toInt()) }
            MarkerKind.MIDPOINT -> areaDraft.update { draft ->
                draft?.inserted(draft.edgeMidpoints()[handle.id.toInt()], after = handle.id.toInt())
            }
            else -> Unit
        }
    }

    fun onMarkerDrag(handle: MapHandle, point: LatLng, done: Boolean) {
        when (handle.kind) {
            MarkerKind.PENDING -> pending.value = point
            MarkerKind.ENTRANCE -> dragEntrance(handle.id, point, done)
            MarkerKind.VERTEX -> moveCorner(handle.id.toInt(), point)
            MarkerKind.MIDPOINT -> {
                // The first move turns the midpoint into a real corner; after that the corner moves.
                val corner = draggedCorner ?: (handle.id.toInt() + 1).also { inserted ->
                    areaDraft.update { it?.inserted(point, after = handle.id.toInt()) }
                    draggedCorner = inserted
                }
                moveCorner(corner, point)
                if (done) draggedCorner = null
            }
            else -> Unit
        }
    }

    fun startAreaEdit() {
        pending.value = null
        areaDraft.value = repository.data.value?.complex?.polygon
    }

    /** Starts the outline over, wrapped around the buildings on the complex's street. */
    fun resetArea() {
        areaDraft.value = repository.data.value?.fittedArea()
    }

    fun cancelAreaEdit() {
        areaDraft.value = null
    }

    fun saveArea() {
        val polygon = areaDraft.value ?: return
        areaDraft.value = null
        repository.updateComplex { it.copy(polygon = polygon) }
    }

    fun setStreet(street: String) {
        repository.updateComplex { it.copy(street = street) }
    }

    fun renameBuilding(osmId: Long, name: String) {
        repository.renameBuilding(osmId, name)
    }

    private fun dragEntrance(id: Long, point: LatLng, done: Boolean) {
        dragged.value = id to point
        if (!done) return
        // The marker stays under the finger's last spot until the stored position has replaced it.
        repository.updateEntrance(id) { it.copy(latLng = point) }
            .invokeOnCompletion { dragged.compareAndSet(id to point, null) }
    }

    private fun moveCorner(index: Int, point: LatLng) {
        areaDraft.update { draft -> draft?.mapIndexed { i, corner -> if (i == index) point else corner } }
    }
}
