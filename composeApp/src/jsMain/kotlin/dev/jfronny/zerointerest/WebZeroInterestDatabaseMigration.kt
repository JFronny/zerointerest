package dev.jfronny.zerointerest

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.openDatabase
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.TransactionTemplate
import dev.jfronny.zerointerest.service.ZeroInterestDatabase
import dev.jfronny.zerointerest.service.db.RoomZeroInterestDatabase
import dev.jfronny.zerointerest.service.db.SummaryEntity
import dev.jfronny.zerointerest.service.db.SummaryHeadEntity
import dev.jfronny.zerointerest.service.db.SummaryTransactionEntity
import kotlinx.serialization.json.Json

private inline fun <T : Any> jso(): T = js("({})")
private inline fun <T : Any> jso(block: T.() -> Unit): T = jso<T>().apply(block)

private external interface JsSummaryTrust {
    var roomId: String
    var eventId: String
    var state: String
}

private external interface JsSummaryHead {
    var roomId: String
    var eventId: String
}

private external interface JsSummaryLink {
    var roomId: String
    var summaryId: String
    var otherId: String
}

private external interface JsTransactionTemplate {
    var roomId: String
    var id: String
    var description: String
    var sender: String
    var receivers: String // Serialized map
}

class WebZeroInterestDatabaseMigration {
    private var _db: Database? = null

    private suspend fun getDb(): Database {
        if (_db == null) {
            _db = openDatabase("zerointerest-summary-trust", 3) { database, oldVersion, newVersion ->
                if (oldVersion < 1) {
                    database.createObjectStore("summary_trust", KeyPath("roomId", "eventId"))
                    val headStore = database.createObjectStore("summary_head", KeyPath("roomId", "eventId"))
                    headStore.createIndex("roomId", KeyPath("roomId"), unique = false)
                }
                if (oldVersion < 2) {
                    val summaryStore = database.createObjectStore("summary_link", KeyPath("roomId", "summaryId", "otherId"))
                    summaryStore.createIndex("parent", KeyPath("roomId", "summaryId"), unique = false)
                    val transactionStore = database.createObjectStore("summary_transaction", KeyPath("roomId", "summaryId", "otherId"))
                    transactionStore.createIndex("summary", KeyPath("roomId", "summaryId"), unique = false)
                    transactionStore.createIndex("transaction", KeyPath("roomId", "otherId"), unique = false)
                }
                if (oldVersion < 3) {
                    val templateStore = database.createObjectStore("transaction_template", KeyPath("roomId", "id"))
                    templateStore.createIndex("roomId", KeyPath("roomId"), unique = false)
                }
            }
        }
        return _db!!
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    suspend fun migrateTo(db: RoomZeroInterestDatabase) {
        getDb().writeTransaction("transaction_template") {
            val os = objectStore("transaction_template")
            os.getAll().forEach { js ->
                val entity = js.asDynamic() as JsTransactionTemplate
                db.addTransactionTemplate(RoomId(entity.roomId), TransactionTemplate(
                    id = entity.id,
                    description = entity.description,
                    sender = UserId(entity.sender),
                    receivers = Json.decodeFromString(entity.receivers)
                ))
                os.delete(Key(entity.roomId, entity.id))
            }
        }
        getDb().writeTransaction("summary_trust") {
            val os = objectStore("summary_trust")
            os.getAll().forEach { js ->
                val entity = js.asDynamic() as JsSummaryTrust
                when (enumValueOf<ZeroInterestDatabase.TrustState>(entity.state)) {
                    ZeroInterestDatabase.TrustState.UNTRUSTED -> {}
                    ZeroInterestDatabase.TrustState.TRUSTED -> db.markTrusted(RoomId(entity.roomId), EventId(entity.eventId))
                    ZeroInterestDatabase.TrustState.REJECTED -> db.markRejected(RoomId(entity.roomId), EventId(entity.eventId))
                }
                os.delete(Key(entity.roomId, entity.eventId))
            }
        }
        getDb().writeTransaction("summary_head") {
            val os = objectStore("summary_head")
            os.getAll().forEach { js ->
                val entity = js.asDynamic() as JsSummaryHead
                db.db.summaryHeadDao().insert(SummaryHeadEntity(entity.roomId, entity.eventId))
                os.delete(Key(entity.roomId, entity.eventId))
            }
        }
        getDb().writeTransaction("summary_link") {
            val os = objectStore("summary_link")
            db.db.summaryDao().insertSummary(os.getAll().map { js ->
                val entity = js.asDynamic() as JsSummaryLink
                os.delete(Key(entity.summaryId, entity.otherId))
                SummaryEntity(
                    roomId = entity.roomId,
                    summaryId = entity.summaryId,
                    parentId = entity.otherId,
                )
            })
        }
        getDb().writeTransaction("summary_transaction") {
            val os = objectStore("summary_transaction")
            db.db.summaryDao().insertTransactions(os.getAll().map { js ->
                val entity = js.asDynamic() as JsSummaryLink
                os.delete(Key(entity.roomId, entity.summaryId, entity.otherId))
                SummaryTransactionEntity(
                    roomId = entity.roomId,
                    summaryId = entity.summaryId,
                    transactionId = entity.otherId
                )
            })
        }
    }
}
