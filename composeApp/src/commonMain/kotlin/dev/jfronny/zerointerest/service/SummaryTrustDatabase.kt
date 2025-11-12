package dev.jfronny.zerointerest.service

import net.folivo.trixnity.core.model.EventId
import net.folivo.trixnity.core.model.RoomId

interface SummaryTrustDatabase {
    suspend fun markTrusted(room: RoomId, event: EventId)
    suspend fun markRejected(room: RoomId, event: EventId)
    suspend fun checkTrust(room: RoomId, event: EventId): TrustState

    enum class TrustState {
        UNTRUSTED, TRUSTED, REJECTED
    }
}