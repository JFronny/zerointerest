package dev.jfronny.zerointerest

import dev.jfronny.zerointerest.data.TransactionTemplate
import dev.jfronny.zerointerest.service.ZeroInterestDatabase
import dev.jfronny.zerointerest.service.ZeroInterestDatabase.TrustState
import dev.jfronny.zerointerest.service.ZeroInterestDatabase.TrustState.*
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomZeroInterestDatabase(val db: ZeroInterestRoomDatabase) : ZeroInterestDatabase {
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

    override fun getTransactionTemplates(room: RoomId): Flow<List<TransactionTemplate>> {
        return db.transactionTemplateDao().getTemplates(room.full).map { list ->
            list.map { entity ->
                TransactionTemplate(
                    id = entity.id,
                    description = entity.description,
                    sender = UserId(entity.sender),
                    receivers = entity.receivers
                )
            }
        }
    }

    override suspend fun addTransactionTemplate(room: RoomId, template: TransactionTemplate) {
        db.transactionTemplateDao().insert(
            TransactionTemplateEntity(
                roomId = room.full,
                id = template.id,
                description = template.description,
                sender = template.sender.full,
                receivers = template.receivers
            )
        )
    }

    override suspend fun removeTransactionTemplate(room: RoomId, templateId: String) {
        db.transactionTemplateDao().delete(room.full, templateId)
    }
}
