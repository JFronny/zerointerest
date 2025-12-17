package dev.jfronny.zerointerest.service

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

interface SummaryTrustDatabase {
    suspend fun markTrusted(room: RoomId, event: EventId)
    suspend fun markRejected(room: RoomId, event: EventId)
    suspend fun checkTrust(room: RoomId, event: EventId): TrustState
    suspend fun getHeads(room: RoomId): Set<EventId>
    suspend fun setHeads(room: RoomId, heads: Set<EventId>)
    suspend fun addHead(room: RoomId, head: EventId)
    suspend fun removeHeads(room: RoomId, heads: Set<EventId>)

    enum class TrustState {
        UNTRUSTED, TRUSTED, REJECTED
    }
}
