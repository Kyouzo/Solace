package dev.kyouzo.solace.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.coroutines.flow.Flow

enum class SyncState { PENDING, PROCESSING, COMPLETED, FAILED }
enum class SyncDestination { CLOUD, PEER }

class DatabaseConverters {
    @TypeConverter fun stateToString(value: SyncState): String = value.name
    @TypeConverter fun stateFromString(value: String): SyncState = SyncState.valueOf(value)
    @TypeConverter fun destinationToString(value: SyncDestination): String = value.name
    @TypeConverter fun destinationFromString(value: String): SyncDestination = SyncDestination.valueOf(value)
}

@Entity(tableName = "location_points", indices = [Index("recordedAt"), Index("syncState")])
data class LocationPointEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val deviceId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val altitude: Double?,
    val speed: Float?,
    val bearing: Float?,
    val batteryLevel: Int?,
    val recordedAt: Long,
    val receivedAt: Long?,
    val syncState: SyncState = SyncState.PENDING
)

@Entity(tableName = "sync_outbox", indices = [Index("recordId"), Index(value = ["recordId", "destination"], unique = true)])
data class SyncOutboxEntity(
    @PrimaryKey val id: String,
    val recordId: String,
    val destination: SyncDestination,
    val attempts: Int = 0,
    val lastAttemptAt: Long? = null,
    val state: SyncState = SyncState.PENDING,
    val createdAt: Long
)

@Entity(tableName = "remote_locations", indices = [Index(value = ["userId", "recordedAt"])])
data class RemoteLocationEntity(@PrimaryKey val id: String, val userId: String, val latitude: Double, val longitude: Double, val accuracy: Float, val recordedAt: Long, val receivedAt: Long)

@Entity(tableName = "devices")
data class DeviceEntity(@PrimaryKey val id: String, val userId: String, val name: String, val batteryLevel: Int?, val charging: Boolean?, val lastSeenAt: Long?)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(@PrimaryKey val id: Int = 1, val sharingEnabled: Boolean = false, val retentionDays: Int? = null, val lastSyncAt: Long? = null)

@Entity(tableName = "saved_places", indices = [Index("createdAt")])
data class SavedPlaceEntity(
    @PrimaryKey val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val createdAt: Long
)

@Dao
interface LocationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(point: LocationPointEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOutbox(item: SyncOutboxEntity): Long
    @Query("SELECT * FROM location_points WHERE recordedAt BETWEEN :from AND :to ORDER BY recordedAt") fun observeBetween(from: Long, to: Long): Flow<List<LocationPointEntity>>
    @Query("SELECT * FROM location_points ORDER BY recordedAt DESC LIMIT 1") suspend fun last(): LocationPointEntity?
    @Query("SELECT * FROM sync_outbox WHERE state = 'PENDING' ORDER BY createdAt LIMIT :limit") suspend fun pending(limit: Int): List<SyncOutboxEntity>
    @Query("UPDATE sync_outbox SET state = :state, attempts = attempts + 1, lastAttemptAt = :at WHERE id IN (:ids)") suspend fun markOutbox(ids: List<String>, state: SyncState, at: Long)
    @Query("UPDATE location_points SET syncState = :state WHERE id IN (:ids)") suspend fun markPoints(ids: List<String>, state: SyncState)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePlace(place: SavedPlaceEntity)
    @Query("SELECT * FROM saved_places ORDER BY createdAt DESC") fun observePlaces(): Flow<List<SavedPlaceEntity>>
    @Query("DELETE FROM saved_places WHERE id = :id") suspend fun deletePlace(id: String)

    @Transaction
    suspend fun insertCapturedPoint(point: LocationPointEntity, cloud: SyncOutboxEntity, peer: SyncOutboxEntity) {
        if (insert(point) != -1L) { insertOutbox(cloud); insertOutbox(peer) }
    }
}

@TypeConverters(DatabaseConverters::class)
@Database(entities = [LocationPointEntity::class, SyncOutboxEntity::class, RemoteLocationEntity::class, DeviceEntity::class, AppSettingsEntity::class, SavedPlaceEntity::class], version = 2, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "solace.db").addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
        private val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS saved_places (id TEXT NOT NULL, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, radiusMeters REAL NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))"); db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_places_createdAt ON saved_places(createdAt)") } }
    }
}
