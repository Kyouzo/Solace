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
data class RemoteLocationEntity(@PrimaryKey val id: String, val userId: String, val latitude: Double, val longitude: Double, val accuracy: Float, val recordedAt: Long, val receivedAt: Long, val batteryLevel: Int? = null)

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
    val createdAt: Long,
    /** HOME, SCHOOL, WORK or CUSTOM: picks the icon and default name. */
    val kind: String = "CUSTOM",
    /** 0 = only on this phone so far, 1 = mirrored in the shared cloud table. */
    val syncState: Int = 0,
    /** Tombstone: removed locally, waiting for the cloud delete before being purged. */
    val deleted: Boolean = false
)

/** One row per local calendar day that has recorded points. */
data class DaySummary(val day: String, val pointCount: Int, val firstAt: Long)

@Dao
interface LocationDao {
    @Query("SELECT strftime('%Y-%m-%d', recordedAt / 1000, 'unixepoch', 'localtime') AS day, COUNT(*) AS pointCount, MIN(recordedAt) AS firstAt FROM location_points GROUP BY day ORDER BY day DESC") fun observeDays(): Flow<List<DaySummary>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(point: LocationPointEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertOutbox(item: SyncOutboxEntity): Long
    @Query("SELECT * FROM location_points WHERE recordedAt BETWEEN :from AND :to ORDER BY recordedAt") fun observeBetween(from: Long, to: Long): Flow<List<LocationPointEntity>>
    @Query("SELECT * FROM location_points ORDER BY recordedAt DESC LIMIT 1") suspend fun last(): LocationPointEntity?
    @Query("SELECT * FROM sync_outbox WHERE state = 'PENDING' ORDER BY createdAt LIMIT :limit") suspend fun pending(limit: Int): List<SyncOutboxEntity>
    @Query("UPDATE sync_outbox SET state = :state, attempts = attempts + 1, lastAttemptAt = :at WHERE id IN (:ids)") suspend fun markOutbox(ids: List<String>, state: SyncState, at: Long)
    @Query("UPDATE location_points SET syncState = :state WHERE id IN (:ids)") suspend fun markPoints(ids: List<String>, state: SyncState)
    @Query("SELECT * FROM sync_outbox WHERE state = 'PENDING' AND destination = :destination ORDER BY createdAt LIMIT :limit") suspend fun pendingFor(destination: SyncDestination, limit: Int): List<SyncOutboxEntity>
    @Query("SELECT * FROM location_points WHERE id IN (:ids)") suspend fun pointsByIds(ids: List<String>): List<LocationPointEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveRemote(location: RemoteLocationEntity)
    @Query("SELECT * FROM location_points ORDER BY recordedAt DESC LIMIT 1") fun observeLast(): Flow<LocationPointEntity?>
    @Query("SELECT * FROM saved_places WHERE deleted = 0") suspend fun places(): List<SavedPlaceEntity>
    @Query("DELETE FROM sync_outbox WHERE recordId IN (SELECT id FROM location_points WHERE recordedAt < :cutoff)") suspend fun deleteOutboxBefore(cutoff: Long)
    @Query("DELETE FROM location_points WHERE recordedAt < :cutoff") suspend fun deletePointsBefore(cutoff: Long)
    @Transaction suspend fun purgeBefore(cutoff: Long) { deleteOutboxBefore(cutoff); deletePointsBefore(cutoff) }
    @Query("DELETE FROM remote_locations") suspend fun clearRemote()
    @Query("SELECT * FROM remote_locations ORDER BY recordedAt DESC LIMIT 1") fun observeLatestRemote(): Flow<RemoteLocationEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePlace(place: SavedPlaceEntity)
    @Query("SELECT * FROM saved_places WHERE deleted = 0 ORDER BY createdAt DESC") fun observePlaces(): Flow<List<SavedPlaceEntity>>
    @Query("DELETE FROM saved_places WHERE id = :id AND syncState = 0") suspend fun hardDeleteUnsyncedPlace(id: String)
    @Query("UPDATE saved_places SET deleted = 1 WHERE id = :id") suspend fun tombstonePlace(id: String)
    /** Never-synced places vanish immediately; synced ones are tombstoned until the cloud delete succeeds. */
    @Transaction suspend fun removePlace(id: String) { hardDeleteUnsyncedPlace(id); tombstonePlace(id) }
    @Query("SELECT * FROM saved_places WHERE deleted = 1") suspend fun deletedPlaces(): List<SavedPlaceEntity>
    @Query("SELECT * FROM saved_places WHERE syncState = 0 AND deleted = 0") suspend fun pendingPlaces(): List<SavedPlaceEntity>
    @Query("UPDATE saved_places SET syncState = 1 WHERE id IN (:ids)") suspend fun markPlacesSynced(ids: List<String>)
    @Query("DELETE FROM saved_places WHERE id IN (:ids)") suspend fun purgePlaces(ids: List<String>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun savePlaces(places: List<SavedPlaceEntity>)
    @Query("DELETE FROM saved_places WHERE syncState = 1 AND id NOT IN (:keep)") suspend fun deleteSyncedPlacesNotIn(keep: List<String>)
    @Query("DELETE FROM saved_places WHERE syncState = 1") suspend fun deleteSyncedPlaces()
    /** Mirrors the shared cloud list locally; places the partner removed disappear here too. */
    @Transaction suspend fun applyRemotePlaces(remote: List<SavedPlaceEntity>) { savePlaces(remote); deleteSyncedPlacesNotIn(remote.map { it.id }) }

    @Transaction
    suspend fun insertCapturedPoint(point: LocationPointEntity, cloud: SyncOutboxEntity, peer: SyncOutboxEntity) {
        if (insert(point) != -1L) { insertOutbox(cloud); insertOutbox(peer) }
    }
}

@TypeConverters(DatabaseConverters::class)
@Database(entities = [LocationPointEntity::class, SyncOutboxEntity::class, RemoteLocationEntity::class, DeviceEntity::class, AppSettingsEntity::class, SavedPlaceEntity::class], version = 4, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationDao(): LocationDao
    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "solace.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE remote_locations ADD COLUMN batteryLevel INTEGER") }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_places ADD COLUMN kind TEXT NOT NULL DEFAULT 'CUSTOM'")
                db.execSQL("ALTER TABLE saved_places ADD COLUMN syncState INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE saved_places ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0")
            }
        }
        private val MIGRATION_1_2 = object : Migration(1, 2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE IF NOT EXISTS saved_places (id TEXT NOT NULL, name TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL, radiusMeters REAL NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))"); db.execSQL("CREATE INDEX IF NOT EXISTS index_saved_places_createdAt ON saved_places(createdAt)") } }
    }
}
