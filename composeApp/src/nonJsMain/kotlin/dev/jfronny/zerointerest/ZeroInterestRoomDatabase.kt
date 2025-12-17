package dev.jfronny.zerointerest

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import dev.jfronny.zerointerest.service.SummaryTrustDatabase

@Database(entities = [SummaryTrustEntity::class], version = 1)
@TypeConverters(ZeroInterestTypeConverters::class)
abstract class ZeroInterestRoomDatabase : RoomDatabase() {
    abstract fun summaryTrustDao(): SummaryTrustDao
}

@Entity(primaryKeys = ["roomId", "eventId"])
data class SummaryTrustEntity(
    val roomId: String,
    val eventId: String,
    val state: SummaryTrustDatabase.TrustState
)

@Dao
interface SummaryTrustDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SummaryTrustEntity)

    @Query("SELECT state FROM SummaryTrustEntity WHERE roomId = :roomId AND eventId = :eventId")
    suspend fun getTrustState(roomId: String, eventId: String): SummaryTrustDatabase.TrustState?
}

class ZeroInterestTypeConverters {
    @TypeConverter
    fun toTrustState(value: String) = enumValueOf<SummaryTrustDatabase.TrustState>(value)

    @TypeConverter
    fun fromTrustState(value: SummaryTrustDatabase.TrustState) = value.name
}
