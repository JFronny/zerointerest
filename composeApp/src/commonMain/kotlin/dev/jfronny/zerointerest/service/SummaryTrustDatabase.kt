package dev.jfronny.zerointerest.service

import de.connect2x.trixnity.core.model.EventId
import de.connect2x.trixnity.core.model.RoomId

interface SummaryTrustDatabase {
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

    enum class TrustState {
        UNTRUSTED, TRUSTED, REJECTED
    }
}
