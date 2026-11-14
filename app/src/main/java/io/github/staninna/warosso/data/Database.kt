package io.github.staninna.warosso.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import io.github.staninna.warosso.domain.Address
import io.github.staninna.warosso.domain.Building
import io.github.staninna.warosso.domain.Complex
import io.github.staninna.warosso.domain.ComplexData
import io.github.staninna.warosso.domain.Entrance
import io.github.staninna.warosso.domain.FloorDoors
import io.github.staninna.warosso.domain.LatLng
import kotlinx.serialization.json.Json

@Database(
    entities = [Complex::class, Address::class, Building::class, Entrance::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(ListConverters::class)
abstract class WarOssoDatabase : RoomDatabase() {
    abstract fun dao(): ComplexDao

    companion object {
        const val FILE_NAME = "warosso.db"
    }
}

/** Vertex lists and door lists are always read and written whole, so each lives in one text column. */
class ListConverters {
    @TypeConverter
    fun pointsToText(points: List<LatLng>): String = Json.encodeToString(points)

    @TypeConverter
    fun textToPoints(text: String): List<LatLng> = Json.decodeFromString(text)

    @TypeConverter
    fun floorsToText(floors: List<FloorDoors>): String = Json.encodeToString(floors)

    @TypeConverter
    fun textToFloors(text: String): List<FloorDoors> = Json.decodeFromString(text)
}

/** Plain reads and writes. Every rule about what to keep or merge lives in the domain package. */
@Dao
interface ComplexDao {
    @Query("SELECT * FROM complex LIMIT 1")
    suspend fun complex(): Complex?

    @Query("SELECT * FROM address")
    suspend fun addresses(): List<Address>

    @Query("SELECT * FROM building")
    suspend fun buildings(): List<Building>

    @Query("SELECT * FROM entrance ORDER BY id")
    suspend fun entrances(): List<Entrance>

    @Query("SELECT * FROM entrance WHERE id = :id")
    suspend fun entrance(id: Long): Entrance?

    /** All four tables in one transaction, or null before the seed is in. */
    @Transaction
    suspend fun load(): ComplexData? =
        complex()?.let { ComplexData(it, addresses(), buildings(), entrances()) }

    @Upsert
    suspend fun upsert(complex: Complex)

    @Upsert
    suspend fun upsert(entrance: Entrance): Long

    @Query("DELETE FROM entrance WHERE id = :id")
    suspend fun deleteEntrance(id: Long)

    @Query("UPDATE building SET customName = :name WHERE osmId = :osmId")
    suspend fun renameBuilding(osmId: Long, name: String?)

    @Insert
    suspend fun insertBuildings(buildings: List<Building>)

    @Insert
    suspend fun insertAddresses(addresses: List<Address>)

    @Insert
    suspend fun insertEntrances(entrances: List<Entrance>)

    @Query("DELETE FROM entrance")
    suspend fun clearEntrances()

    @Query("DELETE FROM building")
    suspend fun clearBuildings()

    @Query("DELETE FROM address")
    suspend fun clearAddresses()

    /** Stores [data] as the whole content of the database. Entrances with id 0 get a fresh id. */
    @Transaction
    suspend fun replaceAll(data: ComplexData) {
        clearEntrances()
        clearBuildings()
        clearAddresses()
        upsert(data.complex)
        insertBuildings(data.buildings)
        insertAddresses(data.addresses)
        insertEntrances(data.entrances)
    }
}
