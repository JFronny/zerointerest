package dev.jfronny.zerointerest.db

import dev.jfronny.zerointerest.data.TransactionTemplate
import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import de.connect2x.trixnity.core.model.UserId
import dev.jfronny.zerointerest.data.TrustState
import dev.jfronny.zerointerest.data.ZeroInterestSummaryEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ZeroInterestDatabase(val db: ZeroInterestRoomDatabase) {
    suspend fun markTrusted(room: RoomId, event: EventId) {
        db.summaryTrustDao().insert(SummaryTrustEntity(room.full, event.full, TrustState.TRUSTED))
    }

    suspend fun markRejected(room: RoomId, event: EventId) {
        db.summaryTrustDao().insert(SummaryTrustEntity(room.full, event.full, TrustState.REJECTED))
    }

    suspend fun checkTrust(room: RoomId, event: EventId): TrustState {
        return db.summaryTrustDao().getTrustState(room.full, event.full) ?: TrustState.UNTRUSTED
    }

    suspend fun getHeads(room: RoomId): Set<EventId> {
        return db.summaryHeadDao().getHeads(room.full).map { EventId(it) }.toSet()
    }

    fun getHeadsFlow(room: RoomId): Flow<Set<EventId>> {
        return db.summaryHeadDao().getHeadsFlow(room.full).map { list -> list.map { EventId(it) }.toSet() }
    }

    suspend fun addTrustedSummary(
        room: RoomId,
        eventId: EventId,
        event: ZeroInterestSummaryEvent,
        root: Boolean = false,
    ) {
        if (root) db.summaryHeadDao().clear(room.full)
        db.summaryHeadDao().insert(SummaryHeadEntity(room.full, eventId.full))
        event.parents.keys.forEach {
            db.summaryHeadDao().removeHead(room.full, it.full)
        }
        db.summaryTrustDao().insert(SummaryTrustEntity(room.full, eventId.full, TrustState.TRUSTED))
        db.summaryDao().insertSummary(event.parents.keys.map {
            SummaryEntity(room.full, eventId.full, it.full)
        })
        event.parents.values.firstOrNull()?.let { transactions ->
            db.summaryDao().insertTransactions(transactions.map {
                SummaryTransactionEntity(room.full, eventId.full, it.full)
            })
        }
    }

    suspend fun resetTrust(room: RoomId) {
        db.summaryHeadDao().clear(room.full)
        db.summaryDao().clearSummaryTransactions(room.full)
        db.summaryDao().clearSummaries(room.full)
        db.summaryTrustDao().clear(room.full)
    }

    suspend fun getSummaryParents(room: RoomId, summary: EventId): Set<EventId> {
        return db.summaryDao().getParents(room.full, summary.full).map { EventId(it) }.toSet()
    }

    suspend fun getSummariesReferencingTransactions(room: RoomId, transactions: Set<EventId>): Map<EventId, Set<EventId>> {
        val result = mutableMapOf<EventId, MutableSet<EventId>>()
        db.summaryDao().getSummariesForTransactions(room.full, transactions.map { it.full }).forEach {
            result.getOrPut(EventId(it.transactionId)) { mutableSetOf() }.add(EventId(it.summaryId))
        }
        return result
    }

    fun getTransactionTemplates(room: RoomId): Flow<List<TransactionTemplate>> {
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

    suspend fun addTransactionTemplate(room: RoomId, template: TransactionTemplate) {
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

    suspend fun removeTransactionTemplate(room: RoomId, templateId: String) {
        db.transactionTemplateDao().delete(room.full, templateId)
    }
}
