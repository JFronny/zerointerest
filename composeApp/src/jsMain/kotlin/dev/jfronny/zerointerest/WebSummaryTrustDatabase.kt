package dev.jfronny.zerointerest

import com.juul.indexeddb.Database
import com.juul.indexeddb.Key
import com.juul.indexeddb.KeyPath
import com.juul.indexeddb.openDatabase
import dev.jfronny.zerointerest.service.SummaryTrustDatabase
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

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

class WebSummaryTrustDatabase : SummaryTrustDatabase {
    private var _db: Database? = null

    private suspend fun getDb(): Database {
        if (_db == null) {
            _db = openDatabase("zerointerest-summary-trust", 2) { database, oldVersion, newVersion ->
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
            }
        }
        return _db!!
    }

    override suspend fun markTrusted(
        room: RoomId,
        event: EventId
    ) {
        getDb().writeTransaction("summary_trust") {
            objectStore("summary_trust").put(jso<JsSummaryTrust> {
                roomId = room.full
                eventId = event.full
                state = SummaryTrustDatabase.TrustState.TRUSTED.name
            })
        }
    }

    override suspend fun markRejected(
        room: RoomId,
        event: EventId
    ) {
        getDb().writeTransaction("summary_trust") {
            objectStore("summary_trust").put(jso<JsSummaryTrust> {
                roomId = room.full
                eventId = event.full
                state = SummaryTrustDatabase.TrustState.REJECTED.name
            })
        }
    }

    override suspend fun checkTrust(
        room: RoomId,
        event: EventId
    ): SummaryTrustDatabase.TrustState {
        val result = getDb().transaction("summary_trust") {
            objectStore("summary_trust").get(Key(room.full, event.full)) as? JsSummaryTrust
        }
        return result?.state?.let { enumValueOf<SummaryTrustDatabase.TrustState>(it) }
            ?: SummaryTrustDatabase.TrustState.UNTRUSTED
    }

    override suspend fun getHeads(room: RoomId): Set<EventId> {
        return getDb().transaction("summary_head") {
            val index = objectStore("summary_head").index("roomId")
            index.getAll(Key(room.full)).map { (it as JsSummaryHead).eventId }.map { EventId(it) }.toSet()
        }
    }

    override suspend fun addTrustedSummary(
        room: RoomId,
        summaryId: EventId,
        parents: Set<EventId>,
        transactions: Set<EventId>,
        root: Boolean
    ) {
        getDb().writeTransaction("summary_head", "summary_trust", "summary_link", "summary_transaction") {
            val headStore = objectStore("summary_head")
            if (root) {
                headStore.index("roomId")
                    .openCursor(Key(room.full), autoContinue = true)
                    .collect {
                        it.delete()
                    }
            }
            headStore.put(jso<JsSummaryHead> {
                this.roomId = room.full
                this.eventId = summaryId.full
            })
            for (head in parents) {
                headStore.delete(Key(room.full, head.full))
            }

            objectStore("summary_trust").put(jso<JsSummaryTrust> {
                this.roomId = room.full
                this.eventId = summaryId.full
                this.state = SummaryTrustDatabase.TrustState.TRUSTED.name
            })

            val linkStore = objectStore("summary_link")
            for (parent in parents) {
                linkStore.put(jso<JsSummaryLink> {
                    this.roomId = room.full
                    this.summaryId = summaryId.full
                    this.otherId = parent.full
                })
            }
            val transactionStore = objectStore("summary_transaction")
            for (transaction in transactions) {
                transactionStore.put(jso<JsSummaryLink> {
                    this.roomId = room.full
                    this.summaryId = summaryId.full
                    this.otherId = transaction.full
                })
            }
        }
    }

    override suspend fun getSummaryParents(room: RoomId, summary: EventId): Set<EventId> {
        return getDb().transaction("summary_link") {
            val index = objectStore("summary_link").index("parent")
            index.getAll(Key(room.full, summary.full)).map { (it as JsSummaryLink).otherId }.map { EventId(it) }.toSet()
        }
    }

    override suspend fun getSummariesReferencingTransactions(room: RoomId, transactions: Set<EventId>): Map<EventId, Set<EventId>> {
        return getDb().transaction("summary_transaction") {
            val index = objectStore("summary_transaction").index("transaction")
            val result = mutableMapOf<EventId, MutableSet<EventId>>()
            transactions.forEach { transactionId ->
                index.getAll(Key(room.full, transactionId.full)).forEach {
                    val link = it as JsSummaryLink
                    result.getOrPut(EventId(link.otherId)) { mutableSetOf() }.add(EventId(link.summaryId))
                }
            }
            result
        }
    }
}
