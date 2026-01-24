package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId
import dev.jfronny.zerointerest.data.TransactionTemplate
import kotlinx.coroutines.flow.Flow

interface ZeroInterestDatabase {
    suspend fun markTrusted(room: RoomId, event: EventId)
    suspend fun markRejected(room: RoomId, event: EventId)
    suspend fun checkTrust(room: RoomId, event: EventId): TrustState
    suspend fun getHeads(room: RoomId): Set<EventId>
    suspend fun addTrustedSummary(
        room: RoomId,
        summaryId: EventId,
        parents: Set<EventId>,
        transactions: Set<EventId>,
        root: Boolean = false
    )
    suspend fun getSummaryParents(room: RoomId, summary: EventId): Set<EventId>
    suspend fun getSummariesReferencingTransactions(room: RoomId, transactions: Set<EventId>): Map<EventId, Set<EventId>>

    fun getTransactionTemplates(room: RoomId): Flow<List<TransactionTemplate>>
    suspend fun addTransactionTemplate(room: RoomId, template: TransactionTemplate)
    suspend fun removeTransactionTemplate(room: RoomId, templateId: String)

    enum class TrustState {
        UNTRUSTED, TRUSTED, REJECTED
    }
}
