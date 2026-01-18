package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.service.SummaryTrustDatabase
import dev.jfronny.zerointerest.service.SummaryTrustDatabase.TrustState
import dev.jfronny.zerointerest.service.SummaryTrustDatabase.TrustState.*
import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

class RoomSummaryTrustDatabase(val db: ZeroInterestRoomDatabase) : SummaryTrustDatabase {
    override suspend fun markTrusted(room: RoomId, event: EventId) {
        db.summaryTrustDao().insert(SummaryTrustEntity(room.full, event.full, TRUSTED))
    }

    override suspend fun markRejected(room: RoomId, event: EventId) {
        db.summaryTrustDao().insert(SummaryTrustEntity(room.full, event.full, REJECTED))
    }

    override suspend fun checkTrust(room: RoomId, event: EventId): TrustState {
        return db.summaryTrustDao().getTrustState(room.full, event.full) ?: UNTRUSTED
    }

    override suspend fun getHeads(room: RoomId): Set<EventId> {
        return db.summaryHeadDao().getHeads(room.full).map { EventId(it) }.toSet()
    }

    override suspend fun addTrustedSummary(
        room: RoomId,
        summaryId: EventId,
        parents: Set<EventId>,
        transactions: Set<EventId>,
        root: Boolean
    ) {
        if (root) db.summaryHeadDao().clear(room.full)
        db.summaryHeadDao().insert(SummaryHeadEntity(room.full, summaryId.full))
        parents.forEach {
            db.summaryHeadDao().removeHead(room.full, it.full)
        }
        db.summaryTrustDao().insert(SummaryTrustEntity(room.full, summaryId.full, TRUSTED))
        db.summaryDao().insertSummary(parents.map {
            SummaryEntity(room.full, summaryId.full, it.full)
        })
        db.summaryDao().insertTransactions(transactions.map {
            SummaryTransactionEntity(room.full, summaryId.full, it.full)
        })
    }

    override suspend fun getSummaryParents(room: RoomId, summary: EventId): Set<EventId> {
        return db.summaryDao().getParents(room.full, summary.full).map { EventId(it) }.toSet()
    }

    override suspend fun getSummariesReferencingTransactions(room: RoomId, transactions: Set<EventId>): Map<EventId, Set<EventId>> {
        val result = mutableMapOf<EventId, MutableSet<EventId>>()
        db.summaryDao().getSummariesForTransactions(room.full, transactions.map { it.full }).forEach {
            result.getOrPut(EventId(it.transactionId)) { mutableSetOf() }.add(EventId(it.summaryId))
        }
        return result
    }
}
