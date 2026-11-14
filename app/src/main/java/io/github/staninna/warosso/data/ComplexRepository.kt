package io.github.staninna.warosso.data

import androidx.room.withTransaction
import io.github.staninna.warosso.domain.Complex
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.Entrance
import io.github.staninna.warosso.domain.LatLng
import io.github.staninna.warosso.domain.OsmData
import io.github.staninna.warosso.domain.Setup
import io.github.staninna.warosso.domain.areaAround
import io.github.staninna.warosso.domain.copyBeside
import io.github.staninna.warosso.domain.holdingOrNearest
import io.github.staninna.warosso.domain.withOsm
import io.github.staninna.warosso.domain.withSetup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The complex as stored on the phone, and the only code that writes it.
 *
 * [data] is the one read path: a whole, consistent snapshot. Every write runs in one transaction,
 * re-reads the snapshot in that same transaction and publishes it, so [data] is up to date by
 * the time a write returns. Writes run in the app's scope: leaving a screen does not cancel one.
 */
class ComplexRepository(
    private val db: WarOssoDatabase,
    private val scope: CoroutineScope,
    private val readSeed: () -> String,
) {
    private val dao = db.dao()
    private val snapshot = MutableStateFlow<ComplexData?>(null)

    /** Null until the database is read, which on first launch includes importing the seed. */
    val data: StateFlow<ComplexData?> = snapshot.asStateFlow()

    init {
        launchWrite { if (dao.complex() == null) dao.replaceAll(seed()) }
    }

    fun updateComplex(change: (Complex) -> Complex): Job =
        launchWrite { dao.complex()?.let { dao.upsert(change(it)) } }

    /** Stores the owner's settings of a building. An empty [name] goes back to the building's own name. */
    fun updateBuilding(osmId: Long, name: String, streetDoors: Boolean): Job =
        launchWrite { dao.updateBuilding(osmId, name.trim().ifEmpty { null }, streetDoors) }

    /** Adds an empty entrance at [latLng] on the nearest building. Null when OSM has no buildings to attach it to. */
    suspend fun addEntrance(latLng: LatLng): Long? = awaitWrite {
        dao.buildings().holdingOrNearest(latLng)?.let { dao.upsert(Entrance(buildingOsmId = it.osmId, latLng = latLng)) }
    }

    /** Stores a copy of the entrance next to it, see [copyBeside]. Null when the entrance is gone. */
    suspend fun copyEntrance(id: Long): Long? = awaitWrite { dao.entrance(id)?.let { dao.upsert(it.copyBeside()) } }

    /** Applies [change] to the entrance as it is stored right now, so quick edits never overwrite each other. */
    fun updateEntrance(id: Long, change: (Entrance) -> Entrance): Job =
        launchWrite { dao.entrance(id)?.let { dao.upsert(change(it)) } }

    fun deleteEntrance(id: Long): Job = launchWrite { dao.deleteEntrance(id) }

    /** Takes fresh addresses and outlines from OSM. What the owner set up stays, see [withOsm]. */
    suspend fun applyOsm(fresh: OsmData) = awaitWrite { transform { it.withOsm(fresh) } }

    /** Replaces the owner's setup with an imported one, see [withSetup]. */
    suspend fun replaceSetup(setup: Setup) = awaitWrite { transform { it.withSetup(setup) } }

    /** The bundled BAG/OSM seed as a first snapshot: the street with the most addresses, outlined around its buildings. */
    private fun seed(): ComplexData {
        val osm = parseOverpass(readSeed())
        val street = osm.addresses.groupingBy { it.street }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
        val area = areaAround(street, osm.addresses, osm.buildings)
        return ComplexData(Complex(street, area), osm.addresses, osm.buildings, entrances = emptyList())
    }

    private suspend fun transform(change: (ComplexData) -> ComplexData) {
        dao.load()?.let { dao.replaceAll(change(it)) }
    }

    private fun launchWrite(block: suspend () -> Unit): Job = scope.launch { write(block) }

    private suspend fun <T> awaitWrite(block: suspend () -> T): T = scope.async { write(block) }.await()

    private suspend fun <T> write(block: suspend () -> T): T {
        val (result, after) = db.withTransaction { block() to dao.load() }
        snapshot.value = after
        return result
    }
}
