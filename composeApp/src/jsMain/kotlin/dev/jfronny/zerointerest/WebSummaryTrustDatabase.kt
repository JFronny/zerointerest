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

class WebSummaryTrustDatabase : SummaryTrustDatabase {
    private var _db: Database? = null

    private suspend fun getDb(): Database {
        if (_db == null) {
            _db = openDatabase("zerointerest-summary-trust", 1) { database, oldVersion, newVersion ->
                if (oldVersion < 1) {
                    database.createObjectStore("summary_trust", KeyPath("roomId", "eventId"))
                    val headStore = database.createObjectStore("summary_head", KeyPath("roomId", "eventId"))
                    headStore.createIndex("roomId", KeyPath("roomId"), unique = false)
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

    override suspend fun setHeads(
        room: RoomId,
        heads: Set<EventId>
    ) {
        getDb().writeTransaction("summary_head") {
            val store = objectStore("summary_head")
            val index = store.index("roomId")
            val existing = index.getAll(Key(room.full))
            for (item in existing) {
                val head = item as JsSummaryHead
                store.delete(Key(head.roomId, head.eventId))
            }
            for (head in heads) {
                store.put(jso<JsSummaryHead> {
                    roomId = room.full
                    eventId = head.full
                })
            }
        }
    }

    override suspend fun addHead(
        room: RoomId,
        head: EventId
    ) {
        getDb().writeTransaction("summary_head") {
            objectStore("summary_head").put(jso<JsSummaryHead> {
                roomId = room.full
                eventId = head.full
            })
        }
    }

    override suspend fun removeHeads(
        room: RoomId,
        heads: Set<EventId>
    ) {
        getDb().writeTransaction("summary_head") {
            val store = objectStore("summary_head")
            for (head in heads) {
                store.delete(Key(room.full, head.full))
            }
        }
    }
}