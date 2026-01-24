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
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.service.ZeroInterestDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json

@Database(entities = [SummaryTrustEntity::class, SummaryHeadEntity::class, SummaryEntity::class, SummaryTransactionEntity::class, TransactionTemplateEntity::class], version = 4)
@TypeConverters(ZeroInterestTypeConverters::class)
abstract class ZeroInterestRoomDatabase : RoomDatabase() {
    abstract fun summaryTrustDao(): SummaryTrustDao
    abstract fun summaryHeadDao(): SummaryHeadDao
    abstract fun summaryDao(): SummaryDao
    abstract fun transactionTemplateDao(): TransactionTemplateDao
}

@Entity(primaryKeys = ["roomId", "id"])
data class TransactionTemplateEntity(
    val roomId: String,
    val id: String,
    val description: String,
    val sender: String,
    val receivers: Map<UserId, Long>
)

@Dao
interface TransactionTemplateDao {
    @Query("SELECT * FROM TransactionTemplateEntity WHERE roomId = :roomId")
    fun getTemplates(roomId: String): Flow<List<TransactionTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionTemplateEntity)

    @Query("DELETE FROM TransactionTemplateEntity WHERE roomId = :roomId AND id = :id")
    suspend fun delete(roomId: String, id: String)
}

@Entity(primaryKeys = ["roomId", "summaryId", "parentId"])
data class SummaryEntity(
    val roomId: String,
    val summaryId: String,
    val parentId: String
)

@Entity(primaryKeys = ["roomId", "summaryId", "transactionId"])
data class SummaryTransactionEntity(
    val roomId: String,
    val summaryId: String,
    val transactionId: String
)

@Dao
interface SummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(entities: List<SummaryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(entities: List<SummaryTransactionEntity>)

    @Query("SELECT parentId FROM SummaryEntity WHERE roomId = :roomId AND summaryId = :summaryId")
    suspend fun getParents(roomId: String, summaryId: String): List<String>

    @Query("SELECT transactionId FROM SummaryTransactionEntity WHERE roomId = :roomId AND summaryId IN (:summaryIds)")
    suspend fun getTransactions(roomId: String, summaryIds: List<String>): List<String>

    @Query("SELECT * FROM SummaryTransactionEntity WHERE roomId = :roomId AND transactionId IN (:transactionIds)")
    suspend fun getSummariesForTransactions(roomId: String, transactionIds: List<String>): List<SummaryTransactionEntity>
}


@Entity(primaryKeys = ["roomId", "eventId"])
data class SummaryTrustEntity(
    val roomId: String,
    val eventId: String,
    val state: ZeroInterestDatabase.TrustState
)

@Dao
interface SummaryTrustDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SummaryTrustEntity)

    @Query("SELECT state FROM SummaryTrustEntity WHERE roomId = :roomId AND eventId = :eventId")
    suspend fun getTrustState(roomId: String, eventId: String): ZeroInterestDatabase.TrustState?
}

class ZeroInterestTypeConverters {
    @TypeConverter
    fun toTrustState(value: String) = enumValueOf<ZeroInterestDatabase.TrustState>(value)

    @TypeConverter
    fun fromTrustState(value: ZeroInterestDatabase.TrustState) = value.name

    @TypeConverter
    fun toReceivers(value: String): Map<UserId, Long> = Json.decodeFromString(value)

    @TypeConverter
    fun fromReceivers(value: Map<UserId, Long>): String = Json.encodeToString(value)
}

@Entity(primaryKeys = ["roomId", "eventId"])
data class SummaryHeadEntity(
    val roomId: String,
    val eventId: String
)

@Dao
interface SummaryHeadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SummaryHeadEntity)

    @Query("DELETE FROM SummaryHeadEntity WHERE roomId = :roomId")
    suspend fun clear(roomId: String)

    @Query("SELECT eventId FROM SummaryHeadEntity WHERE roomId = :roomId")
    suspend fun getHeads(roomId: String): List<String>

    @Query("DELETE FROM SummaryHeadEntity WHERE roomId = :roomId AND eventId = :head")
    suspend fun removeHead(roomId: String, head: String)
}
